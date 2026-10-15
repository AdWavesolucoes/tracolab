package tracolab.nucleo

import java.math.BigDecimal

/** Árvore de expressão da sintaxe de texto do app (a mesma do protótipo Python). */
sealed class No {
    data class Num(val texto: String) : No()
    data class Id(val nome: String) : No()
    data class Unidade(val expr: No) : No()          // {kN/m^2}: dentro, Id = nome de unidade
    data class Bin(val op: Char, val a: No, val b: No) : No()   // + - * / ^
    data class Neg(val a: No) : No()
    data class Cmp(val op: String, val a: No, val b: No) : No()
    data class Chama(val nome: String, val args: List<No>) : No()
    data class Indice(val base: No, val idx: List<No>) : No()
    data class Matriz(val linhas: List<List<No>>) : No()   // [a, b; c, d]: linhas por ';', colunas por ','
    data class Texto(val valor: String) : No()               // "OK"
}

class ErroSintaxe(msg: String) : Exception(msg)

/** Parser descendente recursivo: + - * / ^, chamadas f(a, b), índices v[i], comparações,
 *  unidades entre chaves, inclusive coladas no número (20{mm}), matrizes [1, 2; 3, 4] e textos "OK". */
class Parser(private val s: String) {
    private var p = 0

    companion object {
        fun parse(texto: String): No {
            val pr = Parser(texto)
            val n = pr.comparacao()
            pr.espacos()
            if (pr.p != pr.s.length) throw ErroSintaxe("sobra em '${pr.s.substring(pr.p)}'")
            return n
        }
    }

    private fun espacos() { while (p < s.length && s[p].isWhitespace()) p++ }
    private fun olha(): Char? { espacos(); return if (p < s.length) s[p] else null }
    private fun consome(t: String): Boolean {
        espacos()
        if (s.startsWith(t, p)) { p += t.length; return true }
        return false
    }

    private fun comparacao(): No {
        val a = soma()
        for (op in listOf(">=", "<=", "==", "!=", ">", "<")) {
            if (consome(op)) return No.Cmp(op, a, soma())
        }
        return a
    }

    private fun soma(): No {
        var a = produto()
        while (true) {
            a = when {
                consome("+") -> No.Bin('+', a, produto())
                olha() == '-' && !s.startsWith("->", p) -> { p++; No.Bin('-', a, produto()) }
                else -> return a
            }
        }
    }

    private fun produto(): No {
        var a = unario()
        while (true) {
            a = when {
                consome("*") || consome("·") -> No.Bin('*', a, unario())
                consome("×") -> No.Bin('×', a, unario())   // produto vetorial entre vetores; senão, produto
                consome("/") -> No.Bin('/', a, unario())
                else -> return a
            }
        }
    }

    private fun unario(): No = if (olha() == '-' && !s.startsWith("->", p)) { p++; No.Neg(unario()) } else potencia()

    private fun potencia(): No {
        val base = posfixo()
        return if (consome("^")) No.Bin('^', base, unario()) else base
    }

    private fun posfixo(): No {
        var a = primario()
        while (true) {
            a = when (olha()) {
                '[' -> { p++; val idx = lista(']'); No.Indice(a, idx) }
                '{' -> No.Bin('*', a, unidade())          // 20{mm}
                else -> return a
            }
        }
    }

    private fun lista(fim: Char): List<No> {
        val out = mutableListOf<No>()
        if (olha() == fim) { p++; return out }
        while (true) {
            out += comparacao()
            if (consome(",")) continue
            if (olha() == fim) { p++; return out }
            throw ErroSintaxe("esperava '$fim'")
        }
    }

    private fun primario(): No {
        val c = olha() ?: throw ErroSintaxe("fim inesperado")
        if (c == '(') { p++; val n = comparacao(); if (!consome(")")) throw ErroSintaxe("falta ')'"); return n }
        if (c == '{') return unidade()
        if (c == '[') { p++; return matriz() }
        if (c == '"') return texto()
        if (c.isDigit() || c == '.') return numero()
        if (ehLetra(c)) {
            val nome = identificador()
            if (olha() == '(') { p++; return No.Chama(nome, lista(')')) }
            return No.Id(nome)
        }
        throw ErroSintaxe("caractere inesperado '$c'")
    }

    private fun ehLetra(c: Char) = c.isLetter() || c == '_'

    /** [a, b; c, d] (o '[' já foi lido): linhas separadas por ';', elementos por ','. */
    private fun matriz(): No {
        val linhas = mutableListOf<List<No>>()
        var linha = mutableListOf<No>()
        if (olha() == ']') throw ErroSintaxe("matriz vazia")
        while (true) {
            linha += comparacao()
            when {
                consome(",") -> continue
                consome(";") -> { linhas += linha; linha = mutableListOf() }
                consome("]") -> { linhas += linha; break }
                else -> throw ErroSintaxe("esperava ',', ';' ou ']' na matriz")
            }
        }
        if (linhas.any { it.size != linhas[0].size }) throw ErroSintaxe("linhas da matriz com tamanhos diferentes")
        return No.Matriz(linhas)
    }

    /** "texto" entre aspas duplas, sem escapes. */
    private fun texto(): No {
        val fim = s.indexOf('"', p + 1)
        if (fim < 0) throw ErroSintaxe("falta '\"'")
        val t = s.substring(p + 1, fim)
        p = fim + 1
        return No.Texto(t)
    }

    private fun identificador(): String {
        val i = p
        while (p < s.length && (s[p].isLetterOrDigit() || s[p] == '_')) p++
        return s.substring(i, p)
    }

    private fun numero(): No {
        val i = p
        while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
        if (p < s.length && (s[p] == 'e' || s[p] == 'E') && p + 1 < s.length &&
            (s[p + 1].isDigit() || s[p + 1] == '-' || s[p + 1] == '+')) {
            p += 2
            while (p < s.length && s[p].isDigit()) p++
        }
        val t = s.substring(i, p)
        try { BigDecimal(t) } catch (_: NumberFormatException) { throw ErroSintaxe("número inválido: '$t'") }
        return No.Num(t)
    }

    /** {kN/m^2}: nomes de unidade podem ter μ, Ω, %, °, Δ. */
    private fun unidade(): No {
        if (!consome("{")) throw ErroSintaxe("esperava '{'")
        val fim = s.indexOf('}', p)
        if (fim < 0) throw ErroSintaxe("falta '}'")
        val dentro = s.substring(p, fim)
        p = fim + 1
        return No.Unidade(ParserUnidade(dentro).tudo())
    }
}

/** Expressão dentro das chaves: nomes de unidade, * / ^ e parênteses. */
private class ParserUnidade(private val s: String) {
    private var p = 0
    private fun esp() { while (p < s.length && s[p].isWhitespace()) p++ }

    fun expr(): No {
        var a = fator()
        while (true) {
            esp()
            if (p >= s.length || s[p] == ')') return a
            a = when (s[p]) {
                '*', '·' -> { p++; No.Bin('*', a, fator()) }
                '/' -> { p++; No.Bin('/', a, fator()) }
                else -> No.Bin('*', a, fator())
            }
        }
    }

    /** A unidade inteira: o que sobrar (ex.: "m) kg") é erro, e não ignorado. */
    fun tudo(): No {
        val e = expr(); esp()
        if (p != s.length) throw ErroSintaxe("unidade: sobra em '${s.substring(p)}'")
        return e
    }

    private fun numero(): No.Num {
        val i = p; while (p < s.length && (s[p].isDigit() || s[p] == '.')) p++
        val t = s.substring(i, p)
        if (t.isEmpty() || t.toBigDecimalOrNull() == null) throw ErroSintaxe("unidade: número inválido em '$s'")
        return No.Num(t)
    }

    private fun fator(): No {
        esp()
        if (p >= s.length) throw ErroSintaxe(if (s.isBlank()) "unidade vazia" else "unidade incompleta: '$s'")
        var base: No = if (s[p] == '(') {
            p++; val e = expr(); esp()
            if (p >= s.length || s[p] != ')') throw ErroSintaxe("unidade: falta ')'")
            p++; e
        } else if (s[p].isDigit()) numero()
        else {
            val i = p
            while (p < s.length && !s[p].isWhitespace() && s[p] !in "*/^()·") p++
            if (p == i) throw ErroSintaxe("unidade: '${s[p]}' fora de lugar")
            No.Id(s.substring(i, p))
        }
        esp()
        if (p < s.length && s[p] == '^') {
            p++; esp()
            val neg = if (p < s.length && s[p] == '-') { p++; true } else false
            val n = numero()
            base = No.Bin('^', base, if (neg) No.Neg(n) else n)
        }
        return base
    }
}
