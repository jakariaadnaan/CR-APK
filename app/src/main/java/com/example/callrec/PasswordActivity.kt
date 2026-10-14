package com.example.callrec

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.PersistableBundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.security.SecureRandom

/** পাসওয়ার্ড জেনারেটর: শক্তিশালী পাসওয়ার্ড তৈরি ও এক ট্যাপে কপি। */
class PasswordActivity : Activity() {

    private val rnd = SecureRandom()
    private var password = ""
    private var length = 16

    private lateinit var out: TextView
    private lateinit var strength: TextView
    private lateinit var bar: View
    private lateinit var lenLabel: TextView
    private lateinit var upper: CheckBox
    private lateinit var lower: CheckBox
    private lateinit var digits: CheckBox
    private lateinit var symbols: CheckBox
    private lateinit var clear: CheckBox

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val top = card(16)
        out = label("", 22f, Ui.TEXT, true)
        out.typeface = Typeface.MONOSPACE
        out.gravity = Gravity.CENTER
        out.setTextIsSelectable(true)
        out.setPadding(0, dp(10), 0, dp(10))
        top.addView(out, lp())

        bar = View(this)
        top.addView(bar, lp(height = 6, t = 6))
        strength = label("", 13f, Ui.MUTED)
        strength.gravity = Gravity.CENTER
        top.addView(strength, lp(t = 6, b = 12))

        val btns = LinearLayout(this)
        btns.orientation = LinearLayout.HORIZONTAL
        btns.addView(pill("নতুন তৈরি করুন") { generate() }, lp(width = 0, r = 6).also { it.weight = 1f })
        btns.addView(pill("কপি", Ui.BG, Ui.PRIMARY) { copySecret() }, lp(width = 0, l = 6).also { it.weight = 1f })
        top.addView(btns, lp())
        box.addView(top, lp(b = 12))

        val opts = card(16)
        lenLabel = label("", 14f, Ui.TEXT, true)
        opts.addView(lenLabel)
        val seek = SeekBar(this)
        seek.max = 58 // 6..64
        seek.progress = length - 6
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                length = p + 6
                if (fromUser) generate() else updateLenLabel()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        opts.addView(seek, lp(t = 4, b = 8))

        upper = check("বড় হাতের অক্ষর (A-Z)", true)
        lower = check("ছোট হাতের অক্ষর (a-z)", true)
        digits = check("সংখ্যা (0-9)", true)
        symbols = check("চিহ্ন (!@#$…)", true)
        clear = check("দেখতে এক রকম অক্ষর বাদ (O, 0, l, 1, I)", false)
        for (c in listOf(upper, lower, digits, symbols, clear)) opts.addView(c)
        box.addView(opts, lp(b = 12))

        val note = label(
            "পাসওয়ার্ড শুধু এই ফোনেই তৈরি হয়, কোথাও সংরক্ষিত বা পাঠানো হয় না। কপি করা পাসওয়ার্ড ক্লিপবোর্ডে থাকে, তাই ব্যবহারের পর অন্য কিছু কপি করে নিন।",
            12f, Ui.MUTED
        )
        box.addView(note, lp(l = 4))

        setScreen("পাসওয়ার্ড জেনারেটর", scroll)
        generate()
    }

    private fun check(text: String, on: Boolean): CheckBox {
        val c = CheckBox(this)
        c.text = text
        c.isChecked = on
        c.setOnCheckedChangeListener { _, _ -> generate() }
        return c
    }

    private fun updateLenLabel() {
        lenLabel.text = "দৈর্ঘ্য: " + bn(length.toString()) + " অক্ষর"
    }

    private fun generate() {
        updateLenLabel()
        val sets = ArrayList<String>()
        val skip = if (clear.isChecked) "O0lI1|" else ""
        fun add(chars: String) {
            val f = chars.filter { it !in skip }
            if (f.isNotEmpty()) sets.add(f)
        }
        if (upper.isChecked) add("ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        if (lower.isChecked) add("abcdefghijklmnopqrstuvwxyz")
        if (digits.isChecked) add("0123456789")
        if (symbols.isChecked) add("!@#\$%^&*()-_=+[]{};:,.?/")
        if (sets.isEmpty()) {
            Toast.makeText(this, "অন্তত একটি ধরন বেছে নিন", Toast.LENGTH_SHORT).show()
            lower.setOnCheckedChangeListener(null)
            lower.isChecked = true
            lower.setOnCheckedChangeListener { _, _ -> generate() }
            add("abcdefghijklmnopqrstuvwxyz")
        }
        val pool = sets.joinToString("")
        val chars = ArrayList<Char>()
        for (s in sets) chars.add(s[rnd.nextInt(s.length)]) // at least one of each chosen kind
        while (chars.size < length) chars.add(pool[rnd.nextInt(pool.length)])
        for (i in chars.size - 1 downTo 1) { // Fisher-Yates shuffle
            val j = rnd.nextInt(i + 1)
            val t = chars[i]
            chars[i] = chars[j]
            chars[j] = t
        }
        password = chars.joinToString("")
        out.text = password

        val bits = length * (Math.log(pool.length.toDouble()) / Math.log(2.0))
        val lbl: String
        val color: Int
        val frac: Float
        when {
            bits < 45 -> {
                lbl = "দুর্বল"; color = Ui.RED; frac = 0.25f
            }
            bits < 70 -> {
                lbl = "মোটামুটি"; color = android.graphics.Color.parseColor("#E08E0B"); frac = 0.5f
            }
            bits < 100 -> {
                lbl = "শক্তিশালী"; color = Ui.GREEN; frac = 0.78f
            }
            else -> {
                lbl = "অনেক শক্তিশালী"; color = Ui.PRIMARY; frac = 1f
            }
        }
        strength.text = lbl + " (প্রায় " + bn(bits.toInt().toString()) + " বিট)"
        strength.setTextColor(color)
        bar.background = bg(color, 3)
        val p = bar.layoutParams as LinearLayout.LayoutParams
        // the bar is as wide as the strength fraction of its parent card content
        val parentWidth = (bar.parent as View).width - (bar.parent as View).paddingLeft - (bar.parent as View).paddingRight
        p.width = if (parentWidth > 0) (parentWidth * frac).toInt() else (resources.displayMetrics.widthPixels * 0.7f * frac).toInt()
        bar.layoutParams = p
    }

    private fun copySecret() {
        if (password.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", password)
        val extras = PersistableBundle()
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
        clip.description.extras = extras
        cm.setPrimaryClip(clip)
        Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }
}
