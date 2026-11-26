package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name ASC")
    fun getExercisesByCategory(category: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getExerciseById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteExercises(): Flow<List<ExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Update
    suspend fun updateExercise(exercise: ExerciseEntity)

    @Query("UPDATE exercises SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getCount(): Int
}

@Dao
interface WorkoutPlanDao {
    @Query("SELECT * FROM workout_plans ORDER BY createdAt ASC")
    fun getAllPlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE isCustom = 0 ORDER BY category ASC")
    fun getPredefinedPlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE isCustom = 1 ORDER BY createdAt DESC")
    fun getCustomPlans(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE category = :category ORDER BY name ASC")
    fun getPlansByCategory(category: String): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE isChallenge = 1 ORDER BY totalDays ASC")
    fun getChallenges(): Flow<List<WorkoutPlanEntity>>

    @Query("SELECT * FROM workout_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: String): WorkoutPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: WorkoutPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<WorkoutPlanEntity>)

    @Delete
    suspend fun deletePlan(plan: WorkoutPlanEntity)

    @Query("DELETE FROM workout_plans WHERE id = :id")
    suspend fun deletePlanById(id: String)

    @Query("SELECT * FROM workout_plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    fun getPlanExercises(planId: String): Flow<List<WorkoutPlanExerciseEntity>>

    @Query("SELECT * FROM workout_plan_exercises WHERE planId = :planId ORDER BY orderIndex ASC")
    suspend fun getPlanExercisesSync(planId: String): List<WorkoutPlanExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlanExercises(exercises: List<WorkoutPlanExerciseEntity>)

    @Query("DELETE FROM workout_plan_exercises WHERE planId = :planId")
    suspend fun deletePlanExercises(planId: String)

    @Query("SELECT * FROM workout_plans WHERE remoteAssignmentId = :remoteAssignmentId LIMIT 1")
    suspend fun getPlanByRemoteAssignmentId(remoteAssignmentId: String): WorkoutPlanEntity?

    @Query("SELECT * FROM workout_plans WHERE remoteAssignmentId IS NOT NULL ORDER BY startDateEpochMs DESC, createdAt DESC")
    suspend fun getAssignedPlansSync(): List<WorkoutPlanEntity>

    @Query("UPDATE workout_plans SET isCompletedByMember = :completed, status = CASE WHEN :completed = 1 THEN 'COMPLETED' ELSE status END WHERE id = :planId AND status != 'CANCELLED'")
    suspend fun markAssignedPlanCompleted(planId: String, completed: Boolean)

    @Query(
        "UPDATE workout_plans SET status = 'CANCELLED', isCompletedByMember = 0, excludeFromBilling = 1, " +
            "cancelledReason = :reason, cancelledByUid = :cancelledByUid, cancelledByName = :cancelledByName, " +
            "cancelledByRole = :cancelledByRole, cancelledAtUtcMs = :cancelledAtUtcMs WHERE id = :planId"
    )
    suspend fun markPlanCancelled(
        planId: String,
        reason: String,
        cancelledByUid: String,
        cancelledByName: String,
        cancelledByRole: String,
        cancelledAtUtcMs: Long
    )
}

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED' ORDER BY completedAt DESC")
    fun getAllCompletedSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE status = 'CANCELLED' ORDER BY cancelledAtUtcMs DESC, completedAt DESC")
    fun getCancelledSessions(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE sessionId = :id LIMIT 1")
    suspend fun getSessionById(id: Long): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED' ORDER BY completedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<WorkoutSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE sessionId = :id")
    suspend fun deleteSessionById(id: Long)

    @Query(
        "UPDATE workout_sessions SET status = 'CANCELLED', isFinished = 0, excludeFromBilling = 1, " +
            "cancelledReason = :reason, cancelledByUid = :cancelledByUid, cancelledByName = :cancelledByName, " +
            "cancelledByRole = :cancelledByRole, cancelledAtUtcMs = :cancelledAtUtcMs WHERE sessionId = :sessionId"
    )
    suspend fun cancelSessionById(
        sessionId: Long,
        reason: String,
        cancelledByUid: String,
        cancelledByName: String,
        cancelledByRole: String,
        cancelledAtUtcMs: Long
    )

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED'")
    fun getTotalWorkoutsCount(): Flow<Int>

    @Query("SELECT SUM(durationSeconds) FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED'")
    fun getTotalDurationSeconds(): Flow<Int?>

    @Query("SELECT SUM(caloriesBurned) FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED'")
    fun getTotalCaloriesBurned(): Flow<Int?>

    @Query("SELECT completedAt FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED' ORDER BY completedAt ASC")
    suspend fun getAllCompletedTimestamps(): List<Long>

    @Query("UPDATE workout_sessions SET xpAwarded = 1 WHERE sessionId = :sessionId AND status != 'CANCELLED'")
    suspend fun markSessionXpAwarded(sessionId: Long)

    @Query("SELECT COALESCE(SUM(caloriesBurned), 0) FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED'")
    suspend fun getTotalCaloriesBurnedSync(): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED' AND UPPER(category) = 'ABS'")
    suspend fun getCompletedAbsWorkoutsCountSync(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sessions WHERE isFinished = 1 AND status != 'CANCELLED' AND planId LIKE 'custom_%')")
    suspend fun hasCompletedCustomWorkoutSync(): Boolean
}

@Dao
interface BodyMeasurementDao {
    @Query("SELECT * FROM body_measurements ORDER BY timestamp DESC")
    fun getAllMeasurements(): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getMeasurementsSince(sinceTimestamp: Long): Flow<List<BodyMeasurementEntity>>

    @Query("SELECT * FROM body_measurements ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMeasurement(): Flow<BodyMeasurementEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: BodyMeasurementEntity): Long

    @Update
    suspend fun updateMeasurement(measurement: BodyMeasurementEntity)

    @Query("SELECT * FROM body_measurements WHERE weekKey = :weekKey ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMeasurementByWeekKey(weekKey: String): BodyMeasurementEntity?

    @Query("SELECT * FROM body_measurements WHERE weekKey IS NOT NULL ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestWeeklyMeasurementSync(): BodyMeasurementEntity?

    @Query("SELECT MAX(weekNumber) FROM body_measurements WHERE weekNumber IS NOT NULL")
    suspend fun getMaxWeekNumber(): Int?

    @Query("DELETE FROM body_measurements WHERE id = :id")
    suspend fun deleteMeasurementById(id: Long)

    @Query("SELECT COUNT(*) FROM body_measurements")
    suspend fun getCount(): Int
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getUserProfileSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET xpPoints = :xp, level = :level WHERE id = 1")
    suspend fun updateXpAndLevel(xp: Int, level: Int)
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badges ORDER BY unlocked DESC, badgeId ASC")
    fun getAllBadges(): Flow<List<BadgeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBadges(badges: List<BadgeEntity>)

    @Update
    suspend fun updateBadge(badge: BadgeEntity)

    @Query("SELECT * FROM badges WHERE badgeId = :id LIMIT 1")
    suspend fun getBadgeById(id: String): BadgeEntity?
}

@Dao
interface WorkoutScheduleDao {
    @Query("SELECT * FROM workout_schedules ORDER BY dayOfWeek ASC")
    fun getAllSchedules(): Flow<List<WorkoutScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: WorkoutScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<WorkoutScheduleEntity>)

    @Update
    suspend fun updateSchedule(schedule: WorkoutScheduleEntity)

    @Query("DELETE FROM workout_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)
}

@Dao
interface BodyScanDao {
    @Query("SELECT * FROM body_scans ORDER BY scanDate DESC")
    fun getAllScans(): Flow<List<BodyScanEntity>>

    @Query("SELECT * FROM body_scans WHERE scanId = :scanId LIMIT 1")
    suspend fun getScanById(scanId: String): BodyScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScan(scan: BodyScanEntity)

    @Delete
    suspend fun deleteScan(scan: BodyScanEntity)
}

@Dao
interface BmiRecordDao {
    @Query("SELECT * FROM bmi_records ORDER BY assessmentDate DESC")
    fun getAllRecords(): Flow<List<BmiRecordEntity>>

    @Query("SELECT * FROM bmi_records ORDER BY assessmentDate DESC LIMIT 1")
    fun getLatestRecord(): Flow<BmiRecordEntity?>

    @Query("SELECT * FROM bmi_records WHERE bmiRecordId = :id LIMIT 1")
    suspend fun getRecordById(id: String): BmiRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: BmiRecordEntity)

    @Delete
    suspend fun deleteRecord(record: BmiRecordEntity)
}

@Dao
interface BodyPoseLandmarkDao {
    @Query("SELECT * FROM body_pose_landmarks WHERE scanId = :scanId")
    suspend fun getLandmarksByScanId(scanId: String): List<BodyPoseLandmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLandmarks(landmarks: List<BodyPoseLandmarkEntity>)
}

@Dao
interface PendingSyncDao {
    @Query("SELECT COUNT(*) FROM pending_sync_ops")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM pending_sync_ops ORDER BY createdAtMs ASC")
    suspend fun getAllSync(): List<PendingSyncEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(op: PendingSyncEntity)

    @Query("DELETE FROM pending_sync_ops WHERE opKey = :opKey")
    suspend fun deleteByOpKey(opKey: String)

    @Query("UPDATE pending_sync_ops SET attempts = :attempts, lastAttemptMs = :lastAttemptMs, lastError = :lastError WHERE id = :id")
    suspend fun updateAttempt(id: Long, attempts: Int, lastAttemptMs: Long, lastError: String?)

    @Query("DELETE FROM pending_sync_ops")
    suspend fun clearAll()
}

