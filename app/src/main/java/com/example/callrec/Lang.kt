package com.example.callrec

import android.content.Context

/** App language. Texts are written in Bangla in the code; [tr] swaps them for English when English is chosen. */
object Lang {
    @JvmField var en = false
    private val map: HashMap<String, String> by lazy {
        val m = HashMap<String, String>(1600)
        for (p in Dict1.pairs()) m[p.first] = p.second
        for (p in Dict2.pairs()) m[p.first] = p.second
        for (p in Dict3.pairs()) m[p.first] = p.second
        for (p in Dict4.pairs()) m[p.first] = p.second
        for (p in Dict5.pairs()) m[p.first] = p.second
        for (p in Dict6.pairs()) m[p.first] = p.second
        for (p in Dict7.pairs()) m[p.first] = p.second
        m
    }

    fun load(ctx: Context) {
        en = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("lang", "bn") == "en"
    }

    fun set(ctx: Context, english: Boolean) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putString("lang", if (english) "en" else "bn")
            .putBoolean("digitsBn", !english)
            .apply()
        en = english
    }

    fun lookup(s: String): String = map[s] ?: s
}

fun tr(s: String): String = if (Lang.en) Lang.lookup(s) else s
