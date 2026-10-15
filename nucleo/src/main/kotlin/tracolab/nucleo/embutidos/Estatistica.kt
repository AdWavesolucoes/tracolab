package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Matriciais
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor

/**
 * Estatística descritiva e normal (ADR 0018). Os dados são vetores ou matrizes (achatados) e/ou
 * valores soltos. `stdev`/`var` são populacionais (÷ n); `Stdev`/`Var` amostrais (÷ n−1).
 * Unidades: mean/median/percentile mantêm a unidade; stdev, var (unidade²), slope e intercept
 * têm a unidade que lhes cabe; corr é adimensional.
 */
object Estatistica : ModuloEmbutidos {

    override fun registrar(r: Embutidos) {
        f(r, "mean", -1) { a, c ->
            val m = c.pl.motor; val xs = dados("mean", a)
            fatorado("mean", xs, m)
            m.ev(F.Divide(F.Plus(*xs.toTypedArray()), F.ZZ(xs.size.toLong())))
        }
        f(r, "median", -1) { a, c ->
            val m = c.pl.motor; val xs = dados("median", a)
            val (n, u) = reais("median", xs, m)
            val o = n.indices.sortedBy { n[it] }
            val k = n.size / 2
            if (n.size % 2 == 1) xs[o[k]] else m.ev(F.Divide(F.Plus(xs[o[k - 1]], xs[o[k]]), F.C2))
        }
        for ((nome, amostral) in listOf("stdev" to false, "Stdev" to true)) f(r, nome, -1) { a, c ->
            val m = c.pl.motor; val (n, u) = reais(nome, dados(nome, a), m)
            comUnidade(Math.sqrt(variancia(nome, n, amostral)), u, m)
        }
        for ((nome, amostral) in listOf("var" to false, "Var" to true)) f(r, nome, -1) { a, c ->
            val m = c.pl.motor; val (n, u) = reais(nome, dados(nome, a), m)
            comUnidade(variancia(nome, n, amostral), if (u == F.C1) u else m.ev(F.Times(u, u)), m)
        }
        // percentile(v, p): p entre 0 e 1; interpolação linear entre os valores ordenados (posição p·(n−1))
        f(r, "percentile", 2) { a, c ->
            val m = c.pl.motor; val xs = dados("percentile", listOf(a[0]))
            m.exigirAdimensional(a[1], "percentile")
            val p = try { m.dbl(a[1]) } catch (e: Exception) { if (m.interrompido(e)) throw e; throw ErroCalculo("percentile: p deve ser um número entre 0 e 1.") }
            if (!(p in 0.0..1.0)) throw ErroCalculo("percentile: p deve estar entre 0 e 1.")
            val (n, u) = reais("percentile", xs, m)
            val s = n.sorted(); val pos = p * (s.size - 1); val i = Math.floor(pos).toInt()
            val d = if (i + 1 < s.size) s[i] + (pos - i) * (s[i + 1] - s[i]) else s[i]
            comUnidade(d, u, m)
        }
        f(r, "corr", 2) { a, c ->
            val m = c.pl.motor; val (x, y) = par("corr", a, m)
            val (sxx, syy, sxy) = somas(x.first, y.first)
            if (sxx == 0.0 || syy == 0.0) throw ErroCalculo("corr: os dados não variam, a correlação não existe.")
            F.num(sxy / Math.sqrt(sxx * syy))
        }
        f(r, "slope", 2) { a, c ->
            val m = c.pl.motor; val (x, y) = par("slope", a, m)
            val (sxx, _, sxy) = somas(x.first, y.first)
            if (sxx == 0.0) throw ErroCalculo("slope: os valores de x não variam.")
            val u = m.ev(F.Divide(y.second, x.second))
            comUnidade(sxy / sxx, u, m)
        }
        f(r, "intercept", 2) { a, c ->
            val m = c.pl.motor; val (x, y) = par("intercept", a, m)
            val (sxx, _, sxy) = somas(x.first, y.first)
            if (sxx == 0.0) throw ErroCalculo("intercept: os valores de x não variam.")
            comUnidade(y.first.average() - sxy / sxx * x.first.average(), y.second, m)
        }

        // normal: pnorm(x[, μ, σ]) acumulada, dnorm densidade, qnorm(p[, μ, σ]) inversa da acumulada
        f(r, "pnorm", 1, 3) { a, c ->
            val m = c.pl.motor; val (z, _) = padronizar("pnorm", a, m)
            F.num(0.5 * m.dbl(F.Erfc(F.num(-z / Math.sqrt(2.0)))))
        }
        f(r, "dnorm", 1, 3) { a, c ->
            val m = c.pl.motor; val (z, s) = padronizar("dnorm", a, m)
            val dens = Math.exp(-z * z / 2) / Math.sqrt(2 * Math.PI) / s.first
            val u = s.second
            comUnidade(dens, if (u == F.C1) u else m.ev(F.Power(u, F.CN1)), m)
        }
        f(r, "qnorm", 1, 3) { a, c ->
            val m = c.pl.motor
            m.exigirAdimensional(a[0], "qnorm")
            val p = try { m.dbl(a[0]) } catch (e: Exception) { if (m.interrompido(e)) throw e; throw ErroCalculo("qnorm: p deve ser um número entre 0 e 1.") }
            if (!(p > 0.0 && p < 1.0)) throw ErroCalculo("qnorm: p deve estar estritamente entre 0 e 1.")
            val z = Math.sqrt(2.0) * m.dbl(F.InverseErf(F.num(2 * p - 1)))
            if (a.size == 1) F.num(z) else {
                val (n, u) = reais("qnorm", listOf(a[1], a[2]), m)
                if (n[1] <= 0) throw ErroCalculo("qnorm: o desvio padrão deve ser positivo.")
                comUnidade(n[0] + n[1] * z, u, m)
            }
        }
    }

    /** Valores soltos e elementos de vetores/matrizes, achatados. */
    private fun dados(nome: String, a: List<IExpr>): List<IExpr> {
        val xs = a.flatMap { if (it.isList) Matriciais.linhas(it).flatten() else listOf(it) }
        if (xs.isEmpty()) throw ErroCalculo("$nome: sem dados.")
        return xs
    }

    private fun variancia(nome: String, n: DoubleArray, amostral: Boolean): Double {
        if (amostral && n.size < 2) throw ErroCalculo("$nome: a versão amostral precisa de ao menos 2 valores.")
        val mu = n.average()
        return n.sumOf { (it - mu) * (it - mu) } / (if (amostral) n.size - 1 else n.size)
    }

    private fun par(nome: String, a: List<IExpr>, m: Motor): Pair<Pair<DoubleArray, IExpr>, Pair<DoubleArray, IExpr>> {
        val x = dados(nome, listOf(a[0])); val y = dados(nome, listOf(a[1]))
        if (x.size != y.size) throw ErroCalculo("$nome: os dois vetores devem ter o mesmo tamanho (${x.size} e ${y.size}).")
        if (x.size < 2) throw ErroCalculo("$nome: precisa de ao menos 2 pontos.")
        return reais(nome, x, m) to reais(nome, y, m)
    }

    /** (Sxx, Syy, Sxy) em torno das médias. */
    private fun somas(x: DoubleArray, y: DoubleArray): Triple<Double, Double, Double> {
        val mx = x.average(); val my = y.average()
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in x.indices) { sxx += (x[i] - mx) * (x[i] - mx); syy += (y[i] - my) * (y[i] - my); sxy += (x[i] - mx) * (y[i] - my) }
        return Triple(sxx, syy, sxy)
    }

    /** z = (x − μ)/σ; devolve também (σ, unidade). Sem μ e σ: normal padrão. */
    private fun padronizar(nome: String, a: List<IExpr>, m: Motor): Pair<Double, Pair<Double, IExpr>> {
        if (a.size == 1) {
            m.exigirAdimensional(a[0], nome)
            val (n, _) = reais(nome, a, m)
            return n[0] to (1.0 to F.C1)
        }
        val (n, u) = reais(nome, a, m)
        if (n[2] <= 0) throw ErroCalculo("$nome: o desvio padrão deve ser positivo.")
        return (n[0] - n[1]) / n[2] to (n[2] to u)
    }
}
