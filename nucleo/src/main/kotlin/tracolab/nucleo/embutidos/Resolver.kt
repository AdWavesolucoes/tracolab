package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.expression.S
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import org.matheclipse.core.interfaces.ISymbol
import tracolab.nucleo.Contexto
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Especial
import tracolab.nucleo.Faixa
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor
import tracolab.nucleo.No
import tracolab.nucleo.UsaFaixa
import tracolab.nucleo.Valor
import tracolab.nucleo.aridade
import tracolab.nucleo.avaliar

/**
 * Resolver equações (ADR 0012): o dimensionamento, quando a folha conhece o alvo e falta uma entrada.
 *
 * - `resolver(eq, x)`, `resolver(eq, x, palpite)`, `resolver(eq, x, a, b)` (também `solve`);
 *   `eq` é `a == b` (no editor, `a = b`) ou uma expressão igualada a zero.
 * - `resolver([eq1; eq2], [x; y])`: sistema; devolve o vetor [x; y].
 * - `raiz(f, x, a, b)` / `raiz(f, x)` (também `root`): raiz de f no intervalo, ou perto do x de cima.
 * - `raizes(v)` (também `polyroots`): raízes do polinômio v0 + v1·x + v2·x² + ...
 *
 * Simbólico primeiro (`Solve`), numérico depois (`FindRoot`, a partir do palpite ou do intervalo).
 * A incógnita vale `X·u`, com u deduzida pelas unidades da equação (ou do palpite); X é adimensional.
 * Faltar outro nome não é erro: a solução sai simbólica nele, pendente com chips.
 */
object Resolver : ModuloEmbutidos {

    override fun registrar(r: Embutidos) {
        for (nome in listOf("resolver", "solve"))
            r.especial(nome, Especial(setOf(1)) { n, c -> resolver(n, c) })
        for (nome in listOf("raiz", "root"))
            r.especial(nome, Especial(setOf(1), usaLigado = { it == 2 }) { n, c -> raiz(n, c) })
        for (nome in listOf("raizes", "polyroots"))
            r.funcao(nome) { args, c -> polinomio(args, c) }
    }

    private const val TENTATIVA_MS = 1500L
    private const val TOL = 1e-9

    // ------------------------------------------------------------------ apoio

    private fun <T> com(c: Contexto, valores: Map<String, IExpr>, bloco: () -> T): T {
        val antes = valores.keys.associateWith { c.iter[it] }
        c.iter.putAll(valores)
        try { return bloco() } finally {
            antes.forEach { (k, v) -> if (v == null) c.iter.remove(k) else c.iter[k] = v }
        }
    }

    private fun nomes(n: No, fn: String): List<String> = when (n) {
        is No.Id -> listOf(n.nome)
        is No.Matriz -> n.linhas.flatten().map { (it as? No.Id)?.nome ?: throw ErroCalculo("$fn: as incógnitas devem ser nomes: [x; y].") }
        else -> throw ErroCalculo("$fn: o 2º argumento deve ser o nome da incógnita (ou um vetor de nomes).")
    }

    /** Lado esquerdo menos o direito (`a == b`), ou a própria expressão (`f = 0`). */
    private fun residuo(n: No, c: Contexto, fn: String): IExpr {
        val m = c.pl.motor
        val g = if (n is No.Cmp) {
            if (n.op != "==") throw ErroCalculo("$fn: a equação usa = (igual), não ${n.op}.")
            val a = avaliar(n.a, c); val b = avaliar(n.b, c)
            if (a.isList || b.isList) throw ErroCalculo("$fn: cada equação deve ser entre valores escalares.")
            m.ev(F.Subtract(a, b))
        } else avaliar(n, c)
        if (g.isList) throw ErroCalculo("$fn: a equação deve ser um valor só (para sistemas, [eq1; eq2]).")
        if (g.isString) throw ErroCalculo("$fn: texto não entra em equações.")
        return g
    }

    private fun contem(e: IExpr, p: (IExpr) -> Boolean): Boolean =
        p(e) || (e is IAST && (p(e.head()) || (1 until e.size()).any { contem(e.get(it), p) }))

    private fun livresAlem(e: IExpr, nomes: Collection<String>, m: Motor) = m.nomesLivres(e).filter { it !in nomes }

    /** Unidade (monômio SI) de um valor conhecido; 1 se não tem unidade. */
    private fun unidadeDe(v: IExpr, m: Motor): IExpr =
        if (m.temUnidade(v) && !m.temLivres(v) && !v.isList) m.monomio(m.dims(m.expandirUnidades(v))) else F.C1

    private fun termos(e: IExpr): List<IExpr> = if (e.isPlus) (1 until (e as IAST).size()).map { e.get(it) } else listOf(e)
    private fun fatores(e: IExpr): List<IExpr> = if (e.isTimes) (1 until (e as IAST).size()).map { e.get(it) } else listOf(e)

    /**
     * Dimensão da incógnita pelas unidades: em cada termo, X aparece como X^k e o resto tem
     * dimensão d; dois termos com k diferentes dão D = (dⱼ − dᵢ)/(kᵢ − kⱼ). Sem par assim, adimensional.
     */
    private fun deduzirUnidade(g: IExpr, x: ISymbol, m: Motor): IExpr {
        if (!m.temUnidade(g)) return F.C1
        val pares = mutableListOf<Pair<Double, DoubleArray>>()
        for (t in termos(m.ev(F.Expand(g)))) {
            // expoente de X no termo: X·∂t/∂X / t, constante quando o termo é c·X^k
            val k = if (!contem(t) { it == x }) 0.0 else {
                val q = try { m.ev(F.Together(F.Divide(F.Times(x, F.D(t, x)), t))) } catch (e: Exception) { if (m.interrompido(e)) throw e; null }
                if (q == null || !q.isNumber) continue
                m.dbl(q)
            }
            val resto = m.ev(F.PowerExpand(F.Divide(t, F.Power(x, F.num(k).let { if (k == Math.rint(k)) F.ZZ(k.toLong()) else it }))))
            if (contem(resto) { it == x }) continue
            val d = try { m.dims(m.expandirUnidades(resto)) } catch (e: ErroUnidade) { continue }
            pares += k to d
        }
        for (i in pares.indices) for (j in i + 1 until pares.size) {
            val (ki, di) = pares[i]; val (kj, dj) = pares[j]
            if (ki != kj) return m.monomio(DoubleArray(7) { arredondar((dj[it] - di[it]) / (ki - kj)) })
        }
        return F.C1
    }

    /** Expoente de unidade sem o ruído do ponto flutuante: 1.0000000000000004 → 1, 0.49999… → 1/2. */
    private fun arredondar(v: Double): Double {
        val r = Math.round(v * 12) / 12.0
        return (if (Math.abs(v - r) < 1e-9) r else v) + 0.0
    }

    /**
     * Tira as unidades da equação já em X adimensional. Se os termos têm todos a mesma dimensão
     * (conferido numa amostra de X; o X não carrega unidade), as unidades de base valem 1: sobra
     * a equação em números do SI.
     */
    private fun semUnidade(g: IExpr, x: ISymbol, m: Motor): IExpr {
        if (!m.temUnidade(g)) return g
        val amostra = m.expandirUnidades(m.ev(F.ReplaceAll(g, F.Rule(x, F.fraction(7, 3)))))
        try { m.dims(amostra) } catch (e: ErroUnidade) { throw ErroUnidade("As unidades dos dois lados da equação não são compatíveis.") }
        return m.ev(F.ReplaceAll(g, F.List(*m.bases.map { F.Rule(it, F.C1) }.toTypedArray())))
    }

    private fun numero(e: IExpr, m: Motor): Pair<Double, Double>? = try {
        val v = m.ev(F.N(e))
        if (!v.isNumber) null else {
            val re = m.dbl(m.ev(F.Re(v))); val im = m.dbl(m.ev(F.Im(v)))
            if (re.isFinite() && im.isFinite()) re to im else null
        }
    } catch (ex: Exception) { if (m.interrompido(ex)) throw ex; null }

    private fun real(z: Pair<Double, Double>) = Math.abs(z.second) <= TOL * (Math.abs(z.first) + 1)

    // ------------------------------------------------------------------ Solve / FindRoot

    /** Raízes simbólicas de g = 0 em X; null se o Solve não deu conta (ou esgotou o tempo). */
    private fun solve(g: IExpr, x: ISymbol, m: Motor, numerica: Boolean, fn: String, nome: String): List<IExpr>? {
        val bloco = {
            try { m.ev(F.Solve(F.Equal(g, F.C0), x)) } catch (e: Exception) { if (m.interrompido(e)) throw e; null }
        }
        val r = (if (numerica) m.prazo.tentarPor(TENTATIVA_MS, bloco) else bloco()) ?: return null
        if (!r.isList || contem(r) { it == S.Solve || it == S.ConditionalExpression || it == S.C }) return null
        val sols = (1 until (r as IAST).size()).map { r.get(it) }
        if (sols.isEmpty()) return emptyList()
        if (sols.any { it.isList && (it as IAST).size() == 1 }) throw ErroCalculo("$fn: a equação vale para qualquer $nome (infinitas soluções).")
        return sols.map { s ->
            val regra = (s as? IAST)?.takeIf { it.isList && it.size() > 1 }?.get(1) ?: return null
            if (!regra.isRuleAST) return null
            (regra as IAST).arg2()
        }
    }

    private fun findRoot(g: IExpr, x: ISymbol, m: Motor, inicio: Double?, a: Double?, b: Double?): Double? {
        val faixa = if (a != null && b != null) F.List(x, F.num(a), F.num(b)) else F.List(x, F.num(inicio ?: 1.0))
        val r = try {
            val opc = if (a != null) listOf(F.Rule(S.Method, F.stringx("Brent"))) else emptyList()
            val ast = F.ast(S.FindRoot); ast.append(g); ast.append(faixa); opc.forEach { ast.append(it) }
            m.ev(ast)
        } catch (e: Exception) { if (m.interrompido(e)) throw e; return null }
        val regra = (if (r.isList && (r as IAST).size() > 1) r.get(1) else r) as? IAST ?: return null
        if (!regra.isRuleAST) return null
        val v = numero(regra.arg2(), m) ?: return null
        if (!real(v)) return null
        // confere o resíduo: FindRoot devolve o último passo mesmo sem convergir
        val res = numero(m.ev(F.ReplaceAll(g, F.Rule(x, F.num(v.first)))), m) ?: return null
        val escala = 1.0 + Math.abs(v.first)
        if (Math.abs(res.first) > 1e-7 * escala) return null
        if (a != null && b != null && (v.first < minOf(a, b) - 1e-9 || v.first > maxOf(a, b) + 1e-9)) return null
        return v.first
    }

    // ------------------------------------------------------------------ equação de uma incógnita

    private class Pedido(val fn: String, val nome: String, val palpite: IExpr?, val a: IExpr?, val b: IExpr?, val umaSo: Boolean)

    private fun escalar(eq: No, p: Pedido, c: Contexto): IExpr {
        val m = c.pl.motor
        val x = m.sym(p.nome)
        val g0 = com(c, mapOf(p.nome to x)) { residuo(eq, c, p.fn) }
        if (!contem(g0) { it == x }) {
            if (g0.isZero) throw ErroCalculo("${p.fn}: a equação vale para qualquer ${p.nome} (infinitas soluções).")
            if (!m.temLivres(g0)) throw ErroCalculo("${p.fn}: a equação não tem solução (os dois lados nunca ficam iguais).")
            throw ErroCalculo("${p.fn}: a equação não depende de ${p.nome}.")
        }
        val outros = livresAlem(g0, listOf(p.nome), m)
        // unidade da incógnita: do palpite/intervalo, senão deduzida pela equação
        val dados = listOfNotNull(p.palpite, p.a, p.b).filter { !m.temLivres(it) }
        val u = when {
            dados.any { m.temUnidade(it) } -> Faixa.unidade(m, *dados.toTypedArray())
            outros.isEmpty() -> deduzirUnidade(g0, x, m)
            else -> F.C1
        }
        var g = m.ev(F.ReplaceAll(g0, F.Rule(x, F.Times(x, u))))
        if (outros.isEmpty()) g = semUnidade(g, x, m)
        val numerica = outros.isEmpty() && !m.temUnidade(g)
        fun emX(e: IExpr?) = e?.let { val v = m.expandirUnidades(m.ev(F.Divide(it, u))); if (m.temUnidade(v)) throw ErroUnidade("${p.fn}: o palpite e a incógnita têm unidades diferentes."); numero(v, m)?.first }
        val pal = emX(p.palpite); val ia = emX(p.a); val ib = emX(p.b)

        val raizes = solve(g, x, m, numerica, p.fn, p.nome)
        if (!numerica) {
            // nome faltando: a solução fica simbólica nele (pendente com chips)
            raizes ?: throw ErroCalculo("${p.fn}: não encontrei a solução simbólica; dê os valores que faltam e um palpite.")
            if (raizes.isEmpty()) throw ErroCalculo("${p.fn}: a equação não tem solução.")
            return m.ev(F.Times(escolherSimbolica(raizes, m), u))
        }
        // tudo número: reais primeiro; complexas só para a mensagem
        val reais = mutableListOf<Pair<IExpr, Double>>()
        var complexas = 0
        raizes?.forEach { r ->
            val z = numero(r, m) ?: return@forEach
            if (real(z)) reais += (if (Math.abs(z.second) == 0.0) r else F.num(z.first)) to z.first else complexas++
        }
        val dentro = if (ia != null && ib != null) reais.filter { it.second >= minOf(ia, ib) - 1e-12 && it.second <= maxOf(ia, ib) + 1e-12 } else reais
        val escolhidas: List<Pair<IExpr, Double>> = if (dentro.isNotEmpty()) dentro else {
            // Solve sem raiz real (ou sem resposta): tenta o numérico pelo palpite/intervalo
            val v = findRoot(g, x, m, pal, ia, ib)
            when {
                v != null -> listOf(F.num(v) to v)
                raizes != null && raizes.isEmpty() && pal == null && ia == null -> throw ErroCalculo("${p.fn}: a equação não tem solução.")
                complexas > 0 && reais.isEmpty() -> throw ErroCalculo("${p.fn}: a equação só tem soluções complexas (nenhuma real).")
                ia != null -> throw ErroCalculo("${p.fn}: não achei raiz entre os limites dados (a função deve trocar de sinal no intervalo).")
                pal != null -> throw ErroCalculo("${p.fn}: não achei raiz a partir do palpite; tente outro palpite ou um intervalo.")
                else -> throw ErroCalculo("${p.fn}: não encontrei solução; dê um palpite (${p.fn}(eq, ${p.nome}, palpite)) ou um intervalo.")
            }
        }
        val ordenadas = escolhidas.distinctBy { Math.round(it.second * 1e9) }.sortedBy { it.second }
        val final: List<Pair<IExpr, Double>> = when {
            ordenadas.size == 1 -> ordenadas
            pal != null -> listOf(ordenadas.minBy { Math.abs(it.second - pal) })
            !u.isOne && ordenadas.count { it.second > 0 } == 1 -> ordenadas.filter { it.second > 0 }
            p.umaSo -> listOf(ordenadas.first())
            else -> ordenadas
        }
        val vals = final.map { m.ev(F.Times(it.first, u)) }
        return if (vals.size == 1) vals[0] else F.List(*vals.toTypedArray())
    }

    /** Com nomes faltando: raízes em pares ±r ficam com a escrita sem sinal de menos; senão, vetor. */
    private fun escolherSimbolica(raizes: List<IExpr>, m: Motor): IExpr {
        var rs = raizes.distinct()
        if (rs.size == 2 && m.ev(F.Plus(rs[0], rs[1])).isZero) rs = rs.filter { !it.isNegativeSigned }.ifEmpty { rs.take(1) }
        return if (rs.size == 1) rs[0] else F.List(*rs.toTypedArray())
    }

    // ------------------------------------------------------------------ resolver(...)

    private fun resolver(n: No.Chama, c: Contexto): IExpr {
        aridade(n.nome, n.args, 2, 3, 4)
        val nomes = nomes(n.args[1], n.nome)
        val eqs = (n.args[0] as? No.Matriz)?.linhas?.flatten()
        if (eqs == null && nomes.size == 1) {
            val pal = if (n.args.size == 3) avaliar(n.args[2], c) else null
            val (a, b) = if (n.args.size == 4) avaliar(n.args[2], c) to avaliar(n.args[3], c) else null to null
            return escalar(n.args[0], Pedido(n.nome, nomes[0], pal, a, b, umaSo = false), c)
        }
        if (n.args.size != 2) throw ErroCalculo("${n.nome}: para sistemas, use ${n.nome}([eq1; eq2], [x; y]), sem palpite.")
        return sistema(eqs ?: listOf(n.args[0]), nomes, n.nome, c)
    }

    private fun sistema(eqs: List<No>, nomes: List<String>, fn: String, c: Contexto): IExpr {
        val m = c.pl.motor
        if (nomes.distinct().size != nomes.size) throw ErroCalculo("$fn: incógnita repetida.")
        val xs = nomes.map { m.sym(it) }
        val gs = com(c, nomes.zip(xs).toMap()) { eqs.map { residuo(it, c, fn) } }
        val r = try { m.ev(F.Solve(F.List(*gs.map { F.Equal(it, F.C0) }.toTypedArray()), F.List(*xs.toTypedArray()))) }
                catch (e: Exception) { if (m.interrompido(e)) throw e; null }
        if (r == null || !r.isList || contem(r) { it == S.Solve || it == S.ConditionalExpression || it == S.C })
            throw ErroCalculo("$fn: não consegui resolver o sistema.")
        val sols = (1 until (r as IAST).size()).map { r.get(it) as IAST }
        if (sols.isEmpty()) throw ErroCalculo("$fn: o sistema não tem solução.")
        val valores = sols.map { s ->
            val regras = (1 until s.size()).associate { (s.get(it) as IAST).let { rr -> rr.arg1() to rr.arg2() } }
            if (xs.any { it !in regras } || regras.values.any { v -> xs.any { x -> contem(v) { it == x } } })
                throw ErroCalculo("$fn: o sistema tem infinitas soluções (equações dependentes).")
            xs.map { m.expandirUnidades(m.ev(regras.getValue(it))) }
        }
        val numerica = valores.flatten().none { m.temLivres(it) }
        var escolhidas = valores
        if (numerica) {
            fun z(v: IExpr) = numero(m.ev(F.Divide(v, unidadeDe(v, m))), m)
            escolhidas = valores.filter { vs -> vs.all { v -> z(v)?.let(::real) == true } }
            if (escolhidas.isEmpty()) throw ErroCalculo("$fn: o sistema só tem soluções complexas (nenhuma real).")
            if (escolhidas.size > 1) {
                val positivas = escolhidas.filter { vs -> vs.all { v -> (z(v)?.first ?: -1.0) > 0 } }
                if (positivas.size == 1) escolhidas = positivas
            }
        }
        // uma solução: vetor [x; y]; várias: matriz com uma coluna por solução
        if (escolhidas.size == 1) return F.List(*escolhidas[0].toTypedArray())
        return F.List(*xs.indices.map { i -> F.List(*escolhidas.map { it[i] }.toTypedArray()) }.toTypedArray())
    }

    // ------------------------------------------------------------------ raiz(...)

    private fun raiz(n: No.Chama, c: Contexto): IExpr {
        aridade(n.nome, n.args, 2, 4)
        val nome = (n.args[1] as? No.Id)?.nome ?: throw ErroCalculo("${n.nome}: o 2º argumento deve ser um nome (a variável).")
        if (n.args.size == 4) {
            val a = avaliar(n.args[2], c); val b = avaliar(n.args[3], c)
            return escalar(n.args[0], Pedido(n.nome, nome, null, a, b, umaSo = true), c)
        }
        // raiz(f, x): palpite = o x definido acima
        val pal = c.iter[nome] ?: when (val v = c.env[nome]) {
            is Valor.Expr -> v.e
            is Valor.Faixa_ -> throw UsaFaixa(nome, v.f)
            else -> throw ErroCalculo("${n.nome}: defina um palpite para $nome acima (ex.: $nome := 1) ou dê o intervalo: ${n.nome}(f, $nome, a, b).")
        }
        return escalar(n.args[0], Pedido(n.nome, nome, pal, null, null, umaSo = true), c)
    }

    // ------------------------------------------------------------------ raizes(v)

    private fun polinomio(args: List<IExpr>, c: Contexto): IExpr {
        aridade("raizes", args, 1)
        val m = c.pl.motor
        val v = args[0]
        if (!v.isList || (v as IAST).size() < 3 || (1 until v.size()).any { v.get(it).isList })
            throw ErroCalculo("raizes: espera o vetor de coeficientes [a0; a1; a2; ...] (a0 + a1·x + a2·x² + ...).")
        if (m.temLivres(v)) throw ErroCalculo("raizes: os coeficientes devem ser conhecidos.")
        val x = F.Dummy("x")
        val p = (1 until v.size()).fold(F.C0 as IExpr) { s, k -> F.Plus(s, F.Times(v.get(k), F.Power(x, F.ZZ((k - 1).toLong())))) }
        val g0 = m.ev(p)
        val u = deduzirUnidade(g0, x, m)
        val g = semUnidade(m.ev(F.ReplaceAll(g0, F.Rule(x, F.Times(x, u)))), x, m)
        if (!contem(g) { it == x }) throw ErroCalculo("raizes: o polinômio é constante.")
        val r = try { m.ev(F.NRoots(F.Equal(g, F.C0), x)) } catch (e: Exception) { if (m.interrompido(e)) throw e; null }
            ?: throw ErroCalculo("raizes: não consegui calcular as raízes.")
        val vals = mutableListOf<IExpr>()
        fun anda(e: IExpr) {
            when {
                e.isNumber -> vals += e
                e.isRuleAST -> vals += (e as IAST).arg2()
                e is IAST && (e.isList || e.isOr || e.head() == S.Equal) -> if (e.head() == S.Equal) vals += e.arg2() else (1 until e.size()).forEach { anda(e.get(it)) }
            }
        }
        anda(r)
        if (vals.isEmpty()) throw ErroCalculo("raizes: não consegui calcular as raízes.")
        val nums = vals.mapNotNull { e -> numero(e, m)?.let { z -> z to e } }
            .sortedWith(compareBy({ Math.abs(it.first.second) > TOL }, { it.first.first }, { it.first.second }))
            .map { (z, e) -> if (real(z)) F.num(z.first) else e }
        return F.List(*nums.map { m.ev(F.Times(it, u)) }.toTypedArray())
    }
}
