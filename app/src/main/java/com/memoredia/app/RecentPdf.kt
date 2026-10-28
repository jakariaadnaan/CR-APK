package com.memoredia.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** The document library of PDF Edit: saved or added PDFs with folders, tags, stars and OCR text for searching. */
object RecentPdf {
    class Item(
        var uri: String, var name: String, var time: Long, var size: Long,
        var folder: String = "", var tags: List<String> = emptyList(), var star: Boolean = false, var ocr: String = ""
    )

    private fun sp(c: Context) = c.getSharedPreferences("pdfedit_recent", Context.MODE_PRIVATE)

    fun list(c: Context): List<Item> {
        val out = ArrayList<Item>()
        try {
            val a = JSONArray(sp(c).getString("l", "[]"))
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val tg = ArrayList<String>()
                val ta = o.optJSONArray("g")
                if (ta != null) for (k in 0 until ta.length()) tg.add(ta.getString(k))
                out.add(Item(o.getString("u"), o.getString("n"), o.getLong("t"), o.getLong("s"), o.optString("f", ""), tg, o.optBoolean("x", false), o.optString("o", "")))
            }
        } catch (e: Throwable) { }
        return out
    }

    private fun write(c: Context, l: List<Item>) {
        val a = JSONArray()
        for (it in l.take(300)) {
            val o = JSONObject().put("u", it.uri).put("n", it.name).put("t", it.time).put("s", it.size).put("f", it.folder).put("x", it.star).put("o", it.ocr.take(20000))
            val g = JSONArray()
            for (t in it.tags) g.put(t)
            o.put("g", g)
            a.put(o)
        }
        sp(c).edit().putString("l", a.toString()).apply()
    }

    private fun keep(c: Context, u: Uri) {
        try { c.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Throwable) { }
    }

    fun add(c: Context, u: Uri, name: String, size: Long, ocr: String = "") {
        keep(c, u)
        val all = list(c)
        val old = all.firstOrNull { it.uri == u.toString() }
        val l = ArrayList(all.filter { it.uri != u.toString() })
        l.add(0, Item(u.toString(), name, System.currentTimeMillis(), size, old?.folder ?: "", old?.tags ?: emptyList(), old?.star ?: false, if (ocr.isNotEmpty()) ocr else (old?.ocr ?: "")))
        write(c, l)
    }

    fun remove(c: Context, uri: String) = write(c, list(c).filter { it.uri != uri })

    fun remove(c: Context, uris: List<String>) = write(c, list(c).filter { it.uri !in uris })

    fun update(c: Context, uris: List<String>, f: (Item) -> Unit) {
        val l = list(c)
        for (it in l) if (it.uri in uris) f(it)
        write(c, l)
    }

    fun rename(c: Context, uri: String, name: String) = update(c, listOf(uri)) { it.name = name }

    fun folders(c: Context): List<String> {
        val out = ArrayList<String>()
        try {
            val a = JSONArray(sp(c).getString("folders", "[]"))
            for (i in 0 until a.length()) out.add(a.getString(i))
        } catch (e: Throwable) { }
        return out
    }

    private fun writeFolders(c: Context, l: List<String>) {
        val a = JSONArray()
        for (f in l) a.put(f)
        sp(c).edit().putString("folders", a.toString()).apply()
    }

    fun addFolder(c: Context, name: String) {
        val l = folders(c)
        if (name.isNotBlank() && !l.contains(name)) writeFolders(c, l + name)
    }

    fun deleteFolder(c: Context, name: String) {
        writeFolders(c, folders(c).filter { it != name })
        val l = list(c)
        for (it in l) if (it.folder == name) it.folder = ""
        write(c, l)
    }

    fun tags(c: Context): List<String> = list(c).flatMap { it.tags }.distinct().sorted()
}
