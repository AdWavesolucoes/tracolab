# TracoLab

> **Traço** de caneta: o app vai receber as fórmulas escritas à mão com a S Pen.

Caderno de cálculo para **tablet Android** em que as fórmulas **continuam funcionando quando
faltam variáveis**. Em vez de mostrar erro, o app mostra o resultado parcial (`2·a`) e o completa
conforme os valores são preenchidos (`6`).

Os apps de calculadora e de CAS (sistema de álgebra computacional) para Android tratam cada
expressão isoladamente. Este projeto trata a página como uma **planilha de engenharia**: as
fórmulas ficam em posições na tela, dependem umas das outras e são recalculadas na ordem certa
sempre que algo muda.

## Exemplo

```
d := 0,02            ← o usuário escreveu primeiro o diâmetro
A := π·d²/4
σ := F/A
σ =  1,27·F / d²     ← antes de d existir: parcial, falta F e d
σ =  3183·F          ← depois de d: parcial, falta F
σ =  3,18·10⁶        ← depois de F := 1000: número
```

## Status

| Etapa | Situação |
|---|---|
| Estudo do comportamento esperado | ✅ concluído ([modelo de avaliação](docs/comportamento/modelo-de-avaliacao.md)) |
| Protótipo do motor (Python + SymPy) | ✅ funcionando, com unidades; 28/28 testes; [validação](docs/pesquisa/validacao.md): 94% de acerto, 29% de cobertura |
| Motor em Kotlin + Symja | ✅ prova de conceito: 30/30 na JVM e no Galaxy Tab S10 Lite ([resultado](docs/pesquisa/prova-de-conceito-kotlin-symja.md)) |
| Interface para tablet | ⏳ planejado |
| Versões Linux e webservice | 💭 possíveis, depois do Android ([ADR 0006](docs/decisoes/0006-nucleo-unico-multiplataforma.md)) |

Veja o [roadmap](docs/roadmap.md).

## Documentação

Comece pelo [índice da documentação](docs/README.md).

## Como foi feito

| Ferramenta | Uso no projeto |
|---|---|
| [Claude Code](https://claude.com/claude-code) (Anthropic) | Desenvolvimento assistido por IA: estudo do comportamento, protótipo, núcleo Kotlin, testes e documentação |
| [REA](https://github.com/morluto/rea) + [Ghidra](https://github.com/NationalSecurityAgency/ghidra) | Inspeção de artefatos binários na fase de estudo do comportamento |
| [SymPy](https://www.sympy.org/) | Motor simbólico do protótipo em Python |
| [Symja](https://github.com/axkr/symja_android_library) (LGPL-3.0) | Motor simbólico do núcleo em Kotlin, no Android e na JVM |

## Licença

A definir antes da primeira publicação. Veja o [roadmap](docs/roadmap.md).
