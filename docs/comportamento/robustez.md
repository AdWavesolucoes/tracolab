# Robustez: limites do cálculo

Nada do que se digita pode travar a folha. Ao passar de um limite, **só a região** fica com erro,
com uma mensagem em português, e o resto da folha continua calculando. A decisão e as medições
estão na [ADR 0017](../decisoes/0017-limites-de-calculo.md).

## Limites e mensagens

| Limite | Padrão | Onde | Mensagem na região |
|---|---|---|---|
| Tempo de uma região | 3 s | `Limites.msRegiao` | Cálculo interrompido: passou de 3 s. |
| Tempo da folha | 15 s | `Limites.msFolha` | Não calculada: a folha passou de 15 s. |
| Recursão de função do usuário | 200 chamadas | `Limites.profundidade` | Recursão profunda demais: mais de 200 chamadas aninhadas de f. |
| Valores de faixa, resultado por faixa e vetor `v[i] :=` | 200 mil | `Limites.elementos` | Faixa grande demais… / Resultado grande demais… / Índice grande demais… |
| Inteiro exato | 200 mil bits (~60 mil algarismos) | Symja | Número grande demais para a conta exata… |
| Resultado numérico | ±1,8×10³⁰⁸ | `Motor.problemaNumerico` | O resultado passa do maior número que dá para calcular… |
| Pilha (fórmula ou recursão funda) | — | — | Fórmula ou recursão profunda demais. |
| Fórmula no pedido | 20 mil caracteres, 200 níveis | `LimitesPedido` | Fórmula longa demais… / Fórmula aninhada demais… |
| Regiões por folha | 2000 | `LimitesPedido.regioes` | (pedido recusado, 400) |
| Tela | 100 linhas de vetor, 3000 termos | `LatexSaida` | Mostrando 100 de N valores. / Expressão grande demais para mostrar. |

## Para quem escreve operadores (Σ, ∫, resolver, gráficos)

O prazo da região já vale dentro do seu código:

- **Chamadas ao Symja** (`motor.ev(...)`) param sozinhas no fim do prazo: um vigia pede a parada
  ao `EvalEngine`, e a exceção vira erro da região. Nada a fazer.
- **Laços próprios longos** (amostrar um gráfico, iterar uma raiz): chame
  `c.pl.motor.verificarPrazo()` a cada volta (custa um `System.nanoTime()`).
- **Tentativa com plano B** (integral simbólica por até 1 s, depois numérica):

  ```kotlin
  val m = c.pl.motor
  val exata = m.prazo.tentarPor(1_000) { m.ev(F.Integrate(f, x)) }   // null se esgotou
  return exata ?: integralNumerica(f, x, a, b)                       // o prazo da região continua
  ```

  Se o prazo da região também acabou, a interrupção segue normalmente.
- **Não engula a interrupção**: se capturar `Exception` para tentar outro caminho, relance quando
  `m.interrompido(e)` for verdadeiro (`TempoEsgotado` não é `ErroCalculo` justamente por isso).
- **Recursão preguiçosa**: `if` e afins têm que ser operadores especiais (`Especial`), que avaliam
  só o ramo escolhido; senão `f(n) := if(n ≤ 1, 1, n·f(n − 1))` nunca termina e vira "recursão
  profunda demais".

## Servidor de desenvolvimento

- Erros da API em JSON: `{"erro": "..."}` com 400 (pedido inválido), 413 (mais de 4 MB), 503
  (motores ocupados, com `Retry-After`) e 500 (erro interno, sem stack trace; o detalhe vai para o
  log). A página mostra a mensagem na barra.
- Cálculo que não parar nem 5 s depois do prazo da folha (conta presa fora do Symja) é abandonado:
  a resposta traz `erro` e cada região com erro, e o motor dele é trocado por um novo.
- `TRACOLAB_MOTORES` (padrão 2): cálculos ao mesmo tempo. Um cálculo lento não prende os outros.
- Arquivos: só `GET`/`HEAD`, `Last-Modified` com revalidação (`no-cache`), e cabeçalhos de
  segurança (CSP sem script inline nem `eval`, `nosniff`, `Referrer-Policy`, `X-Frame-Options`).

## Medição

`ferramentas/robustez/hostis.py <url> [--reiniciar "comando"]` manda 30 entradas hostis e confere
se o servidor continua vivo depois de cada uma. **Nunca aponte para a produção nem para o
endereço público.** Resultado no servidor-dev (`-Xmx256m`), antes e depois:

| Entrada | Antes | Depois |
|---|---|---|
| `x := 10^{10^{10}}` / `9^{9^{9^{9}}}` | travou > 40 s, servidor reiniciado | erro na região, 0,1 s |
| `10^{1000000} =` / `10^{400} =` | 400 "Infinite or NaN" (folha inteira) | erro só na região |
| `f(x) := f(x)` / `n·f(n−1)` sem caso base | conexão fechada sem resposta | "Recursão profunda demais", o resto calcula |
| faixa de 200 mil valores avaliada | 4,6 MB de LaTeX na resposta | 1,5 s; tela com 100 linhas e ⋮ |
| `(x+1)^{100000} →` | "interno IterationLimitExceeded…" | "Cálculo grande demais para o motor" |
| 5000 parênteses / 20000 chaves / soma de 300 mil termos | conexão fechada (pilha) | erro só na região (longa demais) |
| 10 mil chaves aninhadas (dentro do limite de texto) | 9,3 s | erro só na região (200 níveis) |
| nome de 1 milhão de letras | travou > 40 s | erro só na região |
| 50 mil regiões `1=` | 29,8 s calculando | 400 "Folha grande demais" |
| JSON malformado / região sem id | 400 com mensagem interna do Jackson/NPE | 400 com mensagem em pt-BR |
| corpo de 60 MB | lido inteiro na memória | recusado pelo `Content-Length` |
| `OPTIONS /`, `PUT /index.html` | 200 servindo o arquivo | 405 com `Allow` |
| `GET /index.html%00.js` | conexão fechada sem resposta | 404 |
| corpo lento (slowloris) | `2+2=` de outro cliente não respondeu | respondeu na hora |
| cálculo pesado em andamento | `2+2=` esperou 4,7 s | respondeu na hora |

Todos os 30 casos terminam com o servidor vivo. Com a unit systemd endurecida (unidade
transitória de teste): pico de 262 MB de 512 MB e 37 tarefas de 128.
