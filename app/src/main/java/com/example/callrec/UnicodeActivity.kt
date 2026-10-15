package com.example.callrec

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/** ইউনিকোড কনভার্টার: text to code points, UTF-8 bytes, \uXXXX, HTML and URL forms, and back. */
class UnicodeActivity : GuardedActivity() {

    private var decode = false

    private lateinit var tabs: LinearLayout
    private lateinit var input: EditText
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        box.addView(tabs, lp(b = 12))

        val inCard = card(14)
        inCard.addView(label("ইনপুট", 12f, Ui.MUTED, true))
        input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.minLines = 3
        input.gravity = Gravity.TOP or Gravity.START
        input.onChange { update() }
        inCard.addView(input, lp(t = 4))
        box.addView(inCard, lp(b = 12))

        results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        box.addView(results, lp())

        setScreen("ইউনিকোড কনভার্টার", scroll)
        buildTabs()
        update()
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        val names = listOf("টেক্সট → কোড", "কোড → টেক্সট")
        for (i in names.indices) {
            val on = (i == 1) == decode
            val chip = label(names[i], 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
            chip.gravity = Gravity.CENTER
            chip.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
            chip.setPadding(dp(18), dp(8), dp(18), dp(8))
            chip.setOnClickListener {
                decode = i == 1
                buildTabs()
                input.hint = if (decode) "যেমন: U+0985 U+09AE  বা  \\u0985  বা  &#2437;  বা  %E0%A6%85" else "এখানে লিখুন, যেমন: অমর"
                update()
            }
            tabs.addView(chip, lp(r = 8))
        }
        input.hint = if (decode) "যেমন: U+0985 U+09AE  বা  \\u0985  বা  &#2437;  বা  %E0%A6%85" else "এখানে লিখুন, যেমন: অমর"
    }

    private fun hex(n: Int, width: Int): String = String.format(Locale.US, "%0" + width + "X", n)

    private fun update() {
        results.removeAllViews()
        val s = input.text.toString()
        if (s.isEmpty()) return
        if (decode) {
            addResult("টেক্সট", decodeText(s))
            return
        }
        val cps = ArrayList<Int>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        addResult("কোড পয়েন্ট", cps.joinToString(" ") { "U+" + hex(it, 4) })
        addResult("UTF-8 (হেক্স বাইট)", s.toByteArray(Charsets.UTF_8).joinToString(" ") { hex(it.toInt() and 0xFF, 2) })
        addResult("\\uXXXX (জাভা / জাভাস্ক্রিপ্ট)", s.map { "\\u" + hex(it.code, 4) }.joinToString(""))
        addResult("HTML (&#...;)", cps.joinToString("") { "&#" + it + ";" })
        addResult("URL এনকোড", URLEncoder.encode(s, "UTF-8"))
    }

    private fun addResult(title: String, value: String) {
        val r: Pair<LinearLayout, TextView> = resultCard(title)
        r.second.text = value
        results.addView(r.first, lp(b = 8))
    }

    private fun decodeText(src: String): String {
        var r = src
        r = Regex("U\\+([0-9A-Fa-f]{4,6})\\s*").replace(r) { m ->
            val cp = m.groupValues[1].toInt(16)
            if (cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        r = Regex("\\\\u([0-9A-Fa-f]{4})").replace(r) { m -> m.groupValues[1].toInt(16).toChar().toString() }
        r = Regex("&#[xX]([0-9A-Fa-f]+);").replace(r) { m ->
            val cp = m.groupValues[1].toIntOrNull(16)
            if (cp != null && cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        r = Regex("&#(\\d+);").replace(r) { m ->
            val cp = m.groupValues[1].toIntOrNull()
            if (cp != null && cp in 0..0x10FFFF) String(Character.toChars(cp)) else m.value
        }
        if (Regex("%[0-9A-Fa-f]{2}").containsMatchIn(r)) {
            r = try {
                URLDecoder.decode(r, "UTF-8")
            } catch (e: Exception) {
                r
            }
        }
        return r
    }
}
