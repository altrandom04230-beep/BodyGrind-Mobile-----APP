package com.example.ui.calendar

import com.example.data.local.entity.CalendarEventEntity
import java.time.LocalTime

data class EventTitleAndNote(
    val title: String,
    val note: String?
)

object CalendarEventFormHelper {

    /**
     * Builds clean title and note strings based on the selected event type.
     * `title` holds only the routine plan name, "Rest day", or custom text.
     * `note` holds the user's optional note text or null if blank.
     */
    fun buildTitleAndNote(
        eventType: String,
        planName: String?,
        customTitle: String,
        noteText: String
    ): EventTitleAndNote {
        val cleanNote = noteText.trim().ifEmpty { null }
        return when (eventType) {
            "PLAN" -> {
                val cleanTitle = planName?.trim()?.ifEmpty { "Workout" } ?: "Workout"
                EventTitleAndNote(title = cleanTitle, note = cleanNote)
            }
            "REST" -> {
                EventTitleAndNote(title = "Rest day", note = cleanNote)
            }
            "CUSTOM" -> {
                val cleanTitle = customTitle.trim().ifEmpty { "Workout" }
                EventTitleAndNote(title = cleanTitle, note = cleanNote)
            }
            else -> EventTitleAndNote(title = "Workout", note = cleanNote)
        }
    }

    /**
     * Pre-fills the note input when editing an event.
     * 1. If the event has a non-null `note` column value, returns it directly.
     * 2. Otherwise applies legacy fallback: strips any repeated "<plan name> - " prefixes
     *    from legacy events that combined plan name and note into the title field.
     */
    fun prefillNote(event: CalendarEventEntity?, planName: String?): String {
        if (event == null) return ""
        if (event.note != null) {
            return event.note
        }
        val effectivePrefix = if (!planName.isNullOrBlank()) {
            "$planName - "
        } else if (event.title.startsWith("Rest day - ")) {
            "Rest day - "
        } else {
            null
        }
        if (effectivePrefix != null) {
            var current = event.title
            while (current.startsWith(effectivePrefix) && current.length > effectivePrefix.length) {
                current = current.substring(effectivePrefix.length)
            }
            if (current != event.title) {
                return current.trim()
            }
        }
        return ""
    }

    /**
     * Rounds a LocalTime up to the next 15-minute boundary.
     * Pure function with no side effects or system clock references.
     *
     * Exact boundaries (e.g. 10:00:00, 10:15:00) with 0 seconds and nanos are preserved.
     * If seconds or nanos > 0 at a 15-min mark, it rounds to the subsequent 15-minute mark.
     * At 23:55 (or anywhere in 23:46..23:59:59), the 15-minute round-up wraps to midnight (00:00:00).
     */
    fun roundToNext15Minutes(time: LocalTime): LocalTime {
        val minute = time.minute
        val remainder = minute % 15
        if (remainder == 0 && time.second == 0 && time.nano == 0) {
            return time
        }
        val minutesToAdd = (15 - remainder).toLong()
        return time.plusMinutes(minutesToAdd).withSecond(0).withNano(0)
    }

    /**
     * Validates activity name for Custom note events.
     */
    fun validateCustomTitle(eventType: String, customTitle: String): String? {
        return if (eventType == "CUSTOM" && customTitle.trim().isBlank()) {
            "Please enter an activity name"
        } else null
    }

    /**
     * Validates that at least one weekday is selected for weekly repeat.
     */
    fun validateRepeatDays(repeatType: String, repeatDaysMask: Int): String? {
        return if (repeatType == "WEEKLY" && repeatDaysMask == 0) {
            "Please select at least one day for weekly repetition"
        } else null
    }

    /**
     * Validates that the repeat-until date is not before the start date.
     */
    fun validateRepeatUntil(repeatUntilEpochDay: Long?, startEpochDay: Long): String? {
        return if (repeatUntilEpochDay != null && repeatUntilEpochDay < startEpochDay) {
            "Repeat-until date cannot be before the start date"
        } else null
    }
}
