package tracolab.nucleo

import org.matheclipse.core.basic.Config
import org.matheclipse.core.eval.exception.ASTElementLimitExceeded
import org.matheclipse.core.eval.exception.BigIntegerLimitExceeded
import org.matheclipse.core.eval.exception.LimitException
import org.matheclipse.core.eval.exception.MemoryLimitExceeded
import org.matheclipse.core.eval.exception.PolynomialDegreeLimitExceeded
import org.matheclipse.core.eval.exception.TimeoutException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Limites de um cálculo (ADR 0017): nada do que se digita pode travar a folha. Ao passar de um
 * limite, a região fica com erro (mensagem em pt-BR) e o resto da folha continua.
 *
 * @param msRegiao     tempo máximo de uma região
 * @param msFolha      tempo máximo da folha inteira; as regiões que sobrarem ficam com erro
 * @param profundidade chamadas aninhadas de funções do usuário (recursão)
 * @param elementos    valores de uma faixa, de um resultado por faixa e de um vetor montado por v[i] :=
 */
data class Limites(
    val msRegiao: Long = 3_000,
    val msFolha: Long = 15_000,
    val profundidade: Int = 200,
    val elementos: Int = 200_000,
)

/** O prazo da região (ou da folha) acabou. Não é [ErroCalculo]: quem captura erros de cálculo
 *  para tentar outro caminho não deve engolir a interrupção (ver [Motor.interrompido]). */
class TempoEsgotado(msg: String) : RuntimeException(msg, null, false, false)

/** "2 s", "1,5 s", "500 ms" */
fun textoTempo(ms: Long): String = when {
    ms < 1000 -> "$ms ms"
    ms % 1000 == 0L -> "${ms / 1000} s"
    else -> "%.1f s".format(ms / 1000.0).replace('.', ',')
}

/**
 * Prazo do cálculo em andamento num [Motor]. Dois mecanismos juntos:
 * - cooperativo: o nosso código chama [verificar] (avaliar, faixas, laços dos embutidos);
 * - no Symja: um vigia pede a parada ao motor (`EvalEngine.stopRequest`), e o laço de avaliação
 *   do Symja lança `TimeoutException`, que vira [TempoEsgotado].
 * Um motor é usado por uma thread de cada vez; o prazo segue essa regra.
 */
class Prazo internal constructor(private val motor: Motor) {
    private var fimNs = Long.MAX_VALUE
    /** Mensagem do erro se o prazo em vigor acabar. */
    var motivo = ""
        private set
    private var tarefa: ScheduledFuture<*>? = null

    val ativo get() = fimNs != Long.MAX_VALUE
    fun esgotado() = System.nanoTime() > fimNs

    fun verificar() { if (System.nanoTime() > fimNs) throw TempoEsgotado(motivo) }

    /**
     * Roda [bloco] com no máximo [ms] de prazo, sem nunca estender um prazo de fora já mais curto.
     * [motivo] é a mensagem do erro se o prazo acabar.
     */
    fun <T> com(ms: Long, motivo: String, bloco: () -> T): T {
        val fimAntes = fimNs; val motivoAntes = this.motivo
        val fim = System.nanoTime() + ms * 1_000_000
        if (fim >= fimAntes) return bloco()
        armar(fim, motivo)
        try {
            return bloco()
        } catch (e: TimeoutException) {
            throw TempoEsgotado(this.motivo)
        } finally { armar(fimAntes, motivoAntes) }
    }

    /**
     * Tenta [bloco] por no máximo [ms]; se esse prazo menor acabar, devolve null e o prazo de fora
     * continua valendo (ex.: integral simbólica por 1 s, depois a numérica). Se o prazo de fora
     * também acabou, a interrupção segue.
     */
    fun <T> tentarPor(ms: Long, bloco: () -> T): T? {
        val fimAntes = fimNs
        return try {
            com(ms, "tentativa esgotada", bloco)
        } catch (e: TempoEsgotado) {
            if (System.nanoTime() > fimAntes) throw TempoEsgotado(motivo) else null
        }
    }

    private fun armar(fim: Long, motivo: String) {
        tarefa?.cancel(false)
        tarefa = null
        fimNs = fim; this.motivo = motivo
        motor.engine.isStopRequested.let { if (it) motor.engine.setStopRequested(false) }
        if (fim != Long.MAX_VALUE) {
            val engine = motor.engine
            tarefa = VIGIA.schedule({ engine.stopRequest() }, fim - System.nanoTime(), TimeUnit.NANOSECONDS)
        }
    }

    companion object {
        /** Um vigia só para todos os motores: dispara o pedido de parada no fim de cada prazo. */
        private val VIGIA = ScheduledThreadPoolExecutor(1) { r ->
            Thread(r, "tracolab-vigia").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}

/**
 * Limites globais do Symja, ajustados uma vez por processo: sem eles, `10^10^10` tenta montar um
 * inteiro de 4 GB e trava. Com eles, a conta é recusada na hora e fica sem avaliar (o `N` dá
 * `Overflow`, que vira erro claro).
 */
internal object LimitesSymja {
    init {
        Config.MAX_BIT_LENGTH = 200_000              // ~60 mil algarismos
        Config.MAX_AST_SIZE = 1_000_000              // listas e expressões
        Config.MAX_POLYNOMIAL_DEGREE = 1_000
        Config.MAX_MATRIX_DIMENSION_SIZE = 1_000
    }
    fun garantir() = Unit
}

/** Mensagem de erro em pt-BR para limites e falhas graves durante a avaliação de uma região. */
internal fun mensagemDeLimite(e: Throwable, prazo: String): String? = when (e) {
    is TempoEsgotado -> e.message
    is TimeoutException -> prazo
    is BigIntegerLimitExceeded -> "Número grande demais para a conta exata (mais de 60 mil algarismos)."
    is ASTElementLimitExceeded -> "Expressão ou lista grande demais para o motor."
    is PolynomialDegreeLimitExceeded -> "Polinômio de grau alto demais."
    is MemoryLimitExceeded -> "Memória insuficiente para este cálculo."
    is LimitException -> "Cálculo grande demais para o motor (limite de repetições)."
    is StackOverflowError -> "Fórmula ou recursão profunda demais."
    is OutOfMemoryError -> "Memória insuficiente para este cálculo."
    else -> null
}
