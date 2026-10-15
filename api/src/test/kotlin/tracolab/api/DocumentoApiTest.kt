package tracolab.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Definir e mostrar e substituição explícita pela API da página. */
class DocumentoApiTest {
    private val calc = Calculadora()
    private val json = ObjectMapper()

    private fun calcular(vararg regioes: Map<String, Any>) = json.readTree(calc.calcularJson(
        json.writeValueAsString(mapOf("modo" to "parcial", "regioes" to regioes.toList()))))["regioes"]
        .associateBy { it["id"].asText() }

    private fun r(id: String, latex: String, y: Int, substituir: Boolean = false) =
        mapOf("id" to id, "latex" to latex, "x" to 40, "y" to y, "substituir" to substituir)

    @Test fun definirEMostrar() {
        val o = calcular(r("q", "q\\tldef12\\,\\mathrm{kN}/\\mathrm{m}", 10), r("L", "L\\tldef6\\,\\mathrm{m}", 20),
            r("R", "R\\tldef\\frac{q\\cdot L}{2}=\\,\\mathrm{kN}", 30))
        val reg = o.getValue("R")
        assertEquals("definicao", reg["papel"].asText())
        assertTrue(reg["mostra"].asBoolean())
        assertEquals("36\\,\\mathrm{kN}", reg["resultado"].asText())
        assertFalse(o.getValue("q").has("mostra"))
        assertFalse(reg.has("substituicao"))   // só quando pedida
    }

    @Test fun substituicaoQuandoPedida() {
        val o = calcular(r("q", "q\\tldef12\\,\\mathrm{kN}/\\mathrm{m}", 10), r("L", "L\\tldef6\\,\\mathrm{m}", 20),
            r("R", "R\\tldef\\frac{q\\cdot L}{2}=\\,\\mathrm{kN}", 30, substituir = true),
            r("s", "q\\cdot L=", 40, substituir = true), r("d", "q\\tldef1", 50, substituir = true))
        assertEquals("\\frac{12\\,\\mathrm{kN}/\\mathrm{m}\\cdot 6\\,\\mathrm{m}}{2}", o.getValue("R")["substituicao"].asText())
        assertEquals("12\\,\\mathrm{kN}/\\mathrm{m}\\cdot 6\\,\\mathrm{m}", o.getValue("s")["substituicao"].asText())
        assertFalse(o.getValue("d").has("substituicao"))   // definição sem "=" não mostra valor
    }

    @Test fun funcaoComIgualAvisa() {
        val o = calcular(r("f", "f\\left(t\\right)\\tldef t^2=", 10))
        assertEquals("Uma função não tem valor para mostrar.", o.getValue("f")["aviso"].asText())
    }
}
