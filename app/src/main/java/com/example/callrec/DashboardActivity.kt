package com.example.callrec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Calendar

/** Dashboard: month summary, spending by category (donut) and six-month income vs expense bars. */
class DashboardActivity : GuardedActivity() {

    private var year = 0
    private var month = 0
    private lateinit var box: LinearLayout
    private val shortBn = listOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে")

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val now = Calendar.getInstance()
        year = now.get(Calendar.YEAR)
        month = now.get(Calendar.MONTH)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(8), dp(14), dp(24))
        scroll.addView(box)
        setScreen(tr("ড্যাশবোর্ড"), scroll)
        render()
    }

    private fun shortMonth(m: Int): String = if (Lang.en) BN_MONTHS[m].take(3) else shortBn[m]

    private fun inMonth(e: Entry, y: Int, m: Int): Boolean {
        val c = Calendar.getInstance()
        c.timeInMillis = e.date
        return c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
    }

    private fun shift(delta: Int) {
        month += delta
        if (month < 0) { month = 11; year-- } else if (month > 11) { month = 0; year++ }
        render()
    }

    private fun render() {
        box.removeAllViews()
        val all = LedgerStore.all(this)
        val items = all.filter { inMonth(it, year, month) }
        val inc = items.filter { it.income }.sumOf { it.amount }
        val exp = items.filter { !it.income }.sumOf { it.amount }

        // month switcher
        val nav = LinearLayout(this)
        nav.gravity = Gravity.CENTER_VERTICAL
        val prev = label("‹", 26f, Ui.PRIMARY, true)
        prev.gravity = Gravity.CENTER
        prev.setOnClickListener { shift(-1) }
        val next = label("›", 26f, Ui.PRIMARY, true)
        next.gravity = Gravity.CENTER
        next.setOnClickListener { shift(1) }
        val title = label(BN_MONTHS[month] + " " + bn(year.toString()), 17f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        nav.addView(prev, LinearLayout.LayoutParams(dp(48), dp(44)))
        nav.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next, LinearLayout.LayoutParams(dp(48), dp(44)))
        box.addView(nav, lp(b = 6))

        // summary card
        val sum = card(18)
        sum.addView(label(tr("ব্যালান্স"), 13f, Ui.MUTED, true))
        val bal = inc - exp
        sum.addView(label(taka(bal), 32f, if (bal < 0) Ui.RED else Ui.TEXT, true), lp(t = 2))
        val row = LinearLayout(this)
        row.addView(statBlock(tr("আয়"), taka(inc), Ui.GREEN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(statBlock(tr("ব্যয়"), taka(exp), Ui.RED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sum.addView(row, lp(t = 14))
        // compare with previous month
        val pm = if (month == 0) 11 else month - 1
        val py = if (month == 0) year - 1 else year
        val prevExp = all.filter { !it.income && inMonth(it, py, pm) }.sumOf { it.amount }
        if (prevExp > 0 && exp > 0) {
            val diff = Math.round((exp - prevExp) / prevExp * 100).toInt()
            val txt = if (diff > 0) bn(diff.toString()) + tr("% বেশি ব্যয় গত মাসের চেয়ে")
            else if (diff < 0) bn((-diff).toString()) + tr("% কম ব্যয় গত মাসের চেয়ে")
            else tr("গত মাসের সমান ব্যয়")
            sum.addView(label(txt, 13f, if (diff > 0) Ui.RED else Ui.GREEN), lp(t = 12))
        }
        box.addView(sum, lp(b = 12))

        if (items.isEmpty()) {
            val e = label(tr("এই মাসে এখনো কোনো লেনদেন নেই।"), 14f, Ui.MUTED)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(24), 0, dp(24))
            box.addView(e, lp())
        } else if (exp > 0) {
            box.addView(insightCard(items, exp), lp(b = 12))
            box.addView(categoryCard(items.filter { !it.income }, exp), lp(b = 12))
        }

        // six-month trend
        val trend = card(16)
        trend.addView(label(tr("গত ৬ মাস"), 15f, Ui.TEXT, true), lp(b = 4))
        val data = ArrayList<Triple<String, Double, Double>>()
        var y = year
        var m = month
        val tmp = ArrayList<Triple<String, Double, Double>>()
        for (k in 0 until 6) {
            val ms = all.filter { inMonth(it, y, m) }
            tmp.add(Triple(shortMonth(m), ms.filter { it.income }.sumOf { it.amount }, ms.filter { !it.income }.sumOf { it.amount }))
            m--; if (m < 0) { m = 11; y-- }
        }
        data.addAll(tmp.reversed())
        trend.addView(BarsView(this, data), lp(height = dp(190), t = 6))
        val leg = LinearLayout(this)
        leg.gravity = Gravity.CENTER
        leg.addView(legendDot(Ui.GREEN, tr("আয়")))
        leg.addView(legendDot(Ui.RED, tr("ব্যয়")), lp(l = 18))
        trend.addView(leg, lp(t = 8))
        box.addView(trend, lp(b = 12))
        box.addView(debtCard(), lp(b = 12))
        box.addView(todoCard(), lp())
    }

    private fun insightCard(items: List<Entry>, exp: Double): LinearLayout {
        val c = card(16)
        c.addView(label(tr("এক নজরে"), 15f, Ui.TEXT, true), lp(b = 4))
        val top = items.filter { !it.income }.groupBy { it.category }.map { Pair(it.key, it.value.sumOf { e -> e.amount }) }.maxByOrNull { it.second }
        if (top != null) c.addView(insightLine(tr("সবচেয়ে বেশি ব্যয়"), tr(top.first) + " · " + taka(top.second)), lp(t = 8))
        val now = Calendar.getInstance()
        val days = if (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)) now.get(Calendar.DAY_OF_MONTH)
        else Calendar.getInstance().also { it.set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        c.addView(insightLine(tr("দৈনিক গড় ব্যয়"), taka(exp / days)), lp(t = 8))
        val big = items.filter { !it.income }.maxByOrNull { it.amount }
        if (big != null) c.addView(insightLine(tr("সবচেয়ে বড় একক ব্যয়"), taka(big.amount) + " · " + tr(big.category)), lp(t = 8))
        return c
    }

    private fun insightLine(name: String, value: String): LinearLayout {
        val l = LinearLayout(this)
        l.gravity = Gravity.CENTER_VERTICAL
        l.addView(label(name, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        l.addView(label(value, 14f, Ui.TEXT, true))
        return l
    }

    private fun debtCard(): LinearLayout {
        val c = card(16)
        c.addView(label(tr("বাকির চিত্র"), 15f, Ui.TEXT, true), lp(b = 8))
        val debts = DebtStore.all(this)
        if (debts.isEmpty()) {
            c.addView(label(tr("কোনো বাকি নেই।"), 14f, Ui.MUTED))
            return c
        }
        val get = debts.filter { !it.owe }.sumOf { it.amount }
        val give = debts.filter { it.owe }.sumOf { it.amount }
        val row = LinearLayout(this)
        row.addView(statBlock(tr("পাবো"), taka(get), Ui.GREEN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(statBlock(tr("দেবো"), taka(give), Ui.RED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row)
        val tot = get + give
        if (tot > 0) {
            val bar = LinearLayout(this)
            bar.background = bg(Ui.LINE, 4)
            bar.weightSum = 100f
            val g = View(this)
            g.background = bg(Ui.GREEN, 4)
            bar.addView(g, LinearLayout.LayoutParams(0, dp(8), (get / tot * 100).toFloat()))
            c.addView(bar, lp(height = dp(8), t = 12))
        }
        val tops = debts.sortedByDescending { it.amount }.take(3)
        c.addView(label(tr("সবচেয়ে বড় বাকি"), 13f, Ui.MUTED, true), lp(t = 16, b = 2))
        for (d in tops) {
            val l = LinearLayout(this)
            l.gravity = Gravity.CENTER_VERTICAL
            l.addView(label(d.name, 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            l.addView(label((if (d.owe) tr("দেবো") else tr("পাবো")) + "  ", 12f, Ui.MUTED))
            l.addView(label(taka(d.amount), 14f, if (d.owe) Ui.RED else Ui.GREEN, true))
            c.addView(l, lp(t = 8))
        }
        return c
    }

    private fun todoCard(): LinearLayout {
        val c = card(16)
        c.addView(label(tr("করণীয়"), 15f, Ui.TEXT, true), lp(b = 8))
        val todos = TodoStore.all(this)
        if (todos.isEmpty()) {
            c.addView(label(tr("কোনো কাজ নেই।"), 14f, Ui.MUTED))
            return c
        }
        val done = todos.count { it.done }
        val today = todayStart()
        val late = todos.count { !it.done && it.due != 0L && it.due < today }
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(RingView(this, done.toFloat() / todos.size), LinearLayout.LayoutParams(dp(84), dp(84)))
        val txt = LinearLayout(this)
        txt.orientation = LinearLayout.VERTICAL
        txt.addView(label(tr("শেষ: ") + bn(done.toString()) + " / " + bn(todos.size.toString()), 16f, Ui.TEXT, true))
        txt.addView(label(tr("বাকি: ") + bn((todos.size - done).toString()), 14f, Ui.MUTED), lp(t = 4))
        txt.addView(label(tr("সময় পার: ") + bn(late.toString()), 14f, if (late > 0) Ui.RED else Ui.MUTED), lp(t = 2))
        row.addView(txt, lp(l = 18))
        c.addView(row)
        return c
    }

    private fun statBlock(name: String, value: String, color: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.addView(label(name, 13f, Ui.MUTED))
        c.addView(label(value, 18f, color, true), lp(t = 2))
        return c
    }

    private fun legendDot(color: Int, text: String): LinearLayout {
        val l = LinearLayout(this)
        l.gravity = Gravity.CENTER_VERTICAL
        val d = View(this)
        d.background = bg(color, 4)
        l.addView(d, LinearLayout.LayoutParams(dp(10), dp(10)))
        l.addView(label(text, 13f, Ui.MUTED), lp(l = 6))
        return l
    }

    private fun categoryCard(exps: List<Entry>, total: Double): LinearLayout {
        val c = card(16)
        c.addView(label(tr("ক্যাটাগরি অনুযায়ী ব্যয়"), 15f, Ui.TEXT, true), lp(b = 8))
        val groups = exps.groupBy { it.category }.map { Pair(it.key, it.value.sumOf { e -> e.amount }) }.sortedByDescending { it.second }
        val top = groups.take(5)
        val restSum = groups.drop(5).sumOf { it.second }
        val parts = ArrayList<Pair<String, Double>>(top)
        if (restSum > 0) parts.add(Pair("অন্যান্য", restSum))
        val colors = parts.indices.map { i ->
            if (i == parts.size - 1 && restSum > 0) Ui.GREY else Ui.mix(Ui.PRIMARY, if (Ui.dark) Ui.CARD else Color.WHITE, 0.14f * i)
        }
        c.addView(DonutView(this, parts.map { it.second.toFloat() }, colors, taka(total), tr("মোট ব্যয়")), lp(height = dp(190)))
        for ((i, p) in parts.withIndex()) {
            val line = LinearLayout(this)
            line.gravity = Gravity.CENTER_VERTICAL
            val dot = View(this)
            dot.background = bg(colors[i], 4)
            line.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
            line.addView(label(tr(p.first), 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { it.leftMargin = dp(10) })
            line.addView(label(taka(p.second) + "  " + bn(Math.round(p.second / total * 100).toString()) + "%", 13f, Ui.MUTED))
            c.addView(line, lp(t = 10))
        }
        return c
    }
}

/** Ring chart with a total in the middle. */
class DonutView(ctx: Context, private val values: List<Float>, private val colors: List<Int>, private val centerBig: String, private val centerSmall: String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val total = values.sum()
        if (total <= 0f) return
        val size = Math.min(width, height).toFloat()
        val stroke = size * 0.16f
        val r = RectF((width - size) / 2 + stroke / 2, (height - size) / 2 + stroke / 2, (width + size) / 2 - stroke / 2, (height + size) / 2 - stroke / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        var start = -90f
        val gap = if (values.size > 1) 2f else 0f
        for (i in values.indices) {
            val sweep = values[i] / total * 360f
            p.color = colors[i]
            c.drawArc(r, start + gap / 2, Math.max(sweep - gap, 0.5f), false, p)
            start += sweep
        }
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = Ui.TEXT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size * 0.11f
        c.drawText(centerBig, width / 2f, height / 2f + size * 0.01f, p)
        p.color = Ui.MUTED
        p.typeface = Typeface.DEFAULT
        p.textSize = size * 0.07f
        c.drawText(centerSmall, width / 2f, height / 2f + size * 0.1f, p)
    }
}

/** Paired income / expense bars per month. */
class BarsView(ctx: Context, private val data: List<Triple<String, Double, Double>>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val maxV = data.maxOf { Math.max(it.second, it.third) }
        val labelH = resources.displayMetrics.density * 22
        val chartH = height - labelH
        val slot = width / data.size.toFloat()
        val bw = slot * 0.26f
        p.strokeWidth = resources.displayMetrics.density
        p.color = Ui.LINE
        c.drawLine(0f, chartH, width.toFloat(), chartH, p)
        p.textAlign = Paint.Align.CENTER
        p.textSize = resources.displayMetrics.scaledDensity * 11f
        for ((i, d) in data.withIndex()) {
            val cx = slot * i + slot / 2
            fun bar(v: Double, x: Float, col: Int) {
                if (maxV <= 0 || v <= 0) return
                val h = Math.max((v / maxV * (chartH - 8)).toFloat(), 3f)
                p.color = col
                p.style = Paint.Style.FILL
                c.drawRoundRect(RectF(x, chartH - h, x + bw, chartH), bw / 3, bw / 3, p)
            }
            bar(d.second, cx - bw - 2f, Ui.GREEN)
            bar(d.third, cx + 2f, Ui.RED)
            p.color = Ui.MUTED
            p.typeface = Typeface.DEFAULT
            c.drawText(d.first, cx, height - labelH * 0.25f, p)
        }
    }
}

/** Progress ring with a percentage in the middle. */
class RingView(ctx: Context, private val fraction: Float) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val size = Math.min(width, height).toFloat()
        val stroke = size * 0.14f
        val r = RectF((width - size) / 2 + stroke / 2, (height - size) / 2 + stroke / 2, (width + size) / 2 - stroke / 2, (height + size) / 2 - stroke / 2)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.color = Ui.LINE
        c.drawArc(r, 0f, 360f, false, p)
        p.color = Ui.PRIMARY
        p.strokeCap = Paint.Cap.ROUND
        if (fraction > 0f) c.drawArc(r, -90f, 360f * fraction, false, p)
        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = Ui.TEXT
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = size * 0.22f
        c.drawText(bn(Math.round(fraction * 100).toString()) + "%", width / 2f, height / 2f + size * 0.08f, p)
    }
}
