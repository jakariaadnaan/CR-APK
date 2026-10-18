package com.example.callrec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Calendar

/** Dashboard: month summary, spending by category (donut) and six-month income vs expense bars. */
class DashboardActivity : GuardedActivity() {

    private var year = 0
    private var month = 0
    private var mode = 0 // 0 month, 1 three months, 2 year, 3 custom dates
    private var customFrom = 0L
    private var customTo = 0L
    private val DAY = 86_400_000L
    private lateinit var box: LinearLayout
    private val shortBn = listOf("জানু", "ফেব্রু", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টে", "অক্টো", "নভে", "ডিসে")

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val now = Calendar.getInstance()
        year = now.get(Calendar.YEAR)
        month = now.get(Calendar.MONTH)
        val first = Calendar.getInstance()
        first.set(year, month, 1, 0, 0, 0)
        first.set(Calendar.MILLISECOND, 0)
        customFrom = first.timeInMillis
        customTo = todayStart()
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

    private fun len(): Int = when (mode) { 0 -> 1; 1 -> 3; 2 -> 12; else -> 1 }

    private fun pickDate(initial: Long, onPicked: (Long) -> Unit) {
        val c = Calendar.getInstance()
        c.timeInMillis = initial
        android.app.DatePickerDialog(this, { _, y, m, d ->
            val r = Calendar.getInstance()
            r.set(y, m, d, 0, 0, 0)
            r.set(Calendar.MILLISECOND, 0)
            onPicked(r.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun syncEnd() {
        val c = Calendar.getInstance()
        c.timeInMillis = customTo
        year = c.get(Calendar.YEAR)
        month = c.get(Calendar.MONTH)
    }

    private fun dateRow(): LinearLayout {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        val from = pill(dateText(customFrom), Ui.CARD, Ui.TEXT) {
            pickDate(customFrom) { ms ->
                customFrom = ms
                if (customTo < ms) customTo = ms
                syncEnd(); render()
            }
        }
        val to = pill(dateText(customTo), Ui.CARD, Ui.TEXT) {
            pickDate(customTo) { ms ->
                customTo = ms
                if (customFrom > ms) customFrom = ms
                syncEnd(); render()
            }
        }
        from.background = bg(Ui.CARD, 16, Ui.LINE, 1)
        to.background = bg(Ui.CARD, 16, Ui.LINE, 1)
        from.textSize = 14f
        to.textSize = 14f
        from.setPadding(dp(8), dp(11), dp(8), dp(11))
        to.setPadding(dp(8), dp(11), dp(8), dp(11))
        row.addView(from, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("→", 18f, Ui.MUTED, true), lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, l = 10, r = 10))
        row.addView(to, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    /** [back] 0 = the chosen period, 1 = the period just before it. */
    private fun inRange(e: Entry, back: Int): Boolean {
        if (mode == 3) {
            val span = customTo - customFrom + DAY
            return if (back == 0) e.date in customFrom..customTo else e.date >= customFrom - span && e.date < customFrom
        }
        val c = Calendar.getInstance()
        c.timeInMillis = e.date
        val idx = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
        val end = if (mode == 2) year * 12 + 11 else year * 12 + month
        val l = len()
        val hi = end - back * l
        return idx in (hi - l + 1)..hi
    }

    private fun shift(delta: Int) {
        var idx = year * 12 + month + delta * (if (mode == 2) 12 else len())
        year = idx / 12
        month = idx % 12
        render()
    }

    private fun rangeTitle(): String = when (mode) {
        0 -> BN_MONTHS[month] + " " + bn(year.toString())
        1 -> {
            var sm = month - 2
            var sy = year
            if (sm < 0) { sm += 12; sy-- }
            shortMonth(sm) + (if (sy != year) " " + bn(sy.toString()) else "") + " – " + shortMonth(month) + " " + bn(year.toString())
        }
        2 -> bn(year.toString())
        else -> dateText(customFrom) + " – " + dateText(customTo)
    }

    private fun chips(): LinearLayout {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER
        val names = listOf(tr("মাস"), tr("৩ মাস"), tr("বছর"), tr("তারিখ"))
        for (i in 0..3) {
            val sel = i == mode
            val tv = label(names[i], 14f, if (sel) Color.WHITE else Ui.MUTED, sel)
            tv.gravity = Gravity.CENTER
            tv.background = if (sel) bg(Ui.PRIMARY, 18) else bg(Ui.CARD, 18, Ui.LINE, 1)
            tv.setOnClickListener { if (mode != i) { mode = i; if (i == 3) syncEnd(); render() } }
            row.addView(tv, LinearLayout.LayoutParams(0, dp(38), 1f).also { it.setMargins(dp(4), 0, dp(4), 0) })
        }
        return row
    }

    private fun render() {
        box.removeAllViews()
        val all = LedgerStore.all(this)
        val items = all.filter { inRange(it, 0) }
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
        val title = label(rangeTitle(), 17f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        nav.addView(prev, LinearLayout.LayoutParams(dp(48), dp(44)))
        nav.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next, LinearLayout.LayoutParams(dp(48), dp(44)))
        box.addView(chips(), lp(b = 4))
        if (mode == 3) box.addView(dateRow(), lp(t = 8, b = 8)) else box.addView(nav, lp(b = 6))

        // summary card
        val sum = card(18)
        sum.addView(label(tr("ব্যালান্স"), 13f, Ui.MUTED, true))
        val bal = inc - exp
        sum.addView(label(taka(bal), 32f, if (bal < 0) Ui.RED else Ui.TEXT, true), lp(t = 2))
        val row = LinearLayout(this)
        row.addView(statBlock(tr("আয়"), taka(inc), Ui.GREEN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(statBlock(tr("ব্যয়"), taka(exp), Ui.RED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        sum.addView(row, lp(t = 14))
        // compare with the period before
        val prevExp = all.filter { !it.income && inRange(it, 1) }.sumOf { it.amount }
        if (prevExp > 0 && exp > 0) {
            val diff = Math.round((exp - prevExp) / prevExp * 100).toInt()
            val txt = if (diff > 0) bn(diff.toString()) + tr("% বেশি ব্যয় আগের সময়ের চেয়ে")
            else if (diff < 0) bn((-diff).toString()) + tr("% কম ব্যয় আগের সময়ের চেয়ে")
            else tr("আগের সময়ের সমান ব্যয়")
            sum.addView(label(txt, 13f, if (diff > 0) Ui.RED else Ui.GREEN), lp(t = 12))
        }
        box.addView(sum, lp(b = 12))

        if (items.isEmpty()) {
            val e = label(tr("এই সময়ে এখনো কোনো লেনদেন নেই।"), 14f, Ui.MUTED)
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
        box.addView(budgetCard(), lp(b = 12))
        box.addView(debtCard(), lp(b = 12))
        box.addView(todoCard(), lp())
    }

    private fun insightCard(items: List<Entry>, exp: Double): LinearLayout {
        val c = card(16)
        c.addView(label(tr("এক নজরে"), 15f, Ui.TEXT, true), lp(b = 4))
        val top = items.filter { !it.income }.groupBy { it.category }.map { Pair(it.key, it.value.sumOf { e -> e.amount }) }.maxByOrNull { it.second }
        if (top != null) c.addView(insightLine(tr("সবচেয়ে বেশি ব্যয়"), tr(top.first) + " · " + taka(top.second)), lp(t = 8))
        val now = Calendar.getInstance()
        if (mode == 3) {
            val days = Math.max(1L, (customTo - customFrom) / DAY + 1)
            c.addView(insightLine(tr("দৈনিক গড় ব্যয়"), taka(exp / days)), lp(t = 8))
        } else if (mode == 0) {
            val days = if (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH)) now.get(Calendar.DAY_OF_MONTH)
            else Calendar.getInstance().also { it.set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
            c.addView(insightLine(tr("দৈনিক গড় ব্যয়"), taka(exp / days)), lp(t = 8))
        } else {
            c.addView(insightLine(tr("মাসিক গড় ব্যয়"), taka(exp / len())), lp(t = 8))
        }
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

    private fun budgetCard(): LinearLayout {
        val c = card(16)
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label(tr("বাজেট"), 15f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(label(tr("ঠিক করুন"), 14f, Ui.PRIMARY, true))
        c.addView(head)
        c.setOnClickListener { startActivity(android.content.Intent(this, BudgetActivity::class.java)) }
        if (!BudgetStore.any(this)) {
            c.addView(label(tr("ক্যাটাগরি ধরে মাসের খরচের সীমা ঠিক করুন।"), 13f, Ui.MUTED), lp(t = 6))
            return c
        }
        val spent = BudgetStore.spent(this, year, month)
        var shown = 0
        for (cat in LedgerStore.EXPENSE_CATS) {
            val limit = BudgetStore.get(this, cat)
            if (limit <= 0) continue
            val used = spent[cat] ?: 0.0
            val ratio = used / limit
            val line = LinearLayout(this)
            line.gravity = Gravity.CENTER_VERTICAL
            line.addView(label(tr(cat), 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(label(taka(used) + " / " + taka(limit), 13f, if (ratio >= 1.0) Ui.RED else Ui.MUTED))
            c.addView(line, lp(t = 12))
            val bar = LinearLayout(this)
            bar.background = bg(Ui.LINE, 4)
            bar.weightSum = 100f
            val f = View(this)
            f.background = bg(budgetColor(ratio), 4)
            bar.addView(f, LinearLayout.LayoutParams(0, dp(8), Math.min(100.0, ratio * 100).toFloat()))
            c.addView(bar, lp(height = dp(8), t = 6))
            if (ratio >= 1.0) c.addView(label(tr("সীমা পার হয়েছে, ") + taka(used - limit) + tr(" বেশি"), 12f, Ui.RED), lp(t = 4))
            else if (ratio >= 0.8) c.addView(label(tr("সীমার কাছাকাছি, মাত্র ") + taka(limit - used) + tr(" বাকি"), 12f, budgetColor(ratio)), lp(t = 4))
            shown++
        }
        return c
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
