package com.example.callrec

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Home screen: a grid of tools. Tools that are not built yet say "শীঘ্রই". */
class HomeActivity : Activity() {

    private class Tile(val icon: String, val title: String, val sub: String, val target: Class<*>?)

    private val tiles = listOf(
        Tile("📒", "খরচের খাতা", "আয়-ব্যয় ও মাসিক হিসাব", LedgerActivity::class.java),
        Tile("🧾", "বাকির খাতা", "কে কত দেবে / পাবে", DebtActivity::class.java),
        Tile("✅", "করণীয়", "দিনের কাজের তালিকা", TodoActivity::class.java),
        Tile("📝", "দ্রুত নোট", "নোট, নম্বর, আইডিয়া", NotesActivity::class.java),
        Tile("🧮", "ক্যালকুলেটর", "হিসাব ও ইউনিট রূপান্তর", CalcActivity::class.java),
        Tile("🌾", "জমির মাপ", "শতাংশ, কাঠা, বিঘা", LandActivity::class.java),
        Tile("💬", "টাকা কথায়", "চেক ও রশিদের জন্য", WordsActivity::class.java),
        Tile("📅", "তারিখ ও বয়স", "বাংলা, হিজরি, বয়স", DateActivity::class.java),
        Tile("⏱️", "টাইমার", "পমোডোরো, স্টপওয়াচ", null),
        Tile("🔑", "পাসওয়ার্ড", "শক্তিশালী পাসওয়ার্ড", null),
        Tile("🎙️", "কল রেকর্ডিং", "টেস্ট স্ক্রিন", MainActivity::class.java)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(12), dp(14), dp(12), dp(20))
        scroll.addView(box)

        var i = 0
        while (i < tiles.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val w = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                w.setMargins(dp(4), dp(4), dp(4), dp(4))
                if (i + j < tiles.size) {
                    row.addView(tileView(tiles[i + j]), w)
                } else {
                    row.addView(View(this), w)
                }
            }
            box.addView(row)
            i += 2
        }

        setScreen("আমার খাতা", scroll, back = false)
    }

    private fun tileView(t: Tile): View {
        val ready = t.target != null
        val c = card(16)
        c.gravity = Gravity.START
        c.minimumHeight = dp(112)
        c.addView(label(t.icon, 28f))
        c.addView(label(t.title, 16f, if (ready) Ui.TEXT else Ui.MUTED, true), lp(t = 8))
        c.addView(label(if (ready) t.sub else "শীঘ্রই আসছে", 12f, Ui.MUTED))
        if (!ready) c.alpha = 0.6f
        c.setOnClickListener {
            val target = t.target
            if (target == null) {
                Toast.makeText(this, "এই অংশটি শীঘ্রই আসছে", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(this, target))
            }
        }
        return c
    }
}
