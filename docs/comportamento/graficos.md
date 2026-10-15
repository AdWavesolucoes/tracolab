# Gráficos X-Y

Decisão em [ADR 0016](../decisoes/0016-graficos.md).

## Na folha

Tecla de gráfico na faixa (sobre uma fórmula vazia, ela vira o gráfico). A região mostra eixos, grade
com números redondos, curvas (cobalto, vermelhão, nanquim, grafite), legenda e as unidades nos eixos
(`x (m)`, `y (J)`). "editar" abre as expressões em `x` (até 4 curvas), o intervalo `x de ... até ...`
(com unidade, se quiser) e o número de pontos. Tocar no desenho (trace) mostra `x` e `y` de cada curva
no ponto mais próximo; o toque fora do gráfico limpa.

| Situação | O que aparece |
|---|---|
| Tudo definido | Curvas e legenda |
| Falta um nome além de `x` | "Pendente" e o chip "falta q" (cria `q :=` acima) |
| Nome só definido abaixo | Balão de erro de ordem, como numa fórmula |
| Ponto sem valor real (`ln` de negativo, `1/0`, `√` de negativo), polo | Buraco na curva |
| Nenhum ponto real, intervalo inválido, fórmula ilegível | Erro só na região |

## API

Região do pedido: `{"id", "tipo": "grafico", "series": ["latex"...], "a": "latex", "b": "latex", "n": 100, "x", "y"}`.
Resposta: `tipo: "grafico"` e `grafico: {"xu", "yu", "x": [...], "series": [{"texto", "y": [... null]}]}`,
ou `tipo: "parcial"` com `livres`. Até 8 curvas por gráfico.
