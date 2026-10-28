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
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        input = EditText(this)
        input.hint = tr("এখানে লিখুন বা পেস্ট করুন")
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.minLines = 4
        input.gravity = Gravity.TOP or Gravity.START
        input.onChange { update() }
        box.addView(sectionLabel(tr("আপনার লেখা"), 0))
        styleInput(input)
        box.addView(input, lp(b = 4))

        box.addView(captionLabel(tr("রূপান্তর")).also { it.layoutParams = lp(t = 12, b = 8, l = 2) })
        flow = FlowLayout(this)
        box.addView(flow, lp(b = 12))

        box.addView(sectionLabel(tr("ফলাফলের প্রিভিউ"), 2))
        val res = resultBox(tr("ফলাফল"), "", "", 16f)
        output = res.getChildAt(1) as TextView
        output.setTextIsSelectable(true)
        output.textSize = 15f
        output.setTypeface(null, android.graphics.Typeface.NORMAL)
        countV = TextView(this)
        countV.setTextColor(Ui.MUTED)
        countV.textSize = 11f
        res.addView(countV, lp(t = 10))
        box.addView(res, lp(b = 10))

        box.addView(outlineButton(tr("ফলাফল কপি")) {
            if (output.text.isNotEmpty()) copyText(output.text.toString()) else Toast.makeText(this, tr("আগে কিছু লিখুন"), Toast.LENGTH_SHORT).show()
        }, lp(height = dp(48)))
        val clr = label(tr("ইনপুট মুছুন"), 13f, Ui.MUTED)
        clr.gravity = Gravity.CENTER
        clr.setPadding(0, dp(14), 0, dp(6))
        clr.setOnClickListener { input.setText("") }
        box.addView(clr)
        box.addView(label(tr("আগের ফলাফলকে ইনপুটে বসাতে চাইলে নিচের লিংক:"), 11f, Ui.MUTED).also { it.gravity = Gravity.CENTER }, lp(t = 4))
        val use = label(tr("ইনপুটে বসান"), 13f, Ui.PRIMARY, true)
        use.gravity = Gravity.CENTER
        use.setPadding(0, dp(8), 0, dp(8))
        use.setOnClickListener { input.setText(output.text); input.setSelection(input.text.length) }
        box.addView(use)

        setScreen(tr("টেক্সট কেস কনভার্টার"), scroll)
        buildChips()
        update()
    }

    private lateinit var countV: TextView

    private fun buildChips() {
        flow.removeAllViews()
        for (i in modes.indices) {
            flow.addView(choiceChip(modes[i].name, i == selected) { selected = i; buildChips(); update() })
        }
    }

    private fun update() {
        output.text = modes[selected].f(input.text.toString())
        countV.text = bn(output.text.length.toString()) + tr(" অক্ষর")
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
