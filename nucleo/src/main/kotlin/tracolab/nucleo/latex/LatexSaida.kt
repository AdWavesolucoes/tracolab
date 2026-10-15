package tracolab.nucleo.latex

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Matriciais
import tracolab.nucleo.Motor
import tracolab.nucleo.PU
import tracolab.nucleo.PV
import tracolab.nucleo.Planilha
import tracolab.nucleo.decodificar

/** Resultado de uma região pronto para a tela. */
data class ResultadoTela(val latex: String?, val avisoExibicao: String? = null,
                         val sugestoesUnidade: List<SugestaoUnidade> = emptyList())

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

    private val SIMBOLO = Regex("(zz[vub])((?:[0-9a-f]{4})+)")

    /** LaTeX de uma expressão do Symja, trocando os nomes codificados pelos nomes reais. */
    fun expressao(e: IExpr, m: Motor): String {
        expressaoComCasos(e, m)?.let { return it }   // por partes: a TeXForm não escreve cases (SaidaLogica.kt)
        val tex = m.ev(F.TeXForm(e)).toString()
        val comNomes = SIMBOLO.replace(tex) { mr ->
            val n = decodificar(mr.groupValues[2])
            when (mr.groupValues[1]) {
                PU -> "\\mathrm{$n}"
                Matriciais.PB -> "\\operatorname{$n}"   // embutido pendente: rows(x)
                else -> nome(n)
            }
        }
        return comNomes
            .replace(Regex("\\^\\{(-?\\d+)\\.0\\}"), "^{$1}")      // m^{2.0} → m^{2}
            .replace(Regex("(\\d)\\.(\\d)"), "$1{,}$2")                // vírgula decimal
            .replace(Regex("(\\d)\\.(?!\\d)"), "$1")                    // 2. → 2
            .replace(Regex("(\\d)\\*10\\^\\{"), "$1\\\\times10^{")        // 5*10^{6} → 5\times10^{6}
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

/** Linhas de um vetor e tamanho de uma expressão que a tela ainda desenha sem travar (ADR 0017). */
const val MAX_LINHAS_TELA = 100
const val MAX_FOLHAS_TELA = 3_000

/** O que a tela mostra para a região `id` (null quando não há valor a mostrar). */
fun Planilha.resultadoTela(id: String): ResultadoTela {
    val r = regioes.first { it.id == id }
    val e = r.estado ?: return ResultadoTela(null)
    if (e.tipo != "numerico" && e.tipo != "parcial") return ResultadoTela(null)
    e.faixa?.let { return ResultadoTela("${LatexSaida.expressao(it.inicio, motor)}\\,..\\,${LatexSaida.expressao(it.fim, motor)}") }
    if (e.funcao != null) return ResultadoTela(null)
    val v = e.valor ?: return ResultadoTela(null)
    // ADR 0017: a tela não recebe o que não consegue desenhar (expressão gigante); listas: matrizTela resume
    if (v.leafCount() > MAX_FOLHAS_TELA && !v.isList)
        return ResultadoTela(null, "Expressão grande demais para mostrar (${v.leafCount()} termos).")
    if (v.isList) return matrizTela(v, r)
    if (r.tipo == "avaliacao_simbolica" || r.tipo == "definicao_simbolica") return ResultadoTela(LatexSaida.expressao(v, motor))
    // ADR 0014: unidades da folha e formato dos números (TelaUnidades.kt)
    val f = formatoTela(id)
    logicaTela(v, r, f)?.let { return ResultadoTela(it) }   // textos, relações, por partes (SaidaLogica.kt)
    if (e.tipo == "parcial" || motor.temLivres(v)) return ResultadoTela(pendenteTela(v, f))
    return try { ResultadoTela(escalarTela(v, r, f)) }
    catch (err: ErroUnidade) {
        if (r.unidadeExibicao == null) ResultadoTela(null, err.message)
        else diagnosticoUnidade(r, v).let { (msg, sug) -> ResultadoTela(null, msg, sug) }   // o que falta (DiagnosticoUnidades.kt)
    }
    catch (err: Exception) {
        if (motor.interrompido(err)) throw err
        ResultadoTela(LatexSaida.expressao(v, motor))   // número que não é real (2·i): mostra a expressão
    }
}

internal fun Planilha.valorSimples(x: IExpr): String =
    if (motor.temLivres(x) || motor.temUnidade(x)) LatexSaida.expressao(x, motor)
    else try { LatexSaida.numero(tracolab.nucleo.g6(motor.dbl(x))) } catch (_: Exception) { LatexSaida.expressao(x, motor) }
