package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.expression.S
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Contexto
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.Especial
import tracolab.nucleo.Faixa
import tracolab.nucleo.Matriciais
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor
import tracolab.nucleo.No
import tracolab.nucleo.UsaFaixa
import tracolab.nucleo.Valor
import tracolab.nucleo.aridade
import tracolab.nucleo.avaliar

/**
 * Operadores de cálculo (ADR 0011): Σ `sum`, Π `prod`, ∫ `int` e d/dx `deriv`.
 *
 * O 2º argumento é o nome ligado (`i`, `x`): dentro do operador ele é a variável de soma ou de
 * integração, não o valor de cima, e nunca vira "falta". Faltar outro nome não é erro: o resultado
 * sai simbólico e pendente (Σ_{i=1}^n i → n(n+1)/2, ∫₀ᴸ q·x dx → q·L²/2).
 *
 * Unidades: a variável ligada carrega a unidade dos limites (∫ e d/dx). Por dentro ela vale
 * X·u, com X adimensional; a integral em X é multiplicada por u e a derivada dividida por uⁿ.
 */
object Calculo : ModuloEmbutidos {

    override fun registrar(r: Embutidos) {
        r.especial("sum", Especial(setOf(1), usaLigado = { it == 2 }) { n, c -> somaProduto(n, c, soma = true) })
        r.especial("prod", Especial(setOf(1), usaLigado = { it == 2 }) { n, c -> somaProduto(n, c, soma = false) })
        r.especial("int", Especial(setOf(1), usaLigado = { it == 2 }) { n, c -> integral(n, c) })
        r.especial("deriv", Especial(setOf(1), usaLigado = { true }) { n, c -> derivada(n, c) })
    }

    // ------------------------------------------------------------------ apoio

    private fun ligado(n: No.Chama): String =
        (n.args[1] as? No.Id)?.nome ?: throw ErroCalculo("${n.nome}: o 2º argumento deve ser um nome (a variável).")

    /** Avalia [bloco] com o nome ligado valendo [valor]; o valor de antes volta no fim. */
    private fun <T> com(c: Contexto, nome: String, valor: IExpr, bloco: () -> T): T {
        val antes = c.iter[nome]
        c.iter[nome] = valor
        try { return bloco() } finally { if (antes == null) c.iter.remove(nome) else c.iter[nome] = antes }
    }

    /** Valor de cima do nome ligado (para d/dx no ponto); faixa repete a região por valor. */
    private fun ponto(nome: String, c: Contexto, antes: IExpr?): IExpr? {
        antes?.let { return it }
        return when (val v = c.env[nome]) {
            is Valor.Expr -> v.e
            is Valor.Faixa_ -> throw UsaFaixa(nome, v.f)
            else -> null
        }
    }

    /** Unidade (monômio SI) de um valor conhecido; 1 se não tem unidade ou ainda falta algo. */
    private fun unidadeDe(v: IExpr, m: Motor): IExpr =
        if (m.temUnidade(v) && !m.temLivres(v) && !v.isList) m.monomio(m.dims(v)) else F.C1

    private fun contem(e: IExpr, cabeca: IExpr): Boolean =
        e is IAST && (e.head() == cabeca || (1 until e.size()).any { contem(e.get(it), cabeca) })

    // ------------------------------------------------------------------ Σ e Π

    private fun somaProduto(n: No.Chama, c: Contexto, soma: Boolean): IExpr {
        aridade(n.nome, n.args, 1, 2, 4)
        val m = c.pl.motor
        if (n.args.size == 1) return elementos(n, c, soma)
        val nome = ligado(n)
        if (n.args.size == 2) {
            // sem limites: percorre a faixa definida acima
            val f = (c.env[nome] as? Valor.Faixa_)?.f
                ?: throw ErroCalculo("${n.nome}: '$nome' deve ser uma variável de faixa (ex.: $nome := 1..10), ou dê os limites.")
            return acumular(f.valores(m, c.pl.limites.elementos), n, nome, c, soma)
        }
        val a = avaliar(n.args[2], c); val b = avaliar(n.args[3], c)
        val ia = Matriciais.inteiro(a, m); val ib = Matriciais.inteiro(b, m)
        if (ia != null && ib != null && ib.toLong() - ia < c.pl.limites.elementos)
            return acumular((ia..ib).map { F.ZZ(it.toLong()) }, n, nome, c, soma)
        if ((ia == null && !m.temLivres(a)) || (ib == null && !m.temLivres(b)))
            throw ErroCalculo("${n.nome}: os limites devem ser números inteiros.")
        // limite ainda faltando: soma simbólica (Σ_{i=1}^n i → n(n+1)/2)
        val s = m.sym(nome)
        val corpo = com(c, nome, s) { avaliar(n.args[0], c) }
        if (corpo.isList) throw ErroCalculo("${n.nome}: com limite faltando, o termo deve ser um valor só.")
        val faixa = F.List(s, a, b)
        return m.ev(if (soma) F.Sum(corpo, faixa) else F.Product(corpo, faixa))
    }

    private fun acumular(valores: List<IExpr>, n: No.Chama, nome: String, c: Contexto, soma: Boolean): IExpr {
        val m = c.pl.motor
        var total: IExpr = if (soma) F.C0 else F.C1
        var primeiro = true
        for ((k, v) in valores.withIndex()) {
            if (k % 256 == 0) m.verificarPrazo()
            val termo = com(c, nome, v) { avaliar(n.args[0], c) }
            total = when {
                primeiro -> termo
                total.isList || termo.isList -> Matriciais.binario(if (soma) '+' else '*', total, termo, m)
                soma -> { m.checarSoma(total, termo); m.ev(F.Plus(total, termo)) }
                else -> m.ev(F.Times(total, termo))
            }
            primeiro = false
        }
        return total
    }

    /** sum(v) e prod(v): soma (produto) dos elementos de um vetor ou matriz. */
    private fun elementos(n: No.Chama, c: Contexto, soma: Boolean): IExpr {
        val m = c.pl.motor
        val arg = n.args[0]
        // sum(i) com i faixa: soma os valores da faixa
        val f = ((arg as? No.Id)?.let { c.env[it.nome] } as? Valor.Faixa_)?.f
        val xs = if (f != null) f.valores(m, c.pl.limites.elementos) else {
            val v = avaliar(arg, c)
            if (!v.isList) {
                if (m.temLivres(v)) return pendenteDe(n.nome, v, c)
                throw ErroCalculo("${n.nome}: com 1 argumento, espera um vetor ou matriz.")
            }
            Matriciais.linhas(v).flatten()
        }
        if (xs.isEmpty()) return if (soma) F.C0 else F.C1
        var total = xs[0]
        for (x in xs.drop(1)) total = if (soma) { m.checarSoma(total, x); m.ev(F.Plus(total, x)) } else m.ev(F.Times(total, x))
        return total
    }

    /** sum(v) com v ainda faltando: fica Σ v pendente (v vira chip, já anotado ao avaliar). */
    private fun pendenteDe(nome: String, v: IExpr, c: Contexto): IExpr =
        F.unaryAST1(if (nome == "sum") S.Total else S.Product, v)

    // ------------------------------------------------------------------ ∫

    private fun integral(n: No.Chama, c: Contexto): IExpr {
        aridade("int", n.args, 2, 4)
        val m = c.pl.motor
        val nome = ligado(n)
        val x = m.sym(nome)
        if (n.args.size == 2) {
            // indefinida: a primitiva (sem constante); com x definido acima, avaliada nesse ponto
            val p = ponto(nome, c, c.iter[nome])
            val u = p?.let { unidadeDe(it, m) } ?: F.C1
            val f = com(c, nome, m.ev(F.Times(x, u))) { avaliar(n.args[0], c) }
            if (f.isList) throw ErroCalculo("int: o integrando deve ser um valor só.")
            val prim = m.ev(F.Times(m.ev(F.Integrate(f, x)), u))
            if (contem(prim, S.Integrate)) throw ErroCalculo("int: não encontrei a primitiva; dê os limites para a integral numérica.")
            return if (p == null) prim else m.ev(F.ReplaceAll(prim, F.Rule(x, m.ev(F.Divide(p, u)))))
        }
        val a = avaliar(n.args[2], c); val b = avaliar(n.args[3], c)
        val conhecidos = listOf(a, b).filter { !m.temLivres(it) }
        val u = if (conhecidos.isEmpty()) F.C1 else Faixa.unidade(m, *conhecidos.toTypedArray())
        val (a1, b1) = m.ev(F.Divide(a, u)) to m.ev(F.Divide(b, u))
        val f = com(c, nome, m.ev(F.Times(x, u))) { avaliar(n.args[0], c) }
        if (f.isList) throw ErroCalculo("int: o integrando deve ser um valor só.")
        val faixa = F.List(x, a1, b1)
        val numerica = m.nomesLivres(f).all { it == nome } && !m.temLivres(a1) && !m.temLivres(b1)
        // simbólica primeiro (por pouco tempo se dá para cair na numérica)
        val simb = if (numerica) m.prazo.tentarPor(TENTATIVA_MS) { integrar(f, faixa, m) } else integrar(f, faixa, m)
        val r = if (simb != null && !contem(simb, S.Integrate) && m.problemaNumerico(m.ev(F.N(simb))) == null) simb
                else if (numerica) numerico(f, x, a1, b1, m)
                else simb ?: throw ErroCalculo("int: não consegui integrar.")
        return m.ev(F.Times(r, u))
    }

    private fun integrar(f: IExpr, faixa: IExpr, m: Motor): IExpr? = try { m.ev(F.Integrate(f, faixa)) } catch (e: Exception) {
        if (m.interrompido(e)) throw e
        null
    }

    /** NIntegrate da parte numérica; a unidade do integrando (constante em X) sai para fora. */
    private fun numerico(f: IExpr, x: IExpr, a: IExpr, b: IExpr, m: Motor): IExpr {
        val uf = try { unidadeDe(m.ev(F.ReplaceAll(f, F.Rule(x, m.ev(F.Divide(F.Plus(a, b), F.C2))))), m) } catch (e: Exception) {
            if (m.interrompido(e)) throw e
            F.C1
        }
        val g = m.ev(F.PowerExpand(F.Divide(f, uf)))
        if (m.temUnidade(g)) throw ErroCalculo("int: as unidades do integrando variam com a variável.")
        // Gauss-Kronrod adaptativo (erro ~1e-13); se não servir (limite infinito...), o método padrão
        val r = try {
            m.ev(F.ternaryAST3(S.NIntegrate, g, F.List(x, a, b), F.Rule(S.Method, F.stringx("GaussKronrod"))))
                .takeIf { it.isNumber } ?: m.ev(F.binaryAST2(S.NIntegrate, g, F.List(x, a, b)))
        } catch (e: Exception) {
            if (m.interrompido(e)) throw e
            throw ErroCalculo("int: a integral numérica não convergiu.")
        }
        if (!r.isNumber || m.problemaNumerico(r) != null) throw ErroCalculo("int: a integral numérica não convergiu.")
        return m.ev(F.Times(r, uf))
    }

    private const val TENTATIVA_MS = 1500L

    // ------------------------------------------------------------------ d/dx

    private fun derivada(n: No.Chama, c: Contexto): IExpr {
        aridade("deriv", n.args, 2, 3)
        val m = c.pl.motor
        val nome = ligado(n)
        val ordem = if (n.args.size == 3) {
            val k = Matriciais.inteiro(avaliar(n.args[2], c), m)
            if (k == null || k < 1 || k > 20) throw ErroCalculo("deriv: a ordem deve ser um inteiro de 1 a 20.")
            k
        } else 1
        val x = m.sym(nome)
        val p = ponto(nome, c, c.iter[nome])
        val u = p?.let { unidadeDe(it, m) } ?: F.C1
        val f = com(c, nome, m.ev(F.Times(x, u))) { avaliar(n.args[0], c) }
        if (f.isList) throw ErroCalculo("deriv: a expressão deve ser um valor só.")
        val d = m.ev(F.Divide(F.binaryAST2(S.D, f, F.List(x, F.ZZ(ordem.toLong()))), F.Power(u, F.ZZ(ordem.toLong()))))
        // x definido acima: a derivada nesse ponto; senão, a derivada simbólica (x vira "falta")
        return if (p == null) d else m.ev(F.ReplaceAll(d, F.Rule(x, m.ev(F.Divide(p, u)))))
    }
}
