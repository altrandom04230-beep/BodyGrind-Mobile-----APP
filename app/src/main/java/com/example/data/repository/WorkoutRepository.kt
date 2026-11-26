package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

import android.content.Context
import com.example.ui.calendar.reminder.CalendarEventReminderScheduler

class WorkoutRepository(
    private val database: AppDatabase,
    private val context: Context? = null
) {

    private val exerciseDao = database.exerciseDao()
    private val workoutPlanDao = database.workoutPlanDao()
    private val workoutSessionDao = database.workoutSessionDao()
    private val bodyMeasurementDao = database.bodyMeasurementDao()
    private val userProfileDao = database.userProfileDao()
    private val badgeDao = database.badgeDao()
    private val scheduleDao = database.workoutScheduleDao()
    private val pendingSyncDao = database.pendingSyncDao()

    // Exercises
    fun getAllExercises(): Flow<List<ExerciseEntity>> = exerciseDao.getAllExercises()
    fun getExercisesByCategory(category: String): Flow<List<ExerciseEntity>> = exerciseDao.getExercisesByCategory(category)
    fun getFavoriteExercises(): Flow<List<ExerciseEntity>> = exerciseDao.getFavoriteExercises()
    suspend fun getExerciseById(id: String): ExerciseEntity? = exerciseDao.getExerciseById(id)
    suspend fun toggleFavorite(id: String, currentVal: Boolean) = exerciseDao.setFavorite(id, !currentVal)

    // Plans
    fun getAllPlans(): Flow<List<WorkoutPlanEntity>> = workoutPlanDao.getAllPlans()
    fun getPredefinedPlans(): Flow<List<WorkoutPlanEntity>> = workoutPlanDao.getPredefinedPlans()
    fun getCustomPlans(): Flow<List<WorkoutPlanEntity>> = workoutPlanDao.getCustomPlans()
    fun getPlansByCategory(category: String): Flow<List<WorkoutPlanEntity>> = workoutPlanDao.getPlansByCategory(category)
    fun getChallenges(): Flow<List<WorkoutPlanEntity>> = workoutPlanDao.getChallenges()
    suspend fun getPlanById(id: String): WorkoutPlanEntity? = workoutPlanDao.getPlanById(id)
    fun getPlanExercises(planId: String): Flow<List<WorkoutPlanExerciseEntity>> = workoutPlanDao.getPlanExercises(planId)
    suspend fun getPlanExercisesSync(planId: String): List<WorkoutPlanExerciseEntity> = workoutPlanDao.getPlanExercisesSync(planId)

    // Custom Plan Builder
    suspend fun createCustomPlan(
        name: String,
        description: String,
        category: String,
        difficulty: String,
        exercises: List<WorkoutPlanExerciseEntity>
    ): String {
        val planId = "custom_${System.currentTimeMillis()}"
        val totalSecs = exercises.sumOf {
            val dur = if (it.type == "TIME") it.count else it.count * 3
            dur + it.restSeconds
        }
        val estimatedMins = maxOf(3, totalSecs / 60)

        val plan = WorkoutPlanEntity(
            id = planId,
            name = name,
            description = description,
            category = category,
            difficulty = difficulty,
            estimatedDurationMinutes = estimatedMins,
            isCustom = true,
            createdAt = System.currentTimeMillis()
        )
        workoutPlanDao.insertPlan(plan)

        val exercisesWithPlanId = exercises.mapIndexed { index, ex ->
            ex.copy(id = 0, planId = planId, orderIndex = index)
        }
        workoutPlanDao.insertPlanExercises(exercisesWithPlanId)
        return planId
    }

    suspend fun deleteCustomPlan(planId: String) {
        workoutPlanDao.deletePlanExercises(planId)
        workoutPlanDao.deletePlanById(planId)
    }

    // Sessions & History
    fun getCompletedSessions(): Flow<List<WorkoutSessionEntity>> = workoutSessionDao.getAllCompletedSessions()
    fun getCancelledSessions(): Flow<List<WorkoutSessionEntity>> = workoutSessionDao.getCancelledSessions()
    fun getRecentSessions(limit: Int): Flow<List<WorkoutSessionEntity>> = workoutSessionDao.getRecentSessions(limit)
    fun getTotalWorkoutsCount(): Flow<Int> = workoutSessionDao.getTotalWorkoutsCount()
    fun getTotalDurationSeconds(): Flow<Int?> = workoutSessionDao.getTotalDurationSeconds()
    fun getTotalCaloriesBurned(): Flow<Int?> = workoutSessionDao.getTotalCaloriesBurned()
    suspend fun deleteSessionById(id: Long) = workoutSessionDao.deleteSessionById(id)

    data class WorkoutCompletionResult(
        val sessionId: Long,
        val earnedXp: Int,
        val newTotalXp: Int,
        val newLevel: Int,
        val unlockedBadgeTitle: String?
    )

    // Complete a workout session & trigger gamification updates atomically and idempotently
    suspend fun completeWorkoutSession(
        planId: String,
        planName: String,
        category: String,
        startedAt: Long,
        completedAt: Long,
        durationSeconds: Int,
        caloriesBurned: Int,
        exercisesCompleted: Int,
        totalExercises: Int
    ): WorkoutCompletionResult {
        return database.withTransaction {
            val plan = workoutPlanDao.getPlanById(planId)
            // A cancelled plan/session must NEVER be treated as completed.
            if (plan != null && plan.status.equals("CANCELLED", ignoreCase = true)) {
                val currentProfile = userProfileDao.getUserProfileSync() ?: UserProfileEntity()
                return@withTransaction WorkoutCompletionResult(
                    sessionId = -1L,
                    earnedXp = 0,
                    newTotalXp = currentProfile.xpPoints,
                    newLevel = currentProfile.level,
                    unlockedBadgeTitle = null
                )
            }

            val session = WorkoutSessionEntity(
                planId = planId,
                planName = planName,
                category = category,
                startedAt = startedAt,
                completedAt = completedAt,
                durationSeconds = durationSeconds,
                caloriesBurned = caloriesBurned,
                exercisesCompleted = exercisesCompleted,
                totalExercises = totalExercises,
                isFinished = true,
                xpAwarded = false,
                status = "COMPLETED",
                excludeFromBilling = false
            )
            val sessionId = workoutSessionDao.insertSession(session)
            val rewardResult = awardSessionRewardsInternal(sessionId)

            // If this was a trainer-assigned workout plan, mark completed locally and queue remote sync
            val assignmentId = plan?.remoteAssignmentId
            if (plan != null && !assignmentId.isNullOrBlank() && !plan.status.equals("CANCELLED", ignoreCase = true)) {
                workoutPlanDao.markAssignedPlanCompleted(planId, true)
                val payload = org.json.JSONObject().apply {
                    put("assignmentId", assignmentId)
                    put("completed", true)
                    put("completedAtMs", completedAt)
                }.toString()
                pendingSyncDao.upsert(
                    PendingSyncEntity(
                        opKey = "COMPLETE_ASSIGNED_PLAN:$assignmentId",
                        opType = "COMPLETE_ASSIGNED_PLAN",
                        payloadJson = payload,
                        createdAtMs = completedAt
                    )
                )
            }

            rewardResult
        }
    }

    suspend fun awardSessionRewardsIfNeeded(sessionId: Long): WorkoutCompletionResult {
        return database.withTransaction {
            awardSessionRewardsInternal(sessionId)
        }
    }

    private suspend fun awardSessionRewardsInternal(sessionId: Long): WorkoutCompletionResult {
        val session = workoutSessionDao.getSessionById(sessionId)
        val existingProfile = userProfileDao.getUserProfileSync()
        val currentProfile = existingProfile ?: UserProfileEntity()

        if (session == null || !session.isFinished || session.status.equals("CANCELLED", ignoreCase = true) || session.xpAwarded) {
            return WorkoutCompletionResult(
                sessionId = sessionId,
                earnedXp = 0,
                newTotalXp = currentProfile.xpPoints,
                newLevel = currentProfile.level,
                unlockedBadgeTitle = null
            )
        }

        // Guard against duplicate XP awards for the same session
        workoutSessionDao.markSessionXpAwarded(sessionId)

        val earnedXp = com.example.data.gamification.GamificationRules.calculateWorkoutXp(session.durationSeconds)
        val newXp = (currentProfile.xpPoints + earnedXp).coerceAtLeast(currentProfile.xpPoints)
        val newLevel = maxOf(
            currentProfile.level,
            com.example.data.gamification.GamificationRules.levelForXp(newXp)
        )

        if (existingProfile == null) {
            userProfileDao.insertOrUpdateProfile(
                currentProfile.copy(xpPoints = newXp, level = newLevel)
            )
        } else {
            userProfileDao.updateXpAndLevel(newXp, newLevel)
        }

        val unlockedBadgeTitle = checkAndUpdateBadges(session.completedAt)

        return WorkoutCompletionResult(
            sessionId = sessionId,
            earnedXp = earnedXp,
            newTotalXp = newXp,
            newLevel = newLevel,
            unlockedBadgeTitle = unlockedBadgeTitle
        )
    }

    private suspend fun checkAndUpdateBadges(completedAt: Long): String? {
        val timestamps = workoutSessionDao.getAllCompletedTimestamps()
        val totalCount = timestamps.size
        val newlyUnlockedTitles = mutableListOf<String>()

        // 1. First Workout
        if (totalCount >= 1) {
            unlockBadge("first_workout", 1)?.let { newlyUnlockedTitles.add(it) }
        }
        // 2. Workout count badges
        if (totalCount >= 5) {
            unlockBadge("workout_5", 5)?.let { newlyUnlockedTitles.add(it) }
        } else {
            updateBadgeProgress("workout_5", totalCount, neverDecrease = true)
        }
        if (totalCount >= 20) {
            unlockBadge("workout_20", 20)?.let { newlyUnlockedTitles.add(it) }
        } else {
            updateBadgeProgress("workout_20", totalCount, neverDecrease = true)
        }

        // 3. Streak badges (using DST-safe local calendar streak calculation)
        val streakSummary = com.example.data.gamification.GamificationRules.computeStreakSummary(
            completedTimestamps = timestamps,
            nowMillis = maxOf(System.currentTimeMillis(), completedAt)
        )
        val streak = streakSummary.currentStreak
        if (streak >= 3) {
            unlockBadge("streak_3", 3)?.let { newlyUnlockedTitles.add(it) }
        } else {
            updateBadgeProgress("streak_3", streak, neverDecrease = false)
        }
        if (streak >= 7) {
            unlockBadge("streak_7", 7)?.let { newlyUnlockedTitles.add(it) }
        } else {
            updateBadgeProgress("streak_7", streak, neverDecrease = false)
        }

        // 4. Custom plan badge
        if (workoutSessionDao.hasCompletedCustomWorkoutSync()) {
            unlockBadge("custom_creator", 1)?.let { newlyUnlockedTitles.add(it) }
        }

        // 5. Calories burned badge
        val totalCalories = workoutSessionDao.getTotalCaloriesBurnedSync()
        if (totalCalories >= 500) {
            unlockBadge("calories_500", 500)?.let { newlyUnlockedTitles.add(it) }
        } else {
            updateBadgeProgress("calories_500", totalCalories, neverDecrease = true)
        }

        // 6. Abs Master (derived idempotently from completed Abs sessions)
        val absCount = workoutSessionDao.getCompletedAbsWorkoutsCountSync()
        if (absCount >= 3) {
            unlockBadge("abs_master", 3)?.let { newlyUnlockedTitles.add(it) }
        } else {
            updateBadgeProgress("abs_master", absCount, neverDecrease = true)
        }

        return newlyUnlockedTitles.firstOrNull()
    }

    private suspend fun unlockBadge(badgeId: String, targetVal: Int): String? {
        val badge = badgeDao.getBadgeById(badgeId) ?: return null
        if (!badge.unlocked) {
            badgeDao.updateBadge(
                badge.copy(
                    unlocked = true,
                    unlockedAt = System.currentTimeMillis(),
                    progressCurrent = maxOf(targetVal, badge.progressTarget)
                )
            )
            return badge.title
        }
        return null
    }

    private suspend fun updateBadgeProgress(badgeId: String, progress: Int, neverDecrease: Boolean) {
        val badge = badgeDao.getBadgeById(badgeId) ?: return
        if (!badge.unlocked) {
            val clamped = progress.coerceIn(0, badge.progressTarget)
            val nextProgress = if (neverDecrease) maxOf(badge.progressCurrent, clamped) else clamped
            if (nextProgress != badge.progressCurrent) {
                badgeDao.updateBadge(badge.copy(progressCurrent = nextProgress))
            }
        }
    }

    suspend fun calculateCurrentStreak(): Int {
        val timestamps = workoutSessionDao.getAllCompletedTimestamps()
        return com.example.data.gamification.GamificationRules.computeStreakSummary(timestamps).currentStreak
    }

    suspend fun syncAccountIdentityIfDefault(accountName: String?, accountGoal: String? = null) {
        val cleanName = accountName?.trim().orEmpty()
        val cleanGoal = accountGoal?.trim().orEmpty()
        if (cleanName.isBlank() && cleanGoal.isBlank()) return

        database.withTransaction {
            val existing = userProfileDao.getUserProfileSync()
            val current = existing ?: UserProfileEntity()
            val defaultName = com.example.data.gamification.GamificationRules.DEFAULT_DISPLAY_NAME
            val defaultGoal = com.example.data.gamification.GamificationRules.DEFAULT_FITNESS_GOAL

            val updatedName = if (cleanName.isNotBlank() && (current.name.isBlank() || current.name == defaultName)) {
                cleanName
            } else {
                current.name
            }
            val updatedGoal = if (cleanGoal.isNotBlank() && (current.fitnessGoal.isBlank() || current.fitnessGoal == defaultGoal)) {
                cleanGoal
            } else {
                current.fitnessGoal
            }

            if (existing == null || updatedName != current.name || updatedGoal != current.fitnessGoal) {
                userProfileDao.insertOrUpdateProfile(
                    current.copy(name = updatedName, fitnessGoal = updatedGoal)
                )
            }
        }
    }

    // Body Measurements & BMI
    fun getAllMeasurements(): Flow<List<BodyMeasurementEntity>> = bodyMeasurementDao.getAllMeasurements()
    fun getMeasurementsSince(sinceTimestamp: Long): Flow<List<BodyMeasurementEntity>> = bodyMeasurementDao.getMeasurementsSince(sinceTimestamp)
    fun getLatestMeasurement(): Flow<BodyMeasurementEntity?> = bodyMeasurementDao.getLatestMeasurement()

    private fun computeBmiAndCategory(weightKg: Float, heightCm: Float): Pair<Float, String> {
        val heightM = heightCm / 100f
        val bmi = if (heightM > 0f) weightKg / (heightM * heightM) else 22f
        val category = when {
            bmi < 18.5f -> "Underweight"
            bmi < 25.0f -> "Normal weight"
            bmi < 30.0f -> "Overweight"
            else -> "Obese"
        }
        return Pair(bmi, category)
    }

    suspend fun logMeasurement(
        weightKg: Float,
        heightCm: Float,
        waistCm: Float? = null,
        chestCm: Float? = null,
        armCm: Float? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        require(weightKg > 0f) { "Weight must be greater than zero." }
        require(heightCm > 0f) { "Height must be greater than zero." }
        require(timestamp <= System.currentTimeMillis()) { "Measurement date cannot be in the future." }
        if (chestCm != null) require(chestCm > 0f) { "Chest measurement must be positive." }
        if (armCm != null) require(armCm > 0f) { "Arm measurement must be positive." }
        if (waistCm != null) require(waistCm > 0f) { "Waist measurement must be positive." }

        val (bmi, category) = computeBmiAndCategory(weightKg, heightCm)
        val entity = BodyMeasurementEntity(
            timestamp = timestamp,
            weightKg = weightKg,
            heightCm = heightCm,
            chestCm = chestCm,
            armCm = armCm,
            waistCm = waistCm,
            bmi = bmi,
            bmiCategory = category,
            notes = notes
        )
        // Also sync to UserProfile
        val profile = userProfileDao.getUserProfileSync() ?: UserProfileEntity()
        userProfileDao.insertOrUpdateProfile(profile.copy(weightKg = weightKg, heightCm = heightCm))
        return bodyMeasurementDao.insertMeasurement(entity)
    }

    /**
     * Single source of truth save for the Progress screen's weekly Member Information + Body Measurement form.
     * Atomically updates [UserProfileEntity] and creates or updates AT MOST ONE record for [timestamp]'s local week.
     * Past weekly records are never modified.
     */
    suspend fun saveWeeklyProgressRecord(
        memberName: String,
        memberAge: Int,
        weightKg: Float,
        heightCm: Float,
        chestCm: Float,
        armCm: Float,
        waistCm: Float,
        useMetric: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): Int {
        val cleanName = memberName.trim()
        require(cleanName.isNotEmpty()) { "Member name is required." }
        require(memberAge in com.example.data.progress.WeekDefinition.MIN_AGE..com.example.data.progress.WeekDefinition.MAX_AGE) {
            "Age must be between ${com.example.data.progress.WeekDefinition.MIN_AGE} and ${com.example.data.progress.WeekDefinition.MAX_AGE}."
        }
        require(weightKg in com.example.data.progress.WeekDefinition.MIN_WEIGHT_KG..com.example.data.progress.WeekDefinition.MAX_WEIGHT_KG) {
            "Weight must be between 20 and 300 kg."
        }
        require(heightCm in com.example.data.progress.WeekDefinition.MIN_HEIGHT_CM..com.example.data.progress.WeekDefinition.MAX_HEIGHT_CM) {
            "Height must be between 100 and 250 cm."
        }
        require(chestCm in com.example.data.progress.WeekDefinition.MIN_CHEST_CM..com.example.data.progress.WeekDefinition.MAX_CHEST_CM) {
            "Chest measurement must be between 30 and 250 cm."
        }
        require(armCm in com.example.data.progress.WeekDefinition.MIN_ARM_CM..com.example.data.progress.WeekDefinition.MAX_ARM_CM) {
            "Arm measurement must be between 10 and 100 cm."
        }
        require(waistCm in com.example.data.progress.WeekDefinition.MIN_WAIST_CM..com.example.data.progress.WeekDefinition.MAX_WAIST_CM) {
            "Waist measurement must be between 30 and 250 cm."
        }

        return database.withTransaction {
            val currentProfile = userProfileDao.getUserProfileSync() ?: UserProfileEntity()
            userProfileDao.insertOrUpdateProfile(
                currentProfile.copy(
                    name = cleanName,
                    age = memberAge,
                    weightKg = weightKg,
                    heightCm = heightCm,
                    useMetric = useMetric
                )
            )

            val weekKey = com.example.data.progress.WeekDefinition.weekKeyFor(timestamp)
            val (bmi, category) = computeBmiAndCategory(weightKg, heightCm)
            val existingThisWeek = bodyMeasurementDao.getMeasurementByWeekKey(weekKey)

            if (existingThisWeek != null) {
                val weekNum = existingThisWeek.weekNumber ?: ((bodyMeasurementDao.getMaxWeekNumber() ?: 0) + 1)
                bodyMeasurementDao.updateMeasurement(
                    existingThisWeek.copy(
                        timestamp = timestamp,
                        weightKg = weightKg,
                        heightCm = heightCm,
                        chestCm = chestCm,
                        armCm = armCm,
                        waistCm = waistCm,
                        bmi = bmi,
                        bmiCategory = category,
                        notes = "Week $weekNum: $cleanName",
                        weekKey = weekKey,
                        weekNumber = weekNum,
                        memberName = cleanName,
                        memberAge = memberAge
                    )
                )
                weekNum
            } else {
                val weekNum = (bodyMeasurementDao.getMaxWeekNumber() ?: 0) + 1
                bodyMeasurementDao.insertMeasurement(
                    BodyMeasurementEntity(
                        timestamp = timestamp,
                        weightKg = weightKg,
                        heightCm = heightCm,
                        chestCm = chestCm,
                        armCm = armCm,
                        waistCm = waistCm,
                        bmi = bmi,
                        bmiCategory = category,
                        notes = "Week $weekNum: $cleanName",
                        weekKey = weekKey,
                        weekNumber = weekNum,
                        memberName = cleanName,
                        memberAge = memberAge
                    )
                )
                weekNum
            }
        }
    }

    /**
     * Updates [UserProfileEntity] and, ONLY if weight or height changed, creates or updates
     * this week's weekly record so Profile and Progress never disagree.
     * Editing only name, age, gender, fitness level, goal, or preferences never creates a weekly record.
     */
    suspend fun updateProfileWithWeeklySync(
        updatedProfile: UserProfileEntity,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val cleanName = updatedProfile.name.trim()
        require(cleanName.isNotEmpty()) { "Member name is required." }
        require(updatedProfile.age in com.example.data.progress.WeekDefinition.MIN_AGE..com.example.data.progress.WeekDefinition.MAX_AGE) {
            "Age must be between ${com.example.data.progress.WeekDefinition.MIN_AGE} and ${com.example.data.progress.WeekDefinition.MAX_AGE}."
        }
        require(updatedProfile.weightKg in com.example.data.progress.WeekDefinition.MIN_WEIGHT_KG..com.example.data.progress.WeekDefinition.MAX_WEIGHT_KG) {
            "Weight must be between 20 and 300 kg."
        }
        require(updatedProfile.heightCm in com.example.data.progress.WeekDefinition.MIN_HEIGHT_CM..com.example.data.progress.WeekDefinition.MAX_HEIGHT_CM) {
            "Height must be between 100 and 250 cm."
        }

        val normalized = updatedProfile.copy(name = cleanName)
        database.withTransaction {
            val previous = userProfileDao.getUserProfileSync() ?: UserProfileEntity()
            userProfileDao.insertOrUpdateProfile(normalized)
            syncWeeklyRecordForBiometricsLocked(
                previousProfile = previous,
                updatedProfile = normalized,
                timestamp = timestamp
            )
        }
    }

    private suspend fun syncWeeklyRecordForBiometricsLocked(
        previousProfile: UserProfileEntity,
        updatedProfile: UserProfileEntity,
        timestamp: Long
    ) {
        val biometricsChanged =
            kotlin.math.abs(updatedProfile.weightKg - previousProfile.weightKg) > 0.001f ||
                kotlin.math.abs(updatedProfile.heightCm - previousProfile.heightCm) > 0.001f

        if (!biometricsChanged) return

        val weekKey = com.example.data.progress.WeekDefinition.weekKeyFor(timestamp)
        val (bmi, category) = computeBmiAndCategory(updatedProfile.weightKg, updatedProfile.heightCm)
        val existingThisWeek = bodyMeasurementDao.getMeasurementByWeekKey(weekKey)

        if (existingThisWeek != null) {
            bodyMeasurementDao.updateMeasurement(
                existingThisWeek.copy(
                    timestamp = timestamp,
                    weightKg = updatedProfile.weightKg,
                    heightCm = updatedProfile.heightCm,
                    bmi = bmi,
                    bmiCategory = category,
                    memberName = updatedProfile.name,
                    memberAge = updatedProfile.age
                )
            )
        } else {
            val latestWeekly = bodyMeasurementDao.getLatestWeeklyMeasurementSync()
            val nextWeekNum = (bodyMeasurementDao.getMaxWeekNumber() ?: 0) + 1
            bodyMeasurementDao.insertMeasurement(
                BodyMeasurementEntity(
                    timestamp = timestamp,
                    weightKg = updatedProfile.weightKg,
                    heightCm = updatedProfile.heightCm,
                    chestCm = latestWeekly?.chestCm ?: 96.0f,
                    armCm = latestWeekly?.armCm ?: 35.0f,
                    waistCm = latestWeekly?.waistCm ?: 82.0f,
                    bmi = bmi,
                    bmiCategory = category,
                    notes = "Week $nextWeekNum: ${updatedProfile.name}",
                    weekKey = weekKey,
                    weekNumber = nextWeekNum,
                    memberName = updatedProfile.name,
                    memberAge = updatedProfile.age
                )
            )
        }
    }

    suspend fun updateUseMetric(useMetric: Boolean) {
        database.withTransaction {
            val current = userProfileDao.getUserProfileSync() ?: UserProfileEntity()
            if (current.useMetric != useMetric) {
                userProfileDao.insertOrUpdateProfile(current.copy(useMetric = useMetric))
            }
        }
    }

    suspend fun migrateLegacyWeeklyRecordsIfNeeded() {
        val ctx = context ?: return
        val legacyRecords = com.example.data.progress.WeeklyProgressRepository(ctx).consumeUnmigratedUserRecords()
        if (legacyRecords.isEmpty()) return

        database.withTransaction {
            legacyRecords.sortedBy { it.timestamp }.forEach { rec ->
                if (bodyMeasurementDao.getMeasurementByWeekKey(rec.weekKey) == null) {
                    val (bmi, category) = computeBmiAndCategory(rec.weightKg, rec.heightCm)
                    bodyMeasurementDao.insertMeasurement(
                        BodyMeasurementEntity(
                            timestamp = rec.timestamp,
                            weightKg = rec.weightKg,
                            heightCm = rec.heightCm,
                            chestCm = rec.chestCm,
                            armCm = rec.armCm,
                            waistCm = rec.waistCm,
                            bmi = bmi,
                            bmiCategory = category,
                            notes = "Week ${rec.weekNumber}: ${rec.memberName}",
                            weekKey = rec.weekKey,
                            weekNumber = rec.weekNumber,
                            memberName = rec.memberName,
                            memberAge = rec.memberAge
                        )
                    )
                }
            }
        }
    }

    suspend fun deleteMeasurementById(id: Long) = bodyMeasurementDao.deleteMeasurementById(id)

    // Profile & Settings
    fun getUserProfile(): Flow<UserProfileEntity?> = userProfileDao.getUserProfile()
    suspend fun getUserProfileSync(): UserProfileEntity? = userProfileDao.getUserProfileSync()
    suspend fun updateProfile(profile: UserProfileEntity) = userProfileDao.insertOrUpdateProfile(profile)

    // Badges
    fun getAllBadges(): Flow<List<BadgeEntity>> = badgeDao.getAllBadges()

    // Schedules
    fun getAllSchedules(): Flow<List<WorkoutScheduleEntity>> = scheduleDao.getAllSchedules()
    suspend fun updateSchedule(schedule: WorkoutScheduleEntity) = scheduleDao.updateSchedule(schedule)
    suspend fun addSchedule(schedule: WorkoutScheduleEntity) = scheduleDao.insertSchedule(schedule)
    suspend fun deleteSchedule(id: Long) = scheduleDao.deleteSchedule(id)

    // Body Pose & BMI Analytics
    private val bodyScanDao = database.bodyScanDao()
    private val bmiRecordDao = database.bmiRecordDao()
    private val bodyPoseLandmarkDao = database.bodyPoseLandmarkDao()

    fun getAllBodyScans(): Flow<List<BodyScanEntity>> = bodyScanDao.getAllScans()
    suspend fun insertBodyScan(scan: BodyScanEntity) = bodyScanDao.insertScan(scan)
    suspend fun deleteBodyScan(scan: BodyScanEntity) = bodyScanDao.deleteScan(scan)

    fun getAllBmiRecords(): Flow<List<BmiRecordEntity>> = bmiRecordDao.getAllRecords()
    fun getLatestBmiRecord(): Flow<BmiRecordEntity?> = bmiRecordDao.getLatestRecord()
    suspend fun insertBmiRecord(record: BmiRecordEntity) = bmiRecordDao.insertRecord(record)
    suspend fun deleteBmiRecord(record: BmiRecordEntity) = bmiRecordDao.deleteRecord(record)

    suspend fun getLandmarksByScanId(scanId: String): List<BodyPoseLandmarkEntity> =
        bodyPoseLandmarkDao.getLandmarksByScanId(scanId)
    suspend fun insertLandmarks(landmarks: List<BodyPoseLandmarkEntity>) =
        bodyPoseLandmarkDao.insertLandmarks(landmarks)

    suspend fun saveFullScanAndBmi(
        scan: BodyScanEntity,
        landmarks: List<BodyPoseLandmarkEntity>,
        bmiRecord: BmiRecordEntity
    ) {
        database.withTransaction {
            bodyScanDao.insertScan(scan)
            if (landmarks.isNotEmpty()) {
                bodyPoseLandmarkDao.insertLandmarks(landmarks)
            }
            bmiRecordDao.insertRecord(bmiRecord)
            // Also update UserProfile and current week's record with confirmed weight & height
            val previous = userProfileDao.getUserProfileSync() ?: UserProfileEntity()
            val updated = previous.copy(
                weightKg = bmiRecord.weightKg,
                heightCm = bmiRecord.heightCm
            )
            userProfileDao.insertOrUpdateProfile(updated)
            if (bmiRecord.weightKg in com.example.data.progress.WeekDefinition.MIN_WEIGHT_KG..com.example.data.progress.WeekDefinition.MAX_WEIGHT_KG &&
                bmiRecord.heightCm in com.example.data.progress.WeekDefinition.MIN_HEIGHT_CM..com.example.data.progress.WeekDefinition.MAX_HEIGHT_CM
            ) {
                syncWeeklyRecordForBiometricsLocked(
                    previousProfile = previous,
                    updatedProfile = updated,
                    timestamp = bmiRecord.assessmentDate
                )
            }
        }
    }

    // Calendar Event Reminders
    private val calendarEventDao = database.calendarEventDao()

    fun getAllCalendarEvents(): Flow<List<CalendarEventEntity>> = calendarEventDao.getAllEvents()
    fun getActiveCalendarEvents(): Flow<List<CalendarEventEntity>> = calendarEventDao.getActiveEvents()
    fun getAllCalendarExceptions(): Flow<List<CalendarEventExceptionEntity>> = calendarEventDao.getAllExceptions()
    suspend fun getCalendarEventById(id: Long): CalendarEventEntity? = calendarEventDao.getEventById(id)
    suspend fun insertCalendarEvent(event: CalendarEventEntity): Long {
        val id = calendarEventDao.insertEvent(event)
        safeSyncAlarm(id)
        return id
    }

    suspend fun updateCalendarEvent(event: CalendarEventEntity) {
        calendarEventDao.updateEvent(event)
        safeSyncAlarm(event.id)
    }

    suspend fun deleteCalendarEvent(id: Long) {
        calendarEventDao.deleteEventById(id)
        safeCancelAlarm(id)
    }

    suspend fun editCalendarEventThisDayOnly(eventId: Long, occurrenceDate: String, newEvent: CalendarEventEntity): Long {
        val newEventId = calendarEventDao.editThisDayOnly(eventId, occurrenceDate, newEvent)
        safeSyncAlarm(eventId)
        safeSyncAlarm(newEventId)
        return newEventId
    }

    suspend fun deleteCalendarEventThisDayOnly(eventId: Long, occurrenceDate: String): Long {
        val result = calendarEventDao.deleteThisDayOnly(eventId, occurrenceDate)
        safeSyncAlarm(eventId)
        return result
    }

    suspend fun getCalendarExceptionsForEvent(eventId: Long): List<CalendarEventExceptionEntity> =
        calendarEventDao.getExceptionsForEvent(eventId)

    suspend fun deleteException(eventId: Long, occurrenceDate: String) {
        calendarEventDao.deleteException(eventId, occurrenceDate)
        safeSyncAlarm(eventId)
    }

    suspend fun restoreCalendarSeries(event: CalendarEventEntity, exceptions: List<CalendarEventExceptionEntity>) {
        database.withTransaction {
            calendarEventDao.insertEvent(event)
            for (exception in exceptions) {
                calendarEventDao.insertException(exception)
            }
        }
        safeSyncAlarm(event.id)
    }

    private suspend fun safeSyncAlarm(eventId: Long) {
        context?.let { ctx ->
            try {
                CalendarEventReminderScheduler.syncAlarmForEvent(ctx, eventId)
            } catch (_: Throwable) {
                // An alarm failure must never break or roll back a save
            }
        }
    }

    private fun safeCancelAlarm(eventId: Long) {
        context?.let { ctx ->
            try {
                CalendarEventReminderScheduler.cancelAlarmForEvent(ctx, eventId)
            } catch (_: Throwable) {
                // An alarm failure must never break or roll back a delete
            }
        }
    }
}


