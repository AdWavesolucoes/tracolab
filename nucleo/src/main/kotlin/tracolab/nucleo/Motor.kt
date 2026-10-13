package tracolab.nucleo

import org.matheclipse.core.eval.EvalEngine
import org.matheclipse.core.eval.ExprEvaluator
import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import org.matheclipse.core.interfaces.ISymbol
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

// ------------------------------------------------------------------ tipos

class NaoSuportado(msg: String) : Exception(msg)
class ErroCalculo(msg: String) : Exception(msg)
class ErroUnidade(msg: String) : Exception(msg)
class UsaFaixa(val nome: String, val faixa: Faixa) : Exception(nome)

/** Valor guardado no ambiente: expressão, faixa ou função do usuário. */
sealed class Valor {
    data class Expr(val e: IExpr) : Valor()
    data class Faixa_(val f: Faixa) : Valor()
    data class Funcao(val params: List<String>, val corpo: No, val env: MutableMap<String, Valor>) : Valor()
}

class Faixa(val inicio: IExpr, val passo: IExpr, val fim: IExpr) {
    fun valores(m: Motor): List<IExpr> {
        val a = m.dbl(inicio); val d = m.dbl(passo); val b = m.dbl(fim)
        if (d == 0.0 || (b - a) / d < 0) throw ErroCalculo("Faixa inválida")
        val n = ((b - a) / d + 1e-9).toInt() + 1
        if (n > 200_000) throw NaoSuportado("faixa grande demais")
        return (0 until n).map { k -> m.ev(F.Plus(inicio, F.Times(F.ZZ(k.toLong()), passo))) }
    }
}

data class Estado(
    val tipo: String,                    // numerico | parcial | erro | nao_suportado
    val valor: IExpr? = null,
    val livres: List<String> = emptyList(),
    val msg: String? = null,
    var aviso: String? = null,
    val faixa: Faixa? = null,
    val funcao: Valor.Funcao? = null,
)

class Regiao(
    val id: String, val y: Double, val x: Double,
    val tipo: String,                    // definicao | definicao_global | definicao_simbolica |
                                         // avaliacao_numerica | avaliacao_simbolica
    val expr: No? = null,
    val faixa: Pair<List<No>, No>? = null,   // i := a[, a2] .. b
    val define: List<String> = emptyList(),
    val params: List<String> = emptyList(),
    val tipoAlvo: String? = null,        // variavel | funcao | elemento
    val indices: List<No> = emptyList(),
    val usa: List<String> = emptyList(),
    val unidadeExibicao: No? = null,
    val unidadeExibicaoTexto: String? = null,
) {
    var estado: Estado? = null
}

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
            val k = dbl(ex); return dims(e.base()).map { it * k }.toDoubleArray()
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

// ------------------------------------------------------------------ avaliação de expressões

class Contexto(
    val env: Map<String, Valor>,
    val pl: Planilha,
    val livres: MutableList<String> = mutableListOf(),
    val iter: MutableMap<String, IExpr> = mutableMapOf(),
)

fun numeroExato(t: String): IExpr {
    val bd = BigDecimal(t)
    return if (bd.scale() <= 0) F.ZZ(bd.toBigIntegerExact())
    else F.fraction(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
}

fun avaliar(n: No, c: Contexto): IExpr {
    val m = c.pl.motor
    return when (n) {
        is No.Num -> numeroExato(n.texto)
        is No.Id -> {
            c.iter[n.nome]?.let { return it }
            when (val v = c.env[n.nome]) {
                is Valor.Expr -> return v.e
                is Valor.Faixa_ -> throw UsaFaixa(n.nome, v.f)
                is Valor.Funcao -> throw ErroCalculo("${n.nome} é uma função")
                null -> {}
            }
            when (n.nome) {
                "π" -> return F.Pi
                "e" -> return F.E
            }
            if (c.pl.unidades && n.nome in TabelaUnidades.tabela) return m.unidade(n.nome)
            if (n.nome !in c.livres) c.livres += n.nome
            m.sym(n.nome)
        }
        is No.Unidade -> avaliarUnidade(n.expr, m)
        is No.Neg -> m.ev(F.Negate(avaliar(n.a, c)))
        is No.Bin -> {
            val a = avaliar(n.a, c); val b = avaliar(n.b, c)
            when (n.op) {
                '+' -> { m.checarSoma(a, b); m.ev(F.Plus(a, b)) }
                '-' -> { m.checarSoma(a, b); m.ev(F.Subtract(a, b)) }
                '*' -> m.ev(F.Times(a, b))
                '/' -> { if (b.isZero) throw ErroCalculo("Divisão por zero."); m.ev(F.Divide(a, b)) }
                '^' -> m.ev(F.Power(a, b))
                else -> throw NaoSuportado("operador ${n.op}")
            }
        }
        is No.Cmp -> comparar(n.op, avaliar(n.a, c), avaliar(n.b, c), m)
        is No.Chama -> chamar(n, c)
        is No.Indice -> {
            val base = avaliar(n.base, c)
            val idx = n.idx.map { avaliar(it, c) }
            if (base.isList && idx.size == 1 && idx[0].isInteger) {
                val k = m.dbl(idx[0]).toInt() - c.pl.origem
                val l = base as IAST
                if (k < 0 || k >= l.argSize()) throw ErroCalculo("Índice fora do intervalo.")
                l.get(k + 1)
            } else throw NaoSuportado("índice")
        }
    }
}

private fun avaliarUnidade(n: No, m: Motor): IExpr = when (n) {
    is No.Id -> m.unidade(n.nome)
    is No.Num -> numeroExato(n.texto)
    is No.Neg -> m.ev(F.Negate(avaliarUnidade(n.a, m)))
    is No.Bin -> {
        val a = avaliarUnidade(n.a, m); val b = avaliarUnidade(n.b, m)
        m.ev(when (n.op) { '*' -> F.Times(a, b); '/' -> F.Divide(a, b); '^' -> F.Power(a, b); else -> throw ErroSintaxe("unidade") })
    }
    else -> throw ErroSintaxe("unidade")
}

private fun comparar(op: String, a0: IExpr, b0: IExpr, m: Motor): IExpr {
    var a = a0; var b = b0
    if ((m.temUnidade(a) || m.temUnidade(b)) && !(m.temLivres(a) || m.temLivres(b))) {
        m.checarSoma(a, b)
        val mono = m.monomio(m.dims(if (m.temUnidade(a)) a else b))
        a = m.ev(F.Divide(a, mono)); b = m.ev(F.Divide(b, mono))
    }
    val r = m.ev(when (op) {
        ">" -> F.Greater(a, b); "<" -> F.Less(a, b); ">=" -> F.GreaterEqual(a, b)
        "<=" -> F.LessEqual(a, b); "==" -> F.Equal(a, b); else -> F.Unequal(a, b)
    })
    return when { r.isTrue -> F.C1; r.isFalse -> F.C0; else -> r }
}

private val TRANSC: Map<String, (IExpr) -> IExpr> = mapOf(
    "sin" to { x -> F.Sin(x) }, "cos" to { x -> F.Cos(x) }, "tan" to { x -> F.Tan(x) },
    "asin" to { x -> F.ArcSin(x) }, "acos" to { x -> F.ArcCos(x) }, "atan" to { x -> F.ArcTan(x) },
    "sinh" to { x -> F.Sinh(x) }, "cosh" to { x -> F.Cosh(x) }, "tanh" to { x -> F.Tanh(x) },
    "exp" to { x -> F.Exp(x) }, "ln" to { x -> F.Log(x) }, "log" to { x -> F.Log10(x) },
)

private fun chamar(n: No.Chama, c: Contexto): IExpr {
    val m = c.pl.motor
    val f = c.env[n.nome]
    if (f is Valor.Funcao) {
        val vals = n.args.map { avaliar(it, c) }
        if (vals.size != f.params.size) throw ErroCalculo("${n.nome}: número errado de argumentos")
        val env = HashMap(f.env); f.params.zip(vals).forEach { (p, v) -> env[p] = Valor.Expr(v) }
        return avaliar(f.corpo, Contexto(env, c.pl, c.livres))
    }
    if (f != null) throw ErroCalculo("${n.nome} não é uma função")
    val vals = n.args.map { avaliar(it, c) }
    TRANSC[n.nome]?.let { fn -> vals.forEach { m.exigirAdimensional(it, n.nome) }; return m.ev(fn(vals[0])) }
    when (n.nome) {
        "sqrt" -> return m.ev(F.Sqrt(vals[0]))
        "abs" -> return m.ev(F.Abs(vals[0]))
        "floor" -> return m.ev(F.Floor(vals[0]))
        "ceil" -> return m.ev(F.Ceiling(vals[0]))
        "max", "min" -> {
            val ast = F.ast(if (n.nome == "max") F.Max else F.Min); vals.forEach { ast.append(it) }
            return m.ev(ast)
        }
    }
    // função ainda não definida: fica livre
    if (n.nome !in c.livres) c.livres += n.nome
    val ast = F.ast(m.sym(n.nome)); vals.forEach { ast.append(it) }
    return ast
}

// ------------------------------------------------------------------ planilha

/**
 * Uma página de cálculo: regiões posicionadas, avaliadas na ordem da linha de base.
 *
 * @param modo "parcial" (padrão do app: variável que falta deixa o resultado parcial) ou
 *             "classico" (variável que falta em := ou = é erro). Ver ADR 0002.
 * @param unidades true trata nomes como m, s, kg como unidades quando não são variáveis
 *             (modo compatível). No app a unidade é explícita: 20{mm}. Ver ADR 0007.
 */
class Planilha(val modo: String = "parcial", val origem: Int = 0, val unidades: Boolean = false,
               val motor: Motor = Motor()) {
    init { require(modo == "parcial" || modo == "classico") { "modo deve ser 'parcial' ou 'classico'" } }
    val regioes = mutableListOf<Regiao>()
    private var env = HashMap<String, Valor>()
    private var origemNome = HashMap<String, String>()

    fun ordenadas(): List<Regiao> {
        val ch = compareBy<Regiao>({ it.y }, { it.x })
        return regioes.filter { it.tipo == "definicao_global" }.sortedWith(ch) +
            regioes.filter { it.tipo != "definicao_global" }.sortedWith(ch)
    }

    fun adicionar(r: Regiao): List<String> { regioes += r; return recalcular(r) }

    fun editar(id: String, nova: Regiao): List<String> {
        val i = regioes.indexOfFirst { it.id == id }
        val antiga = regioes[i]; regioes[i] = nova
        return recalcular(nova, antiga.define.toSet(), global = antiga.tipo == "definicao_global")
    }

    fun remover(id: String): List<String> {
        val antiga = regioes.first { it.id == id }; regioes.remove(antiga)
        return recalcular(null, antiga.define.toSet(), antiga.y to antiga.x, antiga.tipo == "definicao_global")
    }

    /** Recalcula só as regiões afetadas (a alterada e quem depende dela, transitivamente). */
    fun recalcular(aPartirDe: Regiao? = null, nomesExtra: Set<String> = emptySet(),
                   posicao: Pair<Double, Double>? = null, global: Boolean = false): List<String> {
        val ordem = ordenadas()
        var sujos: MutableSet<String>? = null
        if (aPartirDe != null || nomesExtra.isNotEmpty()) {
            val nomes = nomesExtra.toMutableSet().apply { aPartirDe?.let { addAll(it.define) } }
            sujos = mutableSetOf<String>().apply { aPartirDe?.let { add(it.id) } }
            var ini = aPartirDe?.let { it.y to it.x } ?: posicao!!
            if (global || aPartirDe?.tipo == "definicao_global") ini = Double.NEGATIVE_INFINITY to Double.NEGATIVE_INFINITY
            for (r in ordem) {
                val depois = r.y > ini.first || (r.y == ini.first && r.x >= ini.second) ||
                    r.tipo == "definicao_global"
                if (r.id in sujos || (depois && r.usa.any { it in nomes })) { sujos += r.id; nomes += r.define }
            }
        }
        env = HashMap(); origemNome = HashMap()
        val recalc = mutableListOf<String>()
        for (r in ordem) {
            if (sujos == null || r.id in sujos || r.estado == null) { r.estado = avaliarRegiao(r); recalc += r.id }
            publicar(r)
        }
        return recalc
    }

    private fun publicar(r: Regiao) {
        if (!r.tipo.startsWith("definicao")) return
        val e = r.estado!!
        for (n in r.define) {
            origemNome[n] = r.id
            when {
                e.tipo != "numerico" && e.tipo != "parcial" -> env.remove(n)
                e.faixa != null -> env[n] = Valor.Faixa_(e.faixa)
                e.funcao != null -> env[n] = e.funcao
                else -> env[n] = Valor.Expr(e.valor!!)
            }
        }
    }

    private fun avaliarRegiao(r: Regiao): Estado {
        for (n in r.usa) {
            val dep = regioes.firstOrNull { it.id == origemNome[n] } ?: continue
            val t = dep.estado?.tipo
            if ((t == "erro" || t == "nao_suportado") && !(n in r.define && r.tipoAlvo != "elemento"))
                return if (t == "erro") Estado("erro", msg = "Depende de '$n', que está com erro.")
                else Estado("nao_suportado", msg = "Depende de '$n' (não suportado)")
        }
        val aviso = if (r.define.any { it in env } && r.tipoAlvo != "elemento") "Redefinição" else null
        val c = Contexto(HashMap(env), this)
        return try {
            calcular(r, c).also { if (it.aviso == null) it.aviso = aviso }
        } catch (e: ErroUnidade) { Estado("erro", msg = e.message)
        } catch (e: ErroCalculo) { Estado("erro", msg = e.message)
        } catch (e: NaoSuportado) { Estado("nao_suportado", msg = e.message)
        } catch (e: UsaFaixa) { Estado("erro", msg = "Faixa '${e.nome}' usada fora de contexto")
        } catch (e: Exception) { Estado("nao_suportado", msg = "interno ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun calcular(r: Regiao, c: Contexto): Estado {
        if (r.tipo.startsWith("definicao")) {
            when (r.tipoAlvo) {
                "funcao" -> {
                    val f = Valor.Funcao(r.params, r.expr!!, HashMap(env))
                    f.env[r.define[0]] = f
                    return Estado("numerico", funcao = f)
                }
                "variavel" -> {
                    r.faixa?.let { (ini, fim) ->
                        val a = avaliar(ini[0], c)
                        val passo = if (ini.size == 2) motor.ev(F.Subtract(avaliar(ini[1], c), a)) else null
                        val b = avaliar(fim, c)
                        val p = passo ?: if (motor.dbl(b) >= motor.dbl(a)) F.C1 else F.CN1
                        return Estado("numerico", faixa = Faixa(a, p, b))
                    }
                    val v = if (r.tipo == "definicao_simbolica") motor.ev(F.Simplify(avaliar(r.expr!!, c)))
                            else iterar(c) { avaliar(r.expr!!, c) }
                    return estadoDe(v, c, exato = r.tipo == "definicao_simbolica")
                }
                "elemento" -> {
                    val atual = (env[r.define[0]] as? Valor.Expr)?.e
                    val lista: MutableList<IExpr> =
                        if (atual != null && atual.isList) (1 until (atual as IAST).size()).map { atual.get(it) }.toMutableList()
                        else mutableListOf()
                    iterar(c) {
                        val k = motor.dbl(avaliar(r.indices[0], c)).toInt() - origem
                        if (k < 0) throw ErroCalculo("Índice fora do intervalo.")
                        val v = avaliar(r.expr!!, c)
                        while (lista.size <= k) lista += F.C0
                        lista[k] = v
                        F.C0
                    }
                    return estadoDe(F.List(*lista.toTypedArray()), c)
                }
            }
            throw NaoSuportado("definição ${r.tipoAlvo}")
        }
        if (r.tipo == "avaliacao_numerica") return estadoDe(iterar(c) { avaliar(r.expr!!, c) }, c)
        if (r.tipo == "avaliacao_simbolica")
            return estadoDe(motor.ev(F.Simplify(avaliar(r.expr!!, c))), c, exato = true, simbolica = true)
        throw NaoSuportado(r.tipo)
    }

    /** Se a expressão usa uma variável de faixa, repete para cada valor e devolve uma lista. */
    private fun iterar(c: Contexto, fn: () -> IExpr): IExpr = try { fn() } catch (u: UsaFaixa) {
        if (u.nome in c.iter) throw u
        val res = u.faixa.valores(motor).map { v -> c.iter[u.nome] = v; iterar(c, fn) }
        c.iter.remove(u.nome)
        F.List(*res.toTypedArray())
    }

    private fun estadoDe(v: IExpr, c: Contexto, exato: Boolean = false, simbolica: Boolean = false): Estado {
        val restantes = motor.nomesLivres(v)
        val livres = if (modo == "classico") (c.livres + restantes).distinct()
                     else c.livres.filter { it in restantes } + restantes.filter { it !in c.livres }
        if (livres.isNotEmpty()) {
            if (modo == "classico" && !simbolica) throw ErroCalculo("Variável não definida.")
            return Estado("parcial", valor = v, livres = livres)
        }
        return Estado("numerico", valor = if (exato) v else motor.ev(F.N(v)))
    }

    // ---- exibição

    fun exibir(id: String): String {
        val r = regioes.first { it.id == id }
        val e = r.estado!!
        if (e.tipo != "numerico" && e.tipo != "parcial") return "[${e.tipo}] ${e.msg}"
        val v = e.valor ?: return "?"
        if (v.isList || motor.temLivres(v)) return v.toString()
        return try {
            if (r.unidadeExibicao != null) {
                val alvo = avaliar(r.unidadeExibicao, Contexto(emptyMap(), this))
                if (!motor.dims(v).contentEquals(motor.dims(alvo))) throw ErroUnidade("A unidade pedida não é compatível com o resultado.")
                "${g6(motor.dbl(motor.ev(F.Divide(v, alvo))))} ${r.unidadeExibicaoTexto}"
            } else if (!motor.temUnidade(v)) g6(motor.dbl(v))
            else {
                val d = motor.dims(v)
                "${g6(motor.dbl(motor.ev(F.Divide(v, motor.monomio(d)))))} ${textoUnidade(d)}"
            }
        } catch (err: ErroUnidade) { "[erro] ${err.message}" }
    }

    private val derivadas = listOf(
        "N" to intArrayOf(1, 1, -2, 0, 0, 0, 0), "Pa" to intArrayOf(-1, 1, -2, 0, 0, 0, 0),
        "J" to intArrayOf(2, 1, -2, 0, 0, 0, 0), "W" to intArrayOf(2, 1, -3, 0, 0, 0, 0),
        "C" to intArrayOf(0, 0, 1, 1, 0, 0, 0), "V" to intArrayOf(2, 1, -3, -1, 0, 0, 0),
        "Ω" to intArrayOf(2, 1, -3, -2, 0, 0, 0), "Hz" to intArrayOf(0, 0, -1, 0, 0, 0, 0),
    )

    private fun textoUnidade(d: DoubleArray): String {
        derivadas.firstOrNull { (_, v) -> v.indices.all { v[it].toDouble() == d[it] } }?.let { return it.first }
        val nomes = listOf("m", "kg", "s", "A", "K", "mol", "cd")
        return d.indices.filter { d[it] != 0.0 }.joinToString("·") {
            val p = d[it]; val ps = if (p == Math.rint(p)) p.toLong().toString() else p.toString()
            if (p == 1.0) nomes[it] else "${nomes[it]}^$ps"
        }
    }
}

/** Formatação equivalente ao "{:.6g}" do Python. */
fun g6(x: Double): String {
    if (x == 0.0) return "0"
    val bd = BigDecimal(x).round(MathContext(6, RoundingMode.HALF_EVEN)).stripTrailingZeros()
    val exp = bd.precision() - bd.scale() - 1
    return if (exp < -4 || exp >= 6) {
        val m = bd.movePointLeft(exp).stripTrailingZeros().toPlainString()
        "${m}e${if (exp < 0) "-" else "+"}${"%02d".format(Math.abs(exp))}"
    } else bd.toPlainString()
}

// ------------------------------------------------------------------ texto -> região

fun usados(n: No?, ligados: Set<String> = emptySet()): List<String> {
    val out = linkedSetOf<String>()
    fun anda(x: No?) {
        when (x) {
            null, is No.Num, is No.Unidade -> {}
            is No.Id -> if (x.nome !in ligados) out += x.nome
            is No.Bin -> { anda(x.a); anda(x.b) }
            is No.Neg -> anda(x.a)
            is No.Cmp -> { anda(x.a); anda(x.b) }
            is No.Chama -> { if (x.nome !in ligados) out += x.nome; x.args.forEach { anda(it) } }
            is No.Indice -> { anda(x.base); x.idx.forEach { anda(it) } }
        }
    }
    anda(n); return out.toList()
}

fun regiaoDeTexto(id: String, texto: String, y: Double, x: Double = 0.0): Regiao {
    val s = texto.trim()
    for ((sim, tipo0) in listOf(":=" to "definicao", "≡" to "definicao_global")) {
        val i = s.indexOf(sim)
        if (i < 0) continue
        val lhs = s.substring(0, i).trim()
        var rhs = s.substring(i + sim.length).trim()
        val simb = rhs.endsWith("->") || rhs.endsWith("→")
        if (simb) rhs = rhs.removeSuffix("->").removeSuffix("→").trim()
        val alvo = Parser.parse(lhs)
        if (".." in rhs) {
            val (esq, fim) = rhs.split("..", limit = 2)
            val ini = esq.split(",").map { Parser.parse(it.trim()) }
            return Regiao(id, y, x, tipo0, faixa = ini to Parser.parse(fim.trim()), define = listOf((alvo as No.Id).nome),
                tipoAlvo = "variavel", usa = ini.flatMap { usados(it) } + usados(Parser.parse(fim.trim())))
        }
        val corpo = Parser.parse(rhs)
        return when (alvo) {
            is No.Chama -> {
                val params = alvo.args.map { (it as No.Id).nome }
                Regiao(id, y, x, tipo0, corpo, define = listOf(alvo.nome), params = params, tipoAlvo = "funcao",
                    usa = usados(corpo, params.toSet()))
            }
            is No.Indice -> Regiao(id, y, x, tipo0, corpo, define = listOf((alvo.base as No.Id).nome), tipoAlvo = "elemento",
                indices = alvo.idx, usa = (alvo.idx.flatMap { usados(it) } + usados(corpo)).distinct())
            is No.Id -> Regiao(id, y, x, if (simb && tipo0 == "definicao") "definicao_simbolica" else tipo0, corpo,
                define = listOf(alvo.nome), tipoAlvo = "variavel", usa = usados(corpo))
            else -> throw ErroSintaxe("lado esquerdo inválido: $lhs")
        }
    }
    if (s.endsWith("->") || s.endsWith("→")) {
        val e = Parser.parse(s.removeSuffix("->").removeSuffix("→").trim())
        return Regiao(id, y, x, "avaliacao_simbolica", e, usa = usados(e))
    }
    val m = Regex("^(.*?)=\\s*(\\{[^}]*\\})?\\s*$").find(s) ?: throw ErroSintaxe("não entendi: $texto")
    val e = Parser.parse(m.groupValues[1].trim())
    val un = m.groupValues[2].takeIf { it.isNotEmpty() }
    return Regiao(id, y, x, "avaliacao_numerica", e, usa = usados(e),
        unidadeExibicao = un?.let { Parser.parse(it) }, unidadeExibicaoTexto = un?.removeSurrounding("{", "}"))
}
