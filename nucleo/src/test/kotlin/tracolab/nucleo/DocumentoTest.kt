package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.matheclipse.core.expression.F
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela
import tracolab.nucleo.latex.substituicaoTela

/** Folha como documento: definir e mostrar pelo editor e substituição explícita (memorial). */
class DocumentoTest {

    private fun folha(vararg textos: String): Planilha {
        val pl = Planilha("parcial")
        textos.forEachIndexed { k, t -> pl.adicionar(regiaoDeTexto("r$k", t, 10.0 * (k + 1))) }
        return pl
    }

    @Test fun definirEMostrarPeloEditor() {
        val t = LatexEntrada.regiao("R\\tldef\\frac{q\\cdot L}{2}=\\,\\mathrm{kN}")
        assertEquals("R := ((q*L)/(2)) = {kN}", t)
        val pl = folha("q := 12{kN/m}", "L := 6{m}", t)
        assertTrue(pl.regioes[2].mostrar)
        assertEquals("36\\,\\mathrm{kN}", pl.resultadoTela("r2").latex)
    }

    @Test fun substituicaoDoMemorial() {
        val pl = folha("q := 12{kN/m}", "L := 6{m}", "R := ((q*L)/(2)) = {kN}")
        assertEquals("\\frac{12\\,\\mathrm{kN}/\\mathrm{m}\\cdot 6\\,\\mathrm{m}}{2}", pl.substituicaoTela("r2"))
    }

    @Test fun potenciaDeQuantidadeLevaParenteses() {
        val pl = folha("d := 20{mm}", "A := π*d^2/4 = {mm^2}")
        assertEquals("\\frac{\\pi\\cdot \\left(20\\,\\mathrm{mm}\\right)^{2}}{4}", pl.substituicaoTela("r1"))
    }

    @Test fun valorCalculadoApareceComoNaTela() {
        val pl = folha("d := 20{mm}", "A := π*d^2/4 = {mm^2}", "F := 10{kN}", "σ := F/A = {MPa}")
        assertEquals("\\frac{10\\,\\mathrm{kN}}{314{,}159\\,\\mathrm{mm}^{2}}", pl.substituicaoTela("r3"))
    }

    @Test fun substituicaoParcialMantemOQueFalta() {
        val pl = folha("A := 50{mm^2}", "σ := F/A =")
        assertEquals("\\frac{F}{50\\,\\mathrm{mm}^{2}}", pl.substituicaoTela("r1"))
    }

    @Test fun semNomeComValorNaoHaSubstituicao() {
        assertNull(folha("2*3 =").substituicaoTela("r0"))
        assertNull(folha("x := 2", "x =").substituicaoTela("r1"))
        assertNull(folha("a*b =").substituicaoTela("r0"))
    }

    @Test fun negativoLevaParenteses() {
        val pl = folha("a := -3", "b := 2", "b*a =", "5 - a =", "a^2 =")
        assertEquals("2\\cdot \\left(-3\\right)", pl.substituicaoTela("r2"))
        assertEquals("5-\\left(-3\\right)", pl.substituicaoTela("r3"))
        assertEquals("\\left(-3\\right)^{2}", pl.substituicaoTela("r4"))
    }

    @Test fun usaADefinicaoVigenteNaPosicao() {
        val pl = folha("x := 1", "y := x + 1 =", "x := 5", "z := x*2 =")
        assertEquals("1+1", pl.substituicaoTela("r1"))
        assertEquals("5\\cdot 2", pl.substituicaoTela("r3"))
    }

    @Test fun redefinicaoUsaOValorAnterior() {
        val pl = folha("x := 1", "x := x + 1 =")
        assertEquals("1+1", pl.substituicaoTela("r1"))
    }

    @Test fun faixaEFuncaoFicamComoNome() {
        val pl = folha("i := 1..3", "k := 2", "f(t) := t^2", "f(k)*i =")
        assertEquals("f\\left(2\\right)\\cdot i", pl.substituicaoTela("r3"))
    }

    @Test fun embutidosConhecidos() {
        val pl = folha("x := 4", "sqrt(x) + sin(x) =")
        assertEquals("\\sqrt{4}+\\sin\\left(4\\right)", pl.substituicaoTela("r1"))
    }

    @Test fun nomeLigadoDeEspecialNaoESubstituido() {
        Embutidos.especiais["zz_doc_soma"] = Especial(ligados = setOf(1)) { _, _ -> F.C0 }
        try {
            val pl = folha("i := 7", "n := 3", "zz_doc_soma(i^2, i, 1, n) =")
            assertEquals("\\operatorname{zz_doc_soma}\\left(i^{2};\\,i;\\,1;\\,3\\right)", pl.substituicaoTela("r2"))
        } finally { Embutidos.especiais.remove("zz_doc_soma") }
    }

    @Test fun unidadesComoOTecladoInsere() {
        // o teclado de unidades insere \mathrm{kN}/\mathrm{m} e \mathrm{mm}^2 separados
        val pl = folha(LatexEntrada.regiao("q\\tldef12\\,\\mathrm{kN}/\\mathrm{m}"),
            LatexEntrada.regiao("A\\tldef50\\,\\mathrm{mm}^2"), "x := q*A =")
        assertEquals("12\\,\\mathrm{kN}/\\mathrm{m}\\cdot 50\\,\\mathrm{mm}^{2}", pl.substituicaoTela("r2"))
    }
}
