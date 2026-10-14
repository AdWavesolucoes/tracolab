package tracolab.servidordev

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tracolab.api.Calculadora
import tracolab.api.Ocupado
import tracolab.api.PedidoInvalido
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * ./gradlew :servidor-dev:run --args="<endereço> <porta>"   (padrão: 127.0.0.1 8098)
 * TRACOLAB_MOTOR=<nome> identifica a máquina no cabeçalho X-TracoLab-Motor (a página mostra
 * quem está calculando quando há mais de uma instância atrás de um proxy).
 * TRACOLAB_MOTORES=<n> cálculos ao mesmo tempo (padrão 2; ADR 0017).
 * GET/HEAD /...        arquivos de web/
 * POST /api/calcular   Calculadora.calcularJson
 *
 * Não tem autenticação: na internet, só atrás de uma autenticação na borda (ADR 0009). Os limites
 * de tamanho e de tempo (ADR 0017) evitam que um pedido, por acidente ou não, trave o servidor.
 */
fun main(args: Array<String>) {
    // Cliente que manda o pedido ou lê a resposta devagar demais é desconectado (lidos pelo
    // HttpServer do JDK ao carregar; por isso antes de criar o servidor).
    System.setProperty("sun.net.httpserver.maxReqTime", "15")
    System.setProperty("sun.net.httpserver.maxRspTime", "60")
    val endereco = args.getOrNull(0) ?: "127.0.0.1"
    val porta = args.getOrNull(1)?.toInt() ?: 8098
    val raiz = File("web").canonicalFile
    val calc = Calculadora(motores = System.getenv("TRACOLAB_MOTORES")?.toIntOrNull()?.coerceIn(1, 8) ?: 2)
    iniciar(endereco, porta, raiz, calc)
    println("TracoLab (desenvolvimento) em http://$endereco:$porta/  — página em ${raiz.path}")
}

/** Sobe o servidor (porta 0: uma livre, para testes). */
fun iniciar(endereco: String, porta: Int, raiz: File, calc: Calculadora): HttpServer {
    val srv = HttpServer.create(InetSocketAddress(endereco, porta), 0)
    srv.createContext("/api/calcular") { ex -> tratar(ex) {
        if (ex.requestMethod != "POST") return@tratar naoPermitido(ex, "POST")
        val corpo = try { lerCorpo(ex, MAX_CORPO) } catch (e: GrandeDemais) {
            drenar(ex)   // sem ler o resto, a conexão fecha e o cliente não vê a resposta
            return@tratar erroJson(ex, 413, "Pedido grande demais (máximo ${MAX_CORPO / 1_000_000} MB).")
        }
        val resp = try { calc.calcularJson(corpo.toString(Charsets.UTF_8)) }
                   catch (e: PedidoInvalido) { return@tratar erroJson(ex, 400, e.message!!) }
                   catch (e: Ocupado) { ex.responseHeaders.add("Retry-After", "5"); return@tratar erroJson(ex, 503, e.message!!) }
        responder(ex, 200, "application/json; charset=utf-8", resp.toByteArray())
    } }
    srv.createContext("/") { ex -> tratar(ex) {
        if (ex.requestMethod != "GET" && ex.requestMethod != "HEAD") return@tratar naoPermitido(ex, "GET, HEAD")
        val caminho = ex.requestURI.path.let { if (it == "/") "/index.html" else it }
        val f = try { File(raiz, caminho).canonicalFile } catch (e: Exception) { null }   // ex.: caminho com NUL
        // toPath().startsWith compara pastas inteiras: "/x/web-foo" não está dentro de "/x/web"
        if (f == null || !f.toPath().startsWith(raiz.toPath()) || !f.isFile) return@tratar responder(ex, 404, "text/plain; charset=utf-8", "404".toByteArray())
        val modificado = f.lastModified() / 1000 * 1000
        val desde = ex.requestHeaders.getFirst("If-Modified-Since")?.let { runCatching { ZonedDateTime.parse(it, HTTP_DATA).toInstant().toEpochMilli() }.getOrNull() }
        ex.responseHeaders.add("Last-Modified", HTTP_DATA.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(modificado), ZoneOffset.UTC)))
        if (desde != null && modificado <= desde) return@tratar responder(ex, 304, null, ByteArray(0), cache = "no-cache")
        responder(ex, 200, TIPOS[f.extension] ?: "application/octet-stream", f.readBytes(), cache = "no-cache")
    } }
    srv.executor = Executors.newFixedThreadPool(16)   // um cliente lento não prende os outros
    srv.start()
    return srv
}

private val MOTOR = System.getenv("TRACOLAB_MOTOR").orEmpty()
private const val MAX_CORPO = 4_000_000
private val HTTP_DATA = DateTimeFormatter.RFC_1123_DATE_TIME
private val TIPOS = mapOf("html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8",
    "mjs" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8", "json" to "application/json",
    "woff2" to "font/woff2", "woff" to "font/woff", "ttf" to "font/ttf", "svg" to "image/svg+xml", "png" to "image/png",
    "ico" to "image/x-icon", "txt" to "text/plain; charset=utf-8", "pdf" to "application/pdf")

/**
 * Política de segurança da página. O MathLive desenha com estilos inline (style-src
 * 'unsafe-inline'); scripts só os arquivos do próprio servidor, sem inline e sem eval.
 */
private const val CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
    "font-src 'self'; img-src 'self' data: blob:; connect-src 'self'; media-src 'self'; object-src 'none'; " +
    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

private class GrandeDemais : Exception()

/** Lê o corpo inteiro, mas no máximo [max] bytes. */
private fun lerCorpo(ex: HttpExchange, max: Int): ByteArray {
    ex.requestHeaders.getFirst("Content-Length")?.toLongOrNull()?.let { if (it > max) throw GrandeDemais() }
    val saida = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    ex.requestBody.let { ent ->
        while (true) {
            val n = ent.read(buf)
            if (n < 0) break
            saida.write(buf, 0, n)
            if (saida.size() > max) throw GrandeDemais()
        }
    }
    return saida.toByteArray()
}

/** Descarta o resto do corpo, até 4× o máximo (depois disso a conexão fecha mesmo). */
private fun drenar(ex: HttpExchange) = runCatching {
    val buf = ByteArray(64 * 1024); var total = 0L
    while (total < 4L * MAX_CORPO) { val n = ex.requestBody.read(buf); if (n < 0) break; total += n }
}

/** Toda resposta sai, mesmo com erro inesperado: nada de conexão pendurada nem stack trace para o cliente. */
private fun tratar(ex: HttpExchange, bloco: () -> Unit) {
    try {
        bloco()
    } catch (e: Throwable) {
        System.err.println("erro em ${ex.requestMethod} ${ex.requestURI.rawPath}: $e")
        e.printStackTrace()
        runCatching { erroJson(ex, 500, "Erro interno do servidor.") }
    } finally {
        ex.close()
    }
}

private fun naoPermitido(ex: HttpExchange, metodos: String) {
    ex.responseHeaders.add("Allow", metodos)
    erroJson(ex, 405, "Use $metodos.")
}

private fun erroJson(ex: HttpExchange, codigo: Int, msg: String) {
    val texto = msg.flatMap { c -> if (c == '"' || c == '\\') listOf('\\', c) else if (c < ' ') listOf(' ') else listOf(c) }.joinToString("")
    responder(ex, codigo, "application/json; charset=utf-8", """{"erro":"$texto"}""".toByteArray())
}

private fun responder(ex: HttpExchange, codigo: Int, tipo: String?, corpo: ByteArray, cache: String = "no-store") {
    val h = ex.responseHeaders
    tipo?.let { h.add("Content-Type", it) }
    if (MOTOR.isNotEmpty()) h.add("X-TracoLab-Motor", MOTOR)
    h.add("Cache-Control", cache)
    h.add("X-Content-Type-Options", "nosniff")
    h.add("Referrer-Policy", "no-referrer")
    h.add("X-Frame-Options", "DENY")
    h.add("Content-Security-Policy", CSP)
    val semCorpo = ex.requestMethod == "HEAD" || codigo == 304
    ex.sendResponseHeaders(codigo, if (semCorpo) -1 else corpo.size.toLong())
    if (!semCorpo) ex.responseBody.use { it.write(corpo) }
}
