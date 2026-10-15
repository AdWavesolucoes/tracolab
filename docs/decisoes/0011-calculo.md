# 0011 — Σ, Π, ∫ e d/dx como operadores com nome ligado

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

Somatório, produtório, integral e derivada aparecem em quase toda folha de engenharia (carga
distribuída integrada no vão, momento de inércia somado por partes, taxa de variação). Eles têm
uma variável ligada (`i`, `x`) que não é o valor definido acima, e a folha precisa continuar com
o comportamento do TraçoLab: faltar um nome não é erro, o resultado fica pendente com chips.

## Decisão

`sum(expr, i, a, b)`, `prod(...)`, `int(expr, x[, a, b])` e `deriv(expr, x[, n])` são
operadores `Especial` (recebem a árvore sem avaliar) com o nome ligado na posição 1, registrados
em `embutidos/Calculo.kt`. A variável ligada entra no contexto em `c.iter` (sombra o valor de
cima e nunca vira "falta"). Regras:

- Σ/Π com limites inteiros conhecidos somam termo a termo (vetores e unidades valem); com limite
  faltando, `Sum`/`Product` do Symja (Σ_{i=1}^n i → n(n+1)/2, `n` vira chip). Limites conhecidos
  não inteiros são erro.
- Σ/Π sem limites (`sum(e, i)`) percorrem a faixa `i` definida acima; `sum(v)` soma os elementos.
- ∫ e d/dx com unidades: a variável vale `X·u` (u = unidade comum dos limites, ou do x de cima),
  com X adimensional; a integral em X é multiplicada por u, a derivada dividida por uⁿ.
- ∫ tenta `Integrate` (até 1,5 s quando tudo é número) e cai em `NIntegrate` (Gauss-Kronrod).
- d/dx com x definido acima avalia nesse ponto (x faixa: um valor por ponto); sem x, devolve a
  derivada simbólica, em que x vira chip. A ∫ indefinida segue a mesma regra.
- Quando o nome ligado também lê o valor de cima (Σ sem limites, d/dx, ∫ indefinida), ele conta
  como usado para o recálculo incremental: `Especial.usaLigado` (mudança pequena em
  `Embutidos.kt` e `Texto.kt`).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Passar tudo ao Symja (`Sum`, `Integrate`, `D`) direto | Não respeita unidades nem o x de cima; Σ numérica grande ficaria lenta e simbólica sem necessidade |
| Variável ligada como símbolo livre normal | Viraria chip "falta i" e colidiria com o `i` definido acima |
| ∫ só numérica | Perde o resultado pendente (∫₀ᴸ q·x dx → q·L²/2), que é o forte do modo parcial |

## Consequências

- A entrada LaTeX (`latex/EntradaCalculo.kt`) aceita `\sum_{i=1}^{n}`, `\prod`, `\int_a^b … dx`
  (`\,dx`, `\mathrm{d}x`, `\differentialD x`) e `\frac{d}{dx}` / `\frac{\mathrm{d}^2}{\mathrm{d}x^2}`.
  O somando vai até o primeiro `+`, `−`, `=`, comparação ou vírgula de fora; mais termos pedem
  parênteses. `\mathrm{d}` só é diferencial dentro de ∫ e de `\frac{d}{dx}`; fora, continua unidade.
- Pendências: limite (`lim`), Σ dupla com limites dependentes testada só no núcleo, exibição
  LaTeX de `Sum`/`Integrate` não resolvidos (saem como função), ∫ com limites infinitos.
