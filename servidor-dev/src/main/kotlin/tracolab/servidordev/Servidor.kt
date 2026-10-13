package tracolab.servidordev

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import tracolab.api.Calculadora
import java.io.File
import java.net.InetSocketAddress

/**
 * ./gradlew :servidor-dev:run --args="<endereço> <porta>"   (padrão: 127.0.0.1 8098)
 * GET  /...            arquivos de web/
 * POST /api/calcular   Calculadora.calcularJson
 */
fun main(args: Array<String>) {
    val endereco = args.getOrNull(0) ?: "127.0.0.1"
    val porta = args.getOrNull(1)?.toInt() ?: 8098
    val raiz = File("web").canonicalFile
    val calc = Calculadora()
    val tipos = mapOf("html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8",
        "mjs" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8", "json" to "application/json",
        "woff2" to "font/woff2", "svg" to "image/svg+xml", "png" to "image/png")

    val srv = HttpServer.create(InetSocketAddress(endereco, porta), 0)
    srv.createContext("/api/calcular") { ex ->
        if (ex.requestMethod != "POST") return@createContext responder(ex, 405, "text/plain", "use POST".toByteArray())
        val resp = try { calc.calcularJson(ex.requestBody.readBytes().toString(Charsets.UTF_8)) }
                   catch (e: Exception) { return@createContext responder(ex, 400, "text/plain", (e.message ?: "erro").toByteArray()) }
        responder(ex, 200, "application/json; charset=utf-8", resp.toByteArray())
    }
    srv.createContext("/") { ex ->
        val caminho = ex.requestURI.path.let { if (it == "/") "/index.html" else it }
        val f = File(raiz, caminho).canonicalFile
        if (!f.path.startsWith(raiz.path) || !f.isFile) return@createContext responder(ex, 404, "text/plain", "404".toByteArray())
        responder(ex, 200, tipos[f.extension] ?: "application/octet-stream", f.readBytes())
    }
    srv.start()
    println("TracoLab (desenvolvimento) em http://$endereco:$porta/  — página em ${raiz.path}")
}

private fun responder(ex: HttpExchange, codigo: Int, tipo: String, corpo: ByteArray) {
    ex.responseHeaders.add("Content-Type", tipo)
    ex.responseHeaders.add("Cache-Control", "no-store")
    ex.sendResponseHeaders(codigo, corpo.size.toLong())
    ex.responseBody.use { it.write(corpo) }
}
