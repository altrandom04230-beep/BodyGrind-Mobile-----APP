package com.example

import android.app.Application
import com.example.data.coaching.AssignedPlanDoc
import com.example.data.scheduling.AvailabilityDateOverride
import com.example.data.scheduling.ScheduledSessionDoc
import com.example.data.scheduling.SchedulingValidator
import com.example.data.scheduling.SessionStatus
import com.example.data.scheduling.TrainerAvailabilityDoc
import com.example.data.scheduling.WeeklyAvailabilitySlot
import com.example.data.trainer.AppRole
import com.example.ui.trainer.SchedulingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SchedulingCancellationTest {

    @Test
    fun roleGates_blockMembersAndAllowTrainersAndAdmins() {
        assertFalse(SchedulingValidator.canCancelRole(AppRole.MEMBER))
        assertTrue(SchedulingValidator.canCancelRole(AppRole.TRAINER))
        assertTrue(SchedulingValidator.canCancelRole(AppRole.ADMIN))

        assertFalse(SchedulingValidator.canEditTrainerAvailability(AppRole.MEMBER, "m1", "t1"))
        assertFalse(SchedulingValidator.canEditTrainerAvailability(AppRole.TRAINER, "t1", "t2"))
        assertTrue(SchedulingValidator.canEditTrainerAvailability(AppRole.TRAINER, "t1", "t1"))
        assertTrue(SchedulingValidator.canEditTrainerAvailability(AppRole.ADMIN, "admin1", "t1"))
        assertTrue(SchedulingValidator.canEditTrainerAvailability(AppRole.ADMIN, "admin1", "t2"))
    }

    @Test
    fun cancellationReasonValidation_requiresNonEmptyReason() {
        assertNotNull(SchedulingValidator.validateCancellationReason(""))
        assertNotNull(SchedulingValidator.validateCancellationReason("  "))
        assertNotNull(SchedulingValidator.validateCancellationReason("no"))
        assertNull(SchedulingValidator.validateCancellationReason("Client requested reschedule due to illness"))
        assertNotNull(SchedulingValidator.validateCancellationReason("a".repeat(301)))
    }

    @Test
    fun availabilityValidation_andDateOverrideResolution() {
        val validWeekly = TrainerAvailabilityDoc.defaultWeeklySlots()
        val validOverrides = listOf(
            AvailabilityDateOverride(
                dateKey = "2026-03-05",
                isAvailable = false,
                note = "Gym holiday"
            ),
            AvailabilityDateOverride(
                dateKey = "2026-03-06",
                isAvailable = true,
                startTime = "10:00",
                endTime = "14:00",
                note = "Short shift"
            )
        )
        assertNull(SchedulingValidator.validateAvailability(validWeekly, validOverrides))

        val doc = TrainerAvailabilityDoc(
            trainerId = "t1",
            trainerName = "Coach Cobie",
            weeklySlots = validWeekly,
            dateOverrides = validOverrides
        )
        assertEquals("Unavailable (Gym holiday)", doc.availabilitySummaryFor(LocalDate.of(2026, 3, 5)))
        assertEquals("10:00 AM – 2:00 PM (Short shift)", doc.availabilitySummaryFor(LocalDate.of(2026, 3, 6)))
        // Sunday is off in defaults
        assertEquals("Off / Unavailable", doc.availabilitySummaryFor(LocalDate.of(2026, 3, 8)))

        // Invalid time range rejected
        val invalidWeekly = listOf(WeeklyAvailabilitySlot(dayOfWeek = 1, enabled = true, startTime = "18:00", endTime = "09:00"))
        assertNotNull(SchedulingValidator.validateAvailability(invalidWeekly, emptyList()))
    }

    @Test
    fun cancelledSessionsAndPlans_areNeverCompletedAndExcludedFromTotals() {
        val completed = ScheduledSessionDoc(
            id = "s1",
            status = SessionStatus.COMPLETED,
            excludeFromBilling = false
        )
        val cancelled = ScheduledSessionDoc(
            id = "s2",
            status = SessionStatus.CANCELLED,
            excludeFromBilling = true,
            cancelledReason = "Trainer out sick"
        )
        val scheduled = ScheduledSessionDoc(
            id = "s3",
            status = SessionStatus.SCHEDULED,
            excludeFromBilling = false
        )

        assertTrue(completed.isCompleted)
        assertFalse(cancelled.isCompleted)
        assertTrue(cancelled.isCancelled)
        assertEquals(1, SchedulingValidator.completedNonCancelledCount(listOf(completed, cancelled, scheduled)))

        val cancelledPlanWithStaleCompletedFlag = AssignedPlanDoc(
            id = "p1",
            status = "cancelled",
            completed = true,
            excludeFromBilling = true
        )
        assertTrue(cancelledPlanWithStaleCompletedFlag.isCancelled)
        assertFalse(cancelledPlanWithStaleCompletedFlag.isEffectivelyCompleted)

        val completedPlan = AssignedPlanDoc(
            id = "p2",
            status = "completed",
            completed = true,
            excludeFromBilling = false
        )
        assertFalse(completedPlan.isCancelled)
        assertTrue(completedPlan.isEffectivelyCompleted)
    }

    @Test
    fun schedulingViewModel_exposesApplicationOnlyConstructorForDefaultFactory() {
        val ctor = SchedulingViewModel::class.java.getDeclaredConstructor(Application::class.java)
        assertNotNull(ctor)
    }
}
