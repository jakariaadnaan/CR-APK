package com.example.callrec

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.icu.text.BreakIterator

/** শব্দ ও অক্ষর গণনা: live counts while you type or paste. */
class CounterActivity : GuardedActivity() {

    private lateinit var input: EditText

    private val names = listOf(
        tr("শব্দ"), tr("অক্ষর (স্পেসসহ)"), tr("অক্ষর (স্পেস ছাড়া)"),
        tr("লাইন"), tr("অনুচ্ছেদ"), tr("বাক্য"), tr("পড়তে সময় (আনুমানিক)")
    )

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
        input.minLines = 5
        input.gravity = Gravity.TOP or Gravity.START
        input.onChange { update() }
        styleInput(input)
        box.addView(input, lp(b = 6))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(pill(tr("পেস্ট"), Ui.KEY_OP, Ui.PRIMARY) { paste() }, lp(r = 8))
        row.addView(pill(tr("মুছুন"), Ui.CARD, Ui.MUTED) { input.setText("") })
        box.addView(row, lp(b = 4))

        box.addView(sectionLabel(tr("লাইভ পরিসংখ্যান")))
        val tilesNames = listOf(0 to tr("শব্দ"), 1 to tr("অক্ষর"), 5 to tr("বাক্য"), 6 to tr("পড়ার সময়"))
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        var rowL: LinearLayout? = null
        for ((k, t) in tilesNames.withIndex()) {
            if (k % 2 == 0) {
                rowL = LinearLayout(this)
                rowL.orientation = LinearLayout.HORIZONTAL
                grid.addView(rowL, lp(b = 8))
            }
            val tile = if (k == 0) resultBox(t.second, "০", "", 22f) else statTile(t.second, "০")
            val v = tile.getChildAt(1) as TextView
            if (k != 0) { v.textSize = 22f }
            tileViews[t.first] = v
            rowL!!.addView(tile, lp(width = 0, l = if (k % 2 == 1) 4 else 0, r = if (k % 2 == 0) 4 else 0).also { it.weight = 1f })
        }
        box.addView(grid)

        box.addView(sectionLabel(tr("আরও গণনা"), 4))
        val stats = card(8)
        val extra = listOf(2 to tr("অক্ষর (স্পেস ছাড়া)"), 3 to tr("লাইন"), 4 to tr("অনুচ্ছেদ"), 7 to tr("আলাদা শব্দ"), 8 to tr("স্পেস"))
        for ((i, n) in extra.withIndex()) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.VERTICAL
            val line = LinearLayout(this)
            line.setPadding(dp(4), dp(11), dp(4), dp(11))
            line.addView(label(n.second, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val v = label("০", 14f, Ui.TEXT, true)
            tileViews[n.first] = v
            line.addView(v)
            r.addView(line)
            if (i < extra.size - 1) r.addView(View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1))
            stats.addView(r)
        }
        box.addView(stats, lp(b = 8))
        box.addView(
            label(tr("অক্ষর গণনায় যুক্তাক্ষর ও কার-চিহ্নসহ অক্ষরকে একটি ধরা হয়। স্পেস ও লাইন-ব্রেক \"অক্ষর\"-এ গোনা হয়, \"স্পেস ছাড়া\"-তে নয়।"), 11f, Ui.MUTED),
            lp(l = 4)
        )

        val w = LinearLayout(this)
        w.addView(primaryButton(tr("লেখা কপি")) { if (input.text.isNotEmpty()) copyText(input.text.toString()) else Toast.makeText(this, tr("আগে কিছু লিখুন"), Toast.LENGTH_SHORT).show() }, lp(height = dp(48)))
        setScreen(tr("শব্দ ও অক্ষর গণনা"), scroll, null, true, bottomBar(w))
        update()
    }

    private val tileViews = HashMap<Int, TextView>()

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
        for (i in nums.indices) tileViews[i]?.text = bn(nums[i].toString())
        tileViews[6]?.text = read
        val unique = s.lowercase().split(Regex("[^\\p{L}\\p{M}\\p{N}]+")).filter { it.isNotEmpty() }.toSet().size
        tileViews[7]?.text = bn(unique.toString())
        tileViews[8]?.text = bn(s.count { it == ' ' }.toString())
    }
}
