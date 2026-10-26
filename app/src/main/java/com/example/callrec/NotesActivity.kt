package com.example.callrec

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
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
    private lateinit var tagBar: LinearLayout
    private var tagFilter: String? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        search = EditText(this)
        search.hint = tr("🔍  নোট খুঁজুন")
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true
        search.background = bg(Ui.CARD, 10, Ui.LINE, 1)
        search.setTextColor(Ui.TEXT)
        search.setHintTextColor(Ui.MUTED)
        search.setPadding(dp(14), dp(12), dp(14), dp(12))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                render()
            }
        })
        content.addView(search, lp(l = 16, t = 10, r = 16, b = 4))

        val tagScroll = android.widget.HorizontalScrollView(this)
        tagScroll.isHorizontalScrollBarEnabled = false
        tagBar = LinearLayout(this)
        tagBar.setPadding(dp(16), dp(6), dp(16), dp(6))
        tagScroll.addView(tagBar)
        content.addView(tagScroll, lp())

        val scroll = ScrollView(this)
        listBox = LinearLayout(this)
        listBox.orientation = LinearLayout.VERTICAL
        listBox.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(listBox)
        content.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setScreen(tr("দ্রুত নোট"), content, null, true, bottomBar(LinearLayout(this).also { it.addView(primaryButton(tr("নোট যোগ করুন")) { showEditor(null) }, lp(height = dp(48))) }))
        render()
    }

    private fun tint(n: Note): Int = Ui.mix(Color.parseColor(NOTE_COLORS[n.color - 1]), Ui.CARD, if (Ui.dark) 0.8f else 0.86f)

    private fun renderTags() {
        tagBar.removeAllViews()
        val tags = NoteStore.allTags(this)
        if (tagFilter != null && tagFilter !in tags) tagFilter = null
        if (tags.isEmpty()) { tagBar.visibility = android.view.View.GONE; return }
        tagBar.visibility = android.view.View.VISIBLE
        for (name in listOf<String?>(null) + tags) {
            val sel = name == tagFilter
            val chip = choiceChip(if (name == null) tr("সব") else "#$name", sel) { tagFilter = name; render() }
            tagBar.addView(chip, lp(width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT, r = 8))
        }
    }

    private fun render() {
        listBox.removeAllViews()
        renderTags()
        val q = search.text.toString().trim().lowercase()
        val tf = tagFilter
        val items = NoteStore.all(this)
            .filter { tf == null || it.tags.contains(tf) }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) || it.body.lowercase().contains(q) || it.tags.any { t -> t.lowercase().contains(q) } || it.checks.any { c -> c.first.lowercase().contains(q) } }
            .sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updated })

        if (items.isEmpty()) {
            val msg = if (q.isEmpty()) tr("কোনো নোট নেই।\nনিচের “+ নতুন নোট” চেপে লিখুন।") else tr("কিছু পাওয়া যায়নি।")
            val empty = label(msg, 14f, Ui.MUTED)
            empty.gravity = Gravity.CENTER
            empty.setPadding(0, dp(30), 0, dp(30))
            listBox.addView(empty, lp())
        }

        val cards = ArrayList<LinearLayout>()
        for (n in items) {
            val c = card(12)
            if (n.color > 0) c.background = bg(tint(n), Ui.cardR, Color.parseColor(NOTE_COLORS[n.color - 1]), 1)
            val head = if (n.title.isNotBlank()) n.title else n.body.lineSequence().firstOrNull() ?: n.checks.firstOrNull()?.first ?: ""
            c.addView(label((if (n.pinned) "📌 " else "") + head, 15f, Ui.TEXT, true))
            if (n.title.isNotBlank() && n.body.isNotBlank()) {
                val prev = label(n.body, 13f, Ui.MUTED)
                prev.maxLines = 2
                prev.ellipsize = TextUtils.TruncateAt.END
                c.addView(prev, lp(t = 2))
            }
            if (n.checks.isNotEmpty()) {
                val done = n.checks.count { it.second }
                c.addView(label(bn(done.toString()) + " / " + bn(n.checks.size.toString()), 12f, Ui.PRIMARY, true), lp(t = 6))
                for ((idx, ch) in n.checks.withIndex().take(5)) {
                    val line = label((if (ch.second) "☑  " else "☐  ") + ch.first, 14f, if (ch.second) Ui.MUTED else Ui.TEXT)
                    if (ch.second) line.paintFlags = line.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                    line.setPadding(0, dp(4), 0, dp(4))
                    line.setOnClickListener {
                        val nc = n.checks.toMutableList()
                        nc[idx] = Pair(ch.first, !ch.second)
                        NoteStore.upsert(this, n.copy(checks = nc))
                        render()
                    }
                    c.addView(line)
                }
                if (n.checks.size > 5) c.addView(label("…", 13f, Ui.MUTED))
            }
            if (n.tags.isNotEmpty()) c.addView(label(n.tags.joinToString("  ") { "#$it" }, 12f, Ui.PRIMARY), lp(t = 6))
            c.addView(label(dateText(n.updated), 11f, Ui.MUTED), lp(t = 4))
            c.setOnClickListener { showEditor(n) }
            cards.add(c)
        }
        if (cards.isNotEmpty()) listBox.addView(sectionLabel(tr("সাম্প্রতিক নোট"), 6))
        for (pair in cards.chunked(2)) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for ((i, c) in pair.withIndex()) row.addView(c, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(if (i == 1) dp(5) else 0, 0, if (i == 0) dp(5) else 0, 0) })
            if (pair.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).also { it.leftMargin = dp(5) })
            listBox.addView(row, lp(b = 10))
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

        // checklist
        val checkBox = LinearLayout(this)
        checkBox.orientation = LinearLayout.VERTICAL
        form.addView(checkBox, lp(t = 6))
        fun addCheckRow(text: String, done: Boolean) {
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            val cb = CheckBox(this)
            cb.isChecked = done
            val et = EditText(this)
            et.hint = tr("আইটেম")
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            et.setText(text)
            val x = label("✕", 16f, Ui.MUTED, true)
            x.gravity = Gravity.CENTER
            x.setOnClickListener { checkBox.removeView(row) }
            row.addView(cb)
            row.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(x, LinearLayout.LayoutParams(dp(40), dp(40)))
            checkBox.addView(row)
        }
        for (ch in old?.checks ?: emptyList()) addCheckRow(ch.first, ch.second)
        val addItem = label(tr("+ চেকলিস্টে আইটেম যোগ করুন"), 14f, Ui.PRIMARY, true)
        addItem.setPadding(0, dp(8), 0, dp(8))
        addItem.setOnClickListener { addCheckRow("", false) }
        form.addView(addItem)

        val tagsEt = EditText(this)
        tagsEt.hint = tr("ট্যাগ (কমা দিয়ে আলাদা করুন)")
        tagsEt.inputType = InputType.TYPE_CLASS_TEXT
        tagsEt.setText(old?.tags?.joinToString(", ") ?: "")
        form.addView(tagsEt, lp(t = 4))

        // colour
        var color = old?.color ?: 0
        val swRow = LinearLayout(this)
        swRow.gravity = Gravity.CENTER_VERTICAL
        fun paintSwatches() {
            swRow.removeAllViews()
            for (i in 0..5) {
                val col = if (i == 0) Ui.GREY else Color.parseColor(NOTE_COLORS[i - 1])
                val dot = LinearLayout(this)
                dot.gravity = Gravity.CENTER
                dot.background = bg(col, 16)
                if (i == color) dot.addView(label("✓", 15f, Color.WHITE, true))
                dot.setOnClickListener { color = i; paintSwatches() }
                swRow.addView(dot, LinearLayout.LayoutParams(dp(32), dp(32)).also { it.setMargins(0, 0, dp(10), 0) })
            }
        }
        paintSwatches()
        form.addView(swRow, lp(t = 12, b = 4))

        val pin = CheckBox(this)
        pin.text = tr("উপরে পিন করুন")
        pin.isChecked = old?.pinned ?: false
        form.addView(pin, lp(t = 6))

        val formScroll = ScrollView(this)
        formScroll.addView(form)
        val builder = AlertDialog.Builder(this)
            .setTitle(if (old == null) tr("নতুন নোট") else tr("নোট সম্পাদনা"))
            .setView(formScroll)
            .setPositiveButton(tr("সংরক্ষণ"), null)
            .setNegativeButton(tr("বাতিল"), null)
        if (old != null) builder.setNeutralButton(tr("মুছুন"), null)
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val t = title.text.toString().trim()
                val b = body.text.toString().trim()
                val checks = ArrayList<Pair<String, Boolean>>()
                for (k in 0 until checkBox.childCount) {
                    val row = checkBox.getChildAt(k) as LinearLayout
                    val txt = (row.getChildAt(1) as EditText).text.toString().trim()
                    if (txt.isNotEmpty()) checks.add(Pair(txt, (row.getChildAt(0) as CheckBox).isChecked))
                }
                val tags = tagsEt.text.toString().split(",", "،").map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct()
                if (t.isEmpty() && b.isEmpty() && checks.isEmpty()) {
                    Toast.makeText(this, tr("কিছু লিখুন"), Toast.LENGTH_SHORT).show()
                } else {
                    NoteStore.upsert(this, Note(old?.id ?: System.currentTimeMillis(), t, b, pin.isChecked, System.currentTimeMillis(), color, tags, checks))
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
