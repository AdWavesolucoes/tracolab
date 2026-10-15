# Unidades e formato dos números

Unidades da prática brasileira, temperaturas, a regra das **unidades da folha** e o formato dos
números. A decisão está na [ADR 0014](../decisoes/0014-unidades-da-folha-e-formato.md); os casos
estão em [`spec/casos/unidades.json`](../../spec/casos/unidades.json) (`uni-01` a `uni-40`).

## Unidades da prática brasileira

Só valem entre chaves (no modo compatível continuam nomes de variável):

| Grandeza | Unidades novas | Valor |
|---|---|---|
| Força | `tf` | 1000 kgf = 9806,65 N |
| Massa | `t` | 1000 kg |
| Pressão | `mca`, `mmca` | 9806,65 Pa (coluna d'água a 1000 kg/m³ e g padrão), 9,80665 Pa |
| Potência | `cv`, `mW`, `VA`, `kVA`, `MVA`, `var`, `kvar` | 1 cv = 75 kgf·m/s = 735,49875 W |
| Tempo | `h` | 3600 s |
| Comprimento | `pol`, `dm` | 25,4 mm, 0,1 m |
| Energia | `Wh`, `MWh` | 3600 J |
| Carga elétrica | `Ah`, `mAh`, `μA` | 3600 C |
| Luz | `lm`, `lx` | cd·sr, lm/m² |
| Temperatura | `°C`, `°F` (`degC`, `degF`) | absolutas (abaixo) |

Unidades compostas se escrevem como sempre: `{kN/m^2}`, `{kgf/cm^2}`, `{kN*cm}`, `{m^3/h}`,
`{cm^4}`, `{N/mm^2}` (= MPa).

## Temperaturas

| Escrita | Significado |
|---|---|
| `20{°C}`, `68{°F}` | Temperatura absoluta: 293,15 K |
| `-10{°C}` | 263,15 K (o sinal é do número) |
| `T = {°C}`, `T = {°F}` | Mostra descontando o zero da escala: 20 °C, 68 °F |
| `15{Δ°C}` | Diferença de temperatura (= 15 K) |
| `{W/(m*°C)}` | Dentro de unidade composta, °C é intervalo (= W/(m·K)) |
| `20^{\circ}C` no editor | A tecla de grau seguida de C ou F é temperatura (°C), não ângulo |

> [!WARNING] Somar temperaturas absolutas
> `20{°C} + 10{°C}` soma 293,15 K + 283,15 K = 576,3 K = 303,15 °C. Para "20 °C mais 10 graus",
> escreva `20{°C} + 10{Δ°C}` = 30 °C. Uma diferença de temperaturas (`T₂ − T₁`) aparece em K.

## Unidades da folha

Sem unidade pedida pelo chip, o resultado aparece nas unidades que a folha usa:

| Folha | Resultado | Antes (SI) | Agora |
|---|---|---|---|
| `d := 20{mm}` | `d =`, painel | 0,02 m | 20 mm |
| `d := 20{mm}`, `A := π·d²/4` | `A =` | 0,000314159 m² | 314,159 mm² |
| ... `σ := F/A` (F faltando) | `σ =` | 3183,099·F/m² | F/(314,159 mm²) |
| `F := 10{kN}`, `L := 6{m}` | `F·L =` | 60000 J | 60 kN·m |
| `q := 12{kN/m}`, `L := 6{m}` | `q·L²/8 =` | 54000 J | 54 kN·m |
| `F` em kN, `A` em mm² | `F/A =` | 1e+08 Pa | 100 MPa |
| `M := 50{kN*m}`, `W := 557{cm^3}` | `M/W =` | 8,97666e+07 Pa | 8,97666 kN/cm² |
| `q := 2{tf/m^2}` | `q·3{m} =` | 58839,9 kg·s⁻² | 6 tf/m |
| `m := 2{kg}` | `m·9,81{m/s^2} =` | 19,62 N | 19,62 N |
| `v := 3{m}/2{s}` | `v =` | 1,5 m·s⁻¹ | 1,5 m/s |
| `0.000012·L` (L faltando) | `=` | 3/250000·L | 1,2×10⁻⁵·L |

Como escolhe (ADR 0014): primeiro, **como foi digitado** (o nome mostrado definido por uma
quantidade sem nomes); depois a candidata de menor nota = distância do número a [1, 1000) +
peso da origem (unidade escrita na folha 0; potência de unidade da folha 0,4; produto de duas
0,5; família de engenharia 1; SI 2). Empate: a primeira que aparece na folha. Temperatura fora
da regra 1 sai em K. Ajustes → "Unidades dos resultados: SI" volta ao SI.

## Formato dos números

Ajustes → "Números e unidades" (vale para a folha); tocar no número de um resultado muda só
ele (fica com um sublinhado pontilhado).

| Ajuste | Padrão | Exemplo |
|---|---|---|
| Precisão | 6 algarismos significativos | 2 casas decimais: 0,67 |
| Notação exponencial a partir de | 10⁶ (e abaixo de 10⁻⁴) | 10³: 1,23×10³ |
| Notação de engenharia | desligada | 12,5×10³ |
| Separar milhares | ligado (5 algarismos ou mais) | 123 456 |
| Zeros à direita | desligado | 2,500 m |

O pedido leva `opcoes.formato` (`algarismos` ou `casas`, `limite`, `engenharia`, `milhar`,
`zeros`, `unidades`: `"folha"` ou `"si"`) e `opcoes.formatos` (`{id: formato}`). O arredondamento
é o comercial (2,345 → 2,35).
