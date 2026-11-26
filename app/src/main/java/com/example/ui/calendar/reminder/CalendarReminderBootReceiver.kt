package com.example.ui.calendar.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarReminderBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleBoot(context, action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun handleBoot(context: Context, action: String) {
        val validActions = mutableSetOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            validActions.add(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        }

        if (action in validActions) {
            CalendarEventReminderScheduler.syncAll(context.applicationContext)
        }
    }
}
