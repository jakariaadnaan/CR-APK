package com.example.callrec

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
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val c = card(14)
        c.addView(pill("ভিডিও বাছুন") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "video/*"
            startActivityForResult(i, 93)
        }, lp(b = 10))
        thumb = ImageView(this)
        thumb.scaleType = ImageView.ScaleType.FIT_CENTER
        thumb.setBackgroundColor(Ui.PREVIEW)
        c.addView(thumb, lp(height = 200))
        box.addView(c, lp(b = 12))

        val r = card(14)
        rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        rows.addView(label("কোনো ভিডিও বাছা হয়নি", 13f, Ui.MUTED))
        r.addView(rows, lp())
        box.addView(r, lp())

        setScreen("ভিডিও মেটাডেটা", scroll)
    }

    private fun row(k: String, v: String) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.addView(label(k, 13f, Ui.MUTED), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val t = label(v, 14f, Ui.TEXT, true)
        t.gravity = android.view.Gravity.END
        t.setTextIsSelectable(true)
        r.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f))
        rows.addView(r, lp(b = 8))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 93 || resultCode != Activity.RESULT_OK || data?.data == null) return
        val u = data.data!!
        rows.removeAllViews()
        rows.addView(label("পড়া হচ্ছে…", 13f, Ui.MUTED))
        Thread {
            val m = MediaMetadataRetriever()
            val list = ArrayList<Pair<String, String>>()
            var frame: android.graphics.Bitmap? = null
            var ok = true
            try {
                m.setDataSource(this, u)
                fun g(k: Int): String? = m.extractMetadata(k)
                list.add("ফাইল" to Img.name(this, u))
                list.add("সাইজ" to Img.human(Img.size(this, u)))
                g(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { list.add("ধরন" to it) }
                g(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { list.add("দৈর্ঘ্য" to dur(it)) }
                var w = g(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                var h = g(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                val rot = g(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rot == 90 || rot == 270) { val t = w; w = h; h = t }
                if (w != null && h != null) list.add("রেজোলিউশন" to bn(w) + " × " + bn(h))
                if (rot != 0) list.add("ঘোরানো" to bn(rot.toString()) + "°")
                g(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()?.let {
                    list.add("ফ্রেম রেট" to bn(String.format(Locale.US, "%.1f", it)) + " fps")
                }
                g(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
                    list.add("বিটরেট" to bn(String.format(Locale.US, "%.2f", it / 1000000.0)) + " Mbps")
                }
                list.add("অডিও আছে" to (if (g(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") "হ্যাঁ" else "না"))
                g(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { list.add("তারিখ" to it) }
                g(MediaMetadataRetriever.METADATA_KEY_LOCATION)?.let { list.add("অবস্থান" to it) }
                frame = m.getFrameAtTime(0)
            } catch (e: Throwable) {
                ok = false
            } finally {
                try { m.release() } catch (e: Throwable) {}
            }
            runOnUiThread {
                rows.removeAllViews()
                if (!ok) {
                    rows.addView(label("তথ্য পড়া যায়নি", 13f, Ui.MUTED))
                    Toast.makeText(this, "ভিডিও খোলা যায়নি", Toast.LENGTH_LONG).show()
                } else {
                    thumb.setImageBitmap(frame)
                    for (p in list) row(p.first, p.second)
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
