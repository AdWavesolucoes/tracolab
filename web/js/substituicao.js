"use strict";
// Substituição explícita (memorial de cálculo): numa região que mostra valor, o botão "x=2" liga
// a fórmula com os valores no lugar dos nomes: R := qL/2 = (12 kN/m · 6 m)/2 = 36 kN.
// O núcleo monta o LaTeX (campo `substituicao` da resposta, pedido com `substituir: true`).
{
  // "σ =" ou "σ = {MPa}": a substituição seria o próprio resultado
  const SO_NOME = /^[\p{L}\p{N}_]+\s*=(\s*\{[^}]*\})?\s*$/u;

  function botao(el, r) {
    let b = el.querySelector(".subst");
    if (b) return b;
    b = document.createElement("button");
    b.className = "subst";
    b.textContent = "x=2";
    b.addEventListener("pointerdown", (ev) => ev.preventDefault());   // não tira o foco da fórmula
    b.addEventListener("click", () => {
      r.substituir = !r.substituir;
      registrar();
      agendar(0);
    });
    el.appendChild(b);
    return b;
  }

  TL.pintores.push((o, el, r) => {
    if (!r) return;
    const mostraValor = o.papel === "avaliacao_numerica" || o.papel === "avaliacao_simbolica" || !!o.mostra;
    const pode = mostraValor && !SO_NOME.test(o.texto || "") && (o.tipo === "numerico" || o.tipo === "parcial");
    const b = botao(el, r);
    b.hidden = !pode && !r.substituir;
    b.setAttribute("aria-pressed", String(!!r.substituir));
    b.setAttribute("aria-label", r.substituir ? "Esconder os valores substituídos" : "Mostrar a fórmula com os valores");
    let s = el.querySelector(".substituicao");
    if (!s) {
      s = document.createElement("span");
      s.className = "substituicao";
      el.querySelector(".resultado").before(s);
    }
    const mostra = r.substituir && pode && o.substituicao;
    s.innerHTML = mostra ? markup(o.substituicao + "=") : "";
    el.classList.toggle("com-substituicao", !!mostra);
  });
}
