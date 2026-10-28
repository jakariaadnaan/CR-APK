package com.memoredia.app

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
class PasswordActivity : GuardedActivity() {

    private val rnd = SecureRandom()
    private var password = ""
    private var length = 16

    private lateinit var out: TextView
    private lateinit var strength: TextView
    private lateinit var bars: LinearLayout
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
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(captionLabel(tr("আপনার পাসওয়ার্ড")))
        val top = card(16)
        val pr = LinearLayout(this)
        pr.gravity = Gravity.CENTER_VERTICAL
        out = label("", 20f, Ui.TEXT, true)
        out.setTextIsSelectable(true)
        out.setPadding(0, dp(8), 0, dp(8))
        pr.addView(out, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val cp = label("⧉", 18f, Ui.PRIMARY, true)
        cp.gravity = Gravity.CENTER
        cp.background = bg(Ui.KEY_OP, 20, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.5f), 1)
        cp.setOnClickListener { copySecret() }
        pr.addView(cp, lp(width = dp(40), height = dp(40), l = 8))
        top.addView(pr, lp())
        top.addView(label(tr("কপি করা পাসওয়ার্ড সংরক্ষিত হয় না"), 11f, Ui.MUTED), lp(t = 4))
        box.addView(top, lp(b = 14))

        val sh = LinearLayout(this)
        sh.gravity = Gravity.CENTER_VERTICAL
        sh.addView(captionLabel(tr("পাসওয়ার্ডের শক্তি")).also { it.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        strength = label("", 12f, Ui.PRIMARY, true)
        sh.addView(strength)
        box.addView(sh)
        bars = LinearLayout(this)
        for (i in 0..3) bars.addView(View(this), LinearLayout.LayoutParams(0, dp(6), 1f).also { it.rightMargin = if (i < 3) dp(6) else 0 })
        box.addView(bars, lp(t = 6, b = 14))

        val opts = card(16)
        val lh = LinearLayout(this)
        lh.gravity = Gravity.CENTER_VERTICAL
        lh.addView(label(tr("দৈর্ঘ্য"), 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        lenLabel = label("", 12f, Ui.MUTED)
        lh.addView(lenLabel)
        opts.addView(lh)
        val seek = SeekBar(this)
        seek.max = 58 // 6..64
        seek.progress = length - 6
        seek.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        seek.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        seek.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Ui.LINE)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                length = p + 6
                if (fromUser) generate() else updateLenLabel()
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        opts.addView(seek, lp(t = 4, b = 8))
        opts.addView(label(tr("অক্ষরের ধরন"), 14f, Ui.TEXT, true), lp(t = 6, b = 2))

        upper = check(tr("বড় হাতের অক্ষর (A-Z)"), true)
        lower = check(tr("ছোট হাতের অক্ষর (a-z)"), true)
        digits = check(tr("সংখ্যা (0-9)"), true)
        symbols = check(tr("চিহ্ন (!@#$…)"), true)
        clear = check(tr("দেখতে এক রকম অক্ষর বাদ (O, 0, l, 1, I)"), false)
        for (c in listOf(upper, lower, digits, symbols, clear)) opts.addView(c)
        box.addView(opts, lp(b = 12))

        val note = label(
            tr("পাসওয়ার্ড শুধু এই ফোনেই তৈরি হয়, কোথাও সংরক্ষিত বা পাঠানো হয় না। কপি করা পাসওয়ার্ড ক্লিপবোর্ডে থাকে, তাই ব্যবহারের পর অন্য কিছু কপি করে নিন।"),
            12f, Ui.MUTED
        )
        box.addView(note, lp(l = 4))

        setScreen(tr("পাসওয়ার্ড জেনারেটর"), scroll, null, true, bottomBar(LinearLayout(this).also { it.addView(primaryButton(tr("পাসওয়ার্ড তৈরি করুন")) { generate() }, lp(height = dp(48))) }))
        generate()
    }

    private fun check(text: String, on: Boolean): CheckBox {
        val c = CheckBox(this)
        c.buttonTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        c.setTextColor(Ui.TEXT)
        c.textSize = 14f
        c.text = text
        c.isChecked = on
        c.setOnCheckedChangeListener { _, _ -> generate() }
        return c
    }

    private fun updateLenLabel() {
        lenLabel.text = bn(length.toString()) + tr(" অক্ষর")
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
            Toast.makeText(this, tr("অন্তত একটি ধরন বেছে নিন"), Toast.LENGTH_SHORT).show()
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
        val n: Int
        when {
            bits < 45 -> { lbl = tr("দুর্বল"); color = Ui.RED; n = 1 }
            bits < 70 -> { lbl = tr("মোটামুটি"); color = Ui.PRIMARY; n = 2 }
            bits < 100 -> { lbl = tr("শক্তিশালী"); color = Ui.PRIMARY; n = 3 }
            else -> { lbl = tr("অনেক শক্তিশালী"); color = Ui.PRIMARY; n = 4 }
        }
        strength.text = lbl
        strength.setTextColor(color)
        for (i in 0..3) bars.getChildAt(i).background = bg(if (i < n) color else Ui.LINE, 3)
    }

    private fun copySecret() {
        if (password.isEmpty()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("password", password)
        val extras = PersistableBundle()
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
        clip.description.extras = extras
        cm.setPrimaryClip(clip)
        Toast.makeText(this, tr("কপি হয়েছে"), Toast.LENGTH_SHORT).show()
    }
}
