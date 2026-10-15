# 0014 — Unidades da folha, temperaturas e formato dos números

- **Status:** Aceita
- **Data:** 2026-10-15

## Contexto

O núcleo mostrava todo resultado sem unidade pedida em SI cru: `d := 20{mm}` aparecia `0,02 m`
no painel, um momento de kN e m aparecia `60000 J`, uma tensão pendente `3183,099·F/m²`, uma
carga distribuída `12000 kg·s⁻²`. Num memorial brasileiro as grandezas são kN, kN·m, kN/m, MPa,
kN/cm², tf/m², kgf/cm², cm⁴, e as temperaturas em °C. Faltavam unidades da prática brasileira
(tf, mca, cv, h, pol...), °C/°F absolutos eram "não suportados" e não havia como escolher casas
decimais, notação de engenharia ou separador de milhar.

## Decisão

**Unidades da folha.** Sem unidade pedida pelo chip, o resultado aparece:

1. **como foi digitado**, se o nome mostrado (`d =`, ou `d` no painel) foi definido por uma
   quantidade sem nomes: `20{mm}` → 20 mm, `3{m}/2{s}` → 1,5 m/s, `20{°C}` → 20 °C;
2. senão, na melhor candidata, com nota = distância do número ao intervalo [1, 1000) em potências
   de 10 (para mmᵏ, o intervalo é [1, 1000ᵏ)) + o peso da origem: unidade escrita na folha com a
   mesma dimensão (0), potência de uma unidade da folha (0,4), produto de duas unidades da folha
   de dimensões diferentes (0,5), família de engenharia (1: N/kN/MN, Pa…GPa, mm/m/km, W/kW/MW...)
   e SI com barra (2: m/s, kg/m^3). Empate: a que aparece primeiro na folha, depois a de menos
   fatores. Momento e energia têm a mesma dimensão: com força e sem energia escritas na folha, a
   família é a do momento (kN·m);
3. uma temperatura só sai em °C/°F pela regra 1 (ou pedida). Fora disso, K: uma diferença de
   temperaturas tem a mesma dimensão de uma temperatura e nunca deve aparecer como "15 °C".

A regra vale para resultados numéricos, pendentes (cada termo; unidade só no denominador sai como
divisão: `F/(314,159 mm²)`), painel de variáveis, vetores (unidade comum escolhida pelo maior
elemento) e para a substituição explícita, que usa a mesma exibição. O ajuste "Unidades dos
resultados: SI" desliga a regra. `exibir()` (o `tela` da especificação) continua em SI.

**Temperaturas absolutas.** `20{°C}` (número seguido só da unidade) é 293,15 K; `-10{°C}` é
263,15 K; `T = {°C}` desconta o zero da escala. Dentro de unidade composta (`W/(m·°C)`) °C é
intervalo, como Δ°C. Somar duas temperaturas absolutas soma em kelvin (20 °C + 10 °C = 303,15 °C):
para diferenças, `Δ°C`. `20^{\circ}C` digitado com a tecla de grau vira °C.

**Unidades novas** (tf, t, mca, mmca, cv, h, pol, dm, Wh, MWh, VA, kVA, MVA, var, kvar, mW, μA,
Ah, mAh, lm, lx, °C, °F) só valem entre chaves: no modo compatível (validação), `t`, `h`, `cv`
sem chaves continuam nomes de variável.

**Formato dos números**, formatado no núcleo (web e Android iguais): algarismos significativos
(padrão 6) ou casas decimais, notação exponencial a partir de 10ⁿ (padrão: 10⁶ e abaixo de 10⁻⁴,
como sempre), notação de engenharia, separador de milhar (padrão ligado, com 5 algarismos ou mais,
`\:` no LaTeX porque `\,` separa número e unidade na tela) e zeros à direita. Arredondamento
comercial sobre o decimal mais curto do número (2,345 → 2,35). Por folha (`opcoes.formato`) e por
resultado (`opcoes.formatos`, tocando no número).

## Alternativas consideradas

| Alternativa | Por que não |
|---|---|
| Continuar em SI e deixar o usuário pedir cada unidade | É o que mais polui a folha: toda conta intermediária vira chip trocado à mão |
| Prefixo de engenharia automático sobre o SI (só N/kN/MN...) | Momento sairia em kJ e tensão de kN e cm em MPa; ignora o vocabulário que a própria folha usa (tf/m², kN/cm²) |
| Unidade definida pelo usuário dentro de chaves (`tf ≡ 1000{kgf}`, `20{tf}`) | Sem chaves já funciona como variável (`20·tf`); o que motivava (tf, kgf/cm²) entrou na tabela. Exigiria resolver o ambiente da região na exibição e tornar unidade digitada errada um "falta", escondendo erros de digitação |
| °C sempre absoluto, inclusive em unidades compostas | `W/(m·°C)` passaria a ter deslocamento, o que não tem sentido físico |
| Mostrar diferença de temperaturas em °C pela folha | `T₂ − T₁ = 15 °C` seria lido como temperatura absoluta |

## Consequências

- A tela de vários resultados muda (kN·m em vez de J, mm em vez de m): testes de interface que
  conferiam o texto exato precisam ser escritos pela tela nova (`tela_folha` na especificação).
- A regra depende das unidades escritas em toda a folha: acrescentar `{km/h}` em algum lugar pode
  mudar a exibição de outra velocidade. É deliberado e determinístico.
- Formatos novos (frações, notação científica com prefixo) entram em `Formato` sem mexer na API.
