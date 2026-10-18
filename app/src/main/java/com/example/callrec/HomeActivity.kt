package com.example.callrec

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView

/** Home: big title, search, favourite tools and the category list. */
class HomeActivity : GuardedActivity() {

    private lateinit var content: LinearLayout
    private lateinit var search: EditText

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("settings", MODE_PRIVATE)
        if (!sp.getBoolean("onboarded", false)) {
            if (Lock.enabled(this)) {
                sp.edit().putBoolean("onboarded", true).apply()
            } else {
                startActivity(Intent(this, OnboardingActivity::class.java))
                finish()
                return
            }
        }
        DebtReminder.rescheduleAll(this)
        TodoReminder.rescheduleAll(this)
        PrayerReminder.scheduleAll(this)
        window.statusBarColor = Ui.BG
        window.navigationBarColor = Ui.NAV
        window.decorView.systemUiVisibility = if (Ui.dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.VERTICAL
        header.setPadding(dp(20), dp(22), dp(20), dp(6))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label(tr("আমার খাতা"), 32f, Ui.TEXT, true))
        titles.addView(label(tr("সব দরকারি টুল এক জায়গায়"), 15f, Ui.MUTED), lp(b = 4))
        top.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sBtn = LinearLayout(this)
        sBtn.gravity = Gravity.CENTER
        sBtn.background = bg(Ui.CARD, 24, Ui.LINE, 1)
        sBtn.addView(iconView(R.drawable.ic_search, 22, Ui.TEXT))
        top.addView(sBtn, lp(width = dp(48), height = dp(48), l = 12))
        header.addView(top, lp())

        val box = LinearLayout(this)
        box.orientation = LinearLayout.HORIZONTAL
        box.gravity = Gravity.CENTER_VERTICAL
        box.background = bg(Ui.CARD, 18, Ui.LINE, 1)
        box.setPadding(dp(16), 0, dp(16), 0)
        box.visibility = View.GONE
        box.addView(iconView(R.drawable.ic_search, 22, Ui.MUTED))
        search = EditText(this)
        search.hint = tr("টুল খুঁজুন, যেমন: PDF, QR, ছবি")
        search.textSize = 16f
        search.setTextColor(Ui.TEXT)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.isSingleLine = true
        search.background = null
        search.setPadding(dp(12), 0, 0, 0)
        search.onChange { render() }
        box.addView(search, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(box, lp(height = dp(52), t = 10))
        sBtn.setOnClickListener {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            if (box.visibility == View.GONE) {
                box.visibility = View.VISIBLE
                search.requestFocus()
                imm.showSoftInput(search, 0)
            } else {
                search.setText("")
                box.visibility = View.GONE
                imm.hideSoftInputFromWindow(search.windowToken, 0)
            }
        }
        root.addView(header, lp())

        val scroll = ScrollView(this)
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(16), dp(12), dp(16), dp(20))
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

    private fun hasData(): Boolean = listOf("ledger", "debts", "todos", "notes").any { Store.read(this, it).length() > 0 }

    /** A gentle reminder when no backup exists, or the last one is old. */
    private fun backupBanner(): View? {
        if (!hasData()) return null
        val sp = getSharedPreferences("settings", MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now < sp.getLong("backupSnooze", 0L)) return null
        val last = sp.getLong("lastBackup", 0L)
        val days = if (last == 0L) -1 else ((now - last) / 86_400_000L).toInt()
        if (last != 0L && days < 14) return null
        val c = card(14)
        c.background = bg(Ui.CARD, 20, Ui.PRIMARY, 1)
        c.addView(label(tr("ব্যাকআপ নিন"), 15f, Ui.TEXT, true))
        c.addView(label(if (last == 0L) tr("এখনো কোনো ব্যাকআপ নেওয়া হয়নি। ফোন হারালে বা অ্যাপ মুছলে ডেটা চলে যাবে।") else bn(days.toString()) + tr(" দিন ধরে ব্যাকআপ নেওয়া হয়নি।"), 13f, Ui.MUTED), lp(t = 4))
        val row = LinearLayout(this)
        val now2 = label(tr("এখনই নিন"), 14f, Ui.PRIMARY, true)
        now2.setPadding(0, dp(10), dp(24), 0)
        now2.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java).putExtra("page", "backup")) }
        val later = label(tr("পরে মনে করান"), 14f, Ui.MUTED)
        later.setPadding(0, dp(10), 0, 0)
        later.setOnClickListener {
            sp.edit().putLong("backupSnooze", System.currentTimeMillis() + 3 * 86_400_000L).apply()
            render()
        }
        row.addView(now2)
        row.addView(later)
        c.addView(row)
        return c
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val q = search.text.toString()
        if (q.isNotBlank()) {
            val found = Registry.tools.filter { Registry.matches(it, q) }
            content.addView(label(tr("ফলাফল (") + bn(found.size.toString()) + ")", 14f, Ui.MUTED, true), lp(l = 6, b = 8))
            if (found.isEmpty()) {
                val e = label(tr("কিছু পাওয়া যায়নি।"), 14f, Ui.MUTED)
                e.gravity = Gravity.CENTER
                e.setPadding(0, dp(30), 0, dp(30))
                content.addView(e, lp())
            } else {
                content.addView(groupCard(found.map { toolRow(it) { render() } }), lp())
            }
            return
        }

        val nowC = java.util.Calendar.getInstance()
        val monthItems = LedgerStore.all(this).filter {
            val c = java.util.Calendar.getInstance()
            c.timeInMillis = it.date
            c.get(java.util.Calendar.YEAR) == nowC.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.MONTH) == nowC.get(java.util.Calendar.MONTH)
        }
        val mInc = monthItems.filter { it.income }.sumOf { it.amount }
        val mExp = monthItems.filter { !it.income }.sumOf { it.amount }
        val dashSub = if (monthItems.isEmpty()) tr("আয় ও ব্যয়ের ছবি দেখুন")
        else tr("এই মাসে ব্যয়: ") + taka(mExp) + tr("  ব্যালান্স: ") + taka(mInc - mExp)
        val bk = backupBanner()
        if (bk != null) content.addView(bk, lp(b = 12))
        val dash = listRow(R.drawable.ic_wallet, tr("ড্যাশবোর্ড"), dashSub)
        dash.setOnClickListener { startActivity(Intent(this, DashboardActivity::class.java)) }
        content.addView(groupCard(listOf(dash)), lp(b = 14))

        val favs = Registry.favIds(this).mapNotNull { id -> Registry.tools.firstOrNull { it.id == id && it.id !in Registry.hidden } }
        content.addView(label(tr("প্রিয় টুল"), 14f, Ui.MUTED, true), lp(l = 6, b = 8))
        if (favs.isEmpty()) {
            content.addView(label(tr("কোনো টুলে চেপে ধরে রাখলে সেটি এখানে যোগ হবে।"), 13f, Ui.MUTED), lp(l = 6, b = 6))
        } else {
            val grid = LinearLayout(this)
            grid.orientation = LinearLayout.HORIZONTAL
            val shown = favs.take(4)
            for (t in shown) {
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.setMargins(dp(4), 0, dp(4), 0)
                grid.addView(favTile(t), p)
            }
            for (i in shown.size until 4) {
                val p = LinearLayout.LayoutParams(0, 1, 1f)
                p.setMargins(dp(4), 0, dp(4), 0)
                grid.addView(View(this), p)
            }
            content.addView(grid, lp(b = 6))
        }

        content.addView(label(tr("ক্যাটাগরি"), 14f, Ui.MUTED, true), lp(l = 6, t = 14, b = 8))
        val rows = Registry.cats.filter { Registry.inCat(it.id).isNotEmpty() }.map { c ->
            val r = listRow(c.icon, c.name, bn(Registry.inCat(c.id).size.toString()) + tr("টি টুল"))
            r.setOnClickListener {
                startActivity(Intent(this, ToolListActivity::class.java).putExtra("cat", c.id))
            }
            r as View
        }
        content.addView(groupCard(rows), lp())
        content.addView(label(tr("প্রিয় করতে বা সরাতে যেকোনো টুলে চেপে ধরে রাখুন।"), 12f, Ui.MUTED), lp(l = 6, t = 10))
    }

    private fun favTile(t: Tool): View {
        val cat = Registry.cat(t.cat)
        val col = card(10)
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.addView(iconView(cat.icon, 28, Ui.ICON))
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
}
