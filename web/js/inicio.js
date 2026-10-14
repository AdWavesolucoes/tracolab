"use strict";
// Carrega a folha salva (ou a de exemplo) e faz o primeiro cálculo. Sempre o último script.

carregar();
if (!estado.regioes.length) {
  // folha de exemplo: tensão numa barra tracionada, esperando a carga
  estado.nome = "Barra tracionada";
  const ex = [
    [`d${DEF}20\\,\\mathrm{mm}`, "", 40, 48], [`A${DEF}\\frac{\\pi d^2}{4}`, "", 40, 132],
    [`\\sigma${DEF}\\frac{F}{A}`, "", 40, 240], ["\\sigma=", "\\,\\mathrm{MPa}", 40, 372],
  ];
  for (const [latex, unidade, x, y] of ex) estado.regioes.push({ id: "r" + proximoId++, latex, unidade, x, y });
}
nomeFolha.value = estado.nome;
document.title = `${estado.nome} · TraçoLab`;
selModo.value = estado.modo;
aplicarOrdem();
montar();
registrar();
customElements.whenDefined("math-field").then(() => agendar(0, false));
