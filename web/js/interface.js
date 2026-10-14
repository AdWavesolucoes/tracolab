"use strict";
// Faixa de teclas, teclado virtual, menu de unidade do resultado, barra, trilho e ajustes.

// ------------------------------------------------------------------ faixa de teclas e teclado

function campoAlvo() {
  if (ultimoCampo && ultimoCampo.isConnected) return ultimoCampo;
  const maisBaixa = estado.regioes.reduce((m, r) => Math.max(m, r.y), 0);
  const r = novaRegiao(40, maisBaixa ? maisBaixa + 72 : 48, "", false);
  return campoDe(r.id);
}

for (const tecla of document.querySelectorAll(".faixa .key")) {
  tecla.addEventListener("pointerdown", (ev) => ev.preventDefault());   // não tira o foco da fórmula
  tecla.addEventListener("click", () => {
    const campo = campoAlvo();
    campo.focus();
    if (tecla.dataset.insere) campo.insert(tecla.dataset.insere, { format: "latex", selectionMode: "placeholder", focus: true });
    if (tecla.dataset.camada) {
      window.mathVirtualKeyboard.show({ animate: true });
      window.mathVirtualKeyboard.currentLayer = tecla.dataset.camada;
    }
    if (tecla.id === "btn-teclado") {
      if (window.mathVirtualKeyboard.visible) window.mathVirtualKeyboard.hide();
      else window.mathVirtualKeyboard.show({ animate: true });
    }
  });
}

// O teclado virtual cobre a parte de baixo da tela: abre espaço na mesa e mantém a região em edição visível.
const mesa = document.querySelector(".mesa");
window.mathVirtualKeyboard.addEventListener("geometrychange", () => {
  const altura = window.mathVirtualKeyboard.visible ? window.mathVirtualKeyboard.boundingRect.height : 0;
  mesa.style.paddingBottom = Math.max(120, altura + 40) + "px";
  if (altura && ultimoCampo?.isConnected) ultimoCampo.closest(".rg").scrollIntoView({ block: "nearest", behavior: "smooth" });
});

// ------------------------------------------------------------------ unidade do resultado

function abrirMenuUnidade(r, botao) {
  window.mathVirtualKeyboard.hide();   // o teclado cobriria o menu, e escolher unidade não precisa dele
  menuUnidade.innerHTML = "";
  const auto = document.createElement("button");
  auto.textContent = "Automática (SI)";
  auto.className = r.unidade ? "" : "sel";
  auto.addEventListener("click", () => escolherUnidade(r, ""));
  menuUnidade.appendChild(auto);
  for (const [grupo, us] of GRUPOS_UNIDADES) {
    const g = document.createElement("div");
    g.className = "grupo";
    g.innerHTML = `<span class="lbl">${grupo}</span>`;
    for (const u of us) {
      const b = document.createElement("button");
      b.textContent = u.rot;
      if (r.unidade === u.latex) b.className = "sel";
      b.addEventListener("click", () => escolherUnidade(r, u.latex));
      g.appendChild(b);
    }
    menuUnidade.appendChild(g);
  }
  const c = botao.getBoundingClientRect();
  menuUnidade.style.left = Math.min(window.innerWidth - 332, c.left) + "px";
  menuUnidade.style.top = Math.min(window.innerHeight - 372, c.bottom + 6) + "px";
  menuUnidade.hidden = false;
}

function escolherUnidade(r, latex) {
  r.unidade = latex;
  menuUnidade.hidden = true;
  registrar();
  agendar(0);
}

document.addEventListener("pointerdown", (ev) => {
  if (!menuUnidade.hidden && !menuUnidade.contains(ev.target) && !ev.target.closest(".unidade")) menuUnidade.hidden = true;
});

// ------------------------------------------------------------------ barra, trilho e ajustes

nomeFolha.addEventListener("input", () => { estado.nome = nomeFolha.value; document.title = `${estado.nome} · TraçoLab`; salvar(); });

const btnOrdem = document.getElementById("btn-ordem");
btnOrdem.addEventListener("click", () => { estado.ordem = !estado.ordem; aplicarOrdem(); salvar(); });
function aplicarOrdem() {
  btnOrdem.setAttribute("aria-pressed", String(estado.ordem));
  pagina.classList.toggle("mostra-ordem", estado.ordem);
}

document.getElementById("btn-calcular").addEventListener("click", () => agendar(0));

const painel = document.getElementById("painel");
const ajustes = document.getElementById("ajustes");
const railVars = document.getElementById("rail-variaveis");
const railAjustes = document.getElementById("rail-ajustes");
railVars.addEventListener("click", () => {
  const abrir = painel.hidden;
  painel.hidden = !abrir; ajustes.hidden = true;
  railVars.classList.toggle("on", abrir); railVars.setAttribute("aria-pressed", String(abrir));
  railAjustes.classList.remove("on"); railAjustes.setAttribute("aria-expanded", "false");
});
railAjustes.addEventListener("click", () => {
  const abrir = ajustes.hidden;
  ajustes.hidden = !abrir; painel.hidden = true;
  railAjustes.classList.toggle("on", abrir); railAjustes.setAttribute("aria-expanded", String(abrir));
  railVars.classList.remove("on"); railVars.setAttribute("aria-pressed", "false");
});
selModo.addEventListener("change", () => { estado.modo = selModo.value; agendar(0); });
document.getElementById("btn-limpar").addEventListener("click", () => {
  if (!estado.regioes.length) return;
  estado.regioes = [];
  montar();
  registrar();
  agendar(0);
});
busca.addEventListener("input", () => pintarVariaveis());

