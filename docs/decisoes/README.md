# Registros de decisão (ADRs)

Cada decisão de projeto relevante fica registrada aqui com contexto, alternativas e
consequências. Uma decisão nunca é apagada: quando muda, um novo ADR a **substitui**. Para
criar um novo, use o [modelo](0000-modelo.md).

| # | Decisão | Status |
|---|---|---|
| [0001](0001-ordem-pela-linha-de-base.md) | A ordem de cálculo usa a linha de base da região | Aceita |
| [0002](0002-modo-parcial-como-padrao.md) | Variável que falta gera resultado parcial, não erro | Aceita |
| [0003](0003-prototipo-python-sympy.md) | Protótipo em Python + SymPy antes do app | Aceita |
| [0004](0004-matrizes-por-coluna.md) | Matrizes serializadas por coluna | Aceita |
| [0005](0005-cas-no-android-symja.md) | Symja como motor simbólico no Android | Substituída por 0019 (o Symja fica; o alvo Android sai) |
| [0006](0006-nucleo-unico-multiplataforma.md) | Núcleo único para Android, Linux e webservice | Aceita (confirmada pela 0019: o núcleo serve o serviço web) |
| [0007](0007-unidades-explicitas.md) | Unidades de medida explícitas | Aceita |
| [0008](0008-interface-web-mathlive.md) | Interface em página web com MathLive, ligada ao núcleo | Aceita |
| [0009](0009-versao-web-de-teste.md) | Versão web de teste antes do APK | Substituída por 0019 |
| [0010](0010-vetores-e-matrizes.md) | Vetores e matrizes: representação, operadores e unidades por elemento | Aceita |
| [0013](0013-condicionais-logica-e-verificacoes.md) | Condicionais, lógica, textos e verificações de engenharia | Aceita |
| [0014](0014-unidades-da-folha-e-formato.md) | Unidades da folha, temperaturas e formato dos números | Aceita |
| [0015](0015-folha-como-documento.md) | A folha como memorial: texto, definir e mostrar, substituição e arquivo | Aceita |
| [0017](0017-limites-de-calculo.md) | Limites de tempo, recursão e tamanho em cada cálculo | Aceita |
| [0011](0011-calculo.md) | Σ, Π, ∫ e d/dx com nomes ligados, unidades e pendências | Aceita |
| [0012](0012-resolver.md) | Resolver equações e sistemas para a incógnita | Aceita |
| [0016](0016-graficos.md) | Gráficos X-Y com amostragem no núcleo | Aceita |
| [0018](0018-funcoes.md) | Biblioteca de funções: números, estatística, interpolação | Aceita |
| [0019](0019-servico-web-multiusuario.md) | TraçoLab como serviço web auto-hospedável, com contas e 2FA | Aceita |
