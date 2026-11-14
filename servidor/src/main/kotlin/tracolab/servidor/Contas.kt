package tracolab.servidor

import java.util.concurrent.ConcurrentHashMap

class ErroHttp(val codigo: Int, mensagem: String) : Exception(mensagem)

data class Usuario(val id: Long, val nome: String, val admin: Boolean, val doisFatores: Boolean, val criadoEm: Long)

data class Sessao(val hash: String, val usuario: Usuario, val pendente2fa: Boolean)

/**
 * Usuários, sessões, convites e verificação em duas etapas (ADR 0019). Tudo que mexe no banco
 * passa por aqui; o servidor só traduz HTTP.
 */
class Contas(private val banco: Banco, private val config: Config) {
    private val reNome = Regex("^[a-z0-9][a-z0-9._-]{2,31}$")
    private val tentativas = ConcurrentHashMap<String, Tentativas>()

    private class Tentativas(var n: Int, var inicio: Long)

    companion object {
        const val SESSAO_MS = 30L * 24 * 3600 * 1000
        const val SESSAO_PENDENTE_MS = 10L * 60 * 1000
        const val CONVITE_MS = 7L * 24 * 3600 * 1000
        const val JANELA_MS = 15L * 60 * 1000
        const val SENHA_MIN = 10
        const val SENHA_MAX = 200
    }

    // ------------------------------------------------------------------ leitura

    fun total(): Int = banco.uso { c -> c.consulta("SELECT count(*) FROM usuarios").uma { it.getInt(1) } ?: 0 }

    private fun lerUsuario(r: java.sql.ResultSet) =
        Usuario(r.getLong("id"), r.getString("nome"), r.getInt("admin") == 1, r.getString("totp_segredo") != null, r.getLong("criado_em"))

    fun usuario(id: Long): Usuario? = banco.uso { c -> c.consulta("SELECT * FROM usuarios WHERE id = ?", id).uma(::lerUsuario) }

    fun usuarios(): List<Map<String, Any?>> = banco.uso { c ->
        c.consulta("""SELECT u.*, (SELECT count(*) FROM folhas f WHERE f.usuario_id = u.id) AS folhas
                      FROM usuarios u ORDER BY u.criado_em""").lista { r ->
            val u = lerUsuario(r)
            mapOf("id" to u.id, "usuario" to u.nome, "admin" to u.admin, "doisFatores" to u.doisFatores, "criadoEm" to u.criadoEm, "folhas" to r.getInt("folhas"))
        }
    }

    // ------------------------------------------------------------------ cadastro

    /** Valida nome e senha (400) e as regras de cadastro (403/409); devolve o usuário já com sessão aberta. */
    fun cadastrar(nomeBruto: String?, senha: String?, convite: String?): Pair<Usuario, String> {
        val nome = nomeBruto?.trim()?.lowercase().orEmpty()
        if (!reNome.matches(nome)) throw ErroHttp(400, "Nome de usuário: 3 a 32 caracteres, só letras minúsculas, números, ponto, traço e sublinhado.")
        validarSenha(senha)
        return banco.transacao { c ->
            val primeiro = (c.consulta("SELECT count(*) FROM usuarios").uma { it.getInt(1) } ?: 0) == 0
            var conviteHash: String? = null
            if (!primeiro) when (config.cadastro) {
                Cadastro.fechado -> throw ErroHttp(403, "Esta instância não aceita novos cadastros.")
                Cadastro.aberto -> {}
                Cadastro.convite -> {
                    val h = convite?.trim()?.takeIf { it.isNotEmpty() }?.let(::sha256Hex) ?: throw ErroHttp(403, "É preciso um convite para criar conta.")
                    val valido = c.consulta("SELECT 1 FROM convites WHERE hash = ? AND usado_em IS NULL AND expira_em > ?", h, agora()).uma { true } ?: false
                    if (!valido) throw ErroHttp(403, "Convite inválido, usado ou vencido.")
                    conviteHash = h
                }
            }
            if (c.consulta("SELECT 1 FROM usuarios WHERE nome = ?", nome).uma { true } == true) throw ErroHttp(409, "Esse nome de usuário já existe.")
            val t = agora()
            c.executa("INSERT INTO usuarios (nome, senha, admin, criado_em, senha_em) VALUES (?, ?, ?, ?, ?)", nome, Senhas.gerar(senha!!), primeiro, t, t)
            val id = c.consulta("SELECT id FROM usuarios WHERE nome = ?", nome).uma { it.getLong(1) }!!
            conviteHash?.let { c.executa("UPDATE convites SET usado_por = ?, usado_em = ? WHERE hash = ?", id, t, it) }
            val u = Usuario(id, nome, primeiro, false, t)
            u to novaSessao(c, id, pendente = false)
        }
    }

    private fun validarSenha(senha: String?) {
        if (senha == null || senha.length < SENHA_MIN) throw ErroHttp(400, "A senha precisa ter pelo menos $SENHA_MIN caracteres.")
        if (senha.length > SENHA_MAX) throw ErroHttp(400, "Senha longa demais (máximo $SENHA_MAX caracteres).")
    }

    // ------------------------------------------------------------------ entrar e sair

    /** Resultado de entrar: sessão completa (`ok`) ou pendente da segunda etapa. */
    data class Entrada(val token: String, val pendente2fa: Boolean)

    fun entrar(nomeBruto: String?, senha: String?, ip: String): Entrada {
        val nome = nomeBruto?.trim()?.lowercase().orEmpty()
        val chaveU = "u:$nome"; val chaveIp = "ip:$ip"
        if (bloqueado(chaveU, config.tentativas) || bloqueado(chaveIp, config.tentativas * 10))
            throw ErroHttp(429, "Muitas tentativas. Espere 15 minutos e tente de novo.")
        val linha = banco.uso { c -> c.consulta("SELECT id, senha, totp_segredo FROM usuarios WHERE nome = ?", nome).uma { Triple(it.getLong(1), it.getString(2), it.getString(3)) } }
        val confere = Senhas.confere(senha.orEmpty(), linha?.second ?: Senhas.FANTASMA)
        if (linha == null || !confere) {
            falhou(chaveU); falhou(chaveIp)
            throw ErroHttp(401, "Usuário ou senha incorretos.")
        }
        tentativas.remove(chaveU)
        val pendente = linha.third != null
        val token = banco.uso { c -> novaSessao(c, linha.first, pendente) }
        return Entrada(token, pendente)
    }

    /** Segunda etapa: TOTP ou código de recuperação. Devolve o token da sessão completa (o pendente morre). */
    fun confirmar2fa(sessao: Sessao, codigo: String?, ip: String): String {
        val chaveU = "u:${sessao.usuario.nome}"
        if (bloqueado(chaveU, config.tentativas) || bloqueado("ip:$ip", config.tentativas * 10)) throw ErroHttp(429, "Muitas tentativas. Espere 15 minutos e tente de novo.")
        val ok = banco.transacao { c -> conferirSegundoFator(c, sessao.usuario.id, codigo) }
        if (!ok) { falhou(chaveU); falhou("ip:$ip"); throw ErroHttp(401, "Código inválido.") }
        tentativas.remove(chaveU)
        return banco.transacao { c ->
            c.executa("DELETE FROM sessoes WHERE hash = ?", sessao.hash)
            novaSessao(c, sessao.usuario.id, pendente = false)
        }
    }

    /** TOTP do segredo ativo, ou um código de recuperação ainda não usado (que fica usado). */
    private fun conferirSegundoFator(c: java.sql.Connection, usuarioId: Long, codigo: String?): Boolean {
        val digitado = codigo?.trim()?.lowercase().orEmpty()
        if (digitado.isEmpty()) return false
        val segredo = c.consulta("SELECT totp_segredo FROM usuarios WHERE id = ?", usuarioId).uma { it.getString(1) } ?: return false
        if (Totp.confere(segredo, digitado)) return true
        val h = sha256Hex(digitado.replace("-", "").replace(" ", ""))
        return c.executa("UPDATE codigos_recuperacao SET usado = 1 WHERE usuario_id = ? AND hash = ? AND usado = 0", usuarioId, h) == 1
    }

    fun sair(hash: String) = banco.uso { c -> c.executa("DELETE FROM sessoes WHERE hash = ?", hash) }

    private fun novaSessao(c: java.sql.Connection, usuarioId: Long, pendente: Boolean): String {
        val token = novoToken(); val t = agora()
        c.executa("INSERT INTO sessoes (hash, usuario_id, pendente_2fa, criada_em, expira_em, usada_em) VALUES (?, ?, ?, ?, ?, ?)",
            sha256Hex(token), usuarioId, pendente, t, t + if (pendente) SESSAO_PENDENTE_MS else SESSAO_MS, t)
        return token
    }

    /** Sessão do cookie, se existir e não tiver vencido. Renova a validade de hora em hora. */
    fun sessao(token: String?): Sessao? {
        if (token.isNullOrEmpty()) return null
        val hash = sha256Hex(token)
        return banco.uso { c ->
            val t = agora()
            val linha = c.consulta("""SELECT s.pendente_2fa, s.expira_em, s.usada_em, u.* FROM sessoes s JOIN usuarios u ON u.id = s.usuario_id
                                      WHERE s.hash = ?""", hash).uma { r -> Triple(r.getInt("pendente_2fa") == 1, r.getLong("expira_em") to r.getLong("usada_em"), lerUsuario(r)) }
                ?: return@uso null
            val (pendente, prazos, u) = linha
            if (prazos.first < t) { c.executa("DELETE FROM sessoes WHERE hash = ?", hash); return@uso null }
            if (!pendente && t - prazos.second > 3600_000) c.executa("UPDATE sessoes SET usada_em = ?, expira_em = ? WHERE hash = ?", t, t + SESSAO_MS, hash)
            Sessao(hash, u, pendente)
        }
    }

    // ------------------------------------------------------------------ limite de tentativas

    private fun bloqueado(chave: String, limite: Int): Boolean {
        val t = tentativas[chave] ?: return false
        if (agora() - t.inicio > JANELA_MS) { tentativas.remove(chave); return false }
        return t.n >= limite
    }

    private fun falhou(chave: String) {
        val t = agora()
        tentativas.compute(chave) { _, v -> if (v == null || t - v.inicio > JANELA_MS) Tentativas(1, t) else v.also { it.n++ } }
        if (tentativas.size > 50_000) tentativas.clear()   // nunca cresce sem limite
    }

    // ------------------------------------------------------------------ senha e 2FA do próprio usuário

    fun trocarSenha(sessao: Sessao, atual: String?, nova: String?) {
        validarSenha(nova)
        banco.transacao { c ->
            val guardada = c.consulta("SELECT senha FROM usuarios WHERE id = ?", sessao.usuario.id).uma { it.getString(1) }!!
            if (!Senhas.confere(atual.orEmpty(), guardada)) throw ErroHttp(401, "A senha atual não confere.")
            c.executa("UPDATE usuarios SET senha = ?, senha_em = ? WHERE id = ?", Senhas.gerar(nova!!), agora(), sessao.usuario.id)
            c.executa("DELETE FROM sessoes WHERE usuario_id = ? AND hash <> ?", sessao.usuario.id, sessao.hash)   // as outras sessões caem
        }
    }

    data class Preparo(val segredo: String, val otpauth: String, val qr: String)

    fun preparar2fa(sessao: Sessao): Preparo {
        val segredo = Totp.novoSegredo()
        banco.uso { c -> c.executa("UPDATE usuarios SET totp_pendente = ? WHERE id = ?", segredo, sessao.usuario.id) }
        val uri = Totp.otpauth(sessao.usuario.nome, segredo)
        return Preparo(segredo, uri, Totp.qrSvg(uri))
    }

    /** Confirma o segredo preparado com um código do app e devolve os códigos de recuperação (só agora, só uma vez). */
    fun ativar2fa(sessao: Sessao, codigo: String?): List<String> = banco.transacao { c ->
        val pendente = c.consulta("SELECT totp_pendente FROM usuarios WHERE id = ?", sessao.usuario.id).uma { it.getString(1) }
            ?: throw ErroHttp(400, "Primeiro prepare a verificação em duas etapas.")
        if (!Totp.confere(pendente, codigo.orEmpty())) throw ErroHttp(400, "Código inválido. Confira o relógio do celular e tente de novo.")
        c.executa("UPDATE usuarios SET totp_segredo = ?, totp_pendente = NULL WHERE id = ?", pendente, sessao.usuario.id)
        novosCodigos(c, sessao.usuario.id)
    }

    fun desativar2fa(sessao: Sessao, senha: String?, codigo: String?) = banco.transacao { c ->
        val guardada = c.consulta("SELECT senha FROM usuarios WHERE id = ?", sessao.usuario.id).uma { it.getString(1) }!!
        if (!Senhas.confere(senha.orEmpty(), guardada)) throw ErroHttp(401, "A senha não confere.")
        if (!conferirSegundoFator(c, sessao.usuario.id, codigo)) throw ErroHttp(400, "Código inválido.")
        c.executa("UPDATE usuarios SET totp_segredo = NULL, totp_pendente = NULL WHERE id = ?", sessao.usuario.id)
        c.executa("DELETE FROM codigos_recuperacao WHERE usuario_id = ?", sessao.usuario.id)
    }

    fun renovarCodigos(sessao: Sessao, senha: String?, codigo: String?): List<String> = banco.transacao { c ->
        val guardada = c.consulta("SELECT senha FROM usuarios WHERE id = ?", sessao.usuario.id).uma { it.getString(1) }!!
        if (!Senhas.confere(senha.orEmpty(), guardada)) throw ErroHttp(401, "A senha não confere.")
        if (!sessao.usuario.doisFatores) throw ErroHttp(400, "A verificação em duas etapas não está ativa.")
        if (!conferirSegundoFator(c, sessao.usuario.id, codigo)) throw ErroHttp(400, "Código inválido.")
        novosCodigos(c, sessao.usuario.id)
    }

    private fun novosCodigos(c: java.sql.Connection, usuarioId: Long): List<String> {
        c.executa("DELETE FROM codigos_recuperacao WHERE usuario_id = ?", usuarioId)
        return List(10) { codigoLegivel(10) }.map { "${it.substring(0, 5)}-${it.substring(5)}" }.also { lista ->
            for (cod in lista) c.executa("INSERT INTO codigos_recuperacao (usuario_id, hash) VALUES (?, ?)", usuarioId, sha256Hex(cod.replace("-", "")))
        }
    }

    // ------------------------------------------------------------------ administração

    data class Convite(val codigo: String, val expiraEm: Long)

    fun criarConvite(admin: Usuario): Convite {
        val codigo = codigoLegivel(20); val t = agora()
        banco.uso { c -> c.executa("INSERT INTO convites (hash, criado_por, criado_em, expira_em) VALUES (?, ?, ?, ?)", sha256Hex(codigo), admin.id, t, t + CONVITE_MS) }
        return Convite(codigo, t + CONVITE_MS)
    }

    fun convitesAbertos(): List<Map<String, Any?>> = banco.uso { c ->
        c.consulta("""SELECT c.criado_em, c.expira_em, u.nome FROM convites c LEFT JOIN usuarios u ON u.id = c.criado_por
                      WHERE c.usado_em IS NULL AND c.expira_em > ? ORDER BY c.criado_em DESC""", agora())
            .lista { mapOf("criadoEm" to it.getLong(1), "expiraEm" to it.getLong(2), "criadoPor" to it.getString(3)) }
    }

    fun cancelarConvites(): Int = banco.uso { c -> c.executa("DELETE FROM convites WHERE usado_em IS NULL") }

    fun removerUsuario(admin: Usuario, id: Long) {
        if (id == admin.id) throw ErroHttp(400, "Você não pode remover a própria conta por aqui.")
        if (banco.uso { c -> c.executa("DELETE FROM usuarios WHERE id = ?", id) } == 0) throw ErroHttp(404, "Usuário não encontrado.")
    }

    fun zerar2fa(id: Long) = banco.transacao { c ->
        if (c.executa("UPDATE usuarios SET totp_segredo = NULL, totp_pendente = NULL WHERE id = ?", id) == 0) throw ErroHttp(404, "Usuário não encontrado.")
        c.executa("DELETE FROM codigos_recuperacao WHERE usuario_id = ?", id)
        c.executa("DELETE FROM sessoes WHERE usuario_id = ? AND pendente_2fa = 1", id)
    }

    fun definirAdmin(admin: Usuario, id: Long, valor: Boolean) {
        if (id == admin.id && !valor) throw ErroHttp(400, "Você não pode tirar a própria administração.")
        if (banco.uso { c -> c.executa("UPDATE usuarios SET admin = ? WHERE id = ?", valor, id) } == 0) throw ErroHttp(404, "Usuário não encontrado.")
    }

    /** Limpeza periódica: sessões vencidas e convites velhos. */
    fun limpar() = banco.uso { c ->
        val t = agora()
        c.executa("DELETE FROM sessoes WHERE expira_em < ?", t)
        c.executa("DELETE FROM convites WHERE usado_em IS NULL AND expira_em < ?", t)
    }
}
