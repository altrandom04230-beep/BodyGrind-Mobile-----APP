package com.example.ui.meals

import com.example.data.meals.DayOfWeekPlan
import com.example.utils.ManilaDateTime
import java.util.Calendar

object MealCalendarHelper {

    fun formatDateKey(timestamp: Long): String {
        return ManilaDateTime.toDateKey(timestamp)
    }

    fun formatDateKey(calendar: Calendar): String {
        return ManilaDateTime.toDateKey(calendar.timeInMillis)
    }

    fun formatHeaderDate(timestamp: Long): String {
        return ManilaDateTime.formatDate(timestamp)
    }

    /**
     * Resolves the calendar date corresponding to a given day of the week in the current week (Monday-based)
     * in Asia/Manila time.
     */
    fun getCalendarDateForDay(day: DayOfWeekPlan): Calendar {
        val cal = ManilaDateTime.manilaCalendar()
        cal.firstDayOfWeek = Calendar.MONDAY
        val currentDow = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = when (currentDow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
        // Add day.ordinal (MONDAY = 0, TUESDAY = 1, ... SUNDAY = 6)
        cal.add(Calendar.DAY_OF_YEAR, day.ordinal)
        return cal
    }

    fun isToday(day: DayOfWeekPlan): Boolean {
        return day == DayOfWeekPlan.today()
    }
}
