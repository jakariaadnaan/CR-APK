package com.example.callrec

import android.app.Activity
import android.app.DatePickerDialog
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.time.LocalDate
import java.time.Period
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit

/** তারিখ ও বয়স: ইংরেজি ↔ বাংলা ↔ হিজরি তারিখ এবং জন্মতারিখ থেকে বয়স। */
class DateActivity : GuardedActivity() {

    private val hijriMonths = listOf(
        tr("মুহাররম"), tr("সফর"), tr("রবিউল আউয়াল"), tr("রবিউস সানি"), tr("জমাদিউল আউয়াল"), tr("জমাদিউস সানি"),
        tr("রজব"), tr("শাবান"), tr("রমজান"), tr("শাওয়াল"), tr("জিলকদ"), tr("জিলহজ")
    )
    private val weekdays = listOf(tr("রবিবার"), tr("সোমবার"), tr("মঙ্গলবার"), tr("বুধবার"), tr("বৃহস্পতিবার"), tr("শুক্রবার"), tr("শনিবার"))

    private val prefs by lazy { getSharedPreferences("date_prefs", MODE_PRIVATE) }

    private var tab = 0
    private var gDate: LocalDate = LocalDate.now()
    private var birth: LocalDate? = null
    private var asOf: LocalDate = LocalDate.now()

    private lateinit var tabRow: LinearLayout
    private lateinit var body: FrameLayout

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        tabRow = LinearLayout(this)
        tabRow.orientation = LinearLayout.HORIZONTAL
        root.addView(tabRow, lp(l = 16, t = 10, r = 16, b = 6))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen(tr("তারিখ ও বয়স"), root)
        showTab(0)
    }

    private fun showTab(t: Int) {
        tab = t
        tabRow.removeAllViews()
        val names = listOf(tr("তারিখ রূপান্তর"), tr("বয়স"))
        for (i in names.indices) tabRow.addView(choiceChip(names[i], tab == i) { showTab(i) }, lp(r = 8))
        body.removeAllViews()
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(6), dp(16), dp(20))
        scroll.addView(col)
        body.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (tab == 0) buildConverter(col) else buildAge(col)
    }

    private fun dateBox(text: String): TextView {
        val t = label(text, 15f, Ui.TEXT)
        t.background = bg(Ui.CARD, 10, Ui.LINE, 1)
        t.setPadding(dp(14), dp(13), dp(14), dp(13))
        return t
    }

    private fun ldText(d: LocalDate): String =
        bn(d.dayOfMonth.toString()) + " " + BN_MONTHS[d.monthValue - 1] + " " + bn(d.year.toString()) +
            ", " + weekdays[d.dayOfWeek.value % 7]

    private fun pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            DatePickerDialog.OnDateSetListener { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).show()
    }

    // ------------------------------------------------------------------ converter

    private fun hijriText(d: LocalDate, adj: Int): String =
        try {
            val h = HijrahDate.from(d.plusDays(adj.toLong()))
            bn(h.get(ChronoField.DAY_OF_MONTH).toString()) + " " + hijriMonths[h.get(ChronoField.MONTH_OF_YEAR) - 1] +
                " " + bn(h.get(ChronoField.YEAR).toString()) + tr(" হিজরি")
        } catch (e: Exception) {
            tr("এই তারিখের হিজরি মান পাওয়া যায়নি")
        }

    private fun banglaText(d: LocalDate): String {
        val b = BanglaCal.fromGregorian(d)
        return bn(b.day.toString()) + " " + BanglaCal.MONTHS[b.month] + " " + bn(b.year.toString()) + tr(" বঙ্গাব্দ")
    }

    private fun buildConverter(col: LinearLayout) {
        // ---- Gregorian -> Bangla / Hijri
        val c = card(16)
        c.addView(label(tr("ইংরেজি তারিখ"), 13f, Ui.MUTED))
        val dateBtn = dateBox(ldText(gDate))
        c.addView(dateBtn, lp(t = 6, b = 12))

        val bnLine: TextView = label("", 17f, Ui.TEXT, true)
        val hjLine: TextView = label("", 17f, Ui.TEXT, true)
        c.addView(label(tr("বাংলা"), 12f, Ui.MUTED))
        c.addView(bnLine, lp(b = 8))
        c.addView(label(tr("হিজরি"), 12f, Ui.MUTED))
        c.addView(hjLine, lp(b = 8))

        val adjRow = LinearLayout(this)
        adjRow.orientation = LinearLayout.HORIZONTAL
        adjRow.gravity = Gravity.CENTER_VERTICAL
        val adjLabel = label("", 13f, Ui.MUTED)
        val minus = pill("−", Ui.KEY_OP, Ui.PRIMARY) {}
        val plus = pill("+", Ui.KEY_OP, Ui.PRIMARY) {}
        adjRow.addView(label(tr("হিজরি সমন্বয়"), 13f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        adjRow.addView(minus)
        adjRow.addView(adjLabel, lp(l = 12, r = 12))
        adjRow.addView(plus)
        c.addView(adjRow, lp(t = 4))
        c.addView(
            label(
                tr("হিজরি তারিখ উম্মুল কুরা (সৌদি) পঞ্জিকা অনুযায়ী। বাংলাদেশে চাঁদ দেখার কারণে ১-২ দিনের ব্যবধান হতে পারে, তাই প্রয়োজনে সমন্বয় করুন।"),
                11f, Ui.MUTED
            ),
            lp(t = 8)
        )
        col.addView(c, lp(b = 12))

        fun refresh() {
            val adj = prefs.getInt("hijriAdj", 0)
            dateBtn.text = ldText(gDate)
            bnLine.text = banglaText(gDate)
            hjLine.text = hijriText(gDate, adj)
            adjLabel.text = bn((if (adj > 0) "+" else "") + adj.toString()) + tr(" দিন")
        }
        dateBtn.setOnClickListener {
            pickDate(gDate) {
                gDate = it
                refresh()
            }
        }
        minus.setOnClickListener {
            prefs.edit().putInt("hijriAdj", (prefs.getInt("hijriAdj", 0) - 1).coerceAtLeast(-3)).apply()
            refresh()
        }
        plus.setOnClickListener {
            prefs.edit().putInt("hijriAdj", (prefs.getInt("hijriAdj", 0) + 1).coerceAtMost(3)).apply()
            refresh()
        }
        refresh()

        // ---- Bangla -> Gregorian
        val r = card(16)
        r.addView(label(tr("বাংলা তারিখ থেকে ইংরেজি"), 15f, Ui.TEXT, true), lp(b = 8))
        val today = BanglaCal.fromGregorian(LocalDate.now())

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val day = EditText(this)
        day.hint = tr("দিন")
        day.inputType = InputType.TYPE_CLASS_NUMBER
        day.setText(today.day.toString())
        val month = Spinner(this)
        month.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, BanglaCal.MONTHS)
        month.setSelection(today.month)
        val year = EditText(this)
        year.hint = tr("সাল")
        year.inputType = InputType.TYPE_CLASS_NUMBER
        year.setText(today.year.toString())
        row.addView(day, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f))
        row.addView(month, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
        row.addView(year, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(row, lp())

        val out = label("", 17f, Ui.TEXT, true)
        r.addView(out, lp(t = 10))
        col.addView(r, lp())

        fun update() {
            val d = asciiDigits(day.text.toString()).trim().toIntOrNull()
            val y = asciiDigits(year.text.toString()).trim().toIntOrNull()
            if (d == null || y == null) {
                out.text = ""
                return
            }
            val g = BanglaCal.toGregorian(y, month.selectedItemPosition, d)
            out.text = if (g == null) tr("এই তারিখটি সঠিক নয়") else ldText(g)
        }
        day.onChange { update() }
        year.onChange { update() }
        month.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                update()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        update()
    }

    // ------------------------------------------------------------------ age

    private fun buildAge(col: LinearLayout) {
        col.addView(captionLabel(tr("জন্মতারিখ")))
        val birthBtn = dateBox(birth?.let { ldText(it) } ?: tr("জন্মতারিখ বেছে নিন"))
        col.addView(birthBtn, lp(b = 10))
        col.addView(captionLabel(tr("গণনার তারিখ (ডিফল্ট আজ)")))
        val asOfBtn = dateBox(ldText(asOf))
        col.addView(asOfBtn, lp(b = 12))

        val res = LinearLayout(this)
        res.orientation = LinearLayout.VERTICAL
        col.addView(res)

        fun refresh() {
            val b = birth
            birthBtn.text = if (b == null) tr("জন্মতারিখ বেছে নিন") else ldText(b)
            asOfBtn.text = ldText(asOf)
            res.removeAllViews()
            if (b == null) {
                res.addView(label(tr("জন্মতারিখ বেছে নিলে বয়স দেখাবে।"), 14f, Ui.MUTED))
                return
            }
            if (b.isAfter(asOf)) {
                res.addView(label(tr("জন্মতারিখ নির্ধারিত তারিখের পরে হতে পারে না।"), 14f, Ui.RED))
                return
            }
            val p = Period.between(b, asOf)
            val days = ChronoUnit.DAYS.between(b, asOf)
            val months = ChronoUnit.MONTHS.between(b, asOf)
            var next = b.withYear(asOf.year)
            if (next.isBefore(asOf)) next = b.withYear(asOf.year + 1)
            val left = ChronoUnit.DAYS.between(asOf, next)
            res.addView(
                resultBox(tr("আপনার বয়স"), bn(p.years.toString()) + tr(" বছর ") + bn(p.months.toString()) + tr(" মাস"), bn(p.days.toString()) + tr(" দিন সহ"), 24f),
                lp(b = 12)
            )
            res.addView(sectionLabel(tr("বয়সের বিস্তারিত"), 2))
            val kv = card(8)
            kv.addView(kvRow(tr("মোট মাস"), bn(months.toString())))
            kv.addView(kvRow(tr("মোট সপ্তাহ"), bn((days / 7).toString())))
            kv.addView(kvRow(tr("মোট দিন"), bn(days.toString())))
            kv.addView(kvRow(tr("জন্মদিনের বাকি দিন"), bn(left.toString()), true))
            res.addView(kv, lp(b = 10))
            val nb = LinearLayout(this)
            nb.gravity = Gravity.CENTER_VERTICAL
            nb.background = bg(Ui.CARD, 10, Ui.LINE, 1)
            nb.setPadding(dp(14), dp(13), dp(14), dp(13))
            nb.addView(iconView(R.drawable.ic_clock, 20, Ui.PRIMARY), lp(width = dp(20), height = dp(20), r = 10))
            nb.addView(label(if (left == 0L) tr("🎂 আজই জন্মদিন!") else tr("পরবর্তী জন্মদিন: ") + ldText(next), 13f, Ui.TEXT))
            res.addView(nb)
        }
        birthBtn.setOnClickListener {
            pickDate(birth ?: LocalDate.of(2000, 1, 1)) {
                birth = it
                refresh()
            }
        }
        asOfBtn.setOnClickListener {
            pickDate(asOf) {
                asOf = it
                refresh()
            }
        }
        refresh()
    }
}
