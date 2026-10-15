# 0012 — Resolver equações: `resolver`, `raiz` e `raizes`

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

Dimensionar é o caso mais comum de uma folha de engenharia: a folha conhece o alvo (a tensão
admissível, a flecha limite) e falta uma entrada (o diâmetro, a altura da viga). Escrever a
fórmula já isolada à mão é fonte de erro; a folha precisa resolver a equação para a incógnita,
com unidades, e continuar com o comportamento do TraçoLab: faltar um nome não é erro, a solução
fica pendente com chips.

## Decisão

Operadores `Especial` em `embutidos/Resolver.kt`, com a incógnita como nome ligado na posição 1:

- **Nomes:** `resolver(eq, x)` (pt-BR, o verbo que o engenheiro diria), com `solve` como
  sinônimo; `raiz(f, x, a, b)` / `raiz(f, x)` (sinônimo `root`); `raizes(v)` (sinônimo
  `polyroots`). Sem acento para digitar rápido.
- **Equação:** `a == b` (no editor, `a = b`: o `=` no meio já vira `==`; nas células de um vetor
  também) ou uma expressão, igualada a zero.
- **Simbólico primeiro:** `Solve` do Symja (por até 1,5 s quando tudo é número); se não serve,
  `FindRoot` a partir do palpite (`resolver(eq, x, x0)`, padrão 1) ou do intervalo
  (`resolver(eq, x, a, b)`, Brent).
- **Unidades:** a incógnita vale `X·u`, X adimensional. `u` vem do palpite/intervalo, ou é deduzida
  da equação: em cada termo X aparece como X^k e o resto tem dimensão d; dois termos com k
  diferentes dão `u = (dⱼ − dᵢ)/(kᵢ − kⱼ)` (expoentes arredondados ao 1/12). Conferida a
  homogeneidade, as unidades de base valem 1 e a equação segue em números do SI.
- **Várias raízes (tudo número):** só as reais; com palpite, a mais próxima; com unidade e uma
  só positiva, ela (o diâmetro); com intervalo, as de dentro dele; senão, o vetor das reais em
  ordem crescente. `raiz` devolve uma só (a menor).
- **Nome faltando:** a solução sai simbólica nele, pendente com chips. Par ±r: fica a escrita sem
  sinal de menos (dados positivos); outras multiplicidades: vetor.
- **Sistemas:** `resolver([eq1; eq2], [x; y])` devolve o vetor `[x; y]`; várias soluções, a única
  com todos positivos, ou uma matriz com uma coluna por solução. As unidades passam pelo Solve
  como símbolos e saem simplificadas (`expandirUnidades`).
- **Erros em pt-BR:** sem solução, infinitas soluções, só soluções complexas, sem raiz no
  intervalo/palpite, unidades incompatíveis.
- O nome ligado pode ser um vetor de nomes: `usados` (Texto.kt) aceita `No.Matriz` nas posições
  ligadas (mudança pequena em arquivo compartilhado).

## Alternativas consideradas

- **Bloco "resolver" à parte (estilo Given/Find):** mais poderoso para restrições, mas cria um
  tipo de região novo; a função cabe numa definição comum e no recálculo incremental que já existe.
- **Solve com as unidades como símbolos sempre:** funciona para o linear, mas o `FindRoot` não
  aceita unidades e o Symja deixa `√(m²)` sem simplificar; ficou só para sistemas.
- **Devolver sempre todas as raízes:** fiel, mas `d := resolver(...)` viraria vetor e quebraria
  as contas de baixo; a regra da positiva com unidade cobre o dimensionamento.

## Consequências

- O dimensionamento cabe numa linha: `d := resolver(σ_adm = F/(π d²/4), d)` → 17,84 mm.
- Raiz complexa só aparece em `raizes` (exibição ainda crua: `I*1.0`).
- Sistemas não têm recurso numérico nem palpite; desigualdades (Given/Find com restrições) ficam
  para depois.
