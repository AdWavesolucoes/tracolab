package tracolab.servidor

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Folhas de cada usuário (ADR 0019). O conteúdo é o JSON que a interface já guardava no
 * navegador (`{modo, ordem, regioes, ...}`), sem interpretação: o servidor só confere que é um
 * objeto e cabe no limite. A `versao` sobe a cada gravação; gravar com uma versão velha dá 409
 * com o conteúdo atual, para o navegador decidir.
 */
class Folhas(private val banco: Banco, private val json: ObjectMapper) {
    companion object {
        const val MAX_CONTEUDO = 4_000_000
        const val MAX_POR_USUARIO = 500
        const val NOME_PADRAO = "Folha sem título"
    }

    private fun nome(n: JsonNode?) = n?.takeIf { it.isTextual }?.asText()?.trim()?.take(200)?.takeIf { it.isNotEmpty() } ?: NOME_PADRAO

    private fun conteudo(n: JsonNode?): String {
        if (n == null || !n.isObject) throw ErroHttp(400, "O conteúdo da folha precisa ser um objeto.")
        val texto = json.writeValueAsString(n)
        if (texto.length > MAX_CONTEUDO) throw ErroHttp(413, "Folha grande demais (máximo ${MAX_CONTEUDO / 1_000_000} MB).")
        return texto
    }

    fun listar(usuario: Usuario): List<Map<String, Any?>> = banco.uso { c ->
        c.consulta("SELECT id, nome, versao, criada_em, atualizada_em FROM folhas WHERE usuario_id = ? ORDER BY atualizada_em DESC", usuario.id)
            .lista { mapOf("id" to it.getString(1), "nome" to it.getString(2), "versao" to it.getLong(3), "criadaEm" to it.getLong(4), "atualizadaEm" to it.getLong(5)) }
    }

    fun criar(usuario: Usuario, pedido: JsonNode): ObjectNode {
        val n = nome(pedido["nome"]); val cont = conteudo(pedido["conteudo"])
        return banco.transacao { c ->
            val quantas = c.consulta("SELECT count(*) FROM folhas WHERE usuario_id = ?", usuario.id).uma { it.getInt(1) } ?: 0
            if (quantas >= MAX_POR_USUARIO) throw ErroHttp(400, "Limite de $MAX_POR_USUARIO folhas por conta. Apague alguma antes de criar outra.")
            val id = novoToken().take(16); val t = agora()
            c.executa("INSERT INTO folhas (id, usuario_id, nome, conteudo, versao, criada_em, atualizada_em) VALUES (?, ?, ?, ?, 1, ?, ?)", id, usuario.id, n, cont, t, t)
            json.createObjectNode().put("id", id).put("nome", n).put("versao", 1).put("criadaEm", t).put("atualizadaEm", t)
        }
    }

    fun abrir(usuario: Usuario, id: String): ObjectNode = banco.uso { c ->
        c.consulta("SELECT nome, conteudo, versao, criada_em, atualizada_em FROM folhas WHERE id = ? AND usuario_id = ?", id, usuario.id).uma { r ->
            json.createObjectNode().put("id", id).put("nome", r.getString(1)).put("versao", r.getLong(3)).put("criadaEm", r.getLong(4)).put("atualizadaEm", r.getLong(5))
                .also { it.set<JsonNode>("conteudo", json.readTree(r.getString(2))) }
        } ?: throw ErroHttp(404, "Folha não encontrada.")
    }

    /** Grava se a versão enviada for a atual; senão, lança 409 com a folha como está no servidor. */
    fun salvar(usuario: Usuario, id: String, pedido: JsonNode): ObjectNode {
        val n = nome(pedido["nome"]); val cont = conteudo(pedido["conteudo"])
        val versao = pedido["versao"]?.takeIf { it.isNumber }?.asLong() ?: throw ErroHttp(400, "Informe a versão da folha que você abriu.")
        return banco.transacao { c ->
            val atual = c.consulta("SELECT versao FROM folhas WHERE id = ? AND usuario_id = ?", id, usuario.id).uma { it.getLong(1) } ?: throw ErroHttp(404, "Folha não encontrada.")
            if (atual != versao) throw Conflito(abrir(usuario, id))
            val t = agora()
            c.executa("UPDATE folhas SET nome = ?, conteudo = ?, versao = ?, atualizada_em = ? WHERE id = ?", n, cont, atual + 1, t, id)
            json.createObjectNode().put("id", id).put("nome", n).put("versao", atual + 1).put("atualizadaEm", t)
        }
    }

    fun apagar(usuario: Usuario, id: String) {
        if (banco.uso { c -> c.executa("DELETE FROM folhas WHERE id = ? AND usuario_id = ?", id, usuario.id) } == 0) throw ErroHttp(404, "Folha não encontrada.")
    }

    /** 409: a folha mudou em outro lugar. Leva a versão do servidor. */
    class Conflito(val folha: ObjectNode) : Exception("A folha foi alterada em outro lugar.")
}
