// Teste de interface: repete no Chromium, com tela de toque do tamanho do Galaxy Tab S10 Lite
// (2112×1320), o fluxo de uso da página. Criado depois do bug em que o texto digitado não era
// lido (o <math-field> era preenchido antes de ser ativado) e a região sumia ao tocar fora.
//
//   ./gradlew :servidor-dev:run   (em outro terminal)
//   cd ferramentas/teste-ui && npm ci && CHROME=/caminho/do/chrome node fluxo.mjs [url] [print.png]

import { chromium } from "playwright-core";
const URL = process.argv[2] || "http://127.0.0.1:8098/";
const CHROME = process.env.CHROME;  // caminho de um Chrome/Chromium local (ex.: o do cache do Playwright)
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const ctx = await nav.newContext({ viewport: { width: 2112, height: 1320 }, hasTouch: true, isMobile: true });
const p = await ctx.newPage();
const erros = []; p.on("pageerror", e => erros.push(e.message)); p.on("response", r => { if (r.status() >= 400) erros.push(r.status() + " " + r.url()); });
let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const valores = () => p.$$eval(".regiao", els => els.map(e => ({ id: e.dataset.id, v: e.querySelector("math-field").value, cls: e.className, res: e.querySelector(".resultado").textContent, info: e.querySelector(".info").textContent })));
const tecla = async (rotulo) => { const k = p.locator(".MLK__keycap", { hasText: new RegExp("^" + rotulo.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "$") }).first(); await k.tap(); await p.waitForTimeout(120); };
const aba = async (nome) => { if (await p.locator(".selected:visible", { hasText: nome }).count()) return; await p.locator(".layer-switch:visible", { hasText: nome }).first().tap(); await p.waitForTimeout(250); };

await p.goto(URL); await p.evaluate(() => localStorage.clear()); await p.reload(); await p.waitForTimeout(1800);
let v = await valores();
confere("exemplo carregado com 3 fórmulas visíveis", v.length === 3 && v.every(x => x.v.length > 3), JSON.stringify(v.map(x => x.v)));
const atomos = await p.$$eval("math-field", mfs => mfs.map(mf => mf.shadowRoot.querySelectorAll("[data-atom-id]").length));
confere("editor desenha as fórmulas (átomos no shadow DOM)", atomos.every(n => n > 3), JSON.stringify(atomos));

// região nova acima de tudo: d := 20 mm (digitando + tecla de unidade)
await p.touchscreen.tap(400, 100); await p.waitForTimeout(500);
await p.keyboard.type("d:=20"); await p.waitForTimeout(200);
await aba("Unid."); await tecla("mm");
v = await valores(); const d = v[v.length - 1];
confere("o que se digita entra no campo", d.v.includes("20") && d.v.includes("\\mathrm{mm}"), d.v);
await p.keyboard.press("Enter"); await p.waitForTimeout(500);
v = await valores();
confere("Enter mantém a fórmula e abre região abaixo", v.find(x => x.id === d.id)?.v === d.v && v.length === 5, JSON.stringify(v.map(x => x.v)));
const foco = await p.evaluate(() => document.activeElement?.closest?.(".regiao")?.dataset.id);
confere("foco vai para a região nova", foco && foco !== d.id, foco);

// F na região aberta pelo Enter
await p.keyboard.type("F:=31,4159265"); await aba("Unid."); await tecla("kN");
await p.touchscreen.tap(1500, 900); await p.waitForTimeout(1200);
v = await valores();
confere("tocar fora NÃO apaga fórmulas escritas", v.some(x => x.v.includes("\\mathrm{mm}")) && v.some(x => x.v.includes("\\mathrm{kN}")), JSON.stringify(v.map(x => x.v)));
const vazias = v.filter(x => !x.v.trim()).length;
confere("região vazia criada pelo toque some ao perder o foco", vazias <= 1, "vazias=" + vazias);
const sigma = v.find(x => x.v.startsWith("\\sigma="));
confere("σ = 100 MPa com os dados acima", sigma && /100/.test(sigma.res) && /MPa/.test(sigma.res), JSON.stringify(sigma));
confere("σ deixou de ser parcial", sigma && !/parcial/.test(sigma.cls), sigma?.cls);
const salvo = JSON.parse(await p.evaluate(() => localStorage.getItem("tracolab.pagina.v1")));
confere("página salva no aparelho com o que foi digitado", salvo.regioes.some(r => r.latex.includes("\\mathrm{kN}")));
if (process.argv[3]) await p.screenshot({ path: process.argv[3] });
confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
