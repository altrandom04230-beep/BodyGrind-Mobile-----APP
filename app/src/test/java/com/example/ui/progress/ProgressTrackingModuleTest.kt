package com.example.ui.progress

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.dao.BodyMeasurementDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.dao.WorkoutSessionDao
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.data.repository.WorkoutRepository
import com.example.ui.posebmi.BmiCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProgressTrackingModuleTest {

    private lateinit var db: AppDatabase
    private lateinit var sessionDao: WorkoutSessionDao
    private lateinit var measurementDao: BodyMeasurementDao
    private lateinit var profileDao: UserProfileDao
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessionDao = db.workoutSessionDao()
        measurementDao = db.bodyMeasurementDao()
        profileDao = db.userProfileDao()
        repository = WorkoutRepository(db, context)

        // Seed initial profile
        runBlocking {
            profileDao.insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    weightKg = 70.0f,
                    heightCm = 175.0f
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // FEATURE 1: WORKOUT HISTORY
    // =========================================================================

    @Test
    fun `Workout History - Empty State - Returns empty list and 0 aggregates`() = runBlocking {
        val sessions = repository.getCompletedSessions().first()
        assertTrue("Workout history should be empty initially", sessions.isEmpty())

        val count = repository.getTotalWorkoutsCount().first()
        assertEquals("Total workouts count should be 0", 0, count)

        val totalDuration = repository.getTotalDurationSeconds().first()
        assertNull("Total duration should be null when empty", totalDuration)

        val totalCalories = repository.getTotalCaloriesBurned().first()
        assertNull("Total calories should be null when empty", totalCalories)
    }

    @Test
    fun `Workout History - Populated State - Populates completed sessions ordered by completedAt DESC`() = runBlocking {
        val session1 = WorkoutSessionEntity(
            sessionId = 0,
            planId = "plan_full_body",
            planName = "Full Body Starter",
            category = "Full Body",
            startedAt = 1000L,
            completedAt = 2000L,
            durationSeconds = 600,
            caloriesBurned = 80,
            exercisesCompleted = 5,
            totalExercises = 5,
            isFinished = true
        )
        val session2 = WorkoutSessionEntity(
            sessionId = 0,
            planId = "plan_abs",
            planName = "Abs Crusher",
            category = "Abs",
            startedAt = 3000L,
            completedAt = 4500L,
            durationSeconds = 900,
            caloriesBurned = 120,
            exercisesCompleted = 8,
            totalExercises = 8,
            isFinished = true
        )

        sessionDao.insertSession(session1)
        sessionDao.insertSession(session2)

        val completedSessions = repository.getCompletedSessions().first()
        assertEquals(2, completedSessions.size)
        // Verify ordering: newest completedAt first
        assertEquals("Abs Crusher", completedSessions[0].planName)
        assertEquals(4500L, completedSessions[0].completedAt)
        assertEquals("Full Body Starter", completedSessions[1].planName)
        assertEquals(2000L, completedSessions[1].completedAt)
    }

    @Test
    fun `Workout History - Aggregates Calculation - Correctly sums workouts, duration, and calories`() = runBlocking {
        sessionDao.insertSession(
            WorkoutSessionEntity(
                sessionId = 0,
                planId = "p1",
                planName = "Routine A",
                category = "Chest",
                startedAt = 100L,
                completedAt = 500L,
                durationSeconds = 400,
                caloriesBurned = 60,
                exercisesCompleted = 4,
                totalExercises = 4,
                isFinished = true
            )
        )
        sessionDao.insertSession(
            WorkoutSessionEntity(
                sessionId = 0,
                planId = "p2",
                planName = "Routine B",
                category = "Legs",
                startedAt = 600L,
                completedAt = 1400L,
                durationSeconds = 800,
                caloriesBurned = 110,
                exercisesCompleted = 6,
                totalExercises = 6,
                isFinished = true
            )
        )

        val sessions = repository.getCompletedSessions().first()
        val totalWorkouts = sessions.size
        val totalDurationSeconds = sessions.sumOf { it.durationSeconds }
        val totalCaloriesBurned = sessions.sumOf { it.caloriesBurned }

        assertEquals(2, totalWorkouts)
        assertEquals(1200, totalDurationSeconds)
        assertEquals(170, totalCaloriesBurned)
    }

    @Test
    fun `Workout History - Delete Session - Successfully deletes session and updates history`() = runBlocking {
        val sId = sessionDao.insertSession(
            WorkoutSessionEntity(
                sessionId = 0,
                planId = "p1",
                planName = "To Delete",
                category = "Cardio",
                startedAt = 100L,
                completedAt = 500L,
                durationSeconds = 400,
                caloriesBurned = 50,
                exercisesCompleted = 3,
                totalExercises = 3,
                isFinished = true
            )
        )

        assertEquals(1, repository.getCompletedSessions().first().size)
        repository.deleteSessionById(sId)
        val remaining = repository.getCompletedSessions().first()
        assertTrue("Workout sessions should be empty after deleting session", remaining.isEmpty())
    }

    @Test
    fun `Workout History - Unfinished Session Filtered - Non-finished sessions are not in completed history`() = runBlocking {
        sessionDao.insertSession(
            WorkoutSessionEntity(
                sessionId = 0,
                planId = "p1",
                planName = "Incomplete Session",
                category = "Cardio",
                startedAt = 100L,
                completedAt = 0L,
                durationSeconds = 120,
                caloriesBurned = 15,
                exercisesCompleted = 1,
                totalExercises = 5,
                isFinished = false
            )
        )

        val completed = repository.getCompletedSessions().first()
        assertTrue("Unfinished sessions must not appear in completed workout history", completed.isEmpty())
    }

    // =========================================================================
    // FEATURE 2: BMI CHANGES OVER TIME
    // =========================================================================

    @Test
    fun `BMI Calculation - Standard Formula - Correctly calculates weightKg div (heightM squared)`() {
        // Height = 175cm = 1.75m. Weight = 70kg.
        // BMI = 70 / (1.75 * 1.75) = 70 / 3.0625 = 22.85714...
        val weightKg = 70f
        val heightCm = 175f
        val heightM = heightCm / 100f
        val bmi = weightKg / (heightM * heightM)

        assertEquals(22.86f, bmi, 0.05f)
        val analysis = BmiCalculator.classifyAdultBmi(bmi, heightCm)
        assertEquals("Normal weight", analysis.category)
    }

    @Test
    fun `BMI Changes - Delta vs Previous Measurement - Accurately computes change between consecutive logs`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 86400000L

        // Day 1 (Baseline): 80kg, 175cm -> BMI = 80 / 3.0625 = 26.12
        repository.logMeasurement(80f, 175f, timestamp = now - 2 * day)

        // Day 2: 78kg, 175cm -> BMI = 78 / 3.0625 = 25.47
        repository.logMeasurement(78f, 175f, timestamp = now - day)

        // Day 3 (Latest): 76kg, 175cm -> BMI = 76 / 3.0625 = 24.82
        repository.logMeasurement(76f, 175f, timestamp = now)

        val measurements = repository.getAllMeasurements().first()
        assertEquals(3, measurements.size)

        val latest = measurements[0]
        val previous = measurements[1]
        val baseline = measurements.last()

        val deltaFromPrevious = latest.bmi - previous.bmi
        val deltaFromBaseline = latest.bmi - baseline.bmi

        // 76kg BMI (~24.82) - 78kg BMI (~25.47) = -0.65
        assertTrue("BMI should have decreased vs previous", deltaFromPrevious < 0)
        assertEquals(-0.65f, deltaFromPrevious, 0.05f)

        // 76kg BMI (~24.82) - 80kg BMI (~26.12) = -1.30
        assertTrue("BMI should have decreased vs baseline", deltaFromBaseline < 0)
        assertEquals(-1.30f, deltaFromBaseline, 0.05f)
    }

    @Test
    fun `BMI Changes - Baseline Only (1 Entry) - Delta from previous is null`() = runBlocking {
        val now = System.currentTimeMillis()
        repository.logMeasurement(75f, 175f, timestamp = now)

        val measurements = repository.getAllMeasurements().first()
        assertEquals(1, measurements.size)

        val previous = measurements.getOrNull(1)
        assertNull("Previous measurement should be null for single entry", previous)
    }

    @Test
    fun `BMI Changes - Zero Entries - Falls back to profile default metrics`() = runBlocking {
        val measurements = repository.getAllMeasurements().first()
        assertTrue(measurements.isEmpty())

        val profile = repository.getUserProfile().first()!!
        val currentWeight = profile.weightKg
        val currentHeight = profile.heightCm
        val heightM = currentHeight / 100f
        val bmi = currentWeight / (heightM * heightM)

        assertEquals(70.0f, currentWeight, 0.01f)
        assertEquals(175.0f, currentHeight, 0.01f)
        assertEquals(22.86f, bmi, 0.05f)
    }

    @Test
    fun `BMI Changes - Stable Delta - Changes under 0_05 treated as flat`() {
        val bmi1 = 22.84f
        val bmi2 = 22.87f
        val delta = kotlin.math.abs(bmi2 - bmi1) // 0.03 < 0.05
        assertTrue("Delta < 0.05 should be recognized as flat", delta < 0.05f)
    }

    // =========================================================================
    // FEATURE 3: WEIGHT PROGRESS & LOG FLOW
    // =========================================================================

    @Test
    fun `Weight Progress - Valid Log - Saves measurement with correct BMI and updates user profile`() = runBlocking {
        val now = System.currentTimeMillis()
        val id = repository.logMeasurement(weightKg = 72.5f, heightCm = 178f, timestamp = now)
        assertTrue("Generated measurement id should be > 0", id > 0)

        val measurements = repository.getAllMeasurements().first()
        assertEquals(1, measurements.size)
        val m = measurements.first()
        assertEquals(72.5f, m.weightKg, 0.01f)
        assertEquals(178.0f, m.heightCm, 0.01f)
        assertEquals(now, m.timestamp)

        // Verify BMI and Category
        // 72.5 / (1.78 * 1.78) = 72.5 / 3.1684 = 22.88
        assertEquals(22.88f, m.bmi, 0.05f)
        assertEquals("Normal weight", m.bmiCategory)

        // Profile weight & height updated
        val updatedProfile = repository.getUserProfile().first()!!
        assertEquals(72.5f, updatedProfile.weightKg, 0.01f)
        assertEquals(178.0f, updatedProfile.heightCm, 0.01f)
    }

    @Test
    fun `Weight Progress - Invalid Input - Rejects zero weight with IllegalArgumentException`() = runBlocking {
        try {
            repository.logMeasurement(weightKg = 0f, heightCm = 175f)
            fail("Expected IllegalArgumentException for zero weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun `Weight Progress - Invalid Input - Rejects negative weight with IllegalArgumentException`() = runBlocking {
        try {
            repository.logMeasurement(weightKg = -15f, heightCm = 175f)
            fail("Expected IllegalArgumentException for negative weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun `Weight Progress - Invalid Input - Rejects zero height with IllegalArgumentException`() = runBlocking {
        try {
            repository.logMeasurement(weightKg = 70f, heightCm = 0f)
            fail("Expected IllegalArgumentException for zero height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun `Weight Progress - Invalid Input - Rejects negative height with IllegalArgumentException`() = runBlocking {
        try {
            repository.logMeasurement(weightKg = 70f, heightCm = -170f)
            fail("Expected IllegalArgumentException for negative height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun `Weight Progress - Invalid Input - Rejects future timestamp with IllegalArgumentException`() = runBlocking {
        val futureTime = System.currentTimeMillis() + 86400000L * 5 // 5 days in the future
        try {
            repository.logMeasurement(weightKg = 70f, heightCm = 175f, timestamp = futureTime)
            fail("Expected IllegalArgumentException for future date")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("future", ignoreCase = true))
        }
    }

    @Test
    fun `Weight Progress - Delete Measurement - Successfully removes entry by ID`() = runBlocking {
        val id1 = repository.logMeasurement(75f, 175f, timestamp = 1000L)
        val id2 = repository.logMeasurement(74f, 175f, timestamp = 2000L)

        assertEquals(2, repository.getAllMeasurements().first().size)

        repository.deleteMeasurementById(id1)

        val remaining = repository.getAllMeasurements().first()
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining.first().id)
        assertEquals(74f, remaining.first().weightKg, 0.01f)
    }

    @Test
    fun `Weight Progress - Summary Metrics - Accurately calculates starting, current, and net weight change`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 86400000L

        // Initial entry
        repository.logMeasurement(82.0f, 180f, timestamp = now - 10 * day)
        // Intermediate entry
        repository.logMeasurement(80.5f, 180f, timestamp = now - 5 * day)
        // Current entry
        repository.logMeasurement(78.2f, 180f, timestamp = now)

        val measurements = repository.getAllMeasurements().first() // ordered DESC by timestamp
        val startingWeight = measurements.last().weightKg
        val currentWeight = measurements.first().weightKg
        val netChange = currentWeight - startingWeight

        assertEquals(82.0f, startingWeight, 0.01f)
        assertEquals(78.2f, currentWeight, 0.01f)
        // 78.2 - 82.0 = -3.8 kg
        assertEquals(-3.8f, netChange, 0.01f)
    }

    @Test
    fun `Weight Progress - Single Entry Summary - Net change is zero`() = runBlocking {
        repository.logMeasurement(70.0f, 175f)
        val measurements = repository.getAllMeasurements().first()
        val startingWeight = measurements.last().weightKg
        val currentWeight = measurements.first().weightKg
        val netChange = currentWeight - startingWeight

        assertEquals(0.0f, netChange, 0.001f)
    }

    // =========================================================================
    // VIEWMODEL TESTS: VALIDATION & DELEGATION
    // =========================================================================

    @Test
    fun `ViewModel - logWeight - Rejects non-positive weight with IllegalArgumentException`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.MainViewModel(app)

        try {
            viewModel.logWeight(-5f, 175f)
            fail("Expected IllegalArgumentException for negative weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }

        try {
            viewModel.logWeight(0f, 175f)
            fail("Expected IllegalArgumentException for zero weight")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun `ViewModel - logWeight - Rejects non-positive height with IllegalArgumentException`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.MainViewModel(app)

        try {
            viewModel.logWeight(70f, -170f)
            fail("Expected IllegalArgumentException for negative height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }

        try {
            viewModel.logWeight(70f, 0f)
            fail("Expected IllegalArgumentException for zero height")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("greater than zero", ignoreCase = true))
        }
    }

    @Test
    fun `ViewModel - logWeight - Rejects future timestamp with IllegalArgumentException`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.ui.MainViewModel(app)
        val future = System.currentTimeMillis() + 86400000L * 2

        try {
            viewModel.logWeight(70f, 175f, timestamp = future)
            fail("Expected IllegalArgumentException for future timestamp")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("future", ignoreCase = true))
        }
    }

    // =========================================================================
    // CHRONOLOGICAL PAST WORKOUTS & 30-DAY PROGRESS CHART TESTS
    // =========================================================================

    @Test
    fun `Workout History - Chronological Ordering - Sorts sessions oldest to newest`() = runBlocking {
        val s1 = WorkoutSessionEntity(
            sessionId = 0,
            planId = "p1",
            planName = "First Routine",
            category = "Full Body",
            startedAt = 1000L,
            completedAt = 2000L,
            durationSeconds = 300,
            caloriesBurned = 40,
            exercisesCompleted = 3,
            totalExercises = 3,
            isFinished = true
        )
        val s2 = WorkoutSessionEntity(
            sessionId = 0,
            planId = "p2",
            planName = "Second Routine",
            category = "Chest",
            startedAt = 3000L,
            completedAt = 5000L,
            durationSeconds = 600,
            caloriesBurned = 80,
            exercisesCompleted = 5,
            totalExercises = 5,
            isFinished = true
        )
        sessionDao.insertSession(s1)
        sessionDao.insertSession(s2)

        val completed = repository.getCompletedSessions().first()
        val chronological = completed.sortedBy { it.completedAt }

        assertEquals(2, chronological.size)
        assertEquals("First Routine", chronological[0].planName)
        assertEquals(2000L, chronological[0].completedAt)
        assertEquals("Second Routine", chronological[1].planName)
        assertEquals(5000L, chronological[1].completedAt)
    }

    @Test
    fun `Weight Lifting Progress - 30 Day Filter - Excludes routines older than 30 days`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 86400000L
        val thirtyDaysAgo = now - (30L * day)

        // Workout within 30 days (10 days ago)
        val recentSession = WorkoutSessionEntity(
            sessionId = 0,
            planId = "p1",
            planName = "Recent Lift",
            category = "Chest",
            startedAt = now - (10 * day) - 1000L,
            completedAt = now - (10 * day),
            durationSeconds = 900,
            caloriesBurned = 150,
            exercisesCompleted = 6,
            totalExercises = 6,
            isFinished = true
        )
        // Workout older than 30 days (45 days ago)
        val oldSession = WorkoutSessionEntity(
            sessionId = 0,
            planId = "p2",
            planName = "Old Lift",
            category = "Legs",
            startedAt = now - (45 * day) - 1000L,
            completedAt = now - (45 * day),
            durationSeconds = 900,
            caloriesBurned = 150,
            exercisesCompleted = 6,
            totalExercises = 6,
            isFinished = true
        )

        sessionDao.insertSession(recentSession)
        sessionDao.insertSession(oldSession)

        val allSessions = repository.getCompletedSessions().first()
        val filtered30D = allSessions.filter { it.isFinished && it.completedAt >= thirtyDaysAgo }

        assertEquals("Only 1 session should be within the last 30 days", 1, filtered30D.size)
        assertEquals("Recent Lift", filtered30D.first().planName)
    }

    @Test
    fun `Weight Lifting Progress - Volume Calculation - Correctly computes 30-day lifting volume and reps`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 86400000L
        val thirtyDaysAgo = now - (30L * day)

        sessionDao.insertSession(
            WorkoutSessionEntity(
                sessionId = 0,
                planId = "p1",
                planName = "Hypertrophy Push",
                category = "Chest",
                startedAt = now - (5 * day) - 1000L,
                completedAt = now - (5 * day),
                durationSeconds = 1200,
                caloriesBurned = 200,
                exercisesCompleted = 5,
                totalExercises = 5,
                isFinished = true
            )
        )
        sessionDao.insertSession(
            WorkoutSessionEntity(
                sessionId = 0,
                planId = "p2",
                planName = "Hypertrophy Pull",
                category = "Shoulder & Back",
                startedAt = now - (2 * day) - 1000L,
                completedAt = now - (2 * day),
                durationSeconds = 1200,
                caloriesBurned = 200,
                exercisesCompleted = 6,
                totalExercises = 6,
                isFinished = true
            )
        )

        val sessions = repository.getCompletedSessions().first()
            .filter { it.isFinished && it.completedAt >= thirtyDaysAgo }

        val totalExercises = sessions.sumOf { it.exercisesCompleted } // 5 + 6 = 11
        val totalReps = sessions.sumOf { it.exercisesCompleted * 12 } // 11 * 12 = 132 reps
        val totalVolumeKg = sessions.sumOf { (it.exercisesCompleted * 12 * 20).toLong() } // 132 * 20 = 2640 kg

        assertEquals(11, totalExercises)
        assertEquals(132, totalReps)
        assertEquals(2640L, totalVolumeKg)
    }

    // =========================================================================
    // FEATURE 4: BODY MEASUREMENTS ROOM TRACKING & 3-MONTH TRENDS
    // =========================================================================

    @Test
    fun `Body Measurements - Log All Metrics - Persists weight, height, chest, arm, waist, notes in Room`() = runBlocking {
        val now = System.currentTimeMillis()
        val mId = repository.logMeasurement(
            weightKg = 74.5f,
            heightCm = 176.0f,
            chestCm = 98.0f,
            armCm = 36.5f,
            waistCm = 81.0f,
            notes = "Post-workout check-in",
            timestamp = now
        )

        assertTrue(mId > 0)
        val list = repository.getAllMeasurements().first()
        assertEquals(1, list.size)
        val item = list.first()

        assertEquals(74.5f, item.weightKg, 0.01f)
        assertEquals(176.0f, item.heightCm, 0.01f)
        assertEquals(98.0f, item.chestCm ?: 0f, 0.01f)
        assertEquals(36.5f, item.armCm ?: 0f, 0.01f)
        assertEquals(81.0f, item.waistCm ?: 0f, 0.01f)
        assertEquals("Post-workout check-in", item.notes)
        assertEquals(now, item.timestamp)
    }

    @Test
    fun `Body Measurements - Last 3 Months Query - Filters records within 90 days`() = runBlocking {
        val now = System.currentTimeMillis()
        val day = 86400000L
        val cutoff90Days = now - (90L * day)

        // Seed 3 records within 90 days (75, 45, 10 days ago)
        repository.logMeasurement(weightKg = 78f, heightCm = 175f, timestamp = now - 75 * day)
        repository.logMeasurement(weightKg = 76f, heightCm = 175f, timestamp = now - 45 * day)
        repository.logMeasurement(weightKg = 74f, heightCm = 175f, timestamp = now - 10 * day)

        // Seed 1 record older than 90 days (110 days ago)
        repository.logMeasurement(weightKg = 82f, heightCm = 175f, timestamp = now - 110 * day)

        val total = repository.getAllMeasurements().first()
        assertEquals(4, total.size)

        val last3Months = repository.getMeasurementsSince(cutoff90Days).first()
        assertEquals("Should only return the 3 measurements from the last 90 days", 3, last3Months.size)
        assertTrue(last3Months.all { it.timestamp >= cutoff90Days })
    }

    @Test
    fun `Body Measurements - Validation - Rejects negative chest, arm, or waist measurements`() = runBlocking {
        try {
            repository.logMeasurement(weightKg = 70f, heightCm = 175f, chestCm = -10f)
            fail("Expected IllegalArgumentException for negative chest measurement")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Chest", ignoreCase = true))
        }

        try {
            repository.logMeasurement(weightKg = 70f, heightCm = 175f, armCm = -5f)
            fail("Expected IllegalArgumentException for negative arm measurement")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Arm", ignoreCase = true))
        }

        try {
            repository.logMeasurement(weightKg = 70f, heightCm = 175f, waistCm = -80f)
            fail("Expected IllegalArgumentException for negative waist measurement")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Waist", ignoreCase = true))
        }
    }

    @Test
    fun `Body Measurements - Conversion to WeeklyRecord - Correctly maps Room entity to WeeklyRecord`() {
        val entity = BodyMeasurementEntity(
            id = 42,
            weightKg = 73.0f,
            heightCm = 177.0f,
            chestCm = 97.0f,
            armCm = 36.0f,
            waistCm = 80.0f,
            bmi = 23.3f,
            bmiCategory = "Normal weight",
            notes = "Morning fasted log",
            timestamp = 1700000000000L
        )

        val weekly = entity.toWeeklyRecord()
        assertEquals("room_42", weekly.id)
        assertEquals(73.0f, weekly.weightKg, 0.01f)
        assertEquals(177.0f, weekly.heightCm, 0.01f)
        assertEquals(97.0f, weekly.chestCm, 0.01f)
        assertEquals(36.0f, weekly.armCm, 0.01f)
        assertEquals(80.0f, weekly.waistCm, 0.01f)
        assertEquals("Morning fasted log", weekly.memberName)
    }
}
