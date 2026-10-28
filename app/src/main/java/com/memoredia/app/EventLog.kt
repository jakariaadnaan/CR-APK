package com.memoredia.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A small on-device log, shown in the app so you can paste it into the chat. */
object EventLog {
    private const val PREFS = "callrec_log"
    private const val KEY = "lines"
    private const val MAX_LINES = 200

    @Synchronized
    fun add(ctx: Context, msg: String) {
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val lines = (p.getString(KEY, "") ?: "").split("\n").filter { it.isNotEmpty() }.toMutableList()
        lines.add("$time  $msg")
        while (lines.size > MAX_LINES) lines.removeAt(0)
        p.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    @Synchronized
    fun read(ctx: Context): String =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    @Synchronized
    fun clear(ctx: Context) {
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
