"use strict";
// Pedido ao núcleo (ponte nativa no app ou POST /api/calcular no navegador) e ordem de cálculo.

function agendar(ms = 220, salvarHist = true) {
  salvar();
  clearTimeout(temporizador);
  temporizador = setTimeout(() => { if (salvarHist) registrar(); recalcular(); }, ms);
}

async function calcular(pedido) {
  if (window.TracoLabNativo) return JSON.parse(window.TracoLabNativo.calcular(JSON.stringify(pedido)));
  const resp = await fetch("api/calcular", { method: "POST", body: JSON.stringify(pedido) });
  if (!resp.ok) throw new Error(await resp.text());
  motor = resp.headers.get("X-TracoLab-Motor") || "";
  return resp.json();
}

async function recalcular() {
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
    ultimaResposta = resp;
    for (const o of resp.regioes) pintar(o);
    pintarResumo(resp);
    pintarVariaveis();
    for (const f of TL.aposCalculo) f(resp);
  } catch (e) {
    resumo.textContent = "sem conexão com o núcleo";
  }
}

/** Ordem de cálculo: linha de base, depois x (ADR 0001). Numera as regiões e devolve a lista. */
function numerarOrdem() {
  const ordem = [...estado.regioes].sort((a, b) => linhaDeBase(a) - linhaDeBase(b) || a.x - b.x);
  ordem.forEach((r, i) => { const bd = elementos.get(r.id)?.querySelector(".bd"); if (bd) bd.textContent = i + 1; });
  return ordem;
}

