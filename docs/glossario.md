# Glossário

| Termo | Significado |
|---|---|
| **Região** | Um bloco da página com uma fórmula e uma posição |
| **Linha de base** | A linha horizontal onde a fórmula "senta". Define a ordem vertical |
| **Definição** (`:=`) | Dá valor a um nome dali para baixo |
| **Global** (`≡`) | Dá valor a um nome na página inteira |
| **Avaliação numérica** (`=`) | Mostra o valor em ponto flutuante |
| **Avaliação simbólica** (`→`) | Mostra o resultado exato, possivelmente com nomes livres |
| **Nome livre** | Nome usado numa fórmula que ainda não tem valor |
| **Estado parcial** | Resultado que ainda contém nomes livres |
| **Faixa** | Variável que percorre valores (`i := 0..n`) |
| **Propagação** | Repasse de um estado (erro, parcial) para quem depende da região |
| **Recálculo incremental** | Recalcular só as regiões afetadas por uma mudança |
| **CAS** | Sistema de álgebra computacional: o motor que manipula expressões simbólicas |
| **Modo clássico** | Comportamento de referência: nome livre em `:=`/`=` é erro |
| **ADR** | Registro de decisão de arquitetura |
