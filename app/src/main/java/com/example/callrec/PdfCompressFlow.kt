package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayOutputStream
import java.io.File

/** PDF ছোট করুন, step by step: choose file, pick level and quality, compress with progress, result. */
class PdfCompressActivity : GuardedActivity() {

    private var stage = 0 // 0 choose, 1 settings, 2 progress / result
    private lateinit var root: LinearLayout

    private var uri: Uri? = null
    private var name = ""
    private var size = 0L
    private var pages = 0
    private var thumb: Bitmap? = null
    private var src: File? = null

    private var level = 1      // 0 low, 1 medium, 2 high compression
    private var qualityIdx = 0 // 0 follow level, 1 best, 2 good, 3 standard, 4 low

    private var estimate = -1L // -1 unknown/working
    private var estToken = 0

    private var percent = 0
    private var step = 0
    private var done = false
    private var failed: String? = null
    private var outUri: Uri? = null
    private var outName = ""
    private var outSize = 0L

    private val dpis = intArrayOf(150, 110, 72)
    private val levelQ = floatArrayOf(0.8f, 0.65f, 0.45f)
    private val dropQ = floatArrayOf(0f, 0.9f, 0.75f, 0.6f, 0.4f)

    private fun dpi() = dpis[level]
    private fun quality() = if (qualityIdx == 0) levelQ[level] else dropQ[qualityIdx]

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
        if (t != null) tv.setImageBitmap(t)
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

    // ------------------------------------------------------------------ step 1: choose file

    private fun renderChoose() {
        root.addView(pdfHeader(tr("PDF ছোট করুন"), tr("ফাইলের সাইজ কমান"), { finish() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(col)

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
        bar.addView(bigButton(tr("পরবর্তী →"), uri != null && pages > 0) { stage = 1; render(); computeEstimate() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, 81)
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
                val f = File(cacheDir, "cmp_src.pdf")
                contentResolver.openInputStream(u)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                if (uri != u) return@Thread
                src = f
                pdfThumb(u, 160) { bmp, pg ->
                    if (uri != u) return@pdfThumb
                    thumb = bmp
                    pages = pg
                    if (pg <= 0) Toast.makeText(this, tr("এই PDF খোলা যায়নি (পাসওয়ার্ড দেওয়া থাকতে পারে)"), Toast.LENGTH_LONG).show()
                    if (stage == 0) render()
                }
            } catch (e: Throwable) {
                runOnUiThread { Toast.makeText(this, tr("ফাইল পড়া যায়নি"), Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    // ------------------------------------------------------------------ step 2: settings

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

    private fun qualityNames() = listOf(tr("লেভেল অনুযায়ী"), tr("সেরা মান"), tr("ভালো মান"), tr("সাধারণ মান"), tr("কম মান"))

    private var estLabel: TextView? = null
    private var savedLabel: TextView? = null

    private fun renderSettings() {
        root.addView(pdfHeader(tr("সেটিংস"), tr("কতটা ছোট করবেন ঠিক করুন"), { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(col)

        col.addView(fileCard(), lp(b = 14))
        col.addView(label(tr("কমপ্রেশন লেভেল"), 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }, lp(b = 8))
        val names = listOf(tr("কম"), tr("মাঝারি"), tr("বেশি"))
        val subs = listOf(tr("সেরা মান, সাইজ কমে অল্প"), tr("মান ও সাইজের ভালো ভারসাম্য"), tr("সবচেয়ে ছোট ফাইল, মান কিছুটা কম"))
        val tags = listOf("", tr("প্রস্তাবিত"), "")
        for (k in 0..2) {
            val on = level == k
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.background = selectBg(on)
            r.setPadding(dp(14), dp(13), dp(14), dp(13))
            val tx = LinearLayout(this)
            tx.orientation = LinearLayout.VERTICAL
            val top = LinearLayout(this)
            top.gravity = Gravity.CENTER_VERTICAL
            top.addView(label(names[k], 14f, Ui.TEXT, true))
            if (tags[k].isNotEmpty()) {
                val tg = label(tags[k], 10f, Ui.PRIMARY, true)
                tg.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.85f), 8)
                tg.setPadding(dp(8), dp(2), dp(8), dp(2))
                top.addView(tg, lp(l = 8))
            }
            tx.addView(top)
            tx.addView(label(subs[k], 12f, Ui.MUTED), lp(t = 2))
            r.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(tick(on))
            r.setOnClickListener { it.tapFx(); level = k; render(); computeEstimate() }
            col.addView(r, lp(b = 8))
        }

        col.addView(label(tr("আউটপুট কোয়ালিটি"), 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }, lp(t = 10, b = 8))
        val dd = LinearLayout(this)
        dd.gravity = Gravity.CENTER_VERTICAL
        dd.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        dd.setPadding(dp(14), dp(14), dp(14), dp(14))
        dd.addView(label(qualityNames()[qualityIdx], 14f, Ui.TEXT), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        dd.addView(label("▾", 16f, Ui.MUTED, true))
        dd.setOnClickListener {
            it.tapFx()
            android.app.AlertDialog.Builder(this)
                .setTitle(tr("আউটপুট কোয়ালিটি"))
                .setSingleChoiceItems(qualityNames().toTypedArray(), qualityIdx) { d, w -> qualityIdx = w; d.dismiss(); render(); computeEstimate() }
                .show()
        }
        col.addView(dd, lp())

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.addView(label(tr("আনুমানিক নতুন সাইজ"), 12f, Ui.MUTED))
        val e = label("", 20f, Ui.PRIMARY, true)
        estLabel = e
        box.addView(e, lp(t = 2))
        val sv = label("", 12f, Ui.MUTED)
        savedLabel = sv
        box.addView(sv, lp(t = 2))
        col.addView(box, lp(t = 16))
        col.addView(label(tr("পেজগুলো ছবি হয়ে যায়, তাই PDF-এর লেখা আর বাছা বা কপি করা যাবে না। স্ক্যান করা ও ছবিওয়ালা PDF-এ সবচেয়ে ভালো কাজ করে।"), 11f, Ui.MUTED), lp(l = 2, t = 8))
        showEstimate()

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("PDF ছোট করুন")) { startCompress() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun showEstimate() {
        val e = estLabel ?: return
        val sv = savedLabel ?: return
        if (estimate < 0) {
            e.text = tr("হিসাব হচ্ছে…")
            sv.text = ""
        } else {
            e.text = "≈ " + human(estimate)
            sv.text = if (estimate >= size) tr("এই PDF আগেই ছোট, সাইজ নাও কমতে পারে")
            else tr("প্রায় ") + bn(((size - estimate) * 100 / Math.max(1L, size)).toString()) + tr("% কম")
        }
    }

    /** Compress up to three sample pages and scale the result to the whole file. */
    private fun computeEstimate() {
        val f = src ?: return
        val token = ++estToken
        estimate = -1
        showEstimate()
        val d = dpi()
        val q = quality()
        val total = pages
        Thread {
            var est = -1L
            try {
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val rd = PdfRenderer(pfd)
                try {
                    val n = rd.pageCount
                    val picks = if (n <= 3) (0 until n).toList() else listOf(0, n / 2, n - 1)
                    var sum = 0L
                    for (i in picks) {
                        if (token != estToken) return@Thread
                        sum += encodePage(rd, i, d, q).size
                    }
                    est = sum / Math.max(1, picks.size) * Math.max(total, n) + 2048
                } finally {
                    rd.close()
                    pfd.close()
                }
            } catch (e: Throwable) { }
            if (token == estToken) runOnUiThread { estimate = est; if (stage == 1) showEstimate() }
        }.start()
    }

    private fun encodePage(rd: PdfRenderer, i: Int, dpi: Int, q: Float): ByteArray {
        val pg = rd.openPage(i)
        val w = Math.max(1, Math.round(pg.width * dpi / 72f))
        val h = Math.max(1, Math.round(pg.height * dpi / 72f))
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        pg.close()
        val bos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, (q * 100).toInt(), bos)
        bmp.recycle()
        return bos.toByteArray()
    }

    // ------------------------------------------------------------------ step 3: progress / result

    private fun startCompress() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, name.removeSuffix(".pdf").removeSuffix(".PDF") + "_small.pdf")
        saveOrAsk(i, 82)
    }

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 2) render()
        }
    }

    private fun compressInto(out: Uri) {
        stage = 2
        done = false
        failed = null
        percent = 0
        step = 0
        outUri = out
        render()
        val f = src
        val d = dpi()
        val q = quality()
        val before = size
        Thread {
            var err: String? = null
            var after = 0L
            val tmp = File(cacheDir, "pdf_small.pdf")
            try {
                if (f == null) throw IllegalStateException()
                setProgress(3, 0)
                val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
                val rd = PdfRenderer(pfd)
                val nd = PDDocument()
                try {
                    val n = rd.pageCount
                    for (i in 0 until n) {
                        setProgress(5 + 80 * i / n, if (i == 0) 0 else 1)
                        val pg = rd.openPage(i)
                        val w = Math.max(1, Math.round(pg.width * d / 72f))
                        val h = Math.max(1, Math.round(pg.height * d / 72f))
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val page = PDPage(PDRectangle(pg.width.toFloat(), pg.height.toFloat()))
                        pg.close()
                        nd.addPage(page)
                        val img = JPEGFactory.createFromImage(nd, bmp, q)
                        PDPageContentStream(nd, page).use { cs -> cs.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
                        bmp.recycle()
                    }
                    setProgress(88, 2)
                    tmp.outputStream().use { nd.save(it) }
                } finally {
                    nd.close()
                    rd.close()
                    pfd.close()
                }
                setProgress(94, 3)
                contentResolver.openOutputStream(out)?.use { o -> tmp.inputStream().use { it.copyTo(o) } }
                after = tmp.length()
            } catch (e: SecurityException) {
                err = tr("এই PDF-এ পাসওয়ার্ড দেওয়া আছে, আগে আনলক করুন")
            } catch (e: Throwable) {
                err = tr("কাজটি করা যায়নি")
            }
            val ok = err == null
            runOnUiThread {
                if (ok) {
                    outSize = after
                    outName = Img.name(this, out)
                    done = true
                    percent = 100
                    Usage.done(this, "PdfCompressActivity", after, outName)
                } else failed = err
                render()
            }
        }.start()
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("PDF ছোট করুন"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
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
                failed != null -> tr("ছোট করা যায়নি")
                done -> tr("ছোট হয়ে গেছে")
                else -> tr("ছোট করা হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে আগে-পরের সাইজ দেখুন") else tr("পেজ বেশি হলে কিছুটা সময় লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("পেজ ছোট করা হচ্ছে"), tr("নতুন PDF বানানো হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
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
            o.setPadding(dp(14), dp(12), dp(14), dp(14))
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            val a = LinearLayout(this)
            a.orientation = LinearLayout.VERTICAL
            a.addView(label(tr("আগে"), 11f, Ui.MUTED))
            a.addView(label(human(size), 16f, Ui.TEXT, true))
            r.addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label("→", 18f, Ui.MUTED, true), lp(l = 8, r = 8))
            val b = LinearLayout(this)
            b.orientation = LinearLayout.VERTICAL
            b.addView(label(tr("এখন"), 11f, Ui.MUTED))
            b.addView(label(human(outSize), 16f, Ui.PRIMARY, true))
            r.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            o.addView(r)
            val msg = if (outSize >= size) tr("এই PDF আগেই ছোট ছিল, আসল ফাইলটাই রাখুন।")
            else tr("প্রায় ") + bn(((size - outSize) * 100 / Math.max(1L, size)).toString()) + tr("% কম হয়েছে")
            o.addView(label(msg, 12f, Ui.MUTED), lp(t = 8))
            col.addView(o, lp(t = 20))
            col.addView(label(outName, 12f, Ui.MUTED).also { it.gravity = Gravity.CENTER; it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END }, lp(t = 10))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (done || failed != null) {
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            if (done) {
                bar.addView(bigButton(tr("ফাইল দেখুন")) { viewFile() }, lp(height = dp(52), b = 8))
                bar.addView(outlineButton(tr("সম্পন্ন")) { finish() }, lp(height = dp(48)))
            } else {
                bar.addView(bigButton(tr("আবার চেষ্টা করুন")) { failed = null; stage = 1; render(); computeEstimate() }, lp(height = dp(52)))
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
        if (requestCode == 81) data.data?.let { setFile(it) }
        else if (requestCode == 82) data.data?.let { compressInto(it) }
    }
}
