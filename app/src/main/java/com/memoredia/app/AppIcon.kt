package com.memoredia.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color

/** The launcher icon follows the accent colour: one icon per preset, the nearest one for a custom colour. */
object AppIcon {

    private val RES = intArrayOf(
        R.mipmap.ic_launcher_0, R.mipmap.ic_launcher_1, R.mipmap.ic_launcher_2,
        R.mipmap.ic_launcher_3, R.mipmap.ic_launcher_4, R.mipmap.ic_launcher_5
    )

    fun index(ctx: Context): Int {
        val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val ai = p.getInt("accent", 0).coerceIn(0, Ui.ACCENTS.size)
        if (ai < Ui.ACCENTS.size) return Math.min(ai, RES.size - 1)
        val c = try { Color.parseColor(p.getString("accentHex", "#2D5BE3")) } catch (e: Throwable) { Color.parseColor("#2D5BE3") }
        var best = 0
        var bd = Int.MAX_VALUE
        for ((i, a) in Ui.ACCENTS.withIndex()) {
            val x = Color.parseColor(a.light)
            val dr = Color.red(x) - Color.red(c)
            val dg = Color.green(x) - Color.green(c)
            val db = Color.blue(x) - Color.blue(c)
            val d = dr * dr + dg * dg + db * db
            if (d < bd) { bd = d; best = i }
        }
        return Math.min(best, RES.size - 1)
    }

    fun res(ctx: Context): Int = RES[index(ctx)]

    /** Turns on the launcher entry that matches the accent and turns the others off. */
    fun sync(ctx: Context) {
        try {
            val want = index(ctx)
            val pm = ctx.packageManager
            for (i in RES.indices) {
                val cn = ComponentName(ctx, "com.memoredia.app.Icon$i")
                val on = i == want
                val cur = pm.getComponentEnabledSetting(cn)
                val isOn = cur == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || (cur == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i == 0)
                if (isOn != on) {
                    pm.setComponentEnabledSetting(cn, if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                }
            }
        } catch (e: Throwable) {
        }
    }
}
