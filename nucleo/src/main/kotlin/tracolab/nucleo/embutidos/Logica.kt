package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.expression.S
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Contexto
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Especial
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor
import tracolab.nucleo.No
import tracolab.nucleo.aridade
import tracolab.nucleo.avaliar

/**
 * Condicionais e lógica (ADR 0013): `if`/`se`, `cases` (por partes), `and`, `or`, `xor`, `not`.
 *
 * Valores lógicos são 1 e 0, como nas comparações. Uma condição que ainda depende de nomes que
 * faltam não é erro: o resultado fica pendente (por partes simbólica), e os nomes viram chips.
 * `if`, `cases`, `and` e `or` são operadores especiais: só avaliam o que precisam (o ramo
 * escolhido), o que permite recursão com caso base e `x = 0 ∨ 1/x > 2` sem dividir por zero.
 */
object Logica : ModuloEmbutidos {

    private val RELACOES = setOf(S.Greater, S.Less, S.GreaterEqual, S.LessEqual, S.Equal, S.Unequal,
        S.And, S.Or, S.Not, S.Xor, S.True, S.False)

    /** Palavras que, na última linha de um `cases`, querem dizer "nos outros casos". */
    val SENAO = setOf("senão", "senao", "otherwise", "else", "casocontrário", "casocontrario", "c.c.", "cc")

    override fun registrar(r: Embutidos) {
        for (nome in listOf("if", "se")) r.especial(nome, Especial { n, c -> se(n, c) })
        r.especial("cases", Especial { n, c -> casos(n, c) })
        r.especial("and", Especial { n, c -> eOu(n, c, conjuncao = true) })
        r.especial("or", Especial { n, c -> eOu(n, c, conjuncao = false) })
        r.funcao("xor") { a, c ->
            aridade("xor", a, 2)
            val m = c.pl.motor
            val (p, q) = decidir(a[0], m, "xor") to decidir(a[1], m, "xor")
            if (p != null && q != null) logico(p != q)
            else {
                val bp = booleano(a[0], m, "xor"); val bq = booleano(a[1], m, "xor")
                numero(m.ev(F.Or(F.And(bp, F.Not(bq)), F.And(F.Not(bp), bq))))
            }
        }
        r.funcao("not") { a, c ->
            aridade("not", a, 1)
            val m = c.pl.motor
            when (decidir(a[0], m, "not")) {
                true -> F.C0
                false -> F.C1
                null -> numero(m.ev(F.Not(booleano(a[0], m, "not"))))
            }
        }
    }

    // ------------------------------------------------------------------ valores lógicos

    /** Valor lógico de uma condição: verdadeiro, falso ou null se ainda depende de nomes que faltam. */
    fun decidir(v: IExpr, m: Motor, onde: String): Boolean? {
        if (v.isTrue) return true
        if (v.isFalse) return false
        if (v.isString) throw ErroCalculo("$onde: a condição é um texto; compare-o com = ou ≠.")
        if (v.isList) throw ErroCalculo("$onde: a condição deve ser um valor só, não um vetor.")
        if (m.temLivres(v)) return null
        if (m.temUnidade(v) && m.dims(v).any { it != 0.0 })
            throw ErroUnidade("$onde: a condição deve ser uma comparação ou um número sem unidade.")
        val d = try { m.dbl(v) } catch (e: Exception) {
            if (m.interrompido(e)) throw e
            throw ErroCalculo("$onde: não dá para decidir a condição.")
        }
        return d != 0.0
    }

    /** Condição como expressão lógica do Symja (para por partes e e/ou pendentes). */
    private fun booleano(v: IExpr, m: Motor, onde: String): IExpr = when (decidir(v, m, onde)) {
        true -> F.True
        false -> F.False
        null -> if (v.isAST && v.head() in RELACOES) v else F.Unequal(v, F.C0)
    }

    private fun logico(b: Boolean): IExpr = if (b) F.C1 else F.C0

    /** True/False do Symja viram 1/0; o resto (pendente) fica como está. */
    private fun numero(b: IExpr): IExpr = when { b.isTrue -> F.C1; b.isFalse -> F.C0; else -> b }

    // ------------------------------------------------------------------ e / ou

    private fun eOu(n: No.Chama, c: Contexto, conjuncao: Boolean): IExpr {
        val nome = if (conjuncao) "and" else "or"
        aridade(nome, n.args, 2)
        val m = c.pl.motor
        val a = avaliar(n.args[0], c)
        val da = decidir(a, m, nome)
        // curto-circuito: falso ∧ ..., verdadeiro ∨ ... nem avaliam o segundo
        if (da == !conjuncao) return logico(da)
        val b = avaliar(n.args[1], c)
        val db = decidir(b, m, nome)
        if (da != null && db != null) return logico(if (conjuncao) da && db else da || db)
        if (db == !conjuncao) return logico(db)
        val ba = booleano(a, m, nome); val bb = booleano(b, m, nome)
        return numero(m.ev(if (conjuncao) F.And(ba, bb) else F.Or(ba, bb)))
    }

    // ------------------------------------------------------------------ se e por partes

    private fun se(n: No.Chama, c: Contexto): IExpr {
        aridade(n.nome, n.args, 3)
        val m = c.pl.motor
        val cond = avaliar(n.args[0], c)
        return when (decidir(cond, m, n.nome)) {
            true -> avaliar(n.args[1], c)
            false -> avaliar(n.args[2], c)
            null -> pendente(c) { porPartes(listOf(avaliar(n.args[1], c) to cond), avaliar(n.args[2], c), m) }
        }
    }

    /**
     * cases(v₁, c₁, v₂, c₂, ..., [v_senão]): o primeiro vₖ cuja condição cₖ vale. Número ímpar de
     * argumentos: o último é o "senão". Sem "senão" e sem condição verdadeira, é erro.
     */
    private fun casos(n: No.Chama, c: Contexto): IExpr {
        if (n.args.size < 2) throw ErroCalculo("cases: espera pares valor, condição")
        val m = c.pl.motor
        val pares = n.args.chunked(2).filter { it.size == 2 }
        val senaoNo = if (n.args.size % 2 == 1) n.args.last() else null
        val pendentes = mutableListOf<Pair<IExpr, IExpr>>()
        for ((valorNo, condNo) in pares) {
            val cond = avaliar(condNo, c)
            when (decidir(cond, m, "cases")) {
                true -> {
                    if (pendentes.isEmpty()) return avaliar(valorNo, c)
                    // verdadeira depois de pendentes: vira o "senão" deles
                    return pendente(c) { porPartes(pendentes, avaliar(valorNo, c), m) }
                }
                false -> {}
                null -> pendente(c) { pendentes += avaliar(valorNo, c) to cond }
            }
        }
        if (pendentes.isNotEmpty()) return pendente(c) { porPartes(pendentes, senaoNo?.let { avaliar(it, c) }, m) }
        return senaoNo?.let { avaliar(it, c) }
            ?: throw ErroCalculo("Nenhum caso se aplica: nenhuma condição é verdadeira e não há \"senão\".")
    }

    /** Por partes simbólica: Piecewise({{v, cond}, ...}, senão). Sem senão, o resto fica indefinido. */
    private fun porPartes(casos: List<Pair<IExpr, IExpr>>, senao: IExpr?, m: Motor): IExpr {
        val lista = F.List(*casos.map { (v, cond) -> F.List(v, booleano(cond, m, "cases")) }.toTypedArray())
        return m.ev(F.Piecewise(lista, senao ?: F.Indeterminate))
    }

    // ------------------------------------------------------------------ ramos com condição pendente

    /**
     * Com a condição pendente, os dois ramos são avaliados (simbolicamente). Numa função que chama a
     * si mesma com o argumento ainda faltando (fat(n) com n faltando), isso não teria fim: a conta
     * dos ramos assim é limitada, e o erro diz o que falta definir.
     */
    private val emAndamento = ThreadLocal.withInitial { intArrayOf(0, 0) }   // [profundidade, ramos]
    private const val MAX_PROFUNDIDADE = 24
    private const val MAX_RAMOS = 2_000

    private fun <T> pendente(c: Contexto, bloco: () -> T): T {
        val estado = emAndamento.get()
        if (estado[0] == 0) estado[1] = 0
        if (estado[0] >= MAX_PROFUNDIDADE || ++estado[1] > MAX_RAMOS) {
            val faltam = c.livres.joinToString(", ").ifEmpty { "os nomes da condição" }
            throw ErroCalculo("A condição depende de $faltam, que ainda falta, numa conta que se repete sem fim (função recursiva?): defina $faltam.")
        }
        estado[0]++
        try { return bloco() } finally { estado[0]-- }
    }

    // ------------------------------------------------------------------ textos

    /** Comparação com texto: só = e ≠ ("OK" = "OK" vale 1). */
    fun compararTextos(op: String, a: IExpr, b: IExpr, m: Motor): IExpr {
        if (op != "==" && op != "!=") throw ErroCalculo("Textos só se comparam com = e ≠.")
        val r = m.ev(if (op == "==") F.Equal(a, b) else F.Unequal(a, b))
        return numero(r)
    }

    /** Expressão lógica ou relação do Symja (a tela escreve com ≤, ∧, por partes...). */
    fun ehLogica(e: IExpr) = e.isAST && (e.head() in RELACOES || e.head() == S.Piecewise)
}
