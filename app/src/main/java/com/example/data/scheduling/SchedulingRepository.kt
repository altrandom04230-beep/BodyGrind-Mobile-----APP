package com.example.data.scheduling

import android.content.Context
import android.util.Log
import com.example.data.coaching.AssignedPlanDoc
import com.example.data.coaching.CoachingMappers
import com.example.data.coaching.CoachingUiState
import com.example.data.coaching.FirestoreProvider
import com.example.data.coaching.OperationType
import com.example.data.coaching.handleFirestoreError
import com.example.data.trainer.AppRole
import com.example.utils.ManilaDateTime
import com.example.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID

/**
 * Firestore repository for:
 * 1. Trainer Availability (`/trainerAvailability/{trainerId}` — weekly recurring slots + date overrides in Asia/Manila)
 * 2. Scheduled Sessions (`/scheduledSessions/{sessionId}` — linked with assigned workout plans)
 * 3. Role-gated Cancellation (Trainers & Admins only; required reason; excluded from completed totals & billing)
 * 4. Multi-channel Cancellation Notifications:
 *    - In-app: `/cancellationNotifications/{id}` + `/adminNotifications/{id}`
 *    - Push: FCM token registration at `/users/{uid}/fcmTokens/{token}` + local `NotificationHelper` alert + Cloud Functions trigger
 *    - Email: Firestore `/mail/{id}` queue formatted for the Firebase Trigger Email extension
 */
class SchedulingRepository(
    context: Context
) {
    private val appContext = context.applicationContext
    private val db: FirebaseFirestore by lazy { FirestoreProvider.getDb(appContext) }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    fun currentUid(): String? = try {
        auth.currentUser?.uid
    } catch (_: Throwable) {
        null
    }

    // --- FCM DEVICE TOKEN REGISTRATION ---

    suspend fun registerFcmTokenForCurrentUser() {
        val uid = currentUid() ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            if (token.isNullOrBlank()) return
            db.collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "FCM token registration skipped or unavailable on this device", t)
        }
    }

    // --- TRAINER AVAILABILITY ---

    fun observeTrainerAvailability(trainerId: String): Flow<CoachingUiState<TrainerAvailabilityDoc>> = callbackFlow {
        if (trainerId.isBlank()) {
            trySend(CoachingUiState.Success(TrainerAvailabilityDoc()))
            close()
            return@callbackFlow
        }
        trySend(CoachingUiState.Loading)
        val reg = db.collection(COLLECTION_AVAILABILITY)
            .document(trainerId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load availability"))
                    return@addSnapshotListener
                }
                val parsed = snap?.let { SchedulingMappers.parseAvailability(it) }
                    ?: TrainerAvailabilityDoc(trainerId = trainerId)
                trySend(CoachingUiState.Success(parsed))
            }
        awaitClose { reg.remove() }
    }

    suspend fun saveTrainerAvailability(
        trainerId: String,
        trainerName: String,
        weeklySlots: List<WeeklyAvailabilitySlot>,
        dateOverrides: List<AvailabilityDateOverride>,
        actorRole: AppRole
    ): Result<Unit> {
        val actorUid = currentUid() ?: return Result.failure(IllegalStateException("Please sign in first."))
        if (!SchedulingValidator.canEditTrainerAvailability(actorRole, actorUid, trainerId)) {
            return Result.failure(SecurityException("Only the trainer or an admin can edit this availability."))
        }
        val validationError = SchedulingValidator.validateAvailability(weeklySlots, dateOverrides)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }

        return try {
            val payload = mapOf(
                "trainerId" to trainerId,
                "trainerName" to trainerName.trim().ifBlank { "Coach" }.take(100),
                "timezone" to ManilaDateTime.TIMEZONE_ID,
                "weeklySlots" to weeklySlots.sortedBy { it.dayOfWeek }.map { it.toFirestoreMap() },
                "dateOverrides" to dateOverrides.sortedBy { it.dateKey }.take(60).map { it.toFirestoreMap() },
                "updatedByUid" to actorUid,
                "updatedByRole" to actorRole.name.lowercase(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION_AVAILABILITY)
                .document(trainerId)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COLLECTION_AVAILABILITY/$trainerId")
            Result.failure(e)
        }
    }

    // --- SCHEDULED SESSIONS ---

    fun observeScheduledSessionsForTrainer(trainerId: String): Flow<CoachingUiState<List<ScheduledSessionDoc>>> =
        callbackFlow {
            if (trainerId.isBlank()) {
                trySend(CoachingUiState.Success(emptyList()))
                close()
                return@callbackFlow
            }
            trySend(CoachingUiState.Loading)
            val reg = db.collection(COLLECTION_SESSIONS)
                .whereEqualTo("trainerId", trainerId)
                .addSnapshotListener { snaps, err ->
                    if (err != null) {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load sessions"))
                        return@addSnapshotListener
                    }
                    val list = snaps?.documents
                        ?.mapNotNull { SchedulingMappers.parseScheduledSession(it) }
                        ?.sortedWith(compareByDescending<ScheduledSessionDoc> { it.scheduledDateKey }.thenBy { it.startTime })
                        .orEmpty()
                    trySend(CoachingUiState.Success(list))
                }
            awaitClose { reg.remove() }
        }

    fun observeScheduledSessionsForMember(memberId: String): Flow<CoachingUiState<List<ScheduledSessionDoc>>> =
        callbackFlow {
            if (memberId.isBlank()) {
                trySend(CoachingUiState.Success(emptyList()))
                close()
                return@callbackFlow
            }
            trySend(CoachingUiState.Loading)
            val reg = db.collection(COLLECTION_SESSIONS)
                .whereEqualTo("memberId", memberId)
                .addSnapshotListener { snaps, err ->
                    if (err != null) {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load scheduled sessions"))
                        return@addSnapshotListener
                    }
                    val list = snaps?.documents
                        ?.mapNotNull { SchedulingMappers.parseScheduledSession(it) }
                        ?.sortedWith(compareByDescending<ScheduledSessionDoc> { it.scheduledDateKey }.thenBy { it.startTime })
                        .orEmpty()
                    trySend(CoachingUiState.Success(list))
                }
            awaitClose { reg.remove() }
        }

    fun observeAllScheduledSessionsForAdmin(): Flow<CoachingUiState<List<ScheduledSessionDoc>>> =
        callbackFlow {
            trySend(CoachingUiState.Loading)
            val reg = db.collection(COLLECTION_SESSIONS)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener { snaps, err ->
                    if (err != null) {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load all sessions"))
                        return@addSnapshotListener
                    }
                    val list = snaps?.documents
                        ?.mapNotNull { SchedulingMappers.parseScheduledSession(it) }
                        ?.sortedWith(compareByDescending<ScheduledSessionDoc> { it.scheduledDateKey }.thenBy { it.startTime })
                        .orEmpty()
                    trySend(CoachingUiState.Success(list))
                }
            awaitClose { reg.remove() }
        }

    suspend fun fetchAssignedPlansForMembers(memberIds: List<String>, trainerId: String? = null): List<AssignedPlanDoc> {
        if (memberIds.isEmpty()) return emptyList()
        val result = mutableListOf<AssignedPlanDoc>()
        for (memberId in memberIds.distinct()) {
            try {
                val snaps = db.collection("users")
                    .document(memberId)
                    .collection("assignedPlans")
                    .get()
                    .await()
                val plans = snaps.documents
                    .mapNotNull { CoachingMappers.parseAssignedPlan(it) }
                    .filter { !it.isDeleted && (trainerId.isNullOrBlank() || it.trainerId == trainerId) }
                result.addAll(plans)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Unable to read assignedPlans for member $memberId: ${e.message}")
            }
        }
        return result.sortedByDescending { it.startDateEpochMs }
    }

    suspend fun createScheduledSession(
        trainerId: String,
        trainerName: String,
        memberId: String,
        memberName: String,
        title: String,
        notes: String,
        scheduledDate: LocalDate,
        startTime: String,
        endTime: String,
        actorRole: AppRole,
        assignedPlanId: String? = null
    ): Result<String> {
        val actorUid = currentUid() ?: return Result.failure(IllegalStateException("Please sign in first."))
        if (!SchedulingValidator.canEditTrainerAvailability(actorRole, actorUid, trainerId)) {
            return Result.failure(SecurityException("Only the trainer or an admin can schedule sessions."))
        }
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Session title is required."))
        }
        if (!SchedulingValidator.isValidTimeSlot(startTime, endTime)) {
            return Result.failure(IllegalArgumentException("Start time must be earlier than end time (HH:mm)."))
        }

        val sessionId = "ss_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val startParts = startTime.split(":")
        val startHour = startParts[0].toInt()
        val startMin = startParts[1].toInt()
        val scheduledUtcMs = ManilaDateTime.manilaDateTimeToUtcMs(scheduledDate, startHour, startMin)
        val dateKey = ManilaDateTime.toDateKey(scheduledDate)

        return try {
            val payload = mutableMapOf<String, Any?>(
                "trainerId" to trainerId,
                "trainerName" to trainerName.trim().ifBlank { "Coach" }.take(100),
                "memberId" to memberId,
                "memberName" to memberName.trim().ifBlank { "Member" }.take(100),
                "assignedPlanId" to assignedPlanId?.takeIf { it.isNotBlank() },
                "title" to cleanTitle.take(120),
                "notes" to notes.trim().take(500),
                "scheduledDateUtcMs" to scheduledUtcMs,
                "scheduledDateKey" to dateKey,
                "startTime" to startTime.trim(),
                "endTime" to endTime.trim(),
                "status" to SessionStatus.SCHEDULED,
                "excludeFromBilling" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection(COLLECTION_SESSIONS)
                .document(sessionId)
                .set(payload)
                .await()
            Result.success(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "$COLLECTION_SESSIONS/$sessionId")
            Result.failure(e)
        }
    }

    suspend fun deleteScheduledSession(sessionId: String): Result<Unit> {
        return try {
            db.collection(COLLECTION_SESSIONS).document(sessionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "$COLLECTION_SESSIONS/$sessionId")
            Result.failure(e)
        }
    }

    // --- ROLE-GATED CANCELLATION + 3-CHANNEL NOTIFICATIONS ---

    suspend fun cancelScheduledSession(
        session: ScheduledSessionDoc,
        reason: String,
        actorRole: AppRole,
        actorName: String
    ): Result<Unit> {
        val actorUid = currentUid() ?: return Result.failure(IllegalStateException("Please sign in first."))
        if (!SchedulingValidator.canCancelRole(actorRole)) {
            return Result.failure(SecurityException("Members cannot cancel scheduled sessions."))
        }
        if (actorRole == AppRole.TRAINER && session.trainerId != actorUid) {
            return Result.failure(SecurityException("Trainers can only cancel their own sessions."))
        }
        if (session.isCancelled) {
            return Result.failure(IllegalStateException("This session is already cancelled."))
        }
        val reasonError = SchedulingValidator.validateCancellationReason(reason)
        if (reasonError != null) {
            return Result.failure(IllegalArgumentException(reasonError))
        }
        val cleanReason = reason.trim()
        val safeActorName = actorName.trim().ifBlank { actorRole.name.lowercase().replaceFirstChar { it.uppercase() } }.take(100)
        val roleLower = actorRole.name.lowercase()
        val cancelledNowUtcMs = ManilaDateTime.nowUtcMillis()

        return try {
            val updates = mapOf(
                "status" to SessionStatus.CANCELLED,
                "excludeFromBilling" to true,
                "cancelledReason" to cleanReason,
                "cancelledByUid" to actorUid,
                "cancelledByName" to safeActorName,
                "cancelledByRole" to roleLower,
                "cancelledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION_SESSIONS)
                .document(session.id)
                .update(updates)
                .await()

            // Also soft-cancel linked assigned plan if present (exact 9 fields permitted by Firestore v11 rules)
            if (!session.assignedPlanId.isNullOrBlank() && session.memberId.isNotBlank()) {
                try {
                    val planCancelUpdates = mapOf<String, Any>(
                        "status" to SessionStatus.CANCELLED,
                        "completed" to false,
                        "excludeFromBilling" to true,
                        "cancelledReason" to cleanReason.take(500),
                        "cancelledByUid" to actorUid,
                        "cancelledByName" to safeActorName,
                        "cancelledByRole" to roleLower,
                        "cancelledAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    db.collection("users")
                        .document(session.memberId)
                        .collection("assignedPlans")
                        .document(session.assignedPlanId)
                        .update(planCancelUpdates)
                        .await()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Linked assigned plan cancel skipped: ${e.message}")
                }
            }

            dispatchCancellationAlerts(
                sessionId = session.id,
                assignedPlanId = session.assignedPlanId,
                sessionTitle = session.title.ifBlank { "Scheduled Session" },
                trainerId = session.trainerId,
                trainerName = session.trainerName,
                memberId = session.memberId,
                memberName = session.memberName,
                scheduledDateKey = session.scheduledDateKey.ifBlank { ManilaDateTime.toDateKey(session.scheduledDateUtcMs) },
                reason = cleanReason,
                actorUid = actorUid,
                actorName = safeActorName,
                actorRole = roleLower,
                cancelledNowUtcMs = cancelledNowUtcMs
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "$COLLECTION_SESSIONS/${session.id}")
            Result.failure(e)
        }
    }

    suspend fun cancelAssignedPlan(
        plan: AssignedPlanDoc,
        memberName: String,
        reason: String,
        actorRole: AppRole,
        actorName: String
    ): Result<Unit> {
        val actorUid = currentUid() ?: return Result.failure(IllegalStateException("Please sign in first."))
        if (!SchedulingValidator.canCancelRole(actorRole)) {
            return Result.failure(SecurityException("Members cannot cancel assigned workouts."))
        }
        if (actorRole == AppRole.TRAINER && plan.trainerId != actorUid) {
            return Result.failure(SecurityException("Trainers can only cancel their own assigned plans."))
        }
        if (plan.isCancelled) {
            return Result.failure(IllegalStateException("This assigned workout is already cancelled."))
        }
        if (plan.completed || plan.isEffectivelyCompleted) {
            return Result.failure(IllegalStateException("Completed workouts cannot be cancelled."))
        }
        val reasonError = SchedulingValidator.validateCancellationReason(reason)
        if (reasonError != null) {
            return Result.failure(IllegalArgumentException(reasonError))
        }
        val cleanReason = reason.trim().take(500)
        val safeActorName = actorName.trim().ifBlank { actorRole.name.lowercase().replaceFirstChar { it.uppercase() } }.take(100)
        val roleLower = actorRole.name.lowercase()
        val cancelledNowUtcMs = ManilaDateTime.nowUtcMillis()
        val dateKey = if (plan.startDateEpochMs > 0L) {
            ManilaDateTime.toDateKey(plan.startDateEpochMs)
        } else {
            ManilaDateTime.toDateKey(ManilaDateTime.todayManila())
        }

        return try {
            val updates = mapOf<String, Any>(
                "status" to SessionStatus.CANCELLED,
                "completed" to false,
                "excludeFromBilling" to true,
                "cancelledReason" to cleanReason,
                "cancelledByUid" to actorUid,
                "cancelledByName" to safeActorName,
                "cancelledByRole" to roleLower,
                "cancelledAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users")
                .document(plan.memberId)
                .collection("assignedPlans")
                .document(plan.id)
                .update(updates)
                .await()

            dispatchCancellationAlerts(
                sessionId = null,
                assignedPlanId = plan.id,
                sessionTitle = plan.planTitle.ifBlank { "Assigned Workout" },
                trainerId = plan.trainerId,
                trainerName = plan.trainerName,
                memberId = plan.memberId,
                memberName = memberName.ifBlank { "Member" },
                scheduledDateKey = dateKey,
                reason = cleanReason,
                actorUid = actorUid,
                actorName = safeActorName,
                actorRole = roleLower,
                cancelledNowUtcMs = cancelledNowUtcMs
            )

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/${plan.memberId}/assignedPlans/${plan.id}")
            Result.failure(e)
        }
    }

    /**
     * Dispatches all 3 notification channels for a session/workout cancellation:
     * 1. In-app feed (`/cancellationNotifications/{id}` + `/adminNotifications/{id}`)
     * 2. Push notification (local alert via `NotificationHelper` + Cloud Function FCM trigger on `/cancellationNotifications`)
     * 3. Email queue (`/mail/{id}` formatted for Firebase Trigger Email extension)
     */
    private suspend fun dispatchCancellationAlerts(
        sessionId: String?,
        assignedPlanId: String?,
        sessionTitle: String,
        trainerId: String,
        trainerName: String,
        memberId: String,
        memberName: String,
        scheduledDateKey: String,
        reason: String,
        actorUid: String,
        actorName: String,
        actorRole: String,
        cancelledNowUtcMs: Long
    ) {
        val notifId = "cn_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val formattedDate = ManilaDateTime.formatDateKey(scheduledDateKey)
        val formattedCancelledAt = ManilaDateTime.formatDateTime(cancelledNowUtcMs)

        // 1. In-app notification record (visible to affected trainer and admins)
        val notifPayload = mutableMapOf<String, Any?>(
            "sessionId" to sessionId,
            "assignedPlanId" to assignedPlanId,
            "sessionTitle" to sessionTitle.take(120),
            "trainerId" to trainerId,
            "trainerName" to trainerName.take(100),
            "memberId" to memberId,
            "memberName" to memberName.take(100),
            "scheduledDateKey" to scheduledDateKey,
            "reason" to reason.take(300),
            "cancelledByUid" to actorUid,
            "cancelledByName" to actorName.take(100),
            "cancelledByRole" to actorRole,
            "cancelledAt" to FieldValue.serverTimestamp(),
            "notifyAdmins" to true,
            "readByUids" to emptyList<String>()
        ).filterValues { it != null }

        try {
            db.collection(COLLECTION_CANCELLATION_NOTIFS).document(notifId).set(notifPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "In-app cancellation notification write failed: ${e.message}")
        }

        // Mirror into existing /adminNotifications collection for Admin dashboard visibility
        try {
            val adminNotifPayload = mapOf(
                "type" to "session_cancellation",
                "title" to "Session Cancelled: $sessionTitle",
                "message" to "$sessionTitle ($formattedDate) with $memberName was cancelled by $actorName ($actorRole). Reason: $reason",
                "trainerId" to trainerId,
                "memberId" to memberId,
                "sessionId" to (sessionId ?: assignedPlanId ?: ""),
                "reason" to reason.take(300),
                "cancelledByUid" to actorUid,
                "cancelledByRole" to actorRole,
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("adminNotifications").document(notifId).set(adminNotifPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Admin notification mirror write skipped: ${e.message}")
        }

        // 2. Local Android system notification immediately on this device (FCM Cloud Function handles other devices)
        NotificationHelper.showCancellationNotification(
            context = appContext,
            title = "Session Cancelled: $sessionTitle",
            body = "$formattedDate • Cancelled by $actorName ($actorRole). Reason: $reason"
        )

        // 3. Email queue document for Firebase Trigger Email from Firestore extension
        val recipientEmails = resolveCancellationEmailRecipients(trainerId)
        try {
            val emailSubject = "[BodyGrind] Session Cancelled: $sessionTitle ($formattedDate)"
            val emailText = buildString {
                appendLine("A scheduled session/workout in BodyGrind has been cancelled.")
                appendLine()
                appendLine("Session: $sessionTitle")
                appendLine("Scheduled Date (Asia/Manila): $formattedDate")
                appendLine("Trainer: ${trainerName.ifBlank { trainerId }}")
                appendLine("Member: ${memberName.ifBlank { memberId }}")
                appendLine("Cancelled By: $actorName ($actorRole)")
                appendLine("Cancelled At (Asia/Manila): $formattedCancelledAt")
                appendLine("Reason: $reason")
                appendLine()
                appendLine("Status: CANCELLED (excluded from completed totals, billing, trainer payouts, and client package balances).")
            }
            val emailHtml = """
                <h3>BodyGrind Session Cancellation Notice</h3>
                <p><strong>Session:</strong> ${escapeHtml(sessionTitle)}</p>
                <p><strong>Scheduled Date (Asia/Manila):</strong> ${escapeHtml(formattedDate)}</p>
                <p><strong>Trainer:</strong> ${escapeHtml(trainerName.ifBlank { trainerId })}</p>
                <p><strong>Member:</strong> ${escapeHtml(memberName.ifBlank { memberId })}</p>
                <p><strong>Cancelled By:</strong> ${escapeHtml(actorName)} (${escapeHtml(actorRole)})</p>
                <p><strong>Cancelled At (Asia/Manila):</strong> ${escapeHtml(formattedCancelledAt)}</p>
                <p><strong>Reason:</strong> ${escapeHtml(reason)}</p>
                <p><em>This cancelled session is automatically excluded from completed-session totals, billing, trainer payouts, and client package balances.</em></p>
            """.trimIndent()

            val mailPayload = mapOf(
                "to" to recipientEmails,
                "message" to mapOf(
                    "subject" to emailSubject,
                    "text" to emailText,
                    "html" to emailHtml
                ),
                "type" to "session_cancellation",
                "trainerId" to trainerId,
                "memberId" to memberId,
                "sessionId" to (sessionId ?: assignedPlanId ?: ""),
                "cancelledByUid" to actorUid,
                "cancelledAtManila" to formattedCancelledAt,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION_MAIL).document("mail_$notifId").set(mailPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Email queue write to /mail failed: ${e.message}")
        }
    }

    private suspend fun resolveCancellationEmailRecipients(trainerId: String): List<String> {
        val recipients = linkedSetOf<String>()
        // Try trainer profile email first
        try {
            val trainerDoc = db.collection("trainers").document(trainerId).get().await()
            val tEmail = trainerDoc.getString("email")?.trim().orEmpty()
            if (tEmail.contains("@")) recipients.add(tEmail)
        } catch (_: Exception) {
            // Fallback to users doc below
        }
        try {
            val userDoc = db.collection("users").document(trainerId).get().await()
            val uEmail = userDoc.getString("email")?.trim().orEmpty()
            if (uEmail.contains("@")) recipients.add(uEmail)
        } catch (_: Exception) {
            // Ignore if current user lacks permission to read another user doc
        }
        // Admin accounts (succeeds when actor is admin; Cloud Function /mail enrichment can also append admin emails)
        try {
            val adminSnaps = db.collection("users").whereEqualTo("role", "admin").get().await()
            adminSnaps.documents.forEach { d ->
                val e = d.getString("email")?.trim().orEmpty()
                if (e.contains("@")) recipients.add(e)
            }
        } catch (_: Exception) {
            // Trainer clients cannot list /users; handled safely
        }
        try {
            val adminSnapsUpper = db.collection("users").whereEqualTo("accountType", "ADMIN").get().await()
            adminSnapsUpper.documents.forEach { d ->
                val e = d.getString("email")?.trim().orEmpty()
                if (e.contains("@")) recipients.add(e)
            }
        } catch (_: Exception) {
            // Safe fallback
        }
        auth.currentUser?.email?.trim()?.takeIf { it.contains("@") }?.let { recipients.add(it) }
        return recipients.toList()
    }

    // --- IN-APP CANCELLATION NOTIFICATIONS ---

    fun observeCancellationNotifications(
        role: AppRole,
        uid: String
    ): Flow<List<CancellationNotificationDoc>> = callbackFlow {
        if (uid.isBlank() || role == AppRole.MEMBER) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val query = if (role == AppRole.ADMIN) {
            db.collection(COLLECTION_CANCELLATION_NOTIFS)
                .orderBy("cancelledAt", Query.Direction.DESCENDING)
                .limit(50)
        } else {
            db.collection(COLLECTION_CANCELLATION_NOTIFS)
                .whereEqualTo("trainerId", uid)
        }
        val reg = query.addSnapshotListener { snaps, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val list = snaps?.documents
                ?.mapNotNull { SchedulingMappers.parseCancellationNotification(it) }
                ?.sortedByDescending { it.cancelledAtMs }
                .orEmpty()
            trySend(list)
        }
        awaitClose { reg.remove() }
    }

    suspend fun markCancellationNotificationRead(notificationId: String) {
        val uid = currentUid() ?: return
        try {
            db.collection(COLLECTION_CANCELLATION_NOTIFS)
                .document(notificationId)
                .update("readByUids", FieldValue.arrayUnion(uid))
                .await()
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    private fun escapeHtml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    companion object {
        private const val TAG = "SchedulingRepository"
        const val COLLECTION_AVAILABILITY = "trainerAvailability"
        const val COLLECTION_SESSIONS = "scheduledSessions"
        const val COLLECTION_CANCELLATION_NOTIFS = "cancellationNotifications"
        const val COLLECTION_MAIL = "mail"
    }
}
