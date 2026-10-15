package tracolab.nucleo.latex

import tracolab.nucleo.latex.LatexEntrada.ErroLatex
import tracolab.nucleo.latex.LatexEntrada.Leitor
import tracolab.nucleo.latex.LatexEntrada.Peca
import tracolab.nucleo.latex.LatexEntrada.Tok
import tracolab.nucleo.latex.LatexEntrada.argumento
import tracolab.nucleo.latex.LatexEntrada.converterSeq
import tracolab.nucleo.latex.LatexEntrada.juntar

// Entrada de Σ, Π, ∫ e d/dx vinda do editor (ADR 0011).
//
// Até onde vai o operando: o somando de Σ/Π e o que vem depois de d/dx vão até o primeiro
// `+`, `−`, `=`, comparação, `→`, `,` ou `;` no mesmo nível (ou até fechar o parêntese de
// fora). Mais de um termo: entre parênteses, Σ (i + 1). O integrando vai até o diferencial
// (`dx`, `\,dx`, `\mathrm{d}x`), que é obrigatório.

internal val OPERADORES_CALCULO = setOf("sum", "prod", "int", "intop")

private val PARADAS_CMD = setOf("to", "rightarrow", "longrightarrow", "Rightarrow", "gt", "lt", "ge", "geq",
    "geqslant", "le", "leq", "leqslant", "ne", "neq", "coloneq", "coloneqq", "Coloneq", "tldef", "equiv", "pm", "\\") +
    COMANDOS_LOGICOS.keys
private val PARADAS_CH = setOf('+', '-', '=', '<', '>', ',', ';', ':', '&')
private val SALTAR = setOf(Tok.Cmd("limits"), Tok.Cmd("nolimits"), Tok.Cmd("displaystyle"))

/** `\sum`, `\prod`, `\int` (o comando já foi lido) com limites e operando → sum(...), prod(...), int(...). */
internal fun lerOperadorCalculo(nome: String, l: Leitor): Peca {
    while (l.olha() in SALTAR) l.prox()
    var baixo: List<Tok>? = null; var cima: List<Tok>? = null
    while (l.olha() == Tok.Ch('_') || l.olha() == Tok.Ch('^')) {
        if ((l.prox() as Tok.Ch).c == '_') baixo = argumento(l) else cima = argumento(l)
    }
    fun txt(ts: List<Tok>) = juntar(converterSeq(ts))
    if (nome == "int" || nome == "intop") {
        val (corpo, x) = integrando(l)
        if (corpo.isEmpty()) throw ErroLatex("∫ sem integrando")
        val lim = when {
            baixo == null && cima == null -> ""
            baixo != null && cima != null -> ", " + txt(baixo) + ", " + txt(cima)
            else -> throw ErroLatex("∫ precisa dos dois limites, ou de nenhum")
        }
        return Peca("int(" + txt(corpo) + ", $x$lim)", true, true)
    }
    val corpo = operando(l)
    if (corpo.isEmpty()) throw ErroLatex("\\$nome sem termo")
    val i = baixo?.indexOf(Tok.Ch('=')) ?: -1
    val args = when {
        baixo == null && cima == null -> ""
        baixo != null && i > 0 && cima != null -> ", " + txt(baixo.subList(0, i)) + ", " + txt(baixo.subList(i + 1, baixo.size)) + ", " + txt(cima)
        baixo != null && i < 0 && cima == null -> ", " + txt(baixo)   // Σ_i: percorre a faixa i
        else -> throw ErroLatex("\\$nome: escreva os limites como _{i=1}^{n}")
    }
    return Peca("$nome(" + txt(corpo) + args + ")", true, true)
}

/** `\frac{d}{dx}` / `\frac{\mathrm{d}^2}{\mathrm{d}x^2}` seguido do operando → deriv(...); null se não é derivada. */
internal fun derivadaFrac(num: List<Tok>, den: List<Tok>, l: Leitor): Peca? {
    val nd = marcaD(num, 0)
    if (nd <= 0) return null
    val ordemNum = ordem(num, nd) ?: return null
    val dd = marcaD(den, 0)
    if (dd <= 0 || dd >= den.size || !ehVariavel(den[dd])) return null
    val x = juntar(converterSeq(listOf(den[dd])))
    val ordemDen = ordem(den, dd + 1) ?: return null
    if (ordemNum.n != null && ordemDen.n != null && ordemNum.n != ordemDen.n) throw ErroLatex("d/dx: a ordem de cima e a de baixo diferem")
    val n = ordemNum.n ?: ordemDen.n
    val corpo = operando(l)
    if (corpo.isEmpty()) throw ErroLatex("d/dx sem expressão")
    return Peca("deriv(" + juntar(converterSeq(corpo)) + ", $x" + (n?.let { ", $it" } ?: "") + ")", true, true)
}

/** Ordem depois da marca: nada (1ª ordem, n = null) ou `^{n}`; outro conteúdo → null (não é derivada). */
private class Ordem(val n: String?)

private fun ordem(ts: List<Tok>, k: Int): Ordem? {
    if (k == ts.size) return Ordem(null)
    if (ts[k] != Tok.Ch('^')) return null
    val l = Leitor(ts.subList(k + 1, ts.size))
    val n = juntar(converterSeq(argumento(l)))
    return if (l.fim()) Ordem(n) else null
}

/** Tamanho da marca de diferencial `d`, `\mathrm{d}`, `\operatorname{d}`, `\differentialD` em [k]; 0 se não há. */
private fun marcaD(ts: List<Tok>, k: Int): Int {
    val t = ts.getOrNull(k) ?: return 0
    if (t == Tok.Ch('d') || t == Tok.Cmd("differentialD") || t == Tok.Cmd("dd")) return 1
    if (t is Tok.Cmd && t.nome in setOf("mathrm", "operatorname", "mathit") &&
        ts.getOrNull(k + 1) == Tok.Abre && ts.getOrNull(k + 2) == Tok.Ch('d') && ts.getOrNull(k + 3) == Tok.Fecha) return 4
    return 0
}

private fun ehVariavel(t: Tok) = (t is Tok.Ch && t.c.isLetter()) || (t is Tok.Cmd && t.nome in LatexEntrada.GREGAS)
private fun ehLetra(t: Tok?) = t is Tok.Ch && t.c.isLetter()

/** Tokens até a primeira parada no nível de fora (o operando de Σ, Π e d/dx). */
private fun operando(l: Leitor): List<Tok> {
    val ini = l.p
    var nivel = 0; var chaves = 0
    while (!l.fim()) {
        val t = l.olha()!!
        if (chaves == 0 && nivel == 0 && l.p > ini &&
            ((t is Tok.Ch && t.c in PARADAS_CH) || (t is Tok.Cmd && t.nome in PARADAS_CMD))) break
        when (t) {
            Tok.Abre -> chaves++
            Tok.Fecha -> chaves--
            Tok.Ch('('), Tok.Ch('['), Tok.Cmd("lparen"), Tok.Cmd("lbrack") -> if (chaves == 0) nivel++
            Tok.Ch(')'), Tok.Ch(']'), Tok.Cmd("rparen"), Tok.Cmd("rbrack") -> if (chaves == 0) { if (nivel == 0) break; nivel-- }
            Tok.Cmd("left") -> if (chaves == 0) { nivel++; l.prox() }
            Tok.Cmd("right") -> if (chaves == 0) { if (nivel == 0) break; nivel--; l.prox() }
            else -> {}
        }
        l.prox()
    }
    return l.toks.subList(ini, l.p)
}

/** Integrando e variável: tudo até o diferencial no nível de fora (`dx`, `\,dx`, `\mathrm{d}x`). */
private fun integrando(l: Leitor): Pair<List<Tok>, String> {
    val ini = l.p
    var nivel = 0
    var k = ini
    val ts = l.toks
    while (k < ts.size) {
        val t = ts[k]
        when (t) {
            Tok.Abre, Tok.Ch('('), Tok.Ch('['), Tok.Cmd("left") -> nivel++
            Tok.Fecha, Tok.Ch(')'), Tok.Ch(']'), Tok.Cmd("right") -> nivel--
            else -> {}
        }
        if (nivel == 0) {
            val d = marcaD(ts, k)
            val v = ts.getOrNull(k + d)
            if (d > 0 && v != null && ehVariavel(v) && !ehLetra(ts.getOrNull(k + d + 1))) {
                val depois = ts.getOrNull(k + d + 1)
                // `dx` solto: antes dele não pode haver letra (qdx), a não ser que seja o fim do integrando
                val fim = depois == null || (depois is Tok.Ch && depois.c in PARADAS_CH) ||
                    (depois is Tok.Cmd && depois.nome in PARADAS_CMD) || depois == Tok.Ch(')')
                if (d > 1 || k == ini || !ehLetra(ts[k - 1]) || fim) {
                    l.p = k + d + 1
                    return ts.subList(ini, k) to juntar(converterSeq(listOf(v)))
                }
            }
        }
        if (nivel < 0) break
        k++
    }
    throw ErroLatex("∫ sem o diferencial: termine com dx")
}
