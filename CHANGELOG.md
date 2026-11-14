# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), com
versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Não publicado]

### Mudado (14/11) — TraçoLab vira serviço web multiusuário (ADR 0019)
- O alvo deixa de ser um APK: o produto é o **serviço web auto-hospedável**, instalado por Docker (imagem multi-arquitetura) ou systemd, com cada pessoa na sua conta. Licença definida: **AGPL-3.0**.
- `servidor/` (Ktor 3.6, Kotlin 2.3) substitui o `servidor-dev`: mesmas rotas, limites e cabeçalhos de antes, mais contas, folhas e administração. Dados em SQLite (`TRACOLAB_DADOS`) com migrações numeradas.
- Contas: usuário e senha (Argon2id), sessão em cookie `HttpOnly`/`SameSite=Strict`, limite de tentativas por usuário e por IP, CSRF barrado por `Sec-Fetch-Site`/`Origin` e pela exigência de JSON. Verificação em duas etapas por TOTP com QR e 10 códigos de recuperação. A primeira conta é a do administrador; depois, `TRACOLAB_CADASTRO` = `convite` (padrão), `aberto` ou `fechado`.
- Administração: convites (link de 7 dias, uso único), lista de usuários, tornar administrador, zerar 2FA, remover conta.
- Folhas por usuário em `/api/folhas`, com número de versão: gravar com versão velha dá 409 e a página oferece "usar a de lá" ou "manter a minha". Gravação automática 1,2 s depois da edição, indicador na barra, `sendBeacon` ao sair da aba, rascunho local para a última edição não se perder. As folhas que estavam no navegador (`localStorage`) sobem para a conta na primeira entrada. `.tlab` continua.
- Página de conta (`conta.html`): primeiro acesso, entrar com segunda etapa, criar conta por convite, trocar senha, ativar/desativar 2FA, baixar os códigos, administração.
- PWA: `manifest.webmanifest`, ícones e service worker (rede primeiro para os arquivos próprios, cache primeiro para `vendor/`, nunca para a API).
- Empacotamento: `Dockerfile` multi-stage, `docker-compose.yml`, `ferramentas/instalar-systemd.sh` (substitui `implantar-web.sh`), CI publicando a imagem no GHCR a cada tag `v*`. Guia em `docs/hospedar.md`.
- Especificação executável do serviço: `ferramentas/harness/servico.sh` (109 verificações: página, API, contas, CSRF, 2FA, bloqueio, folhas, administração, reinício) e `ferramentas/harness/interface.sh` (9 roteiros Playwright, 141 verificações, inclusive a página de conta com 2FA e convite de ponta a ponta).
- O log4j do Symja passa a sair pelo slf4j (sem o aviso no boot). `web/base.css` concentra fontes e tokens para a folha e a página de conta.

### Adicionado (15/10, áreas feitas por agentes em paralelo)
- Pontos de extensão para trabalho em paralelo: núcleo em arquivos, registro de embutidos, `spec/casos/` por área, interface em `web/js/` com `TL`.
- Folha como documento (ADR 0015): regiões de texto, definir e mostrar (`R := qL/2 = 36 kN`), substituição explícita dos valores (memorial), várias folhas, arquivo `.tlab`, páginas A4, impressão/PDF.
- Limites de cálculo (ADR 0017): prazo por região e por folha, recursão, tamanho e limites do Symja. O que passa vira erro só na região. Pool de motores na API; CSP e cabeçalhos de segurança; unit systemd endurecida.
- Vetores e matrizes (ADR 0010): índices, produto, transposta, inversa, determinante, `lsolve` com unidades, autovalores, ordenação, `vec`; primeiro índice 0/1 nos ajustes; teclas de matriz.
- Unidades da prática brasileira (tf, mca, cv, h, pol...) e °C/°F absolutas; resultados nas unidades da folha; formato dos números por folha e por resultado (ADR 0014).
- Se, por partes, lógica (∧ ∨ ⊕ ¬), comparação em cadeia, textos e verificações "atende / não atende" com utilização (ADR 0013).
- `bancada lote`: avaliação de folhas em lote. Testes de propriedade e fuzz do conversor LaTeX. Validação do núcleo Kotlin contra o corpus de referência: 22,4% de cobertura, 95,9% de acerto.

- Σ, Π, ∫ e d/dx com unidades e pendências (ADR 0011); resolver/raiz/raizes com unidade deduzida (ADR 0012); funções de números, estatística e interpolação, `n!` (ADR 0018); gráficos X-Y em SVG com trace (ADR 0016).

### Corrigido (15/10)
- Unidade pedida incompatível agora diz o que falta ("σ está em 1/m²; para MPa falta uma força. Sem unidade: F") e o chip "F em kN" acrescenta a unidade no número digitado sem ela (relato do usuário).
- Interface: resposta antiga do núcleo não pinta mais por cima da nova; desfazer logo depois de digitar pulava dois passos; faixa de teclas cortada em retrato.
- `10^10^10` travava a folha; recursão infinita e fórmulas fundas derrubavam a conexão; estouro numérico derrubava a folha inteira; cliente lento ou cálculo pesado prendia o servidor; arquivos estáticos aceitavam pasta vizinha com o mesmo prefixo.
- Raiz de grandeza com unidade e número não real derrubavam a página; Hz × 1/s; índices de vetor; faixas com unidade; redefinição a partir de erro; recálculo ao mover definição; recorrência `v[i] := v[i-1]·2`; `a = b =`; `|x|` no editor; zero com unidade pedida.
- Regiões abaixo da primeira página saíam do papel; unidade `kN·m` com asterisco; teclas da camada Operadores vinham com um índice vazio.

### Corrigido
- 2026-10-13: na interface, o texto digitado não era lido e a região sumia ao tocar fora. O `<math-field>` era preenchido antes de o MathLive ativá-lo, e a atribuição encobria a propriedade `value`. O Enter agora confirma e abre uma região abaixo. Teste de interface em `ferramentas/teste-ui/` (11 verificações).

### Adicionado
- 2026-10-13: versão web de teste (ADR 0009): `ferramentas/implantar-web.sh` instala o serviço systemd `tracolab-web`; o cabeçalho `X-TracoLab-Motor` mostra qual instância calcula; todo o projeto em bytecode Java 17.
- 2026-10-14: identidade visual na interface: papel, nanquim, vermelhão e cobalto; fontes IBM Plex e Bricolage embutidas; marca e favicon. Estrutura do design "Tablet B": barra com resumo, trilho, painel de variáveis, folha A4 e faixa de teclas.
- 2026-10-14: estado pendente com chips "falta F" (o toque cria `F :=` acima de quem usa), erro de ordem ("só é definida mais abaixo"), unidade do resultado como chip trocável (`σ = 100 MPa`), desfazer/refazer, numeração da ordem e nome da folha. A API ganhou `define` e `abaixo`.
- 2026-10-13: interface web (`web/`) com MathLive 0.110.0 embutido, conversor LaTeX ⇄ núcleo em Kotlin (20 testes), módulo `api` (JSON) e `servidor-dev`; ADR 0008.
- 2026-10-13: núcleo Kotlin no repositório (`nucleo/`, `compat/log4j-android/`, `bancada/`), wrapper do Gradle com hash fixado, testes JUnit gerados da especificação e CI no GitHub Actions. O modo de validação passa a se chamar `classico`, e a mensagem de variável indefinida passa a ser "Variável não definida.".
- 2026-10-13: projeto nomeado **TracoLab** (traço da S Pen); repositório Git iniciado.
- 2026-10-13: núcleo testado no Galaxy Tab S10 Lite: 30/30 casos, recálculo de 110 regiões em 33–63 ms, APK de 8,5 MB; ADR 0005 aceita; `LogManager` substituta para o Symja rodar no Android.
- 2026-10-12: especificação executável `spec/casos-comportamento.json` (30 casos), rodada pelo protótipo Python e pelo núcleo Kotlin.
- 2026-10-12: prova de conceito do núcleo em Kotlin + Symja: 30/30 casos, recálculo de 110 regiões em 30 ms; licença do Symja confirmada como LGPL-3.0.
- 2026-10-12: unidades de medida no motor (SI + imperiais, conversão, checagem de dimensão, exibição na unidade pedida); ADR 0007; 11 testes novos; validação em 29,2% de cobertura e 94,0% de acerto.
- 2026-10-12: ADR 0006 (proposta): núcleo único para Android, Linux e webservice; fase 4 no roadmap.
- 2026-10-12: vault/repositório criado, com a documentação do estudo de comportamento, o modelo de
  avaliação, os registros de decisão 0001 a 0005 e o roadmap.
- 2026-10-12: protótipo do motor em Python + SymPy, com dois modos (`classico` e `parcial`),
  recálculo incremental e 17 testes de comportamento.
- 2026-10-12: extrator de planilhas usado no estudo, para ler o formato e gerar os casos de validação.
