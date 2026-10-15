package tracolab.nucleo.latex

import tracolab.nucleo.embutidos.Logica
import tracolab.nucleo.latex.LatexEntrada.ErroLatex
import tracolab.nucleo.latex.LatexEntrada.Leitor
import tracolab.nucleo.latex.LatexEntrada.Peca
import tracolab.nucleo.latex.LatexEntrada.Tok

// Entrada de textos, lógica e por partes vinda do editor (ADR 0013).

/** Comandos de texto do editor: o conteúdo é um texto ("OK"), nunca um nome. */
internal val COMANDOS_TEXTO = setOf("text", "textnormal", "textrm", "textsf", "texttt", "mbox")

/** Operadores lógicos do editor → sintaxe do núcleo. */
internal val COMANDOS_LOGICOS = mapOf(
    "land" to "∧", "wedge" to "∧", "lor" to "∨", "vee" to "∨",
    "lnot" to "¬", "neg" to "¬", "oplus" to "⊕", "veebar" to "⊕",
)

private val ESCAPES = mapOf(
    "%" to "%", "&" to "&", "#" to "#", "_" to "_", "$" to "$", "{" to "{", "}" to "}", " " to " ",
    "textbackslash" to "\\", "textbraceleft" to "{", "textbraceright" to "}", "textasciicircum" to "^",
    "textasciitilde" to "~", "textquotedblleft" to "\"", "textquotedblright" to "\"", "textquoteleft" to "'",
    "textquoteright" to "'", "textdegree" to "°", "degree" to "°", "textendash" to "–", "textemdash" to "—",
)

/** Conteúdo de um `\text{...}` como foi escrito (com os espaços, que o léxico preserva ali). */
internal fun textoLatex(toks: List<Tok>): String = toks.joinToString("") {
    when (it) {
        is Tok.Ch -> it.c.toString()
        is Tok.Cmd -> ESCAPES[it.nome] ?: LatexEntrada.textoBruto(listOf(it))
        else -> ""
    }
}

/**
 * Peça de um texto. As aspas em volta são opcionais (`\text{"OK"}`, `\text{''OK''}` e `\text{OK}`
 * são o mesmo texto); aspas no meio viram aspas tipográficas, porque a sintaxe do núcleo não tem
 * escape. Os espaços vão protegidos (espaço não separável) da limpeza de espaços do conversor.
 */
internal fun pecaTexto(conteudo: String): Peca {
    var t = conteudo.trim()
    for ((a, f) in listOf("''" to "''", "``" to "''", "\"" to "\"", "“" to "”")) {
        if (t.length >= a.length + f.length && t.startsWith(a) && t.endsWith(f)) { t = t.substring(a.length, t.length - f.length); break }
    }
    return Peca("\"" + t.replace('"', '”').replace(' ', ' ') + "\"", true, true)
}

/** `"OK"` digitado no modo de fórmula (o editor manda as aspas como caracteres). */
internal fun textoEntreAspas(l: Leitor): Peca {
    val dentro = mutableListOf<Tok>()
    while (true) {
        if (l.fim()) throw ErroLatex("texto sem as aspas de fechar")
        val t = l.prox()
        if (t == Tok.Ch('"')) break
        dentro += t
    }
    return pecaTexto(textoLatex(dentro))
}

private val IGUAL_SOZINHO = Regex("(?<![<>!:=])=(?!=)")

/**
 * `\begin{cases} v₁ & c₁ \\ v₂ & c₂ \\ v₃ & \text{senão} \end{cases}` → `cases(v₁, c₁, v₂, c₂, v₃)`.
 * A condição da última linha pode ser "senão" (ou "otherwise", "caso contrário") ou vazia.
 */
internal fun casos(linhas: List<List<String>>): Peca {
    val ls = linhas.filter { l -> l.any { it.isNotBlank() } }
    if (ls.isEmpty()) throw ErroLatex("por partes vazio")
    val args = mutableListOf<String>()
    ls.forEachIndexed { i, l ->
        if (l.size > 2) throw ErroLatex("por partes: cada linha tem um valor e uma condição")
        val valor = l[0]
        if (valor.isBlank()) throw ErroLatex("por partes: linha sem valor")
        val cond = l.getOrElse(1) { "" }
        val senao = cond.isBlank() || cond.trim('"').replace(" ", "").replace(" ", "").lowercase() in Logica.SENAO
        if (senao) {
            if (i != ls.lastIndex) throw ErroLatex("por partes: \"senão\" só na última linha")
            args += valor
        } else args += listOf(valor, IGUAL_SOZINHO.replace(cond, "=="))
    }
    return Peca("cases(" + args.joinToString(", ") + ")", true, true)
}
