# Vetores e matrizes

Como a folha trata vetores e matrizes. A decisão está na [ADR 0010](../decisoes/0010-vetores-e-matrizes.md)
e os casos executáveis em [`spec/casos/matrizes.json`](../../spec/casos/matrizes.json).

## Escrever

| No editor | No texto do núcleo | O quê |
|---|---|---|
| tecla 2×2, 3×3, vetor (camada Operadores) | `[1, 2; 3, 4]`, `[1; 2; 3]` | matriz e vetor-coluna |
| digitar `[1;2;3]` | `[1; 2; 3]` | vetor (o `;` separa linhas) |
| `M[i,j]` (tecla `[ ]`) | `M[i, j]` | elemento; a vírgula decimal separa índices |
| `M[i,j] := ...` | `M[i, j] := ...` | define um elemento; a matriz cresce com zeros |
| teclas `+ linha` / `+ coluna` | — | acrescentam linha/coluna à matriz em edição |
| `Mᵀ`, `M⁻¹`, `|M|`, `M⟨j⟩`, `vec`, `×` | `M^T`, `M^(-1)`, `abs(M)`, `col(M, j)`, `vec(...)`, `×` | operadores |

O primeiro índice (0 ou 1) fica em **Ajustes → Primeiro índice de vetores** e é salvo com a folha.

## Operadores

| Expressão | Resultado |
|---|---|
| `A + B`, `A − B` | elemento a elemento; dimensões iguais (senão, erro) |
| `A + 10` | soma 10 em todos os elementos |
| `M·v`, `A·B` | produto de matrizes (colunas de A = linhas de B) |
| `v·w` | produto escalar (vetores do mesmo tamanho) |
| `v × w` | produto vetorial (vetores de 3 elementos) |
| `F/A`, `v/2` | elemento a elemento; dividir por matriz é erro |
| `M^2`, `M^{-1}`, `M^0` | potência de matriz quadrada; −1 é a inversa |
| `v^2` | elemento a elemento |
| `M^T` | transposta (vetor vira linha) |
| `|x|` | módulo (escalar), comprimento (vetor), determinante (matriz quadrada) |
| `vec(F·d)` | elemento a elemento: [F₀d₀; F₁d₁; ...] |
| `A > B` | erro: compare elemento a elemento com `vec(...)` |

Funções de uma variável (sin, sqrt, ln...) aplicam em cada elemento.

## Funções

| Função | O quê |
|---|---|
| `rows(A)`, `cols(A)` | linhas e colunas (escalar: 0) |
| `length(v)`, `last(v)` | quantidade de elementos e índice do último |
| `augment(A, B, ...)`, `stack(A, B, ...)` | lado a lado / um embaixo do outro |
| `submatrix(A, i1, i2, j1, j2)` | linhas i1..i2, colunas j1..j2 |
| `identity(n)`, `diag(v)`, `diag(M)` | identidade; matriz diagonal; diagonal de M |
| `col(M, j)`, `row(M, i)` | coluna (vetor) e linha (matriz 1×n) |
| `det(M)`, `tr(M)`, `rank(M)` | determinante, traço, posto |
| `lsolve(K, f)` | resolve K·x = f (com unidades: rigidez e força dão deslocamento) |
| `eigenvals(M)`, `eigenvecs(M)` | autovalores; autovetores normalizados, um por coluna |
| `sort(v)`, `reverse(v)`, `csort(M, j)`, `rsort(M, i)` | ordenações (com unidade, pela grandeza) |
| `max(...)`, `min(...)` | aceitam vetores e matrizes, com unidade |

## Unidades e pendências

- Cada elemento tem a sua unidade; misturar vale (`[2{m}; 3{s}]`). Somas e produtos conferem as
  dimensões elemento a elemento.
- Na tela, se todos os elementos têm a mesma unidade, ela aparece uma vez, no chip do resultado,
  que troca a unidade da matriz inteira (`F = [1; 2] kN`).
- Elemento com nome faltando deixa a matriz **parcial**, com os chips "falta". A álgebra segue
  simbólica: `det([k, −k; −k, k])` dá 0 mesmo sem `k`.
- Vetor ainda não definido: `v[1]` fica pendente (`v₁`); `rows(X)` fica pendente com "falta X".

## Erros

| Situação | Mensagem |
|---|---|
| dimensões diferentes | As dimensões não são compatíveis: 2×1 e 3×1. |
| produto impossível | As dimensões não são compatíveis para o produto: 2×2 · 3×1. |
| inversa ou lsolve de matriz singular | A matriz é singular (determinante zero)... |
| índice simples numa matriz | É uma matriz: use dois índices, M[i, j]. |
| índice fora | Índice fora do intervalo. |
| comparar matrizes | A comparação é entre escalares; elemento a elemento, use vec(...). |
