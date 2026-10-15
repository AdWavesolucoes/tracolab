"use strict";
// Várias folhas no aparelho e o arquivo .tlab (exportar/importar pelo navegador, sem servidor).
//
// Armazenamento (localStorage, por endereço):
//   tracolab.folha.v2             a folha aberta, como sempre foi (nunca muda de lugar: nada a migrar)
//   tracolab.folhas.v1            índice: { versao, folhas: [{ id, nome, atualizada }] }
//   tracolab.folha.arquivo.<id>   as outras folhas, guardadas ao trocar de folha
// A folha aberta ganha um `id` na primeira vez; até lá ela é "a folha do aparelho" de antes.
{
  const INDICE = "tracolab.folhas.v1";
  const ARQ = "tracolab.folha.arquivo.";
  const FORMATO = "tracolab";
  const VERSAO = 1;
  const MAX_REGIOES = 5000;

  const ler = (chave) => { try { return JSON.parse(localStorage.getItem(chave) || "null"); } catch (_) { return null; } };
  const gravar = (chave, v) => { try { localStorage.setItem(chave, JSON.stringify(v)); return true; } catch (_) { return false; } };
  const novoIdFolha = () => "f" + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);

  function indice() {
    const i = ler(INDICE);
    return i && Array.isArray(i.folhas) ? i : { versao: 1, folhas: [] };
  }

  /** Dá id à folha aberta (a do aparelho, de antes das várias folhas) e a põe no índice. */
  function garantirIndice() {
    if (!estado.id) { estado.id = novoIdFolha(); salvar(); }
    const i = indice();
    const e = i.folhas.find((f) => f.id === estado.id);
    if (e) { e.nome = estado.nome; e.atualizada = Date.now(); } else i.folhas.push({ id: estado.id, nome: estado.nome, atualizada: Date.now() });
    gravar(INDICE, i);
    return i;
  }

  /** Guarda a folha aberta na sua chave de arquivo (antes de trocar de folha). */
  function arquivar() {
    garantirIndice();
    return gravar(ARQ + estado.id, estado);
  }

  /** Abre os dados de uma folha no lugar da atual (o histórico de desfazer recomeça). */
  function aplicar(dados) {
    estado = Object.assign({ nome: "Folha sem título", modo: "parcial", ordem: false, regioes: [] }, dados);
    proximoId = 1;
    for (const r of estado.regioes) proximoId = Math.max(proximoId, (Number(String(r.id).replace(/\D/g, "")) || 0) + 1);
    historico.length = 0;
    posHist = -1;
    ultimaResposta = null;
    nomeFolha.value = estado.nome;
    document.title = `${estado.nome} · TraçoLab`;
    selModo.value = estado.modo;
    aplicarOrdem();
    montar();
    registrar();
    salvar();
    garantirIndice();
    agendar(0, false);
    pintarLista();
  }

  function abrir(id) {
    if (id === estado.id) return;
    const dados = ler(ARQ + id);
    if (!dados || !Array.isArray(dados.regioes)) return aviso("Não foi possível abrir essa folha.");
    if (!arquivar()) return aviso("Sem espaço no aparelho para guardar a folha atual.");
    aplicar(dados);
  }

  function nova(dados = {}) {
    if (!arquivar()) return aviso("Sem espaço no aparelho para guardar a folha atual.");
    aplicar(Object.assign({ nome: "Folha sem título", modo: estado.modo, ordem: false, regioes: [] }, dados, { id: novoIdFolha() }));
  }

  function duplicar(id) {
    const dados = id === estado.id ? estado : ler(ARQ + id);
    if (!dados) return;
    const copia = JSON.parse(JSON.stringify(dados));
    copia.id = novoIdFolha();
    copia.nome = `${dados.nome || "Folha"} (cópia)`;
    if (!gravar(ARQ + copia.id, copia)) return aviso("Sem espaço no aparelho para a cópia.");
    const i = garantirIndice();
    i.folhas.push({ id: copia.id, nome: copia.nome, atualizada: Date.now() });
    gravar(INDICE, i);
    pintarLista();
  }

  function renomear(id, nome) {
    nome = nome.trim() || "Folha sem título";
    if (id === estado.id) {
      estado.nome = nome; nomeFolha.value = nome; document.title = `${nome} · TraçoLab`; salvar();
    } else {
      const dados = ler(ARQ + id);
      if (dados) { dados.nome = nome; gravar(ARQ + id, dados); }
    }
    const i = garantirIndice();
    const e = i.folhas.find((f) => f.id === id);
    if (e) { e.nome = nome; gravar(INDICE, i); }
    pintarLista();
  }

  function apagar(id) {
    const i = garantirIndice();
    i.folhas = i.folhas.filter((f) => f.id !== id);
    gravar(INDICE, i);
    try { localStorage.removeItem(ARQ + id); } catch (_) { /* ignora */ }
    if (id !== estado.id) return pintarLista();
    // apagou a aberta: abre a mais recente das outras, ou começa uma em branco
    const outra = [...i.folhas].sort((a, b) => b.atualizada - a.atualizada)[0];
    const dados = outra && ler(ARQ + outra.id);
    if (dados) aplicar(dados);
    else aplicar({ id: novoIdFolha(), nome: "Folha sem título", modo: estado.modo, regioes: [] });
  }

  // ------------------------------------------------------------------ arquivo .tlab

  function exportar() {
    garantirIndice();
    const conteudo = {
      formato: FORMATO, versao: VERSAO, nome: estado.nome, exportado: new Date().toISOString(),
      folha: { modo: estado.modo, ordem: !!estado.ordem, regioes: estado.regioes },
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
    return { dados: { nome, modo: f.modo === "classico" ? "classico" : "parcial", ordem: f.ordem === true, regioes }, ignoradas };
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
      nova(dados);
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
  const lista = secao.querySelector(".lista-folhas");
  const avisoEl = secao.querySelector(".aviso-folha");
  let tempoAviso = null;
  function aviso(t) {
    avisoEl.textContent = t;
    clearTimeout(tempoAviso);
    tempoAviso = setTimeout(() => { avisoEl.textContent = ""; }, 6000);
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
    const i = garantirIndice();
    lista.innerHTML = "";
    for (const f of [...i.folhas].sort((a, b) => b.atualizada - a.atualizada)) {
      const atual = f.id === estado.id;
      const item = document.createElement("div");
      item.className = "fl" + (atual ? " atual" : "");
      item.setAttribute("role", "listitem");
      item.dataset.id = f.id;
      item.innerHTML = `<button class="fl-abrir" ${atual ? 'aria-current="true"' : ""}>
          <span class="fl-nome">${escapar(f.nome || "Folha sem título")}</span>
          <span class="fl-data">${atual ? "aberta" : quando(f.atualizada)}</span></button>
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
      lista.appendChild(item);
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

  rail.addEventListener("click", () => {
    const abrirPainel = secao.hidden;
    secao.hidden = !abrirPainel;
    if (abrirPainel) {
      painel.hidden = true; ajustes.hidden = true;
      railVars.classList.remove("on"); railVars.setAttribute("aria-pressed", "false");
      railAjustes.classList.remove("on"); railAjustes.setAttribute("aria-expanded", "false");
      pintarLista();
    }
    rail.classList.toggle("on", abrirPainel);
    rail.setAttribute("aria-expanded", String(abrirPainel));
  });
  for (const outro of [railVars, railAjustes]) outro.addEventListener("click", () => {
    secao.hidden = true; rail.classList.remove("on"); rail.setAttribute("aria-expanded", "false");
  });

  // a folha aberta entra no índice depois do primeiro cálculo (e o nome da barra o acompanha)
  TL.aposCalculo.push(() => { if (!estado.id || !indice().folhas.some((f) => f.id === estado.id)) garantirIndice(); });
  nomeFolha.addEventListener("change", () => { garantirIndice(); pintarLista(); });

  TL.folhas = { abrir, nova, duplicar, renomear, apagar, exportar, validar };   // para os testes de interface
}
