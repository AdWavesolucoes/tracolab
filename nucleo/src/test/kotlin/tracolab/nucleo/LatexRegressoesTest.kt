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

    /**
     * Fuzz: LaTeX qualquer (pedaços do que o editor produz, em ordem aleatória) só pode virar
     * texto ou dar ErroLatex; o texto só pode virar região ou dar ErroSintaxe. Qualquer outra
     * exceção é bug (o servidor responderia "erro interno" para a região).
     */
    @Test fun fuzzDoConversor() {
        val pedacos = listOf("x", "2", "{,}", "5", "\\frac{", "}{", "}", "{", "\\sqrt{", "\\sqrt[3]{", "^{", "_{", "^2", "_1",
            "\\left(", "\\right)", "(", ")", "+", "-", "\\cdot", "\\times", "=", "\\tldef", "\\coloneq", "\\equiv", "\\to",
            "\\mathrm{mm}", "\\mathrm{kN}", "\\,", "\\sin", "\\pi", "\\alpha", "\\lbrack", "\\rbrack", "[", "]", "|",
            "\\begin{pmatrix}", "&", "\\\\", "\\end{pmatrix}", ",", ";", "<", "\\le", "\\ne", "\\placeholder{}", "\\operatorname{f}",
            "\\text{a}", "\\%", "^{\\circ}", "e", "10^{-3}", ":", "!", "'", "\\textcolor{#2748C9}{", "\\mathrm{m}^2")
        val rnd = kotlin.random.Random(3)
        val falhas = mutableListOf<String>()
        repeat(4000) {
            val latex = (1..rnd.nextInt(1, 9)).joinToString("") { pedacos.random(rnd) }
            val texto = try { LatexEntrada.regiao(latex) } catch (e: LatexEntrada.ErroLatex) { return@repeat } catch (e: Exception) {
                falhas += "LaTeX '$latex': ${e.javaClass.simpleName} ${e.message}"; return@repeat
            }
            try { regiaoDeTexto("r", texto, 0.0) } catch (e: ErroSintaxe) { } catch (e: Exception) {
                falhas += "texto '$texto' (de '$latex'): ${e.javaClass.simpleName} ${e.message}"
            }
        }
        assertTrue(falhas.isEmpty()) { falhas.distinct().take(25).joinToString("\n") }
    }
}
