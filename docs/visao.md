# Visão

## O problema

Na engenharia, um cálculo raramente nasce com todos os dados. A pessoa escreve a fórmula da
tensão antes de saber o diâmetro, define a carga depois e troca o material no fim. As
ferramentas de desktop para memorial de cálculo lidam bem com isso: a página inteira é um
documento vivo que se recalcula sozinho.

No Android, e principalmente no tablet, não existe nada equivalente:

- **calculadoras** avaliam uma expressão por vez e esquecem o contexto;
- **apps de CAS** manipulam símbolos, mas não têm a noção de página com fórmulas que dependem
  umas das outras;
- quando falta uma variável, o máximo que fazem é dar **erro**.

## A proposta

Um caderno de cálculo para tablet em que:

1. **as fórmulas ficam posicionadas na página** e a posição define a ordem de cálculo;
2. **faltar variável não é erro**: a fórmula mostra o resultado parcial e lista o que falta;
3. **preencher um valor atualiza só o que depende dele**, em tempo real;
4. a escrita é feita com **caneta ou toque**, pensada para a tela grande.

## Plataformas

O foco é o **tablet Android**. O motor é feito para rodar também num **app Linux** e num
**webservice**, sem reescrita ([ADR 0006](decisoes/0006-nucleo-unico-multiplataforma.md)).
Essas versões vêm depois do Android.

## Para quem

Estudantes e profissionais de engenharia que fazem memoriais e conferências de cálculo e querem
fazer isso no tablet, no campo ou na sala de aula.

## Fora do escopo (por enquanto)

- Compatibilidade de arquivos com outros programas.
- Unidades de medida. É a primeira extensão planejada; veja o [roadmap](roadmap.md).
- Gráficos, solvers numéricos (`Find`/`Given`), leitura de arquivos externos.

## Princípio

Estudamos como ferramentas consagradas **se comportam** para acertar a experiência, mas a
implementação é **própria**. Nenhum código, arquivo ou dado de terceiros entra no projeto. Veja
[CONTRIBUTING](../CONTRIBUTING.md).
