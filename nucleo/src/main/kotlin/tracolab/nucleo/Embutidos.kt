package tracolab.nucleo

import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.embutidos.MODULOS

/** Função embutida comum: recebe os argumentos já avaliados. */
fun interface Embutida {
    fun chamar(args: List<IExpr>, c: Contexto): IExpr
}

/**
 * Operador especial: recebe a chamada sem avaliar os argumentos (Σ, ∫, d/dx, resolver...).
 * [ligados] são as posições (a partir de 0) dos argumentos que são nomes ligados, como o `i` de
 * `sum(i^2, i, 1, n)`: não contam como nomes usados da folha ([usados]) e nunca ficam "faltando".
 */
class Especial(val ligados: Set<Int> = emptySet(), val fn: (n: No.Chama, c: Contexto) -> IExpr)

/** Uma área de embutidos: um arquivo em `embutidos/`, listado em [MODULOS]. */
interface ModuloEmbutidos {
    fun registrar(r: Embutidos)
}

/**
 * Registro das funções embutidas. O avaliador só consulta este registro; cada área (elementares,
 * matrizes, cálculo...) registra as suas num arquivo próprio. Uma função do usuário com o mesmo
 * nome tem prioridade: redefinir um embutido é permitido, como redefinir uma variável.
 */
object Embutidos {
    val funcoes = HashMap<String, Embutida>()
    val especiais = HashMap<String, Especial>()

    fun funcao(nome: String, fn: Embutida) { livre(nome); funcoes[nome] = fn }
    fun especial(nome: String, e: Especial) { livre(nome); especiais[nome] = e }
    fun existe(nome: String) = nome in funcoes || nome in especiais

    // Dois módulos registrando o mesmo nome é erro de integração, não escolha silenciosa.
    private fun livre(nome: String) = check(!existe(nome)) { "embutido registrado duas vezes: $nome" }

    init { MODULOS.forEach { it.registrar(this) } }
}

/** Confere a quantidade de argumentos de um embutido. */
fun aridade(nome: String, args: List<*>, vararg aceitas: Int) {
    if (args.size !in aceitas) {
        val esp = if (aceitas.size == 1) "${aceitas[0]}" else aceitas.joinToString(" ou ")
        throw ErroCalculo("$nome: espera $esp argumento${if (aceitas.singleOrNull() == 1) "" else "s"}")
    }
}
