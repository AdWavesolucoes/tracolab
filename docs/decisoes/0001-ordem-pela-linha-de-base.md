# 0001 — A ordem de cálculo usa a linha de base da região

- **Status:** Aceita
- **Data:** 2026-10-12

## Contexto

As regiões são calculadas "de cima para baixo, da esquerda para a direita". Mas uma região tem
altura: uma fórmula com índice (`s[i]`), fração ou matriz é mais alta que a vizinha, e seu topo
fica acima mesmo estando na mesma linha visual.

No estudo de 712 planilhas de referência, comparamos as duas leituras de "altura" contra 2.164
resultados gravados:

| Critério | Contradições |
|---|---|
| Topo da região | 82 |
| Linha de base | 7 |

## Decisão

A posição vertical de uma região é a **linha de base** da fórmula (`align-y`), e a horizontal é
o ponto de alinhamento (`align-x`). A ordem é `(linha de base, alinhamento x)`, com as globais
`≡` antes de tudo.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Topo da caixa | 82 contradições: fórmulas altas "pulam" para antes da vizinha |
| Centro da caixa | Sofre do mesmo problema com matrizes altas |

## Consequências

- A interface precisa expor a linha de base de cada região, e não só o retângulo.
- Ao arrastar uma região, a ordem muda quando a **linha de base** cruza a de outra região. A
  interface deve mostrar isso, por exemplo com uma guia horizontal durante o arraste.
