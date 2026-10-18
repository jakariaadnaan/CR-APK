package com.example.callrec

import android.content.Context

/** Monthly spending limit per expense category (0 = no limit). Stored in prefs "budget". */
object BudgetStore {
    private fun sp(c: Context) = c.getSharedPreferences("budget", Context.MODE_PRIVATE)

    fun get(c: Context, cat: String): Double = sp(c).getFloat("limit_$cat", 0f).toDouble()

    fun set(c: Context, cat: String, v: Double) {
        val e = sp(c).edit()
        if (v <= 0) e.remove("limit_$cat") else e.putFloat("limit_$cat", v.toFloat())
        e.apply()
    }

    fun any(c: Context): Boolean = LedgerStore.EXPENSE_CATS.any { get(c, it) > 0 }

    /** Spent per category for the given month. */
    fun spent(c: Context, year: Int, month: Int): Map<String, Double> {
        val cal = java.util.Calendar.getInstance()
        return LedgerStore.all(c).filter {
            if (it.income) return@filter false
            cal.timeInMillis = it.date
            cal.get(java.util.Calendar.YEAR) == year && cal.get(java.util.Calendar.MONTH) == month
        }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
    }
}
