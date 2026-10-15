package com.example.callrec

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** Home: search, favourite tools and the category cards. */
class HomeActivity : GuardedActivity() {

    private lateinit var content: LinearLayout
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.PRIMARY

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        // ---- header with search
        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        val r = dp(24).toFloat()
        val hb = GradientDrawable()
        hb.setColor(Ui.PRIMARY)
        hb.cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        header.background = hb
        header.setPadding(dp(20), dp(22), dp(20), dp(22))
        header.elevation = dp(3).toFloat()
        header.addView(label("আমার খাতা", 26f, Color.WHITE, true))
        header.addView(label("সব দরকারি টুল এক জায়গায়", 14f, Color.parseColor("#D4EBDF")), lp(b = 14))

        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = bg(Color.WHITE, 14)
        box.setPadding(dp(14), 0, dp(14), 0)
        box.addView(iconView(R.drawable.ic_search, 20, Ui.MUTED))
        search = EditText(this)
        search.hint = "টুল খুঁজুন, যেমন: PDF, QR, ছবি"
        search.textSize = 15f
        search.setTextColor(Ui.TEXT)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true
        search.background = null
        search.setPadding(dp(10), 0, 0, 0)
        search.onChange { render() }
        box.addView(search, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(box, lp(height = 48))
        root.addView(header, lp())

        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(16), dp(16), dp(20))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(navBar(0), lp())
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val q = search.text.toString()
        if (q.isNotBlank()) {
            val found = Registry.tools.filter { Registry.matches(it, q) }
            content.addView(label("ফলাফল (" + bn(found.size.toString()) + ")", 15f, Ui.MUTED, true), lp(l = 4, b = 8))
            if (found.isEmpty()) {
                val e = label("কিছু পাওয়া যায়নি।", 14f, Ui.MUTED)
                e.gravity = Gravity.CENTER
                e.setPadding(0, dp(30), 0, dp(30))
                content.addView(e, lp())
            }
            for (t in found) content.addView(toolRow(t) { render() }, lp(b = 8))
            return
        }

        // ---- favourites
        val favs = Registry.favIds(this).mapNotNull { id -> Registry.tools.firstOrNull { it.id == id } }
        content.addView(label("প্রিয় টুল", 16f, Ui.TEXT, true), lp(l = 2, b = 10))
        if (favs.isEmpty()) {
            content.addView(label("কোনো টুলে চেপে ধরে রাখলে সেটি এখানে যোগ হবে।", 13f, Ui.MUTED), lp(l = 2, b = 6))
        } else {
            val grid = LinearLayout(this)
            grid.orientation = LinearLayout.HORIZONTAL
            val shown = favs.take(4)
            for (t in shown) grid.addView(favTile(t), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            for (i in shown.size until 4) grid.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(grid, lp(b = 6))
        }

        // ---- categories
        content.addView(label("ক্যাটাগরি", 16f, Ui.TEXT, true), lp(l = 2, t = 14, b = 10))
        var i = 0
        while (i < Registry.cats.size) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (j in 0..1) {
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.setMargins(dp(if (j == 0) 0 else 6), 0, dp(if (j == 0) 6 else 0), dp(12))
                if (i + j < Registry.cats.size) row.addView(catCard(Registry.cats[i + j]), p) else row.addView(View(this), p)
            }
            content.addView(row, lp())
            i += 2
        }
        content.addView(label("প্রিয় করতে বা সরাতে যেকোনো টুলে চেপে ধরে রাখুন।", 12f, Ui.MUTED), lp(l = 2, t = 4))
    }

    private fun favTile(t: Tool): View {
        val cat = Registry.cat(t.cat)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        val box = iconBox(cat.icon, cat.tint, cat.fg, 52, 26, 16, 0)
        col.addView(box)
        val name = label(t.title, 12f, Ui.TEXT)
        name.gravity = Gravity.CENTER
        name.maxLines = 2
        col.addView(name, lp(t = 6))
        col.setOnClickListener { openTool(t) }
        col.setOnLongClickListener {
            Registry.toggleFav(this, t.id)
            render()
            true
        }
        return col
    }

    private fun catCard(c: Cat): View {
        val card = card(12)
        card.orientation = LinearLayout.HORIZONTAL
        card.gravity = Gravity.CENTER_VERTICAL
        card.minimumHeight = dp(72)
        card.addView(iconBox(c.icon, c.tint, c.fg, 38, 22, 12, 10))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(c.name, 14f, Ui.TEXT, true))
        col.addView(label(bn(Registry.inCat(c.id).size.toString()) + "টি টুল", 12f, Ui.MUTED))
        card.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.setOnClickListener {
            startActivity(Intent(this, ToolListActivity::class.java).putExtra("cat", c.id))
        }
        return card
    }
}
