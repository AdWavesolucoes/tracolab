package tracolab.nucleo.latex

import org.matheclipse.core.expression.F
import org.matheclipse.core.expression.S
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Formato
import tracolab.nucleo.Motor
import tracolab.nucleo.Planilha
import tracolab.nucleo.Regiao
import tracolab.nucleo.UnidadesFolha
import tracolab.nucleo.embutidos.Logica
import tracolab.nucleo.formatarNumero
import kotlin.math.abs
import kotlin.math.max

// Saída de textos, relações, lógica e por partes para a tela (ADR 0013). A TeXForm do Symja não
// escreve "por partes" (sai \text{Piecewise}(...)) e escreve as unidades das relações no SI.

private val OPS: Map<IExpr, String> = mapOf(
    S.Greater to ">", S.Less to "<", S.GreaterEqual to "\\ge ", S.LessEqual to "\\le ",
    S.Equal to "=", S.Unequal to "\\ne ",
)
private val OPS_TEXTO = mapOf("<" to "<", "<=" to "\\le ", ">" to ">", ">=" to "\\ge ", "==" to "=", "!=" to "\\ne ")

/** Texto na tela: \text{...}, com os caracteres especiais do LaTeX escapados. */
fun textoTela(t: String): String = "\\text{" + t.replace("\\", "\\textbackslash ").replace("{", "\\{").replace("}", "\\}")
    .replace("%", "\\%").replace("#", "\\#").replace("&", "\\&").replace("_", "\\_").replace("$", "\\$") + "}"

private fun temCasos(e: IExpr): Boolean =
    e.isAST && (e.head() == S.Piecewise || (1 until (e as IAST).size()).any { temCasos(e.get(it)) })

/**
 * LaTeX de uma expressão com por partes dentro: cada Piecewise vira um marcador, o resto passa
 * pela [LatexSaida.expressao] normal e o marcador volta como `\begin{cases}`. null se não há.
 */
internal fun expressaoComCasos(e: IExpr, m: Motor): String? {
    if (!temCasos(e)) return null
    val casos = mutableListOf<IAST>()
    fun troca(x: IExpr): IExpr {
        if (!x.isAST) return x
        if (x.head() == S.Piecewise) { casos += x as IAST; return F.symbol("zzpcaso${casos.size - 1}") }
        val c = (x as IAST).copy()
        for (i in 1 until c.size()) c.set(i, troca(c.get(i)))
        return c
    }
    val tex = LatexSaida.expressao(troca(e), m)
    return Regex("zzpcaso(\\d+)").replace(tex) { mr -> casosTela(casos[mr.groupValues[1].toInt()]) { LatexSaida.expressao(it, m) } }
}

/** `\begin{cases} v & c \\ ... \\ d & \text{senão} \end{cases}` de um Piecewise. */
private fun casosTela(pw: IAST, parte: (IExpr) -> String): String {
    val linhas = mutableListOf<String>()
    val lista = pw.get(1)
    if (lista is IAST) for (i in 1 until lista.size()) {
        val par = lista.get(i) as? IAST ?: continue
        linhas += parte(par.get(1)) + " & " + parte(par.get(2))
    }
    if (pw.size() > 2 && pw.get(2) != S.Indeterminate) linhas += parte(pw.get(2)) + " & \\text{senão}"
    return "\\begin{cases}" + linhas.joinToString("\\\\ ") + "\\end{cases}"
}

/**
 * Resultado com lógica (relação, ∧ ∨ ¬, por partes, texto) na tela, cada parte nas unidades da
 * folha: `σ_adm ≥ 150 MPa` em vez de `150000000 kg/(m·s²) ≤ σ_adm`. null se não é o caso.
 */
internal fun Planilha.logicaTela(v: IExpr, r: Regiao, f: Formato): String? {
    if (!v.isString && !Logica.ehLogica(v) && !temCasos(v)) return null
    return parteTela(v, r, f)
}

private fun Planilha.parteTela(v: IExpr, r: Regiao, f: Formato): String {
    if (v.isString) return textoTela(v.toString())
    if (v.isTrue) return "1"
    if (v.isFalse) return "0"
    if (v.isAST) {
        val a = v as IAST
        val args = (1 until a.size()).map { a.get(it) }
        when (a.head()) {
            S.Piecewise -> return casosTela(a) { parteTela(it, r, f) }
            S.And, S.Or -> {
                val op = if (a.head() == S.And) " \\land " else " \\lor "
                return args.joinToString(op) { x ->
                    val t = parteTela(x, r, f)
                    if (a.head() == S.And && x.isAST && x.head() == S.Or) "\\left($t\\right)" else t
                }
            }
            S.Not -> return "\\lnot\\left(" + parteTela(args[0], r, f) + "\\right)"
            in OPS.keys -> return args.joinToString(OPS.getValue(a.head())) { parteTela(it, r, f) }
        }
        distribuirCasos(v)?.let { return casosTela(it) { x -> parteTela(x, r, f) } }
    }
    return valorTela(v, r, f)
}

/**
 * Conta com um por partes dentro (3·{2 kN se x > 0; a senão}) vira um por partes de contas
 * ({6 kN se x > 0; 3·a senão}): cada ramo sai nas unidades da folha. null se não há um só.
 */
private fun Planilha.distribuirCasos(v: IExpr): IAST? {
    var achado: IAST? = null
    var quantos = 0
    fun procura(x: IExpr) {
        if (!x.isAST) return
        if (x.head() == S.Piecewise) { quantos++; achado = x as IAST; return }
        for (i in 1 until (x as IAST).size()) procura(x.get(i))
    }
    procura(v)
    val pw = achado ?: return null
    if (quantos != 1 || pw === v) return null
    fun troca(x: IExpr, por: IExpr): IExpr {
        if (x === pw) return por
        if (!x.isAST) return x
        val c = (x as IAST).copy()
        for (i in 1 until c.size()) c.set(i, troca(c.get(i), por))
        return c
    }
    val lista = pw.get(1) as? IAST ?: return null
    val ramos = F.ListAlloc(lista.size())
    for (i in 1 until lista.size()) {
        val par = lista.get(i) as? IAST ?: return null
        ramos.append(F.List(motor.ev(troca(v, par.get(1))), par.get(2)))
    }
    val r = F.ast(S.Piecewise); r.append(ramos)
    if (pw.size() > 2) r.append(if (pw.get(2) == S.Indeterminate) S.Indeterminate else motor.ev(troca(v, pw.get(2))))
    return r
}

/** Um valor dentro de uma relação: número com unidade da folha, ou o pendente como na tela. */
private fun Planilha.valorTela(v: IExpr, r: Regiao, f: Formato): String = try {
    if (motor.temLivres(v)) pendenteTela(v, f)
    else if (temCasos(v)) expressaoComCasos(v, motor)!!
    else escalarTela(v, r, f)
} catch (e: Exception) {
    if (motor.interrompido(e)) throw e
    LatexSaida.expressao(v, motor)
}

/**
 * Os dois lados de uma verificação, na mesma unidade (a escolhida para o maior): `150 MPa ≤ 250 MPa`.
 * null se a região não é verificação numérica.
 */
fun Planilha.verificacaoTela(id: String): String? {
    val r = regioes.first { it.id == id }
    val vf = r.estado?.verificacao ?: return null
    val (a, b) = vf.lados ?: return null
    val op = OPS_TEXTO[vf.op] ?: return null
    return try {
        val f = formatoTela(id)
        val (sa, d) = UnidadesFolha.siDe(a, motor); val (sb, _) = UnidadesFolha.siDe(b, motor)
        val u = unidadeTela(r, d, max(abs(sa), abs(sb)), f)
        fun lado(si: Double) = formatarNumero(UnidadesFolha.valorEm(si, u, motor), f).latex(f) + (u?.let { "\\," + it.latex() } ?: "")
        lado(sa) + op + lado(sb)
    } catch (e: Exception) {
        if (motor.interrompido(e)) throw e
        null
    }
}
