package com.example.callrec

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** দ্রুত নোট: short notes, phone numbers, ideas, with search and pinning. */
class NotesActivity : GuardedActivity() {

    private lateinit var listBox: LinearLayout
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        search = EditText(this)
        search.hint = tr("🔍  নোট খুঁজুন")
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true
        search.background = bg(Ui.CARD, 12)
        search.setPadding(dp(14), dp(10), dp(14), dp(10))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })
        content.addView(search, lp(l = 14, t = 12, r = 14, b = 4))

        val scroll = ScrollView(this)
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(dp(14), dp(8), dp(14), dp(96))
        scroll.addView(listBox)
        content.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setScreen(tr("দ্রুত নোট"), content, pill(tr("+ নতুন নোট")) { showEditor(null) })
        render()
    }

    private fun render() {
        listBox.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val items = NoteStore.all(this)
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.body.lowercase().contains(q) }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })

        if (items.isEmpty()) {
            val msg = if (q.isEmpty()) tr("কোনো নোট নেই।\nনিচের “+ নতুন নোট” চেপে লিখুন।") else tr("কিছু পাওয়া যায়নি।")
            val empty = label(msg, 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            listBox.addView(empty, lp())
        }

        for (n in items) {
            val c = card(14)
            val head = if (n.title.isNotBlank()) n.title else n.body.lineSequence().firstOrNull() ?: ""
            c.addView(label((if (n.pinned) "📌 " else "") + head, 16f, Ui.TEXT, true))
            if (n.title.isNotBlank() && n.body.isNotBlank()) {
                val prev = label(n.body, 13f, Ui.MUTED)
                prev.maxLines = 2
                prev.ellipsize = TextUtils.TruncateAt.END
                c.addView(prev, lp(t = 2))
            }
            c.addView(label(dateText(n.updated), 11f, Ui.MUTED), lp(t = 4))
            c.setOnClickListener { showEditor(n) }
            listBox.addView(c, lp(b = 8))
        }
    }

    private fun showEditor(old: Note?) {
        val form = LinearLayout(this)
        form.orientation = LinearLayout.VERTICAL
        form.setPadding(dp(20), dp(10), dp(20), 0)

        val title = EditText(this)
        title.hint = tr("শিরোনাম (ঐচ্ছিক)")
        title.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        title.setText(old?.title ?: "")
        form.addView(title, lp())

        val body = EditText(this)
        body.hint = tr("এখানে লিখুন…")
        body.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        body.minLines = 6
        body.gravity = Gravity.TOP or Gravity.START
        body.setText(old?.body ?: "")
        form.addView(body, lp(t = 4))

        val pin = CheckBox(this)
        pin.text = tr("উপরে পিন করুন")
        pin.isChecked = old?.pinned ?: false
        form.addView(pin, lp(t = 6))

        val builder = AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("নতুন নোট") else tr("নোট সম্পাদনা"))
            .setView(form)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
        if (old != null) builder.setNeutralButton(tr("মুছুন"), null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim()
                val b = body.text.toString().trim()
                if (t.isEmpty() && b.isEmpty()) {
                    Toast.makeText(this, tr("কিছু লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    NoteStore.upsert(this, Note(old?.id ?: System.currentTimeMillis(), t, b, pin.isChecked, System.currentTimeMillis()))
                    dlg.dismiss()
                    render()
                }
            }
            if (old != null) {
                dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                    AlertDialog.Builder(this)
                        .setMessage(tr("এই নোট মুছে ফেলবেন?"))
                        .setPositiveButton(tr("মুছুন")) { _, _ ->
                            NoteStore.delete(this, old.id)
                            dlg.dismiss()
                            render()
                        }
                        .setNegativeButton(tr("না"), null)
                        .show()
                }
            }
        }
        dlg.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show()
    }
}
