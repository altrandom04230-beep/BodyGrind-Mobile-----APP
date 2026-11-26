package com.example.ui.calendar.reminder

import com.example.data.local.entity.CalendarEventEntity
import com.example.data.local.entity.CalendarEventExceptionEntity
import com.example.ui.calendar.CalendarOccurrenceEngine
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeParseException

data class CalendarAlarmSchedule(
    val occurrenceDate: LocalDate,
    val occurrenceStart: ZonedDateTime,
    val triggerAt: ZonedDateTime
)

object CalendarEventReminderCalculator {

    /**
     * Calculates the full alarm schedule (occurrence date, start time, and trigger time)
     * for the given calendar event.
     *
     * @param event The calendar event entity
     * @param exceptions The list of exceptions (SKIPPED occurrences)
     * @param now Current zoned date-time (determining target time zone and relative future)
     * @param lastNotifiedOccurrence The occurrence date that was already notified, if any
     * @return CalendarAlarmSchedule, or null if no upcoming alarm.
     */
    fun calculateNextAlarmSchedule(
        event: CalendarEventEntity,
        exceptions: List<CalendarEventExceptionEntity>,
        now: ZonedDateTime,
        lastNotifiedOccurrence: LocalDate? = null
    ): CalendarAlarmSchedule? {
        if (!event.isEnabled || event.reminderMinutesBefore < 0) {
            return null
        }

        val eventStartDate = try {
            LocalDate.parse(event.startDate)
        } catch (e: DateTimeParseException) {
            return null
        }

        val eventUntilDate = event.repeatUntil?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }

        val zone = now.zone
        val skippedDateStrings = exceptions
            .filter { it.eventId == event.id && it.type == "SKIPPED" }
            .map { it.occurrenceDate }
            .toSet()

        val eventTime = LocalTime.of(event.timeHour, event.timeMinute)
        val reminderMinutes = event.reminderMinutesBefore.toLong()

        when (event.repeatType) {
            "NONE" -> {
                if (eventUntilDate != null && eventStartDate.isAfter(eventUntilDate)) {
                    return null
                }
                if (lastNotifiedOccurrence != null && eventStartDate == lastNotifiedOccurrence) {
                    return null
                }
                if (skippedDateStrings.contains(eventStartDate.toString())) {
                    return null
                }

                val occurrenceZdt = ZonedDateTime.of(eventStartDate, eventTime, zone)
                val nominalAlarmZdt = occurrenceZdt.minusMinutes(reminderMinutes)

                // Event hasn't started yet
                if (occurrenceZdt.isAfter(now)) {
                    val trigger = if (nominalAlarmZdt.isAfter(now)) {
                        nominalAlarmZdt
                    } else {
                        // Inside reminder window (imminent) and not yet notified: fire immediately
                        now
                    }
                    return CalendarAlarmSchedule(
                        occurrenceDate = eventStartDate,
                        occurrenceStart = occurrenceZdt,
                        triggerAt = trigger
                    )
                }
                return null
            }
            "WEEKLY" -> {
                if (event.repeatDaysMask == 0) {
                    return null
                }

                val todayDate = now.toLocalDate()
                val searchStartDate = if (eventStartDate.isAfter(todayDate)) eventStartDate else todayDate

                var candidateDate = searchStartDate
                val maxSearchDate = eventUntilDate ?: candidateDate.plusYears(1)

                while (!candidateDate.isAfter(maxSearchDate)) {
                    if (eventUntilDate != null && candidateDate.isAfter(eventUntilDate)) {
                        break
                    }

                    if (CalendarOccurrenceEngine.isDayIncluded(event.repeatDaysMask, candidateDate.dayOfWeek)) {
                        val isAlreadyNotified = lastNotifiedOccurrence != null && candidateDate == lastNotifiedOccurrence
                        val isSkipped = skippedDateStrings.contains(candidateDate.toString())

                        if (!isAlreadyNotified && !isSkipped) {
                            val occurrenceZdt = ZonedDateTime.of(candidateDate, eventTime, zone)
                            val nominalAlarmZdt = occurrenceZdt.minusMinutes(reminderMinutes)

                            if (occurrenceZdt.isAfter(now)) {
                                val trigger = if (nominalAlarmZdt.isAfter(now)) {
                                    nominalAlarmZdt
                                } else {
                                    // Inside reminder window (imminent) and not yet notified
                                    now
                                }
                                return CalendarAlarmSchedule(
                                    occurrenceDate = candidateDate,
                                    occurrenceStart = occurrenceZdt,
                                    triggerAt = trigger
                                )
                            }
                        }
                    }

                    candidateDate = candidateDate.plusDays(1)
                }

                return null
            }
            else -> return null
        }
    }

    /**
     * Calculates the next alarm ZonedDateTime for the given calendar event.
     * Preserved unchanged for backwards compatibility.
     *
     * @param event The calendar event entity
     * @param exceptions The list of exceptions (SKIPPED occurrences)
     * @param now Current zoned date-time (determining target time zone and relative future)
     * @param lastNotifiedOccurrence The occurrence date that was already notified, if any
     * @return Next ZonedDateTime when the alarm should trigger, or null if no upcoming alarm.
     */
    fun calculateNextAlarm(
        event: CalendarEventEntity,
        exceptions: List<CalendarEventExceptionEntity>,
        now: ZonedDateTime,
        lastNotifiedOccurrence: LocalDate? = null
    ): ZonedDateTime? {
        return calculateNextAlarmSchedule(event, exceptions, now, lastNotifiedOccurrence)?.triggerAt
    }
}
