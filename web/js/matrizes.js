"use strict";
// Vetores e matrizes (ADR 0010): teclas na camada "Operadores" e o primeiro índice (0 ou 1) nos ajustes.
{
  const MAT = (l, c) => "\\begin{pmatrix}" + Array.from({ length: l }, () => Array(c).fill("#0").join(" & ")).join("\\\\ ") + "\\end{pmatrix}";
  TL.teclas.push(
    { latex: MAT(2, 2), label: "2×2", tooltip: "Matriz 2×2" },
    { latex: MAT(3, 3), label: "3×3", tooltip: "Matriz 3×3" },
    { latex: MAT(3, 1), label: "vetor", tooltip: "Vetor de 3 elementos" },
    { label: "+ linha", tooltip: "Acrescentar linha à matriz", command: ["performWithFeedback", "addRowAfter"] },
    { label: "+ coluna", tooltip: "Acrescentar coluna à matriz", command: ["performWithFeedback", "addColumnAfter"] },
    { latex: "#@^{\\top}", label: "Mᵀ", tooltip: "Transposta" },
    { latex: "#@^{-1}", label: "M⁻¹", tooltip: "Inversa" },
    { latex: "\\left|#0\\right|", label: "|M|", tooltip: "Determinante, comprimento ou módulo" },
    { latex: "#@^{\\langle #?\\rangle}", label: "M⟨j⟩", tooltip: "Coluna j" },
    { latex: "\\overrightarrow{#@}", label: "vec", tooltip: "Elemento a elemento" },
    { latex: "\\times", label: "×", tooltip: "Produto vetorial" },
  );

  // primeiro índice de vetores e matrizes (o ORIGIN de outras ferramentas), salvo com a folha
  TL.opcoes.push(() => ({ origem: estado.origem === 1 ? 1 : 0 }));
  const campo = document.createElement("label");
  campo.className = "campo-ajuste";
  campo.textContent = "Primeiro índice de vetores";
  const sel = document.createElement("select");
  sel.id = "sel-origem";
  sel.innerHTML = '<option value="0">0 (v[0] é o primeiro)</option><option value="1">1 (v[1] é o primeiro)</option>';
  sel.addEventListener("change", () => { estado.origem = Number(sel.value); agendar(0); });
  campo.appendChild(sel);
  document.getElementById("btn-limpar").before(campo);
  TL.aposCalculo.push(() => { sel.value = String(estado.origem === 1 ? 1 : 0); });
}
