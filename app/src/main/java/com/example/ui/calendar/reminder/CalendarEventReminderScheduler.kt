package com.example.ui.calendar.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CalendarEventEntity
import com.example.utils.ManilaDateTime
import java.time.LocalDate
import java.time.ZonedDateTime

object CalendarEventReminderScheduler {

    private const val PREFS_NAME = "calendar_event_reminder_prefs"
    private const val KEY_LAST_NOTIFIED_PREFIX = "last_notified_"
    private const val KEY_EXACT_ALARM_NOT_NOW = "exact_alarm_not_now"

    fun hasOptedOutOfExactAlarm(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_EXACT_ALARM_NOT_NOW, false)
    }

    fun setOptedOutOfExactAlarm(context: Context, optedOut: Boolean = true) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_EXACT_ALARM_NOT_NOW, optedOut).apply()
    }

    fun buildSignature(occurrenceDate: LocalDate, hour: Int, minute: Int, offsetMinutes: Int): String {
        return "%s|%02d:%02d|%d".format(occurrenceDate, hour, minute, offsetMinutes)
    }

    fun getLastNotifiedOccurrence(context: Context, event: CalendarEventEntity): LocalDate? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedSig = prefs.getString("$KEY_LAST_NOTIFIED_PREFIX${event.id}", null) ?: return null
        val parts = savedSig.split("|")
        if (parts.size != 3) return null

        val expectedTimeStr = "%02d:%02d".format(event.timeHour, event.timeMinute)
        val expectedOffsetStr = event.reminderMinutesBefore.toString()
        if (parts[1] != expectedTimeStr || parts[2] != expectedOffsetStr) {
            return null
        }
        return try {
            LocalDate.parse(parts[0])
        } catch (_: Exception) {
            null
        }
    }

    fun recordNotified(context: Context, eventId: Long, signature: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("$KEY_LAST_NOTIFIED_PREFIX$eventId", signature).apply()
    }

    fun clearNotified(context: Context, eventId: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("$KEY_LAST_NOTIFIED_PREFIX$eventId").apply()
    }

    fun getSignature(context: Context, eventId: Long): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("$KEY_LAST_NOTIFIED_PREFIX$eventId", null)
    }

    fun getRequestCode(eventId: Long): Int {
        return (100_000 + (eventId % 800_000)).toInt()
    }

    suspend fun syncAlarmForEvent(context: Context, eventId: Long) {
        val db = AppDatabase.getDatabase(context)
        val event = db.calendarEventDao().getEventById(eventId)
        if (event == null || !event.isEnabled || event.reminderMinutesBefore < 0) {
            cancelAlarmInternal(context, eventId)
            return
        }

        val exceptions = db.calendarEventDao().getExceptionsForEvent(eventId)
        val lastNotified = getLastNotifiedOccurrence(context, event)
        val now = ManilaDateTime.nowManilaZoned()
        val schedule = CalendarEventReminderCalculator.calculateNextAlarmSchedule(
            event = event,
            exceptions = exceptions,
            now = now,
            lastNotifiedOccurrence = lastNotified
        )

        if (schedule == null) {
            cancelAlarmInternal(context, eventId)
            return
        }

        armAlarm(context, event.id, schedule.occurrenceDate, schedule.triggerAt)
    }

    fun cancelAlarmForEvent(context: Context, eventId: Long) {
        clearNotified(context, eventId)
        cancelAlarmInternal(context, eventId)
    }

    private fun cancelAlarmInternal(context: Context, eventId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = getRequestCode(eventId)
        val intent = Intent(context, CalendarReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun armAlarm(
        context: Context,
        eventId: Long,
        occurrenceDate: LocalDate,
        triggerAt: ZonedDateTime
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = getRequestCode(eventId)
        val intent = Intent(context, CalendarReminderReceiver::class.java).apply {
            putExtra(CalendarReminderReceiver.EXTRA_EVENT_ID, eventId)
            putExtra(CalendarReminderReceiver.EXTRA_OCCURRENCE_DATE, occurrenceDate.toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerMillis = triggerAt.toInstant().toEpochMilli()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    suspend fun syncAll(context: Context) {
        val db = AppDatabase.getDatabase(context)
        val eventsWithReminders = db.calendarEventDao().getEventsWithReminders()
        for (event in eventsWithReminders) {
            try {
                syncAlarmForEvent(context, event.id)
            } catch (_: Exception) {}
        }
    }
}
