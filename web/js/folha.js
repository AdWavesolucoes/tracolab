"use strict";
// Estado da folha, persistência e histórico (só neste aparelho) e regiões na página.

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
let registroPendente = false;   // edição já feita mas ainda não gravada no histórico (espera o debounce)
let ultimaResposta = null;
let ultimoCampo = null;
let motor = "";   // máquina que calculou (versão web de teste com mais de uma instância)
const elementos = new Map();   // id -> elemento .rg

// ------------------------------------------------------------------ persistência e histórico (só neste aparelho)

function salvar() {
  try { localStorage.setItem(CHAVE, JSON.stringify(estado)); } catch (_) { /* modo privado etc. */ }
}

function carregar() {
  try {
    const s = JSON.parse(localStorage.getItem(CHAVE) || "null");
    if (s && Array.isArray(s.regioes)) {
      s.regioes = s.regioes.filter((r) => r.tipo || (r.latex || "").trim());   // fórmulas vazias não voltam
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

/** Grava no histórico a edição que ainda espera o debounce, para desfazer/refazer partirem do estado visto. */
function fecharEdicao() {
  if (!registroPendente) return;
  clearTimeout(temporizador);
  registroPendente = false;
  registrar();
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

document.getElementById("btn-desfazer").addEventListener("click", () => { fecharEdicao(); if (posHist > 0) voltarPara(posHist - 1); });
document.getElementById("btn-refazer").addEventListener("click", () => { fecharEdicao(); if (posHist < historico.length - 1) voltarPara(posHist + 1); });

// ------------------------------------------------------------------ regiões na folha

function montar() {
  for (const el of elementos.values()) el.remove();
  elementos.clear();
  ultimoCampo = null;
  for (const r of estado.regioes) criarElemento(r);
}

function campoDe(id) { return elementos.get(id)?.querySelector(".campo"); }

function criarElemento(r) {
  const tipo = TL.tipos[r.tipo];
  // importNode + appendChild ANTES de mexer no <math-field>: num elemento ainda não ativado pelo
  // MathLive, atribuir .value cria uma propriedade comum que encobre a do MathLive para sempre.
  const el = tipo ? tipo.criar(r) : document.importNode(modelo.content.firstElementChild, true);
  el.dataset.id = r.id;
  el.style.left = r.x + "px";
  el.style.top = r.y + "px";
  pagina.appendChild(el);
  elementos.set(r.id, el);
  if (tipo) tipo.iniciar?.(el, r);
  else iniciarFormula(el, r);
  arrastavel(el, r);
  return el;
}

/** Região de fórmula: o editor MathLive e o chip de unidade do resultado. */
function iniciarFormula(el, r) {
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
}

const novoId = () => "r" + proximoId++;

/** Acrescenta uma região já montada (de qualquer tipo) à folha e ao histórico. */
function adicionarRegiao(r) {
  estado.regioes.push(r);
  criarElemento(r);
  registrar();
  return r;
}

/** Nova região de fórmula em (x, y), opcionalmente com o editor já focado. */
function novaRegiao(x, y, latex = "", focar = true) {
  const r = { id: novoId(), latex, unidade: "", x: ajustarX(x), y: ajustar(y) };
  adicionarRegiao(r);
  if (focar) requestAnimationFrame(() => { const c = campoDe(r.id); c.focus(); c.executeCommand("moveToMathfieldEnd"); });
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
  const existente = estado.regioes.find((o) => !o.tipo && Math.abs(o.x - r.x) < GRADE * 2 && o.y > r.y && o.y <= abaixo + GRADE);
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
  const tipo = TL.tipos[r.tipo];
  if (tipo) return tipo.linhaDeBase ? tipo.linhaDeBase(r, elementos.get(r.id)) : r.y + 24;
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

