package com.example.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WorkoutReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Time for Your Daily Workout!"
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "Stay consistent! 15 minutes of bodyweight exercises can transform your health."

        NotificationHelper.showWorkoutReminder(context, title, message)

        // Reschedule for next day
        val hour = intent?.getIntExtra(EXTRA_HOUR, 8) ?: 8
        val minute = intent?.getIntExtra(EXTRA_MINUTE, 0) ?: 0
        WorkoutReminderScheduler.scheduleDailyReminder(context, hour, minute)
    }

    companion object {
        const val EXTRA_TITLE = "extra_reminder_title"
        const val EXTRA_MESSAGE = "extra_reminder_message"
        const val EXTRA_HOUR = "extra_reminder_hour"
        const val EXTRA_MINUTE = "extra_reminder_minute"
    }
}
