# 0010 — Vetores e matrizes

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

Folhas de engenharia usam vetores (tabelas de valores, cargas, diâmetros) e matrizes (rigidez,
sistemas lineares). Era preciso fixar a representação no núcleo, a escrita no editor e o
significado dos operadores, sem perder o estado pendente nem as unidades por elemento.

## Decisão

- **Representação na memória:** vetor-coluna é uma lista simples (`{a, b, c}`, como a de
  `v[i] := ...`); qualquer outra matriz é uma lista de linhas (`{{a, b}, {c, d}}`), que é a forma
  natural do Symja. Uma matriz n×1 vira vetor. A ordem **por coluna** da ADR 0004 continua
  valendo para serializar (arquivo, casos de teste); a memória é por linha.
- **Escrita:** `[1, 2; 3, 4]` no texto do núcleo; `pmatrix`/`bmatrix` no editor. Índice
  `v[i]` e `M[i, j]`; `;` separa linhas numa matriz digitada e, dentro de um índice, a vírgula
  decimal do pt-BR (`M[0,1]`) separa índices. Primeiro índice 0 ou 1, opção da folha.
- **Operadores:** `+ −` elemento a elemento (escalar soma em todos); `·` é produto de matrizes,
  e entre dois vetores é o produto escalar; `×` é produto vetorial; `/` é elemento a elemento
  (por matriz é erro: use a inversa); `^` é potência de matriz quadrada (`−1` é a inversa) e
  elemento a elemento em vetor; `M^T` (ou `M^⊤`) é a transposta; `|x|` é módulo, comprimento de
  vetor ou determinante.
- **Vetorização:** as funções de uma variável (sin, sqrt, ln...) já aplicam em cada elemento.
  Para o resto, `vec(...)` (seta sobre a expressão no editor) avalia elemento a elemento: cada
  vetor ou matriz da expressão é trocado pelo elemento da mesma posição.
- **Unidades e pendências:** cada elemento tem a sua unidade (pode misturar, como `[x; v]` em m e
  m/s); as operações conferem as dimensões elemento a elemento. Elemento com nome faltando deixa
  a matriz parcial, e a álgebra segue simbólica (`det` de `[k, −k; −k, k]` dá 0). Embutido sem a
  matriz fica pendente (`rows(X)`, chip "falta X").

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Matriz por coluna na memória (como a ADR 0004) | Todas as funções do Symja (Det, Inverse, LinearSolve, Dot) trabalham por linha; converter a cada operação só custaria tempo |
| `·` sempre elemento a elemento | O produto de matrizes (rigidez × deslocamento) é o uso principal em engenharia |
| `/` por vetor ser erro, como nas ferramentas de desktop | `σ := F/A` com um vetor de áreas é o que se espera; dividir por vetor não tem outro sentido |
| Exigir a mesma unidade em todos os elementos | Vetores de estado (posição e velocidade) misturam unidades; a checagem por elemento já pega os erros |
| Vetor elevado a 2 como produto escalar | Tabelas (`π·d²/4` para vários diâmetros) são mais comuns; o produto escalar é `v·v` |

## Consequências

- `abs` saiu das elementares: `|x|` é um operador só, que depende do que está dentro.
- O texto `T` depois de `^` em uma matriz é a transposta, mesmo que exista uma variável `T`.
- A tela mostra matrizes em `pmatrix`; com uma unidade comum, ela sai uma vez, no chip.
  Matrizes grandes aparecem resumidas (as primeiras linhas e colunas, ⋮/⋯ e a última).
- A API devolve `forma` ([linhas, colunas]) para o painel de variáveis.
