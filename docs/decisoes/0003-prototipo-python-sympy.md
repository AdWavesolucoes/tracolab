# 0003 — Protótipo em Python + SymPy antes do app

- **Status:** Aceita
- **Data:** 2026-10-12

## Contexto

O risco principal do projeto está na **semântica**: ordem, estados, propagação, recálculo. A
interface vem depois. Errar a semântica dentro de um app Android sai caro, porque cada ciclo de
teste passa por build, instalação e toque na tela.

## Decisão

Validar o modelo primeiro num protótipo em **Python 3.13 + SymPy 1.14**, com:

- testes de comportamento automatizados ([casos](../comportamento/casos.md));
- validação contra um acervo de resultados de referência ([validação](../pesquisa/validacao.md)).

Só depois disso portar para Kotlin.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Começar direto em Kotlin + Symja | Ciclo de teste mais lento; o Symja é menos conhecido para explorar a semântica |
| Protótipo em JavaScript (math.js / nerdamer) | CAS mais fraco para a parte simbólica |

## Consequências

- Os testes do protótipo viram a **especificação executável** que a versão Kotlin precisa
  passar. Convém exportá-los em formato neutro (JSON) antes do porte.
- Atenção ao SymPy 1.13+: `Float(6.0) == 6` é **falso**. Comparações numéricas precisam
  converter o valor para número antes.
