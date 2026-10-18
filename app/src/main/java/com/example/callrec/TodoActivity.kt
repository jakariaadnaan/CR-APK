package com.example.callrec

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.DialogInterface
import android.graphics.Paint
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.util.Calendar

/** করণীয়: day-to-day tasks with tick-off, an urgent flag and an optional due date. */
class TodoActivity : GuardedActivity() {

    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(96))
        scroll.addView(box)
        setScreen(tr("করণীয়"), scroll, pill(tr("+ নতুন কাজ")) { showEditor(null) })
        render()
    }

    private fun render() {
        box.removeAllViews()
        val today = todayStart()
        val all = TodoStore.all(this)
        val pending = all.count { !it.done }
        val done = all.count { it.done }

        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(
            label(tr("বাকি ") + bn(pending.toString()) + tr(" টি · সম্পন্ন ") + bn(done.toString()) + tr(" টি"), 14f, Ui.MUTED, true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        if (done > 0) {
            head.addView(pill(tr("সম্পন্নগুলো মুছুন"), Ui.BG, Ui.RED) {
                AlertDialog.Builder(this)
                    .setMessage(tr("সব সম্পন্ন কাজ তালিকা থেকে মুছে ফেলবেন?"))
                    .setPositiveButton(tr("মুছুন")) { _, _ ->
                        TodoStore.clearDone(this)
                        render()
                    }
                    .setNegativeButton(tr("না"), null)
                    .show()
            })
        }
        box.addView(head, lp(b = 10))

        val items = all.sortedWith(
            compareBy<Todo> { it.done }
                .thenByDescending { it.urgent }
                .thenBy { if (it.due == 0L) Long.MAX_VALUE else it.due }
                .thenByDescending { it.id }
        )

        if (items.isEmpty()) {
            val empty = label(tr("কোনো কাজ নেই।\nনিচের “+ নতুন কাজ” চেপে লিখে রাখুন।"), 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            box.addView(empty, lp())
        }

        for (t in items) {
            val c = card(12)
            c.orientation = LinearLayout.HORIZONTAL
            c.gravity = Gravity.CENTER_VERTICAL

            val tick = label(if (t.done) "✔" else "○", 24f, if (t.done) Ui.GREEN else Ui.MUTED, true)
            tick.gravity = Gravity.CENTER
            tick.setPadding(dp(6), dp(4), dp(14), dp(4))
            tick.setOnClickListener {
                TodoStore.upsert(this, t.copy(done = !t.done))
                render()
            }
            c.addView(tick)

            val mid = LinearLayout(this)
            mid.orientation = LinearLayout.VERTICAL
            val title = label(t.title, 16f, if (t.done) Ui.MUTED else Ui.TEXT, !t.done)
            if (t.done) title.paintFlags = title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            mid.addView(title)
            if (t.due != 0L) {
                val late = !t.done && t.due < today
                val dueTxt = tr("শেষ তারিখ: ") + dateText(t.due) + if (late) tr(" (দেরি হয়েছে)") else if (t.due == today) tr(" (আজ)") else ""
                mid.addView(label(dueTxt, 12f, if (late) Ui.RED else Ui.MUTED))
            }
            if (t.remind != 0L && !t.done) {
                mid.addView(label("⏰ " + remindText(t.remind), 12f, if (t.remind < System.currentTimeMillis()) Ui.MUTED else Ui.PRIMARY))
            }
            c.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            if (t.urgent && !t.done) {
                val u = label(tr("জরুরি"), 12f, android.graphics.Color.WHITE, true)
                u.background = bg(Ui.RED, 10)
                u.setPadding(dp(10), dp(3), dp(10), dp(3))
                c.addView(u)
            }

            c.setOnClickListener { showEditor(t) }
            box.addView(c, lp(b = 8))
        }
    }

    private fun remindText(ms: Long): String {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        return dateText(ms) + ", " + bn(String.format(java.util.Locale.US, "%d:%02d", if (c.get(Calendar.HOUR) == 0) 12 else c.get(Calendar.HOUR), c.get(Calendar.MINUTE))) +
            " " + (if (c.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM")
    }

    private fun showEditor(old: Todo?) {
        var dueMs = old?.due ?: 0L
        var remindMs = old?.remind ?: 0L

        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(10), dp(20), 0)

        val title = EditText(this)
        title.hint = tr("কাজের নাম")
        title.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        title.setText(old?.title ?: "")
        form.addView(title, lp())

        val urgent = CheckBox(this)
        urgent.text = tr("জরুরি কাজ")
        urgent.isChecked = old?.urgent ?: false
        form.addView(urgent, lp(t = 8))

        fun dueLabel(): String = if (dueMs == 0L) tr("শেষ তারিখ: নেই") else tr("শেষ তারিখ: ") + dateText(dueMs)
        val dueBtn = pill(dueLabel(), Ui.BG, Ui.PRIMARY) {}
        dueBtn.setOnClickListener {
            val c = Calendar.getInstance()
            c.timeInMillis = if (dueMs == 0L) todayStart() else dueMs
            val dpd = DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { _, y, m, d ->
                    val n = Calendar.getInstance()
                    n.clear()
                    n.set(y, m, d)
                    dueMs = n.timeInMillis
                    dueBtn.text = dueLabel()
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            )
            dpd.setButton(DialogInterface.BUTTON_NEUTRAL, tr("তারিখ বাদ দিন")) { _, _ ->
                dueMs = 0L
                dueBtn.text = dueLabel()
            }
            dpd.show()
        }
        form.addView(dueBtn, lp(t = 10, b = 4))

        fun remindLabel(): String = if (remindMs == 0L) tr("রিমাইন্ডার: নেই") else tr("রিমাইন্ডার: ") + remindText(remindMs)
        val remindBtn = pill(remindLabel(), Ui.BG, Ui.PRIMARY) {}
        remindBtn.setOnClickListener {
            val c = Calendar.getInstance()
            if (remindMs != 0L) c.timeInMillis = remindMs
            val dpd = DatePickerDialog(
                this,
                DatePickerDialog.OnDateSetListener { _, y, m, d ->
                    android.app.TimePickerDialog(this, { _, hh, mm ->
                        val n = Calendar.getInstance()
                        n.clear()
                        n.set(y, m, d, hh, mm)
                        remindMs = n.timeInMillis
                        remindBtn.text = remindLabel()
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 9)
                    }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            )
            dpd.setButton(DialogInterface.BUTTON_NEUTRAL, tr("রিমাইন্ডার বাদ দিন")) { _, _ ->
                remindMs = 0L
                remindBtn.text = remindLabel()
            }
            dpd.show()
        }
        form.addView(remindBtn, lp(t = 8, b = 4))

        val builder = AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("নতুন কাজ") else tr("কাজ সম্পাদনা"))
            .setView(form)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
        if (old != null) builder.setNeutralButton(tr("মুছুন"), null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val txt = title.text.toString().trim()
                if (txt.isEmpty()) {
                    Toast.makeText(this, tr("কাজের নাম লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    TodoStore.upsert(
                        this,
                        Todo(old?.id ?: System.currentTimeMillis(), txt, old?.done ?: false, urgent.isChecked, dueMs, remindMs)
                    )
                    dlg.dismiss()
                    render()
                }
            }
            if (old != null) {
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    TodoStore.delete(this, old.id)
                    dlg.dismiss()
                    render()
                }
            }
        }
        if (old == null) dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }
}
