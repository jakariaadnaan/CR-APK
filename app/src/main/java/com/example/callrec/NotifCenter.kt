package com.example.callrec

import android.content.Context
import android.graphics.Color

/** One live warning shown in the bell page. [target] is the screen it opens when tapped. */
class Alert(val color: Int, val icon: Int, val title: String, val text: String, val open: ((Context) -> android.content.Intent)? = null)

/** Builds the warnings that are true right now: backup due, budget limits, overdue debts and tasks. */
object NotifCenter {
    private val RED = Color.parseColor("#E53935")
    private val AMBER = Color.parseColor("#F29900")
    private val BLUE = Color.parseColor("#3F6FE0")

    fun alerts(c: Context): List<Alert> {
        val out = ArrayList<Alert>()
        val today = todayStart()
        try {
            // Backup
            val hasData = listOf("ledger", "debts", "todos", "notes").any { Store.read(c, it).length() > 0 }
            if (hasData && NotifPrefs.on(c, NotifPrefs.BACKUP)) {
                val last = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getLong("lastBackup", 0L)
                val days = if (last == 0L) -1 else ((System.currentTimeMillis() - last) / 86_400_000L).toInt()
                if (last == 0L) out.add(Alert(AMBER, R.drawable.ic_backup, tr("ব্যাকআপ নিন"), tr("এখনো কোনো ব্যাকআপ নেওয়া হয়নি। ফোন হারালে বা অ্যাপ মুছলে ডেটা চলে যাবে।"), { c -> android.content.Intent(c, SettingsActivity::class.java).putExtra("page", "backup") }))
                else if (days >= 14) out.add(Alert(AMBER, R.drawable.ic_backup, tr("ব্যাকআপ পুরোনো হয়ে গেছে"), bn(days.toString()) + tr(" দিন ধরে ব্যাকআপ নেওয়া হয়নি।"), { c -> android.content.Intent(c, SettingsActivity::class.java).putExtra("page", "backup") }))
            }
            // Budget limits for this month
            if (BudgetStore.any(c)) {
                val cal = java.util.Calendar.getInstance()
                val spent = BudgetStore.spent(c, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH))
                for (cat in LedgerStore.EXPENSE_CATS) {
                    val lim = BudgetStore.get(c, cat)
                    if (lim <= 0) continue
                    val sp = spent[cat] ?: 0.0
                    if (sp >= lim) out.add(Alert(RED, R.drawable.ic_wallet, tr("বাজেট পার হয়েছে"), cat + ": " + taka(sp) + " / " + taka(lim), { c -> android.content.Intent(c, BudgetActivity::class.java) }))
                    else if (sp >= lim * 0.8) out.add(Alert(AMBER, R.drawable.ic_wallet, tr("বাজেটের সীমা কাছে"), cat + ": " + taka(sp) + " / " + taka(lim), { c -> android.content.Intent(c, BudgetActivity::class.java) }))
                }
            }
            // Debts whose reminder day has come or passed
            for (d in DebtStore.all(c)) {
                val rem = d.amount - d.pays.sumOf { it.second }
                if (d.due <= 0L || rem <= 0.5 || d.due > today) continue
                val late = d.due < today
                val head = if (d.owe) tr("দিতে হবে") else tr("পাবেন")
                out.add(Alert(if (late) RED else AMBER, R.drawable.ic_user, (if (late) tr("সময় পার: ") else tr("আজ: ")) + head, d.name + " · " + taka(rem), { c -> android.content.Intent(c, DebtActivity::class.java) }))
            }
            // Tasks
            val todos = TodoStore.all(c).filter { !it.done && it.due > 0L }
            val late = todos.count { it.due < today }
            val now = todos.count { it.due == today }
            if (late > 0) out.add(Alert(RED, R.drawable.ic_clock, tr("সময় পার হওয়া করণীয়"), bn(late.toString()) + tr("টি কাজের সময় পার হয়ে গেছে"), { c -> android.content.Intent(c, TodoActivity::class.java) }))
            if (now > 0) out.add(Alert(BLUE, R.drawable.ic_clock, tr("আজকের করণীয়"), bn(now.toString()) + tr("টি কাজ আজ শেষ করার কথা"), { c -> android.content.Intent(c, TodoActivity::class.java) }))

            // Automatic backup that failed
            if (AutoBackup.freq(c) > 0 && AutoBackup.status(c) == "fail") {
                out.add(Alert(RED, R.drawable.ic_backup, tr("অটো-ব্যাকআপ হয়নি"), tr("শেষবার স্বয়ংক্রিয় ব্যাকআপ ব্যর্থ হয়েছে। ফোল্ডারটি আছে কি না দেখুন।"), { c -> android.content.Intent(c, SettingsActivity::class.java).putExtra("page", "backup") }))
            }
            // Reminders cannot show without the notification permission
            if (android.os.Build.VERSION.SDK_INT >= 33 && NotifPrefs.on(c, NotifPrefs.ALL) &&
                c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                out.add(Alert(AMBER, R.drawable.ic_bell, tr("নোটিফিকেশনের অনুমতি বন্ধ"), tr("অনুমতি না দিলে নামাজ, বাকি ও করণীয়ের রিমাইন্ডার ফোনে আসবে না।"), { c ->
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, c.packageName)
                }))
            }
            // This month's spending is above income
            val cal2 = java.util.Calendar.getInstance()
            val mi = LedgerStore.all(c).filter {
                val k = java.util.Calendar.getInstance()
                k.timeInMillis = it.date
                k.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) && k.get(java.util.Calendar.MONTH) == cal2.get(java.util.Calendar.MONTH)
            }
            val inc = mi.filter { it.income }.sumOf { it.amount }
            val exp = mi.filter { !it.income }.sumOf { it.amount }
            if (exp > 0 && exp > inc) {
                out.add(Alert(AMBER, R.drawable.ic_wallet, tr("এই মাসে খরচ আয়ের চেয়ে বেশি"), tr("খরচ ") + taka(exp) + tr(", আয় ") + taka(inc), { c -> android.content.Intent(c, DashboardActivity::class.java) }))
            }
            // Debt reminder due tomorrow
            for (d in DebtStore.all(c)) {
                val rem = d.amount - d.pays.sumOf { it.second }
                if (d.due != today + 86_400_000L || rem <= 0.5) continue
                out.add(Alert(BLUE, R.drawable.ic_user, tr("কাল: ") + (if (d.owe) tr("দিতে হবে") else tr("পাবেন")), d.name + " · " + taka(rem), { c -> android.content.Intent(c, DebtActivity::class.java) }))
            }
            // Suggestions
            if (hasData && !Lock.enabled(c)) {
                out.add(Alert(BLUE, R.drawable.ic_lock, tr("অ্যাপ লক চালু করুন"), tr("আপনার হিসাব ও নোট পিন বা প্যাটার্ন দিয়ে সুরক্ষিত রাখতে পারেন।"), { c -> android.content.Intent(c, SettingsActivity::class.java).putExtra("page", "lock") }))
            }
            if (Lock.enabled(c) && Lock.question(c) == null) {
                out.add(Alert(BLUE, R.drawable.ic_lock, tr("নিরাপত্তা-প্রশ্ন সেট করা নেই"), tr("লক ভুলে গেলে ডেটা না মুছে ফিরে আসার উপায় এটাই।"), { c -> android.content.Intent(c, SettingsActivity::class.java).putExtra("page", "lock") }))
            }
            var cache = 0L
            fun sz(f: java.io.File?): Long {
                if (f == null || !f.exists()) return 0L
                if (f.isFile) return f.length()
                var n = 0L
                f.listFiles()?.forEach { n += sz(it) }
                return n
            }
            cache = sz(c.cacheDir) + sz(c.externalCacheDir)
            if (cache > 200L * 1024 * 1024) {
                out.add(Alert(BLUE, R.drawable.ic_trash, tr("ক্যাশ অনেক জমেছে"), bn(String.format(java.util.Locale.US, "%.0f", cache / 1048576.0)) + " MB · " + tr("সেটিংস থেকে পরিষ্কার করা যায়"), { c -> android.content.Intent(c, SettingsActivity::class.java) }))
            }
        } catch (e: Throwable) { }
        return out
    }

    fun unseenLog(c: Context): Int {
        val seen = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getLong("notifSeen", 0L)
        return NotifLog.list(c).count { it.first > seen }
    }

    /** The number on the bell: live warnings plus history entries not yet opened. */
    fun badge(c: Context): Int = alerts(c).count { it.color != BLUE } + unseenLog(c)
}
