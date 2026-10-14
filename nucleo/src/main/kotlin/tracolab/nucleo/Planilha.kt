package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

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

