package com.memoredia.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One income or expense line. [date] is the local midnight of that day in epoch millis. */
data class Entry(
    val id: Long,
    val income: Boolean,
    val amount: Double,
    val category: String,
    val note: String,
    val date: Long
)

object LedgerStore {
    private const val KEY = "ledger"

    val EXPENSE_CATS = listOf(
        "খাবার", "বাজার", "যাতায়াত", "বিল", "বাড়ি ভাড়া",
        "চিকিৎসা", "শিক্ষা", "বিনোদন", "কেনাকাটা", "অন্যান্য"
    )
    val INCOME_CATS = listOf("বেতন", "ব্যবসা", "উপহার", "ধার ফেরত", "অন্যান্য")

    fun all(c: Context): MutableList<Entry> {
        val a = Store.read(c, KEY)
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            out.add(
                Entry(
                    o.optLong("id"), o.optBoolean("in"), o.optDouble("amt"),
                    o.optString("cat"), o.optString("note"), o.optLong("date")
                )
            )
        }
        return out
    }

    private fun save(c: Context, list: List<Entry>) {
        val a = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("in", e.income)
            o.put("amt", e.amount)
            o.put("cat", e.category)
            o.put("note", e.note)
            o.put("date", e.date)
            a.put(o)
        }
        Store.write(c, KEY, a)
    }

    fun upsert(c: Context, e: Entry) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == e.id }
        if (i >= 0) list[i] = e else list.add(e)
        save(c, list)
    }

    fun delete(c: Context, id: Long) {
        save(c, all(c).filter { it.id != id })
    }
}
