package tracolab.nucleo

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Como os números e as unidades aparecem na tela (ADR 0014). Vale para a folha (Ajustes) e pode
 * ser trocado por resultado. O padrão escreve como sempre ("{:.6g}": 6 algarismos significativos,
 * notação exponencial a partir de 10⁶ e abaixo de 10⁻⁴), mais o separador de milhar.
 *
 * @param algarismos algarismos significativos (quando [casas] é null)
 * @param casas casas decimais fixas
 * @param engenharia na notação exponencial, expoente múltiplo de 3 (12,5×10³)
 * @param expMax notação exponencial quando o expoente do número é >= expMax...
 * @param expMin ... ou <= expMin
 * @param milhar separa os milhares com espaço fino quando a parte inteira tem 5 algarismos ou mais
 * @param zeros mantém os zeros à direita (2,30 com 2 casas)
 * @param unidades "folha" (unidades usadas na folha, ADR 0014) ou "si" (SI com nomes derivados)
 */
data class Formato(
    val algarismos: Int = 6,
    val casas: Int? = null,
    val engenharia: Boolean = false,
    val expMax: Int = 6,
    val expMin: Int = -5,
    val milhar: Boolean = true,
    val zeros: Boolean = false,
    val unidades: String = "folha",
) {
    init {
        require(algarismos in 1..15) { "algarismos significativos entre 1 e 15" }
        require(casas == null || casas in 0..12) { "casas decimais entre 0 e 12" }
        require(unidades == "folha" || unidades == "si") { "unidades deve ser 'folha' ou 'si'" }
    }

    /** Limite simétrico da notação exponencial: 10^n e 10^-n (o ajuste da folha). */
    fun comLimite(n: Int) = copy(expMax = n, expMin = -n)

    /**
     * Este formato com os campos de [m] por cima (o que vem do pedido: `{"casas": 2}`).
     * `limite` ajusta [expMax] e [expMin] juntos; valores fora do lugar são ignorados.
     */
    fun com(m: Map<*, *>?): Formato {
        if (m == null) return this
        fun int(k: String) = (m[k] as? Number)?.toInt()
        fun bool(k: String) = m[k] as? Boolean
        var f = this
        int("algarismos")?.takeIf { it in 1..15 }?.let { f = f.copy(algarismos = it, casas = null) }
        if (m.containsKey("casas")) f = f.copy(casas = int("casas")?.takeIf { it in 0..12 })
        bool("engenharia")?.let { f = f.copy(engenharia = it) }
        int("limite")?.takeIf { it in 1..15 }?.let { f = f.comLimite(it) }
        bool("milhar")?.let { f = f.copy(milhar = it) }
        bool("zeros")?.let { f = f.copy(zeros = it) }
        (m["unidades"] as? String)?.takeIf { it == "folha" || it == "si" }?.let { f = f.copy(unidades = it) }
        return f
    }
}

/** Número pronto para escrever: sinal, parte inteira, parte decimal e expoente de 10 (ou null). */
class NumeroFormatado(val negativo: Boolean, val inteiro: String, val fracao: String, val expoente: Int?) {

    private fun grupos(sep: String, f: Formato) =
        if (f.milhar && inteiro.length >= 5) inteiro.reversed().chunked(3).map { it.reversed() }.reversed().joinToString(sep) else inteiro

    /** 12\thinspace345{,}6, 1{,}5\times10^{6}. O espaço fino não é `\,`: a tela separa número e unidade por `\,`. */
    fun latex(f: Formato): String {
        val corpo = grupos("\\thinspace ", f) + if (fracao.isEmpty()) "" else "{,}$fracao"
        return (if (negativo) "-" else "") + corpo + (expoente?.let { "\\times10^{$it}" } ?: "")
    }

    /** 12 345,6 e 1,5×10^6 (texto simples, para testes e relatórios). */
    fun texto(f: Formato): String {
        val corpo = grupos(" ", f) + if (fracao.isEmpty()) "" else ",$fracao"
        return (if (negativo) "-" else "") + corpo + (expoente?.let { "×10^$it" } ?: "")
    }
}

/** Arredonda e escreve [x] segundo [f]. Arredondamento comercial (meio para cima) sobre o decimal mais curto do número. */
fun formatarNumero(x: Double, f: Formato = Formato()): NumeroFormatado {
    if (x == 0.0 || !x.isFinite()) {
        if (!x.isFinite()) return NumeroFormatado(false, if (x.isNaN()) "NaN" else if (x > 0) "∞" else "-∞", "", null)
        return NumeroFormatado(false, "0", if (f.zeros && f.casas != null) "0".repeat(f.casas) else "", null)
    }
    val bd = BigDecimal(x.toString()).abs()
    fun expDe(b: BigDecimal) = b.precision() - b.scale() - 1
    fun partes(b: BigDecimal, exp: Int?): NumeroFormatado {
        val s = (if (f.zeros) b else b.stripTrailingZeros()).toPlainString()
        return NumeroFormatado(x < 0, s.substringBefore('.'), s.substringAfter('.', ""), exp)
    }
    fun exponencial(e: Int) = e >= f.expMax || e <= f.expMin
    // expoente da notação exponencial (de engenharia: múltiplo de 3)
    fun expoente(e: Int) = if (f.engenharia) Math.floorDiv(e, 3) * 3 else e

    if (f.casas == null) {
        var r = bd.round(MathContext(f.algarismos, RoundingMode.HALF_UP))
        val e = expDe(r)
        if (!exponencial(e)) return partes(r, null)
        val ex = expoente(e)
        r = r.movePointLeft(ex)
        return partes(r, ex)
    }
    val e0 = expDe(bd)
    val fixo = bd.setScale(f.casas, RoundingMode.HALF_UP)
    // casas fixas: número pequeno demais para as casas (0,0001 com 2 casas) vai para a notação exponencial
    if (!exponencial(e0) && fixo.signum() != 0) return partes(fixo, null)
    var ex = expoente(e0)
    var m = bd.movePointLeft(ex).setScale(f.casas, RoundingMode.HALF_UP)
    if (m.abs() >= BigDecimal.TEN.pow(if (f.engenharia) 3 else 1)) { ex += if (f.engenharia) 3 else 1; m = bd.movePointLeft(ex).setScale(f.casas, RoundingMode.HALF_UP) }
    return partes(m, ex)
}
