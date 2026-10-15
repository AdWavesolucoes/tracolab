package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AmostragemTest {
    private fun estado(expr: String, a: String, b: String, n: Int, vararg antes: Pair<String, String>): Estado {
        val pl = Planilha()
        antes.forEachIndexed { i, (id, t) -> pl.regioes += regiaoDeTexto(id, t, 10.0 * (i + 1)) }
        val e = Parser.parse(expr)
        pl.regioes += Regiao("g", 100.0, 0.0, "grafico", usa = usados(e).filter { it != "x" },
            grafico = EspecGrafico(listOf(e), Parser.parse(a), Parser.parse(b), n))
        pl.recalcular()
        return pl.regioes.first { it.id == "g" }.estado!!
    }

    @Test fun pontosIgualmenteEspacados() {
        val g = estado("2*x + c", "0", "10", 11, "c" to "c := 1").grafico!!
        assertEquals(11, g.x.size)
        assertEquals(listOf(1.0, 3.0, 5.0, 7.0, 9.0, 11.0, 13.0, 15.0, 17.0, 19.0, 21.0), g.series[0]!!.toList())
    }

    @Test fun faltaNomeFicaPendente() {
        val e = estado("q*x", "0", "1", 10)
        assertEquals("parcial", e.tipo)
        assertEquals(listOf("q"), e.livres)
    }

    @Test fun logaritmoDeNegativoEBuraco() {
        val y = estado("ln(x)", "-1", "1", 5).grafico!!.series[0]!!
        assertTrue(y[0].isNaN() && y[1].isNaN() && y[2].isNaN() && !y[3].isNaN())
    }
}
