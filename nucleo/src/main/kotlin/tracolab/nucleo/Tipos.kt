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
        val a = m.dbl(inicio); val d = m.dbl(passo); val b = m.dbl(fim)
        if (d == 0.0 || (b - a) / d < 0 || !((b - a) / d).isFinite()) throw ErroCalculo("Faixa inválida")
        val n = (b - a) / d + 1e-9 + 1
        if (n > maximo) throw ErroCalculo("Faixa grande demais: ${"%.0f".format(n)} valores (máximo $maximo).")
        return (0 until n.toInt()).map { k -> if (k % 1024 == 0) m.verificarPrazo(); m.ev(F.Plus(inicio, F.Times(F.ZZ(k.toLong()), passo))) }
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
) {
    var estado: Estado? = null
}
