package com.example.callrec

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
        box.setPadding(dp(14), dp(14), dp(14), dp(96))
        scroll.addView(box)

        val add = pill("+ নতুন লেনদেন") { showEditor(null) }
        setScreen("খরচের খাতা", scroll, add)
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

        val sum = card()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(statCol("আয়", taka(inc), Ui.GREEN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(statCol("ব্যয়", taka(exp), Ui.RED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            statCol("ব্যালেন্স", taka(bal), if (bal >= 0) Ui.GREEN else Ui.RED),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        sum.addView(row)
        box.addView(sum, lp(b = 12))

        // Category summary (expenses only)
        val byCat = items.filter { !it.income }
            .groupBy { it.category }
            .map { Pair(it.key, it.value.sumOf { e -> e.amount }) }
            .sortedByDescending { it.second }
        if (byCat.isNotEmpty() && exp > 0) {
            val cc = card()
            cc.addView(label("ক্যাটাগরি অনুযায়ী ব্যয়", 15f, Ui.TEXT, true), lp(b = 8))
            for ((name, amt) in byCat) {
                val pct = (amt / exp * 100)
                val line = LinearLayout(this)
                line.orientation = LinearLayout.HORIZONTAL
                line.addView(label(name, 14f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                line.addView(label(taka(amt) + "  (" + bn(Math.round(pct).toString()) + "%)", 13f, Ui.MUTED))
                cc.addView(line, lp(t = 6))

                val bar = LinearLayout(this)
                bar.orientation = LinearLayout.HORIZONTAL
                bar.weightSum = 100f
                bar.background = bg(Ui.LINE, 4)
                val fill = View(this)
                fill.background = bg(Ui.PRIMARY, 4)
                bar.addView(fill, LinearLayout.LayoutParams(0, dp(6), pct.toFloat().coerceIn(1f, 100f)))
                cc.addView(bar, lp(height = 6, t = 4))
            }
            box.addView(cc, lp(b = 12))
        }

        // Entries
        box.addView(label("লেনদেন (" + bn(items.size.toString()) + ")", 15f, Ui.MUTED, true), lp(l = 4, b = 6))
        if (items.isEmpty()) {
            val empty = label("এই মাসে কোনো লেনদেন নেই।\nনিচের “+ নতুন লেনদেন” চেপে শুরু করুন।", 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            box.addView(empty, lp())
        }
        for (e in items) {
            val c = card(14)
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL
            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(label(e.category, 15f, Ui.TEXT, true))
            if (e.note.isNotBlank()) left.addView(label(e.note, 13f, Ui.MUTED))
            left.addView(label(dateText(e.date), 12f, Ui.MUTED))
            c.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(
                label((if (e.income) "+" else "−") + taka(e.amount), 16f, if (e.income) Ui.GREEN else Ui.RED, true)
            )
            c.setOnClickListener { showEditor(e) }
            box.addView(c, lp(b = 8))
        }
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
        val tOut = label("ব্যয়", 15f, Color.WHITE, true)
        val tIn = label("আয়", 15f, Color.WHITE, true)
        for (t in listOf(tOut, tIn)) {
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
        }
        tabs.addView(tOut, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        tabs.addView(tIn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        form.addView(tabs, lp(b = 12))

        val amount = EditText(this)
        amount.hint = "টাকার পরিমাণ"
        amount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (old != null) {
            val a = old.amount
            amount.setText(if (a == Math.floor(a)) a.toLong().toString() else a.toString())
        }
        form.addView(amount, lp())

        val spin = Spinner(this)
        form.addView(spin, lp(t = 8))

        val note = EditText(this)
        note.hint = "নোট (ঐচ্ছিক)"
        note.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        note.setText(old?.note ?: "")
        form.addView(note, lp(t = 4))

        val dateBtn = pill(dateText(dateMs), Ui.BG, Ui.PRIMARY) {}
        form.addView(dateBtn, lp(t = 12, b = 4))

        fun cats(): List<String> = if (income) LedgerStore.INCOME_CATS else LedgerStore.EXPENSE_CATS

        fun fillCats(selected: String?) {
            spin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, cats())
            val i = cats().indexOf(selected)
            if (i >= 0) spin.setSelection(i)
        }

        fun styleTabs() {
            tOut.background = bg(if (!income) Ui.RED else Color.parseColor("#B8C0BC"), 10)
            tIn.background = bg(if (income) Ui.GREEN else Color.parseColor("#B8C0BC"), 10)
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
            .setTitle(if (old == null) "নতুন লেনদেন" else "লেনদেন সম্পাদনা")
            .setView(form)
            .setPositiveButton("সংরক্ষণ", null)
            .setNegativeButton("বাতিল", null)
        if (old != null) builder.setNeutralButton("মুছুন", null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = asciiDigits(amount.text.toString()).replace(",", "").trim().toDoubleOrNull()
                if (v == null || v <= 0.0 || v > 1.0e11) {
                    Toast.makeText(this, "সঠিক টাকার পরিমাণ লিখুন", Toast.LENGTH_SHORT).show()
                } else {
                    val cat = spin.selectedItem as? String ?: "অন্যান্য"
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
                        .setMessage("এই লেনদেন মুছে ফেলবেন?")
                        .setPositiveButton("মুছুন") { _, _ ->
                            LedgerStore.delete(this, old.id)
                            dlg.dismiss()
                            render()
                        }
                        .setNegativeButton("না", null)
                        .show()
                }
            }
        }
        if (old == null) dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }
}
