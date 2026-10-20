package com.example.callrec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** A small smooth line chart with a filled area and a bubble on the highest point. */
class ActivityChart(ctx: Context) : View(ctx) {
    var values: IntArray = IntArray(0)
    var bubbleText: String = ""
    var color: Int = Color.parseColor("#6D3FE0")
    var bubbleBg: Int = Color.WHITE
    var bubbleFg: Int = Color.BLACK

    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val n = values.size
        if (n < 2) return
        val d = context.resources.displayMetrics.density
        val padT = 34f * d
        val padB = 8f * d
        val padX = 8f * d
        val w = width - padX * 2
        val h = height - padT - padB
        val max = Math.max(1, values.max())
        val xs = FloatArray(n) { padX + w * it / (n - 1) }
        val ys = FloatArray(n) { padT + h * (1f - values[it].toFloat() / max) }

        val path = Path()
        path.moveTo(xs[0], ys[0])
        for (i in 1 until n) {
            val mx = (xs[i - 1] + xs[i]) / 2f
            path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i])
        }
        val area = Path(path)
        area.lineTo(xs[n - 1], height - padB)
        area.lineTo(xs[0], height - padB)
        area.close()
        fill.style = Paint.Style.FILL
        fill.shader = LinearGradient(0f, padT, 0f, height.toFloat(), Color.argb(90, Color.red(color), Color.green(color), Color.blue(color)), Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Shader.TileMode.CLAMP)
        canvas.drawPath(area, fill)

        line.style = Paint.Style.STROKE
        line.strokeWidth = 2.2f * d
        line.strokeCap = Paint.Cap.ROUND
        line.color = color
        canvas.drawPath(path, line)

        // dots only when there are few points, so a 30 day chart stays clean
        if (n <= 10) {
            dot.style = Paint.Style.FILL
            for (i in 0 until n) {
                dot.color = Color.WHITE
                canvas.drawCircle(xs[i], ys[i], 4.2f * d, dot)
                dot.color = color
                canvas.drawCircle(xs[i], ys[i], 2.6f * d, dot)
            }
        }

        var mi = 0
        for (i in 0 until n) if (values[i] >= values[mi]) mi = i
        if (values[mi] > 0 && bubbleText.isNotEmpty()) {
            dot.color = color
            canvas.drawCircle(xs[mi], ys[mi], 4.5f * d, dot)
            txt.textSize = 10f * d
            txt.isFakeBoldText = true
            val parts = bubbleText.split("\n")
            var tw = 0f
            for (p in parts) tw = Math.max(tw, txt.measureText(p))
            val bw = tw + 14f * d
            val bh = (if (parts.size > 1) 30f else 18f) * d
            var left = xs[mi] - bw / 2f
            if (left < 0f) left = 0f
            if (left + bw > width) left = width - bw
            val top = Math.max(0f, ys[mi] - bh - 8f * d)
            dot.color = bubbleBg
            canvas.drawRoundRect(RectF(left, top, left + bw, top + bh), 8f * d, 8f * d, dot)
            txt.color = bubbleFg
            var ty = top + 12.5f * d
            for (p in parts) {
                canvas.drawText(p, left + (bw - txt.measureText(p)) / 2f, ty, txt)
                ty += 12f * d
            }
        }
    }
}
