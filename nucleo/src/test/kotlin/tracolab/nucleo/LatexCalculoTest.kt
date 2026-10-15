package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada

/** Entrada do editor para Σ, Π, ∫ e d/dx (ADR 0011). */
class LatexCalculoTest {

    private fun t(latex: String) = LatexEntrada.regiao(latex)

    /** Calcula uma folha de LaTeX (uma região por linha) e devolve o estado e a exibição da última. */
    private fun folha(vararg latex: String): Pair<Estado, String> {
        val pl = Planilha()
        latex.forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", t(l), 10.0 * (k + 1))) }
        return pl.regioes.last().estado!! to pl.exibir("r${latex.size - 1}")
    }

    @Test fun somatorio() {
        assertEquals("sum(i^(2), i, 1, n) =", t("\\sum_{i=1}^{n}i^2="))
        assertEquals("prod(k, k, 1, 5) =", t("\\prod_{k=1}^{5}k="))
        assertEquals("sum(2*i, i) =", t("\\sum_{i}2i="))           // sem limites: a faixa i
        assertEquals("sum(v) =", t("\\sum v="))
        assertEquals("30", folha("\\sum_{i=1}^{4}i^2=").second)
    }

    @Test fun somandoVaiAteOMaisDeFora() {
        assertEquals("sum(i, i, 1, 3) + 1 =", t("\\sum_{i=1}^{3}i+1="))
        assertEquals("sum((i + 1), i, 1, 3) =", t("\\sum_{i=1}^{3}\\left(i+1\\right)="))
        assertEquals("sum(i*2, i, 1, 3) =", t("\\sum_{i=1}^{3}i\\cdot2="))
        assertEquals("(sum(i, i, 1, 3)) + 1 =", t("\\left(\\sum_{i=1}^{3}i\\right)+1="))
        assertEquals("2*sum(i, i, 1, 3) =", t("2\\sum_{i=1}^{3}i="))
        assertEquals("7", folha("\\sum_{i=1}^{3}i+1=").second)
    }

    @Test fun integral() {
        assertEquals("int(q*x, x, 0, L) =", t("\\int_{0}^{L}q\\cdot x\\,dx="))
        assertEquals("int(q*x, x, 0, L) =", t("\\int_0^Lq\\cdot x\\,\\mathrm{d}x="))
        assertEquals("int(x^(2), x, 0, 3) =", t("\\int_{0}^{3}x^2dx="))
        assertEquals("int(x, x, 0, 1) =", t("\\int\\limits_{0}^{1}x\\,\\differentialD x="))
        assertEquals("int(sin(θ), θ, 0, π) =", t("\\int_0^{\\pi}\\sin\\theta\\,d\\theta="))
        assertEquals("int(2*x, x) ->", t("\\int2x\\,dx\\to"))
        assertEquals("int(x, x, 0, 1) + 1 =", t("\\int_0^1x\\,dx+1="))
        assertEquals("9", folha("\\int_{0}^{3}x^2\\,dx=").second)
        assertThrows(LatexEntrada.ErroLatex::class.java) { t("\\int_0^1x=") }
    }

    @Test fun integralComUnidades() {
        val (e, _) = folha("q\\coloneq10\\mathrm{kN}/\\mathrm{m}", "L\\coloneq2\\mathrm{m}", "\\int_{0}^{L}q\\,\\mathrm{d}x=\\mathrm{kN}")
        assertEquals("numerico", e.tipo)
        val (p, _) = folha("\\int_{0}^{L}q\\cdot x\\,dx=")
        assertEquals(setOf("q", "L"), p.livres.toSet())
    }

    @Test fun derivada() {
        assertEquals("deriv(x^(3), x) =", t("\\frac{d}{dx}x^3="))
        assertEquals("deriv(x^(3), x) =", t("\\frac{\\mathrm{d}}{\\mathrm{d}x}x^3="))
        assertEquals("deriv(x^(3), x, 2) =", t("\\frac{d^2}{dx^2}x^3="))
        assertEquals("deriv(x^(2), x) + 1 =", t("\\frac{d}{dx}x^2+1="))
        assertEquals("((d)/(y)) =", t("\\frac{d}{y}="))   // d/y é divisão comum
        assertEquals("12", folha("x\\coloneq2", "\\frac{d}{dx}x^3=").second)
    }
}
