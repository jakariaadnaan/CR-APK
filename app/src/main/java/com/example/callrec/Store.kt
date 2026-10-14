package com.example.callrec

import android.content.Context
import org.json.JSONArray

/** Tiny JSON-array storage on top of SharedPreferences. Enough for personal ledgers and notes. */
object Store {
    private fun sp(c: Context) = c.getSharedPreferences("appdata", Context.MODE_PRIVATE)

    fun read(c: Context, key: String): JSONArray =
        try {
            JSONArray(sp(c).getString(key, "[]"))
        } catch (e: Exception) {
            JSONArray()
        }

    fun write(c: Context, key: String, arr: JSONArray) {
        sp(c).edit().putString(key, arr.toString()).commit()
    }
}
