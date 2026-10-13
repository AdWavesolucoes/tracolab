# Achados do estudo

Para acertar o comportamento, estudamos um acervo de **712 planilhas de engenharia** de uma
ferramenta de desktop consagrada. Elas incluem tanto as fórmulas quanto os resultados e erros que a
própria ferramenta gravou. Os arquivos e os dados extraídos **não fazem parte deste repositório**
(veja [CONTRIBUTING](../../CONTRIBUTING.md)). Aqui ficam só as conclusões e os números agregados.

## O acervo

| Medida | Valor |
|---|---|
| Planilhas | 712, em 3 versões do formato |
| Regiões de fórmula | 13.830 |
| Definições `:=` | 7.478 |
| Avaliações numéricas `=` | 2.582 |
| Avaliações simbólicas `→` | 329 (+ 40 definições simbólicas) |
| Definições globais `≡` | 68 |
| Avaliações com resultado gravado | 2.164 numéricas e 378 simbólicas |

## Conclusões

### 1. A ordem é pela linha de base

Comparamos duas hipóteses de ordem com os resultados gravados. Se uma região tem resultado e
nenhum erro, tudo o que ela usa precisa ter sido definido antes.

| Critério de ordem | Contradições (em 2.164) |
|---|---|
| Topo da região | 82 |
| **Linha de base da região** | **7** |

Das 7 contradições que sobraram, ao menos 2 têm explicação: são variáveis embutidas da
ferramenta (uma tolerância numérica e uma constante física) redefinidas mais abaixo na página.
→ [ADR 0001](../decisoes/0001-ordem-pela-linha-de-base.md)

### 2. O erro se propaga

Das regiões que dependem de uma região com erro, **101 de 101** também tinham erro gravado.

### 3. Redefinir é rotina

Há **1.078** avisos de redefinição de variável do usuário e **595** de nomes de unidades
embutidas. Redefinir precisa ser barato e só gerar aviso. → [Modelo, seção 7](../comportamento/modelo-de-avaliacao.md#7-redefinição)

### 4. O simbólico mantém nomes livres

Os resultados simbólicos gravados contêm nomes sem valor (ex.: `invlaplace(...)` em função de
`t`, `s` e de constantes). É exatamente o estado **parcial** que o app generaliza.

### 5. Os erros mais comuns

| Erro gravado | Ocorrências |
|---|---|
| Variável não definida | 68 |
| `bad_variable` (código interno; significado a confirmar) | 59 + 5 |
| Unidade incompatível | 5 |
| Valor precisa ser escalar | 3 |

### 6. Matrizes são gravadas por coluna

Validando com as duas hipóteses, a leitura por coluna concorda com mais resultados (255 contra
247). → [ADR 0004](../decisoes/0004-matrizes-por-coluna.md)

## Como foi medido

Um extrator próprio leu o formato XML das planilhas e gerou, para cada fórmula: posição, árvore
da expressão, nomes usados, a região de onde vem cada nome, e o resultado e o erro gravados. A
ferramenta de estudo fica fora deste repositório, por depender do acervo de terceiros.
