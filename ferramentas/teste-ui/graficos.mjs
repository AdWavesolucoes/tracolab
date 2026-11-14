// Teste de interface do gráfico X-Y (ADR 0016): criar pela tecla, curva em SVG com eixos e unidades,
// pendência com chip "falta q", gráfico completo ao definir q, e o trace (tocar mostra x, y).
//
//   cd ferramentas/teste-ui && CHROME=/caminho/do/chrome node graficos.mjs [url] [print.png]

import { chromium } from "playwright-core";
import { entrar, semear } from "./sessao.mjs";
const URL = process.argv[2] || "http://127.0.0.1:8098/";
const CHROME = process.env.CHROME;
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const ctx = await nav.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: true, isMobile: true });
const p = await ctx.newPage();
const erros = [];
p.on("pageerror", (e) => erros.push(e.message));
p.on("response", (r) => { if (r.status() >= 400) erros.push(r.status() + " " + r.url()); });
let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const esperar = (ms = 900) => p.waitForTimeout(ms);
const info = (id) => p.$eval(`.rg[data-id="${id}"]`, (e) => ({
  cls: e.className,
  curvas: [...e.querySelectorAll(".gr-curva")].map((c) => (c.getAttribute("d") || "").length),
  rotulos: [...e.querySelectorAll(".gr-num text, .gr-eixo")].map((t) => t.textContent),
  leg: e.querySelectorAll(".gr-item").length,
  chips: [...e.querySelectorAll(".chip")].map((c) => c.textContent),
  vazio: !e.querySelector(".gr-vazio").hidden,
  trace: e.dataset.trace || "",
}));

await entrar(ctx, URL);
await p.goto(URL);
await semear(p, { nome: "Gráficos", modo: "parcial", ordem: false, regioes: [] }); await esperar(600);

// 1) criar pela tecla da faixa
await p.locator(".tecla-grafico").tap(); await esperar(1500);
const id = await p.$eval(".rg.grafico", (e) => e.dataset.id);
const g1 = await info(id);
confere("tecla cria o gráfico com a curva em SVG", g1.curvas.length === 1 && g1.curvas[0] > 200 && g1.leg === 1, JSON.stringify(g1));
confere("eixos com números e rótulos x e y", g1.rotulos.includes("x") && g1.rotulos.includes("y") && g1.rotulos.includes("10") && g1.rotulos.includes("0"), JSON.stringify(g1.rotulos));

// 2) trace: tocar no desenho mostra (x, y)
await p.$eval(`.rg[data-id="${id}"] .gr-svg`, (e) => e.scrollIntoView({ block: "center" })); await esperar(200);
const caixa = await p.$eval(`.rg[data-id="${id}"] .gr-svg`, (e) => { const b = e.getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height }; });
await p.touchscreen.tap(caixa.x + caixa.w * 0.5, caixa.y + caixa.h * 0.5); await esperar(300);
const g2 = await info(id);
confere("trace mostra x e y do ponto tocado", /x = [\d,]+/.test(g2.trace) && /y = -?[\d,]+/.test(g2.trace), g2.trace);

// 3) pendência: curva com q, sem q definido
await p.evaluate((id) => { const r = estado.regioes.find((x) => x.id === id); r.series = ["q\\cdot x"]; r.a = "0"; r.b = "4"; montar(); agendar(0); }, id);
await esperar(1500);
const g3 = await info(id);
confere("sem q: gráfico pendente com chip q e nenhuma curva", /parcial/.test(g3.cls) && g3.chips.includes("q") && g3.vazio && g3.curvas.length === 0, JSON.stringify(g3));

// 4) definir q acima: o gráfico aparece, na unidade do eixo
await p.evaluate(() => { const r = { id: "q", latex: "q\\tldef3\\,\\mathrm{N}", unidade: "", x: 40, y: 24 };
  estado.regioes.push(r); criarElemento(r); agendar(0); });
await esperar(1500);
await p.evaluate((id) => { const r = estado.regioes.find((x) => x.id === id); r.series = ["q\\cdot x", "x^2"]; r.a = "0\\,\\mathrm{m}"; r.b = "4\\,\\mathrm{m}"; r.y = 120; montar(); agendar(0); }, id);
await esperar(1800);
const g4 = await info(id);
confere("com q := 3 N: duas curvas, eixo x em m e y em J", g4.curvas.length === 2 && g4.rotulos.includes("x (m)") && g4.rotulos.includes("y (J)") && !g4.chips.length, JSON.stringify(g4));

if (process.argv[3]) await p.screenshot({ path: process.argv[3] });
confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
