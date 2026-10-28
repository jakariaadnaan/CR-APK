package com.example.callrec

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
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
    private var copyAll = ""

    private lateinit var tabs: LinearLayout
    private lateinit var input: EditText
    private lateinit var results: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        box.addView(tabs, lp(b = 12))

        input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.minLines = 2
        input.gravity = Gravity.TOP or Gravity.START
        input.onChange { update() }
        styleInput(input)
        box.addView(captionLabel(tr("ইনপুট")))
        box.addView(input, lp(b = 12))

        results = LinearLayout(this)
        results.orientation = LinearLayout.VERTICAL
        box.addView(results, lp())

        val w = LinearLayout(this)
        w.addView(primaryButton(tr("কোড পয়েন্ট কপি")) { if (copyAll.isNotEmpty()) copyText(copyAll) }, lp(height = dp(48)))
        setScreen(tr("ইউনিকোড কনভার্টার"), scroll, null, true, bottomBar(w))
        buildTabs()
        update()
    }

    private fun buildTabs() {
        tabs.removeAllViews()
        val names = listOf(tr("টেক্সট → কোড"), tr("কোড → টেক্সট"))
        for (i in names.indices) {
            tabs.addView(choiceChip(names[i], (i == 1) == decode) {
                decode = i == 1
                buildTabs()
                update()
            }, lp(r = 8))
        }
        input.hint = if (decode) tr("যেমন: U+0985 U+09AE  বা  \\u0985  বা  &#2437;  বা  %E0%A6%85") else tr("এখানে লিখুন, যেমন: অমর")
                update()
            }
            tabs.addView(chip, lp(r = 8))
        }
        input.hint = if (decode) tr("যেমন: U+0985 U+09AE  বা  \\u0985  বা  &#2437;  বা  %E0%A6%85") else tr("এখানে লিখুন, যেমন: অমর")
    }

    private fun hex(n: Int, width: Int): String = String.format(Locale.US, "%0" + width + "X", n)

    private fun update() {
        results.removeAllViews()
        val s = input.text.toString()
        copyAll = ""
        if (s.isEmpty()) return
        if (decode) {
            copyAll = decodeText(s)
            addResult(tr("টেক্সট"), copyAll)
            return
        }
        val cps = ArrayList<Int>()
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            cps.add(cp)
            i += Character.charCount(cp)
        }
        copyAll = cps.joinToString(" ") { "U+" + hex(it, 4) }
        results.addView(sectionLabel(tr("কোড পয়েন্ট আউটপুট"), 0))
        val list = card(8)
        for ((k, cp) in cps.take(100).withIndex()) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(4), dp(8), dp(4), dp(8))
            val g = label(String(Character.toChars(cp)), 18f, Ui.PRIMARY, true)
            g.gravity = Gravity.CENTER
            g.background = bg(Ui.KEY_OP, 8, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.6f), 1)
            r.addView(g, lp(width = dp(44), height = dp(40), r = 14))
            r.addView(label("U+" + hex(cp, 4), 15f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val c = label(tr("কপি"), 12f, Ui.MUTED)
            c.setPadding(dp(10), dp(6), dp(4), dp(6))
            c.setOnClickListener { copyText("U+" + hex(cp, 4)) }
            r.addView(c)
            list.addView(r)
            if (k < Math.min(cps.size, 100) - 1) list.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1))
        }
        results.addView(list, lp(b = 10))
        results.addView(resultBox("UTF-8 " + tr("বাইট"), s.toByteArray(Charsets.UTF_8).joinToString(" ") { hex(it.toInt() and 0xFF, 2) }, "", 15f), lp(b = 10))
        addResult(tr("\\uXXXX (জাভা / জাভাস্ক্রিপ্ট)"), s.map { "\\u" + hex(it.code, 4) }.joinToString(""))
        addResult("HTML (&#...;)", cps.joinToString("") { "&#" + it + ";" })
        addResult(tr("URL এনকোড"), URLEncoder.encode(s, "UTF-8"))
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
