"use strict";
// Verificações de engenharia e lógica (ADR 0013): uma comparação avaliada (σ ≤ σ_adm =) mostra os
// dois lados na unidade da folha e um selo "atende"/"não atende" com a utilização, em vez de 1 ou 0.
// Também as teclas de lógica, por partes e texto na camada "Operadores".
{
  const pct = (u) => (u * 100).toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + "%";
  const ICONE_OK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
  const ICONE_FALHA = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18"/></svg>';

  TL.pintores.push((o, el) => {
    let selo = el.querySelector(".verif");
    const v = o.verificacao;
    if (!v || (o.tipo !== "numerico" && o.tipo !== "parcial")) { selo?.remove(); return; }
    if (!selo) {
      selo = document.createElement("span");
      selo.className = "verif";
      selo.setAttribute("role", "status");
      el.querySelector(".linha").appendChild(selo);
    }
    if (v.ok === null || v.ok === undefined) {   // pendente: a relação e os chips "falta" já estão na região
      el.classList.add("verif-pendente");
      selo.innerHTML = '<span class="selo pend">a verificar</span>';
      selo.setAttribute("aria-label", "Verificação pendente");
      return;
    }
    el.classList.add(v.ok ? "verif-ok" : "verif-falha");
    const res = el.querySelector(".resultado");
    res.innerHTML = v.lados ? markup(v.lados) : "";   // 150 MPa ≤ 250 MPa, no lugar do 1 ou 0
    el.querySelector(".unidade").hidden = true;
    const u = typeof v.utilizacao === "number" ? v.utilizacao : null;
    selo.innerHTML = `<span class="selo ${v.ok ? "ok" : "falha"}">${v.ok ? ICONE_OK : ICONE_FALHA}${v.ok ? "atende" : "não atende"}</span>` +
      (u === null ? "" : `<span class="util${u > 1 ? " alta" : ""}" title="Utilização: quanto da capacidade foi usado">` +
        `<span class="barra-util" aria-hidden="true"><span style="width:${Math.min(100, Math.max(0, u * 100)).toFixed(1)}%"></span></span>${pct(u)}</span>`);
    selo.setAttribute("aria-label", (v.ok ? "Atende" : "Não atende") + (u === null ? "" : `, utilização de ${pct(u)}`));
  });

  TL.teclas.push(
    { latex: "\\operatorname{se}\\left(#?,#?,#?\\right)", label: "se", tooltip: "se(condição, valor se verdadeira, valor se falsa)" },
    { latex: "\\begin{cases}#? & #?\\\\ #? & \\text{senão}\\end{cases}", label: "{ por partes", tooltip: "Por partes: um valor por condição, e o senão" },
    { latex: "\\land", label: "∧", tooltip: "e" },
    { latex: "\\lor", label: "∨", tooltip: "ou" },
    { latex: "\\lnot", label: "¬", tooltip: "não" },
    { latex: "\\le", label: "≤", tooltip: "menor ou igual (verificação: σ ≤ σ_adm =)" },
    { latex: "\\ge", label: "≥", tooltip: "maior ou igual" },
    { latex: "\\ne", label: "≠", tooltip: "diferente" },
    { latex: ">", label: ">", tooltip: "maior" },
    { label: "texto", tooltip: "Escrever um texto (\"atende\") na fórmula", command: ["switchMode", "text"] },
    { label: "fórmula", tooltip: "Voltar a escrever fórmula depois de um texto", command: ["switchMode", "math"] },
  );
}
