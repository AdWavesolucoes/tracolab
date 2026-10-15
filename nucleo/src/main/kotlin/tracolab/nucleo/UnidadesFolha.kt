package tracolab.nucleo

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.log10

/**
 * Unidade de exibição: fatores (nome da tabela, expoente) na ordem em que se escrevem, e quanto
 * ela vale no SI. Em °C/°F sozinhas, [absoluta] diz a escala (o zero é descontado na exibição).
 */
class UnidadeTela(val fatores: List<Pair<String, Int>>, val si: Double, val dims: DoubleArray,
                  val absoluta: TabelaUnidades.Absoluta? = null) {

    /** kN·m, kN/m^2, kg/(m·s^2), 1/s */
    fun texto(): String = escrever({ it }, { n, p -> if (p == 1) n else "$n^$p" }, "·", "/", "(", ")")

    /** \mathrm{kN}\cdot\mathrm{m}, \mathrm{kN}/\mathrm{m}^{2} (sem `\,`: a tela separa número e unidade por ele) */
    fun latex(): String = escrever({ "\\mathrm{${it.replace("%", "\\%")}}" }, { n, p -> if (p == 1) n else "$n^{$p}" },
        "\\cdot ", "/", "\\left(", "\\right)")

    private fun escrever(nome: (String) -> String, pot: (String, Int) -> String, vezes: String, barra: String,
                         abre: String, fecha: String): String {
        val num = fatores.filter { it.second > 0 }.map { pot(nome(it.first), it.second) }
        val den = fatores.filter { it.second < 0 }.map { pot(nome(it.first), -it.second) }
        val n = if (num.isEmpty()) "1" else num.joinToString(vezes)
        if (den.isEmpty()) return n
        return n + barra + if (den.size == 1) den[0] else abre + den.joinToString(vezes) + fecha
    }

    override fun toString() = texto()
}

/**
 * Escolha da unidade em que um resultado aparece quando a região não pediu uma (ADR 0014,
 * "unidades da folha"). Por ordem:
 *
 * 1. **Como foi digitado**: `d =` (ou `d` no painel) usa a unidade escrita na definição de d,
 *    se tiver a mesma dimensão (`d := 20{mm}` aparece 20 mm, e não 0,02 m).
 * 2. Candidatas, com uma nota (menor é melhor) = distância do número ao intervalo [1, 1000) em
 *    potências de 10 (para uma potência pura, como mm⁴, o intervalo é [1, 1000ᵏ)) + o peso da origem:
 *    - 0,0: unidade escrita em alguma região da folha com a mesma dimensão ({kN/m}, {MPa} pedida);
 *    - 0,4: potência de uma unidade da folha (mm² a partir de mm); 0,5: produto de duas unidades da
 *      folha de dimensões diferentes (kN·m, kN/cm², tf/m², km/h);
 *    - 1,0: família de engenharia da dimensão (N/kN/MN, Pa/kPa/MPa/GPa, mm/m/km...). Momento e
 *      energia têm a mesma dimensão: com força e sem energia escritas na folha, a família é a do
 *      momento (kN·m), senão a da energia (kJ);
 *    - 2,0: SI com nome derivado ou unidades de base (m/s, kg/m^3).
 * 3. Empate: a unidade que aparece primeiro na folha, depois a de menos fatores.
 *
 * Unidades de frequência e adimensionais (Hz, %, °) não entram em produtos, e °C/°F sozinhas
 * (escala absoluta) só valem pela regra 1: uma diferença de temperaturas nunca sai em °C.
 */
object UnidadesFolha {

    /** Valor de cada unidade da tabela no SI (o mesmo em qualquer motor). */
    private val fatores = ConcurrentHashMap<String, Double>()

    fun fatorSi(nome: String, m: Motor): Double? {
        val u = TabelaUnidades.tabela[nome] ?: return null
        return fatores.getOrPut(nome) { m.dbl(m.avaliador.eval(u.fator)) }
    }

    fun dimsDe(nome: String): DoubleArray? = TabelaUnidades.tabela[nome]?.dims?.map { it.toDouble() }?.toDoubleArray()

    /** Unidade a partir dos fatores (nome, expoente); null se algum nome não é da tabela. */
    fun unidade(fs: List<Pair<String, Int>>, m: Motor, absoluta: TabelaUnidades.Absoluta? = null): UnidadeTela? {
        var si = 1.0
        val d = DoubleArray(7)
        for ((n, p) in fs) {
            si *= Math.pow(fatorSi(n, m) ?: return null, p.toDouble())
            dimsDe(n)!!.forEachIndexed { i, x -> d[i] += x * p }
        }
        return UnidadeTela(fs, si, d, absoluta)
    }

    /** Fatores de uma unidade escrita: {kN/m^2} → [(kN, 1), (m, -2)]; null se não dá (nome de fora, expoente não inteiro). */
    fun fatoresDe(n: No): List<Pair<String, Int>>? {
        val acc = LinkedHashMap<String, Int>()
        fun anda(x: No, p: Int): Boolean = when (x) {
            is No.Id -> { if (x.nome !in TabelaUnidades.tabela) false else { acc.merge(x.nome, p, Int::plus); true } }
            is No.Bin -> when (x.op) {
                '*' -> anda(x.a, p) && anda(x.b, p)
                '/' -> anda(x.a, p) && anda(x.b, -p)
                '^' -> inteiro(x.b)?.let { anda(x.a, p * it) } ?: false
                else -> false
            }
            is No.Num -> x.texto == "1"   // {1/s}
            else -> false
        }
        if (!anda(n, 1)) return null
        return acc.filter { it.value != 0 }.toList().ifEmpty { null }
    }

    private fun inteiro(n: No): Int? = when (n) {
        is No.Num -> n.texto.toIntOrNull()
        is No.Neg -> inteiro(n.a)?.let { -it }
        else -> null
    }

    // ------------------------------------------------------------------ vocabulário da folha

    /** Uma unidade escrita na folha (ou pedida pelo chip), na ordem de cálculo. */
    class Escrita(val unidade: UnidadeTela, val ordem: Int)

    class Vocabulario(val escritas: List<Escrita>, val atomos: List<Pair<String, Int>>) {
        val temForca = atomos.any { (n, _) -> dimsDe(n)!!.contentEquals(FORCA) } || escritas.any { it.unidade.dims.contentEquals(FORCA) }
        val temEnergia = escritas.any { it.unidade.dims.contentEquals(ENERGIA) && it.unidade.fatores.size == 1 }
    }

    private val cache = WeakHashMap<Planilha, Pair<Int, Vocabulario>>()

    /** Unidades escritas nas regiões da folha (valores e unidades pedidas), na ordem de cálculo. */
    fun vocabulario(pl: Planilha): Vocabulario {
        val chave = pl.regioes.fold(17) { h, r -> 31 * h + System.identityHashCode(r) }
        synchronized(cache) { cache[pl]?.let { (k, v) -> if (k == chave) return v } }
        val escritas = mutableListOf<Escrita>()
        val atomos = LinkedHashMap<String, Int>()
        var ordem = 0
        fun registra(u: No, sozinha: Boolean) {
            val fs = fatoresDe(u) ?: return
            // °C/°F sozinhas são escala absoluta: não servem para exibir outros resultados
            if (sozinha && u is No.Id && u.nome in TabelaUnidades.absolutas) return
            val un = unidade(fs, pl.motor) ?: return
            escritas += Escrita(un, ordem++)
            for ((n, _) in fs) atomos.putIfAbsent(n, ordem)
        }
        fun anda(n: No?) {
            when (n) {
                null, is No.Num, is No.Id, is No.Texto -> {}
                is No.Unidade -> registra(n.expr, true)
                is No.Bin -> { anda(n.a); anda(n.b) }
                is No.Neg -> anda(n.a)
                is No.Cmp -> { anda(n.a); anda(n.b) }
                is No.Chama -> n.args.forEach { anda(it) }
                is No.Indice -> { anda(n.base); n.idx.forEach { anda(it) } }
                is No.Matriz -> n.linhas.forEach { l -> l.forEach { anda(it) } }
            }
        }
        for (r in pl.ordenadas()) {
            anda(r.expr)
            r.faixa?.let { (ini, fim) -> ini.forEach { anda(it) }; anda(fim) }
            r.indices.forEach { anda(it) }
            r.unidadeExibicao?.let { anda(it) }
        }
        val v = Vocabulario(escritas, atomos.toList())
        synchronized(cache) { cache[pl] = chave to v }
        return v
    }

    // ------------------------------------------------------------------ famílias de engenharia

    private val FORCA = doubleArrayOf(1.0, 1.0, -2.0, 0.0, 0.0, 0.0, 0.0)
    private val ENERGIA = doubleArrayOf(2.0, 1.0, -2.0, 0.0, 0.0, 0.0, 0.0)
    private val TEMPERATURA = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0)

    private fun f(vararg ns: String): List<List<Pair<String, Int>>> = ns.map { n ->
        n.split("·").flatMap { parte ->
            val (num, den) = parte.split("/").let { it[0] to it.getOrNull(1) }
            listOfNotNull(pot(num, 1), den?.let { pot(it, -1) })
        }
    }

    private fun pot(t: String, s: Int): Pair<String, Int> {
        val i = t.indexOf('^')
        return if (i < 0) t to s else t.substring(0, i) to s * t.substring(i + 1).toInt()
    }

    /** Famílias por dimensão; a escolha dentro da família é pela grandeza do número. */
    private val FAMILIAS: List<List<List<Pair<String, Int>>>> = listOf(
        f("mm", "m", "km"), f("mm^2", "m^2"), f("mm^3", "m^3"), f("mm^4", "m^4"),
        f("g", "kg", "t"), f("ms", "s"), f("N", "kN", "MN"), f("Pa", "kPa", "MPa", "GPa"),
        f("W", "kW", "MW"), f("Hz", "kHz", "MHz"), f("mV", "V", "kV"), f("mA", "A", "kA"),
        f("Ω", "kΩ", "MΩ"), f("pF", "nF", "μF", "F"), f("mH", "H"), f("C"), f("K"),
        f("m/s"), f("m/s^2"), f("kg/m^3"), f("m^3/s"), f("N/m", "kN/m"), f("N/m^3", "kN/m^3"),
    )
    private val ENERGIAS = f("J", "kJ", "MJ")
    private val MOMENTOS = f("N·m", "kN·m", "MN·m")

    private val DERIVADAS = listOf("N", "Pa", "J", "W", "C", "V", "Ω", "Hz", "F", "H", "T", "Wb", "S")
    private val BASES = listOf("m", "kg", "s", "A", "K", "mol", "cd")

    /** SI coerente: nome derivado (N, Pa...) ou unidades de base com barra (m/s, kg/m^3). */
    fun si(d: DoubleArray, m: Motor): UnidadeTela? {
        DERIVADAS.firstOrNull { dimsDe(it)!!.contentEquals(d) }?.let { return unidade(listOf(it to 1), m) }
        if (d.any { it != Math.rint(it) }) return null
        return unidade(d.indices.filter { d[it] != 0.0 }.map { BASES[it] to d[it].toInt() }, m)
    }

    // ------------------------------------------------------------------ escolha

    private class Candidata(val u: UnidadeTela, val peso: Double, val ordem: Int, val potencia: Int)

    /**
     * Unidade para mostrar um valor de dimensão [d] cujo módulo no SI é [grandeza]. [digitadas] são
     * as unidades da regra 1 (a definição do nome mostrado); null quando o SI fica (adimensional).
     */
    fun escolher(d: DoubleArray, grandeza: Double, pl: Planilha, digitadas: List<UnidadeTela> = emptyList(),
                 modo: String = "folha"): UnidadeTela? {
        if (d.all { it == 0.0 }) return null
        val m = pl.motor
        if (modo == "si") return si(d, m)
        digitadas.filter { it.dims.contentEquals(d) }.minByOrNull { distancia(grandeza / it.si, potenciaPura(it)) }?.let { return it }

        // temperatura: uma diferença e uma temperatura absoluta têm a mesma dimensão; fora da
        // regra 1, sai em K, que não engana (°C e Δ°C só quando pedidas ou digitadas)
        if (d.contentEquals(TEMPERATURA)) return unidade(listOf("K" to 1), m)

        val voc = vocabulario(pl)
        val cands = mutableListOf<Candidata>()
        for (e in voc.escritas) if (e.unidade.dims.contentEquals(d)) cands += Candidata(e.unidade, 0.0, e.ordem, potenciaPura(e.unidade))
        val atomos = voc.atomos.filter { (n, _) -> combinavel(n) }
        for ((n, ord) in atomos) for (p in -4..4) {
            if (p == 0) continue
            val u = unidade(listOf(n to p), m) ?: continue
            if (u.dims.contentEquals(d)) cands += Candidata(u, 0.4, ord, abs(p))
        }
        for (i in atomos.indices) for (j in atomos.indices) {
            if (i == j) continue
            val (a, oa) = atomos[i]; val (b, ob) = atomos[j]
            if (dimsDe(a)!!.contentEquals(dimsDe(b)!!)) continue
            for (pa in 1..3) for (pb in -3..3) {
                if (pb == 0 || (pb > 0 && j < i)) continue   // sem repetir o mesmo produto
                val fs = listOf(a to pa, b to pb).sortedBy { (n, p) -> if (p < 0) 1 else if (dimsDe(n)!![1] != 0.0) -1 else 0 }
                val u = unidade(fs, m) ?: continue
                if (u.dims.contentEquals(d)) cands += Candidata(u, 0.5, maxOf(oa, ob), 1)
            }
        }
        val familias = FAMILIAS + listOf(if (voc.temForca && !voc.temEnergia) MOMENTOS else ENERGIAS)
        for (fam in familias) for (fs in fam) {
            val u = unidade(fs, m) ?: continue
            if (u.dims.contentEquals(d)) cands += Candidata(u, 1.0, Int.MAX_VALUE - 1, potenciaPura(u))
        }
        si(d, m)?.let { cands += Candidata(it, 2.0, Int.MAX_VALUE, 1) }
        return cands.minWithOrNull(compareBy<Candidata>({ distancia(grandeza / it.u.si, it.potencia) + it.peso }, { it.ordem }, { it.u.fatores.size }))?.u
    }

    /** Hz, rpm, %, ° e °C/°F sozinhas não entram em produtos de unidades. */
    private fun combinavel(n: String): Boolean {
        if (n in TabelaUnidades.absolutas) return false
        val d = dimsDe(n) ?: return false
        if (d.all { it == 0.0 }) return false
        if (d.contentEquals(doubleArrayOf(0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0))) return false
        return true
    }

    /** Expoente de uma potência pura de comprimento (mm⁴ → 4), para o intervalo [1, 1000ᵏ). */
    private fun potenciaPura(u: UnidadeTela): Int =
        if (u.fatores.size == 1 && dimsDe(u.fatores[0].first)!!.contentEquals(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
            abs(u.fatores[0].second) else 1

    /** Distância (em potências de 10) de |x| ao intervalo [1, 1000ᵏ). */
    private fun distancia(x: Double, k: Int): Double {
        if (x == 0.0 || !x.isFinite()) return 0.0
        val l = log10(abs(x))
        return when { l < 0 -> -l; l >= 3.0 * k -> l - 3.0 * k + 1e-9; else -> 0.0 }
    }

    // ------------------------------------------------------------------ regra 1: como foi digitado

    /**
     * Unidades da regra 1: a definição de [nome] vigente na posição de [r] (ou a própria [r]) é uma
     * quantidade digitada, sem nomes (`20{mm}`, `3{m}/2{s}`, `2{m} + 30{cm}`, `20{°C}`)? Então
     * as unidades em que ela foi escrita (m/s; m ou cm, a decidir pela grandeza). Com nomes
     * (`T + 15{Δ°C}`, `π·d²/4`), nenhuma: vale a regra da folha.
     */
    fun digitadas(pl: Planilha, r: Regiao, nome: String): List<UnidadeTela> {
        val ordem = pl.ordenadas()
        val pos = ordem.indexOfFirst { it.id == r.id }
        val def = if (nome in r.define) r else (pos - 1 downTo 0).map { ordem[it] }.firstOrNull { nome in it.define } ?: return emptyList()
        if (def.tipoAlvo != "variavel" || def.faixa != null) return emptyList()
        val opcoes = def.expr?.let { quantidade(it) } ?: return emptyList()
        return opcoes.mapNotNull { (fs, abs) -> if (fs.isEmpty()) null else unidade(fs, pl.motor, abs) }
    }

    private class Opcao(val fs: List<Pair<String, Int>>, val abs: TabelaUnidades.Absoluta? = null) {
        operator fun component1() = fs
        operator fun component2() = abs
    }

    /** Unidades possíveis de uma quantidade digitada (só números e unidades); null se tem nomes. */
    private fun quantidade(n: No): List<Opcao>? = when (n) {
        is No.Num -> listOf(Opcao(emptyList()))
        is No.Unidade -> {
            val abs = (n.expr as? No.Id)?.let { TabelaUnidades.absolutas[it.nome] }
            fatoresDe(n.expr)?.let { listOf(Opcao(it, abs)) }
        }
        is No.Neg -> quantidade(n.a)
        is No.Bin -> {
            val a = quantidade(n.a); val b = if (n.op == '^') null else quantidade(n.b)
            when {
                a == null -> null
                n.op == '+' || n.op == '-' -> b?.let { (a + it).filter { o -> o.fs.isNotEmpty() }.ifEmpty { a } }
                n.op == '*' || n.op == '/' -> b?.let { bs ->
                    a.flatMap { x -> bs.map { y ->
                        val acc = LinkedHashMap<String, Int>()
                        x.fs.forEach { (k, p) -> acc.merge(k, p, Int::plus) }
                        y.fs.forEach { (k, p) -> acc.merge(k, if (n.op == '/') -p else p, Int::plus) }
                        Opcao(acc.filter { it.value != 0 }.toList(), if (y.fs.isEmpty()) x.abs else if (x.fs.isEmpty()) y.abs else null)
                    } }
                }
                n.op == '^' -> inteiro(n.b)?.let { k -> a.map { o -> Opcao(o.fs.map { (x, p) -> x to p * k }) } }
                else -> null
            }
        }
        else -> null
    }

    /** Nome mostrado por uma região: `d =` mostra d; `d := ...` (painel) mostra d. */
    fun nomeMostrado(r: Regiao): String? = when {
        r.tipo.startsWith("definicao") && r.tipoAlvo == "variavel" -> r.define.singleOrNull()
        r.tipo == "avaliacao_numerica" && r.expr is No.Id -> (r.expr as No.Id).nome
        else -> null
    }

    /** Valor numérico de [v] (SI) na unidade [u], descontando o zero de uma escala absoluta. */
    fun valorEm(v: Double, u: UnidadeTela?, m: Motor): Double {
        if (u == null) return v
        val x = v / u.si
        val a = u.absoluta ?: return x
        return x - m.dbl(m.avaliador.eval(a.zero))
    }

    /** Valor no SI (número) e dimensão de um valor numérico com unidade. */
    fun siDe(v: IExpr, m: Motor): Pair<Double, DoubleArray> {
        if (!m.temUnidade(v)) return m.dbl(v) to DoubleArray(7)
        val d = m.dims(v)
        return m.dbl(m.ev(F.PowerExpand(F.Divide(v, m.monomio(d))))) to d
    }
}
