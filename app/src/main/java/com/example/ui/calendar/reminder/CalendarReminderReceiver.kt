package com.example.ui.calendar.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.ui.calendar.CalendarDateHelper
import com.example.utils.ManilaDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class CalendarReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val eventId = intent?.getLongExtra(EXTRA_EVENT_ID, -1L) ?: -1L
        val occurrenceDateStr = intent?.getStringExtra(EXTRA_OCCURRENCE_DATE)

        if (eventId <= 0L || occurrenceDateStr == null) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(context, eventId, occurrenceDateStr)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal suspend fun handleAlarm(context: Context, eventId: Long, occurrenceDateStr: String) {
        val occurrenceDate = try {
            LocalDate.parse(occurrenceDateStr)
        } catch (_: Exception) {
            return
        }

        val db = AppDatabase.getDatabase(context)
        val event = db.calendarEventDao().getEventById(eventId)
        if (event == null || !event.isEnabled || event.reminderMinutesBefore < 0) {
            return
        }

        val exceptions = db.calendarEventDao().getExceptionsForEvent(eventId)
        val isSkippedOrCancelled = exceptions.any {
            it.occurrenceDate == occurrenceDateStr && (it.type == "SKIPPED" || it.type == "CANCELLED")
        }
        if (isSkippedOrCancelled) {
            // Skipped or cancelled occurrence: post nothing, sync next
            CalendarEventReminderScheduler.syncAlarmForEvent(context, eventId)
            return
        }

        val signature = CalendarEventReminderScheduler.buildSignature(
            occurrenceDate = occurrenceDate,
            hour = event.timeHour,
            minute = event.timeMinute,
            offsetMinutes = event.reminderMinutesBefore
        )

        // Check if already notified for this exact signature
        val lastSig = CalendarEventReminderScheduler.getSignature(context, eventId)
        if (lastSig == signature) {
            CalendarEventReminderScheduler.syncAlarmForEvent(context, eventId)
            return
        }

        // Post notification
        createNotificationChannel(context)

        val title = if (event.reminderMinutesBefore == 0) {
            "Time to train: ${event.title}"
        } else if (event.reminderMinutesBefore == 60) {
            "${event.title} starts in 1 hour"
        } else {
            "${event.title} starts in ${event.reminderMinutesBefore} min"
        }

        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
        val timeStr = CalendarDateHelper.formatTime(is24Hour, event.timeHour, event.timeMinute)
        val dayRelation = ManilaDateTime.formatRelativeDay(occurrenceDate)
        val body = "$timeStr · $dayRelation"

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (300_000 + (eventId % 600_000)).toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (200_000 + (eventId % 700_000)).toInt()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS missing handled gracefully
        }

        // Record that we notified for this signature
        CalendarEventReminderScheduler.recordNotified(context, eventId, signature)

        // Reschedule for later occurrence
        CalendarEventReminderScheduler.syncAlarmForEvent(context, eventId)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Calendar workout reminders for scheduled events"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "calendar_event_reminders"
        const val CHANNEL_NAME = "Calendar workout reminders"
        const val EXTRA_EVENT_ID = "extra_calendar_event_id"
        const val EXTRA_OCCURRENCE_DATE = "extra_calendar_occurrence_date"
    }
}
