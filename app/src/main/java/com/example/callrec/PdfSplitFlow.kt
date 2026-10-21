package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

import android.provider.DocumentsContract
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import com.tom_roush.pdfbox.multipdf.Splitter
/** PDF ভাগ, step by step: choose file, pick a split mode, split with progress, result. */
class PdfSplitActivity : GuardedActivity() {

    private var stage = 0 // 0 choose, 1 settings, 2 progress / result
    private lateinit var root: LinearLayout

    private var uri: Uri? = null
    private var name = ""
    private var size = 0L
    private var pages = 0
    private var thumb: Bitmap? = null
    private var src: File? = null

    private var mode = 0 // 0 extract pages, 1 split by ranges, 2 every N pages
    private var specExtract = ""
    private var specRanges = ""
    private var everyN = "1"

    private var percent = 0
    private var step = 0
    private var done = false
    private var failed: String? = null
    private var outUri: Uri? = null
    private var outName = ""
    private var outCount = 0
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
        root.addView(pdfHeader(tr("PDF ভাগ"), tr("পেজ বেছে বা ভাগ করে আলাদা ফাইল বানান"), { finish() }), lp())
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
        bar.addView(bigButton(tr("পরবর্তী →"), uri != null && pages > 0) { stage = 1; render() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        startActivityForResult(i, 83)
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
                val f = File(cacheDir, "split_src.pdf")
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

    private var summary: TextView? = null

    private fun inputField(hint: String, value: String, number: Boolean, onChange: (String) -> Unit): EditText {
        val e = EditText(this)
        e.hint = hint
        e.setText(value)
        e.setTextColor(Ui.TEXT)
        e.setHintTextColor(Ui.MUTED)
        e.textSize = 15f
        e.inputType = if (number) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT
        e.background = bg(Ui.CARD, 12, Ui.LINE, 1)
        e.setPadding(dp(14), dp(12), dp(14), dp(12))
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { onChange(s?.toString() ?: ""); showSummary() }
        })
        return e
    }

    /** The groups of pages to write, one list per output file; null when the input is not valid. */
    private fun plan(): List<List<Int>>? {
        val total = pages
        when (mode) {
            0 -> {
                val l = parsePageSpec(specExtract, total) ?: return null
                return listOf(l)
            }
            1 -> {
                val out = ArrayList<List<Int>>()
                for (part in asciiDigits(specRanges).replace(" ", "").split(",")) {
                    if (part.isEmpty()) continue
                    out.add(parsePageSpec(part, total) ?: return null)
                }
                return if (out.isEmpty()) null else out
            }
            else -> {
                val n = asciiDigits(everyN).toIntOrNull() ?: return null
                if (n < 1) return null
                val out = ArrayList<List<Int>>()
                var i = 0
                while (i < total) {
                    out.add((i until Math.min(total, i + n)).toList())
                    i += n
                }
                return out
            }
        }
    }

    private fun showSummary() {
        val v = summary ?: return
        val p = plan()
        v.text = if (p == null) tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(pages.toString()) + tr(" পেজ)")
        else if (p.size == 1) tr("১টি ফাইল তৈরি হবে, ") + bn(p[0].size.toString()) + tr(" পেজ")
        else bn(p.size.toString()) + tr("টি ফাইল তৈরি হবে")
        v.setTextColor(if (p == null) Ui.RED else Ui.PRIMARY)
    }

    private fun renderSettings() {
        root.addView(pdfHeader(tr("সেটিংস"), tr("কীভাবে ভাগ করবেন ঠিক করুন"), { stage = 0; render() }), lp())
        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(20))
        scroll.addView(col)

        col.addView(fileCard(), lp(b = 14))
        col.addView(label(tr("ভাগের ধরন"), 14f, Ui.TEXT, true).also { it.setPadding(dp(2), 0, 0, 0) }, lp(b = 8))
        val names = listOf(tr("পেজ বের করুন"), tr("রেঞ্জ ধরে ভাগ করুন"), tr("প্রতি কয়েক পেজে ভাগ"))
        val subs = listOf(tr("বাছা পেজ দিয়ে একটি নতুন PDF"), tr("প্রতিটি রেঞ্জ আলাদা ফাইল হবে"), tr("যেমন প্রতি ১ পেজে একটি ফাইল"))
        for (k in 0..2) {
            val on = mode == k
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.background = selectBg(on)
            r.setPadding(dp(14), dp(13), dp(14), dp(13))
            val tx = LinearLayout(this)
            tx.orientation = LinearLayout.VERTICAL
            tx.addView(label(names[k], 14f, Ui.TEXT, true))
            tx.addView(label(subs[k], 12f, Ui.MUTED), lp(t = 2))
            r.addView(tx, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(tick(on))
            r.setOnClickListener { it.tapFx(); mode = k; render() }
            col.addView(r, lp(b = 8))
        }

        col.addView(label(
            when (mode) { 0 -> tr("কোন কোন পেজ চান"); 1 -> tr("রেঞ্জগুলো লিখুন"); else -> tr("প্রতি কত পেজে একটি ফাইল") },
            14f, Ui.TEXT, true
        ).also { it.setPadding(dp(2), 0, 0, 0) }, lp(t = 10, b = 8))
        when (mode) {
            0 -> {
                col.addView(inputField(tr("যেমন: 1-3, 5, 8-"), specExtract, false) { specExtract = it }, lp())
                col.addView(label(tr("কমা দিয়ে আলাদা করুন। 8- মানে ৮ থেকে শেষ পর্যন্ত।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
            }
            1 -> {
                col.addView(inputField(tr("যেমন: 1-3, 4-6, 7-"), specRanges, false) { specRanges = it }, lp())
                col.addView(label(tr("কমা দিয়ে আলাদা প্রতিটি রেঞ্জ একটি আলাদা ফাইল হবে।"), 11f, Ui.MUTED), lp(l = 2, t = 6))
            }
            else -> col.addView(inputField(tr("যেমন: ১"), everyN, true) { everyN = it }, lp())
        }

        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.93f), 16, Ui.LINE, 1)
        box.setPadding(dp(14), dp(12), dp(14), dp(12))
        box.addView(label(tr("আউটপুট"), 12f, Ui.MUTED))
        val sm = label("", 15f, Ui.PRIMARY, true)
        summary = sm
        box.addView(sm, lp(t = 2))
        if (mode != 0) box.addView(label(tr("ফাইলের নাম হবে নাম_part1.pdf, নাম_part2.pdf…"), 11f, Ui.MUTED), lp(t = 4))
        col.addView(box, lp(t = 16))
        showSummary()

        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(bigButton(tr("PDF ভাগ করুন")) { start() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    // ------------------------------------------------------------------ step 3: progress / result

    private fun baseName() = name.removeSuffix(".pdf").removeSuffix(".PDF")

    private fun start() {
        val p = plan()
        if (p == null) {
            Toast.makeText(this, tr("পেজ নম্বর ঠিক নেই (মোট ") + bn(pages.toString()) + tr(" পেজ)"), Toast.LENGTH_LONG).show()
            return
        }
        if (p.size == 1) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "application/pdf"
            i.putExtra(Intent.EXTRA_TITLE, baseName() + (if (mode == 0) "_pages.pdf" else "_part1.pdf"))
            saveOrAsk(i, 85)
        } else {
            treeOrAsk(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 86)
        }
    }

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 2) render()
        }
    }

    /** Writes each group of pages as its own PDF; a single group goes to [single], several into the folder [tree]. */
    private fun splitInto(single: Uri?, tree: Uri?) {
        val groups = plan() ?: return
        stage = 2
        done = false
        failed = null
        percent = 0
        step = 0
        outUri = single
        render()
        val f = src
        val base = baseName()
        Thread {
            var err: String? = null
            var made = 0
            var bytes = 0L
            var lastName = ""
            try {
                if (f == null) throw IllegalStateException()
                setProgress(5, 0)
                PDDocument.load(f).use { doc ->
                    val parent = if (tree != null) DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)) else null
                    for ((gi, g) in groups.withIndex()) {
                        setProgress(15 + 75 * gi / groups.size, 1)
                        val out = single ?: DocumentsContract.createDocument(contentResolver, parent!!, "application/pdf", base + "_part" + (gi + 1) + ".pdf")
                        if (out == null) continue
                        val nd = PDDocument()
                        for (i in g) nd.importPage(doc.getPage(i))
                        setProgress(15 + 75 * gi / groups.size, 2)
                        contentResolver.openOutputStream(out)?.use { nd.save(it) }
                        nd.close()
                        made++
                        bytes += Img.size(this, out)
                        lastName = Img.name(this, out)
                    }
                }
                setProgress(95, 3)
                if (made == 0) throw IllegalStateException()
            } catch (e: Throwable) {
                err = tr("কাজটি করা যায়নি")
            }
            val ok = err == null
            runOnUiThread {
                if (ok) {
                    outCount = made
                    outSize = bytes
                    outName = if (made == 1) lastName else bn(made.toString()) + tr("টি ফাইল")
                    done = true
                    percent = 100
                    Usage.done(this, "PdfSplitActivity", bytes, if (made == 1) lastName else "")
                } else failed = err
                render()
            }
        }.start()
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("PDF ভাগ"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
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
                failed != null -> tr("ভাগ করা যায়নি")
                done -> tr("ভাগ হয়ে গেছে")
                else -> tr("ভাগ হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে তৈরি ফাইলের তথ্য দেখুন") else tr("কয়েক সেকেন্ড লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("পেজ আলাদা করা হচ্ছে"), tr("নতুন PDF বানানো হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
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
            m.addView(label(human(outSize), 12f, Ui.MUTED))
            r.addView(m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(label("✓", 18f, Ui.PRIMARY, true))
            o.addView(r, lp(t = 6))
            col.addView(o, lp(t = 20))
            if (outCount > 1) col.addView(label(tr("ফাইলগুলো বাছা ফোল্ডারে সংরক্ষিত হয়েছে।"), 12f, Ui.MUTED).also { it.gravity = Gravity.CENTER }, lp(t = 10))
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (done || failed != null) {
            val bar = LinearLayout(this)
            bar.orientation = LinearLayout.VERTICAL
            if (done) {
                if (outCount == 1) bar.addView(bigButton(tr("ফাইল দেখুন")) { viewFile() }, lp(height = dp(52), b = 8))
                bar.addView(if (outCount == 1) outlineButton(tr("সম্পন্ন")) { finish() } else bigButton(tr("সম্পন্ন")) { finish() }, lp(height = dp(if (outCount == 1) 48 else 52)))
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
            83 -> data.data?.let { setFile(it) }
            85 -> data.data?.let { splitInto(it, null) }
            86 -> data.data?.let { splitInto(null, it) }
        }
    }
}
