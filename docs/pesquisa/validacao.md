# Validação do protótipo

O protótipo roda em **modo clássico** sobre o acervo do [estudo](achados-do-estudo.md) e compara
cada avaliação com o resultado que a ferramenta de referência gravou.

## Métricas

| Métrica | Definição |
|---|---|
| **Cobertura** | Avaliações comparadas ÷ avaliações com resultado gravado |
| **Acerto** | Avaliações iguais (tolerância relativa de 10⁻⁶) ÷ avaliações comparadas |
| **Erros concordantes** | Regiões em que a referência gravou erro e o motor também deu erro |

Não entram na comparação: resultados com unidade, construções não suportadas e tudo o que
depende delas.

## Rodadas

| Data | Versão do motor | Cobertura | Acerto | Observação |
|---|---|---|---|---|
| 2026-10-12 | 1ª | 15,0% (273/1.822) | 93,4% (255/273) | 57 planilhas falharam inteiras por bug no tratamento de exceções |
| 2026-10-12 | 2ª | 19,0% (406/2.141) | 92,6% (376/406) | Corrigidos: exceções, índice `M[i,j]`, `Σ` sobre faixa, `NaN` |
| 2026-10-12 | 3ª | 20,5% (432/2.112) | 93,1% (402/432) | Embutidos desconhecidos separados de "variável não definida": erros falsos caíram de 369 para 87 |
| 2026-10-12 | 4ª | **29,2% (617/2.112)** | **94,0% (580/617)** | Unidades de medida; o resultado gravado é convertido para a unidade de exibição antes da comparação |

## O que limita a cobertura

1. **Dados externos**: componentes de leitura de arquivo (296 regiões), imagens e números
   aleatórios. Estão fora do escopo do app.
2. **Unidades**: resolvido na 4ª rodada. Restam °C/°F e 29 casos de unidade divergente a investigar.
3. **Efeito cascata**: uma região não suportada (leitura de arquivo, solver, unidade) torna não
   suportado tudo o que depende dela.
4. **Construções avançadas**: solvers (`Find`/`Given`), comandos simbólicos como `solve` e
   `laplace`, atribuição múltipla.

## Divergências conhecidas (até a 3ª rodada)

| Caso | Causa provável |
|---|---|
| Literais em binário/octal (`1011001 =` dá `89` na referência) | Sufixo de base numérica não lido |
| `vec(A·B)` | Vetorização precisa aplicar elemento a elemento |
| `1 − α =` dá `0,9`; a referência mostra `90` | Exibição pedida em `%`: o valor é gravado já convertido, sem a unidade no resultado |
| Programas com laço retornando 0 | Retorno do valor do laço no interpretador de programas |
| `trunc` de número complexo | A referência trunca as partes real e imaginária separadamente |
| Operadores booleanos vetorizados | `vec(P and Q)` precisa aplicar elemento a elemento |
