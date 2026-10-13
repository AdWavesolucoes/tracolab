# Prova de conceito: núcleo em Kotlin + Symja

**Data:** 2026-10-12 · **Ambiente:** JVM do servidor de desenvolvimento (OpenJDK 21, x86-64)

## Objetivo

Antes de investir no app Android, responder a duas perguntas
([ADR 0005](../decisoes/0005-cas-no-android-symja.md) e [ADR 0006](../decisoes/0006-nucleo-unico-multiplataforma.md)):

1. O Symja reproduz o comportamento especificado tão bem quanto o SymPy do protótipo?
2. O desempenho é aceitável para recálculo interativo?

## O que foi construído

Um núcleo mínimo em Kotlin 2.0, com o Symja 3.0.0 (Maven Central) como CAS:

- parser próprio da sintaxe de texto (`+ - * / ^`, funções, índices, comparações, `20{mm}`);
- planilha com ordem por linha de base, globais, estados, propagação de erro e recálculo incremental;
- funções com captura de ambiente, faixas e vetores;
- unidades: a tabela de 194 nomes é **gerada a partir do protótipo Python**, para garantir os
  mesmos fatores nas duas implementações;
- um executor de [`spec/casos-comportamento.json`](../../spec/casos-comportamento.json).

## Resultados

### Comportamento

| Implementação | Casos da especificação |
|---|---|
| Protótipo Python + SymPy | **30/30** |
| Núcleo Kotlin + Symja | **30/30** |

### Desempenho (JVM do servidor)

| Medida | Kotlin + Symja | Python + SymPy |
|---|---|---|
| Inicialização do CAS | ~1.040 ms | — |
| Caso da especificação (mediana) | 1,4 ms | — |
| Página de 110 regiões com unidades: montar | 38 ms | 53 ms |
| Editar a 1ª definição (110 regiões recalculadas) | **30 ms** | 45 ms |
| Editar uma definição do meio (50 recalculadas) | **10 ms** | 20 ms |

Valor final da página de teste: **8.787,21 mm** nas duas implementações e na conta direta.

### Tamanho e licença

| Item | Valor |
|---|---|
| Dependências (Symja + transitivas) | 59 jars, **43 MB** |
| Licença de `matheclipse-core`, `-parser`, `-external` | **LGPL-3.0** (declarada no POM do Maven Central) |

## Lições

> [!WARNING] O Symja guarda nomes de símbolo em minúsculas
> As variáveis `F` e `f` viravam o mesmo símbolo, um erro silencioso. O caso 18 da
> especificação pegou isso. Solução: o nome de cada variável é **codificado em hexadecimal**
> (injetivo e sem maiúsculas) antes de virar símbolo, e decodificado na volta. Variáveis e
> unidades base também levam prefixos próprios, para nunca colidir com funções embutidas do
> Symja (`N`, `D`, `E`, `I`, `C`).

- A especificação em JSON funcionou como pretendido: a mesma bateria rodou nas duas
  linguagens sem adaptação.
- Na JVM do servidor a inicialização levou ~1 s. No tablet, com R8, levou **270 ms**. Mesmo
  assim, convém iniciar o CAS em segundo plano enquanto a página carrega.

## Conclusão

- **Comportamento:** o Symja atende. Os 30 casos passaram, inclusive simbólico exato,
  unidades e recálculo incremental.
- **Desempenho na JVM:** sobra folga. Recalcular uma página de 110 regiões leva 30 ms.
- **Riscos para o Android:** todos resolvidos na fase 2 (abaixo): desempenho medido no
  aparelho, APK de 8,5 MB com R8 e as incompatibilidades do Symja contornadas.

## Fase 2: no tablet (2026-10-13)

**Aparelho:** Samsung Galaxy Tab S10 Lite (Exynos 1380: 4× Cortex-A78 2,4 GHz + 4× A55; Android 15 / One UI 7).
**Como:** APK de medição com a **mesma bancada** usada na JVM (o módulo do núcleo não depende de
Android), em duas compilações: *debug* e *release* com R8.

| Medida | JVM do servidor | Tablet, release (R8) | Tablet, debug |
|---|---|---|---|
| Casos da especificação | 30/30 | **30/30** | 30/30 |
| Inicialização do Symja | 1.050 ms | **270 ms** | 1.251 ms |
| Recalcular 110 regiões (fria → aquecida) | 34 → 11 ms | **63 → 33 ms** | 157 → 102 ms |
| Recalcular 50 regiões | 13 → 4 ms | **20 → 15 ms** | 64 → 42 ms |
| Memória em uso | 31 MB | **28 MB** | 31 MB |
| Tamanho do APK | — | **8,5 MB** | 20,5 MB |

Na compilação de loja (R8), o recálculo de uma página inteira fica abaixo de ~100 ms (sensação de
instantâneo) e a inicialização é curta. Os 43 MB de dependências viram um APK de 8,5 MB.

### Incompatibilidades encontradas e resolvidas

Foram três rodadas no aparelho: duas com erro (cada erro revelou um problema) e a terceira com 30/30.

| Rodada | Erro no aparelho | Causa | Correção |
|---|---|---|---|
| 1 (R8) | `DefaultFlowMessageFactory cannot be instantiated` | O R8 removeu um construtor que o Log4j chama por reflexão | `-keep` para Log4j, Kryo, Objenesis, Apfloat e Ehcache |
| 1 (debug) | `LogManager.callerClass`: *No class provided* | **O Log4j descobre quem chamou `getLogger()` com recursos da JVM de desktop que não existem no Android**, e o Symja faz essa chamada ao iniciar | `LogManager` substituta (mesmas assinaturas, logger de nome fixo) num jar próprio que reempacota o `log4j-api` |
| 2 (debug) | `NoClassDefFoundError: SimpleLoggerContextFactory` | O Android usou só a classe compilada do módulo, não o jar reempacotado | Depender do jar, não do módulo |
| 2 (R8) | `Invalid signature file digest for Manifest main attributes` | `DUMMY.SF/DSA` de uma dependência entraram no APK; uma regra genérica de duplicatas anulava a exclusão | Excluir as assinaturas e listar as duplicatas uma a uma |

> [!IMPORTANT] Para o app de verdade
> O Symja do Maven Central **não roda no Android sem a `LogManager` substituta**. Ela precisa
> ir junto com o núcleo. O empacotamento também precisa manter
> `META-INF/maven/org.matheclipse/matheclipse-core/pom.properties`, que o Symja lê ao iniciar
> e que o Android Gradle Plugin exclui por padrão.

O núcleo desta prova de conceito agora está no repositório: `nucleo/` (motor), `compat/log4j-android/`
(a `LogManager` substituta) e `bancada/` (esta medição na JVM). O APK de medição do tablet ainda
não está no repositório, porque depende do Android SDK, e entra junto com o app.
