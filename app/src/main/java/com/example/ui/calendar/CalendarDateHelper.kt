package com.example.ui.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object CalendarDateHelper {
    /**
     * Converts a LocalDate to epoch milliseconds at start-of-day in UTC.
     * Material 3 DatePicker expects and returns UTC millis for calendar dates.
     */
    fun localDateToPickerMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    /**
     * Converts epoch milliseconds from Material 3 DatePicker back to LocalDate using UTC.
     */
    fun pickerMillisToLocalDate(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    }

    /**
     * Formats hour and minute according to whether 24-hour format is enabled.
     * 24-hour: "08:05", "14:30", "00:00"
     * 12-hour: "8:05 AM", "2:30 PM", "12:00 AM"
     */
    fun formatTime(is24Hour: Boolean, hour: Int, minute: Int): String {
        return if (is24Hour) {
            "%02d:%02d".format(hour, minute)
        } else {
            val amPm = if (hour >= 12) "PM" else "AM"
            val hour12 = when (val h = hour % 12) {
                0 -> 12
                else -> h
            }
            "%d:%02d %s".format(hour12, minute, amPm)
        }
    }

    enum class DeleteSnackbarType {
        ONE_TIME,
        THIS_DAY_ONLY,
        WHOLE_SERIES
    }

    /**
     * Returns the exact user-facing snackbar message corresponding to the deletion type.
     * - one-time event deleted: "Event deleted"
     * - "This day only" delete: "Event deleted for this day"
     * - whole series deleted: "All events in this series deleted"
     */
    fun getDeleteSnackbarMessage(type: DeleteSnackbarType): String {
        return when (type) {
            DeleteSnackbarType.ONE_TIME -> "Event deleted"
            DeleteSnackbarType.THIS_DAY_ONLY -> "Event deleted for this day"
            DeleteSnackbarType.WHOLE_SERIES -> "All events in this series deleted"
        }
    }

    /**
     * Pure function returning whether a candidate date (given as UTC millis) is selectable for repeat-until.
     * Dates before the event's start date are disabled (not selectable).
     * The start date and later dates are selectable.
     */
    fun isRepeatUntilDateSelectable(utcTimeMillis: Long, startDate: LocalDate): Boolean {
        val candidateDate = pickerMillisToLocalDate(utcTimeMillis)
        return isRepeatUntilDateSelectable(candidateDate, startDate)
    }

    /**
     * Overload for LocalDate to facilitate direct unit testing.
     */
    fun isRepeatUntilDateSelectable(candidateDate: LocalDate, startDate: LocalDate): Boolean {
        return !candidateDate.isBefore(startDate)
    }

    /**
     * Formats reminder minutes before into user-facing short label:
     * - 0: "At start"
     * - 10: "10 min before"
     * - 30: "30 min before"
     * - 60: "1 hour before"
     * - -1 / other: "Off"
     */
    fun formatReminder(minutes: Int): String {
        return when (minutes) {
            0 -> "At start"
            10 -> "10 min before"
            30 -> "30 min before"
            60 -> "1 hour before"
            else -> if (minutes > 0) "$minutes min before" else "Off"
        }
    }

    /**
     * Formats reminder minutes into clear content description for TalkBack:
     */
    fun formatReminderContentDescription(minutes: Int): String {
        return when (minutes) {
            0 -> "Reminder at start"
            10 -> "Reminder 10 minutes before"
            30 -> "Reminder 30 minutes before"
            60 -> "Reminder 1 hour before"
            else -> if (minutes > 0) "Reminder $minutes minutes before" else "No reminder"
        }
    }
}
