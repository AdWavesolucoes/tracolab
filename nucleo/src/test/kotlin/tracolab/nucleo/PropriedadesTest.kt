package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.matheclipse.core.expression.F
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Propriedades do núcleo conferidas em folhas geradas ao acaso (semente fixa: o mesmo teste
 * sempre gera as mesmas folhas). Cada falha mostra a folha que a produziu.
 */
class PropriedadesTest {
    private val motor = Motor()
    private val nomes = listOf("a", "b", "c", "d", "F", "L", "σ")
    private val unidades = listOf("", "", "{m}", "{mm}", "{kN}", "{s}", "{MPa}")

    private fun Random.expr(prof: Int = 0): String = when (if (prof > 2) nextInt(2) else nextInt(7)) {
        0 -> "${nextInt(1, 50)}${unidades.random(this)}"
        6 -> "f(${nextInt(1, 5)})"
        1 -> nomes.random(this)
        2 -> "(${expr(prof + 1)} + ${expr(prof + 1)})"
        3 -> "${expr(prof + 1)}*${expr(prof + 1)}"
        4 -> "${expr(prof + 1)}/${nextInt(1, 9)}"
        else -> "(${expr(prof + 1)})^2"
    }

    private fun Random.regiao(): String = when (nextInt(7)) {
        0, 1 -> "${expr()} ="
        2 -> "${nomes.random(this)} ≡ ${expr()}"          // global: vale na folha toda
        3 -> "f(t) := t*${expr()}"
        else -> "${nomes.random(this)} := ${expr()}"
    }

    /** Estado comparável de uma região. */
    private fun assinatura(r: Regiao): String = r.estado!!.let { "${it.tipo}|${it.valor}|${it.livres.sorted()}|${it.msg}" }

    private fun completa(regs: List<Triple<String, String, Double>>): Map<String, String> {
        val pl = Planilha("parcial", motor = motor)
        regs.forEach { (id, t, y) -> pl.regioes += regiaoDeTexto(id, t, y) }
        pl.recalcular()
        return pl.regioes.associate { it.id to assinatura(it) }
    }

    /** (a) recálculo incremental (adicionar, editar, remover) dá o mesmo que recalcular tudo. */
    @Test fun incrementalIgualAoCompleto() {
        val rnd = Random(20261015)
        repeat(60) { folha ->
            val pl = Planilha("parcial", motor = motor)
            val regs = mutableListOf<Triple<String, String, Double>>()
            val log = StringBuilder()
            repeat(14) { k ->
                val op = if (regs.isEmpty()) 0 else rnd.nextInt(4)
                when (op) {
                    0, 1 -> {
                        val t = Triple("r$k", rnd.regiao(), rnd.nextInt(1, 40) * 10.0)
                        regs += t; pl.adicionar(regiaoDeTexto(t.first, t.second, t.third)); log.appendLine("+ ${t.first} y=${t.third} ${t.second}")
                    }
                    2 -> {
                        val i = rnd.nextInt(regs.size); val velha = regs[i]
                        val t = Triple(velha.first, rnd.regiao(), if (rnd.nextBoolean()) velha.third else rnd.nextInt(1, 40) * 10.0)
                        regs[i] = t; pl.editar(t.first, regiaoDeTexto(t.first, t.second, t.third)); log.appendLine("~ ${t.first} y=${t.third} ${t.second}")
                    }
                    else -> {
                        val t = regs.removeAt(rnd.nextInt(regs.size)); pl.remover(t.first); log.appendLine("- ${t.first}")
                    }
                }
                val esperado = completa(regs)
                for (r in pl.regioes) assertEquals(esperado[r.id], assinatura(r)) { "folha $folha, região ${r.id}:\n$log" }
            }
        }
    }

    /** (b) definir os valores acima dá o mesmo número que substituí-los no resultado pendente. */
    @Test fun parcialDepoisNumericoConfere() {
        val rnd = Random(7)
        repeat(80) {
            val e = rnd.expr()
            val valores = nomes.associateWith { "${rnd.nextInt(1, 20)}${unidades.random(rnd)}" }
            val p1 = Planilha("parcial", motor = motor)
            p1.adicionar(regiaoDeTexto("v", "$e =", 100.0))
            val pendente = p1.regioes[0].estado!!
            val p2 = Planilha("parcial", motor = motor)
            valores.entries.forEachIndexed { k, (n, v) -> p2.regioes += regiaoDeTexto("d$k", "$n := $v", k.toDouble()) }
            p2.regioes += regiaoDeTexto("v", "$e =", 100.0)
            p2.recalcular()
            val cheio = p2.regioes.last().estado!!
            if (pendente.tipo != "parcial" && pendente.tipo != "numerico") return@repeat
            if ("f" in pendente.livres) return@repeat   // função sem definição: não há o que substituir
            if (cheio.tipo == "erro") return@repeat   // unidades incompatíveis com os valores sorteados
            // substitui os nomes no pendente e compara com o calculado
            val ctx = Contexto(emptyMap(), p2)
            var sub = pendente.valor!!
            for ((n, v) in valores) sub = motor.ev(F.ReplaceAll(sub, F.Rule(motor.sym(n), avaliar(Parser.parse(v), ctx))))
            val dif = motor.ev(F.Simplify(F.Subtract(motor.ev(F.N(sub)), cheio.valor!!)))
            val rel = try { abs(motor.dbl(motor.ev(F.Divide(dif, cheio.valor!!)))) } catch (_: Exception) { if (dif.isZero) 0.0 else 1.0 }
            assertTrue(rel < 1e-9) { "$e com $valores: pendente ${pendente.valor}, calculado ${cheio.valor}" }
        }
    }

    /** (c) toda unidade da tabela volta ao mesmo número: x := 3.7{u}; x = {u} mostra 3.7. */
    @Test fun unidadeIdaEVolta() {
        for (u in TabelaUnidades.tabela.keys) {
            val pl = Planilha("parcial", motor = motor)
            pl.adicionar(regiaoDeTexto("x", "x := 3.7{$u}", 10.0))
            pl.adicionar(regiaoDeTexto("e", "x = {$u}", 20.0))
            assertEquals("3.7 $u", pl.exibir("e")) { "unidade $u" }
        }
    }

    /** (d) na mesma linha de base, a ordem é pela posição x, não pela ordem de inserção. */
    @Test fun desempatePorX() {
        val pl = Planilha("parcial", motor = motor)
        pl.adicionar(regiaoDeTexto("v", "x*2 =", 10.0, 300.0))
        pl.adicionar(regiaoDeTexto("x2", "x := 7", 10.0, 500.0))
        pl.adicionar(regiaoDeTexto("x1", "x := 5", 10.0, 100.0))
        assertEquals("10", pl.exibir("v"))
    }

    /** (e) o LaTeX que a tela mostra, digitado de volta, dá o mesmo valor. */
    @Test fun latexDaTelaVoltaAoMesmoValor() {
        val rnd = Random(11)
        repeat(120) {
            val e = rnd.expr()
            val valores = nomes.filter { rnd.nextInt(3) > 0 }.associateWith { "${rnd.nextInt(1, 20)}${unidades.random(rnd)}" }
            val pl = Planilha("parcial", motor = motor)
            valores.entries.forEachIndexed { k, (n, v) -> pl.regioes += regiaoDeTexto("d$k", "$n := $v", k.toDouble()) }
            pl.regioes += regiaoDeTexto("v", "$e =", 100.0)
            pl.recalcular()
            val est = pl.regioes.last().estado!!
            if (est.tipo != "numerico" && est.tipo != "parcial") return@repeat
            if ("f" in est.livres) return@repeat   // f(1) sem definição: a comparação numérica não se aplica
            val tela = pl.resultadoTela("v").latex ?: return@repeat
            val texto = LatexEntrada.regiao("$tela=")
            val p2 = Planilha("parcial", motor = motor)
            p2.adicionar(regiaoDeTexto("w", texto, 10.0))
            val volta = p2.regioes[0].estado!!
            assertEquals(est.tipo, volta.tipo) { "$e: tela '$tela' -> '$texto' (${volta.msg})" }
            // a tela mostra 6 algarismos: compara com os nomes que faltam trocados por 1,3
            fun num(v: org.matheclipse.core.interfaces.IExpr): org.matheclipse.core.interfaces.IExpr {
                var x = v
                for (n in motor.nomesLivres(v)) x = motor.ev(F.ReplaceAll(x, F.Rule(motor.sym(n), F.num(1.3))))
                for (b in motor.bases) x = motor.ev(F.ReplaceAll(x, F.Rule(b, F.num(1.7))))
                return x
            }
            val dif = motor.ev(F.Simplify(F.Subtract(num(est.valor!!), num(volta.valor!!))))
            val ok = dif.isZero || runCatching {
                val r = abs(motor.dbl(motor.ev(F.PowerExpand(F.Divide(dif, num(est.valor!!))))))
                r < 5e-6
            }.getOrDefault(false)
            assertTrue(ok) { "$e: '${est.valor}' -> tela '$tela' -> '$texto' -> '${volta.valor}'" }
        }
    }

    @Suppress("unused") private fun perto(a: Double, b: Double) = abs(a - b) <= 1e-9 * max(abs(a), abs(b))
}
