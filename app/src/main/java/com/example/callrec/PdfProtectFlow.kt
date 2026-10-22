package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

/** PDF লক / আনলক, step by step: choose file, password and security level, process, result. */
class PdfLockActivity : GuardedActivity() {

    private var stage = 0 // 0 choose, 1 settings, 2 progress / result
    private lateinit var root: LinearLayout

    private var lock = true
    private var uri: Uri? = null
    private var name = ""
    private var size = 0L
    private var pages = 0
    private var thumb: Bitmap? = null
    private var src: File? = null

    private var pass = ""
    private var confirm = ""
    private var showPass = false
    private var strong = true // AES-256

    private var percent = 0
    private var step = 0
    private var done = false
    private var failed: String? = null
    private var outUri: Uri? = null
    private var outName = ""
    private var outSize = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.NAV
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = if (Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        setContentView(root)
        render()
    }

    @Deprecated("Back moves one step back")
    override fun onBackPressed() {
        when {
            stage == 2 && !done && failed == null -> { }
            stage == 2 -> finish()
            stage == 1 -> { stage = 0; render() }
            else -> super.onBackPressed()
        }
    }

    private fun render() {
        root.removeAllViews()
        when (stage) {
            0 -> renderChoose()
            1 -> renderSettings()
            else -> renderProgress()
        }
    }

    private fun human(b: Long) = Img.human(b)

    private fun fileCard(): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        val tv = thumbView(44, 58)
        val t = thumb
        if (t != null) tv.setImageBitmap(t) else tv.setImageDrawable(null)
        r.addView(tv, lp(width = dp(44), height = dp(58), r = 12))
        val m = LinearLayout(this)
        m.orientation = LinearLayout.VERTICAL
        val nm = label(name, 14f, Ui.TEXT, true)
        nm.maxLines = 1
        nm.ellipsize = android.text.TextUtils.TruncateAt.END
        m.addView(nm)
        m.addView(label(human(size) + (if (pages > 0) " · " + bn(pages.toString()) + tr(" পেজ") else ""), 12f, Ui.MUTED), lp(t = 2))
        r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        return r
    }

    private fun selectBg(on: Boolean): GradientDrawable {
        val g = GradientDrawable()
        g.cornerRadius = dp(14).toFloat()
        g.setColor(if (on) Ui.mix(Ui.PRIMARY, Ui.CARD, 0.9f) else Ui.CARD)
        g.setStroke(if (on) dp(2) else dp(1), if (on) Ui.PRIMARY else Ui.LINE)
        return g
    }

    private fun tick(on: Boolean): View {
        val v = LinearLayout(this)
        v.gravity = Gravity.CENTER
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        if (on) g.setColor(Ui.PRIMARY) else { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), Ui.LINE) }
        v.background = g
        if (on) v.addView(label("✓", 12f, Color.WHITE, true))
        v.layoutParams = LinearLayout.LayoutParams(dp(22), dp(22))
        return v
    }

    // ------------------------------------------------------------------ step 1: choose file

    private fun renderChoose() {
        root.addView(pdfHeader(tr("PDF লক / আনলক"), tr("পাসওয়ার্ড দিয়ে PDF সুরক্ষিত করুন"), { finish() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(col)

        val tabs = LinearLayout(this)
        tabs.background = bg(Ui.mix(Ui.PRIMARY, Ui.BG, 0.9f), 14)
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4))
        for ((k, n) in listOf(tr("লক করুন"), tr("আনলক করুন")).withIndex()) {
            val on = (k == 0) == lock
            val t = label(n, 14f, if (on) Color.WHITE else Ui.TEXT, true)
            t.gravity = Gravity.CENTER
            t.setPadding(0, dp(10), 0, dp(10))
            if (on) t.background = bg(Ui.PRIMARY, 11)
            t.setOnClickListener { _ -> lock = k == 0; render() }
            tabs.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        col.addView(tabs, lp(b = 14))

        val zone = LinearLayout(this)
        zone.orientation = LinearLayout.VERTICAL
        zone.gravity = Gravity.CENTER
        val dz = GradientDrawable()
        dz.setColor(Ui.mix(Ui.PRIMARY, Ui.BG, if (Ui.dark) 0.14f else 0.07f))
        dz.cornerRadius = dp(18).toFloat()
        dz.setStroke(dp(1), Ui.mix(Ui.PRIMARY, Ui.BG, 0.45f), dp(6).toFloat(), dp(4).toFloat())
        zone.background = dz
        zone.setPadding(dp(16), dp(26), dp(16), dp(26))
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        ib.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.82f), 18)
        ib.addView(iconView(R.drawable.ic_folder, 34, Ui.PRIMARY))
        zone.addView(ib, lp(width = dp(70), height = dp(70)))
        val t = label(if (uri == null) tr("PDF ফাইল বাছুন") else tr("অন্য ফাইল বাছুন"), 17f, Ui.PRIMARY, true)
        t.gravity = Gravity.CENTER
        zone.addView(t, lp(t = 12))
        val s = label(tr("চেপে ফোন থেকে একটি PDF বাছুন"), 12f, Ui.MUTED)
        s.gravity = Gravity.CENTER
        zone.addView(s, lp(t = 4))
        zone.setOnClickListener { it.tapFx(); pick() }
        col.addView(zone, lp())

        if (uri != null) {
            col.addView(label(tr("বাছা ফাইল"), 14f, Ui.TEXT, true), lp(t = 18, b = 8, l = 2))
            col.addView(fileCard(), lp())
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("পরবর্তী →"), uri != null && src != null && (!lock || pages > 0)) { stage = 1; render() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, 87)
    }

    private fun setFile(u: Uri) {
        uri = u
        name = Img.name(this, u)
        size = Img.size(this, u)
        pages = 0
        thumb = null
        src = null
        failed = null
        render()
        Thread {
            try {
                val f = File(cacheDir, "lock_src.pdf")
                contentResolver.openInputStream(u)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                if (uri != u) return@Thread
                runOnUiThread { if (uri == u) { src = f; if (stage == 0) render() } }
                pdfThumb(u, 160) { bmp, pg ->
                    if (uri != u) return@pdfThumb
                    thumb = bmp
                    pages = pg
                    if (pg <= 0 && lock) Toast.makeText(this, tr("এই PDF খোলা যায়নি (আগে থেকেই লক করা থাকতে পারে)"), Toast.LENGTH_LONG).show()
                    if (stage == 0) render()
                }
            } catch (e: Throwable) {
                runOnUiThread { Toast.makeText(this, tr("ফাইল পড়া যায়নি"), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ------------------------------------------------------------------ step 2: password and level

    private var strengthLabel: TextView? = null
    private var matchLabel: TextView? = null

    private fun strengthOf(p: String): Int {
        if (p.isEmpty()) return 0
        var s = 0
        if (p.length >= 6) s++
        if (p.length >= 10) s++
        if (p.any { it.isDigit() } && p.any { it.isLetter() }) s++
        if (p.any { !it.isLetterOrDigit() }) s++
        return Math.min(3, Math.max(1, s))
    }

    private fun refreshHints() {
        val sl = strengthLabel
        if (sl != null) {
            val st = strengthOf(pass)
            sl.text = when (st) { 0 -> ""; 1 -> tr("দুর্বল পাসওয়ার্ড"); 2 -> tr("মোটামুটি পাসওয়ার্ড"); else -> tr("শক্তিশালী পাসওয়ার্ড") }
            sl.setTextColor(when (st) { 1 -> Ui.RED; 2 -> Color.parseColor("#F9A825"); else -> Ui.GREEN })
        }
        val ml = matchLabel
        if (ml != null) {
            if (confirm.isEmpty()) ml.text = ""
            else if (confirm == pass) { ml.text = tr("পাসওয়ার্ড মিলেছে"); ml.setTextColor(Ui.GREEN) }
            else { ml.text = tr("পাসওয়ার্ড মেলেনি"); ml.setTextColor(Ui.RED) }
        }
    }

    private fun passField(hint: String, value: String, onChange: (String) -> Unit): View {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = bg(Ui.CARD, 12, Ui.LINE, 1)
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(Ui.TEXT)
        e.setHintTextColor(Ui.MUTED)
        e.textSize = 15f
        e.background = null
        e.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        e.transformationMethod = if (showPass) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
        e.setPadding(dp(14), dp(12), dp(8), dp(12))
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: ""); refreshHints() }
        })
        row.addView(e, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val eye = label(if (showPass) tr("লুকান") else tr("দেখান"), 12f, Ui.PRIMARY, true)
        eye.setPadding(dp(8), dp(10), dp(14), dp(10))
        eye.setOnClickListener { showPass = !showPass; render() }
        row.addView(eye)
        return row
    }

    private fun renderSettings() {
        root.addView(pdfHeader(tr("সেটিংস"), if (lock) tr("পাসওয়ার্ড ও নিরাপত্তার মাত্রা ঠিক করুন") else tr("PDF-এর পাসওয়ার্ড দিন"), { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(col)

        col.addView(fileCard(), lp(b = 14))
        col.addView(label(tr("পাসওয়ার্ড"), 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }, lp(b = 8))
        col.addView(passField(tr("পাসওয়ার্ড লিখুন"), pass) { pass = it }, lp())
        if (lock) {
            val sl = label("", 12f, Ui.MUTED, true)
            strengthLabel = sl
            col.addView(sl, lp(l = 2, t = 6))
            col.addView(label(tr("পাসওয়ার্ড নিশ্চিত করুন"), 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }, lp(t = 10, b = 8))
            col.addView(passField(tr("আবার লিখুন"), confirm) { confirm = it }, lp())
            val ml = label("", 12f, Ui.MUTED, true)
            matchLabel = ml
            col.addView(ml, lp(l = 2, t = 6))

            col.addView(label(tr("নিরাপত্তার মাত্রা"), 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }, lp(t = 14, b = 8))
            val names = listOf("AES-256", "AES-128")
            val subs = listOf(tr("সর্বোচ্চ নিরাপত্তা, সবচেয়ে নতুন PDF রিডারে চলে"), tr("মানসম্মত নিরাপত্তা, প্রায় সব PDF রিডারে চলে"))
            for (k in 0..1) {
                val on = (k == 0) == strong
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                r.background = selectBg(on)
                r.setPadding(dp(14), dp(13), dp(14), dp(13))
                val tx = LinearLayout(this)
                tx.orientation = LinearLayout.VERTICAL
                val top = LinearLayout(this)
                top.gravity = Gravity.CENTER_VERTICAL
                top.addView(label(names[k], 14f, Ui.TEXT, true))
                if (k == 0) {
                    val tg = label(tr("প্রস্তাবিত"), 10f, Ui.PRIMARY, true)
                    tg.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.85f), 8)
                    tg.setPadding(dp(8), dp(2), dp(8), dp(2))
                    top.addView(tg, lp(l = 8))
                }
                tx.addView(top)
                tx.addView(label(subs[k], 12f, Ui.MUTED), lp(t = 2))
                r.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                r.addView(tick(on))
                r.setOnClickListener { it.tapFx(); strong = k == 0; render() }
                col.addView(r, lp(b = 8))
            }
            col.addView(label(tr("পাসওয়ার্ড ভুলে গেলে PDF খোলা যাবে না, তাই মনে রাখুন।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
            refreshHints()
        } else {
            col.addView(label(tr("সঠিক পাসওয়ার্ড দিলে নতুন একটি পাসওয়ার্ডহীন কপি তৈরি হবে।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
        }

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(if (lock) tr("PDF লক করুন") else tr("PDF আনলক করুন")) { start() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    // ------------------------------------------------------------------ step 3: progress / result

    private fun start() {
        if (pass.isEmpty()) { Toast.makeText(this, tr("পাসওয়ার্ড লিখুন"), Toast.LENGTH_SHORT).show(); return }
        if (lock && pass != confirm) { Toast.makeText(this, tr("পাসওয়ার্ড মেলেনি"), Toast.LENGTH_SHORT).show(); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, name.removeSuffix(".pdf").removeSuffix(".PDF") + (if (lock) "_locked.pdf" else "_unlocked.pdf"))
        saveOrAsk(i, 88)
    }

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 2) render()
        }
    }

    private fun process(out: Uri) {
        stage = 2
        done = false
        failed = null
        percent = 0
        step = 0
        outUri = out
        render()
        val f = src
        val doLock = lock
        val p = pass
        val aes256 = strong
        Thread {
            var err: String? = null
            try {
                if (f == null) throw IllegalStateException()
                setProgress(10, 0)
                val doc = if (doLock) PDDocument.load(f) else PDDocument.load(f, p)
                try {
                    setProgress(40, 1)
                    if (doLock) {
                        val policy = StandardProtectionPolicy(p, p, AccessPermission())
                        policy.encryptionKeyLength = if (aes256) 256 else 128
                        policy.isPreferAES = true
                        doc.protect(policy)
                    } else {
                        doc.isAllSecurityToBeRemoved = true
                    }
                    setProgress(70, 2)
                    contentResolver.openOutputStream(out)?.use { doc.save(it) }
                } finally {
                    doc.close()
                }
                setProgress(95, 3)
            } catch (e: InvalidPasswordException) {
                err = tr("পাসওয়ার্ড ঠিক নেই")
            } catch (e: Throwable) {
                err = tr("কাজটি করা যায়নি")
            }
            val ok = err == null
            runOnUiThread {
                if (ok) {
                    outSize = Img.size(this, out)
                    outName = Img.name(this, out)
                    done = true
                    percent = 100
                    Usage.done(this, "PdfLockActivity", outSize, outName)
                } else failed = err
                render()
            }
        }.start()
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("PDF লক / আনলক"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(24), dp(12), dp(24), dp(16))
        scroll.addView(col)

        val ring = PercentRing(this)
        ring.percent = if (done) 100 else percent
        col.addView(ring, lp(width = dp(170), height = dp(170), t = 12))
        val title = label(
            when {
                failed != null -> if (lock) tr("লক করা যায়নি") else tr("আনলক করা যায়নি")
                done -> if (lock) tr("PDF লক হয়েছে") else tr("PDF আনলক হয়েছে")
                else -> if (lock) tr("লক করা হচ্ছে…") else tr("আনলক করা হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে তৈরি ফাইলটি দেখুন") else tr("কয়েক সেকেন্ড লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("নিরাপত্তা প্রয়োগ হচ্ছে"), tr("নতুন PDF বানানো হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
            val sc = LinearLayout(this)
            sc.orientation = LinearLayout.VERTICAL
            for ((k, n) in steps.withIndex()) {
                val r = LinearLayout(this)
                r.gravity = Gravity.CENTER_VERTICAL
                val state = if (done || k < step) 2 else if (k == step) 1 else 0
                val mark = label(if (state == 2) "✓" else if (state == 1) "●" else "○", 15f, if (state == 0) Ui.MUTED else Ui.PRIMARY, true)
                mark.gravity = Gravity.CENTER
                r.addView(mark, lp(width = dp(26)))
                r.addView(label(n, 14f, if (state == 0) Ui.MUTED else Ui.TEXT, state == 1))
                sc.addView(r, lp(t = 8))
            }
            col.addView(sc, lp(t = 18))
        }

        if (done) {
            val o = LinearLayout(this)
            o.orientation = LinearLayout.VERTICAL
            o.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
            o.setPadding(dp(14), dp(10), dp(14), dp(12))
            o.addView(label(tr("তৈরি ফাইল"), 11f, Ui.MUTED))
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            val ib = LinearLayout(this)
            ib.gravity = Gravity.CENTER
            ib.background = bg(Color.parseColor("#E53935"), 10)
            ib.addView(iconView(R.drawable.ic_pdf, 20, Color.WHITE))
            r.addView(ib, lp(width = dp(40), height = dp(40), r = 12))
            val m = LinearLayout(this)
            m.orientation = LinearLayout.VERTICAL
            val nm = label(outName, 14f, Ui.TEXT, true)
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            m.addView(nm)
            m.addView(label(human(outSize) + (if (lock) " · " + (if (strong) "AES-256" else "AES-128") else ""), 12f, Ui.MUTED))
            r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label("✓", 18f, Ui.PRIMARY, true))
            o.addView(r, lp(t = 6))
            col.addView(o, lp(t = 20))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (done || failed != null) {
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            if (done) {
                bar.addView(bigButton(tr("ফাইল দেখুন")) { viewFile() }, lp(height = dp(52), b = 8))
                bar.addView(outlineButton(tr("সম্পন্ন")) { finish() }, lp(height = dp(48)))
            } else {
                bar.addView(bigButton(tr("আবার চেষ্টা করুন")) { failed = null; stage = 1; render() }, lp(height = dp(52)))
            }
            root.addView(bottomBar(bar), lp())
        }
    }

    private fun viewFile() {
        val u = outUri ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(u, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (e: Throwable) {
            Toast.makeText(this, tr("PDF দেখার কোনো অ্যাপ পাওয়া যায়নি"), Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            87 -> data.data?.let { setFile(it) }
            88 -> data.data?.let { process(it) }
        }
    }
}
