# 0008 — Interface em página web com MathLive, ligada ao núcleo

- **Status:** Aceita
- **Data:** 2026-10-13

## Contexto

A interface do tablet precisa de: página com regiões posicionáveis, **desenho de fórmulas em
2D** (frações, potências, raízes, matrizes), **edição com cursor dentro da fórmula 2D**, teclado
matemático e de unidades, e exibição dos estados (numérico, parcial, erro).

Estimativa de fazer tudo nativo do zero: **12–21 sessões**. Mais de metade do custo e quase todo
o risco estão no desenho e na edição de fórmulas 2D.

## Decisão

- A página é **web** (HTML/CSS/JS), com um editor **[MathLive](https://github.com/arnog/mathlive)**
  (MIT, versão 0.110.0 embutida em `web/vendor/`, sem depender de rede) em cada região.
- O cálculo continua no **núcleo Kotlin** (ADR 0005/0006). A página manda o LaTeX de cada região
  e a posição; o núcleo devolve o estado e o resultado em LaTeX.
- A conversão **LaTeX → sintaxe do núcleo** fica em Kotlin (`nucleo/.../latex/`), com testes, e é
  a mesma para todos os destinos.
- A ponte é `window.TracoLabNativo.calcular(json)` no app Android (WebView) e `POST /api/calcular`
  no navegador (servidor de desenvolvimento hoje, webservice no futuro).
- Inspiração de experiência de uso: [EngineeringPaper.xyz](https://github.com/mgreminger/EngineeringPaper.xyz)
  (MIT), também baseado em MathLive.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Tudo nativo (Compose) do zero | 12–21 sessões; o editor de fórmulas 2D é o maior risco do projeto |
| Fork do EngineeringPaper.xyz | O motor dele é SymPy no navegador (Pyodide): descartaria o núcleo validado no tablet e pesaria no aparelho |
| Converter LaTeX em JavaScript | Duas implementações (app e servidor) para manter iguais |

## Consequências

- A **mesma página** serve ao Android, à web e ao Linux (ADR 0006).
- `\mathrm{...}` passa a ser reservado para **unidades** (teclado de unidades), coerente com a
  ADR 0007. Letras seguidas formam um nome, e nome seguido de `(` é chamada de função.
- Os atalhos de digitação do MathLive são substituídos por uma lista controlada: o padrão troca
  `in` por ∈ e quebraria nomes como `Fin`.
- Estimativa revista para a interface: **6–10 sessões**.
