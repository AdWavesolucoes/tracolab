package tracolab.api

import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import tracolab.nucleo.Matriciais
import tracolab.nucleo.Formato
import tracolab.nucleo.Limites
import tracolab.nucleo.Motor
import tracolab.nucleo.Planilha
import tracolab.nucleo.latex.ConfigTela
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.configTela
import tracolab.nucleo.latex.resultadoTela
import tracolab.nucleo.latex.substituicaoTela
import tracolab.nucleo.regiaoDeTexto
import tracolab.nucleo.textoTempo
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val MAX_TEXTO_JSON = 8_000_000

/** Maior aninhamento de chaves e parênteses do LaTeX. */
private fun profundidade(latex: String): Int {
    var nivel = 0; var maior = 0
    for (c in latex) when (c) {
        '{', '(', '[' -> { nivel++; if (nivel > maior) maior = nivel }
        '}', ')', ']' -> nivel--
    }
    return maior
}

/** Pedido que a API recusa inteiro (400): JSON malformado, campos errados, tamanho além do limite. */
class PedidoInvalido(msg: String) : Exception(msg)

/** Todos os motores ocupados por mais tempo que a espera (503: tentar de novo). */
class Ocupado(msg: String) : Exception(msg)

/**
 * Tamanho máximo de um pedido (ADR 0017). Região longa demais vira erro só nela; o resto recusa
 * o pedido inteiro.
 */
data class LimitesPedido(
    val regioes: Int = 2_000,
    val caracteresLatex: Int = 20_000,
    val caracteresId: Int = 100,
    /** Chaves e parênteses aninhados numa fórmula. */
    val niveis: Int = 200,
    /** Espera por um motor livre antes de responder "ocupado". */
    val msEspera: Long = 20_000,
    /** Folga, além do prazo da folha, antes de abandonar um cálculo que não para (ex.: conta presa
     *  fora do Symja). O motor dele é trocado por um novo. */
    val msFolga: Long = 5_000,
)

/**
 * Recalcula uma página inteira a partir do JSON vindo da interface.
 *
 * Pedido:   {"modo": "parcial", "regioes": [{"id": "r1", "latex": "F\\coloneq m\\cdot a", "x": 40, "y": 120}]}
 * Resposta: {"ms": 12, "regioes": [{"id": "r1", "papel": "definicao", "tipo": "parcial",
 *            "texto": "F := m*a", "resultado": null, "livres": ["a", "m"], "msg": null, "aviso": null}]}
 *
 * Cada região também traz `define` (nomes que ela define, para o painel de variáveis) e
 * `abaixo` (nomes que faltam mas são definidos mais abaixo na folha: erro de ordem, não dado faltando).
 *
 * Folha como documento: `mostra: true` numa definição terminada em "=" (definir e mostrar, a
 * tela mostra `resultado` como numa avaliação); com `"substituir": true` no pedido de uma região
 * que mostra valor, a resposta traz `substituicao` (a fórmula com os valores, ver substituicaoTela).
 *
 * `y` é a linha de base da região (ADR 0001). `tipo` pode ser também "erro_sintaxe" (o LaTeX
 * não pôde ser lido) ou "vazia".
 *
 * Limites (ADR 0017): o tamanho do pedido ([LimitesPedido]) e o tempo de cada região e da folha
 * ([Limites]). O motor (Symja) não é seguro para várias threads: há [motores] motores, cada pedido
 * usa um de cada vez, numa thread com pilha grande; um cálculo que não para nem depois do prazo é
 * abandonado e o motor dele é trocado.
 */
class Calculadora(
    private val motores: Int = 1,
    private val limites: Limites = Limites(),
    private val limitesPedido: LimitesPedido = LimitesPedido(),
) {
    private val json = ObjectMapper().apply {
        // o tamanho de cada fórmula é conferido depois, região por região (erro só nela)
        factory.setStreamReadConstraints(StreamReadConstraints.builder().maxStringLength(MAX_TEXTO_JSON).build())
    }
    private val livres = ArrayBlockingQueue<Motor>(motores).apply { repeat(motores) { add(Motor()) } }
    private val threads = Executors.newCachedThreadPool { r ->
        Thread(null, r, "tracolab-calculo", 64L shl 20).apply { isDaemon = true }   // 64 MB de pilha: fórmulas fundas
    }

    fun calcularJson(pedido: String): String {
        val arvore = try { json.readTree(pedido) } catch (e: Exception) { throw PedidoInvalido("JSON inválido.") }
        return json.writeValueAsString(calcular(arvore))
    }

    fun calcular(pedido: JsonNode): ObjectNode {
        validar(pedido)
        val motor = livres.poll(limitesPedido.msEspera, TimeUnit.MILLISECONDS)
            ?: throw Ocupado("Servidor ocupado com outros cálculos; tente de novo.")
        val tarefa = threads.submit<ObjectNode> { calcularCom(motor, pedido) }
        try {
            val r = tarefa.get(limites.msFolha + limitesPedido.msFolga, TimeUnit.MILLISECONDS)
            livres.add(motor)
            return r
        } catch (e: TimeoutException) {
            // não parou nem com o pedido de parada: abandona a thread e troca o motor
            motor.engine.stopRequest(); tarefa.cancel(true)
            livres.add(Motor())
            val msg = "Cálculo interrompido: a folha passou de ${textoTempo(limites.msFolha)}."
            val resp = json.createObjectNode().put("ms", limites.msFolha + limitesPedido.msFolga).put("erro", msg)
            val regs = resp.putArray("regioes")
            for (r in pedido["regioes"] ?: json.createArrayNode())
                regs.addObject().put("id", r["id"].asText()).put("tipo", "erro").put("msg", msg)
            return resp
        } catch (e: ExecutionException) {
            livres.add(Motor())   // motor num estado desconhecido depois de uma falha grave
            throw e.cause ?: e
        }
    }

    private fun validar(pedido: JsonNode) {
        if (!pedido.isObject) throw PedidoInvalido("O pedido deve ser um objeto JSON.")
        val modo = pedido["modo"]
        if (modo != null && modo.asText() !in setOf("parcial", "classico")) throw PedidoInvalido("modo deve ser 'parcial' ou 'classico'.")
        val regs = pedido["regioes"] ?: return
        if (!regs.isArray) throw PedidoInvalido("regioes deve ser uma lista.")
        if (regs.size() > limitesPedido.regioes) throw PedidoInvalido("Folha grande demais: ${regs.size()} regiões (máximo ${limitesPedido.regioes}).")
        val ids = HashSet<String>()
        for (r in regs) {
            val id = r["id"]
            if (r !is ObjectNode || id == null || !id.isTextual || id.asText().isEmpty() || id.asText().length > limitesPedido.caracteresId)
                throw PedidoInvalido("Cada região precisa de um id de texto (até ${limitesPedido.caracteresId} caracteres).")
            if (!ids.add(id.asText())) throw PedidoInvalido("id de região repetido: ${id.asText()}")
        }
    }

    /** `opcoes.formato` (folha) e `opcoes.formatos` ({id: formato}, por resultado, por cima do da folha). */
    private fun configTela(opcoes: JsonNode?): ConfigTela {
        fun mapa(n: JsonNode?) = n?.takeIf { it.isObject }?.let { json.convertValue(it, Map::class.java) }
        val folha = Formato().com(mapa(opcoes?.get("formato")))
        val regioes = HashMap<String, Formato>()
        opcoes?.get("formatos")?.takeIf { it.isObject }?.fields()?.forEach { (id, f) -> mapa(f)?.let { regioes[id] = folha.com(it) } }
        return ConfigTela(folha, regioes)
    }

    private fun calcularCom(motor: Motor, pedido: JsonNode): ObjectNode {
        motor.ativar()
        val t0 = System.nanoTime()
        val origem = pedido["opcoes"]?.get("origem")?.asInt() ?: 0   // primeiro índice de vetores (ADR 0010)
        val pl = Planilha(pedido["modo"]?.asText() ?: "parcial", origem = if (origem == 1) 1 else 0, motor = motor, limites = limites)
        pl.configTela = configTela(pedido["opcoes"])   // formato dos números e unidades da folha (ADR 0014)
        val saidas = linkedMapOf<String, ObjectNode>()
        val substituir = HashSet<String>()
        for (r in pedido["regioes"] ?: json.createArrayNode()) {
            val id = r["id"].asText()
            val latex = r["latex"]?.asText().orEmpty()
            val o = json.createObjectNode().put("id", id)
            saidas[id] = o
            if (r["substituir"]?.asBoolean() == true) substituir += id
            if (latex.isBlank()) { o.put("tipo", "vazia"); continue }
            if (latex.length > limitesPedido.caracteresLatex) {
                o.put("tipo", "erro_sintaxe").put("msg", "Fórmula longa demais: ${latex.length} caracteres (máximo ${limitesPedido.caracteresLatex}).")
                continue
            }
            // a conversão do LaTeX não tem prazo e fica quadrática com chaves muito aninhadas
            if (profundidade(latex) > limitesPedido.niveis) {
                o.put("tipo", "erro_sintaxe").put("msg", "Fórmula aninhada demais (mais de ${limitesPedido.niveis} níveis).")
                continue
            }
            try {
                val texto = LatexEntrada.regiao(latex)
                o.put("texto", texto)
                val reg = regiaoDeTexto(id, texto, r["y"]?.asDouble() ?: 0.0, r["x"]?.asDouble() ?: 0.0)
                o.put("papel", reg.tipo)
                pl.regioes += reg
            } catch (e: StackOverflowError) {
                o.put("tipo", "erro_sintaxe").put("msg", "Fórmula aninhada demais.")
            } catch (e: Exception) {
                o.put("tipo", "erro_sintaxe").put("msg", e.message ?: e.javaClass.simpleName)
            }
        }
        pl.recalcular()
        val definidos = pl.regioes.flatMap { it.define }.toSet()
        for (reg in pl.regioes) {
            val o = saidas.getValue(reg.id)
            val e = reg.estado!!
            o.put("tipo", e.tipo).put("msg", e.msg).put("aviso", e.aviso)
            o.putArray("livres").apply { e.livres.forEach { add(it) } }
            o.putArray("define").apply { reg.define.forEach { add(it) } }
            o.putArray("abaixo").apply { e.livres.filter { it in definidos }.forEach { add(it) } }
            e.valor?.takeIf { it.isList }?.let { v ->   // vetor/matriz: [linhas, colunas] (painel de variáveis)
                runCatching { Matriciais.dimensoes(v) }.getOrNull()?.let { (l, c) -> o.putArray("forma").add(l).add(c) }
            }
            // exibir também tem prazo e não pode derrubar a folha (ADR 0017)
            val tela = try {
                motor.prazo.com(limites.msRegiao, "Resultado demorou demais para mostrar.") { pl.resultadoTela(reg.id) }
            } catch (e: Throwable) {
                if (e is Error && e !is StackOverflowError && e !is OutOfMemoryError) throw e
                tracolab.nucleo.latex.ResultadoTela(null, if (motor.interrompido(e)) e.message ?: "Resultado demorou demais para mostrar."
                                                          else "Não foi possível mostrar o resultado.")
            }
            o.put("resultado", tela.latex)
            tela.avisoExibicao?.let { o.put("aviso", it) }
            if (reg.mostrar) {
                o.put("mostra", true)
                if (reg.tipoAlvo == "funcao" && e.aviso == null) o.put("aviso", "Uma função não tem valor para mostrar.")
            }
            val mostraValor = reg.mostrar || reg.tipo.startsWith("avaliacao")
            if (reg.id in substituir && mostraValor && (e.tipo == "numerico" || e.tipo == "parcial"))
                try {   // mesma proteção da exibição: sem substituição, mas a folha sai (ADR 0017)
                    motor.prazo.com(limites.msRegiao, "Substituição demorou demais.") { pl.substituicaoTela(reg.id) }?.let { o.put("substituicao", it) }
                } catch (e: Throwable) {
                    if (e is Error && e !is StackOverflowError && e !is OutOfMemoryError) throw e
                }
        }
        val resp = json.createObjectNode()
        resp.put("ms", (System.nanoTime() - t0) / 1_000_000)
        resp.putArray("regioes").addAll(saidas.values)
        return resp
    }
}
