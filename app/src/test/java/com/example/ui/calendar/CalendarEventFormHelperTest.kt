package com.example.ui.calendar

import com.example.data.local.entity.CalendarEventEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class CalendarEventFormHelperTest {

    // 1. Round trip: build -> prefill -> build gives identical title and note
    @Test
    fun `round trip build to prefill to build preserves title and note`() {
        val planName = "Full Body Blitz"
        val originalNote = "Focus on squats depth and breath control"

        // Step 1: Initial build
        val built = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "PLAN",
            planName = planName,
            customTitle = "",
            noteText = originalNote
        )
        assertEquals(planName, built.title)
        assertEquals(originalNote, built.note)

        // Step 2: Save to entity
        val entity = CalendarEventEntity(
            id = 42L,
            planId = "plan_123",
            title = built.title,
            startDate = "2026-11-15",
            timeHour = 10,
            timeMinute = 0,
            repeatType = "NONE",
            note = built.note
        )

        // Step 3: Open in edit mode (prefill)
        val prefilledNote = CalendarEventFormHelper.prefillNote(entity, planName)
        assertEquals(originalNote, prefilledNote)

        // Step 4: Save again without changing note
        val rebuilt = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "PLAN",
            planName = planName,
            customTitle = "",
            noteText = prefilledNote
        )
        assertEquals(built.title, rebuilt.title)
        assertEquals(built.note, rebuilt.note)
    }

    // 2. Legacy doubled title fallback: strips repeated "<plan name> - "
    @Test
    fun `legacy doubled title strips repeated plan prefixes down to note`() {
        val legacyTitle = "Abs Sculpt Beginner - Abs Sculpt Beginner - test"
        val legacyEntity = CalendarEventEntity(
            id = 10L,
            planId = "plan_abs",
            title = legacyTitle,
            startDate = "2026-11-01",
            timeHour = 8,
            timeMinute = 0,
            repeatType = "WEEKLY",
            note = null // Legacy row had no separate note column
        )

        val extractedNote = CalendarEventFormHelper.prefillNote(legacyEntity, "Abs Sculpt Beginner")
        assertEquals("test", extractedNote)

        // Re-saving produces clean title and note
        val saved = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "PLAN",
            planName = "Abs Sculpt Beginner",
            customTitle = "",
            noteText = extractedNote
        )
        assertEquals("Abs Sculpt Beginner", saved.title)
        assertEquals("test", saved.note)
    }

    // 3. Rest day: title is "Rest day" and note is preserved
    @Test
    fun `rest day sets title to Rest day and preserves note`() {
        val note = "Active recovery and foam rolling"
        val built = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "REST",
            planName = null,
            customTitle = "Anything else",
            noteText = note
        )
        assertEquals("Rest day", built.title)
        assertEquals(note, built.note)

        val entity = CalendarEventEntity(
            id = 5L,
            planId = null,
            title = built.title,
            startDate = "2026-11-12",
            timeHour = 9,
            timeMinute = 0,
            repeatType = "NONE",
            note = built.note
        )
        val prefilled = CalendarEventFormHelper.prefillNote(entity, null)
        assertEquals(note, prefilled)
    }

    // 4. Custom note: title comes from custom title and note is preserved
    @Test
    fun `custom note uses custom title and preserves note`() {
        val customTitle = "5km Trail Run"
        val note = "Heart rate zone 2, hydration pack"

        val built = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "CUSTOM",
            planName = null,
            customTitle = customTitle,
            noteText = note
        )
        assertEquals(customTitle, built.title)
        assertEquals(note, built.note)

        val entity = CalendarEventEntity(
            id = 7L,
            planId = null,
            title = built.title,
            startDate = "2026-11-14",
            timeHour = 7,
            timeMinute = 0,
            repeatType = "NONE",
            note = built.note
        )
        val prefilled = CalendarEventFormHelper.prefillNote(entity, null)
        assertEquals(note, prefilled)
    }

    // 5. Plan names containing " - " or regex characters
    @Test
    fun `plan names with dashes or regex characters are handled safely without errors`() {
        // Plan name containing dashes and regex characters: "[HIIT] - Pro (Advanced) + Bonus$"
        val complexPlanName = "[HIIT] - Pro (Advanced) + Bonus$"
        val note = "Push pace on sprint intervals"

        val built = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "PLAN",
            planName = complexPlanName,
            customTitle = "",
            noteText = note
        )
        assertEquals(complexPlanName, built.title)
        assertEquals(note, built.note)

        // Legacy format test with doubled complex plan name
        val doubledLegacyTitle = "$complexPlanName - $complexPlanName - $note"
        val legacyEntity = CalendarEventEntity(
            id = 20L,
            planId = "plan_complex",
            title = doubledLegacyTitle,
            startDate = "2026-11-01",
            timeHour = 18,
            timeMinute = 30,
            repeatType = "WEEKLY",
            note = null
        )

        val extracted = CalendarEventFormHelper.prefillNote(legacyEntity, complexPlanName)
        assertEquals(note, extracted)
    }

    // 6. "This day only" override keeping the note
    @Test
    fun `this day only override retains the note`() {
        val series = CalendarEventEntity(
            id = 100L,
            planId = "plan_push",
            title = "Push Day",
            startDate = "2026-11-01",
            timeHour = 10,
            timeMinute = 0,
            repeatType = "WEEKLY",
            repeatDaysMask = 64,
            note = "Warm up rotator cuff"
        )

        // User edits this single occurrence and adjusts note
        val editedTitleAndNote = CalendarEventFormHelper.buildTitleAndNote(
            eventType = "PLAN",
            planName = "Push Day",
            customTitle = "",
            noteText = "Rotator cuff warm up + heavier bench sets"
        )

        val overrideEvent = series.copy(
            id = 0L,
            startDate = "2026-11-08",
            repeatType = "NONE",
            repeatDaysMask = 0,
            repeatUntil = null,
            title = editedTitleAndNote.title,
            note = editedTitleAndNote.note
        )

        assertEquals(0L, overrideEvent.id)
        assertEquals("Push Day", overrideEvent.title)
        assertEquals("Rotator cuff warm up + heavier bench sets", overrideEvent.note)
        assertEquals("NONE", overrideEvent.repeatType)
        assertEquals("2026-11-08", overrideEvent.startDate)
    }

    // 7. Time rounding tests: exact boundary, mid-interval, 23:55 (midnight wrap), and seconds > 0
    @Test
    fun `roundToNext15Minutes exact boundary preserves time`() {
        val boundary00 = LocalTime.of(10, 0, 0, 0)
        assertEquals(LocalTime.of(10, 0, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(boundary00))

        val boundary15 = LocalTime.of(10, 15, 0, 0)
        assertEquals(LocalTime.of(10, 15, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(boundary15))

        val boundary45 = LocalTime.of(14, 45, 0, 0)
        assertEquals(LocalTime.of(14, 45, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(boundary45))
    }

    @Test
    fun `roundToNext15Minutes mid interval rounds up to next 15 minutes`() {
        val time02 = LocalTime.of(10, 2, 0, 0)
        assertEquals(LocalTime.of(10, 15, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(time02))

        val time16 = LocalTime.of(10, 16, 0, 0)
        assertEquals(LocalTime.of(10, 30, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(time16))

        val time59 = LocalTime.of(10, 59, 0, 0)
        assertEquals(LocalTime.of(11, 0, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(time59))
    }

    @Test
    fun `roundToNext15Minutes with seconds greater than zero rounds up to subsequent 15 minutes`() {
        // At 10:15:01, 15-minute mark has passed, rounds to 10:30:00
        val timeWithSeconds = LocalTime.of(10, 15, 1, 0)
        assertEquals(LocalTime.of(10, 30, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(timeWithSeconds))

        val time00WithNanos = LocalTime.of(10, 0, 0, 500)
        assertEquals(LocalTime.of(10, 15, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(time00WithNanos))
    }

    @Test
    fun `roundToNext15Minutes at 23-55 wraps cleanly to midnight 00-00`() {
        // 23:55 + 5 minutes wraps past 23:59:59 into 00:00:00
        val time2355 = LocalTime.of(23, 55, 0, 0)
        assertEquals(LocalTime.of(0, 0, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(time2355))

        val time2346 = LocalTime.of(23, 46, 0, 0)
        assertEquals(LocalTime.of(0, 0, 0, 0), CalendarEventFormHelper.roundToNext15Minutes(time2346))
    }

    // Item 13 Validation Tests:
    // 1. Custom Note with empty activity name shows visible error; non-empty is valid
    @Test
    fun `validation custom note with empty activity name shows error and passes when provided`() {
        val blankError = CalendarEventFormHelper.validateCustomTitle(eventType = "CUSTOM", customTitle = "   ")
        assertEquals("Please enter an activity name", blankError)

        val emptyError = CalendarEventFormHelper.validateCustomTitle(eventType = "CUSTOM", customTitle = "")
        assertEquals("Please enter an activity name", emptyError)

        val validResult = CalendarEventFormHelper.validateCustomTitle(eventType = "CUSTOM", customTitle = "5km Run")
        assertNull(validResult)

        // For PLAN or REST, customTitle validation returns null (not applicable)
        assertNull(CalendarEventFormHelper.validateCustomTitle(eventType = "PLAN", customTitle = ""))
        assertNull(CalendarEventFormHelper.validateCustomTitle(eventType = "REST", customTitle = ""))
    }

    // 2. Weekly repeat with no weekday selected shows visible error; at least one day selected is valid
    @Test
    fun `validation weekly repeat with no weekday selected shows error and passes when day selected`() {
        val noDayError = CalendarEventFormHelper.validateRepeatDays(repeatType = "WEEKLY", repeatDaysMask = 0)
        assertEquals("Please select at least one day for weekly repetition", noDayError)

        // Monday mask (bit 1 = 2)
        val withMonday = CalendarEventFormHelper.validateRepeatDays(repeatType = "WEEKLY", repeatDaysMask = 2)
        assertNull(withMonday)

        // Repeat NONE with mask 0 is valid (does not repeat)
        val noneRepeat = CalendarEventFormHelper.validateRepeatDays(repeatType = "NONE", repeatDaysMask = 0)
        assertNull(noneRepeat)
    }

    // 3. Repeat-until date before start date shows visible error; on or after start date is valid
    @Test
    fun `validation repeat-until date before start date shows error and passes when on or after start date`() {
        val startDateEpoch = 20000L // arbitrary epoch day
        val beforeDateEpoch = 19999L
        val sameDateEpoch = 20000L
        val afterDateEpoch = 20030L

        val beforeError = CalendarEventFormHelper.validateRepeatUntil(
            repeatUntilEpochDay = beforeDateEpoch,
            startEpochDay = startDateEpoch
        )
        assertEquals("Repeat-until date cannot be before the start date", beforeError)

        val sameDateResult = CalendarEventFormHelper.validateRepeatUntil(
            repeatUntilEpochDay = sameDateEpoch,
            startEpochDay = startDateEpoch
        )
        assertNull(sameDateResult)

        val afterDateResult = CalendarEventFormHelper.validateRepeatUntil(
            repeatUntilEpochDay = afterDateEpoch,
            startEpochDay = startDateEpoch
        )
        assertNull(afterDateResult)

        // Null repeatUntilEpochDay (forever) is valid
        val nullResult = CalendarEventFormHelper.validateRepeatUntil(
            repeatUntilEpochDay = null,
            startEpochDay = startDateEpoch
        )
        assertNull(nullResult)
    }
}
