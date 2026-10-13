# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), com
versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [Não publicado]

### Corrigido
- 2026-10-13: na interface, o texto digitado não era lido e a região sumia ao tocar fora. O `<math-field>` era preenchido antes de o MathLive ativá-lo, e a atribuição encobria a propriedade `value`. O Enter agora confirma e abre uma região abaixo. Teste de interface em `ferramentas/teste-ui/` (11 verificações).

### Adicionado
- 2026-10-13: interface web (`web/`) com MathLive 0.110.0 embutido, conversor LaTeX ⇄ núcleo em Kotlin (20 testes), módulo `api` (JSON) e `servidor-dev`; ADR 0008.
- 2026-10-13: núcleo Kotlin no repositório (`nucleo/`, `compat/log4j-android/`, `bancada/`), wrapper do Gradle com hash fixado, testes JUnit gerados da especificação e CI no GitHub Actions. O modo de validação passa a se chamar `classico`, e a mensagem de variável indefinida passa a ser "Variável não definida.".
- 2026-10-13: projeto nomeado **TracoLab** (traço da S Pen); repositório Git iniciado.
- 2026-10-13: núcleo testado no Galaxy Tab S10 Lite: 30/30 casos, recálculo de 110 regiões em 33–63 ms, APK de 8,5 MB; ADR 0005 aceita; `LogManager` substituta para o Symja rodar no Android.
- 2026-10-12: especificação executável `spec/casos-comportamento.json` (30 casos), rodada pelo protótipo Python e pelo núcleo Kotlin.
- 2026-10-12: prova de conceito do núcleo em Kotlin + Symja: 30/30 casos, recálculo de 110 regiões em 30 ms; licença do Symja confirmada como LGPL-3.0.
- 2026-10-12: unidades de medida no motor (SI + imperiais, conversão, checagem de dimensão, exibição na unidade pedida); ADR 0007; 11 testes novos; validação em 29,2% de cobertura e 94,0% de acerto.
- 2026-10-12: ADR 0006 (proposta): núcleo único para Android, Linux e webservice; fase 4 no roadmap.
- 2026-10-12: vault/repositório criado, com a documentação do estudo de comportamento, o modelo de
  avaliação, os registros de decisão 0001 a 0005 e o roadmap.
- 2026-10-12: protótipo do motor em Python + SymPy, com dois modos (`classico` e `parcial`),
  recálculo incremental e 17 testes de comportamento.
- 2026-10-12: extrator de planilhas usado no estudo, para ler o formato e gerar os casos de validação.
