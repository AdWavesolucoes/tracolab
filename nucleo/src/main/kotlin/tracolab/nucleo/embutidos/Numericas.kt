package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Contexto
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.Matriciais
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor
import tracolab.nucleo.aridade
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Funções numéricas do dia a dia (ADR 0018): mod, round, trunc, sign, atan2, sec/csc/cot,
 * hiperbólicas inversas, combinatória (n!, combin, permut) e gcd/lcm.
 * Nome que ainda falta em qualquer argumento deixa o resultado pendente (chips "falta X").
 */
object Numericas : ModuloEmbutidos {
    private const val LIMITE_FATORIAL = 170   // 171! passa do maior double

    override fun registrar(r: Embutidos) {
        // trigonométricas e hiperbólicas inversas: argumento adimensional, como em Elementares
        val transc: Map<String, (IExpr) -> IExpr> = mapOf(
            "sec" to { x -> F.Sec(x) }, "csc" to { x -> F.Csc(x) }, "cot" to { x -> F.Cot(x) },
            "asinh" to { x -> F.ArcSinh(x) }, "acosh" to { x -> F.ArcCosh(x) }, "atanh" to { x -> F.ArcTanh(x) },
        )
        for ((nome, fn) in transc) f(r, nome, 1) { a, c ->
            c.pl.motor.exigirAdimensional(a[0], nome)
            c.pl.motor.ev(fn(a[0]))
        }
        // atan2(y, x): ângulo do ponto (x, y), em radianos, no intervalo (-π, π]; y e x na mesma unidade
        f(r, "atan2", 2) { a, c ->
            val m = c.pl.motor
            val (n, _) = reais("atan2", listOf(a[0], a[1]), m)
            m.ev(F.ArcTan(m.ev(F.num(n[1])), m.ev(F.num(n[0]))))
        }
        // mod(x, y): resto com o sinal de y (mod(-1, 3) = 2); mantém a unidade
        f(r, "mod", 2) { a, c ->
            val m = c.pl.motor
            val (xs, u) = fatorado("mod", listOf(a[0], a[1]), m)
            if (xs[1].isZero) throw ErroCalculo("mod: divisão por zero.")
            m.ev(F.Times(m.ev(F.Mod(xs[0], xs[1])), u))
        }
        f(r, "round", 1, 2) { a, c -> arredondar("round", a, c.pl.motor, RoundingMode.HALF_UP) }
        f(r, "trunc", 1, 2) { a, c -> arredondar("trunc", a, c.pl.motor, RoundingMode.DOWN) }
        f(r, "sign", 1) { a, c ->
            val m = c.pl.motor
            val (n, _) = reais("sign", listOf(a[0]), m)
            F.ZZ(Math.signum(n[0]).toLong())
        }

        // combinatória (resultados exatos)
        f(r, "fact", 1) { a, c -> F.ZZ(fatorial("fact", inteiroNaoNeg("fact", a[0], c.pl.motor))) }
        r.funcao("fatorial") { a, c -> embutido("fact").chamar(a, c) }
        f(r, "combin", 2) { a, c ->
            val (n, k) = a.map { inteiroNaoNeg("combin", it, c.pl.motor) }
            if (k > n) throw ErroCalculo("combin: k não pode passar de n.")
            F.ZZ(fatorial("combin", n).divide(fatorial("combin", k)).divide(fatorial("combin", n - k)))
        }
        f(r, "permut", 2) { a, c ->
            val (n, k) = a.map { inteiroNaoNeg("permut", it, c.pl.motor) }
            if (k > n) throw ErroCalculo("permut: k não pode passar de n.")
            F.ZZ(fatorial("permut", n).divide(fatorial("permut", n - k)))
        }
        f(r, "gcd", -1) { a, c -> F.ZZ(inteiros("gcd", a, c.pl.motor).reduce { x, y -> x.gcd(y) }) }
        f(r, "lcm", -1) { a, c ->
            F.ZZ(inteiros("lcm", a, c.pl.motor).reduce { x, y -> if (x.signum() == 0 || y.signum() == 0) BigInteger.ZERO else x.multiply(y).abs().divide(x.gcd(y)) })
        }
    }

    private fun embutido(nome: String) = Embutidos.funcoes.getValue(nome)

    private fun arredondar(nome: String, a: List<IExpr>, m: Motor, modo: RoundingMode): IExpr {
        m.exigirAdimensional(a[0], nome)
        val casas = if (a.size == 2) Matriciais.inteiro(a[1], m) ?: throw ErroCalculo("$nome: o número de casas deve ser inteiro.") else 0
        if (a.size == 2) m.exigirAdimensional(a[1], nome)
        if (a[0].isInteger && casas >= 0) return a[0]
        val x = try { m.dbl(a[0]) } catch (e: Exception) { if (m.interrompido(e)) throw e; throw ErroCalculo("$nome: o valor deve ser um número real.") }
        if (!x.isFinite()) throw ErroCalculo("$nome: o valor deve ser finito.")
        val bd = BigDecimal(x.toString()).setScale(casas, modo)
        return if (casas <= 0) F.ZZ(bd.toBigIntegerExact()) else F.num(bd.toDouble())
    }

    private fun fatorial(nome: String, n: Int): BigInteger {
        if (n > LIMITE_FATORIAL) throw ErroCalculo("$nome: o valor passa do maior número que dá para calcular ($LIMITE_FATORIAL! é o limite).")
        var p = BigInteger.ONE
        for (i in 2..n) p = p.multiply(BigInteger.valueOf(i.toLong()))
        return p
    }

    private fun inteiroNaoNeg(nome: String, e: IExpr, m: Motor): Int {
        m.exigirAdimensional(e, nome)
        val k = Matriciais.inteiro(e, m)
        if (k == null || k < 0) throw ErroCalculo("$nome: espera um inteiro não negativo.")
        return k
    }

    private fun inteiros(nome: String, a: List<IExpr>, m: Motor): List<BigInteger> {
        val xs = a.flatMap { if (it.isList) Matriciais.linhas(it).flatten() else listOf(it) }
        if (xs.size < 2) throw ErroCalculo("$nome: espera ao menos 2 valores.")
        return xs.map {
            m.exigirAdimensional(it, nome)
            val d = try { m.dbl(it) } catch (e: Exception) { if (m.interrompido(e)) throw e; Double.NaN }
            if (d.isNaN() || d != Math.rint(d) || Math.abs(d) > 1e15) throw ErroCalculo("$nome: espera inteiros.")
            BigInteger.valueOf(d.toLong())
        }
    }
}

// ---------------------------------------------------------------------- auxiliares das áreas de funções

/**
 * Registra uma função de valores (aridade n; [n] vazio ou -1 = ao menos um argumento). Nome que
 * ainda falta em algum argumento deixa o resultado pendente.
 */
internal fun f(r: Embutidos, nome: String, vararg n: Int, fn: (List<IExpr>, Contexto) -> IExpr) = r.funcao(nome) { a, c ->
    if (n.size == 1 && n[0] < 0) { if (a.isEmpty()) throw ErroCalculo("$nome: espera ao menos 1 argumento") }
    else aridade(nome, a, *n)
    if (a.any { c.pl.motor.temLivres(it) }) return@funcao Matriciais.pendente(nome, a)
    fn(a, c)
}

/** Valores escalares com unidade comum fatorada (exatos): (valores adimensionais, unidade). */
internal fun fatorado(nome: String, xs: List<IExpr>, m: Motor): Pair<List<IExpr>, IExpr> {
    if (xs.any { it.isList }) throw ErroCalculo("$nome: espera valores, não vetores ou matrizes.")
    return Matriciais.fatorar(xs, m) ?: throw ErroCalculo("$nome: todos os valores precisam ser conhecidos.")
}

/** Como [fatorado], mas em Double (os valores devem ser reais). */
internal fun reais(nome: String, xs: List<IExpr>, m: Motor): Pair<DoubleArray, IExpr> {
    val (ns, u) = fatorado(nome, xs, m)
    return ns.map {
        try { m.dbl(it) } catch (e: Exception) { if (m.interrompido(e)) throw e; throw ErroCalculo("$nome: os valores devem ser números reais.") }
    }.toDoubleArray() to u
}

/** Resultado numérico multiplicado pela unidade. */
internal fun comUnidade(d: Double, u: IExpr, m: Motor): IExpr = if (u == F.C1) F.num(d) else m.ev(F.Times(F.num(d), u))
