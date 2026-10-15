"use strict";
// Unidades e formato dos números (ADR 0014): a seção "Números" dos ajustes (vale para a folha) e
// o formato de um resultado só, escolhido tocando no número. O núcleo formata; aqui só se escolhe.
//   estado.formato  { algarismos | casas, limite, engenharia, milhar, zeros, unidades }
//   r.formato       o mesmo, por cima do da folha, só naquele resultado
{
  TL.opcoes.push(() => {
    const formatos = {};
    for (const r of estado.regioes) if (r.formato && Object.keys(r.formato).length) formatos[r.id] = r.formato;
    return { formato: estado.formato || {}, formatos };
  });

  // ------------------------------------------------------------------ ajustes da folha

  const PRECISOES = [
    ["a6", "6 algarismos significativos"], ["a3", "3 algarismos significativos"], ["a4", "4 algarismos significativos"],
    ["a5", "5 algarismos significativos"], ["a8", "8 algarismos significativos"],
    ["c0", "0 casas decimais"], ["c1", "1 casa decimal"], ["c2", "2 casas decimais"], ["c3", "3 casas decimais"], ["c4", "4 casas decimais"],
  ];
  const LIMITES = [["3", "10³"], ["4", "10⁴"], ["6", "10⁶"], ["9", "10⁹"], ["12", "10¹²"]];

  const secao = document.createElement("div");
  secao.className = "ajustes-numeros";
  secao.innerHTML = `
    <span class="lbl">Números e unidades</span>
    <label class="campo-ajuste">Unidades dos resultados
      <select id="fmt-unidades">
        <option value="folha">As usadas na folha (20 mm, kN·m, MPa)</option>
        <option value="si">SI (0,02 m, N·m, Pa)</option>
      </select>
    </label>
    <label class="campo-ajuste">Precisão
      <select id="fmt-precisao">${PRECISOES.map(([v, t]) => `<option value="${v}">${t}</option>`).join("")}</select>
    </label>
    <label class="campo-ajuste">Notação exponencial a partir de
      <select id="fmt-limite">${LIMITES.map(([v, t]) => `<option value="${v}">${t}</option>`).join("")}</select>
    </label>
    <label class="campo-check"><input type="checkbox" id="fmt-engenharia"> Notação de engenharia (12,5×10³)</label>
    <label class="campo-check"><input type="checkbox" id="fmt-milhar"> Separar milhares (12 345)</label>
    <label class="campo-check"><input type="checkbox" id="fmt-zeros"> Zeros à direita (2,50)</label>
    <p class="dica">Toque num resultado para mudar só o formato dele.</p>`;
  document.getElementById("btn-limpar").before(secao);
  const $ = (id) => document.getElementById(id);

  /** Precisão como texto do seletor: "a6" (algarismos) ou "c2" (casas). */
  const precisao = (f) => (f && f.casas != null ? "c" + f.casas : "a" + ((f && f.algarismos) || 6));
  const aplicarPrecisao = (f, v) => {
    delete f.casas; delete f.algarismos;
    if (v[0] === "c") f.casas = Number(v.slice(1)); else if (v !== "a6") f.algarismos = Number(v.slice(1));
  };

  function mostrarAjustes() {
    const f = estado.formato || {};
    $("fmt-unidades").value = f.unidades || "folha";
    $("fmt-precisao").value = precisao(f);
    $("fmt-limite").value = String(f.limite || 6);
    $("fmt-engenharia").checked = !!f.engenharia;
    $("fmt-milhar").checked = f.milhar !== false;
    $("fmt-zeros").checked = !!f.zeros;
  }

  function lerAjustes() {
    const f = {};
    if ($("fmt-unidades").value !== "folha") f.unidades = $("fmt-unidades").value;
    aplicarPrecisao(f, $("fmt-precisao").value);
    if ($("fmt-limite").value !== "6") f.limite = Number($("fmt-limite").value);
    if ($("fmt-engenharia").checked) f.engenharia = true;
    if (!$("fmt-milhar").checked) f.milhar = false;
    if ($("fmt-zeros").checked) f.zeros = true;
    estado.formato = f;
    agendar(0);
  }
  for (const id of ["fmt-unidades", "fmt-precisao", "fmt-limite", "fmt-engenharia", "fmt-milhar", "fmt-zeros"])
    $(id).addEventListener("change", lerAjustes);
  mostrarAjustes();
  // outra folha aberta (ou importada): os controles mostram o formato dela
  TL.aposCalculo.push(mostrarAjustes);

  // ------------------------------------------------------------------ formato de um resultado

  const menu = document.createElement("div");
  menu.id = "menu-formato";
  menu.className = "menu-unidade";
  menu.hidden = true;
  menu.setAttribute("role", "dialog");
  menu.setAttribute("aria-label", "Formato deste resultado");
  document.body.appendChild(menu);

  const OPCOES = [["", "Igual à folha"], ["c0", "0 casas"], ["c1", "1 casa"], ["c2", "2 casas"], ["c3", "3 casas"], ["c4", "4 casas"],
    ["a3", "3 sig."], ["a4", "4 sig."], ["a6", "6 sig."], ["a8", "8 sig."]];

  function abrir(r, alvo) {
    window.mathVirtualKeyboard.hide();
    const atual = r.formato && Object.keys(r.formato).length ? precisao(r.formato) : "";
    menu.innerHTML = `<span class="lbl">Formato deste resultado</span>`;
    const g = document.createElement("div");
    g.className = "grupo";
    for (const [v, t] of OPCOES) {
      const b = document.createElement("button");
      b.textContent = t;
      b.dataset.formato = v;
      if (v === atual) b.className = "sel";
      b.addEventListener("click", () => escolher(r, v));
      g.appendChild(b);
    }
    menu.appendChild(g);
    const eng = document.createElement("button");
    eng.className = "alterna" + (r.formato?.engenharia ? " sel" : "");
    eng.textContent = "Notação de engenharia";
    eng.addEventListener("click", () => { r.formato = { ...(r.formato || {}), engenharia: !r.formato?.engenharia }; fechar(true); });
    menu.appendChild(eng);
    const c = alvo.getBoundingClientRect();
    menu.style.left = Math.max(8, Math.min(window.innerWidth - 332, c.left)) + "px";
    menu.style.top = Math.max(8, Math.min(window.innerHeight - 240, c.bottom + 6)) + "px";
    menu.hidden = false;
  }

  function escolher(r, v) {
    if (!v) delete r.formato;
    else { const f = { ...(r.formato || {}) }; aplicarPrecisao(f, v); if (v === "a6") f.algarismos = 6; r.formato = f; }
    fechar(true);
  }

  function fechar(mudou) {
    menu.hidden = true;
    if (mudou) { registrar(); agendar(0); }
  }

  // toque no número do resultado (não no chip de unidade, que troca a unidade)
  document.getElementById("pagina").addEventListener("click", (ev) => {
    const res = ev.target.closest?.(".resultado");
    if (!res || !res.textContent.trim()) return;
    const el = res.closest(".rg");
    const r = estado.regioes.find((x) => x.id === el?.dataset.id);
    if (r) { ev.stopPropagation(); abrir(r, res); }
  });
  document.addEventListener("pointerdown", (ev) => {
    if (!menu.hidden && !menu.contains(ev.target) && !ev.target.closest?.(".resultado")) fechar(false);
  });

  // marca discreta nos resultados com formato próprio
  TL.pintores.push((o, el, r) => el.classList.toggle("formato-proprio", !!(r?.formato && Object.keys(r.formato).length)));
}
