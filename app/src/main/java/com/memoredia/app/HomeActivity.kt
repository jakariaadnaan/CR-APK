package com.memoredia.app

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
    private lateinit var badge: android.widget.TextView

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
        DailySummary.schedule(this)
        Thread { AutoBackup.runIfDue(applicationContext) }.start()
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
        val spIcon = getSharedPreferences("settings", MODE_PRIVATE)
        val iconRes = when (spIcon.getString("appIcon", "IconA")) {
            "IconB" -> R.mipmap.ic_launcher_b
            "IconC" -> R.mipmap.ic_launcher_c
            else -> R.mipmap.ic_launcher_a
        }
        val logo = android.widget.ImageView(this)
        logo.setImageResource(iconRes)
        logo.clipToOutline = true
        logo.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, v.width * 0.26f) }
        }
        top.addView(logo, lp(width = dp(46), height = dp(46), r = 12))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label(tr("Memoredia"), 22f, Ui.TEXT, true))
        titles.addView(label(tr("সব দরকারি টুল এক জায়গায়"), 13f, Ui.MUTED), lp(t = 1))
        top.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sBtn = LinearLayout(this)
        sBtn.gravity = Gravity.CENTER
        sBtn.background = bg(Ui.CARD, 24, Ui.LINE, 1)
        sBtn.addView(iconView(R.drawable.ic_search, 22, Ui.TEXT))
        top.addView(sBtn, lp(width = dp(46), height = dp(46), l = 8))
        val bellWrap = android.widget.FrameLayout(this)
        val bell = LinearLayout(this)
        bell.gravity = Gravity.CENTER
        bell.background = bg(Ui.CARD, 24, Ui.LINE, 1)
        bell.addView(iconView(R.drawable.ic_bell, 22, Ui.TEXT))
        bellWrap.addView(bell, android.widget.FrameLayout.LayoutParams(dp(46), dp(46)))
        badge = label("", 10f, Color.WHITE, true)
        badge.gravity = Gravity.CENTER
        badge.background = bg(Color.parseColor("#E53935"), 9)
        val bp = android.widget.FrameLayout.LayoutParams(dp(18), dp(18))
        bp.gravity = Gravity.TOP or Gravity.END
        bellWrap.addView(badge, bp)
        bell.setOnClickListener { it.tapFx(); startActivity(Intent(this, NotificationsActivity::class.java)) }
        top.addView(bellWrap, lp(width = dp(46), height = dp(46), l = 8))
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

    override fun onBackPressed() {
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("confirmExit", false)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Exit")
                .setMessage("Do you want to close the app?")
                .setPositiveButton("Exit") { _, _ -> finish() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        updateBadge()
        render()
    }

    private fun updateBadge() {
        if (!::badge.isInitialized) return
        val n = NotifCenter.badge(this)
        badge.visibility = if (n > 0) View.VISIBLE else View.GONE
        badge.text = if (n > 9) "9+" else bn(n.toString())
    }

    private fun hasData(): Boolean = listOf("ledger", "debts", "todos", "notes").any { Store.read(this, it).length() > 0 }

    /** A gentle reminder when no backup exists, or the last one is old. */
    private fun backupBanner(): View? {
        if (!hasData()) return null
        if (!NotifPrefs.on(this, NotifPrefs.BACKUP)) return null
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
        content.addView(activityCard(), lp(b = 16))

        val hdr = LinearLayout(this)
        hdr.gravity = Gravity.CENTER_VERTICAL
        hdr.addView(label(tr("দ্রুত অ্যাক্সেস"), 17f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val all = label(tr("সব দেখুন") + "  ›", 13f, Ui.PRIMARY, true)
        all.setPadding(dp(8), dp(6), dp(4), dp(6))
        all.setOnClickListener { startActivity(Intent(this, ToolListActivity::class.java).putExtra("cat", "all")) }
        hdr.addView(all)
        val qa = LinearLayout(this)
        qa.orientation = LinearLayout.VERTICAL
        qa.background = bg(Ui.CARD, 20, Ui.LINE, 1)
        qa.setPadding(dp(10), dp(8), dp(10), dp(8))
        qa.addView(hdr, lp(l = 4, b = 2))

        val grid = quickTools()
        var idx = 0
        while (idx < grid.size) {
            val line = LinearLayout(this)
            line.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until 3) {
                val p = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                p.setMargins(dp(3), dp(3), dp(3), dp(3))
                if (idx + k < grid.size) line.addView(favTile(grid[idx + k]), p) else line.addView(View(this), p)
            }
            qa.addView(line, lp())
            idx += 3
        }
        content.addView(qa, lp())
        content.addView(recentSection(), lp(t = 14, b = 14))
    }

    private fun ago(t: Long): String {
        val m = (System.currentTimeMillis() - t) / 60_000L
        return when {
            m < 1 -> tr("এইমাত্র")
            m < 60 -> bn(m.toString()) + tr(" মিনিট আগে")
            m < 1440 -> bn((m / 60).toString()) + tr(" ঘণ্টা আগে")
            else -> bn((m / 1440).toString()) + tr(" দিন আগে")
        }
    }

    private fun recentRow(e: Usage.Ev): View {
        val tool = Registry.tools.firstOrNull { it.target?.simpleName == e.id }
        val catId = tool?.cat ?: ""
        val (res, col) = when (catId) {
            "pdf" -> Pair(R.drawable.ic_pdf, "#E53935")
            "image" -> Pair(R.drawable.ic_image, "#43A047")
            "media" -> Pair(R.drawable.ic_media, "#7E57C2")
            else -> Pair(R.drawable.ic_doc, "#3F6FE0")
        }
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(dp(12), dp(11), dp(10), dp(11))
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        ib.background = bg(Color.parseColor(col), 11)
        ib.addView(iconView(res, 20, Color.WHITE))
        r.addView(ib, lp(width = dp(40), height = dp(40), r = 12))
        val mid = LinearLayout(this)
        mid.orientation = LinearLayout.VERTICAL
        val nm = label(if (e.n.isNotBlank()) e.n else (tool?.title ?: tr("কাজ সম্পন্ন")), 14f, Ui.TEXT, true)
        nm.maxLines = 1
        nm.ellipsize = android.text.TextUtils.TruncateAt.END
        mid.addView(nm, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        if (tool != null) {
            val sb = label(tool.title, 12f, Ui.MUTED)
            sb.maxLines = 1
            sb.ellipsize = android.text.TextUtils.TruncateAt.END
            mid.addView(sb, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        r.addView(mid, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tm = label(ago(e.t), 11f, Ui.MUTED)
        tm.maxLines = 1
        r.addView(tm, lp(width = LinearLayout.LayoutParams.WRAP_CONTENT, l = 8))
        val tag = label("● " + tr("সম্পন্ন"), 10f, Ui.GREEN, true)
        tag.maxLines = 1
        tag.background = bg(Ui.mix(Ui.GREEN, Ui.CARD, 0.85f), 12)
        tag.setPadding(dp(8), dp(3), dp(8), dp(3))
        r.addView(tag, lp(width = LinearLayout.LayoutParams.WRAP_CONTENT, l = 8))
        r.addView(iconView(R.drawable.ic_chevron, 16, Ui.CHEV), lp(width = dp(16), height = dp(16), l = 6))
        if (tool != null) r.setOnClickListener { it.tapFx(); openTool(tool) }
        return r
    }

    private fun recentSection(): View {
        val sec = LinearLayout(this)
        sec.orientation = LinearLayout.VERTICAL
        val hdr = LinearLayout(this)
        hdr.gravity = Gravity.CENTER_VERTICAL
        hdr.addView(label(tr("সাম্প্রতিক কাজ"), 17f, Ui.TEXT, true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val all = label(tr("সব দেখুন") + "  ›", 13f, Ui.PRIMARY, true)
        all.setPadding(dp(8), dp(6), dp(4), dp(6))
        all.setOnClickListener { showAllRecent() }
        hdr.addView(all)
        sec.addView(hdr, lp(l = 4, b = 8))
        val list = Usage.recent(this, 4)
        if (list.isEmpty()) {
            val e = label(tr("এখনো কোনো কাজ সম্পন্ন হয়নি। কোনো টুল দিয়ে ফাইল বানালে এখানে দেখা যাবে।"), 13f, Ui.MUTED)
            e.setPadding(dp(14), dp(14), dp(14), dp(14))
            e.background = bg(Ui.CARD, 18, Ui.LINE, 1)
            sec.addView(e, lp())
        } else {
            sec.addView(groupCard(list.map { recentRow(it) }), lp())
        }
        return sec
    }

    private fun showAllRecent() {
        val list = Usage.recent(this, 30)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        if (list.isEmpty()) {
            col.addView(label(tr("এখনো কোনো কাজ সম্পন্ন হয়নি।"), 14f, Ui.MUTED), lp(l = 20, t = 12, r = 20, b = 12))
        } else {
            for (e in list) col.addView(recentRow(e), lp())
        }
        val sc = ScrollView(this)
        sc.addView(col)
        android.app.AlertDialog.Builder(this)
            .setTitle(tr("সাম্প্রতিক কাজ"))
            .setView(sc)
            .setPositiveButton(tr("বন্ধ করুন"), null)
            .show()
    }

    private fun pctView(cur: Double, prev: Double, small: Boolean): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val text: String
        val color: Int
        if (prev <= 0.0) {
            text = if (cur > 0.0) tr("নতুন") else "—"
            color = if (cur > 0.0) Ui.PRIMARY else Ui.MUTED
        } else {
            val p = (cur - prev) * 100.0 / prev
            text = (if (p >= 0) "↑ " else "↓ ") + bn(String.format(java.util.Locale.US, "%.1f", Math.abs(p))) + "%"
            color = if (p >= 0) Ui.PRIMARY else Ui.RED
        }
        col.addView(label(text, if (small) 11f else 10f, color, true))
        return col
    }

    private fun humanBytes(b: Long): String {
        val s = when {
            b <= 0L -> "0 MB"
            b < 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f KB", b / 1024.0)
            b < 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
            else -> String.format(java.util.Locale.US, "%.2f GB", b / 1073741824.0)
        }
        return bn(s)
    }

    private fun humanMinutes(m: Double): String =
        if (m < 60.0) bn(Math.round(m).toString()) + " " + tr("মিনিট")
        else bn(String.format(java.util.Locale.US, "%.1f", m / 60.0)) + " " + tr("ঘণ্টা")

    /** The "Your Activity" card: counts for the chosen period, compared with the period before. */
    private fun activityCard(): View {
        val days = 7
        val st = Usage.stats(this, days)
        val tint = Ui.mix(Ui.PRIMARY, Ui.CARD, if (Ui.dark) 0.82f else 0.9f)
        val card = AspectLinear(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = bg(tint, 22, Ui.LINE, 1)
        card.setPadding(dp(12), dp(8), dp(12), dp(8))
        card.setOnClickListener { it.tapFx(); startActivity(Intent(this, DashboardActivity::class.java)) }

        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.addView(bigStat(R.drawable.ic_grid, bn(st.cur.tools.toString()), tr("টুল ব্যবহার"), st.cur.tools.toDouble(), st.prev.tools.toDouble()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        top.addView(bigStat(R.drawable.ic_sparkle, bn(st.cur.conversions.toString()), tr("কনভার্সন"), st.cur.conversions.toDouble(), st.prev.conversions.toDouble()), lp(width = 0, height = LinearLayout.LayoutParams.MATCH_PARENT, l = 8).also { it.weight = 1f })
        val chart = ActivityChart(this)
        chart.values = st.daily
        chart.color = Ui.PRIMARY
        chart.bubbleBg = Ui.CARD
        chart.bubbleFg = Ui.TEXT
        var mi = 0
        for (i in st.daily.indices) if (st.daily[i] >= st.daily[mi]) mi = i
        chart.bubbleText = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(st.labels[mi])) + "\n" + bn(st.daily[mi].toString())
        top.addView(chart, lp(width = 0, height = LinearLayout.LayoutParams.MATCH_PARENT, l = 6).also { it.weight = 1.3f })
        card.addView(top, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        val line = View(this)
        line.setBackgroundColor(Ui.LINE)
        card.addView(line, lp(height = 1, t = 6, b = 6))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.addView(smallStat(R.drawable.ic_doc, tr("ফাইল প্রসেস"), humanBytes(st.cur.bytes), st.cur.bytes.toDouble(), st.prev.bytes.toDouble()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(smallStat(R.drawable.ic_clock, tr("সাশ্রয়ী সময়"), humanMinutes(st.cur.minutes), st.cur.minutes, st.prev.minutes), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(smallStat(R.drawable.ic_star, tr("সম্পন্ন করণীয়"), bn(st.cur.todos.toString()), st.cur.todos.toDouble(), st.prev.todos.toDouble()), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(bottom, lp())
        return card
    }

    private fun bigStat(icon: Int, value: String, name: String, cur: Double, prev: Double): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(10), dp(6), dp(6), dp(6))
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        r.addView(iconView(icon, 18, Ui.PRIMARY), lp(r = 6))
        r.addView(label(value, 19f, Ui.TEXT, true))
        c.addView(r)
        c.addView(label(name, 12f, Ui.MUTED), lp(t = 1))
        c.addView(pctView(cur, prev, false), lp(t = 3))
        return c
    }

    private fun smallStat(icon: Int, name: String, value: String, cur: Double, prev: Double): View {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(2), 0, dp(4), 0)
        val r = LinearLayout(this)
        r.gravity = Gravity.CENTER_VERTICAL
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        ib.background = bg(Ui.CARD, 10)
        ib.addView(iconView(icon, 16, Ui.PRIMARY))
        r.addView(ib, lp(width = dp(30), height = dp(30), r = 6))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(name, 11f, Ui.MUTED))
        col.addView(label(value, 15f, Ui.TEXT, true))
        r.addView(col)
        c.addView(r)
        c.addView(pctView(cur, prev, true), lp(t = 4))
        return c
    }

    /** Up to nine tools: the favourites first, then one from each category in turn. */
    private fun quickTools(): List<Tool> {
        val out = ArrayList<Tool>()
        for (id in Registry.favIds(this)) {
            val t = Registry.tools.firstOrNull { it.id == id && it.id !in Registry.hidden }
            if (t != null && t !in out && out.size < 9) out.add(t)
        }
        val per = Registry.cats.map { c -> Registry.inCat(c.id).filter { it.id !in Registry.hidden } }
        var round = 0
        while (out.size < 9 && round < 20) {
            for (l in per) {
                if (out.size >= 9) break
                val t = l.getOrNull(round)
                if (t != null && t !in out) out.add(t)
            }
            round++
        }
        return out
    }

    /** The colour people already associate with each kind of tool (PDF red, Word blue, Excel green and so on). */
    private fun toolColor(t: Tool): Int {
        val id = t.id
        val hex = when {
            id.startsWith("pdf") || id in listOf("img2pdf", "txt2pdf", "web2pdf", "campdf", "pdf2img", "invoice") -> "#E53935"
            id in listOf("imgfmt", "imgcomp", "upscale", "transp", "collage") -> "#43A047"
            id == "color" -> "#EC407A"
            id in listOf("words", "counter", "textcase", "unicode", "dict") -> "#2B6CD9"
            id == "notes" -> "#F9A825"
            id in listOf("ledger", "debt") -> "#1D8B4C"
            id in listOf("calc", "sci") -> "#F57C00"
            id == "land" -> "#8D6E63"
            id == "currency" -> "#00897B"
            id == "prayer" -> "#00796B"
            id == "todo" -> "#3F6FE0"
            id == "date" -> "#E64A19"
            id == "timer" -> "#FB8C00"
            id == "pass" -> "#5E35B1"
            id in listOf("aconv", "v2mp3") -> "#7E57C2"
            id == "gif" -> "#8E24AA"
            id == "vmeta" -> "#5E35B1"
            id == "rec" -> "#D32F2F"
            id in listOf("qrgen", "barcode", "scan") -> "#26A69A"
            id == "speed" -> "#7C4DFF"
            id in listOf("ip", "whois") -> "#1E88E5"
            id == "webcam" -> "#546E7A"
            else -> null
        }
        return if (hex != null) Color.parseColor(hex) else Registry.cat(t.cat).fg
    }

    private fun favTile(t: Tool): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.background = bg(Ui.CARD, 14, Ui.LINE, 1)
        col.setPadding(dp(4), dp(10), dp(4), dp(9))
        val ib = LinearLayout(this)
        ib.gravity = Gravity.CENTER
        val g = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(Ui.mix(toolColor(t), Color.WHITE, 0.25f), toolColor(t))
        )
        g.cornerRadius = dp(10).toFloat()
        ib.background = g
        ib.addView(iconView(Registry.cat(t.cat).icon, 18, Color.WHITE))
        col.addView(ib, lp(width = dp(34), height = dp(34)))
        val name = label(t.title, 11f, Ui.TEXT)
        name.gravity = Gravity.CENTER
        name.maxLines = 1
        name.ellipsize = android.text.TextUtils.TruncateAt.END
        col.addView(name, lp(t = 6))
        col.setOnClickListener { it.tapFx(); openTool(t) }
        col.setOnLongClickListener {
            Registry.toggleFav(this, t.id)
            render()
            true
        }
        return col
    }
}
