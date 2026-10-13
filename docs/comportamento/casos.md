# Casos de comportamento

Cenários que o motor **precisa** cumprir. A versão executável está em
[`spec/casos-comportamento.json`](../../spec/casos-comportamento.json), e todas as
implementações rodam esse mesmo arquivo. Notação: cada linha do bloco é uma região, de cima para baixo.

| # | Cenário | Página | Esperado |
|---|---|---|---|
| 1 | Tudo faltando | `F := m·a` / `F =` | parcial `a·m`, livres `a, m` |
| 2 | Preenchimento em etapas | inserir `m := 2` e depois `a := 3` acima | parcial `2·a`, livre `a` → numérico `6` |
| 3 | Comportamento clássico | caso 1 no modo clássico | `F` com erro "variável não definida"; `F =` com erro propagado |
| 4 | Definida abaixo não vale | `x + 1 =` / `x := 5` | parcial, livre `x` |
| 5 | Mesma linha | `x := 5` à esquerda, `x·2 =` à direita | `10` |
| 6 | Global vale acima | `g·2 =` / `g ≡ 9,8` | `19,6` |
| 7 | Redefinição | `x := 1` / `x =` / `x := 5` / `x =` | `1`, depois `5`; aviso na 2ª definição |
| 8 | Função com nome livre | `f(t) := t² + k` / `f(3) =`; depois `k := 1` acima | parcial `9 + k` → `10` |
| 9 | Captura na definição | `k := 1` / `f(t) := t + k` / `k := 100` / `f(0) =` | `1` |
| 10 | Simbólico mantém livres | `a := 2` / `a·x + b →` | `2·x + b`, nos dois modos |
| 11 | Simbólico exato | `1/3 + 1/6 →` | `1/2` (fração, não decimal) |
| 12 | Vetor por faixa | `i := 0..4` / `v[i] := i²` / `v =` | `[0, 1, 4, 9, 16]` |
| 13 | Tabela por faixa | `i := 1..3` / `i·10 =` | `[10, 20, 30]` |
| 14 | Erro propaga | `x := 1/0` / `y := x + 1` | os dois com erro |
| 15 | Corrigir limpa a cadeia | caso 14, trocar por `x := 4` | `y = 5` |
| 16 | Recálculo mínimo | `a, b, c := a+1, d := b+1, c =, d =`; alterar `a` | só `a`, `c` e `c =` recalculam |
| 17 | Remover definição | `a := 2` / `a·x →`; remover `a` | parcial, livres `a, x` |
| 18 | Engenharia encadeada | `A := π·d²/4` / `σ := F/A` / `σ =`; depois `d` e `F` | livres `F, d` → `F` → número |
| 19 | Soma converte | `L := 2{m}` / `L + 30{cm} =` | `2.3 m` |
| 20 | Unidade pedida | `F := 10{kN}`, `A := 50{mm^2}`, `σ := F/A`, `σ = {MPa}` | `200 MPa` |
| 21 | Nome derivado no SI | `m := 2{kg}` / `m·9.81{m/s^2} =` | `19.62 N` |
| 22 | Dimensões incompatíveis | `x := L + 1{s}` / `y := x·2` | os dois com erro |
| 23 | Transcendental adimensional | `sin(2{m}) =` / `sin(90{deg}) =` | erro / `1` |
| 24 | Unidade pedida incompatível | `3{m} = {s}` | cálculo numérico; só a exibição dá erro |
| 25 | Parcial com unidade | `x + 5{m} =` | parcial, livre `x`, sem erro |
| 26 | Engenharia em etapas | `A := π·d²/4`, `σ := F/A`, `σ = {MPa}`; depois `d := 20{mm}`, `F := 31.4159265{kN}` | parcial → `100 MPa` |
| 27 | Imperial | `1{ft} + 1{in} = {in}` / `1{ksi} = {MPa}` | `13 in` / `6.89476 MPa` |
| 28 | Comparação com unidades | `2{m} > 150{cm} =` | `1` |
| 29 | Modo compatível | `d := 3*ft` / `d = {in}`; depois `m := 5`, `m·2 =` | `36 in`; `10` (a variável esconde a unidade) |

Situação: **30 casos no JSON** (o cenário 10 tem uma versão para cada modo). Passam **30/30** no protótipo Python e **30/30** no núcleo Kotlin + Symja.
