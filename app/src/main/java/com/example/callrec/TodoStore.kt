package com.example.callrec

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One করণীয় task. [due] is local midnight in epoch millis, or 0 when there is no due date. */
data class Todo(
    val id: Long,
    val title: String,
    val done: Boolean,
    val urgent: Boolean,
    val due: Long
)

object TodoStore {
    private const val KEY = "todos"

    fun all(c: Context): MutableList<Todo> {
        val a = Store.read(c, KEY)
        val out = ArrayList<Todo>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Todo(
                    o.optLong("id"), o.optString("title"), o.optBoolean("done"),
                    o.optBoolean("urgent"), o.optLong("due")
                )
            )
        }
        return out
    }

    private fun save(c: Context, list: List<Todo>) {
        val a = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("title", t.title)
            o.put("done", t.done)
            o.put("urgent", t.urgent)
            o.put("due", t.due)
            a.put(o)
        }
        Store.write(c, KEY, a)
    }

    fun upsert(c: Context, t: Todo) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == t.id }
        if (i >= 0) list[i] = t else list.add(t)
        save(c, list)
    }

    fun delete(c: Context, id: Long) {
        save(c, all(c).filter { it.id != id })
    }

    fun clearDone(c: Context) {
        save(c, all(c).filter { !it.done })
    }
}
