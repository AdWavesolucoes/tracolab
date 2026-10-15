package tracolab.nucleo.latex

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Contexto
import tracolab.nucleo.No
import tracolab.nucleo.Planilha
import tracolab.nucleo.Regiao
import tracolab.nucleo.UnidadesFolha
import tracolab.nucleo.Valor
import tracolab.nucleo.avaliar
import kotlin.math.abs

/** Sugestão de unidade para um número digitado sem unidade: `F := 60` → acrescentar kN na região `regiao`. */
data class SugestaoUnidade(val nome: String, val regiao: String, val unidade: String, val latex: String)

/**
 * Diagnóstico quando a unidade pedida não combina com o resultado (`σ = {MPa}` com σ em 1/mm²).
 * Diz em que o resultado está, a grandeza que falta para chegar à unidade pedida e, se houver,
 * o número digitado sem unidade que resolveria. O caso típico: `F := 60` em vez de `60 {kN}`.
 */
internal fun Planilha.diagnosticoUnidade(r: Regiao, v: IExpr): Pair<String, List<SugestaoUnidade>> {
    val m = motor
    val pedida = r.unidadeExibicao ?: return "A unidade pedida não é compatível com o resultado." to emptyList()
    val obtida = m.dims(v)
    val alvo = m.dims(avaliar(pedida, Contexto(emptyMap(), this)))
    val falta = DoubleArray(7) { alvo[it] - obtida[it] }
    val textoObtido = UnidadesFolha.si(obtida, m)?.texto() ?: "sem unidade"
    val (grandeza, unidadeSug) = GRANDEZAS.firstOrNull { it.dims.contentEquals(falta) }?.let { it.nome to it.unidade }
        ?: (("a unidade " + (UnidadesFolha.si(falta, m)?.texto() ?: "?")) to null)
    val sugestoes = if (unidadeSug == null) emptyList() else semUnidade(r).filter { confirma(it, falta, unidadeSug) }
        .map { SugestaoUnidade(it.nome, it.definicao.id, unidadeSug, latexUnidade(unidadeSug)) }
    val nome = UnidadesFolha.nomeMostrado(r)?.let { "$it está" } ?: "O resultado está"
    val msg = buildString {
        append("$nome em $textoObtido; para ${r.unidadeExibicaoTexto} falta $grandeza.")
        if (sugestoes.isNotEmpty()) append(" Sem unidade: ${sugestoes.joinToString(", ") { it.nome }}.")
    }
    return msg to sugestoes
}

private class Grandeza(val nome: String, val unidade: String, vararg d: Int) {
    val dims = d.map { it.toDouble() }.toDoubleArray()
}

// Grandezas mais comuns na falta de unidade, com a unidade sugerida (a mais usada na prática).
private val GRANDEZAS = listOf(
    Grandeza("uma força", "kN", 1, 1, -2, 0, 0, 0, 0),
    Grandeza("um comprimento", "m", 1, 0, 0, 0, 0, 0, 0),
    Grandeza("uma área", "m^2", 2, 0, 0, 0, 0, 0, 0),
    Grandeza("um volume", "m^3", 3, 0, 0, 0, 0, 0, 0),
    Grandeza("uma tensão (pressão)", "MPa", -1, 1, -2, 0, 0, 0, 0),
    Grandeza("um momento (energia)", "kN*m", 2, 1, -2, 0, 0, 0, 0),
    Grandeza("uma massa", "kg", 0, 1, 0, 0, 0, 0, 0),
    Grandeza("um tempo", "s", 0, 0, 1, 0, 0, 0, 0),
    Grandeza("uma carga por metro", "kN/m", 0, 1, -2, 0, 0, 0, 0),
    Grandeza("uma potência", "kW", 2, 1, -3, 0, 0, 0, 0),
    Grandeza("uma velocidade", "m/s", 1, 0, -1, 0, 0, 0, 0),
    Grandeza("uma corrente", "A", 0, 0, 0, 1, 0, 0, 0),
    Grandeza("uma temperatura", "K", 0, 0, 0, 0, 1, 0, 0),
)

private fun latexUnidade(u: String): String = "\\," + u.split("*").joinToString("\\cdot ") { parte ->
    parte.split("/").joinToString("/") { p ->
        val (n, e) = p.split("^").let { it[0] to it.getOrNull(1) }
        "\\mathrm{$n}" + (e?.let { "^{$it}" } ?: "")
    }
}

/** Região que define `nome` e vale para `r`: a última antes dela na ordem de cálculo. */
private fun Planilha.definicao(nome: String, antesDe: Regiao): Regiao? {
    val ordem = ordenadas()
    val i = ordem.indexOf(antesDe).let { if (it < 0) ordem.size else it }
    return ordem.subList(0, i).lastOrNull { nome in it.define && it.tipo.startsWith("definicao") }
}

/** Nome definido por um número puro, a região que o define e a que o usa. */
private class SemUnidade(val nome: String, val definicao: Regiao, val usuaria: Regiao)

/** Nomes da cadeia de dependências de `r` definidos por um número puro (sem unidade escrita). */
private fun Planilha.semUnidade(r: Regiao): List<SemUnidade> {
    val achados = linkedMapOf<String, SemUnidade>()
    val vistos = HashSet<String>()
    fun anda(reg: Regiao, prof: Int) {
        if (prof > 20) return
        for (n in reg.usa) {
            val d = definicao(n, reg) ?: continue
            if (!vistos.add(d.id)) continue
            val e = d.estado ?: continue
            val numeroPuro = d.tipoAlvo == "variavel" && d.faixa == null && e.tipo == "numerico" &&
                e.valor != null && !motor.temUnidade(e.valor) && !temUnidadeEscrita(d.expr)
            if (numeroPuro) achados.putIfAbsent(n, SemUnidade(n, d, reg)) else anda(d, prof + 1)
        }
    }
    anda(r, 0)
    return achados.values.toList()
}

private fun temUnidadeEscrita(n: No?): Boolean = when (n) {
    null, is No.Num, is No.Id, is No.Texto -> false
    is No.Unidade -> true
    is No.Bin -> temUnidadeEscrita(n.a) || temUnidadeEscrita(n.b)
    is No.Neg -> temUnidadeEscrita(n.a)
    is No.Cmp -> temUnidadeEscrita(n.a) || temUnidadeEscrita(n.b)
    is No.Chama -> n.args.any { temUnidadeEscrita(it) }
    is No.Indice -> temUnidadeEscrita(n.base) || n.idx.any { temUnidadeEscrita(it) }
    is No.Matriz -> n.linhas.any { l -> l.any { temUnidadeEscrita(it) } }
}

/**
 * Confere a sugestão refazendo a conta da região que usa o nome com a unidade acrescentada:
 * vale se a dimensão do resultado dela mudar exatamente pela grandeza que falta (ex.: F aparece no
 * numerador). Se o nome estiver no denominador, a sugestão não serve e é descartada.
 */
private fun Planilha.confirma(c: SemUnidade, falta: DoubleArray, unidade: String): Boolean {
    val nome = c.nome; val usuaria = c.usuaria
    val corpo = usuaria.expr ?: return false
    if (usuaria.tipoAlvo != null && usuaria.tipoAlvo != "variavel") return false
    return try {
        val env = HashMap<String, Valor>()
        for (n in usuaria.usa) {
            val d = definicao(n, usuaria) ?: continue
            val v = d.estado?.valor ?: return false
            env[n] = Valor.Expr(v)
        }
        val antes = motor.dims(avaliar(corpo, Contexto(env, this)))
        val u = unidade.split("*").map { parte ->
            parte.split("/").mapIndexed { i, p ->
                val (n, e) = p.split("^").let { it[0] to (it.getOrNull(1)?.toInt() ?: 1) }
                F.Power(motor.unidade(n), F.ZZ((if (i == 0) e else -e).toLong()))
            }.reduce { a, b -> F.Times(a, b) }
        }.reduce { a, b -> F.Times(a, b) }
        env[nome] = Valor.Expr(motor.ev(F.Times((env[nome] as Valor.Expr).e, u)))
        val depois = motor.dims(avaliar(corpo, Contexto(env, this)))
        (0 until 7).all { abs(depois[it] - antes[it] - falta[it]) < 1e-9 }
    } catch (e: Exception) {
        if (motor.interrompido(e)) throw e
        false
    }
}
