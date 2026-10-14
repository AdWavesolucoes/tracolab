package tracolab.nucleo

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import tracolab.nucleo.spec.rodarCaso
import java.io.File

/**
 * Um teste por caso da especificação: spec/casos-comportamento.json (a base) e cada arquivo de
 * spec/casos/ (um por área: matrizes, cálculo, regressões...). A especificação é a fonte única.
 */
class EspecificacaoTest {
    private val motor = Motor()

    @TestFactory
    fun casosDeComportamento(): List<DynamicTest> {
        val base = File(System.getProperty("tracolab.spec") ?: "../spec/casos-comportamento.json")
        val pasta = File(System.getProperty("tracolab.spec.pasta") ?: "../spec/casos")
        val arquivos = listOf(base) + (pasta.listFiles { f -> f.extension == "json" }?.sortedBy { it.name } ?: emptyList())
        val ids = HashSet<String>()
        return arquivos.flatMap { arq ->
            ObjectMapper().readTree(arq)["casos"].map { caso ->
                val id = caso["id"].asText()
                check(ids.add(id)) { "id de caso repetido: $id (${arq.name})" }
                val prefixo = if (arq == base) "" else "${arq.nameWithoutExtension}/"
                DynamicTest.dynamicTest("$prefixo$id ${caso["nome"].asText()}") {
                    val falhas = rodarCaso(caso, motor)
                    assertTrue(falhas.isEmpty()) { falhas.joinToString("\n") }
                }
            }
        }
    }
}
