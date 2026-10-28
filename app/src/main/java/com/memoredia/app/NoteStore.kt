package com.memoredia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A quick note. [updated] is epoch millis of the last edit. */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val updated: Long,
    /** 0 = no colour, 1..5 = index into [NOTE_COLORS]. */
    val color: Int = 0,
    val tags: List<String> = emptyList(),
    /** Checklist items: text and whether it is ticked. */
    val checks: List<Pair<String, Boolean>> = emptyList()
)

val NOTE_COLORS = listOf("#E5A93B", "#4C9F70", "#4A7FD6", "#C0617E", "#8A63C9")

object NoteStore {
    private const val KEY = "notes"

    fun all(c: Context): MutableList<Note> {
        val a = Store.read(c, KEY)
        val out = ArrayList<Note>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val ta = o.optJSONArray("tags")
            val tags = ArrayList<String>()
            if (ta != null) for (k in 0 until ta.length()) tags.add(ta.optString(k))
            val ca = o.optJSONArray("chk")
            val checks = ArrayList<Pair<String, Boolean>>()
            if (ca != null) for (k in 0 until ca.length()) {
                val co = ca.optJSONObject(k) ?: continue
                checks.add(Pair(co.optString("t"), co.optBoolean("d")))
            }
            out.add(
                Note(
                    o.optLong("id"), o.optString("title"), o.optString("body"),
                    o.optBoolean("pin"), o.optLong("upd"), o.optInt("col"), tags, checks
                )
            )
        }
        return out
    }

    private fun save(c: Context, list: List<Note>) {
        val a = JSONArray()
        for (n in list) {
            val o = JSONObject()
            o.put("id", n.id)
            o.put("title", n.title)
            o.put("body", n.body)
            o.put("pin", n.pinned)
            o.put("upd", n.updated)
            o.put("col", n.color)
            o.put("tags", JSONArray(n.tags))
            val ca = JSONArray()
            for (ch in n.checks) ca.put(JSONObject().put("t", ch.first).put("d", ch.second))
            o.put("chk", ca)
            a.put(o)
        }
        Store.write(c, KEY, a)
    }

    fun upsert(c: Context, n: Note) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == n.id }
        if (i >= 0) list[i] = n else list.add(n)
        save(c, list)
    }

    fun allTags(c: Context): List<String> = all(c).flatMap { it.tags }.distinct().sorted()

    fun delete(c: Context, id: Long) {
        save(c, all(c).filter { it.id != id })
    }
}
