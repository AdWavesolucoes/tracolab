package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr

// ------------------------------------------------------------------ erros

class NaoSuportado(msg: String) : Exception(msg)
class ErroCalculo(msg: String) : Exception(msg)
class ErroUnidade(msg: String) : Exception(msg)
class UsaFaixa(val nome: String, val faixa: Faixa) : Exception(nome)

// ------------------------------------------------------------------ valores e regiões

/** Valor guardado no ambiente: expressão, faixa ou função do usuário. */
sealed class Valor {
    data class Expr(val e: IExpr) : Valor()
    data class Faixa_(val f: Faixa) : Valor()
    data class Funcao(val params: List<String>, val corpo: No, val env: MutableMap<String, Valor>) : Valor()
}

class Faixa(val inicio: IExpr, val passo: IExpr, val fim: IExpr) {
    fun valores(m: Motor, maximo: Int = Limites().elementos): List<IExpr> {
        val (a, d, b) = numeros(m, inicio, passo, fim)
        if (d == 0.0 || (b - a) / d < 0 || !((b - a) / d).isFinite()) throw ErroCalculo("Faixa inválida")
        val n = ((b - a) / d + 1e-9).toLong() + 1
        if (n > maximo) throw ErroCalculo("Faixa grande demais: $n valores (máximo $maximo).")
        return (0 until n.toInt()).map { k -> if (k % 1024 == 0) m.verificarPrazo(); m.ev(F.Plus(inicio, F.Times(F.ZZ(k.toLong()), passo))) }
    }

    companion object {
        /** Unidade comum dos limites de uma faixa (0 combina com qualquer uma); 1 se não têm unidade. */
        fun unidade(m: Motor, vararg xs: IExpr): IExpr {
            val com = xs.filter { !it.isZero && m.temUnidade(it) }
            if (com.isEmpty()) return F.C1
            val d = m.dims(com[0])
            if (xs.any { !it.isZero && !m.dims(it).contentEquals(d) }) throw ErroUnidade("As unidades dos limites da faixa não são compatíveis.")
            return m.monomio(d)
        }

        /** Números dos limites na unidade comum: 0{m}, 0.5{m}..2{m} dá 0, 0.5 e 2. */
        fun numeros(m: Motor, vararg xs: IExpr): List<Double> {
            val u = unidade(m, *xs)
            return xs.map { x ->
                try { m.dbl(m.ev(F.Divide(x, u))) } catch (e: Exception) {
                    if (m.interrompido(e)) throw e
                    throw ErroCalculo("Os limites da faixa devem ser números.")
                }
            }
        }
    }
}

data class Estado(
    val tipo: String,                    // numerico | parcial | erro | nao_suportado
    val valor: IExpr? = null,
    val livres: List<String> = emptyList(),
    val msg: String? = null,
    var aviso: String? = null,
    val faixa: Faixa? = null,
    val funcao: Valor.Funcao? = null,
    val verificacao: Verificacao? = null,   // σ ≤ σ_adm =: atende ou não, e a utilização (ADR 0013)
)

class Regiao(
    val id: String, val y: Double, val x: Double,
    val tipo: String,                    // definicao | definicao_global | definicao_simbolica |
                                         // avaliacao_numerica | avaliacao_simbolica
    val expr: No? = null,
    val faixa: Pair<List<No>, No>? = null,   // i := a[, a2] .. b
    val define: List<String> = emptyList(),
    val params: List<String> = emptyList(),
    val tipoAlvo: String? = null,        // variavel | funcao | elemento
    val indices: List<No> = emptyList(),
    val usa: List<String> = emptyList(),
    val unidadeExibicao: No? = null,
    val unidadeExibicaoTexto: String? = null,
    val mostrar: Boolean = false,        // definir e mostrar: R := q*L/2 = (o valor aparece na definição)
) {
    var estado: Estado? = null
}
