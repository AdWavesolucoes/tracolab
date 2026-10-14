package tracolab.nucleo

import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.resultadoTela

class SondaTest {
    @Test fun sonda() {
        val arq = java.io.File("/tmp/tracolab-bugs-nucleo/sonda.in")
        if (!arq.exists()) return
        val linhas = arq.readLines().filter { it.isNotBlank() }
        val out = StringBuilder()
        val pl = Planilha("parcial")
        linhas.forEachIndexed { k, t -> pl.regioes += regiaoDeTexto("r$k", t, 10.0 * (k + 1)) }
        pl.recalcular()
        for (r in pl.regioes) {
            val tela = try { pl.resultadoTela(r.id).toString() } catch (e: Throwable) { "EXCEÇÃO ${e.javaClass.simpleName}: ${e.message}" }
            out.appendLine("${r.id} ${r.estado?.tipo} ${r.estado?.msg ?: ""} | exibir=${try { pl.exibir(r.id) } catch (e: Throwable) { "EXC " + e.javaClass.simpleName }} | tela=$tela")
        }
        java.io.File("/tmp/tracolab-bugs-nucleo/sonda.out").writeText(out.toString())
    }
}
