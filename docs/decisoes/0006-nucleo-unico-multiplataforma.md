# 0006 — Núcleo único para Android, Linux e webservice

- **Status:** Proposta (a prova de conceito confirmou o núcleo na JVM: [resultado](../pesquisa/prova-de-conceito-kotlin-symja.md))
- **Data:** 2026-10-12

## Contexto

O foco continua sendo o **app Android para tablet**. Queremos, porém, deixar aberta a
possibilidade de oferecer o software também como **aplicativo Linux** e como **webservice**,
sem reescrever o motor para cada plataforma.

O motor é a parte difícil e a que precisa dar **o mesmo resultado em todo lugar**: a mesma
página não pode calcular diferente no tablet e no servidor.

## Decisão (proposta)

Um **núcleo único** em Kotlin/JVM (motor de avaliação + CAS Symja), sem dependências de
plataforma. Cada plataforma é só uma "casca" em volta dele:

```
                 ┌──────────────────────────────┐
                 │ núcleo (Kotlin/JVM + Symja)  │
                 │ motor · formato da página    │
                 └──────┬──────────┬─────────┬──┘
                        │          │         │
                 Android app   Linux app   webservice (Ktor)
                 (Compose)     (Compose        │
                               Multiplatform)  página web / clientes HTTP
```

Regras que valem desde já, mesmo com o foco no Android:

1. O núcleo **não importa nada do Android** (nem `android.*`, nem `Context`).
2. O **formato da página** é JSON neutro, o mesmo nas três plataformas.
3. Os **casos de comportamento** ficam em JSON e são o teste de aceitação de qualquer
   implementação do núcleo.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Python + SymPy no Linux/servidor e Kotlin no Android | Dois motores para manter iguais; risco de resultados divergentes |
| SymPy embutido no Android (Chaquopy) para ter um motor só | APK grande e inicialização lenta (ver [ADR 0005](0005-cas-no-android-symja.md)) |
| Motor rodando no navegador | Viabilidade do Symja fora da JVM não verificada; o webservice já cobre a web |

## Consequências

- Reforça a [ADR 0005](0005-cas-no-android-symja.md): o Symja, por rodar na JVM, serve aos três destinos.
- **Licença:** os módulos do Symja usados são **LGPL-3.0**. O projeto pode ter a licença que
  quiser, desde que cumpra a LGPL na distribuição dos apps: avisar o uso, entregar a licença e
  permitir trocar a biblioteca. O webservice não distribui o Symja, então essas obrigações não
  se aplicam a ele.
- O webservice precisa de limites de tempo e de memória por requisição, porque cálculos
  simbólicos podem não terminar. O protótipo já mostrou isso: algumas planilhas estouraram o
  limite de 30 s.
- Ordem do roadmap mantida: Linux e webservice só entram depois do app Android.
