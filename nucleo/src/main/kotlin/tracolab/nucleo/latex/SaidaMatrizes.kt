package tracolab.nucleo.latex

import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Matriciais
import tracolab.nucleo.Planilha
import tracolab.nucleo.Regiao

/** Matrizes maiores que isso aparecem resumidas: as primeiras linhas/colunas, ⋮/⋯ e a última. */
private const val MAX_LINHAS = 12
private const val MAX_COLUNAS = 8

/**
 * Vetor ou matriz na tela (ADR 0010): `pmatrix` com linhas e colunas. Se todos os elementos têm
 * a mesma unidade, ela sai uma vez só, depois da matriz (vira o chip de unidade do resultado);
 * senão, cada elemento leva a sua. Elementos com nome faltando saem como expressão.
 */
internal fun Planilha.matrizTela(v: IExpr, r: Regiao): ResultadoTela {
    val todas = try { Matriciais.linhas(v) } catch (_: Exception) { return ResultadoTela(LatexSaida.expressao(v, motor)) }
    // resume antes de formatar: um vetor de 200 mil valores não passa elemento por elemento
    val iLin = indices(todas.size, MAX_LINHAS); val iCol = indices(todas[0].size, MAX_COLUNAS)
    val ls = iLin.map { i -> iCol.map { j -> todas[i][j] } }
    val aviso = when {
        iLin.size == todas.size && iCol.size == todas[0].size -> null
        todas[0].size == 1 -> "Vetor com ${todas.size} valores: mostrando os ${MAX_LINHAS - 2} primeiros e o último."
        else -> "Matriz ${todas.size}×${todas[0].size}: mostrando o começo e o fim."
    }
    var unidade = ""
    val celulas: List<List<String>> = if (ls.any { l -> l.any { motor.temLivres(it) } }) ls.map { l -> l.map { valorSimples(it) } }
    else {
        // ADR 0014: unidade comum escolhida pelas unidades da folha, números no formato da folha
        val (cel, comum) = try { celulasTela(ls, r, formatoTela(r.id)) }
        catch (e: ErroUnidade) { return ResultadoTela(null, e.message) }
        catch (e: Exception) {
            if (motor.interrompido(e)) throw e
            return ResultadoTela(pmatrix(pontos(ls.map { l -> l.map { valorSimples(it) } }, iLin, iCol)), aviso)
        }
        if (comum != null) unidade = comum
        cel
    }
    return ResultadoTela(pmatrix(pontos(celulas, iLin, iCol)) + if (unidade.isEmpty()) "" else "\\,$unidade", aviso)
}

/** Índices mostrados: todos, ou os primeiros e o último. */
private fun indices(n: Int, max: Int): List<Int> = if (n <= max) (0 until n).toList() else (0 until max - 2).toList() + (n - 1)

private fun pmatrix(c: List<List<String>>) = c.joinToString("\\\\", "\\begin{pmatrix}", "\\end{pmatrix}") { it.joinToString("&") }

/** Põe ⋯ antes da última coluna e ⋮ antes da última linha quando a matriz foi resumida. */
private fun pontos(c: List<List<String>>, iLin: List<Int>, iCol: List<Int>): List<List<String>> {
    var ls = c
    if (iCol.size > 1 && iCol.last() != iCol[iCol.size - 2] + 1) ls = ls.map { it.dropLast(1) + "\\cdots" + it.last() }
    if (iLin.size > 1 && iLin.last() != iLin[iLin.size - 2] + 1) {
        val p = ls[0].map { if (it == "\\cdots") "\\ddots" else "\\vdots" }
        ls = ls.dropLast(1) + listOf(p) + listOf(ls.last())
    }
    return ls
}
