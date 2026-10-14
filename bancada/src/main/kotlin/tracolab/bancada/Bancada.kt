package tracolab.bancada

import tracolab.nucleo.Motor
import tracolab.nucleo.Planilha
import tracolab.nucleo.regiaoDeTexto
import tracolab.nucleo.spec.executarSpec
import java.io.File

/*
 * Bancada de desempenho: roda a especificação (duas passadas) e uma página sintética de
 * 110 regiões com unidades. É a mesma medição feita no Galaxy Tab S10 Lite
 * (docs/pesquisa/prova-de-conceito-kotlin-symja.md).
 *
 *   ./gradlew :bancada:run
 *
 * Também avalia folhas em lote (ver Lote.kt): bancada lote <entrada.jsonl> <saida.jsonl> [limite-s]
 */

/** Roda tudo e devolve o relatório em texto. */
fun relatorioCompleto(json: String, onde: String): String {
    val sb = StringBuilder()
    val t0 = System.nanoTime()
    val motor = Motor()
    val tInit = (System.nanoTime() - t0) / 1e6
    sb.appendLine("== $onde")
    sb.appendLine("init do Symja: %.0f ms".format(tInit))
    val r1 = executarSpec(json, motor)
    sb.appendLine("spec (1ª passada, JVM fria): ${r1.ok}/${r1.total} casos; total %.0f ms; mediana %.1f ms; máx %.0f ms"
        .format(r1.tempos.sum(), r1.tempos.sorted()[r1.tempos.size / 2], r1.tempos.max()))
    val r2 = executarSpec(json, motor)
    sb.appendLine("spec (2ª passada, aquecida): ${r2.ok}/${r2.total} casos; total %.0f ms; mediana %.1f ms; máx %.0f ms"
        .format(r2.tempos.sum(), r2.tempos.sorted()[r2.tempos.size / 2], r2.tempos.max()))
    repeat(3) { sb.appendLine(bench(motor)) }
    val rt = Runtime.getRuntime()
    sb.appendLine("memória usada: %.0f MB (máx da JVM %.0f MB)".format((rt.totalMemory() - rt.freeMemory()) / 1e6, rt.maxMemory() / 1e6))
    sb.appendLine()
    r1.linhas.filter { !it.startsWith("ok") }.forEach { sb.appendLine(it) }
    if (r1.ok == r1.total) sb.appendLine("todos os casos passaram")
    return sb.toString()
}

/** Página sintética de 100 regiões encadeadas, com unidades: tempo de montar e de editar
 *  a primeira definição (pior caso: tudo depende dela) e uma do meio. */
fun bench(motor: Motor): String {
    val pl = Planilha("parcial", motor = motor)
    val a = System.nanoTime()
    pl.adicionar(regiaoDeTexto("r0", "x0 := 1{m}", 0.0))
    for (k in 1 until 100) {
        pl.regioes += regiaoDeTexto("r$k", "x$k := x${k - 1}*1.01 + 2{cm}", k.toDouble())
        if (k % 10 == 9) pl.regioes += regiaoDeTexto("e$k", "x$k = {mm}", k + 0.5)   // 10 exibições
    }
    pl.recalcular()
    val tMonta = (System.nanoTime() - a) / 1e6
    val b = System.nanoTime()
    val rec1 = pl.editar("r0", regiaoDeTexto("r0", "x0 := 2{m}", 0.0))
    val tTudo = (System.nanoTime() - b) / 1e6
    val c = System.nanoTime()
    val rec2 = pl.editar("r55", regiaoDeTexto("r55", "x55 := x54*1.02 + 2{cm}", 55.0))
    val tMeio = (System.nanoTime() - c) / 1e6
    return ("bench 110 regiões: montar %.0f ms; editar a 1ª (%d recalculadas) %.0f ms; editar a 55ª (%d recalculadas) %.0f ms; x99 = %s"
        .format(tMonta, rec1.size, tTudo, rec2.size, tMeio, pl.exibir("e99")))
}

fun main(args: Array<String>) {
    if (args.firstOrNull() == "lote") {
        require(args.size >= 3) { "uso: bancada lote <entrada.jsonl> <saida.jsonl> [limite-s]" }
        return lote(File(args[1]), File(args[2]), args.getOrNull(3)?.toLong() ?: 30)
    }
    val caminho = args.firstOrNull() ?: "spec/casos-comportamento.json"
    println(relatorioCompleto(File(caminho).readText(), "JVM " + System.getProperty("java.version") + " / " + System.getProperty("os.arch")))
}
