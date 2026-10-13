"use strict";
/*
 * TracoLab: página de cálculo (ADR 0008).
 * Cada região é um editor MathLive posicionado no papel. A cada mudança, a página inteira vai
 * para o núcleo (ponte nativa no Android ou POST /api/calcular no navegador) e a resposta
 * pinta o estado de cada região: numérico, parcial ("faltam: …"), erro.
 */

const MF = window.MathfieldElement;
MF.fontsDirectory = "vendor/mathlive-0.110.0/fonts";
MF.soundsDirectory = null;
MF.decimalSeparator = ",";

// Atalhos controlados: os padrões do MathLive trocam "in" por ∈ e quebrariam nomes como "Fin".
const ATALHOS = {
  ":=": "\\coloneq", "->": "\\to", ">=": "\\ge", "<=": "\\le", "!=": "\\ne",
  "sqrt": "\\sqrt{#?}", "pi": "\\pi", "sin": "\\sin", "cos": "\\cos", "tan": "\\tan",
  "ln": "\\ln", "log": "\\log", "exp": "\\exp",
  "alpha": "\\alpha", "beta": "\\beta", "gamma": "\\gamma", "delta": "\\delta", "epsilon": "\\varepsilon",
  "theta": "\\theta", "lambda": "\\lambda", "mu": "\\mu", "rho": "\\rho", "sigma": "\\sigma",
  "tau": "\\tau", "phi": "\\varphi", "omega": "\\omega", "Delta": "\\Delta", "Omega": "\\Omega",
};

const UNIDADES = [
  ["mm", "cm", "m", "km", "in", "ft"],
  ["N", "kN", "MN", "kgf", "lbf", "kip"],
  ["Pa", "kPa", "MPa", "GPa", "psi", "ksi"],
  ["kg", "g", "s", "min", "hr", "Hz"],
  ["J", "kJ", "W", "kW", "V", "A"],
  ["K", "rad", "deg", "%", "L", "rpm"],
];

window.mathVirtualKeyboard.layouts = [
  {
    label: "TracoLab", tooltip: "Definir, avaliar e estrutura",
    rows: [
      [{ latex: "\\coloneq", label: ":=" }, { latex: "=", label: "=" }, { latex: "\\to", label: "→" },
       { latex: "\\equiv", label: "≡" }, { latex: "\\frac{#@}{#?}", label: "a/b" }, { latex: "#@^{#?}", label: "xⁿ" },
       { latex: "\\sqrt{#0}", label: "√" }, { latex: "#@_{#?}", label: "xᵢ" }],
      [{ latex: "\\lbrack #0\\rbrack", label: "v[i]" }, { latex: "\\left(#0\\right)", label: "( )" },
       { latex: "\\cdot", label: "·" }, { latex: "\\pi", label: "π" }, { latex: "\\sin\\left(#0\\right)", label: "sin" },
       { latex: "\\cos\\left(#0\\right)", label: "cos" }, { latex: "\\ln\\left(#0\\right)", label: "ln" },
       "[backspace]"],
      ["7", "8", "9", "/", "[left]", "[right]", "[return]"],
      ["4", "5", "6", "*", "+", "-", "{,}"],
      ["1", "2", "3", "0", "<", ">", "[hide-keyboard]"],
    ],
  },
  "alphabetic", "greek",
  {
    label: "Unid.", tooltip: "Unidades (ADR 0007): nunca se confundem com variáveis",
    rows: UNIDADES.map((linha) => linha.map((u) => ({ latex: `\\mathrm{${u === "%" ? "\\%" : u}}`, label: u }))),
  },
];

const pagina = document.getElementById("pagina");
const modelo = document.getElementById("modelo-regiao");
const selModo = document.getElementById("sel-modo");
const status = document.getElementById("status");
const CHAVE = "tracolab.pagina.v1";
const GRADE = 12;

let estado = { modo: "parcial", regioes: [] };   // regioes: {id, latex, x, y}
let proximoId = 1;
let temporizador = null;

// ------------------------------------------------------------------ persistência (só neste aparelho)

function salvar() {
  try { localStorage.setItem(CHAVE, JSON.stringify(estado)); } catch (_) { /* modo privado, etc. */ }
}

function carregar() {
  try {
    const s = JSON.parse(localStorage.getItem(CHAVE) || "null");
    // descarta regiões vazias (a versão anterior podia salvá-las e elas ficariam invisíveis)
    if (s && Array.isArray(s.regioes)) { s.regioes = s.regioes.filter((r) => (r.latex || "").trim()); estado = s; }
  } catch (_) { /* ignora */ }
  for (const r of estado.regioes) proximoId = Math.max(proximoId, Number(String(r.id).replace(/\D/g, "")) + 1 || 1);
}

// ------------------------------------------------------------------ regiões na tela

const elementos = new Map();   // id -> elemento .regiao

function criarElemento(r) {
  // importNode + appendChild ANTES de mexer no <math-field>: num elemento ainda não ativado pelo
  // MathLive, atribuir .value cria uma propriedade comum que encobre a do MathLive para sempre
  // (a leitura devolvia sempre "" e a região era apagada ao perder o foco).
  const el = document.importNode(modelo.content.firstElementChild, true);
  el.dataset.id = r.id;
  el.style.left = r.x + "px";
  el.style.top = r.y + "px";
  pagina.appendChild(el);
  elementos.set(r.id, el);
  const campo = el.querySelector(".campo");
  customElements.upgrade(campo);
  campo.inlineShortcuts = ATALHOS;
  campo.smartMode = false;
  campo.value = r.latex;
  campo.addEventListener("input", () => { r.latex = campo.value; agendar(); });
  // Enter (teclado físico ou tecla ↵ do teclado virtual): confirma e abre uma região logo abaixo
  campo.addEventListener("keydown", (ev) => {
    if (ev.key === "Enter" && !ev.shiftKey) { ev.preventDefault(); ev.stopPropagation(); proximaLinha(r); }
  }, true);
  campo.addEventListener("change", () => { if (campo.hasFocus()) proximaLinha(r); });
  campo.addEventListener("focusout", () => {
    // região vazia some ao perder o foco
    setTimeout(() => { if (!campo.value.trim() && !campo.hasFocus()) remover(r.id); }, 200);
  });
  el.querySelector(".apagar").addEventListener("click", () => remover(r.id));
  arrastavel(el, r);
  return el;
}

/** Depois do Enter: recalcula já e abre uma região vazia abaixo (ou foca a que já está lá). */
function proximaLinha(r) {
  r.latex = elementos.get(r.id).querySelector(".campo").value;
  agendar(0);
  if (!r.latex.trim()) return;
  const abaixo = r.y + 72;
  const existente = estado.regioes.find((o) => Math.abs(o.x - r.x) < GRADE * 2 && o.y > r.y && o.y <= abaixo + GRADE);
  if (existente) elementos.get(existente.id).querySelector(".campo").focus();
  else novaRegiao(r.x, abaixo);
}

function novaRegiao(x, y, focar = true) {
  const r = { id: "r" + proximoId++, latex: "", x: ajustar(x), y: ajustar(y) };
  estado.regioes.push(r);
  const el = criarElemento(r);
  if (focar) requestAnimationFrame(() => el.querySelector(".campo").focus());
  return r;
}

function remover(id) {
  estado.regioes = estado.regioes.filter((r) => r.id !== id);
  elementos.get(id)?.remove();
  elementos.delete(id);
  agendar();
}

const ajustar = (v) => Math.max(GRADE, Math.round(v / GRADE) * GRADE);

/** Linha de base aproximada da região: é ela que define a ordem de cálculo (ADR 0001). */
function linhaDeBase(r) {
  const el = elementos.get(r.id);
  const campo = el?.querySelector(".campo");
  if (!campo) return r.y + 24;
  return r.y + campo.offsetTop + campo.offsetHeight * 0.62;
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
    r.x = ajustar(ini.x + ev.clientX - ini.px);
    r.y = ajustar(ini.y + ev.clientY - ini.py);
    el.style.left = r.x + "px";
    el.style.top = r.y + "px";
    guia.style.top = linhaDeBase(r) + "px";
  });
  const soltar = () => {
    if (!ini) return;
    ini = null;
    el.classList.remove("arrastando");
    guia?.remove();
    agendar(0);
  };
  alca.addEventListener("pointerup", soltar);
  alca.addEventListener("pointercancel", soltar);
}

// toque no papel vazio cria uma região ali
pagina.addEventListener("pointerup", (ev) => {
  if (ev.target !== pagina) return;
  const caixa = pagina.getBoundingClientRect();
  novaRegiao(ev.clientX - caixa.left, ev.clientY - caixa.top - 20);
});

document.getElementById("btn-nova").addEventListener("click", () => {
  const maisBaixa = estado.regioes.reduce((m, r) => Math.max(m, r.y), 0);
  novaRegiao(48, maisBaixa ? maisBaixa + 72 : 48);
});

document.getElementById("btn-limpar").addEventListener("click", () => {
  if (!estado.regioes.length) return;
  for (const id of [...elementos.keys()]) remover(id);
});

selModo.addEventListener("change", () => { estado.modo = selModo.value; agendar(0); });

// ------------------------------------------------------------------ cálculo

function agendar(ms = 220) {
  salvar();
  clearTimeout(temporizador);
  temporizador = setTimeout(recalcular, ms);
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
    regioes: estado.regioes.map((r) => ({ id: r.id, latex: r.latex, x: r.x, y: linhaDeBase(r) })),
  };
  numerarOrdem(pedido.regioes);
  try {
    const t0 = performance.now();
    const resp = await calcular(pedido);
    for (const o of resp.regioes) pintar(o);
    status.textContent = `${estado.regioes.length} regiões · núcleo ${resp.ms} ms · total ${Math.round(performance.now() - t0)} ms`;
  } catch (e) {
    status.textContent = "sem conexão com o núcleo: " + e.message;
  }
}

/** Mostra a ordem de cálculo na alça de cada região (ADR 0001: linha de base, depois x). */
function numerarOrdem(regs) {
  [...regs].sort((a, b) => a.y - b.y || a.x - b.x)
    .forEach((r, i) => { const el = elementos.get(r.id); if (el) el.querySelector(".ordem").textContent = i + 1; });
}

function pintar(o) {
  const el = elementos.get(o.id);
  if (!el) return;
  el.className = "regiao " + (o.tipo || "");
  const res = el.querySelector(".resultado");
  const info = el.querySelector(".info");
  const avaliacao = o.papel === "avaliacao_numerica" || o.papel === "avaliacao_simbolica";
  res.innerHTML = avaliacao && o.resultado ? window.MathLive.convertLatexToMarkup(o.resultado) : "";
  let txt = "";
  if (o.tipo === "parcial" && o.livres?.length) txt = "faltam: " + o.livres.join(", ");
  else if (o.tipo === "erro" || o.tipo === "erro_sintaxe" || o.tipo === "nao_suportado") txt = o.msg || o.tipo;
  if (o.aviso) txt += (txt ? " · " : "") + "⚠ " + o.aviso;
  info.textContent = txt;
}

// ------------------------------------------------------------------ início

carregar();
selModo.value = estado.modo;
for (const r of estado.regioes) criarElemento(r);
if (!estado.regioes.length) {
  // página de exemplo: tensão numa barra, com dados chegando em partes
  const ex = [
    ["A\\coloneq\\frac{\\pi d^2}{4}", 48, 60], ["\\sigma\\coloneq\\frac{F}{A}", 48, 132],
    ["\\sigma=\\mathrm{MPa}", 48, 204],
  ];
  for (const [latex, x, y] of ex) {
    const r = { id: "r" + proximoId++, latex, x, y };
    estado.regioes.push(r);
    criarElemento(r);
  }
}
customElements.whenDefined("math-field").then(() => agendar(0));
