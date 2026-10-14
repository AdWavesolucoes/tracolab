package tracolab.nucleo.rascunho

import org.junit.jupiter.api.Test
import tracolab.nucleo.Planilha
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela
import tracolab.nucleo.regiaoDeTexto

class RascunhoTest {
    private fun folha(vararg linhas: String): Planilha {
        val pl = Planilha("parcial")
        linhas.forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", l, 10.0 * (k + 1))) }
        return pl
    }
    @Test fun mostra() {
        val casos = listOf(
            listOf("d := 20{mm}", "A := π*d^2/4", "σ := F/A", "σ =", "d =", "A ="),
            listOf("F := 10{kN}", "L := 6{m}", "M := F*L", "M =", "q := 12{kN/m}", "q*L^2/8 ="),
            listOf("x + 5{m} =", "M = {kN*m}"),
            listOf("i := 0..2", "v[i] := i*2{kN}", "v ="),
            listOf("T := 300{K}", "T =", "v2 := 3{m}/2{s}", "v2 ="),
        )
        for (c in casos) {
            val pl = folha(*c.toTypedArray())
            for (r in pl.regioes) println("${r.id} ${c[r.id.drop(1).toInt()]}  => tipo=${r.estado?.tipo} valor=${r.estado?.valor} | exibir='${runCatching { pl.exibir(r.id) }.getOrElse { it.toString() }}' | tela='${pl.resultadoTela(r.id).latex}'")
            println("----")
        }
    }
}
