package com.example.callrec

import android.content.Context
import android.graphics.Color

/** One live warning shown in the bell page. [target] is the screen it opens when tapped. */
class Alert(val color: Int, val icon: Int, val title: String, val text: String, val target: Class<*>?, val page: String = "")

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
                if (last == 0L) out.add(Alert(AMBER, R.drawable.ic_backup, tr("ব্যাকআপ নিন"), tr("এখনো কোনো ব্যাকআপ নেওয়া হয়নি। ফোন হারালে বা অ্যাপ মুছলে ডেটা চলে যাবে।"), SettingsActivity::class.java, "backup"))
                else if (days >= 14) out.add(Alert(AMBER, R.drawable.ic_backup, tr("ব্যাকআপ পুরোনো হয়ে গেছে"), bn(days.toString()) + tr(" দিন ধরে ব্যাকআপ নেওয়া হয়নি।"), SettingsActivity::class.java, "backup"))
            }
            // Budget limits for this month
            if (BudgetStore.any(c)) {
                val cal = java.util.Calendar.getInstance()
                val spent = BudgetStore.spent(c, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH))
                for (cat in LedgerStore.EXPENSE_CATS) {
                    val lim = BudgetStore.get(c, cat)
                    if (lim <= 0) continue
                    val sp = spent[cat] ?: 0.0
                    if (sp >= lim) out.add(Alert(RED, R.drawable.ic_wallet, tr("বাজেট পার হয়েছে"), cat + ": " + taka(sp) + " / " + taka(lim), BudgetActivity::class.java))
                    else if (sp >= lim * 0.8) out.add(Alert(AMBER, R.drawable.ic_wallet, tr("বাজেটের সীমা কাছে"), cat + ": " + taka(sp) + " / " + taka(lim), BudgetActivity::class.java))
                }
            }
            // Debts whose reminder day has come or passed
            for (d in DebtStore.all(c)) {
                val rem = d.amount - d.pays.sumOf { it.second }
                if (d.due <= 0L || rem <= 0.5 || d.due > today) continue
                val late = d.due < today
                val head = if (d.owe) tr("দিতে হবে") else tr("পাবেন")
                out.add(Alert(if (late) RED else AMBER, R.drawable.ic_user, (if (late) tr("সময় পার: ") else tr("আজ: ")) + head, d.name + " · " + taka(rem), DebtActivity::class.java))
            }
            // Tasks
            val todos = TodoStore.all(c).filter { !it.done && it.due > 0L }
            val late = todos.count { it.due < today }
            val now = todos.count { it.due == today }
            if (late > 0) out.add(Alert(RED, R.drawable.ic_clock, tr("সময় পার হওয়া করণীয়"), bn(late.toString()) + tr("টি কাজের সময় পার হয়ে গেছে"), TodoActivity::class.java))
            if (now > 0) out.add(Alert(BLUE, R.drawable.ic_clock, tr("আজকের করণীয়"), bn(now.toString()) + tr("টি কাজ আজ শেষ করার কথা"), TodoActivity::class.java))
        } catch (e: Throwable) { }
        return out
    }

    fun unseenLog(c: Context): Int {
        val seen = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getLong("notifSeen", 0L)
        return NotifLog.list(c).count { it.first > seen }
    }

    /** The number on the bell: live warnings plus history entries not yet opened. */
    fun badge(c: Context): Int = alerts(c).size + unseenLog(c)
}
