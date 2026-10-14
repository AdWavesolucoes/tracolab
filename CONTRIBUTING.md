# Como contribuir

## Documentação (este vault)

A pasta raiz serve ao mesmo tempo como **vault do Obsidian** e como **repositório Git**.
Para os dois funcionarem juntos:

- **Links em markdown padrão**, com caminho relativo: `[texto](docs/arquivo.md)`.
  Não use `[[wikilinks]]`, porque o GitHub não os renderiza. O vault já vem configurado
  (`.obsidian/app.json`) para o Obsidian gerar links nesse formato.
- **Imagens e anexos** ficam em `docs/assets/`.
- **Nomes de arquivo** em minúsculas, com hífen e sem acento: `modelo-de-avaliacao.md`.
- **Decisões de projeto** viram um registro de decisão (ADR) em `docs/decisoes/`, numerado
  (`0006-titulo.md`), usando o [modelo](docs/decisoes/0000-modelo.md). Decisão registrada não é
  apagada: se mudar, um novo ADR a substitui e o antigo fica marcado como "Substituída".
- **Toda mudança relevante** entra no [CHANGELOG](CHANGELOG.md), na seção `Não publicado`.

## Código

- **Kotlin** no estilo oficial (`kotlin.code.style=official`), com nomes em português.
- O módulo `nucleo` **não pode depender de Android** ([ADR 0006](docs/decisoes/0006-nucleo-unico-multiplataforma.md)).
- **Todo comportamento novo começa por um caso na especificação**: `spec/casos-comportamento.json`
  ou o arquivo da área em `spec/casos/`. Os testes do núcleo rodam todos (`./gradlew build`), e o
  caso precisa falhar antes da mudança e passar depois dela.
- **Funções embutidas** ficam no registro do núcleo: um arquivo por área em
  `nucleo/src/main/kotlin/tracolab/nucleo/embutidos/`, listado em `Modulos.kt`.
- **Interface (`web/js/`)**: scripts clássicos, um por assunto. Uma área nova entra num arquivo
  próprio e se registra nos pontos de extensão `TL` (ver `web/js/configuracao.js`).
- Mexeu em desempenho? Compare `./gradlew :bancada:run` antes e depois.
- Mexeu na interface (`web/`)? Rode `ferramentas/teste-ui/fluxo.mjs` com o `servidor-dev` no ar.

## Regra de conteúdo: só o que é nosso

O projeto estuda o **comportamento** de ferramentas de cálculo existentes, sem copiar nada delas.
Por isso, **nunca versionar**:

- arquivos, planilhas, modelos ou executáveis de software de terceiros;
- dados extraídos desses arquivos (a pasta `dados-externos/` é ignorada pelo Git);
- trechos de código decompilado.

O que entra são conclusões, números agregados e casos de teste **escritos por nós**.

## Mensagens de commit

Seguir [Conventional Commits](https://www.conventionalcommits.org/pt-br/), em português:

```
docs: descreve propagação de erro no modelo de avaliação
feat(motor): suporta somatório sobre variável de faixa
fix(motor): global ≡ recalcula regiões acima dele
```

## Segredos

Nunca versionar chaves, tokens ou senhas. Antes do primeiro push, varrer também o
**histórico** do Git, não só os arquivos atuais.
