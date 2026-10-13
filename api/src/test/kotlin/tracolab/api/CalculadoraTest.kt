package tracolab.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CalculadoraTest {
    private val calc = Calculadora()
    private val json = ObjectMapper()

    private fun calcular(vararg regioes: Triple<String, String, Double>) = json.readTree(calc.calcularJson(
        json.writeValueAsString(mapOf("modo" to "parcial",
            "regioes" to regioes.map { (id, latex, y) -> mapOf("id" to id, "latex" to latex, "x" to 0, "y" to y) }))))["regioes"]
            .associateBy { it["id"].asText() }

    @Test fun parcialDepoisNumerico() {
        val r = calcular(Triple("F", "F\\coloneq m\\cdot a", 20.0), Triple("v", "F=", 30.0))
        assertEquals("parcial", r.getValue("v")["tipo"].asText())
        assertEquals(listOf("a", "m"), r.getValue("v")["livres"].map { it.asText() }.sorted())
        val r2 = calcular(Triple("m", "m\\coloneq2", 5.0), Triple("a", "a\\coloneq3", 6.0),
            Triple("F", "F\\coloneq m\\cdot a", 20.0), Triple("v", "F=", 30.0))
        assertEquals("numerico", r2.getValue("v")["tipo"].asText())
        assertEquals("6", r2.getValue("v")["resultado"].asText())
    }

    @Test fun unidadeNaTela() {
        val r = calcular(Triple("a", "L\\coloneq2\\mathrm{m}", 10.0), Triple("b", "L+30\\mathrm{cm}=", 20.0))
        assertEquals("2{,}3\\,\\mathrm{m}", r.getValue("b")["resultado"].asText())
    }

    @Test fun unidadePedidaComPotencia() {
        val r = calcular(Triple("d", "d\\tldef20\\,\\mathrm{mm}", 10.0), Triple("a", "A\\tldef\\frac{\\pi d^2}{4}", 20.0),
            Triple("v", "A=\\,\\mathrm{mm}^2", 30.0))
        assertEquals("314{,}159\\,\\mathrm{mm}^{2}", r.getValue("v")["resultado"].asText())
    }

    @Test fun nomeDefinidoMaisAbaixoVemEmAbaixo() {
        val r = calcular(Triple("s", "\\sigma\\coloneq\\frac{M}{W}", 10.0), Triple("w", "W\\coloneq2", 20.0))
        assertEquals(listOf("W"), r.getValue("s")["abaixo"].map { it.asText() })
        assertEquals(listOf("M", "W"), r.getValue("s")["livres"].map { it.asText() }.sorted())
        assertEquals(listOf("W"), r.getValue("w")["define"].map { it.asText() })
    }

    @Test fun erroDeSintaxeNaoDerrubaAPagina() {
        val r = calcular(Triple("a", "x\\coloneq\\frac{1}", 10.0), Triple("b", "2+2=", 20.0))
        assertEquals("erro_sintaxe", r.getValue("a")["tipo"].asText())
        assertEquals("4", r.getValue("b")["resultado"].asText())
    }
}
