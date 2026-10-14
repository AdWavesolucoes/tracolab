"use strict";
/*
 * TraçoLab: página de cálculo (ADR 0008), com a identidade do design (Tablet B).
 * Cada região é um editor MathLive posicionado na folha. A cada mudança, a folha inteira vai
 * para o núcleo (ponte nativa no Android ou POST /api/calcular no navegador) e a resposta
 * pinta o estado de cada região: numérico, pendente ("falta F"), erro de ordem, erro.
 *
 * Scripts clássicos com defer, carregados nesta ordem e no mesmo escopo global:
 *   configuracao.js  pontos de extensão (TL) e configuração do editor e do teclado
 *   folha.js         estado da folha, persistência, histórico e regiões na página
 *   calculo.js       pedido ao núcleo e ordem de cálculo
 *   pintura.js       estado de cada região, resumo e painel de variáveis
 *   interface.js     faixa de teclas, teclado virtual, menu de unidade, barra, trilho e ajustes
 *   inicio.js        carrega a folha e faz o primeiro cálculo (sempre o último)
 * Uma área nova (texto, gráfico, verificação...) entra num arquivo próprio em js/, carregado
 * antes de inicio.js, e se registra em TL. Para não colidir com os nomes globais dos outros
 * arquivos, o código dela fica dentro de um bloco { } ou de uma função.
 */

/** Pontos de extensão da interface. */
const TL = {
  /**
   * Tipos de região além da fórmula (a fórmula é o padrão: região sem `tipo`). Cada tipo tem:
   *   criar(r)           → elemento .rg ainda fora da página (inclua .alca para arrastar e .bd para a ordem)
   *   iniciar(el, r)     → chamado depois de o elemento entrar na página (opcional)
   *   pedido(r)          → a região no pedido ao núcleo, ou null se não calcula (opcional)
   *   pintar(o, el, r)   → aplica a resposta do núcleo para a região (opcional)
   *   linhaDeBase(r, el) → y da linha de base, que define a ordem (opcional; padrão r.y + 24)
   * O estado de cada região (r) fica em estado.regioes e é salvo como está, com o campo `tipo`.
   */
  tipos: {},
  /** Pintores extras das regiões de fórmula, chamados depois do padrão: (o, el, r) => void. */
  pintores: [],
  /** Chamados depois de cada resposta do núcleo, já pintada: (resp) => void. */
  aposCalculo: [],
  /** Opções da folha que vão ao núcleo: () => objeto, mesclado em pedido.opcoes. */
  opcoes: [],
  /**
   * Teclas extras do teclado virtual: { latex, label, tooltip? }. Juntas formam a camada
   * "Operadores" (Σ, ∫, matriz, se...), montada por inicio.js na ordem em que foram registradas.
   */
  teclas: [],
};

const MF = window.MathfieldElement;
MF.fontsDirectory = "vendor/mathlive-0.110.0/fonts";
MF.soundsDirectory = null;
MF.decimalSeparator = ",";

// := em cobalto. É um macro (e não \textcolor direto) porque o MathLive faz o texto digitado
// depois de um trecho colorido herdar a cor; o macro desenha o símbolo em cobalto sem passar a cor adiante.
const DEF = "\\tldef";
const MACROS = { tldef: { args: 0, def: "\\textcolor{#2748C9}{\\coloneq}", captureSelection: true, expand: false } };

// Atalhos controlados: os padrões do MathLive trocam "in" por ∈ e quebrariam nomes como "Fin".
const ATALHOS = {
  ":=": DEF, "->": "\\to", ">=": "\\ge", "<=": "\\le", "!=": "\\ne",
  "sqrt": "\\sqrt{#?}", "pi": "\\pi", "sin": "\\sin", "cos": "\\cos", "tan": "\\tan",
  "ln": "\\ln", "log": "\\log", "exp": "\\exp",
  "alpha": "\\alpha", "beta": "\\beta", "gamma": "\\gamma", "delta": "\\delta", "epsilon": "\\varepsilon",
  "theta": "\\theta", "lambda": "\\lambda", "mu": "\\mu", "rho": "\\rho", "sigma": "\\sigma",
  "tau": "\\tau", "phi": "\\varphi", "omega": "\\omega", "Delta": "\\Delta", "Omega": "\\Omega",
};

// Unidades (ADR 0007): rótulo na tela → LaTeX (\mathrm é sempre unidade)
// "\," antes da unidade: espaço fino entre número e unidade (20 mm); o núcleo ignora o espaço.
const UN = (rot, latex) => ({ rot, latex: "\\," + (latex || `\\mathrm{${rot}}`) });
const GRUPOS_UNIDADES = [
  ["Comprimento", [UN("mm"), UN("cm"), UN("m"), UN("km"), UN("in"), UN("ft")]],
  ["Área e volume", [UN("mm²", "\\mathrm{mm}^2"), UN("cm²", "\\mathrm{cm}^2"), UN("m²", "\\mathrm{m}^2"), UN("m³", "\\mathrm{m}^3"), UN("L")]],
  ["Força", [UN("N"), UN("kN"), UN("MN"), UN("kgf"), UN("lbf"), UN("kip")]],
  ["Momento", [UN("N·m", "\\mathrm{N}\\cdot\\mathrm{m}"), UN("kN·m", "\\mathrm{kN}\\cdot\\mathrm{m}")]],
  ["Pressão", [UN("Pa"), UN("kPa"), UN("MPa"), UN("GPa"), UN("psi"), UN("ksi")]],
  ["Massa e tempo", [UN("kg"), UN("g"), UN("s"), UN("min"), UN("hr"), UN("Hz")]],
  ["Energia e elétrica", [UN("J"), UN("kJ"), UN("W"), UN("kW"), UN("V"), UN("A")]],
  ["Outras", [UN("K"), UN("rad"), UN("°", "\\mathrm{deg}"), UN("%", "\\mathrm{\\%}"), UN("rpm")]],
];

window.mathVirtualKeyboard.layouts = [
  {
    id: "tracolab", label: "Matemática", tooltip: "Definir, avaliar e números",
    rows: [
      [{ latex: DEF, label: ":=" }, { latex: "=", label: "=" }, { latex: "\\to", label: "→" },
       { latex: "\\equiv", label: "≡" }, { latex: "\\frac{#@}{#?}", label: "÷" }, { latex: "#@^{#?}", label: "xⁿ" }],
      [{ latex: "\\sqrt{#0}", label: "√" }, { latex: "#@_{#?}", label: "xᵢ" }, { latex: "\\lbrack #0\\rbrack", label: "[ ]" },
       { latex: "\\left(#0\\right)", label: "( )" }, { latex: "\\pi", label: "π" }, "[backspace]"],
      ["7", "8", "9", { latex: "\\cdot", label: "×" }, "[left]", "[right]"],
      ["4", "5", "6", "+", "-", "<"],
      ["1", "2", "3", "{,}", "0", "[return]"],
    ],
  },
  "greek", "alphabetic",
  {
    id: "unidades", label: "Unidades", tooltip: "Unidades (ADR 0007): nunca se confundem com variáveis",
    rows: GRUPOS_UNIDADES.filter(([g]) => g !== "Momento").map(([, us]) => us.map((u) => ({ latex: u.latex, label: u.rot }))),
  },
];

