# Cálculo: Σ, Π, ∫ e d/dx

Casos executáveis: [`spec/casos/calculo.json`](../../spec/casos/calculo.json) (`calc-01`…`calc-20`).
Decisão: [ADR 0011](../decisoes/0011-calculo.md). Código: `nucleo/.../embutidos/Calculo.kt` e
`nucleo/.../latex/EntradaCalculo.kt`; teclas em `web/js/calculo-operadores.js`.

| Escrita | Editor | Resultado |
|---|---|---|
| `sum(i^2, i, 1, 4)` | `\sum_{i=1}^{4} i^2` | 30 |
| `sum(i, i, 1, n)` (n faltando) | | n(n+1)/2, chip "falta n" |
| `i := 1..4` e `sum(2*i, i)` | `\sum_{i} 2i` | 20 (percorre a faixa) |
| `sum(v)`, `prod(v)` | `\sum v` | soma/produto dos elementos |
| `prod(i, i, 1, 5)` | `\prod_{i=1}^{5} i` | 120 |
| `int(q, x, 0, L)` com q = 10 kN/m, L = 2 m | `\int_0^L q\,dx` | 20 kN |
| `int(q*x, x, 0, L)` (q, L faltando) | | q·L²/2, chips q e L |
| `int(x^x, x, 0, 1)` | | 0,78343… (numérica) |
| `int(2*x, x)` | `\int 2x\,dx` | x² (x vira chip, ou o valor no x de cima) |
| `deriv(x^3, x)` sem x | `\frac{d}{dx} x^3` | 3x², chip "falta x" |
| `x := 2` e `deriv(x^3, x, 2)` | `\frac{d^2}{dx^2} x^3` | 12 (no ponto) |
| `t := 2 s` e `deriv(5 m/s²·t²/2, t)` | | 10 m/s |

- A variável ligada (`i`, `x`) não é a definida acima e nunca falta; o `i := 10` de cima não
  muda `sum(i, i, 1, 3)`.
- Unidade de x: a dos limites (∫) ou a do x de cima (d/dx). A integral multiplica por ela, a
  derivada divide.
- Limites conhecidos de Σ/Π têm de ser inteiros (senão, erro); limite faltando dá a soma simbólica.
- Até onde vai o operando no editor: o somando de Σ/Π e a expressão depois de d/dx vão até o
  primeiro `+`, `−`, `=`, comparação, `→` ou vírgula de fora, ou até fechar o parêntese em que o
  operador está. `\sum_{i=1}^{3} i + 1` é (Σ i) + 1 = 7; para somar `i + 1`, use parênteses.
  O integrando vai até o diferencial, que é obrigatório (`dx`, `\,dx`, `\mathrm{d}x`).
- Edição de `x` (ou da faixa `i`) recalcula a derivada (ou a Σ sem limites) que depende dele.
