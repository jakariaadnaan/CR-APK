package com.memoredia.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** ভিডিও মেটাডেটা: size, length, resolution, frame rate, bitrate, date and so on. */
class VideoInfoActivity : GuardedActivity() {

    private lateinit var thumb: ImageView
    private lateinit var rows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        val c = card(12)
        val top = LinearLayout(this)
        top.gravity = android.view.Gravity.CENTER_VERTICAL
        thumb = ImageView(this)
        thumb.scaleType = ImageView.ScaleType.CENTER_CROP
        thumb.background = bg(Ui.PREVIEW, 10)
        top.addView(thumb, lp(width = dp(88), height = dp(100), r = 14))
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        nameV = label(tr("ভিডিও বাছুন"), 15f, Ui.TEXT, true)
        nameV.maxLines = 2
        nameV.ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        typeV = label(tr("কোনো ভিডিও বাছা হয়নি"), 12f, Ui.MUTED)
        sizeV = label("", 20f, Ui.TEXT, true)
        mid.addView(nameV)
        mid.addView(typeV, lp(t = 2))
        mid.addView(sizeV, lp(t = 6))
        top.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(top)
        c.setOnClickListener { pickVideo() }
        box.addView(c, lp(b = 6))

        box.addView(sectionLabel(tr("টেকনিক্যাল তথ্য")))
        val r = card(8)
        rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.addView(label(tr("কোনো ভিডিও বাছা হয়নি"), 13f, Ui.MUTED))
        r.addView(rows, lp())
        box.addView(r, lp())
        box.addView(label(tr("ডিভাইসভেদে সব তথ্য পাওয়া যায় না; না পেলে \"পাওয়া যায়নি\" লেখা হয়।"), 11f, Ui.MUTED), lp(t = 8, l = 2))

        val w = LinearLayout(this)
        w.orientation = LinearLayout.HORIZONTAL
        w.addView(primaryButton(tr("ভিডিও বাছুন")) { pickVideo() }, lp(width = 0, height = dp(48), r = 4).also { it.weight = 1f })
        w.addView(primaryButton(tr("মেটাডেটা কপি")) { if (copyAll.isNotEmpty()) copyText(copyAll) }, lp(width = 0, height = dp(48), l = 4).also { it.weight = 1f })
        setScreen(tr("ভিডিও মেটাডেটা"), scroll, null, true, bottomBar(w))
    }

    private lateinit var nameV: TextView
    private lateinit var typeV: TextView
    private lateinit var sizeV: TextView
    private var copyAll = ""

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "video/*"
        startActivityForResult(i, 93)
    }

    private fun row(k: String, v: String, last: Boolean) {
        rows.addView(kvRow(k, v, last))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 93 || resultCode != Activity.RESULT_OK || data?.data == null) return
        val u = data.data!!
        rows.removeAllViews()
        rows.addView(label(tr("পড়া হচ্ছে…"), 13f, Ui.MUTED))
        Thread {
            val m = MediaMetadataRetriever()
            val list = ArrayList<Pair<String, String>>()
            var frame: android.graphics.Bitmap? = null
            var ok = true
            try {
                m.setDataSource(this, u)
                fun g(k: Int): String? = m.extractMetadata(k)
                list.add(tr("ফাইল") to Img.name(this, u))
                list.add(tr("সাইজ") to Img.human(Img.size(this, u)))
                g(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { list.add(tr("ধরন") to it) }
                g(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { list.add(tr("দৈর্ঘ্য") to dur(it)) }
                var w = g(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                var h = g(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rot = g(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                if (w != null && h != null) list.add(tr("রেজোলিউশন") to bn(w) + " × " + bn(h))
                if (rot != 0) list.add(tr("ঘোরানো") to bn(rot.toString()) + "°")
                val none = tr("পাওয়া যায়নি")
                list.add(tr("ফ্রেম রেট") to (g(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let { bn(String.format(Locale.US, "%.1f", it)) + " fps" } ?: none))
                list.add(tr("বিটরেট") to (g(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { bn(String.format(Locale.US, "%.2f", it / 1000000.0)) + " Mbps" } ?: none))
                list.add(tr("অডিও আছে") to (if (g(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") tr("হ্যাঁ") else tr("না")))
                list.add(tr("তৈরির তারিখ") to (g(MediaMetadataRetriever.METADATA_KEY_DATE) ?: none))
                g(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { list.add(tr("অবস্থান") to it) }
                frame = m.getFrameAtTime(0)
            } catch (e: Throwable) {
                ok = false
            } finally {
                try { m.release() } catch (e: Throwable) {}
            }
            runOnUiThread {
                rows.removeAllViews()
                if (!ok) {
                    rows.addView(label(tr("তথ্য পড়া যায়নি"), 13f, Ui.MUTED))
                    Toast.makeText(this, tr("ভিডিও খোলা যায়নি"), Toast.LENGTH_LONG).show()
                } else {
                    thumb.setImageBitmap(frame)
                    nameV.text = list[0].second
                    sizeV.text = list[1].second
                    typeV.text = list.firstOrNull { it.first == tr("ধরন") }?.second ?: ""
                    val rest = list.drop(2).filter { it.first != tr("ধরন") }
                    for ((i, p) in rest.withIndex()) row(p.first, p.second, i == rest.size - 1)
                    copyAll = list.joinToString("\n") { it.first + ": " + it.second }
                }
            }
        }.start()
    }

    private fun dur(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val mi = (s % 3600) / 60
        val se = s % 60
        return bn(if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, mi, se) else String.format(Locale.US, "%d:%02d", mi, se))
    }
}
