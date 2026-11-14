package tracolab.servidor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.contentLength
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.discard
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import tracolab.api.Calculadora
import tracolab.api.Ocupado
import tracolab.api.PedidoInvalido
import java.io.File
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Timer
import java.util.TimerTask

/**
 * O serviço TraçoLab (ADR 0019): página, API de cálculo, contas e folhas, num processo só.
 *
 *   GET  /...                      arquivos de web/ (sem login: a página decide o que mostrar)
 *   GET  /api/instancia            modo de cadastro e se o primeiro acesso já foi feito (público)
 *   POST /api/conta/cadastrar      {usuario, senha, convite?} → 201 e sessão
 *   POST /api/conta/entrar         {usuario, senha} → {ok} ou {doisFatores: true} (sessão pendente)
 *   POST /api/conta/entrar/2fa     {codigo} (TOTP ou recuperação) → {ok}
 *   POST /api/conta/sair
 *   GET  /api/conta                quem sou
 *   POST /api/conta/senha          {atual, nova}
 *   POST /api/conta/2fa/preparar   → {segredo, otpauth, qr}
 *   POST /api/conta/2fa/ativar     {codigo} → {codigosRecuperacao}
 *   POST /api/conta/2fa/desativar  {senha, codigo}
 *   POST /api/conta/2fa/codigos    {senha, codigo} → {codigosRecuperacao}
 *   GET  /api/admin/usuarios · DELETE /api/admin/usuarios/{id} · POST .../{id}/2fa/remover · POST .../{id}/admin {admin}
 *   POST /api/admin/convites → {codigo, link, expiraEm} · GET /api/admin/convites · DELETE /api/admin/convites
 *   GET/POST /api/folhas · GET/PUT/DELETE /api/folhas/{id} (POST no id = PUT, para o sendBeacon)
 *   POST /api/calcular             Calculadora.calcularJson (ADR 0017), só com sessão
 *
 * Toda rota de escrita exige JSON e recusa pedidos de outro site (Sec-Fetch-Site / Origin): com o
 * cookie SameSite=Strict, isso fecha o CSRF sem token.
 */
fun main(args: Array<String>) {
    val config = Config.deArgumentos(args)
    val servidor = criarServidor(config)
    println("TraçoLab $VERSAO em http://${config.endereco}:${config.porta}/  — dados em ${config.dados.absolutePath}, página em ${config.web.absolutePath}, cadastro: ${config.cadastro}")
    servidor.start(wait = true)
}

fun criarServidor(config: Config): EmbeddedServer<*, *> {
    val banco = Banco(config.dados)
    val contas = Contas(banco, config)
    val folhas = Folhas(banco, JSON)
    val calc = Calculadora(motores = config.motores)
    Timer("tracolab-limpeza", true).schedule(object : TimerTask() { override fun run() = runCatching { contas.limpar() }.let {} }, 3600_000L, 3600_000L)
    return embeddedServer(CIO, configure = {
        connector { host = config.endereco; port = config.porta }
        connectionIdleTimeoutSeconds = 45
    }) { modulo(config, contas, folhas, calc) }
}

val JSON: ObjectMapper = ObjectMapper()
private const val MAX_CORPO = 4_000_000
private const val COOKIE = "tl_sessao"
private val SESSAO = AttributeKey<Sessao>("tracolab.sessao")
private val SESSAO_LIDA = AttributeKey<Boolean>("tracolab.sessao.lida")
private val HTTP_DATA = DateTimeFormatter.RFC_1123_DATE_TIME
private val TIPOS = mapOf("html" to "text/html; charset=utf-8", "js" to "text/javascript; charset=utf-8",
    "mjs" to "text/javascript; charset=utf-8", "css" to "text/css; charset=utf-8", "json" to "application/json",
    "webmanifest" to "application/manifest+json", "woff2" to "font/woff2", "woff" to "font/woff", "ttf" to "font/ttf",
    "svg" to "image/svg+xml", "png" to "image/png", "ico" to "image/x-icon", "txt" to "text/plain; charset=utf-8", "pdf" to "application/pdf")

/**
 * Política de segurança da página. O MathLive desenha com estilos inline (style-src
 * 'unsafe-inline'); scripts só os arquivos do próprio servidor, sem inline e sem eval.
 */
private const val CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
    "font-src 'self'; img-src 'self' data: blob:; connect-src 'self'; media-src 'self'; object-src 'none'; " +
    "base-uri 'none'; form-action 'none'; frame-ancestors 'none'"

fun Application.modulo(config: Config, contas: Contas, folhas: Folhas, calc: Calculadora) {
    val raiz = config.web.canonicalFile
    if (config.proxy) install(XForwardedHeaders)

    install(StatusPages) {
        exception<ErroHttp> { call, e -> call.erro(e.codigo, e.message ?: "Erro.") }
        exception<Folhas.Conflito> { call, e -> call.json(409, e.folha.deepCopy().put("erro", e.message)) }
        exception<PedidoInvalido> { call, e -> call.erro(400, e.message ?: "Pedido inválido.") }
        exception<Ocupado> { call, e -> call.response.header("Retry-After", "5"); call.erro(503, e.message ?: "Ocupado.") }
        exception<Throwable> { call, e ->
            call.application.log.error("erro em ${call.request.httpMethod.value} ${call.request.path()}", e)
            call.erro(500, "Erro interno do servidor.")
        }
    }

    // cabeçalhos de segurança em toda resposta; CSRF nas rotas de escrita da API
    intercept(ApplicationCallPipeline.Plugins) {
        val caminho = call.request.path()
        val h = call.response.headers
        h.append("X-Content-Type-Options", "nosniff")
        h.append("Referrer-Policy", "no-referrer")
        h.append("X-Frame-Options", "DENY")
        h.append("Content-Security-Policy", CSP)
        h.append("Cache-Control", if (caminho.startsWith("/api/")) "no-store" else "no-cache")
        if (config.motor.isNotEmpty()) h.append("X-TracoLab-Motor", config.motor)
        if (caminho.startsWith("/api/") && call.request.httpMethod !in setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)) {
            val site = call.request.header("Sec-Fetch-Site")
            if (site != null && site !in setOf("same-origin", "same-site", "none")) { call.erro(403, "Pedido vindo de outro site."); return@intercept finish() }
            val origem = call.request.header("Origin")
            if (origem != null && origem != "null") {
                val autoridade = runCatching { URI(origem).authority?.lowercase() }.getOrNull()
                val meus = setOfNotNull(call.request.header("Host")?.lowercase(), call.request.origin.let { "${it.serverHost}:${it.serverPort}".lowercase() }, call.request.origin.serverHost.lowercase())
                if (autoridade == null || autoridade !in meus) { call.erro(403, "Pedido vindo de outro site."); return@intercept finish() }
            }
            val tipo = call.request.header("Content-Type")
            val temCorpo = (call.request.contentLength() ?: 0L) > 0L || call.request.header("Transfer-Encoding") != null
            if ((tipo != null && !tipo.lowercase().startsWith("application/json")) || (tipo == null && temCorpo)) {
                call.erro(415, "Envie JSON (Content-Type: application/json)."); return@intercept finish()
            }
        }
    }

    routing {
        // --- público
        get("/api/instancia") {
            val primeiro = withContext(Dispatchers.IO) { contas.total() == 0 }
            call.json(200, mapOf("versao" to VERSAO, "cadastro" to config.cadastro.name, "primeiroAcesso" to primeiro))
        }
        route("/api/conta") {
            route("/cadastrar") { post {
                val p = call.lerJson()
                val (u, token) = withContext(Dispatchers.IO) { contas.cadastrar(p.texto("usuario"), p.texto("senha"), p.texto("convite")) }
                call.cookieSessao(config, token, Contas.SESSAO_MS)
                call.json(201, mapOf("id" to u.id, "usuario" to u.nome, "admin" to u.admin))
            }; senao405("POST") }
            route("/entrar") { post {
                val p = call.lerJson()
                val e = withContext(Dispatchers.IO) { contas.entrar(p.texto("usuario"), p.texto("senha"), call.ip()) }
                call.cookieSessao(config, e.token, if (e.pendente2fa) Contas.SESSAO_PENDENTE_MS else Contas.SESSAO_MS)
                call.json(200, if (e.pendente2fa) mapOf("doisFatores" to true) else mapOf("ok" to true))
            }; senao405("POST") }
            route("/entrar/2fa") { post {
                val s = call.sessaoOuNull(contas)
                if (s == null || !s.pendente2fa) throw ErroHttp(401, "Entre com usuário e senha primeiro.")
                val p = call.lerJson()
                val token = withContext(Dispatchers.IO) { contas.confirmar2fa(s, p.texto("codigo"), call.ip()) }
                call.cookieSessao(config, token, Contas.SESSAO_MS)
                call.json(200, mapOf("ok" to true))
            }; senao405("POST") }

            // --- com sessão
            route("/sair") { post {
                call.sessaoOuNull(contas)?.let { withContext(Dispatchers.IO) { contas.sair(it.hash) } }
                call.response.cookies.append(Cookie(COOKIE, "", CookieEncoding.RAW, maxAge = 0, expires = GMTDate.START, path = "/", httpOnly = true))
                call.respondText("", status = HttpStatusCode.NoContent)
            }; senao405("POST") }
            get {
                val s = call.exigir(contas)
                call.json(200, mapOf("id" to s.usuario.id, "usuario" to s.usuario.nome, "admin" to s.usuario.admin,
                    "doisFatores" to s.usuario.doisFatores, "criadoEm" to s.usuario.criadoEm, "cadastro" to config.cadastro.name))
            }
            route("/senha") { post {
                val s = call.exigir(contas); val p = call.lerJson()
                withContext(Dispatchers.IO) { contas.trocarSenha(s, p.texto("atual"), p.texto("nova")) }
                call.semConteudo()
            }; senao405("POST") }
            route("/2fa/preparar") { post {
                val s = call.exigir(contas)
                val pr = withContext(Dispatchers.IO) { contas.preparar2fa(s) }
                call.json(200, mapOf("segredo" to pr.segredo, "otpauth" to pr.otpauth, "qr" to pr.qr))
            }; senao405("POST") }
            route("/2fa/ativar") { post {
                val s = call.exigir(contas); val p = call.lerJson()
                val codigos = withContext(Dispatchers.IO) { contas.ativar2fa(s, p.texto("codigo")) }
                call.json(200, mapOf("codigosRecuperacao" to codigos))
            }; senao405("POST") }
            route("/2fa/desativar") { post {
                val s = call.exigir(contas); val p = call.lerJson()
                withContext(Dispatchers.IO) { contas.desativar2fa(s, p.texto("senha"), p.texto("codigo")) }
                call.semConteudo()
            }; senao405("POST") }
            route("/2fa/codigos") { post {
                val s = call.exigir(contas); val p = call.lerJson()
                val codigos = withContext(Dispatchers.IO) { contas.renovarCodigos(s, p.texto("senha"), p.texto("codigo")) }
                call.json(200, mapOf("codigosRecuperacao" to codigos))
            }; senao405("POST") }
        }

        // --- administração
        route("/api/admin") {
            route("/usuarios") {
                get { call.exigirAdmin(contas); call.json(200, withContext(Dispatchers.IO) { contas.usuarios() }) }
                route("/{id}") {
                    delete {
                        val s = call.exigirAdmin(contas)
                        withContext(Dispatchers.IO) { contas.removerUsuario(s.usuario, call.id()) }
                        call.semConteudo()
                    }
                    route("/2fa/remover") { post { call.exigirAdmin(contas); withContext(Dispatchers.IO) { contas.zerar2fa(call.id()) }; call.semConteudo() }; senao405("POST") }
                    route("/admin") { post {
                        val s = call.exigirAdmin(contas); val p = call.lerJson()
                        withContext(Dispatchers.IO) { contas.definirAdmin(s.usuario, call.id(), p["admin"]?.asBoolean() == true) }
                        call.semConteudo()
                    }; senao405("POST") }
                    senao405("DELETE")
                }
                senao405("GET")
            }
            route("/convites") {
                post {
                    val s = call.exigirAdmin(contas)
                    val c = withContext(Dispatchers.IO) { contas.criarConvite(s.usuario) }
                    val o = call.request.origin
                    val porta = if ((o.scheme == "http" && o.serverPort == 80) || (o.scheme == "https" && o.serverPort == 443)) "" else ":${o.serverPort}"
                    call.json(201, mapOf("codigo" to c.codigo, "expiraEm" to c.expiraEm, "link" to "${o.scheme}://${o.serverHost}$porta/conta.html?convite=${c.codigo}"))
                }
                get { call.exigirAdmin(contas); call.json(200, withContext(Dispatchers.IO) { contas.convitesAbertos() }) }
                delete { call.exigirAdmin(contas); call.json(200, mapOf("cancelados" to withContext(Dispatchers.IO) { contas.cancelarConvites() })) }
                senao405("GET, POST, DELETE")
            }
        }

        // --- folhas
        route("/api/folhas") {
            get { val s = call.exigir(contas); call.json(200, withContext(Dispatchers.IO) { folhas.listar(s.usuario) }) }
            post { val s = call.exigir(contas); val p = call.lerJson(); call.json(201, withContext(Dispatchers.IO) { folhas.criar(s.usuario, p) }) }
            route("/{id}") {
                get { val s = call.exigir(contas); call.json(200, withContext(Dispatchers.IO) { folhas.abrir(s.usuario, call.parameters["id"]!!) }) }
                put { val s = call.exigir(contas); val p = call.lerJson(); call.json(200, withContext(Dispatchers.IO) { folhas.salvar(s.usuario, call.parameters["id"]!!, p) }) }
                post { val s = call.exigir(contas); val p = call.lerJson(); call.json(200, withContext(Dispatchers.IO) { folhas.salvar(s.usuario, call.parameters["id"]!!, p) }) }   // sendBeacon só faz POST
                delete { val s = call.exigir(contas); withContext(Dispatchers.IO) { folhas.apagar(s.usuario, call.parameters["id"]!!) }; call.semConteudo() }
                senao405("GET, PUT, POST, DELETE")
            }
            senao405("GET, POST")
        }

        // --- cálculo
        route("/api/calcular") {
            post {
                call.exigir(contas)
                val corpo = call.lerCorpo()
                val resp = withContext(Dispatchers.IO) { calc.calcularJson(corpo.toString(Charsets.UTF_8)) }
                call.respondText(resp, ContentType.Application.Json, HttpStatusCode.OK)
            }
            senao405("POST")
        }

        // --- arquivos da página (e 404 em JSON para o resto da API)
        route("/{...}") { handle { estatico(call, raiz) } }
        route("/") { handle { estatico(call, raiz) } }
    }
}

// ------------------------------------------------------------------ apoio

private fun Route.senao405(metodos: String) = handle {
    call.response.header("Allow", metodos)
    call.erro(405, "Use $metodos.")
}

private fun JsonNode.texto(campo: String): String? = get(campo)?.takeIf { it.isTextual }?.asText()

private fun ApplicationCall.id(): Long = parameters["id"]?.toLongOrNull() ?: throw ErroHttp(404, "Usuário não encontrado.")

private fun ApplicationCall.ip(): String = request.origin.remoteAddress

private suspend fun ApplicationCall.json(codigo: Int, corpo: Any) =
    respondText(JSON.writeValueAsString(corpo), ContentType.Application.Json, HttpStatusCode.fromValue(codigo))

private suspend fun ApplicationCall.erro(codigo: Int, msg: String) = json(codigo, mapOf("erro" to msg))

private suspend fun ApplicationCall.semConteudo() = respondText("", status = HttpStatusCode.NoContent)

private fun ApplicationCall.cookieSessao(config: Config, token: String, duracaoMs: Long) =
    response.cookies.append(Cookie(COOKIE, token, CookieEncoding.RAW, maxAge = (duracaoMs / 1000).toInt(), path = "/",
        secure = config.https, httpOnly = true, extensions = mapOf("SameSite" to "Strict")))

/** Sessão do cookie (lida uma vez por pedido), ou null. */
private suspend fun ApplicationCall.sessaoOuNull(contas: Contas): Sessao? {
    if (attributes.getOrNull(SESSAO_LIDA) != true) {
        attributes.put(SESSAO_LIDA, true)
        withContext(Dispatchers.IO) { contas.sessao(request.cookies[COOKIE]) }?.let { attributes.put(SESSAO, it) }
    }
    return attributes.getOrNull(SESSAO)
}

/** Sessão completa (senha e, se ativo, segundo fator), ou 401. */
private suspend fun ApplicationCall.exigir(contas: Contas): Sessao {
    val s = sessaoOuNull(contas)
    if (s == null || s.pendente2fa) throw ErroHttp(401, "Entre para continuar.")
    return s
}

private suspend fun ApplicationCall.exigirAdmin(contas: Contas): Sessao =
    exigir(contas).also { if (!it.usuario.admin) throw ErroHttp(403, "Só o administrador pode fazer isso.") }

/** Corpo inteiro, até [max] bytes; além disso, descarta o resto e responde 413 (a conexão não fica pendurada). */
private suspend fun ApplicationCall.lerCorpo(max: Int = MAX_CORPO): ByteArray {
    val canal = receiveChannel()
    val anunciado = request.contentLength()
    if (anunciado != null && anunciado > max) { canal.discard(4L * max); throw ErroHttp(413, "Pedido grande demais (máximo ${max / 1_000_000} MB).") }
    val bytes = canal.readRemaining(max.toLong() + 1).readByteArray()
    if (bytes.size > max) { canal.discard(4L * max); throw ErroHttp(413, "Pedido grande demais (máximo ${max / 1_000_000} MB).") }
    return bytes
}

private suspend fun ApplicationCall.lerJson(): JsonNode {
    val bytes = lerCorpo()
    if (bytes.isEmpty()) return JSON.createObjectNode()
    val arvore = try { JSON.readTree(bytes) } catch (e: Exception) { throw ErroHttp(400, "JSON inválido.") }
    return if (arvore is ObjectNode) arvore else throw ErroHttp(400, "O pedido deve ser um objeto JSON.")
}

/** Arquivos de web/: só GET/HEAD, só dentro da raiz, com Last-Modified e 304. */
private suspend fun estatico(call: ApplicationCall, raiz: File) {
    val caminho = call.request.path()
    if (caminho.startsWith("/api/")) { call.erro(404, "Rota inexistente."); return }
    if (call.request.httpMethod != HttpMethod.Get && call.request.httpMethod != HttpMethod.Head) {
        call.response.header("Allow", "GET, HEAD"); call.erro(405, "Use GET, HEAD."); return
    }
    val alvo = if (caminho == "/" || caminho.isEmpty()) "/index.html" else caminho
    val f = try { File(raiz, alvo).canonicalFile } catch (e: Exception) { null }   // ex.: caminho com NUL
    // toPath().startsWith compara pastas inteiras: "/x/web-foo" não está dentro de "/x/web"
    if (f == null || !f.toPath().startsWith(raiz.toPath()) || !f.isFile) {
        call.respondText("404", ContentType.Text.Plain, HttpStatusCode.NotFound); return
    }
    val modificado = f.lastModified() / 1000 * 1000
    call.response.header("Last-Modified", HTTP_DATA.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(modificado), ZoneOffset.UTC)))
    val desde = call.request.header("If-Modified-Since")?.let { runCatching { ZonedDateTime.parse(it, HTTP_DATA).toInstant().toEpochMilli() }.getOrNull() }
    if (desde != null && modificado <= desde) { call.respondText("", status = HttpStatusCode.NotModified); return }
    val tipo = TIPOS[f.extension]?.let { ContentType.parse(it) } ?: ContentType.Application.OctetStream
    call.respondBytes(withContext(Dispatchers.IO) { f.readBytes() }, tipo, HttpStatusCode.OK)
}
