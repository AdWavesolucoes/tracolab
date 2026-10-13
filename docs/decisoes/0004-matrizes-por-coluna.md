# 0004 — Matrizes serializadas por coluna

- **Status:** Aceita
- **Data:** 2026-10-12

## Contexto

Quando uma matriz vira uma lista plana, por exemplo num arquivo ou num caso de teste, é preciso
fixar a ordem. No acervo de referência, rodamos a validação com as duas hipóteses:

| Hipótese | Resultados iguais |
|---|---|
| **Por coluna** | **255** |
| Por linha | 247 |

## Decisão

Matrizes são serializadas **por coluna** (column-major): `[[a, b], [c, d]]` vira `a, c, b, d`.

## Consequências

- O formato de arquivo do app segue a mesma ordem, o que simplifica importar casos de teste.
- A diferença de 8 casos é pequena. Se surgirem casos contraditórios com matrizes não quadradas,
  esta decisão deve ser reavaliada.
