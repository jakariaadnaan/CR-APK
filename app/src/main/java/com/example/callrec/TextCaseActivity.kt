package com.example.callrec

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** টেক্সট কেস কনভার্টার: one input, many ways to change its case or layout. */
class TextCaseActivity : GuardedActivity() {

    private class Mode(val name: String, val f: (String) -> String)

    private val modes = listOf(
        Mode(tr("বড় হাতের (UPPER)")) { it.uppercase() },
        Mode(tr("ছোট হাতের (lower)")) { it.lowercase() },
        Mode(tr("প্রতি শব্দের প্রথম বড় (Title)")) { titleCase(it) },
        Mode(tr("বাক্যের প্রথম বড় (Sentence)")) { sentenceCase(it) },
        Mode("camelCase") { s -> wordsOf(s).mapIndexed { i, w -> if (i == 0) w.lowercase() else cap(w) }.joinToString("") },
        Mode("PascalCase") { s -> wordsOf(s).joinToString("") { cap(it) } },
        Mode("snake_case") { s -> wordsOf(s).joinToString("_") { it.lowercase() } },
        Mode("kebab-case") { s -> wordsOf(s).joinToString("-") { it.lowercase() } },
        Mode(tr("বাড়তি স্পেস মুছুন")) { s -> s.lines().joinToString("\n") { it.trim().replace(Regex("[ \\t]+"), " ") } },
        Mode(tr("লাইন সাজান (A→Z)")) { s -> s.lines().sorted().joinToString("\n") },
        Mode(tr("লাইনের ক্রম উল্টো")) { s -> s.lines().reversed().joinToString("\n") }
    )

    private var selected = 0
    private lateinit var input: EditText
    private lateinit var output: TextView
    private lateinit var flow: FlowLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val inCard = card(14)
        inCard.addView(label(tr("আপনার লেখা"), 12f, Ui.MUTED, true))
        input = EditText(this)
        input.hint = tr("এখানে লিখুন বা পেস্ট করুন")
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.minLines = 4
        input.gravity = Gravity.TOP or Gravity.START
        input.onChange { update() }
        inCard.addView(input, lp(t = 4))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(pill(tr("মুছুন"), Ui.BG, Ui.RED) { input.setText("") }, lp(r = 8))
        inCard.addView(row, lp(t = 6))
        box.addView(inCard, lp(b = 12))

        box.addView(label(tr("কী করতে চান"), 14f, Ui.MUTED, true), lp(l = 4, b = 8))
        flow = FlowLayout(this)
        box.addView(flow, lp(b = 12))

        val outCard = card(14)
        outCard.addView(label(tr("ফলাফল"), 12f, Ui.MUTED, true))
        output = label("", 16f, Ui.TEXT)
        output.setTextIsSelectable(true)
        outCard.addView(output, lp(t = 4, b = 10))
        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.addView(pill(tr("কপি")) {
            if (output.text.isNotEmpty()) copyText(output.text.toString()) else Toast.makeText(this, tr("আগে কিছু লিখুন"), Toast.LENGTH_SHORT).show()
        }, lp(r = 8))
        btns.addView(pill(tr("ইনপুটে বসান"), Ui.BG, Ui.PRIMARY) {
            input.setText(output.text)
            input.setSelection(input.text.length)
        })
        outCard.addView(btns, lp())
        box.addView(outCard, lp())

        setScreen(tr("টেক্সট কেস কনভার্টার"), scroll)
        buildChips()
        update()
    }

    private fun buildChips() {
        flow.removeAllViews()
        for (i in modes.indices) {
            val on = i == selected
            val chip = label(modes[i].name, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(14), dp(9), dp(14), dp(9))
            chip.setOnClickListener {
                selected = i
                buildChips()
                update()
            }
            flow.addView(chip)
        }
    }

    private fun update() {
        output.text = modes[selected].f(input.text.toString())
    }
}

private fun cap(w: String): String = if (w.isEmpty()) w else w.substring(0, 1).uppercase() + w.substring(1).lowercase()

/** Splits on anything that is not a letter, mark or digit, and also between aB in camelCase input. */
private fun wordsOf(s: String): List<String> =
    s.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
        .split(Regex("[^\\p{L}\\p{M}\\p{N}]+"))
        .filter { it.isNotEmpty() }

private fun titleCase(s: String): String {
    val sb = StringBuilder()
    var start = true
    for (ch in s) {
        if (ch.isWhitespace()) {
            start = true
            sb.append(ch)
        } else {
            sb.append(if (start) ch.uppercaseChar() else ch.lowercaseChar())
            start = false
        }
    }
    return sb.toString()
}

private fun sentenceCase(s: String): String {
    val sb = StringBuilder()
    var start = true
    for (ch in s.lowercase()) {
        if (start && ch.isLetter()) {
            sb.append(ch.uppercaseChar())
            start = false
        } else {
            sb.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n' || ch == '।') {
                start = true
            } else if (ch.isLetterOrDigit()) {
                start = false
            }
        }
    }
    return sb.toString()
}
