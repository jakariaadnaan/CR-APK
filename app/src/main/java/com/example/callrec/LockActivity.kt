package com.example.callrec

import android.app.Activity
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

    private lateinit var dots: TextView
    private lateinit var msg: TextView

    private val isPattern: Boolean get() = Lock.type(this) == "pattern"

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.apply(this)
        Lock.showing = true
        if (screenHidden(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Ui.PRIMARY

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Ui.PRIMARY)
        root.setPadding(dp(24), dp(40), dp(24), dp(24))

        val lock = label("🔒", 40f, Color.WHITE)
        lock.gravity = Gravity.CENTER
        root.addView(lock, lp())
        val title = label("আমার খাতা", 22f, Color.WHITE, true)
        title.gravity = Gravity.CENTER
        root.addView(title, lp(t = 8))

        dots = label("", 30f, Color.WHITE, true)
        dots.gravity = Gravity.CENTER
        msg = label(if (isPattern) "প্যাটার্ন আঁকুন" else "পিন দিন", 14f, Color.parseColor("#D6E0FB"))
        msg.gravity = Gravity.CENTER

        if (isPattern) {
            root.addView(msg, lp(t = 16, b = 16))
            val pv = PatternView(this)
            pv.baseColor = Color.parseColor("#A9BDF5")
            pv.activeColor = Color.WHITE
            pv.errorColor = Color.parseColor("#FF8A80")
            pv.onComplete = { s -> onPattern(pv, s) }
            pattern = pv
            root.addView(pv, LinearLayout.LayoutParams(dp(290), dp(290)))
            if (bioReady()) {
                val bio = label("👆  ফিঙ্গারপ্রিন্ট ব্যবহার করুন", 15f, Color.WHITE, true)
                bio.gravity = Gravity.CENTER
                bio.setPadding(dp(16), dp(12), dp(16), dp(12))
                bio.setOnClickListener { showBiometric() }
                root.addView(bio, lp(t = 16))
            }
        } else {
            root.addView(dots, lp(t = 24))
            root.addView(msg, lp(t = 6, b = 16))
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("👆", "0", "⌫")
            )
            val pad = LinearLayout(this)
            pad.orientation = LinearLayout.VERTICAL
            for (r in keys) {
                val line = LinearLayout(this)
                line.orientation = LinearLayout.HORIZONTAL
                for (k in r) {
                    val isBioKey = k == "👆"
                    val b = label(if (isBioKey) (if (bioReady()) k else "") else bn(k), 26f, Color.WHITE, true)
                    b.gravity = Gravity.CENTER
                    if (!isBioKey || bioReady()) b.background = bg(Color.parseColor("#4A74EC"), 40)
                    b.setOnClickListener {
                        when (k) {
                            "👆" -> if (bioReady()) showBiometric()
                            "⌫" -> {
                                if (input.isNotEmpty()) input = input.dropLast(1)
                                refreshDots()
                            }
                            else -> press(k)
                        }
                    }
                    val p = LinearLayout.LayoutParams(0, dp(68), 1f)
                    p.setMargins(dp(8), dp(6), dp(8), dp(6))
                    line.addView(b, p)
                }
                pad.addView(line, lp())
            }
            root.addView(pad, lp(l = 12, r = 12))
            refreshDots()
        }
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
        val sb = StringBuilder()
        for (i in 0 until n) sb.append(if (i < input.length) "●" else "○").append(' ')
        dots.text = sb.toString().trim()
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
            msg.text = "ভুল $what"
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
            registerFail("প্যাটার্ন")
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
                registerFail("পিন")
            }
        }
    }

    private fun countdown() {
        val left = (lockedUntil - System.currentTimeMillis() + 999) / 1000
        if (left <= 0) {
            msg.text = if (isPattern) "প্যাটার্ন আঁকুন" else "পিন দিন"
            return
        }
        msg.text = "অনেকবার ভুল হয়েছে। " + bn(left.toString()) + " সেকেন্ড পরে চেষ্টা করুন"
        handler.postDelayed({ countdown() }, 1000)
    }

    private fun showBiometric() {
        if (Build.VERSION.SDK_INT < 28 || signal != null) return
        try {
            val s = CancellationSignal()
            signal = s
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("আনলক করুন")
                .setNegativeButton(
                    if (isPattern) "প্যাটার্ন ব্যবহার করুন" else "পিন ব্যবহার করুন",
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
