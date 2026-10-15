package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela

/** Regressões dos conversores LaTeX (área bugs-nucleo). */
class LatexRegressoesTest {

    /** Coeficiente grande num resultado pendente aparecia como 5*10^{6}, com asterisco na tela. */
    @Test fun potenciaDeDezSemAsterisco() {
        val pl = Planilha()
        listOf("a := 5{MPa}", "a*L =", "b := 1234567.0", "b*L =").forEachIndexed { k, t -> pl.adicionar(regiaoDeTexto("r$k", t, 10.0 * (k + 1))) }
        for (id in listOf("r1", "r3")) {
            val tex = pl.resultadoTela(id).latex!!
            assertTrue("*" !in tex && "\\times10^{6}" in tex) { tex }
            // e o que aparece na tela pode ser digitado de volta
            assertTrue(LatexEntrada.regiao("$tex=").endsWith("=")) { tex }
        }
    }

    /** a = b = (comparação avaliada) dava erro de sintaxe: o '=' do meio não virava comparação. */
    @Test fun comparacaoAvaliada() {
        assertTrue(LatexEntrada.regiao("a=b=") == "a == b =") { LatexEntrada.regiao("a=b=") }
        val pl = Planilha()
        pl.adicionar(regiaoDeTexto("c", LatexEntrada.regiao("2+2=4="), 10.0))
        assertTrue(pl.exibir("c") == "1") { pl.exibir("c") }
    }
}
