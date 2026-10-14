package tracolab.nucleo

import org.matheclipse.core.eval.EvalEngine
import org.matheclipse.core.eval.ExprEvaluator
import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import org.matheclipse.core.interfaces.ISymbol

// ------------------------------------------------------------------ motor

/** Prefixos dos símbolos internos: variáveis do usuário e unidades base nunca colidem
 *  com funções embutidas do Symja (N, D, E, I, C...).
 *  O Symja guarda nomes de símbolo em minúsculas, então F e f virariam o mesmo símbolo:
 *  o nome é codificado em hexadecimal (injetivo e sem maiúsculas) e decodificado na volta. */
const val PV = "zzv"
const val PU = "zzu"

fun codificar(nome: String): String = nome.map { "%04x".format(it.code) }.joinToString("")
fun decodificar(cod: String): String = cod.chunked(4).map { it.toInt(16).toChar() }.joinToString("")

class Motor {
    val avaliador = ExprEvaluator()
    val engine: EvalEngine = avaliador.evalEngine
    val bases: List<ISymbol> = listOf("m", "kg", "s", "A", "K", "mol", "cd").map { F.symbol(PU + codificar(it)) }
    private val cacheUnid = HashMap<String, IExpr>()

    fun ev(e: IExpr): IExpr = engine.evaluate(e)
    fun dbl(e: IExpr): Double = engine.evalDouble(e)
    fun sym(nome: String): ISymbol = F.symbol(PV + codificar(nome))

    fun unidade(nome: String): IExpr {
        if (nome in TabelaUnidades.naoSuportadas) throw NaoSuportado("unidade $nome")
        val u = TabelaUnidades.tabela[nome] ?: throw ErroCalculo("unidade desconhecida: $nome")
        return cacheUnid.getOrPut(nome) {
            var e: IExpr = avaliador.eval(u.fator)
            u.dims.forEachIndexed { i, p -> if (p != 0) e = F.Times(e, F.Power(bases[i], F.ZZ(p.toLong()))) }
            ev(e)
        }
    }

    // ---- dimensões

    fun temUnidade(e: IExpr): Boolean = contem(e) { it is ISymbol && it in bases }
    fun temLivres(e: IExpr): Boolean = contem(e) { it is ISymbol && it.symbolName.startsWith(PV) }

    private fun contem(e: IExpr, p: (IExpr) -> Boolean): Boolean {
        if (p(e)) return true
        if (e is IAST) { if (p(e.head())) return true; for (i in 1 until e.size()) if (contem(e.get(i), p)) return true }
        return false
    }

    fun dims(e: IExpr): DoubleArray {
        if (!temUnidade(e)) return DoubleArray(7)
        val i = bases.indexOf(e)
        if (i >= 0) return DoubleArray(7).also { it[i] = 1.0 }
        if (e.isTimes) {
            val d = DoubleArray(7); for (k in 1 until (e as IAST).size()) dims(e.get(k)).forEachIndexed { j, v -> d[j] += v }; return d
        }
        if (e.isPower) {
            val ex = e.exponent()
            if (temUnidade(ex) || !ex.isNumber) throw ErroUnidade("Expoente com unidade ou simbólico.")
            // + 0.0: 0 * (-1) dá -0.0, e contentEquals distingue -0.0 de 0.0 (1/{s} ≠ {Hz})
            val k = dbl(ex); return dims(e.base()).map { it * k + 0.0 }.toDoubleArray()
        }
        if (e.isPlus) {
            val ds = (1 until (e as IAST).size()).map { dims(e.get(it)) }
            if (ds.any { !it.contentEquals(ds[0]) }) throw ErroUnidade("As unidades não são compatíveis.")
            return ds[0]
        }
        if (e is IAST && e.head() == F.Abs) return dims(e.arg1())
        throw ErroUnidade("Este valor deve ser adimensional.")
    }

    fun checarSoma(a: IExpr, b: IExpr) {
        if (!(temUnidade(a) || temUnidade(b))) return
        if (temLivres(a) || temLivres(b) || a.isZero || b.isZero) return
        if (!dims(a).contentEquals(dims(b))) throw ErroUnidade("As unidades não são compatíveis.")
    }

    fun exigirAdimensional(e: IExpr, onde: String) {
        if (temUnidade(e) && !temLivres(e) && dims(e).any { it != 0.0 })
            throw ErroUnidade("$onde(): o argumento deve ser adimensional.")
    }

    fun monomio(d: DoubleArray): IExpr {
        var m: IExpr = F.C1
        d.forEachIndexed { i, p -> if (p != 0.0) m = F.Times(m, F.Power(bases[i], numero(p))) }
        return ev(m)
    }

    private fun numero(p: Double): IExpr = if (p == Math.rint(p)) F.ZZ(p.toLong()) else F.num(p)

    /**
     * Raízes e potências fracionárias de unidades: √(25 m²) → 5 m. As unidades de base são
     * positivas, mas o Symja não supõe isso e deixa √(m²) sem simplificar (aí o número não sai).
     * Só vale para valor sem nome faltando, para não transformar √(x²) em x.
     */
    fun expandirUnidades(e: IExpr): IExpr = if (temUnidade(e) && !temLivres(e)) ev(F.PowerExpand(e)) else e

    /** Nomes ainda não fornecidos dentro de um valor. */
    fun nomesLivres(e: IExpr): List<String> {
        val out = sortedSetOf<String>()
        fun anda(x: IExpr) {
            if (x is ISymbol && x.symbolName.startsWith(PV)) out += decodificar(x.symbolName.removePrefix(PV))
            if (x is IAST) { anda(x.head()); for (i in 1 until x.size()) anda(x.get(i)) }
        }
        anda(e); return out.toList()
    }
}

