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
 * @param limites tempo, recursão e tamanho máximos (ADR 0017).
 */
class Planilha(val modo: String = "parcial", val origem: Int = 0, val unidades: Boolean = false,
               val motor: Motor = Motor(), val limites: Limites = Limites()) {
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
        require(i >= 0) { "região inexistente: $id" }
        val antiga = regioes[i]; regioes[i] = nova
        // a região pode ter mudado de lugar: quem está entre a posição antiga e a nova também conta
        return recalcular(nova, antiga.define.toSet(), antiga.y to antiga.x, global = antiga.tipo == "definicao_global")
    }

    fun remover(id: String): List<String> {
        val antiga = requireNotNull(regioes.firstOrNull { it.id == id }) { "região inexistente: $id" }; regioes.remove(antiga)
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
            var ini = listOfNotNull(aPartirDe?.let { it.y to it.x }, posicao).minWith(compareBy({ it.first }, { it.second }))
            if (global || aPartirDe?.tipo == "definicao_global") ini = Double.NEGATIVE_INFINITY to Double.NEGATIVE_INFINITY
            for (r in ordem) {
                val depois = r.y > ini.first || (r.y == ini.first && r.x >= ini.second) ||
                    r.tipo == "definicao_global"
                if (r.id in sujos || (depois && r.usa.any { it in nomes })) { sujos += r.id; nomes += r.define }
            }
        }
        env = HashMap(); origemNome = HashMap()
        val recalc = mutableListOf<String>()
        motor.ativar()
        // ADR 0017: cada região tem um prazo, e a folha inteira também; o que passar vira erro
        val folha = "Não calculada: a folha passou de ${textoTempo(limites.msFolha)}."
        motor.prazo.com(limites.msFolha, folha) {
            for (r in ordem) {
                if (sujos == null || r.id in sujos || r.estado == null) {
                    r.estado = if (motor.prazo.esgotado()) Estado("erro", msg = folha)
                               else motor.prazo.com(limites.msRegiao, "Cálculo interrompido: passou de ${textoTempo(limites.msRegiao)}.") { avaliarRegiao(r) }
                    recalc += r.id
                }
                publicar(r)
            }
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
            // só a função recursiva (f(n) := ... f(n-1)) não depende da definição anterior de f;
            // x := x + 1 depende do x de cima, e herda o erro dele
            if ((t == "erro" || t == "nao_suportado") && !(n in r.define && r.tipoAlvo == "funcao"))
                return if (t == "erro") Estado("erro", msg = "Depende de '$n', que está com erro.")
                else Estado("nao_suportado", msg = "Depende de '$n' (não suportado)")
        }
        val aviso = if (r.define.any { it in env } && r.tipoAlvo != "elemento") "Redefinição" else null
        val c = Contexto(HashMap(env), this)
        elementosRegiao = 0
        return try {
            calcular(r, c).also { if (it.aviso == null) it.aviso = aviso }
        } catch (e: Throwable) {
            // limites e falhas graves (ADR 0017): só esta região fica com erro
            mensagemDeLimite(e, motor.prazo.motivo)?.let { return Estado("erro", msg = it) }
            erroComum(e)
        }
    }

    private fun erroComum(e: Throwable): Estado = when (e) {
        is ErroUnidade, is ErroCalculo -> Estado("erro", msg = e.message)
        is NaoSuportado -> Estado("nao_suportado", msg = e.message)
        is UsaFaixa -> Estado("erro", msg = "Faixa '${e.nome}' usada fora de contexto")
        is Exception -> Estado("nao_suportado", msg = "interno ${e.javaClass.simpleName}: ${e.message}")
        else -> throw e
    }

    /** Valores produzidos pela região em andamento (faixas, v[i] :=), contra [Limites.elementos]. */
    private var elementosRegiao = 0
    private fun contarElementos(n: Int) {
        elementosRegiao += n
        if (elementosRegiao > limites.elementos)
            throw ErroCalculo("Resultado grande demais: mais de ${limites.elementos} valores.")
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
                        // limite ainda faltando: a faixa fica pendente, e quem a usa também
                        val faltam = listOfNotNull(a, passo, b).flatMap { motor.nomesLivres(it) }.distinct()
                        if (faltam.isNotEmpty()) {
                            if (modo == "classico") throw ErroCalculo("Variável não definida.")
                            return Estado("parcial", valor = F.List(a, b), livres = faltam)
                        }
                        val (na, nb) = Faixa.numeros(motor, a, b)
                        val p = passo ?: if (nb >= na) (if (motor.temUnidade(b)) Faixa.unidade(motor, a, b) else F.C1)
                                         else (if (motor.temUnidade(b)) motor.ev(F.Negate(Faixa.unidade(motor, a, b))) else F.CN1)
                        return Estado("numerico", faixa = Faixa(a, p, b))
                    }
                    val v = if (r.tipo == "definicao_simbolica") motor.ev(F.Simplify(avaliar(r.expr!!, c)))
                            else iterar(c) { avaliar(r.expr!!, c) }
                    return estadoDe(v, c, exato = r.tipo == "definicao_simbolica")
                }
                "elemento" -> {   // v[i] := ... e M[i, j] := ... (Matriciais.Gravador)
                    val g = Matriciais.Gravador((env[r.define[0]] as? Valor.Expr)?.e, origem, motor, limites.elementos)
                    var pendente: List<IExpr>? = null
                    // recorrência (v[i] := v[i-1]*2 com i de faixa): cada passo enxerga o que os
                    // passos anteriores já gravaram, como numa tabela preenchida linha a linha
                    val nome = r.define[0]
                    val envLocal = HashMap(c.env)
                    val cl = Contexto(envLocal, c.pl, c.livres, c.iter, c.profundidade)
                    val recorrente = nome in r.usa
                    iterar(cl) {
                        val idx = r.indices.map { avaliar(it, cl) }
                        // índice ainda faltando: o vetor fica pendente (v_n), e quem o usa também
                        if (idx.any { motor.temLivres(it) }) pendente = idx
                        else {
                            g.gravar(idx, avaliar(r.expr!!, cl))
                            if (recorrente) envLocal[nome] = Valor.Expr(g.valor())
                        }
                        F.C0
                    }
                    pendente?.let { idx -> return estadoDe(F.ast(F.Subscript).also { s -> s.append(g.valor()); idx.forEach { s.append(it) } }, c) }
                    return estadoDe(g.valor(), c)
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
        val vals = u.faixa.valores(motor, limites.elementos)
        contarElementos(vals.size)
        val res = vals.map { v -> c.iter[u.nome] = v; iterar(c, fn) }
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
        val valor = if (exato) v else motor.ev(F.N(v))
        motor.problemaNumerico(valor, exato)?.let { throw ErroCalculo(it) }
        return Estado("numerico", valor = motor.expandirUnidades(valor))
    }

    // ---- exibição

    fun exibir(id: String): String {
        val r = regioes.first { it.id == id }
        val e = r.estado!!
        if (e.tipo != "numerico" && e.tipo != "parcial") return "[${e.tipo}] ${e.msg}"
        val v = e.valor ?: return "?"
        if (motor.temLivres(v)) return v.toString()
        // número que não é real (2·i) não tem texto "número unidade": mostra a expressão
        fun texto(x: IExpr) = try { exibirValor(x, r) } catch (err: ErroUnidade) { throw err } catch (_: Exception) { x.toString() }
        if (v.isList) return try {   // [1 kN; 2 kN] e [1, 2; 3, 4]
            Matriciais.linhas(v).joinToString("; ", "[", "]") { l -> l.joinToString(", ") { texto(it) } }
        } catch (err: ErroUnidade) { "[erro] ${err.message}" }
        return try { texto(v) } catch (err: ErroUnidade) { "[erro] ${err.message}" }
    }

    /** Texto "número unidade" de um valor escalar numérico, na unidade pedida pela região ou no SI. */
    fun exibirValor(v: IExpr, r: Regiao): String =
        if (r.unidadeExibicao != null) {
            val alvo = avaliar(r.unidadeExibicao, Contexto(emptyMap(), this))
            // zero combina com qualquer unidade (elementos nulos de uma matriz em kN, por exemplo)
            if (!v.isZero && !motor.dims(v).contentEquals(motor.dims(alvo))) throw ErroUnidade("A unidade pedida não é compatível com o resultado.")
            "${g6(valorNaUnidade(v, alvo, r.unidadeExibicao))} ${r.unidadeExibicaoTexto}"
        } else if (!motor.temUnidade(v)) g6(motor.dbl(v))
        else {
            val d = motor.dims(v)
            "${g6(motor.dbl(motor.ev(F.Divide(v, motor.monomio(d)))))} ${textoUnidade(d)}"
        }

    /** v na unidade pedida; em °C/°F (escala absoluta) desconta o zero da escala (ADR 0014). */
    fun valorNaUnidade(v: IExpr, alvo: IExpr, unidade: No?): Double {
        val x = motor.dbl(motor.ev(F.Divide(v, alvo)))
        val t = unidade?.let { temperaturaAbsoluta(it) } ?: return x
        return x - motor.dbl(motor.avaliador.eval(t.zero))
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

