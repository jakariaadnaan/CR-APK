package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File

/** PDF জোড়া, step by step: add files, arrange, (settings), merge with progress, result. */
class PdfMergeActivity : GuardedActivity() {

    private class Item(val uri: Uri, val name: String) {
        var pages = 0
        var size = 0L
        var thumb: Bitmap? = null
    }

    private val items = ArrayList<Item>()
    private var stage = 0 // 0 add, 1 arrange, 3 merging / result
    private lateinit var root: LinearLayout

    // progress screen state
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
            stage == 3 && !done && failed == null -> { }
            stage == 3 -> finish()
            stage == 1 -> { stage = 0; render() }
            else -> super.onBackPressed()
        }
    }

    private fun render() {
        root.removeAllViews()
        when (stage) {
            0 -> renderAdd()
            1 -> renderArrange()
            else -> renderProgress()
        }
    }

    private fun human(b: Long): String = Img.human(b)

    private fun loadThumb(it: Item, iv: android.widget.ImageView) {
        val t = it.thumb
        if (t != null) { iv.setImageBitmap(t); return }
        pdfThumb(it.uri, 160) { bmp, pages ->
            it.thumb = bmp
            if (pages > 0) it.pages = pages
            iv.setImageBitmap(bmp)
            if (stage == 0 || stage == 1) refreshTexts()
        }
    }

    private var refresh: (() -> Unit)? = null
    private fun refreshTexts() { refresh?.invoke() }

    private fun itemSub(it: Item): String =
        human(it.size) + (if (it.pages > 0) " · " + bn(it.pages.toString()) + tr(" পেজ") else "")

    // ------------------------------------------------------------------ step 1: add files

    private fun renderAdd() {
        root.addView(pdfHeader(tr("PDF জোড়া"), tr("একাধিক PDF ফাইল একসাথে করুন"), { finish() }), lp())
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
        val t = label(tr("PDF ফাইল যোগ করুন"), 17f, Ui.PRIMARY, true)
        t.gravity = Gravity.CENTER
        zone.addView(t, lp(t = 12))
        val s = label(tr("চেপে ফোনের ফাইল বাছুন, একসাথে অনেকগুলো নেওয়া যায়"), 12f, Ui.MUTED)
        s.gravity = Gravity.CENTER
        zone.addView(s, lp(t = 4))
        val add = label("+  " + tr("ফাইল যোগ করুন"), 14f, Color.WHITE, true)
        add.gravity = Gravity.CENTER
        add.background = bg(Ui.PRIMARY, 12)
        add.setPadding(dp(22), dp(11), dp(22), dp(11))
        zone.addView(add, lp(width = LinearLayout.LayoutParams.WRAP_CONTENT, t = 14))
        zone.setOnClickListener { pick() }
        add.setOnClickListener { it.tapFx(); pick() }
        col.addView(zone, lp(b = 16))

        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val cnt = label("", 14f, Ui.TEXT, true)
        head.addView(cnt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val clr = label(tr("সব মুছুন"), 13f, Ui.PRIMARY, true)
        clr.setPadding(dp(8), dp(6), dp(0), dp(6))
        clr.setOnClickListener { items.clear(); render() }
        head.addView(clr)
        col.addView(head, lp(l = 2, b = 6))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        col.addView(list, lp())
        val texts = ArrayList<TextView>()

        if (items.isEmpty()) {
            val e = label(tr("এখনো কোনো ফাইল যোগ করা হয়নি"), 13f, Ui.MUTED)
            e.gravity = Gravity.CENTER
            e.setPadding(0, dp(12), 0, dp(12))
            list.addView(e, lp())
            clr.visibility = View.GONE
        }
        for ((i, it) in items.withIndex()) {
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.background = bg(Ui.CARD, 14, Ui.LINE, 1)
            row.setPadding(dp(10), dp(9), dp(8), dp(9))
            val th = thumbView(40, 52)
            loadThumb(it, th)
            row.addView(th)
            val mid = LinearLayout(this)
            mid.orientation = LinearLayout.VERTICAL
            val nm = label(it.name, 14f, Ui.TEXT, true)
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            mid.addView(nm)
            val sub = label(itemSub(it), 12f, Ui.MUTED)
            texts.add(sub)
            mid.addView(sub, lp(t = 2))
            row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { p -> p.setMargins(dp(12), 0, dp(8), 0) })
            val x = label("✕", 15f, Ui.MUTED, true)
            x.gravity = Gravity.CENTER
            x.setOnClickListener { _ -> items.removeAt(i); render() }
            row.addView(x, lp(width = dp(36), height = dp(36)))
            list.addView(row, lp(b = 8))
        }
        refresh = {
            cnt.text = tr("যোগ করা ফাইল") + " (" + bn(items.size.toString()) + ")"
            for ((i, t2) in texts.withIndex()) if (i < items.size) t2.text = itemSub(items[i])
        }
        refresh?.invoke()
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        if (items.size < 2) {
            val hint = label(tr("কমপক্ষে ২টি PDF যোগ করুন"), 12f, Ui.MUTED)
            hint.gravity = Gravity.CENTER
            bar.addView(hint, lp(b = 8))
        }
        bar.addView(bigButton(tr("পরবর্তী"), items.size >= 2) { stage = 1; render() }, lp(height = dp(52)))
        root.addView(bottomBar(bar), lp())
    }

    private fun pick() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(i, 91)
    }

    // ------------------------------------------------------------------ step 2: arrange

    private fun renderArrange() {
        val save = label(tr("ক্রম সংরক্ষণ"), 13f, Ui.PRIMARY, true)
        save.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.85f), 14)
        save.setPadding(dp(12), dp(7), dp(12), dp(7))
        save.setOnClickListener { Toast.makeText(this, tr("ক্রম ঠিক করা হয়েছে"), Toast.LENGTH_SHORT).show() }
        root.addView(pdfHeader(tr("PDF জোড়া"), bn(items.size.toString()) + tr(" টি ফাইল বাছা হয়েছে"), { stage = 0; render() }, save), lp())

        val scroll = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(8), dp(16), dp(16))
        scroll.addView(col)
        col.addView(label(tr("ক্রম বদলাতে ডানের ≡ চিহ্ন ধরে টানুন"), 12f, Ui.MUTED), lp(l = 2, b = 8))

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        col.addView(list, lp())
        val badges = HashMap<View, TextView>()

        fun renumber() {
            for (k in 0 until list.childCount) {
                val v = list.getChildAt(k)
                badges[v]?.text = bn((k + 1).toString())
            }
        }

        for (it in items) {
            val row = LinearLayout(this)
            row.gravity = Gravity.CENTER_VERTICAL
            row.tag = it
            row.background = bg(Ui.CARD, 16, Ui.LINE, 1)
            row.setPadding(dp(10), dp(10), dp(6), dp(10))
            val badge = label("", 13f, Ui.PRIMARY, true)
            badge.gravity = Gravity.CENTER
            badge.background = bg(Ui.mix(Ui.PRIMARY, Ui.CARD, 0.85f), 12)
            row.addView(badge, lp(width = dp(24), height = dp(24), r = 10))
            badges[row] = badge
            val th = thumbView(46, 60)
            loadThumb(it, th)
            row.addView(th)
            val mid = LinearLayout(this)
            mid.orientation = LinearLayout.VERTICAL
            val nm = label(it.name, 14f, Ui.TEXT, true)
            nm.maxLines = 1
            nm.ellipsize = android.text.TextUtils.TruncateAt.END
            mid.addView(nm)
            mid.addView(label(itemSub(it), 12f, Ui.MUTED), lp(t = 2))
            row.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also { p -> p.setMargins(dp(12), 0, dp(6), 0) })
            val handle = label("≡", 24f, Ui.MUTED, true)
            handle.gravity = Gravity.CENTER
            attachDrag(handle, row, list, scroll) { renumber(); syncOrder(list) }
            row.addView(handle, lp(width = dp(44), height = dp(48)))
            list.addView(row, lp(b = 10))
        }
        renumber()
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val totalPages = items.sumOf { it.pages }
        val totalSize = items.sumOf { it.size }
        val sum = LinearLayout(this)
        sum.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        fun cell(title: String, value: String): View {
            val c = LinearLayout(this)
            c.orientation = LinearLayout.VERTICAL
            c.setPadding(dp(14), dp(10), dp(14), dp(10))
            c.addView(label(title, 11f, Ui.MUTED))
            c.addView(label(value, 15f, Ui.TEXT, true), lp(t = 2))
            return c
        }
        val pagesCell = cell(tr("মোট পেজ"), if (totalPages > 0) bn(totalPages.toString()) + tr(" পেজ") else "…")
        sum.addView(pagesCell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val vl = View(this)
        vl.setBackgroundColor(Ui.LINE)
        sum.addView(vl, lp(width = 1))
        sum.addView(cell(tr("আনুমানিক সাইজ"), "~ " + human(totalSize)), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val btns = LinearLayout(this)
        btns.gravity = Gravity.CENTER_VERTICAL
        val back = label("‹", 24f, Ui.PRIMARY, true)
        back.gravity = Gravity.CENTER
        back.background = bg(Ui.CARD, 14, Ui.PRIMARY, 1)
        back.setOnClickListener { stage = 0; render() }
        btns.addView(back, lp(width = dp(52), height = dp(52), r = 10))
        btns.addView(bigButton(tr("পরবর্তী") + "  →") { startMerge() }, LinearLayout.LayoutParams(0, dp(52), 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.VERTICAL
        bar.addView(sum, lp(b = 10))
        bar.addView(btns, lp())
        root.addView(bottomBar(bar), lp())
        refresh = {
            val tp = items.sumOf { it.pages }
            (pagesCell as LinearLayout).getChildAt(1).let { v -> (v as TextView).text = if (tp > 0) bn(tp.toString()) + tr(" পেজ") else "…" }
        }
    }

    /** Lets the ≡ handle drag its row up and down inside [list]. */
    private fun attachDrag(handle: View, row: View, list: LinearLayout, scroll: ScrollView, onDone: () -> Unit) {
        var startY = 0f
        var baseTop = 0
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = e.rawY
                    baseTop = row.top
                    row.elevation = dp(8).toFloat()
                    row.alpha = 0.92f
                    scroll.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val desired = baseTop + (e.rawY - startY)
                    val idx = list.indexOfChild(row)
                    val prev = if (idx > 0) list.getChildAt(idx - 1) else null
                    val next = if (idx < list.childCount - 1) list.getChildAt(idx + 1) else null
                    val mid = desired + row.height / 2f
                    if (prev != null && mid < prev.top + prev.height / 2f) {
                        val pt = prev.top
                        list.removeView(row)
                        list.addView(row, idx - 1)
                        row.translationY = desired - pt
                    } else if (next != null && mid > next.top + next.height / 2f) {
                        val nt = next.top
                        list.removeView(row)
                        list.addView(row, idx + 1)
                        row.translationY = desired - nt
                    } else {
                        row.translationY = desired - row.top
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    row.translationY = 0f
                    row.elevation = 0f
                    row.alpha = 1f
                    onDone()
                    true
                }
                else -> false
            }
        }
    }

    private fun syncOrder(list: LinearLayout) {
        val order = ArrayList<Item>()
        for (k in 0 until list.childCount) (list.getChildAt(k).tag as? Item)?.let { order.add(it) }
        if (order.size == items.size) { items.clear(); items.addAll(order) }
    }

    // ------------------------------------------------------------------ merge, progress, result

    private fun startMerge() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/pdf"
        i.putExtra(Intent.EXTRA_TITLE, "merged.pdf")
        saveOrAsk(i, 92)
    }

    private fun renderProgress() {
        root.addView(pdfHeader(tr("PDF জোড়া"), if (done) tr("কাজ শেষ") else tr("একটু অপেক্ষা করুন"), if (done || failed != null) ({ finish() }) else null), lp())
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
                failed != null -> tr("জোড়া দেওয়া যায়নি")
                done -> tr("জোড়া হয়ে গেছে")
                else -> tr("ফাইলগুলো জোড়া হচ্ছে…")
            }, 18f, if (failed != null) Ui.RED else Ui.PRIMARY, true
        )
        title.gravity = Gravity.CENTER
        col.addView(title, lp(t = 16))
        val sub = label(failed ?: if (done) tr("নিচে তৈরি ফাইলটি দেখুন") else tr("কয়েক সেকেন্ড লাগতে পারে"), 13f, Ui.MUTED)
        sub.gravity = Gravity.CENTER
        col.addView(sub, lp(t = 4))

        if (failed == null) {
            val steps = listOf(tr("ফাইল পড়া হচ্ছে"), tr("পেজ প্রস্তুত করা হচ্ছে"), tr("ফাইল জোড়া হচ্ছে"), tr("চূড়ান্ত করা হচ্ছে"))
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

    private fun setProgress(p: Int, s: Int) {
        runOnUiThread {
            percent = p
            step = s
            if (stage == 3) render()
        }
    }

    private fun mergeInto(out: Uri) {
        stage = 3
        done = false
        failed = null
        percent = 0
        step = 0
        outUri = out
        render()
        val snapshot = items.toList()
        Thread {
            val docs = ArrayList<PDDocument>()
            var err: String? = null
            var total = 0
            try {
                val dest = PDDocument()
                val util = PDFMergerUtility()
                val n = snapshot.size
                for ((i, it) in snapshot.withIndex()) {
                    setProgress(5 + 25 * i / n, 0)
                    val f = File(cacheDir, "merge_$i.pdf")
                    contentResolver.openInputStream(it.uri)?.use { ins -> f.outputStream().use { o -> ins.copyTo(o) } }
                    val d = PDDocument.load(f)
                    docs.add(d)
                    total += d.numberOfPages
                }
                setProgress(30, 1)
                for ((i, d) in docs.withIndex()) {
                    setProgress(35 + 50 * i / n, 2)
                    util.appendDocument(dest, d)
                }
                setProgress(90, 3)
                contentResolver.openOutputStream(out)?.use { dest.save(it) }
                dest.close()
            } catch (e: InvalidPasswordException) {
                err = tr("কোনো PDF-এ পাসওয়ার্ড দেওয়া আছে, আগে আনলক করুন")
            } catch (e: Throwable) {
                err = tr("কাজটি করা যায়নি")
            }
            for (d in docs) try { d.close() } catch (e: Throwable) { }
            for (i in snapshot.indices) try { File(cacheDir, "merge_$i.pdf").delete() } catch (e: Throwable) { }
            var size = 0L
            try { contentResolver.openFileDescriptor(out, "r")?.use { size = it.statSize } } catch (e: Throwable) { }
            val nm = try { Img.name(this, out) } catch (e: Throwable) { "merged.pdf" }
            runOnUiThread {
                if (err == null) {
                    done = true
                    percent = 100
                    outName = nm
                    outPages = total
                    outSize = size
                    Usage.done(this, "PdfMergeActivity", size, nm)
                    actionBuzz()
                } else failed = err
                render()
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        if (requestCode == 91) {
            val cd = data.clipData
            if (cd != null) {
                for (k in 0 until cd.itemCount) addUri(cd.getItemAt(k).uri)
            } else data.data?.let { addUri(it) }
            render()
        } else if (requestCode == 92) {
            data.data?.let { mergeInto(it) }
        }
    }

    private fun addUri(u: Uri) {
        val it = Item(u, Img.name(this, u))
        it.size = Img.size(this, u)
        items.add(it)
    }
}
