package tracolab.servidor

import java.io.File

const val VERSAO = "0.1.0"

/** Modo de cadastro da instância (ADR 0019). A primeira conta sempre pode ser criada: vira administradora. */
enum class Cadastro { convite, aberto, fechado }

/**
 * Tudo vem do ambiente, para a imagem Docker e o systemd configurarem do mesmo jeito:
 *
 *   TRACOLAB_ENDERECO   onde escutar (padrão 0.0.0.0)
 *   TRACOLAB_PORTA      porta (padrão 8098)
 *   TRACOLAB_DADOS      diretório do banco SQLite (padrão ./dados)
 *   TRACOLAB_WEB        diretório da página (padrão ./web)
 *   TRACOLAB_CADASTRO   convite (padrão) | aberto | fechado
 *   TRACOLAB_HTTPS      1 quando a instância é servida por HTTPS (cookie Secure)
 *   TRACOLAB_PROXY      1 quando há um proxy na frente (confia em X-Forwarded-For/Proto)
 *   TRACOLAB_MOTORES    cálculos ao mesmo tempo (padrão 2; ADR 0017)
 *   TRACOLAB_MOTOR      nome desta instância no cabeçalho X-TracoLab-Motor
 *   TRACOLAB_TENTATIVAS entradas erradas por usuário antes do bloqueio de 15 min (padrão 10)
 *
 * Os dois primeiros também podem vir como argumentos: `servidor <endereço> <porta>`.
 */
data class Config(
    val endereco: String = env("TRACOLAB_ENDERECO") ?: "0.0.0.0",
    val porta: Int = env("TRACOLAB_PORTA")?.toIntOrNull() ?: 8098,
    val dados: File = File(env("TRACOLAB_DADOS") ?: "dados"),
    val web: File = File(env("TRACOLAB_WEB") ?: "web"),
    val cadastro: Cadastro = env("TRACOLAB_CADASTRO")?.let { runCatching { Cadastro.valueOf(it.lowercase()) }.getOrNull() } ?: Cadastro.convite,
    val https: Boolean = env("TRACOLAB_HTTPS") == "1",
    val proxy: Boolean = env("TRACOLAB_PROXY") == "1",
    val motores: Int = env("TRACOLAB_MOTORES")?.toIntOrNull()?.coerceIn(1, 8) ?: 2,
    val motor: String = env("TRACOLAB_MOTOR").orEmpty(),
    val tentativas: Int = env("TRACOLAB_TENTATIVAS")?.toIntOrNull()?.coerceIn(3, 1000) ?: 10,
) {
    companion object {
        private fun env(nome: String) = System.getenv(nome)?.trim()?.takeIf { it.isNotEmpty() }
        fun deArgumentos(args: Array<String>): Config {
            val base = Config()
            return base.copy(endereco = args.getOrNull(0) ?: base.endereco, porta = args.getOrNull(1)?.toIntOrNull() ?: base.porta)
        }
    }
}
