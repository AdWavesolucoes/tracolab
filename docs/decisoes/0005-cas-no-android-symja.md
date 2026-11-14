# 0005 — Symja como motor simbólico no Android

- **Status:** Substituída por [0019](0019-servico-web-multiusuario.md) em 2026-11-14 (o Symja continua como motor; o alvo Android saiu). Era: Aceita (JVM em 2026-10-12; Galaxy Tab S10 Lite em 2026-10-13)
- **Data:** 2026-10-12

## Contexto

O estado parcial ([ADR 0002](0002-modo-parcial-como-padrao.md)) exige um CAS no aparelho,
funcionando **offline**. O app é para tablet Android.

## Decisão (proposta)

Usar o **Symja** (biblioteca Java de álgebra computacional) chamado a partir de Kotlin.

## Alternativas consideradas

| Alternativa | Prós | Contras |
|---|---|---|
| **Symja** | Java puro, roda na JVM, offline; licença **LGPL-3.0** | Dependências somam 43 MB; desempenho no tablet a medir |
| SymPy via Chaquopy | Mesmo motor do protótipo | Embute um interpretador Python inteiro; APK grande; inicialização lenta |
| CAS em servidor | APK leve | Exige rede; não funciona em campo |
| CAS próprio | Controle total | Esforço enorme; reinventar a roda |

## Resultado da prova de conceito (2026-10-12)

Veja [prova de conceito Kotlin + Symja](../pesquisa/prova-de-conceito-kotlin-symja.md):
**30/30 casos** da especificação na JVM, recálculo de 110 regiões em 30 ms e inicialização em ~1 s.
A licença dos módulos usados é **LGPL-3.0**, não GPL como se supunha antes.

## Pendências

1. ~~Rodar os casos de comportamento portados~~: feito, 30/30.
2. ~~Confirmar a licença~~: LGPL-3.0 (`matheclipse-core`, `-parser`, `-external`).
3. ~~Medir no tablet~~: 30/30 casos; 110 regiões em 33–63 ms; inicialização em 270 ms ([resultado](../pesquisa/prova-de-conceito-kotlin-symja.md#fase-2-no-tablet-2026-10-13)).
4. ~~Tamanho do APK~~: 8,5 MB com R8.

## Condições para usar

- Levar junto a `LogManager` substituta do Log4j (o Symja do Maven não inicia no Android sem ela).
- Regras do R8 para as bibliotecas que usam reflexão e o empacotamento descrito no resultado.
