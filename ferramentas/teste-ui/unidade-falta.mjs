// Relato de 15/10: F := 60 (sem unidade) deixava σ = MPa "incompatível" sem dizer o porquê.
// Agora a região explica o que falta e o chip "F em kN" acrescenta a unidade na definição de F.
//   CHROME=/caminho/do/chrome node unidade-falta.mjs [url]
import { chromium } from "playwright-core";
const URL = process.argv[2] || "http://127.0.0.1:8098/";
const nav = await chromium.launch({ executablePath: process.env.CHROME, args: ["--no-sandbox"] });
const p = await (await nav.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: true, isMobile: true })).newPage();
const erros = []; p.on("pageerror", (e) => erros.push(e.message));
let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const regiao = (inicio) => p.$$eval(".rg", (els, ini) => els.map((e) => ({ v: e.querySelector("math-field")?.value || "", nota: e.querySelector(".nota")?.hidden ? "" : e.querySelector(".nota")?.textContent || "",
  res: e.querySelector(".resultado")?.textContent || "", un: e.querySelector(".unidade")?.hidden ? "" : e.querySelector(".unidade")?.textContent || "",
  sug: [...e.querySelectorAll(".sug-unidade .chip")].map((c) => c.textContent) })).find((x) => x.v.startsWith(ini)), inicio);

await p.goto(URL); await p.evaluate(() => localStorage.clear()); await p.reload(); await p.waitForTimeout(1800);
// folha de exemplo: σ := F/A pede F; o chip cria F := e digita 60 sem unidade
await p.locator(".rg .chip", { hasText: "F" }).first().tap(); await p.waitForTimeout(500);
await p.keyboard.type("60"); await p.keyboard.press("Enter"); await p.waitForTimeout(1500);
let s = await regiao("\\sigma=");
confere("σ = MPa explica o que falta", /falta uma força/.test(s?.nota) && /Sem unidade: F/.test(s?.nota), JSON.stringify(s));
confere("chip 'F em kN'", s?.sug.includes("F em kN"), JSON.stringify(s?.sug));
await p.locator(".sug-unidade .chip", { hasText: "F em kN" }).tap(); await p.waitForTimeout(1500);
const f = await regiao("F\\tldef");
confere("F recebe a unidade", /60\\,\\mathrm\{kN\}$/.test(f?.v), f?.v);
s = await regiao("\\sigma=");
confere("σ = 190,986 MPa", /190[,.]986/.test(s?.res) && /MPa/.test(s?.un) && !s?.sug.length, JSON.stringify(s));
confere("sem erros de JS", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
