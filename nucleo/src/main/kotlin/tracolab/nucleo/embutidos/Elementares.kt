package tracolab.nucleo.embutidos

import org.matheclipse.core.expression.F
import org.matheclipse.core.interfaces.IExpr
import tracolab.nucleo.Embutidos
import tracolab.nucleo.ModuloEmbutidos
import tracolab.nucleo.aridade

/** Funções elementares: trigonométricas, exponencial, logaritmos, raiz, módulo, arredondamento, máx/mín. */
object Elementares : ModuloEmbutidos {
    private val TRANSC: Map<String, (IExpr) -> IExpr> = mapOf(
        "sin" to { x -> F.Sin(x) }, "cos" to { x -> F.Cos(x) }, "tan" to { x -> F.Tan(x) },
        "asin" to { x -> F.ArcSin(x) }, "acos" to { x -> F.ArcCos(x) }, "atan" to { x -> F.ArcTan(x) },
        "sinh" to { x -> F.Sinh(x) }, "cosh" to { x -> F.Cosh(x) }, "tanh" to { x -> F.Tanh(x) },
        "exp" to { x -> F.Exp(x) }, "ln" to { x -> F.Log(x) }, "log" to { x -> F.Log10(x) },
    )

    override fun registrar(r: Embutidos) {
        // transcendentais exigem argumento adimensional (sin(2 m) é erro; sin(90°) = 1)
        for ((nome, fn) in TRANSC) r.funcao(nome) { a, c ->
            aridade(nome, a, 1)
            c.pl.motor.exigirAdimensional(a[0], nome)
            c.pl.motor.ev(fn(a[0]))
        }
        val uma: Map<String, (IExpr) -> IExpr> = mapOf(
            "sqrt" to { x -> F.Sqrt(x) }, "abs" to { x -> F.Abs(x) },
            "floor" to { x -> F.Floor(x) }, "ceil" to { x -> F.Ceiling(x) },
        )
        for ((nome, fn) in uma) r.funcao(nome) { a, c -> aridade(nome, a, 1); c.pl.motor.ev(fn(a[0])) }
        for (nome in listOf("max", "min")) r.funcao(nome) { a, c ->
            if (a.isEmpty()) throw tracolab.nucleo.ErroCalculo("$nome: espera ao menos 1 argumento")
            val ast = F.ast(if (nome == "max") F.Max else F.Min); a.forEach { ast.append(it) }
            c.pl.motor.ev(ast)
        }
    }
}
