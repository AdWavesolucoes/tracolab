# TraçoLab

> **Traço** de caneta, como o da S Pen no tablet onde o projeto nasceu.

Caderno de cálculo de engenharia **que roda no navegador** e em que **faltar variável não é
erro**: a fórmula mostra o que já dá para calcular e lista o que falta. Você hospeda na sua
rede (ou na internet), cada pessoa tem a sua conta e as suas folhas, e o código é aberto.

```
d := 20 mm            ← o usuário escreveu primeiro o diâmetro
A := π·d²/4
σ := F/A
σ =  3,18·F           falta F        ← o chip "falta F" cria a definição acima
F := 60 kN
σ =  191 MPa                         ← pronto, na unidade da folha
```

Os apps de calculadora e de CAS tratam cada expressão isoladamente. O TraçoLab trata a página
como uma **planilha de engenharia**: as fórmulas ficam em posições na folha, dependem umas das
outras e são recalculadas na ordem certa sempre que algo muda. Unidades são parte do número
(`20 mm`, `kN/m²`, `°C`), a folha vira memorial (texto, "definir e mostrar", substituição dos
valores, impressão em A4) e tem vetores e matrizes, Σ Π ∫ d/dx, resolver equações, verificações
"atende / não atende" e gráficos X-Y.

## Rodar na sua máquina

Com Docker (amd64 ou arm64):

```bash
docker run -d --name tracolab -p 8098:8098 -v tracolab-dados:/dados ghcr.io/adwavesolucoes/tracolab:latest
```

Abra `http://<máquina>:8098/`. A **primeira conta** criada é a do administrador; depois dela o
cadastro é por **convite** (padrão), aberto ou fechado. Cada conta pode ligar a **verificação
em duas etapas** (TOTP, qualquer app autenticador). Tudo fica num único arquivo SQLite no volume
`/dados`.

Sem Docker: `./gradlew :servidor:installDist` e `servidor/build/install/servidor/bin/servidor`
(Java 17+), ou `ferramentas/instalar-systemd.sh local` para instalar como serviço.

O guia completo — variáveis, proxy com HTTPS, cópia de segurança, atualização — está em
[docs/hospedar.md](docs/hospedar.md).

## Instalar no tablet ou no celular

A página é uma PWA: no navegador, "Adicionar à tela inicial". Abre em tela cheia, com o teclado
matemático e a faixa de teclas pensados para toque e caneta.

## Estrutura

```
nucleo/        motor de cálculo em Kotlin + Symja, sem dependência de plataforma (ADR 0006)
api/           página em JSON (LaTeX + posição) → estado de cada região (ADR 0017: limites)
servidor/      Ktor: página, API, contas com 2FA, folhas por usuário, SQLite (ADR 0019)
web/           interface: folha com regiões e editor MathLive, página de conta, PWA (ADR 0008)
spec/          especificação executável: os casos que todo motor precisa passar
ferramentas/   harness do serviço e da interface, instalador systemd, geração de ícones
bancada/       medição de desempenho na JVM
docs/          documentação, decisões (ADRs) e pesquisa
```

## Desenvolver

Requer JDK 17 ou mais novo (o Gradle vem pelo wrapper).

```bash
./gradlew build                          # compila e roda os ~400 testes (especificação de comportamento, API, servidor)
ferramentas/harness/servico.sh           # sobe um servidor limpo e confere o contrato HTTP inteiro (contas, 2FA, folhas, API)
ferramentas/harness/interface.sh         # os roteiros de interface no Chromium (toque, tablet 1280×800)
./gradlew :servidor:run                  # servidor de desenvolvimento em http://127.0.0.1:8098/ (dados em ./dados)
```

Como contribuir: [CONTRIBUTING.md](CONTRIBUTING.md). Comece pelo [índice da documentação](docs/README.md)
e pelo [roadmap](docs/roadmap.md).

## Como foi feito

| Ferramenta | Uso no projeto |
|---|---|
| [Claude Code](https://claude.com/claude-code) (Anthropic) | Desenvolvimento assistido por IA: estudo do comportamento, protótipo, núcleo Kotlin, servidor, testes e documentação |
| [REA](https://github.com/morluto/rea) + [Ghidra](https://github.com/NationalSecurityAgency/ghidra) | Inspeção de artefatos binários na fase de estudo do comportamento |
| [Symja](https://github.com/axkr/symja_android_library) (LGPL-3.0) | Motor simbólico do núcleo |
| [Ktor](https://ktor.io/) (Apache-2.0) | Servidor HTTP |
| [SQLite](https://sqlite.org/) via [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) (Apache-2.0) | Banco de dados num arquivo |
| [Bouncy Castle](https://www.bouncycastle.org/) (MIT) | Argon2id para as senhas |
| [ZXing](https://github.com/zxing/zxing) (Apache-2.0) | Código QR da verificação em duas etapas |
| [MathLive](https://github.com/arnog/mathlive) (MIT) | Editor de fórmulas da interface (`web/vendor/`) |
| [IBM Plex](https://github.com/IBM/plex) e [Bricolage Grotesque](https://github.com/ateliertriay/bricolage) (OFL-1.1) | Fontes da interface, embutidas em `web/vendor/fontes/` |
| [SymPy](https://www.sympy.org/) | Motor simbólico do protótipo em Python (fase de estudo) |

## Licença

[GNU AGPL-3.0](LICENSE). Você pode usar, estudar, modificar e hospedar o TraçoLab, inclusive
como serviço público; se distribuir ou hospedar uma versão modificada, o código dela precisa
ficar disponível a quem usa, sob a mesma licença.
