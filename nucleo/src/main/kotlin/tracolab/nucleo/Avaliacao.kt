package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import java.math.BigDecimal
import java.math.BigInteger

// ------------------------------------------------------------------ avaliação de expressões

class Contexto(
    val env: Map<String, Valor>,
    val pl: Planilha,
    val livres: MutableList<String> = mutableListOf(),
    val iter: MutableMap<String, IExpr> = mutableMapOf(),
    val profundidade: Int = 0,           // chamadas aninhadas de funções do usuário (ADR 0017)
)

fun numeroExato(t: String): IExpr {
    val bd = BigDecimal(t)
    return if (bd.scale() <= 0) F.ZZ(bd.toBigIntegerExact())
    else F.fraction(bd.unscaledValue(), BigInteger.TEN.pow(bd.scale()))
}

fun avaliar(n: No, c: Contexto): IExpr {
    val m = c.pl.motor
    m.verificarPrazo()
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
        // Representação (ADR 0004): uma coluna só ([a; b; c]) é vetor, uma List simples como a de
        // v[i] := ...; senão, List de linhas (List de List). Operações de matriz: embutidos/.
        is No.Matriz -> {
            val linhas = n.linhas.map { l -> l.map { avaliar(it, c) } }
            if (linhas.all { it.size == 1 }) F.List(*linhas.map { it[0] }.toTypedArray())
            else F.List(*linhas.map { F.List(*it.toTypedArray()) }.toTypedArray())
        }
        is No.Texto -> throw NaoSuportado("texto")   // sintaxe reservada; a avaliação fica com a área de lógica
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

private fun chamar(n: No.Chama, c: Contexto): IExpr {
    val m = c.pl.motor
    val f = c.env[n.nome]
    if (f is Valor.Funcao) {
        val vals = n.args.map { avaliar(it, c) }
        if (vals.size != f.params.size) throw ErroCalculo("${n.nome}: número errado de argumentos")
        if (c.profundidade >= c.pl.limites.profundidade)
            throw ErroCalculo("Recursão profunda demais: mais de ${c.pl.limites.profundidade} chamadas aninhadas de ${n.nome}.")
        val env = HashMap(f.env); f.params.zip(vals).forEach { (p, v) -> env[p] = Valor.Expr(v) }
        return avaliar(f.corpo, Contexto(env, c.pl, c.livres, profundidade = c.profundidade + 1))
    }
    if (f != null) throw ErroCalculo("${n.nome} não é uma função")
    // embutidos: registro em Embutidos (um arquivo por área em embutidos/)
    Embutidos.especiais[n.nome]?.let { return it.fn(n, c) }
    val vals = n.args.map { avaliar(it, c) }
    Embutidos.funcoes[n.nome]?.let { return it.chamar(vals, c) }
    // função ainda não definida: fica livre
    if (n.nome !in c.livres) c.livres += n.nome
    val ast = F.ast(m.sym(n.nome)); vals.forEach { ast.append(it) }
    return ast
}

