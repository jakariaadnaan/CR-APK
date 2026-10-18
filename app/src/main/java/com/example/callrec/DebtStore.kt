package com.example.callrec

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One বাকি line. [owe] = true means I have to pay; false means I will receive. */
data class Debt(
    val id: Long,
    val owe: Boolean,
    val name: String,
    val amount: Double,
    val note: String,
    val date: Long,
    /** Payments so far: (day, amount). */
    val pays: List<Pair<Long, Double>> = emptyList(),
    /** Reminder day (local midnight), 0 = none. */
    val due: Long = 0L
)

object DebtStore {
    private const val KEY = "debts"

    fun all(c: Context): MutableList<Debt> {
        val a = Store.read(c, KEY)
        val out = ArrayList<Debt>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val pa = o.optJSONArray("pays")
            val pays = ArrayList<Pair<Long, Double>>()
            if (pa != null) for (k in 0 until pa.length()) {
                val po = pa.optJSONObject(k) ?: continue
                pays.add(Pair(po.optLong("t"), po.optDouble("a")))
            }
            out.add(
                Debt(
                    o.optLong("id"), o.optBoolean("owe"), o.optString("name"),
                    o.optDouble("amt"), o.optString("note"), o.optLong("date"), pays, o.optLong("due")
                )
            )
        }
        return out
    }

    private fun save(c: Context, list: List<Debt>) {
        val a = JSONArray()
        for (d in list) {
            val o = JSONObject()
            o.put("id", d.id)
            o.put("owe", d.owe)
            o.put("name", d.name)
            o.put("amt", d.amount)
            o.put("note", d.note)
            o.put("date", d.date)
            o.put("due", d.due)
            val pa = JSONArray()
            for (pp in d.pays) pa.put(JSONObject().put("t", pp.first).put("a", pp.second))
            o.put("pays", pa)
            a.put(o)
        }
        Store.write(c, KEY, a)
    }

    fun upsert(c: Context, d: Debt) {
        val list = all(c)
        val i = list.indexOfFirst { it.id == d.id }
        if (i >= 0) list[i] = d else list.add(d)
        save(c, list)
        DebtReminder.schedule(c, d)
    }

    fun delete(c: Context, id: Long) {
        DebtReminder.cancel(c, id)
        save(c, all(c).filter { it.id != id })
    }

    fun names(c: Context): List<String> = all(c).map { it.name }.distinct().sorted()
}
