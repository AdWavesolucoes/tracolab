# 0017 — Limites de tempo, recursão e tamanho em cada cálculo

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

Medido no servidor-dev com `ferramentas/robustez/hostis.py`, 30 entradas acidentais ou
maliciosas, antes desta decisão:

- `x := 10^{10^{10}}` e `9^{9^{9^{9}}}` travavam o servidor (mais de 40 s, só reiniciando). No
  tablet, travariam o app.
- `f(x) := f(x)` (recursão sem fim) estourava a pilha: a conexão fechava sem resposta e a página
  mostrava "sem conexão".
- `10^{400} =` derrubava a **folha inteira** com "Infinite or NaN".
- Uma faixa de 200 mil valores devolvia 4,6 MB de LaTeX, que nenhum editor desenha.
- Um nome de 1 milhão de letras travava (conversão quadrática do LaTeX).
- Um cliente que mandava o corpo devagar prendia o servidor (uma thread só); um cálculo de 4 s
  fazia todos os outros esperarem (um motor só, sincronizado).

O mesmo núcleo roda no Android, onde não há servidor para reiniciar.

## Decisão

Limites em três camadas, cada um virando **erro só na região** (mensagem em pt-BR), com o resto
da folha calculando:

1. **Núcleo** (vale no app e no servidor), em `Limites`: prazo por região (3 s) e por folha
   (15 s), aplicado junto pelo nosso código (`Motor.verificarPrazo()`) e pelo Symja (um vigia pede
   a parada ao `EvalEngine` no fim do prazo); profundidade de recursão de função do usuário (200);
   valores de faixa, de resultado e de vetor (200 mil). Limites globais do Symja (inteiros de até
   200 mil bits, AST de até 1 milhão de elementos, grau 1000, matriz 1000). Estouro, indeterminado
   e infinito no resultado numérico, `StackOverflowError`, `OutOfMemoryError` e limites do Symja
   viram erro da região.
2. **API** (`Calculadora`), em `LimitesPedido`: até 2000 regiões, fórmula de até 20 mil
   caracteres e 200 níveis de chaves/parênteses; JSON inválido vira `PedidoInvalido`. Um pool de
   motores (2 no servidor, 1 no app): Motor custa 0,35 ms e ~0 MB depois do primeiro (o primeiro
   leva ~1 s, carga do Symja), e 80 cálculos em 4 motores deram os mesmos resultados que um só. Cada
   cálculo roda numa thread com pilha de 64 MB; o que não parar nem 5 s depois do prazo da folha é
   abandonado e o motor é trocado.
3. **Servidor-dev:** corpo até 4 MB (413), 16 threads, tempo máximo de pedido/resposta do
   HttpServer, respostas de erro sempre em JSON e sem stack trace, só GET/HEAD nos arquivos,
   checagem da pasta por `Path.startsWith`, CSP e cabeçalhos de segurança; unit systemd com
   `MemoryMax=512M` e endurecimento.

Os tempos são parâmetros (`Limites`, `LimitesPedido`): os testes usam orçamentos de 50–200 ms.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Só o `TimeConstrained` do Symja | Não cobre o nosso código (faixas, recursão de função do usuário, conversão do LaTeX) e cria uma thread por chamada |
| Interromper a thread (`Thread.interrupt`/`stop`) | `stop` não existe mais no Java 21; interromper não para conta presa em Java puro. O pedido de parada do motor é verificado no laço do Symja |
| Um motor por pedido | Possível (0,35 ms), mas perde o cache de unidades do motor; o pool limita também a CPU usada ao mesmo tempo |
| Fila única com um motor (como antes) | Um cálculo lento prende todos os outros até o prazo |
| Limitar a recursão só pela pilha | A pilha varia entre servidor e Android; a profundidade dá a mesma mensagem nos dois |

## Consequências

- Operadores novos (integrais, resolver, gráficos) ganham prazo sem fazer nada: as chamadas ao
  Symja param sozinhas. Laços próprios longos chamam `motor.verificarPrazo()`; tentativas com
  plano B usam `motor.prazo.tentarPor(ms) { ... }`. Ver `docs/comportamento/robustez.md`.
- Quem capturar `Exception` para tentar outro caminho não deve engolir a interrupção
  (`motor.interrompido(e)` → relançar).
- `if` precisa ser operador especial (argumentos preguiçosos): avaliado como função comum, toda
  recursão com caso base viraria "recursão profunda demais".
- `10^{10^{10}} →` (simbólico) também vira erro, em vez de mostrar a torre sem calcular.
- A CSP proíbe script inline e `eval` na página; estilos inline continuam permitidos (MathLive).
