package tracolab.servidor

import io.ktor.server.engine.EmbeddedServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.File
import java.net.CookieManager
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files

/**
 * O servidor pelo HTTP: cabeçalhos, arquivos, métodos, erros da API e login. O contrato inteiro
 * (contas, 2FA, folhas, reinício) é o harness `ferramentas/harness/servico.sh`; aqui ficam os
 * casos que convém rodar em todo `./gradlew build`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServidorTest {
    private val pasta = Files.createTempDirectory("tracolab-servidor").toFile()
    private val raiz = File(pasta, "web").apply { mkdirs(); File(this, "index.html").writeText("<p>oi</p>") }
    private lateinit var servidor: EmbeddedServer<*, *>
    private lateinit var url: String
    private val http = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
    private var porta = 0

    @BeforeAll fun subir() {
        File(pasta, "web-segredo").apply { mkdirs(); File(this, "x.txt").writeText("segredo") }
        val config = Config(endereco = "127.0.0.1", porta = 0, dados = File(pasta, "dados"), web = raiz, motor = "teste", tentativas = 3)
        servidor = criarServidor(config).start(wait = false)
        porta = runBlocking { servidor.engine.resolvedConnectors().first().port }
        url = "http://127.0.0.1:$porta"
        // uma conta, para as rotas com sessão
        assertEquals(201, pedir("POST", "/api/conta/cadastrar", """{"usuario":"teste","senha":"senha-de-teste-123"}""").statusCode())
    }
    @AfterAll fun descer() { servidor.stop(100, 500); pasta.deleteRecursively() }

    private fun pedir(metodo: String, caminho: String, corpo: String? = null, cab: Map<String, String> = emptyMap()): HttpResponse<String> {
        val b = HttpRequest.newBuilder(URI.create(url + caminho))
            .method(metodo, corpo?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
        if (corpo != null) b.setHeader("Content-Type", "application/json")
        cab.forEach { (k, v) -> b.setHeader(k, v) }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString())
    }

    /** Pedido cru pelo socket (o HttpClient normaliza o caminho). Devolve a linha de status. */
    private fun cru(linha: String): String = Socket("127.0.0.1", porta).use { s ->
        s.getOutputStream().write("$linha\r\nHost: x\r\nConnection: close\r\n\r\n".toByteArray())
        s.getInputStream().bufferedReader().readLine()
    }

    @Test fun paginaComCabecalhosDeSeguranca() {
        val r = pedir("GET", "/")
        assertEquals(200, r.statusCode())
        assertTrue(r.headers().firstValue("Content-Security-Policy").get().contains("script-src 'self'"))
        assertEquals("nosniff", r.headers().firstValue("X-Content-Type-Options").get())
        assertEquals("teste", r.headers().firstValue("X-TracoLab-Motor").get())
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

    @Test fun semSessaoNaoCalcula() {
        val anonimo = HttpClient.newHttpClient()
        val r = anonimo.send(HttpRequest.newBuilder(URI.create("$url/api/calcular")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"regioes":[]}""")).build(), HttpResponse.BodyHandlers.ofString())
        assertEquals(401, r.statusCode())
        assertEquals(401, anonimo.send(HttpRequest.newBuilder(URI.create("$url/api/folhas")).build(), HttpResponse.BodyHandlers.ofString()).statusCode())
    }

    @Test fun csrf() {
        assertEquals(403, pedir("POST", "/api/conta/sair", cab = mapOf("Sec-Fetch-Site" to "cross-site")).statusCode())
        assertEquals(403, pedir("POST", "/api/conta/sair", cab = mapOf("Origin" to "https://malicioso.example")).statusCode())
        assertEquals(415, pedir("POST", "/api/folhas", "a=1", cab = mapOf("Content-Type" to "application/x-www-form-urlencoded")).statusCode())
        assertEquals(200, pedir("GET", "/api/conta").statusCode())   // a sessão continua: nada acima executou
    }

    @Test fun senhasEtotp() {
        val h = Senhas.gerar("minha senha longa")
        assertTrue(h.startsWith("argon2id\$19\$m=19456,t=2,p=1\$"))
        assertTrue(Senhas.confere("minha senha longa", h)); assertFalse(Senhas.confere("outra", h))
        val seg = Totp.novoSegredo()
        assertEquals(32, seg.length)
        assertTrue(Totp.confere(seg, Totp.codigo(seg, agora() / 30000)))
        assertTrue(Totp.confere(seg, Totp.codigo(seg, agora() / 30000 - 1)))   // passo anterior ainda vale
        assertFalse(Totp.confere(seg, Totp.codigo(seg, agora() / 30000 + 5)))
        assertEquals("JBSWY3DPEHPK3PXP", base32("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1)))
        assertTrue(Totp.qrSvg(Totp.otpauth("ana", seg)).startsWith("<svg"))
    }
}
