package tracolab.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Formato dos números e unidades da folha pelo pedido (ADR 0014). */
class FormatoApiTest {
    private val calc = Calculadora()
    private val json = ObjectMapper()

    private fun calcular(opcoes: Map<String, Any>, vararg regioes: Pair<String, String>) = json.readTree(calc.calcularJson(
        json.writeValueAsString(mapOf("modo" to "parcial", "opcoes" to opcoes,
            "regioes" to regioes.mapIndexed { k, (id, latex) -> mapOf("id" to id, "latex" to latex, "x" to 0, "y" to 10 * (k + 1)) }))))["regioes"]
            .associateBy { it["id"].asText() }

    @Test fun formatoDaFolhaEPorResultado() {
        val r = calcular(mapOf("formato" to mapOf("casas" to 2, "zeros" to true), "formatos" to mapOf("b" to mapOf("casas" to 0))),
            "a" to "\\frac{2}{3}=", "b" to "\\frac{2}{3}=")
        assertEquals("0{,}67", r.getValue("a")["resultado"].asText())
        assertEquals("1", r.getValue("b")["resultado"].asText())
    }

    @Test fun unidadesDaFolhaOuSi() {
        val folha = calcular(emptyMap(), "d" to "d\\tldef20\\,\\mathrm{mm}", "e" to "d=")
        assertEquals("20\\,\\mathrm{mm}", folha.getValue("e")["resultado"].asText())
        assertEquals("20\\,\\mathrm{mm}", folha.getValue("d")["resultado"].asText())   // painel de variáveis
        val si = calcular(mapOf("formato" to mapOf("unidades" to "si")), "d" to "d\\tldef20\\,\\mathrm{mm}", "e" to "d=")
        assertEquals("0{,}02\\,\\mathrm{m}", si.getValue("e")["resultado"].asText())
    }

    @Test fun opcaoInvalidaEIgnorada() {
        val r = calcular(mapOf("formato" to mapOf("casas" to "muitas", "algarismos" to 99)), "a" to "\\frac{2}{3}=")
        assertEquals("0{,}666667", r.getValue("a")["resultado"].asText())
    }
}
