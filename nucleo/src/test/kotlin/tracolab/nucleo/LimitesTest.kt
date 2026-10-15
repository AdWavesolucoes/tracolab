package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.matheclipse.core.expression.F

/** Limites do cálculo (ADR 0017), com orçamentos pequenos para os testes continuarem rápidos. */
class LimitesTest {
    private val motor = Motor()

    // Simplify de (a+b+c+d+e)^40 leva mais de 1 s: serve de "conta lenta" no Symja.
    private val lenta = "(a + b + c + d + e)^40 ->"

    private fun folha(limites: Limites, vararg textos: String): Planilha {
        val pl = Planilha(motor = motor, limites = limites)
        textos.forEachIndexed { k, t -> pl.regioes += regiaoDeTexto("r$k", t, 10.0 * (k + 1)) }
        pl.recalcular()
        return pl
    }

    private fun ms(bloco: () -> Unit): Long { val t = System.nanoTime(); bloco(); return (System.nanoTime() - t) / 1_000_000 }

    @Test fun prazoDaRegiaoInterrompeOSymjaEASeguinteCalcula() {
        lateinit var pl: Planilha
        val t = ms { pl = folha(Limites(msRegiao = 200), lenta, "2 + 2 =") }
        val e = pl.regioes[0].estado!!
        assertEquals("erro", e.tipo)
        assertEquals("Cálculo interrompido: passou de 200 ms.", e.msg)
        assertEquals("4", pl.exibir("r1"))
        assertTrue(t < 1500) { "levou $t ms" }
    }

    @Test fun prazoDaFolhaDeixaAsRestantesComErro() {
        val pl = folha(Limites(msRegiao = 5_000, msFolha = 200), lenta, "2 + 2 =")
        assertEquals("Não calculada: a folha passou de 200 ms.", pl.regioes[0].estado!!.msg)
        assertEquals("Não calculada: a folha passou de 200 ms.", pl.regioes[1].estado!!.msg)
    }

    @Test fun motorVoltaAoNormalDepoisDeUmaInterrupcao() {
        folha(Limites(msRegiao = 100), lenta)
        assertTrue(!motor.engine.isStopRequested)
        val pl = folha(Limites(), "x := 3", "x^2 =")
        assertEquals("9", pl.exibir("r1"))
    }

    @Test fun tentarPorDevolveNullEOPrazoDeForaContinua() {
        val r = motor.prazo.com(5_000, "fora") {
            val soma = F.Plus(*listOf("a", "b", "c", "d", "e").map { motor.sym(it) }.toTypedArray())
            val tentativa = motor.prazo.tentarPor(100) { motor.ev(F.Simplify(F.Power(soma, F.ZZ(40)))) }
            assertNull(tentativa)
            motor.ev(F.Plus(F.C1, F.C1))
        }
        assertEquals("2", r.toString())
    }

    @Test fun verificarPrazoNoCodigoProprio() {
        val pl = Planilha(motor = motor, limites = Limites(msRegiao = 50))
        // faixa de 100 mil valores calculados um a um: o laço confere o prazo
        pl.regioes += regiaoDeTexto("i", "i := 0..99999", 10.0)
        pl.regioes += regiaoDeTexto("a", "i^2 + i =", 20.0)
        pl.recalcular()
        assertEquals("Cálculo interrompido: passou de 50 ms.", pl.regioes[1].estado!!.msg)
    }

    @Test fun recursaoLimitadaPelaProfundidade() {
        val pl = folha(Limites(profundidade = 30), "f(n) := n * f(n - 1)", "f(5) =")
        assertEquals("Recursão profunda demais: mais de 30 chamadas aninhadas de f.", pl.regioes[1].estado!!.msg)
    }

    @Test fun formulaProfundaDemaisViraErroDaRegiao() {
        // 200 mil somas encadeadas: a recursão de avaliar estoura a pilha da thread
        var arv: No = No.Num("1")
        repeat(200_000) { arv = No.Bin('+', arv, No.Num("1")) }
        val pl = Planilha(motor = motor)
        pl.regioes += Regiao("a", 10.0, 0.0, "avaliacao_numerica", arv)
        pl.regioes += regiaoDeTexto("b", "2 + 2 =", 20.0)
        pl.recalcular()
        assertEquals("Fórmula ou recursão profunda demais.", pl.regioes[0].estado!!.msg)
        assertEquals("4", pl.exibir("b"))
    }

    @Test fun textoDoTempo() {
        assertEquals("500 ms", textoTempo(500))
        assertEquals("2 s", textoTempo(2_000))
        assertEquals("1,5 s", textoTempo(1_500))
    }
}
