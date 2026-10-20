package com.example.callrec

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.content.DialogInterface
import android.graphics.Color
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen lock: PIN pad or pattern pad, plus optional fingerprint. Back goes home, never into the app. */
class LockActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var input = ""
    private var fails = 0
    private var lockedUntil = 0L
    private var signal: CancellationSignal? = null
    private var pattern: PatternView? = null

    private lateinit var dotRow: LinearLayout
    private lateinit var msg: TextView

    private val isPattern: Boolean get() = Lock.type(this) == "pattern"
    private val isPass: Boolean get() = Lock.type(this) == "password"
    private lateinit var passField: android.widget.EditText

    private fun prompt(): String = when {
        isPattern -> tr("প্যাটার্ন আঁকুন")
        isPass -> tr("পাসকোড দিন")
        else -> tr("পিন দিন")
    }

    private fun typeName(): String = when {
        isPattern -> tr("প্যাটার্ন")
        isPass -> tr("পাসকোড")
        else -> tr("পিন")
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.apply(this)
        Lock.showing = true
        if (screenHidden(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setPadding(dp(24), dp(28), dp(24), dp(16))

        val ink = Color.parseColor("#0A0A12")
        val topCol = Ui.mix(Ui.PRIMARY, ink, 0.62f)
        root.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(topCol, ink)
        )
        window.statusBarColor = topCol
        window.navigationBarColor = ink
        val soft = Color.argb(170, 255, 255, 255)
        val timeStyle = Lock.style(this) == 1 && Lock.type(this) == "pin"

        if (timeStyle) {
            val clock = android.widget.TextClock(this)
            clock.format12Hour = "hh:mm"
            clock.format24Hour = "HH:mm"
            clock.setTextColor(Color.WHITE)
            clock.textSize = 58f
            clock.typeface = android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
            clock.gravity = Gravity.CENTER
            root.addView(clock, lp(t = 16))
            val date = android.widget.TextClock(this)
            date.format12Hour = "EEEE, d MMMM"
            date.format24Hour = "EEEE, d MMMM"
            date.setTextColor(soft)
            date.textSize = 15f
            date.gravity = Gravity.CENTER
            root.addView(date, lp(t = 2))
        } else {
            val sp = getSharedPreferences("settings", MODE_PRIVATE)
            val iconRes = when (sp.getString("appIcon", "IconA")) {
                "IconB" -> R.mipmap.ic_launcher_b
                "IconC" -> R.mipmap.ic_launcher_c
                else -> R.mipmap.ic_launcher_a
            }
            val iv = android.widget.ImageView(this)
            iv.setImageResource(iconRes)
            iv.clipToOutline = true
            iv.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(v: android.view.View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.24f) }
            }
            root.addView(iv, lp(width = dp(68), height = dp(68), t = 20))
            val title = label(tr("আমার খাতা"), 20f, Color.WHITE, true)
            title.gravity = Gravity.CENTER
            root.addView(title, lp(t = 12))
        }

        msg = label(prompt(), 15f, soft)
        msg.gravity = Gravity.CENTER

        if (isPattern) {
            root.addView(msg, lp(t = 24, b = 16))
            val pv = PatternView(this)
            pv.baseColor = Ui.mix(Ui.PRIMARY, Color.WHITE, 0.6f)
            pv.activeColor = Color.WHITE
            pv.errorColor = Color.parseColor("#FF8A80")
            pv.onComplete = { s -> onPattern(pv, s) }
            pattern = pv
            val panel = LinearLayout(this)
            panel.gravity = Gravity.CENTER
            panel.background = bg(Color.argb(24, 255, 255, 255), 28)
            panel.addView(pv, LinearLayout.LayoutParams(dp(280), dp(280)))
            root.addView(panel, lp(width = dp(300), height = dp(300)))
            if (bioReady()) {
                val bio = label("👆", 26f, Color.WHITE, true)
                bio.gravity = Gravity.CENTER
                bio.background = bg(Color.argb(36, 255, 255, 255), 32)
                bio.setOnClickListener { it.tapFx(); showBiometric() }
                root.addView(bio, lp(width = dp(64), height = dp(64), t = 18))
                val hint = label(tr("ফিঙ্গারপ্রিন্ট ব্যবহার করুন"), 13f, soft)
                hint.gravity = Gravity.CENTER
                root.addView(hint, lp(t = 6))
            }
        } else if (isPass) {
            root.addView(msg, lp(t = 26))
            val et = android.widget.EditText(this)
            passField = et
            et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            et.hint = tr("পাসকোড")
            et.setHintTextColor(Color.argb(110, 255, 255, 255))
            et.setTextColor(Color.WHITE)
            et.textSize = 18f
            et.gravity = Gravity.CENTER
            et.setSingleLine(true)
            et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            et.background = bg(Color.argb(36, 255, 255, 255), 14)
            et.setPadding(dp(16), dp(14), dp(16), dp(14))
            et.setOnEditorActionListener { _, _, _ -> submitPass(); true }
            root.addView(et, lp(width = dp(290), t = 18))
            val go = label(tr("আনলক"), 16f, Color.WHITE, true)
            go.gravity = Gravity.CENTER
            go.background = bg(Ui.PRIMARY, 14)
            go.setOnClickListener { it.tapFx(); submitPass() }
            root.addView(go, lp(width = dp(290), height = dp(50), t = 14))
            if (bioReady()) {
                val bio = label("👆", 26f, Color.WHITE, true)
                bio.gravity = Gravity.CENTER
                bio.background = bg(Color.argb(36, 255, 255, 255), 32)
                bio.setOnClickListener { it.tapFx(); showBiometric() }
                root.addView(bio, lp(width = dp(64), height = dp(64), t = 22))
            }
        } else {
            root.addView(msg, lp(t = if (timeStyle) 20 else 26))
            dotRow = LinearLayout(this)
            dotRow.orientation = LinearLayout.HORIZONTAL
            dotRow.gravity = Gravity.CENTER
            root.addView(dotRow, lp(t = 16, b = 18))
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("👆", "0", "⌫")
            )
            val pad = LinearLayout(this)
            pad.orientation = LinearLayout.VERTICAL
            val circle = Color.argb(36, 255, 255, 255)
            for (r in keys) {
                val line = LinearLayout(this)
                line.orientation = LinearLayout.HORIZONTAL
                line.gravity = Gravity.CENTER
                for (k in r) {
                    val isBioKey = k == "👆"
                    val b = label(if (isBioKey) (if (bioReady()) k else "") else if (k == "⌫") k else bn(k), 26f, Color.WHITE, true)
                    b.gravity = Gravity.CENTER
                    if (!isBioKey || bioReady()) b.background = bg(if (isBioKey) Color.TRANSPARENT else circle, 36)
                    b.setOnClickListener {
                        it.tapFx()
                        when (k) {
                            "👆" -> if (bioReady()) showBiometric()
                            "⌫" -> {
                                if (input.isNotEmpty()) input = input.dropLast(1)
                                refreshDots()
                            }
                            else -> press(k)
                        }
                    }
                    val p = LinearLayout.LayoutParams(dp(72), dp(72))
                    p.setMargins(dp(11), dp(7), dp(11), dp(7))
                    line.addView(b, p)
                }
                pad.addView(line, lp())
            }
            root.addView(pad, lp())
            refreshDots()
        }

        root.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))
        val forgot = label(if (isPattern) tr("প্যাটার্ন ভুলে গেছেন?") else if (isPass) tr("পাসকোড ভুলে গেছেন?") else tr("পিন ভুলে গেছেন?"), 14f, soft)
        forgot.gravity = Gravity.CENTER
        forgot.setPadding(dp(16), dp(12), dp(16), dp(8))
        forgot.setOnClickListener {
            it.tapFx()
            AlertDialog.Builder(this)
                .setTitle(tr("ভুলে গেলে কী করবেন"))
                .setMessage(tr("পিন বা প্যাটার্ন ফোনেই থাকে, তাই আমরা ফিরিয়ে দিতে পারি না। ফোনের Settings > Apps > আমার খাতা > Storage থেকে ডেটা মুছে নতুন করে শুরু করতে হবে। আগে ব্যাকআপ নেওয়া থাকলে তা ফিরিয়ে আনা যাবে।"))
                .setPositiveButton(tr("ঠিক আছে"), null)
                .show()
        }
        root.addView(forgot, lp())
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (bioReady()) showBiometric()
    }

    override fun onPause() {
        super.onPause()
        signal?.cancel()
        signal = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Lock.showing = false
        handler.removeCallbacksAndMessages(null)
    }

    @Deprecated("Back must not reveal the app behind the lock")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun bioReady(): Boolean =
        Build.VERSION.SDK_INT >= 28 && Lock.bioEnabled(this) && Lock.biometricAvailable(this)

    private fun refreshDots() {
        val n = maxOf(Lock.pinLength(this), input.length)
        dotRow.removeAllViews()
        for (i in 0 until n) {
            val d = View(this)
            val g = android.graphics.drawable.GradientDrawable()
            g.shape = android.graphics.drawable.GradientDrawable.OVAL
            if (i < input.length) g.setColor(Color.WHITE) else { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), Color.argb(150, 255, 255, 255)) }
            d.background = g
            val p = LinearLayout.LayoutParams(dp(13), dp(13))
            p.setMargins(dp(8), 0, dp(8), 0)
            dotRow.addView(d, p)
        }
    }

    private fun shake() {
        val a = android.view.animation.TranslateAnimation(-14f, 14f, 0f, 0f)
        a.duration = 60
        a.repeatCount = 5
        a.repeatMode = android.view.animation.Animation.REVERSE
        if (::dotRow.isInitialized) dotRow.startAnimation(a)
    }

    private fun success() {
        Lock.markUnlocked()
        finish()
    }

    private fun registerFail(what: String) {
        fails += 1
        if (fails >= 5) {
            fails = 0
            lockedUntil = System.currentTimeMillis() + 30_000L
            countdown()
        } else {
            msg.text = tr("ভুল") + " " + what
        }
    }

    private fun onPattern(pv: PatternView, s: String) {
        if (System.currentTimeMillis() < lockedUntil) {
            pv.flashError()
            return
        }
        if (Lock.check(this, s)) {
            success()
        } else {
            pv.flashError()
            registerFail(tr("প্যাটার্ন"))
        }
    }

    private fun submitPass() {
        if (System.currentTimeMillis() < lockedUntil) return
        val t = passField.text.toString()
        if (t.isEmpty()) return
        if (Lock.check(this, t)) {
            success()
        } else {
            passField.setText("")
            val a = android.view.animation.TranslateAnimation(-14f, 14f, 0f, 0f)
            a.duration = 60
            a.repeatCount = 5
            a.repeatMode = android.view.animation.Animation.REVERSE
            passField.startAnimation(a)
            registerFail(tr("পাসকোড"))
        }
    }

    private fun press(d: String) {
        if (System.currentTimeMillis() < lockedUntil) return
        if (input.length >= 8) return
        input += d
        refreshDots()
        if (input.length >= Lock.pinLength(this)) {
            if (Lock.check(this, input)) {
                success()
            } else {
                input = ""
                refreshDots()
                shake()
                registerFail(tr("পিন"))
            }
        }
    }

    private fun countdown() {
        val left = (lockedUntil - System.currentTimeMillis() + 999) / 1000
        if (left <= 0) {
            msg.text = prompt()
            return
        }
        msg.text = tr("অনেকবার ভুল হয়েছে। ") + bn(left.toString()) + tr(" সেকেন্ড পরে চেষ্টা করুন")
        handler.postDelayed({ countdown() }, 1000)
    }

    private fun showBiometric() {
        if (Build.VERSION.SDK_INT < 28 || signal != null) return
        try {
            val s = CancellationSignal()
            signal = s
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(tr("আনলক করুন"))
                .setNegativeButton(
                    if (isPattern) tr("প্যাটার্ন ব্যবহার করুন") else if (isPass) tr("পাসকোড ব্যবহার করুন") else tr("পিন ব্যবহার করুন"),
                    mainExecutor,
                    DialogInterface.OnClickListener { _, _ -> signal = null }
                )
                .build()
            prompt.authenticate(s, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    signal = null
                    success()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    signal = null
                }
            })
        } catch (e: Exception) {
            signal = null
        }
    }
}
