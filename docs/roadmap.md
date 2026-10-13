# Roadmap

## Fase 0 — Entender o comportamento ✅

- [x] Estudar planilhas de referência e medir a ordem, a propagação de erro e as redefinições
- [x] Escrever o [modelo de avaliação](comportamento/modelo-de-avaliacao.md)

## Fase 1 — Protótipo do motor (Python + SymPy) 🛠️

- [x] Estados numérico / parcial / erro / não suportado
- [x] Dois modos (parcial e clássico)
- [x] Recálculo incremental (só o que depende)
- [x] Funções, faixas, vetores, programas simples, Σ/∫/d/dx
- [x] 28 testes de comportamento
- [x] Validação contra resultados de referência (rodadas 1-2)
- [ ] Subir a cobertura da validação, que hoje é limitada por unidades e embutidos
- [x] **Unidades de medida** (m, kN, MPa...): cobertura da validação de 20,5% para 29,2% ([ADR 0007](decisoes/0007-unidades-explicitas.md))
- [x] Exportar os casos de comportamento em JSON neutro ([spec/](../spec/README.md))

## Fase 2 — Motor no Android

- [x] Prova de conceito Kotlin + Symja na JVM: 30/30 casos ([resultado](pesquisa/prova-de-conceito-kotlin-symja.md))
- [x] Medir o Symja no tablet (Galaxy Tab S10 Lite): 30/30, 110 regiões em 33–63 ms, APK de 8,5 MB
- [x] Portar o motor para Kotlin como **biblioteca sem dependência de Android** ([ADR 0006](decisoes/0006-nucleo-unico-multiplataforma.md)), passando os mesmos casos: `nucleo/`, 30/30 no `./gradlew build`

## Fase 3 — Interface para tablet

- [x] Página com regiões posicionáveis (arrastar, guia de linha de base) — web + MathLive ([ADR 0008](decisoes/0008-interface-web-mathlive.md))
- [x] Entrada de fórmula pelo teclado matemático (abas TracoLab e Unidades)
- [ ] Escrita à mão com a S Pen (MyScript iink é a candidata; ML Kit não reconhece matemática)
- [x] Exibição do estado parcial: expressão restante + "faltam: …"
- [ ] APK: a mesma página numa WebView, com a ponte `TracoLabNativo`
- [ ] Salvar e abrir páginas (formato próprio)

## Fase 4 — Outras plataformas (depois do Android)

Possível graças ao núcleo único ([ADR 0006](decisoes/0006-nucleo-unico-multiplataforma.md)):

- [ ] App Linux (Compose Multiplatform, reaproveitando a interface)
- [ ] Webservice (Ktor) com limites de tempo/memória por cálculo + página web

## Antes de publicar no GitHub

- [x] Escolher o nome definitivo do projeto: **TracoLab** (2026-10-13)
- [ ] Escolher a licença (o Symja é LGPL-3.0: compatível com licenças permissivas, desde que se cumpram as obrigações da LGPL nos apps)
- [ ] Conferir que nenhum arquivo ou dado de terceiros está no repositório, inclusive no histórico
- [ ] Revisar o README para quem chega sem contexto
