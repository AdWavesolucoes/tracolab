# 0016 — Gráfico X-Y como região da folha

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

O gráfico precisa das definições que estão acima dele na folha (`q := 3 {N}`, `L := 4 {m}`) e,
como tudo no TraçoLab, faltar dado não pode ser erro: a região fica pendente com chips "falta q".

## Decisão

O gráfico é uma região (`tipo: "grafico"`) que vai **no mesmo pedido** das fórmulas e entra na
ordem de cálculo pela linha de base. O núcleo avalia cada expressão **uma vez**, com `x` simbólico
(o `x` da folha, se houver, é ignorado dentro do gráfico), e amostra N pontos (padrão 100, de 2 a
2000) só **substituindo `x` por número** na expressão pronta (`Amostragem.kt`). A resposta traz `x`, as
curvas (`null` = buraco) e as unidades dos eixos. A interface desenha em SVG, sem biblioteca.

- Nome faltando além de `x`: região `parcial` com `livres`, sem curva.
- NaN, infinito, complexo, unidade incompatível e salto de polo a polo (sinais opostos, os dois
  lados 10× acima da mediana de |y|) viram buraco, não erro. Sem nenhum ponto real: erro da região.
- O prazo da região (ADR 0017) vale: `verificarPrazo()` a cada ponto.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Região de gráfico em chamada própria à API | Não enxergaria as definições acima dela |
| Reavaliar a folha inteira a cada ponto | N avaliações completas do Symja: lento |
| Biblioteca de gráficos (Chart.js, Plotly) | Peso, e não controlamos eixos, unidades e impressão |

## Consequências

- Valores dos eixos saem nas unidades do SI (mm vira m); escolher a unidade do eixo fica para depois.
- Fora do P1: gráfico paramétrico/polar, dados de tabela, zoom e arraste, escala log, exportar imagem.
