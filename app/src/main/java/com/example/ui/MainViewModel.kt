package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.gamification.GamificationRules
import com.example.data.gamification.StreakDayStatus
import com.example.data.gamification.StreakSummary
import com.example.data.local.AppDatabase
import com.example.data.local.DataSeeder
import com.example.data.local.entity.*
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    val repository = WorkoutRepository(database, application.applicationContext)

    val userProfile: StateFlow<UserProfileEntity?> = repository.getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPlans: StateFlow<List<WorkoutPlanEntity>> = repository.getAllPlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExercises: StateFlow<List<ExerciseEntity>> = repository.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedSessions: StateFlow<List<WorkoutSessionEntity>> = repository.getCompletedSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val measurements: StateFlow<List<BodyMeasurementEntity>> = repository.getAllMeasurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val badges: StateFlow<List<BadgeEntity>> = repository.getAllBadges()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val schedules: StateFlow<List<WorkoutScheduleEntity>> = repository.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarEvents: StateFlow<List<CalendarEventEntity>> = repository.getAllCalendarEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calendarExceptions: StateFlow<List<CalendarEventExceptionEntity>> = repository.getAllCalendarExceptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBmiRecords: StateFlow<List<BmiRecordEntity>> = repository.getAllBmiRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestBmiRecord: StateFlow<BmiRecordEntity?> = repository.getLatestBmiRecord()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allBodyScans: StateFlow<List<BodyScanEntity>> = repository.getAllBodyScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val currentDayTick = MutableStateFlow(System.currentTimeMillis())

    val streakSummary: StateFlow<StreakSummary> = combine(completedSessions, currentDayTick) { sessions, nowMs ->
        GamificationRules.computeStreakSummary(
            completedTimestamps = sessions.map { it.completedAt },
            nowMillis = nowMs
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GamificationRules.computeStreakSummary(emptyList())
    )

    val streakDays: StateFlow<Int> = streakSummary
        .map { it.currentStreak }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val longestStreakDays: StateFlow<Int> = streakSummary
        .map { it.longestStreak }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val last30DaysStreak: StateFlow<List<StreakDayStatus>> = streakSummary
        .map { it.last30Days }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            GamificationRules.computeStreakSummary(emptyList()).last30Days
        )

    private val _calendarErrorMessage = MutableStateFlow<String?>(null)
    val calendarErrorMessage: StateFlow<String?> = _calendarErrorMessage.asStateFlow()

    fun clearCalendarErrorMessage() {
        _calendarErrorMessage.value = null
    }

    val recommendedPlan: StateFlow<WorkoutPlanEntity?> = combine(userProfile, allPlans) { profile, plans ->
        if (plans.isEmpty()) return@combine null
        val effectiveAge = profile?.calculateAge() ?: 25
        // Tailor workout recommendations using calculated age:
        // Senior citizens (age 60+) receive joint-friendly low-impact / beginner full body recommendations
        if (effectiveAge >= 60) {
            return@combine plans.find { it.difficulty.equals("Beginner", ignoreCase = true) || it.category == "Full Body" }
                ?: plans.first()
        }
        val goal = profile?.fitnessGoal?.ifBlank { GamificationRules.DEFAULT_FITNESS_GOAL }
            ?: GamificationRules.DEFAULT_FITNESS_GOAL
        when {
            goal.contains("Abs", ignoreCase = true) -> plans.find { it.category == "Abs" } ?: plans.first()
            goal.contains("Weight", ignoreCase = true) -> plans.find { it.category == "Fat-Burning" } ?: plans.first()
            goal.contains("Endurance", ignoreCase = true) -> plans.find { it.category == "Full Body" } ?: plans.first()
            else -> plans.find { it.category == "Full Body" } ?: plans.first()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val seedJob: Job = viewModelScope.launch {
        DataSeeder.seedIfNeeded(database)
        repository.migrateLegacyWeeklyRecordsIfNeeded()
    }

    fun refreshDateTick(nowMillis: Long = System.currentTimeMillis()) {
        currentDayTick.value = nowMillis
    }

    fun updateStreak() {
        refreshDateTick()
    }

    fun syncAccountIdentityIfDefault(accountName: String?, accountGoal: String? = null) {
        viewModelScope.launch {
            seedJob.join()
            repository.syncAccountIdentityIfDefault(accountName, accountGoal)
        }
    }

    fun logMeasurement(
        weight: Float,
        height: Float,
        chest: Float? = null,
        arm: Float? = null,
        waist: Float? = null,
        notes: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        require(weight > 0f) { "Weight must be greater than zero." }
        require(height > 0f) { "Height must be greater than zero." }
        require(timestamp <= System.currentTimeMillis()) { "Measurement date cannot be in the future." }
        if (chest != null) require(chest > 0f) { "Chest measurement must be positive." }
        if (arm != null) require(arm > 0f) { "Arm measurement must be positive." }
        if (waist != null) require(waist > 0f) { "Waist measurement must be positive." }

        viewModelScope.launch {
            repository.logMeasurement(
                weightKg = weight,
                heightCm = height,
                chestCm = chest,
                armCm = arm,
                waistCm = waist,
                notes = notes,
                timestamp = timestamp
            )
        }
    }

    fun logWeight(weight: Float, height: Float, timestamp: Long = System.currentTimeMillis()) {
        logMeasurement(weight = weight, height = height, timestamp = timestamp)
    }

    fun deleteMeasurement(id: Long) {
        viewModelScope.launch {
            repository.deleteMeasurementById(id)
        }
    }

    fun toggleFavorite(exercise: ExerciseEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(exercise.id, exercise.isFavorite)
        }
    }

    fun toggleSchedule(schedule: WorkoutScheduleEntity) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
        }
    }

    fun updateSchedule(schedule: WorkoutScheduleEntity) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
        }
    }

    fun addSchedule(schedule: WorkoutScheduleEntity) {
        viewModelScope.launch {
            repository.addSchedule(schedule)
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(id)
        }
    }

    fun updateProfile(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.updateProfile(profile)
        }
    }

    fun updateProfileWithWeeklySync(profile: UserProfileEntity) {
        viewModelScope.launch {
            repository.updateProfileWithWeeklySync(profile)
        }
    }

    fun updateUseMetric(useMetric: Boolean) {
        viewModelScope.launch {
            repository.updateUseMetric(useMetric)
        }
    }

    fun saveWeeklyProgressRecord(
        memberName: String,
        memberAge: Int,
        weightKg: Float,
        heightCm: Float,
        chestCm: Float,
        armCm: Float,
        waistCm: Float,
        useMetric: Boolean,
        onSaved: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val weekNum = repository.saveWeeklyProgressRecord(
                memberName = memberName,
                memberAge = memberAge,
                weightKg = weightKg,
                heightCm = heightCm,
                chestCm = chestCm,
                armCm = armCm,
                waistCm = waistCm,
                useMetric = useMetric
            )
            onSaved(weekNum)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSessionById(sessionId)
            updateStreak()
        }
    }

    fun deleteCustomPlan(planId: String) {
        viewModelScope.launch {
            repository.deleteCustomPlan(planId)
        }
    }

    fun saveCalendarEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            try {
                repository.insertCalendarEvent(event)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't save the event. Please try again."
            }
        }
    }

    fun updateCalendarEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            try {
                repository.updateCalendarEvent(event)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't update the event. Please try again."
            }
        }
    }

    fun deleteCalendarEvent(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteCalendarEvent(id)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't delete the event. Please try again."
            }
        }
    }

    fun editCalendarEventThisDayOnly(eventId: Long, occurrenceDate: String, newEvent: CalendarEventEntity) {
        viewModelScope.launch {
            try {
                repository.editCalendarEventThisDayOnly(eventId, occurrenceDate, newEvent)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't update the event for this day. Please try again."
            }
        }
    }

    fun deleteCalendarEventThisDayOnly(eventId: Long, occurrenceDate: String) {
        viewModelScope.launch {
            try {
                repository.deleteCalendarEventThisDayOnly(eventId, occurrenceDate)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't delete the event for this day. Please try again."
            }
        }
    }

    suspend fun getCalendarExceptionsForEvent(eventId: Long): List<CalendarEventExceptionEntity> {
        return try {
            repository.getCalendarExceptionsForEvent(eventId)
        } catch (e: android.database.sqlite.SQLiteException) {
            emptyList()
        }
    }

    fun restoreCalendarEvent(event: CalendarEventEntity) {
        viewModelScope.launch {
            try {
                repository.insertCalendarEvent(event)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't restore the event. Please try again."
            }
        }
    }

    fun restoreCalendarSeries(event: CalendarEventEntity, exceptions: List<CalendarEventExceptionEntity>) {
        viewModelScope.launch {
            try {
                repository.restoreCalendarSeries(event, exceptions)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't restore the event. Please try again."
            }
        }
    }

    fun undoDeleteThisDayOnly(eventId: Long, occurrenceDate: String) {
        viewModelScope.launch {
            try {
                repository.deleteException(eventId, occurrenceDate)
            } catch (e: android.database.sqlite.SQLiteException) {
                _calendarErrorMessage.value = "Couldn't restore the event for this day. Please try again."
            }
        }
    }

    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAllTables()
            DataSeeder.seedIfNeeded(database)
            withContext(Dispatchers.Main) {
                updateStreak()
            }
        }
    }
}
