package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** ছবি → PDF, step by step: add pictures, arrange, page settings, build with progress, result. */
class ImagesToPdfActivity : GuardedActivity() {

    private class Item(val uri: Uri, val name: String) {
        var size = 0L
        var thumb: Bitmap? = null
    }

    private val items = ArrayList<Item>()
    private var stage = 0 // 0 add, 1 arrange, 2 settings, 3 progress / result
    private lateinit var root: LinearLayout

    private var sizeMode = 0 // 0 A4, 1 Letter, 2 same as the picture
    private var orient = 0   // 0 automatic, 1 portrait, 2 landscape
    private var marginIdx = 1 // 0 none, 1 small, 2 normal

    private var percent = 0
    private var step = 0
    private var done = false
    private var failed: String? = null
    private var outUri: Uri? = null
    private var outName = ""
    private var outPages = 0
    private var outSize = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            stage == 3 && !done && failed == null -> { }
            stage == 3 -> finish()
            stage == 2 -> { stage = 1; render() }
            stage == 1 -> { stage = 0; render() }
            else -> super.onBackPressed()
        }
    }

    private fun render() {
        root.removeAllViews()
        when (stage) {
            0 -> renderAdd()
            1 -> renderArrange()
            2 -> renderSettings()
            else -> renderProgress()
        }
    }

    private fun human(b: Long) = Img.human(b)

    private fun loadThumb(it: Item, iv: android.widget.ImageView) {
        val t = it.thumb
        if (t != null) { iv.setImageBitmap(t); return }
        Thread {
            val b = try { Img.decode(this, it.uri, 200) } catch (e: Throwable) { null }
            runOnUiThread {
                it.thumb = b
                if (b != null) iv.setImageBitmap(b)
            }
        }.start()
    }

    private fun row(it: Item, index: Int, arrange: Boolean): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        r.setPadding(dp(12), dp(10), dp(12), dp(10))
        if (arrange) {
            val nb = label(bn((index + 1).toString()), 12f, Color.WHITE, true)
            nb.gravity = Gravity.CENTER
            nb.background = bg(Ui.PRIMARY, 11)
            r.addView(nb, lp(width = dp(22), height = dp(22), r = 10))
        }
        val tv = thumbView(46, 56)
        loadThumb(it, tv)
        r.addView(tv, lp(width = dp(46), height = dp(56), r = 12))
        val m = LinearLayout(this)
        m.orientation = LinearLayout.VERTICAL
        val nm = label(it.name, 14f, Ui.TEXT, true)
        nm.maxLines = 1
        nm.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        m.addView(nm)
        m.addView(label(human(it.size), 12f, Ui.MUTED), lp(t = 2))
        r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (arrange) {
            val up = label("↑", 18f, if (index == 0) Ui.LINE else Ui.PRIMARY, true)
            up.setPadding(dp(10), dp(6), dp(10), dp(6))
            up.setOnClickListener { _ -> move(index, -1) }
            r.addView(up)
            val dn = label("↓", 18f, if (index == items.size - 1) Ui.LINE else Ui.PRIMARY, true)
            dn.setPadding(dp(10), dp(6), dp(6), dp(6))
            dn.setOnClickListener { _ -> move(index, 1) }
            r.addView(dn)
        } else {
            val x = label("✕", 16f, Ui.MUTED, true)
            x.setPadding(dp(10), dp(6), dp(4), dp(6))
            x.setOnClickListener { _ -> items.remove(it); render() }
            r.addView(x)
        }
        return r
    }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= items.size) return
        items.add(j, items.removeAt(i))
        render()
    }

    // ------------------------------------------------------------------ step 1: add pictures

    private fun renderAdd() {
        root.addView(pdfHeader(tr("ছবি → PDF"), tr("একাধিক ছবি একটি PDF-এ"), { finish() }), lp())
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
        ib.addView(iconView(R.drawable.ic_image, 34, Ui.PRIMARY))
        zone.addView(ib, lp(width = dp(70), height = dp(70)))
        val t = label(tr("ছবি যোগ করুন"), 17f, Ui.PRIMARY, true)
        t.gravity = Gravity.CENTER
        zone.addView(t, lp(t = 12))
        val s = label(tr("চেপে ফোনের ছবি বাছুন, একসাথে অনেকগুলো নেওয়া যায়"), 12f, Ui.MUTED)
        s.gravity = Gravity.CENTER
        zone.addView(s, lp(t = 4))
        zone.setOnClickListener { it.tapFx(); pick() }
        col.addView(zone, lp())

        if (items.isNotEmpty()) {
            val hr = LinearLayout(this)
            hr.gravity = Gravity.CENTER_VERTICAL
            hr.addView(label(tr("যোগ করা ছবি") + " (" + bn(items.size.toString()) + ")", 14f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val clr = label(tr("সব মুছুন"), 12f, Ui.PRIMARY, true)
            clr.setOnClickListener { _ -> items.clear(); render() }
            hr.addView(clr)
            col.addView(hr, lp(t = 18, b = 8, l = 2))
            for ((i, it) in items.withIndex()) col.addView(row(it, i, false), lp(b = 8))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("পরবর্তী →"), items.isNotEmpty()) { stage = 1; render() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 81)
    }

    private fun addUri(u: Uri) {
        val it = Item(u, Img.name(this, u))
        it.size = Img.size(this, u)
        items.add(it)
    }

    // ------------------------------------------------------------------ step 2: arrange

    private fun renderArrange() {
        root.addView(pdfHeader(tr("ছবি সাজান"), bn(items.size.toString()) + tr("টি ছবি, ↑ ↓ দিয়ে ক্রম বদলান"), { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(col)
        for ((i, it) in items.withIndex()) col.addView(row(it, i, true), lp(b = 8))
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("পরবর্তী →")) { stage = 2; render() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    // ------------------------------------------------------------------ step 3: settings

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

    private fun optionCard(title: String, sub: String, on: Boolean, click: () -> Unit): View {
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = selectBg(on)
        r.setPadding(dp(14), dp(13), dp(14), dp(13))
        val tx = LinearLayout(this)
        tx.orientation = LinearLayout.VERTICAL
        tx.addView(label(title, 14f, Ui.TEXT, true))
        tx.addView(label(sub, 12f, Ui.MUTED), lp(t = 2))
        r.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(tick(on))
        r.setOnClickListener { it.tapFx(); click() }
        return r
    }

    private fun sectionTitle(t: String, top: Int = 0): View {
        val w = LinearLayout(this)
        w.addView(label(t, 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) })
        w.layoutParams = lp(t = top, b = 8)
        return w
    }

    private fun renderSettings() {
        root.addView(pdfHeader(tr("সেটিংস"), tr("PDF-এর পেজ কেমন হবে ঠিক করুন"), { stage = 1; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(col)

        col.addView(sectionTitle(tr("পেজ সাইজ")))
        col.addView(optionCard("A4", tr("সবচেয়ে চালু মাপ"), sizeMode == 0) { sizeMode = 0; render() }, lp(b = 8))
        col.addView(optionCard("Letter", tr("আমেরিকান মাপ"), sizeMode == 1) { sizeMode = 1; render() }, lp(b = 8))
        col.addView(optionCard(tr("ছবির মাপ অনুযায়ী"), tr("প্রতিটি পেজ ছবির সমান, কোনো ফাঁকা জায়গা নেই"), sizeMode == 2) { sizeMode = 2; render() }, lp(b = 8))

        val fixed = sizeMode != 2
        col.addView(sectionTitle(tr("পেজের দিক"), 10))
        val orow = LinearLayout(this)
        orow.alpha = if (fixed) 1f else 0.45f
        for ((k, n) in listOf(tr("নিজে থেকে"), tr("লম্বা"), tr("আড়াআড়ি")).withIndex()) {
            val on = orient == k && fixed
            val t = LinearLayout(this)
            t.gravity = Gravity.CENTER
            t.background = selectBg(on)
            t.setPadding(dp(4), dp(14), dp(4), dp(14))
            t.addView(label(n, 14f, if (on) Ui.PRIMARY else Ui.TEXT, true))
            t.setOnClickListener { _ -> if (fixed) { orient = k; render() } }
            orow.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { p -> p.setMargins(if (k == 0) 0 else dp(4), 0, if (k == 2) 0 else dp(4), 0) })
        }
        col.addView(orow, lp())
        col.addView(label(if (fixed) tr("\"নিজে থেকে\" মানে ছবি যেদিকে লম্বা, পেজও সেদিকে।") else tr("ছবির মাপ রাখলে পেজের দিক ছবির মতোই থাকে।"), 11f, Ui.MUTED), lp(l = 2, t = 6))

        col.addView(sectionTitle(tr("মার্জিন"), 12))
        val mrow = LinearLayout(this)
        mrow.alpha = if (fixed) 1f else 0.45f
        for ((k, n) in listOf(tr("কোনো মার্জিন নয়"), tr("ছোট"), tr("মাঝারি")).withIndex()) {
            val on = marginIdx == k && fixed
            val t = LinearLayout(this)
            t.gravity = Gravity.CENTER
            t.background = selectBg(on)
            t.setPadding(dp(4), dp(14), dp(4), dp(14))
            val l = label(n, 13f, if (on) Ui.PRIMARY else Ui.TEXT, true)
            l.gravity = Gravity.CENTER
            t.addView(l)
            t.setOnClickListener { _ -> if (fixed) { marginIdx = k; render() } }
            mrow.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { p -> p.setMargins(if (k == 0) 0 else dp(4), 0, if (k == 2) 0 else dp(4), 0) })
        }
        col.addView(mrow, lp())

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.addView(label(tr("ফলাফল"), 12f, Ui.MUTED))
        box.addView(label(bn(items.size.toString()) + tr("টি ছবি → ") + bn(items.size.toString()) + tr(" পেজের PDF"), 14f, Ui.PRIMARY, true), lp(t = 2))
        col.addView(box, lp(t = 16))

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("PDF বানান")) { start() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    // ------------------------------------------------------------------ step 4: build, progress, result

    private fun start() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "images.pdf")
        saveOrAsk(i, 82)
    }

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 3) render()
        }
    }

    private fun build(out: Uri) {
        stage = 3
        done = false
        failed = null
        percent = 0
        step = 0
        outUri = out
        render()
        val list = items.map { it.uri }
        val mode = sizeMode
        val ori = orient
        val margin = floatArrayOf(0f, 28f, 57f)[marginIdx]
        Thread {
            var err: String? = null
            var pages = 0
            try {
                val doc = PdfDocument()
                setProgress(5, 0)
                for ((k, u) in list.withIndex()) {
                    setProgress(8 + 80 * k / list.size, 1)
                    val b = Img.decode(this, u, 2200) ?: continue
                    var pw: Int
                    var ph: Int
                    var m = margin
                    if (mode == 2) {
                        val s = 842f / Math.max(b.width, b.height)
                        pw = Math.max(1, (b.width * s).toInt())
                        ph = Math.max(1, (b.height * s).toInt())
                        m = 0f
                    } else {
                        val short = if (mode == 0) 595 else 612
                        val long = if (mode == 0) 842 else 792
                        val land = when (ori) { 1 -> false; 2 -> true; else -> b.width > b.height }
                        pw = if (land) long else short
                        ph = if (land) short else long
                    }
                    setProgress(8 + 80 * k / list.size, 2)
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pages + 1).create())
                    val cv: Canvas = page.canvas
                    cv.drawColor(Color.WHITE)
                    val s = Math.min((pw - 2 * m) / b.width, (ph - 2 * m) / b.height)
                    val w = b.width * s
                    val h = b.height * s
                    val l = (pw - w) / 2f
                    val t = (ph - h) / 2f
                    cv.drawBitmap(b, null, RectF(l, t, l + w, t + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(page)
                    b.recycle()
                    pages++
                }
                setProgress(92, 3)
                if (pages == 0) throw IllegalStateException()
                contentResolver.openOutputStream(out)?.use { doc.writeTo(it) }
                doc.close()
            } catch (e: Throwable) {
                err = tr("PDF বানানো যায়নি")
            }
            val ok = err == null
            runOnUiThread {
                if (ok) {
                    outPages = pages
                    outSize = Img.size(this, out)
                    outName = Img.name(this, out)
                    done = true
                    percent = 100
                    Usage.done(this, "ImagesToPdf", outSize, outName)
                } else failed = err
                render()
            }
        }.start()
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("ছবি → PDF"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
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
                failed != null -> tr("PDF বানানো যায়নি")
                done -> tr("PDF তৈরি হয়েছে")
                else -> tr("PDF তৈরি হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে তৈরি ফাইলটি দেখুন") else tr("কয়েক সেকেন্ড লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("ছবি পেজে বসানো হচ্ছে"), tr("PDF লেখা হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
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
            m.addView(label(human(outSize) + " · " + bn(outPages.toString()) + tr(" পেজ"), 12f, Ui.MUTED))
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
        if (requestCode == 81) {
            val cd = data.clipData
            if (cd != null) {
                for (k in 0 until cd.itemCount) addUri(cd.getItemAt(k).uri)
            } else data.data?.let { addUri(it) }
            render()
        } else if (requestCode == 82) {
            data.data?.let { build(it) }
        }
    }
}
