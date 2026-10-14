package com.example.callrec

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A quick note. [updated] is epoch millis of the last edit. */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val pinned: Boolean,
    val updated: Long
)

object NoteStore {
    private const val KEY = "notes"

    fun all(c: Context): MutableList<Note> {
        val a = Store.read(c, KEY)
        val out = ArrayList<Note>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Note(
                    o.optLong("id"), o.optString("title"), o.optString("body"),
                    o.optBoolean("pin"), o.optLong("upd")
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

    fun delete(c: Context, id: Long) {
        save(c, all(c).filter { it.id != id })
    }
}
