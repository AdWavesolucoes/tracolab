package tracolab.nucleo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tracolab.nucleo.latex.LatexEntrada
import tracolab.nucleo.latex.LatexSaida
import tracolab.nucleo.latex.resultadoTela

class LatexTest {

    private fun t(latex: String) = LatexEntrada.regiao(latex)

    @Test fun definicaoComProduto() = assertEquals("F := m*a", t("F\\coloneq m\\cdot a"))
    @Test fun doisPontosIgual() = assertEquals("x := 5", t("x:=5"))
    @Test fun nomeComVariasLetras() = assertEquals("Ftot := Fa + Fb", t("Ftot\\coloneq Fa+Fb"))
    @Test fun indiceEntraNoNome() = assertEquals("F_max =", t("F_{max}="))
    @Test fun fracaoEPotencia() = assertEquals("A := ((π*d^(2))/(4))", t("A\\coloneq\\frac{\\pi d^2}{4}"))
    @Test fun gregaENumeroImplicito() = assertEquals("σ := 2*F", t("\\sigma\\coloneq2F"))
    @Test fun decimalComVirgula() = assertEquals("d := 0.02", t("d\\coloneq0{,}02"))
    @Test fun unidade() = assertEquals("L := 2*{m}", t("L\\coloneq2\\mathrm{m}"))
    @Test fun unidadeComposta() = assertEquals("A := 50*{mm}^(2)", t("A\\coloneq50\\mathrm{mm}^2"))
    @Test fun avaliacaoComUnidadePedida() = assertEquals("σ = {MPa}", t("\\sigma=\\mathrm{MPa}"))
    @Test fun avaliacaoSimbolica() = assertEquals("a*x + b ->", t("a\\cdot x+b\\to"))
    @Test fun global() = assertEquals("g ≡ 9.8", t("g\\equiv9.8"))
    @Test fun funcaoDefinida() = assertEquals("f(t) := t^(2) + k", t("f\\left(t\\right)\\coloneq t^2+k"))
    @Test fun senoSemParenteses() = assertEquals("sin(x) =", t("\\sin x="))
    @Test fun raiz() = assertEquals("r := sqrt(a + b)", t("r\\coloneq\\sqrt{a+b}"))
    @Test fun indiceDeVetor() = assertEquals("v[i] := i^(2)", t("v\\lbrack i\\rbrack\\coloneq i^2"))
    @Test fun igualNoMeioEComparacao() = assertEquals("a == b", t("a=b"))
    @Test fun corNaoMudaOCalculo() = assertEquals("F := m*a", t("F\\textcolor{#2748C9}{\\coloneq}m\\cdot a"))
    @Test fun macroDeDefinicaoDaInterface() = assertEquals("F := 31.4*{kN}", t("F\\tldef31{,}4\\mathrm{kN}"))
    @Test fun graus() = assertEquals("sin(90*{deg}) =", t("\\sin\\left(90^{\\circ}\\right)="))

    @Test fun nomesNaSaida() {
        assertEquals("\\sigma", LatexSaida.nome("σ"))
        assertEquals("F_{max}", LatexSaida.nome("F_max"))
        assertEquals("\\mathit{Ftot}", LatexSaida.nome("Ftot"))
        assertEquals("8787{,}21", LatexSaida.numero("8787.21"))
        assertEquals("5\\times10^{-5}", LatexSaida.numero("5e-05"))
    }

    @Test fun ponteaPontaDoLatexAteATela() {
        val pl = Planilha("parcial")
        listOf("F\\coloneq10\\mathrm{kN}", "A\\coloneq50\\mathrm{mm}^2", "\\sigma\\coloneq\\frac{F}{A}", "\\sigma=\\mathrm{MPa}")
            .forEachIndexed { k, l -> pl.adicionar(regiaoDeTexto("r$k", LatexEntrada.regiao(l), 10.0 * (k + 1))) }
        assertEquals("200\\,\\mathrm{MPa}", pl.resultadoTela("r3").latex)

        val p2 = Planilha("parcial")
        p2.adicionar(regiaoDeTexto("a", LatexEntrada.regiao("F\\coloneq m\\cdot a"), 10.0))
        p2.adicionar(regiaoDeTexto("b", LatexEntrada.regiao("F="), 20.0))
        assertEquals("a \\cdot m", p2.resultadoTela("b").latex)  // formato do TeXForm do Symja

        // pendente com unidade: vírgula decimal e expoente inteiro (o Symja escreve 3183.099 e m^{2.0})
        val p3 = Planilha("parcial")
        listOf("d\\tldef20\\mathrm{mm}", "\\sigma\\tldef\\frac{F}{\\pi d^2/4}", "\\sigma=")
            .forEachIndexed { k, l -> p3.adicionar(regiaoDeTexto("q$k", LatexEntrada.regiao(l), 10.0 * (k + 1))) }
        val tex = p3.resultadoTela("q2").latex!!
        assertTrue("{,}" in tex && "2.0" !in tex && "." !in tex) { tex }
    }
}
