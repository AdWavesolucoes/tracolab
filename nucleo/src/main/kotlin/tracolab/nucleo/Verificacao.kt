package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.embutidos.Logica

/**
 * Verificação de engenharia (ADR 0013): uma comparação avaliada (`σ ≤ σ_adm =`) diz se atende,
 * e quanto da capacidade foi usado.
 *
 * @param ok          atende (true), não atende (false) ou pendente (null: falta um dos lados)
 * @param utilizacao  demanda/capacidade de uma comparação ≤ ou ≥ entre grandezas da mesma
 *                    dimensão (0,6 = 60%); numa conjunção (∧) vale a maior, numa disjunção (∨) a menor
 * @param lados       os dois lados da comparação no topo, já calculados (a tela os mostra nas
 *                    unidades da folha: 150 MPa ≤ 250 MPa)
 * @param op          o operador dessa comparação (<=, >=, <, >, ==, !=)
 */
data class Verificacao(
    val ok: Boolean?,
    val utilizacao: Double? = null,
    val lados: Pair<IExpr, IExpr>? = null,
    val op: String? = null,
)

private val LOGICOS = setOf("and", "or", "not", "xor")

/** A expressão é uma verificação: comparação, ou lógica (∧ ∨ ¬ ⊕) só de verificações. */
private fun ehVerificacao(n: No, c: Contexto): Boolean = when (n) {
    is No.Cmp -> true
    is No.Chama -> n.nome in LOGICOS && c.env[n.nome] !is Valor.Funcao && n.args.all { ehVerificacao(it, c) }
    else -> false
}

/** O estado [e] de uma avaliação, com a verificação quando a expressão [expr] for uma. */
internal fun Planilha.verificado(expr: No, c: Contexto, e: Estado): Estado {
    if (!ehVerificacao(expr, c)) return e
    if (e.tipo == "parcial") return e.copy(verificacao = Verificacao(null))
    val v = e.valor ?: return e
    if (e.tipo != "numerico" || v.isList) return e   // com faixa vira tabela de 1 e 0, sem selo
    val ok = try { Logica.decidir(v, motor, "verificação") } catch (x: Exception) {
        if (motor.interrompido(x)) throw x
        return e
    } ?: return e
    val topo = (expr as? No.Cmp)?.let { lados(it, c) }
    return e.copy(verificacao = Verificacao(ok, utilizacao(expr, c), topo, (expr as? No.Cmp)?.op))
}

/** Os dois lados de uma comparação, numéricos, ou null (texto, vetor, nome faltando, erro). */
private fun Planilha.lados(n: No.Cmp, c: Contexto): Pair<IExpr, IExpr>? = try {
    val cl = Contexto(c.env, this, mutableListOf(), HashMap(c.iter), c.profundidade)
    val a = avaliar(n.a, cl); val b = avaliar(n.b, cl)
    if (a.isString || b.isString || a.isList || b.isList || motor.temLivres(a) || motor.temLivres(b)) null else a to b
} catch (x: Exception) {
    if (motor.interrompido(x)) throw x
    null
}

private fun Planilha.utilizacao(n: No, c: Contexto): Double? = when {
    n is No.Cmp -> lados(n, c)?.let { (a, b) -> utilizacao(n.op, a, b, motor) }
    n is No.Chama && (n.nome == "and" || n.nome == "or") -> {
        val us = n.args.map { utilizacao(it, c) }
        if (us.any { it == null }) null else if (n.nome == "and") us.maxOf { it!! } else us.minOf { it!! }
    }
    else -> null
}

/**
 * Demanda/capacidade de `a op b`. Em `a ≤ b`, a deve ficar abaixo de b: 150 ≤ 250 usa 60%. Em
 * `a ≥ b`, quem fica abaixo é b: R ≥ S usa S/R. Com os dois lados negativos (compressão escrita
 * com sinal, `σ ≥ −f`), a razão se inverte: −150 ≥ −250 usa 60%. Sinais trocados: sem utilização.
 */
fun utilizacao(op: String, a: IExpr, b: IExpr, m: Motor): Double? {
    if (op !in setOf("<", "<=", ">", ">=")) return null
    return try {
        val (va, vb) = if (m.temUnidade(a) || m.temUnidade(b)) {
            val mono = m.monomio(m.dims(if (m.temUnidade(a) && !a.isZero) a else b))
            m.dbl(m.ev(F.Divide(a, mono))) to m.dbl(m.ev(F.Divide(b, mono)))
        } else m.dbl(a) to m.dbl(b)
        val abaixo = if (op.startsWith("<")) va else vb
        val acima = if (op.startsWith("<")) vb else va
        val u = when {
            acima > 0 && abaixo >= 0 -> abaixo / acima
            abaixo < 0 && acima <= 0 -> acima / abaixo
            else -> null
        }
        u?.takeIf { it.isFinite() }
    } catch (x: Exception) {
        if (m.interrompido(x)) throw x
        null
    }
}
