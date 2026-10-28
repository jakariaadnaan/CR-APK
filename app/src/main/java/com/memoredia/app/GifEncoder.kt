package com.memoredia.app

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Small GIF89a writer. Uses a fixed 252-colour palette (6 red x 7 green x 6 blue levels)
 * with light ordered dithering, so frames can be written one at a time without keeping them in memory.
 */
class GifEncoder(private val out: OutputStream, private val w: Int, private val h: Int, private val delayMs: Int) {

    private var started = false
    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    private fun u16(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun header() {
        out.write("GIF89a".toByteArray())
        u16(w); u16(h)
        out.write(0xF7) // global table, 8 bits per colour, 256 entries
        out.write(0)
        out.write(0)
        for (i in 0 until 256) {
            if (i < 252) {
                val r = i / 42
                val g = (i / 6) % 7
                val b = i % 6
                out.write(r * 255 / 5)
                out.write(g * 255 / 6)
                out.write(b * 255 / 5)
            } else {
                out.write(0); out.write(0); out.write(0)
            }
        }
        // loop forever
        out.write(0x21); out.write(0xFF); out.write(11)
        out.write("NETSCAPE2.0".toByteArray())
        out.write(3); out.write(1); u16(0); out.write(0)
        started = true
    }

    /** [bmp] must be exactly w x h. */
    fun addFrame(bmp: Bitmap) {
        if (!started) header()
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val idx = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                val a = Color.alpha(c)
                var r = Color.red(c)
                var g = Color.green(c)
                var b = Color.blue(c)
                if (a < 255) {
                    r = (r * a + 255 * (255 - a)) / 255
                    g = (g * a + 255 * (255 - a)) / 255
                    b = (b * a + 255 * (255 - a)) / 255
                }
                val d = bayer[(y and 3) * 4 + (x and 3)] * 2 - 15 // -15..15
                r = (r + d * 51 / 16).coerceIn(0, 255)
                g = (g + d * 42 / 16).coerceIn(0, 255)
                b = (b + d * 51 / 16).coerceIn(0, 255)
                val ri = (r * 5 + 127) / 255
                val gi = (g * 6 + 127) / 255
                val bi = (b * 5 + 127) / 255
                idx[y * w + x] = (ri * 42 + gi * 6 + bi).toByte()
            }
        }
        // graphic control extension
        out.write(0x21); out.write(0xF9); out.write(4)
        out.write(0)
        u16(Math.max(2, delayMs / 10))
        out.write(0); out.write(0)
        // image descriptor
        out.write(0x2C)
        u16(0); u16(0); u16(w); u16(h)
        out.write(0)
        out.write(8)
        val data = lzw(idx)
        var p = 0
        while (p < data.size) {
            val n = Math.min(255, data.size - p)
            out.write(n)
            out.write(data, p, n)
            p += n
        }
        out.write(0)
    }

    fun finish() {
        if (started) out.write(0x3B)
        out.flush()
    }

    // ---- LZW (GIF flavour) ----
    private class Bits {
        val buf = ByteArrayOutputStream()
        var cur = 0
        var n = 0
        fun put(code: Int, bits: Int) {
            cur = cur or (code shl n)
            n += bits
            while (n >= 8) {
                buf.write(cur and 0xFF)
                cur = cur ushr 8
                n -= 8
            }
        }
        fun flush() {
            if (n > 0) buf.write(cur and 0xFF)
            cur = 0; n = 0
        }
    }

    private fun lzw(pix: ByteArray): ByteArray {
        val clear = 256
        val eoi = 257
        val bo = Bits()
        var bits = 9
        var next = 258
        var clearFlag = false
        val dict = HashMap<Int, Int>()

        fun emit(code: Int) {
            bo.put(code, bits)
            if (clearFlag) {
                bits = 9
                clearFlag = false
            } else if (next > (1 shl bits) - 1 && bits < 12) {
                bits++
            }
        }

        clearFlag = true
        emit(clear)
        var cur = pix[0].toInt() and 0xFF
        for (i in 1 until pix.size) {
            val c = pix[i].toInt() and 0xFF
            val key = (cur shl 8) or c
            val v = dict[key]
            if (v != null) {
                cur = v
            } else {
                emit(cur)
                if (next < 4096) {
                    dict[key] = next++
                } else {
                    dict.clear()
                    next = 258
                    clearFlag = true
                    emit(clear)
                }
                cur = c
            }
        }
        emit(cur)
        emit(eoi)
        bo.flush()
        return bo.buf.toByteArray()
    }
}
