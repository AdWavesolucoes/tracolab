// Teste de interface das matrizes (ADR 0010), no tamanho do tablet com toque: teclas da camada
// "Operadores", resultado em matriz com chip de unidade, painel de variáveis e primeiro índice.
//
//   CHROME=/caminho/do/chrome node matrizes.mjs [url]

import { chromium } from "playwright-core";
import { entrar, salva } from "./sessao.mjs";
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
const regiao = (id) => p.$eval(`.rg[data-id="${id}"]`, (e) => ({
  v: e.querySelector("math-field").value, cls: e.className, res: e.querySelector(".resultado").textContent,
  un: e.querySelector(".unidade").hidden ? "" : e.querySelector(".unidade").textContent,
  nota: e.querySelector(".nota").hidden ? "" : e.querySelector(".nota").textContent,
  linhas: e.querySelectorAll(".resultado .ML__mtable .ML__arraycolsep, .resultado .col-align-c, .resultado .ML__array").length,
}));
const resultado = (id) => p.evaluate((id) => ultimaResposta.regioes.find((o) => o.id === id)?.resultado || "", id);
const nova = async (y) => { const id = await p.evaluate((y) => novaRegiao(40, y).id, y); await esperar(300); return id; };
const tecla = async (rotulo) => {
  const k = p.locator(".MLK__keycap:visible", { hasText: new RegExp("^" + rotulo.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "$") }).first();
  await k.tap(); await p.waitForTimeout(150);
};
const operadores = async () => {
  await p.evaluate(() => window.mathVirtualKeyboard.show()); await p.waitForTimeout(300);
  await aba("Operadores");
};
const aba = async (nome) => {
  if (await p.locator(".selected:visible", { hasText: nome }).count()) return;
  await p.locator(".layer-switch:visible", { hasText: nome }).first().tap(); await p.waitForTimeout(250);
};

await entrar(ctx, URL);
await p.goto(URL); await p.evaluate(() => { localStorage.clear(); }); await p.reload(); await esperar(1800);
await p.evaluate(() => { document.getElementById("btn-limpar").click(); }); await esperar(500);

// 1. matriz 2×2 pela tecla, preenchida pelas casas
const m = await nova(48);
await p.keyboard.type("M:="); await operadores(); await tecla("2×2");
for (const d of ["1", "2", "3", "4"]) { await p.keyboard.type(d); await p.keyboard.press("Tab"); }
await esperar(300);
const vm = (await regiao(m)).v;
confere("tecla 2×2 monta a matriz", /\\begin\{pmatrix\}1\s*&\s*2\\\\\s*3\s*&\s*4\\end\{pmatrix\}/.test(vm), vm);

// 2. transposta pela tecla e resultado em matriz
const t = await nova(140);
await p.keyboard.type("M"); await operadores(); await tecla("Mᵀ"); await p.keyboard.type("="); await esperar(1200);
const rt = await resultado(t);
confere("Mᵀ = mostra a matriz transposta", rt === "\\begin{pmatrix}1&3\\\\2&4\\end{pmatrix}" && /numerico/.test((await regiao(t)).cls), rt);

// 3. determinante com barras
const d = await nova(240);
await operadores(); await tecla("|M|"); await p.keyboard.type("M"); await p.keyboard.press("ArrowRight"); await p.keyboard.type("="); await esperar(1200);
const rd = await regiao(d);
confere("|M| = −2", /^[−-]2$/.test(rd.res.trim()), JSON.stringify(rd));

// 4. vetor com unidade: unidade comum vira o chip (a da folha, kN: ADR 0014); trocar para N
const f = await nova(320);
await p.keyboard.type("F:=[1;2]"); await p.keyboard.press("ArrowRight");
await p.locator(".faixa .key", { hasText: "un" }).tap(); await esperar(300);
await aba("Unidades"); await tecla("kN");
const fe = await nova(400);
await p.keyboard.type("F="); await esperar(1300);
let rf = await regiao(fe);
confere("F = vetor em kN com o chip da unidade", /^\(?12/.test(rf.res.replace(/\s|\u200b/g, "")) && /^kN$/.test(rf.un.trim()), JSON.stringify(rf));
await p.evaluate(() => window.mathVirtualKeyboard.hide()); await esperar(300);
await p.locator(`.rg[data-id="${fe}"] .unidade`).tap(); await esperar(300);
await p.locator("#menu-unidade button", { hasText: /^N$/ }).tap(); await esperar(1200);
rf = await regiao(fe);
confere("chip troca a matriz para N", (await resultado(fe)) === "\\begin{pmatrix}1000\\\\2000\\end{pmatrix}\\,\\mathrm{N}" && /N/.test(rf.un), await resultado(fe));

// 5. painel de variáveis resume vetores e matrizes
const vars = await p.$$eval("#lista-variaveis .vr", (els) => els.map((e) => e.textContent.replace(/\s+/g, " ").trim()));
confere("painel: M matriz 2×2 e F vetor · 2", vars.some((v) => /^M\s*matriz 2×2/.test(v)) && vars.some((v) => /^F\s*vetor · 2/.test(v)), JSON.stringify(vars));

// 6. primeiro índice nos ajustes
const e = await nova(480);
await p.keyboard.type("M[1,1]="); await esperar(1200);
confere("origem 0: M[1,1] = 4", /^4$/.test((await regiao(e)).res.trim()), JSON.stringify(await regiao(e)));
await p.evaluate(() => window.mathVirtualKeyboard.hide()); await esperar(200);
await p.locator("#rail-ajustes").tap(); await esperar(300);
await p.selectOption("#sel-origem", "1"); await esperar(1300);
confere("origem 1: M[1,1] = 1", /^1$/.test((await regiao(e)).res.trim()), JSON.stringify(await regiao(e)));
const salvo = await salva(p);
confere("primeiro índice salvo com a folha", salvo.origem === 1);

// 7. teclas + linha / + coluna dentro da matriz
const g = await nova(640);
await operadores(); await tecla("2×2");
for (const d of ["5", "6", "7", "8"]) { await p.keyboard.type(d); await p.keyboard.press("Tab"); }
await p.evaluate((id) => { const c = campoDe(id); c.executeCommand("moveToMathfieldEnd"); c.executeCommand("moveToPreviousChar"); }, g);
await operadores(); await tecla("+ linha"); await tecla("+ coluna"); await esperar(300);
const vg = (await regiao(g)).v;
confere("+ linha e + coluna crescem a matriz para 3×3", (vg.match(/\\\\/g) || []).length === 2 && vg.split("\\\\")[0].split("&").length === 3, vg);
await p.evaluate((id) => remover(id), g); await esperar(300);

// 8. erro de dimensão aparece na região
const x = await nova(560);
await p.keyboard.type("[1;2]+[1;2;3]="); await esperar(1200);
const rx = await regiao(x);
confere("dimensões diferentes: erro com mensagem", /erro/.test(rx.cls) && /dimens/.test(rx.nota), JSON.stringify(rx));

confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
