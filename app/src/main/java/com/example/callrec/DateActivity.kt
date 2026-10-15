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
        "মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউস সানি", "জমাদিউল আউয়াল", "জমাদিউস সানি",
        "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"
    )
    private val weekdays = listOf("রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার")

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
        root.addView(tabRow, lp(l = 14, t = 12, r = 14, b = 6))
        body = FrameLayout(this)
        root.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setScreen("তারিখ ও বয়স", root)
        showTab(0)
    }

    private fun showTab(t: Int) {
        tab = t
        tabRow.removeAllViews()
        val names = listOf("তারিখ রূপান্তর", "বয়স")
        for (i in names.indices) {
            val on = tab == i
            val chip = label(names[i], 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.gravity = Gravity.CENTER
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(18), dp(8), dp(18), dp(8))
            chip.setOnClickListener { showTab(i) }
            tabRow.addView(chip, lp(r = 8))
        }
        body.removeAllViews()
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(14), dp(6), dp(14), dp(24))
        scroll.addView(col)
        body.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        if (tab == 0) buildConverter(col) else buildAge(col)
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
                " " + bn(h.get(ChronoField.YEAR).toString()) + " হিজরি"
        } catch (e: Exception) {
            "এই তারিখের হিজরি মান পাওয়া যায়নি"
        }

    private fun banglaText(d: LocalDate): String {
        val b = BanglaCal.fromGregorian(d)
        return bn(b.day.toString()) + " " + BanglaCal.MONTHS[b.month] + " " + bn(b.year.toString()) + " বঙ্গাব্দ"
    }

    private fun buildConverter(col: LinearLayout) {
        // ---- Gregorian -> Bangla / Hijri
        val c = card(16)
        c.addView(label("ইংরেজি তারিখ", 13f, Ui.MUTED))
        val dateBtn = pill(ldText(gDate), Ui.BG, Ui.PRIMARY) {}
        c.addView(dateBtn, lp(t = 6, b = 12))

        val bnLine: TextView = label("", 17f, Ui.PRIMARY, true)
        val hjLine: TextView = label("", 17f, Ui.PRIMARY, true)
        c.addView(label("বাংলা", 12f, Ui.MUTED))
        c.addView(bnLine, lp(b = 8))
        c.addView(label("হিজরি", 12f, Ui.MUTED))
        c.addView(hjLine, lp(b = 8))

        val adjRow = LinearLayout(this)
        adjRow.orientation = LinearLayout.HORIZONTAL
        adjRow.gravity = Gravity.CENTER_VERTICAL
        val adjLabel = label("", 13f, Ui.MUTED)
        val minus = pill("−", Ui.BG, Ui.PRIMARY) {}
        val plus = pill("+", Ui.BG, Ui.PRIMARY) {}
        adjRow.addView(label("হিজরি সমন্বয়", 13f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        adjRow.addView(minus)
        adjRow.addView(adjLabel, lp(l = 12, r = 12))
        adjRow.addView(plus)
        c.addView(adjRow, lp(t = 4))
        c.addView(
            label(
                "হিজরি তারিখ উম্মুল কুরা (সৌদি) পঞ্জিকা অনুযায়ী। বাংলাদেশে চাঁদ দেখার কারণে ১-২ দিনের ব্যবধান হতে পারে, তাই প্রয়োজনে সমন্বয় করুন।",
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
            adjLabel.text = bn((if (adj > 0) "+" else "") + adj.toString()) + " দিন"
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
        r.addView(label("বাংলা তারিখ থেকে ইংরেজি", 15f, Ui.TEXT, true), lp(b = 8))
        val today = BanglaCal.fromGregorian(LocalDate.now())

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val day = EditText(this)
        day.hint = "দিন"
        day.inputType = InputType.TYPE_CLASS_NUMBER
        day.setText(today.day.toString())
        val month = Spinner(this)
        month.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, BanglaCal.MONTHS)
        month.setSelection(today.month)
        val year = EditText(this)
        year.hint = "সাল"
        year.inputType = InputType.TYPE_CLASS_NUMBER
        year.setText(today.year.toString())
        row.addView(day, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f))
        row.addView(month, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
        row.addView(year, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(row, lp())

        val out = label("", 17f, Ui.PRIMARY, true)
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
            out.text = if (g == null) "এই তারিখটি সঠিক নয়" else ldText(g)
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
        val c = card(16)
        c.addView(label("জন্মতারিখ", 13f, Ui.MUTED))
        val birthBtn = pill(birth?.let { ldText(it) } ?: "জন্মতারিখ বেছে নিন", Ui.BG, Ui.PRIMARY) {}
        c.addView(birthBtn, lp(t = 6, b = 12))
        c.addView(label("যে তারিখ পর্যন্ত (ডিফল্ট আজ)", 13f, Ui.MUTED))
        val asOfBtn = pill(ldText(asOf), Ui.BG, Ui.PRIMARY) {}
        c.addView(asOfBtn, lp(t = 6))
        col.addView(c, lp(b = 12))

        val res = card(16)
        val out = label("", 16f, Ui.TEXT)
        out.setLineSpacing(0f, 1.25f)
        res.addView(out)
        col.addView(res, lp())

        fun refresh() {
            val b = birth
            birthBtn.text = if (b == null) "জন্মতারিখ বেছে নিন" else ldText(b)
            asOfBtn.text = ldText(asOf)
            if (b == null) {
                out.text = "জন্মতারিখ বেছে নিলে বয়স দেখাবে।"
                return
            }
            if (b.isAfter(asOf)) {
                out.text = "জন্মতারিখ নির্ধারিত তারিখের পরে হতে পারে না।"
                return
            }
            val p = Period.between(b, asOf)
            val days = ChronoUnit.DAYS.between(b, asOf)
            val months = ChronoUnit.MONTHS.between(b, asOf)
            var next = b.withYear(asOf.year)
            if (next.isBefore(asOf)) next = b.withYear(asOf.year + 1)
            val left = ChronoUnit.DAYS.between(asOf, next)
            val sb = StringBuilder()
            sb.append("বয়স: ").append(bn(p.years.toString())).append(" বছর ")
                .append(bn(p.months.toString())).append(" মাস ")
                .append(bn(p.days.toString())).append(" দিন\n")
            sb.append("মোট: ").append(bn(months.toString())).append(" মাস, ")
                .append(bn((days / 7).toString())).append(" সপ্তাহ, ")
                .append(bn(days.toString())).append(" দিন\n")
            if (left == 0L) {
                sb.append("🎂 আজই জন্মদিন!")
            } else {
                sb.append("পরবর্তী জন্মদিন: ").append(ldText(next)).append(" (").append(bn(left.toString())).append(" দিন বাকি)")
            }
            out.text = sb.toString()
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
