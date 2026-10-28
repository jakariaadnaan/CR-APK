package com.memoredia.app

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** GIF মেকার: several pictures, or a slice of a video, become an animated GIF. */
class GifMakerActivity : GuardedActivity() {

    private var fromVideo = false
    private val uris = ArrayList<Uri>()
    private val names = ArrayList<String>()
    private var video: Uri? = null
    private var videoName = ""
    private var delayMs = 500
    private var fps = 8
    private var seconds = 3
    private var width = 320
    private var busy = false

    private lateinit var modeRow: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var sizeRow: LinearLayout
    private lateinit var status: TextView
    private lateinit var startF: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        box.addView(modeRow, lp(b = 12))

        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        box.addView(body, lp(b = 4))

        box.addView(sectionLabel(tr("GIF-এর চওড়া (পিক্সেল)"), 8))
        sizeRow = LinearLayout(this)
        sizeRow.orientation = LinearLayout.HORIZONTAL
        box.addView(sizeRow, lp(b = 8))
        val szCard = card(14)
        szCard.addView(label(tr("আউটপুটের মাপ"), 12f, Ui.TEXT, true))
        estV = label("", 12f, Ui.MUTED)
        szCard.addView(estV, lp(t = 2))
        szCard.addView(label(tr("ছোট মাপ হলে ফাইল ছোট হয়। রঙ সীমিত (২৫২টি), তাই ছবির মান কিছুটা কমে।"), 11f, Ui.MUTED), lp(t = 6))
        box.addView(szCard, lp(b = 12))

        status = label("", 14f, Ui.TEXT)
        box.addView(statusCard(status))

        val w = LinearLayout(this)
        w.addView(primaryButton(tr("GIF বানান")) { go() }, lp(height = dp(48)))
        setScreen(tr("GIF মেকার"), scroll, null, true, bottomBar(w))
        render()
    }

    private lateinit var estV: TextView
    private var vidDur = 0L
    private var vidW = 0
    private var vidH = 0
    private var vidThumb: Bitmap? = null

    /** The track with the picked slice highlighted, like the trim timeline in the mockup. */
    private inner class Timeline : android.view.View(this@GifMakerActivity) {
        override fun onDraw(c: Canvas) {
            val d = resources.displayMetrics.density
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Ui.PREVIEW
            c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), 8 * d, 8 * d, p)
            if (vidDur <= 0) return
            val st = (asciiDigits(if (::startF.isInitialized) startF.text.toString() else "0").toIntOrNull() ?: 0) * 1000L
            val l = width * (st.coerceAtMost(vidDur).toFloat() / vidDur)
            val r = width * ((st + seconds * 1000L).coerceAtMost(vidDur).toFloat() / vidDur)
            p.color = Ui.KEY_OP
            c.drawRoundRect(l, 0f, Math.max(r, l + 6 * d), height.toFloat(), 6 * d, 6 * d, p)
            p.color = Ui.PRIMARY
            c.drawRect(l, 0f, l + 2.5f * d, height.toFloat(), p)
            c.drawRect(Math.max(r, l + 6 * d) - 2.5f * d, 0f, Math.max(r, l + 6 * d), height.toFloat(), p)
        }
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return bn(String.format("%02d:%02d", s / 60, s % 60))
    }

    private fun estimate() {
        val frames: Int
        val h: Int
        if (fromVideo) {
            if (vidW <= 0) { estV.text = tr("আগে ভিডিও বাছুন"); return }
            frames = seconds * fps
            h = Math.round(vidH * width / vidW.toFloat())
        } else {
            if (uris.size < 2) { estV.text = tr("কমপক্ষে ২টি ছবি লাগবে"); return }
            frames = uris.size
            h = Math.round(width * 0.75f)
        }
        estV.text = bn(width.toString()) + tr(" px চওড়া · আনুমানিক সাইজ ") + "~" + Img.human((frames.toLong() * width * h * 0.3).toLong())
    }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView = choiceChip(text, on, click)

    private fun chipRow(title: String, opts: List<Pair<String, Int>>, cur: Int, set: (Int) -> Unit): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(sectionLabel(title, 10))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (o in opts) row.addView(chip(o.first, o.second == cur) { set(o.second); render() }, lp(r = 8))
        col.addView(row)
        return col
    }

    private fun render() {
        modeRow.removeAllViews()
        modeRow.addView(chip(tr("ছবি থেকে"), !fromVideo) { fromVideo = false; render() }, lp(r = 8))
        modeRow.addView(chip(tr("ভিডিও থেকে"), fromVideo) { fromVideo = true; render() })

        sizeRow.removeAllViews()
        for (w in listOf(240, 320, 480, 640)) sizeRow.addView(chip(bn(w.toString()), w == width) { width = w; render() }, lp(r = 8))

        body.removeAllViews()
        if (!fromVideo) {
            body.addView(pill(tr("ছবি যোগ করুন"), Ui.KEY_OP, Ui.PRIMARY) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "image/*"
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                startActivityForResult(i, 101)
            }, lp(b = 8))
            if (uris.isEmpty()) body.addView(label(tr("কমপক্ষে ২টি ছবি লাগবে"), 13f, Ui.MUTED))
            for (i in uris.indices) {
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = android.view.Gravity.CENTER_VERTICAL
                val t = label(bn((i + 1).toString()) + ". " + names[i], 14f, Ui.TEXT)
                t.maxLines = 1
                t.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
                row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(pill("↑", Ui.KEY_OP, Ui.PRIMARY) { move(i, -1) }, lp(l = 6))
                row.addView(pill("↓", Ui.KEY_OP, Ui.PRIMARY) { move(i, 1) }, lp(l = 6))
                row.addView(pill("✕", Ui.KEY_OP, Ui.RED) { uris.removeAt(i); names.removeAt(i); render() }, lp(l = 6))
                body.addView(row, lp(b = 6))
            }
            body.addView(chipRow(tr("প্রতি ছবি কতক্ষণ থাকবে"), listOf(tr("০.৩ সে.") to 300, tr("০.৫ সে.") to 500, tr("১ সে.") to 1000, tr("২ সে.") to 2000), delayMs) { delayMs = it })
        } else {
            val pc = card(10)
            val fr = android.widget.FrameLayout(this)
            val iv = android.widget.ImageView(this)
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            iv.background = bg(Ui.PREVIEW, 10)
            iv.setImageBitmap(vidThumb)
            fr.addView(iv, android.widget.FrameLayout.LayoutParams(-1, dp(170)))
            val play = label("▶", 16f, Ui.PRIMARY, true)
            play.gravity = android.view.Gravity.CENTER
            play.background = bg(Ui.CARD, 22, Ui.mix(Ui.PRIMARY, Ui.CARD, 0.5f), 1)
            fr.addView(play, android.widget.FrameLayout.LayoutParams(dp(44), dp(44), android.view.Gravity.CENTER))
            pc.addView(fr)
            val nr = LinearLayout(this)
            nr.gravity = android.view.Gravity.CENTER_VERTICAL
            val nv = label(if (video == null) tr("ভিডিও বাছুন") else videoName, 14f, Ui.TEXT, true)
            nv.maxLines = 1
            nv.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            nr.addView(nv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (vidDur > 0) nr.addView(label(clock(vidDur), 12f, Ui.MUTED))
            nr.addView(label(if (video == null) tr("বাছুন") else tr("বদলান"), 13f, Ui.PRIMARY, true).also { it.setPadding(dp(12), dp(6), dp(2), dp(6)) })
            nr.setOnClickListener {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "video/*"
                startActivityForResult(i, 102)
            }
            pc.addView(nr, lp(t = 10))
            body.addView(pc, lp(b = 4))
            body.addView(chipRow(tr("প্রতি সেকেন্ডে ফ্রেম"), listOf(tr("৫ FPS") to 5, tr("৮ FPS") to 8, tr("১০ FPS") to 10), fps) { fps = it })
            body.addView(chipRow(tr("কত সেকেন্ডের GIF"), listOf(tr("৩") to 3, tr("৫") to 5, tr("১০") to 10), seconds) { seconds = it })
            body.addView(sectionLabel(tr("ট্রিম টাইমলাইন"), 10))
            val keep = if (::startF.isInitialized) startF.text.toString() else "0"
            startF = EditText(this)
            startF.inputType = InputType.TYPE_CLASS_NUMBER
            startF.setText(keep)
            val tl = Timeline()
            startF.onChange { tl.invalidate() }
            body.addView(tl, lp(height = dp(36), b = 8))
            body.addView(fieldBox(tr("ভিডিওর কত সেকেন্ড থেকে শুরু"), startF))
        }
        estimate()
    }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= uris.size) return
        uris.add(j, uris.removeAt(i))
        names.add(j, names.removeAt(i))
        render()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data == null) return
        when (requestCode) {
            101 -> {
                val clip = data.clipData
                if (clip != null) {
                    for (k in 0 until clip.itemCount) { val u = clip.getItemAt(k).uri; uris.add(u); names.add(Img.name(this, u)) }
                } else if (data.data != null) {
                    uris.add(data.data!!); names.add(Img.name(this, data.data!!))
                }
                render()
            }
            102 -> {
                video = data.data
                videoName = if (video != null) Img.name(this, video!!) else ""
                vidDur = 0L; vidW = 0; vidH = 0; vidThumb = null
                render()
                val u = video
                if (u != null) Thread {
                    var d = 0L; var vw = 0; var vh = 0; var bm: Bitmap? = null
                    try {
                        val m = MediaMetadataRetriever()
                        m.setDataSource(this, u)
                        d = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        vw = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                        vh = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                        val rot = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                        if (rot == 90 || rot == 270) { val t = vw; vw = vh; vh = t }
                        bm = m.getFrameAtTime(0)
                        m.release()
                    } catch (e: Throwable) { }
                    runOnUiThread {
                        if (video != u) return@runOnUiThread
                        vidDur = d; vidW = vw; vidH = vh; vidThumb = bm
                        render()
                    }
                }.start()
            }
            103 -> data.data?.let { make(it) }
        }
    }

    private fun go() {
        if (busy) return
        if (!fromVideo && uris.size < 2) { status.text = tr("কমপক্ষে ২টি ছবি যোগ করুন"); return }
        if (fromVideo && video == null) { status.text = tr("আগে একটি ভিডিও বাছুন"); return }
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/gif"
        i.putExtra(Intent.EXTRA_TITLE, "animation.gif")
        saveOrAsk(i, 103)
    }

    /** Draws [src] centred inside a white w x h canvas, keeping its shape. */
    private fun fit(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        val s = Math.min(w / src.width.toFloat(), h / src.height.toFloat())
        val dw = src.width * s
        val dh = src.height * s
        c.drawBitmap(src, null, RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun make(dest: Uri) {
        busy = true
        status.text = tr("GIF তৈরি হচ্ছে…")
        val vid = fromVideo
        val list = ArrayList(uris)
        val v = video
        val W = width
        val delay = delayMs
        val fr = fps
        val secs = seconds
        val start = (asciiDigits(if (::startF.isInitialized && vid) startF.text.toString() else "0").toIntOrNull() ?: 0) * 1000L
        Thread {
            val tmp = File(cacheDir, "anim.gif")
            var msg: String
            try {
                var frames = 0
                BufferedOutputStream(FileOutputStream(tmp)).use { os ->
                    if (!vid) {
                        var enc: GifEncoder? = null
                        var H = 0
                        for (u in list) {
                            val b = Img.decode(this, u, 2000) ?: continue
                            if (enc == null) {
                                H = Math.max(1, Math.round(b.height * W / b.width.toFloat()))
                                enc = GifEncoder(os, W, H, delay)
                            }
                            val f = fit(b, W, H)
                            b.recycle()
                            enc.addFrame(f)
                            f.recycle()
                            frames++
                            val n = frames
                            runOnUiThread { status.text = tr("GIF তৈরি হচ্ছে… ") + bn(n.toString()) + "/" + bn(list.size.toString()) }
                        }
                        enc?.finish()
                    } else {
                        val m = MediaMetadataRetriever()
                        try {
                            m.setDataSource(this, v!!)
                            val durMs = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            var vw = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                            var vh = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                            val rot = m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                            if (rot == 90 || rot == 270) { val t = vw; vw = vh; vh = t }
                            if (vw <= 0 || vh <= 0 || start >= durMs) throw IllegalStateException()
                            val H = Math.max(1, Math.round(vh * W / vw.toFloat()))
                            val total = Math.min(secs * fr, ((durMs - start) * fr / 1000).toInt()).coerceAtLeast(1)
                            val enc = GifEncoder(os, W, H, 1000 / fr)
                            for (i in 0 until total) {
                                val tUs = (start + i * 1000L / fr) * 1000L
                                val b = m.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                                val f = fit(b, W, H)
                                b.recycle()
                                enc.addFrame(f)
                                f.recycle()
                                frames++
                                val n = i + 1
                                runOnUiThread { status.text = tr("GIF তৈরি হচ্ছে… ") + bn(n.toString()) + "/" + bn(total.toString()) }
                            }
                            enc.finish()
                        } finally {
                            try { m.release() } catch (e: Throwable) {}
                        }
                    }
                }
                if (frames == 0) throw IllegalStateException()
                contentResolver.openOutputStream(dest)?.use { os -> FileInputStream(tmp).use { it.copyTo(os) } }
                msg = tr("সংরক্ষিত হয়েছে: ") + bn(frames.toString()) + tr(" ফ্রেম · ") + Img.human(tmp.length())
                Usage.done(this, "GifMaker", tmp.length())
            } catch (e: Throwable) {
                msg = tr("GIF বানানো যায়নি")
            } finally {
                tmp.delete()
            }
            runOnUiThread {
                busy = false
                status.text = msg
            }
        }.start()
    }
}
