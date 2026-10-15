package tracolab.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Verificações, textos e por partes na resposta da API (ADR 0013). */
class VerificacaoApiTest {
    private val calc = Calculadora()
    private val json = ObjectMapper()

    private fun calcular(vararg latex: String) = json.readTree(calc.calcularJson(json.writeValueAsString(mapOf("modo" to "parcial",
        "regioes" to latex.mapIndexed { k, l -> mapOf("id" to "r$k", "latex" to l, "x" to 0, "y" to 10 * (k + 1)) }))))["regioes"]
        .associateBy { it["id"].asText() }

    @Test fun verificacaoComLadosNaUnidadeDaFolha() {
        val r = calcular("\\sigma\\tldef150\\,\\mathrm{MPa}", "\\sigma_{adm}\\tldef250\\,\\mathrm{MPa}", "\\sigma\\le\\sigma_{adm}=")
        val v = r.getValue("r2")["verificacao"]
        assertTrue(v["ok"].asBoolean())
        assertEquals(0.6, v["utilizacao"].asDouble(), 1e-12)
        assertEquals("150\\,\\mathrm{MPa}\\le 250\\,\\mathrm{MPa}", v["lados"].asText())
    }

    @Test fun verificacaoPendenteMostraARelacaoNaUnidadeDaFolha() {
        val r = calcular("\\sigma\\tldef150\\,\\mathrm{MPa}", "\\sigma\\le\\sigma_{adm}=")
        val o = r.getValue("r1")
        assertEquals("parcial", o["tipo"].asText())
        assertTrue(o["verificacao"]["ok"].isNull)
        val tela = o["resultado"].asText()
        assertTrue("MPa" in tela && "\\sigma_{adm}" in tela && "kg" !in tela) { tela }
    }

    @Test fun naoVerificacaoNaoTrazCampo() {
        assertFalse(calcular("2+2=").getValue("r0").has("verificacao"))
    }

    @Test fun textoEPorPartesNaTela() {
        val r = calcular("m\\tldef\\text{não atende}", "m=", "\\operatorname{if}\\left(x>0,a,b\\right)=")
        assertEquals("\\text{não atende}", r.getValue("r1")["resultado"].asText())
        val pw = r.getValue("r2")["resultado"].asText()
        assertTrue(pw.startsWith("\\begin{cases}") && "\\text{senão}" in pw && "Piecewise" !in pw) { pw }
        // conta com por partes: cada ramo na unidade da folha (6 kN, não 6000 kg·m/s²)
        val r2 = calcular("y\\tldef\\operatorname{se}\\left(x>0,2\\,\\mathrm{kN},a\\right)", "y\\cdot3=")
        val t2 = r2.getValue("r1")["resultado"].asText()
        assertTrue(t2.startsWith("\\begin{cases}6\\,\\mathrm{kN}") && "kg" !in t2) { t2 }
    }
}
