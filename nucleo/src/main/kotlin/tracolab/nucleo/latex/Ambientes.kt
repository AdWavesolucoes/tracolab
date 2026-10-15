package tracolab.nucleo.latex

import tracolab.nucleo.latex.LatexEntrada.ErroLatex
import tracolab.nucleo.latex.LatexEntrada.Peca

/**
 * Ambientes `\begin{...}` do editor de fórmulas, já separados em células convertidas para a
 * sintaxe do núcleo. Cada área acrescenta os seus aqui (ex.: `cases` para funções por partes).
 */
internal fun ambiente(nome: String, linhas: List<List<String>>): Peca = when (nome) {
    // matriz: [a, b; c, d] (linhas por ';', colunas por ','), sintaxe do núcleo (Arvore.kt)
    "pmatrix", "bmatrix", "matrix", "Bmatrix" -> {
        if (linhas.isEmpty() || linhas.any { l -> l.any { it.isBlank() } }) throw ErroLatex("matriz com célula vazia")
        if (linhas.any { it.size != linhas[0].size }) throw ErroLatex("linhas da matriz com tamanhos diferentes")
        Peca("[" + linhas.joinToString("; ") { it.joinToString(", ") } + "]", true, true)
    }
    // |A|: determinante escrito com barras
    "vmatrix" -> Peca("det(" + ambiente("pmatrix", linhas).t + ")", true, true)
    // por partes: { v₁ se c₁; v₂ se c₂; v₃ senão } → cases(v₁, c₁, v₂, c₂, v₃) (EntradaLogica.kt)
    "cases", "dcases" -> casos(linhas)
    else -> throw ErroLatex("ambiente não suportado: $nome")
}
