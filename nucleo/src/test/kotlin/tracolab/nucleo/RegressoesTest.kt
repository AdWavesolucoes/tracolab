package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.resultadoTela

/** Regressões que não cabem no formato da especificação (área bugs-nucleo). */
class RegressoesTest {

    /** M[1, 1] := 5 gravava v[1] em silêncio (o segundo índice era ignorado). */
    @Test fun elementoDeMatrizNaoViraElementoDeVetor() {
        val pl = Planilha()
        pl.adicionar(regiaoDeTexto("m", "M[1, 1] := 5", 10.0))
        assertNotEquals("{0.0,5.0}", pl.regioes[0].estado!!.valor.toString())
    }

    /** √(−4) = derrubava a página inteira: exibir lançava ArgumentTypeException ao pedir o double. */
    @Test fun numeroNaoRealNaoDerrubaAExibicao() {
        val pl = Planilha()
        listOf("sqrt(-4) =", "[1; sqrt(-1)] =", "sqrt(-4{m^2}) =").forEachIndexed { k, t ->
            pl.adicionar(regiaoDeTexto("r$k", t, 10.0 * (k + 1)))
        }
        for (r in pl.regioes) {
            val tela = pl.resultadoTela(r.id).latex
            assertTrue(tela != null && "i" in tela) { "${r.id}: $tela" }
            pl.exibir(r.id)
        }
    }

    /** Texto malformado dava exceções cruas (NumberFormatException, índice fora da string) ou
     *  era ignorado em silêncio ({m) kg} lia só m): agora é ErroSintaxe com mensagem. */
    @Test fun sintaxeMalformadaEErroDeSintaxe() {
        for (t in listOf(". =", "x := .", "2{} =", "2{m^} =", "2{(m} =", "2{m) kg} =", "2{*m} =", "2{m^-} ="))
            org.junit.jupiter.api.Assertions.assertThrows(ErroSintaxe::class.java, { regiaoDeTexto("r", t, 0.0) }, t)
    }

    /** editar/remover região que não existe davam IndexOutOfBounds/NoSuchElement sem dizer qual. */
    @Test fun regiaoInexistenteDizQual() {
        val pl = Planilha()
        val e1 = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) { pl.editar("zz", regiaoDeTexto("zz", "x := 1", 0.0)) }
        val e2 = org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) { pl.remover("zz") }
        org.junit.jupiter.api.Assertions.assertTrue("zz" in e1.message!! && "zz" in e2.message!!)
    }
}
