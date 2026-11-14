# 0019 — TraçoLab como serviço web auto-hospedável, com contas

- **Status:** Aceita. Substitui [0005](0005-cas-no-android-symja.md) (o alvo Android) e [0009](0009-versao-web-de-teste.md) (a versão web era provisória; agora é o produto).
- **Data:** 2026-11-14

## Contexto

O plano original era um APK para o Galaxy Tab S10 Lite, com a página web como versão de teste
(ADR 0009). Um mês de uso da versão web mostrou que ela já entrega o essencial, e o custo do
caminho Android (WebView, ponte nativa, loja, assinatura, S Pen com SDK pago) não se paga para um
projeto que quer ser **usado por outras pessoas e hospedado por elas**.

O que falta à versão web para isso não é cálculo: é **conta de usuário**, **folhas no servidor**
(hoje no `localStorage`, por endereço) e **instalação simples** em qualquer máquina.

Decisões do autor em 2026-11-14: licença **AGPL-3.0**; cadastro **configurável, com padrão por
convite**; a instância dele fica **interna** (rede local e VPN), sem túnel público, e o
repositório é que permite a qualquer pessoa hospedar na internet seguindo a licença.

## Decisão

O TraçoLab passa a ser um **serviço web** — um único processo na JVM, com o núcleo dentro — que
qualquer pessoa instala e no qual cada usuário tem a sua conta e as suas folhas:

1. **Servidor** em Kotlin com **Ktor** (motor CIO), módulo `servidor/`, no lugar do
   `servidor-dev`. Mesmas rotas de hoje (`/api/calcular`, arquivos de `web/`), mesmos limites
   (ADR 0017) e cabeçalhos de segurança.
2. **Dados em SQLite**, num diretório só (`TRACOLAB_DADOS`): usuários, sessões, convites, folhas.
   Um arquivo, um volume, uma cópia de segurança. Migrações numeradas dentro do servidor.
3. **Contas**: usuário e senha (Argon2id); sessão em cookie `HttpOnly`, `SameSite=Strict`;
   **verificação em duas etapas por TOTP** (RFC 6238, qualquer app autenticador) com 10 códigos de
   recuperação de uso único; limite de tentativas de entrada por usuário e por endereço; CSRF
   barrado por `Sec-Fetch-Site`/`Origin` e por exigir JSON nos pedidos que alteram algo.
4. **Primeiro acesso**: a primeira conta criada numa instância vira administradora. Depois,
   `TRACOLAB_CADASTRO` decide: `convite` (padrão: o administrador gera links), `aberto` ou
   `fechado`. O administrador lista usuários, gera convites, remove contas e zera o 2FA de quem
   se trancou para fora.
5. **Folhas por usuário**, salvas pela API (`/api/folhas`) com **número de versão**: salvar com
   versão velha devolve 409 e a versão atual, para o navegador resolver. O `localStorage` passa a
   ser rascunho: na primeira entrada, as folhas que estavam no aparelho são enviadas para a conta.
   O arquivo `.tlab` continua existindo para levar folhas entre instâncias.
6. **Instalação**: imagem Docker multi-arquitetura (amd64 e arm64) publicada pelo CI, com
   `docker-compose.yml` de exemplo e todas as opções por variável de ambiente; e um script para
   quem prefere `systemd` com Java 17+. O servidor não faz TLS: fica atrás de um proxy (Caddy,
   nginx, túnel) quando exposto, e avisa isso na documentação.
7. **PWA**: manifesto e service worker para a página ser instalada na tela inicial do tablet, em
   tela cheia, no lugar do APK. A S Pen continua escrevendo via eventos de ponteiro;
   reconhecimento de caligrafia sai do roadmap.

O `nucleo`, a `api`, a `spec` e a interface não mudam de papel. O `compat/log4j-android` deixa de
ser necessário e fica no histórico.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Seguir para o APK | Ciclo lento, loja, SDK pago para a S Pen; não serve a quem quer hospedar |
| Manter o `HttpServer` do JDK e acrescentar contas | Roteamento, sessões e cookies à mão; para um projeto aberto, um framework conhecido convida contribuição |
| Postgres | Mais uma peça para quem instala; o SQLite aguenta com folga uma instância doméstica ou de equipe. Pode entrar depois atrás da mesma camada de dados |
| Login por provedor externo (Google, GitHub) | Dependência de terceiros para quem hospeda na rede local sem internet; pode ser opção futura |
| Verificação por e-mail/SMS | Exige SMTP ou operadora; o TOTP funciona sem nada além do app autenticador |
| Senhas sem segundo fator | Instâncias públicas ficariam expostas a senhas vazadas |
| Guardar as folhas só no navegador | Perde tudo ao limpar o navegador; não acompanha o usuário entre aparelhos |

## Consequências

- O ADR 0006 (núcleo único) se confirma: o mesmo `nucleo` serve a JVM do servidor; o alvo Android
  sai, Linux e webservice se fundem no serviço web.
- `ferramentas/harness/servico.sh` é a **especificação executável do serviço**: sobe o servidor
  limpo e confere página, API, contas, 2FA, CSRF, folhas e reinício. Roda no CI e antes de
  qualquer implantação.
- A reserva na OP4 (ADR 0009) deixa de fazer sentido: duas instâncias teriam dois bancos. Fica
  **uma** instância por casa.
- Antes da publicação: licença AGPL-3.0 no repositório, auditoria do histórico (feita em
  2026-11-14: sem arquivos de terceiros), README para quem chega de fora.
- Segredos do TOTP ficam no banco em claro (quem lê o banco já tem as senhas em hash e as sessões);
  a proteção é do arquivo. Criptografia com chave do ambiente é uma melhoria possível.
