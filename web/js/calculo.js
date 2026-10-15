"use strict";
// Pedido ao núcleo (ponte nativa no app ou POST /api/calcular no navegador) e ordem de cálculo.

function agendar(ms = 220, salvarHist = true) {
  salvar();
  clearTimeout(temporizador);
  registroPendente = registroPendente || salvarHist;
  temporizador = setTimeout(() => { if (registroPendente) registrar(); registroPendente = false; recalcular(); }, ms);
}

async function calcular(pedido) {
  if (window.TracoLabNativo) return JSON.parse(window.TracoLabNativo.calcular(JSON.stringify(pedido)));
  const resp = await fetch("api/calcular", { method: "POST", body: JSON.stringify(pedido) });
  // erro da API vem como {"erro": "..."} (pedido grande demais, servidor ocupado...): mostra na barra
  if (!resp.ok) throw new Error((await resp.json().catch(() => ({}))).erro || "sem conexão com o núcleo");
  motor = resp.headers.get("X-TracoLab-Motor") || "";
  return resp.json();
}

let seqCalculo = 0;   // só a resposta do último pedido pinta a folha

async function recalcular() {
  const minha = ++seqCalculo;
  const pedido = {
    modo: estado.modo,
    regioes: estado.regioes.map((r) => {
      const tipo = TL.tipos[r.tipo];
      if (tipo) return tipo.pedido ? tipo.pedido(r) : null;
      // a unidade do resultado só vale enquanto a fórmula termina em "=": sem ele, numa definição
      // ela multiplicaria o valor (R := qL/2 kN)
      const unidade = r.unidade && /=\s*$/.test(r.latex) ? r.unidade : "";
      const o = { id: r.id, latex: r.latex + unidade, x: r.x, y: linhaDeBase(r) };
      if (r.substituir) o.substituir = true;
      return o;
    }).filter(Boolean),
    opcoes: Object.assign({}, ...TL.opcoes.map((f) => f())),
  };
  numerarOrdem();
  try {
    const resp = await calcular(pedido);
    if (minha !== seqCalculo) return;   // resposta velha: já há um pedido mais novo
    ultimaResposta = resp;
    for (const o of resp.regioes) pintar(o);
    pintarResumo(resp);
    pintarVariaveis();
    for (const f of TL.aposCalculo) f(resp);
    if (resp.erro) resumo.textContent = resp.erro;
  } catch (e) {
    if (minha !== seqCalculo) return;
    resumo.textContent = e instanceof TypeError || !e.message ? "sem conexão com o núcleo" : e.message;   // TypeError: rede
  }
}

/** Ordem de cálculo: linha de base, depois x (ADR 0001). Numera as regiões e devolve a lista. */
function numerarOrdem() {
  // só o que vai ao núcleo entra na numeração (texto não tem ordem de cálculo)
  const ordem = estado.regioes.filter((r) => !TL.tipos[r.tipo] || TL.tipos[r.tipo].pedido?.(r))
    .sort((a, b) => linhaDeBase(a) - linhaDeBase(b) || a.x - b.x);
  ordem.forEach((r, i) => { const bd = elementos.get(r.id)?.querySelector(".bd"); if (bd) bd.textContent = i + 1; });
  return ordem;
}

