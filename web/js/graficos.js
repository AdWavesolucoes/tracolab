"use strict";
// Gráfico X-Y (ADR 0016): uma região com uma ou mais curvas em x num intervalo [a, b]. Vai no mesmo
// pedido das fórmulas (precisa das definições acima dela); o núcleo devolve os pontos amostrados e
// aqui eles viram SVG, sem biblioteca. Nome faltando além de x deixa a região pendente (chips "falta").
// Criar: tecla de gráfico na faixa. Tocar na curva mostra (x, y) ("trace").
{
  const L = 520, A = 300, ME = 62, MD = 16, MT = 14, MB = 44;   // viewBox e margens do desenho
  const CORES = ["#2748C9", "#C23A14", "#0E1726", "#5B6270"];
  const MAX_CURVAS = 4;
  const NS = "http://www.w3.org/2000/svg";

  const num = (v, casas) => {
    if (v === 0 || Object.is(v, -0)) return "0";
    const a = Math.abs(v);
    if (a >= 1e6 || a < 1e-3) return v.toExponential(1).replace(".", ",").replace("e+", "e");
    return Number(v.toFixed(casas)).toLocaleString("pt-BR", { maximumFractionDigits: casas });
  };

  /** Marcas com números redondos (1, 2, 5 × 10ⁿ) cobrindo [lo, hi]. */
  function marcas(lo, hi, alvo) {
    const bruto = (hi - lo) / Math.max(2, alvo);
    const pot = Math.pow(10, Math.floor(Math.log10(bruto)));
    const f = bruto / pot;
    const passo = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * pot;
    const out = [];
    for (let v = Math.ceil(lo / passo - 1e-9) * passo; v <= hi + passo * 1e-9; v += passo) out.push(Math.abs(v) < passo * 1e-9 ? 0 : v);
    return { valores: out, casas: Math.max(0, Math.min(8, -Math.floor(Math.log10(passo) + 1e-9))) };
  }

  /** Faixa vertical: tudo, ou os percentis 2 a 98 quando há polos que esmagariam a curva. */
  function faixaY(series) {
    const v = series.flatMap((s) => s.y.filter((q) => q !== null)).sort((a, b) => a - b);
    if (!v.length) return [-1, 1];
    let lo = v[0], hi = v[v.length - 1];
    const abs = v.map(Math.abs).sort((a, b) => a - b);
    if (hi - lo > 50 * (abs[abs.length >> 1] || 1e-12) && v.length > 20) {
      lo = v[Math.floor(v.length * 0.02)]; hi = v[Math.ceil(v.length * 0.98) - 1];
    }
    if (lo === hi) { const d = Math.abs(lo) * 0.1 || 1; lo -= d; hi += d; }
    const folga = (hi - lo) * 0.06;
    return [lo - folga, hi + folga];
  }

  const el_ = (nome, attrs = {}, texto) => {
    const e = document.createElementNS(NS, nome);
    for (const [k, v] of Object.entries(attrs)) e.setAttribute(k, v);
    if (texto !== undefined) e.textContent = texto;
    return e;
  };

  /** Desenha os eixos, a grade, as curvas e devolve o que o trace precisa. */
  function desenhar(svg, g) {
    svg.replaceChildren();
    const xs = g.x, x0 = xs[0], x1 = xs[xs.length - 1];
    const [y0, y1] = faixaY(g.series);
    const px = (v) => ME + ((v - x0) / (x1 - x0)) * (L - ME - MD);
    const py = (v) => MT + (1 - (v - y0) / (y1 - y0)) * (A - MT - MB);
    const defs = el_("defs");
    const clip = el_("clipPath", { id: "gc" + Math.random().toString(36).slice(2, 8) });
    clip.appendChild(el_("rect", { x: ME, y: MT, width: L - ME - MD, height: A - MT - MB }));
    defs.appendChild(clip); svg.appendChild(defs);

    const mx = marcas(x0, x1, 6), my = marcas(y0, y1, 5);
    const grade = el_("g", { class: "gr-grade" }), rotulos = el_("g", { class: "gr-num" });
    for (const v of mx.valores) {
      grade.appendChild(el_("line", { x1: px(v), x2: px(v), y1: MT, y2: A - MB }));
      rotulos.appendChild(el_("text", { x: px(v), y: A - MB + 16, "text-anchor": "middle" }, num(v, mx.casas)));
    }
    for (const v of my.valores) {
      grade.appendChild(el_("line", { x1: ME, x2: L - MD, y1: py(v), y2: py(v) }));
      rotulos.appendChild(el_("text", { x: ME - 7, y: py(v) + 4, "text-anchor": "end" }, num(v, my.casas)));
    }
    svg.append(grade, rotulos);
    if (x0 < 0 && x1 > 0) svg.appendChild(el_("line", { class: "gr-zero", x1: px(0), x2: px(0), y1: MT, y2: A - MB }));
    if (y0 < 0 && y1 > 0) svg.appendChild(el_("line", { class: "gr-zero", x1: ME, x2: L - MD, y1: py(0), y2: py(0) }));
    svg.appendChild(el_("rect", { class: "gr-moldura", x: ME, y: MT, width: L - ME - MD, height: A - MT - MB }));
    svg.appendChild(el_("text", { class: "gr-eixo", x: (ME + L - MD) / 2, y: A - 6, "text-anchor": "middle" }, g.xu ? `x (${g.xu})` : "x"));
    svg.appendChild(el_("text", { class: "gr-eixo", transform: `translate(14 ${(MT + A - MB) / 2}) rotate(-90)`, "text-anchor": "middle" }, g.yu ? `y (${g.yu})` : "y"));

    const curvas = el_("g", { "clip-path": `url(#${clip.id})` });
    g.series.forEach((s, i) => {
      // buraco (null) interrompe a linha; ponto isolado vira um pontinho
      let d = "", aberto = false, solto = null;
      const pontos = [];
      s.y.forEach((v, k) => {
        if (v === null) { aberto = false; return; }
        const X = px(xs[k]).toFixed(1), Y = Math.max(-1e4, Math.min(1e4, py(v))).toFixed(1);
        d += (aberto ? "L" : "M") + X + " " + Y;
        aberto = true;
      });
      s.y.forEach((v, k) => { if (v !== null && (k === 0 || s.y[k - 1] === null) && (k === s.y.length - 1 || s.y[k + 1] === null)) pontos.push([px(xs[k]), py(v)]); });
      curvas.appendChild(el_("path", { class: "gr-curva", d, stroke: CORES[i % CORES.length] }));
      for (const [X, Y] of pontos) curvas.appendChild(el_("circle", { cx: X, cy: Y, r: 2, fill: CORES[i % CORES.length] }));
    });
    svg.appendChild(curvas);
    const trace = el_("g", { class: "gr-trace", hidden: "" });
    svg.appendChild(trace);
    return { px, py, x0, x1, y0, y1 };
  }

  /** Trace: mostra (x, y) de cada curva no ponto mais próximo do toque. */
  function ligarTrace(svg, el) {
    const mover = (ev) => {
      const e = el._g; if (!e) return;
      const caixa = svg.getBoundingClientRect();
      const sx = ((ev.clientX - caixa.left) / caixa.width) * L;
      const { dados, escala } = e;
      let k = Math.round(((sx - ME) / (L - ME - MD)) * (dados.x.length - 1));
      k = Math.max(0, Math.min(dados.x.length - 1, k));
      const t = svg.querySelector(".gr-trace");
      t.replaceChildren(); t.removeAttribute("hidden");
      const X = escala.px(dados.x[k]);
      t.appendChild(el_("line", { x1: X, x2: X, y1: MT, y2: A - MB }));
      const partes = [`x = ${num(dados.x[k], 4)}${dados.xu ? " " + dados.xu : ""}`];
      dados.series.forEach((s, i) => {
        const v = s.y[k];
        partes.push(`y${dados.series.length > 1 ? i + 1 : ""} = ${v === null ? "—" : num(v, 4) + (dados.yu ? " " + dados.yu : "")}`);
        if (v !== null) t.appendChild(el_("circle", { cx: X, cy: Math.max(MT, Math.min(A - MB, escala.py(v))), r: 4.5, fill: CORES[i % CORES.length] }));
      });
      const texto = partes.join("   ");
      const larg = Math.min(L - MD - ME, texto.length * 6.4 + 16);
      const bx = Math.max(ME, Math.min(L - MD - larg, X - larg / 2));
      t.appendChild(el_("rect", { class: "gr-dica", x: bx, y: MT + 4, width: larg, height: 22, rx: 6 }));
      t.appendChild(el_("text", { class: "gr-dica-t", x: bx + 8, y: MT + 19 }, texto));
      el.dataset.trace = texto;
    };
    svg.addEventListener("pointerdown", (ev) => { svg.setPointerCapture?.(ev.pointerId); mover(ev); });
    svg.addEventListener("pointermove", (ev) => { if (ev.buttons || ev.pointerType === "touch") mover(ev); });
    // o trace fica até o toque seguir para fora do gráfico
    document.addEventListener("pointerdown", (ev) => {
      if (el.contains(ev.target)) return;
      svg.querySelector(".gr-trace")?.setAttribute("hidden", ""); delete el.dataset.trace;
    });
  }

  function criar(r) {
    const el = document.createElement("div");
    el.className = "rg grafico";
    el.tabIndex = -1;
    el.innerHTML = `<span class="bd" aria-hidden="true"></span>
      <button class="alca" aria-label="Arrastar gráfico"></button>
      <div class="gr-cab"><span class="lbl">gráfico</span>
        <button class="gr-editar" aria-expanded="false" aria-label="Editar gráfico">editar</button></div>
      <svg class="gr-svg" viewBox="0 0 ${L} ${A}" role="img" aria-label="Gráfico X-Y" preserveAspectRatio="xMidYMid meet"></svg>
      <div class="gr-vazio" hidden>Aguardando dados.</div>
      <div class="gr-leg"></div>
      <div class="gr-edicao" hidden>
        <div class="gr-series"></div>
        <button class="gr-mais">+ curva</button>
        <div class="gr-intervalo"><span class="lbl">x de</span><math-field class="gr-campo gr-a" math-virtual-keyboard-policy="manual"></math-field>
          <span class="lbl">até</span><math-field class="gr-campo gr-b" math-virtual-keyboard-policy="manual"></math-field>
          <label class="lbl">pontos <input class="gr-n" type="number" min="2" max="2000" step="1" inputmode="numeric"></label></div>
        <button class="gr-apagar">Apagar gráfico</button>
      </div>
      <div class="falta" hidden><span class="lbl">falta</span></div>
      <div class="nota" role="note" hidden></div>`;
    return el;
  }

  function prepararCampo(c, valor, aoMudar) {
    customElements.upgrade(c);
    c.macros = { ...c.macros, ...MACROS };
    c.inlineShortcuts = ATALHOS;
    c.smartMode = false;
    c.value = valor;
    c.addEventListener("focusin", () => { ultimoCampo = c; });
    c.addEventListener("input", () => aoMudar(c.value));
    c.addEventListener("keydown", (ev) => { if (ev.key === "Enter") { ev.preventDefault(); ev.stopPropagation(); c.blur(); } }, true);
  }

  function montarSeries(el, r) {
    const lista = el.querySelector(".gr-series");
    lista.replaceChildren();
    r.series.forEach((latex, i) => {
      const linha = document.createElement("div");
      linha.className = "gr-serie";
      linha.innerHTML = `<span class="gr-cor" style="background:${CORES[i % CORES.length]}" aria-hidden="true"></span><span class="lbl">y =</span>
        <math-field class="gr-campo" math-virtual-keyboard-policy="manual"></math-field>
        <button class="gr-tira" aria-label="Remover curva ${i + 1}">×</button>`;
      lista.appendChild(linha);
      prepararCampo(linha.querySelector("math-field"), latex, (v) => { r.series[i] = v; mudou(); });
      linha.querySelector(".gr-tira").addEventListener("click", () => {
        if (r.series.length <= 1) return;
        r.series.splice(i, 1); montarSeries(el, r); mudou(true);
      });
    });
    el.querySelector(".gr-mais").hidden = r.series.length >= MAX_CURVAS;
  }

  const mudou = (registro) => { if (registro) registrar(); agendar(); };

  function iniciar(el, r) {
    r.series = Array.isArray(r.series) && r.series.length ? r.series.map(String) : ["\\sin(x)"];
    r.a = r.a ?? "0"; r.b = r.b ?? "10"; r.n = Number(r.n) || 100;
    const svg = el.querySelector(".gr-svg");
    montarSeries(el, r);
    prepararCampo(el.querySelector(".gr-a"), r.a, (v) => { r.a = v; mudou(); });
    prepararCampo(el.querySelector(".gr-b"), r.b, (v) => { r.b = v; mudou(); });
    const n = el.querySelector(".gr-n");
    n.value = r.n;
    n.addEventListener("input", () => { r.n = Math.max(2, Math.min(2000, Math.round(Number(n.value)) || 100)); mudou(); });
    const painel = el.querySelector(".gr-edicao"), botao = el.querySelector(".gr-editar");
    const abrir = (sim) => { r.aberto = sim; painel.hidden = !sim; botao.setAttribute("aria-expanded", String(sim)); botao.textContent = sim ? "fechar" : "editar"; };
    abrir(r.aberto !== false);
    botao.addEventListener("click", () => abrir(painel.hidden));
    el.querySelector(".gr-mais").addEventListener("click", () => {
      if (r.series.length >= MAX_CURVAS) return;
      r.series.push("x"); montarSeries(el, r); mudou(true);
      el.querySelectorAll(".gr-serie math-field")[r.series.length - 1]?.focus();
    });
    el.querySelector(".gr-apagar").addEventListener("click", () => remover(r.id));
    el.addEventListener("focusout", () => setTimeout(() => { if (!el.contains(document.activeElement)) registrar(); }, 200));
    ligarTrace(svg, el);
  }

  function pintar(o, el, r) {
    const abaixo = o.abaixo || [];
    el.className = "rg grafico " + (abaixo.length ? "ordem" : o.tipo === "grafico" ? "" : o.tipo || "");
    const svg = el.querySelector(".gr-svg"), vazio = el.querySelector(".gr-vazio"), leg = el.querySelector(".gr-leg");
    const falta = el.querySelector(".falta"), nota = el.querySelector(".nota");
    const g = o.tipo === "grafico" ? o.grafico : null;
    el._g = null; delete el.dataset.trace;
    svg.hidden = !g; leg.hidden = !g; vazio.hidden = !!g;
    if (g) {
      el._g = { dados: g, escala: desenhar(svg, g) };
      leg.innerHTML = g.series.map((s, i) => `<span class="gr-item"><span class="gr-cor" style="background:${CORES[i % CORES.length]}"></span>${markup(r.series[i] || "")}</span>`).join("");
    } else {
      vazio.textContent = o.tipo === "parcial" ? "Pendente: faltam dados para traçar a curva." : "Sem curva para mostrar.";
    }
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
    nota.hidden = true; nota.className = "nota";
    if (abaixo.length) {
      nota.innerHTML = `${abaixo.map((n) => `<i>${escapar(n)}</i>`).join(", ")} só ${abaixo.length > 1 ? "são definidas" : "é definida"} mais abaixo: mova a definição para cima do gráfico.`;
      nota.hidden = false;
    } else if (["erro", "erro_sintaxe", "nao_suportado"].includes(o.tipo)) {
      nota.textContent = o.msg || o.tipo; nota.classList.add("discreta"); nota.hidden = false;
    }
  }

  TL.tipos.grafico = {
    criar, iniciar, pintar,
    pedido: (r) => ({ id: r.id, tipo: "grafico", series: r.series || [], a: r.a, b: r.b, n: r.n, x: r.x, y: linhaDeBase(r) }),
  };

  /** Cria um gráfico em (x, y) e abre a edição da primeira curva. */
  function novoGrafico(x, y) {
    const r = adicionarRegiao({ id: novoId(), tipo: "grafico", series: ["\\sin(x)"], a: "0", b: "10", n: 100, aberto: true,
      x: Math.min(ajustarX(x), 200), y: ajustar(y) });
    agendar(0, false);
    return r;
  }

  const tecla = document.createElement("button");
  tecla.className = "key tecla-grafico";
  tecla.setAttribute("aria-label", "Gráfico X-Y");
  tecla.title = "Gráfico";
  tecla.innerHTML = '<svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 4v16h16"/><path d="M7 15c2-8 4-8 5-4s3 4 5-3" stroke="#C23A14"/></svg>';
  tecla.addEventListener("pointerdown", (ev) => ev.preventDefault());
  tecla.addEventListener("click", () => {
    const f = ultimoCampo?.isConnected ? estado.regioes.find((x) => !x.tipo && campoDe(x.id) === ultimoCampo) : null;
    if (f && !campoDe(f.id).value.trim()) {   // fórmula vazia vira o gráfico, no mesmo lugar
      estado.regioes = estado.regioes.filter((x) => x.id !== f.id);
      elementos.get(f.id)?.remove(); elementos.delete(f.id); ultimoCampo = null;
      return novoGrafico(f.x, f.y);
    }
    if (f) return novoGrafico(f.x, f.y + 72);
    const base = estado.regioes.reduce((m, x) => Math.max(m, x.y + (x.tipo === "grafico" ? 360 : 0)), 0);
    novoGrafico(40, base ? base + 72 : 48);
  });
  document.getElementById("btn-teclado").before(tecla);
}
