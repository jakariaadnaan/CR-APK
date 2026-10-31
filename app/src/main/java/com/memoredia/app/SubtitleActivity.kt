package com.memoredia.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import java.util.Locale

/** ভিডিও সাবটাইটেল: play a video, tap at the start of every line you typed, save the result as an .srt file. */
class SubtitleActivity : GuardedActivity() {

    private var video: Uri? = null
    private var videoName = "subtitles"
    private lateinit var vv: VideoView
    private lateinit var script: EditText
    private lateinit var panel: LinearLayout
    private lateinit var barBox: LinearLayout
    private lateinit var info: TextView

    private var lines: List<String> = emptyList()
    private var starts = LongArray(0)
    private var cur = 0
    private var syncing = false
    private var scriptText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(16), dp(10), dp(16), dp(20))
        scroll.addView(box)

        box.addView(captionLabel(tr("ভিডিও")))
        val c = card(8)
        val fr = FrameLayout(this)
        vv = VideoView(this)
        fr.addView(vv, FrameLayout.LayoutParams(-1, dp(210), Gravity.CENTER))
        c.addView(fr)
        info = label(tr("কোনো ভিডিও বাছা হয়নি। নিচের বাটনে বাছুন।"), 12f, Ui.MUTED)
        info.gravity = Gravity.CENTER
        c.addView(info, lp(t = 6, b = 2))
        box.addView(c, lp(b = 12))

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        box.addView(panel, lp())

        barBox = LinearLayout(this)
        barBox.orientation = LinearLayout.HORIZONTAL
        setScreen(tr("ভিডিও সাবটাইটেল"), scroll, null, true, bottomBar(barBox))
        render()
    }

    override fun onPause() {
        super.onPause()
        try { if (vv.isPlaying) vv.pause() } catch (e: Throwable) { }
    }

    private fun pickVideo() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "video/*"
        startActivityForResult(i, 91)
    }

    private fun splitLines(t: String): List<String> {
        var l = t.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (l.size == 1) l = l[0].split(Regex("(?<=[।.?!])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        return l
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
    }

    private fun srtTime(ms: Long): String {
        val t = Math.max(0L, ms)
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", t / 3600000, t / 60000 % 60, t / 1000 % 60, t % 1000)
    }

    private fun buildSrt(): String {
        val sb = StringBuilder()
        for (i in lines.indices) {
            val st = starts[i]
            val room = Math.max(3000L, lines[i].length * 90L)
            val end = if (i < lines.size - 1) Math.min(starts[i + 1] - 80, st + room) else st + room
            sb.append(i + 1).append('\n')
            sb.append(srtTime(st)).append(" --> ").append(srtTime(Math.max(end, st + 300))).append('\n')
            sb.append(lines[i]).append("\n\n")
        }
        return sb.toString()
    }

    private fun done() = syncing && cur >= lines.size

    private fun render() {
        panel.removeAllViews()
        barBox.removeAllViews()
        if (!syncing) {
            script = EditText(this)
            script.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            script.minLines = 7
            script.gravity = Gravity.TOP or Gravity.START
            script.hint = tr("ভিডিওতে যা বলা হয়েছে তা এখানে লিখুন। প্রতিটি সাবটাইটেল আলাদা লাইনে দিন।")
            script.setText(scriptText)
            styleInput(script)
            panel.addView(captionLabel(tr("ভিডিওর কথা")))
            panel.addView(script, lp(b = 8))
            panel.addView(label(tr("অ্যাপ নিজে কথা শুনে সময় বসায় না। ভিডিও চালিয়ে প্রতিটি লাইনের শুরুতে আপনি বোতাম চাপবেন, অ্যাপ সময় বসিয়ে .srt বানাবে।"), 11f, Ui.MUTED), lp(l = 2))
            barBox.addView(outlineButton(tr("ভিডিও বাছুন")) { pickVideo() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.rightMargin = dp(8) })
            barBox.addView(primaryButton(tr("মেলানো শুরু করুন")) { begin() }, LinearLayout.LayoutParams(0, dp(48), 1f))
            return
        }
        if (done()) {
            panel.addView(resultBox(tr("মেলানো শেষ"), bn(lines.size.toString()) + tr(" টি লাইন"), tr("ঠিক না লাগলে নিচের কোনো লাইনে চাপ দিয়ে আবার সময় বসান।"), 22f), lp(b = 12))
        } else {
            val rb = resultBox(tr("লাইন ") + bn((cur + 1).toString()) + "/" + bn(lines.size.toString()), lines[cur], tr("এই কথাটা শুরু হওয়ার মুহূর্তে নিচের বোতাম চাপুন"), 20f)
            panel.addView(rb, lp(b = 10))
        }
        val ctl = LinearLayout(this)
        ctl.addView(outlineButton(tr("৩ সেকেন্ড পিছনে")) { try { vv.seekTo(Math.max(0, vv.currentPosition - 3000)) } catch (e: Throwable) { } }, LinearLayout.LayoutParams(0, dp(44), 1f).also { it.rightMargin = dp(8) })
        ctl.addView(outlineButton(if (vv.isPlaying) tr("থামান") else tr("চালান")) {
            try { if (vv.isPlaying) vv.pause() else vv.start() } catch (e: Throwable) { }
            render()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).also { it.rightMargin = dp(8) })
        ctl.addView(outlineButton(tr("লেখা বদলান")) { syncing = false; render() }, LinearLayout.LayoutParams(0, dp(44), 1f))
        panel.addView(ctl, lp(b = 10))

        panel.addView(sectionLabel(tr("লাইনের তালিকা"), 4))
        val lc = card(6)
        for (i in lines.indices) {
            val r = LinearLayout(this)
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(4), dp(9), dp(4), dp(9))
            val marked = i < cur || (done() && starts[i] >= 0)
            val t = label(if (marked && starts[i] >= 0) clock(starts[i]) else "--:--", 13f, if (i == cur && !done()) Ui.PRIMARY else Ui.MUTED, true)
            r.addView(t, lp(width = dp(52)))
            val tx = label(lines[i], 14f, if (i == cur && !done()) Ui.PRIMARY else Ui.TEXT, i == cur && !done())
            tx.maxLines = 2
            tx.ellipsize = android.text.TextUtils.TruncateAt.END
            r.addView(tx, LinearLayout.LayoutParams(0, -2, 1f))
            if (i < cur) {
                r.setOnClickListener {
                    cur = i
                    try { vv.seekTo(Math.max(0L, starts[i] - 1500).toInt()) } catch (e: Throwable) { }
                    render()
                }
            }
            lc.addView(r)
            if (i < lines.size - 1) lc.addView(android.view.View(this).also { it.setBackgroundColor(Ui.LINE) }, lp(height = 1))
        }
        panel.addView(lc, lp())

        if (done()) {
            barBox.addView(outlineButton(tr("আবার শুরু")) { cur = 0; try { vv.seekTo(0) } catch (e: Throwable) { }; render() }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.rightMargin = dp(8) })
            barBox.addView(primaryButton(tr("SRT সংরক্ষণ")) { save() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        } else {
            barBox.addView(outlineButton(tr("পিছনে")) {
                if (cur > 0) { cur--; render() }
            }, LinearLayout.LayoutParams(0, dp(48), 1f).also { it.rightMargin = dp(8) })
            barBox.addView(primaryButton(tr("এই লাইন শুরু")) { mark() }, LinearLayout.LayoutParams(0, dp(48), 2f))
        }
    }

    private fun begin() {
        scriptText = script.text.toString()
        lines = splitLines(scriptText)
        if (video == null) { Toast.makeText(this, tr("আগে ভিডিও বাছুন"), Toast.LENGTH_SHORT).show(); return }
        if (lines.isEmpty()) { Toast.makeText(this, tr("আগে ভিডিওর কথা লিখুন"), Toast.LENGTH_SHORT).show(); return }
        starts = LongArray(lines.size) { -1L }
        cur = 0
        syncing = true
        try { vv.seekTo(0); vv.start() } catch (e: Throwable) { }
        render()
    }

    private fun mark() {
        if (cur >= lines.size) return
        val p = try { vv.currentPosition.toLong() } catch (e: Throwable) { 0L }
        if (cur > 0 && p <= starts[cur - 1]) {
            Toast.makeText(this, tr("আগের লাইনের পরের সময়ে চাপুন"), Toast.LENGTH_SHORT).show()
            return
        }
        starts[cur] = p
        cur++
        render()
    }

    private fun save() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "application/x-subrip"
        i.putExtra(Intent.EXTRA_TITLE, videoName + ".srt")
        saveOrAsk(i, 92)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val u = data?.data ?: return
        if (requestCode == 91) {
            video = u
            var n = "subtitles"
            try {
                contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) n = it.getString(0) ?: n
                }
            } catch (e: Throwable) { }
            videoName = n.substringBeforeLast('.').ifEmpty { "subtitles" }
            vv.setVideoURI(u)
            vv.setOnPreparedListener { mp ->
                info.text = videoName + " · " + clock(mp.duration.toLong())
                vv.seekTo(1)
            }
            vv.setOnErrorListener { _, _, _ -> info.text = tr("এই ভিডিও চালানো যায়নি"); true }
            Toast.makeText(this, tr("ভিডিও বাছা হয়েছে"), Toast.LENGTH_SHORT).show()
        } else if (requestCode == 92) {
            try {
                contentResolver.openOutputStream(u)?.use { it.write(buildSrt().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, tr("SRT ফাইল সংরক্ষিত হয়েছে"), Toast.LENGTH_LONG).show()
            } catch (e: Throwable) {
                Toast.makeText(this, tr("সংরক্ষণ করা যায়নি"), Toast.LENGTH_LONG).show()
            }
        }
    }
}
