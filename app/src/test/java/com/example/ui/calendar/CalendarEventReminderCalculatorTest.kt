package com.example.ui.calendar

import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import com.example.ui.calendar.reminder.CalendarEventReminderCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarEventReminderCalculatorTest {

    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `one-time event in future subtracts offset correctly`() {
        val now = ZonedDateTime.of(2026, 11, 7, 7, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 1L,
            title = "Morning Workout",
            startDate = "2026-11-07",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )

        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, emptyList(), now)
        assertNotNull(nextAlarm)
        assertEquals(ZonedDateTime.of(2026, 11, 7, 7, 50, 0, 0, zone), nextAlarm)
    }

    @Test
    fun `one-time event at start (0 minutes offset) fires at exact event time`() {
        val now = ZonedDateTime.of(2026, 11, 7, 7, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 2L,
            title = "Start Time Event",
            startDate = "2026-11-07",
            timeHour = 9,
            timeMinute = 15,
            repeatType = "NONE",
            reminderMinutesBefore = 0,
            isEnabled = true
        )

        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, emptyList(), now)
        assertNotNull(nextAlarm)
        assertEquals(ZonedDateTime.of(2026, 11, 7, 9, 15, 0, 0, zone), nextAlarm)
    }

    @Test
    fun `past one-time event returns null`() {
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 3L,
            title = "Past Event",
            startDate = "2026-11-07",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )

        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, emptyList(), now)
        assertNull(nextAlarm)
    }

    @Test
    fun `weekly recurring event finds next valid weekday from bitmask including week wrap`() {
        // 2026-11-07 is a Saturday (bit 6 = 64)
        // Bitmask for Monday (bit 1 = 2) and Thursday (bit 4 = 16) -> mask = 18
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 4L,
            title = "Weekly HIIT",
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 30,
            repeatType = "WEEKLY",
            repeatDaysMask = (1 shl 1) or (1 shl 4), // Monday (2) and Thursday (16)
            reminderMinutesBefore = 30,
            isEnabled = true
        )

        // Next valid day after Saturday 2026-11-07 is Monday 2026-11-09
        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, emptyList(), now)
        assertNotNull(nextAlarm)
        // 18:30 minus 30 mins = 18:00 on Monday 2026-11-09
        assertEquals(ZonedDateTime.of(2026, 11, 9, 18, 0, 0, 0, zone), nextAlarm)
    }

    @Test
    fun `weekly recurring event skips occurrence present in exceptions`() {
        // Saturday 2026-11-07
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 5L,
            title = "Weekly Cardio",
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = (1 shl 1) or (1 shl 4), // Monday 2026-11-09, Thursday 2026-11-12
            reminderMinutesBefore = 10,
            isEnabled = true
        )

        // Monday 2026-11-09 is skipped
        val exceptions = listOf(
            CalendarEventExceptionEntity(id = 1L, eventId = 5L, occurrenceDate = "2026-11-09", type = "SKIPPED")
        )

        // Should skip Monday and pick Thursday 2026-11-12
        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, exceptions, now)
        assertNotNull(nextAlarm)
        assertEquals(ZonedDateTime.of(2026, 11, 12, 17, 50, 0, 0, zone), nextAlarm)
    }

    @Test
    fun `repeatUntil is inclusive on last day and returns null after it`() {
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        // Monday 2026-11-09 is within repeatUntil = "2026-11-09"
        val eventInclusive = CalendarEventEntity(
            id = 6L,
            title = "Bootcamp",
            startDate = "2026-11-01",
            timeHour = 19,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 1 shl 1, // Monday
            repeatUntil = "2026-11-09",
            reminderMinutesBefore = 60,
            isEnabled = true
        )
        val alarmOnLastDay = CalendarEventReminderCalculator.calculateNextAlarm(eventInclusive, emptyList(), now)
        assertNotNull(alarmOnLastDay)
        assertEquals(ZonedDateTime.of(2026, 11, 9, 18, 0, 0, 0, zone), alarmOnLastDay)

        // Now move now to after Monday 2026-11-09
        val laterNow = ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, zone)
        val alarmAfterEnd = CalendarEventReminderCalculator.calculateNextAlarm(eventInclusive, emptyList(), laterNow)
        assertNull(alarmAfterEnd)
    }

    @Test
    fun `disabled event or Off reminder returns null`() {
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val disabledEvent = CalendarEventEntity(
            id = 7L,
            title = "Disabled",
            startDate = "2026-11-08",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = false
        )
        assertNull(CalendarEventReminderCalculator.calculateNextAlarm(disabledEvent, emptyList(), now))

        val offReminderEvent = CalendarEventEntity(
            id = 8L,
            title = "Off Reminder",
            startDate = "2026-11-08",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = -1,
            isEnabled = true
        )
        assertNull(CalendarEventReminderCalculator.calculateNextAlarm(offReminderEvent, emptyList(), now))
    }

    @Test
    fun `imminent event in 5 minutes with 10-minute reminder returns immediate time`() {
        // Event at 10:05, reminder 10 min (ideal nominal was 09:55)
        // Now is 10:00 (inside the reminder window, but event not yet started)
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 9L,
            title = "Imminent Event",
            startDate = "2026-11-07",
            timeHour = 10,
            timeMinute = 5,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )

        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, emptyList(), now)
        assertNotNull(nextAlarm)
        assertEquals(now, nextAlarm)
    }

    @Test
    fun `no-repeat case with lastNotifiedOccurrence matching date returns next occurrence or null`() {
        // For one-time imminent event: if lastNotifiedOccurrence = today, returns null
        val now = ZonedDateTime.of(2026, 11, 7, 10, 0, 0, 0, zone)
        val oneTimeEvent = CalendarEventEntity(
            id = 10L,
            title = "One-Time Imminent",
            startDate = "2026-11-07",
            timeHour = 10,
            timeMinute = 5,
            repeatType = "NONE",
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        val resultOneTime = CalendarEventReminderCalculator.calculateNextAlarm(
            oneTimeEvent,
            emptyList(),
            now,
            lastNotifiedOccurrence = LocalDate.of(2026, 11, 7)
        )
        assertNull(resultOneTime)

        // For weekly event: if today (Saturday) was already notified, advances to next occurrence (Sunday)
        val weeklyEvent = CalendarEventEntity(
            id = 11L,
            title = "Weekly Imminent",
            startDate = "2026-11-01",
            timeHour = 10,
            timeMinute = 5,
            repeatType = "WEEKLY",
            repeatDaysMask = (1 shl 6) or (1 shl 0), // Saturday (6) and Sunday (0)
            reminderMinutesBefore = 10,
            isEnabled = true
        )
        val resultWeekly = CalendarEventReminderCalculator.calculateNextAlarm(
            weeklyEvent,
            emptyList(),
            now,
            lastNotifiedOccurrence = LocalDate.of(2026, 11, 7)
        )
        assertNotNull(resultWeekly)
        // Tomorrow Sunday 2026-11-08 at 09:55
        assertEquals(ZonedDateTime.of(2026, 11, 8, 9, 55, 0, 0, zone), resultWeekly)
    }

    @Test
    fun `day boundary reminder before midnight for event just after midnight`() {
        // Event at 00:30 on 2026-11-08 with 60-minute reminder -> nominal alarm is 23:30 on 2026-11-07
        val now = ZonedDateTime.of(2026, 11, 7, 22, 0, 0, 0, zone)
        val event = CalendarEventEntity(
            id = 12L,
            title = "Midnight Training",
            startDate = "2026-11-08",
            timeHour = 0,
            timeMinute = 30,
            repeatType = "NONE",
            reminderMinutesBefore = 60,
            isEnabled = true
        )

        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(event, emptyList(), now)
        assertNotNull(nextAlarm)
        assertEquals(ZonedDateTime.of(2026, 11, 7, 23, 30, 0, 0, zone), nextAlarm)
    }

    @Test
    fun `daylight saving time day in America New_York computes correctly`() {
        // In 2026, US Daylight Saving Time ends on Sunday, Nov 1, 2026 (clocks fall back at 2:00 AM)
        // Event at 08:00 AM on 2026-11-01 with 30 min reminder -> 07:30 AM in America/New_York (UTC-5 EST)
        val now = ZonedDateTime.of(2026, 10, 31, 20, 0, 0, 0, zone)
        val dstEvent = CalendarEventEntity(
            id = 13L,
            title = "DST Morning Workout",
            startDate = "2026-11-01",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "NONE",
            reminderMinutesBefore = 30,
            isEnabled = true
        )

        val nextAlarm = CalendarEventReminderCalculator.calculateNextAlarm(dstEvent, emptyList(), now)
        assertNotNull(nextAlarm)
        assertEquals(ZonedDateTime.of(2026, 11, 1, 7, 30, 0, 0, zone), nextAlarm)
        assertEquals(ZoneOffset.ofHours(-5), nextAlarm!!.offset)
    }
}
