// Teste de interface: repete no Chromium, com tela de toque do tamanho do Galaxy Tab S10 Lite
// (1280×800 CSS px), o fluxo de uso da folha. Criado depois do bug em que o texto digitado não
// era lido (o <math-field> era preenchido antes de ser ativado) e a região sumia ao tocar fora.
//
//   ./gradlew :servidor-dev:run   (em outro terminal)
//   cd ferramentas/teste-ui && npm ci && CHROME=/caminho/do/chrome node fluxo.mjs [url] [print.png]

import { chromium } from "playwright-core";
const URL = process.argv[2] || "http://127.0.0.1:8098/";
const CHROME = process.env.CHROME;  // caminho de um Chrome/Chromium local (ex.: o do cache do Playwright)
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const ctx = await nav.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: true, isMobile: true });
const p = await ctx.newPage();
const erros = [];
p.on("pageerror", (e) => erros.push(e.message));
p.on("response", (r) => { if (r.status() >= 400) erros.push(r.status() + " " + r.url()); });

let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const regioes = () => p.$$eval(".rg", (els) => els.map((e) => ({
  id: e.dataset.id, v: e.querySelector("math-field").value, cls: e.className,
  res: e.querySelector(".resultado").textContent, un: e.querySelector(".unidade").hidden ? "" : e.querySelector(".unidade").textContent,
  chips: [...e.querySelectorAll(".chip")].map((c) => c.textContent), nota: e.querySelector(".nota").hidden ? "" : e.querySelector(".nota").textContent,
})));
const tecla = async (rotulo) => {
  const k = p.locator(".MLK__keycap:visible", { hasText: new RegExp("^" + rotulo.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "$") }).first();
  await k.tap(); await p.waitForTimeout(120);
};
const aba = async (nome) => {
  if (await p.locator(".selected:visible", { hasText: nome }).count()) return;
  await p.locator(".layer-switch:visible", { hasText: nome }).first().tap(); await p.waitForTimeout(250);
};
const folha = async () => p.locator("#pagina").boundingBox();
const tocarFolha = async (x, y) => {
  await p.evaluate(() => window.mathVirtualKeyboard.hide()); await p.waitForTimeout(300);
  await p.evaluate((yy) => { document.querySelector(".mesa").scrollTop = Math.max(0, yy - 200); }, y); await p.waitForTimeout(200);
  const b = await folha(); await p.touchscreen.tap(b.x + x, b.y + y); await p.waitForTimeout(450); };
const esperar = (ms = 900) => p.waitForTimeout(ms);
const resumo = () => p.locator("#resumo").textContent();

await p.goto(URL); await p.evaluate(() => localStorage.clear()); await p.reload(); await esperar(1800);

// 1. folha de exemplo: pendente, falta F
let v = await regioes();
confere("exemplo carregado com 4 fórmulas visíveis", v.length === 4 && v.every((x) => x.v.length > 2), JSON.stringify(v.map((x) => x.v)));
const atomos = await p.$$eval("math-field", (mfs) => mfs.map((mf) => mf.shadowRoot.querySelectorAll("[data-atom-id]").length));
confere("editor desenha as fórmulas", atomos.every((n) => n > 2), JSON.stringify(atomos));
const sigmaEq = v.find((x) => x.v === "\\sigma=");
confere("σ = fica pendente com o chip F", sigmaEq && /parcial/.test(sigmaEq.cls) && sigmaEq.chips.includes("F"), JSON.stringify(sigmaEq));
confere("resumo diz o que falta", /falta F/.test(await resumo()), await resumo());

// 2. chip F cria a definição acima de quem usa
const sigmaDef = v.find((x) => x.v.startsWith("\\sigma\\tldef"));
await p.locator(`.rg[data-id="${sigmaDef.id}"] .chip`, { hasText: "F" }).tap(); await esperar(600);
v = await regioes();
const fReg = v.find((x) => x.v.startsWith("F\\tldef"));
confere("tocar em F cria F := com foco", !!fReg && (await p.evaluate(() => document.activeElement?.closest?.(".rg")?.dataset.id)) === fReg?.id, JSON.stringify(v.map((x) => x.v)));

// 3. digitar a carga e a unidade pelo teclado
await p.keyboard.type("31,4159265"); await esperar(200);
await p.locator(".faixa .key", { hasText: "un" }).tap(); await esperar(400);
await aba("Unidades"); await tecla("kN");
await p.keyboard.press("Enter"); await esperar(1200);
v = await regioes();
const sigma = v.find((x) => x.v === "\\sigma=");
confere("o que se digita depois de := fica fora do grupo colorido", (await regioes()).some((x) => x.v === "F\\tldef31{,}4159265\\,\\mathrm{kN}"), JSON.stringify((await regioes()).map((x) => x.v)));
confere("σ = 100 MPa (número e unidade em chip)", sigma && /^100$/.test(sigma.res.trim()) && /MPa/.test(sigma.un) && /numerico/.test(sigma.cls), JSON.stringify(sigma));
confere("resumo: calculado", /calculado/.test(await resumo()), await resumo());

// 4. trocar a unidade pelo chip
await p.locator(`.rg[data-id="${sigma.id}"] .unidade`).tap(); await esperar(300);
await p.locator("#menu-unidade button", { hasText: /^kPa$/ }).tap(); await esperar(1000);
v = await regioes();
const s2 = v.find((x) => x.id === sigma.id);
confere("trocar para kPa: 100000 kPa", /100\s?000/.test(s2.res.replace(/\s/g, "")) && /kPa/.test(s2.un), JSON.stringify(s2));

// 5. variáveis no painel
const vars = await p.$$eval("#lista-variaveis .vr", (els) => els.map((e) => e.textContent.replace(/\s+/g, " ").trim()));
confere("painel lista d, F, A e σ", ["d", "F", "A", "σ"].every((n) => vars.some((t) => t.startsWith(n))), JSON.stringify(vars));

// 6. erro de ordem: usar W no topo e definir W embaixo
await tocarFolha(420, 560); await p.keyboard.type("W:=2"); await p.keyboard.press("Enter"); await esperar(500);
await tocarFolha(420, 36); await p.keyboard.type("y:=W"); await esperar(1200);
v = await regioes();
const yReg = v.find((x) => x.v.startsWith("y"));
confere("y := W acima de W é erro de ordem com a mensagem", yReg && /ordem/.test(yReg.cls) && /mais abaixo/.test(yReg.nota), JSON.stringify(yReg));

// 7. tocar fora: nada escrito some; vazias somem
await tocarFolha(700, 1000); await esperar(900);
v = await regioes();
confere("tocar fora não apaga fórmulas escritas", v.some((x) => x.v.includes("\\mathrm{kN}")) && v.some((x) => x.v.startsWith("W")), JSON.stringify(v.map((x) => x.v)));
confere("no máximo a região recém-criada fica vazia", v.filter((x) => !x.v.trim()).length <= 1);
const salvo = JSON.parse(await p.evaluate(() => localStorage.getItem("tracolab.folha.v2")));
confere("folha salva no aparelho", salvo.regioes.some((r) => r.latex.includes("\\mathrm{kN}")) && salvo.regioes.some((r) => r.unidade === "\\,\\mathrm{kPa}"));

if (process.argv[3]) await p.screenshot({ path: process.argv[3] });
confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
