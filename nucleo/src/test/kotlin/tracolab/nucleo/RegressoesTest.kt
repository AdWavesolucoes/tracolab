package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/** Regressões que não cabem no formato da especificação (área bugs-nucleo). */
class RegressoesTest {

    /** M[1, 1] := 5 gravava v[1] em silêncio (o segundo índice era ignorado). */
    @Test fun elementoDeMatrizNaoViraElementoDeVetor() {
        val pl = Planilha()
        pl.adicionar(regiaoDeTexto("m", "M[1, 1] := 5", 10.0))
        assertNotEquals("{0.0,5.0}", pl.regioes[0].estado!!.valor.toString())
    }
}
