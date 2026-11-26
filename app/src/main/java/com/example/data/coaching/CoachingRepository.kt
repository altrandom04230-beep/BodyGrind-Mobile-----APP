package com.example.data.coaching

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.PendingSyncEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.data.trainer.AppRole
import com.example.data.trainer.MemberStatus
import com.example.utils.ManilaDateTime
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

class CoachingRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val appContext = context.applicationContext
    private val db: FirebaseFirestore by lazy { FirestoreProvider.getDb(appContext) }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val pendingSyncDao = database.pendingSyncDao()
    private val workoutPlanDao = database.workoutPlanDao()

    fun currentUid(): String? = try {
        auth.currentUser?.uid
    } catch (_: Throwable) {
        null
    }

    fun observePendingSyncCount(): Flow<Int> = pendingSyncDao.observePendingCount()

    // --- ACCOUNT & ROLE ---

    suspend fun fetchUserAccount(uid: String): Result<UserAccountDoc?> {
        return try {
            val snap = db.collection("users").document(uid).get().await()
            val parsed = CoachingMappers.parseUserAccount(snap)
            if (parsed != null) {
                CoachingSessionPrefs.saveUserMeta(appContext, uid, parsed.accountType, parsed.name)
                if (!parsed.trainerInviteCode.isNullOrBlank()) {
                    CoachingSessionPrefs.saveInviteCode(appContext, uid, parsed.trainerInviteCode)
                }
            }
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun ensureUserAccountDoc(
        uid: String,
        role: AppRole,
        name: String,
        email: String,
        trainerTitle: String? = null,
        specialization: String? = null,
        fitnessGoal: String? = null,
        fitnessLevel: String? = null,
        birthdate: String? = null
    ): Result<UserAccountDoc> {
        return try {
            val userRef = db.collection("users").document(uid)
            val existing = try {
                userRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, "users/$uid")
                return Result.failure(e)
            }
            val safeName = name.trim().ifBlank { email.substringBefore("@").ifBlank { "Athlete" } }.take(80)

            if (existing.exists()) {
                val parsed = CoachingMappers.parseUserAccount(existing)!!
                val rawData = existing.data.orEmpty()
                val updates = mutableMapOf<String, Any>(
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if ((rawData["userId"] as? String).isNullOrBlank()) updates["userId"] = uid
                if ((rawData["accountType"] as? String).isNullOrBlank()) updates["accountType"] = parsed.accountType.name
                if ((rawData["status"] as? String).isNullOrBlank()) updates["status"] = "active"
                if (rawData["createdAt"] == null) updates["createdAt"] = FieldValue.serverTimestamp()
                if (parsed.name.isBlank() && safeName.isNotBlank()) updates["name"] = safeName
                if (email.isNotBlank() && parsed.email.isBlank()) updates["email"] = email.take(160)
                if (!fitnessGoal.isNullOrBlank()) updates["fitnessGoal"] = fitnessGoal.take(100)
                if (!fitnessLevel.isNullOrBlank()) updates["fitnessLevel"] = fitnessLevel.take(40)
                if (!birthdate.isNullOrBlank() && (rawData["birthdate"] as? String).isNullOrBlank()) {
                    updates["birthdate"] = birthdate
                    updates["calculatedAge"] = ManilaDateTime.calculateAge(birthdate) ?: 0
                }

                try {
                    userRef.update(updates).await()
                    CoachingSyncHealth.clearUserWritePermissionLimited(uid)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        try {
                            userRef.update("lastActiveAt", FieldValue.serverTimestamp()).await()
                        } catch (_: Exception) {
                            CoachingSyncHealth.markUserWritePermissionLimited(uid)
                        }
                        android.util.Log.w(
                            "CoachingRepository",
                            "Existing users/$uid metadata update limited by Firestore rules: ${e.message}"
                        )
                    } else {
                        throw e
                    }
                }
                CoachingSessionPrefs.saveUserMeta(appContext, uid, parsed.accountType, parsed.name.ifBlank { safeName })
                return Result.success(parsed)
            }

            val inviteCode = if (role == AppRole.TRAINER) generateCodeForUid(uid) else null
            val calculatedAge = birthdate?.let { ManilaDateTime.calculateAge(it) }
            val payload = mutableMapOf<String, Any?>(
                "userId" to uid,
                "name" to safeName,
                "email" to email.take(160),
                "accountType" to role.name,
                "status" to "active",
                "trainerInviteCode" to inviteCode,
                "trainerTitle" to (trainerTitle ?: if (role == AppRole.TRAINER) "Certified Personal Trainer" else null),
                "specialization" to (specialization ?: if (role == AppRole.TRAINER) "Strength, Conditioning & Form" else null),
                "fitnessGoal" to fitnessGoal?.take(100),
                "fitnessLevel" to fitnessLevel?.take(40),
                "birthdate" to birthdate,
                "calculatedAge" to calculatedAge,
                "memberType" to "Regular",
                "isVerified" to false,
                "discountPercent" to 0.0,
                "discountAppliesToBilling" to false,
                "discountAppliesToPackages" to false,
                "discountAppliesToSessionFees" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "lastActiveAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            userRef.set(payload).await()
            CoachingSyncHealth.clearUserWritePermissionLimited(uid)
            CoachingSessionPrefs.saveUserMeta(appContext, uid, role, safeName)

            if (role == AppRole.TRAINER && inviteCode != null) {
                ensureTrainerInviteCode(
                    trainerId = uid,
                    trainerName = safeName,
                    trainerTitle = trainerTitle ?: "Certified Personal Trainer",
                    specialization = specialization ?: "Strength, Conditioning & Form",
                    preferredCode = inviteCode
                )
            }

            val created = UserAccountDoc(
                userId = uid,
                name = safeName,
                email = email,
                accountType = role,
                status = "active",
                trainerInviteCode = inviteCode,
                trainerTitle = if (role == AppRole.TRAINER) (trainerTitle ?: "Certified Personal Trainer") else null,
                specialization = if (role == AppRole.TRAINER) (specialization ?: "Strength, Conditioning & Form") else null,
                fitnessGoal = fitnessGoal,
                fitnessLevel = fitnessLevel,
                birthdate = birthdate,
                calculatedAge = calculatedAge,
                memberType = "Regular",
                isVerified = false,
                discountPercent = 0.0,
                discountAppliesToBilling = false,
                discountAppliesToPackages = false,
                discountAppliesToSessionFees = false
            )
            Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val fsEx = generateSequence(e as Throwable) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                CoachingSyncHealth.markUserWritePermissionLimited(uid)
            }
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun ensureTrainerInviteCode(
        trainerId: String,
        trainerName: String,
        trainerTitle: String? = null,
        specialization: String? = null,
        preferredCode: String? = null
    ): Result<String> {
        return try {
            val cached = preferredCode ?: CoachingSessionPrefs.getSavedInviteCode(appContext, trainerId)
            val userDoc = try { db.collection("users").document(trainerId).get().await() } catch (_: Exception) { null }
            val existingCode = userDoc?.getString("trainerInviteCode") ?: cached
            val code = (existingCode ?: generateCodeForUid(trainerId)).uppercase()

            val inviteRef = db.collection("trainerInvites").document(code)
            val inviteSnap = try { inviteRef.get().await() } catch (_: Exception) { null }
            val safeName = trainerName.trim().ifBlank { "Coach" }.take(80)
            if (inviteSnap == null || !inviteSnap.exists()) {
                val inviteMap = mutableMapOf<String, Any?>(
                    "trainerId" to trainerId,
                    "trainerName" to safeName,
                    "trainerTitle" to trainerTitle?.take(120),
                    "specialization" to specialization?.take(200),
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                inviteRef.set(inviteMap).await()
            } else {
                val updates = mutableMapOf<String, Any?>(
                    "trainerName" to safeName,
                    "trainerTitle" to trainerTitle?.take(120),
                    "specialization" to specialization?.take(200),
                    "active" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                inviteRef.update(updates).await()
            }

            if (userDoc != null && userDoc.exists() && userDoc.getString("trainerInviteCode") != code) {
                try {
                    db.collection("users").document(trainerId).update(
                        mapOf(
                            "trainerInviteCode" to code,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Non-fatal; trainerInvites/{code} is authoritative
                }
            }
            CoachingSessionPrefs.saveInviteCode(appContext, trainerId, code)
            Result.success(code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "trainerInvites")
            Result.failure(e)
        }
    }

    private fun generateCodeForUid(uid: String): String {
        val cleaned = uid.filter { it.isLetterOrDigit() }.uppercase()
        val suffix = if (cleaned.length >= 6) cleaned.take(6) else cleaned.padEnd(6, 'X')
        return "BG-$suffix"
    }

    // --- LINKING ---

    suspend fun lookupTrainerInvite(rawInput: String): Result<TrainerInviteDoc> {
        val code = CoachingMappers.parseInviteCodeFromInput(rawInput)
        if (code.length < 4 || !code.matches(Regex("^[A-Za-z0-9_\\-]+$"))) {
            return Result.failure(IllegalArgumentException("Enter a valid trainer invite code (e.g. BG-XXXXXX)."))
        }
        return try {
            val snap = db.collection("trainerInvites").document(code).get().await()
            val invite = CoachingMappers.parseTrainerInvite(snap)
            if (invite == null || !invite.active) {
                Result.failure(IllegalStateException("Invite code '$code' was not found or is inactive."))
            } else if (invite.trainerId == currentUid()) {
                Result.failure(IllegalStateException("You cannot link to your own trainer invite code."))
            } else {
                Result.success(invite)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "trainerInvites/$code")
            Result.failure(e)
        }
    }

    suspend fun linkMemberToTrainerWithInvite(
        invite: TrainerInviteDoc,
        memberProfile: UserProfileEntity,
        consent: LinkConsentOptions
    ): Result<TrainerMemberLink> {
        val memberId = currentUid() ?: return Result.failure(IllegalStateException("Please sign in first."))
        if (invite.trainerId == memberId) {
            return Result.failure(IllegalStateException("Cannot link to your own account."))
        }
        return try {
            // Check existing link (1 active trainer per member)
            val linkRef = db.collection("links").document(memberId)
            val existing = try { linkRef.get().await() } catch (_: Exception) { null }
            if (existing != null && existing.exists()) {
                val existingLink = CoachingMappers.parseLink(existing)
                if (existingLink != null && existingLink.status == LinkStatus.ACTIVE && existingLink.trainerId != invite.trainerId) {
                    return Result.failure(IllegalStateException("You already have an active coach (${existingLink.trainerName}). Unlink first before switching."))
                }
            }

            val resolvedAccountName = CoachingSessionPrefs.getSavedName(appContext, memberId)
                ?: auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            val profileName = memberProfile.name.trim()
            val safeMemberName = (if (profileName.isNotBlank() && profileName != "Fitness Athlete") {
                profileName
            } else {
                resolvedAccountName ?: profileName.ifBlank { "Member" }
            }).take(80)
            val linkMap = mutableMapOf<String, Any?>(
                "memberId" to memberId,
                "trainerId" to invite.trainerId,
                "memberName" to safeMemberName,
                "trainerName" to invite.trainerName.take(80),
                "trainerTitle" to invite.trainerTitle?.take(120),
                "inviteCode" to invite.inviteCode,
                "status" to LinkStatus.ACTIVE.name,
                "memberStatus" to MemberStatus.ACTIVE.name,
                "requestedBy" to AppRole.MEMBER.name,
                "shareWorkouts" to consent.shareWorkouts,
                "shareStreakXp" to consent.shareStreakXp,
                "shareBodyStats" to consent.shareBodyStats,
                "agreedShareWorkouts" to consent.shareWorkouts,
                "agreedShareStreakXp" to consent.shareStreakXp,
                "agreedShareBodyStats" to consent.shareBodyStats,
                "fitnessGoal" to memberProfile.fitnessGoal.take(100),
                "fitnessLevel" to memberProfile.fitnessLevel.take(40),
                "acceptedAt" to FieldValue.serverTimestamp(),
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            linkRef.set(linkMap).await()
            val resultLink = TrainerMemberLink(
                memberId = memberId,
                trainerId = invite.trainerId,
                memberName = safeMemberName,
                trainerName = invite.trainerName,
                trainerTitle = invite.trainerTitle,
                inviteCode = invite.inviteCode,
                status = LinkStatus.ACTIVE,
                memberStatus = MemberStatus.ACTIVE,
                requestedBy = AppRole.MEMBER,
                shareWorkouts = consent.shareWorkouts,
                shareStreakXp = consent.shareStreakXp,
                shareBodyStats = consent.shareBodyStats,
                agreedShareWorkouts = consent.shareWorkouts,
                agreedShareStreakXp = consent.shareStreakXp,
                agreedShareBodyStats = consent.shareBodyStats,
                fitnessGoal = memberProfile.fitnessGoal,
                fitnessLevel = memberProfile.fitnessLevel
            )
            Result.success(resultLink)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "links/$memberId")
            Result.failure(e)
        }
    }

    suspend fun acceptPendingLinkRequest(
        memberProfile: UserProfileEntity,
        consent: LinkConsentOptions
    ): Result<Unit> {
        val memberId = currentUid() ?: return Result.failure(IllegalStateException("Not signed in."))
        return try {
            val updates = mapOf(
                "status" to LinkStatus.ACTIVE.name,
                "memberName" to memberProfile.name.trim().ifBlank { "Member" }.take(80),
                "shareWorkouts" to consent.shareWorkouts,
                "shareStreakXp" to consent.shareStreakXp,
                "shareBodyStats" to consent.shareBodyStats,
                "agreedShareWorkouts" to consent.shareWorkouts,
                "agreedShareStreakXp" to consent.shareStreakXp,
                "agreedShareBodyStats" to consent.shareBodyStats,
                "fitnessGoal" to memberProfile.fitnessGoal.take(100),
                "fitnessLevel" to memberProfile.fitnessLevel.take(40),
                "acceptedAt" to FieldValue.serverTimestamp(),
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("links").document(memberId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "links/$memberId")
            Result.failure(e)
        }
    }

    suspend fun unlinkTrainerMember(memberId: String): Result<Unit> {
        return try {
            db.collection("links").document(memberId).delete().await()
            // Also clean up locally queued progress sync ops if the unlinked member is the current user
            if (memberId == currentUid()) {
                pendingSyncDao.deleteByOpKey("SYNC_WORKOUTS")
                pendingSyncDao.deleteByOpKey("SYNC_STREAK_XP")
                pendingSyncDao.deleteByOpKey("SYNC_BODY_STATS")
                pendingSyncDao.deleteByOpKey("SYNC_LINK_PRIVACY")
                // Remove uncompleted assigned plans from that coach? Keep completed history or remove uncompleted
                val assigned = workoutPlanDao.getAssignedPlansSync()
                assigned.filter { !it.isCompletedByMember }.forEach { plan ->
                    workoutPlanDao.deletePlanExercises(plan.id)
                    workoutPlanDao.deletePlanById(plan.id)
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "links/$memberId")
            Result.failure(e)
        }
    }

    fun observeMemberLink(memberId: String): Flow<CoachingUiState<TrainerMemberLink?>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("links").document(memberId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(CoachingUiState.Success(null))
                    } else {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load coach link"))
                    }
                    return@addSnapshotListener
                }
                val link = if (snap != null && snap.exists()) CoachingMappers.parseLink(snap) else null
                trySend(CoachingUiState.Success(link))
            }
        awaitClose { reg.remove() }
    }.catch { error ->
        if (error is CancellationException) throw error
        val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
        emit(CoachingUiState.Error(fsErr?.localizedMessage ?: error.localizedMessage ?: "Error loading link"))
    }

    fun observeTrainerLinks(trainerId: String): Flow<CoachingUiState<List<TrainerMemberLink>>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("links")
            .whereEqualTo("trainerId", trainerId)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load members"))
                    return@addSnapshotListener
                }
                val list = snaps?.documents
                    ?.mapNotNull { CoachingMappers.parseLink(it) }
                    ?.sortedByDescending { it.lastActiveAtMs ?: it.updatedAtMs ?: 0L }
                    .orEmpty()
                trySend(CoachingUiState.Success(list))
            }
        awaitClose { reg.remove() }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(CoachingUiState.Error(error.localizedMessage ?: "Failed to observe members"))
    }

    suspend fun updateLinkMemberStatus(memberId: String, status: MemberStatus): Result<Unit> {
        return try {
            db.collection("links").document(memberId).update(
                mapOf(
                    "memberStatus" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "links/$memberId")
            Result.failure(e)
        }
    }

    // --- PRIVACY TOGGLES & PROGRESS SYNC (WITH OFFLINE QUEUE) ---

    suspend fun updatePrivacySettings(
        shareWorkouts: Boolean,
        shareStreakXp: Boolean,
        shareBodyStats: Boolean
    ): Result<Unit> {
        val memberId = currentUid() ?: return Result.failure(IllegalStateException("Not signed in."))
        val payload = JSONObject().apply {
            put("memberId", memberId)
            put("shareWorkouts", shareWorkouts)
            put("shareStreakXp", shareStreakXp)
            put("shareBodyStats", shareBodyStats)
        }.toString()

        if (!FirestoreProvider.isNetworkAvailable(appContext)) {
            pendingSyncDao.upsert(
                PendingSyncEntity(
                    opKey = "SYNC_LINK_PRIVACY",
                    opType = "SYNC_LINK_PRIVACY",
                    payloadJson = payload
                )
            )
            return Result.success(Unit)
        }

        return try {
            executePrivacyUpdate(memberId, shareWorkouts, shareStreakXp, shareBodyStats)
            pendingSyncDao.deleteByOpKey("SYNC_LINK_PRIVACY")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "links/$memberId")
            pendingSyncDao.upsert(
                PendingSyncEntity(
                    opKey = "SYNC_LINK_PRIVACY",
                    opType = "SYNC_LINK_PRIVACY",
                    payloadJson = payload,
                    lastError = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    private suspend fun executePrivacyUpdate(
        memberId: String,
        shareWorkouts: Boolean,
        shareStreakXp: Boolean,
        shareBodyStats: Boolean
    ) {
        db.collection("links").document(memberId).update(
            mapOf(
                "shareWorkouts" to shareWorkouts,
                "shareStreakXp" to shareStreakXp,
                "shareBodyStats" to shareBodyStats,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun queueOrSyncMemberProgress(
        link: TrainerMemberLink?,
        profile: UserProfileEntity,
        sessions: List<WorkoutSessionEntity>,
        measurements: List<BodyMeasurementEntity>,
        streakDays: Int
    ) {
        if (link == null || link.status != LinkStatus.ACTIVE) return
        val memberId = currentUid() ?: return
        if (link.memberId != memberId) return

        if (link.shareWorkouts) {
            val payload = buildWorkoutsPayloadJson(memberId, sessions)
            if (FirestoreProvider.isNetworkAvailable(appContext)) {
                try {
                    executeWorkoutsSync(JSONObject(payload))
                    pendingSyncDao.deleteByOpKey("SYNC_WORKOUTS")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pendingSyncDao.upsert(
                        PendingSyncEntity(opKey = "SYNC_WORKOUTS", opType = "SYNC_WORKOUTS", payloadJson = payload, lastError = e.localizedMessage)
                    )
                }
            } else {
                pendingSyncDao.upsert(
                    PendingSyncEntity(opKey = "SYNC_WORKOUTS", opType = "SYNC_WORKOUTS", payloadJson = payload)
                )
            }
        }

        if (link.shareStreakXp) {
            val payload = JSONObject().apply {
                put("memberId", memberId)
                put("streakDays", streakDays.coerceAtLeast(0))
                put("level", profile.level.coerceAtLeast(1))
                put("xpPoints", profile.xpPoints.coerceAtLeast(0))
                put("fitnessGoal", profile.fitnessGoal.take(100))
                put("fitnessLevel", profile.fitnessLevel.take(40))
            }.toString()
            if (FirestoreProvider.isNetworkAvailable(appContext)) {
                try {
                    executeStreakXpSync(JSONObject(payload))
                    pendingSyncDao.deleteByOpKey("SYNC_STREAK_XP")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pendingSyncDao.upsert(
                        PendingSyncEntity(opKey = "SYNC_STREAK_XP", opType = "SYNC_STREAK_XP", payloadJson = payload, lastError = e.localizedMessage)
                    )
                }
            } else {
                pendingSyncDao.upsert(
                    PendingSyncEntity(opKey = "SYNC_STREAK_XP", opType = "SYNC_STREAK_XP", payloadJson = payload)
                )
            }
        }

        val cleanMemberName = profile.name.trim().ifBlank { "Member" }.take(80)
        val cleanGoal = profile.fitnessGoal.take(100)
        val cleanLevel = profile.fitnessLevel.take(40)
        if ((link.memberName != cleanMemberName || link.fitnessGoal != cleanGoal || link.fitnessLevel != cleanLevel) &&
            FirestoreProvider.isNetworkAvailable(appContext)
        ) {
            try {
                db.collection("links").document(memberId).update(
                    mapOf(
                        "memberName" to cleanMemberName,
                        "fitnessGoal" to cleanGoal,
                        "fitnessLevel" to cleanLevel,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-fatal; next online sync will refresh link metadata
            }
        }

        if (link.shareBodyStats) {
            val payload = buildBodyStatsPayloadJson(memberId, profile, measurements)
            if (FirestoreProvider.isNetworkAvailable(appContext)) {
                try {
                    executeBodyStatsSync(JSONObject(payload))
                    pendingSyncDao.deleteByOpKey("SYNC_BODY_STATS")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    pendingSyncDao.upsert(
                        PendingSyncEntity(opKey = "SYNC_BODY_STATS", opType = "SYNC_BODY_STATS", payloadJson = payload, lastError = e.localizedMessage)
                    )
                }
            } else {
                pendingSyncDao.upsert(
                    PendingSyncEntity(opKey = "SYNC_BODY_STATS", opType = "SYNC_BODY_STATS", payloadJson = payload)
                )
            }
        }
    }

    suspend fun flushPendingQueue(): Int {
        val uid = currentUid() ?: return 0
        if (!FirestoreProvider.isNetworkAvailable(appContext)) return 0
        val pending = pendingSyncDao.getAllSync()
        var flushed = 0
        for (op in pending) {
            try {
                val json = JSONObject(op.payloadJson)
                when (op.opType) {
                    "SYNC_LINK_PRIVACY" -> {
                        if (json.optString("memberId") == uid) {
                            executePrivacyUpdate(
                                memberId = uid,
                                shareWorkouts = json.getBoolean("shareWorkouts"),
                                shareStreakXp = json.getBoolean("shareStreakXp"),
                                shareBodyStats = json.getBoolean("shareBodyStats")
                            )
                        }
                    }
                    "SYNC_WORKOUTS" -> {
                        if (json.optString("memberId") == uid) executeWorkoutsSync(json)
                    }
                    "SYNC_STREAK_XP" -> {
                        if (json.optString("memberId") == uid) executeStreakXpSync(json)
                    }
                    "SYNC_BODY_STATS" -> {
                        if (json.optString("memberId") == uid) executeBodyStatsSync(json)
                    }
                    "COMPLETE_ASSIGNED_PLAN" -> {
                        val assignmentId = json.getString("assignmentId")
                        executeMarkAssignedPlanCompleted(uid, assignmentId)
                    }
                }
                pendingSyncDao.deleteByOpKey(op.opKey)
                flushed++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingSyncDao.updateAttempt(
                    id = op.id,
                    attempts = op.attempts + 1,
                    lastAttemptMs = System.currentTimeMillis(),
                    lastError = e.localizedMessage
                )
            }
        }
        return flushed
    }

    private fun buildWorkoutsPayloadJson(memberId: String, sessions: List<WorkoutSessionEntity>): String {
        val weekStartMs = com.example.data.progress.WeekDefinition.startOfWeekMillis()

        val finished = sessions.filter { it.isFinished && !it.status.equals("CANCELLED", ignoreCase = true) }
        val completedThisWeek = finished.count { it.completedAt >= weekStartMs }
        val totalCalories = finished.sumOf { it.caloriesBurned.coerceAtLeast(0) }
        val totalMinutes = finished.sumOf { (it.durationSeconds / 60).coerceAtLeast(1) }

        val sessionsArr = JSONArray()
        finished.take(25).forEach { s ->
            sessionsArr.put(JSONObject().apply {
                put("sessionId", "s_${s.sessionId}")
                put("planName", s.planName.ifBlank { "Workout" }.take(120))
                put("category", s.category.take(60))
                put("completedAtMs", s.completedAt.coerceAtLeast(0L))
                put("durationMinutes", (s.durationSeconds / 60).coerceAtLeast(1))
                put("caloriesBurned", s.caloriesBurned.coerceAtLeast(0))
                put("completed", s.isFinished)
            })
        }
        return JSONObject().apply {
            put("memberId", memberId)
            put("totalWorkouts", finished.size)
            put("completedThisWeek", completedThisWeek)
            put("totalCaloriesBurned", totalCalories)
            put("totalMinutes", totalMinutes)
            put("sessions", sessionsArr)
        }.toString()
    }

    private suspend fun executeWorkoutsSync(json: JSONObject) {
        val memberId = json.getString("memberId")
        val summaryMap = mapOf(
            "memberId" to memberId,
            "totalWorkouts" to json.getInt("totalWorkouts").coerceAtLeast(0),
            "completedThisWeek" to json.getInt("completedThisWeek").coerceAtLeast(0),
            "totalCaloriesBurned" to json.getInt("totalCaloriesBurned").coerceAtLeast(0),
            "totalMinutes" to json.getInt("totalMinutes").coerceAtLeast(0),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val batch = db.batch()
        val summaryRef = db.collection("users").document(memberId)
            .collection("sharedSummary").document("workouts")
        batch.set(summaryRef, summaryMap)

        val sessions = json.getJSONArray("sessions")
        for (i in 0 until sessions.length()) {
            val obj = sessions.getJSONObject(i)
            val docId = obj.getString("sessionId")
            val ref = db.collection("users").document(memberId)
                .collection("sharedWorkouts").document(docId)
            val sMap = mutableMapOf<String, Any?>(
                "memberId" to memberId,
                "planName" to obj.getString("planName").take(120),
                "category" to obj.optString("category").takeIf { it.isNotBlank() }?.take(60),
                "completedAtMs" to obj.getLong("completedAtMs").coerceAtLeast(0L),
                "durationMinutes" to obj.getInt("durationMinutes").coerceAtLeast(0),
                "caloriesBurned" to obj.getInt("caloriesBurned").coerceAtLeast(0),
                "completed" to obj.getBoolean("completed"),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            batch.set(ref, sMap)
        }
        batch.commit().await()
    }

    private suspend fun executeStreakXpSync(json: JSONObject) {
        val memberId = json.getString("memberId")
        val map = mutableMapOf<String, Any?>(
            "memberId" to memberId,
            "streakDays" to json.getInt("streakDays").coerceAtLeast(0),
            "level" to json.getInt("level").coerceAtLeast(1),
            "xpPoints" to json.getInt("xpPoints").coerceAtLeast(0),
            "fitnessGoal" to json.optString("fitnessGoal").takeIf { it.isNotBlank() }?.take(100),
            "fitnessLevel" to json.optString("fitnessLevel").takeIf { it.isNotBlank() }?.take(40),
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }
        db.collection("users").document(memberId)
            .collection("sharedSummary").document("streakXp")
            .set(map).await()
    }

    private fun buildBodyStatsPayloadJson(
        memberId: String,
        profile: UserProfileEntity,
        measurements: List<BodyMeasurementEntity>
    ): String {
        val latest = measurements.firstOrNull()
        val heightCm = (profile.heightCm.takeIf { it > 0f } ?: latest?.heightCm ?: 175f).coerceIn(50f, 300f)
        val weightKg = (profile.weightKg.takeIf { it > 0f } ?: latest?.weightKg ?: 70f).coerceIn(10f, 500f)
        val hM = heightCm / 100f
        val bmi = (weightKg / (hM * hM)).coerceIn(5f, 120f)
        val bmiCat = when {
            bmi < 18.5f -> "Underweight"
            bmi < 25f -> "Normal weight"
            bmi < 30f -> "Overweight"
            else -> "Obese"
        }.take(40)

        val measArr = JSONArray()
        measurements.take(20).forEach { m ->
            val mWeight = m.weightKg.coerceIn(10f, 500f)
            val mHeight = m.heightCm.coerceIn(50f, 300f)
            val mBmi = m.bmi.coerceIn(5f, 120f)
            measArr.put(JSONObject().apply {
                put("measurementId", "m_${m.id}")
                put("timestampMs", m.timestamp.coerceAtLeast(0L))
                put("weightKg", mWeight.toDouble())
                put("heightCm", mHeight.toDouble())
                put("bmi", mBmi.toDouble())
                put("bmiCategory", m.bmiCategory.ifBlank { "Normal" }.take(40))
                if (m.chestCm != null && m.chestCm > 0f && m.chestCm <= 300f) put("chestCm", m.chestCm.toDouble())
                if (m.armCm != null && m.armCm > 0f && m.armCm <= 200f) put("armCm", m.armCm.toDouble())
                if (m.waistCm != null && m.waistCm > 0f && m.waistCm <= 300f) put("waistCm", m.waistCm.toDouble())
                if (m.notes.isNotBlank()) put("notes", m.notes.take(300))
            })
        }

        return JSONObject().apply {
            put("memberId", memberId)
            put("heightCm", heightCm.toDouble())
            put("weightKg", weightKg.toDouble())
            put("bmi", bmi.toDouble())
            put("bmiCategory", bmiCat)
            latest?.chestCm?.takeIf { it > 0f && it <= 300f }?.let { put("chestCm", it.toDouble()) }
            latest?.armCm?.takeIf { it > 0f && it <= 200f }?.let { put("armCm", it.toDouble()) }
            latest?.waistCm?.takeIf { it > 0f && it <= 300f }?.let { put("waistCm", it.toDouble()) }
            put("measurements", measArr)
        }.toString()
    }

    private suspend fun executeBodyStatsSync(json: JSONObject) {
        val memberId = json.getString("memberId")
        val summaryMap = mutableMapOf<String, Any?>(
            "memberId" to memberId,
            "heightCm" to json.getDouble("heightCm"),
            "weightKg" to json.getDouble("weightKg"),
            "bmi" to json.getDouble("bmi"),
            "bmiCategory" to json.getString("bmiCategory").take(40),
            "chestCm" to if (json.has("chestCm")) json.getDouble("chestCm") else null,
            "armCm" to if (json.has("armCm")) json.getDouble("armCm") else null,
            "waistCm" to if (json.has("waistCm")) json.getDouble("waistCm") else null,
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        val batch = db.batch()
        val summaryRef = db.collection("users").document(memberId)
            .collection("sharedSummary").document("bodyStats")
        batch.set(summaryRef, summaryMap)

        val arr = json.getJSONArray("measurements")
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val docId = obj.getString("measurementId")
            val ref = db.collection("users").document(memberId)
                .collection("sharedMeasurements").document(docId)
            val mMap = mutableMapOf<String, Any?>(
                "memberId" to memberId,
                "timestampMs" to obj.getLong("timestampMs").coerceAtLeast(0L),
                "weightKg" to obj.getDouble("weightKg"),
                "heightCm" to obj.getDouble("heightCm"),
                "bmi" to obj.getDouble("bmi"),
                "bmiCategory" to obj.getString("bmiCategory").take(40),
                "chestCm" to if (obj.has("chestCm")) obj.getDouble("chestCm") else null,
                "armCm" to if (obj.has("armCm")) obj.getDouble("armCm") else null,
                "waistCm" to if (obj.has("waistCm")) obj.getDouble("waistCm") else null,
                "notes" to obj.optString("notes").takeIf { it.isNotBlank() }?.take(300),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            batch.set(ref, mMap)
        }
        batch.commit().await()
    }

    // --- TRAINER READS FOR LINKED MEMBER (PRIVACY-GATED BY RULES) ---

    fun observeMemberWorkoutsSummary(memberId: String): Flow<CoachingUiState<SharedWorkoutsSummary?>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("users").document(memberId)
            .collection("sharedSummary").document("workouts")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(CoachingUiState.Error("Workouts sharing is turned off by this member."))
                    } else {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load workouts"))
                    }
                    return@addSnapshotListener
                }
                trySend(CoachingUiState.Success(snap?.let { CoachingMappers.parseWorkoutsSummary(it) }))
            }
        awaitClose { reg.remove() }
    }

    fun observeMemberWorkouts(memberId: String): Flow<CoachingUiState<List<SharedWorkoutItem>>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("users").document(memberId)
            .collection("sharedWorkouts")
            .orderBy("completedAtMs", Query.Direction.DESCENDING)
            .limit(30)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(CoachingUiState.Error("Workouts sharing is turned off by this member."))
                    } else {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load workout sessions"))
                    }
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { CoachingMappers.parseWorkoutItem(it) }.orEmpty()
                trySend(CoachingUiState.Success(list))
            }
        awaitClose { reg.remove() }
    }

    fun observeMemberStreakXp(memberId: String): Flow<CoachingUiState<SharedStreakXp?>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("users").document(memberId)
            .collection("sharedSummary").document("streakXp")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(CoachingUiState.Error("Streak & XP sharing is turned off by this member."))
                    } else {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load streak & XP"))
                    }
                    return@addSnapshotListener
                }
                trySend(CoachingUiState.Success(snap?.let { CoachingMappers.parseStreakXp(it) }))
            }
        awaitClose { reg.remove() }
    }

    fun observeMemberBodyStats(memberId: String): Flow<CoachingUiState<SharedBodyStatsSummary?>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("users").document(memberId)
            .collection("sharedSummary").document("bodyStats")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(CoachingUiState.Error("Body stats sharing is turned off by this member."))
                    } else {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load body stats"))
                    }
                    return@addSnapshotListener
                }
                trySend(CoachingUiState.Success(snap?.let { CoachingMappers.parseBodyStatsSummary(it) }))
            }
        awaitClose { reg.remove() }
    }

    fun observeMemberMeasurements(memberId: String): Flow<CoachingUiState<List<SharedMeasurementItem>>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("users").document(memberId)
            .collection("sharedMeasurements")
            .orderBy("timestampMs", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    if (err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        trySend(CoachingUiState.Error("Body stats sharing is turned off by this member."))
                    } else {
                        trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load measurements"))
                    }
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { CoachingMappers.parseMeasurementItem(it) }.orEmpty()
                trySend(CoachingUiState.Success(list))
            }
        awaitClose { reg.remove() }
    }

    // --- MESSAGING ---

    fun observeChatThread(trainerId: String, memberId: String): Flow<ChatThread?> = callbackFlow {
        val pairId = CoachingMappers.pairId(trainerId, memberId)
        val reg = db.collection("chats").document(pairId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snap?.let { CoachingMappers.parseChatThread(it) })
            }
        awaitClose { reg.remove() }
    }

    fun observeTrainerChatThreads(trainerId: String): Flow<List<ChatThread>> = callbackFlow {
        val reg = db.collection("chats")
            .whereEqualTo("trainerId", trainerId)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snaps?.documents
                    ?.mapNotNull { CoachingMappers.parseChatThread(it) }
                    ?.sortedByDescending { it.lastMessageAtMs ?: 0L }
                    .orEmpty()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    fun observeChatMessages(trainerId: String, memberId: String): Flow<CoachingUiState<List<ChatMessage>>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val pairId = CoachingMappers.pairId(trainerId, memberId)
        val reg = db.collection("chats").document(pairId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load messages"))
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { CoachingMappers.parseChatMessage(it) }.orEmpty()
                trySend(CoachingUiState.Success(list))
            }
        awaitClose { reg.remove() }
    }

    suspend fun fetchChatMessages(trainerId: String, memberId: String): Result<List<ChatMessage>> = runCatching {
        val pairId = CoachingMappers.pairId(trainerId, memberId)
        val snaps = db.collection("chats").document(pairId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .get()
            .await()
        snaps.documents.mapNotNull { CoachingMappers.parseChatMessage(it) }
    }

    suspend fun sendChatMessage(
        trainerId: String,
        memberId: String,
        trainerName: String,
        memberName: String,
        senderRole: AppRole,
        senderName: String,
        text: String
    ): Result<Unit> {
        val uid = currentUid() ?: return Result.failure(IllegalStateException("Not signed in."))
        val trimmed = text.trim().take(1000)
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Message cannot be empty."))

        val pairId = CoachingMappers.pairId(trainerId, memberId)
        val threadRef = db.collection("chats").document(pairId)
        val msgRef = threadRef.collection("messages").document(UUID.randomUUID().toString().replace("-", ""))

        return try {
            val threadSnap = try { threadRef.get().await() } catch (_: Exception) { null }
            val existingTrainerUnread = (threadSnap?.get("trainerUnreadCount") as? Number)?.toInt() ?: 0
            val existingMemberUnread = (threadSnap?.get("memberUnreadCount") as? Number)?.toInt() ?: 0

            val nextTrainerUnread = if (senderRole == AppRole.MEMBER) existingTrainerUnread + 1 else 0
            val nextMemberUnread = if (senderRole == AppRole.TRAINER) existingMemberUnread + 1 else 0

            val batch = db.batch()
            if (threadSnap == null || !threadSnap.exists()) {
                val threadCreate = mapOf(
                    "trainerId" to trainerId,
                    "memberId" to memberId,
                    "trainerName" to trainerName.ifBlank { "Coach" }.take(80),
                    "memberName" to memberName.ifBlank { "Member" }.take(80),
                    "lastMessage" to trimmed,
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "trainerUnreadCount" to nextTrainerUnread,
                    "memberUnreadCount" to nextMemberUnread,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(threadRef, threadCreate)
            } else {
                val threadUpdate = mapOf(
                    "trainerName" to trainerName.ifBlank { "Coach" }.take(80),
                    "memberName" to memberName.ifBlank { "Member" }.take(80),
                    "lastMessage" to trimmed,
                    "lastSenderId" to uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "trainerUnreadCount" to nextTrainerUnread,
                    "memberUnreadCount" to nextMemberUnread,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.update(threadRef, threadUpdate)
            }

            val msgMap = mapOf(
                "trainerId" to trainerId,
                "memberId" to memberId,
                "senderId" to uid,
                "senderRole" to senderRole.name,
                "senderName" to senderName.ifBlank { "User" }.take(80),
                "text" to trimmed,
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(msgRef, msgMap)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "chats/$pairId/messages")
            Result.failure(e)
        }
    }

    suspend fun markChatRead(trainerId: String, memberId: String, readerRole: AppRole) {
        val pairId = CoachingMappers.pairId(trainerId, memberId)
        try {
            val threadRef = db.collection("chats").document(pairId)
            val snap = threadRef.get().await()
            if (!snap.exists()) return
            val field = if (readerRole == AppRole.TRAINER) "trainerUnreadCount" else "memberUnreadCount"
            val current = (snap.get(field) as? Number)?.toInt() ?: 0
            if (current > 0) {
                threadRef.update(
                    mapOf(
                        field to 0,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Non-fatal
        }
    }

    // --- TRAINER-ASSIGNED WORKOUT PLANS ---

    suspend fun assignPlanToMember(
        memberId: String,
        trainerName: String,
        planTitle: String,
        planDescription: String,
        category: String,
        difficulty: String,
        estimatedDurationMinutes: Int,
        note: String?,
        startDateEpochMs: Long,
        exercises: List<AssignedPlanExercisePayload>
    ): Result<String> {
        val trainerId = currentUid() ?: return Result.failure(IllegalStateException("Not signed in."))
        if (exercises.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least one exercise."))
        }
        val assignmentId = "ap_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        return try {
            val exMaps = exercises.take(50).map { ex ->
                mapOf(
                    "exerciseId" to ex.exerciseId,
                    "exerciseName" to ex.exerciseName.take(100),
                    "type" to ex.type,
                    "count" to ex.count.coerceAtLeast(1),
                    "restSeconds" to ex.restSeconds.coerceIn(0, 300)
                )
            }
            val payload = mutableMapOf<String, Any?>(
                "trainerId" to trainerId,
                "trainerName" to trainerName.ifBlank { "Coach" }.take(80),
                "memberId" to memberId,
                "planTitle" to planTitle.trim().take(120),
                "planDescription" to planDescription.trim().take(500).ifBlank { "Coach-designed workout plan" },
                "category" to category.ifBlank { "Full Body" }.take(60),
                "difficulty" to difficulty.ifBlank { "Intermediate" }.take(40),
                "estimatedDurationMinutes" to estimatedDurationMinutes.coerceIn(1, 300),
                "note" to note?.trim()?.takeIf { it.isNotBlank() }?.take(500),
                "startDateEpochMs" to startDateEpochMs.coerceAtLeast(0L),
                "exercises" to exMaps,
                "completed" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            db.collection("users").document(memberId)
                .collection("assignedPlans").document(assignmentId)
                .set(payload).await()
            Result.success(assignmentId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$memberId/assignedPlans/$assignmentId")
            Result.failure(e)
        }
    }

    suspend fun deleteAssignedPlan(
        memberId: String,
        assignmentId: String,
        actorUid: String,
        actorName: String
    ): Result<Unit> {
        val safeActorName = actorName.trim().ifBlank { "Trainer" }.take(100)
        return try {
            val updates = mapOf<String, Any>(
                "deletedAt" to FieldValue.serverTimestamp(),
                "deletedByUid" to actorUid,
                "deletedByName" to safeActorName,
                "deletedByRole" to "trainer",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(memberId)
                .collection("assignedPlans").document(assignmentId)
                .update(updates)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$memberId/assignedPlans/$assignmentId")
            Result.failure(e)
        }
    }

    fun observeMemberAssignedPlans(memberId: String): Flow<CoachingUiState<List<AssignedPlanDoc>>> = callbackFlow {
        trySend(CoachingUiState.Loading)
        val reg = db.collection("users").document(memberId)
            .collection("assignedPlans")
            .orderBy("startDateEpochMs", Query.Direction.DESCENDING)
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    trySend(CoachingUiState.Error(err.localizedMessage ?: "Failed to load assigned plans"))
                    return@addSnapshotListener
                }
                val list = snaps?.documents?.mapNotNull { CoachingMappers.parseAssignedPlan(it) }.orEmpty()
                trySend(CoachingUiState.Success(list))
            }
        awaitClose { reg.remove() }
    }

    suspend fun fetchMemberAssignedPlans(memberId: String): Result<List<AssignedPlanDoc>> {
        return try {
            val snaps = db.collection("users").document(memberId)
                .collection("assignedPlans")
                .orderBy("startDateEpochMs", Query.Direction.DESCENDING)
                .get()
                .await()
            val list = snaps.documents
                .mapNotNull { CoachingMappers.parseAssignedPlan(it) }
                .filterNot { it.isDeleted }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$memberId/assignedPlans")
            Result.failure(e)
        }
    }

    suspend fun syncAssignedPlansToRoom(memberId: String, remotePlans: List<AssignedPlanDoc>, activeLink: TrainerMemberLink?) {
        // Soft-deleted plans must always be removed locally (even if completed) and never re-inserted
        for (remote in remotePlans) {
            if (remote.isDeleted) {
                val localId = "assigned_${remote.id}"
                workoutPlanDao.deletePlanExercises(localId)
                workoutPlanDao.deletePlanById(localId)
            }
        }

        val activeRemotePlans = remotePlans.filterNot { it.isDeleted }
        val localAssigned = workoutPlanDao.getAssignedPlansSync()
        val remoteIds = activeRemotePlans.map { it.id }.toSet()

        // Remove local uncompleted assigned plans that were removed or belong to an unlinked trainer
        for (local in localAssigned) {
            val rId = local.remoteAssignmentId ?: continue
            val trainerMismatch = activeLink == null || activeLink.status != LinkStatus.ACTIVE || local.assignedByTrainerId != activeLink.trainerId
            if ((!local.isCompletedByMember && rId !in remoteIds) || (!local.isCompletedByMember && trainerMismatch)) {
                workoutPlanDao.deletePlanExercises(local.id)
                workoutPlanDao.deletePlanById(local.id)
            }
        }

        if (activeLink == null || activeLink.status != LinkStatus.ACTIVE) return

        for (remote in activeRemotePlans) {
            val localId = "assigned_${remote.id}"
            val existing = workoutPlanDao.getPlanById(localId)
            val isCancelled = remote.isCancelled
            val isCompleted = if (isCancelled) false else ((existing?.isCompletedByMember == true) || remote.completed)
            val localStatus = when {
                isCancelled -> "CANCELLED"
                isCompleted -> "COMPLETED"
                else -> "ACTIVE"
            }
            val planEntity = WorkoutPlanEntity(
                id = localId,
                name = remote.planTitle,
                description = remote.planDescription.ifBlank { "Assigned by Coach ${remote.trainerName}" },
                category = remote.category,
                difficulty = remote.difficulty,
                estimatedDurationMinutes = remote.estimatedDurationMinutes,
                isCustom = false,
                isChallenge = false,
                createdAt = remote.startDateEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                targetMusclesDescription = "Coach-assigned routine by ${remote.trainerName}",
                educationalOverview = remote.note?.takeIf { it.isNotBlank() }
                    ?: "Assigned by Coach ${remote.trainerName}. Complete every movement with controlled form.",
                assignedByTrainerId = remote.trainerId,
                assignedByTrainerName = remote.trainerName,
                trainerNote = remote.note,
                startDateEpochMs = remote.startDateEpochMs,
                remoteAssignmentId = remote.id,
                isCompletedByMember = isCompleted,
                status = localStatus,
                cancelledReason = remote.cancelledReason,
                cancelledByUid = remote.cancelledByUid,
                cancelledByName = remote.cancelledByName,
                cancelledByRole = remote.cancelledByRole,
                cancelledAtUtcMs = remote.cancelledAtMs,
                excludeFromBilling = remote.excludeFromBilling || isCancelled
            )
            workoutPlanDao.insertPlan(planEntity)

            val planExercises = remote.exercises.mapIndexed { index, ex ->
                WorkoutPlanExerciseEntity(
                    id = 0,
                    planId = localId,
                    exerciseId = ex.exerciseId,
                    orderIndex = index,
                    type = ex.type,
                    count = ex.count,
                    restSeconds = ex.restSeconds
                )
            }
            workoutPlanDao.deletePlanExercises(localId)
            workoutPlanDao.insertPlanExercises(planExercises)
        }
    }

    private suspend fun executeMarkAssignedPlanCompleted(memberId: String, assignmentId: String) {
        val ref = db.collection("users").document(memberId)
            .collection("assignedPlans").document(assignmentId)
        val snap = ref.get().await()
        if (snap.get("deletedAt") != null) {
            // Soft-deleted plans must never be marked completed
            return
        }
        val currentStatus = (snap.getString("status") ?: "scheduled").lowercase()
        if (currentStatus == "cancelled") {
            // Cancelled workouts must never be marked completed
            return
        }
        ref.update(
            mapOf(
                "completed" to true,
                "completedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun updateMemberBirthdate(uid: String, birthdate: String): Result<Int> {
        return try {
            val trimmed = birthdate.trim()
            val age = ManilaDateTime.calculateAge(trimmed)
            val userRef = db.collection("users").document(uid)
            userRef.update(
                mapOf(
                    "birthdate" to trimmed,
                    "calculatedAge" to age,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(age)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    fun observeAllMembersForAdmin(): kotlinx.coroutines.flow.Flow<Result<List<UserAccountDoc>>> {
        return db.collection("users")
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { CoachingMappers.parseUserAccount(it) }
                    .filter { it.accountType == AppRole.MEMBER }
                    .sortedBy { it.name.lowercase() }
                Result.success(list)
            }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(Result.failure(error))
            }
    }

    suspend fun updateMemberTypeAndDiscountAdmin(
        targetUserId: String,
        memberType: String,
        isVerified: Boolean,
        adminUid: String,
        adminName: String = "Admin",
        discountPercent: Double,
        discountAppliesToBilling: Boolean,
        discountAppliesToPackages: Boolean,
        discountAppliesToSessionFees: Boolean
    ): Result<Unit> {
        return try {
            val resolvedType = MemberType.fromString(memberType)
            val userRef = db.collection("users").document(targetUserId)
            val updates = mutableMapOf<String, Any?>(
                "memberType" to resolvedType.label,
                "isVerified" to isVerified,
                "verifiedBy" to (if (isVerified) adminName.ifBlank { "Admin" } else null),
                "verifiedAt" to (if (isVerified) FieldValue.serverTimestamp() else null),
                "discountPercent" to discountPercent.coerceIn(0.0, 100.0),
                "discountAppliesToBilling" to discountAppliesToBilling,
                "discountAppliesToPackages" to discountAppliesToPackages,
                "discountAppliesToSessionFees" to discountAppliesToSessionFees,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUserId")
            Result.failure(e)
        }
    }

    suspend fun updateMemberTypeAndDiscountAdmin(
        memberId: String,
        adminUid: String,
        adminName: String,
        memberType: MemberType,
        isVerified: Boolean,
        discountPercent: Double,
        appliesToBilling: Boolean,
        appliesToPackages: Boolean,
        appliesToSessionFees: Boolean
    ): Result<Unit> {
        return updateMemberTypeAndDiscountAdmin(
            targetUserId = memberId,
            memberType = memberType.label,
            isVerified = isVerified,
            adminUid = adminUid,
            adminName = adminName,
            discountPercent = discountPercent,
            discountAppliesToBilling = appliesToBilling,
            discountAppliesToPackages = appliesToPackages,
            discountAppliesToSessionFees = appliesToSessionFees
        )
    }
}
