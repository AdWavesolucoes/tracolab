package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.expression.S
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr

/**
 * Vetores e matrizes (ADR 0010). Representação: vetor-coluna é uma List simples ({a, b, c}),
 * qualquer outra matriz é uma List de linhas ({{a, b}, {c, d}}); uma matriz n×1 vira vetor.
 * Cada elemento é uma expressão comum: pode ter unidade própria e nomes que faltam.
 *
 * Operadores (chamados por [avaliar] quando um dos lados é lista):
 * - `+` `−`: elemento a elemento, com as mesmas dimensões; escalar com matriz soma em todos;
 * - `·`: matriz·matriz e matriz·vetor são o produto de matrizes; vetor·vetor é o produto escalar;
 *   escalar·matriz multiplica cada elemento;
 * - `×`: produto vetorial entre vetores de 3 elementos;
 * - `/`: elemento a elemento (dividir por vetor também); dividir por matriz é erro (use a inversa);
 * - `^`: matriz quadrada com expoente inteiro é potência de matriz (−1 é a inversa); vetor é
 *   elemento a elemento; `^T` é a transposta.
 */
object Matriciais {

    const val PB = "zzb"   // cabeça de um embutido que ficou pendente (ex.: rows(x) sem x)

    fun ehLista(e: IExpr) = e.isList
    /** Matriz de duas dimensões (List de linhas). */
    fun ehMatriz(e: IExpr) = e.isList && (e as IAST).argSize() > 0 && (1..e.argSize()).all { e.get(it).isList }
    fun ehVetor(e: IExpr) = e.isList && !ehMatriz(e)

    fun itens(e: IExpr): List<IExpr> = (1..(e as IAST).argSize()).map { e.get(it) }

    /** Linhas de uma lista (vetor = n×1; escalar = 1×1). */
    fun linhas(e: IExpr): List<List<IExpr>> {
        if (!e.isList) return listOf(listOf(e))
        val xs = itens(e)
        if (xs.isEmpty()) throw ErroCalculo("Matriz vazia.")
        if (xs.none { it.isList }) return xs.map { listOf(it) }
        if (!xs.all { it.isList }) throw ErroCalculo("Matriz irregular: misture escalares e linhas não.")
        val ls = xs.map { itens(it) }
        if (ls.any { l -> l.any { it.isList } }) throw ErroCalculo("O elemento de uma matriz deve ser um escalar.")
        if (ls.any { it.size != ls[0].size }) throw ErroCalculo("Matriz irregular: as linhas têm tamanhos diferentes.")
        return ls
    }

    /** Monta a lista canônica a partir das linhas: n×1 vira vetor. */
    fun deLinhas(ls: List<List<IExpr>>): IExpr =
        if (ls.all { it.size == 1 }) F.List(*ls.map { it[0] }.toTypedArray())
        else F.List(*ls.map { F.List(*it.toTypedArray()) }.toTypedArray())

    fun dimensoes(e: IExpr): Pair<Int, Int> = linhas(e).let { it.size to it[0].size }
    private fun texto(d: Pair<Int, Int>) = "${d.first}×${d.second}"

    /** Embutido que não pôde ser calculado porque falta um nome: fica pendente, como `rows(x)`. */
    fun pendente(nome: String, args: List<IExpr>): IExpr {
        val ast = F.ast(F.symbol(PB + codificar(nome))); args.forEach { ast.append(it) }
        return ast
    }

    // ------------------------------------------------------------------ operadores

    fun binario(op: Char, a: IExpr, b: IExpr, m: Motor): IExpr = when (op) {
        '+', '-' -> elementoAElemento(a, b, m) { x, y ->
            m.checarSoma(x, y); m.ev(if (op == '+') F.Plus(x, y) else F.Subtract(x, y))
        }
        '*' -> if (a.isList && b.isList) produto(a, b, m) else elementoAElemento(a, b, m) { x, y -> m.ev(F.Times(x, y)) }
        '×' -> if (a.isList && b.isList) vetorial(a, b, m) else elementoAElemento(a, b, m) { x, y -> m.ev(F.Times(x, y)) }
        '/' -> {
            if (ehMatriz(b)) throw ErroCalculo("Divisão por matriz: use a inversa (A^{-1}) ou lsolve.")
            elementoAElemento(a, b, m) { x, y ->
                if (y.isZero) throw ErroCalculo("Divisão por zero.")
                m.ev(F.Divide(x, y))
            }
        }
        '^' -> potencia(a, b, m)
        else -> throw NaoSuportado("operador $op com matrizes")
    }

    /** Aplica [f] elemento a elemento; um escalar vale para todos os elementos do outro lado. */
    fun elementoAElemento(a: IExpr, b: IExpr, m: Motor, f: (IExpr, IExpr) -> IExpr): IExpr {
        val la = if (a.isList) linhas(a) else null
        val lb = if (b.isList) linhas(b) else null
        if (la != null && lb != null && (la.size != lb.size || la[0].size != lb[0].size))
            throw ErroCalculo("As dimensões não são compatíveis: ${texto(la.size to la[0].size)} e ${texto(lb.size to lb[0].size)}.")
        val forma = la ?: lb!!
        return deLinhas(forma.indices.map { i ->
            forma[i].indices.map { j -> f(la?.get(i)?.get(j) ?: a, lb?.get(i)?.get(j) ?: b) }
        })
    }

    /** Produto de matrizes; vetor·vetor do mesmo tamanho é o produto escalar. */
    fun produto(a: IExpr, b: IExpr, m: Motor): IExpr {
        val la = linhas(a); val lb = linhas(b)
        if (ehVetor(a) && ehVetor(b)) {
            if (la.size != lb.size) throw ErroCalculo("Produto escalar entre vetores de tamanhos diferentes (${la.size} e ${lb.size}).")
            return somaVerificada(la.indices.map { F.Times(la[it][0], lb[it][0]) }, m)
        }
        if (la[0].size != lb.size)
            throw ErroCalculo("As dimensões não são compatíveis para o produto: ${texto(la.size to la[0].size)} · ${texto(lb.size to lb[0].size)}.")
        val r = la.map { linha -> lb[0].indices.map { j -> somaVerificada(linha.indices.map { k -> F.Times(linha[k], lb[k][j]) }, m) } }
        return if (r.size == 1 && r[0].size == 1) r[0][0] else deLinhas(r)
    }

    /** Soma de termos conferindo que as unidades combinam (como a soma comum). */
    private fun somaVerificada(termos: List<IExpr>, m: Motor): IExpr {
        val ts = termos.map { m.ev(it) }
        for (k in 1 until ts.size) m.checarSoma(ts[0], ts[k])
        val s = m.ev(F.Plus(*ts.toTypedArray()))
        if (m.temUnidade(s) && !m.temLivres(s)) m.dims(s)
        return s
    }

    fun vetorial(a: IExpr, b: IExpr, m: Motor): IExpr {
        if (!ehVetor(a) || !ehVetor(b) || itens(a).size != 3 || itens(b).size != 3)
            throw ErroCalculo("O produto vetorial (×) é entre dois vetores de 3 elementos.")
        val (x1, y1, z1) = itens(a); val (x2, y2, z2) = itens(b)
        fun d(p: IExpr, q: IExpr, r: IExpr, s: IExpr) = somaVerificada(listOf(F.Times(p, q), F.Times(F.CN1, r, s)), m)
        return F.List(d(y1, z2, z1, y2), d(z1, x2, x1, z2), d(x1, y2, y1, x2))
    }

    fun potencia(a: IExpr, b: IExpr, m: Motor): IExpr {
        if (a.isList && b.isList) throw ErroCalculo("Expoente não pode ser uma matriz.")
        if (!a.isList) return elementoAElemento(a, b, m) { x, y -> m.ev(F.Power(x, y)) }
        if (ehVetor(a)) return elementoAElemento(a, b, m) { x, y -> m.ev(F.Power(x, y)) }
        val (nl, nc) = dimensoes(a)
        if (nl != nc) throw ErroCalculo("Potência de matriz: a matriz deve ser quadrada (${texto(nl to nc)}). Elemento a elemento: use vec(...).")
        val n = inteiro(b, m) ?: throw ErroCalculo("Potência de matriz: o expoente deve ser inteiro.")
        var base = if (n < 0) inversa(a, m) else a
        var e = Math.abs(n)
        var r: IExpr = identidade(nl)
        while (e > 0) {   // quadrados sucessivos
            if (e and 1 == 1) r = produto(r, base, m)
            e = e shr 1
            if (e > 0) base = produto(base, base, m)
        }
        return r
    }

    fun identidade(n: Int): IExpr = deLinhas((0 until n).map { i -> (0 until n).map { j -> if (i == j) F.C1 else F.C0 } })

    fun transposta(a: IExpr): IExpr {
        val ls = linhas(a)
        return if (ehVetor(a)) F.List(F.List(*ls.map { it[0] }.toTypedArray()))
        else deLinhas(ls[0].indices.map { j -> ls.map { it[j] } })
    }

    fun quadrada(a: IExpr, oque: String): List<List<IExpr>> {
        if (!a.isList) throw ErroCalculo("$oque: o argumento deve ser uma matriz quadrada.")
        val ls = linhas(a)
        if (ls.size != ls[0].size) throw ErroCalculo("$oque: a matriz deve ser quadrada (${texto(ls.size to ls[0].size)}).")
        return ls
    }

    private fun matrizSymja(ls: List<List<IExpr>>): IExpr = F.List(*ls.map { F.List(*it.toTypedArray()) }.toTypedArray())

    fun determinante(a: IExpr, m: Motor): IExpr {
        val ls = quadrada(a, "Determinante")
        if (ls.size == 1) return ls[0][0]
        val d = m.ev(F.Det(matrizSymja(ls)))
        if (d.isAST(S.Det)) throw ErroCalculo("Não foi possível calcular o determinante.")
        if (m.temUnidade(d) && !m.temLivres(d)) m.dims(d)
        return d
    }

    fun inversa(a: IExpr, m: Motor): IExpr {
        val ls = quadrada(a, "Inversa")
        val det = determinante(a, m)
        if (det.isZero) throw ErroCalculo("A matriz é singular (determinante zero): não tem inversa.")
        val r = m.ev(F.Inverse(matrizSymja(ls)))
        if (!r.isList) throw ErroCalculo("A matriz é singular (determinante zero): não tem inversa.")
        return deLinhas(linhas(r))
    }

    /** |x|: módulo de escalar, comprimento de vetor, determinante de matriz quadrada. */
    fun modulo(a: IExpr, m: Motor): IExpr {
        if (!a.isList) return m.ev(F.Abs(a))
        if (ehMatriz(a)) return determinante(a, m)
        val xs = itens(a)
        val fat = fatorar(xs, m)
        if (fat != null) {
            val (nums, u) = fat
            return m.ev(F.Times(F.Sqrt(F.Plus(*nums.map { F.Sqr(F.Abs(it)) }.toTypedArray())), u))
        }
        return m.ev(F.Sqrt(F.Plus(*xs.map { F.Sqr(it) }.toTypedArray())))
    }

    /**
     * Separa uma lista de valores numéricos de mesma dimensão em números puros e a unidade SI
     * comum. Zeros combinam com qualquer unidade. Null se há nomes faltando; erro se as
     * dimensões diferem.
     */
    fun fatorar(xs: List<IExpr>, m: Motor): Pair<List<IExpr>, IExpr>? {
        if (xs.any { m.temLivres(it) }) return null
        val comUnidade = xs.filter { !it.isZero && m.temUnidade(it) }
        if (comUnidade.isEmpty()) return xs to F.C1
        val d = m.dims(comUnidade[0])
        if (xs.any { !it.isZero && !m.dims(it).contentEquals(d) }) throw ErroUnidade("As unidades não são compatíveis.")
        val u = m.monomio(d)
        return xs.map { m.ev(F.Divide(it, u)) } to u
    }

    /** Valor numérico de um índice (aceita 2.0, que é como um inteiro definido acima chega). */
    fun inteiro(e: IExpr, m: Motor): Int? {
        if (m.temLivres(e) || m.temUnidade(e) || e.isList) return null
        val x = try { m.dbl(e) } catch (_: Exception) { return null }
        val k = Math.rint(x)
        return if (Math.abs(x - k) < 1e-9 && Math.abs(k) < Int.MAX_VALUE) k.toInt() else null
    }

    private fun indiceInteiro(e: IExpr, m: Motor): Int =
        inteiro(e, m) ?: throw ErroCalculo("O índice deve ser um número inteiro.")

    // ------------------------------------------------------------------ índices

    /** Leitura `v[i]` e `M[i, j]`. Vetor ou índice ainda faltando: fica pendente (v_i). */
    fun indice(base: IExpr, idx: List<IExpr>, origem: Int, m: Motor): IExpr {
        if (idx.isEmpty() || idx.size > 2) throw ErroCalculo("Use um índice (vetor) ou dois (matriz).")
        if (idx.any { m.temLivres(it) } || (!base.isList && m.temLivres(base))) {
            val ast = F.ast(S.Subscript); ast.append(base); idx.forEach { ast.append(it) }
            return ast
        }
        if (!base.isList) throw ErroCalculo("Só vetores e matrizes têm índice.")
        val ls = linhas(base)
        val i = indiceInteiro(idx[0], m) - origem
        if (idx.size == 1) {
            if (ehMatriz(base)) throw ErroCalculo("É uma matriz: use dois índices, M[i, j].")
            if (i < 0 || i >= ls.size) throw ErroCalculo("Índice fora do intervalo.")
            return ls[i][0]
        }
        val j = indiceInteiro(idx[1], m) - origem
        if (i < 0 || i >= ls.size || j < 0 || j >= ls[0].size) throw ErroCalculo("Índice fora do intervalo.")
        return ls[i][j]
    }

    /** Definição de elemento: `v[i] := ...` e `M[i, j] := ...`. A matriz cresce com zeros. */
    class Gravador(atual: IExpr?, private val origem: Int, private val m: Motor) {
        private val ls: MutableList<MutableList<IExpr>> =
            if (atual != null && atual.isList) linhas(atual).map { it.toMutableList() }.toMutableList() else mutableListOf()
        private var duasDimensoes = atual != null && ehMatriz(atual)

        fun gravar(idx: List<IExpr>, v: IExpr) {
            if (v.isList) throw ErroCalculo("O elemento deve ser um escalar.")
            if (idx.isEmpty() || idx.size > 2) throw ErroCalculo("Use um índice (vetor) ou dois (matriz).")
            val i = indiceInteiro(idx[0], m) - origem
            val j = if (idx.size == 2) indiceInteiro(idx[1], m) - origem else 0
            if (i < 0 || j < 0) throw ErroCalculo("Índice fora do intervalo.")
            if (idx.size == 1 && duasDimensoes && (ls.firstOrNull()?.size ?: 1) > 1)
                throw ErroCalculo("É uma matriz: use dois índices, M[i, j].")
            if (idx.size == 2) duasDimensoes = true
            val colunas = maxOf(ls.firstOrNull()?.size ?: 1, j + 1)
            ls.forEach { l -> while (l.size < colunas) l += F.C0 }
            while (ls.size <= i) ls += MutableList<IExpr>(colunas) { F.C0 }
            ls[i][j] = v
        }

        fun valor(): IExpr = if (ls.isEmpty()) F.List() else deLinhas(ls)
    }

    // ------------------------------------------------------------------ vetorização

    /**
     * vec(expr): avalia a expressão elemento a elemento, trocando cada vetor/matriz que ela usa
     * pelo elemento da mesma posição (todos com as mesmas dimensões).
     */
    fun vetorizar(corpo: No, c: Contexto): IExpr {
        val arrays = usados(corpo).mapNotNull { n ->
            val v = c.iter[n] ?: (c.env[n] as? Valor.Expr)?.e
            if (v != null && v.isList) n to linhas(v) else null
        }
        if (arrays.isEmpty()) return avaliar(corpo, c)
        val forma = arrays[0].second
        for ((n, ls) in arrays) if (ls.size != forma.size || ls[0].size != forma[0].size)
            throw ErroCalculo("vec: '$n' tem dimensões diferentes (${texto(ls.size to ls[0].size)} e ${texto(forma.size to forma[0].size)}).")
        val antes = arrays.associate { (n, _) -> n to c.iter[n] }
        try {
            return deLinhas(forma.indices.map { i ->
                forma[i].indices.map { j ->
                    for ((n, ls) in arrays) c.iter[n] = ls[i][j]
                    val r = avaliar(corpo, c)
                    if (r.isList) throw ErroCalculo("vec: cada elemento deve dar um escalar.")
                    r
                }
            })
        } finally {
            for ((n, v) in antes) if (v == null) c.iter.remove(n) else c.iter[n] = v
        }
    }
}
