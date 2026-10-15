# Resolver equações

Decisão e alternativas: [ADR 0012](../decisoes/0012-resolver.md). Casos: `spec/casos/resolver.json`.

## Funções

| Escrita | Resultado |
|---|---|
| `d := resolver(σ == F/(π*d^2/4), d)` | o `d` que satisfaz a equação (unidade deduzida: 17,84 mm) |
| `resolver(x^2 == 4, x, -1)` | com palpite: a raiz mais próxima (−2) |
| `resolver(cos(x) == x, x, 0, 1)` | no intervalo (numérico, Brent) |
| `resolver(x - 5, x)` | sem `==`: a expressão igualada a zero |
| `resolver([x + y == 3; x - y == 1], [x; y])` | sistema: vetor `[2; 1]` |
| `raiz(x^3 - 2*x - 5, x, 2, 3)` | raiz no intervalo; `raiz(f, x)` parte do `x` definido acima |
| `raizes([-6; 1; 1])` | raízes de −6 + x + x²: `[-3; 2]` (complexas também) |

Sinônimos: `solve`, `root`, `polyroots`. No editor, a tecla **resolver** (camada Operadores)
insere `resolver(□ = □, □)`; o `=` no meio vira `==`.

## Regras

- **Simbólico primeiro**, numérico depois: o `FindRoot` parte do palpite (padrão 1, na unidade
  deduzida) ou do intervalo.
- **Unidade da incógnita:** do palpite/intervalo, ou deduzida da equação. Lados com unidades
  incompatíveis são erro.
- **Várias raízes:** só reais; palpite escolhe a mais próxima; com unidade, a única positiva;
  intervalo filtra; senão vetor crescente.
- **Nome faltando:** solução simbólica, pendente com chips (`resolver(a*x == b, x)` → `b/a`,
  falta a, b). Par ±: a escrita positiva.
- **Mensagens:** "a equação não tem solução", "vale para qualquer x (infinitas soluções)",
  "só tem soluções complexas", "não achei raiz entre os limites dados", "não achei raiz a partir
  do palpite", "as unidades dos dois lados da equação não são compatíveis".
