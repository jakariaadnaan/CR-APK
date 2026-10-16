package com.example.callrec

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** বাকির খাতা: who owes me and whom I owe. Paid-off lines are removed. */
class DebtActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private var filter = 0 // 0 = all, 1 = I will receive, 2 = I have to pay

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(96))
        scroll.addView(box)
        setScreen(tr("বাকির খাতা"), scroll, pill(tr("+ নতুন বাকি")) { showEditor(null) })
        render()
    }

    private fun parseAmount(s: String): Double? =
        asciiDigits(s).replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0.0 && it < 1.0e11 }

    private fun render() {
        box.removeAllViews()
        val all = DebtStore.all(this)
        val get = all.filter { !it.owe }.sumOf { it.amount }
        val pay = all.filter { it.owe }.sumOf { it.amount }
        val net = get - pay

        val sum = card()
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(stat(tr("আমি পাবো"), taka(get), Ui.GREEN), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(stat(tr("আমি দেবো"), taka(pay), Ui.RED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(
            stat(tr("নিট"), taka(net), if (net >= 0) Ui.GREEN else Ui.RED),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        sum.addView(row)
        box.addView(sum, lp(b = 12))

        // Filter chips
        val chips = LinearLayout(this)
        chips.orientation = LinearLayout.HORIZONTAL
        val names = listOf(tr("সব"), tr("পাবো"), tr("দেবো"))
        for (i in names.indices) {
            val on = filter == i
            val chip = label(names[i], 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.gravity = Gravity.CENTER
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(18), dp(8), dp(18), dp(8))
            chip.setOnClickListener {
                filter = i
                render()
            }
            chips.addView(chip, lp(r = 8))
        }
        box.addView(chips, lp(b = 12))

        val items = all
            .filter { filter == 0 || (filter == 1 && !it.owe) || (filter == 2 && it.owe) }
            .sortedWith(compareByDescending<Debt> { it.date }.thenByDescending { it.id })

        if (items.isEmpty()) {
            val empty = label(tr("কোনো বাকি নেই।\nনিচের “+ নতুন বাকি” চেপে যোগ করুন।"), 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            box.addView(empty, lp())
        }

        for (d in items) {
            val c = card(14)
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL
            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(label(d.name, 16f, Ui.TEXT, true))
            if (d.note.isNotBlank()) left.addView(label(d.note, 13f, Ui.MUTED))
            left.addView(label(dateText(d.date), 12f, Ui.MUTED))
            c.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val right = LinearLayout(this)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.END
            right.addView(label(taka(d.amount), 16f, if (d.owe) Ui.RED else Ui.GREEN, true))
            right.addView(label(if (d.owe) tr("আমি দেবো") else tr("আমি পাবো"), 12f, Ui.MUTED))
            c.addView(right)

            c.setOnClickListener { showActions(d) }
            box.addView(c, lp(b = 8))
        }
    }

    private fun stat(title: String, value: String, color: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER
        col.addView(label(title, 12f, Ui.MUTED))
        val v: TextView = label(value, 15f, color, true)
        v.gravity = Gravity.CENTER
        col.addView(v)
        return col
    }

    private fun showActions(d: Debt) {
        val options = arrayOf(tr("আংশিক পরিশোধ"), tr("পুরো পরিশোধ হয়েছে (মুছে ফেলুন)"), tr("সম্পাদনা"))
        AlertDialog.Builder(this)
            .setTitle(d.name + " · " + taka(d.amount))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPartial(d)
                    1 -> confirmPaid(d)
                    else -> showEditor(d)
                }
            }
            .setNegativeButton(tr("বন্ধ"), null)
            .show()
    }

    private fun confirmPaid(d: Debt) {
        AlertDialog.Builder(this)
            .setMessage(d.name + tr(" এর ") + taka(d.amount) + tr(" পরিশোধ হয়েছে? তালিকা থেকে মুছে যাবে।"))
            .setPositiveButton(tr("হ্যাঁ, মুছুন")) { _, _ ->
                DebtStore.delete(this, d.id)
                render()
            }
            .setNegativeButton(tr("না"), null)
            .show()
    }

    private fun showPartial(d: Debt) {
        val input = EditText(this)
        input.hint = tr("কত টাকা পরিশোধ হলো")
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val wrap = LinearLayout(this)
        wrap.setPadding(dp(20), dp(10), dp(20), 0)
        wrap.addView(input, lp())
        val dlg = AlertDialog.Builder(this)
            .setTitle(tr("আংশিক পরিশোধ"))
            .setMessage(tr("বাকি আছে ") + taka(d.amount))
            .setView(wrap)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val v = parseAmount(input.text.toString())
                if (v == null) {
                    Toast.makeText(this, tr("সঠিক টাকার পরিমাণ লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    val left = d.amount - v
                    if (left <= 0.005) {
                        DebtStore.delete(this, d.id)
                        Toast.makeText(this, tr("পুরো পরিশোধ হয়েছে, তালিকা থেকে মুছে গেছে"), Toast.LENGTH_SHORT).show()
                    } else {
                        DebtStore.upsert(this, d.copy(amount = Math.round(left * 100) / 100.0))
                    }
                    dlg.dismiss()
                    render()
                }
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }

    private fun showEditor(old: Debt?) {
        var owe = old?.owe ?: false
        var dateMs = old?.date ?: todayStart()

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(10), dp(20), 0)

        form.addView(
            dialogTabs(tr("আমি পাবো"), tr("আমি দেবো"), Ui.GREEN, Ui.RED, owe) { owe = it },
            lp(b = 12)
        )

        val name = AutoCompleteTextView(this)
        name.hint = tr("কার সাথে (নাম)")
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        name.threshold = 1
        name.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, DebtStore.names(this)))
        name.setText(old?.name ?: "")
        form.addView(name, lp())

        val amount = EditText(this)
        amount.hint = tr("টাকার পরিমাণ")
        amount.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (old != null) {
            val a = old.amount
            amount.setText(if (a == Math.floor(a)) a.toLong().toString() else a.toString())
        }
        form.addView(amount, lp(t = 4))

        val note = EditText(this)
        note.hint = tr("নোট (ঐচ্ছিক)")
        note.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        note.setText(old?.note ?: "")
        form.addView(note, lp(t = 4))

        val dateBtn = pill(dateText(dateMs), Ui.BG, Ui.PRIMARY) {}
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
        form.addView(dateBtn, lp(t = 12, b = 4))

        val dlg = AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("নতুন বাকি") else tr("বাকি সম্পাদনা"))
            .setView(form)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val who = name.text.toString().trim()
                val v = parseAmount(amount.text.toString())
                if (who.isEmpty()) {
                    Toast.makeText(this, tr("নাম লিখুন"), Toast.LENGTH_SHORT).show()
                } else if (v == null) {
                    Toast.makeText(this, tr("সঠিক টাকার পরিমাণ লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    DebtStore.upsert(
                        this,
                        Debt(old?.id ?: System.currentTimeMillis(), owe, who, v, note.text.toString().trim(), dateMs)
                    )
                    dlg.dismiss()
                    render()
                }
            }
        }
        if (old == null) dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }
}
