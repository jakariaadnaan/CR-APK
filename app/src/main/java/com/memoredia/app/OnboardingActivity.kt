package com.memoredia.app

import android.graphics.Color
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Three intro screens shown once, the first time the app opens. */
class OnboardingActivity : GuardedActivity() {

    private var page = 0
    private lateinit var holder: FrameLayout
    private lateinit var detector: GestureDetector

    private val titles = listOf("আপনার সব দরকারি টুল, এক জায়গায়", "হিসাব রাখুন, ছবি-PDF সামলান", "আপনার ডেটা, শুধু আপনার ফোনে")
    private val subs = listOf(
        "খরচের খাতা, বাকির খাতা, ক্যালকুলেটর, নোট আর আরও অনেক কিছু, সবই এক অ্যাপে।",
        "আয়-ব্যয় আর বাকির হিসাব রাখুন। ছবি ছোট করুন, PDF জোড়া দিন বা ভাগ করুন, QR কোড বানান। বেশিরভাগ টুল ইন্টারনেট ছাড়াই চলে।",
        "সব তথ্য এই ফোনেই থাকে। পিন বা ফিঙ্গারপ্রিন্ট দিয়ে লক করুন, ব্যাকআপ রাখুন, আর পছন্দমতো ভাষা ও রঙ বেছে নিন।"
    )
    private val buttons = listOf("Get Started", "Next", "You are ready to go")

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.BG
        window.decorView.systemUiVisibility = if (Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        page = savedInstanceState?.getInt("page") ?: 0
        holder = FrameLayout(this)
        holder.setBackgroundColor(Ui.BG)
        setContentView(holder)
        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                if (Math.abs(dx) > dp(80) && Math.abs(dx) > Math.abs(e2.y - e1.y)) {
                    if (dx < 0 && page < 2) show(page + 1) else if (dx > 0 && page > 0) show(page - 1)
                    return true
                }
                return false
            }
        })
        show(page)
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("page", page)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        detector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun finishIntro() {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
        startActivity(android.content.Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun show(p: Int) {
        page = p
        holder.removeAllViews()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(28), dp(24), dp(28), dp(28))

        col.addView(illustration(p), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val title = label(tr(titles[p]), 28f, Ui.TEXT, true)
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(tr(subs[p]), 15.5f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        sub.setLineSpacing(0f, 1.15f)
        col.addView(sub, lp(t = 12))

        val dots = LinearLayout(this)
        dots.gravity = Gravity.CENTER
        for (i in 0..2) {
            val d = View(this)
            d.background = bg(if (i == p) Ui.PRIMARY else Ui.GREY, 4)
            dots.addView(d, LinearLayout.LayoutParams(dp(if (i == p) 22 else 8), dp(8)).also { it.setMargins(dp(4), 0, dp(4), 0) })
        }
        col.addView(dots, lp(t = 24))

        val btn = TextView(this)
        btn.text = buttons[p]
        btn.textSize = 17f
        btn.setTextColor(Color.WHITE)
        btn.typeface = android.graphics.Typeface.DEFAULT_BOLD
        btn.gravity = Gravity.CENTER
        btn.background = bg(Ui.PRIMARY, 30)
        btn.setOnClickListener { if (p < 2) show(p + 1) else finishIntro() }
        col.addView(btn, lp(height = dp(56), t = 24))

        holder.addView(col, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        col.alpha = 0f
        col.animate().alpha(1f).setDuration(220).start()
    }

    private fun tile(icon: Int, size: Int = 84, iconSize: Int = 34, filled: Boolean = false): LinearLayout {
        val t = LinearLayout(this)
        t.gravity = Gravity.CENTER
        t.background = if (filled) bg(Ui.PRIMARY, 24) else bg(Ui.CARD, 24, Ui.LINE, 1)
        t.elevation = dp(2).toFloat()
        t.addView(iconView(icon, iconSize, if (filled) Color.WHITE else Ui.PRIMARY))
        t.layoutParams = FrameLayout.LayoutParams(dp(size), dp(size))
        return t
    }

    private fun illustration(p: Int): View {
        val outer = FrameLayout(this)
        val stage = FrameLayout(this)
        val size = dp(270)
        val soft = Ui.mix(Ui.PRIMARY, Ui.BG, 0.88f)
        val circle = View(this)
        circle.background = bg(soft, 135)
        stage.addView(circle, FrameLayout.LayoutParams(size, size, Gravity.CENTER))

        fun place(v: View, g: Int, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
            val lp = v.layoutParams as FrameLayout.LayoutParams
            lp.gravity = g
            lp.setMargins(dp(l), dp(t), dp(r), dp(b))
            stage.addView(v, lp)
        }
        when (p) {
            0 -> {
                place(tile(R.drawable.ic_wallet), Gravity.CENTER, r = 50, b = 50)
                place(tile(R.drawable.ic_clock), Gravity.CENTER, l = 50, b = 50)
                place(tile(R.drawable.ic_text), Gravity.CENTER, r = 50, t = 50)
                place(tile(R.drawable.ic_grid, filled = true), Gravity.CENTER, l = 50, t = 50)
            }
            1 -> {
                place(tile(R.drawable.ic_wallet, 116, 48, filled = true), Gravity.CENTER)
                place(tile(R.drawable.ic_pdf, 72, 30), Gravity.CENTER, l = 150, b = 110)
                place(tile(R.drawable.ic_image, 72, 30), Gravity.CENTER, r = 150, b = 70)
                place(tile(R.drawable.ic_qr, 72, 30), Gravity.CENTER, l = 110, t = 150)
            }
            else -> {
                val ring = View(this)
                ring.background = bg(Color.TRANSPARENT, 100, Ui.mix(Ui.PRIMARY, Ui.BG, 0.6f), 2)
                stage.addView(ring, FrameLayout.LayoutParams(dp(200), dp(200), Gravity.CENTER))
                place(tile(R.drawable.ic_lock, 110, 48, filled = true), Gravity.CENTER)
                place(tile(R.drawable.ic_globe, 56, 24), Gravity.CENTER, l = 190, b = 150)
                place(tile(R.drawable.ic_star, 56, 24), Gravity.CENTER, r = 190, t = 150)
            }
        }
        outer.addView(stage, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        return outer
    }
}
