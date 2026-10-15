// Teste de interface das verificações, da lógica e dos textos (ADR 0013), no tamanho do Galaxy
// Tab S10 Lite com toque: selo atende/não atende com a utilização, verificação pendente com chip,
// texto e por partes no resultado, e as teclas da camada "Operadores".
//
//   cd ferramentas/teste-ui && CHROME=/caminho/do/chrome node verificacoes.mjs [url] [print.png]

import { chromium } from "playwright-core";
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
  cls: e.className, res: e.querySelector(".resultado").textContent.replace(/\s+/g, " ").trim(),
  selo: e.querySelector(".verif")?.textContent.replace(/\s+/g, " ").trim() || "",
  aria: e.querySelector(".verif")?.getAttribute("aria-label") || "",
  barra: e.querySelector(".barra-util > span")?.style.width || "",
  chips: [...e.querySelectorAll(".chip")].map((c) => c.textContent),
  casos: !!e.querySelector(".resultado .ML__array, .resultado table, .resultado [class*=array]"),
}));

const R = (id, latex, y, x = 40) => ({ id, latex, unidade: "", x, y });
const folha = { nome: "Verificações", modo: "parcial", ordem: false, regioes: [
  R("s", "\\sigma\\tldef150\\,\\mathrm{MPa}", 48), R("d", "\\sigma_{adm}\\tldef250\\,\\mathrm{MPa}", 120),
  R("v1", "\\sigma\\le\\sigma_{adm}=", 192), R("v2", "\\sigma\\ge\\sigma_{adm}=", 264), R("v3", "\\sigma\\le\\tau=", 336),
  R("t1", "m\\tldef\\text{não atende}", 408), R("t2", "m=", 480),
  R("p1", "y\\tldef\\operatorname{se}\\left(x>0,1,2\\right)", 552), R("p2", "y=", 624),
] };
await p.goto(URL);
await p.evaluate((f) => { localStorage.clear(); localStorage.setItem("tracolab.folha.v2", JSON.stringify(f)); }, folha);
await p.reload(); await esperar(2000);

const v1 = await regiao("v1");
confere("σ ≤ σ_adm: selo atende e 60%", /verif-ok/.test(v1.cls) && /atende/.test(v1.selo) && !/não/.test(v1.selo) && /60\s?%/.test(v1.selo), JSON.stringify(v1));
confere("os dois lados na unidade da folha no lugar do 1", /150/.test(v1.res) && /250/.test(v1.res) && /MPa/.test(v1.res) && v1.res !== "1", JSON.stringify(v1));
confere("barra de utilização em 60%", parseFloat(v1.barra) === 60, v1.barra);
confere("leitura acessível do selo", /Atende, utilização de 60\s?%/.test(v1.aria), v1.aria);
const v2 = await regiao("v2");
confere("σ ≥ σ_adm: não atende, utilização acima de 100% em destaque", /verif-falha/.test(v2.cls) && /não atende/.test(v2.selo) && /166,7\s?%/.test(v2.selo), JSON.stringify(v2));
const v3 = await regiao("v3");
confere("verificação pendente: a verificar, com chip τ", /parcial/.test(v3.cls) && /a verificar/.test(v3.selo) && v3.chips.includes("τ"), JSON.stringify(v3));
const t2 = await regiao("t2");
confere("texto no resultado (não atende)", /não atende/.test(t2.res) && !t2.selo, JSON.stringify(t2));
const p2 = await regiao("p2");
confere("se pendente: por partes desenhado, com chip x", p2.casos && p2.chips.includes("x") && /senão/.test(p2.res), JSON.stringify(p2));

// completar a verificação pendente: τ := 100 MPa acima dela → não atende, 150%
await p.evaluate(() => { const r = { id: "tau", latex: "\\tau\\tldef100\\,\\mathrm{MPa}", unidade: "", x: 420, y: 120 };
  estado.regioes.push(r); criarElemento(r); agendar(0); });
await esperar(1200);
const v3b = await regiao("v3");
confere("definir τ completa a verificação: não atende, 150%", /verif-falha/.test(v3b.cls) && /150\s?%/.test(v3b.selo) && !v3b.chips.length, JSON.stringify(v3b));

// teclas da camada Operadores: se, ∧ e o modo texto
await p.locator(`.rg[data-id="t2"] math-field`).tap(); await esperar(300);
await p.evaluate(() => { const mf = document.querySelector('.rg[data-id="t2"] math-field'); mf.value = ""; });
await p.evaluate(() => window.mathVirtualKeyboard.show()); await esperar(500);
await p.locator(".layer-switch:visible", { hasText: "Operadores" }).first().tap(); await esperar(300);
const rotulos = await p.$$eval(".MLK__keycap:visible", (els) => els.map((e) => e.textContent.trim()));
confere("camada Operadores tem se, por partes, ∧, ∨, ¬ e texto", ["se", "∧", "∨", "¬", "texto"].every((k) => rotulos.includes(k)) && rotulos.some((k) => /por partes/.test(k)), JSON.stringify(rotulos));
await p.locator(".MLK__keycap:visible", { hasText: /^texto$/ }).first().tap(); await esperar(200);
await p.keyboard.type("ok sim"); await esperar(200);
await p.locator(".MLK__keycap:visible", { hasText: /^fórmula$/ }).first().tap(); await esperar(200);
const val = await p.evaluate(() => document.querySelector('.rg[data-id="t2"] math-field').value);
confere("tecla texto escreve \\text{ok sim} e fórmula volta ao modo de fórmula", val === "\\text{ok sim}", val);
await p.evaluate(() => { const mf = document.querySelector('.rg[data-id="t2"] math-field'); mf.value = "m="; mf.dispatchEvent(new Event("input")); });
await p.evaluate(() => window.mathVirtualKeyboard.hide()); await esperar(1000);

if (process.argv[3]) await p.screenshot({ path: process.argv[3] });
confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
