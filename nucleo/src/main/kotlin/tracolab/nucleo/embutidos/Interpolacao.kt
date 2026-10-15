package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ErroCalculo
import tracolab.nucleo.Matriciais
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.Motor

/**
 * Interpolação e busca em tabelas (ADR 0018): `linterp`, `lookup`, `match`, `vlookup`.
 * Posições devolvidas e recebidas seguem a origem dos índices da folha.
 */
object Interpolacao : ModuloEmbutidos {

    override fun registrar(r: Embutidos) {
        // linterp(vx, vy, x): reta entre os dois pontos vizinhos; fora da faixa, prolonga o primeiro/último trecho
        f(r, "linterp", 3) { a, c ->
            val m = c.pl.motor
            val vx = achatar(a[0]); val vy = achatar(a[1])
            if (vx.size != vy.size) throw ErroCalculo("linterp: vx e vy devem ter o mesmo tamanho (${vx.size} e ${vy.size}).")
            if (vx.size < 2) throw ErroCalculo("linterp: precisa de ao menos 2 pontos.")
            val xq = achatar(a[2])
            val (nx, ux) = reais("linterp", vx + xq, m)
            val (ny, uy) = reais("linterp", vy, m)
            val tx = nx.copyOfRange(0, vx.size)
            for (i in 1 until tx.size) if (!(tx[i] > tx[i - 1])) throw ErroCalculo("linterp: vx deve ser estritamente crescente.")
            val ys = (vx.size until nx.size).map { j ->
                val x = nx[j]
                val i = (1 until tx.size).firstOrNull { x <= tx[it] } ?: (tx.size - 1)
                comUnidade(ny[i - 1] + (x - tx[i - 1]) * (ny[i] - ny[i - 1]) / (tx[i] - tx[i - 1]), uy, m)
            }
            if (a[2].isList) F.List(*ys.toTypedArray()) else ys[0]
        }
        // lookup(x, vx, vy): o elemento de vy na posição do valor de vx mais próximo de x (empate: o primeiro)
        f(r, "lookup", 3) { a, c ->
            val m = c.pl.motor
            val vx = achatar(a[1]); val vy = achatar(a[2])
            if (vx.size != vy.size) throw ErroCalculo("lookup: vx e vy devem ter o mesmo tamanho (${vx.size} e ${vy.size}).")
            val (n, _) = reais("lookup", listOf(a[0]) + vx, m)
            val k = (1 until n.size).minByOrNull { Math.abs(n[it] - n[0]) }!! - 1
            vy[k]
        }
        // match(x, v[, tipo]): posição de x em v. tipo 0 (padrão): valor igual; 1: o maior valor ≤ x (v crescente); -1: o menor ≥ x (v decrescente)
        f(r, "match", 2, 3) { a, c ->
            val m = c.pl.motor
            val k = posicao("match", a[0], achatar(a[1]), if (a.size == 3) a[2] else null, m)
            F.ZZ((k + c.pl.origem).toLong())
        }
        // vlookup(z, M, col[, tipo]): procura z na primeira coluna de M (como match) e devolve o elemento da coluna col
        f(r, "vlookup", 3, 4) { a, c ->
            val m = c.pl.motor
            val ls = Matriciais.linhas(a[1])
            val j = (Matriciais.inteiro(a[2], m) ?: throw ErroCalculo("vlookup: a coluna deve ser um inteiro.")) - c.pl.origem
            if (j < 0 || j >= ls[0].size) throw ErroCalculo("vlookup: coluna fora da matriz.")
            ls[posicao("vlookup", a[0], ls.map { it[0] }, if (a.size == 4) a[3] else null, m)][j]
        }
    }

    private fun achatar(e: IExpr): List<IExpr> = if (e.isList) Matriciais.linhas(e).flatten() else listOf(e)

    private fun posicao(nome: String, x: IExpr, v: List<IExpr>, tipoE: IExpr?, m: Motor): Int {
        val tipo = if (tipoE == null) 0 else Matriciais.inteiro(tipoE, m) ?: 2
        if (tipo !in -1..1) throw ErroCalculo("$nome: o tipo deve ser 0, 1 ou -1.")
        val (n, _) = reais(nome, listOf(x) + v, m)
        val z = n[0]; val t = n.copyOfRange(1, n.size)
        val tol = 1e-9 * maxOf(1.0, Math.abs(z))
        val k = when (tipo) {
            0 -> t.indices.firstOrNull { Math.abs(t[it] - z) <= tol }
            1 -> t.indices.lastOrNull { t[it] <= z + tol }
            else -> t.indices.lastOrNull { t[it] >= z - tol }
        }
        return k ?: throw ErroCalculo(if (tipo == 0) "$nome: valor não encontrado." else "$nome: nenhum valor atende (x fora da faixa da tabela).")
    }
}
