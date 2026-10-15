# 0015 — A folha como memorial: texto, definir e mostrar, substituição e arquivo

- **Status:** Aceita
- **Data:** 2026-10-14

## Contexto

Uma folha de cálculo de engenharia vira um memorial: tem títulos e parágrafos, mostra os
valores intermediários e é entregue impressa ou em PDF. Nas planilhas estudadas, as regiões de
texto são o tipo de região mais comum (mais numerosas que as fórmulas), e cada folha costuma
ocupar várias páginas. Até aqui, o TraçoLab tinha só fórmulas, uma folha por aparelho e uma
página: uma região criada abaixo da primeira A4 ficava fora do papel.

## Decisão

1. **Texto** é um tipo de região da interface (`tipo: "texto"`), fora do cálculo, com título,
   subtítulo e parágrafo e só negrito, itálico e quebra de linha. O HTML guardado é sanitizado
   numa lista fechada, sempre, inclusive ao importar arquivo.
2. **Definir e mostrar**: uma definição terminada em `=` (com ou sem unidade) define e mostra o
   valor na mesma região: `R := q·L/2 = 36 kN`. É sintaxe do núcleo, não da interface.
3. **Substituição explícita** é uma opção por região, calculada pelo núcleo: a fórmula com os
   valores no lugar dos nomes. O valor de um nome vem da definição vigente na posição da região.
   Um número escrito à mão aparece como foi escrito (`12 kN/m`); um valor calculado aparece como
   a definição dele se exibe. **Nome que falta continua nome** (substituição parcial).
4. **Várias folhas** no aparelho, sem migração: a folha aberta continua em
   `tracolab.folha.v2`, e as outras ficam em chaves próprias, com um índice.
5. **Arquivo `.tlab`**: JSON com `formato: "tracolab"`, `versao`, `nome` e `folha` (`modo`,
   `ordem`, `regioes`), exportado e importado pelo navegador. Importar sempre cria uma folha
   nova, com os ids refeitos e cada região conferida.
6. **Páginas A4**: a folha cresce de página em página. Na impressão, a região que cruzaria a
   quebra desce para a página seguinte, levando junto tudo o que vem abaixo.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Substituição só na interface (trocar nomes no LaTeX) | Precisa da ordem de cálculo, das unidades e dos valores vigentes, que estão no núcleo; o app Android teria de repetir tudo |
| Mostrar todos os nomes, inclusive os que faltam, como erro | Vai contra o estado parcial: a substituição parcial mostra o que já se sabe |
| Migrar a folha atual para uma chave nova | Risco de perder a folha do usuário por nada; deixá-la onde está dispensa migração |
| Guardar as folhas no servidor | O servidor de teste não tem login; o arquivo `.tlab` resolve o transporte entre aparelhos |
| Texto rico completo (listas, cores, imagens) | Cresce a superfície de ataque do HTML importado; títulos, parágrafo, negrito e itálico cobrem o memorial |
| Deixar o navegador cortar as regiões na quebra de página | Fórmula partida ao meio no PDF |

## Consequências

- O pedido de uma região pode levar `substituir: true`. A resposta traz `mostra` (definir e
  mostrar) e `substituicao`.
- A unidade do chip só vai ao núcleo enquanto a fórmula termina em `=`. Sem ele, ela
  multiplicaria o valor de uma definição.
- O valor calculado aparece na substituição com a mesma unidade da tela. Quando as unidades da
  folha melhorarem, a substituição melhora junto.
- Na ponte do APK (WebView), exportar, importar e imprimir vão precisar de código nativo
  (download, escolha de arquivo, impressão do Android).
