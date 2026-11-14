// Teste de interface da conta (ADR 0019): primeiro acesso ou criar conta, trocar senha, ativar a
// verificação em duas etapas lendo a chave da tela, sair e entrar com o código, gerar convite e
// usar o link noutro navegador, desativar o 2FA. Tablet 1280×800 com toque, como o fluxo.mjs.
//
//   ferramentas/harness/interface.sh conta   ou, com um servidor no ar (cadastro aberto ou primeiro acesso):
//   cd ferramentas/teste-ui && CHROME=/caminho/do/chrome node conta.mjs [url] [pasta-dos-prints]
import { chromium } from "playwright-core";
import { createHmac } from "node:crypto";
import { pronta } from "./sessao.mjs";

const URL_BASE = new URL(process.argv[2] || "http://127.0.0.1:8098/").origin + "/";
const PRINTS = process.argv[3];
const CHROME = process.env.CHROME;
if (!CHROME) { console.error("defina CHROME=/caminho/do/chrome"); process.exit(2); }
const nav = await chromium.launch({ executablePath: CHROME, args: ["--no-sandbox"] });
const ctx = await nav.newContext({ viewport: { width: 1280, height: 800 }, hasTouch: true, isMobile: true });
const p = await ctx.newPage();
const erros = [];
p.on("pageerror", (e) => erros.push(e.message));
p.on("response", (r) => { if (r.status() >= 500) erros.push(r.status() + " " + r.url()); });

let ok = 0, falhas = 0;
const confere = (nome, cond, det = "") => { if (cond) { ok++; console.log("  ok   ", nome); } else { falhas++; console.log("  FALHA", nome, det); } };
const esperar = (ms = 600) => p.waitForTimeout(ms);
const tela = () => p.$eval("[data-tela]:not([hidden])", (e) => e.dataset.tela).catch(() => null);
const print = async (nome) => { if (PRINTS) await p.screenshot({ path: `${PRINTS}/${nome}.png` }); };

/** TOTP (RFC 6238) a partir da chave em base32 mostrada na tela. */
function totp(segredo, desvio = 0) {
  const alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const limpo = segredo.replace(/[\s=]/g, "").toUpperCase();
  const bytes = []; let buffer = 0, bits = 0;
  for (const c of limpo) { buffer = (buffer << 5) | alfabeto.indexOf(c); bits += 5; if (bits >= 8) { bytes.push((buffer >> (bits - 8)) & 255); bits -= 8; } }
  const passo = Math.floor(Date.now() / 30000) + desvio;
  const msg = Buffer.alloc(8); msg.writeBigUInt64BE(BigInt(passo));
  const h = createHmac("sha1", Buffer.from(bytes)).update(msg).digest();
  const o = h[19] & 15;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1_000_000)).padStart(6, "0");
}

const USUARIO = "conta-ui-" + Date.now().toString(36).slice(-5);
const SENHA = "senha-da-conta-ui-123";
const SENHA2 = "outra-senha-da-conta-456";

// 1. primeiro acesso (instância nova) ou criar conta (cadastro aberto)
await p.goto(URL_BASE + "conta.html"); await esperar(800);
const inst = await (await ctx.request.get(URL_BASE + "api/instancia")).json();
const primeira = inst.primeiroAcesso;
confere(primeira ? "instância nova mostra o primeiro acesso" : "mostra entrar, com o link de criar conta", (await tela()) === (primeira ? "primeiro" : "entrar"), await tela());
await print("conta-1-" + (await tela()));
if (!primeira) { await p.locator("#entrar-cadastro a").tap(); await esperar(300); confere("link leva a criar conta", (await tela()) === "cadastrar", await tela()); }
const formCriar = primeira ? "#form-primeiro" : "#form-cadastrar";
await p.fill(`${formCriar} [name=usuario]`, USUARIO);
await p.fill(`${formCriar} [name=senha]`, SENHA);
await p.fill(`${formCriar} [name=confirmar]`, SENHA + "x");
await p.locator(`${formCriar} button[type=submit]`).tap(); await esperar(400);
confere("senhas diferentes: erro na tela, sem sair da página", /iguais/.test(await p.locator(`${formCriar} .erro`).textContent()) && (await tela()) === (primeira ? "primeiro" : "cadastrar"));
await p.fill(`${formCriar} [name=confirmar]`, SENHA);
await p.locator(`${formCriar} button[type=submit]`).tap();
await p.waitForURL((u) => !u.pathname.endsWith("conta.html"), { timeout: 10000 }); await pronta(p); await esperar(1200);
confere("conta criada: vai para a folha, com a folha de exemplo", (await p.$$eval(".rg", (e) => e.length)) === 4 && (await p.inputValue("#nome-folha")) === "Barra tracionada");
confere("botão da conta mostra quem entrou", (await p.getAttribute("#btn-conta", "title")) === `Conta: ${USUARIO}`);

// 2. minha conta
await p.locator("#btn-conta").tap(); await p.waitForURL(/conta\.html/); await esperar(800);
confere("abre Minha conta com o nome", (await tela()) === "conta" && (await p.textContent("#conta-nome")) === USUARIO);
confere(primeira ? "primeira conta é administradora" : "conta comum não é administradora", (await p.isHidden("#conta-admin")) !== primeira);
await print("conta-2-minha-conta");

// 3. trocar a senha
await p.fill("#form-senha [name=atual]", "errada-errada-errada");
await p.fill("#form-senha [name=nova]", SENHA2); await p.fill("#form-senha [name=confirmar]", SENHA2);
await p.locator("#form-senha button[type=submit]").tap(); await esperar(800);
confere("senha atual errada: erro do servidor na tela", /não confere/.test(await p.locator("#form-senha .erro").textContent()));
await p.fill("#form-senha [name=atual]", SENHA);
await p.fill("#form-senha [name=nova]", SENHA2); await p.fill("#form-senha [name=confirmar]", SENHA2);
await p.locator("#form-senha button[type=submit]").tap(); await esperar(800);
confere("senha trocada", (await p.locator("#form-senha .ok-texto").textContent()) === "Senha trocada.");

// 4. ativar a verificação em duas etapas
await p.locator("#btn-2fa-preparar").tap(); await esperar(800);
confere("QR e chave aparecem", (await p.$$eval("#qr svg path", (e) => e.length)) === 1 && /^[A-Z2-7 ]{35,}$/.test((await p.textContent("#segredo")).trim()));
const segredo = (await p.textContent("#segredo")).trim();
await p.fill("#form-2fa-ativar [name=codigo]", "000000");
await p.locator("#form-2fa-ativar button[type=submit]").tap(); await esperar(800);
confere("código errado não ativa", /inválido/i.test(await p.locator("#form-2fa-ativar .erro").textContent()));
await p.fill("#form-2fa-ativar [name=codigo]", totp(segredo));
await p.locator("#form-2fa-ativar button[type=submit]").tap(); await esperar(1000);
const codigos = await p.$$eval("#lista-codigos span", (e) => e.map((x) => x.textContent));
confere("2FA ativa, com 10 códigos de recuperação na tela", (await p.isVisible("#dois-fatores-on")) && codigos.length === 10 && codigos.every((c) => /^[a-z2-9]{5}-[a-z2-9]{5}$/.test(c)), JSON.stringify(codigos));
await print("conta-3-2fa-ativa");

// 5. sair e entrar de novo: senha nova + código
await p.locator("#btn-sair").tap(); await esperar(800);
confere("sair volta para a tela de entrar", (await tela()) === "entrar", await tela());
await p.fill("#form-entrar [name=usuario]", USUARIO); await p.fill("#form-entrar [name=senha]", SENHA);
await p.locator("#form-entrar button[type=submit]").tap(); await esperar(800);
confere("senha antiga não entra mais", /incorretos/.test(await p.locator("#form-entrar .erro").textContent()));
await p.fill("#form-entrar [name=senha]", SENHA2);
await p.locator("#form-entrar button[type=submit]").tap(); await esperar(800);
confere("senha certa pede a segunda etapa", (await tela()) === "2fa", await tela());
await print("conta-4-segunda-etapa");
await p.fill("#form-2fa [name=codigo]", "123456");
await p.locator("#form-2fa button[type=submit]").tap(); await esperar(800);
confere("código errado: erro, continua na segunda etapa", (await tela()) === "2fa" && /inválido/i.test(await p.locator("#form-2fa .erro").textContent()));
await p.fill("#form-2fa [name=codigo]", codigos[0]);
await p.locator("#form-2fa button[type=submit]").tap();
await p.waitForURL((u) => !u.pathname.endsWith("conta.html"), { timeout: 10000 }); await pronta(p);
confere("código de recuperação entra e abre a folha", (await p.inputValue("#nome-folha")) === "Barra tracionada");

// 6. convite (só administrador) usado noutro navegador
await p.goto(URL_BASE + "conta.html"); await esperar(800);
if (primeira) {
  await p.locator("#btn-convite").tap(); await esperar(800);
  const link = (await p.textContent("#convite-link")).trim();
  confere("convite gerado com link", /conta\.html\?convite=[a-z2-9]{20}$/.test(link), link);
  const ctx2 = await nav.newContext({ viewport: { width: 800, height: 1280 }, hasTouch: true, isMobile: true });
  const p2 = await ctx2.newPage();
  await p2.goto(link.replace(/^https?:\/\/[^/]+\//, URL_BASE)); await p2.waitForTimeout(800);
  confere("link abre criar conta com o convite preenchido", (await p2.$eval("[data-tela]:not([hidden])", (e) => e.dataset.tela)) === "cadastrar" && (await p2.inputValue("#form-cadastrar [name=convite]")).length === 20);
  await p2.fill("#form-cadastrar [name=usuario]", USUARIO + "-b");
  await p2.fill("#form-cadastrar [name=senha]", SENHA); await p2.fill("#form-cadastrar [name=confirmar]", SENHA);
  await p2.locator("#form-cadastrar button[type=submit]").tap();
  await p2.waitForURL((u) => !u.pathname.endsWith("conta.html"), { timeout: 10000 }); await pronta(p2);
  confere("convidado entra e tem a própria folha de exemplo", (await p2.inputValue("#nome-folha")) === "Barra tracionada");
  await ctx2.close();
  await p.reload(); await esperar(800);
  confere("administração lista os 2 usuários", (await p.$$eval(".linha-u", (e) => e.length)) === 2);
  await print("conta-5-administracao");
} else {
  confere("conta comum não vê a administração", await p.isHidden("[data-tela=admin]"));
}

// 7. desativar o 2FA
await p.locator("#dois-fatores-on details:nth-of-type(2) summary").tap(); await esperar(300);
await p.fill("#form-2fa-desativar [name=senha]", SENHA2);
await p.fill("#form-2fa-desativar [name=codigo]", totp(segredo));
await p.locator("#form-2fa-desativar button[type=submit]").tap(); await esperar(800);
confere("2FA desativada", await p.isVisible("#dois-fatores-off"));

confere("sem erros de JS nem 5xx", erros.length === 0, erros.join(" | "));
console.log(`\n${ok}/${ok + falhas} verificações passaram`);
await nav.close(); process.exit(falhas ? 1 : 0);
