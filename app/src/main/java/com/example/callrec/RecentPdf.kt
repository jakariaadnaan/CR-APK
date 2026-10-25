package com.example.callrec

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/** The PDFs saved from PDF Edit (or added by hand): the document library with folders, tags and stars. */
object RecentPdf {
    class Item(
        val uri: String, val name: String, val time: Long, val size: Long,
        val folder: String = "", val tags: List<String> = emptyList(), val star: Boolean = false
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
                out.add(Item(o.getString("u"), o.getString("n"), o.getLong("t"), o.getLong("s"), o.optString("f", ""), tg, o.optBoolean("x", false)))
            }
        } catch (e: Throwable) { }
        return out
    }

    private fun write(c: Context, l: List<Item>) {
        val a = JSONArray()
        for (it in l.take(300)) {
            val o = JSONObject().put("u", it.uri).put("n", it.name).put("t", it.time).put("s", it.size).put("f", it.folder).put("x", it.star)
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

    fun add(c: Context, u: Uri, name: String, size: Long) {
        keep(c, u)
        val old = list(c).firstOrNull { it.uri == u.toString() }
        val l = ArrayList(list(c).filter { it.uri != u.toString() })
        l.add(0, Item(u.toString(), name, System.currentTimeMillis(), size, old?.folder ?: "", old?.tags ?: emptyList(), old?.star ?: false))
        write(c, l)
    }

    fun remove(c: Context, uri: String) = write(c, list(c).filter { it.uri != uri })

    fun update(c: Context, uri: String, f: (Item) -> Item) = write(c, list(c).map { if (it.uri == uri) f(it) else it })

    fun rename(c: Context, uri: String, name: String) = update(c, uri) { Item(it.uri, name, it.time, it.size, it.folder, it.tags, it.star) }

    fun setFolder(c: Context, uri: String, folder: String) = update(c, uri) { Item(it.uri, it.name, it.time, it.size, folder, it.tags, it.star) }

    fun setTags(c: Context, uri: String, tags: List<String>) = update(c, uri) { Item(it.uri, it.name, it.time, it.size, it.folder, tags, it.star) }

    fun setStar(c: Context, uri: String, star: Boolean) = update(c, uri) { Item(it.uri, it.name, it.time, it.size, it.folder, it.tags, star) }

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

    fun removeFolder(c: Context, name: String) {
        writeFolders(c, folders(c).filter { it != name })
        write(c, list(c).map { if (it.folder == name) Item(it.uri, it.name, it.time, it.size, "", it.tags, it.star) else it })
    }

    fun renameFolder(c: Context, old: String, new: String) {
        if (new.isBlank() || folders(c).contains(new)) return
        writeFolders(c, folders(c).map { if (it == old) new else it })
        write(c, list(c).map { if (it.folder == old) Item(it.uri, it.name, it.time, it.size, new, it.tags, it.star) else it })
    }
}
