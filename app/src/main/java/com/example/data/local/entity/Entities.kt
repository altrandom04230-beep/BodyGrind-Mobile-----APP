package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String, // Full Body, Abs, Chest, Arm, Leg, Shoulder & Back, Warm-Up, Bedtime, Fat-Burning
    val targetMuscles: String,
    val difficulty: String, // Beginner, Intermediate, Advanced
    val defaultType: String, // TIME, REPS
    val defaultCount: Int, // seconds or reps
    val instructions: String,
    val tips: String,
    val caloriesPerMinute: Float = 7.5f,
    val animationType: String, // PUSH_UP, SQUAT, PLANK, JUMPING_JACKS, etc.
    val isFavorite: Boolean = false,
    val primaryMuscles: String = "",
    val secondaryMuscles: String = "",
    val educationalDescription: String = "",
    val functionalBenefit: String = ""
)

@Entity(tableName = "workout_plans")
data class WorkoutPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val category: String,
    val difficulty: String,
    val estimatedDurationMinutes: Int,
    val isCustom: Boolean = false,
    val isChallenge: Boolean = false,
    val totalDays: Int = 1,
    val currentDay: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val targetMusclesDescription: String = "",
    val educationalOverview: String = "",
    val assignedByTrainerId: String? = null,
    val assignedByTrainerName: String? = null,
    val trainerNote: String? = null,
    val startDateEpochMs: Long? = null,
    val remoteAssignmentId: String? = null,
    val isCompletedByMember: Boolean = false,
    val status: String = "ACTIVE", // ACTIVE, COMPLETED, CANCELLED
    val cancelledReason: String? = null,
    val cancelledByUid: String? = null,
    val cancelledByName: String? = null,
    val cancelledByRole: String? = null,
    val cancelledAtUtcMs: Long? = null,
    val excludeFromBilling: Boolean = false
)

@Entity(tableName = "workout_plan_exercises")
data class WorkoutPlanExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val type: String, // TIME, REPS
    val count: Int,
    val restSeconds: Int = 20
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val sessionId: Long = 0,
    val planId: String,
    val planName: String,
    val category: String,
    val startedAt: Long,
    val completedAt: Long,
    val durationSeconds: Int,
    val caloriesBurned: Int,
    val exercisesCompleted: Int,
    val totalExercises: Int,
    val isFinished: Boolean,
    val xpAwarded: Boolean = false,
    val status: String = "COMPLETED", // SCHEDULED, COMPLETED, CANCELLED
    val cancelledReason: String? = null,
    val cancelledByUid: String? = null,
    val cancelledByName: String? = null,
    val cancelledByRole: String? = null,
    val cancelledAtUtcMs: Long? = null,
    val excludeFromBilling: Boolean = false
)

@Entity(tableName = "body_measurements")
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val weightKg: Float,
    val heightCm: Float,
    val chestCm: Float? = null,
    val armCm: Float? = null,
    val waistCm: Float? = null,
    val bmi: Float,
    val bmiCategory: String,
    val notes: String = "",
    val weekKey: String? = null,
    val weekNumber: Int? = null,
    val memberName: String? = null,
    val memberAge: Int? = null
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "Fitness Athlete",
    val age: Int = 26,
    val gender: String = "Prefer not to say",
    val fitnessGoal: String = "Build Muscle",
    val fitnessLevel: String = "Beginner",
    val preferredDuration: Int = 15,
    val weightKg: Float = 70f,
    val heightCm: Float = 175f,
    val useMetric: Boolean = true,
    val soundEnabled: Boolean = true,
    val voiceCuesEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val reminderDaysMask: Int = 127,
    val onboardingCompleted: Boolean = false,
    val darkThemeMode: String = "LIGHT", // "DARK", "LIGHT", "SYSTEM"
    val xpPoints: Int = 0,
    val level: Int = 1,
    val isPrivate: Boolean = false,
    val accentColorHex: String = "#E50914",
    val bodyDiagramGender: String = "MALE", // "MALE", "FEMALE"
    val exerciseDrawingGender: String = "MALE", // "MALE", "FEMALE"
    val gymCheckinEnabled: Boolean = true,
    val aiCoachEnabled: Boolean = false,
    val birthdate: String? = null, // ISO "yyyy-MM-dd"
    val memberType: String = "Regular", // Regular, Student, Senior Citizen, PWD
    val isVerified: Boolean = false,
    val discountPercent: Double = 0.0,
    val discountAppliesToBilling: Boolean = false,
    val discountAppliesToPackages: Boolean = false,
    val discountAppliesToSessionFees: Boolean = false
) {
    /**
     * Calculates age dynamically in Asia/Manila (UTC+8) from [birthdate].
     * Falls back to legacy [age] only if birthdate is not yet configured.
     */
    fun calculateAge(today: java.time.LocalDate = com.example.utils.ManilaDateTime.todayManila()): Int {
        if (birthdate.isNullOrBlank()) return age
        return com.example.utils.ManilaDateTime.calculateAge(birthdate, today, defaultAge = age)
    }

    fun hasBirthdate(): Boolean = !birthdate.isNullOrBlank()

    fun isSeniorCitizenEligible(today: java.time.LocalDate = com.example.utils.ManilaDateTime.todayManila()): Boolean {
        return calculateAge(today) >= 60
    }

    /**
     * Calculates daily calorie target using the Mifflin-St Jeor formula with calculated age.
     */
    fun calculateDailyCalorieTarget(today: java.time.LocalDate = com.example.utils.ManilaDateTime.todayManila()): Int {
        val effectiveAge = calculateAge(today).coerceIn(10, 120)
        val wKg = weightKg.coerceIn(20f, 300f)
        val hCm = heightCm.coerceIn(100f, 250f)
        val isFemale = gender.equals("Female", ignoreCase = true)
        val baseBmr = (10f * wKg) + (6.25f * hCm) - (5f * effectiveAge) + (if (isFemale) -161f else 5f)
        val activityMultiplier = 1.35f
        val maintenanceKcal = (baseBmr * activityMultiplier).toInt()
        val goalAdjusted = when {
            fitnessGoal.contains("Maintain", ignoreCase = true) -> maintenanceKcal
            fitnessGoal.contains("Build", ignoreCase = true) || fitnessGoal.contains("Muscle", ignoreCase = true) -> maintenanceKcal + 300
            fitnessGoal.contains("Lose", ignoreCase = true) || fitnessGoal.contains("Fat", ignoreCase = true) || fitnessGoal.contains("Weight", ignoreCase = true) -> maintenanceKcal - 400
            else -> maintenanceKcal
        }
        return goalAdjusted.coerceIn(1200, 4500)
    }
}

@Entity(tableName = "badges")
data class BadgeEntity(
    @PrimaryKey val badgeId: String,
    val title: String,
    val description: String,
    val category: String, // Streak, Workout, Challenge, Milestone
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val progressCurrent: Int = 0,
    val progressTarget: Int = 1,
    val iconName: String
)

@Entity(tableName = "workout_schedules")
data class WorkoutScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayOfWeek: Int, // 1 to 7 (1=Monday, 7=Sunday)
    val planId: String,
    val planName: String,
    val timeHour: Int = 8,
    val timeMinute: Int = 0,
    val isEnabled: Boolean = true
)

@Entity(tableName = "body_scans")
data class BodyScanEntity(
    @PrimaryKey val scanId: String,
    val userId: Int = 1,
    val scanDate: Long = System.currentTimeMillis(),
    val cameraFacing: String = "FRONT", // "FRONT", "BACK"
    val poseDetectionStatus: String = "COMPLETE", // "WAITING", "DETECTING", "DETECTED", "ADJUST_POSITION", "COMPLETE"
    val landmarkConfidence: Float = 0.85f,
    val scanNotes: String = "",
    val bodyVisibilityStatus: String = "FULL_BODY" // "FULL_BODY", "TOO_CLOSE", "TOO_FAR", "HEAD_OUT", "FEET_OUT", "PARTIALLY_BLOCKED"
)

@Entity(tableName = "bmi_records")
data class BmiRecordEntity(
    @PrimaryKey val bmiRecordId: String,
    val userId: Int = 1,
    val heightCm: Float,
    val weightKg: Float,
    val bmiValue: Float,
    val bmiCategory: String, // "Underweight", "Normal weight", "Overweight", "Obesity"
    val assessmentDate: Long = System.currentTimeMillis(),
    val sourceType: String = "MANUAL", // "MANUAL"
    val linkedScanId: String? = null
)

@Entity(tableName = "body_pose_landmarks")
data class BodyPoseLandmarkEntity(
    @PrimaryKey(autoGenerate = true) val landmarkId: Long = 0,
    val scanId: String,
    val landmarkName: String,
    val normalizedX: Float,
    val normalizedY: Float,
    val normalizedZ: Float? = null,
    val visibilityConfidence: Float? = null
)

@Entity(
    tableName = "pending_sync_ops",
    indices = [androidx.room.Index(value = ["opKey"], unique = true)]
)
data class PendingSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val opKey: String,
    val opType: String,
    val payloadJson: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val lastAttemptMs: Long? = null,
    val lastError: String? = null
)

