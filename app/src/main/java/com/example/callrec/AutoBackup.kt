package com.example.callrec

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Builds the backup text; shared by the manual and the automatic backup. */
object BackupData {
    private val keys = listOf("ledger", "debts", "todos", "notes")

    fun build(c: Context): String {
        val data = JSONObject()
        for (k in keys) data.put(k, Store.read(c, k))
        val extra = JSONObject()
        val land = c.getSharedPreferences("land_prefs", Context.MODE_PRIVATE)
        extra.put("katha", land.getString("katha", "720"))
        extra.put("bigha", land.getString("bigha", "20"))
        extra.put("hijriAdj", c.getSharedPreferences("date_prefs", Context.MODE_PRIVATE).getInt("hijriAdj", 0))
        val o = JSONObject()
        o.put("app", "amar-khata")
        o.put("version", 1)
        o.put("created", System.currentTimeMillis())
        o.put("data", data)
        o.put("prefs", extra)
        val more = JSONObject()
        for (n in listOf("budget", "prayer")) more.put(n, dump(c, n))
        o.put("more", more)
        return o.toString(2)
    }

    private fun dump(c: Context, name: String): JSONObject {
        val out = JSONObject()
        for ((k, v) in c.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
            val e = JSONObject()
            when (v) {
                is Boolean -> { e.put("t", "b"); e.put("v", v) }
                is Int -> { e.put("t", "i"); e.put("v", v) }
                is Float -> { e.put("t", "f"); e.put("v", v.toDouble()) }
                is Long -> { e.put("t", "l"); e.put("v", v) }
                is String -> { e.put("t", "s"); e.put("v", v) }
                else -> continue
            }
            out.put(k, e)
        }
        return out
    }
}

/** Writes a backup into the chosen folder on a schedule (daily, weekly or monthly). */
object AutoBackup {
    const val KEEP = 5

    private fun sp(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun freq(c: Context) = sp(c).getInt("autoFreq", 0)
    fun tree(c: Context): String? = sp(c).getString("autoTree", null)
    fun last(c: Context) = sp(c).getLong("autoLast", 0L)
    fun status(c: Context): String = sp(c).getString("autoStatus", "") ?: ""
    fun pass(c: Context): String = sp(c).getString("autoPass", "") ?: ""

    fun save(c: Context, freq: Int, tree: String?, pass: String) {
        sp(c).edit().putInt("autoFreq", freq).putString("autoTree", tree).putString("autoPass", pass).apply()
        schedule(c)
    }

    private fun intervalMs(f: Int): Long = when (f) { 1 -> 1L; 2 -> 7L; else -> 30L } * 86_400_000L

    private fun pending(c: Context, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(c, 5000, Intent(c, AutoBackupReceiver::class.java), flags or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pending(c, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        val f = freq(c)
        if (f == 0 || tree(c) == null) return
        val cal = Calendar.getInstance()
        cal.timeInMillis = Math.max(last(c) + intervalMs(f), System.currentTimeMillis() + 60_000L)
        cal.set(Calendar.HOUR_OF_DAY, 3)
        cal.set(Calendar.MINUTE, 0)
        if (cal.timeInMillis < System.currentTimeMillis() + 60_000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        val pi = pending(c, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    /** Runs a backup now if one is due. Safe to call often. */
    fun runIfDue(c: Context) {
        val f = freq(c)
        if (f == 0 || tree(c) == null) return
        if (System.currentTimeMillis() - last(c) >= intervalMs(f) - 3_600_000L) run(c)
        schedule(c)
    }

    fun run(c: Context): Boolean {
        val treeStr = tree(c) ?: return false
        return try {
            val tree = Uri.parse(treeStr)
            val resolver = c.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val plain = BackupData.build(c)
            val pw = pass(c)
            val text = if (pw.isNotEmpty()) BackupCrypto.encrypt(plain, pw.toCharArray()) else plain
            val name = "amar-khata-auto-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
            val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name) ?: throw IllegalStateException()
            resolver.openOutputStream(doc, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: throw IllegalStateException()
            prune(c, tree, parent)
            val now = System.currentTimeMillis()
            sp(c).edit().putLong("autoLast", now).putLong("lastBackup", now).putString("autoStatus", "ok").apply()
            true
        } catch (e: Exception) {
            sp(c).edit().putString("autoStatus", "fail").apply()
            false
        }
    }

    /** Keeps only the newest [KEEP] automatic files. */
    private fun prune(c: Context, tree: Uri, parent: Uri) {
        val resolver = c.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val found = ArrayList<Pair<String, Uri>>()
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val name = cur.getString(1) ?: continue
                if (name.startsWith("amar-khata-auto-")) found.add(Pair(name, DocumentsContract.buildDocumentUriUsingTree(tree, cur.getString(0))))
            }
        }
        for (old in found.sortedByDescending { it.first }.drop(KEEP)) {
            try { DocumentsContract.deleteDocument(resolver, old.second) } catch (e: Exception) { }
        }
    }
}

class AutoBackupReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        AutoBackup.run(ctx)
        AutoBackup.schedule(ctx)
    }
}
