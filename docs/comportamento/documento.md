# Folha como documento

Como a folha vira um memorial de cálculo. Decisão em [ADR 0015](../decisoes/0015-folha-como-documento.md);
casos executáveis em [`spec/casos/documento.json`](../../spec/casos/documento.json).

## Definir e mostrar

Uma definição terminada em `=` define o nome **e** mostra o valor na mesma região:

```
q := 12{kN/m}
L := 6{m}
R := q·L/2 = {kN}      → 36 kN (e R vale 36 kN daqui para baixo)
```

| Situação | Comportamento |
|---|---|
| Faltam nomes | Estado parcial, como na avaliação: `R := q·L/2 =` mostra `L·q/2`, falta `q`, `L` |
| Unidade pedida incompatível | O cálculo vale; só a exibição acusa erro |
| Global (`≡`), simbólica (`expr → =`), faixa, elemento | Define e mostra o valor (o vetor, no caso do elemento) |
| Função (`f(t) := t² =`) | Define; não há valor a mostrar (aviso discreto) |
| `>=`, `<=`, `==`, `!=` no fim | São operadores, não pedido de exibição |

## Substituição explícita

Numa região que mostra valor (avaliação ou definir e mostrar), o botão **x=2** liga a fórmula
com os valores no lugar dos nomes:

```
R := q·L/2 = (12 kN/m · 6 m)/2 = 36 kN
```

- O valor de cada nome é o da **definição vigente na posição da região**: a última antes dela
  na ordem de cálculo. As globais valem para a folha inteira.
- Número escrito à mão aparece como foi escrito (`12 kN/m`, `(20 mm)²`). Valor calculado aparece
  como a definição dele aparece na tela.
- **Nome que falta continua nome**: `σ := F/A =` com só `A` conhecido mostra `F/(50 mm²)`.
- Faixas, funções, vetores e nomes ligados (o `i` de Σ) continuam como nome.
- Parênteses só onde a leitura exige: `(−3)²`, `5 − (−3)`, `(20 mm)²`.
- Se nada foi substituído (`2·3 =`, `σ =`), não há substituição.

## Texto

- Tecla **T** na faixa: cria um parágrafo abaixo da região em edição ou converte a fórmula vazia
  em edição. Digitar `"` numa fórmula vazia também a converte.
- Estilos **Título**, **Subtítulo** e **Texto**, mais negrito (**N**) e itálico (**I**), numa
  barra que aparece com o foco. No título e no subtítulo, Enter conclui; no parágrafo, quebra a linha.
- O texto vai até a margem direita, fica fora do cálculo e da numeração da ordem, e some se
  ficar vazio. Colar traz só o texto puro.

## Folhas e arquivo

- Painel **Folhas** no trilho: nova (em branco), abrir, renomear, duplicar, apagar (com
  confirmação), exportar, importar e imprimir.
- A folha aberta fica sempre em `tracolab.folha.v2`. As outras ficam em
  `tracolab.folha.arquivo.<id>`, e o índice em `tracolab.folhas.v1`. A folha que já estava no
  aparelho continua onde está e só ganha um `id`.
- A folha de exemplo aparece só na primeira folha do aparelho.
- Trocar de folha recomeça o histórico de desfazer.

### Formato `.tlab`

```json
{ "formato": "tracolab", "versao": 1, "nome": "Viga V1", "exportado": "2026-10-14T20:00:00Z",
  "folha": { "modo": "parcial", "ordem": false, "regioes": [
    { "id": "r1", "latex": "q\\tldef12\\,\\mathrm{kN}/\\mathrm{m}", "unidade": "", "x": 40, "y": 48 },
    { "id": "r2", "tipo": "texto", "estilo": "titulo", "html": "Viga <b>V1</b>", "x": 40, "y": 12 } ] } }
```

Ao importar:
- o arquivo vira uma **folha nova**, com os ids refeitos;
- posições são limitadas ao papel;
- o HTML do texto é sanitizado;
- o LaTeX tem tamanho limitado;
- regiões de tipo desconhecido ficam de fora, com aviso;
- versão mais nova que a do app é recusada.

## Páginas e impressão

- A folha cresce em páginas A4 inteiras, sempre com espaço livre abaixo da última região.
- As quebras aparecem tracejadas, com o número da página.
- **Imprimir / PDF** (ou Ctrl+P):
  - sai só o papel, sem barra, teclas, chips nem notas;
  - a região que cruzaria a quebra (ou invadiria o rodapé) desce para a página seguinte, levando junto o que vem abaixo;
  - cada página tem rodapé com o nome da folha e "página n de N".
