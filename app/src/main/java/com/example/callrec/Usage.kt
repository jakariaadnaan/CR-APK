package com.example.callrec

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A small on-phone log of tool use, shown on the home "Your Activity" card. Nothing leaves the phone. */
object Usage {
    private const val KEY = "ev"
    private const val DAY = 86_400_000L
    private const val KEEP_DAYS = 120
    private const val MAX = 4000

    private fun sp(c: Context) = c.getSharedPreferences("usage", Context.MODE_PRIVATE)

    /** kind: "open" (a tool was opened), "done" (a file job finished), "todo" (a task was ticked off). */
    fun add(c: Context, kind: String, id: String, bytes: Long = 0L) {
        try {
            val now = System.currentTimeMillis()
            val old = JSONArray(sp(c).getString(KEY, "[]"))
            val out = JSONArray()
            val from = now - KEEP_DAYS * DAY
            val start = Math.max(0, old.length() - MAX + 1)
            for (i in start until old.length()) {
                val o = old.getJSONObject(i)
                if (o.optLong("t") >= from) out.put(o)
            }
            out.put(JSONObject().put("t", now).put("k", kind).put("id", id).put("b", bytes))
            sp(c).edit().putString(KEY, out.toString()).apply()
        } catch (e: Throwable) { }
    }

    fun done(c: Context, id: String, bytes: Long = 0L) = add(c, "done", id, bytes)

    class Ev(val t: Long, val k: String, val id: String, val b: Long)

    private fun all(c: Context): List<Ev> {
        val r = ArrayList<Ev>()
        try {
            val a = JSONArray(sp(c).getString(KEY, "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                r.add(Ev(o.optLong("t"), o.optString("k"), o.optString("id"), o.optLong("b")))
            }
        } catch (e: Throwable) { }
        return r
    }

    class Part(val tools: Int, val conversions: Int, val bytes: Long, val todos: Int, val minutes: Double)

    class Stats(val cur: Part, val prev: Part, val daily: IntArray, val labels: List<Long>, val hasAny: Boolean)

    private fun part(ev: List<Ev>): Part {
        val tools = ev.filter { it.k == "open" }.map { it.id }.toSet().size
        val conv = ev.count { it.k == "done" }
        val bytes = ev.filter { it.k == "done" }.sumOf { it.b }
        val todos = ev.count { it.k == "todo" }
        // Rough estimate: about 3 minutes saved per finished file job and 1 per ticked task.
        return Part(tools, conv, bytes, todos, conv * 3.0 + todos * 1.0)
    }

    /** The last [days] days (today included), compared with the [days] days before them. */
    fun stats(c: Context, days: Int): Stats {
        val ev = all(c)
        val today = todayStart()
        val curFrom = today - (days - 1) * DAY
        val prevFrom = curFrom - days * DAY
        val cur = ev.filter { it.t >= curFrom }
        val prev = ev.filter { it.t >= prevFrom && it.t < curFrom }
        val daily = IntArray(days)
        val labels = ArrayList<Long>()
        for (i in 0 until days) labels.add(curFrom + i * DAY)
        for (e in cur) {
            val idx = ((e.t - curFrom) / DAY).toInt()
            if (idx in 0 until days) daily[idx]++
        }
        return Stats(part(cur), part(prev), daily, labels, ev.isNotEmpty())
    }
}
