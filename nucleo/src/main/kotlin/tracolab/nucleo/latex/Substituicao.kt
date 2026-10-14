package tracolab.nucleo.latex

import tracolab.nucleo.Embutidos
import tracolab.nucleo.No
import tracolab.nucleo.Planilha
import tracolab.nucleo.Regiao

/**
 * Substituição explícita, como num memorial de cálculo: a fórmula da região com o valor de cada
 * nome no lugar dele. Para `R := q·L/2 =` com q = 12 kN/m e L = 6 m:
 * `\frac{12\,\mathrm{kN}/\mathrm{m}\cdot 6\,\mathrm{m}}{2}` (a tela completa com "= 36 kN").
 *
 * - O valor de um nome é o da definição vigente na posição da região: a última antes dela na
 *   ordem de cálculo (as globais vêm primeiro). Um número escrito à mão, com ou sem unidade
 *   (`q := 12{kN/m}`), aparece como foi escrito; um valor calculado aparece como a definição
 *   dele se exibe na tela (com a unidade pedida, se houver).
 * - O nome fica quando ainda não há valor numérico: nome que falta (a substituição parcial
 *   mostra o que já se sabe), faixa, função, vetor ou matriz, definição com erro.
 * - Nomes ligados de operadores especiais (o `i` de Σ) nunca são substituídos.
 *
 * Devolve null quando nada foi substituído (a fórmula não tem nome com valor).
 */
fun Planilha.substituicaoTela(id: String): String? {
    val r = regioes.firstOrNull { it.id == id } ?: return null
    val expr = r.expr ?: return null
    if (expr is No.Id) return null   // σ = : a substituição seria o próprio resultado
    val s = Substituidor(this, r)
    val tex = s.latex(expr, emptySet()).s
    return if (s.trocou) tex else null
}

private class Substituidor(val pl: Planilha, val r: Regiao) {
    var trocou = false
    private val ordem = pl.ordenadas()
    private val pos = ordem.indexOfFirst { it.id == r.id }

    /** Trecho de LaTeX e a precedência do que está no topo dele (para decidir os parênteses). */
    class T(val s: String, val p: Int)

    // precedências: comparação 1, soma 2, produto 3, negação 4, potência 5, átomo 9
    private fun par(t: T, minimo: Int) = if (t.p < minimo) "\\left(${t.s}\\right)" else t.s
    private fun parNeg(t: T, minimo: Int) = if (t.p < minimo || t.s.startsWith("-")) "\\left(${t.s}\\right)" else t.s

    fun latex(n: No, lig: Set<String>): T = when (n) {
        is No.Num -> T(numeroLiteral(n.texto), 9)
        is No.Id -> nome(n.nome, lig)
        is No.Unidade -> T(unidade(n.expr), 9)
        is No.Neg -> T("-" + parNeg(latex(n.a, lig), 5), 4)
        is No.Bin -> {
            val a = latex(n.a, lig); val b = latex(n.b, lig)
            when (n.op) {
                '+' -> T("${par(a, 2)}+${parNeg(b, 2)}", 2)
                '-' -> T("${par(a, 2)}-${parNeg(b, 3)}", 2)
                // 20{mm}: número e unidade com espaço fino, sem ponto de multiplicação
                '*' -> if (n.b is No.Unidade) T("${parNeg(a, 3)}\\,${b.s}", 3) else T("${par(a, 3)}\\cdot ${parNeg(b, 3)}", 3)
                '/' -> T("\\frac{${a.s}}{${b.s}}", 9)
                '^' -> T("${parNeg(a, 6)}^{${b.s}}", 5)
                else -> T("${par(a, 3)}${n.op}${par(b, 3)}", 3)
            }
        }
        is No.Cmp -> {
            val op = mapOf(">=" to "\\ge", "<=" to "\\le", "==" to "=", "!=" to "\\ne")[n.op] ?: n.op
            T("${latex(n.a, lig).s}$op ${latex(n.b, lig).s}", 1)
        }
        is No.Chama -> chamada(n, lig)
        is No.Indice -> T(par(latex(n.base, lig + nomeDe(n.base)), 9) + "\\lbrack " +
            n.idx.joinToString(";\\,") { latex(it, lig).s } + "\\rbrack", 9)
        is No.Matriz -> T("\\begin{pmatrix}" + n.linhas.joinToString("\\\\") { l -> l.joinToString("&") { latex(it, lig).s } } +
            "\\end{pmatrix}", 9)
        is No.Texto -> T("\\text{\"${n.valor.replace(Regex("[\\\\{}$&#^_%~]"), "")}\"}", 9)
    }

    /** O vetor de v[i] fica como nome (só os índices são substituídos). */
    private fun nomeDe(n: No): Set<String> = if (n is No.Id) setOf(n.nome) else emptySet()

    private fun nome(nome: String, lig: Set<String>): T {
        if (nome !in lig) valor(nome)?.let { trocou = true; return it }
        if (nome == "π" && definicao(nome) == null) return T("\\pi", 9)
        return T(LatexSaida.nome(nome), 9)
    }

    /** Região cuja definição de [nome] vale na posição da região substituída. */
    private fun definicao(nome: String): Regiao? = (pos - 1 downTo 0).map { ordem[it] }.firstOrNull { nome in it.define }

    /** Valor de [nome] para o memorial, ou null se o nome deve ficar. */
    private fun valor(nome: String): T? {
        val d = definicao(nome) ?: return null
        val e = d.estado ?: return null
        if (e.tipo != "numerico" || e.faixa != null || e.funcao != null || d.tipoAlvo != "variavel") return null
        val v = e.valor ?: return null
        if (v.isList || pl.motor.temLivres(v)) return null
        val expr = d.expr
        if (expr != null && quantidade(expr)) {
            val t = latex(expr, emptySet())
            return T(t.s, if (t.s.startsWith("-")) 4 else if (expr is No.Num) 9 else 3)
        }
        val tela = pl.resultadoTela(d.id).latex ?: return null
        val simples = Regex("^[0-9]+(\\{,\\}[0-9]+)?$").matches(tela)
        return T(tela, if (tela.startsWith("-")) 4 else if (simples) 9 else 3)
    }

    /** Número escrito à mão, com ou sem unidade: 12, 0,02, 20{mm}, -3{kN}, {m}. */
    private fun quantidade(n: No): Boolean = when (n) {
        is No.Num, is No.Unidade -> true
        is No.Neg -> quantidade(n.a)
        is No.Bin -> n.op == '*' && n.a is No.Num && n.b is No.Unidade
        else -> false
    }

    private fun chamada(n: No.Chama, lig: Set<String>): T {
        val esp = Embutidos.especiais[n.nome]
        val ligados = esp?.ligados?.mapNotNull { (n.args.getOrNull(it) as? No.Id)?.nome }?.toSet().orEmpty()
        val args = n.args.map { latex(it, lig + ligados).s }
        val usuario = definicao(n.nome)?.estado?.funcao != null
        if (!usuario) {
            when (n.nome) {
                "sqrt" -> if (args.size == 1) return T("\\sqrt{${args[0]}}", 9)
                "abs" -> if (args.size == 1) return T("\\left|${args[0]}\\right|", 9)
                "floor" -> if (args.size == 1) return T("\\left\\lfloor ${args[0]}\\right\\rfloor", 9)
                "ceil" -> if (args.size == 1) return T("\\left\\lceil ${args[0]}\\right\\rceil", 9)
            }
            FUNCOES_TEX[n.nome]?.let { return T("$it\\left(${args.joinToString(";\\,")}\\right)", 9) }
            if (Embutidos.existe(n.nome)) return T("\\operatorname{${n.nome}}\\left(${args.joinToString(";\\,")}\\right)", 9)
        }
        return T("${LatexSaida.nome(n.nome)}\\left(${args.joinToString(";\\,")}\\right)", 9)
    }

    private companion object {
        val FUNCOES_TEX = mapOf(
            "sin" to "\\sin", "cos" to "\\cos", "tan" to "\\tan", "asin" to "\\arcsin", "acos" to "\\arccos",
            "atan" to "\\arctan", "sinh" to "\\sinh", "cosh" to "\\cosh", "tanh" to "\\tanh", "exp" to "\\exp",
            "ln" to "\\ln", "log" to "\\log", "max" to "\\max", "min" to "\\min",
        )

        /** "0.02" → 0{,}02; "1e-5" → 1\times10^{-5}. */
        fun numeroLiteral(t: String): String {
            val m = Regex("^([0-9.]+)[eE]([+-]?)([0-9]+)$").find(t)
            val base = (m?.groupValues?.get(1) ?: t).let { if (it.startsWith(".")) "0$it" else it }.replace(".", "{,}")
            if (m == null) return base
            val exp = m.groupValues[3].trimStart('0').ifEmpty { "0" }
            return "$base\\times10^{${if (m.groupValues[2] == "-") "-" else ""}$exp}"
        }

        /** Unidade como foi escrita: {kN/m} → \mathrm{kN}/\mathrm{m}; {mm^2} → \mathrm{mm}^{2}. */
        fun unidade(n: No): String = when (n) {
            is No.Id -> "\\mathrm{${n.nome.replace("%", "\\%")}}"
            is No.Num -> n.texto
            is No.Neg -> "-" + unidade(n.a)
            is No.Bin -> when (n.op) {
                '*' -> "${unidade(n.a)}\\cdot ${unidade(n.b)}"
                '/' -> unidade(n.a) + "/" + (if (n.b is No.Bin && n.b.op != '^') "\\left(${unidade(n.b)}\\right)" else unidade(n.b))
                '^' -> "${unidade(n.a)}^{${unidade(n.b)}}"
                else -> unidade(n.a) + n.op + unidade(n.b)
            }
            else -> ""
        }
    }
}
