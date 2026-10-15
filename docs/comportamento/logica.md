# Condicionais, lógica, textos e verificações

Decisão na [ADR 0013](../decisoes/0013-condicionais-logica-e-verificacoes.md); casos executáveis em
[`spec/casos/logica.json`](../../spec/casos/logica.json) (`log-01` a `log-30`).

## Se e por partes

| Escrita | Resultado |
|---|---|
| `if(x > 0, a, b)` ou `se(...)` (tecla **se**) | `a` se a condição vale, senão `b`. Só o ramo escolhido é calculado |
| `\begin{cases} 0 & t < 0 \\ t & t ≤ 1 \\ 1 & \text{senão} \end{cases}` (tecla **{ por partes**) | `cases(0, t < 0, t, t <= 1, 1)`: o primeiro valor cuja condição vale |
| Última linha com "senão", "otherwise", "caso contrário" ou condição vazia | É o valor dos outros casos |
| `cases` sem "senão" e nenhuma condição verdadeira | Erro: "Nenhum caso se aplica" |
| Condição faltando (`if(x > 0, 10, 20)` sem `x`) | **Pendente**: a tela mostra o por partes e o chip "falta x" |
| `fat(n) := if(n ≤ 1, 1, n·fat(n − 1))` | Recursão com caso base: `fat(5) = 120` |
| `fib(k)` com `k` faltando | Erro claro ("defina k"), sem travar |
| Condição com unidade (`if(2 m, ...)`) | Erro de unidade |

Uma conta com um por partes pendente dentro aparece como por partes de contas:
`y := se(x > 0, 2 kN, a)` e `y·3 =` mostram `{6 kN se x > 0; 3·a senão}`.

## Lógica

| Escrita | Editor | Valor |
|---|---|---|
| `a ∧ b` | `\land` (∧) | 1 se as duas valem; curto-circuito: falso ∧ ... nem calcula o resto |
| `a ∨ b` | `\lor` (∨) | 1 se uma vale; verdadeiro ∨ ... nem calcula o resto |
| `a ⊕ b` | `\oplus` | ou exclusivo |
| `¬a` | `\lnot` (¬) | 1 se `a` é 0 |
| `0 < x ≤ 1` | | `(0 < x) ∧ (x ≤ 1)` |

Precedência (da menor para a maior): `∨ ⊕`, `∧`, `¬`, comparação. Com nomes faltando, o
resultado fica pendente e se simplifica (`x > 0 ∧ y > 0` com `x := −1` dá 0, sem pedir `y`).

## Textos

- `m := "OK"` ou, no editor, a tecla **texto** (modo texto; **fórmula** volta), `\text{não atende}`,
  ou `"OK"` digitado depois de `:=`. Espaços valem.
- `"` numa fórmula **vazia** cria uma região de texto (parágrafo), não um valor de texto.
- Texto só se compara com `=` e `≠`; não entra em contas (`"a" + 1` é erro).
- `if(σ ≤ σ_adm, "atende", "não atende") =` mostra o texto escolhido.

## Verificações

Uma comparação avaliada com `=` é uma **verificação**:

```
σ := 150{MPa}
σ_adm := 250{MPa}
σ ≤ σ_adm =        → 150 MPa ≤ 250 MPa   ✓ atende   60%
```

| Situação | Tela |
|---|---|
| Atende | Os dois lados na unidade da folha, selo **atende** (cobalto) e a utilização com barra |
| Não atende | Selo **não atende** (vermelhão), utilização acima de 100% em vermelhão |
| Falta um lado | A relação pendente (`150 MPa ≤ σ_adm`), selo "a verificar" e o chip do que falta |
| `=` ou `≠` | Selo sem utilização |
| Sobre uma faixa (`i ≤ 2 =` com `i` de faixa) | Tabela de 1 e 0, sem selo |
| `ok := σ ≤ σ_adm =` (definir e mostrar) | Também é verificação |

**Utilização** (demanda/capacidade): `a ≤ b` usa a/b; `a ≥ b` usa b/a (`R ≥ S`, `FS ≥ 1,5`);
com os dois lados negativos (compressão com sinal, `σ ≥ −f`) a razão se inverte; sinais trocados
não têm utilização. Em `∧` vale a maior (a que governa); em `∨`, a menor.

A API traz `verificacao: {"ok": true|false|null, "utilizacao": 0.6, "lados": "150\,\mathrm{MPa}\le 250\,\mathrm{MPa}"}`.
