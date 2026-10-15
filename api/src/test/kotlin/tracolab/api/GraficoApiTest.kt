package tracolab.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Região de gráfico na API (ADR 0016): amostragem, definições acima, pendência e buracos. */
class GraficoApiTest {
    private val calc = Calculadora()
    private val json = ObjectMapper()

    private fun grafico(id: String, y: Int, series: List<String>, a: String = "0", b: String = "1", n: Int = 100) =
        mapOf("id" to id, "tipo" to "grafico", "series" to series, "a" to a, "b" to b, "n" to n, "x" to 0, "y" to y)

    private fun calcular(vararg regioes: Map<String, Any>): Map<String, JsonNode> =
        json.readTree(calc.calcularJson(json.writeValueAsString(mapOf("modo" to "parcial", "regioes" to regioes.toList()))))["regioes"]
            .associateBy { it["id"].asText() }

    @Test fun curvaComDefinicaoAcimaEUnidades() {
        val r = calcular(
            mapOf("id" to "k", "latex" to "k\\tldef2\\,\\mathrm{N}", "x" to 0, "y" to 10),
            grafico("g", 50, listOf("k\\cdot x"), "0\\,\\mathrm{m}", "4\\,\\mathrm{m}", 5))
        val g = r.getValue("g")
        assertEquals("grafico", g["tipo"].asText())
        val gr = g["grafico"]
        assertEquals("m", gr["xu"].asText())
        assertEquals("J", gr["yu"].asText())   // N · m
        assertEquals(5, gr["x"].size())
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0), gr["series"][0]["y"].map { it.asDouble() })
    }

    @Test fun duasCurvasEPadraoDe100Pontos() {
        val g = calcular(grafico("g", 10, listOf("\\sin(x)", "x^2"), "0", "6", 100)).getValue("g")["grafico"]
        assertEquals(2, g["series"].size())
        assertEquals(100, g["x"].size())
        assertEquals(Math.sin(6.0 * 50 / 99), g["series"][0]["y"][50].asDouble(), 1e-9)
    }

    @Test fun nomeFaltandoAlemDeXFicaPendente() {
        val g = calcular(grafico("g", 10, listOf("q\\cdot x"))).getValue("g")
        assertEquals("parcial", g["tipo"].asText())
        assertEquals(listOf("q"), g["livres"].map { it.asText() })
        assertTrue(!g.has("grafico"))
    }

    @Test fun xDaFolhaNaoInterfereEPolosViramBuraco() {
        val r = calcular(
            mapOf("id" to "x", "latex" to "x\\tldef5", "x" to 0, "y" to 10),
            grafico("g", 50, listOf("\\frac{1}{x}", "\\sqrt{x}"), "-1", "1", 21))
        val s = r.getValue("g")["grafico"]["series"]
        assertTrue(s[0]["y"][10].isNull)           // 1/0
        assertTrue(s[1]["y"][0].isNull)            // raiz de negativo: complexo
        assertTrue(s[1]["y"][15].asDouble() > 0.0)
    }

    @Test fun poloDaTangenteQuebraACurva() {
        val y = calcular(grafico("g", 10, listOf("\\tan(x)"), "0", "3", 300)).getValue("g")["grafico"]["series"][0]["y"]
        val x = (0 until 300).map { 3.0 * it / 299 }
        val polo = x.indexOfFirst { it > Math.PI / 2 }
        assertTrue(y[polo].isNull || y[polo - 1].isNull) { "sem buraco em π/2" }
    }

    @Test fun expressaoInvalidaViraErroSoNela() {
        val r = calcular(grafico("g", 10, listOf("\\frac{")), mapOf("id" to "b", "latex" to "2+2=", "x" to 0, "y" to 90))
        assertEquals("erro_sintaxe", r.getValue("g")["tipo"].asText())
        assertEquals("numerico", r.getValue("b")["tipo"].asText())
    }

    @Test fun duzentosPontosSaemRapido() {
        val t0 = System.nanoTime()
        val g = calcular(grafico("g", 10, listOf("\\sin(x)\\cdot e^{-x/5}+x^2/10"), "0", "20", 2000)).getValue("g")
        assertEquals(2000, g["grafico"]["x"].size())
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 3000)
    }
}
