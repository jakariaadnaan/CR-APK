package com.example.callrec

import android.app.AlertDialog
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import java.util.Calendar

/** বাজেট: set a monthly limit per expense category and see this month's use. */
class BudgetActivity : GuardedActivity() {

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(8), dp(14), dp(24))
        scroll.addView(box)
        setScreen(tr("বাজেট"), scroll)
        render()
    }

    private fun render() {
        box.removeAllViews()
        val now = Calendar.getInstance()
        val spent = BudgetStore.spent(this, now.get(Calendar.YEAR), now.get(Calendar.MONTH))
        box.addView(label(tr("প্রতি মাসের সীমা। ক্যাটাগরিতে চেপে টাকা ঠিক করুন, খালি রাখলে সীমা থাকবে না।"), 13f, Ui.MUTED), lp(l = 4, b = 10))
        for (cat in LedgerStore.EXPENSE_CATS) {
            val limit = BudgetStore.get(this, cat)
            val used = spent[cat] ?: 0.0
            val c = card(16)
            val top = LinearLayout(this)
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(label(tr(cat), 16f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(label(if (limit > 0) taka(limit) else tr("সীমা নেই"), 15f, if (limit > 0) Ui.PRIMARY else Ui.MUTED, limit > 0))
            c.addView(top)
            if (limit > 0 || used > 0) {
                c.addView(label(tr("এই মাসে খরচ: ") + taka(used), 13f, if (limit > 0 && used > limit) Ui.RED else Ui.MUTED), lp(t = 4))
            }
            if (limit > 0) {
                val bar = LinearLayout(this)
                bar.background = bg(Ui.LINE, 4)
                bar.weightSum = 100f
                val f = View(this)
                f.background = bg(budgetColor(used / limit), 4)
                bar.addView(f, LinearLayout.LayoutParams(0, dp(8), Math.min(100.0, used / limit * 100).toFloat()))
                c.addView(bar, lp(height = dp(8), t = 10))
            }
            c.setOnClickListener { edit(cat, limit) }
            box.addView(c, lp(b = 8))
        }
    }

    private fun edit(cat: String, current: Double) {
        val f = EditText(this)
        f.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        f.hint = tr("যেমন: 5000")
        if (current > 0) f.setText(if (current % 1.0 == 0.0) current.toLong().toString() else current.toString())
        val w = LinearLayout(this)
        w.setPadding(dp(20), dp(10), dp(20), 0)
        w.addView(f, lp())
        AlertDialog.Builder(this)
            .setTitle(tr(cat))
            .setView(w)
            .setPositiveButton(tr("সংরক্ষণ")) { _, _ ->
                val v = asciiDigits(f.text.toString()).toDoubleOrNull() ?: 0.0
                BudgetStore.set(this, cat, v)
                render()
            }
            .setNegativeButton(tr("বাতিল"), null)
            .show()
    }
}

/** Neutral until 80%, amber from 80%, red once over the limit. */
fun budgetColor(ratio: Double): Int = when {
    ratio >= 1.0 -> Ui.RED
    ratio >= 0.8 -> android.graphics.Color.parseColor("#E08A1E")
    else -> Ui.PRIMARY
}
