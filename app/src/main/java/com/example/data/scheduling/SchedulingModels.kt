package com.example.data.scheduling

import com.example.data.trainer.AppRole
import com.example.utils.ManilaDateTime
import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDate

data class WeeklyAvailabilitySlot(
    val dayOfWeek: Int, // 1=Monday .. 7=Sunday
    val enabled: Boolean = true,
    val startTime: String = "08:00",
    val endTime: String = "17:00"
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "dayOfWeek" to dayOfWeek,
        "enabled" to enabled,
        "startTime" to startTime,
        "endTime" to endTime
    )
}

data class AvailabilityDateOverride(
    val dateKey: String, // "YYYY-MM-DD" in Asia/Manila
    val isAvailable: Boolean = false,
    val startTime: String = "09:00",
    val endTime: String = "15:00",
    val note: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "dateKey" to dateKey,
        "isAvailable" to isAvailable,
        "startTime" to startTime,
        "endTime" to endTime,
        "note" to note.trim().take(200)
    )
}

data class TrainerAvailabilityDoc(
    val trainerId: String = "",
    val trainerName: String = "",
    val timezone: String = ManilaDateTime.TIMEZONE_ID,
    val weeklySlots: List<WeeklyAvailabilitySlot> = defaultWeeklySlots(),
    val dateOverrides: List<AvailabilityDateOverride> = emptyList(),
    val updatedAtMs: Long? = null,
    val updatedByUid: String? = null,
    val updatedByRole: String? = null
) {
    companion object {
        fun defaultWeeklySlots(): List<WeeklyAvailabilitySlot> = listOf(
            WeeklyAvailabilitySlot(dayOfWeek = 1, enabled = true, startTime = "08:00", endTime = "17:00"),
            WeeklyAvailabilitySlot(dayOfWeek = 2, enabled = true, startTime = "08:00", endTime = "17:00"),
            WeeklyAvailabilitySlot(dayOfWeek = 3, enabled = true, startTime = "08:00", endTime = "17:00"),
            WeeklyAvailabilitySlot(dayOfWeek = 4, enabled = true, startTime = "08:00", endTime = "17:00"),
            WeeklyAvailabilitySlot(dayOfWeek = 5, enabled = true, startTime = "08:00", endTime = "17:00"),
            WeeklyAvailabilitySlot(dayOfWeek = 6, enabled = true, startTime = "09:00", endTime = "14:00"),
            WeeklyAvailabilitySlot(dayOfWeek = 7, enabled = false, startTime = "09:00", endTime = "14:00")
        )
    }

    /**
     * Resolves a human-readable availability summary for a given [LocalDate] in Asia/Manila,
     * applying date overrides first and falling back to the weekly recurring schedule.
     */
    fun availabilitySummaryFor(date: LocalDate): String {
        val key = ManilaDateTime.toDateKey(date)
        val override = dateOverrides.firstOrNull { it.dateKey == key }
        if (override != null) {
            return if (!override.isAvailable) {
                if (override.note.isNotBlank()) "Unavailable (${override.note})" else "Unavailable"
            } else {
                val base = "${ManilaDateTime.formatSlotTime(override.startTime)} – ${ManilaDateTime.formatSlotTime(override.endTime)}"
                if (override.note.isNotBlank()) "$base (${override.note})" else base
            }
        }
        val dow = date.dayOfWeek.value // 1=Mon..7=Sun
        val slot = weeklySlots.firstOrNull { it.dayOfWeek == dow }
        return if (slot != null && slot.enabled) {
            "${ManilaDateTime.formatSlotTime(slot.startTime)} – ${ManilaDateTime.formatSlotTime(slot.endTime)}"
        } else {
            "Off / Unavailable"
        }
    }
}

object SessionStatus {
    const val SCHEDULED = "scheduled"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
}

data class ScheduledSessionDoc(
    val id: String = "",
    val trainerId: String = "",
    val trainerName: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val assignedPlanId: String? = null,
    val title: String = "",
    val notes: String = "",
    val scheduledDateUtcMs: Long = 0L,
    val scheduledDateKey: String = "", // "YYYY-MM-DD" in Asia/Manila
    val startTime: String = "09:00",
    val endTime: String = "10:00",
    val status: String = SessionStatus.SCHEDULED,
    val excludeFromBilling: Boolean = false,
    val cancelledReason: String? = null,
    val cancelledByUid: String? = null,
    val cancelledByName: String? = null,
    val cancelledByRole: String? = null,
    val cancelledAtMs: Long? = null,
    val createdAtMs: Long? = null,
    val updatedAtMs: Long? = null
) {
    val isCancelled: Boolean
        get() = status.equals(SessionStatus.CANCELLED, ignoreCase = true)

    val isCompleted: Boolean
        get() = !isCancelled && status.equals(SessionStatus.COMPLETED, ignoreCase = true)

    val isScheduled: Boolean
        get() = !isCancelled && !isCompleted

    /** Formatted date in canonical `"MMM dd, yyyy"` (`Asia/Manila`). */
    fun formattedScheduledDate(): String {
        return ManilaDateTime.formatDateKey(scheduledDateKey)
            .takeIf { it != "—" }
            ?: ManilaDateTime.formatDate(scheduledDateUtcMs)
    }

    /** Formatted cancellation timestamp in `Asia/Manila`. */
    fun formattedCancelledAt(): String = ManilaDateTime.formatDateTime(cancelledAtMs)
}

data class CancellationNotificationDoc(
    val id: String = "",
    val sessionId: String? = null,
    val assignedPlanId: String? = null,
    val sessionTitle: String = "",
    val trainerId: String = "",
    val trainerName: String = "",
    val memberId: String = "",
    val memberName: String = "",
    val scheduledDateKey: String = "",
    val reason: String = "",
    val cancelledByUid: String = "",
    val cancelledByName: String = "",
    val cancelledByRole: String = "",
    val cancelledAtMs: Long = 0L,
    val notifyAdmins: Boolean = true,
    val readByUids: List<String> = emptyList()
) {
    fun isReadBy(uid: String?): Boolean = !uid.isNullOrBlank() && readByUids.contains(uid)
}

object SchedulingValidator {
    private val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private val dateKeyRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")

    fun canCancelRole(role: AppRole): Boolean = role == AppRole.TRAINER || role == AppRole.ADMIN

    fun canEditTrainerAvailability(
        actorRole: AppRole,
        actorUid: String,
        targetTrainerId: String
    ): Boolean {
        if (actorUid.isBlank() || targetTrainerId.isBlank()) return false
        return when (actorRole) {
            AppRole.ADMIN -> true
            AppRole.TRAINER -> actorUid == targetTrainerId
            AppRole.MEMBER -> false
        }
    }

    fun validateCancellationReason(reason: String): String? {
        val trimmed = reason.trim()
        return when {
            trimmed.isEmpty() -> "A cancellation reason is required."
            trimmed.length < 3 -> "Please provide a brief cancellation reason (at least 3 characters)."
            trimmed.length > 300 -> "Cancellation reason must be 300 characters or fewer."
            else -> null
        }
    }

    fun isValidTimeSlot(startTime: String, endTime: String): Boolean {
        if (!timeRegex.matches(startTime.trim()) || !timeRegex.matches(endTime.trim())) return false
        return timeToMinutes(startTime.trim()) < timeToMinutes(endTime.trim())
    }

    fun isValidDateKey(dateKey: String): Boolean {
        if (!dateKeyRegex.matches(dateKey.trim())) return false
        return ManilaDateTime.parseDateKey(dateKey.trim()) != null
    }

    fun validateAvailability(
        weeklySlots: List<WeeklyAvailabilitySlot>,
        dateOverrides: List<AvailabilityDateOverride>
    ): String? {
        if (weeklySlots.isEmpty()) return "At least one weekly day slot is required."
        for (slot in weeklySlots) {
            if (slot.dayOfWeek !in 1..7) return "Invalid day of week: ${slot.dayOfWeek}."
            if (slot.enabled && !isValidTimeSlot(slot.startTime, slot.endTime)) {
                return "${ManilaDateTime.dayOfWeekLabel(slot.dayOfWeek)}: start time must be earlier than end time (HH:mm)."
            }
        }
        for (override in dateOverrides) {
            if (!isValidDateKey(override.dateKey)) {
                return "Invalid override date: ${override.dateKey}."
            }
            if (override.isAvailable && !isValidTimeSlot(override.startTime, override.endTime)) {
                return "Override on ${ManilaDateTime.formatDateKey(override.dateKey)}: start time must be earlier than end time."
            }
        }
        return null
    }

    /**
     * Counts only genuinely completed, non-cancelled sessions for totals / billing / payouts.
     */
    fun completedNonCancelledCount(sessions: List<ScheduledSessionDoc>): Int =
        sessions.count { it.isCompleted && !it.isCancelled && !it.excludeFromBilling }

    private fun timeToMinutes(hhMm: String): Int {
        val parts = hhMm.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}

object SchedulingMappers {

    @Suppress("UNCHECKED_CAST")
    fun parseAvailability(doc: DocumentSnapshot): TrainerAvailabilityDoc? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val rawWeekly = (data["weeklySlots"] as? List<*>) ?: emptyList<Any?>()
        val parsedWeekly = rawWeekly.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val dow = (m["dayOfWeek"] as? Number)?.toInt() ?: return@mapNotNull null
            WeeklyAvailabilitySlot(
                dayOfWeek = dow.coerceIn(1, 7),
                enabled = (m["enabled"] as? Boolean) ?: false,
                startTime = (m["startTime"] as? String)?.takeIf { it.isNotBlank() } ?: "08:00",
                endTime = (m["endTime"] as? String)?.takeIf { it.isNotBlank() } ?: "17:00"
            )
        }.sortedBy { it.dayOfWeek }

        val rawOverrides = (data["dateOverrides"] as? List<*>) ?: emptyList<Any?>()
        val parsedOverrides = rawOverrides.mapNotNull { item ->
            val m = item as? Map<String, Any?> ?: return@mapNotNull null
            val dateKey = (m["dateKey"] as? String)?.trim().orEmpty()
            if (dateKey.isBlank()) return@mapNotNull null
            AvailabilityDateOverride(
                dateKey = dateKey,
                isAvailable = (m["isAvailable"] as? Boolean) ?: false,
                startTime = (m["startTime"] as? String)?.takeIf { it.isNotBlank() } ?: "09:00",
                endTime = (m["endTime"] as? String)?.takeIf { it.isNotBlank() } ?: "15:00",
                note = (m["note"] as? String).orEmpty()
            )
        }.sortedBy { it.dateKey }

        return TrainerAvailabilityDoc(
            trainerId = (data["trainerId"] as? String) ?: doc.id,
            trainerName = (data["trainerName"] as? String).orEmpty(),
            timezone = (data["timezone"] as? String)?.takeIf { it.isNotBlank() } ?: ManilaDateTime.TIMEZONE_ID,
            weeklySlots = if (parsedWeekly.isEmpty()) TrainerAvailabilityDoc.defaultWeeklySlots() else parsedWeekly,
            dateOverrides = parsedOverrides,
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                ?: ManilaDateTime.parseFirestoreUtcMillis(data["updatedAt"]),
            updatedByUid = data["updatedByUid"] as? String,
            updatedByRole = data["updatedByRole"] as? String
        )
    }

    fun parseScheduledSession(doc: DocumentSnapshot): ScheduledSessionDoc? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val rawStatus = (data["status"] as? String)?.lowercase() ?: SessionStatus.SCHEDULED
        val isCancelled = rawStatus == SessionStatus.CANCELLED
        val scheduledUtcMs = ManilaDateTime.parseFirestoreUtcMillis(data["scheduledDateUtcMs"])
            ?: doc.getTimestamp("scheduledAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: 0L
        val dateKey = (data["scheduledDateKey"] as? String)?.takeIf { it.isNotBlank() }
            ?: if (scheduledUtcMs > 0L) ManilaDateTime.toDateKey(scheduledUtcMs) else ""
        return ScheduledSessionDoc(
            id = doc.id,
            trainerId = (data["trainerId"] as? String).orEmpty(),
            trainerName = (data["trainerName"] as? String).orEmpty(),
            memberId = (data["memberId"] as? String).orEmpty(),
            memberName = (data["memberName"] as? String).orEmpty(),
            assignedPlanId = data["assignedPlanId"] as? String,
            title = (data["title"] as? String).orEmpty(),
            notes = (data["notes"] as? String).orEmpty(),
            scheduledDateUtcMs = scheduledUtcMs,
            scheduledDateKey = dateKey,
            startTime = (data["startTime"] as? String) ?: "09:00",
            endTime = (data["endTime"] as? String) ?: "10:00",
            status = rawStatus,
            excludeFromBilling = (data["excludeFromBilling"] as? Boolean) ?: isCancelled,
            cancelledReason = data["cancelledReason"] as? String,
            cancelledByUid = data["cancelledByUid"] as? String,
            cancelledByName = data["cancelledByName"] as? String,
            cancelledByRole = data["cancelledByRole"] as? String,
            cancelledAtMs = doc.getTimestamp("cancelledAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                ?: ManilaDateTime.parseFirestoreUtcMillis(data["cancelledAt"]),
            createdAtMs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        )
    }

    fun parseCancellationNotification(doc: DocumentSnapshot): CancellationNotificationDoc? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val readBy = (data["readByUids"] as? List<*>)?.mapNotNull { it as? String }.orEmpty()
        return CancellationNotificationDoc(
            id = doc.id,
            sessionId = data["sessionId"] as? String,
            assignedPlanId = data["assignedPlanId"] as? String,
            sessionTitle = (data["sessionTitle"] as? String).orEmpty(),
            trainerId = (data["trainerId"] as? String).orEmpty(),
            trainerName = (data["trainerName"] as? String).orEmpty(),
            memberId = (data["memberId"] as? String).orEmpty(),
            memberName = (data["memberName"] as? String).orEmpty(),
            scheduledDateKey = (data["scheduledDateKey"] as? String).orEmpty(),
            reason = (data["reason"] as? String).orEmpty(),
            cancelledByUid = (data["cancelledByUid"] as? String).orEmpty(),
            cancelledByName = (data["cancelledByName"] as? String).orEmpty(),
            cancelledByRole = (data["cancelledByRole"] as? String).orEmpty(),
            cancelledAtMs = doc.getTimestamp("cancelledAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                ?: ManilaDateTime.parseFirestoreUtcMillis(data["cancelledAt"])
                ?: 0L,
            notifyAdmins = (data["notifyAdmins"] as? Boolean) ?: true,
            readByUids = readBy
        )
    }
}
