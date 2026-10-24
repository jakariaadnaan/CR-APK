package com.example.callrec

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** The PDFs saved from PDF Edit, remembered so the Recent Files page can list them. */
object RecentPdf {
    class Item(val uri: String, val name: String, val time: Long, val size: Long)

    private fun sp(c: Context) = c.getSharedPreferences("pdfedit_recent", Context.MODE_PRIVATE)

    fun list(c: Context): List<Item> {
        val out = ArrayList<Item>()
        try {
            val a = JSONArray(sp(c).getString("l", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Item(o.getString("u"), o.getString("n"), o.getLong("t"), o.getLong("s")))
            }
        } catch (e: Throwable) { }
        return out
    }

    private fun write(c: Context, l: List<Item>) {
        val a = JSONArray()
        for (it in l.take(40)) a.put(JSONObject().put("u", it.uri).put("n", it.name).put("t", it.time).put("s", it.size))
        sp(c).edit().putString("l", a.toString()).apply()
    }

    fun add(c: Context, u: Uri, name: String, size: Long) {
        try { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Throwable) { }
        val l = ArrayList(list(c).filter { it.uri != u.toString() })
        l.add(0, Item(u.toString(), name, System.currentTimeMillis(), size))
        write(c, l)
    }

    fun remove(c: Context, uri: String) = write(c, list(c).filter { it.uri != uri })

    fun rename(c: Context, uri: String, name: String) = write(c, list(c).map { if (it.uri == uri) Item(it.uri, name, it.time, it.size) else it })
}
