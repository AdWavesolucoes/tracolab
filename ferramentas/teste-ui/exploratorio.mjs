// Teste exploratório de interface: corrida de respostas, desfazer/refazer, faixa de teclas,
// desempenho com muitas regiões e erros de console. Mesmo estilo do fluxo.mjs.
//   CHROME=/caminho/do/chrome node exploratorio.mjs [url]
import { chromium } from "playwright-core";
const URL = process.argv[2] || "http://127.0.0.1:8098/";
const CHROME = process.env.CHROME;
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const erros = [];
let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
async function nova(w, h) {
  const ctx = await nav.newContext({ viewport: { width: w, height: h }, hasTouch: true, isMobile: true });
  const p = await ctx.newPage();
  p.on("pageerror", (e) => erros.push(e.message));
  p.on("console", (m) => { if (m.type() === "error") erros.push("console: " + m.text()); });
  p.on("response", (r) => { if (r.status() >= 400) erros.push(r.status() + " " + r.url()); });
  await p.goto(URL); await p.evaluate(() => localStorage.clear()); await p.reload(); await p.waitForTimeout(1500);
  return p;
}
const reg = (id, latex, y, extra = {}) => ({ id, latex, unidade: "", x: 40, y, ...extra });
const poe = (p, regioes) => p.evaluate((rs) => { estado.regioes = rs; montar(); registrar(); agendar(0, false); }, regioes);
const resultados = (p) => p.$$eval(".rg:not(.texto)", (els) => els.map((e) => e.dataset.id + ":" + e.querySelector(".resultado").textContent.replace(/\s+/g, "")));
const ids = (p) => p.evaluate(() => estado.regioes.map((r) => r.id + "|" + r.latex + "|" + r.unidade + "|" + r.x + "," + r.y).join(";"));
const dom = (p) => p.$$eval(".rg", (els) => els.map((e) => e.dataset.id).join(","));

// ---------------------------------------------------------------- 1. corrida: resposta velha não pinta
{
  const p = await nova(1280, 800);
  let n = 0;
  await p.route("**/api/calcular", async (rota) => {
    const meu = ++n;
    const r = await rota.fetch();
    if (meu === 1) await new Promise((f) => setTimeout(f, 900));   // a primeira resposta chega por último
    await rota.fulfill({ response: r });
  });
  await p.evaluate(() => { estado.regioes = [{ id: "r1", latex: "1+1=", unidade: "", x: 40, y: 48 }]; montar(); recalcular(); });
  await p.waitForTimeout(150);
  await p.evaluate(() => { estado.regioes[0].latex = "2+2="; campoDe("r1").value = "2+2="; recalcular(); });
  await p.waitForTimeout(1800);
  const r = (await resultados(p)).join();
  confere("resposta antiga não sobrescreve a nova (corrida)", /:4$/.test(r), r);
  await p.context().close();
}

// ---------------------------------------------------------------- 2. desfazer / refazer
{
  const p = await nova(1280, 800);
  await poe(p, [reg("r1", "a:=2", 48), reg("r2", "b:=a+1", 120), reg("r3", "b=", 192)]);
  await p.waitForTimeout(600);
  const base = await ids(p);
  const desfazer = async () => { await p.locator("#btn-desfazer").tap(); await p.waitForTimeout(500); };
  const refazer = async () => { await p.locator("#btn-refazer").tap(); await p.waitForTimeout(500); };
  // trocar unidade
  await p.evaluate(() => { const r = estado.regioes[2]; escolherUnidade(r, "\\,\\mathrm{kPa}"); });
  await p.waitForTimeout(500);
  const comUn = await ids(p);
  await desfazer();
  confere("desfazer a troca de unidade", (await ids(p)) === base, await ids(p));
  await refazer();
  confere("refazer a troca de unidade", (await ids(p)) === comUn);
  await desfazer();
  // arrastar (alça)
  const alca = await p.locator('.rg[data-id="r2"] .alca').boundingBox();
  await p.mouse.move(alca.x + 5, alca.y + 5); await p.mouse.down(); await p.mouse.move(alca.x + 60, alca.y + 80, { steps: 4 }); await p.mouse.up();
  await p.waitForTimeout(500);
  const movido = await ids(p);
  confere("arrastar mudou a posição", movido !== base, movido);
  await desfazer();
  confere("desfazer o arrasto", (await ids(p)) === base, await ids(p));
  await refazer();
  confere("refazer o arrasto", (await ids(p)) === movido);
  await desfazer();
  // remover
  await p.evaluate(() => remover("r2")); await p.waitForTimeout(400);
  await desfazer();
  confere("desfazer remover traz a região de volta", (await ids(p)) === base && (await dom(p)) === "r1,r2,r3", await dom(p));
  // apagar tudo
  await p.evaluate(() => document.getElementById("btn-limpar").click()); await p.waitForTimeout(400);
  confere("limpar tudo esvazia a folha", (await dom(p)) === "");
  await desfazer();
  confere("desfazer apagar tudo", (await ids(p)) === base && (await dom(p)) === "r1,r2,r3");
  await p.waitForTimeout(500);
  const res = (await resultados(p)).join();
  confere("depois de desfazer, os resultados voltam", /r3:3/.test(res), res);
  // edição seguida de desfazer logo em seguida (antes do debounce)
  await p.evaluate(() => { estado.regioes[0].latex = "a:=9"; campoDe("r1").value = "a:=9"; agendar(); });
  await p.locator("#btn-desfazer").tap(); await p.waitForTimeout(700);
  const apos = await ids(p);
  confere("desfazer logo após digitar volta ao estado anterior à edição (não dois passos)", apos === base, apos);
  await p.locator("#btn-refazer").tap(); await p.waitForTimeout(500);
  confere("refazer devolve a edição digitada", /r1\|a:=9\|/.test(await ids(p)), await ids(p));
  await p.context().close();
}

// ---------------------------------------------------------------- 3. faixa de teclas
for (const [w, h] of [[1280, 800], [800, 1280]]) {
  const p = await nova(w, h);
  const f = await p.evaluate(() => {
    const fx = document.querySelector(".faixa"), b = fx.getBoundingClientRect();
    const ks = [...fx.querySelectorAll(".key")].map((k) => k.getBoundingClientRect());
    return { cw: fx.clientWidth, sw: fx.scrollWidth, esq: Math.min(...ks.map((k) => k.left)), dir: Math.max(...ks.map((k) => k.right)), vw: innerWidth, n: ks.length, base: b.bottom, vh: innerHeight, h: b.height, docw: document.documentElement.scrollWidth };
  });
  confere(`faixa ${w}x${h}: nenhuma tecla cortada à esquerda`, f.esq >= 0, JSON.stringify(f));
  confere(`faixa ${w}x${h}: sem rolagem horizontal da página`, f.docw <= f.vw, JSON.stringify(f));
  confere(`faixa ${w}x${h}: cabe ou rola até a última tecla`, f.sw <= f.cw + 1 || f.dir <= f.vw + f.sw - f.cw, JSON.stringify(f));
  // tocar logo abaixo da faixa (rodapé) / entre teclas não deve acionar tecla
  await p.evaluate(() => { window.__cliques = 0; document.querySelectorAll(".faixa .key").forEach((k) => k.addEventListener("click", () => window.__cliques++)); });
  const caixa = await p.evaluate(() => { const r = document.querySelector(".faixa").getBoundingClientRect(); const k = document.querySelector(".faixa .key").getBoundingClientRect(); return { fb: r.bottom, ft: r.top, kb: k.bottom, kt: k.top }; });
  const antes = await p.evaluate(() => estado.regioes.length);
  await p.touchscreen.tap(w / 2 + 1, Math.min(h - 1, caixa.fb - 2));   // borda inferior da faixa, abaixo das teclas
  await p.waitForTimeout(300);
  const cl = await p.evaluate(() => window.__cliques);
  confere(`faixa ${w}x${h}: tocar na borda inferior (fora das teclas) não aciona tecla`, cl === 0, `cliques=${cl} ${JSON.stringify(caixa)}`);
  confere(`faixa ${w}x${h}: tocar no rodapé não cria região sozinha`, (await p.evaluate(() => estado.regioes.length)) === antes);
  await p.context().close();
}

// ---------------------------------------------------------------- 4. desempenho: 150 regiões
{
  const p = await nova(1280, 800);
  const m = await p.evaluate(async () => {
    const rs = [];
    for (let i = 0; i < 150; i++) rs.push({ id: "r" + (i + 1), latex: i % 3 === 0 ? `v_{${i}}:=${i}+1` : i % 3 === 1 ? `w_{${i}}:=v_{${i - 1}}*2` : `w_{${i - 1}}=`, unidade: "", x: 40 + (i % 2) * 360, y: 48 + Math.floor(i / 2) * 72 });
    estado.regioes = rs;
    let t = performance.now(); montar(); const tMontar = performance.now() - t;
    t = performance.now(); await recalcular(); const tRecalc = performance.now() - t;
    t = performance.now(); await recalcular(); const tRecalc2 = performance.now() - t;
    return { tMontar, tRecalc, tRecalc2 };
  });
  console.log("  medidas (ms):", JSON.stringify(m, (k, v) => (typeof v === "number" ? Math.round(v) : v)));
  confere("150 regiões: montar < 3 s", m.tMontar < 3000);
  confere("150 regiões: recalcular+pintar < 2 s", m.tRecalc2 < 2000);
  await p.context().close();
}

confere("sem erros de JS, console nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
