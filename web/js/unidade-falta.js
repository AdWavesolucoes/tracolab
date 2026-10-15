"use strict";
// Unidade pedida que não combina com o resultado (σ = MPa com F := 60 sem unidade): o núcleo diz o
// que falta e qual número está sem unidade; aqui vira um chip "F em kN" que acrescenta a unidade.
{
  TL.pintores.push((o, el) => {
    el.querySelector(".sug-unidade")?.remove();
    const sugestoes = o.sugestoesUnidade || [];
    if (!sugestoes.length) return;
    const caixa = document.createElement("div");
    caixa.className = "sug-unidade";
    caixa.innerHTML = '<span class="lbl">sem unidade</span>';
    for (const s of sugestoes) {
      const b = document.createElement("button");
      b.className = "chip";
      b.textContent = `${s.nome.replace("_", "")} em ${s.unidade.replace("*", "·").replace("^2", "²").replace("^3", "³")}`;
      b.setAttribute("aria-label", `Acrescentar ${s.unidade} na definição de ${s.nome}`);
      b.addEventListener("click", () => {
        const def = estado.regioes.find((x) => x.id === s.regiao);
        if (!def || /\\mathrm/.test(def.latex)) return;
        def.latex = def.latex + s.latex;
        const campo = campoDe(def.id);
        if (campo) campo.value = def.latex;
        registrar();
        agendar(0);
      });
      caixa.appendChild(b);
    }
    el.querySelector(".linha").after(caixa);
  });
}
