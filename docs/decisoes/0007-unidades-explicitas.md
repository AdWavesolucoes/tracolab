# 0007 — Unidades de medida explícitas

- **Status:** Aceita
- **Data:** 2026-10-12

## Contexto

O app é para engenharia, então unidades são essenciais. Nas ferramentas clássicas, unidade é
um **nome embutido**: `m` é metro, `s` é segundo, `C` é coulomb, `K` é kelvin. Isso colide
com o uso mais comum desses nomes como variáveis (`m` para massa, `C` para constante). As
ferramentas resolvem a colisão deixando a variável do usuário "esconder" a unidade, com um
aviso. No acervo estudado há **595** desses avisos, o que mostra que a colisão é frequente.

No estado parcial ([ADR 0002](0002-modo-parcial-como-padrao.md)) isso piora. Se `m` não foi
definido, é a massa que ainda falta ou é o metro? Não há como saber.

## Decisão

- **No app, unidade é sempre explícita.** É um elemento próprio da fórmula, nunca confundido
  com variável. Na sintaxe de texto do protótipo, vai entre chaves: `20{mm}`, `F/{kN}`,
  `{kN/m^2}`. Na interface, virá de um teclado de unidades e terá aparência própria.
- O motor tem um **modo compatível** (`unidades=True`), em que o nome sozinho vira unidade
  quando não é variável do usuário. Serve para validar contra o acervo de referência.
- Internamente, cada unidade é **fator × símbolos base do SI** (`ft = 0,3048·⟨m⟩`), e a
  aritmética do CAS cuida das conversões.
- A exibição pode pedir uma unidade: `σ = {MPa}`.

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Unidade implícita pelo nome (como as ferramentas clássicas) | Ambígua com variáveis, especialmente no estado parcial |
| Classe própria de grandeza (valor + dimensão) | Exigiria reimplementar matrizes, funções e simbólico; os símbolos base reaproveitam o CAS |
| `sympy.physics.units` | Simplificação e conversão menos previsíveis; não existe no Android |

## Consequências

- A dimensão é checada em soma, subtração, comparação e funções transcendentais (`sin`, `ln`...).
  A checagem é **pulada quando há variável faltando**, porque a dimensão ainda é desconhecida.
- °C e °F ficam de fora por enquanto (têm deslocamento, não são só um fator). Δ°C e Δ°F entram.
- A tabela de unidades precisa ser portada junto com o motor ([ADR 0006](0006-nucleo-unico-multiplataforma.md)).
