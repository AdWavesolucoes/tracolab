package tracolab.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import tracolab.nucleo.Motor
import tracolab.nucleo.Planilha
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela
import tracolab.nucleo.regiaoDeTexto

/**
 * Recalcula uma página inteira a partir do JSON vindo da interface.
 *
 * Pedido:   {"modo": "parcial", "regioes": [{"id": "r1", "latex": "F\\coloneq m\\cdot a", "x": 40, "y": 120}]}
 * Resposta: {"ms": 12, "regioes": [{"id": "r1", "papel": "definicao", "tipo": "parcial",
 *            "texto": "F := m*a", "resultado": null, "livres": ["a", "m"], "msg": null, "aviso": null}]}
 *
 * `y` é a linha de base da região (ADR 0001). `tipo` pode ser também "erro_sintaxe" (o LaTeX
 * não pôde ser lido) ou "vazia". O motor (Symja) não é seguro para várias threads: as chamadas
 * são serializadas.
 */
class Calculadora(private val motor: Motor = Motor()) {
    private val json = ObjectMapper()

    fun calcularJson(pedido: String): String = json.writeValueAsString(calcular(json.readTree(pedido)))

    @Synchronized
    fun calcular(pedido: JsonNode): ObjectNode {
        val t0 = System.nanoTime()
        val pl = Planilha(pedido["modo"]?.asText() ?: "parcial", motor = motor)
        val saidas = linkedMapOf<String, ObjectNode>()
        for (r in pedido["regioes"] ?: json.createArrayNode()) {
            val id = r["id"].asText()
            val latex = r["latex"]?.asText().orEmpty()
            val o = json.createObjectNode().put("id", id)
            saidas[id] = o
            if (latex.isBlank()) { o.put("tipo", "vazia"); continue }
            try {
                val texto = LatexEntrada.regiao(latex)
                o.put("texto", texto)
                val reg = regiaoDeTexto(id, texto, r["y"]?.asDouble() ?: 0.0, r["x"]?.asDouble() ?: 0.0)
                o.put("papel", reg.tipo)
                pl.regioes += reg
            } catch (e: Exception) {
                o.put("tipo", "erro_sintaxe").put("msg", e.message ?: e.javaClass.simpleName)
            }
        }
        pl.recalcular()
        for (reg in pl.regioes) {
            val o = saidas.getValue(reg.id)
            val e = reg.estado!!
            o.put("tipo", e.tipo).put("msg", e.msg).put("aviso", e.aviso)
            o.putArray("livres").apply { e.livres.forEach { add(it) } }
            val tela = pl.resultadoTela(reg.id)
            o.put("resultado", tela.latex)
            tela.avisoExibicao?.let { o.put("aviso", it) }
        }
        val resp = json.createObjectNode()
        resp.put("ms", (System.nanoTime() - t0) / 1_000_000)
        resp.putArray("regioes").addAll(saidas.values)
        return resp
    }
}
