package com.example.ui.calendar

import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException

data class CalendarOccurrence(
    val eventId: Long,
    val planId: String?,
    val title: String,
    val date: LocalDate,
    val timeHour: Int,
    val timeMinute: Int,
    val repeatType: String,
    val isRecurring: Boolean,
    val note: String? = null,
    val isCancelled: Boolean = false,
    val cancelledReason: String? = null,
    val cancelledByName: String? = null,
    val cancelledByRole: String? = null,
    val cancelledAtUtcMs: Long? = null
)

enum class CalendarDayDot {
    NONE,
    COMPLETED,
    PLANNED,
    MISSED
}

object CalendarOccurrenceEngine {

    /**
     * Map DayOfWeek to bitmask position:
     * Sunday = 0 (1), Monday = 1 (2), Tuesday = 2 (4), Wednesday = 3 (8),
     * Thursday = 4 (16), Friday = 5 (32), Saturday = 6 (64).
     */
    fun dayOfWeekToMaskBit(dayOfWeek: DayOfWeek): Int {
        return when (dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }
    }

    fun isDayIncluded(mask: Int, dayOfWeek: DayOfWeek): Boolean {
        val bit = dayOfWeekToMaskBit(dayOfWeek)
        return (mask and (1 shl bit)) != 0
    }

    fun expandForMonth(
        yearMonth: YearMonth,
        events: List<CalendarEventEntity>,
        exceptions: List<CalendarEventExceptionEntity>
    ): Map<LocalDate, List<CalendarOccurrence>> {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()
        val skippedSet = exceptions
            .filter { it.type == "SKIPPED" }
            .map { "${it.eventId}_${it.occurrenceDate}" }
            .toSet()
        val cancelledMap = exceptions
            .filter { it.type == "CANCELLED" }
            .associateBy { "${it.eventId}_${it.occurrenceDate}" }

        val results = mutableListOf<CalendarOccurrence>()

        for (event in events) {
            if (!event.isEnabled) continue

            val eventStart = try {
                LocalDate.parse(event.startDate)
            } catch (e: DateTimeParseException) {
                continue
            }

            val eventUntil = event.repeatUntil?.let {
                try {
                    LocalDate.parse(it)
                } catch (e: DateTimeParseException) {
                    null
                }
            }

            when (event.repeatType) {
                "NONE" -> {
                    val key = "${event.id}_${eventStart}"
                    if (!skippedSet.contains(key)) {
                        if (eventStart in firstDay..lastDay) {
                            val cancelEx = cancelledMap[key]
                            results.add(
                                CalendarOccurrence(
                                    eventId = event.id,
                                    planId = event.planId,
                                    title = event.title,
                                    date = eventStart,
                                    timeHour = event.timeHour,
                                    timeMinute = event.timeMinute,
                                    repeatType = "NONE",
                                    isRecurring = false,
                                    note = event.note,
                                    isCancelled = cancelEx != null,
                                    cancelledReason = cancelEx?.cancelledReason,
                                    cancelledByName = cancelEx?.cancelledByName,
                                    cancelledByRole = cancelEx?.cancelledByRole,
                                    cancelledAtUtcMs = cancelEx?.cancelledAtUtcMs
                                )
                            )
                        }
                    }
                }
                "WEEKLY" -> {
                    val rangeStart = if (eventStart.isAfter(firstDay)) eventStart else firstDay
                    val effectiveEnd = if (eventUntil != null && eventUntil.isBefore(lastDay)) eventUntil else lastDay

                    if (!rangeStart.isAfter(effectiveEnd)) {
                        var curr = rangeStart
                        while (!curr.isAfter(effectiveEnd)) {
                            if (isDayIncluded(event.repeatDaysMask, curr.dayOfWeek)) {
                                val key = "${event.id}_${curr}"
                                if (!skippedSet.contains(key)) {
                                    val cancelEx = cancelledMap[key]
                                    results.add(
                                        CalendarOccurrence(
                                            eventId = event.id,
                                            planId = event.planId,
                                            title = event.title,
                                            date = curr,
                                            timeHour = event.timeHour,
                                            timeMinute = event.timeMinute,
                                            repeatType = "WEEKLY",
                                            isRecurring = true,
                                            note = event.note,
                                            isCancelled = cancelEx != null,
                                            cancelledReason = cancelEx?.cancelledReason,
                                            cancelledByName = cancelEx?.cancelledByName,
                                            cancelledByRole = cancelEx?.cancelledByRole,
                                            cancelledAtUtcMs = cancelEx?.cancelledAtUtcMs
                                        )
                                    )
                                }
                            }
                            curr = curr.plusDays(1)
                        }
                    }
                }
            }
        }

        return results
            .sortedWith(compareBy({ it.timeHour }, { it.timeMinute }))
            .groupBy { it.date }
    }

    /**
     * Computes the dot indicator state for a given calendar day.
     * - Completed workout on that day takes precedence -> COMPLETED (Filled Red Dot).
     * - Cancelled occurrences are excluded from active planned/missed counts.
     * - If active planned occurrences exist:
     *   - If date is in the past: only shows MISSED (Gray Dot) if there is an event with a non-null planId.
     *     Rest days and custom notes (planId == null) never show MISSED.
     *   - If date is today or future -> PLANNED (Hollow White Ring Dot).
     * - Otherwise -> NONE.
     */
    fun computeDotState(
        date: LocalDate,
        today: LocalDate,
        hasCompletedWorkout: Boolean,
        occurrencesOnDay: List<CalendarOccurrence>
    ): CalendarDayDot {
        if (hasCompletedWorkout) {
            return CalendarDayDot.COMPLETED
        }
        val activeOccurrences = occurrencesOnDay.filter { !it.isCancelled }
        if (activeOccurrences.isNotEmpty()) {
            return if (date.isBefore(today)) {
                if (activeOccurrences.any { it.planId != null }) {
                    CalendarDayDot.MISSED
                } else {
                    CalendarDayDot.NONE
                }
            } else {
                CalendarDayDot.PLANNED
            }
        }
        return CalendarDayDot.NONE
    }
}
