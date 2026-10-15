package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada

/** Entrada do editor para textos, lógica, se e por partes (ADR 0013). */
class LatexLogicaTest {

    private fun t(latex: String) = LatexEntrada.regiao(latex)

    /** Calcula uma folha de LaTeX (uma região por linha) e devolve a exibição da última. */
    private fun folha(vararg latex: String): Pair<Estado, String> {
        val pl = Planilha()
        latex.forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", t(l), 10.0 * (k + 1))) }
        val id = "r${latex.size - 1}"
        return pl.regioes.last().estado!! to pl.exibir(id)
    }

    @Test fun textoComEspacos() = assertEquals("m := \"não atende\"", t("m\\tldef\\text{não atende}").replace(' ', ' '))
    @Test fun textoComAspasDoEditor() {
        assertEquals("\"OK\"", folha("m\\tldef\\text{''OK''}", "m=").second)
        assertEquals("\"OK\"", folha("m\\tldef\"OK\"", "m=").second)
        assertEquals("\"a b\"", folha("m\\tldef\\text{\"a b\"}", "m=").second)
        assertEquals("\"50%\"", folha("m\\tldef\\text{50\\%}", "m=").second)
    }
    @Test fun aspasSemFechar() { assertThrows(LatexEntrada.ErroLatex::class.java) { t("m\\tldef\"OK") } }

    @Test fun logicos() {
        assertEquals("a > 0 ∧ b <= 1 ∨ ¬ c >= 2", t("a>0\\land b\\le1\\lor\\lnot c\\ge2"))
        assertEquals("1", folha("1>0\\land\\neg\\left(2<1\\right)=").second)
        assertEquals("0", folha("1>0\\oplus2>1=").second)
    }

    @Test fun seDoTeclado() {
        assertEquals("2", folha("x\\tldef-1", "\\operatorname{se}\\left(x>0,1,2\\right)=").second)
        // o editor escreve \operatorname{\mathrm{se}} quando a tecla insere \operatorname{se}
        assertEquals("1", folha("x\\tldef3", "\\operatorname{\\mathrm{se}}\\left(x>0,1,2\\right)=").second)
    }

    @Test fun porPartes() {
        assertEquals("g(t) := cases(0, t < 0, t, t <= 1, 1)",
            t("g\\left(t\\right)\\tldef\\begin{cases}0 & t<0\\\\ t & t\\le1\\\\ 1 & \\text{senão}\\end{cases}"))
        val (_, tela) = folha("g\\left(t\\right)\\tldef\\begin{cases}0 & t<0\\\\ t & t\\le1\\\\ 1 & \\text{otherwise}\\end{cases}", "g\\left(5\\right)=")
        assertEquals("1", tela)
        // condição com "=" é comparação; última linha sem condição é o senão
        assertEquals("cases(1, x == 0, 2)", t("\\begin{cases}1 & x=0\\\\ 2 & \\end{cases}"))
        assertThrows(LatexEntrada.ErroLatex::class.java) { t("\\begin{cases}1 & \\text{senão}\\\\ 2 & x>0\\end{cases}") }
    }

    @Test fun verificacaoDoEditor() {
        assertEquals("σ <= σ_adm =", t("\\sigma\\le\\sigma_{adm}="))
        val (e, _) = folha("\\sigma\\tldef150\\,\\mathrm{MPa}", "\\sigma_{adm}\\tldef250\\,\\mathrm{MPa}", "\\sigma\\le\\sigma_{adm}=")
        assertEquals(true, e.verificacao?.ok)
        assertEquals(0.6, e.verificacao!!.utilizacao!!, 1e-12)
    }

    @Test fun textoAindaNaoEraNome() {
        // \operatorname e \mathit continuam nomes; só \text é texto
        assertEquals("Ftot := 2", t("\\mathit{Ftot}\\tldef2"))
        assertEquals("f(x) =", t("\\operatorname{f}\\left(x\\right)="))
    }
}
