package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada

/** Entrada do editor para resolver, raiz e raizes (ADR 0012): `=` dentro de resolver(...) é a equação. */
class LatexResolverTest {

    private fun t(latex: String) = LatexEntrada.regiao(latex)

    private fun folha(vararg latex: String): Pair<Estado, String> {
        val pl = Planilha()
        latex.forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", t(l), 10.0 * (k + 1))) }
        return pl.regioes.last().estado!! to pl.exibir("r${latex.size - 1}")
    }

    @Test fun igualDentroDoResolverViraEquacao() {
        assertEquals("d := resolver(σ_adm == ((F)/(π*d^(2) / 4)), d)",
            t("d\\coloneq\\operatorname{resolver}\\left(\\sigma_{adm}=\\frac{F}{\\pi d^2/4},d\\right)"))
        assertEquals("resolver(x^(2) == 4, x) =", t("\\operatorname{resolver}\\left(x^2=4,x\\right)="))
        assertEquals("resolver(x^(2) == 4, x, - 1) =", t("\\operatorname{resolver}(x^2=4,x,-1)="))
        assertEquals("solve(3*x == 12, x) =", t("\\operatorname{solve}\\left(3x=12,x\\right)="))
    }

    @Test fun sistemaERaiz() {
        assertEquals("resolver([x + y == 3; x - y == 1], [x; y]) =",
            t("\\operatorname{resolver}\\left(\\begin{bmatrix}x+y=3\\\\ x-y=1\\end{bmatrix},\\begin{bmatrix}x\\\\ y\\end{bmatrix}\\right)="))
        assertEquals("raiz(x^(3) - 2*x - 5, x, 2, 3) =", t("\\operatorname{raiz}\\left(x^3-2x-5,x,2,3\\right)="))
    }

    @Test fun dimensionamentoPeloEditor() {
        val (e, tela) = folha(
            "\\sigma_{adm}\\coloneq200\\mathrm{MPa}",
            "F\\coloneq50\\mathrm{kN}",
            "d\\coloneq\\operatorname{resolver}\\left(\\sigma_{adm}=\\frac{F}{\\pi d^2/4},d\\right)",
            "d=\\mathrm{mm}",
        )
        assertEquals("numerico", e.tipo)
        assertEquals("17.8412 mm", tela)
        val (p, _) = folha("d\\coloneq\\operatorname{resolver}\\left(\\sigma_{adm}=\\frac{F}{\\pi d^2/4},d\\right)")
        assertEquals(setOf("F", "σ_adm"), p.livres.toSet())
    }
}
