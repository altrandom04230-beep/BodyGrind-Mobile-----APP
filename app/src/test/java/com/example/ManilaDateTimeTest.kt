package com.example

import com.example.utils.ManilaDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

class ManilaDateTimeTest {

    @Test
    fun formatDate_usesCanonicalZeroPaddedFormat() {
        val date = LocalDate.of(2026, 3, 5)
        assertEquals("Mar 05, 2026", ManilaDateTime.formatDate(date))
        assertEquals("Mar 05, 2026", ManilaDateTime.formatDateKey("2026-03-05"))
    }

    @Test
    fun asiaManilaRollover_isIndependentOfDeviceTimeZone() {
        val previousDefault = TimeZone.getDefault()
        try {
            // Simulate a US Pacific (UTC-8) device
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

            // 2025-12-31T16:30:00Z is 2025-12-31 08:30 AM in Los Angeles,
            // but 2026-01-01 00:30 AM in Asia/Manila (UTC+8)!
            val utcMs = Instant.parse("2025-12-31T16:30:00Z").toEpochMilli()

            assertEquals(LocalDate.of(2026, 1, 1), ManilaDateTime.utcMillisToManilaDate(utcMs))
            assertEquals("Jan 01, 2026", ManilaDateTime.formatDate(utcMs))
            assertEquals("2026-01-01", ManilaDateTime.toDateKey(utcMs))
            assertEquals("Jan 01, 2026 • 12:30 AM", ManilaDateTime.formatDateTime(utcMs))
        } finally {
            TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun material3DatePickerUtcMidnightRoundTrip_neverShiftsDayOrYear() {
        val previousDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))

            // Material 3 DatePicker returns UTC midnight for Jan 01, 2026
            val pickerUtcMidnight = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()

            val manilaDate = ManilaDateTime.datePickerUtcMsToManilaDate(pickerUtcMidnight)
            assertEquals(LocalDate.of(2026, 1, 1), manilaDate)

            val startOfManilaDayUtcMs = ManilaDateTime.datePickerUtcMsToManilaStartOfDayUtcMs(pickerUtcMidnight)
            // 2026-01-01 00:00:00+08:00 == 2025-12-31T16:00:00Z
            assertEquals(Instant.parse("2025-12-31T16:00:00Z").toEpochMilli(), startOfManilaDayUtcMs)
            assertEquals("Jan 01, 2026", ManilaDateTime.formatDate(startOfManilaDayUtcMs))

            // Legacy UTC-midnight stored timestamp also resolves to Jan 01, 2026 in Asia/Manila
            assertEquals(LocalDate.of(2026, 1, 1), ManilaDateTime.assignedPlanManilaDate(pickerUtcMidnight))

            // Round-trip back into DatePicker UTC millis
            assertEquals(pickerUtcMidnight, ManilaDateTime.utcMillisToDatePickerUtcMs(startOfManilaDayUtcMs))
        } finally {
            TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun parseFirestoreUtcMillis_handlesSecondsMillisIsoAndRejectsZero() {
        assertNull(ManilaDateTime.parseFirestoreUtcMillis(null))
        assertNull(ManilaDateTime.parseFirestoreUtcMillis(0L))
        assertNull(ManilaDateTime.parseFirestoreUtcMillis(-100L))
        assertEquals("—", ManilaDateTime.formatDate(0L))

        // 10-digit Unix seconds auto-normalized to milliseconds
        val seconds = 1_772_668_800L // 2026-03-05T00:00:00Z
        val expectedMs = seconds * 1000L
        assertEquals(expectedMs, ManilaDateTime.parseFirestoreUtcMillis(seconds))
        assertEquals("Mar 05, 2026", ManilaDateTime.formatDate(ManilaDateTime.parseFirestoreUtcMillis(seconds)))

        // 13-digit milliseconds preserved as-is
        assertEquals(expectedMs, ManilaDateTime.parseFirestoreUtcMillis(expectedMs))

        // ISO-8601 string supported
        assertEquals(expectedMs, ManilaDateTime.parseFirestoreUtcMillis("2026-03-05T00:00:00Z"))
    }

    @Test
    fun isSameManilaDay_comparesUsingManilaDayBoundary() {
        val beforeMidnightManila = Instant.parse("2026-03-05T15:59:59Z").toEpochMilli() // Mar 05 23:59:59 +08
        val afterMidnightManila = Instant.parse("2026-03-05T16:00:01Z").toEpochMilli()  // Mar 06 00:00:01 +08
        val laterSameManilaDay = Instant.parse("2026-03-06T08:00:00Z").toEpochMilli()   // Mar 06 16:00:00 +08

        assertFalse(ManilaDateTime.isSameManilaDay(beforeMidnightManila, afterMidnightManila))
        assertTrue(ManilaDateTime.isSameManilaDay(afterMidnightManila, laterSameManilaDay))
    }

    @Test
    fun formatRelativeDay_returnsTodayTomorrowOrCanonicalDate() {
        val today = LocalDate.of(2026, 3, 5)
        val tomorrow = LocalDate.of(2026, 3, 6)
        val yesterday = LocalDate.of(2026, 3, 4)
        val nextWeek = LocalDate.of(2026, 3, 12)

        assertEquals("Today", ManilaDateTime.formatRelativeDay(today, today = today))
        assertEquals("Tomorrow", ManilaDateTime.formatRelativeDay(tomorrow, today = today))
        assertEquals("Mar 04, 2026", ManilaDateTime.formatRelativeDay(yesterday, today = today))
        assertEquals("Mar 12, 2026", ManilaDateTime.formatRelativeDay(nextWeek, today = today))
    }

    @Test
    fun formatRelativeDateTime_preservesTwelveHourTimeStyle() {
        // Pinned today: 2026-03-05 in Manila
        val todayDate = LocalDate.of(2026, 3, 5)
        val todayMs = ManilaDateTime.manilaDateTimeToUtcMs(todayDate, 6, 30) // 6:30 AM
        val tomorrowMs = ManilaDateTime.manilaDateTimeToUtcMs(LocalDate.of(2026, 3, 6), 7, 15) // 7:15 AM
        val pastDateMs = ManilaDateTime.manilaDateTimeToUtcMs(LocalDate.of(2026, 3, 4), 18, 40) // 6:40 PM

        val nowPinnedUtcMs = ManilaDateTime.manilaDateTimeToUtcMs(todayDate, 12, 0)

        assertEquals("Today, 6:30 AM", ManilaDateTime.formatRelativeDateTime(todayMs, nowUtcMs = nowPinnedUtcMs))
        assertEquals("Tomorrow, 7:15 AM", ManilaDateTime.formatRelativeDateTime(tomorrowMs, nowUtcMs = nowPinnedUtcMs))
        assertEquals("Mar 04, 2026, 6:40 PM", ManilaDateTime.formatRelativeDateTime(pastDateMs, nowUtcMs = nowPinnedUtcMs))
    }

    @Test
    fun formatRelativeDueDay_returnsDueTodayTomorrowOrCanonicalDate() {
        val today = LocalDate.of(2026, 3, 5)
        val tomorrow = LocalDate.of(2026, 3, 6)
        val future = LocalDate.of(2026, 3, 7)

        assertEquals("Due Today", ManilaDateTime.formatRelativeDueDay(today, today))
        assertEquals("Tomorrow", ManilaDateTime.formatRelativeDueDay(tomorrow, today))
        assertEquals("Mar 07, 2026", ManilaDateTime.formatRelativeDueDay(future, today))
    }

    @Test
    fun rollingPastManilaDate_neverFallsIntoPreviousYear() {
        val todayJan1 = LocalDate.of(2026, 1, 1)
        val rolling75OnJan1 = ManilaDateTime.rollingPastManilaDate(75, today = todayJan1)
        assertEquals(2026, rolling75OnJan1.year)
        assertEquals(LocalDate.of(2026, 1, 1), rolling75OnJan1)

        val todayJan15 = LocalDate.of(2026, 1, 15)
        val rolling75OnJan15 = ManilaDateTime.rollingPastManilaDate(75, today = todayJan15)
        assertEquals(2026, rolling75OnJan15.year)
        assertTrue(rolling75OnJan15.isAfter(LocalDate.of(2025, 12, 31)))

        val todayNov22 = LocalDate.of(2026, 11, 22)
        val rolling75OnNov22 = ManilaDateTime.rollingPastManilaDate(75, today = todayNov22)
        assertEquals(2026, rolling75OnNov22.year)
        assertEquals(LocalDate.of(2026, 9, 8), rolling75OnNov22)
    }

    @Test
    fun mockTrainerDataSource_generatesRollingDatesInCurrentYear() {
        val currentYear = ManilaDateTime.todayManila().year
        val members = com.example.data.trainer.MockTrainerDataSource.sampleMembers

        assertTrue(members.isNotEmpty())
        members.forEach { m ->
            assertTrue("joinDate ${m.joinDate} must contain current year $currentYear", m.joinDate.contains(currentYear.toString()))
            m.workoutHistory.forEach { w ->
                assertFalse("workout dateLabel '${w.dateLabel}' should not contain 'Yesterday'", w.dateLabel.contains("Yesterday"))
                assertFalse("workout dateLabel '${w.dateLabel}' should not contain 'days ago'", w.dateLabel.contains("days ago"))
            }
            m.attendanceHistory.forEach { a ->
                assertFalse("attendance dateLabel '${a.dateLabel}' should not contain 'Yesterday'", a.dateLabel.contains("Yesterday"))
                assertFalse("attendance dateLabel '${a.dateLabel}' should not contain 'days ago'", a.dateLabel.contains("days ago"))
            }
        }

        val tasks = com.example.data.trainer.MockTrainerDataSource.samplePendingTasks
        tasks.forEach { t ->
            assertFalse("dueLabel '${t.dueLabel}' should not contain 'In 2 days'", t.dueLabel.contains("In 2 days"))
        }

        val feedback = com.example.data.trainer.MockTrainerDataSource.initialFeedbackEntries
        feedback.forEach { fb ->
            assertFalse("feedback timestampLabel '${fb.timestampLabel}' should not contain 'Yesterday'", fb.timestampLabel.contains("Yesterday"))
            assertFalse("feedback timestampLabel '${fb.timestampLabel}' should not contain 'days ago'", fb.timestampLabel.contains("days ago"))
        }
    }
}
