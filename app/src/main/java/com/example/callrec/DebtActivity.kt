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
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** বাকির খাতা: who owes me and whom I owe. Paid-off lines are removed. */
class DebtActivity : GuardedActivity() {

    private lateinit var box: LinearLayout
    private var filter = 0 // 0 = I will receive, 1 = I have to pay

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)
        setScreen(tr("বাকির খাতা"), scroll, null, true, bottomBar(LinearLayout(this).also { it.addView(primaryButton(tr("বাকি যোগ করুন")) { showEditor(null) }, lp(height = dp(48))) }))
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

        val items = all
            .filter { (filter == 0 && !it.owe) || (filter == 1 && it.owe) }
            .sortedWith(compareByDescending<Debt> { it.date }.thenByDescending { it.id })

        box.addView(chipRow(listOf(tr("আমি পাবো"), tr("আমি দেবো")), filter) { filter = it; render() }, lp(b = 12))
        val shown = if (filter == 0) get else pay
        box.addView(
            resultBox(tr("মোট বকেয়া"), taka(shown), bn(items.size.toString()) + tr(" টি সক্রিয় রেকর্ড"), 30f),
            lp(b = 10)
        )
        val pair = LinearLayout(this)
        pair.orientation = LinearLayout.HORIZONTAL
        pair.addView(statTile(tr("আমি পাবো"), taka(get), Ui.PRIMARY), lp(width = 0, r = 5).also { it.weight = 1f })
        pair.addView(statTile(tr("আমি দেবো"), taka(pay)), lp(width = 0, l = 5).also { it.weight = 1f })
        box.addView(pair, lp(b = 6))
        box.addView(sectionLabel(tr("সাম্প্রতিক রেকর্ড"), 16))

        if (items.isEmpty()) {
            val empty = label(tr("কোনো বাকি নেই।\nনিচের বাটন চেপে যোগ করুন।"), 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            box.addView(empty, lp())
        }

        val list = card(4)
        for ((i, d) in items.withIndex()) {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL
            c.setPadding(dp(10), dp(12), dp(10), dp(12))
            c.addView(iconBox(R.drawable.ic_user, Ui.KEY_OP, Ui.PRIMARY, 36, 18, 10, 12))
            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(label(d.name, 15f, Ui.TEXT, true))
            val sub = (if (d.note.isNotBlank()) d.note + " · " else "") + dateText(d.date)
            left.addView(label(sub, 12f, Ui.MUTED))
            if (d.pays.isNotEmpty()) left.addView(label(tr("পরিশোধ হয়েছে: ") + taka(d.pays.sumOf { it.second }), 12f, Ui.PRIMARY))
            if (d.due != 0L) left.addView(label(tr("রিমাইন্ডার: ") + dateText(d.due), 12f, if (d.due < todayStart()) Ui.RED else Ui.MUTED))
            c.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            c.addView(label(taka(d.amount), 15f, if (d.owe) Ui.TEXT else Ui.PRIMARY, true))
            c.setOnClickListener { showActions(d) }
            list.addView(c)
            if (i < items.size - 1) list.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1, l = 10, r = 10))
        }
        if (items.isNotEmpty()) box.addView(list)
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
        val options = arrayOf(tr("আংশিক পরিশোধ"), tr("পুরো পরিশোধ হয়েছে (মুছে ফেলুন)"), tr("সম্পাদনা"), tr("পরিশোধের ইতিহাস"))
        AlertDialog.Builder(this)
            .setTitle(d.name + " · " + taka(d.amount))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showPartial(d)
                    1 -> confirmPaid(d)
                    3 -> showHistory(d)
                    else -> showEditor(d)
                }
            }
            .setNegativeButton(tr("বন্ধ"), null)
            .show()
    }

    private fun showHistory(d: Debt) {
        val sb = StringBuilder()
        if (d.pays.isEmpty()) {
            sb.append(tr("এখনো কোনো পরিশোধ নেই।"))
        } else {
            for (p in d.pays) sb.append(dateText(p.first)).append(": ").append(taka(p.second)).append("\n")
            sb.append("\n").append(tr("মোট পরিশোধ: ")).append(taka(d.pays.sumOf { it.second }))
        }
        sb.append("\n").append(tr("বাকি আছে: ")).append(taka(d.amount))
        AlertDialog.Builder(this).setTitle(d.name).setMessage(sb.toString()).setPositiveButton(tr("ঠিক আছে"), null).show()
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
                        DebtStore.upsert(this, d.copy(amount = Math.round(left * 100) / 100.0, pays = d.pays + Pair(todayStart(), v)))
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
            dialogTabs(tr("আমি পাবো"), tr("আমি দেবো"), Ui.PRIMARY, Ui.PRIMARY, owe) { owe = it },
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

        var dueMs = old?.due ?: 0L
        fun dueText() = if (dueMs == 0L) tr("রিমাইন্ডার: নেই") else tr("রিমাইন্ডার: ") + dateText(dueMs)
        val dueBtn = pill(dueText(), Ui.BG, Ui.PRIMARY) {}
        dueBtn.setOnClickListener {
            val c = Calendar.getInstance()
            if (dueMs != 0L) c.timeInMillis = dueMs
            val dp = DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { _, y, m, d ->
                    val n = Calendar.getInstance()
                    n.clear()
                    n.set(y, m, d)
                    dueMs = n.timeInMillis
                    dueBtn.text = dueText()
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 8)
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            )
            dp.setButton(android.content.DialogInterface.BUTTON_NEUTRAL, tr("তারিখ সরান")) { _, _ ->
                dueMs = 0L
                dueBtn.text = dueText()
            }
            dp.show()
        }
        form.addView(dueBtn, lp(t = 8, b = 4))

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
                        Debt(old?.id ?: System.currentTimeMillis(), owe, who, v, note.text.toString().trim(), dateMs, old?.pays ?: emptyList(), dueMs)
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
