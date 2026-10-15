package tracolab.nucleo.latex

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IAST
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.ErroUnidade
import tracolab.nucleo.Formato
import tracolab.nucleo.No
import tracolab.nucleo.Planilha
import tracolab.nucleo.Regiao
import tracolab.nucleo.UnidadeTela
import tracolab.nucleo.UnidadesFolha
import tracolab.nucleo.avaliar
import tracolab.nucleo.Contexto
import tracolab.nucleo.formatarNumero
import tracolab.nucleo.temperaturaAbsoluta
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs

/**
 * Formato dos números da folha e de cada região, para o cálculo em andamento (ADR 0014). A API
 * preenche a partir de `opcoes.formato` e `opcoes.formatos` do pedido; sem nada, vale o padrão.
 */
class ConfigTela(val folha: Formato = Formato(), val regioes: Map<String, Formato> = emptyMap())

private val configs: MutableMap<Planilha, ConfigTela> = Collections.synchronizedMap(WeakHashMap())

var Planilha.configTela: ConfigTela
    get() = configs[this] ?: ConfigTela()
    set(v) { configs[this] = v }

/** Formato de uma região: o dela, se houver, senão o da folha. */
fun Planilha.formatoTela(id: String): Formato = configTela.let { it.regioes[id] ?: it.folha }

/** Texto simples "número unidade" na tela (pt-BR: 20 mm, 36 kN·m, 2,3 m), para testes e relatórios. */
fun Planilha.exibirFolha(id: String, f: Formato = formatoTela(id)): String {
    val r = regioes.first { it.id == id }
    val v = r.estado?.valor ?: return "?"
    val (si, d) = UnidadesFolha.siDe(v, motor)
    val u = unidadeTela(r, d, abs(si), f)
    val num = formatarNumero(UnidadesFolha.valorEm(si, u, motor), f).texto(f)
    return if (u == null) num else "$num ${u.texto()}"
}

/**
 * Unidade em que o valor da região aparece: a pedida pelo chip, senão a regra das unidades da
 * folha ([UnidadesFolha.escolher]). null = sem unidade (adimensional).
 */
internal fun Planilha.unidadeTela(r: Regiao, d: DoubleArray, grandeza: Double, f: Formato): UnidadeTela? {
    val pedida = r.unidadeExibicao
    if (pedida != null) {
        val fs = (pedida as? No.Unidade)?.let { UnidadesFolha.fatoresDe(it.expr) }
        val u = fs?.let { UnidadesFolha.unidade(it, motor, temperaturaAbsoluta(pedida)) } ?: run {
            // unidade que não se decompõe em nomes da tabela: vale o valor dela, com o texto como foi escrito
            val alvo = avaliar(pedida, Contexto(emptyMap(), this))
            val (si, dd) = UnidadesFolha.siDe(alvo, motor)
            UnidadeTela(listOf((r.unidadeExibicaoTexto ?: "?") to 1), si, dd)
        }
        if (grandeza != 0.0 && !u.dims.contentEquals(d)) throw ErroUnidade("A unidade pedida não é compatível com o resultado.")
        return u
    }
    val nome = UnidadesFolha.nomeMostrado(r)
    val digitadas = if (nome != null && f.unidades == "folha") UnidadesFolha.digitadas(this, r, nome) else emptyList()
    return UnidadesFolha.escolher(d, grandeza, this, digitadas, f.unidades)
}

/** Número e unidade de um valor escalar numérico, em LaTeX ("36\,\mathrm{kN}\cdot\mathrm{m}"). */
internal fun Planilha.escalarTela(v: IExpr, r: Regiao, f: Formato): String {
    val (si, d) = UnidadesFolha.siDe(v, motor)
    val u = unidadeTela(r, d, abs(si), f)
    val num = formatarNumero(UnidadesFolha.valorEm(si, u, motor), f).latex(f)
    return if (u == null) num else num + "\\," + u.latex()
}

/**
 * Vetor/matriz de números: a mesma unidade para todos os elementos (escolhida pelo maior), ou a de
 * cada um quando as dimensões diferem. Devolve as células e a unidade comum (null se não há).
 */
internal fun Planilha.celulasTela(ls: List<List<IExpr>>, r: Regiao, f: Formato): Pair<List<List<String>>, String?> {
    val sis = ls.map { l -> l.map { UnidadesFolha.siDe(it, motor) } }
    val dims = sis.flatten().filter { it.first != 0.0 }.map { it.second.toList() }.toSet()
    if (r.unidadeExibicao != null || dims.size <= 1) {
        val d = dims.firstOrNull()?.toDoubleArray() ?: sis.flatten().first().second
        val maior = sis.flatten().maxOf { abs(it.first) }
        val u = unidadeTela(r, d, maior, f)
        val cel = sis.map { l -> l.map { (si, _) -> formatarNumero(UnidadesFolha.valorEm(si, u, motor), f).latex(f) } }
        return cel to u?.latex()
    }
    return sis.map { l -> l.map { (si, d) ->
        val u = unidadeTela(r, d, abs(si), f)
        val num = formatarNumero(UnidadesFolha.valorEm(si, u, motor), f).latex(f)
        if (u == null) num else "$num~${u.latex()}"
    } } to null
}

/**
 * Resultado pendente com unidades nas unidades da folha. Cada termo é coeficiente · nomes que
 * faltam · unidade; os números saem em decimal (0,000012·L, e não 3/250000·L). Quando a unidade
 * do termo só tem expoentes negativos, o termo sai como divisão: F/(314,159 mm²) em vez de
 * 3183,1·F/m², que é como se lê σ = F/A.
 */
internal fun Planilha.pendenteTela(v: IExpr, f: Formato): String {
    val vn = motor.ev(F.N(v))
    val termos = if (vn.isPlus) (1 until (vn as IAST).size()).map { vn.get(it) } else listOf(vn)
    val partes = termos.map { termoTela(it, f) }
    return partes.foldIndexed("") { i, acc, t ->
        if (i == 0) t else if (t.startsWith("-")) "$acc$t" else "$acc+$t"
    }
}

private fun Planilha.termoTela(t: IExpr, f: Formato): String {
    if (!motor.temUnidade(t)) return numerosDecimais(t, f)
    val fatores = if (t.isTimes) (1 until (t as IAST).size()).map { t.get(it) } else listOf(t)
    var coef = 1.0
    val unid = mutableListOf<IExpr>(); val outros = mutableListOf<IExpr>()
    for (x in fatores) when {
        x.isNumber -> coef *= motor.dbl(x)
        motor.temUnidade(x) && !motor.temLivres(x) -> unid += x
        else -> outros += x
    }
    if (outros.any { motor.temUnidade(it) }) return numerosDecimais(t, f)   // unidade dentro de função: como vier
    val (siU, d) = UnidadesFolha.siDe(motor.ev(F.Times(*unid.toTypedArray())), motor)
    coef *= siU
    val resto = if (outros.isEmpty()) null else motor.ev(F.Times(*outros.toTypedArray()))
    val restoTex = resto?.let { x -> numerosDecimais(x, f).let { if (x.isPlus) "\\left($it\\right)" else it } }
    val negativo = coef < 0
    val sinal = if (negativo) "-" else ""
    val mag = abs(coef)
    // unidade só no denominador e com nomes: F/(314,159 mm²)
    if (restoTex != null && d.all { it <= 0.0 } && d.any { it < 0.0 }) {
        val den = d.map { -it + 0.0 }.toDoubleArray()
        val u = UnidadesFolha.escolher(den, 1 / mag, this, modo = f.unidades)
        if (u != null) {
            val n = formatarNumero(UnidadesFolha.valorEm(1 / mag, u, motor), f).latex(f)
            return "$sinal\\frac{$restoTex}{$n\\,${u.latex()}}"
        }
    }
    val u = UnidadesFolha.escolher(d, mag, this, modo = f.unidades)
    val x = if (u == null) mag else mag / u.si
    val num = formatarNumero(x, f).latex(f)
    val corpo = when {
        restoTex == null -> num
        num == "1" -> restoTex
        else -> "$num\\cdot $restoTex"
    }
    return sinal + corpo + (u?.let { "\\," + it.latex() } ?: "")
}

/** LaTeX de uma expressão sem unidade com os números já em decimal e no formato da folha. */
private fun Planilha.numerosDecimais(x: IExpr, f: Formato): String {
    val tex = LatexSaida.expressao(motor.ev(F.N(x)), motor)
    // o TeXForm escreve os decimais com todos os algarismos: reescreve no formato
    return Regex("(?<![\\w}])(\\d+)\\{,\\}(\\d+)").replace(tex) { m ->
        formatarNumero("${m.groupValues[1]}.${m.groupValues[2]}".toDouble(), f).latex(f)
    }
}
