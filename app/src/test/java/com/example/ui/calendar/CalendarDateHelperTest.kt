package com.example.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarDateHelperTest {

    // 1. Unit test for the snackbar message function: one-time, this-day-only, whole-series
    @Test
    fun `getDeleteSnackbarMessage returns exact user-facing text for all deletion types`() {
        assertEquals(
            "Event deleted",
            CalendarDateHelper.getDeleteSnackbarMessage(CalendarDateHelper.DeleteSnackbarType.ONE_TIME)
        )

        assertEquals(
            "Event deleted for this day",
            CalendarDateHelper.getDeleteSnackbarMessage(CalendarDateHelper.DeleteSnackbarType.THIS_DAY_ONLY)
        )

        assertEquals(
            "All events in this series deleted",
            CalendarDateHelper.getDeleteSnackbarMessage(CalendarDateHelper.DeleteSnackbarType.WHOLE_SERIES)
        )
    }

    // 2. Unit test for the selectable-date rule: dates before the start date are not selectable, start date and later are
    @Test
    fun `isRepeatUntilDateSelectable disables dates before start date and allows start date and later`() {
        val startDate = LocalDate.of(2026, 11, 10)

        // Day before start date: NOT selectable (false)
        val dayBefore = LocalDate.of(2026, 11, 9)
        assertFalse(
            "Date before start date must not be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(dayBefore, startDate)
        )

        val dayBeforeMillis = CalendarDateHelper.localDateToPickerMillis(dayBefore)
        assertFalse(
            "Millis before start date must not be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(dayBeforeMillis, startDate)
        )

        // Start date itself: selectable (true)
        assertTrue(
            "Start date itself must be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(startDate, startDate)
        )

        val startDateMillis = CalendarDateHelper.localDateToPickerMillis(startDate)
        assertTrue(
            "Start date millis must be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(startDateMillis, startDate)
        )

        // Days after start date: selectable (true)
        val dayAfter = LocalDate.of(2026, 11, 11)
        assertTrue(
            "Day after start date must be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(dayAfter, startDate)
        )

        val monthLater = LocalDate.of(2026, 12, 10)
        assertTrue(
            "Month later must be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(monthLater, startDate)
        )

        val monthLaterMillis = CalendarDateHelper.localDateToPickerMillis(monthLater)
        assertTrue(
            "Month later millis must be selectable",
            CalendarDateHelper.isRepeatUntilDateSelectable(monthLaterMillis, startDate)
        )
    }
}
