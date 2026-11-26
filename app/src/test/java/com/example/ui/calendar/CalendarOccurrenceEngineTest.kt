package com.example.ui.calendar

import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.TimeZone

class CalendarOccurrenceEngineTest {

    private var defaultTimeZone: TimeZone? = null

    @Before
    fun setUp() {
        defaultTimeZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() {
        defaultTimeZone?.let { TimeZone.setDefault(it) }
    }

    // 1. Date picker helper under negative-offset time zone
    @Test
    fun `picker date helper under a non-UTC zone converts symmetrically`() {
        // Set JVM default time zone to negative offset zone (e.g. Pacific Time GMT-8)
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

        val localDate = LocalDate.of(2026, 11, 6)
        val millis = CalendarDateHelper.localDateToPickerMillis(localDate)
        val restoredDate = CalendarDateHelper.pickerMillisToLocalDate(millis)

        assertEquals("LocalDate must round-trip through UTC picker millis accurately in GMT-8", localDate, restoredDate)

        // Also verify under GMT-10 (Honolulu)
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
        val honoluluDate = LocalDate.of(2024, 2, 29) // Leap day
        val honoluluMillis = CalendarDateHelper.localDateToPickerMillis(honoluluDate)
        assertEquals(honoluluDate, CalendarDateHelper.pickerMillisToLocalDate(honoluluMillis))
    }

    // 2. Rest day in the past is not missed
    @Test
    fun `Rest day in the past is not missed`() {
        val today = LocalDate.of(2026, 11, 6)
        val pastDate = LocalDate.of(2026, 11, 4)

        // Rest day occurrence has planId == null
        val restDayOccurrence = CalendarOccurrence(
            eventId = 1L,
            planId = null,
            title = "Rest day",
            date = pastDate,
            timeHour = 8,
            timeMinute = 0,
            repeatType = "NONE",
            isRecurring = false
        )

        val dotState = CalendarOccurrenceEngine.computeDotState(
            date = pastDate,
            today = today,
            hasCompletedWorkout = false,
            occurrencesOnDay = listOf(restDayOccurrence)
        )

        assertEquals(
            "Rest day in the past must never show a missed (gray) dot",
            CalendarDayDot.NONE,
            dotState
        )

        // Custom note in the past also has planId == null and must not show missed
        val customNoteOccurrence = CalendarOccurrence(
            eventId = 2L,
            planId = null,
            title = "Outdoor Walk",
            date = pastDate,
            timeHour = 9,
            timeMinute = 30,
            repeatType = "NONE",
            isRecurring = false
        )
        val noteDotState = CalendarOccurrenceEngine.computeDotState(
            date = pastDate,
            today = today,
            hasCompletedWorkout = false,
            occurrencesOnDay = listOf(customNoteOccurrence)
        )
        assertEquals(
            "Custom note in the past must never show a missed (gray) dot",
            CalendarDayDot.NONE,
            noteDotState
        )

        // By contrast, an uncompleted workout plan in the past (planId != null) MUST show MISSED
        val missedWorkoutOccurrence = CalendarOccurrence(
            eventId = 3L,
            planId = "plan_full_body",
            title = "Full Body HIIT",
            date = pastDate,
            timeHour = 10,
            timeMinute = 0,
            repeatType = "NONE",
            isRecurring = false
        )
        val workoutDotState = CalendarOccurrenceEngine.computeDotState(
            date = pastDate,
            today = today,
            hasCompletedWorkout = false,
            occurrencesOnDay = listOf(missedWorkoutOccurrence)
        )
        assertEquals(
            "Uncompleted workout plan in the past must show MISSED dot",
            CalendarDayDot.MISSED,
            workoutDotState
        )
    }

    // 3. Disabled event expands to nothing
    @Test
    fun `disabled event expands to nothing`() {
        val disabledOneTimeEvent = CalendarEventEntity(
            id = 10L,
            planId = "plan_arms",
            title = "Arm Shred",
            startDate = "2026-11-10",
            timeHour = 7,
            timeMinute = 0,
            repeatType = "NONE",
            isEnabled = false
        )

        val disabledWeeklyEvent = CalendarEventEntity(
            id = 11L,
            planId = "plan_abs",
            title = "Six Pack Core",
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 127,
            isEnabled = false
        )

        val expanded = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = YearMonth.of(2026, 11),
            events = listOf(disabledOneTimeEvent, disabledWeeklyEvent),
            exceptions = emptyList()
        )

        assertTrue("Disabled events must expand to no occurrences", expanded.isEmpty())
    }

    // 4. RepeatUntil == startDate gives one occurrence
    @Test
    fun `repeatUntil == startDate gives one occurrence`() {
        val friday = LocalDate.of(2026, 11, 6)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        val fridayMask = 1 shl CalendarOccurrenceEngine.dayOfWeekToMaskBit(DayOfWeek.FRIDAY)

        val event = CalendarEventEntity(
            id = 20L,
            planId = "plan_legs",
            title = "Leg Day",
            startDate = "2026-11-06",
            timeHour = 9,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = fridayMask,
            repeatUntil = "2026-11-06",
            isEnabled = true
        )

        val expanded = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = YearMonth.of(2026, 11),
            events = listOf(event),
            exceptions = emptyList()
        )

        val allOccurrences = expanded.values.flatten()
        assertEquals("Weekly event with repeatUntil == startDate must produce exactly 1 occurrence", 1, allOccurrences.size)
        assertEquals(friday, allOccurrences.first().date)
    }

    // 5. Skip exception on a NONE event removes it
    @Test
    fun `skip exception on a NONE event removes it`() {
        val event = CalendarEventEntity(
            id = 30L,
            planId = "plan_chest",
            title = "Chest Blast",
            startDate = "2026-11-15",
            timeHour = 14,
            timeMinute = 30,
            repeatType = "NONE",
            isEnabled = true
        )

        val exception = CalendarEventExceptionEntity(
            id = 1L,
            eventId = 30L,
            occurrenceDate = "2026-11-15",
            type = "SKIPPED"
        )

        val expanded = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = YearMonth.of(2026, 11),
            events = listOf(event),
            exceptions = listOf(exception)
        )

        assertTrue("A SKIPPED exception on a one-time event must remove that occurrence", expanded.isEmpty())
    }

    // 6. Month boundary and leap year verification
    @Test
    fun `weekly recurrence handles month boundaries and February leap year`() {
        // Wednesday Jan 31, 2024 -> repeating every Wednesday
        val wednesdayMask = 1 shl CalendarOccurrenceEngine.dayOfWeekToMaskBit(DayOfWeek.WEDNESDAY)
        val event = CalendarEventEntity(
            id = 40L,
            planId = "plan_full_body",
            title = "Full Body Wednesday",
            startDate = "2024-01-31",
            timeHour = 17,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = wednesdayMask,
            isEnabled = true
        )

        // Expand for February 2024 (Leap year with 29 days)
        val feb2024 = YearMonth.of(2024, 2)
        assertEquals(29, feb2024.lengthOfMonth())

        val expanded = CalendarOccurrenceEngine.expandForMonth(
            yearMonth = feb2024,
            events = listOf(event),
            exceptions = emptyList()
        )

        // Wednesdays in Feb 2024 are: 7, 14, 21, 28
        val dates = expanded.values.flatten().map { it.date }.sorted()
        assertEquals(4, dates.size)
        assertEquals(LocalDate.of(2024, 2, 7), dates[0])
        assertEquals(LocalDate.of(2024, 2, 14), dates[1])
        assertEquals(LocalDate.of(2024, 2, 21), dates[2])
        assertEquals(LocalDate.of(2024, 2, 28), dates[3])
    }

    // 7. Time format tests: 24-hour vs 12-hour
    @Test
    fun `formatTime respects 24-hour and 12-hour formats`() {
        // 24-hour format
        assertEquals("08:05", CalendarDateHelper.formatTime(is24Hour = true, hour = 8, minute = 5))
        assertEquals("14:30", CalendarDateHelper.formatTime(is24Hour = true, hour = 14, minute = 30))
        assertEquals("00:00", CalendarDateHelper.formatTime(is24Hour = true, hour = 0, minute = 0))
        assertEquals("23:59", CalendarDateHelper.formatTime(is24Hour = true, hour = 23, minute = 59))

        // 12-hour format
        assertEquals("8:05 AM", CalendarDateHelper.formatTime(is24Hour = false, hour = 8, minute = 5))
        assertEquals("2:30 PM", CalendarDateHelper.formatTime(is24Hour = false, hour = 14, minute = 30))
        assertEquals("12:00 AM", CalendarDateHelper.formatTime(is24Hour = false, hour = 0, minute = 0))
        assertEquals("12:00 PM", CalendarDateHelper.formatTime(is24Hour = false, hour = 12, minute = 0))
        assertEquals("11:59 PM", CalendarDateHelper.formatTime(is24Hour = false, hour = 23, minute = 59))
    }
}
