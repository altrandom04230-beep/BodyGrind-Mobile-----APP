package com.example.data.coaching

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.R
import com.example.data.trainer.AppRole
import com.example.data.trainer.MemberStatus
import com.example.utils.ManilaDateTime
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

sealed class CoachingUiState<out T> {
    data object Loading : CoachingUiState<Nothing>()
    data class Success<T>(val data: T) : CoachingUiState<T>()
    data class Error(val message: String) : CoachingUiState<Nothing>()
}

enum class OperationType {
    CREATE, UPDATE, DELETE, LIST, GET, WRITE
}

object CoachingSyncHealth {
    private val userWriteLimitedUids = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val _rulesPublishWarning = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val rulesPublishWarning: kotlinx.coroutines.flow.StateFlow<String?> = _rulesPublishWarning

    fun markUserWritePermissionLimited(uid: String) {
        if (uid.isNotBlank()) userWriteLimitedUids.add(uid)
        _rulesPublishWarning.value =
            "Firestore rules in Firebase project 'bodygrind-gym-app' blocked a coaching write. Publish firestore.rules in Firebase Console -> Firestore Database -> Rules."
    }

    fun clearUserWritePermissionLimited(uid: String) {
        userWriteLimitedUids.remove(uid)
        _rulesPublishWarning.value = null
    }

    fun hasUserDocWritePermissionError(uid: String): Boolean =
        uid.isNotBlank() && userWriteLimitedUids.contains(uid)
}

fun toFriendlyFirestoreMessage(error: Throwable): String {
    val fsEx = generateSequence(error) { it.cause }
        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
        .firstOrNull()
    if (fsEx?.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        return "Permission denied by Firestore rules. Publish firestore.rules in Firebase Console (bodygrind-gym-app) and try again."
    }
    return error.localizedMessage ?: "Sync error"
}

fun handleFirestoreError(error: Exception, operationType: OperationType, path: String?): String {
    val auth = try { FirebaseAuth.getInstance() } catch (_: Throwable) { null }
    val currentUser = auth?.currentUser
    val payload = JSONObject().apply {
        put("error", error.message ?: "Unknown Firestore error")
        put("operationType", operationType.name)
        put("path", path ?: "")
        put("authInfo", JSONObject().apply {
            put("userId", currentUser?.uid ?: "")
            put("email", currentUser?.email ?: "")
            put("providerCount", currentUser?.providerData?.size ?: 0)
        })
    }
    Log.e("FirestoreCoachingError", payload.toString(), error)
    return toFriendlyFirestoreMessage(error)
}

object FirestoreProvider {
    fun getDb(context: Context): FirebaseFirestore {
        val dbId = try {
            context.getString(R.string.firestore_database_id)
        } catch (_: Throwable) {
            null
        }
        return if (!dbId.isNullOrBlank()) {
            FirebaseFirestore.getInstance(dbId)
        } else {
            FirebaseFirestore.getInstance()
        }
    }

    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

object CoachingSessionPrefs {
    private const val PREFS_NAME = "bodygrind_coaching_prefs"
    private const val KEY_ROLE_PREFIX = "role_"
    private const val KEY_NAME_PREFIX = "name_"
    private const val KEY_INVITE_PREFIX = "invite_"
    private const val KEY_LAST_SEEN_MSG_PREFIX = "last_seen_msg_"
    private const val KEY_NOTIFIED_CANCELLED_PLANS_PREFIX = "notified_cancelled_plans_"
    private const val KEY_CANCELLED_PLANS_INIT_PREFIX = "cancelled_plans_init_"
    private const val KEY_MEMBER_NOTIF_PERM_ASKED_PREFIX = "member_notif_perm_asked_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUserMeta(context: Context, uid: String, role: AppRole, name: String? = null) {
        prefs(context).edit().apply {
            putString("$KEY_ROLE_PREFIX$uid", role.name)
            if (!name.isNullOrBlank()) {
                putString("$KEY_NAME_PREFIX$uid", name)
            }
        }.apply()
    }

    fun getSavedRole(context: Context, uid: String): AppRole? {
        val raw = prefs(context).getString("$KEY_ROLE_PREFIX$uid", null) ?: return null
        return enumValues<AppRole>().firstOrNull { it.name == raw }
    }

    fun getSavedName(context: Context, uid: String): String? {
        return prefs(context).getString("$KEY_NAME_PREFIX$uid", null)
    }

    fun saveInviteCode(context: Context, uid: String, code: String) {
        prefs(context).edit().putString("$KEY_INVITE_PREFIX$uid", code).apply()
    }

    fun getSavedInviteCode(context: Context, uid: String): String? {
        return prefs(context).getString("$KEY_INVITE_PREFIX$uid", null)
    }

    fun getLastNotifiedMessageTime(context: Context, pairId: String): Long {
        return prefs(context).getLong("$KEY_LAST_SEEN_MSG_PREFIX$pairId", 0L)
    }

    fun setLastNotifiedMessageTime(context: Context, pairId: String, timestampMs: Long) {
        prefs(context).edit().putLong("$KEY_LAST_SEEN_MSG_PREFIX$pairId", timestampMs).apply()
    }

    fun isCancelledPlanNotifierInitialized(context: Context, uid: String): Boolean {
        return prefs(context).getBoolean("$KEY_CANCELLED_PLANS_INIT_PREFIX$uid", false)
    }

    fun getNotifiedCancelledPlanIds(context: Context, uid: String): Set<String> {
        return prefs(context)
            .getStringSet("$KEY_NOTIFIED_CANCELLED_PLANS_PREFIX$uid", null)
            ?.toSet()
            .orEmpty()
    }

    fun markCancelledPlansNotified(context: Context, uid: String, planIds: Collection<String>) {
        val p = prefs(context)
        val merged = HashSet(getNotifiedCancelledPlanIds(context, uid))
        planIds.filter { it.isNotBlank() }.forEach { merged.add(it) }
        p.edit()
            .putStringSet("$KEY_NOTIFIED_CANCELLED_PLANS_PREFIX$uid", merged)
            .putBoolean("$KEY_CANCELLED_PLANS_INIT_PREFIX$uid", true)
            .apply()
    }

    fun hasRequestedMemberNotificationPermission(context: Context, uid: String): Boolean {
        return prefs(context).getBoolean("$KEY_MEMBER_NOTIF_PERM_ASKED_PREFIX$uid", false)
    }

    fun markMemberNotificationPermissionRequested(context: Context, uid: String) {
        prefs(context).edit().putBoolean("$KEY_MEMBER_NOTIF_PERM_ASKED_PREFIX$uid", true).apply()
    }
}

enum class MemberType(val label: String) {
    REGULAR("Regular"),
    STUDENT("Student"),
    SENIOR_CITIZEN("Senior Citizen"),
    PWD("PWD");

    val displayName: String get() = label
    val isDiscounted: Boolean get() = this != REGULAR

    companion object {
        fun fromString(value: String?): MemberType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) || it.label.equals(value, ignoreCase = true) }
                ?: REGULAR
    }
}

data class MemberDiscountSettings(
    val discountPercent: Double = 0.0,
    val appliesToBilling: Boolean = false,
    val appliesToPackages: Boolean = false,
    val appliesToSessionFees: Boolean = false
) {
    val isEnabled: Boolean get() = discountPercent > 0.0 && (appliesToBilling || appliesToPackages || appliesToSessionFees)

    fun formattedSummary(): String {
        if (discountPercent <= 0.0) return "No discount"
        val targets = mutableListOf<String>()
        if (appliesToBilling) targets.add("Billing")
        if (appliesToPackages) targets.add("Packages")
        if (appliesToSessionFees) targets.add("Session Fees")
        return if (targets.isEmpty()) "No charges selected" else "${String.format(java.util.Locale.US, "%.0f%%", discountPercent)} off (${targets.joinToString(", ")})"
    }
}

data class UserAccountDoc(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val accountType: AppRole = AppRole.MEMBER,
    val role: String? = null,
    val status: String = "active",
    val trainerInviteCode: String? = null,
    val trainerTitle: String? = null,
    val specialization: String? = null,
    val fitnessGoal: String? = null,
    val fitnessLevel: String? = null,
    val birthdate: String? = null,
    val calculatedAge: Int? = null,
    val memberType: String = "Regular",
    val isVerified: Boolean = false,
    val verifiedBy: String? = null,
    val verifiedAtMs: Long? = null,
    val discountPercent: Double = 0.0,
    val discountAppliesToBilling: Boolean = false,
    val discountAppliesToPackages: Boolean = false,
    val discountAppliesToSessionFees: Boolean = false
) {
    val effectiveCalculatedAge: Int? get() = calculatedAge ?: birthdate?.let { ManilaDateTime.calculateAge(it) }
    fun effectiveAge(): Int? = effectiveCalculatedAge
    fun isSeniorEligible(): Boolean = (effectiveCalculatedAge ?: 0) >= 60
    fun resolvedMemberType(): MemberType = MemberType.fromString(memberType)
    val isDiscountedType: Boolean get() = resolvedMemberType().isDiscounted
}

data class TrainerInviteDoc(
    val inviteCode: String = "",
    val trainerId: String = "",
    val trainerName: String = "",
    val trainerTitle: String? = null,
    val specialization: String? = null,
    val active: Boolean = true
)

enum class LinkStatus {
    PENDING_CONFIRMATION,
    ACTIVE
}

data class LinkConsentOptions(
    val shareWorkouts: Boolean = true,
    val shareStreakXp: Boolean = true,
    val shareBodyStats: Boolean = true
)

data class TrainerMemberLink(
    val memberId: String = "",
    val trainerId: String = "",
    val memberName: String = "",
    val trainerName: String = "",
    val trainerTitle: String? = null,
    val inviteCode: String? = null,
    val status: LinkStatus = LinkStatus.ACTIVE,
    val memberStatus: MemberStatus = MemberStatus.ACTIVE,
    val requestedBy: AppRole = AppRole.MEMBER,
    val shareWorkouts: Boolean = false,
    val shareStreakXp: Boolean = false,
    val shareBodyStats: Boolean = false,
    val agreedShareWorkouts: Boolean = false,
    val agreedShareStreakXp: Boolean = false,
    val agreedShareBodyStats: Boolean = false,
    val fitnessGoal: String? = null,
    val fitnessLevel: String? = null,
    val lastActiveAtMs: Long? = null,
    val acceptedAtMs: Long? = null,
    val updatedAtMs: Long? = null
)

data class SharedWorkoutsSummary(
    val memberId: String = "",
    val totalWorkouts: Int = 0,
    val completedThisWeek: Int = 0,
    val totalCaloriesBurned: Int = 0,
    val totalMinutes: Int = 0,
    val updatedAtMs: Long? = null
)

data class SharedWorkoutItem(
    val id: String = "",
    val memberId: String = "",
    val planName: String = "",
    val category: String = "",
    val completedAtMs: Long = 0L,
    val durationMinutes: Int = 0,
    val caloriesBurned: Int = 0,
    val completed: Boolean = true
)

data class SharedStreakXp(
    val memberId: String = "",
    val streakDays: Int = 0,
    val level: Int = 1,
    val xpPoints: Int = 0,
    val fitnessGoal: String? = null,
    val fitnessLevel: String? = null,
    val updatedAtMs: Long? = null
)

data class SharedBodyStatsSummary(
    val memberId: String = "",
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val bmi: Float = 0f,
    val bmiCategory: String = "",
    val chestCm: Float? = null,
    val armCm: Float? = null,
    val waistCm: Float? = null,
    val updatedAtMs: Long? = null
)

data class SharedMeasurementItem(
    val id: String = "",
    val memberId: String = "",
    val timestampMs: Long = 0L,
    val weightKg: Float = 0f,
    val heightCm: Float = 0f,
    val bmi: Float = 0f,
    val bmiCategory: String = "",
    val chestCm: Float? = null,
    val armCm: Float? = null,
    val waistCm: Float? = null,
    val notes: String = ""
)

data class ChatThread(
    val pairId: String = "",
    val trainerId: String = "",
    val memberId: String = "",
    val trainerName: String = "",
    val memberName: String = "",
    val lastMessage: String? = null,
    val lastSenderId: String? = null,
    val lastMessageAtMs: Long? = null,
    val trainerUnreadCount: Int = 0,
    val memberUnreadCount: Int = 0
)

data class ChatMessage(
    val id: String = "",
    val trainerId: String = "",
    val memberId: String = "",
    val senderId: String = "",
    val senderRole: AppRole = AppRole.MEMBER,
    val senderName: String = "",
    val text: String = "",
    val createdAtMs: Long = 0L
)

data class AssignedPlanExercisePayload(
    val exerciseId: String = "",
    val exerciseName: String = "",
    val type: String = "REPS",
    val count: Int = 10,
    val restSeconds: Int = 30
)

data class AssignedPlanDoc(
    val id: String = "",
    val trainerId: String = "",
    val trainerName: String = "",
    val memberId: String = "",
    val planTitle: String = "",
    val planDescription: String = "",
    val category: String = "Full Body",
    val difficulty: String = "Intermediate",
    val estimatedDurationMinutes: Int = 20,
    val note: String? = null,
    val startDateEpochMs: Long = 0L,
    val exercises: List<AssignedPlanExercisePayload> = emptyList(),
    val completed: Boolean = false,
    val completedAtMs: Long? = null,
    val status: String = "scheduled", // "scheduled", "completed", "cancelled"
    val excludeFromBilling: Boolean = false,
    val cancelledReason: String? = null,
    val cancelledByUid: String? = null,
    val cancelledByName: String? = null,
    val cancelledByRole: String? = null,
    val cancelledAtMs: Long? = null,
    val deletedAtMs: Long? = null,
    val deletedByUid: String? = null,
    val deletedByName: String? = null,
    val deletedByRole: String? = null,
    val updatedAtMs: Long? = null
) {
    val isCancelled: Boolean
        get() = status.equals("cancelled", ignoreCase = true)

    val isDeleted: Boolean
        get() = deletedAtMs != null

    val isEffectivelyCompleted: Boolean
        get() = !isCancelled && (completed || status.equals("completed", ignoreCase = true))
}

object CoachingMappers {
    fun pairId(trainerId: String, memberId: String): String = "${trainerId}__${memberId}"

    fun parseInviteCodeFromInput(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("BG-TRAINER:", ignoreCase = true) ->
                trimmed.substringAfter(":").trim().uppercase()
            else -> trimmed.uppercase()
        }
    }

    fun buildInviteQrPayload(inviteCode: String): String = "BG-TRAINER:${inviteCode.trim().uppercase()}"

    fun parseUserAccount(doc: DocumentSnapshot): UserAccountDoc? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val rawRoleField = data["role"] as? String
        val rawAccountType = data["accountType"] as? String
        val roleRaw = (rawRoleField ?: rawAccountType)?.uppercase() ?: "MEMBER"
        val role = enumValues<AppRole>().firstOrNull { it.name == roleRaw } ?: AppRole.MEMBER
        return UserAccountDoc(
            userId = (data["userId"] as? String) ?: doc.id,
            name = (data["name"] as? String).orEmpty(),
            email = (data["email"] as? String).orEmpty(),
            accountType = role,
            role = rawRoleField ?: role.name.lowercase(),
            status = (data["status"] as? String) ?: "active",
            trainerInviteCode = data["trainerInviteCode"] as? String,
            trainerTitle = data["trainerTitle"] as? String,
            specialization = data["specialization"] as? String,
            fitnessGoal = data["fitnessGoal"] as? String,
            fitnessLevel = data["fitnessLevel"] as? String,
            birthdate = data["birthdate"] as? String,
            calculatedAge = (data["calculatedAge"] as? Number)?.toInt()
                ?: (data["birthdate"] as? String)?.let { ManilaDateTime.calculateAge(it) },
            memberType = (data["memberType"] as? String) ?: "Regular",
            isVerified = (data["isVerified"] as? Boolean) ?: false,
            verifiedBy = data["verifiedBy"] as? String,
            verifiedAtMs = ManilaDateTime.parseFirestoreUtcMillis(data["verifiedAt"])
                ?: (data["verifiedAtMs"] as? Number)?.toLong(),
            discountPercent = (data["discountPercent"] as? Number)?.toDouble() ?: 0.0,
            discountAppliesToBilling = (data["discountAppliesToBilling"] as? Boolean) ?: false,
            discountAppliesToPackages = (data["discountAppliesToPackages"] as? Boolean) ?: false,
            discountAppliesToSessionFees = (data["discountAppliesToSessionFees"] as? Boolean) ?: false
        )
    }

    fun parseTrainerInvite(doc: DocumentSnapshot): TrainerInviteDoc? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return TrainerInviteDoc(
            inviteCode = doc.id,
            trainerId = (data["trainerId"] as? String).orEmpty(),
            trainerName = (data["trainerName"] as? String).orEmpty(),
            trainerTitle = data["trainerTitle"] as? String,
            specialization = data["specialization"] as? String,
            active = (data["active"] as? Boolean) ?: false
        )
    }

    fun parseLink(doc: DocumentSnapshot): TrainerMemberLink? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val statusRaw = (data["status"] as? String)?.uppercase() ?: "ACTIVE"
        val status = enumValues<LinkStatus>().firstOrNull { it.name == statusRaw } ?: LinkStatus.ACTIVE
        val mStatusRaw = (data["memberStatus"] as? String)?.uppercase() ?: "ACTIVE"
        val mStatus = enumValues<MemberStatus>().firstOrNull { it.name == mStatusRaw } ?: MemberStatus.ACTIVE
        val reqRaw = (data["requestedBy"] as? String)?.uppercase() ?: "MEMBER"
        val reqRole = enumValues<AppRole>().firstOrNull { it.name == reqRaw } ?: AppRole.MEMBER
        return TrainerMemberLink(
            memberId = (data["memberId"] as? String) ?: doc.id,
            trainerId = (data["trainerId"] as? String).orEmpty(),
            memberName = (data["memberName"] as? String).orEmpty(),
            trainerName = (data["trainerName"] as? String).orEmpty(),
            trainerTitle = data["trainerTitle"] as? String,
            inviteCode = data["inviteCode"] as? String,
            status = status,
            memberStatus = mStatus,
            requestedBy = reqRole,
            shareWorkouts = (data["shareWorkouts"] as? Boolean) ?: false,
            shareStreakXp = (data["shareStreakXp"] as? Boolean) ?: false,
            shareBodyStats = (data["shareBodyStats"] as? Boolean) ?: false,
            agreedShareWorkouts = (data["agreedShareWorkouts"] as? Boolean) ?: false,
            agreedShareStreakXp = (data["agreedShareStreakXp"] as? Boolean) ?: false,
            agreedShareBodyStats = (data["agreedShareBodyStats"] as? Boolean) ?: false,
            fitnessGoal = data["fitnessGoal"] as? String,
            fitnessLevel = data["fitnessLevel"] as? String,
            lastActiveAtMs = doc.getTimestamp("lastActiveAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            acceptedAtMs = doc.getTimestamp("acceptedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        )
    }

    fun parseWorkoutsSummary(doc: DocumentSnapshot): SharedWorkoutsSummary? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return SharedWorkoutsSummary(
            memberId = (data["memberId"] as? String).orEmpty(),
            totalWorkouts = (data["totalWorkouts"] as? Number)?.toInt() ?: 0,
            completedThisWeek = (data["completedThisWeek"] as? Number)?.toInt() ?: 0,
            totalCaloriesBurned = (data["totalCaloriesBurned"] as? Number)?.toInt() ?: 0,
            totalMinutes = (data["totalMinutes"] as? Number)?.toInt() ?: 0,
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        )
    }

    fun parseWorkoutItem(doc: DocumentSnapshot): SharedWorkoutItem? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val statusRaw = (data["status"] as? String).orEmpty()
        if (statusRaw.equals("cancelled", ignoreCase = true)) return null
        return SharedWorkoutItem(
            id = doc.id,
            memberId = (data["memberId"] as? String).orEmpty(),
            planName = (data["planName"] as? String).orEmpty(),
            category = (data["category"] as? String).orEmpty(),
            completedAtMs = ManilaDateTime.parseFirestoreUtcMillis(data["completedAtMs"]) ?: 0L,
            durationMinutes = (data["durationMinutes"] as? Number)?.toInt() ?: 0,
            caloriesBurned = (data["caloriesBurned"] as? Number)?.toInt() ?: 0,
            completed = (data["completed"] as? Boolean) ?: true
        )
    }

    fun parseStreakXp(doc: DocumentSnapshot): SharedStreakXp? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return SharedStreakXp(
            memberId = (data["memberId"] as? String).orEmpty(),
            streakDays = (data["streakDays"] as? Number)?.toInt() ?: 0,
            level = (data["level"] as? Number)?.toInt() ?: 1,
            xpPoints = (data["xpPoints"] as? Number)?.toInt() ?: 0,
            fitnessGoal = data["fitnessGoal"] as? String,
            fitnessLevel = data["fitnessLevel"] as? String,
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        )
    }

    fun parseBodyStatsSummary(doc: DocumentSnapshot): SharedBodyStatsSummary? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return SharedBodyStatsSummary(
            memberId = (data["memberId"] as? String).orEmpty(),
            heightCm = (data["heightCm"] as? Number)?.toFloat() ?: 0f,
            weightKg = (data["weightKg"] as? Number)?.toFloat() ?: 0f,
            bmi = (data["bmi"] as? Number)?.toFloat() ?: 0f,
            bmiCategory = (data["bmiCategory"] as? String).orEmpty(),
            chestCm = (data["chestCm"] as? Number)?.toFloat(),
            armCm = (data["armCm"] as? Number)?.toFloat(),
            waistCm = (data["waistCm"] as? Number)?.toFloat(),
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        )
    }

    fun parseMeasurementItem(doc: DocumentSnapshot): SharedMeasurementItem? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return SharedMeasurementItem(
            id = doc.id,
            memberId = (data["memberId"] as? String).orEmpty(),
            timestampMs = ManilaDateTime.parseFirestoreUtcMillis(data["timestampMs"]) ?: 0L,
            weightKg = (data["weightKg"] as? Number)?.toFloat() ?: 0f,
            heightCm = (data["heightCm"] as? Number)?.toFloat() ?: 0f,
            bmi = (data["bmi"] as? Number)?.toFloat() ?: 0f,
            bmiCategory = (data["bmiCategory"] as? String).orEmpty(),
            chestCm = (data["chestCm"] as? Number)?.toFloat(),
            armCm = (data["armCm"] as? Number)?.toFloat(),
            waistCm = (data["waistCm"] as? Number)?.toFloat(),
            notes = (data["notes"] as? String).orEmpty()
        )
    }

    fun parseChatThread(doc: DocumentSnapshot): ChatThread? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        return ChatThread(
            pairId = doc.id,
            trainerId = (data["trainerId"] as? String).orEmpty(),
            memberId = (data["memberId"] as? String).orEmpty(),
            trainerName = (data["trainerName"] as? String).orEmpty(),
            memberName = (data["memberName"] as? String).orEmpty(),
            lastMessage = data["lastMessage"] as? String,
            lastSenderId = data["lastSenderId"] as? String,
            lastMessageAtMs = doc.getTimestamp("lastMessageAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            trainerUnreadCount = (data["trainerUnreadCount"] as? Number)?.toInt() ?: 0,
            memberUnreadCount = (data["memberUnreadCount"] as? Number)?.toInt() ?: 0
        )
    }

    fun parseChatMessage(doc: DocumentSnapshot): ChatMessage? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val roleRaw = (data["senderRole"] as? String)?.uppercase() ?: "MEMBER"
        val role = enumValues<AppRole>().firstOrNull { it.name == roleRaw } ?: AppRole.MEMBER
        return ChatMessage(
            id = doc.id,
            trainerId = (data["trainerId"] as? String).orEmpty(),
            memberId = (data["memberId"] as? String).orEmpty(),
            senderId = (data["senderId"] as? String).orEmpty(),
            senderRole = role,
            senderName = (data["senderName"] as? String).orEmpty(),
            text = (data["text"] as? String).orEmpty(),
            createdAtMs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
                ?: System.currentTimeMillis()
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun parseAssignedPlan(doc: DocumentSnapshot): AssignedPlanDoc? {
        if (!doc.exists()) return null
        val data = doc.data ?: return null
        val rawExercises = (data["exercises"] as? List<*>) ?: emptyList<Any?>()
        val parsedExercises = rawExercises.mapNotNull { item ->
            val map = item as? Map<String, Any?> ?: return@mapNotNull null
            val exId = (map["exerciseId"] as? String).orEmpty()
            val exName = (map["exerciseName"] as? String).orEmpty()
            if (exId.isBlank()) return@mapNotNull null
            val type = (map["type"] as? String) ?: "REPS"
            val count = (map["count"] as? Number)?.toInt() ?: (map["repsOrDuration"] as? Number)?.toInt() ?: 10
            val rest = (map["restSeconds"] as? Number)?.toInt() ?: 30
            AssignedPlanExercisePayload(
                exerciseId = exId,
                exerciseName = exName,
                type = type,
                count = count,
                restSeconds = rest
            )
        }
        val rawCompleted = (data["completed"] as? Boolean) ?: false
        val statusRaw = (data["status"] as? String)?.lowercase()
            ?: if (rawCompleted) "completed" else "scheduled"
        val isCancelled = statusRaw == "cancelled"
        val parsedStartMs = ManilaDateTime.parseFirestoreUtcMillis(data["startDateEpochMs"])
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: 0L
        val parsedCancelledAtMs = doc.getTimestamp("cancelledAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: ManilaDateTime.parseFirestoreUtcMillis(data["cancelledAt"])
        val parsedDeletedAtMs = doc.getTimestamp("deletedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: ManilaDateTime.parseFirestoreUtcMillis(data["deletedAt"])
        return AssignedPlanDoc(
            id = doc.id,
            trainerId = (data["trainerId"] as? String).orEmpty(),
            trainerName = (data["trainerName"] as? String).orEmpty(),
            memberId = (data["memberId"] as? String).orEmpty(),
            planTitle = (data["planTitle"] as? String).orEmpty(),
            planDescription = (data["planDescription"] as? String).orEmpty(),
            category = (data["category"] as? String) ?: "Full Body",
            difficulty = (data["difficulty"] as? String) ?: "Intermediate",
            estimatedDurationMinutes = (data["estimatedDurationMinutes"] as? Number)?.toInt() ?: 20,
            note = data["note"] as? String,
            startDateEpochMs = parsedStartMs,
            exercises = parsedExercises,
            completed = !isCancelled && rawCompleted,
            completedAtMs = if (isCancelled) null else doc.getTimestamp("completedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time,
            status = statusRaw,
            excludeFromBilling = (data["excludeFromBilling"] as? Boolean) ?: isCancelled,
            cancelledReason = data["cancelledReason"] as? String,
            cancelledByUid = data["cancelledByUid"] as? String,
            cancelledByName = data["cancelledByName"] as? String,
            cancelledByRole = data["cancelledByRole"] as? String,
            cancelledAtMs = parsedCancelledAtMs,
            deletedAtMs = parsedDeletedAtMs,
            deletedByUid = data["deletedByUid"] as? String,
            deletedByName = data["deletedByName"] as? String,
            deletedByRole = data["deletedByRole"] as? String,
            updatedAtMs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        )
    }
}
