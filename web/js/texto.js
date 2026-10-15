"use strict";
// Regiões de texto: títulos, subtítulos e parágrafos do memorial. Ficam fora do cálculo (o
// pedido ao núcleo as ignora) e são salvas na folha como HTML mínimo (negrito, itálico, quebra).
// Criar: tecla "T" da faixa, ou digitar " numa fórmula vazia (converte a região em texto).
{
  const ESTILOS = { titulo: "Título", subtitulo: "Subtítulo", paragrafo: "Texto" };

  /** HTML permitido no texto: só negrito, itálico e quebra de linha; o resto vira texto puro. */
  function sanitizar(html) {
    const doc = new DOMParser().parseFromString(`<body>${html || ""}</body>`, "text/html");
    const saida = [];
    const anda = (no) => {
      for (const n of no.childNodes) {
        if (n.nodeType === Node.TEXT_NODE) { saida.push(escapar(n.textContent)); continue; }
        if (n.nodeType !== Node.ELEMENT_NODE) continue;
        const tag = n.tagName.toLowerCase();
        if (tag === "br") { saida.push("<br>"); continue; }
        if (["script", "style", "template", "iframe", "object"].includes(tag)) continue;
        const marca = { b: "b", strong: "b", i: "i", em: "i" }[tag];
        const bloco = ["div", "p", "li", "h1", "h2", "h3"].includes(tag);
        if (bloco && saida.length && saida[saida.length - 1] !== "<br>") saida.push("<br>");
        if (marca) saida.push(`<${marca}>`);
        anda(n);
        if (marca) saida.push(`</${marca}>`);
      }
    };
    anda(doc.body);
    while (saida[saida.length - 1] === "<br>") saida.pop();
    return saida.join("");
  }
  TL.sanitizarTexto = sanitizar;   // usado também ao importar folhas (arquivo.js)

  function criar(r) {
    const el = document.createElement("div");
    el.className = "rg texto";
    el.tabIndex = -1;
    el.innerHTML = `<button class="alca" aria-label="Arrastar texto"></button>
      <div class="txt" contenteditable="true" spellcheck="true" role="textbox" aria-multiline="true" aria-label="Texto"></div>
      <div class="txt-ferr" role="toolbar" aria-label="Formatação do texto">
        ${Object.entries(ESTILOS).map(([k, v]) => `<button data-estilo="${k}">${v}</button>`).join("")}
        <span class="sep" aria-hidden="true"></span>
        <button data-cmd="bold" aria-label="Negrito"><b>N</b></button>
        <button data-cmd="italic" aria-label="Itálico"><i>I</i></button>
      </div>`;
    return el;
  }

  function aplicarEstilo(el, r) {
    for (const k of Object.keys(ESTILOS)) el.classList.toggle("t-" + k, r.estilo === k);
    for (const b of el.querySelectorAll("[data-estilo]")) b.setAttribute("aria-pressed", String(b.dataset.estilo === r.estilo));
  }

  let marcaHist = null;
  function iniciar(el, r) {
    r.estilo = ESTILOS[r.estilo] ? r.estilo : "paragrafo";
    const txt = el.querySelector(".txt");
    txt.innerHTML = sanitizar(r.html);
    aplicarEstilo(el, r);
    txt.addEventListener("input", () => {
      r.html = sanitizar(txt.innerHTML);
      salvar();
      clearTimeout(marcaHist);
      marcaHist = setTimeout(registrar, 600);
    });
    txt.addEventListener("keydown", (ev) => {
      if (ev.key !== "Enter") return;
      ev.preventDefault();
      if (r.estilo === "paragrafo") document.execCommand("insertLineBreak");
      else txt.blur();   // título e subtítulo: Enter conclui
    });
    txt.addEventListener("paste", (ev) => {   // colar só texto puro
      ev.preventDefault();
      document.execCommand("insertText", false, ev.clipboardData?.getData("text/plain") || "");
    });
    el.addEventListener("focusout", () => {
      setTimeout(() => {
        if (el.contains(document.activeElement)) return;
        if (!txt.textContent.trim()) return remover(r.id);   // texto vazio some, como a fórmula vazia
        registrar();
      }, 200);
    });
    for (const b of el.querySelectorAll(".txt-ferr button")) {
      b.addEventListener("pointerdown", (ev) => ev.preventDefault());   // mantém a seleção no texto
      b.addEventListener("click", () => {
        if (b.dataset.estilo) { r.estilo = b.dataset.estilo; aplicarEstilo(el, r); salvar(); registrar(); }
        if (b.dataset.cmd) { document.execCommand(b.dataset.cmd); r.html = sanitizar(txt.innerHTML); salvar(); registrar(); }
      });
    }
  }

  TL.tipos.texto = { criar, iniciar, pedido: () => null };

  function focarTexto(r) {
    requestAnimationFrame(() => {
      const txt = elementos.get(r.id)?.querySelector(".txt");
      if (!txt) return;
      txt.focus();
      const sel = getSelection(); sel.selectAllChildren(txt); sel.collapseToEnd();
    });
  }

  /** Cria uma região de texto em (x, y) e põe o cursor nela. */
  function novoTexto(x, y, estilo = "paragrafo") {
    const r = adicionarRegiao({ id: novoId(), tipo: "texto", estilo, html: "", x: ajustarX(x), y: ajustar(y) });
    focarTexto(r);
    return r;
  }

  /** Troca uma fórmula vazia por um texto no mesmo lugar (um passo só no histórico). */
  function converter(f) {
    estado.regioes = estado.regioes.filter((x) => x.id !== f.id);
    elementos.get(f.id)?.remove();
    elementos.delete(f.id);
    if (ultimoCampo && !ultimoCampo.isConnected) ultimoCampo = null;
    return novoTexto(f.x, f.y);
  }

  const formulaDe = (campo) => campo?.isConnected ? estado.regioes.find((x) => !x.tipo && campoDe(x.id) === campo) : null;

  // tecla "T" na faixa, antes da do teclado
  const tecla = document.createElement("button");
  tecla.className = "key mono tecla-texto";
  tecla.textContent = "T";
  tecla.setAttribute("aria-label", "Texto: título ou parágrafo");
  tecla.addEventListener("pointerdown", (ev) => ev.preventDefault());
  tecla.addEventListener("click", () => {
    const f = formulaDe(ultimoCampo);
    if (f && !campoDe(f.id).value.trim()) return converter(f);
    if (f) return novoTexto(f.x, f.y + 72);
    const maisBaixa = estado.regioes.reduce((m, x) => Math.max(m, x.y), 0);
    novoTexto(40, maisBaixa ? maisBaixa + 72 : 48);
  });
  document.getElementById("btn-teclado").before(tecla);

  // " numa fórmula vazia vira texto (o costume das planilhas de cálculo)
  pagina.addEventListener("keydown", (ev) => {
    if (ev.key !== '"') return;
    const campo = ev.composedPath().find((n) => n.tagName === "MATH-FIELD");
    const f = formulaDe(campo);
    if (!f || campo.value.trim()) return;
    ev.preventDefault(); ev.stopPropagation();
    converter(f);
  }, true);
}
