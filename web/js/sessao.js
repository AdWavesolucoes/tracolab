"use strict";
// Sessão (ADR 0019): quem está entrando, pedidos JSON à API, botão da conta na barra e o service
// worker da PWA. Sem sessão, a página vai para conta.html; os outros scripts esperam TL.sessao.

/** Pedido JSON à API. Erro vira Error com .status e .dados; 401 manda para a página de conta. */
TL.api = async function api(metodo, caminho, corpo) {
  const r = await fetch(caminho, {
    method: metodo,
    headers: corpo !== undefined ? { "Content-Type": "application/json" } : {},
    body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
  });
  let dados = null;
  if (r.status !== 204) { try { dados = await r.json(); } catch (_) { /* sem corpo */ } }
  if (r.status === 401) { location.replace("conta.html"); throw Object.assign(new Error("Sessão encerrada."), { status: 401, dados }); }
  if (!r.ok) throw Object.assign(new Error(dados?.erro || `Erro ${r.status}`), { status: r.status, dados });
  return dados;
};

/** A conta de quem está usando ({id, usuario, admin, doisFatores}); rejeita (e redireciona) sem sessão. */
TL.sessao = TL.api("GET", "api/conta").then((c) => {
  const b = document.getElementById("btn-conta");
  b.title = `Conta: ${c.usuario}`;
  b.setAttribute("aria-label", `Conta de ${c.usuario}`);
  TL.usuario = c;
  return c;
});

if ("serviceWorker" in navigator && location.protocol !== "file:") {
  window.addEventListener("load", () => navigator.serviceWorker.register("sw.js").catch(() => { /* sem PWA, a página funciona igual */ }));
}
