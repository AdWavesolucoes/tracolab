# 0018 — Biblioteca de funções (números, tabelas, estatística)

Status: aceita.

- Três módulos novos de embutidos: `Numericas`, `Estatistica`, `Interpolacao` (comportamento em
  `docs/comportamento/funcoes.md`). `log(x, b)` estende o `log` existente em `Elementares`.
- Convenções: `stdev`/`var` populacionais e `Stdev`/`Var` amostrais; `atan2(y, x)`; `percentile` com `p` em 0..1;
  `round`/`trunc` só adimensionais (a unidade interna é SI, arredondar em mm exige dividir e multiplicar).
- Fatorial pós-fixo `n!` vira `fact(n)` no `Arvore.kt`; o editor produz `≠` para `!=`, que segue comparação.
- Nome faltando em qualquer argumento: resultado pendente (`Matriciais.pendente`), como em `rows(x)`.
- Correção junto: `f("a") := ...` (parâmetro que não é nome) agora é erro de sintaxe, não `ClassCastException`.
