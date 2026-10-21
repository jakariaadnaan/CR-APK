package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * অডিও কনভার্টার and ভিডিও → অডিও. Uses only Android's own codecs: output is WAV or M4A (AAC).
 * A video whose sound is already AAC is copied out as M4A without re-encoding.
 */
abstract class AudioConvertBase(private val video: Boolean) : GuardedActivity() {

    private var src: Uri? = null
    private var name = "audio"
    private var wav = !video
    private var busy = false

    private lateinit var info: TextView
    private lateinit var fmtRow: LinearLayout
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(14), dp(14), dp(14), dp(24))
        scroll.addView(box)

        val c = card(14)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = android.view.Gravity.CENTER_VERTICAL
        row.addView(pill(if (video) tr("ভিডিও বাছুন") else tr("অডিও বাছুন")) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = if (video) "video/*" else "audio/*"
            startActivityForResult(i, 91)
        }, lp(r = 12))
        info = label(tr("কোনো ফাইল বাছা হয়নি"), 13f, Ui.MUTED)
        row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row, lp())
        box.addView(c, lp(b = 12))

        val opt = card(14)
        opt.addView(label(tr("আউটপুট ফরম্যাট"), 12f, Ui.MUTED, true))
        fmtRow = LinearLayout(this)
        fmtRow.orientation = LinearLayout.HORIZONTAL
        opt.addView(fmtRow, lp(t = 6, b = 8))
        opt.addView(label(tr("M4A (AAC) ছোট ফাইল, প্রায় সব প্লেয়ারে চলে। WAV অসংকুচিত, ফাইল অনেক বড়। MP3 এই সংস্করণে নেই।"), 12f, Ui.MUTED))
        box.addView(opt, lp(b = 12))

        val res = card(14)
        status = label("", 14f, Ui.TEXT)
        res.addView(status, lp(b = 10))
        res.addView(pill(tr("কনভার্ট ও সংরক্ষণ")) { go() })
        box.addView(res, lp())

        setScreen(if (video) tr("ভিডিও → অডিও") else tr("অডিও কনভার্টার"), scroll)
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
        fmtRow.removeAllViews()
        fmtRow.addView(chip("M4A (AAC)", !wav) { wav = false; chips() }, lp(r = 8))
        fmtRow.addView(chip("WAV", wav) { wav = true; chips() })
    }

    private fun go() {
        if (busy) return
        if (src == null) {
            Toast.makeText(this, tr("আগে একটি ফাইল বাছুন"), Toast.LENGTH_SHORT).show()
            return
        }
        val base = if (name.contains('.')) name.substring(0, name.lastIndexOf('.')) else name
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = if (wav) "audio/wav" else "audio/mp4"
        i.putExtra(Intent.EXTRA_TITLE, base + (if (wav) ".wav" else ".m4a"))
        saveOrAsk(i, 92)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK || data?.data == null) return
        val u = data.data!!
        if (requestCode == 91) {
            src = u
            name = Img.name(this, u)
            info.text = name + " · " + Img.human(Img.size(this, u))
            status.text = ""
        } else if (requestCode == 92) {
            run(u)
        }
    }

    private fun progress(p: Int) {
        runOnUiThread { status.text = tr("কনভার্ট হচ্ছে… ") + bn(p.toString()) + "%" }
    }

    private fun run(out: Uri) {
        val inUri = src ?: return
        val toWav = wav
        busy = true
        status.text = tr("কনভার্ট হচ্ছে…")
        Thread {
            var msg: String
            val tmp = File(cacheDir, "conv_out.tmp")
            val pcm = File(cacheDir, "conv_pcm.tmp")
            try {
                if (toWav) {
                    val fmt = decodePcm(inUri, pcm)
                    writeWav(pcm, tmp, fmt.first, fmt.second)
                } else if (isAac(inUri)) {
                    remux(inUri, tmp)
                } else {
                    val fmt = decodePcm(inUri, pcm)
                    encodeAac(pcm, tmp, fmt.first, fmt.second)
                }
                contentResolver.openOutputStream(out)?.use { os -> FileInputStream(tmp).use { it.copyTo(os) } }
                msg = tr("সংরক্ষিত হয়েছে (") + Img.human(tmp.length()) + ")"
                Usage.done(this, "AudioConvert", tmp.length())
            } catch (e: NoAudio) {
                msg = tr("এই ফাইলে কোনো অডিও ট্র্যাক নেই")
            } catch (e: Throwable) {
                msg = tr("কনভার্ট করা যায়নি")
            } finally {
                tmp.delete()
                pcm.delete()
            }
            runOnUiThread {
                busy = false
                status.text = msg
            }
        }.start()
    }

    private class NoAudio : Exception()

    private fun audioTrack(ex: MediaExtractor): Int {
        for (i in 0 until ex.trackCount) {
            val m = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (m.startsWith("audio/")) return i
        }
        throw NoAudio()
    }

    private fun isAac(u: Uri): Boolean {
        if (!video) return false
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(this, u, null)
            ex.getTrackFormat(audioTrack(ex)).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
        } catch (e: NoAudio) {
            throw e
        } finally {
            ex.release()
        }
    }

    /** Copies the AAC sound of a video into an M4A file with no re-encoding. */
    private fun remux(u: Uri, out: File) {
        val ex = MediaExtractor()
        ex.setDataSource(this, u, null)
        try {
            val t = audioTrack(ex)
            ex.selectTrack(t)
            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val idx = mux.addTrack(ex.getTrackFormat(t))
            mux.start()
            val buf = ByteBuffer.allocate(1 shl 20)
            val bi = MediaCodec.BufferInfo()
            val dur = try { ex.getTrackFormat(t).getLong(MediaFormat.KEY_DURATION) } catch (e: Throwable) { 0L }
            while (true) {
                val n = ex.readSampleData(buf, 0)
                if (n < 0) break
                bi.offset = 0
                bi.size = n
                bi.presentationTimeUs = ex.sampleTime
                bi.flags = ex.sampleFlags
                mux.writeSampleData(idx, buf, bi)
                if (dur > 0) progress((bi.presentationTimeUs * 100 / dur).toInt().coerceIn(0, 99))
                ex.advance()
            }
            mux.stop()
            mux.release()
        } finally {
            ex.release()
        }
    }

    /** Decodes the first audio track to raw 16-bit PCM in [pcm]; returns (sampleRate, channels). */
    private fun decodePcm(u: Uri, pcm: File): Pair<Int, Int> {
        val ex = MediaExtractor()
        ex.setDataSource(this, u, null)
        var codec: MediaCodec? = null
        try {
            val t = audioTrack(ex)
            ex.selectTrack(t)
            val f = ex.getTrackFormat(t)
            var sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val dur = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            codec = MediaCodec.createDecoderByType(f.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(f, null, null, 0)
            codec.start()
            val bi = MediaCodec.BufferInfo()
            var inDone = false
            var outDone = false
            BufferedOutputStream(FileOutputStream(pcm)).use { os ->
                while (!outDone) {
                    if (!inDone) {
                        val ii = codec.dequeueInputBuffer(10000)
                        if (ii >= 0) {
                            val b = codec.getInputBuffer(ii)!!
                            val n = ex.readSampleData(b, 0)
                            if (n < 0) {
                                codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0)
                                if (dur > 0) progress((ex.sampleTime * 50 / dur).toInt().coerceIn(0, 50))
                                ex.advance()
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(bi, 10000)
                    if (oi >= 0) {
                        if (bi.size > 0) {
                            val ob = codec.getOutputBuffer(oi)!!
                            val arr = ByteArray(bi.size)
                            ob.position(bi.offset)
                            ob.get(arr)
                            os.write(arr)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val nf = codec.outputFormat
                        sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            return Pair(sr, ch)
        } finally {
            try { codec?.stop() } catch (e: Throwable) {}
            codec?.release()
            ex.release()
        }
    }

    private fun writeWav(pcm: File, out: File, sr: Int, ch: Int) {
        val len = pcm.length()
        FileOutputStream(out).use { os ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()); h.putInt((36 + len).toInt()); h.put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()); h.putInt(16); h.putShort(1); h.putShort(ch.toShort())
            h.putInt(sr); h.putInt(sr * ch * 2); h.putShort((ch * 2).toShort()); h.putShort(16)
            h.put("data".toByteArray()); h.putInt(len.toInt())
            os.write(h.array())
            FileInputStream(pcm).use { it.copyTo(os) }
        }
    }

    private fun encodeAac(pcm: File, out: File, sr: Int, ch: Int) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, ch)
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 64000 * ch)
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 65536)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1
        var started = false
        val total = pcm.length()
        var read = 0L
        val bi = MediaCodec.BufferInfo()
        var inDone = false
        var outDone = false
        try {
            FileInputStream(pcm).use { ins ->
                while (!outDone) {
                    if (!inDone) {
                        val ii = codec.dequeueInputBuffer(10000)
                        if (ii >= 0) {
                            val b = codec.getInputBuffer(ii)!!
                            b.clear()
                            val tmp = ByteArray(b.capacity())
                            val n = ins.read(tmp)
                            if (n <= 0) {
                                codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inDone = true
                            } else {
                                b.put(tmp, 0, n)
                                val pts = read / (2L * ch) * 1_000_000L / sr
                                codec.queueInputBuffer(ii, 0, n, pts, 0)
                                read += n
                                if (total > 0) progress(50 + (read * 50 / total).toInt().coerceIn(0, 49))
                            }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(bi, 10000)
                    if (oi >= 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        if (bi.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) bi.size = 0
                        if (bi.size > 0 && started) {
                            ob.position(bi.offset)
                            ob.limit(bi.offset + bi.size)
                            mux.writeSampleData(track, ob, bi)
                        }
                        codec.releaseOutputBuffer(oi, false)
                        if (bi.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                    } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = mux.addTrack(codec.outputFormat)
                        mux.start()
                        started = true
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (e: Throwable) {}
            codec.release()
            try { if (started) mux.stop() } catch (e: Throwable) {}
            mux.release()
        }
    }
}

class AudioConvertActivity : AudioConvertBase(false)
class VideoToAudioActivity : AudioConvertBase(true)
