package com.example.callrec

import android.app.Activity
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.math.BigDecimal
import java.math.RoundingMode

/** টাকা কথায়: চেক, রশিদ বা ভাউচারের জন্য অঙ্ককে বাংলা কথায় লেখা। */
class WordsActivity : GuardedActivity() {

    private var words = ""

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val c = card(16)
        c.addView(label(tr("টাকার অঙ্ক"), 13f, Ui.MUTED))
        val input = EditText(this)
        input.hint = tr("যেমন: 12500 বা 12500.50")
        input.textSize = 24f
        input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        c.addView(input, lp())
        val shown: TextView = label("", 15f, Ui.MUTED)
        c.addView(shown, lp(t = 4))
        box.addView(c, lp(b = 12))

        val out = card(16)
        out.addView(label(tr("কথায়"), 13f, Ui.MUTED))
        val result = label("", 20f, Ui.PRIMARY, true)
        result.setLineSpacing(0f, 1.2f)
        out.addView(result, lp(t = 6, b = 12))
        out.addView(pill(tr("কপি করুন")) { if (words.isNotEmpty()) copyText(words) }, lp())
        box.addView(out, lp(b = 12))

        val note = label(
            tr("চেক বা আইনি কাগজে ব্যবহারের আগে বানান ও অঙ্ক একবার মিলিয়ে নিন।"),
            12f, Ui.MUTED
        )
        note.gravity = Gravity.START
        box.addView(note, lp(l = 4))

        input.onChange {
            val txt = asciiDigits(input.text.toString()).replace(",", "").trim()
            words = ""
            shown.text = ""
            result.text = ""
            if (txt.isNotEmpty()) {
                val d = try {
                    BigDecimal(txt)
                } catch (e: Exception) {
                    null
                }
                if (d == null || d.signum() < 0) {
                    result.text = tr("সঠিক অঙ্ক লিখুন")
                } else if (d > BigDecimal("999999999999.99")) {
                    result.text = tr("সংখ্যাটি অনেক বড়")
                } else {
                    val paisa = d.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
                    words = BanglaWords.amount(paisa)
                    shown.text = taka(paisa / 100.0)
                    result.text = words
                }
            }
        }

        setScreen(tr("টাকা কথায়"), scroll)
    }
}
