# Funções de planilha de engenharia

Casos executáveis em `spec/casos/funcoes.json` (`fun-01`...). Regra geral: nome faltando em qualquer
argumento deixa o resultado **pendente** (chips "falta X"), nunca erro. Erros são em pt-BR.

| Função | Comportamento |
|---|---|
| `mod(x, y)` | resto com o sinal de `y` (`mod(-1, 3) = 2`); mantém a unidade; `y = 0` é erro |
| `round(x[, n])`, `trunc(x[, n])` | `n` casas (negativo arredonda à esquerda); meio-ponto para longe de zero; **adimensional** (use `round(L/{mm}, 0)*{mm}`) |
| `sign(x)` | −1, 0 ou 1; aceita unidade |
| `log(x[, b])` | 1 argumento: base 10 (em `Elementares`); 2 argumentos: base `b` |
| `sec`, `csc`, `cot`, `asinh`, `acosh`, `atanh` | argumento adimensional |
| `atan2(y, x)` | **ordem y, x**; radianos em (−π, π]; `y` e `x` na mesma unidade |
| `n!`, `fact(n)`, `fatorial(n)` | inteiro de 0 a 170, exato. O `!` pós-fixo vale para `5!` e `(n+1)!`; `!=` continua sendo ≠ |
| `combin(n, k)`, `permut(n, k)` | exatos; `k ≤ n` |
| `gcd(a, b, ...)`, `lcm(a, b, ...)` | inteiros; aceitam vetores |
| `mean`, `median`, `percentile(v, p)` | mantêm a unidade; `p` em 0..1, interpolação linear na posição `p·(n−1)` |
| `stdev`, `var` | **populacionais** (÷ n), como no Mathcad; `Stdev`, `Var` são **amostrais** (÷ n−1). `var` tem unidade² |
| `corr(x, y)`, `slope(x, y)`, `intercept(x, y)` | mínimos quadrados; `slope` tem unidade y/x, `intercept` a de y |
| `pnorm(x[, μ, σ])`, `dnorm(x[, μ, σ])`, `qnorm(p[, μ, σ])` | normal; padrão sem μ e σ; μ, σ e x na mesma unidade |
| `linterp(vx, vy, x)` | `vx` estritamente crescente; fora da faixa prolonga o primeiro/último trecho; `vy` mantém a unidade; `x` pode ser vetor |
| `lookup(x, vx, vy)` | elemento de `vy` na posição do valor de `vx` mais próximo de `x` (vetores ou matrizes do mesmo tamanho) |
| `match(x, v[, tipo])` | posição (na origem da folha): `0` igual (padrão), `1` maior valor ≤ x (crescente), `-1` menor valor ≥ x (decrescente) |
| `vlookup(z, M, col[, tipo])` | procura `z` na 1ª coluna de `M` (como `match`) e devolve a coluna `col` |

Dados estatísticos aceitam valores soltos, vetores e matrizes (achatados). Fatorial ≥ 171 e `combin`/`permut`
com `n` > 170 dão erro de limite.
