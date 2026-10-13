package tracolab.nucleo

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tracolab.nucleo.spec.rodarCaso
import java.io.File

/** Um teste por caso de spec/casos-comportamento.json: a especificação é a fonte única. */
class EspecificacaoTest {
    private val motor = Motor()

    @TestFactory
    fun casosDeComportamento(): List<DynamicTest> {
        val arquivo = File(System.getProperty("tracolab.spec") ?: "../spec/casos-comportamento.json")
        val spec = ObjectMapper().readTree(arquivo)
        return spec["casos"].map { caso ->
            DynamicTest.dynamicTest("${caso["id"].asText()} ${caso["nome"].asText()}") {
                val falhas = rodarCaso(caso, motor)
                assertTrue(falhas.isEmpty()) { falhas.joinToString("\n") }
            }
        }
    }
}
