package com.memoredia.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** খরচের খাতা: daily income / expense, monthly totals and a per-category summary. */
class LedgerActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private var year = 0
    private var month = 0

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val now = Calendar.getInstance()
        year = now.get(Calendar.YEAR)
        month = now.get(Calendar.MONTH)

        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        val add = primaryButton(tr("লেনদেন যোগ করুন")) { showEditor(null) }
        setScreen(tr("খরচের খাতা"), scroll, null, true, bottomBar(LinearLayout(this).also { it.addView(add, lp(height = dp(48))) }))
        render()
    }

    private fun render() {
        box.removeAllViews()

        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month, 1)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val end = cal.timeInMillis

        val items = LedgerStore.all(this)
            .filter { it.date >= start && it.date < end }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.id })

        // Month selector
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        val prev = label("‹", 30f, Ui.PRIMARY, true)
        prev.setPadding(dp(18), 0, dp(18), 0)
        prev.setOnClickListener { shiftMonth(-1) }
        val next = label("›", 30f, Ui.PRIMARY, true)
        next.setPadding(dp(18), 0, dp(18), 0)
        next.setOnClickListener { shiftMonth(1) }
        val title = label(BN_MONTHS[month] + " " + bn(year.toString()), 18f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        nav.addView(prev)
        nav.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(next)
        box.addView(nav, lp(b = 10))

        // Totals
        val inc = items.filter { it.income }.sumOf { it.amount }
        val exp = items.filter { !it.income }.sumOf { it.amount }
        val bal = inc - exp

        box.addView(resultBox(tr("মাসিক ব্যালেন্স"), taka(bal), BN_MONTHS[month] + " " + bn(year.toString()), 30f), lp(b = 10))
        val pair = LinearLayout(this)
        pair.orientation = LinearLayout.HORIZONTAL
        pair.addView(statTile(tr("আয়"), taka(inc), Ui.PRIMARY), lp(width = 0, r = 5).also { it.weight = 1f })
        pair.addView(statTile(tr("ব্যয়"), taka(exp)), lp(width = 0, l = 5).also { it.weight = 1f })
        box.addView(pair, lp(b = 6))

        // Category summary (expenses only)
        val byCat = items.filter { !it.income }
            .groupBy { it.category }
            .map { Pair(it.key, it.value.sumOf { e -> e.amount }) }
            .sortedByDescending { it.second }
        if (byCat.isNotEmpty() && exp > 0) {
            box.addView(sectionLabel(tr("ক্যাটাগরি অনুযায়ী ব্যয়")))
            val cc = card()
            for ((i, c2) in byCat.withIndex()) {
                val name = c2.first
                val amt = c2.second
                val pct = (amt / exp * 100)
                val line = LinearLayout(this)
                line.orientation = LinearLayout.HORIZONTAL
                line.addView(label(tr(name), 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                line.addView(label(taka(amt) + "  (" + bn(Math.round(pct).toString()) + "%)", 13f, Ui.MUTED))
                cc.addView(line, lp(t = if (i == 0) 0 else 10))
                val bar = LinearLayout(this)
                bar.orientation = LinearLayout.HORIZONTAL
                bar.weightSum = 100f
                bar.background = bg(Ui.LINE, 4)
                val fill = View(this)
                fill.background = bg(Ui.PRIMARY, 4)
                bar.addView(fill, LinearLayout.LayoutParams(0, dp(6), pct.toFloat().coerceIn(1f, 100f)))
                cc.addView(bar, lp(height = 6, t = 4))
            }
            box.addView(cc)
        }

        // Entries
        box.addView(sectionLabel(tr("সাম্প্রতিক লেনদেন (") + bn(items.size.toString()) + ")", 16))
        if (items.isEmpty()) {
            val empty = label(tr("এই মাসে কোনো লেনদেন নেই।\nনিচের “+ নতুন লেনদেন” চেপে শুরু করুন।"), 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            box.addView(empty, lp())
        }
        val list = card(4)
        for ((i, e) in items.withIndex()) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL
            c.setPadding(dp(10), dp(12), dp(10), dp(12))
            if (e.income) {
                val plus = label("+", 20f, Ui.PRIMARY, true)
                plus.gravity = Gravity.CENTER
                plus.background = bg(Ui.KEY_OP, 10)
                c.addView(plus, lp(width = dp(36), height = dp(36), r = 12))
            } else c.addView(iconBox(R.drawable.ic_wallet, Ui.KEY_OP, Ui.PRIMARY, 36, 18, 10, 12))
            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(label(tr(e.category), 15f, Ui.TEXT, true))
            val sub = (if (e.note.isNotBlank()) e.note + " · " else "") + dateText(e.date)
            left.addView(label(sub, 12f, Ui.MUTED))
            c.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(label((if (e.income) "+" else "−") + taka(e.amount), 15f, if (e.income) Ui.PRIMARY else Ui.TEXT, true))
            c.setOnClickListener { showEditor(e) }
            list.addView(c)
            if (i < items.size - 1) list.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1, l = 10, r = 10))
        }
        if (items.isNotEmpty()) box.addView(list)
    }

    private fun statCol(title: String, value: String, color: Int): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.addView(label(title, 12f, Ui.MUTED))
        val v = label(value, 15f, color, true)
        v.gravity = Gravity.CENTER
        col.addView(v)
        return col
    }

    private fun shiftMonth(delta: Int) {
        month += delta
        if (month < 0) {
            month = 11
            year -= 1
        } else if (month > 11) {
            month = 0
            year += 1
        }
        render()
    }

    private fun showEditor(old: Entry?) {
        var income = old?.income ?: false
        var dateMs = old?.date ?: todayStart()

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(10), dp(20), 0)

        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        val tOut = label(tr("ব্যয়"), 15f, Color.WHITE, true)
        val tIn = label(tr("আয়"), 15f, Color.WHITE, true)
        for (t in listOf(tOut, tIn)) {
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
        }
        tabs.addView(tOut, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(tIn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        form.addView(tabs, lp(b = 12))

        val amount = EditText(this)
        amount.hint = tr("টাকার পরিমাণ")
        amount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (old != null) {
            val a = old.amount
            amount.setText(if (a == Math.floor(a)) a.toLong().toString() else a.toString())
        }
        form.addView(amount, lp())

        val spin = Spinner(this)
        form.addView(spin, lp(t = 8))

        val note = EditText(this)
        note.hint = tr("নোট (ঐচ্ছিক)")
        note.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        note.setText(old?.note ?: "")
        form.addView(note, lp(t = 4))

        val dateBtn = pill(dateText(dateMs), Ui.BG, Ui.PRIMARY) {}
        form.addView(dateBtn, lp(t = 12, b = 4))

        fun cats(): List<String> = if (income) LedgerStore.INCOME_CATS else LedgerStore.EXPENSE_CATS

        fun fillCats(selected: String?) {
            spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cats().map { tr(it) })
            val i = cats().indexOf(selected)
            if (i >= 0) spin.setSelection(i)
        }

        fun styleTabs() {
            tOut.background = bg(if (!income) Ui.PRIMARY else Ui.GREY, 10)
            tIn.background = bg(if (income) Ui.PRIMARY else Ui.GREY, 10)
        }

        styleTabs()
        fillCats(old?.category)
        tOut.setOnClickListener {
            income = false
            styleTabs()
            fillCats(null)
        }
        tIn.setOnClickListener {
            income = true
            styleTabs()
            fillCats(null)
        }
        dateBtn.setOnClickListener {
            val c = Calendar.getInstance()
            c.timeInMillis = dateMs
            DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { _, y, m, d ->
                    val n = Calendar.getInstance()
                    n.clear()
                    n.set(y, m, d)
                    dateMs = n.timeInMillis
                    dateBtn.text = dateText(dateMs)
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("নতুন লেনদেন") else tr("লেনদেন সম্পাদনা"))
            .setView(form)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
        if (old != null) builder.setNeutralButton(tr("মুছুন"), null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = asciiDigits(amount.text.toString()).replace(",", "").trim().toDoubleOrNull()
                if (v == null || v <= 0.0 || v > 1.0e11) {
                    Toast.makeText(this, tr("সঠিক টাকার পরিমাণ লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    val cat = cats().getOrNull(spin.selectedItemPosition) ?: "অন্যান্য"
                    val saved = Entry(old?.id ?: System.currentTimeMillis(), income, v, cat, note.text.toString().trim(), dateMs)
                    LedgerStore.upsert(this, saved)
                    val c = Calendar.getInstance()
                    c.timeInMillis = dateMs
                    year = c.get(Calendar.YEAR)
                    month = c.get(Calendar.MONTH)
                    dlg.dismiss()
                    render()
                }
            }
            if (old != null) {
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(this)
                        .setMessage(tr("এই লেনদেন মুছে ফেলবেন?"))
                        .setPositiveButton(tr("মুছুন")) { _, _ ->
                            LedgerStore.delete(this, old.id)
                            dlg.dismiss()
                            render()
                        }
                        .setNegativeButton(tr("না"), null)
                        .show()
                }
            }
        }
        if (old == null) dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }
}
