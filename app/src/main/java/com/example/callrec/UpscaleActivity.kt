package com.example.callrec

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** ইমেজ আপস্কেলার: smooth enlargement with optional sharpening (no AI, just resampling). */
class UpscaleActivity : ImageToolBase() {

    private var factor = 2
    private var sharpen = true
    private var out: Bitmap? = null
    private var gen = 0

    private lateinit var factorRow: LinearLayout
    private lateinit var sharpRow: LinearLayout
    private lateinit var result: TextView

    init {
        maxSide = 2048
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        box.addView(pickCard(), lp(b = 12))

        val opt = card(14)
        opt.addView(label(tr("কত গুণ বড় করবেন"), 12f, Ui.MUTED, true))
        factorRow = LinearLayout(this)
        factorRow.orientation = LinearLayout.HORIZONTAL
        opt.addView(factorRow, lp(t = 6, b = 10))
        opt.addView(label(tr("ধার বাড়ানো (Sharpen)"), 12f, Ui.MUTED, true))
        sharpRow = LinearLayout(this)
        sharpRow.orientation = LinearLayout.HORIZONTAL
        opt.addView(sharpRow, lp(t = 6, b = 6))
        opt.addView(label(tr("এটি এআই নয়; ছবি মসৃণভাবে বড় হয়, নতুন ডিটেইল তৈরি হয় না।"), 12f, Ui.MUTED))
        box.addView(opt, lp(b = 12))

        val res = card(14)
        res.addView(label(tr("ফলাফল"), 12f, Ui.MUTED, true))
        result = label(tr("আগে একটি ছবি বাছুন"), 15f, Ui.TEXT)
        res.addView(result, lp(t = 4, b = 10))
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(pill(tr("PNG সংরক্ষণ")) { save(0) }, lp(r = 8))
        row.addView(pill(tr("JPG সংরক্ষণ"), Ui.BG, Ui.PRIMARY) { save(1) })
        res.addView(row, lp())
        box.addView(res, lp())

        setScreen(tr("ইমেজ আপস্কেলার"), scroll)
        chips()
    }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView {
        val c = label(text, 14f, if (on) Color.WHITE else Ui.PRIMARY, true)
        c.gravity = android.view.Gravity.CENTER
        c.background = bg(if (on) Ui.PRIMARY else Ui.CARD, 20, Ui.PRIMARY, 1)
        c.setPadding(dp(16), dp(8), dp(16), dp(8))
        c.setOnClickListener { click() }
        return c
    }

    private fun chips() {
        factorRow.removeAllViews()
        for (f in listOf(2, 3, 4)) factorRow.addView(chip(bn(f.toString()) + "×", f == factor) { factor = f; chips(); recompute() }, lp(r = 8))
        sharpRow.removeAllViews()
        sharpRow.addView(chip(tr("চালু"), sharpen) { sharpen = true; chips(); recompute() }, lp(r = 8))
        sharpRow.addView(chip(tr("বন্ধ"), !sharpen) { sharpen = false; chips(); recompute() })
    }

    override fun onImageLoaded() {
        info.text = srcName + " · " + bn(bmp!!.width.toString()) + "×" + bn(bmp!!.height.toString())
        recompute()
    }

    private fun recompute() {
        val src = bmp ?: return
        val my = ++gen
        result.text = tr("হিসাব চলছে…")
        val f = factor
        val sh = sharpen
        Thread {
            var res: Bitmap? = null
            var note = ""
            try {
                var k = f
                // keep the output within about 16 megapixels so memory stays safe
                while (k > 1 && src.width.toLong() * k * src.height * k > 16_000_000L) k--
                if (k < f) note = tr("\n(মেমরির কারণে ") + bn(k.toString()) + tr("× করা হয়েছে)")
                if (k <= 1) {
                    note = tr("\nছবিটি আগেই যথেষ্ট বড়।")
                    res = src
                } else {
                    val big = Bitmap.createScaledBitmap(src, src.width * k, src.height * k, true)
                    res = if (sh) sharpened(big) else big
                }
            } catch (e: Throwable) {
                res = null
            }
            runOnUiThread {
                if (my != gen) return@runOnUiThread
                out = res
                if (res == null) {
                    result.text = tr("ছবিটি বড় করা যায়নি (মেমরি কম)")
                } else {
                    preview.setImageBitmap(res)
                    result.text = tr("নতুন মাপ: ") + bn(res.width.toString()) + "×" + bn(res.height.toString()) + note
                }
            }
        }.start()
    }

    /** Light unsharp-style 3x3 sharpen. */
    private fun sharpened(b: Bitmap): Bitmap {
        val w = b.width
        val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        val o = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    o[i] = px[i]
                    continue
                }
                val c = px[i]
                var r = 0
                var g = 0
                var bl = 0
                val n = intArrayOf(px[i - 1], px[i + 1], px[i - w], px[i + w])
                for (p in n) {
                    r += Color.red(p)
                    g += Color.green(p)
                    bl += Color.blue(p)
                }
                // centre*1.8 - neighbours*0.2 each (weights sum to 1)
                val nr = clamp((Color.red(c) * 18 - r * 2) / 10)
                val ng = clamp((Color.green(c) * 18 - g * 2) / 10)
                val nb = clamp((Color.blue(c) * 18 - bl * 2) / 10)
                o[i] = Color.argb(Color.alpha(c), nr, ng, nb)
            }
        }
        val r = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        r.setPixels(o, 0, w, 0, 0, w, h)
        b.recycle()
        return r
    }

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

    private fun save(fmt: Int) {
        val b = out
        if (b == null) {
            Toast.makeText(this, tr("আগে একটি ছবি বাছুন"), Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val bytes = try { Img.encode(b, fmt, 92) } catch (e: Throwable) { null }
            runOnUiThread {
                if (bytes == null) Toast.makeText(this, tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
                else askSave(bytes, baseName() + "_x" + factor + (if (fmt == 0) ".png" else ".jpg"), if (fmt == 0) "image/png" else "image/jpeg")
            }
        }.start()
    }
}
