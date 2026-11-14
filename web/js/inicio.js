"use strict";
// Espera a sessão, monta a camada "Operadores" do teclado, abre a folha certa da conta e faz o
// primeiro cálculo. Sempre o último script.

// camada "Operadores" com as teclas registradas pelas áreas (TL.teclas), 7 por linha
if (TL.teclas.length) {
  const linhas = [];
  for (let i = 0; i < TL.teclas.length; i += 7) linhas.push(TL.teclas.slice(i, i + 7));
  window.mathVirtualKeyboard.layouts = [...window.mathVirtualKeyboard.layouts,
    { id: "operadores", label: "Operadores", tooltip: "Operadores e funções", rows: linhas }];
}

TL.sessao.then(async () => {
  await customElements.whenDefined("math-field");
  try { await TL.folhas.iniciar(); }
  catch (e) { if (e.status !== 401) resumo.textContent = e instanceof TypeError ? "sem conexão com o servidor" : e.message; }
}).catch(() => { /* sem sessão: sessao.js já mandou para conta.html */ });
