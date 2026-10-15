package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.View
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
    val sub = if (t.online) t.sub + " · ইন্টারনেট লাগে" else t.sub
    var extra: View? = null
    if (Registry.isFav(this, t.id)) {
        extra = iconView(R.drawable.ic_star, 18, STAR)
    } else if (!ready) {
        val soon = label("শীঘ্রই", 11f, Ui.MUTED, true)
        soon.background = bg(Ui.BG, 10)
        soon.setPadding(dp(8), dp(3), dp(8), dp(3))
        extra = soon
    }
    val c = listRow(cat.icon, t.title, sub, if (ready) Ui.TEXT else Ui.MUTED, extra, ready)
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
