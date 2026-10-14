// Teste de interface da folha como documento: definir e mostrar, substituição explícita, regiões
// de texto, várias folhas (com a migração da folha que já estava no aparelho), arquivo .tlab,
// páginas A4 e impressão. Mesmo ambiente do fluxo.mjs (tablet 1280×800 com toque).
//
//   cd ferramentas/teste-ui && CHROME=/caminho/do/chrome node documento.mjs [url]

import { chromium } from "playwright-core";
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const URL = process.argv[2] || "http://127.0.0.1:8098/";
const CHROME = process.env.CHROME;
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const ctx = await nav.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: true, isMobile: true, acceptDownloads: true });
const p = await ctx.newPage();
const erros = [];
p.on("pageerror", (e) => erros.push(e.message));
p.on("response", (r) => { if (r.status() >= 400) erros.push(r.status() + " " + r.url()); });
const pedidos = [];
p.on("request", (r) => { if (r.url().endsWith("/api/calcular")) pedidos.push(JSON.parse(r.postData() || "{}")); });

let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const esperar = (ms = 900) => p.waitForTimeout(ms);
const regioes = () => p.$$eval(".rg", (els) => els.map((e) => ({
  id: e.dataset.id, cls: e.className, v: e.querySelector("math-field")?.value ?? null,
  txt: e.querySelector(".txt")?.innerHTML ?? null,
  res: e.querySelector(".resultado")?.textContent ?? "", un: e.querySelector(".unidade") && !e.querySelector(".unidade").hidden ? e.querySelector(".unidade").textContent : "",
  sub: e.querySelector(".substituicao")?.textContent ?? "",
})));
const salvo = async () => JSON.parse(await p.evaluate(() => localStorage.getItem("tracolab.folha.v2")));
const indice = async () => JSON.parse(await p.evaluate(() => localStorage.getItem("tracolab.folhas.v1") || "null"));
async function abrirFolha(estado, limparIndice = true) {
  await p.evaluate(([e, l]) => {
    localStorage.clear();
    localStorage.setItem("tracolab.folha.v2", JSON.stringify(e));
    if (!l) return;
  }, [estado, limparIndice]);
  await p.reload(); await esperar(1800);
}
const tocar = async (sel) => { await p.locator(sel).first().tap(); await esperar(400); };
const DEF = "\\tldef";

await p.goto(URL); await esperar(1200);

// 1. migração: a folha que já estava no aparelho (sem id, sem índice) continua aberta e entra no índice
const antiga = { nome: "Viga antiga", modo: "parcial", ordem: false, regioes: [
  { id: "r1", latex: `q${DEF}12\\,\\mathrm{kN}/\\mathrm{m}`, unidade: "", x: 40, y: 48 },
  { id: "r2", latex: `L${DEF}6\\,\\mathrm{m}`, unidade: "", x: 40, y: 120 },
  { id: "r3", latex: `R${DEF}\\frac{q\\cdot L}{2}=`, unidade: "\\,\\mathrm{kN}", x: 40, y: 192 },
] };
await abrirFolha(antiga);
let v = await regioes();
confere("migração: a folha do aparelho continua aberta, com as 3 regiões", v.length === 3 && v[0].v.startsWith("q") && (await p.inputValue("#nome-folha")) === "Viga antiga", JSON.stringify(v.map((x) => x.v)));
let s = await salvo(), idx = await indice();
confere("migração: ganhou id e entrou no índice sem mudar de chave", !!s.id && s.regioes.length === 3 && idx?.folhas.length === 1 && idx.folhas[0].id === s.id && idx.folhas[0].nome === "Viga antiga", JSON.stringify(idx));

// 2. definir e mostrar: R := qL/2 = 36 kN, com o chip de unidade
const R = v.find((x) => x.v.startsWith("R"));
confere("definir e mostrar: R := qL/2 = 36 kN", /^36$/.test(R.res.trim()) && /kN/.test(R.un) && /numerico/.test(R.cls), JSON.stringify(R));

// 3. substituição explícita pelo botão x=2
await p.locator(`.rg[data-id="${R.id}"] math-field`).tap(); await esperar(300);
await tocar(`.rg[data-id="${R.id}"] .subst`); await esperar(900);
v = await regioes();
let R2 = v.find((x) => x.id === R.id);
confere("substituição: a fórmula aparece com 12 kN/m e 6 m", /12/.test(R2.sub) && /6/.test(R2.sub) && /kN/.test(R2.sub) && /^36$/.test(R2.res.trim()), JSON.stringify(R2));
confere("substituição: pedida ao núcleo e salva na folha", pedidos.at(-1)?.regioes.some((r) => r.id === R.id && r.substituir) && (await salvo()).regioes.find((r) => r.id === R.id).substituir === true);
await tocar(`.rg[data-id="${R.id}"] .subst`); await esperar(900);
R2 = (await regioes()).find((x) => x.id === R.id);
confere("substituição: o mesmo botão desliga", R2.sub === "", JSON.stringify(R2));

// 4. sem "=", a unidade do resultado não entra na conta (R := qL/2 kN seria outro valor)
await p.evaluate((id) => { const mf = document.querySelector(`.rg[data-id="${id}"] math-field`); mf.value = mf.value.replace(/=$/, ""); mf.dispatchEvent(new Event("input", { bubbles: true })); }, R.id);
await esperar(900);
const ultimo = pedidos.at(-1)?.regioes.find((r) => r.id === R.id);
confere("sem o '=', a unidade do chip não vai junto da definição", ultimo && !/mathrm\{kN\}$/.test(ultimo.latex) && !/=/.test(ultimo.latex), JSON.stringify(ultimo));

// 5. texto: tecla T cria um parágrafo no fim; Título; fora do cálculo
await p.evaluate(() => document.activeElement?.blur()); await esperar(300);
await p.evaluate(() => { window.mathVirtualKeyboard.hide(); }); await esperar(200);
await tocar(".tecla-texto");
await p.keyboard.type("Memorial de cálculo"); await esperar(200);
await p.locator(".rg.texto .txt-ferr button[data-estilo=titulo]").first().tap(); await esperar(300);
const caixa = await p.locator("#pagina").boundingBox();
await p.touchscreen.tap(caixa.x + 700, caixa.y + 700); await esperar(900);
v = await regioes();
const t = v.find((x) => x.txt !== null);
confere("texto: título criado pela tecla T", t && /t-titulo/.test(t.cls) && t.txt === "Memorial de cálculo", JSON.stringify(t));
s = await salvo();
confere("texto: salvo na folha com tipo e estilo", s.regioes.some((r) => r.tipo === "texto" && r.estilo === "titulo" && r.html === "Memorial de cálculo"));
await p.locator("#btn-calcular").tap(); await esperar(900);
confere("texto: não vai para o núcleo", !pedidos.at(-1).regioes.some((r) => r.id === t.id));

// 6. " numa fórmula vazia vira texto
await p.evaluate(() => { document.querySelector(".mesa").scrollTop = 0; }); await esperar(200);
await p.touchscreen.tap(caixa.x + 420, caixa.y + 420); await esperar(500);
await p.keyboard.type('"'); await esperar(300);
await p.keyboard.type("nota de projeto"); await esperar(300);
v = await regioes();
confere('" numa fórmula vazia vira texto', v.some((x) => x.txt === "nota de projeto") && !v.some((x) => x.v === '"' || (x.v ?? "").includes("text")), JSON.stringify(v.map((x) => x.v ?? x.txt)));

// 7. várias folhas: nova (em branco), voltar, duplicar, renomear, apagar
await p.evaluate(() => document.activeElement?.blur()); await esperar(400);
await tocar("#rail-folhas");
confere("painel Folhas lista a folha aberta", (await p.$$eval(".fl", (els) => els.length)) === 1 && (await p.locator(".fl.atual .fl-nome").textContent()) === "Viga antiga");
await tocar(".acoes-folha [data-acao=nova]"); await esperar(800);
confere("nova folha começa em branco (sem a folha de exemplo)", (await regioes()).length === 0 && (await p.inputValue("#nome-folha")) === "Folha sem título");
await p.reload(); await esperar(1500);
confere("folha nova continua em branco depois de recarregar", (await regioes()).length === 0);
await tocar("#rail-folhas");
const itens = await p.$$eval(".fl", (els) => els.map((e) => e.querySelector(".fl-nome").textContent));
confere("duas folhas no índice", itens.length === 2 && itens.includes("Viga antiga"), JSON.stringify(itens));
await p.locator(".fl", { hasText: "Viga antiga" }).locator(".fl-abrir").tap(); await esperar(1200);
v = await regioes();
confere("voltar para a primeira folha traz as regiões e o texto", v.length === 5 && v.some((x) => x.txt === "Memorial de cálculo") && (await p.inputValue("#nome-folha")) === "Viga antiga", JSON.stringify(v.map((x) => x.v ?? x.txt)));
confere("desfazer recomeça ao trocar de folha", await p.locator("#btn-desfazer").isDisabled());
await p.locator(".fl", { hasText: "Viga antiga" }).locator("[data-acao=duplicar]").tap(); await esperar(400);
await p.locator(".fl", { hasText: "(cópia)" }).locator("[data-acao=renomear]").tap(); await esperar(200);
await p.locator(".fl-campo").fill("Viga B"); await p.keyboard.press("Enter"); await esperar(400);
confere("duplicar e renomear", (await p.$$eval(".fl-nome", (els) => els.map((e) => e.textContent))).includes("Viga B"));
await p.locator(".fl", { hasText: "Folha sem título" }).locator("[data-acao=apagar]").tap(); await esperar(200);
await p.locator(".fl", { hasText: "Folha sem título" }).locator("[data-sim]").tap(); await esperar(400);
const nomes = await p.$$eval(".fl-nome", (els) => els.map((e) => e.textContent));
confere("apagar com confirmação", nomes.length === 2 && !nomes.includes("Folha sem título"), JSON.stringify(nomes));

// 8. exportar .tlab
const [baixado] = await Promise.all([p.waitForEvent("download"), p.locator(".acoes-folha [data-acao=exportar]").tap()]);
const dir = mkdtempSync(join(tmpdir(), "tlab-"));
const caminho = join(dir, baixado.suggestedFilename());
await baixado.saveAs(caminho);
const exp = JSON.parse(readFileSync(caminho, "utf8"));
confere("exportar: arquivo .tlab com formato, versão e regiões", baixado.suggestedFilename() === "Viga antiga.tlab" && exp.formato === "tracolab" && exp.versao === 1 && exp.folha.regioes.length === 5, baixado.suggestedFilename());

// 9. importar: vira folha nova; HTML perigoso sai; tipo desconhecido fica de fora
const imp = { formato: "tracolab", versao: 1, nome: "Importada", folha: { modo: "parcial", regioes: [
  { id: "x", tipo: "texto", estilo: "titulo", html: 'Olá <img src=x onerror="window.__xss=1"><b>forte</b><script>window.__xss=2</script>', x: 40, y: 48 },
  { id: "y", latex: `a${DEF}2`, x: 40, y: 120 }, { id: "z", latex: "a\\cdot3=", x: 40, y: 190 },
  { id: "w", tipo: "desconhecido", x: 0, y: 0 },
] } };
writeFileSync(join(dir, "imp.tlab"), JSON.stringify(imp));
await p.locator("#folhas input[type=file], body > input[type=file]").first().setInputFiles(join(dir, "imp.tlab")); await esperar(1500);
v = await regioes();
const xss = await p.evaluate(() => window.__xss);
confere("importar: abre como folha nova e calcula", (await p.inputValue("#nome-folha")) === "Importada" && v.some((x) => /^6$/.test(x.res.trim())), JSON.stringify(v));
confere("importar: HTML perigoso removido, negrito mantido", !xss && (await p.$$eval(".rg.texto img, .rg.texto script", (e) => e.length)) === 0 && v.some((x) => x.txt === "Olá <b>forte</b>"), JSON.stringify(v.map((x) => x.txt)));
confere("importar: avisa a região ignorada", /1 região/.test(await p.locator(".aviso-folha").textContent()), await p.locator(".aviso-folha").textContent());
writeFileSync(join(dir, "ruim.tlab"), '{"formato": "outro"}');
await p.locator("body > input[type=file]").setInputFiles(join(dir, "ruim.tlab")); await esperar(600);
confere("importar arquivo inválido: avisa e não troca a folha", /não é uma folha/.test(await p.locator(".aviso-folha").textContent()) && (await p.inputValue("#nome-folha")) === "Importada");

// 10. páginas: a folha cresce e marca a quebra; na impressão nada fica cortado
await abrirFolha({ nome: "Longa", modo: "parcial", regioes: [
  { id: "r1", latex: `a${DEF}1`, unidade: "", x: 40, y: 48 },
  { id: "r2", latex: `b${DEF}\\frac{a}{2}`, unidade: "", x: 40, y: 1092 },
  { id: "r3", latex: "b=", unidade: "", x: 40, y: 1500 },
] });
const alt = await p.evaluate(() => document.getElementById("pagina").offsetHeight);
confere("a folha cresce para 2 páginas, com a quebra marcada", alt >= 2 * 1122 && (await p.$$eval(".quebra-pagina", (e) => e.length)) === 1, String(alt));
const yImp = await p.evaluate(() => parseFloat(getComputedStyle(document.querySelector('.rg[data-id="r2"]')).getPropertyValue("--y-impressao")));
confere("impressão: a região que cruzaria a quebra desce para a página 2", yImp >= 1122.5, String(yImp));
await p.emulateMedia({ media: "print" });
const visiveis = await p.evaluate(() => ["barra", "faixa", "trilho"].map((c) => getComputedStyle(document.querySelector("." + c)).display));
confere("impressão: barra, faixa e trilho somem", visiveis.every((d) => d === "none"), JSON.stringify(visiveis));
const pdf = await p.pdf({ format: "A4", printBackground: true, preferCSSPageSize: true });
const paginasPdf = (pdf.toString("latin1").match(/\/Type\s*\/Page[^s]/g) || []).length;
confere("PDF com 2 páginas A4", paginasPdf === 2, String(paginasPdf));
await p.emulateMedia({ media: "screen" });

confere("sem erros de JS nem 404", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
