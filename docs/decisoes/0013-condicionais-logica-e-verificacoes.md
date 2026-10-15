# 0013 — Condicionais, lógica, textos e verificações de engenharia

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

Um memorial de cálculo decide coisas (`se a carga passa de x, use a fórmula B`) e, principalmente,
**verifica**: `σ ≤ σ_adm`, `R_d ≥ S_d`, `FS ≥ 1,5`. Nas ferramentas de desktop a comparação dá
1 ou 0, e quem lê o memorial tem de saber que 1 quer dizer "atende" e calcular à parte quanto da
capacidade foi usado. Três restrições do TraçoLab pesam aqui:

- faltar variável não é erro: uma condição que ainda depende de um nome que falta não pode
  derrubar a região;
- a recursão tem limite (ADR 0017): `if` avaliado como função comum avaliaria os dois ramos, e
  `fat(n) := if(n ≤ 1, 1, n·fat(n − 1))` nunca terminaria;
- o editor (MathLive) não tem bloco de programa; tem `\begin{cases}`, `\text{...}` e o modo texto.

## Decisão

1. **`if(c, a, b)` (também `se`) e `cases(v₁, c₁, ..., senão)` são operadores especiais**: só
   avaliam o ramo escolhido. Com a condição pendente, o resultado é **por partes simbólico**
   (pendente, com os chips do que falta). Recursão com condição pendente (`fat(k)` com `k`
   faltando) para depois de 24 níveis ou 2000 ramos, com erro que diz o que definir.
2. **Lógica** `∧ ∨ ⊕ ¬` (`\land \lor \oplus \lnot`), com valores 1 e 0 como nas comparações;
   `∧` e `∨` com curto-circuito (`x = 0 ∨ 1/x > 2` não divide por zero). Comparação em cadeia:
   `0 < x ≤ 1` é `(0 < x) ∧ (x ≤ 1)`.
3. **Textos**: `"OK"` na sintaxe do núcleo. No editor, `\text{...}` é **sempre texto** (antes era
   nome; nomes de várias letras continuam com `\mathit`/`\operatorname`, que é o que a tela
   escreve), com os espaços preservados, e `"OK"` digitado no modo de fórmula também. Texto só se
   compara com `=` e `≠` e não entra em contas. A tecla **texto** da camada Operadores liga o modo
   texto do editor; **fórmula** volta. `"` numa fórmula **vazia** continua criando região de
   texto (ADR 0015).
4. **Verificação**: uma comparação avaliada com `=` (ou uma lógica só de comparações) é uma
   verificação. A resposta da API traz `verificacao: {ok, utilizacao, lados}`, e a tela mostra os
   dois lados **na unidade da folha** (`150 MPa ≤ 250 MPa`), um selo **atende/não atende** e a
   **utilização** (demanda/capacidade), em vez de 1 ou 0. Com um lado faltando, "a verificar".
   - `a ≤ b`: utilização a/b; `a ≥ b`: b/a; os dois lados negativos (compressão com sinal,
     `σ ≥ −f`): a razão se inverte; sinais trocados ou `=`/`≠`: sem utilização.
   - `∧` de verificações: a maior utilização (a que governa); `∨`: a menor.
5. **Programas** (atribuição local, laços, retorno) ficam para depois, num **tipo de região
   próprio** (abaixo).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| `if` como função comum (avalia os dois ramos) | Recursão com caso base vira "recursão profunda demais"; `if(x = 0, 0, 1/x)` dividiria por zero |
| Condição pendente é erro (como no modo clássico) | Contraria a ADR 0002: o caso mais comum é o dado ainda não ter chegado |
| Texto só com aspas dentro de `\text{"..."}` | O editor converte `"` em `''` dentro do texto; e `\text` nunca foi produzido pela tela para nomes |
| Mostrar 1/0 e deixar a utilização para uma fórmula à parte | É o que o memorial mais precisa ler; o selo responde de relance |
| Utilização sempre a/b | Em `R ≥ S` e `FS ≥ 1,5` a demanda está à direita |
| Programas com `\begin{aligned}` | Editar linhas, recuo de laços e o retorno dentro de um ambiente de alinhamento é frágil no toque; erro de uma linha não teria lugar para aparecer |

## Consequências

- `Estado` ganhou `verificacao`; a API, o campo `verificacao`; o executor da especificação, a
  verificação `"verificacao"`.
- A tela escreve por partes como `\begin{cases}` (a TeXForm do Symja escrevia
  `\text{Piecewise}(...)`), relações pendentes nas unidades da folha e contas com um por partes
  dentro como por partes de contas (`3·{2 kN; a}` → `{6 kN; 3·a}`).
- **Programas, para depois:** um tipo de região `programa` (`TL.tipos`), uma linha por
  `<math-field>`, com recuo por toque (laço/condição abrem bloco), `←` para atribuição local e
  `retorna`. O núcleo recebe uma árvore própria (`prog(...)`), avaliada com o mesmo prazo e os
  mesmos limites da ADR 0017, e o estado parcial vale linha a linha (uma linha pendente deixa o
  resultado pendente). Até lá, `if`/`cases` e funções recursivas cobrem a maior parte dos usos.
