package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Contexto
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.Especial
import tracolab.nucleo.Matriciais
import tracolab.nucleo.Matriciais.deLinhas
import tracolab.nucleo.Matriciais.ehMatriz
import tracolab.nucleo.Matriciais.ehVetor
import tracolab.nucleo.Matriciais.inteiro
import tracolab.nucleo.Matriciais.itens
import tracolab.nucleo.Matriciais.linhas
import tracolab.nucleo.Matriciais.pendente
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor
import tracolab.nucleo.aridade

/** Vetores e matrizes (ADR 0010): dimensões, montagem, álgebra linear, ordenação e |x|. */
object Matrizes : ModuloEmbutidos {

    override fun registrar(r: Embutidos) {
        // dimensões (escalar tem 0 linhas e 0 colunas)
        lista(r, "rows", 1) { a, _ -> if (a[0].isList) F.ZZ(linhas(a[0]).size.toLong()) else F.C0 }
        lista(r, "cols", 1) { a, _ -> if (a[0].isList) F.ZZ(linhas(a[0])[0].size.toLong()) else F.C0 }
        lista(r, "length", 1) { a, _ -> F.ZZ(vetor("length", a[0]).size.toLong()) }
        lista(r, "last", 1) { a, c -> F.ZZ((vetor("last", a[0]).size - 1 + c.pl.origem).toLong()) }

        // montagem
        lista(r, "augment", -1) { a, _ ->
            val ls = a.map { linhas(it) }
            if (ls.any { it.size != ls[0].size }) throw ErroCalculo("augment: as matrizes devem ter o mesmo número de linhas.")
            deLinhas(ls[0].indices.map { i -> ls.flatMap { it[i] } })
        }
        lista(r, "stack", -1) { a, _ ->
            val ls = a.map { linhas(it) }
            if (ls.any { it[0].size != ls[0][0].size }) throw ErroCalculo("stack: as matrizes devem ter o mesmo número de colunas.")
            deLinhas(ls.flatten())
        }
        lista(r, "submatrix", 5) { a, c ->
            val ls = linhas(matriz("submatrix", a[0]))
            val (ir, jr, ic, jc) = a.drop(1).map { (inteiro(it, c.pl.motor) ?: throw ErroCalculo("submatrix: os índices devem ser inteiros.")) - c.pl.origem }
            if (ir < 0 || ic < 0 || jr >= ls.size || jc >= ls[0].size || ir > jr || ic > jc) throw ErroCalculo("submatrix: índices fora da matriz.")
            deLinhas((ir..jr).map { i -> (ic..jc).map { j -> ls[i][j] } })
        }
        r.funcao("identity") { a, c ->
            aridade("identity", a, 1)
            val n = inteiro(a[0], c.pl.motor)
            if (n == null || n < 1 || n > 1000) throw ErroCalculo("identity: o argumento deve ser um inteiro positivo.")
            Matriciais.identidade(n)
        }
        lista(r, "diag", 1) { a, _ ->
            if (ehVetor(a[0])) { val xs = itens(a[0]); deLinhas(xs.indices.map { i -> xs.indices.map { j -> if (i == j) xs[i] else F.C0 } }) }
            else { val ls = Matriciais.quadrada(a[0], "diag"); F.List(*ls.indices.map { ls[it][it] }.toTypedArray()) }
        }
        lista(r, "col", 2) { a, c ->
            val ls = linhas(a[0]); val j = indice("col", a[1], c, ls[0].size)
            F.List(*ls.map { it[j] }.toTypedArray())
        }
        lista(r, "row", 2) { a, c ->
            val ls = linhas(a[0]); val i = indice("row", a[1], c, ls.size)
            if (ls[0].size == 1) ls[i][0] else F.List(F.List(*ls[i].toTypedArray()))
        }

        // álgebra linear
        lista(r, "abs", 1) { a, c -> Matriciais.modulo(a[0], c.pl.motor) }
        lista(r, "det", 1) { a, c -> Matriciais.determinante(a[0], c.pl.motor) }
        lista(r, "tr", 1) { a, c ->
            val ls = Matriciais.quadrada(a[0], "tr"); val m = c.pl.motor
            ls.indices.drop(1).forEach { m.checarSoma(ls[0][0], ls[it][it]) }
            m.ev(F.Plus(*ls.indices.map { ls[it][it] }.toTypedArray()))
        }
        lista(r, "rank", 1) { a, c ->
            val m = c.pl.motor
            val num = linhas(a[0]).map { l -> l.map { x -> if (m.temUnidade(x) && !x.isZero) m.ev(F.Divide(x, m.monomio(m.dims(x)))) else x } }
            m.ev(F.unaryAST1(org.matheclipse.core.expression.S.MatrixRank, F.List(*num.map { F.List(*it.toTypedArray()) }.toTypedArray())))
        }
        lista(r, "lsolve", 2) { a, c ->
            val m = c.pl.motor
            val ls = Matriciais.quadrada(a[0], "lsolve")
            val b = vetor("lsolve", a[1])
            if (b.size != ls.size) throw ErroCalculo("lsolve: o vetor deve ter ${ls.size} elementos (tem ${b.size}).")
            if (Matriciais.determinante(a[0], m).isZero) throw ErroCalculo("lsolve: a matriz é singular, o sistema não tem solução única.")
            val x = m.ev(F.LinearSolve(F.List(*ls.map { F.List(*it.toTypedArray()) }.toTypedArray()), F.List(*b.toTypedArray())))
            if (!x.isList) throw ErroCalculo("lsolve: o sistema não tem solução única.")
            x.also { itens(it).forEach { e -> if (m.temUnidade(e) && !m.temLivres(e)) m.dims(e) } }
        }
        lista(r, "eigenvals", 1) { a, c -> autovalores(a[0], c.pl.motor) }
        lista(r, "eigenvecs", 1) { a, c -> autovetores(a[0], c.pl.motor) }

        // ordenação
        lista(r, "sort", 1) { a, c -> F.List(*ordenar(vetor("sort", a[0]), c.pl.motor) { it }.toTypedArray()) }
        lista(r, "reverse", 1) { a, _ -> deLinhas(linhas(a[0]).reversed()) }
        lista(r, "csort", 2) { a, c ->   // linhas ordenadas pela coluna j
            val ls = linhas(matriz("csort", a[0])); val j = indice("csort", a[1], c, ls[0].size)
            deLinhas(ordenar(ls, c.pl.motor) { it[j] })
        }
        lista(r, "rsort", 2) { a, c ->   // colunas ordenadas pela linha i
            val ls = linhas(matriz("rsort", a[0])); val i = indice("rsort", a[1], c, ls.size)
            val cols = ordenar(ls[0].indices.map { j -> ls.map { it[j] } }, c.pl.motor) { it[i] }
            deLinhas(ls.indices.map { k -> cols.map { it[k] } })
        }

        // vetorização explícita: vec(F·d) multiplica elemento a elemento
        r.especial("vec", Especial { n, c ->
            aridade("vec", n.args, 1)
            Matriciais.vetorizar(n.args[0], c)
        })
    }

    /**
     * Registra uma função que recebe vetores/matrizes. Se um argumento é um nome que ainda falta
     * (não é lista e tem nome livre), a função fica pendente, como `rows(x)`. [n] < 0: qualquer quantidade ≥ 1.
     */
    private fun lista(r: Embutidos, nome: String, n: Int, fn: (List<IExpr>, Contexto) -> IExpr) = r.funcao(nome) { a, c ->
        if (n >= 0) aridade(nome, a, n) else if (a.isEmpty()) throw ErroCalculo("$nome: espera ao menos 1 argumento")
        // |x| de um escalar com nome faltando é simbólico (|x|); as demais esperam listas
        if (nome != "abs" && a.any { !it.isList && c.pl.motor.temLivres(it) }) return@funcao pendente(nome, a)
        fn(a, c)
    }

    private fun vetor(nome: String, e: IExpr): List<IExpr> {
        if (!ehVetor(e)) throw ErroCalculo("$nome: o argumento deve ser um vetor.")
        return itens(e)
    }

    private fun matriz(nome: String, e: IExpr): IExpr {
        if (!e.isList) throw ErroCalculo("$nome: o argumento deve ser uma matriz.")
        return e
    }

    private fun indice(nome: String, e: IExpr, c: Contexto, n: Int): Int {
        val k = (inteiro(e, c.pl.motor) ?: throw ErroCalculo("$nome: o índice deve ser inteiro.")) - c.pl.origem
        if (k < 0 || k >= n) throw ErroCalculo("$nome: índice fora do intervalo.")
        return k
    }

    /** Ordena pelo valor numérico da chave (mesma dimensão; os nomes que faltam impedem a ordem). */
    private fun <T> ordenar(xs: List<T>, m: Motor, chave: (T) -> IExpr): List<T> {
        val fat = Matriciais.fatorar(xs.map(chave), m) ?: throw ErroCalculo("Para ordenar, todos os valores precisam ser conhecidos.")
        val nums = fat.first.map {
            try { m.dbl(it) } catch (e: Exception) { if (m.interrompido(e)) throw e; throw ErroCalculo("Para ordenar, os valores devem ser reais.") }
        }
        return xs.indices.sortedBy { nums[it] }.map { xs[it] }
    }

    private fun autovalores(a: IExpr, m: Motor): IExpr {
        val ls = Matriciais.quadrada(a, "eigenvals")
        val (nums, u) = Matriciais.fatorar(ls.flatten(), m) ?: throw ErroCalculo("eigenvals: todos os elementos precisam ser conhecidos.")
        val n = ls.size
        val mat = F.List(*(0 until n).map { i -> F.List(*nums.subList(i * n, i * n + n).toTypedArray()) }.toTypedArray())
        val ev = m.ev(F.Eigenvalues(m.ev(F.N(mat))))
        if (!ev.isList) throw ErroCalculo("eigenvals: não foi possível calcular.")
        return F.List(*itens(ev).map { m.ev(F.Times(it, u)) }.toTypedArray())
    }

    /** Autovetores normalizados, um por coluna, na ordem de eigenvals. */
    private fun autovetores(a: IExpr, m: Motor): IExpr {
        val ls = Matriciais.quadrada(a, "eigenvecs")
        val (nums, _) = Matriciais.fatorar(ls.flatten(), m) ?: throw ErroCalculo("eigenvecs: todos os elementos precisam ser conhecidos.")
        val n = ls.size
        val mat = F.List(*(0 until n).map { i -> F.List(*nums.subList(i * n, i * n + n).toTypedArray()) }.toTypedArray())
        val vs = m.ev(F.Eigenvectors(m.ev(F.N(mat))))
        if (!vs.isList) throw ErroCalculo("eigenvecs: não foi possível calcular.")
        val colunas = itens(vs).map { v ->
            val xs = itens(v)
            val norma = m.ev(F.Sqrt(F.Plus(*xs.map { F.Sqr(F.Abs(it)) }.toTypedArray())))
            xs.map { m.ev(F.Divide(it, norma)) }
        }
        return deLinhas((0 until n).map { i -> colunas.map { it[i] } })
    }
}
