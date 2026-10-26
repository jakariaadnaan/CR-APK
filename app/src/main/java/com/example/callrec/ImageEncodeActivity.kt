package com.example.callrec

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/** Shared screen for the format converter and the size reducer. */
abstract class ImageEncodeActivity(private val compress: Boolean) : ImageToolBase() {

    private var fmt = if (compress) 1 else 0
    private var quality = if (compress) 70 else 90
    private var scalePct = 100
    private var gen = 0
    private var out: ByteArray? = null

    private lateinit var fmtRow: LinearLayout
    private lateinit var scaleRow: LinearLayout
    private lateinit var qLabel: TextView
    private lateinit var qBar: SeekBar
    private lateinit var result: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(pickCard(), lp(b = 12))

        box.addView(sectionLabel(if (compress) tr("সংকোচনের ধরন") else tr("আউটপুট অপশন")))
        fmtRow = LinearLayout(this)
        fmtRow.orientation = LinearLayout.HORIZONTAL
        box.addView(fmtRow, lp(b = 10))

        val opt = card(14)
        qLabel = label("", 12f, Ui.MUTED, true)
        opt.addView(qLabel)
        qBar = SeekBar(this)
        qBar.max = 95 - 10
        qBar.progress = quality - 10
        qBar.progressTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        qBar.thumbTintList = android.content.res.ColorStateList.valueOf(Ui.PRIMARY)
        qBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                quality = p + 10
                updateLabels()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) { recompute() }
        })
        opt.addView(qBar, lp(b = 6))

        if (compress) {
            opt.addView(label(tr("ছবির মাপ"), 12f, Ui.MUTED, true), lp(t = 6))
            scaleRow = LinearLayout(this)
            scaleRow.orientation = LinearLayout.HORIZONTAL
            opt.addView(scaleRow, lp(t = 6))
        }
        box.addView(opt, lp(b = 12))

        val res = resultBox(if (compress) tr("আনুমানিক সাইজ") else tr("আউটপুট"), tr("আগে একটি ছবি বাছুন"), "", 16f)
        result = res.getChildAt(1) as TextView
        box.addView(res, lp())

        screenWithBottom(
            if (compress) tr("ফাইল সাইজ কমানো") else tr("ফরম্যাট কনভার্টার"), scroll,
            primaryButton(if (compress) tr("সাইজ কমিয়ে সংরক্ষণ") else tr("রূপান্তর করে সংরক্ষণ")) { save() }
        )
        buildChips()
        updateLabels()
    }

    private fun fmtName(i: Int) = when (i) { 0 -> "PNG"; 1 -> "JPG"; else -> "WebP" }
    private fun ext(i: Int) = when (i) { 0 -> ".png"; 1 -> ".jpg"; else -> ".webp" }
    private fun mime(i: Int) = when (i) { 0 -> "image/png"; 1 -> "image/jpeg"; else -> "image/webp" }

    private fun chip(text: String, on: Boolean, click: () -> Unit): TextView = choiceChip(text, on, click)

    private fun buildChips() {
        fmtRow.removeAllViews()
        val list = if (compress) listOf(1, 2) else listOf(0, 1, 2)
        for (i in list) fmtRow.addView(chip(fmtName(i), i == fmt) { fmt = i; buildChips(); updateLabels(); recompute() }, lp(r = 8))
        if (compress) {
            scaleRow.removeAllViews()
            for (p in listOf(100, 75, 50, 25)) {
                scaleRow.addView(chip(bn(p.toString()) + "%", p == scalePct) { scalePct = p; buildChips(); recompute() }, lp(r = 8))
            }
        }
    }

    private fun updateLabels() {
        val lossy = fmt != 0
        qBar.isEnabled = lossy
        qBar.alpha = if (lossy) 1f else 0.4f
        qLabel.text = if (lossy) tr("মান: ") + bn(quality.toString()) + tr("% (কম মান = ছোট ফাইল)") else tr("PNG-তে মান কমানো যায় না (লসলেস)")
    }

    override fun onImageLoaded() {
        info.text = srcName + " · " + bn(bmp!!.width.toString()) + "×" + bn(bmp!!.height.toString()) + " · " + Img.human(srcSize)
        recompute()
    }

    private fun recompute() {
        val src: Bitmap = bmp ?: return
        val my = ++gen
        result.text = tr("হিসাব চলছে…")
        val f = fmt
        val q = quality
        val s = scalePct
        Thread {
            var bytes: ByteArray? = null
            try {
                val b = if (s < 100) Bitmap.createScaledBitmap(src, Math.max(1, src.width * s / 100), Math.max(1, src.height * s / 100), true) else src
                bytes = Img.encode(b, f, q)
                if (b !== src) b.recycle()
            } catch (e: Throwable) {
            }
            runOnUiThread {
                if (my != gen) return@runOnUiThread
                out = bytes
                if (bytes == null) {
                    result.text = tr("ছবিটি রূপান্তর করা যায়নি")
                } else {
                    var t = tr("নতুন ফাইল: ") + fmtName(f) + " · " + Img.human(bytes.size.toLong())
                    if (srcSize > 0) {
                        val pct = (bytes.size * 100.0 / srcSize)
                        t += if (pct <= 100) tr("\nমূলের ") + bn(Math.round(pct).toString()) + "% (" + bn(Math.round(100 - pct).toString()) + tr("% কম)")
                        else tr("\nমূলের চেয়ে বড় (") + bn(Math.round(pct).toString()) + "%)"
                    }
                    result.text = t
                }
            }
        }.start()
    }

    private fun save() {
        val b = out
        if (b == null) {
            Toast.makeText(this, tr("আগে একটি ছবি বাছুন"), Toast.LENGTH_SHORT).show()
            return
        }
        askSave(b, baseName() + (if (compress) "_small" else "_converted") + ext(fmt), mime(fmt))
    }
}

class ImageFormatActivity : ImageEncodeActivity(false)
class ImageCompressActivity : ImageEncodeActivity(true)
