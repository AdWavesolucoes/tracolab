package tracolab.nucleo.latex

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Motor
import tracolab.nucleo.PU
import tracolab.nucleo.PV
import tracolab.nucleo.Planilha
import tracolab.nucleo.decodificar

/** Resultado de uma região pronto para a tela. */
data class ResultadoTela(val latex: String?, val avisoExibicao: String? = null)

/** Converte valores do núcleo para LaTeX (exibição no editor de fórmulas). */
object LatexSaida {

    private val GREGAS_INV: Map<Char, String> = mapOf(
        'α' to "alpha", 'β' to "beta", 'γ' to "gamma", 'δ' to "delta", 'ε' to "varepsilon", 'ζ' to "zeta",
        'η' to "eta", 'θ' to "theta", 'ι' to "iota", 'κ' to "kappa", 'λ' to "lambda", 'μ' to "mu", 'ν' to "nu",
        'ξ' to "xi", 'π' to "pi", 'ρ' to "rho", 'σ' to "sigma", 'τ' to "tau", 'υ' to "upsilon", 'φ' to "varphi",
        'χ' to "chi", 'ψ' to "psi", 'ω' to "omega", 'Γ' to "Gamma", 'Δ' to "Delta", 'Θ' to "Theta",
        'Λ' to "Lambda", 'Ξ' to "Xi", 'Σ' to "Sigma", 'Φ' to "Phi", 'Ψ' to "Psi", 'Ω' to "Omega",
    )

    /** Nome de variável em LaTeX: σ → \sigma, F_max → F_{max}, Ftot → \mathit{Ftot}. */
    fun nome(n: String): String {
        val base = n.substringBefore('_')
        val indice = n.substringAfter('_', "")
        val b = when {
            base.length == 1 -> GREGAS_INV[base[0]]?.let { "\\$it" } ?: base
            else -> "\\mathit{" + base.map { c -> GREGAS_INV[c]?.let { "\\$it " } ?: c.toString() }.joinToString("") + "}"
        }
        return if (indice.isEmpty()) b else "${b}_{$indice}"
    }

    private val SIMBOLO = Regex("(zz[vu])((?:[0-9a-f]{4})+)")

    /** LaTeX de uma expressão do Symja, trocando os nomes codificados pelos nomes reais. */
    fun expressao(e: IExpr, m: Motor): String {
        val tex = m.ev(F.TeXForm(e)).toString()
        val comNomes = SIMBOLO.replace(tex) { mr ->
            val n = decodificar(mr.groupValues[2])
            if (mr.groupValues[1] == PU) "\\mathrm{$n}" else nome(n)
        }
        return comNomes
            .replace(Regex("\\^\\{(-?\\d+)\\.0\\}"), "^{$1}")      // m^{2.0} → m^{2}
            .replace(Regex("(\\d)\\.(\\d)"), "$1{,}$2")                // vírgula decimal
            .replace(Regex("(\\d)\\.(?!\\d)"), "$1")                    // 2. → 2
    }

    /** "8787.21" → "8787{,}21"; "5e-05" → "5\times10^{-5}". */
    fun numero(t: String): String {
        val m = Regex("^(-?[0-9.]+)e([+-])0*([0-9]+)$").find(t)
        val base = (m?.groupValues?.get(1) ?: t).replace(".", "{,}")
        return if (m == null) base else "$base\\times10^{${if (m.groupValues[2] == "-") "-" else ""}${m.groupValues[3]}}"
    }

    /** "kN", "m^2", "kg·m^-1", "kN*m" (unidade pedida pelo teclado) → LaTeX de unidade. */
    fun unidade(t: String): String = t.split("·", "*").joinToString("\\cdot ") { parte ->
        val nomeU = parte.substringBefore('^'); val pot = parte.substringAfter('^', "")
        "\\mathrm{$nomeU}" + if (pot.isEmpty()) "" else "^{$pot}"
    }

    init { check(PV != PU) }
}

/** O que a tela mostra para a região `id` (null quando não há valor a mostrar). */
fun Planilha.resultadoTela(id: String): ResultadoTela {
    val r = regioes.first { it.id == id }
    val e = r.estado ?: return ResultadoTela(null)
    if (e.tipo != "numerico" && e.tipo != "parcial") return ResultadoTela(null)
    e.faixa?.let { return ResultadoTela("${LatexSaida.expressao(it.inicio, motor)}\\,..\\,${LatexSaida.expressao(it.fim, motor)}") }
    if (e.funcao != null) return ResultadoTela(null)
    val v = e.valor ?: return ResultadoTela(null)
    if (v.isList) {
        val itens = (1 until (v as IAST).size()).map { valorSimples(v.get(it)) }
        return ResultadoTela("\\begin{pmatrix}" + itens.joinToString("\\\\") + "\\end{pmatrix}")
    }
    if (e.tipo == "parcial" || r.tipo == "avaliacao_simbolica" || r.tipo == "definicao_simbolica" || motor.temLivres(v))
        return ResultadoTela(LatexSaida.expressao(v, motor))
    val tela = exibir(id)
    if (tela.startsWith("[erro]")) return ResultadoTela(null, tela.removePrefix("[erro]").trim())
    val num = tela.substringBefore(' ')
    val un = tela.substringAfter(' ', "")
    return ResultadoTela(LatexSaida.numero(num) + if (un.isEmpty()) "" else "\\," + LatexSaida.unidade(un))
}

private fun Planilha.valorSimples(x: IExpr): String =
    if (motor.temLivres(x) || motor.temUnidade(x)) LatexSaida.expressao(x, motor)
    else try { LatexSaida.numero(tracolab.nucleo.g6(motor.dbl(x))) } catch (_: Exception) { LatexSaida.expressao(x, motor) }
