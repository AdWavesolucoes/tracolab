package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada

class FuncoesTest {
    private fun t(latex: String) = LatexEntrada.regiao(latex)

    @Test fun fatorialSimples() = assertEquals("5 ! =", t("5!="))
    @Test fun fatorialDeParenteses() = assertEquals("(n + 1) ! =", t("\\left(n+1\\right)!="))
    @Test fun fatorialNaoEhDiferente() = assertEquals("a != b", t("a\\ne b"))
    @Test fun secanteSemParenteses() = assertEquals("sec(x) =", t("\\sec x="))
}
