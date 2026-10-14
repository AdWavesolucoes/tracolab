"use strict";
// Páginas A4: a folha cresce de página em página conforme as regiões descem (com a quebra
// marcada na tela) e, na impressão/PDF, nenhuma região fica cortada entre duas páginas: a que
// cruzaria a quebra desce para a página seguinte, levando junto o que vem abaixo dela.
{
  const H = 297 * 96 / 25.4;   // altura de uma A4 em px CSS (1122,5)
  const FOLGA = 240;            // espaço livre abaixo da última região, para tocar e criar outra
  const MARGEM = 36;            // onde começa a região empurrada para a página seguinte

  function marcas(classe, n, monta) {
    const atuais = [...pagina.querySelectorAll(":scope > ." + classe)];
    while (atuais.length > n) atuais.pop().remove();
    for (let k = 0; k < n; k++) {
      let el = atuais[k];
      if (!el) { el = document.createElement("div"); el.className = classe; el.setAttribute("aria-hidden", "true"); pagina.appendChild(el); }
      monta(el, k);
    }
  }

  function diagramar() {
    const caixas = [];
    let fundo = 0;
    for (const r of estado.regioes) {
      const el = elementos.get(r.id);
      if (!el) continue;
      caixas.push({ el, topo: el.offsetTop, alt: el.offsetHeight });
      fundo = Math.max(fundo, el.offsetTop + el.offsetHeight);
    }
    // tela: páginas inteiras, sempre com folga abaixo da última região
    const paginas = Math.max(1, Math.ceil((fundo + FOLGA) / H));
    pagina.style.minHeight = paginas * H + "px";
    marcas("quebra-pagina", paginas - 1, (el, k) => {
      el.style.top = (k + 1) * H + "px";
      el.innerHTML = `<span>página ${k + 2}</span>`;
    });
    // impressão: desce a região que cruzaria a quebra (e tudo o que vem depois dela)
    caixas.sort((a, b) => a.topo - b.topo);
    let desloc = 0, total = 1;
    for (const c of caixas) {
      let t = c.topo + desloc;
      const p = Math.floor(t / H);
      if (c.alt < H - 2 * MARGEM && Math.floor((t + c.alt - 1) / H) > p) {
        const novo = (p + 1) * H + MARGEM;
        desloc += novo - t;
        t = novo;
      }
      c.el.style.setProperty("--y-impressao", t + "px");
      total = Math.max(total, Math.floor((t + c.alt - 1) / H) + 1);
    }
    pagina.style.setProperty("--paginas-impressao", total);
    marcas("rodape-impressao", total, (el, k) => {
      el.style.top = (k + 1) * H - 26 + "px";
      el.innerHTML = `<span>${escapar(estado.nome)}</span><span>página ${k + 1} de ${total}</span>`;
    });
  }

  let pendente = null;
  const agendarDiagrama = () => { cancelAnimationFrame(pendente); pendente = requestAnimationFrame(diagramar); };
  TL.aposCalculo.push(agendarDiagrama);
  pagina.addEventListener("input", agendarDiagrama);   // textos crescem sem passar pelo núcleo
  window.addEventListener("beforeprint", () => { document.activeElement?.blur?.(); diagramar(); });
  TL.diagramar = diagramar;
}
