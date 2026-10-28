package com.memoredia.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale

/** Recursive-descent evaluator for the scientific calculator. */
class SciParser(private val s: String, private val deg: Boolean, private val ans: Double) {
    private var pos = 0

    fun parse(): Double {
        val v = expr()
        if (pos != s.length) throw IllegalArgumentException()
        return v
    }

    private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

    private fun expr(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') {
            val op = s[pos++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = unary()
        while (true) {
            val c = peek()
            if (c == '*' || c == '/') {
                pos++
                val r = unary()
                if (c == '/') {
                    if (r == 0.0) throw ArithmeticException()
                    v /= r
                } else v *= r
            } else if (c == '(' || c.isLetter() || c == 'π') {
                v *= unary() // implicit multiplication: 2π, 3(4), 2sin(30)
            } else break
        }
        return v
    }

    private fun unary(): Double {
        if (peek() == '-') { pos++; return -unary() }
        if (peek() == '+') { pos++; return unary() }
        return power()
    }

    private fun power(): Double {
        val b = postfix()
        if (peek() == '^') {
            pos++
            return Math.pow(b, unary())
        }
        return b
    }

    private fun postfix(): Double {
        var v = primary()
        while (true) {
            if (peek() == '!') { pos++; v = fact(v) }
            else if (peek() == '%') { pos++; v /= 100.0 }
            else break
        }
        return v
    }

    private fun fact(v: Double): Double {
        if (v < 0 || v != Math.floor(v) || v > 170) throw ArithmeticException()
        var r = 1.0
        for (i in 2..v.toInt()) r *= i
        return r
    }

    private fun primary(): Double {
        val c = peek()
        if (c == '(') {
            pos++
            val v = expr()
            if (peek() != ')') throw IllegalArgumentException()
            pos++
            return v
        }
        if (c == 'π') { pos++; return Math.PI }
        if (c.isDigit() || c == '.') {
            val st = pos
            while (peek().isDigit() || peek() == '.') pos++
            return s.substring(st, pos).toDouble()
        }
        if (c.isLetter()) {
            val st = pos
            while (peek().isLetter()) pos++
            val name = s.substring(st, pos)
            when (name) {
                "e" -> return Math.E
                "Ans" -> return ans
            }
            if (peek() != '(') throw IllegalArgumentException()
            pos++
            val a = expr()
            if (peek() != ')') throw IllegalArgumentException()
            pos++
            val rad = if (deg) Math.toRadians(a) else a
            return when (name) {
                "sin" -> Math.sin(rad)
                "cos" -> Math.cos(rad)
                "tan" -> {
                    if (Math.abs(Math.cos(rad)) < 1e-12) throw ArithmeticException()
                    Math.tan(rad)
                }
                "asin" -> { if (a < -1 || a > 1) throw ArithmeticException(); val r = Math.asin(a); if (deg) Math.toDegrees(r) else r }
                "acos" -> { if (a < -1 || a > 1) throw ArithmeticException(); val r = Math.acos(a); if (deg) Math.toDegrees(r) else r }
                "atan" -> { val r = Math.atan(a); if (deg) Math.toDegrees(r) else r }
                "ln" -> { if (a <= 0) throw ArithmeticException(); Math.log(a) }
                "log" -> { if (a <= 0) throw ArithmeticException(); Math.log10(a) }
                "sqrt" -> { if (a < 0) throw ArithmeticException(); Math.sqrt(a) }
                "cbrt" -> Math.cbrt(a)
                "abs" -> Math.abs(a)
                else -> throw IllegalArgumentException()
            }
        }
        throw IllegalArgumentException()
    }
}

/** সায়েন্টিফিক ক্যালকুলেটর: functions, powers, brackets, degrees or radians. */
class SciCalcActivity : GuardedActivity() {

    private var expr = ""
    private var deg = true
    private var ans = 0.0
    private var message: String? = null

    private lateinit var exprView: TextView
    private lateinit var resView: TextView
    private lateinit var degRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(8), dp(12), dp(10))

        degRow = LinearLayout(this)
        degRow.orientation = LinearLayout.HORIZONTAL
        col.addView(degRow, lp(l = 4, b = 8))

        val disp = card(14)
        disp.gravity = Gravity.END
        exprView = label("", 15f, Ui.MUTED)
        exprView.gravity = Gravity.END
        exprView.maxLines = 3
        resView = label("", 30f, Ui.TEXT, true)
        resView.gravity = Gravity.END
        resView.maxLines = 1
        disp.addView(exprView, lp())
        disp.addView(resView, lp(t = 4))
        col.addView(disp, lp(l = 4, r = 4, b = 6))

        // key = (shown text, what it does)
        val rows = listOf(
            listOf("(", ")", "%", "⌫", "C"),
            listOf("sin", "cos", "tan", "^", "√"),
            listOf("sin⁻¹", "cos⁻¹", "tan⁻¹", "ln", "log"),
            listOf("7", "8", "9", "÷", "π"),
            listOf("4", "5", "6", "×", "e"),
            listOf("1", "2", "3", "−", "!"),
            listOf("0", ".", "Ans", "+", "=")
        )
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        for (r in rows) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (k in r) {
                val isNum = k.length == 1 && (k[0].isDigit() || k == ".")
                val isOp = k in listOf("÷", "×", "−", "+", "^")
                val isFn = !isNum && !isOp && k != "=" && k != "(" && k != ")" && k != "⌫" && k != "C" && k != "%" && k != "Ans"
                val fill = when {
                    k == "=" -> Ui.PRIMARY
                    isOp || isFn -> Ui.KEY_OP
                    else -> Ui.CARD
                }
                val fg = when {
                    k == "=" -> Color.WHITE
                    isOp || isFn -> Ui.PRIMARY
                    else -> Ui.TEXT
                }
                val b = label(if (isNum && k != ".") bn(k) else k, if (k.length > 2) 15f else 20f, fg, true)
                b.gravity = Gravity.CENTER
                b.background = bg(fill, 12, if (fill == Ui.PRIMARY) fill else if (fill == Ui.KEY_OP) Ui.mix(Ui.PRIMARY, Ui.CARD, 0.75f) else Ui.LINE, 1)
                b.setOnClickListener { press(k) }
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                p.setMargins(dp(3), dp(3), dp(3), dp(3))
                line.addView(b, p)
            }
            grid.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        col.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(tr("সায়েন্টিফিক ক্যালকুলেটর"), col)
        refresh()
    }

    private fun closed(e: String): String {
        var open = 0
        for (c in e) { if (c == '(') open++ else if (c == ')') open-- }
        return if (open > 0) e + ")".repeat(open) else e
    }

    private fun fmt(v: Double): String {
        if (v.isNaN() || v.isInfinite()) throw ArithmeticException()
        if (Math.abs(v) < 1e-12) return "0"
        val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        return if (Math.abs(v) >= 1e12 || Math.abs(v) < 1e-6) bd.toString().replace("E+", "E") else bd.toPlainString()
    }

    /** Null while the expression is unfinished, "ত্রুটি" when it can never work. */
    private fun evaluate(e: String): String? =
        try {
            fmt(SciParser(closed(e), deg, ans).parse())
        } catch (x: ArithmeticException) {
            tr("ত্রুটি")
        } catch (x: Exception) {
            null
        }

    private fun shown(e: String): String = bn(e.replace('*', '×').replace('/', '÷').replace('-', '−'))

    private fun refresh() {
        degRow.removeAllViews()
        degRow.addView(choiceChip("DEG", deg) { deg = true; refresh() }, lp(r = 8))
        degRow.addView(choiceChip("RAD", !deg) { deg = false; refresh() }, lp(r = 8))
        exprView.text = shown(expr)
        val m = message
        if (m != null) {
            resView.text = m
        } else {
            val r = if (expr.isEmpty()) null else evaluate(expr)
            resView.text = bn(if (r == null || r == tr("ত্রুটি")) expr.ifEmpty { "0" } else r)
        }
    }

    private fun press(k: String) {
        message = null
        when (k) {
            "C" -> expr = ""
            "⌫" -> expr = expr.replace(Regex("(sin|cos|tan|asin|acos|atan|ln|log|sqrt)\\($|Ans$|(?s).$"), "")
            "=" -> {
                if (expr.isNotEmpty()) {
                    val r = evaluate(expr)
                    if (r == tr("ত্রুটি")) message = tr("ত্রুটি (গণনা সম্ভব নয়)")
                    else if (r == null) message = tr("রাশিটি অসম্পূর্ণ")
                    else {
                        ans = r.toDouble()
                        expr = r
                    }
                }
            }
            "÷" -> expr += "/"
            "×" -> expr += "*"
            "−" -> expr += "-"
            "√" -> expr += "sqrt("
            "sin⁻¹" -> expr += "asin("
            "cos⁻¹" -> expr += "acos("
            "tan⁻¹" -> expr += "atan("
            "sin", "cos", "tan", "ln", "log" -> expr += "$k("
            else -> expr += k // digits, ., (, ), +, ^, π, e, !, Ans
        }
        if (expr.length > 120) expr = expr.take(120)
        refresh()
    }
}
