"use strict";
// Pinta a resposta do núcleo: estado de cada região, resumo da barra e painel de variáveis.

const markup = (latex) => window.MathLive.convertLatexToMarkup(latex);
const escapar = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

function pintar(o) {
  const el = elementos.get(o.id);
  if (!el) return;
  const r = estado.regioes.find((x) => x.id === o.id);
  const tipo = TL.tipos[r?.tipo];
  if (tipo) { tipo.pintar?.(o, el, r); return; }
  const abaixo = o.abaixo || [];
  const cadeia = o.tipo === "erro" && /^Depende de/.test(o.msg || "");
  el.className = "rg " + (abaixo.length ? "ordem" : cadeia ? "cadeia" : (o.tipo || ""));
  const res = el.querySelector(".resultado");
  const un = el.querySelector(".unidade");
  const falta = el.querySelector(".falta");
  const nota = el.querySelector(".nota");

  // resultado (avaliações): número em vermelhão e a unidade como chip que troca a exibição
  // avaliação, ou definição terminada em "=" (definir e mostrar: R := qL/2 = 36 kN)
  const avaliacao = o.papel === "avaliacao_numerica" || o.papel === "avaliacao_simbolica" || !!o.mostra;
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
  for (const f of TL.pintores) f(o, el, r);
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
  if (motor) t += ` · <span title="Máquina que está calculando">${escapar(motor)}</span>`;
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
      else if (o.forma) valor = o.forma[1] === 1 ? `vetor · ${o.forma[0]}` : `matriz ${o.forma[0]}×${o.forma[1]}`;
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

