package tracolab.api

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.matheclipse.core.expression.F
import tracolab.nucleo.Embutidos
import tracolab.nucleo.Especial
import tracolab.nucleo.Limites
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Limites da API (ADR 0017): pedido inválido, região grande demais, vários motores, cálculo que não para. */
class LimitesPedidoTest {
    private val json = ObjectMapper()

    private fun pedido(vararg latex: String) = json.writeValueAsString(mapOf("modo" to "parcial",
        "regioes" to latex.mapIndexed { k, l -> mapOf("id" to "r$k", "latex" to l, "x" to 0, "y" to 10 * (k + 1)) }))

    private fun regioes(resp: String) = json.readTree(resp)["regioes"].associateBy { it["id"].asText() }

    @Test fun pedidosInvalidosSaoRecusados() {
        val calc = Calculadora()
        for (p in listOf("{", "[1]", """{"regioes": 5}""", """{"regioes": [{"latex": "1="}]}""",
                """{"regioes": [{"id": "a", "latex": "1="}, {"id": "a", "latex": "2="}]}""", """{"modo": "outro"}"""))
            assertThrows(PedidoInvalido::class.java, { calc.calcularJson(p) }, p)
        val muitas = json.writeValueAsString(mapOf("regioes" to (1..3).map { mapOf("id" to "r$it", "latex" to "1=") }))
        assertThrows(PedidoInvalido::class.java) { Calculadora(limitesPedido = LimitesPedido(regioes = 2)).calcularJson(muitas) }
    }

    @Test fun formulaLongaDemaisEErroSoNela() {
        val r = regioes(Calculadora(limitesPedido = LimitesPedido(caracteresLatex = 50)).calcularJson(pedido("1+" .repeat(40) + "1=", "2+2=")))
        assertEquals("erro_sintaxe", r.getValue("r0")["tipo"].asText())
        assertTrue(r.getValue("r0")["msg"].asText().startsWith("Fórmula longa demais"))
        assertEquals("4", r.getValue("r1")["resultado"].asText())
    }

    @Test fun formulaFundaCalculaNaPilhaGrande() {
        // 3 mil chaves aninhadas estouram a pilha padrão, mas não a da thread de cálculo
        val calc = Calculadora(limitesPedido = LimitesPedido(niveis = 5000))
        val r = regioes(calc.calcularJson(pedido("{".repeat(3000) + "1" + "}".repeat(3000) + "+1=")))
        assertEquals("2", r.getValue("r0")["resultado"].asText())
    }

    @Test fun aninhamentoDemaisEErroSoNaRegiao() {
        val r = regioes(Calculadora().calcularJson(pedido("{".repeat(201) + "1" + "}".repeat(201) + "=", "\\frac{\\left(1+2\\right)}{3}=")))
        assertEquals("Fórmula aninhada demais (mais de 200 níveis).", r.getValue("r0")["msg"].asText())
        assertEquals("1", r.getValue("r1")["resultado"].asText())
    }

    @Test fun estouroNumaRegiaoNaoDerrubaAFolha() {
        val r = regioes(Calculadora().calcularJson(pedido("10^{400}=", "2+2=")))
        assertEquals("erro", r.getValue("r0")["tipo"].asText())
        assertEquals("4", r.getValue("r1")["resultado"].asText())
    }

    @Test fun vetorEnormeChegaCortadoNaTela() {
        val r = regioes(Calculadora().calcularJson(pedido("i\\coloneq0..9999", "i=")))
        val res = r.getValue("r1")
        assertTrue(res["resultado"].asText().contains("\\vdots"))
        assertTrue(res["aviso"].asText().contains("10000 valores"))   // resumo da tela de matrizes (ADR 0010)
    }

    @Test fun variosMotoresDaoOsMesmosResultadosQueUmSo() {
        val folhas = listOf(
            pedido("d\\tldef20\\,\\mathrm{mm}", "A\\tldef\\frac{\\pi d^2}{4}", "F\\tldef31{,}4159265\\mathrm{kN}", "\\sigma\\tldef\\frac{F}{A}", "\\sigma=\\mathrm{MPa}"),
            pedido("F\\coloneq m\\cdot a", "F=", "m\\coloneq2"),
            pedido("i\\coloneq1..30", "i^2+\\sqrt{i}=", "\\frac{1}{3}+\\frac{1}{6}\\to"),
            pedido("f\\left(t\\right)\\coloneq t^2+k", "k\\coloneq1", "f\\left(3\\right)=", "\\sin\\left(90^{\\circ}\\right)="),
        )
        val sozinho = Calculadora()
        val esperado = folhas.map { regioes(sozinho.calcularJson(it)).mapValues { (_, o) -> o["resultado"]?.asText() to o["livres"].toString() } }
        val calc = Calculadora(motores = 4)
        val pool = Executors.newFixedThreadPool(8)
        val tarefas = (0 until 80).map { k -> pool.submit<Boolean> {
            val f = k % folhas.size
            regioes(calc.calcularJson(folhas[f])).mapValues { (_, o) -> o["resultado"]?.asText() to o["livres"].toString() } == esperado[f]
        } }
        assertTrue(tarefas.all { it.get(60, TimeUnit.SECONDS) })
        pool.shutdown()
    }

    @Test fun calculoLentoNaoPrendeOsOutrosMotores() {
        val calc = Calculadora(motores = 2, limites = Limites(msRegiao = 800))
        val pool = Executors.newFixedThreadPool(2)
        val lento = pool.submit<String> { calc.calcularJson(pedido("\\left(a+b+c+d+e\\right)^{40}\\to")) }
        Thread.sleep(150)
        val t0 = System.nanoTime()
        val r = regioes(calc.calcularJson(pedido("2+2=")))
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals("4", r.getValue("r0")["resultado"].asText())
        assertTrue(ms < 500) { "esperou $ms ms" }
        lento.get(30, TimeUnit.SECONDS); pool.shutdown()
    }

    @Test fun calculoQueNaoParaEAbandonadoEOMotorETrocado() {
        // conta presa fora do Symja e sem verificarPrazo: o único jeito é abandonar
        Embutidos.especiais["zzpresa"] = Especial { _, _ ->
            val fim = System.nanoTime() + 3_000_000_000L
            while (System.nanoTime() < fim) { /* ignora o prazo e a interrupção */ }
            F.C0
        }
        try {
            val calc = Calculadora(limites = Limites(msRegiao = 100, msFolha = 200), limitesPedido = LimitesPedido(msFolga = 300))
            val t0 = System.nanoTime()
            val resp = json.readTree(calc.calcularJson(pedido("zzpresa\\left(1\\right)=", "2+2=")))
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 1500)
            assertEquals("Cálculo interrompido: a folha passou de 200 ms.", resp["erro"].asText())
            assertEquals("erro", resp["regioes"][1]["tipo"].asText())
            // o motor novo atende o próximo pedido na hora
            assertEquals("4", regioes(calc.calcularJson(pedido("2+2="))).getValue("r0")["resultado"].asText())
        } finally { Embutidos.especiais.remove("zzpresa") }
    }
}
