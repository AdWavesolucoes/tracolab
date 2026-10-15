package tracolab.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Unidade pedida que não combina: dizer o que falta e qual número está sem unidade (relato de 15/10: F := 60). */
class DiagnosticoUnidadeTest {
    private val calc = Calculadora()
    private val json = ObjectMapper()

    private fun calcular(vararg regioes: Pair<String, String>) = json.readTree(calc.calcularJson(json.writeValueAsString(mapOf(
        "modo" to "parcial",
        "regioes" to regioes.mapIndexed { k, (id, latex) -> mapOf("id" to id, "latex" to latex, "x" to 40, "y" to 60 * (k + 1)) }))))["regioes"]
        .associateBy { it["id"].asText() }

    private val base = arrayOf("d" to "d\\tldef20\\,\\mathrm{mm}", "A" to "A\\tldef\\frac{\\pi d^2}{4}")

    @Test fun forcaSemUnidadeSugereKn() {
        val r = calcular(*base, "F" to "F\\tldef60", "s" to "\\sigma\\tldef\\frac{F}{A}", "e" to "\\sigma=\\,\\mathrm{MPa}")
        val aviso = r.getValue("e")["aviso"].asText()
        assertTrue("falta uma força" in aviso && "Sem unidade: F" in aviso) { aviso }
        val s = r.getValue("e")["sugestoesUnidade"]
        assertEquals(1, s.size())
        assertEquals("F", s[0]["nome"].asText()); assertEquals("F", s[0]["regiao"].asText()); assertEquals("kN", s[0]["unidade"].asText())
        // com a unidade acrescentada, o resultado sai em MPa
        val ok = calcular(*base, "F" to "F\\tldef60${s[0]["latex"].asText()}", "s" to "\\sigma\\tldef\\frac{F}{A}", "e" to "\\sigma=\\,\\mathrm{MPa}")
        assertEquals("190{,}986", ok.getValue("e")["resultado"].asText().substringBefore("\\,"))
    }

    @Test fun numeroNoDenominadorNaoESugerido() {
        val r = calcular("F" to "F\\tldef10\\,\\mathrm{kN}", "A" to "A\\tldef50", "s" to "\\sigma\\tldef\\frac{F}{A}", "e" to "\\sigma=\\,\\mathrm{MPa}")
        assertTrue("para MPa falta" in r.getValue("e")["aviso"].asText())
        assertTrue(r.getValue("e")["sugestoesUnidade"] == null)
    }

    @Test fun semNumeroPuroSoExplica() {
        val r = calcular("L" to "L\\tldef2\\,\\mathrm{m}", "e" to "L=\\,\\mathrm{s}")
        val aviso = r.getValue("e")["aviso"].asText()
        assertTrue(aviso.startsWith("L está em m") && "para s falta" in aviso) { aviso }
        assertTrue(r.getValue("e")["sugestoesUnidade"] == null)
    }
}
