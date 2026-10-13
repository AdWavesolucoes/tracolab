# 0002 — Variável que falta gera resultado parcial, não erro

- **Status:** Aceita
- **Data:** 2026-10-12

## Contexto

Nas ferramentas clássicas, `F := m·a` sem `m` e `a` definidos é **erro**, e tudo o que depende
de `F` também fica com erro. Só a avaliação simbólica `→` aceita nomes livres. Isso obriga a
escrever o cálculo na ordem dos dados, o que não combina com o modo como as pessoas pensam um
problema de engenharia (primeiro a fórmula, depois os números).

## Decisão

O motor tem dois modos:

- **`parcial` (padrão do app):** um nome sem valor vira símbolo. A região fica no estado
  **parcial**, mostra a expressão restante e lista os nomes que faltam. Quem depende dela herda
  a expressão.
- **`classico`:** nome sem valor em `:=` ou `=` é erro. Existe para **validar** o
  motor contra resultados de referência gravados.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Só o comportamento clássico | É exatamente a limitação que o projeto quer resolver |
| Pedir os valores que faltam num diálogo | Interrompe o fluxo de escrita; o parcial mostra o mesmo de forma passiva |

## Consequências

- A lista de livres vem do **valor resultante**, e não do texto da fórmula (ver o
  [modelo, seção 4](../comportamento/modelo-de-avaliacao.md#4-variáveis-que-faltam)).
- Valores parciais circulam entre regiões, então o motor precisa de um CAS mesmo para contas
  "numéricas".
- Expressões parciais podem crescer muito. A interface precisa de um modo compacto (ex.: "faltam: F, d").
