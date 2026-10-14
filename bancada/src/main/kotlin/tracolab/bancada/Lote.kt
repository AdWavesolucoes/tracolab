package tracolab.bancada

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Contexto
import tracolab.nucleo.Embutidos
import tracolab.nucleo.Especial
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Motor
import tracolab.nucleo.NaoSuportado
import tracolab.nucleo.No
import tracolab.nucleo.Planilha
import tracolab.nucleo.Regiao
import tracolab.nucleo.avaliar
import tracolab.nucleo.regiaoDeTexto
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.exitProcess

/*
 * Avaliação em lote: lê folhas de um JSONL (uma por linha), calcula cada uma e grava o estado de
 * cada região noutro JSONL. Serve para comparar o núcleo com resultados de referência obtidos por
 * fora (a entrada e a comparação ficam fora do repositório) e para comparar versões do núcleo.
 *
 *   ./gradlew :bancada:installDist
 *   bancada/build/install/bancada/bin/bancada lote <entrada.jsonl> <saida.jsonl> [limite-s]
 *
 * Entrada, uma folha por linha:
 *   {"id": "f1", "modo": "classico", "origem": 0, "unidades_implicitas": true, "tambem_parcial": true,
 *    "regioes": [{"id": "r1", "texto": "x := 2*a", "x": 0, "y": 10}]}
 * Saída, uma folha por linha, na ordem da entrada:
 *   {"id": "f1", "ms": 3, "regioes": [{"id": "r1", "tipo": "parcial", "msg": null, "aviso": null,
 *    "livres": ["a"], "livres_parcial": ["a"], "valor": {...}}]}
 *
 * `valor` (estados numérico e parcial com número) traz `forma` ([] escalar, [n] vetor,
 * [linhas, colunas] matriz, valores por coluna como na ADR 0004), `re`, `im` e `dims` (expoentes
 * de m, kg, s, A, K, mol, cd). Os números vêm na unidade pedida pela região (`x = {mm}`) ou, sem
 * ela, nas unidades de base do SI. `tambem_parcial` calcula a folha de novo no modo parcial e
 * informa em `livres_parcial` os nomes que faltariam em cada região.
 *
 * A função `nao_suportado("motivo")` marca uma construção que quem gerou a entrada não soube
 * expressar: a região fica no estado não suportado com esse motivo (e quem depende dela também).
 *
 * Uma folha que passa de `limite-s` segundos (padrão 30) sai como {"id": ..., "estouro": true} e o
 * processo termina com código 3: quem chamou pode recomeçar a partir da folha seguinte.
 */

private val json = ObjectMapper()

fun lote(entrada: File, saida: File, limiteS: Long) {
    Embutidos.especial("nao_suportado", Especial { n, _ ->
        throw NaoSuportado((n.args.firstOrNull() as? No.Texto)?.valor ?: "construção não suportada")
    })
    val motor = Motor()
    // pilha grande: funções recursivas legítimas não devem cair por falta de pilha da thread
    val trabalho = Executors.newSingleThreadExecutor { r -> Thread(null, r, "folha", 512L shl 20).apply { isDaemon = true } }
    saida.bufferedWriter().use { w ->
        for (linha in entrada.bufferedReader().lineSequence()) {
            if (linha.isBlank()) continue
            val folha = json.readTree(linha)
            val id = folha["id"].asText()
            val tarefa = trabalho.submit<ObjectNode> { calcularFolha(folha, motor) }
            val res = try {
                tarefa.get(limiteS, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                w.write(json.writeValueAsString(json.createObjectNode().put("id", id).put("estouro", true)))
                w.newLine(); w.flush()
                exitProcess(3)
            } catch (e: Exception) {
                val causa = e.cause ?: e
                json.createObjectNode().put("id", id).put("falha", "${causa.javaClass.simpleName}: ${causa.message}".take(300))
            }
            w.write(json.writeValueAsString(res)); w.newLine(); w.flush()
        }
    }
    trabalho.shutdownNow()
}

private fun montar(folha: JsonNode, modo: String, motor: Motor, erros: MutableMap<String, String>): Planilha {
    val pl = Planilha(modo, folha["origem"]?.asInt() ?: 0, folha["unidades_implicitas"]?.asBoolean() ?: false, motor)
    for (r in folha["regioes"]) {
        val id = r["id"].asText()
        try {
            pl.regioes += regiaoDeTexto(id, r["texto"].asText(), r["y"]?.asDouble() ?: 0.0, r["x"]?.asDouble() ?: 0.0)
        } catch (e: Exception) {
            erros[id] = e.message ?: e.javaClass.simpleName
        }
    }
    pl.recalcular()
    return pl
}

private fun calcularFolha(folha: JsonNode, motor: Motor): ObjectNode {
    val t0 = System.nanoTime()
    val sintaxe = LinkedHashMap<String, String>()
    val pl = montar(folha, folha["modo"]?.asText() ?: "parcial", motor, sintaxe)
    val parcial = if (folha["tambem_parcial"]?.asBoolean() == true && pl.modo != "parcial")
        montar(folha, "parcial", motor, HashMap()).regioes.associateBy { it.id } else null
    val porId = pl.regioes.associateBy { it.id }
    val out = json.createObjectNode().put("id", folha["id"].asText())
    val regs = json.createArrayNode()
    for (r in folha["regioes"]) {
        val id = r["id"].asText()
        val o = regs.addObject().put("id", id)
        val erroSintaxe = sintaxe[id]
        if (erroSintaxe != null) { o.put("tipo", "erro_sintaxe").put("msg", erroSintaxe); continue }
        val reg = porId.getValue(id)
        val e = reg.estado!!
        o.put("tipo", e.tipo).put("msg", e.msg).put("aviso", e.aviso)
        o.putArray("livres").apply { e.livres.forEach { add(it) } }
        parcial?.get(id)?.estado?.let { p -> o.putArray("livres_parcial").apply { p.livres.forEach { add(it) } } }
        if (e.tipo == "numerico" || e.tipo == "parcial") o.put("tela", pl.exibir(id).take(200))
        val v = e.valor
        when {
            e.funcao != null -> o.put("funcao", true)
            e.faixa != null -> o.put("faixa", true)
            v != null && e.tipo == "numerico" -> o.set<ObjectNode>("valor", valor(pl, reg, v))
            v != null -> o.put("texto", v.toString().take(300))
        }
    }
    out.put("ms", (System.nanoTime() - t0) / 1_000_000)
    out.set<ObjectNode>("regioes", regs)
    return out
}

/** Números de um valor, na unidade pedida pela região ou nas de base do SI. */
private fun valor(pl: Planilha, r: Regiao, v: IExpr): ObjectNode {
    val m = pl.motor
    val o = json.createObjectNode()
    var alvo: IExpr? = null
    r.unidadeExibicao?.let {
        try { alvo = avaliar(it, Contexto(emptyMap(), pl)) } catch (e: Exception) { o.put("unidade_pedida_erro", e.message ?: e.javaClass.simpleName) }
    }
    val forma = mutableListOf<Int>()
    val elems: List<IExpr> = if (v.isList) {
        val itens = (1 until (v as IAST).size()).map { v.get(it) }
        if (itens.isNotEmpty() && itens.all { it.isList }) {
            val linhas = itens.map { l -> (1 until (l as IAST).size()).map { l.get(it) } }
            if (linhas.any { it.size != linhas[0].size }) return o.put("irregular", true)
            forma += listOf(linhas.size, linhas[0].size)
            (0 until linhas[0].size).flatMap { j -> linhas.map { it[j] } }
        } else { forma += itens.size; itens }
    } else listOf(v)
    o.putArray("forma").apply { forma.forEach { add(it) } }
    val re = o.putArray("re"); val im = o.putArray("im")
    var dims: DoubleArray? = null
    for (x in elems) {
        val d = try { m.dims(x) } catch (_: ErroUnidade) { null }
        if (dims == null && d != null) dims = d
        val a = alvo
        if (a != null && d != null && !d.contentEquals(m.dims(a))) o.put("unidade_incompativel", true)
        val divisor = when {
            a != null -> a
            d != null && m.temUnidade(x) -> m.monomio(d)
            else -> F.C1
        }
        try {
            val c = m.engine.evalComplex(m.ev(F.Divide(x, divisor)))
            re.add(c.real); im.add(c.imaginary)
        } catch (_: Exception) { re.addNull(); im.addNull() }
    }
    o.putArray("dims").apply { (dims ?: DoubleArray(7)).forEach { add(it) } }
    if (alvo != null) o.put("na_unidade_pedida", true)
    return o
}
