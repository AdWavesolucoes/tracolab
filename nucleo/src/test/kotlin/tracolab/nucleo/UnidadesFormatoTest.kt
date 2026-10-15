package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada

/** Unidades da prática brasileira, temperaturas e exibição nas unidades da folha (ADR 0014). */
class UnidadesFormatoTest {
    private fun t(latex: String) = LatexEntrada.regiao(latex)

    @Test fun grauSeguidoDeCEhTemperatura() {
        assertEquals("T := 20*{°C}", t("T\\coloneq20^{\\circ}C"))
        assertEquals("T := 68*{°F}", t("T\\coloneq68^{\\circ}F"))
        assertEquals("T := 20*{°C}", t("T\\coloneq20\\,\\mathrm{°C}"))
    }

    @Test fun grauSeguidoDeNomeContinuaAngulo() {
        assertEquals("sin(90*{deg}) =", t("\\sin\\left(90^{\\circ}\\right)="))
        assertEquals("a := 30*{deg}*Cx", t("a\\coloneq30^{\\circ}Cx"))
    }
}
