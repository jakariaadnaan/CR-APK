package com.example.callrec

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.icu.text.BreakIterator

/** শব্দ ও অক্ষর গণনা: live counts while you type or paste. */
class CounterActivity : GuardedActivity() {

    private lateinit var input: EditText
    private val values = ArrayList<TextView>()

    private val names = listOf(
        tr("শব্দ"), tr("অক্ষর (স্পেসসহ)"), tr("অক্ষর (স্পেস ছাড়া)"),
        tr("লাইন"), tr("অনুচ্ছেদ"), tr("বাক্য"), tr("পড়তে সময় (আনুমানিক)")
    )

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
        input.minLines = 6
        input.gravity = Gravity.TOP or Gravity.START
        input.onChange { update() }
        inCard.addView(input, lp(t = 4))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(pill(tr("পেস্ট"), Ui.BG, Ui.PRIMARY) { paste() }, lp(r = 8))
        row.addView(pill(tr("মুছুন"), Ui.BG, Ui.RED) { input.setText("") })
        inCard.addView(row, lp(t = 6))
        box.addView(inCard, lp(b = 12))

        val stats = card(14)
        for (n in names) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.addView(label(n, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val v = label(tr("০"), 16f, Ui.PRIMARY, true)
            values.add(v)
            r.addView(v)
            stats.addView(r, lp(t = 8))
        }
        box.addView(stats, lp(b = 8))
        box.addView(
            label(tr("অক্ষর গণনায় যুক্তাক্ষর ও কার-চিহ্নসহ অক্ষরকে একটি ধরা হয়।"), 12f, Ui.MUTED),
            lp(l = 4)
        )

        setScreen(tr("শব্দ ও অক্ষর গণনা"), scroll)
        update()
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (t.isNullOrEmpty()) {
            Toast.makeText(this, tr("ক্লিপবোর্ডে কিছু নেই"), Toast.LENGTH_SHORT).show()
        } else {
            input.setText(t)
        }
    }

    private fun graphemes(s: String): Int {
        if (s.isEmpty()) return 0
        val it = BreakIterator.getCharacterInstance()
        it.setText(s)
        var n = 0
        while (it.next() != BreakIterator.DONE) n++
        return n
    }

    private fun update() {
        val s = input.text.toString()
        val words = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
        val chars = graphemes(s)
        val noSpace = graphemes(s.replace(Regex("\\s"), ""))
        val lines = if (s.isEmpty()) 0 else s.lines().size
        val paras = s.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }.size
        val sentences = s.split(Regex("[.!?।]+")).filter { it.isNotBlank() }.size
        val seconds = Math.round(words / 200.0 * 60).toInt()
        val read = when {
            words == 0 -> tr("০")
            seconds < 60 -> bn(seconds.toString()) + tr(" সেকেন্ড")
            else -> bn((seconds / 60).toString()) + tr(" মিনিট ") + bn((seconds % 60).toString()) + tr(" সেকেন্ড")
        }
        val nums = listOf(words, chars, noSpace, lines, paras, sentences)
        for (i in nums.indices) values[i].text = bn(nums[i].toString())
        values[6].text = read
    }
}
