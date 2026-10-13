"use strict";
/*
 * TraçoLab: página de cálculo (ADR 0008), com a identidade do design (Tablet B).
 * Cada região é um editor MathLive posicionado na folha. A cada mudança, a folha inteira vai
 * para o núcleo (ponte nativa no Android ou POST /api/calcular no navegador) e a resposta
 * pinta o estado de cada região: numérico, pendente ("falta F"), erro de ordem, erro.
 */

const MF = window.MathfieldElement;
MF.fontsDirectory = "vendor/mathlive-0.110.0/fonts";
MF.soundsDirectory = null;
MF.decimalSeparator = ",";

// := em cobalto. É um macro (e não \textcolor direto) porque o MathLive faz o texto digitado
// depois de um trecho colorido herdar a cor; o macro desenha o símbolo em cobalto sem passar a cor adiante.
const DEF = "\\tldef";
const MACROS = { tldef: { args: 0, def: "\\textcolor{#2748C9}{\\coloneq}", captureSelection: true, expand: false } };

// Atalhos controlados: os padrões do MathLive trocam "in" por ∈ e quebrariam nomes como "Fin".
const ATALHOS = {
  ":=": DEF, "->": "\\to", ">=": "\\ge", "<=": "\\le", "!=": "\\ne",
  "sqrt": "\\sqrt{#?}", "pi": "\\pi", "sin": "\\sin", "cos": "\\cos", "tan": "\\tan",
  "ln": "\\ln", "log": "\\log", "exp": "\\exp",
  "alpha": "\\alpha", "beta": "\\beta", "gamma": "\\gamma", "delta": "\\delta", "epsilon": "\\varepsilon",
  "theta": "\\theta", "lambda": "\\lambda", "mu": "\\mu", "rho": "\\rho", "sigma": "\\sigma",
  "tau": "\\tau", "phi": "\\varphi", "omega": "\\omega", "Delta": "\\Delta", "Omega": "\\Omega",
};

// Unidades (ADR 0007): rótulo na tela → LaTeX (\mathrm é sempre unidade)
// "\," antes da unidade: espaço fino entre número e unidade (20 mm); o núcleo ignora o espaço.
const UN = (rot, latex) => ({ rot, latex: "\\," + (latex || `\\mathrm{${rot}}`) });
const GRUPOS_UNIDADES = [
  ["Comprimento", [UN("mm"), UN("cm"), UN("m"), UN("km"), UN("in"), UN("ft")]],
  ["Área e volume", [UN("mm²", "\\mathrm{mm}^2"), UN("cm²", "\\mathrm{cm}^2"), UN("m²", "\\mathrm{m}^2"), UN("m³", "\\mathrm{m}^3"), UN("L")]],
  ["Força", [UN("N"), UN("kN"), UN("MN"), UN("kgf"), UN("lbf"), UN("kip")]],
  ["Momento", [UN("N·m", "\\mathrm{N}\\cdot\\mathrm{m}"), UN("kN·m", "\\mathrm{kN}\\cdot\\mathrm{m}")]],
  ["Pressão", [UN("Pa"), UN("kPa"), UN("MPa"), UN("GPa"), UN("psi"), UN("ksi")]],
  ["Massa e tempo", [UN("kg"), UN("g"), UN("s"), UN("min"), UN("hr"), UN("Hz")]],
  ["Energia e elétrica", [UN("J"), UN("kJ"), UN("W"), UN("kW"), UN("V"), UN("A")]],
  ["Outras", [UN("K"), UN("rad"), UN("°", "\\mathrm{deg}"), UN("%", "\\mathrm{\\%}"), UN("rpm")]],
];

window.mathVirtualKeyboard.layouts = [
  {
    id: "tracolab", label: "Matemática", tooltip: "Definir, avaliar e números",
    rows: [
      [{ latex: DEF, label: ":=" }, { latex: "=", label: "=" }, { latex: "\\to", label: "→" },
       { latex: "\\equiv", label: "≡" }, { latex: "\\frac{#@}{#?}", label: "÷" }, { latex: "#@^{#?}", label: "xⁿ" }],
      [{ latex: "\\sqrt{#0}", label: "√" }, { latex: "#@_{#?}", label: "xᵢ" }, { latex: "\\lbrack #0\\rbrack", label: "[ ]" },
       { latex: "\\left(#0\\right)", label: "( )" }, { latex: "\\pi", label: "π" }, "[backspace]"],
      ["7", "8", "9", { latex: "\\cdot", label: "×" }, "[left]", "[right]"],
      ["4", "5", "6", "+", "-", "<"],
      ["1", "2", "3", "{,}", "0", "[return]"],
    ],
  },
  "greek", "alphabetic",
  {
    id: "unidades", label: "Unidades", tooltip: "Unidades (ADR 0007): nunca se confundem com variáveis",
    rows: GRUPOS_UNIDADES.filter(([g]) => g !== "Momento").map(([, us]) => us.map((u) => ({ latex: u.latex, label: u.rot }))),
  },
];

const pagina = document.getElementById("pagina");
const modelo = document.getElementById("modelo-regiao");
const selModo = document.getElementById("sel-modo");
const nomeFolha = document.getElementById("nome-folha");
const resumo = document.getElementById("resumo");
const listaVars = document.getElementById("lista-variaveis");
const busca = document.getElementById("busca");
const menuUnidade = document.getElementById("menu-unidade");
const CHAVE = "tracolab.folha.v2";
const GRADE = 12;
const LARGURA = 794;

let estado = { nome: "Folha sem título", modo: "parcial", ordem: false, regioes: [] };   // regioes: {id, latex, unidade, x, y}
let proximoId = 1;
let temporizador = null;
let ultimaResposta = null;
let ultimoCampo = null;
const elementos = new Map();   // id -> elemento .rg

// ------------------------------------------------------------------ persistência e histórico (só neste aparelho)

function salvar() {
  try { localStorage.setItem(CHAVE, JSON.stringify(estado)); } catch (_) { /* modo privado etc. */ }
}

function carregar() {
  try {
    const s = JSON.parse(localStorage.getItem(CHAVE) || "null");
    if (s && Array.isArray(s.regioes)) {
      s.regioes = s.regioes.filter((r) => (r.latex || "").trim());   // regiões vazias não voltam
      estado = Object.assign(estado, s);
    }
  } catch (_) { /* ignora */ }
  for (const r of estado.regioes) proximoId = Math.max(proximoId, (Number(String(r.id).replace(/\D/g, "")) || 0) + 1);
}

const historico = [];
let posHist = -1;
const foto = () => JSON.stringify(estado.regioes);

function registrar() {
  const f = foto();
  if (historico[posHist] === f) return;
  historico.splice(posHist + 1);
  historico.push(f);
  if (historico.length > 200) historico.shift();
  posHist = historico.length - 1;
  atualizarBotoesHistorico();
}

function voltarPara(pos) {
  posHist = pos;
  estado.regioes = JSON.parse(historico[pos]);
  montar();
  atualizarBotoesHistorico();
  agendar(0, false);
}

function atualizarBotoesHistorico() {
  document.getElementById("btn-desfazer").disabled = posHist <= 0;
  document.getElementById("btn-refazer").disabled = posHist >= historico.length - 1;
}

document.getElementById("btn-desfazer").addEventListener("click", () => posHist > 0 && voltarPara(posHist - 1));
document.getElementById("btn-refazer").addEventListener("click", () => posHist < historico.length - 1 && voltarPara(posHist + 1));

// ------------------------------------------------------------------ regiões na folha

function montar() {
  for (const el of elementos.values()) el.remove();
  elementos.clear();
  ultimoCampo = null;
  for (const r of estado.regioes) criarElemento(r);
}

function campoDe(id) { return elementos.get(id)?.querySelector(".campo"); }

function criarElemento(r) {
  // importNode + appendChild ANTES de mexer no <math-field>: num elemento ainda não ativado pelo
  // MathLive, atribuir .value cria uma propriedade comum que encobre a do MathLive para sempre.
  const el = document.importNode(modelo.content.firstElementChild, true);
  el.dataset.id = r.id;
  el.style.left = r.x + "px";
  el.style.top = r.y + "px";
  pagina.appendChild(el);
  elementos.set(r.id, el);
  const campo = el.querySelector(".campo");
  customElements.upgrade(campo);
  campo.macros = { ...campo.macros, ...MACROS };
  campo.inlineShortcuts = ATALHOS;
  campo.smartMode = false;
  campo.value = r.latex;
  campo.addEventListener("focusin", () => { ultimoCampo = campo; destacarVariavel(r.id); });
  campo.addEventListener("input", () => { r.latex = campo.value; agendar(); });
  // Enter (teclado físico ou ↵ do teclado virtual): confirma e abre uma região logo abaixo
  campo.addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey) { ev.preventDefault(); ev.stopPropagation(); proximaLinha(r); }
  }, true);
  campo.addEventListener("change", () => { if (campo.hasFocus()) proximaLinha(r); });
  campo.addEventListener("focusout", () => {
    setTimeout(() => {
      if (campo.hasFocus()) return;
      if (!campo.value.trim()) return remover(r.id);   // região vazia some ao perder o foco
      if (extrairUnidade(r)) agendar(0);
    }, 200);
  });
  el.querySelector(".unidade").addEventListener("click", (ev) => abrirMenuUnidade(r, ev.currentTarget));
  arrastavel(el, r);
  return el;
}

function novaRegiao(x, y, latex = "", focar = true) {
  const r = { id: "r" + proximoId++, latex, unidade: "", x: ajustarX(x), y: ajustar(y) };
  estado.regioes.push(r);
  criarElemento(r);
  if (focar) requestAnimationFrame(() => { const c = campoDe(r.id); c.focus(); c.executeCommand("moveToMathfieldEnd"); });
  registrar();
  return r;
}

function remover(id) {
  estado.regioes = estado.regioes.filter((r) => r.id !== id);
  elementos.get(id)?.remove();
  elementos.delete(id);
  registrar();
  agendar();
}

const ajustar = (v) => Math.max(GRADE * 2, Math.round(v / GRADE) * GRADE);
const ajustarX = (v) => Math.min(LARGURA - 120, Math.max(40, Math.round(v / GRADE) * GRADE));

/** Depois do Enter: recalcula já e abre uma região vazia abaixo (ou foca a que já está lá). */
function proximaLinha(r) {
  r.latex = campoDe(r.id).value;
  extrairUnidade(r);
  agendar(0);
  if (!r.latex.trim()) return;
  const abaixo = r.y + 72;
  const existente = estado.regioes.find((o) => Math.abs(o.x - r.x) < GRADE * 2 && o.y > r.y && o.y <= abaixo + GRADE);
  if (existente) campoDe(existente.id).focus();
  else novaRegiao(r.x, abaixo);
}

/**
 * "σ = \mathrm{MPa}": a unidade escrita depois do = sai da fórmula e vira o chip do resultado
 * (σ = 100 MPa, sem a unidade repetida). Devolve true se mudou algo.
 */
function extrairUnidade(r) {
  const latex = r.latex;
  let nivel = 0, igual = -1;
  for (let i = 0; i < latex.length; i++) {
    const c = latex[i];
    if (c === "{") nivel++; else if (c === "}") nivel--;
    else if (c === "=" && nivel === 0) igual = i;
  }
  if (igual < 0) return false;
  const cauda = latex.slice(igual + 1).trim();
  if (!cauda) return false;
  const soUnidade = /^(\s*(\\mathrm\{[^{}]*\}|\^\{?-?\d+\}?|\\cdot|\/|\\,|\s))+$/.test(cauda) && cauda.includes("\\mathrm");
  if (!soUnidade) return false;
  r.unidade = cauda;
  r.latex = latex.slice(0, igual + 1);
  campoDe(r.id).value = r.latex;
  registrar();
  return true;
}

/** Nome de variável → LaTeX de entrada (σ → \sigma, F_max → F_{max}). */
const GREGAS = { "α": "alpha", "β": "beta", "γ": "gamma", "δ": "delta", "ε": "varepsilon", "θ": "theta", "λ": "lambda",
  "μ": "mu", "ρ": "rho", "σ": "sigma", "τ": "tau", "φ": "varphi", "ω": "omega", "Δ": "Delta", "Ω": "Omega", "π": "pi" };
function nomeLatex(n) {
  const [base, ind] = n.split("_");
  const b = [...base].map((c) => (GREGAS[c] ? `\\${GREGAS[c]} ` : c)).join("").trim();
  return ind ? `${b}_{${ind}}` : b;
}

/** Toque no chip "falta F": cria F := ▯ logo acima de quem usa (fica antes na ordem de cálculo). */
function criarDefinicao(nome, usuario) {
  const y = Math.max(GRADE * 2, usuario.y - 64);
  novaRegiao(usuario.x, y, nomeLatex(nome) + DEF);
}

function arrastavel(el, r) {
  const alca = el.querySelector(".alca");
  let ini = null, guia = null;
  alca.addEventListener("pointerdown", (ev) => {
    ev.preventDefault();
    alca.setPointerCapture(ev.pointerId);
    ini = { px: ev.clientX, py: ev.clientY, x: r.x, y: r.y };
    el.classList.add("arrastando");
    guia = document.createElement("div");
    guia.className = "guia";
    pagina.appendChild(guia);
  });
  alca.addEventListener("pointermove", (ev) => {
    if (!ini) return;
    r.x = ajustarX(ini.x + ev.clientX - ini.px);
    r.y = ajustar(ini.y + ev.clientY - ini.py);
    el.style.left = r.x + "px";
    el.style.top = r.y + "px";
    guia.style.top = linhaDeBase(r) + "px";
    numerarOrdem();
  });
  const soltar = () => {
    if (!ini) return;
    ini = null;
    el.classList.remove("arrastando");
    guia?.remove();
    registrar();
    agendar(0);
  };
  alca.addEventListener("pointerup", soltar);
  alca.addEventListener("pointercancel", soltar);
}

/** Linha de base aproximada da região: é ela que define a ordem de cálculo (ADR 0001). */
function linhaDeBase(r) {
  const campo = campoDe(r.id);
  if (!campo) return r.y + 24;
  return r.y + campo.offsetTop + campo.offsetHeight * 0.62;
}

// toque no papel vazio cria uma região ali
pagina.addEventListener("pointerup", (ev) => {
  if (ev.target !== pagina && !ev.target.classList.contains("margem")) return;
  const caixa = pagina.getBoundingClientRect();
  novaRegiao(ev.clientX - caixa.left, ev.clientY - caixa.top - 20);
});

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

// ------------------------------------------------------------------ cálculo

function agendar(ms = 220, salvarHist = true) {
  salvar();
  clearTimeout(temporizador);
  temporizador = setTimeout(() => { if (salvarHist) registrar(); recalcular(); }, ms);
}

async function calcular(pedido) {
  if (window.TracoLabNativo) return JSON.parse(window.TracoLabNativo.calcular(JSON.stringify(pedido)));
  const resp = await fetch("api/calcular", { method: "POST", body: JSON.stringify(pedido) });
  if (!resp.ok) throw new Error(await resp.text());
  return resp.json();
}

async function recalcular() {
  const pedido = {
    modo: estado.modo,
    regioes: estado.regioes.map((r) => ({ id: r.id, latex: r.latex + (r.unidade || ""), x: r.x, y: linhaDeBase(r) })),
  };
  numerarOrdem();
  try {
    const resp = await calcular(pedido);
    ultimaResposta = resp;
    for (const o of resp.regioes) pintar(o);
    pintarResumo(resp);
    pintarVariaveis();
  } catch (e) {
    resumo.textContent = "sem conexão com o núcleo";
  }
}

/** Ordem de cálculo: linha de base, depois x (ADR 0001). Numera as regiões e devolve a lista. */
function numerarOrdem() {
  const ordem = [...estado.regioes].sort((a, b) => linhaDeBase(a) - linhaDeBase(b) || a.x - b.x);
  ordem.forEach((r, i) => { const el = elementos.get(r.id); if (el) el.querySelector(".bd").textContent = i + 1; });
  return ordem;
}

const markup = (latex) => window.MathLive.convertLatexToMarkup(latex);
const escapar = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

function pintar(o) {
  const el = elementos.get(o.id);
  if (!el) return;
  const r = estado.regioes.find((x) => x.id === o.id);
  const abaixo = o.abaixo || [];
  const cadeia = o.tipo === "erro" && /^Depende de/.test(o.msg || "");
  el.className = "rg " + (abaixo.length ? "ordem" : cadeia ? "cadeia" : (o.tipo || ""));
  const res = el.querySelector(".resultado");
  const un = el.querySelector(".unidade");
  const falta = el.querySelector(".falta");
  const nota = el.querySelector(".nota");

  // resultado (avaliações): número em vermelhão e a unidade como chip que troca a exibição
  const avaliacao = o.papel === "avaliacao_numerica" || o.papel === "avaliacao_simbolica";
  res.innerHTML = ""; un.hidden = true;
  if (avaliacao && o.resultado) {
    const partes = o.tipo === "numerico" ? o.resultado.split("\\,") : [o.resultado];
    res.innerHTML = markup(partes[0]);
    if (partes[1]) { un.innerHTML = markup(partes.slice(1).join("\\,")); un.hidden = false; un.setAttribute("aria-label", "Trocar a unidade do resultado"); }
  } else if (avaliacao && r?.unidade && o.tipo !== "parcial") {
    un.innerHTML = markup(r.unidade); un.hidden = false;
  }

  // falta: um chip por nome que ainda não existe na folha
  falta.querySelectorAll(".chip").forEach((c) => c.remove());
  const faltam = (o.livres || []).filter((n) => !abaixo.includes(n));
  falta.hidden = !(o.tipo === "parcial" && faltam.length);
  for (const n of faltam) {
    const chip = document.createElement("button");
    chip.className = "chip";
    chip.textContent = n.replace("_", "");
    chip.setAttribute("aria-label", `Definir ${n}`);
    chip.addEventListener("click", () => criarDefinicao(n, r));
    falta.appendChild(chip);
  }

  // notas: erro de ordem (balão), erro, cadeia, redefinição (discretas)
  nota.hidden = true; nota.className = "nota";
  if (abaixo.length) {
    nota.innerHTML = `${abaixo.map((n) => `<i>${escapar(n)}</i>`).join(", ")} só ${abaixo.length > 1 ? "são definidas" : "é definida"} mais abaixo. A avaliação segue de cima para baixo e da esquerda para a direita: mova a definição para cima.`;
    nota.hidden = false;
  } else if (o.tipo === "erro" || o.tipo === "erro_sintaxe" || o.tipo === "nao_suportado") {
    nota.textContent = o.msg || o.tipo;
    nota.classList.add("discreta"); nota.hidden = false;
  } else if (o.aviso) {
    nota.textContent = o.aviso === "Redefinição" ? `redefine ${(o.define || []).join(", ")}` : o.aviso;
    nota.classList.add("discreta"); nota.hidden = false;
  }
}

function pintarResumo(resp) {
  const regs = resp.regioes.filter((o) => o.tipo !== "vazia");
  const pend = regs.filter((o) => o.tipo === "parcial" && !(o.abaixo || []).length).length;
  const erros = regs.filter((o) => ["erro", "erro_sintaxe"].includes(o.tipo) || (o.abaixo || []).length).length;
  const faltam = [...new Set(regs.flatMap((o) => (o.tipo === "parcial" ? (o.livres || []).filter((n) => !(o.abaixo || []).includes(n)) : [])))];
  let t = `${regs.length} ${regs.length === 1 ? "região" : "regiões"}`;
  if (pend) t += ` · ${pend} ${pend === 1 ? "pendente" : "pendentes"}`;
  if (erros) t += ` · ${erros} ${erros === 1 ? "erro" : "erros"}`;
  if (faltam.length) t += ` · falta ${faltam.slice(0, 4).map((n) => `<i>${escapar(n)}</i>`).join(", ")}${faltam.length > 4 ? "…" : ""}`;
  if (!pend && !erros && regs.length) t += " · calculado";
  resumo.innerHTML = t;
}

function pintarVariaveis() {
  if (!ultimaResposta) return;
  const porId = new Map(ultimaResposta.regioes.map((o) => [o.id, o]));
  const filtro = busca.value.trim().toLowerCase();
  listaVars.innerHTML = "";
  const vistos = new Set();
  for (const r of numerarOrdem()) {
    const o = porId.get(r.id);
    for (const nome of o?.define || []) {
      vistos.add(nome);
      if (filtro && !nome.toLowerCase().includes(filtro)) continue;
      const b = document.createElement("button");
      b.className = "vr";
      b.dataset.id = r.id;
      let valor;
      if ((o.abaixo || []).length || o.tipo === "erro" || o.tipo === "erro_sintaxe") { b.classList.add("erro"); valor = "erro"; }
      else if (o.tipo === "parcial") { b.classList.add("pend"); valor = `pendente · falta ${(o.livres || []).map(escapar).join(", ")}`; }
      else if (o.tipo === "nao_suportado") valor = "não suportado";
      else if (!o.resultado) valor = "função";
      else valor = markup(o.resultado);
      b.innerHTML = `<span class="vs">${escapar(nome).replace(/_(\w+)/, "<sub>$1</sub>")}</span><span class="vv">${valor}</span>`;
      b.addEventListener("click", () => { const el = elementos.get(r.id); el?.scrollIntoView({ block: "center", behavior: "smooth" }); campoDe(r.id)?.focus(); });
      listaVars.appendChild(b);
    }
  }
  // nomes usados que não estão definidos em lugar nenhum
  const usados = new Set(ultimaResposta.regioes.flatMap((o) => (o.tipo === "parcial" ? o.livres || [] : [])));
  for (const nome of usados) {
    if (vistos.has(nome) || (filtro && !nome.toLowerCase().includes(filtro))) continue;
    const usuario = estado.regioes.find((r) => (porId.get(r.id)?.livres || []).includes(nome));
    const b = document.createElement("button");
    b.className = "vr pend";
    b.innerHTML = `<span class="vs" style="color:var(--cobalto)">${escapar(nome)}</span><span class="vv" style="color:var(--cobalto)">falta definir</span>`;
    b.addEventListener("click", () => usuario && criarDefinicao(nome, usuario));
    listaVars.appendChild(b);
  }
  if (!listaVars.children.length) {
    listaVars.innerHTML = `<p class="vazio">${filtro ? "Nenhum símbolo com esse nome." : "As variáveis definidas na folha aparecem aqui, na ordem de cálculo."}</p>`;
  }
}

function destacarVariavel(id) {
  for (const v of listaVars.querySelectorAll(".vr")) v.classList.toggle("foco", v.dataset.id === id);
}

// ------------------------------------------------------------------ início

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
