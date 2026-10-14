package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.matheclipse.core.expression.F
import tracolab.nucleo.latex.LatexEntrada

/** Pontos de extensão do núcleo: sintaxe reservada (matriz, texto), ambientes LaTeX e registro de embutidos. */
class CosturasTest {

    @Test fun matrizNaSintaxeDeTexto() {
        val n = Parser.parse("[1, 2; a, 4]") as No.Matriz
        assertEquals(2, n.linhas.size)
        assertEquals(listOf(No.Num("1"), No.Num("2")), n.linhas[0])
        assertEquals(No.Id("a"), n.linhas[1][0])
        assertEquals(listOf("a"), usados(n))
        assertThrows(ErroSintaxe::class.java) { Parser.parse("[1, 2; 3]") }
        assertThrows(ErroSintaxe::class.java) { Parser.parse("[]") }
    }

    @Test fun indiceContinuaSendoPosfixo() {
        assertTrue(Parser.parse("v[i]") is No.Indice)
        assertTrue(Parser.parse("[1; 2][0]") is No.Indice)
    }

    @Test fun textoEntreAspas() {
        assertEquals(No.Texto("OK"), Parser.parse("\"OK\""))
        assertThrows(ErroSintaxe::class.java) { Parser.parse("\"sem fim") }
    }

    @Test fun matrizDoEditor() {
        assertEquals("M := [1, 2; 3, 4]", LatexEntrada.regiao("M\\coloneq\\begin{pmatrix}1 & 2\\\\ 3 & 4\\end{pmatrix}"))
        assertEquals("v := [a; 2*b]", LatexEntrada.regiao("v\\coloneq\\begin{bmatrix}a\\\\ 2b\\end{bmatrix}"))
        assertEquals("[1, x] =", LatexEntrada.regiao("\\begin{pmatrix}1 & x\\end{pmatrix}="))
        assertThrows(LatexEntrada.ErroLatex::class.java) { LatexEntrada.regiao("\\begin{pmatrix}1 & \\end{pmatrix}") }
        assertThrows(LatexEntrada.ErroLatex::class.java) { LatexEntrada.regiao("\\begin{foo}1\\end{foo}") }
    }

    @Test fun matrizAindaNaoAvaliadaENaoSuportada() {
        val pl = Planilha()
        pl.adicionar(regiaoDeTexto("m", "M := [1, 2; 3, 4]", 10.0))
        assertEquals("nao_suportado", pl.regioes[0].estado!!.tipo)
    }

    @Test fun nomeLigadoDeEspecialNaoEUsado() {
        Embutidos.especiais["zz_teste_soma"] = Especial(ligados = setOf(1)) { _, _ -> F.C0 }
        try {
            assertEquals(setOf("zz_teste_soma", "k", "n"), usados(Parser.parse("zz_teste_soma(i^2 + k, i, 1, n)")).toSet())
            assertEquals(setOf("zz_teste_soma", "i"), usados(Parser.parse("zz_teste_soma(i, i, 0, 3) + i")).toSet())  // i fora do operador é usado
        } finally { Embutidos.especiais.remove("zz_teste_soma") }
    }

    @Test fun embutidoDuplicadoEErroDeIntegracao() {
        assertThrows(IllegalStateException::class.java) { Embutidos.funcao("sin") { a, _ -> a[0] } }
    }

    @Test fun aridadeDosElementares() {
        val pl = Planilha()
        pl.adicionar(regiaoDeTexto("a", "sin(1, 2) =", 10.0))
        assertEquals("erro", pl.regioes[0].estado!!.tipo)
        assertTrue(pl.regioes[0].estado!!.msg!!.contains("sin"))
    }
}
