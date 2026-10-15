# Regressões do núcleo e testes de propriedade

Bugs achados no núcleo e nos conversores LaTeX, cada um com um caso que falhava antes da correção.
Os casos de comportamento estão em [`spec/casos/regressoes.json`](../../spec/casos/regressoes.json)
(ids `reg-NN`); os que não cabem no formato da especificação estão em
`nucleo/src/test/kotlin/tracolab/nucleo/RegressoesTest.kt` e `LatexRegressoesTest.kt`.

| Caso | O que acontecia | Como ficou |
|---|---|---|
| reg-01 | `4000{Hz}/64 + 1{Hz}` dava "unidades não compatíveis"; `f = {Hz}` acusava unidade incompatível. A dimensão de `1/s` tinha `-0.0` | Dimensões sem zero negativo |
| reg-02 | `k := 2` e `v[k] =` davam "não suportado" (o 2 fica guardado como 2.0) | Índice inteiro em ponto flutuante vale |
| reg-03 | `w[0.5] := 1` e `u[i] := i` com `i := 0, 0.5..1` truncavam o índice em silêncio | Erro: o índice deve ser inteiro |
| reg-04 | Vetor ou índice faltando (`w[1]`, `v[m]`, `u[n] := 3`) dava "não suportado" ou erro | Fica pendente, como qualquer nome que falta |
| reg-05 | `c := sqrt(a^2 + b^2)` com a e b em metros derrubava a página inteira (exceção ao mostrar `5·√(m²)`); `√(4 m²) > 1 m` dava "deve ser adimensional" | Raízes e potências de unidades simplificadas (unidades de base são positivas) |
| reg-06 | `x := 1/0` e depois `x := x + 1` deixava "falta x" | Herda o erro do `x` de cima; só a função recursiva fica isenta |
| reg-07 | Faixa com unidade (`0{m}, 0.5{m}..2{m}`) dava erro interno ao ser usada | Os limites são lidos na unidade comum |
| reg-08 | Faixa com limite faltando (`i := 0..n`) dava erro interno | Fica pendente (falta n), e quem a usa também |
| reg-09 | Mover uma definição para baixo não recalculava quem ficou entre a posição antiga e a nova (recálculo incremental) | O recálculo começa na menor das duas posições |
| reg-10 | `(34 + L) + 5{MPa}` ficava pendente, mas `34 + 5{MPa} + L` dava erro | Os termos conhecidos dos dois lados da soma têm de combinar |
| reg-11 | Recorrência `v[i] := v[i-1]*2` com `i` de faixa dava "índice fora do intervalo" | Cada passo enxerga o que os anteriores gravaram |

Também corrigido (testes Kotlin): `√(−4) =` derrubava a exibição; potência de dez aparecia
como `5*10^{6}` (com asterisco) no resultado pendente; `a = b =` dava erro de sintaxe;
`{}`, `{m^}`, `{(m}` e um `.` sozinho davam exceções cruas e `{m) kg}` ignorava o resto;
`M[1, 1] := 5` gravava `v[1]` em silêncio (antes das matrizes); editar ou remover região
inexistente não dizia qual.

## Testes de propriedade

`PropriedadesTest` gera folhas ao acaso (semente fixa) e confere:

1. recálculo incremental (adicionar, editar, mover, remover, com globais e funções) dá o
   mesmo estado que recalcular a folha inteira;
2. definir os valores acima dá o mesmo número que substituí-los no resultado pendente;
3. toda unidade da tabela volta ao mesmo número (`x := 3.7{u}`, `x = {u}`);
4. na mesma linha de base vale a posição x;
5. o LaTeX que a tela mostra, digitado de volta, dá o mesmo valor (até os 6 algarismos da tela).

`LatexRegressoesTest.fuzzDoConversor` monta 4000 LaTeX com pedaços do que o editor produz:
cada um só pode virar texto ou dar `ErroLatex`, e o texto só pode virar região ou dar
`ErroSintaxe`. Qualquer outra exceção seria "erro interno" na tela.

## Avaliação em lote

`bancada lote <entrada.jsonl> <saida.jsonl>` calcula muitas folhas de uma vez e grava o estado
de cada região (tipo, nomes que faltam, números na unidade pedida ou no SI, texto da tela). O
formato está no começo de `bancada/src/main/kotlin/tracolab/bancada/Lote.kt`. Serve para
comparar o núcleo com resultados de referência obtidos fora do repositório (a entrada e a
comparação não são versionadas) e para comparar duas versões do núcleo nas mesmas folhas.
