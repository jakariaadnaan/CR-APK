package com.memoredia.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Sets every reminder alarm again after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val a = intent.action
        if (a != Intent.ACTION_BOOT_COMPLETED && a != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try { DebtReminder.rescheduleAll(ctx) } catch (e: Throwable) { }
        try { TodoReminder.rescheduleAll(ctx) } catch (e: Throwable) { }
        try { PrayerReminder.scheduleAll(ctx) } catch (e: Throwable) { }
        try { DailySummary.schedule(ctx) } catch (e: Throwable) { }
        try { AutoBackup.schedule(ctx) } catch (e: Throwable) { }
    }
}
