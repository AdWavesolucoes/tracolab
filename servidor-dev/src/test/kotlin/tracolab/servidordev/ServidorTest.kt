package tracolab.servidordev

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tracolab.api.Calculadora
import java.io.File
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

/** Servidor de desenvolvimento (ADR 0017): respostas de erro, métodos, arquivos e cabeçalhos. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServidorTest {
    private val pasta = Files.createTempDirectory("tracolab-servidor").toFile()
    private val raiz = File(pasta, "web").apply { mkdirs(); File(this, "index.html").writeText("<p>oi</p>") }
    private lateinit var url: String
    private val srv by lazy { iniciar("127.0.0.1", 0, raiz.canonicalFile, Calculadora()) }
    private val http = HttpClient.newHttpClient()

    @BeforeAll fun subir() {
        File(pasta, "web-segredo").apply { mkdirs(); File(this, "x.txt").writeText("segredo") }
        url = "http://127.0.0.1:${srv.address.port}"
    }
    @AfterAll fun descer() { srv.stop(0); pasta.deleteRecursively() }

    private fun pedir(metodo: String, caminho: String, corpo: String? = null, cab: Map<String, String> = emptyMap()): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create(url + caminho))
            .method(metodo, corpo?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        cab.forEach { (k, v) -> b.header(k, v) }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Pedido cru pelo socket (o HttpClient normaliza o caminho). Devolve a linha de status. */
    private fun cru(linha: String): String = Socket("127.0.0.1", srv.address.port).use { s ->
        s.getOutputStream().write("$linha\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray())
        s.getInputStream().bufferedReader().readLine()
    }

    @Test fun paginaComCabecalhosDeSeguranca() {
        val r = pedir("GET", "/")
        assertEquals(200, r.statusCode())
        assertTrue(r.headers().firstValue("Content-Security-Policy").get().contains("script-src 'self'"))
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").get())
        val r2 = pedir("GET", "/", cab = mapOf("If-Modified-Since" to r.headers().firstValue("Last-Modified").get()))
        assertEquals(304, r2.statusCode())
    }

    @Test fun pastaVizinhaComMesmoPrefixoNaoVaza() {
        assertEquals("HTTP/1.1 404 Not Found", cru("GET /../web-segredo/x.txt HTTP/1.1"))
        assertEquals("HTTP/1.1 404 Not Found", cru("GET /%2e%2e/web-segredo/x.txt HTTP/1.1"))
        assertEquals("HTTP/1.1 404 Not Found", cru("GET /index.html%00.js HTTP/1.1"))
    }

    @Test fun metodos() {
        assertEquals(405, pedir("PUT", "/index.html", "x").statusCode())
        assertEquals(405, pedir("GET", "/api/calcular").statusCode())
        val h = pedir("HEAD", "/index.html")
        assertEquals(200, h.statusCode()); assertEquals("", h.body())
    }

    @Test fun errosDaApiEmJson() {
        val r = pedir("POST", "/api/calcular", "{")
        assertEquals(400, r.statusCode()); assertEquals("""{"erro":"JSON inválido."}""", r.body())
        val semId = pedir("POST", "/api/calcular", """{"regioes":[{"latex":"1="}]}""")
        assertEquals(400, semId.statusCode()); assertTrue(semId.body().startsWith("""{"erro":"Cada região"""))
        assertEquals(413, pedir("POST", "/api/calcular", "x".repeat(5_000_000)).statusCode())
    }

    @Test fun recursaoInfinitaRespondeComErroNaRegiao() {
        val r = pedir("POST", "/api/calcular", """{"regioes":[{"id":"f","latex":"f\\left(x\\right)\\coloneq f\\left(x\\right)","y":10},
            {"id":"a","latex":"f\\left(1\\right)=","y":20},{"id":"b","latex":"2+2=","y":30}]}""")
        assertEquals(200, r.statusCode())
        assertTrue(r.body().contains("Recursão profunda demais")) { r.body() }
        assertTrue(r.body().contains(""""resultado":"4"""")) { r.body() }
    }
}
