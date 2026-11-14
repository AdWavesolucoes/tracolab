"use strict";
// Página de conta (ADR 0019): primeiro acesso, entrar (com segunda etapa), criar conta por
// convite, minha conta (senha, 2FA, códigos de recuperação) e administração (convites, usuários).
{
  const $ = (s, raiz = document) => raiz.querySelector(s);
  const params = new URLSearchParams(location.search);
  const telas = [...document.querySelectorAll("[data-tela]")];
  let instancia = null;

  /** Pedido JSON à API; erro vira Error com .status e .dados. */
  async function api(metodo, caminho, corpo) {
    const r = await fetch(caminho, {
      method: metodo,
      headers: corpo !== undefined ? { "Content-Type": "application/json" } : {},
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    });
    let dados = null;
    if (r.status !== 204) { try { dados = await r.json(); } catch (_) { /* sem corpo */ } }
    if (!r.ok) throw Object.assign(new Error(dados?.erro || `Erro ${r.status}`), { status: r.status, dados });
    return dados;
  }

  function mostrar(nome) {
    for (const t of telas) t.hidden = t.dataset.tela !== nome && !(nome === "conta" && t.dataset.tela === "admin" && t.dataset.liberada === "1");
    $("#lnk-folha").hidden = nome !== "conta";
    const foco = telas.find((t) => t.dataset.tela === nome)?.querySelector("input:not([readonly])");
    if (foco) setTimeout(() => foco.focus(), 50);
  }

  function erroEm(form, msg) {
    const p = $(".erro", form);
    p.textContent = msg || ""; p.hidden = !msg;
  }

  /** Liga um formulário: valida "repita a senha", desabilita o botão enquanto envia, mostra o erro no lugar. */
  function formulario(id, acao) {
    const form = document.getElementById(id);
    if (!form) return;
    form.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      erroEm(form, "");
      const dados = Object.fromEntries(new FormData(form).entries());
      if ("confirmar" in dados && dados.confirmar !== (dados.senha ?? dados.nova)) return erroEm(form, "As senhas não são iguais.");
      const botao = $("button[type=submit]", form);
      botao.disabled = true;
      try { await acao(dados, form); } catch (e) { erroEm(form, e.message); } finally { botao.disabled = false; }
    });
  }

  const paraFolha = () => location.replace("./");

  // ------------------------------------------------------------------ entrar, criar conta

  formulario("form-primeiro", async (d) => { await api("POST", "api/conta/cadastrar", { usuario: d.usuario, senha: d.senha }); paraFolha(); });

  formulario("form-cadastrar", async (d) => {
    await api("POST", "api/conta/cadastrar", { usuario: d.usuario, senha: d.senha, convite: d.convite || undefined });
    paraFolha();
  });

  formulario("form-entrar", async (d) => {
    const r = await api("POST", "api/conta/entrar", { usuario: d.usuario, senha: d.senha });
    if (r.doisFatores) mostrar("2fa"); else paraFolha();
  });

  formulario("form-2fa", async (d) => { await api("POST", "api/conta/entrar/2fa", { codigo: d.codigo }); paraFolha(); });

  document.addEventListener("click", (ev) => {
    const ir = ev.target.closest("[data-ir]");
    if (!ir) return;
    ev.preventDefault();
    if (ir.dataset.ir === "cadastrar" && instancia?.cadastro === "fechado") return mostrar("fechado");
    mostrar(ir.dataset.ir);
  });

  // ------------------------------------------------------------------ minha conta

  let conta = null;

  function pintarConta() {
    $("#conta-nome").textContent = conta.usuario;
    $("#conta-admin").hidden = !conta.admin;
    $("#dois-fatores-off").hidden = conta.doisFatores;
    $("#dois-fatores-on").hidden = !conta.doisFatores;
    $("#preparo-2fa").hidden = true;
    const admin = $("[data-tela=admin]");
    admin.dataset.liberada = conta.admin ? "1" : "0";
    if (conta.admin) pintarAdmin();
    mostrar("conta");
  }

  async function recarregarConta() { conta = await api("GET", "api/conta"); pintarConta(); }

  $("#btn-sair").addEventListener("click", async () => { await api("POST", "api/conta/sair"); location.replace("conta.html"); });

  formulario("form-senha", async (d, form) => {
    await api("POST", "api/conta/senha", { atual: d.atual, nova: d.nova });
    form.reset();
    const ok = $(".ok-texto", form); ok.textContent = "Senha trocada."; ok.hidden = false;
    setTimeout(() => { ok.hidden = true; }, 5000);
  });

  $("#btn-2fa-preparar").addEventListener("click", async () => {
    const b = $("#btn-2fa-preparar"); b.disabled = true;
    try {
      const p = await api("POST", "api/conta/2fa/preparar");
      $("#qr").innerHTML = p.qr;   // SVG gerado pelo servidor (só retângulos)
      $("#segredo").textContent = p.segredo.replace(/(.{4})/g, "$1 ").trim();
      $("#preparo-2fa").hidden = false;
      $("#form-2fa-ativar input").focus();
    } catch (e) { mostrarErroGeral(e.message); } finally { b.disabled = false; }
  });

  let codigosAtuais = [];
  function mostrarCodigos(codigos) {
    codigosAtuais = codigos;
    $("#lista-codigos").innerHTML = codigos.map((c) => `<span>${c}</span>`).join("");
    $("#codigos-recuperacao").hidden = false;
  }
  $("#btn-copiar-codigos").addEventListener("click", () => copiar(codigosAtuais.join("\n"), $("#btn-copiar-codigos")));
  $("#btn-baixar-codigos").addEventListener("click", () => {
    const blob = new Blob([`Códigos de recuperação do TraçoLab — ${conta.usuario} — ${new Date().toLocaleDateString("pt-BR")}\n\n${codigosAtuais.join("\n")}\n`], { type: "text/plain" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob); a.download = `tracolab-recuperacao-${conta.usuario}.txt`;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
  });

  formulario("form-2fa-ativar", async (d, form) => {
    const r = await api("POST", "api/conta/2fa/ativar", { codigo: d.codigo });
    form.reset();
    conta.doisFatores = true;
    pintarConta();
    mostrarCodigos(r.codigosRecuperacao);
  });

  formulario("form-2fa-codigos", async (d, form) => {
    const r = await api("POST", "api/conta/2fa/codigos", { senha: d.senha, codigo: d.codigo });
    form.reset(); form.closest("details").open = false;
    mostrarCodigos(r.codigosRecuperacao);
  });

  formulario("form-2fa-desativar", async (d, form) => {
    await api("POST", "api/conta/2fa/desativar", { senha: d.senha, codigo: d.codigo });
    form.reset(); form.closest("details").open = false;
    $("#codigos-recuperacao").hidden = true;
    conta.doisFatores = false;
    pintarConta();
  });

  async function copiar(texto, botao) {
    try { await navigator.clipboard.writeText(texto); const t = botao.textContent; botao.textContent = "Copiado"; setTimeout(() => { botao.textContent = t; }, 1800); }
    catch (_) { mostrarErroGeral("Não foi possível copiar. Selecione o texto e copie manualmente."); }
  }

  function mostrarErroGeral(msg) {
    const p = $("#erro-geral"); p.textContent = msg; p.hidden = !msg;
    if (msg) setTimeout(() => { p.hidden = true; }, 8000);
  }

  // ------------------------------------------------------------------ administração

  const quando = (t) => new Date(t).toLocaleDateString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric" });

  async function pintarAdmin() {
    const modo = { convite: "Cadastro por convite: só entra quem receber um link gerado aqui.", aberto: "Cadastro aberto: qualquer pessoa com o endereço pode criar conta.", fechado: "Cadastro fechado: ninguém mais cria conta (nem com convite)." };
    $("#admin-modo").textContent = (modo[instancia?.cadastro] || "") + " Muda pela variável TRACOLAB_CADASTRO." + (instancia?.cadastro === "aberto" ? " Um convite ainda serve para mandar o endereço certo." : "");
    $("#btn-convite").disabled = instancia?.cadastro === "fechado";
    try {
      const [usuarios, convites] = await Promise.all([api("GET", "api/admin/usuarios"), api("GET", "api/admin/convites")]);
      $("#convites-abertos").textContent = convites.length ? `${convites.length} convite(s) em aberto.` : "Nenhum convite em aberto.";
      const tabela = $("#tabela-usuarios");
      tabela.innerHTML = "";
      for (const u of usuarios) {
        const eu = u.id === conta.id;
        const linha = document.createElement("div");
        linha.className = "linha-u"; linha.setAttribute("role", "row");
        linha.innerHTML = `<div class="nome-u">${escapar(u.usuario)}${u.admin ? ' <span class="selo">admin</span>' : ""}${eu ? ' <span class="selo">você</span>' : ""}</div>
          <div class="meta-u">desde ${quando(u.criadoEm)} · ${u.folhas} folha(s) · 2FA ${u.doisFatores ? "ativa" : "inativa"}</div>
          <div class="acoes-u"></div>`;
        const acoes = $(".acoes-u", linha);
        if (!eu) {
          acoes.append(botao(u.admin ? "Tirar admin" : "Tornar admin", () => agir(() => api("POST", `api/admin/usuarios/${u.id}/admin`, { admin: !u.admin }))));
          if (u.doisFatores) acoes.append(botao("Zerar 2FA", () => confirmarAcao(acoes, `Zerar a verificação em duas etapas de ${u.usuario}?`, () => api("POST", `api/admin/usuarios/${u.id}/2fa/remover`))));
          acoes.append(botao("Remover", () => confirmarAcao(acoes, `Remover ${u.usuario} e todas as folhas dessa conta?`, () => api("DELETE", `api/admin/usuarios/${u.id}`)), "perigo"));
        }
        tabela.append(linha);
      }
    } catch (e) { $("#admin-erro").textContent = e.message; $("#admin-erro").hidden = false; }
  }

  function botao(texto, acao, classe = "") { const b = document.createElement("button"); b.type = "button"; b.textContent = texto; if (classe) b.className = classe; b.addEventListener("click", acao); return b; }
  async function agir(f) { try { await f(); await pintarAdmin(); } catch (e) { mostrarErroGeral(e.message); } }
  function confirmarAcao(caixa, pergunta, f) {
    caixa.innerHTML = `<span class="pergunta">${escapar(pergunta)}</span>`;
    caixa.append(botao("Sim", () => agir(f), "perigo"), botao("Cancelar", () => pintarAdmin()));
  }
  const escapar = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  $("#btn-convite").addEventListener("click", async () => {
    try {
      const c = await api("POST", "api/admin/convites");
      $("#convite-link").textContent = c.link;
      $("#convite-novo").hidden = false;
      pintarAdmin();
    } catch (e) { mostrarErroGeral(e.message); }
  });
  $("#btn-copiar-convite").addEventListener("click", () => copiar($("#convite-link").textContent, $("#btn-copiar-convite")));
  $("#btn-cancelar-convites").addEventListener("click", () => agir(async () => { await api("DELETE", "api/admin/convites"); $("#convite-novo").hidden = true; }));

  // ------------------------------------------------------------------ início

  async function iniciar() {
    try {
      instancia = await api("GET", "api/instancia");
      try { conta = await api("GET", "api/conta"); } catch (e) { if (e.status !== 401) throw e; }
      if (conta) return pintarConta();
      if (instancia.primeiroAcesso) return mostrar("primeiro");
      const convite = params.get("convite");
      $("#entrar-cadastro").hidden = instancia.cadastro !== "aberto";
      $("#entrar-convite").hidden = instancia.cadastro !== "convite";
      $("#campo-convite").hidden = instancia.cadastro !== "convite";
      if (convite) { const c = $("#form-cadastrar input[name=convite]"); c.value = convite; c.readOnly = true; }
      if (instancia.cadastro === "fechado" && (convite || params.has("cadastrar"))) return mostrar("fechado");
      if (convite || (params.has("cadastrar") && instancia.cadastro === "aberto")) return mostrar("cadastrar");
      mostrar("entrar");
    } catch (e) {
      mostrarErroGeral(e instanceof TypeError ? "Sem conexão com o servidor." : e.message);
      mostrar("entrar");
    }
  }
  iniciar();
}
