package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast

private val STAR = Color.parseColor("#E0A100")

fun Activity.openTool(t: Tool) {
    val target = t.target
    if (target == null) {
        Toast.makeText(this, "এই টুলটি শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
    } else {
        startActivity(Intent(this, target))
    }
}

/** A list row for one tool. Tap opens it, long press adds or removes it from the favourites. */
fun Activity.toolRow(t: Tool, onFavChanged: () -> Unit): View {
    val cat = Registry.cat(t.cat)
    val ready = t.target != null
    val c = card(12)
    c.orientation = LinearLayout.HORIZONTAL
    c.gravity = Gravity.CENTER_VERTICAL

    c.addView(iconBox(cat.icon, cat.tint, cat.fg, 44, 24, 14, 12))

    val mid = LinearLayout(this)
    mid.orientation = LinearLayout.VERTICAL
    mid.addView(label(t.title, 16f, if (ready) Ui.TEXT else Ui.MUTED, true))
    val sub = if (t.online) t.sub + " · ইন্টারনেট লাগে" else t.sub
    mid.addView(label(sub, 13f, Ui.MUTED))
    c.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

    if (Registry.isFav(this, t.id)) {
        val star = iconView(R.drawable.ic_star, 18, STAR)
        c.addView(star)
    }
    if (ready) {
        val ch = iconView(R.drawable.ic_chevron, 20, Ui.MUTED)
        val p = LinearLayout.LayoutParams(dp(20), dp(20))
        p.setMargins(dp(8), 0, 0, 0)
        ch.layoutParams = p
        c.addView(ch)
    } else {
        val soon = label("শীঘ্রই", 11f, Ui.MUTED, true)
        soon.background = bg(Ui.BG, 10)
        soon.setPadding(dp(8), dp(3), dp(8), dp(3))
        c.addView(soon)
    }

    c.setOnClickListener { openTool(t) }
    c.setOnLongClickListener {
        if (!ready) {
            Toast.makeText(this, "এই টুলটি এখনো তৈরি হয়নি", Toast.LENGTH_SHORT).show()
        } else {
            val now = Registry.toggleFav(this, t.id)
            Toast.makeText(this, if (now) "প্রিয় টুলে যোগ হয়েছে" else "প্রিয় টুল থেকে সরানো হয়েছে", Toast.LENGTH_SHORT).show()
            onFavChanged()
        }
        true
    }
    return c
}
