# Modelo de avaliação

Especificação de **como a página é calculada**. O motor, seja o protótipo em Python ou a versão
Android, deve seguir este documento. Cada regra tem um caso em [casos.md](casos.md).

## 1. A página

Uma página é um conjunto de **regiões**. Cada região guarda uma fórmula e uma posição `(x, y)` na tela.

| Região | Escrita | Significado |
|---|---|---|
| Definição | `x := expr` | Dá um valor a `x` daqui para baixo |
| Definição global | `x ≡ expr` | Dá um valor a `x` na página inteira |
| Definição de função | `f(t) := expr` | Cria a função `f` com o parâmetro `t` |
| Definição de elemento | `v[i] := expr` | Grava um elemento do vetor `v`, que cresce se precisar |
| Faixa | `i := 0..n` ou `i := a, a+passo..b` | Define uma variável que percorre valores |
| Avaliação numérica | `expr =` | Mostra o valor numérico |
| Avaliação simbólica | `expr →` | Mostra o resultado exato/simbólico |

## 2. Ordem de cálculo

1. Primeiro, todas as **definições globais** (`≡`), de cima para baixo.
2. Depois, as demais regiões, **de cima para baixo e da esquerda para a direita**.

> [!IMPORTANT] "De cima para baixo" usa a linha de base, não o topo
> A altura vertical de uma região é medida pela **linha de base** da fórmula, a linha onde o
> texto "senta". Uma fórmula com índice ou fração é mais alta e tem o topo acima da vizinha,
> mas está na mesma linha. Ordenar pelo topo colocaria `s[i] := i/I` antes de `I := 100`, que
> está ao lado. Veja a [ADR 0001](../decisoes/0001-ordem-pela-linha-de-base.md).

Uma região **só enxerga** o que foi definido antes dela nessa ordem, mais as globais.

## 3. Estados de uma região

Toda região termina em exatamente um estado:

| Estado | Quando | O que a tela mostra |
|---|---|---|
| **numérico** | Todos os nomes têm valor | O número (ou o vetor/matriz) |
| **parcial** | Sobraram nomes sem valor | A expressão com o que falta e a lista dos nomes livres |
| **erro** | Erro de cálculo (divisão por zero, índice fora do vetor, matriz incompatível...) ou dependência de uma região com erro | A mensagem, destacando a origem |
| **não suportado** | Construção que o motor ainda não implementa | Aviso discreto |

O estado **parcial** é o diferencial do projeto. Veja a [ADR 0002](../decisoes/0002-modo-parcial-como-padrao.md).

## 4. Variáveis que faltam

Quando uma fórmula cita um nome sem valor:

- **na definição** (`F := m·a`): `F` passa a valer a **expressão** `m·a`. Quem usa `F` herda
  essa expressão e também fica parcial;
- **na avaliação numérica** (`F =`): mostra `a·m` como parcial e lista `a, m` como livres;
- **na avaliação simbólica** (`→`): mesmo comportamento. Os nomes conhecidos são substituídos
  e o resultado é simplificado de forma exata (`1/3 + 1/6 → 1/2`, e não `0,5`).

A lista de livres de um resultado considera o que **sobrou no valor**: se `F` vale `m·a`, então
`F =` tem livres `a` e `m`, mesmo sem a fórmula citá-los diretamente. Nomes que se cancelam
(`x − x`) não contam como livres no modo parcial.

> [!NOTE] Comparação com o comportamento clássico
> Nas ferramentas de desktop, um nome sem valor em `:=` ou `=` é **erro**, e só o `→` aceita
> nomes livres. O motor implementa os dois comportamentos (`modo="classico"` e
> `modo="parcial"`). O modo clássico serve para validar o motor contra resultados de
> referência; o app usa o parcial.

## 5. Preenchimento progressivo

Quando o usuário escreve ou altera uma região, só é recalculado o que precisa:

1. a própria região;
2. todas as regiões **depois dela** que usam algum nome que ela define;
3. repetindo o passo 2 para os nomes que essas regiões definem (dependência transitiva).

Se a região é **global**, "depois" vale para a página inteira. **Remover** uma definição
recalcula quem dependia dela, e essas regiões voltam a ser parciais.

Exemplo: em `a := 1`, `b := 2`, `c := a+1`, `d := b+1`, `c =`, `d =`, alterar `a` recalcula só
`a`, `c` e `c =`.

## 6. Erros

- O erro **se propaga**: tudo que depende de uma região com erro também fica com erro, e a
  mensagem aponta a origem ("Depende de 'x', que está com erro").
- **Corrigir a origem limpa a cadeia inteira** no mesmo recálculo.
- Regiões que não dependem do erro continuam calculando normalmente.

## 7. Redefinição

Redefinir um nome é **permitido** e gera só um **aviso**. Cada região usa o valor vigente na
sua posição:

```
x := 1
x =        → 1
x := 5     ⚠ redefinição
x =        → 5
```

## 8. Funções

- `f(t) := t² + k`: `t` é parâmetro e `k` é **capturado no momento da definição**, com o valor
  vigente naquela posição da página.
- Se `k` ainda não tem valor quando `f` é definida, `f(3) =` fica parcial (`9 + k`). Basta
  definir `k` **acima** de `f` para completar.
- Redefinir `k` **depois** de `f` não altera `f`.
- Funções podem ser recursivas.

## 9. Faixas e vetores

- `i := 0..4` cria uma variável de faixa. Uma expressão que usa `i` é avaliada para cada
  valor e o resultado vira uma coluna: `i·10 =` mostra `0, 10, 20, 30, 40`.
- `v[i] := i²` com `i` de faixa monta o vetor `[0, 1, 4, 9, 16]`.
- Índices começam em **0** (configurável por página).
- `Σᵢ expr` sem limites soma sobre os valores da faixa `i`.
- Matrizes são lidas e gravadas **por coluna**. Veja a [ADR 0004](../decisoes/0004-matrizes-por-coluna.md).

## 10. Unidades de medida

- No app, unidade é **explícita**: `20{mm}`, `F/{kN}`. Assim `m` pode ser massa sem ambiguidade
  ([ADR 0007](../decisoes/0007-unidades-explicitas.md)).
- A conversão é automática: `2{m} + 30{cm} =` dá `2,3 m`.
- Sem unidade pedida, o resultado sai no SI, com nome derivado quando houver: `19,62 N`, `200 MPa`.
- Com unidade pedida, `σ = {MPa}` mostra o valor nessa unidade. Se a unidade pedida não for
  compatível, o **cálculo** continua válido e só a **exibição** acusa erro.
- **Erro de dimensão**: somar, subtrair ou comparar grandezas diferentes (`2{m} + 1{s}`), ou
  passar grandeza com unidade para `sin`, `exp`, `ln` etc. O erro se propaga como os demais.
- **Com variável faltando, não há erro de unidade**: `x + 5{m}` fica parcial, porque a dimensão
  de `x` ainda é desconhecida.
- Ângulos são adimensionais: `sin(90{deg}) = 1`.

## 11. Simbólico versus numérico

| | `=` numérico | `→` simbólico |
|---|---|---|
| Precisão | Ponto flutuante (15 dígitos) | Exata (frações, `π`, `√2`) |
| Nome sem valor | Parcial (app) / erro (clássico) | Sempre parcial |
| Pós-processamento | — | `simplify`, `expand`, `factor`, `float` |

## 12. Áreas detalhadas

Cada área tem o seu documento, com os casos em `spec/casos/<área>.json`:

| Área | Documento |
|---|---|
| Vetores e matrizes | [matrizes.md](matrizes.md) |
| Condicionais, lógica e verificações | [logica.md](logica.md) |
| Unidades da folha e formato | [unidades.md](unidades.md) |
| Folha como documento | [documento.md](documento.md) |
| Limites de cálculo | [robustez.md](robustez.md) |
| Regressões do núcleo | [regressoes.md](regressoes.md) |
| Σ, Π, ∫ e d/dx | [calculo.md](calculo.md) |
| Resolver equações | [resolver.md](resolver.md) |
| Funções embutidas | [funcoes.md](funcoes.md) |
| Gráficos X-Y | [graficos.md](graficos.md) |
