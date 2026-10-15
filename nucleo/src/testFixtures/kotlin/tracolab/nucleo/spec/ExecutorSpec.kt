package tracolab.nucleo.spec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import tracolab.nucleo.Contexto
import tracolab.nucleo.Motor
import tracolab.nucleo.Parser
import tracolab.nucleo.Planilha
import tracolab.nucleo.avaliar
import tracolab.nucleo.regiaoDeTexto
import kotlin.math.abs
import kotlin.math.max

/*
 * Executor de spec/casos-comportamento.json: usado pelos testes do núcleo e pela bancada.
 * O formato está descrito em spec/README.md.
 */

class ResultadoSpec(val ok: Int, val total: Int, val linhas: List<String>, val tempos: List<Double>)

fun executarSpec(json: String, motor: Motor): ResultadoSpec {
    val spec = ObjectMapper().readTree(json)
    var ok = 0; var total = 0
    val tempos = mutableListOf<Double>(); val linhas = mutableListOf<String>()
    for (caso in spec["casos"]) {
        total++
        val a = System.nanoTime()
        val falhas = try { rodarCaso(caso, motor) } catch (e: Throwable) { listOf("exceção ${e.javaClass.simpleName}: ${e.message}") }
        tempos += (System.nanoTime() - a) / 1e6
        if (falhas.isEmpty()) { ok++; linhas += "ok    ${caso["id"].asText()} ${caso["nome"].asText()}" }
        else { linhas += "FALHA ${caso["id"].asText()} ${caso["nome"].asText()}"; falhas.forEach { linhas += "      $it" } }
    }
    return ResultadoSpec(ok, total, linhas, tempos)
}

fun rodarCaso(caso: JsonNode, motor: Motor): List<String> {
    val pl = Planilha(caso["modo"]?.asText() ?: "parcial", origem = caso["origem"]?.asInt() ?: 0,
        unidades = caso["unidades_implicitas"]?.asBoolean() ?: false, motor = motor)
    val falhas = mutableListOf<String>()
    for (p in caso["passos"]) {
        if (p.has("verificar")) { falhas += verificar(pl, p).map { "[${p["verificar"].asText()}] $it" }; continue }
        val id = p["id"].asText()
        fun reg() = regiaoDeTexto(id, p["texto"].asText(), p["y"].asDouble(), p["x"]?.asDouble() ?: 0.0)
        when (p["acao"].asText()) {
            "adicionar" -> pl.adicionar(reg())
            "editar" -> {
                val rec = pl.editar(id, reg())
                p["recalculadas"]?.let { esp -> val e = esp.map { it.asText() }.toSet()
                    if (rec.toSet() != e) falhas += "recalculadas ${rec.sorted()} != ${e.sorted()}" }
            }
            "remover" -> pl.remover(id)
        }
    }
    return falhas
}

private fun perto(a: Double, b: Double) = abs(a - b) <= max(1e-9 * max(abs(a), abs(b)), 1e-12)

fun verificar(pl: Planilha, v: JsonNode): List<String> {
    val m = pl.motor
    val r = pl.regioes.first { it.id == v["verificar"].asText() }
    val e = r.estado!!
    val f = mutableListOf<String>()
    v["tipo"]?.let { if (e.tipo != it.asText()) f += "tipo ${e.tipo} != ${it.asText()} (${e.msg})" }
    v["livres"]?.let { esp -> val l = esp.map { it.asText() }.sorted(); if (e.livres.sorted() != l) f += "livres ${e.livres} != $l" }
    v["expr"]?.let {
        val esperado = avaliar(Parser.parse(it.asText()), Contexto(emptyMap(), pl))
        val dif = m.ev(F.Simplify(F.Subtract(e.valor!!, esperado)))
        if (!dif.isZero) f += "expr ${e.valor} != ${it.asText()}"
    }
    v["exato"]?.let { if (e.valor.toString() != it.asText()) f += "exato ${e.valor} != ${it.asText()}" }
    v["num"]?.let {
        try { if (!perto(m.dbl(e.valor!!), it.asDouble())) f += "num ${e.valor} != ${it.asDouble()}" }
        catch (x: Exception) { f += "num: não numérico ${e.valor} (${e.tipo}, ${e.msg})" }
    }
    v["lista"]?.let { esp ->
        val val0 = e.valor
        if (val0 == null || !val0.isList) f += "lista: não é lista ($val0)"
        else {
            val got = (1 until (val0 as IAST).size()).map { m.dbl(val0.get(it)) }
            val exp = esp.map { it.asDouble() }
            if (got.size != exp.size || !got.zip(exp).all { (a, b) -> perto(a, b) }) f += "lista $got != $exp"
        }
    }
    v["matriz"]?.let { esp ->   // matriz numérica, por linhas: [[1, 2], [3, 4]]
        val val0 = e.valor
        val got = try { tracolab.nucleo.Matriciais.linhas(val0!!).map { l -> l.map { m.dbl(it) } } } catch (x: Exception) { null }
        val exp = esp.map { l -> l.map { it.asDouble() } }
        if (got == null || got.size != exp.size || got.zip(exp).any { (a, b) -> a.size != b.size || !a.zip(b).all { (x, y) -> perto(x, y) } })
            f += "matriz $got != $exp ($val0)"
    }
    v["tela"]?.let { val t = pl.exibir(r.id); if (t != it.asText()) f += "tela '$t' != '${it.asText()}'" }
    v["tela_comeca"]?.let { val t = pl.exibir(r.id); if (!t.startsWith(it.asText())) f += "tela '$t' não começa com '${it.asText()}'" }
    v["msg_contem"]?.let { if (!(e.msg ?: "").contains(it.asText())) f += "msg '${e.msg}' não contém '${it.asText()}'" }
    v["aviso"]?.let { if (e.aviso != it.asText()) f += "aviso '${e.aviso}' != '${it.asText()}'" }
    return f
}
