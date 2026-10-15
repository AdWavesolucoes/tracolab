package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela

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

    private fun folha(vararg linhas: String): Planilha {
        val pl = Planilha("parcial")
        linhas.forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", l, 10.0 * (k + 1))) }
        return pl
    }

    @Test fun numeroNoFormato() {
        fun l(x: Double, f: Formato = Formato()) = formatarNumero(x, f).latex(f)
        assertEquals("12\\thinspace 345{,}6", l(12345.6))
        assertEquals("1234{,}5", l(1234.5))                       // 4 algarismos: sem separador
        assertEquals("1{,}5\\times10^{6}", l(1.5e6))
        assertEquals("-0{,}000314159", l(-0.000314159))
        assertEquals("1{,}23\\times10^{-5}", l(1.23e-5))
        assertEquals("2{,}35", l(2.345, Formato(casas = 2)))      // comercial: 2,345 → 2,35
        assertEquals("12{,}5\\times10^{3}", l(12500.0, Formato(engenharia = true).comLimite(3)))
        assertEquals("0", l(0.0))
    }

    @Test fun pendenteSaiComoDivisaoNasUnidadesDaFolha() {
        val pl = folha("d := 20{mm}", "σ := F/(π*d^2/4)", "σ =")
        assertEquals("\\frac{F}{314{,}159\\,\\mathrm{mm}^{2}}", pl.resultadoTela("r2").latex)
    }

    @Test fun decimalPendenteNaoViraFracao() {
        val pl = folha("0.000012*L =", "0.5*L =")
        assertEquals("1{,}2\\times10^{-5}\\cdotL", pl.resultadoTela("r0").latex!!.replace(" ", ""))
        assertEquals("0{,}5\\cdotL", pl.resultadoTela("r1").latex!!.replace(" ", ""))
    }

    @Test fun vetorComUnidadeComum() {
        val pl = folha("v := [1500{mm}; 2.5{m}]", "v =")
        assertEquals("\\begin{pmatrix}1{,}5\\\\2{,}5\\end{pmatrix}\\,\\mathrm{m}", pl.resultadoTela("r1").latex)
    }

    @Test fun unidadePedidaCompostaSemAsterisco() {
        val pl = folha("M := 36{kN*m}", "M = {kN*m}")
        assertEquals("36\\,\\mathrm{kN}\\cdot \\mathrm{m}", pl.resultadoTela("r1").latex)
    }
}
