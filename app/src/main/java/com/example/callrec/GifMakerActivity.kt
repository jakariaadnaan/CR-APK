package com.example.callrec

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
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val m = card(14)
        modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        m.addView(modeRow)
        box.addView(m, lp(b = 12))

        val b = card(14)
        body = LinearLayout(this)
        body.orientation = LinearLayout.VERTICAL
        b.addView(body)
        box.addView(b, lp(b = 12))

        val s = card(14)
        s.addView(label(tr("GIF-এর চওড়া (পিক্সেল)"), 12f, Ui.MUTED, true))
        sizeRow = LinearLayout(this)
        sizeRow.orientation = LinearLayout.HORIZONTAL
        s.addView(sizeRow, lp(t = 6))
        s.addView(label(tr("ছোট মাপ হলে ফাইল ছোট হয়। রঙ সীমিত (২৫২টি), তাই ছবির মান কিছুটা কমে।"), 12f, Ui.MUTED), lp(t = 8))
        box.addView(s, lp(b = 12))

        val r = card(14)
        status = label("", 14f, Ui.TEXT)
        r.addView(status, lp(b = 10))
        r.addView(pill(tr("GIF বানান")) { go() })
        box.addView(r, lp())

        setScreen(tr("GIF মেকার"), scroll)
        render()
    }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView {
        val c = label(text, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
        c.gravity = android.view.Gravity.CENTER
        c.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
        c.setPadding(dp(14), dp(8), dp(14), dp(8))
        c.setOnClickListener { click() }
        return c
    }

    private fun chipRow(title: String, opts: List<Pair<String, Int>>, cur: Int, set: (Int) -> Unit): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(title, 12f, Ui.MUTED, true), lp(t = 8, b = 6))
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
            body.addView(pill(tr("ছবি যোগ করুন")) {
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
                row.addView(pill("↑", Ui.BG, Ui.PRIMARY) { move(i, -1) }, lp(l = 6))
                row.addView(pill("↓", Ui.BG, Ui.PRIMARY) { move(i, 1) }, lp(l = 6))
                row.addView(pill("✕", Ui.BG, Ui.RED) { uris.removeAt(i); names.removeAt(i); render() }, lp(l = 6))
                body.addView(row, lp(b = 6))
            }
            body.addView(chipRow(tr("প্রতি ছবি কতক্ষণ থাকবে"), listOf(tr("০.৩ সে.") to 300, tr("০.৫ সে.") to 500, tr("১ সে.") to 1000, tr("২ সে.") to 2000), delayMs) { delayMs = it })
        } else {
            body.addView(pill(tr("ভিডিও বাছুন")) {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                i.addCategory(Intent.CATEGORY_OPENABLE)
                i.type = "video/*"
                startActivityForResult(i, 102)
            }, lp(b = 8))
            body.addView(label(if (video == null) tr("কোনো ভিডিও বাছা হয়নি") else videoName, 13f, Ui.MUTED))
            body.addView(chipRow(tr("প্রতি সেকেন্ডে ফ্রেম"), listOf(tr("৫") to 5, tr("৮") to 8, tr("১০") to 10), fps) { fps = it })
            body.addView(chipRow(tr("কত সেকেন্ডের GIF"), listOf(tr("৩") to 3, tr("৫") to 5, tr("১০") to 10), seconds) { seconds = it })
            body.addView(label(tr("ভিডিওর কত সেকেন্ড থেকে শুরু"), 12f, Ui.MUTED, true), lp(t = 8, b = 4))
            val keep = if (::startF.isInitialized) startF.text.toString() else "0"
            startF = EditText(this)
            startF.inputType = InputType.TYPE_CLASS_NUMBER
            startF.setText(keep)
            startF.setTextColor(Ui.TEXT)
            body.addView(startF)
        }
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
                render()
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
        startActivityForResult(i, 103)
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
