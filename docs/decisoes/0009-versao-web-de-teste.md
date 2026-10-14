# 0009 — Versão web de teste antes do APK

- **Status:** Aceita
- **Data:** 2026-10-13

## Contexto

A interface é uma página web (ADR 0008), que mais tarde vai para dentro do APK. Cada ajuste no
APK exige compilar, transferir e reinstalar no tablet. Já na web, um ajuste aparece ao
recarregar a página. O comportamento da folha (pendências, chips "falta", ordem, unidades) só
fica claro com uso real, por dias.

## Decisão

Antes do APK, a página roda como **versão web de teste**, servida pelo `servidor-dev` (núcleo
na JVM + `web/`), para uso diário no navegador do tablet:

- implantação por `ferramentas/implantar-web.sh` como serviço systemd, sob um usuário de
  sistema sem privilégios;
- **um endereço só**: as folhas ficam salvas no navegador por endereço, então trocar de endereço
  "perderia" as folhas. Um proxy na frente encaminha para a instância principal e, se ela
  estiver desligada, para uma instância de reserva;
- acesso por rede local ou VPN; um endereço público só **atrás de autenticação na borda**
  (ex.: Cloudflare Access), porque o `servidor-dev` não tem autenticação nem limites de tempo
  por cálculo. O endereço público aponta só para a instância principal, sem usar a reserva;
- o cabeçalho `X-TracoLab-Motor` mostra na página qual instância está calculando.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Ir direto para o APK | Ciclo de ajuste lento; a experiência de uso ainda está sendo descoberta |
| Uma instância só | A máquina principal fica desligada parte do tempo para economizar energia |
| Endereços separados para principal e reserva | As folhas salvas não apareceriam no outro endereço |

## Consequências

- Todo o projeto gera bytecode Java 17, o mínimo das máquinas de destino e do Android.
- Antes de qualquer exposição pública, o servidor precisa de autenticação e de limites de
  tempo e memória por cálculo (ver ADR 0006, webservice).
- As folhas vivem só no navegador (`localStorage`). Exportar e importar arquivos fica para uma
  etapa seguinte.
