package tracolab.nucleo.latex

/**
 * Converte o LaTeX produzido pelo editor de fórmulas (MathLive) para a sintaxe de texto do
 * núcleo (spec/README.md). Regras de planilha de engenharia:
 *
 * - letras seguidas formam UM nome (`Ftot` é uma variável, não F·t·o·t); índice entra no nome
 *   (`F_{max}` → `F_max`);
 * - nome seguido de `(` é chamada de função; `\sin x` sem parênteses vira `sin(x)`;
 * - multiplicação implícita só entre operandos separados (`2a`, `(a)(b)`, `2\mathrm{mm}`);
 * - `\mathrm{...}` é SEMPRE unidade (só o teclado de unidades o insere) → `{mm}` (ADR 0007);
 * - `\coloneq`/`:=` define, `\equiv` define global, `=` no fim avalia, `\to` avalia simbólico;
 *   `=` no meio da expressão é comparação;
 * - vírgula decimal (`{,}`) vira ponto.
 */
object LatexEntrada {

    class ErroLatex(msg: String) : Exception(msg)

    private val GREGAS = mapOf(
        "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ε", "varepsilon" to "ε",
        "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "θ", "iota" to "ι", "kappa" to "κ",
        "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "rho" to "ρ", "sigma" to "σ", "tau" to "τ",
        "upsilon" to "υ", "phi" to "φ", "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Sigma" to "Σ",
        "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
    )
    private val FUNCOES = setOf("sin", "cos", "tan", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
        "exp", "ln", "log", "max", "min")
    private val RENOMEIA_FUNCAO = mapOf("arcsin" to "asin", "arccos" to "acos", "arctan" to "atan")
    private val IGNORADOS = setOf(",", ";", "!", ":", " ", "quad", "qquad", "left", "right", "displaystyle",
        "big", "Big", "bigl", "bigr", "Bigl", "Bigr", "mathstrut", "limits", "nolimits")

    // ------------------------------------------------------------------ léxico

    internal sealed class Tok {
        data class Cmd(val nome: String) : Tok()
        data class Ch(val c: Char) : Tok()
        object Abre : Tok()
        object Fecha : Tok()
    }

    internal fun lexico(s: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> {
                    var j = i + 1
                    if (j < s.length && s[j].isLetter()) {
                        while (j < s.length && s[j].isLetter()) j++
                        out += Tok.Cmd(s.substring(i + 1, j))
                    } else if (j < s.length) {
                        out += Tok.Cmd(s[j].toString()); j++
                    }
                    i = j
                }
                c == '{' -> { out += Tok.Abre; i++ }
                c == '}' -> { out += Tok.Fecha; i++ }
                c.isWhitespace() -> i++
                else -> { out += Tok.Ch(c); i++ }
            }
        }
        return out
    }

    // ------------------------------------------------------------------ peças de saída

    /** Um pedaço do texto de saída e se ele começa/termina um operando (para multiplicação implícita). */
    internal data class Peca(val t: String, val inicia: Boolean, val termina: Boolean, val nome: Boolean = false)

    internal class Leitor(val toks: List<Tok>) {
        var p = 0
        fun fim() = p >= toks.size
        fun olha(): Tok? = toks.getOrNull(p)
        fun prox(): Tok = toks[p++]
    }

    /** Lê um grupo `{...}` (ou um token só) e devolve os tokens de dentro. */
    internal fun argumento(l: Leitor): List<Tok> {
        val t = l.olha() ?: throw ErroLatex("argumento faltando")
        if (t == Tok.Abre) {
            l.prox()
            val ini = l.p
            var nivel = 1
            while (!l.fim()) {
                when (l.prox()) {
                    Tok.Abre -> nivel++
                    Tok.Fecha -> { nivel--; if (nivel == 0) return l.toks.subList(ini, l.p - 1) }
                    else -> {}
                }
            }
            throw ErroLatex("chave '{' sem fechar")
        }
        l.prox()
        return listOf(t)
    }

    /** Texto bruto de um argumento (para nomes de unidade, índices, \operatorname). */
    internal fun textoBruto(toks: List<Tok>): String = toks.joinToString("") {
        when (it) {
            is Tok.Ch -> it.c.toString()
            is Tok.Cmd -> GREGAS[it.nome] ?: when (it.nome) { "%" -> "%"; "circ" -> "°"; "mu" -> "μ"; "Omega" -> "Ω"; else -> "" }
            else -> ""
        }
    }

    internal fun converterSeq(toks: List<Tok>): List<Peca> {
        val l = Leitor(toks)
        val out = mutableListOf<Peca>()
        while (!l.fim()) {
            when (val t = l.prox()) {
                is Tok.Ch -> lerCaractere(t.c, l, out)
                is Tok.Cmd -> lerComando(t.nome, l, out)
                Tok.Abre -> {   // grupo solto {..}: vírgula decimal {,} ou só agrupamento
                    l.p--
                    val dentro = argumento(l)
                    if (dentro == listOf(Tok.Ch(','))) anexarNumero('.', out)
                    else out += Peca("(" + juntar(converterSeq(dentro)) + ")", true, true)
                }
                Tok.Fecha -> throw ErroLatex("'}' sem abrir")
            }
            // potência e índice se aplicam ao que acabou de ser lido
            while (l.olha() == Tok.Ch('^') || l.olha() == Tok.Ch('_')) {
                val c = (l.prox() as Tok.Ch).c
                val arg = argumento(l)
                if (c == '_') {
                    val ult = out.removeLastOrNull() ?: throw ErroLatex("índice sem base")
                    if (!ult.nome) throw ErroLatex("índice só em nomes")
                    out += ult.copy(t = ult.t + "_" + textoBruto(arg).filter { it.isLetterOrDigit() })
                } else if (arg.size == 1 && arg[0] == Tok.Cmd("circ")) {
                    out += Peca("{deg}", true, true)
                } else {
                    val ult = out.removeLastOrNull() ?: throw ErroLatex("potência sem base")
                    out += Peca(ult.t + "^(" + juntar(converterSeq(arg)) + ")", ult.inicia, true)
                }
            }
        }
        return out
    }

    private val NUMERO = Regex("[0-9.]+")

    /** Dígitos chegam um a um no LaTeX: junta com o número anterior. */
    private fun anexarNumero(c: Char, out: MutableList<Peca>) {
        val ult = out.lastOrNull()
        if (ult != null && !ult.nome && NUMERO.matches(ult.t)) out[out.size - 1] = Peca(ult.t + c, true, true)
        else out += Peca(c.toString(), true, true)
    }

    private fun lerCaractere(c: Char, l: Leitor, out: MutableList<Peca>) {
        when {
            c.isDigit() || c == '.' -> anexarNumero(c, out)
            c.isLetter() -> {
                val ult = out.lastOrNull()
                // só junta com nome latino (σ seguido de d são dois nomes: σ·d)
                if (ult != null && ult.nome && !ult.t.contains('_') && ult.t.all { it.code < 128 } && c.code < 128)
                    out[out.size - 1] = ult.copy(t = ult.t + c)
                else out += Peca(c.toString(), true, true, nome = true)
            }
            c == '(' -> out += Peca("(", true, false)
            c == ')' -> out += Peca(")", false, true)
            c == '[' -> out += Peca("[", false, false)
            c == ']' -> out += Peca("]", false, true)
            c == '+' || c == '-' || c == '/' || c == '>' || c == '<' || c == ',' -> out += Peca(c.toString(), false, false)
            c == '*' -> out += Peca("*", false, false)
            c == '=' -> out += Peca("=", false, false)
            c == ':' -> {
                if (l.olha() == Tok.Ch('=')) { l.prox(); out += Peca(":=", false, false) }
            }
            c == '|' -> out += Peca("|", true, true)
            c == '\'' -> {}
            else -> throw ErroLatex("caractere não suportado: '$c'")
        }
    }

    private fun lerComando(nome: String, l: Leitor, out: MutableList<Peca>) {
        when {
            nome in IGNORADOS -> {}
            nome == "frac" || nome == "dfrac" || nome == "tfrac" -> {
                val a = juntar(converterSeq(argumento(l))); val b = juntar(converterSeq(argumento(l)))
                out += Peca("(($a)/($b))", true, true)
            }
            nome == "sqrt" -> {
                var indice: String? = null
                if (l.olha() == Tok.Ch('[')) {
                    l.prox(); val ini = l.p
                    while (!l.fim() && l.olha() != Tok.Ch(']')) l.prox()
                    indice = juntar(converterSeq(l.toks.subList(ini, l.p))); l.prox()
                }
                val a = juntar(converterSeq(argumento(l)))
                out += if (indice == null) Peca("sqrt($a)", true, true) else Peca("(($a)^(1/($indice)))", true, true)
            }
            nome == "mathrm" || nome == "unit" -> out += Peca("{" + textoBruto(argumento(l)) + "}", true, true)
            // cor é só apresentação (ex.: := em cobalto): descarta a cor, mantém o conteúdo
            nome == "textcolor" -> { argumento(l); out += converterSeq(argumento(l)) }
            nome == "color" -> argumento(l)
            nome == "operatorname" || nome == "mathit" || nome == "text" || nome == "textit" || nome == "mathbf" -> {
                val n = textoBruto(argumento(l))
                out += Peca(n, true, true, nome = true)
            }
            nome in FUNCOES -> {
                val f = RENOMEIA_FUNCAO[nome] ?: nome
                val prox = l.olha()
                if (prox == Tok.Ch('(') || prox == Tok.Cmd("left")) out += Peca(f, true, true, nome = true)
                else {   // \sin x  →  sin(x): um operando só
                    val arg = mutableListOf<Tok>()
                    if (prox == Tok.Abre) arg += argumento(l) else if (prox != null) arg += l.prox()
                    out += Peca("$f(" + juntar(converterSeq(arg)) + ")", true, true)
                }
            }
            nome in GREGAS -> out += Peca(GREGAS.getValue(nome), true, true, nome = true)
            nome == "pi" -> out += Peca("π", true, true, nome = true)
            nome == "cdot" || nome == "times" || nome == "ast" -> out += Peca("*", false, false)
            nome == "div" -> out += Peca("/", false, false)
            nome == "coloneq" || nome == "coloneqq" || nome == "Coloneq" || nome == "tldef" -> out += Peca(":=", false, false)
            nome == "equiv" -> out += Peca("≡", false, false)
            nome == "to" || nome == "rightarrow" || nome == "longrightarrow" || nome == "Rightarrow" -> out += Peca("->", false, false)
            nome == "gt" -> out += Peca(">", false, false)
            nome == "lt" -> out += Peca("<", false, false)
            nome == "ge" || nome == "geq" || nome == "geqslant" -> out += Peca(">=", false, false)
            nome == "le" || nome == "leq" || nome == "leqslant" -> out += Peca("<=", false, false)
            nome == "ne" || nome == "neq" -> out += Peca("!=", false, false)
            nome == "lbrack" -> out += Peca("[", false, false)
            nome == "rbrack" -> out += Peca("]", false, true)
            nome == "lparen" -> out += Peca("(", true, false)
            nome == "rparen" -> out += Peca(")", false, true)
            nome == "%" -> out += Peca("{%}", true, true)
            nome == "degree" || nome == "circ" -> out += Peca("{deg}", true, true)
            nome == "{" || nome == "}" -> {}
            nome == "begin" -> out += lerAmbiente(l)
            else -> throw ErroLatex("comando não suportado: \\$nome")
        }
    }

    /**
     * `\begin{nome} ... \end{nome}` (o `\begin` já foi lido): separa as células (linhas por `\\`,
     * colunas por `&`, só no nível de fora), converte cada uma e entrega a [ambiente].
     */
    private fun lerAmbiente(l: Leitor): Peca {
        val nome = textoBruto(argumento(l))
        val corpo = mutableListOf<Tok>()
        var nivel = 1
        while (true) {
            if (l.fim()) throw ErroLatex("\\begin{$nome} sem \\end")
            val t = l.prox()
            if (t == Tok.Cmd("begin")) nivel++
            if (t == Tok.Cmd("end") && --nivel == 0) { argumento(l); break }
            corpo += t
        }
        val linhas = mutableListOf<List<String>>()
        var linha = mutableListOf<String>()
        var celula = mutableListOf<Tok>()
        var chaves = 0; var dentro = 0
        fun fechaCelula() { linha += juntar(converterSeq(celula)); celula = mutableListOf() }
        for (t in corpo) {
            when {
                t == Tok.Abre -> chaves++
                t == Tok.Fecha -> chaves--
                t == Tok.Cmd("begin") -> dentro++
                t == Tok.Cmd("end") -> dentro--
            }
            val fora = chaves == 0 && dentro == 0
            when {
                fora && t == Tok.Ch('&') -> fechaCelula()
                fora && t == Tok.Cmd("\\") -> { fechaCelula(); linhas += linha; linha = mutableListOf() }
                else -> celula += t
            }
        }
        if (celula.isNotEmpty() || linha.isNotEmpty()) { fechaCelula(); linhas += linha }
        return ambiente(nome, linhas)
    }

    /** Junta as peças inserindo '*' na multiplicação implícita (exceto nome seguido de '(' ou '['). */
    internal fun juntar(pecas: List<Peca>): String {
        val sb = StringBuilder()
        var ant: Peca? = null
        for (p in pecas) {
            if (ant != null && ant.termina && p.inicia) {
                val chamada = ant.nome && p.t == "("
                if (!chamada) sb.append("*")
            }
            if (sb.isNotEmpty() && !sb.endsWith("(") && !sb.endsWith("*")) sb.append(' ')
            sb.append(p.t)
            ant = p
        }
        return sb.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*\\*\\s*"), "*")                       // a*b
            .replace(Regex("(?<=[\\p{L}\\p{N}_)\\]])\\s+(?=[(\\[])"), "") // f(t), v[i]
            .replace(Regex("([(\\[])\\s+"), "$1")
            .replace(Regex("\\s+([)\\]])"), "$1")
            .trim()
    }

    /** LaTeX de uma região inteira → texto da região. `=` só no fim (ou seguido de unidade) é avaliação. */
    fun regiao(latex: String): String {
        val pecas = converterSeq(lexico(latex))
        val iIgual = pecas.indexOfLast { it.t == "=" }
        if (iIgual >= 0) {
            val depois = pecas.subList(iIgual + 1, pecas.size)
            val soUnidade = depois.all { it.t.startsWith("{") || it.t == "*" || it.t == "/" }
            if (depois.isEmpty() || (soUnidade && depois.any { it.t.startsWith("{") })) {
                val antes = juntar(pecas.subList(0, iIgual))
                // {mm}^(2) → mm^2: dentro das chaves da unidade, nomes sem chaves e expoente simples
                val unidade = if (depois.isEmpty()) "" else " {" + depois.joinToString("") { it.t }
                    .replace(Regex("\\{([^{}]*)\\}"), "$1")
                    .replace(Regex("\\^\\((-?\\d+)\\)"), "^$1") + "}"
                return "$antes =$unidade"
            }
        }
        // '=' no meio é comparação
        return juntar(pecas.map { if (it.t == "=") it.copy(t = "==") else it })
    }
}
