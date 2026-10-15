package tracolab.nucleo

// ------------------------------------------------------------------ texto -> região

/**
 * Nomes que uma expressão usa da folha. [ligados] não contam (parâmetros de função, e os nomes
 * ligados de operadores especiais como o `i` de `sum(i^2, i, 1, n)`: ver [Especial]).
 */
fun usados(n: No?, ligados: Set<String> = emptySet()): List<String> {
    val out = linkedSetOf<String>()
    fun anda(x: No?, lig: Set<String>) {
        when (x) {
            null, is No.Num, is No.Unidade, is No.Texto -> {}
            is No.Id -> if (x.nome !in lig) out += x.nome
            is No.Bin -> { anda(x.a, lig); anda(x.b, lig) }
            is No.Neg -> anda(x.a, lig)
            is No.Cmp -> { anda(x.a, lig); anda(x.b, lig) }
            is No.Chama -> {
                if (x.nome !in lig) out += x.nome
                val esp = Embutidos.especiais[x.nome]
                if (esp == null) x.args.forEach { anda(it, lig) }
                else {
                    // nome ligado: `x`, ou um vetor de nomes `[x; y]` (sistemas do resolver)
                    val novos = esp.ligados.flatMap { when (val a = x.args.getOrNull(it)) {
                        is No.Id -> listOf(a.nome)
                        is No.Matriz -> a.linhas.flatten().mapNotNull { (it as? No.Id)?.nome }
                        else -> emptyList()
                    } }.toSet()
                    if (esp.usaLigado(x.args.size)) out += novos.filter { it !in lig }
                    x.args.forEachIndexed { i, a -> if (i !in esp.ligados) anda(a, lig + novos) }
                }
            }
            is No.Indice -> { anda(x.base, lig); x.idx.forEach { anda(it, lig) } }
            is No.Matriz -> x.linhas.forEach { l -> l.forEach { anda(it, lig) } }
        }
    }
    anda(n, ligados); return out.toList()
}

/** `expr =` ou `expr = {unidade}` no fim do texto: avaliação (grupo 1: a expressão; grupo 2: a unidade pedida). */
private val AVALIACAO = Regex("^(.*?)=\\s*(\\{[^}]*\\})?\\s*$")

fun regiaoDeTexto(id: String, texto: String, y: Double, x: Double = 0.0): Regiao {
    val s = texto.trim()
    for ((sim, tipo0) in listOf(":=" to "definicao", "≡" to "definicao_global")) {
        val i = s.indexOf(sim)
        if (i < 0) continue
        val lhs = s.substring(0, i).trim()
        var rhs = s.substring(i + sim.length).trim()
        // definir e mostrar: R := q*L/2 = {kN} define R e mostra o valor na mesma região.
        // O '=' colado num operador (>=, <=, ==, !=) não conta; o de "expr -> =" conta.
        val aval = AVALIACAO.find(rhs)?.takeIf { it.groupValues[1].lastOrNull() !in setOf('<', '>', '=', '!') }
        val un = aval?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
        if (aval != null) rhs = aval.groupValues[1].trim()
        val mostrar = aval != null
        val unidade = un?.let { Parser.parse(it) }
        val unidadeTexto = un?.removeSurrounding("{", "}")
        val simb = rhs.endsWith("->") || rhs.endsWith("→")
        if (simb) rhs = rhs.removeSuffix("->").removeSuffix("→").trim()
        val alvo = Parser.parse(lhs)
        if (".." in rhs) {
            val (esq, fim) = rhs.split("..", limit = 2)
            val ini = esq.split(",").map { Parser.parse(it.trim()) }
            return Regiao(id, y, x, tipo0, faixa = ini to Parser.parse(fim.trim()), define = listOf((alvo as No.Id).nome),
                tipoAlvo = "variavel", usa = ini.flatMap { usados(it) } + usados(Parser.parse(fim.trim())),
                unidadeExibicao = unidade, unidadeExibicaoTexto = unidadeTexto, mostrar = mostrar)
        }
        val corpo = Parser.parse(rhs)
        return when (alvo) {
            is No.Chama -> {
                val params = alvo.args.map { (it as No.Id).nome }
                Regiao(id, y, x, tipo0, corpo, define = listOf(alvo.nome), params = params, tipoAlvo = "funcao",
                    usa = usados(corpo, params.toSet()), mostrar = mostrar)
            }
            is No.Indice -> Regiao(id, y, x, tipo0, corpo, define = listOf((alvo.base as No.Id).nome), tipoAlvo = "elemento",
                indices = alvo.idx, usa = (alvo.idx.flatMap { usados(it) } + usados(corpo)).distinct(),
                unidadeExibicao = unidade, unidadeExibicaoTexto = unidadeTexto, mostrar = mostrar)
            is No.Id -> Regiao(id, y, x, if (simb && tipo0 == "definicao") "definicao_simbolica" else tipo0, corpo,
                define = listOf(alvo.nome), tipoAlvo = "variavel", usa = usados(corpo),
                unidadeExibicao = unidade, unidadeExibicaoTexto = unidadeTexto, mostrar = mostrar)
            else -> throw ErroSintaxe("lado esquerdo inválido: $lhs")
        }
    }
    if (s.endsWith("->") || s.endsWith("→")) {
        val e = Parser.parse(s.removeSuffix("->").removeSuffix("→").trim())
        return Regiao(id, y, x, "avaliacao_simbolica", e, usa = usados(e))
    }
    val m = AVALIACAO.find(s) ?: throw ErroSintaxe("não entendi: $texto")
    val e = Parser.parse(m.groupValues[1].trim())
    val un = m.groupValues[2].takeIf { it.isNotEmpty() }
    return Regiao(id, y, x, "avaliacao_numerica", e, usa = usados(e),
        unidadeExibicao = un?.let { Parser.parse(it) }, unidadeExibicaoTexto = un?.removeSurrounding("{", "}"))
}
