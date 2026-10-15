package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr

// ------------------------------------------------------------------ gráfico X-Y (ADR 0016)

/** Gráfico pedido: [exprs] em `x`, no intervalo [[a], [b]], com [n] pontos. */
class EspecGrafico(val exprs: List<No>, val a: No, val b: No, val n: Int = Amostragem.PADRAO)

/**
 * Pontos amostrados. `y` de cada série tem um item por `x`; `null` é buraco na curva (NaN, infinito,
 * complexo, descontinuidade). Os valores vão na unidade do SI indicada em [xu] e [yu] ("" = sem unidade).
 */
class DadosGrafico(val x: DoubleArray, val series: List<DoubleArray?>, val xu: String, val yu: String)

object Amostragem {
    const val PADRAO = 100
    const val MIN = 2
    const val MAX = 2_000

    /**
     * Amostra as expressões com `x` livre. A expressão é avaliada uma vez (com `x` simbólico) e cada
     * ponto só substitui `x` por um número nessa expressão pronta, sem passar de novo pela avaliação
     * da folha. Nome faltando além de `x` deixa o gráfico pendente (com os livres), não erro.
     */
    fun amostrar(e: EspecGrafico, c0: Contexto, textoUnidade: (DoubleArray) -> String): Estado {
        val pl = c0.pl; val m = pl.motor
        // `x` é a variável do gráfico, mesmo que a folha já tenha um x
        val c = Contexto(HashMap(c0.env).also { it.remove("x") }, pl, mutableListOf(), HashMap(), c0.profundidade)
        val xs = m.sym("x")
        val faltam = linkedSetOf<String>()
        val a = avaliar(e.a, c); val b = avaliar(e.b, c)
        faltam += m.nomesLivres(a) + m.nomesLivres(b)
        val avaliadas = e.exprs.map { n ->
            val v = avaliar(n, c)
            if (v.isList || v.isString) throw ErroCalculo("O gráfico precisa de expressões com um valor para cada x.")
            faltam += m.nomesLivres(v).filter { it != "x" }
            v
        }
        if (faltam.isNotEmpty()) return Estado("parcial", livres = faltam.toList())
        if (avaliadas.isEmpty()) throw ErroCalculo("Escreva uma expressão em x.")
        val (na, nb) = Faixa.numeros(m, a, b)
        if (!na.isFinite() || !nb.isFinite() || na == nb) throw ErroCalculo("Intervalo inválido: a e b devem ser diferentes.")
        val un = Faixa.unidade(m, a, b)
        val n = e.n.coerceIn(MIN, MAX)
        val x = DoubleArray(n) { na + (nb - na) * it / (n - 1) }
        var yMono: IExpr? = null; var yDims: DoubleArray? = null
        val series = avaliadas.map { v ->
            val ys = arrayOfNulls<Double>(n)
            for (k in 0 until n) {
                m.verificarPrazo()
                try {
                    val xv = if (un == F.C1) F.num(x[k]) else F.Times(F.num(x[k]), un)
                    val s = v.replaceAll(F.Rule(xs, xv)).orElse(v)
                    var y = m.ev(F.N(s))
                    if (m.temLivres(y)) continue
                    if (m.temUnidade(y)) {
                        val d = m.dims(y)
                        if (yDims == null) { yDims = d; yMono = m.monomio(d) }
                        else if (!yDims!!.contentEquals(d)) continue
                        y = m.ev(F.N(F.Divide(y, yMono)))
                    } else if (yDims != null && yDims!!.any { it != 0.0 }) continue
                    if (!y.isReal) continue
                    val d = y.evalf()
                    if (d.isFinite()) ys[k] = d
                } catch (ex: Exception) {
                    if (m.interrompido(ex)) throw ex   // prazo: a região inteira vira erro
                }
            }
            quebrarDescontinuidades(ys)
        }
        if (series.all { s -> s.all { it.isNaN() } }) throw ErroCalculo("Nenhum ponto do intervalo tem valor real.")
        return Estado("grafico", grafico = DadosGrafico(x, series, if (un == F.C1) "" else textoUnidade(m.dims(un)),
            yDims?.let { textoUnidade(it) } ?: ""))
    }

    /** Buraco (NaN) onde a curva salta de um polo a outro: sinais opostos e os dois lados muito acima do típico. */
    private fun quebrarDescontinuidades(ys: Array<Double?>): DoubleArray {
        val v = DoubleArray(ys.size) { ys[it] ?: Double.NaN }
        val tipicos = v.filter { !it.isNaN() }.map { Math.abs(it) }.sorted()
        if (tipicos.size < 3) return v
        val mediana = tipicos[tipicos.size / 2]
        val limite = maxOf(10 * mediana, 1e-9)
        for (k in 1 until v.size) {
            val p = v[k - 1]; val q = v[k]
            if (p.isNaN() || q.isNaN()) continue
            if (p * q < 0 && minOf(Math.abs(p), Math.abs(q)) > limite) v[k] = Double.NaN
        }
        return v
    }
}
