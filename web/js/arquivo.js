"use strict";
// Folhas da conta (ADR 0019) e o arquivo .tlab (exportar/importar pelo navegador).
//
// As folhas vivem no servidor (/api/folhas), uma por id, com número de versão. O navegador guarda
// só um rascunho da folha aberta (tracolab.rascunho) para não perder a última edição se a aba
// fechar antes de gravar; na volta, se o rascunho é da mesma versão que está no servidor e difere
// dela, ele é aplicado e gravado. Folhas que estavam no localStorage antes das contas
// (tracolab.folha.v2 e tracolab.folha.arquivo.*) são enviadas para a conta na primeira entrada.
{
  const RASCUNHO = "tracolab.rascunho";
  const ANTIGA = "tracolab.folha.v2";
  const ANTIGO_INDICE = "tracolab.folhas.v1";
  const ANTIGO_ARQ = "tracolab.folha.arquivo.";
  const FORMATO = "tracolab";
  const VERSAO = 1;
  const MAX_REGIOES = 5000;

  const ler = (chave) => { try { return JSON.parse(localStorage.getItem(chave) || "null"); } catch (_) { return null; } };
  const gravarLocal = (chave, v) => { try { localStorage.setItem(chave, JSON.stringify(v)); } catch (_) { /* modo privado etc. */ } };
  const apagarLocal = (chave) => { try { localStorage.removeItem(chave); } catch (_) { /* ignora */ } };

  let lista = [];   // {id, nome, versao, atualizadaEm} vindos do servidor

  /** O que vai ao servidor como conteúdo: tudo do estado menos id, versão e nome (colunas próprias). */
  function conteudoDe(e) {
    const { id, versao, nome, ...resto } = e;
    return resto;
  }

  /** Estado a partir de um registro do servidor ({id, nome, versao, conteudo}). */
  function estadoDe(reg) {
    return Object.assign({ modo: "parcial", ordem: false, regioes: [] }, reg.conteudo || {}, { id: reg.id, versao: reg.versao, nome: reg.nome || "Folha sem título" });
  }

  // ------------------------------------------------------------------ gravação no servidor

  const indicador = document.getElementById("gravacao");
  let temporizadorGravacao = null, gravando = false, pendente = false, ultimoEnviado = "", semRede = false;

  function marcar(texto, classe = "") { indicador.textContent = texto; indicador.className = "gravacao " + classe; }

  function agendarGravacao() {
    if (!estado.id) return;
    gravarLocal(RASCUNHO, estado);
    clearTimeout(temporizadorGravacao);
    temporizadorGravacao = setTimeout(gravar, 1200);
    if (JSON.stringify({ nome: estado.nome, conteudo: conteudoDe(estado) }) !== ultimoEnviado) marcar("…");
  }

  async function gravar() {
    if (!estado.id) return;
    if (gravando) { pendente = true; return; }
    const corpo = { nome: estado.nome, conteudo: conteudoDe(estado) };
    const texto = JSON.stringify(corpo);
    if (texto === ultimoEnviado) { marcar("salvo"); return; }
    gravando = true;
    const id = estado.id;
    try {
      const r = await TL.api("PUT", `api/folhas/${id}`, { ...corpo, versao: estado.versao });
      if (estado.id === id) { estado.versao = r.versao; ultimoEnviado = texto; gravarLocal(RASCUNHO, estado); marcar("salvo"); }
      semRede = false;
      atualizarItem(id, r);
    } catch (e) {
      if (e.status === 409 && estado.id === id) conflito(e.dados);
      else if (e.status === 404) { marcar("folha apagada", "erro"); aviso("Esta folha foi apagada em outro lugar. Exporte-a para não perder o trabalho."); }
      else if (e instanceof TypeError) { semRede = true; marcar("sem conexão", "erro"); setTimeout(agendarGravacao, 8000); }
      else if (e.status !== 401) { marcar("não salvo", "erro"); aviso("Não foi possível salvar: " + e.message); }
    } finally {
      gravando = false;
      if (pendente) { pendente = false; agendarGravacao(); }
    }
  }

  /** A aba está saindo com gravação pendente: manda o que der (sendBeacon não espera resposta). */
  document.addEventListener("visibilitychange", () => {
    if (document.visibilityState !== "hidden" || !estado.id || !temporizadorGravacao) return;
    const corpo = { nome: estado.nome, conteudo: conteudoDe(estado), versao: estado.versao };
    if (JSON.stringify({ nome: corpo.nome, conteudo: corpo.conteudo }) === ultimoEnviado) return;
    clearTimeout(temporizadorGravacao); temporizadorGravacao = null;
    navigator.sendBeacon?.(`api/folhas/${estado.id}`, new Blob([JSON.stringify(corpo)], { type: "application/json" }));
  });

  // ------------------------------------------------------------------ conflito (409)

  const banner = document.getElementById("banner");
  function conflito(servidor) {
    marcar("conflito", "erro");
    banner.innerHTML = `<span>Esta folha foi alterada em outro aparelho.</span>
      <button data-acao="servidor" class="secundario">Usar a versão de lá</button>
      <button data-acao="minha" class="secundario">Manter a minha</button>`;
    banner.hidden = false;
    banner.onclick = (ev) => {
      const acao = ev.target.closest("button")?.dataset.acao;
      if (!acao) return;
      banner.hidden = true;
      if (acao === "servidor") aplicar(estadoDe(servidor));
      else { estado.versao = servidor.versao; ultimoEnviado = ""; agendarGravacao(); }
    };
  }

  // ------------------------------------------------------------------ abrir, nova, duplicar...

  /** Abre os dados de uma folha no lugar da atual (o histórico de desfazer recomeça). */
  function aplicar(dados) {
    estado = Object.assign({ nome: "Folha sem título", modo: "parcial", ordem: false, regioes: [] }, dados);
    proximoId = 1;
    for (const r of estado.regioes) proximoId = Math.max(proximoId, (Number(String(r.id).replace(/\D/g, "")) || 0) + 1);
    historico.length = 0;
    posHist = -1;
    ultimaResposta = null;
    ultimoEnviado = JSON.stringify({ nome: estado.nome, conteudo: conteudoDe(estado) });
    nomeFolha.value = estado.nome;
    document.title = `${estado.nome} · TraçoLab`;
    selModo.value = estado.modo;
    aplicarOrdem();
    montar();
    registrar();
    salvar();
    marcar("salvo");
    agendar(0, false);
    pintarLista();
  }

  async function abrir(id) {
    if (id === estado.id) return;
    clearTimeout(temporizadorGravacao); await gravar();   // a atual vai antes
    try { aplicar(estadoDe(await TL.api("GET", `api/folhas/${id}`))); }
    catch (e) { aviso("Não foi possível abrir essa folha: " + e.message); }
  }

  /** Cria uma folha na conta com esses dados e abre. */
  async function nova(dados = {}) {
    clearTimeout(temporizadorGravacao); await gravar();
    const base = Object.assign({ nome: "Folha sem título", modo: estado.modo || "parcial", ordem: false, regioes: [] }, dados);
    delete base.id; delete base.versao;
    try {
      const r = await TL.api("POST", "api/folhas", { nome: base.nome, conteudo: conteudoDe(base) });
      lista.unshift({ id: r.id, nome: r.nome, versao: r.versao, atualizadaEm: r.atualizadaEm });
      aplicar(Object.assign(base, { id: r.id, versao: r.versao, nome: r.nome }));
      return r.id;
    } catch (e) { aviso("Não foi possível criar a folha: " + e.message); return null; }
  }

  async function duplicar(id) {
    try {
      const dados = id === estado.id ? estado : estadoDe(await TL.api("GET", `api/folhas/${id}`));
      const copia = JSON.parse(JSON.stringify(dados));
      copia.nome = `${dados.nome || "Folha"} (cópia)`;
      delete copia.id; delete copia.versao;
      const r = await TL.api("POST", "api/folhas", { nome: copia.nome, conteudo: conteudoDe(copia) });
      lista.unshift({ id: r.id, nome: r.nome, versao: r.versao, atualizadaEm: r.atualizadaEm });
      pintarLista();
    } catch (e) { aviso("Não foi possível duplicar: " + e.message); }
  }

  async function renomear(id, nome) {
    nome = nome.trim() || "Folha sem título";
    if (id === estado.id) {
      estado.nome = nome; nomeFolha.value = nome; document.title = `${nome} · TraçoLab`; salvar();
      atualizarItem(id, { nome }); pintarLista();
      return;
    }
    try {
      const reg = await TL.api("GET", `api/folhas/${id}`);
      const r = await TL.api("PUT", `api/folhas/${id}`, { nome, conteudo: reg.conteudo, versao: reg.versao });
      atualizarItem(id, { nome, versao: r.versao, atualizadaEm: r.atualizadaEm });
    } catch (e) { aviso("Não foi possível renomear: " + e.message); }
    pintarLista();
  }

  async function apagar(id) {
    try { await TL.api("DELETE", `api/folhas/${id}`); }
    catch (e) { if (e.status !== 404) { aviso("Não foi possível apagar: " + e.message); return; } }
    lista = lista.filter((f) => f.id !== id);
    if (id !== estado.id) return pintarLista();
    // apagou a aberta: abre a mais recente das outras, ou começa uma em branco
    clearTimeout(temporizadorGravacao); estado.id = null; apagarLocal(RASCUNHO);
    const outra = [...lista].sort((a, b) => b.atualizadaEm - a.atualizadaEm)[0];
    if (outra) { try { return aplicar(estadoDe(await TL.api("GET", `api/folhas/${outra.id}`))); } catch (_) { /* cria uma nova */ } }
    await nova({ nome: "Folha sem título", modo: estado.modo, regioes: [] });
  }

  function atualizarItem(id, campos) {
    const e = lista.find((f) => f.id === id);
    if (e) Object.assign(e, campos, { atualizadaEm: campos.atualizadaEm ?? Date.now() });
  }

  // ------------------------------------------------------------------ início: migrar, escolher e abrir

  /** Folhas guardadas no navegador antes das contas, para enviar à conta (e apagar de lá). */
  function antigas() {
    const saida = [];
    const aberta = ler(ANTIGA);
    if (aberta && Array.isArray(aberta.regioes) && aberta.regioes.some((r) => r.tipo || (r.latex || "").trim()))
      saida.push({ chave: ANTIGA, dados: aberta });
    const indice = ler(ANTIGO_INDICE);
    for (const f of indice?.folhas || []) {
      if (aberta && f.id === aberta.id) continue;
      const d = ler(ANTIGO_ARQ + f.id);
      if (d && Array.isArray(d.regioes)) saida.push({ chave: ANTIGO_ARQ + f.id, dados: d });
    }
    return saida;
  }

  async function migrar() {
    const velhas = antigas();
    if (!velhas.length) { apagarLocal(ANTIGA); apagarLocal(ANTIGO_INDICE); return 0; }
    let n = 0;
    for (const { chave, dados } of velhas) {
      const nome = dados.nome || "Folha do aparelho";
      const corpo = Object.assign({}, dados); delete corpo.id; delete corpo.versao; delete corpo.nome;
      try {
        const r = await TL.api("POST", "api/folhas", { nome, conteudo: corpo });
        lista.unshift({ id: r.id, nome: r.nome, versao: r.versao, atualizadaEm: r.atualizadaEm });
        apagarLocal(chave); n++;
      } catch (e) { aviso("Uma folha deste aparelho não pôde ser enviada para a conta: " + e.message); }
    }
    if (n === velhas.length) { apagarLocal(ANTIGA); apagarLocal(ANTIGO_INDICE); }
    return n;
  }

  /** Folha de exemplo da primeira entrada: tensão numa barra tracionada, esperando a carga. */
  function exemplo() {
    const ex = [
      [`d${DEF}20\\,\\mathrm{mm}`, "", 40, 48], [`A${DEF}\\frac{\\pi d^2}{4}`, "", 40, 132],
      [`\\sigma${DEF}\\frac{F}{A}`, "", 40, 240], ["\\sigma=", "\\,\\mathrm{MPa}", 40, 372],
    ];
    return { nome: "Barra tracionada", modo: "parcial", ordem: false, regioes: ex.map(([latex, unidade, x, y], i) => ({ id: "r" + (i + 1), latex, unidade, x, y })) };
  }

  /** Chamado por inicio.js depois da sessão: lista as folhas, migra as antigas, abre a certa. */
  async function iniciar() {
    lista = await TL.api("GET", "api/folhas");
    const migradas = await migrar();
    if (migradas) aviso(`${migradas} folha(s) deste aparelho entraram na sua conta.`);
    const rascunho = ler(RASCUNHO);
    if (rascunho?.id && lista.some((f) => f.id === rascunho.id)) {
      const reg = await TL.api("GET", `api/folhas/${rascunho.id}`);
      const servidor = estadoDe(reg);
      if (reg.versao === rascunho.versao && JSON.stringify(conteudoDe(servidor)) + servidor.nome !== JSON.stringify(conteudoDe(rascunho)) + rascunho.nome) {
        aplicar(rascunho);          // a última edição não chegou ao servidor: ela vale, e vai agora
        ultimoEnviado = ""; agendarGravacao();
      } else aplicar(servidor);
      return;
    }
    const recente = [...lista].sort((a, b) => b.atualizadaEm - a.atualizadaEm)[0];
    if (recente) return aplicar(estadoDe(await TL.api("GET", `api/folhas/${recente.id}`)));
    await nova(exemplo());
  }

  // ------------------------------------------------------------------ arquivo .tlab

  function exportar() {
    const conteudo = {
      formato: FORMATO, versao: VERSAO, nome: estado.nome, exportado: new Date().toISOString(),
      folha: Object.assign({ modo: estado.modo, ordem: !!estado.ordem }, conteudoDe(estado)),
    };
    const blob = new Blob([JSON.stringify(conteudo, null, 1)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = nomeArquivo(estado.nome) + ".tlab";
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
  }

  const nomeArquivo = (n) => (n || "folha").normalize("NFC").replace(/[\\/:*?"<>|\u0000-\u001f]+/g, " ").trim().slice(0, 80) || "folha";

  const numero = (v, padrao) => (typeof v === "number" && Number.isFinite(v) ? v : padrao);

  /** Confere um .tlab e devolve os dados de uma folha nova; lança Error com a mensagem para a tela. */
  function validar(obj) {
    if (!obj || typeof obj !== "object" || obj.formato !== FORMATO) throw new Error("Este arquivo não é uma folha do TraçoLab.");
    if (!Number.isInteger(obj.versao) || obj.versao < 1) throw new Error("Arquivo .tlab sem versão válida.");
    if (obj.versao > VERSAO) throw new Error("Este arquivo é de uma versão mais nova do TraçoLab.");
    const f = obj.folha;
    if (!f || !Array.isArray(f.regioes)) throw new Error("O arquivo não tem as regiões da folha.");
    if (f.regioes.length > MAX_REGIOES) throw new Error(`Folha grande demais (mais de ${MAX_REGIOES} regiões).`);
    let ignoradas = 0, n = 1;
    const regioes = [];
    for (const r of f.regioes) {
      if (!r || typeof r !== "object") { ignoradas++; continue; }
      const base = { id: "r" + n, x: Math.min(LARGURA, Math.max(0, numero(r.x, 40))), y: Math.min(1e6, Math.max(0, numero(r.y, 48))) };
      if (r.tipo === undefined || r.tipo === null) {
        if (typeof r.latex !== "string" || !r.latex.trim() || r.latex.length > 20000) { ignoradas++; continue; }
        regioes.push(Object.assign(base, { latex: r.latex, unidade: typeof r.unidade === "string" ? r.unidade : "",
          ...(r.substituir === true ? { substituir: true } : {}) }));
      } else if (r.tipo === "texto") {
        const html = TL.sanitizarTexto(typeof r.html === "string" ? r.html : "");
        if (!html.trim()) { ignoradas++; continue; }
        regioes.push(Object.assign(base, { tipo: "texto", estilo: typeof r.estilo === "string" ? r.estilo : "paragrafo", html }));
      } else if (typeof r.tipo === "string" && TL.tipos[r.tipo]) {
        // tipo de outra área (gráfico...): copia os dados como vieram, com id e posição conferidos
        regioes.push(Object.assign(JSON.parse(JSON.stringify(r)), base));
      } else { ignoradas++; continue; }
      n++;
    }
    const nome = typeof obj.nome === "string" && obj.nome.trim() ? obj.nome.trim().slice(0, 200) : "Folha importada";
    const { regioes: _r, modo: _m, ordem: _o, ...extras } = f;   // ajustes da folha (formato, unidades...) vêm junto
    return { dados: { ...extras, nome, modo: f.modo === "classico" ? "classico" : "parcial", ordem: f.ordem === true, regioes }, ignoradas };
  }

  const entrada = document.createElement("input");
  entrada.type = "file";
  entrada.accept = ".tlab,application/json";
  entrada.hidden = true;
  document.body.appendChild(entrada);
  entrada.addEventListener("change", async () => {
    const arq = entrada.files?.[0];
    entrada.value = "";
    if (!arq) return;
    if (arq.size > 20 * 1024 * 1024) return aviso("Arquivo grande demais.");
    try {
      const { dados, ignoradas } = validar(JSON.parse(await arq.text()));
      await nova(dados);
      aviso(ignoradas ? `Folha importada. ${ignoradas} região(ões) não reconhecida(s) ficaram de fora.` : "Folha importada.");
    } catch (e) {
      aviso(e instanceof SyntaxError ? "O arquivo não é um .tlab válido (JSON com erro)." : e.message);
    }
  });

  // ------------------------------------------------------------------ painel "Folhas"

  const rail = document.createElement("button");
  rail.id = "rail-folhas";
  rail.className = "rail";
  rail.setAttribute("aria-expanded", "false");
  rail.setAttribute("aria-controls", "folhas");
  rail.innerHTML = `<span class="pill"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M7 3h7l5 5v13H7z"/><path d="M14 3v5h5"/><path d="M4 6v15h11"/></svg></span>Folhas`;
  document.getElementById("rail-ajustes").before(rail);

  const secao = document.createElement("section");
  secao.id = "folhas";
  secao.className = "ajustes folhas";
  secao.hidden = true;
  secao.setAttribute("aria-label", "Folhas");
  secao.innerHTML = `<span class="painel-titulo">Folhas</span>
    <div class="acoes-folha">
      <button class="secundario" data-acao="nova">Nova folha</button>
      <button class="secundario" data-acao="importar">Importar .tlab</button>
      <button class="secundario" data-acao="exportar">Exportar esta folha (.tlab)</button>
      <button class="secundario" data-acao="imprimir">Imprimir / PDF</button>
    </div>
    <p class="aviso-folha" role="status" aria-live="polite"></p>
    <div class="lista-folhas" role="list"></div>`;
  document.getElementById("ajustes").after(secao);
  const listaEl = secao.querySelector(".lista-folhas");
  const avisoEl = secao.querySelector(".aviso-folha");
  let tempoAviso = null;
  function aviso(t) {
    avisoEl.textContent = t;
    clearTimeout(tempoAviso);
    tempoAviso = setTimeout(() => { avisoEl.textContent = ""; }, 8000);
  }

  secao.querySelector(".acoes-folha").addEventListener("click", (ev) => {
    const acao = ev.target.closest("button")?.dataset.acao;
    if (acao === "nova") nova();
    if (acao === "importar") entrada.click();
    if (acao === "exportar") exportar();
    if (acao === "imprimir") window.print();
  });

  const quando = (t) => new Date(t).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });

  function pintarLista() {
    if (secao.hidden) return;
    listaEl.innerHTML = "";
    for (const f of [...lista].sort((a, b) => b.atualizadaEm - a.atualizadaEm)) {
      const atual = f.id === estado.id;
      const item = document.createElement("div");
      item.className = "fl" + (atual ? " atual" : "");
      item.setAttribute("role", "listitem");
      item.dataset.id = f.id;
      item.innerHTML = `<button class="fl-abrir" ${atual ? 'aria-current="true"' : ""}>
          <span class="fl-nome">${escapar(f.nome || "Folha sem título")}</span>
          <span class="fl-data">${atual ? "aberta" : quando(f.atualizadaEm)}</span></button>
        <div class="fl-acoes">
          <button data-acao="renomear">Renomear</button><button data-acao="duplicar">Duplicar</button><button data-acao="apagar">Apagar</button>
        </div>`;
      item.querySelector(".fl-abrir").addEventListener("click", () => abrir(f.id));
      item.querySelector(".fl-acoes").addEventListener("click", (ev) => {
        const b = ev.target.closest("button");
        if (!b) return;
        if (b.dataset.acao === "duplicar") duplicar(f.id);
        if (b.dataset.acao === "renomear") editarNome(item, f);
        if (b.dataset.acao === "apagar") confirmarApagar(item, f);
      });
      listaEl.appendChild(item);
    }
  }

  function editarNome(item, f) {
    const campo = document.createElement("input");
    campo.className = "fl-campo";
    campo.value = f.nome || "";
    campo.setAttribute("aria-label", "Novo nome da folha");
    item.querySelector(".fl-abrir").replaceWith(campo);
    campo.focus(); campo.select();
    let feito = false;
    const fim = (ok) => { if (feito) return; feito = true; if (ok) renomear(f.id, campo.value); else pintarLista(); };
    campo.addEventListener("keydown", (ev) => { if (ev.key === "Enter") fim(true); if (ev.key === "Escape") fim(false); });
    campo.addEventListener("blur", () => fim(true));
  }

  function confirmarApagar(item, f) {
    const acoes = item.querySelector(".fl-acoes");
    acoes.innerHTML = `<span class="fl-pergunta">Apagar "${escapar(f.nome || "Folha")}"?</span><button data-sim class="perigo">Apagar</button><button data-nao>Cancelar</button>`;
    acoes.querySelector("[data-sim]").addEventListener("click", (ev) => { ev.stopPropagation(); apagar(f.id); });
    acoes.querySelector("[data-nao]").addEventListener("click", (ev) => { ev.stopPropagation(); pintarLista(); });
  }

  rail.addEventListener("click", async () => {
    const abrirPainel = secao.hidden;
    secao.hidden = !abrirPainel;
    if (abrirPainel) {
      painel.hidden = true; ajustes.hidden = true;
      railVars.classList.remove("on"); railVars.setAttribute("aria-pressed", "false");
      railAjustes.classList.remove("on"); railAjustes.setAttribute("aria-expanded", "false");
      pintarLista();
      try { lista = await TL.api("GET", "api/folhas"); pintarLista(); } catch (_) { /* fica a lista que já tinha */ }
    }
    rail.classList.toggle("on", abrirPainel);
    rail.setAttribute("aria-expanded", String(abrirPainel));
  });
  for (const outro of [railVars, railAjustes]) outro.addEventListener("click", () => {
    secao.hidden = true; rail.classList.remove("on"); rail.setAttribute("aria-expanded", "false");
  });

  nomeFolha.addEventListener("change", () => { estado.nome = nomeFolha.value.trim() || "Folha sem título"; nomeFolha.value = estado.nome; document.title = `${estado.nome} · TraçoLab`; salvar(); atualizarItem(estado.id, { nome: estado.nome }); pintarLista(); });

  TL.gravar = agendarGravacao;
  TL.gravarAgora = async () => { clearTimeout(temporizadorGravacao); await gravar(); };   // testes e troca de folha
  TL.folhas = { iniciar, abrir, nova, duplicar, renomear, apagar, exportar, validar };   // para inicio.js e os testes de interface
}
