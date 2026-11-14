package tracolab.servidor

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * SQLite num arquivo só (ADR 0019). Uma conexão, usada por uma thread de cada vez: o SQLite não
 * ganha nada com várias conexões escrevendo, e o serviço não é de alto volume. Chamar sempre de
 * dentro de `withContext(Dispatchers.IO)`.
 */
class Banco(diretorio: File) : AutoCloseable {
    private val conexao: Connection

    init {
        diretorio.mkdirs()
        conexao = DriverManager.getConnection("jdbc:sqlite:${File(diretorio, "tracolab.db").path}")
        conexao.createStatement().use {
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA foreign_keys=ON")
            it.execute("PRAGMA busy_timeout=5000")
        }
        migrar()
    }

    /** Roda [bloco] com a conexão, uma thread por vez. */
    fun <T> uso(bloco: (Connection) -> T): T = synchronized(this) { bloco(conexao) }

    /** Transação: tudo ou nada. */
    fun <T> transacao(bloco: (Connection) -> T): T = uso { c ->
        c.autoCommit = false
        try { bloco(c).also { c.commit() } } catch (e: Throwable) { c.rollback(); throw e } finally { c.autoCommit = true }
    }

    private fun migrar() = uso { c ->
        c.createStatement().use { it.execute("CREATE TABLE IF NOT EXISTS migracoes (versao INTEGER PRIMARY KEY, aplicada_em INTEGER NOT NULL)") }
        val feitas = c.createStatement().use { s -> s.executeQuery("SELECT versao FROM migracoes").lista { it.getInt(1) }.toSet() }
        for ((versao, sql) in MIGRACOES) if (versao !in feitas) {
            c.autoCommit = false
            try {
                c.createStatement().use { s -> for (cmd in sql.split(";")) if (cmd.isNotBlank()) s.execute(cmd) }
                c.prepareStatement("INSERT INTO migracoes VALUES (?, ?)").use { it.setInt(1, versao); it.setLong(2, agora()); it.executeUpdate() }
                c.commit()
            } catch (e: Throwable) { c.rollback(); throw e } finally { c.autoCommit = true }
        }
    }

    override fun close() = conexao.close()

    companion object {
        /** Migrações numeradas: só se acrescenta no fim, nunca se edita uma aplicada. */
        val MIGRACOES = listOf(
            1 to """
                CREATE TABLE usuarios (
                    id INTEGER PRIMARY KEY,
                    nome TEXT NOT NULL UNIQUE,
                    senha TEXT NOT NULL,
                    admin INTEGER NOT NULL DEFAULT 0,
                    totp_segredo TEXT,
                    totp_pendente TEXT,
                    criado_em INTEGER NOT NULL,
                    senha_em INTEGER NOT NULL
                );
                CREATE TABLE codigos_recuperacao (
                    usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
                    hash TEXT NOT NULL,
                    usado INTEGER NOT NULL DEFAULT 0
                );
                CREATE INDEX codigos_usuario ON codigos_recuperacao(usuario_id);
                CREATE TABLE sessoes (
                    hash TEXT PRIMARY KEY,
                    usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
                    pendente_2fa INTEGER NOT NULL DEFAULT 0,
                    criada_em INTEGER NOT NULL,
                    expira_em INTEGER NOT NULL,
                    usada_em INTEGER NOT NULL
                );
                CREATE INDEX sessoes_usuario ON sessoes(usuario_id);
                CREATE TABLE convites (
                    hash TEXT PRIMARY KEY,
                    criado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,
                    criado_em INTEGER NOT NULL,
                    expira_em INTEGER NOT NULL,
                    usado_por INTEGER REFERENCES usuarios(id) ON DELETE SET NULL,
                    usado_em INTEGER
                );
                CREATE TABLE folhas (
                    id TEXT PRIMARY KEY,
                    usuario_id INTEGER NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
                    nome TEXT NOT NULL,
                    conteudo TEXT NOT NULL,
                    versao INTEGER NOT NULL DEFAULT 1,
                    criada_em INTEGER NOT NULL,
                    atualizada_em INTEGER NOT NULL
                );
                CREATE INDEX folhas_usuario ON folhas(usuario_id, atualizada_em)
            """,
        )
    }
}

fun agora(): Long = System.currentTimeMillis()

/** Lê todas as linhas de um ResultSet. */
fun <T> ResultSet.lista(linha: (ResultSet) -> T): List<T> = use { val r = ArrayList<T>(); while (next()) r += linha(this); r }

/** Lê uma linha ou null. */
fun <T> ResultSet.uma(linha: (ResultSet) -> T): T? = use { if (next()) linha(this) else null }

/** `prepare(sql, a, b, c)`: liga os parâmetros na ordem. */
fun Connection.prep(sql: String, vararg params: Any?): PreparedStatement = prepareStatement(sql).also { st ->
    params.forEachIndexed { i, p ->
        when (p) {
            null -> st.setNull(i + 1, java.sql.Types.NULL)
            is Int -> st.setInt(i + 1, p)
            is Long -> st.setLong(i + 1, p)
            is Boolean -> st.setInt(i + 1, if (p) 1 else 0)
            else -> st.setString(i + 1, p.toString())
        }
    }
}

fun Connection.consulta(sql: String, vararg params: Any?): ResultSet = prep(sql, *params).executeQuery()
fun Connection.executa(sql: String, vararg params: Any?): Int = prep(sql, *params).use { it.executeUpdate() }
