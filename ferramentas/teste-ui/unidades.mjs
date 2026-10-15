// Teste de interface das unidades da folha e do formato dos números (ADR 0014), no tamanho do
// Galaxy Tab S10 Lite (1280×800, toque).
//
//   cd ferramentas/teste-ui && CHROME=/caminho/do/chrome node unidades.mjs [url]

import { chromium } from "playwright-core";
const URL = process.argv[2] || "http://127.0.0.1:8098/";
const CHROME = process.env.CHROME;
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const ctx = await nav.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: true, isMobile: true });
const p = await ctx.newPage();
const erros = [];
p.on("pageerror", (e) => erros.push(e.message));
let resposta = null, pedido = null;
p.on("request", (r) => { if (r.url().endsWith("api/calcular")) pedido = JSON.parse(r.postData() || "{}"); });
p.on("response", async (r) => {
  if (r.status() >= 400) erros.push(r.status() + " " + r.url());
  if (r.url().endsWith("api/calcular") && r.ok()) { try { resposta = await r.json(); } catch (_) { /* corpo já lido */ } }
});

let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const esperar = (ms = 900) => p.waitForTimeout(ms);
const regiao = (latex) => p.evaluate((l) => {
  const el = [...document.querySelectorAll(".rg")].find((e) => e.querySelector("math-field")?.value === l);
  return el && { id: el.dataset.id, cls: el.className, res: el.querySelector(".resultado").textContent, un: el.querySelector(".unidade").hidden ? "" : el.querySelector(".unidade").textContent };
}, latex);
const doNucleo = (id) => resposta?.regioes?.find((o) => o.id === id);
const tocarFolha = async (x, y) => {
  await p.evaluate(() => window.mathVirtualKeyboard.hide()); await p.waitForTimeout(300);
  await p.evaluate((yy) => { document.querySelector(".mesa").scrollTop = Math.max(0, yy - 200); }, y); await p.waitForTimeout(200);
  const b = await p.locator("#pagina").boundingBox(); await p.touchscreen.tap(b.x + x, b.y + y); await p.waitForTimeout(450);
};
// região criada com o LaTeX pronto (o que o editor produziria), sem passar pela digitação
const criar = async (latex, y) => { await p.evaluate(([l, yy]) => { novaRegiao(420, yy, l, false); agendar(0); }, [latex, y]); await esperar(1100); };

await p.goto(URL); await p.evaluate(() => localStorage.clear()); await p.reload(); await esperar(1800);

// 1. painel de variáveis nas unidades da folha: d como foi digitado, A em mm²
const vars = await p.$$eval("#lista-variaveis .vr", (els) => els.map((e) => e.textContent.replace(/\s+/g, "")));
confere("painel: d = 20 mm (como foi digitado, não 0,02 m)", vars.some((t) => /^d20mm/.test(t)), JSON.stringify(vars));
confere("painel: A em mm² (314,159)", vars.some((t) => /^A314,159mm2/.test(t)), JSON.stringify(vars));

// 2. pendente nas unidades da folha: σ := F/A mostra F/(314,159 mm²)
const def = await p.evaluate(() => estado.regioes.find((r) => r.latex.startsWith("\\sigma\\tldef")).id);
const sDef = doNucleo(def);
confere("pendente sai como F/(314,159 mm²)", /\\frac\{F\}\{314\{,\}159\\,\\mathrm\{mm\}\^\{2\}\}/.test(sDef?.resultado || ""), sDef?.resultado);

// 3. momento com kN e m, sem unidade pedida: kN·m
await criar("Q\\tldef12\\,\\mathrm{kN}", 560);
await criar("L\\tldef6\\,\\mathrm{m}", 640);
await criar("Q\\cdot L=", 720);
let m = await regiao("Q\\cdot L=");
confere("Q·L = 72 kN·m (não 72000 J)", m && /^72$/.test(m.res.trim()) && /kN·m|kN⋅m/.test(m.un), JSON.stringify(m));

// 4. formato da folha: 2 casas decimais
await criar("\\frac{2}{3}=", 800);
let t = await regiao("\\frac{2}{3}=");
confere("2/3 = 0,666667 (padrão: 6 algarismos)", t && t.res.replace(/\s/g, "") === "0,666667", JSON.stringify(t));
await p.locator("#rail-ajustes").tap(); await esperar(300);
await p.selectOption("#fmt-precisao", "c2"); await esperar(1200);
t = await regiao("\\frac{2}{3}=");
confere("Ajustes → 2 casas: 0,67", t && t.res.replace(/\s/g, "") === "0,67", JSON.stringify(t));
confere("o pedido leva opcoes.formato", pedido?.opcoes?.formato?.casas === 2, JSON.stringify(pedido?.opcoes));
await p.selectOption("#fmt-precisao", "a6"); await esperar(1000);

// 5. formato de um resultado só: toque no número
await p.evaluate(() => window.mathVirtualKeyboard.hide());
const idFr = await p.evaluate(() => estado.regioes.find((r) => r.latex === "\\frac{2}{3}=").id);
await p.locator(`.rg[data-id="${idFr}"] .resultado`).tap(); await esperar(400);
confere("tocar no número abre o formato do resultado", await p.locator("#menu-formato").isVisible());
await p.locator("#menu-formato button", { hasText: /^4 casas$/ }).tap(); await esperar(1100);
t = await regiao("\\frac{2}{3}=");
confere("só este resultado com 4 casas: 0,6667", t && t.res.replace(/\s/g, "") === "0,6667" && /formato-proprio/.test(t.cls), JSON.stringify(t));
const salvo = JSON.parse(await p.evaluate(() => localStorage.getItem("tracolab.folha.v2")));
confere("formato do resultado salvo na folha", salvo.regioes.some((r) => r.formato?.casas === 4));

// 6. separador de milhar (núcleo manda \: entre os grupos)
await criar("123456=", 880);
const idMil = await p.evaluate(() => estado.regioes.find((r) => r.latex === "123456=").id);
confere("123456 = 123 456 (espaço entre milhares)", /^123\\:456$/.test(doNucleo(idMil)?.resultado || ""), doNucleo(idMil)?.resultado);

// 7. temperatura digitada com a tecla de grau
await tocarFolha(420, 960);
await p.keyboard.type("T:=20");
await p.locator(".faixa .key", { hasText: "°" }).tap(); await esperar(200);
await p.keyboard.type("C"); await p.keyboard.press("Enter"); await esperar(1000);
await p.keyboard.type("T="); await p.keyboard.press("Enter"); await esperar(1200);
t = await regiao("T=");
confere("T := 20°C → T = 20 °C", t && /^20$/.test(t.res.trim()) && /°C/.test(t.un), JSON.stringify(t));

// 8. menu do chip com as unidades novas
const idT = await p.evaluate(() => estado.regioes.find((r) => r.latex === "T=").id);
await p.locator(`.rg[data-id="${idT}"] .unidade`).tap(); await esperar(300);
const opcoes = await p.$$eval("#menu-unidade button", (bs) => bs.map((b) => b.textContent));
confere("menu de unidades tem tf, kN/m², kgf/cm², °F", ["tf", "kN/m²", "kgf/cm²", "°F"].every((u) => opcoes.includes(u)), JSON.stringify(opcoes));
await p.locator("#menu-unidade button", { hasText: /^°F$/ }).tap(); await esperar(1100);
t = await regiao("T=");
confere("trocar para °F: 68 °F", t && /^68$/.test(t.res.trim()) && /°F/.test(t.un), JSON.stringify(t));

confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
