"use strict";
// Operadores de cálculo (ADR 0011): teclas Σ, Π, ∫ e d/dx na camada "Operadores".
// O somando de Σ vai até o primeiro + ou − de fora; mais termos, entre parênteses.
{
  TL.teclas.push(
    { latex: "\\sum_{#?=#?}^{#?}#?", label: "Σ", tooltip: "Somatório: Σ de i = a até b (sem limites, percorre a faixa i)" },
    { latex: "\\prod_{#?=#?}^{#?}#?", label: "Π", tooltip: "Produtório" },
    { latex: "\\int_{#?}^{#?}#?\\,\\mathrm{d}#?", label: "∫", tooltip: "Integral definida: ∫ de a até b ... dx" },
    { latex: "\\frac{\\mathrm{d}}{\\mathrm{d}#?}#?", label: "d/dx", tooltip: "Derivada: no valor de x definido acima, ou simbólica" },
  );
}
