// Sessão e dados para os testes de interface (ADR 0019): a página exige login e as folhas vivem na
// conta. O servidor de teste precisa aceitar cadastro (TRACOLAB_CADASTRO=aberto) ou estar no
// primeiro acesso (ferramentas/harness/interface.sh cuida disso).

const contexto = (ctxOuPagina) => (typeof ctxOuPagina.context === "function" ? ctxOuPagina.context() : ctxOuPagina);

/** Cria (ou entra com) o usuário de teste no mesmo cookie do navegador e apaga as folhas dele. */
export async function entrar(ctxOuPagina, url, usuario = "teste-ui", senha = "senha-de-teste-ui-123") {
  const ctx = contexto(ctxOuPagina);
  const base = new URL(url).origin + "/";
  const r = await ctx.request.post(base + "api/conta/cadastrar", { data: { usuario, senha } });
  if (r.status() !== 201) {
    const e = await ctx.request.post(base + "api/conta/entrar", { data: { usuario, senha } });
    if (!e.ok()) throw new Error(`não foi possível entrar como ${usuario}: ${e.status()} ${await e.text()}`);
  }
  await apagarFolhas(ctx, base);
}

async function apagarFolhas(ctx, base) {
  const lista = await (await ctx.request.get(base + "api/folhas")).json();
  for (const f of lista) await ctx.request.delete(base + "api/folhas/" + f.id);
}

/** Espera a página terminar de abrir uma folha (sessão conferida, folha da conta na tela). */
export const pronta = (p, ms = 15000) => p.waitForFunction(() => typeof estado !== "undefined" && !!estado.id, null, { timeout: ms });   // `let estado` não vira window.estado

/**
 * Deixa a conta só com esta folha, do jeito que ela estava no navegador antes das contas
 * (tracolab.folha.v2): a página a migra para a conta ao recarregar e a abre. Serve para testar a
 * migração e para montar um cenário sem passar pelo teclado.
 */
export async function semear(p, folha, url = p.url()) {
  await pronta(p);
  const base = new URL(url).origin + "/";
  await apagarFolhas(contexto(p), base);
  await p.evaluate((f) => { localStorage.clear(); localStorage.setItem("tracolab.folha.v2", JSON.stringify(f)); }, folha);
  await p.reload();
  await pronta(p);
  await p.waitForTimeout(600);
}

/** A folha aberta como está gravada na conta (força a gravação pendente e lê de volta do servidor). */
export const salva = (p) => p.evaluate(async () => {
  await TL.gravarAgora();
  const r = await TL.api("GET", "api/folhas/" + estado.id);
  return { ...r.conteudo, id: r.id, nome: r.nome, versao: r.versao };
});
