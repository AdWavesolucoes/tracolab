# Especificação executável

`casos-comportamento.json` é a **especificação do motor**. Toda implementação (o protótipo em
Python, o núcleo em Kotlin, o que vier depois) precisa passar todos os casos. A descrição em
texto de cada caso está em [docs/comportamento/casos.md](../docs/comportamento/casos.md).

Os casos de cada área nova ficam num arquivo próprio em [`casos/`](casos/) (`casos/matrizes.json`,
`casos/calculo.json`, `casos/regressoes.json`...), no mesmo formato. O teste do núcleo roda o
arquivo base e todos os de `casos/`, e o `id` de cada caso precisa ser único entre todos eles
(use um prefixo da área: `mat-01`, `calc-01`).

## Formato

Cada caso tem `modo` (`parcial` ou `classico`), opcionalmente `unidades_implicitas: true` e
`origem` (primeiro índice de vetores, 0 ou 1; padrão 0), e uma
lista de `passos` executados em ordem.

### Ações

| Campo | Significado |
|---|---|
| `"acao": "adicionar"` | Cria a região `id` com `texto` na posição `y` (e `x`, padrão 0) |
| `"acao": "editar"` | Troca o texto da região `id`. Com `recalculadas`, confere quais regiões o recálculo incremental reavaliou (como conjunto) |
| `"acao": "remover"` | Apaga a região `id` |

### Verificações (`"verificar": "<id da região>"`)

Todos os campos são opcionais e se somam:

| Campo | Confere |
|---|---|
| `tipo` | Estado: `numerico`, `parcial`, `erro`, `nao_suportado` |
| `livres` | Nomes que faltam, comparados como **conjunto** |
| `expr` | Valor simbolicamente igual a esta expressão (sintaxe: `* / ^ + -`) |
| `exato` | Valor exato (fração/racional), comparado como texto canônico, ex.: `1/2` |
| `num` | Valor numérico, com tolerância relativa de 1e-9 |
| `lista` | Vetor numérico, com a mesma tolerância |
| `matriz` | Matriz numérica por linhas (`[[1, 2], [3, 4]]`), com a mesma tolerância |
| `tela` | Texto exato da exibição em SI ou na unidade pedida (`2.3 m`, `200 MPa`), sem as unidades da folha |
| `tela_folha` | Texto da exibição da tela (pt-BR, com as unidades da folha e o formato, ADR 0014): `20 mm`, `36 kN·m`, `2,3 m`. Com `formato` (ex.: `{"casas": 2, "zeros": true}`) no mesmo passo |
| `tela_comeca` | Prefixo do texto da exibição |
| `msg_contem` | Trecho da mensagem de erro |
| `aviso` | Aviso da região (ex.: `Redefinição`) |

## Sintaxe de texto das regiões

| Escrita | Região |
|---|---|
| `x := expr` | Definição |
| `x ≡ expr` | Definição global |
| `f(t) := expr` | Função |
| `v[i] := expr` | Elemento de vetor |
| `i := 0..4` / `i := 0, 0.5..4` | Faixa |
| `expr =` / `expr = {MPa}` | Avaliação numérica (com unidade de exibição) |
| `expr ->` ou `expr →` | Avaliação simbólica |
| `20{mm}`, `{kN/m^2}` | Unidade explícita |
| `[1, 2; 3, 4]` | Matriz: linhas separadas por `;`, elementos por `,` |
| `"OK"` | Texto |
| `f(a, b)` | Chamada de função do usuário ou embutida (registro em `nucleo/.../embutidos/`) |
