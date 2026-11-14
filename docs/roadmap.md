# Roadmap

## Fase 0 — Entender o comportamento ✅

- [x] Estudar planilhas de referência e medir a ordem, a propagação de erro e as redefinições
- [x] Escrever o [modelo de avaliação](comportamento/modelo-de-avaliacao.md)

## Fase 1 — Protótipo do motor (Python + SymPy) ✅

- [x] Estados numérico / parcial / erro / não suportado; dois modos (parcial e clássico); recálculo incremental
- [x] Funções, faixas, vetores, programas simples, Σ/∫/d/dx; unidades de medida ([ADR 0007](decisoes/0007-unidades-explicitas.md))
- [x] Validação contra resultados de referência; casos de comportamento em JSON neutro ([spec/](../spec/README.md))

## Fase 2 — Núcleo em Kotlin ✅

- [x] Kotlin + Symja como **biblioteca sem dependência de plataforma** ([ADR 0006](decisoes/0006-nucleo-unico-multiplataforma.md)), passando a mesma especificação
- [x] Medido também num tablet Android (30/30, 110 regiões em 33–63 ms) — alvo depois abandonado ([ADR 0019](decisoes/0019-servico-web-multiusuario.md))

## Fase 3 — A folha ✅

- [x] Regiões posicionáveis, teclado matemático, estado parcial com chips "falta F", erro de ordem, desfazer ([ADR 0008](decisoes/0008-interface-web-mathlive.md))
- [x] Identidade visual e estrutura do design "Tablet B"
- [x] Vetores e matrizes ([ADR 0010](decisoes/0010-vetores-e-matrizes.md)); Σ, Π, ∫ e d/dx ([ADR 0011](decisoes/0011-calculo.md)); resolver ([ADR 0012](decisoes/0012-resolver.md))
- [x] Condicionais, lógica, textos e verificações ([ADR 0013](decisoes/0013-condicionais-logica-e-verificacoes.md)); unidades da folha e formato ([ADR 0014](decisoes/0014-unidades-da-folha-e-formato.md))
- [x] Folha como documento: texto, definir e mostrar, substituição, `.tlab`, A4 e impressão ([ADR 0015](decisoes/0015-folha-como-documento.md))
- [x] Gráficos X-Y ([ADR 0016](decisoes/0016-graficos.md)); limites por cálculo ([ADR 0017](decisoes/0017-limites-de-calculo.md)); funções ([ADR 0018](decisoes/0018-funcoes.md))

## Fase 4 — Serviço web multiusuário ([ADR 0019](decisoes/0019-servico-web-multiusuario.md)) 🛠️

- [x] Servidor Ktor no lugar do servidor de desenvolvimento, com os mesmos limites e cabeçalhos
- [x] SQLite com migrações; contas (Argon2id), sessões, limite de tentativas, CSRF sem token
- [x] Verificação em duas etapas (TOTP + códigos de recuperação); primeiro acesso vira administrador; convites
- [x] Folhas por usuário com versão e conflito (409); rascunho local; migração das folhas que estavam no navegador
- [x] Página de conta e administração; PWA (manifesto, ícones, service worker)
- [x] Imagem Docker multi-arquitetura, `docker-compose.yml`, instalador systemd, [guia de hospedagem](hospedar.md)
- [x] Harness do serviço (`ferramentas/harness/servico.sh`) e da interface (`ferramentas/harness/interface.sh`) no CI
- [ ] Publicar o repositório e a primeira imagem (`v0.1.0`)
- [ ] Compartilhar uma folha por link somente-leitura
- [ ] Recuperação de senha por e-mail (opcional, com SMTP configurado)
- [ ] Chaves de acesso (WebAuthn/passkeys) como segundo fator ou no lugar da senha
- [ ] Postgres como alternativa ao SQLite para instâncias maiores

## Núcleo — pendências

- [ ] `lim`; números complexos; programas (atribuição local, laços — desenho na ADR 0013)
- [ ] Zoom e unidade do eixo nos gráficos
- [ ] Subir a cobertura da validação contra o corpus de referência (hoje limitada por embutidos)

## Antes de publicar no GitHub

- [x] Nome definitivo: **TraçoLab** (2026-10-13)
- [x] Licença: **AGPL-3.0** (2026-11-14; o Symja é LGPL-3.0, compatível)
- [x] Conferir que nenhum arquivo ou dado de terceiros está no repositório, inclusive no histórico (2026-11-14: limpo)
- [x] README para quem chega sem contexto
