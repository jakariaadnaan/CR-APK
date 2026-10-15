package com.example.callrec

import android.app.Activity
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** ক্যালকুলেটর with a second tab for unit conversion (length, weight, temperature, volume). */
class CalcActivity : GuardedActivity() {

    private class UnitDef(val name: String, val factor: Double)

    private class Category(val name: String, val units: List<UnitDef>, val isTemp: Boolean = false)

    private val categories = listOf(
        Category(
            "দৈর্ঘ্য",
            listOf(
                UnitDef("মিটার", 1.0), UnitDef("কিলোমিটার", 1000.0), UnitDef("সেন্টিমিটার", 0.01),
                UnitDef("মিলিমিটার", 0.001), UnitDef("ইঞ্চি", 0.0254), UnitDef("ফুট", 0.3048),
                UnitDef("গজ", 0.9144), UnitDef("হাত (১৮ ইঞ্চি)", 0.4572), UnitDef("মাইল", 1609.344)
            )
        ),
        Category(
            "ওজন",
            listOf(
                UnitDef("কেজি", 1.0), UnitDef("গ্রাম", 0.001), UnitDef("মিলিগ্রাম", 0.000001),
                UnitDef("টন (মেট্রিক)", 1000.0), UnitDef("পাউন্ড", 0.45359237),
                UnitDef("আউন্স", 0.028349523125), UnitDef("ভরি (১১.৬৬৪ গ্রাম)", 0.011664),
                UnitDef("সের (৮০ তোলা)", 0.93312), UnitDef("মণ (৪০ সের)", 37.3248)
            )
        ),
        Category("তাপমাত্রা", listOf(UnitDef("সেলসিয়াস °C", 1.0), UnitDef("ফারেনহাইট °F", 1.0), UnitDef("কেলভিন K", 1.0)), true),
        Category(
            "আয়তন",
            listOf(
                UnitDef("লিটার", 1.0), UnitDef("মিলিলিটার", 0.001), UnitDef("ঘনমিটার", 1000.0),
                UnitDef("গ্যালন (US)", 3.785411784), UnitDef("কাপ (US)", 0.2365882365)
            )
        )
    )

    private var expr = ""
    private var message: String? = null
    private var catIdx = 0
    private var tab = 0

    private lateinit var body: FrameLayout
    private lateinit var tabRow: LinearLayout
    private lateinit var exprView: TextView
    private lateinit var resView: TextView

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL

        tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        root.addView(tabRow, lp(l = 14, t = 12, r = 14, b = 6))

        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setScreen("ক্যালকুলেটর", root)
        showTab(0)
    }

    private fun showTab(t: Int) {
        tab = t
        tabRow.removeAllViews()
        val names = listOf("ক্যালকুলেটর", "রূপান্তর")
        for (i in names.indices) {
            val on = tab == i
            val chip = label(names[i], 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.gravity = Gravity.CENTER
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(18), dp(8), dp(18), dp(8))
            chip.setOnClickListener { showTab(i) }
            tabRow.addView(chip, lp(r = 8))
        }
        val sci = label("সায়েন্টিফিক ↗", 14f, Ui.PRIMARY, true)
        sci.gravity = Gravity.CENTER
        sci.background = bg(Ui.CARD, 20, Ui.PRIMARY, 1)
        sci.setPadding(dp(18), dp(8), dp(18), dp(8))
        sci.setOnClickListener { startActivity(android.content.Intent(this, SciCalcActivity::class.java)) }
        tabRow.addView(sci, lp(r = 8))
        body.removeAllViews()
        if (tab == 0) buildCalc() else buildConverter()
    }

    // ------------------------------------------------------------------ calculator

    private class Parser(private val s: String) {
        private var pos = 0

        fun parse(): Double {
            val v = expr()
            if (pos != s.length) throw IllegalArgumentException()
            return v
        }

        private fun expr(): Double {
            var v = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos]
                pos++
                val r = term()
                v = if (op == '+') v + r else v - r
            }
            return v
        }

        private fun term(): Double {
            var v = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos]
                pos++
                val r = factor()
                if (op == '*') {
                    v *= r
                } else {
                    if (r == 0.0) throw ArithmeticException()
                    v /= r
                }
            }
            return v
        }

        private fun factor(): Double {
            var neg = false
            if (pos < s.length && s[pos] == '-') {
                neg = true
                pos++
            }
            val st = pos
            while (pos < s.length && (s[pos] in '0'..'9' || s[pos] == '.')) pos++
            if (st == pos) throw IllegalArgumentException()
            var v = s.substring(st, pos).toDouble()
            while (pos < s.length && s[pos] == '%') {
                v /= 100.0
                pos++
            }
            return if (neg) -v else v
        }
    }

    private fun fmt(d: Double, scale: Int): String {
        if (d.isNaN() || d.isInfinite()) throw ArithmeticException()
        return BigDecimal.valueOf(d).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    /** Result text, null when the expression is not complete yet. */
    private fun evaluate(e: String): String? =
        try {
            fmt(Parser(e).parse(), 8)
        } catch (ex: ArithmeticException) {
            "ত্রুটি"
        } catch (ex: Exception) {
            null
        }

    private fun buildCalc() {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(10), dp(4), dp(10), dp(12))

        val disp = card(16)
        disp.gravity = Gravity.END
        exprView = label("", 28f, Ui.TEXT, true)
        exprView.gravity = Gravity.END
        exprView.maxLines = 2
        resView = label("", 18f, Ui.MUTED)
        resView.gravity = Gravity.END
        disp.addView(exprView, lp())
        disp.addView(resView, lp(t = 4))
        col.addView(disp, lp(l = 4, r = 4, b = 8))

        val rows = listOf(
            listOf("C", "⌫", "%", "/"),
            listOf("7", "8", "9", "*"),
            listOf("4", "5", "6", "-"),
            listOf("1", "2", "3", "+"),
            listOf("00", "0", ".", "=")
        )
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        for (r in rows) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (k in r) {
                val isOp = k in listOf("/", "*", "-", "+")
                val shown = when (k) {
                    "/" -> "÷"
                    "*" -> "×"
                    "-" -> "−"
                    else -> bn(k)
                }
                val fill = when {
                    k == "=" -> Ui.PRIMARY
                    isOp -> Color.parseColor("#D7EBE2")
                    k == "C" || k == "⌫" || k == "%" -> Color.parseColor("#E6E9E7")
                    else -> Ui.CARD
                }
                val fg = when {
                    k == "=" -> Color.WHITE
                    isOp || k == "C" -> Ui.PRIMARY
                    else -> Ui.TEXT
                }
                val b = label(shown, 24f, fg, true)
                b.gravity = Gravity.CENTER
                b.background = bg(fill, 16)
                b.elevation = dp(1).toFloat()
                b.setOnClickListener { press(k) }
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                p.setMargins(dp(4), dp(4), dp(4), dp(4))
                line.addView(b, p)
            }
            grid.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        col.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        refreshCalc()
    }

    private fun refreshCalc() {
        exprView.text = bn(expr.replace('*', '×').replace('/', '÷').replace('-', '−')).ifEmpty { bn("0") }
        val m = message
        if (m != null) {
            resView.text = m
        } else {
            val r = if (expr.isEmpty()) null else evaluate(expr)
            resView.text = if (r == null || r == "ত্রুটি") "" else "= " + bn(r)
        }
    }

    private fun press(k: String) {
        message = null
        val last = if (expr.isEmpty()) ' ' else expr.last()
        val cur = expr.takeLastWhile { it in '0'..'9' || it == '.' }
        when (k) {
            "C" -> expr = ""
            "⌫" -> if (expr.isNotEmpty()) expr = expr.dropLast(1)
            "=" -> {
                if (expr.isNotEmpty()) {
                    val r = evaluate(expr)
                    if (r == "ত্রুটি") message = "ত্রুটি (শূন্য দিয়ে ভাগ)"
                    else if (r != null) expr = r
                }
            }
            "+", "-", "*", "/" -> {
                if (expr.isEmpty()) {
                    if (k == "-") expr = "-"
                } else if (expr != "-") {
                    expr = if (last in "+-*/") expr.dropLast(1) + k else expr + k
                }
            }
            "." -> {
                if (!cur.contains('.') && last != '%') expr += if (cur.isEmpty()) "0." else "."
            }
            "%" -> {
                if (last in '0'..'9' || last == '%') expr += "%"
            }
            else -> {
                if (last != '%' && expr.length < 60) {
                    if (cur == "0") {
                        if (k != "0" && k != "00") expr = expr.dropLast(1) + k
                    } else {
                        expr += k
                    }
                }
            }
        }
        refreshCalc()
    }

    // ------------------------------------------------------------------ converter

    private fun buildConverter() {
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(4), dp(14), dp(20))
        scroll.addView(col)

        val cat = categories[catIdx]

        val chipScroll = HorizontalScrollView(this)
        chipScroll.isHorizontalScrollBarEnabled = false
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        for (i in categories.indices) {
            val on = i == catIdx
            val chip = label(categories[i].name, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(16), dp(8), dp(16), dp(8))
            chip.setOnClickListener {
                catIdx = i
                body.removeAllViews()
                buildConverter()
            }
            chips.addView(chip, lp(r = 8))
        }
        chipScroll.addView(chips)
        col.addView(chipScroll, lp(b = 12))

        val c = card(16)
        val input = EditText(this)
        input.hint = "মান লিখুন"
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        input.textSize = 22f
        c.addView(input, lp())

        val names = cat.units.map { it.name }
        val from = Spinner(this)
        val to = Spinner(this)
        from.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        to.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        to.setSelection(if (names.size > 1) 1 else 0)

        val swap = label("⇄", 24f, Ui.PRIMARY, true)
        swap.gravity = Gravity.CENTER
        swap.setPadding(dp(10), 0, dp(10), 0)

        val pick = LinearLayout(this)
        pick.orientation = LinearLayout.HORIZONTAL
        pick.gravity = Gravity.CENTER_VERTICAL
        pick.addView(from, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        pick.addView(swap)
        pick.addView(to, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(pick, lp(t = 8))

        val result = label("", 26f, Ui.PRIMARY, true)
        result.gravity = Gravity.CENTER
        c.addView(result, lp(t = 16, b = 4))
        col.addView(c, lp())

        fun update() {
            val v = asciiDigits(input.text.toString()).replace(",", "").trim().toDoubleOrNull()
            if (v == null) {
                result.text = ""
                return
            }
            val a = from.selectedItemPosition
            val b = to.selectedItemPosition
            val out: Double = if (cat.isTemp) {
                val celsius = when (a) {
                    0 -> v
                    1 -> (v - 32.0) * 5.0 / 9.0
                    else -> v - 273.15
                }
                when (b) {
                    0 -> celsius
                    1 -> celsius * 9.0 / 5.0 + 32.0
                    else -> celsius + 273.15
                }
            } else {
                v * cat.units[a].factor / cat.units[b].factor
            }
            var txt = try {
                fmt(out, 6)
            } catch (e: Exception) {
                "ত্রুটি"
            }
            if (txt == "0" && out != 0.0) txt = String.format(Locale.US, "%.4e", out)
            result.text = bn(txt) + " " + cat.units[b].name.substringBefore(" ")
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                update()
            }
        })
        val sel = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        from.onItemSelectedListener = sel
        to.onItemSelectedListener = sel
        swap.setOnClickListener {
            val a = from.selectedItemPosition
            from.setSelection(to.selectedItemPosition)
            to.setSelection(a)
        }

        body.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
}
