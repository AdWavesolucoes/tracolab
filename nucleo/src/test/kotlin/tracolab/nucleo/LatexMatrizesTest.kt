package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela

/** Matrizes no editor de fórmulas: entrada (LaTeX → núcleo) e saída (núcleo → LaTeX). ADR 0010. */
class LatexMatrizesTest {

    private fun t(latex: String) = LatexEntrada.regiao(latex)

    @Test fun transposta() {
        assertEquals("M^(T) =", t("M^{T}="))
        assertEquals("M^(T) =", t("M^{\\top}="))
    }
    @Test fun inversa() = assertEquals("M^(- 1) =", t("M^{-1}="))
    @Test fun barrasViramAbs() {
        assertEquals("abs(M) =", t("\\left|M\\right|="))
        assertEquals("abs(x - 1) =", t("|x-1|="))
        assertEquals("abs(v) =", t("\\left\\lvert v\\right\\rvert="))
        assertEquals("abs(a - abs(b)) =", t("\\left|a-\\left|b\\right|\\right|="))
    }
    @Test fun determinanteComBarrasDeMatriz() = assertEquals("det([a, b; c, d]) =", t("\\begin{vmatrix}a & b\\\\ c & d\\end{vmatrix}="))
    @Test fun det() = assertEquals("det(M) =", t("\\det M="))
    @Test fun coluna() = assertEquals("col(M, 1) =", t("M^{\\langle 1\\rangle}="))
    @Test fun vetorizacao() = assertEquals("vec(v*w) =", t("\\overrightarrow{v\\cdot w}="))
    @Test fun produtoVetorial() = assertEquals("a × b =", t("a\\times b="))
    @Test fun indiceDuplo() = assertEquals("M[i, j] := i + j", t("M\\left\\lbrack i,j\\right\\rbrack\\coloneq i+j"))
    @Test fun indiceComVirgulaDecimalDoPtBr() = assertEquals("M[0, 1] =", t("M\\left\\lbrack0{,}1\\right\\rbrack="))
    @Test fun vetorDigitadoComPontoEVirgula() = assertEquals("v := [1; 2.5; 3]", t("v\\tldef\\left\\lbrack1;2{,}5;3\\right\\rbrack"))

    private fun tela(vararg regioes: String): String? {
        val pl = Planilha()
        regioes.forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", LatexEntrada.regiao(l), 10.0 * (k + 1))) }
        return pl.resultadoTela("r${regioes.size - 1}").latex
    }

    @Test fun saidaDeMatriz() = assertEquals("\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}", tela("\\begin{pmatrix}1 & 2\\\\ 3 & 4\\end{pmatrix}="))
    @Test fun saidaComUnidadeFatorada() =
        assertEquals("\\begin{pmatrix}1\\\\2{,}5\\end{pmatrix}\\,\\mathrm{kN}", tela("F\\tldef\\begin{pmatrix}1\\\\ 2{,}5\\end{pmatrix}\\mathrm{kN}", "F=\\mathrm{kN}"))
    @Test fun saidaComUnidadePorElemento() =
        assertEquals("\\begin{pmatrix}2~\\mathrm{m}\\\\3~\\mathrm{s}\\end{pmatrix}", tela("\\begin{pmatrix}2\\mathrm{m}\\\\ 3\\mathrm{s}\\end{pmatrix}="))
    @Test fun saidaParcial() = assertEquals("\\begin{pmatrix}k&0\\\\0&1\\end{pmatrix}", tela("\\begin{pmatrix}k & 0\\\\ 0 & 1\\end{pmatrix}="))
    @Test fun matrizGrandeResumida() {
        val tex = tela("i\\tldef0..19", "v\\left\\lbrack i\\right\\rbrack\\tldef i", "v=")!!
        assertEquals(12, tex.split("\\\\").size)
        assertEquals(true, "\\vdots" in tex && tex.contains("19"))
    }
    @Test fun indicePendente() = assertEquals("2 \\cdot {v}_{1}", tela("v\\left\\lbrack1\\right\\rbrack\\cdot2="))
}
