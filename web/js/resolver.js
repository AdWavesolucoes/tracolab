"use strict";
// Resolver equações (ADR 0012): teclas na camada "Operadores". O "=" dentro de resolver(...) é a
// equação (o núcleo troca por ==); a incógnita é o 2º argumento. Ex.: d := resolver(σ = F/(πd²/4), d).
{
  TL.teclas.push(
    { latex: "\\operatorname{resolver}\\left(#?=#?,#?\\right)", label: "resolver", tooltip: "Resolver a equação para a incógnita: resolver(a = b, x); palpite opcional: resolver(a = b, x, x₀)" },
    { latex: "\\operatorname{raiz}\\left(#?,#?,#?,#?\\right)", label: "raiz", tooltip: "Raiz de f(x) no intervalo: raiz(f, x, a, b)" },
  );
}
