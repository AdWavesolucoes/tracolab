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
| Motor em Kotlin + Symja | ✅ no repositório (`nucleo/`): 30/30 na JVM e no Galaxy Tab S10 Lite ([resultado](docs/pesquisa/prova-de-conceito-kotlin-symja.md)) |
| Interface para tablet | 🛠️ página web com MathLive no ar para testes ([ADR 0008](docs/decisoes/0008-interface-web-mathlive.md)); APK em seguida |
| Versões Linux e webservice | 💭 possíveis, depois do Android ([ADR 0006](docs/decisoes/0006-nucleo-unico-multiplataforma.md)) |

Veja o [roadmap](docs/roadmap.md).

## Estrutura

```
nucleo/                  motor de cálculo em Kotlin + Symja, sem dependência de Android (ADR 0006)
api/                     página em JSON (LaTeX + posição) → estado de cada região
web/                     interface: página com regiões e editor MathLive (ADR 0008)
servidor-dev/            serve web/ + API na rede local, para testar no navegador do tablet
compat/log4j-android/    log4j-api com LogManager compatível com Android (o Symja precisa dele)
bancada/                 medição de desempenho na JVM (a mesma usada no tablet)
spec/                    especificação executável: os casos que todo motor precisa passar
docs/                    documentação, decisões (ADRs) e pesquisa
```

## Como rodar

Requer JDK 17 ou mais novo. O Gradle vem pelo wrapper.

```bash
./gradlew build            # compila e roda os 30 casos de comportamento (JUnit)
./gradlew :bancada:run     # mede o desempenho na sua máquina
./gradlew :servidor-dev:run --args="0.0.0.0 8098"   # abre a interface em http://<ip>:8098/
```

## Documentação

Comece pelo [índice da documentação](docs/README.md).

## Como foi feito

| Ferramenta | Uso no projeto |
|---|---|
| [Claude Code](https://claude.com/claude-code) (Anthropic) | Desenvolvimento assistido por IA: estudo do comportamento, protótipo, núcleo Kotlin, testes e documentação |
| [REA](https://github.com/morluto/rea) + [Ghidra](https://github.com/NationalSecurityAgency/ghidra) | Inspeção de artefatos binários na fase de estudo do comportamento |
| [MathLive](https://github.com/arnog/mathlive) (MIT) | Editor de fórmulas da interface (`web/vendor/`) |
| [SymPy](https://www.sympy.org/) | Motor simbólico do protótipo em Python |
| [Symja](https://github.com/axkr/symja_android_library) (LGPL-3.0) | Motor simbólico do núcleo em Kotlin, no Android e na JVM |

## Licença

A definir antes da primeira publicação. Veja o [roadmap](docs/roadmap.md).
