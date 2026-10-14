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
    val ls = try { Matriciais.linhas(v) } catch (_: Exception) { return ResultadoTela(LatexSaida.expressao(v, motor)) }
    var unidade = ""
    val celulas: List<List<String>> = if (motor.temLivres(v)) ls.map { l -> l.map { valorSimples(it) } }
    else {
        val textos = try { ls.map { l -> l.map { exibirValor(it, r) } } }
        catch (e: ErroUnidade) { return ResultadoTela(null, e.message) }
        catch (_: Exception) { return ResultadoTela(ls.joinToString("\\\\", "\\begin{pmatrix}", "\\end{pmatrix}") { l -> l.joinToString("&") { valorSimples(it) } }) }
        val unids = textos.flatten().filter { ' ' in it }.map { it.substringAfter(' ') }.toSet()
        val comum = unids.size == 1 && textos.flatten().all { ' ' in it || it == "0" }
        if (comum) unidade = unids.first()
        textos.map { l -> l.map { t ->
            val num = LatexSaida.numero(t.substringBefore(' '))
            val un = t.substringAfter(' ', "")
            if (comum || un.isEmpty()) num else "$num~${LatexSaida.unidade(un)}"
        } }
    }
    return ResultadoTela(pmatrix(resumir(celulas)) + if (unidade.isEmpty()) "" else "\\," + LatexSaida.unidade(unidade))
}

private fun pmatrix(c: List<List<String>>) = c.joinToString("\\\\", "\\begin{pmatrix}", "\\end{pmatrix}") { it.joinToString("&") }

private fun resumir(c: List<List<String>>): List<List<String>> {
    var ls = c
    if (ls[0].size > MAX_COLUNAS) ls = ls.map { it.take(MAX_COLUNAS - 2) + "\\cdots" + it.last() }
    if (ls.size > MAX_LINHAS) {
        val pontos = ls[0].map { if (it == "\\cdots") "\\ddots" else "\\vdots" }
        ls = ls.take(MAX_LINHAS - 2) + listOf(pontos) + listOf(ls.last())
    }
    return ls
}
