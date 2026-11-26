package com.example.ui.progress

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.progress.BiometricValidator
import com.example.data.progress.WeekDefinition
import com.example.data.repository.WorkoutRepository
import com.example.ui.profile.ProfileScreen
import com.example.ui.theme.HomeWorkoutTheme
import com.example.utils.UnitConverter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ProfileProgressSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: WorkoutRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepository(db, context)

        runBlocking {
            db.userProfileDao().insertOrUpdateProfile(
                UserProfileEntity(
                    id = 1,
                    name = "Fitness Athlete",
                    age = 26,
                    gender = "Prefer not to say",
                    weightKg = 70.0f,
                    heightCm = 175.0f,
                    useMetric = true,
                    fitnessGoal = "Build Muscle",
                    fitnessLevel = "Beginner",
                    onboardingCompleted = true
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `week definition anchors Monday 00_00 to Sunday 23_59_59_999 in local timezone`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val mondayStart = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 16, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val sundayEnd = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 22, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
        val nextMonday = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 23, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("2026-11-16", WeekDefinition.weekKeyFor(mondayStart, tz))
        assertEquals("2026-11-16", WeekDefinition.weekKeyFor(sundayEnd, tz))
        assertTrue(WeekDefinition.isSameWeek(mondayStart, sundayEnd, tz))
        assertFalse(WeekDefinition.isSameWeek(sundayEnd, nextMonday, tz))
        assertEquals("2026-11-23", WeekDefinition.weekKeyFor(nextMonday, tz))
    }

    @Test
    fun `saving weekly progress record updates profile and enforces 1 record per week`() = runBlocking {
        val monday = com.example.utils.ManilaDateTime.manilaCalendar(WeekDefinition.startOfWeekMillis()).apply {
            set(Calendar.HOUR_OF_DAY, 10)
        }.timeInMillis
        val friday = monday + (4L * 86_400_000L)

        val wk1 = repository.saveWeeklyProgressRecord(
            memberName = "Alex Rivera",
            memberAge = 29,
            weightKg = 74.5f,
            heightCm = 180.0f,
            chestCm = 101.0f,
            armCm = 37.5f,
            waistCm = 80.5f,
            useMetric = false,
            timestamp = monday
        )
        assertEquals(1, wk1)

        val profileAfterFirst = repository.getUserProfile().first()!!
        assertEquals("Alex Rivera", profileAfterFirst.name)
        assertEquals(29, profileAfterFirst.age)
        assertEquals(74.5f, profileAfterFirst.weightKg, 0.001f)
        assertEquals(180.0f, profileAfterFirst.heightCm, 0.001f)
        assertFalse(profileAfterFirst.useMetric)

        // Second save within same week must UPDATE existing weekly row instead of creating a duplicate
        val wkAgain = repository.saveWeeklyProgressRecord(
            memberName = "Alex R.",
            memberAge = 30,
            weightKg = 73.8f,
            heightCm = 180.0f,
            chestCm = 102.0f,
            armCm = 38.0f,
            waistCm = 79.5f,
            useMetric = true,
            timestamp = friday
        )
        assertEquals(1, wkAgain)

        val allMeasurements = repository.getAllMeasurements().first()
        val weeklyRows = allMeasurements.filter { !it.weekKey.isNullOrBlank() }
        assertEquals(1, weeklyRows.size)
        assertEquals(73.8f, weeklyRows.first().weightKg, 0.001f)
        assertEquals(102.0f, weeklyRows.first().chestCm!!, 0.001f)
        assertEquals("Alex R.", weeklyRows.first().memberName)
        assertEquals(30, weeklyRows.first().memberAge)

        val profileAfterSecond = repository.getUserProfile().first()!!
        assertEquals("Alex R.", profileAfterSecond.name)
        assertEquals(30, profileAfterSecond.age)
        assertEquals(73.8f, profileAfterSecond.weightKg, 0.001f)
        assertTrue(profileAfterSecond.useMetric)
    }

    @Test
    fun `editing weight or height in profile syncs current week only and leaves old records untouched`() = runBlocking {
        val now = System.currentTimeMillis()
        val twoWeeksAgo = now - (14L * 86_400_000L)
        val oldWeekKey = WeekDefinition.weekKeyFor(twoWeeksAgo)

        repository.saveWeeklyProgressRecord(
            memberName = "Old Log",
            memberAge = 26,
            weightKg = 78.0f,
            heightCm = 175.0f,
            chestCm = 98.0f,
            armCm = 35.5f,
            waistCm = 84.0f,
            useMetric = true,
            timestamp = twoWeeksAgo
        )

        val currentProfile = repository.getUserProfile().first()!!

        // Edit ONLY name, age, gender, goal -> must NOT create a weekly record for this week
        repository.updateProfileWithWeeklySync(
            currentProfile.copy(
                name = "Jordan Lee",
                age = 27,
                gender = "Female",
                fitnessGoal = "Lose Weight",
                fitnessLevel = "Intermediate"
            ),
            timestamp = now
        )

        val rowsAfterNameAgeEdit = repository.getAllMeasurements().first().filter { !it.weekKey.isNullOrBlank() }
        assertEquals(1, rowsAfterNameAgeEdit.size)
        assertEquals(oldWeekKey, rowsAfterNameAgeEdit.first().weekKey)
        assertEquals(78.0f, rowsAfterNameAgeEdit.first().weightKg, 0.001f)

        // Now edit WEIGHT -> creates this week's record while keeping old week untouched
        val updatedProfile = repository.getUserProfile().first()!!
        repository.updateProfileWithWeeklySync(
            updatedProfile.copy(weightKg = 72.4f),
            timestamp = now
        )

        val thisWeekKey = WeekDefinition.weekKeyFor(now)
        val rowsAfterWeightEdit = repository.getAllMeasurements().first().filter { !it.weekKey.isNullOrBlank() }
        assertEquals(2, rowsAfterWeightEdit.size)

        val currentWeekRow = rowsAfterWeightEdit.first { it.weekKey == thisWeekKey }
        val historicalRow = rowsAfterWeightEdit.first { it.weekKey == oldWeekKey }

        assertEquals(72.4f, currentWeekRow.weightKg, 0.001f)
        assertEquals(2, currentWeekRow.weekNumber)
        assertEquals("Jordan Lee", currentWeekRow.memberName)
        assertEquals(27, currentWeekRow.memberAge)
        // Old weekly record preserved completely untouched
        assertEquals(78.0f, historicalRow.weightKg, 0.001f)
        assertEquals("Old Log", historicalRow.memberName)

        // Edit HEIGHT in same week -> updates currentWeekRow in-place (still only 1 record for this week)
        val latestProfile = repository.getUserProfile().first()!!
        repository.updateProfileWithWeeklySync(
            latestProfile.copy(heightCm = 176.5f),
            timestamp = now + 5000L
        )
        val rowsAfterHeightEdit = repository.getAllMeasurements().first().filter { !it.weekKey.isNullOrBlank() }
        assertEquals(2, rowsAfterHeightEdit.size)
        val refreshedThisWeek = rowsAfterHeightEdit.first { it.weekKey == thisWeekKey }
        assertEquals(176.5f, refreshedThisWeek.heightCm, 0.001f)
        assertEquals(72.4f, refreshedThisWeek.weightKg, 0.001f)
    }

    @Test
    fun `round trip unit conversion preserves exact canonical metric values without drift`() {
        val originalWeightKg = 70.0f
        val originalHeightCm = 175.0f

        val formattedLb = BiometricValidator.formatWeightInput(originalWeightKg, useMetric = false)
        val resolvedUnmodifiedKg = BiometricValidator.resolveMetricWeightOrNull(
            rawInput = formattedLb,
            useMetric = false,
            canonicalWeightKg = originalWeightKg,
            isDirty = false
        )
        assertEquals(originalWeightKg, resolvedUnmodifiedKg!!, 0.0f)

        val formattedInches = BiometricValidator.formatHeightInput(originalHeightCm, useMetric = false)
        val resolvedUnmodifiedCm = BiometricValidator.resolveMetricLengthOrNull(
            rawInput = formattedInches,
            useMetric = false,
            canonicalCm = originalHeightCm,
            isDirty = false
        )
        assertEquals(originalHeightCm, resolvedUnmodifiedCm!!, 0.0f)

        // Validate converted values still match expected display
        assertEquals("154.3", formattedLb)
        assertEquals("68.9", formattedInches)
    }

    @Test
    fun `biometric validator blocks empty name and out of range inputs`() {
        val emptyName = BiometricValidator.validateProfileInputs(
            nameInput = "   ",
            ageInput = "26",
            weightInput = "70.0",
            heightInput = "175.0",
            useMetric = true
        )
        assertFalse(emptyName.isValid)
        assertTrue(emptyName.errors.containsKey("name"))

        val badRanges = BiometricValidator.validateWeeklyProgressInputs(
            nameInput = "Sam",
            ageInput = "5",
            weightInput = "5",
            heightInput = "400",
            chestInput = "10",
            armInput = "0",
            waistInput = "500",
            useMetric = true
        )
        assertFalse(badRanges.isValid)
        assertEquals(
            setOf("age", "weight", "height", "chest", "arm", "waist"),
            badRanges.errors.keys
        )

        // Imperial check: 150 lb & 70 in are valid
        val validImperial = BiometricValidator.validateProfileInputs(
            nameInput = "Sam",
            ageInput = "30",
            weightInput = "150.0",
            heightInput = "70.0",
            useMetric = false
        )
        assertTrue(validImperial.isValid)
        assertEquals(UnitConverter.lbToKg(150.0f), validImperial.weightKg, 0.01f)
        assertEquals(UnitConverter.inchesToCm(70.0f), validImperial.heightCm, 0.01f)
    }

    @Test
    fun `compose ui keeps profile dialog and progress screen values and units synchronized`() {
        var latestProfileState by mutableStateOf(
            UserProfileEntity(
                id = 1,
                name = "Fitness Athlete",
                age = 26,
                gender = "Male",
                weightKg = 70.0f,
                heightCm = 175.0f,
                useMetric = true
            )
        )
        var latestMeasurementsState by mutableStateOf<List<BodyMeasurementEntity>>(emptyList())

        composeTestRule.setContent {
            var profile by remember { mutableStateOf(latestProfileState) }
            var measurements by remember { mutableStateOf(latestMeasurementsState) }

            HomeWorkoutTheme(darkTheme = false) {
                ProfileScreen(
                    userProfile = profile,
                    onUpdateProfile = { updated ->
                        profile = updated
                        latestProfileState = updated
                    },
                    onResetData = {},
                    measurements = measurements,
                    onUpdateUseMetric = { metric ->
                        profile = profile.copy(useMetric = metric)
                        latestProfileState = profile
                    },
                    onSaveWeeklyProgressRecord = { name, age, wKg, hCm, cCm, aCm, waCm, useMetric, onSaved ->
                        profile = profile.copy(
                            name = name,
                            age = age,
                            weightKg = wKg,
                            heightCm = hCm,
                            useMetric = useMetric
                        )
                        latestProfileState = profile
                        val now = System.currentTimeMillis()
                        val weekKey = WeekDefinition.weekKeyFor(now)
                        val row = BodyMeasurementEntity(
                            id = 1L,
                            timestamp = now,
                            weightKg = wKg,
                            heightCm = hCm,
                            chestCm = cCm,
                            armCm = aCm,
                            waistCm = waCm,
                            bmi = 22.9f,
                            bmiCategory = "Normal weight",
                            notes = "Week 1: $name",
                            weekKey = weekKey,
                            weekNumber = 1,
                            memberName = name,
                            memberAge = age
                        )
                        measurements = listOf(row)
                        latestMeasurementsState = measurements
                        onSaved(1)
                    }
                )
            }
        }

        // 1. Switch to Progress tab -> scroll to Member Information & verify pre-fill from profile
        composeTestRule.onNodeWithTag("tab_progress").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_name_input"))
        composeTestRule.onNodeWithTag("member_name_input").assertTextContains("Fitness Athlete")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_weight_input"))
        composeTestRule.onNodeWithTag("member_weight_input").assertTextContains("70.0")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_height_input"))
        composeTestRule.onNodeWithTag("member_height_input").assertTextContains("175.0")

        // 2. Toggle unit to lb on Progress -> toggle back to kg -> verify zero drift
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("progress_weight_unit_lb"))
        composeTestRule.onNodeWithTag("progress_weight_unit_lb").performClick()
        composeTestRule.waitForIdle()
        assertFalse(latestProfileState.useMetric)
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_weight_input"))
        composeTestRule.onNodeWithTag("member_weight_input").assertTextContains("154.3")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_height_input"))
        composeTestRule.onNodeWithTag("member_height_input").assertTextContains("68.9")

        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("progress_weight_unit_kg"))
        composeTestRule.onNodeWithTag("progress_weight_unit_kg").performClick()
        composeTestRule.waitForIdle()
        assertTrue(latestProfileState.useMetric)
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_weight_input"))
        composeTestRule.onNodeWithTag("member_weight_input").assertTextContains("70.0")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_height_input"))
        composeTestRule.onNodeWithTag("member_height_input").assertTextContains("175.0")

        // 3. Invalid input (blank name) on Progress shows inline error and does NOT save
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_name_input"))
        composeTestRule.onNodeWithTag("member_name_input").performTextReplacement("")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("save_weekly_record_button"))
        composeTestRule.onNodeWithTag("save_weekly_record_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasText("Member name is required"))
        composeTestRule.onNodeWithText("Member name is required").assertIsDisplayed()
        assertTrue(latestMeasurementsState.isEmpty())

        // 4. Valid save on Progress updates Profile state
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_name_input"))
        composeTestRule.onNodeWithTag("member_name_input").performTextReplacement("Taylor Kim")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("member_weight_input"))
        composeTestRule.onNodeWithTag("member_weight_input").performTextReplacement("75.0")
        composeTestRule.onNodeWithTag("progress_lazy_column")
            .performScrollToNode(hasTestTag("save_weekly_record_button"))
        composeTestRule.onNodeWithTag("save_weekly_record_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Taylor Kim", latestProfileState.name)
        assertEquals(75.0f, latestProfileState.weightKg, 0.01f)
        assertEquals(1, latestMeasurementsState.size)

        // 5. Switch to Profile tab & open Edit Fitness Profile dialog -> verify synced name, weight & unit toggle
        composeTestRule.onNodeWithTag("tab_profile").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("edit_profile_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("profile_name_input").performScrollTo().assertTextContains("Taylor Kim")
        composeTestRule.onNodeWithTag("profile_weight_input").performScrollTo().assertTextContains("75.0")

        // Toggle unit inside Edit Fitness Profile dialog to lb
        composeTestRule.onNodeWithTag("profile_weight_unit_lb").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(latestProfileState.useMetric)
        composeTestRule.onNodeWithTag("profile_weight_input").performScrollTo().assertTextContains("165.3")

        // Legacy age input is removed from Edit Fitness Profile dialog
        composeTestRule.onNodeWithTag("profile_age_input").assertDoesNotExist()

        // Invalid name inside Profile dialog is blocked and keeps dialog open
        composeTestRule.onNodeWithTag("profile_name_input").performScrollTo().performTextReplacement("")
        composeTestRule.onNodeWithTag("save_profile_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Member name is required").performScrollTo().assertIsDisplayed()

        // Valid save closes dialog cleanly and preserves existing stored age
        val existingAge = latestProfileState.age
        composeTestRule.onNodeWithTag("profile_name_input").performScrollTo().performTextReplacement("Taylor Kim")
        composeTestRule.onNodeWithTag("save_profile_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(existingAge, latestProfileState.age)
        assertEquals(75.0f, latestProfileState.weightKg, 0.01f)
    }

    @Test
    fun `gender and all shared profile and weekly values survive room reopen restart`() = runBlocking {
        val dbName = "bodygrind_restart_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        try {
            var persistentDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            var repo = WorkoutRepository(persistentDb, context)
            persistentDb.userProfileDao().insertOrUpdateProfile(
                UserProfileEntity(id = 1, gender = "Female")
            )
            repo.saveWeeklyProgressRecord(
                memberName = "Casey Morgan",
                memberAge = 32,
                weightKg = 64.5f,
                heightCm = 168.0f,
                chestCm = 91.0f,
                armCm = 31.0f,
                waistCm = 72.0f,
                useMetric = false
            )
            repo.updateProfileWithWeeklySync(
                repo.getUserProfile().first()!!.copy(gender = "Female", fitnessGoal = "Improve Endurance")
            )
            persistentDb.close()

            // Simulate full app restart by reopening Room from disk
            persistentDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            repo = WorkoutRepository(persistentDb, context)

            val restoredProfile = repo.getUserProfile().first()
            assertNotNull(restoredProfile)
            assertEquals("Casey Morgan", restoredProfile!!.name)
            assertEquals(32, restoredProfile.age)
            assertEquals("Female", restoredProfile.gender)
            assertEquals(64.5f, restoredProfile.weightKg, 0.001f)
            assertEquals(168.0f, restoredProfile.heightCm, 0.001f)
            assertFalse(restoredProfile.useMetric)
            assertEquals("Improve Endurance", restoredProfile.fitnessGoal)

            val restoredMeasurements = repo.getAllMeasurements().first()
            assertEquals(1, restoredMeasurements.size)
            val weekly = restoredMeasurements.first().toWeeklyRecord()
            assertEquals("Casey Morgan", weekly.memberName)
            assertEquals(32, weekly.memberAge)
            assertEquals(64.5f, weekly.weightKg, 0.001f)
            assertEquals(168.0f, weekly.heightCm, 0.001f)
            persistentDb.close()
        } finally {
            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
        }
    }

    @Test
    fun `migration 9 to 10 preserves existing body_measurements and user_profile`() = runBlocking {
        val migrationDbName = "migration_9_10_test_${System.nanoTime()}.db"
        val migrationDbFile = context.getDatabasePath(migrationDbName)
        try {
            val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(migrationDbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `targetMuscles` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `defaultType` TEXT NOT NULL, `defaultCount` INTEGER NOT NULL, `instructions` TEXT NOT NULL, `tips` TEXT NOT NULL, `caloriesPerMinute` REAL NOT NULL, `animationType` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `primaryMuscles` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, `educationalDescription` TEXT NOT NULL, `functionalBenefit` TEXT NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `estimatedDurationMinutes` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `isChallenge` INTEGER NOT NULL, `totalDays` INTEGER NOT NULL, `currentDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `targetMusclesDescription` TEXT NOT NULL, `educationalOverview` TEXT NOT NULL, `assignedByTrainerId` TEXT, `assignedByTrainerName` TEXT, `trainerNote` TEXT, `startDateEpochMs` INTEGER, `remoteAssignmentId` TEXT, `isCompletedByMember` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plan_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `type` TEXT NOT NULL, `count` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sessions` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT NOT NULL, `planName` TEXT NOT NULL, `category` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `caloriesBurned` INTEGER NOT NULL, `exercisesCompleted` INTEGER NOT NULL, `totalExercises` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL, `xpAwarded` INTEGER NOT NULL DEFAULT 1)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `chestCm` REAL, `armCm` REAL, `waistCm` REAL, `bmi` REAL NOT NULL, `bmiCategory` TEXT NOT NULL, `notes` TEXT NOT NULL DEFAULT '')")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `gender` TEXT NOT NULL, `fitnessGoal` TEXT NOT NULL, `fitnessLevel` TEXT NOT NULL, `preferredDuration` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `useMetric` INTEGER NOT NULL, `soundEnabled` INTEGER NOT NULL, `voiceCuesEnabled` INTEGER NOT NULL, `vibrationEnabled` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, `reminderHour` INTEGER NOT NULL, `reminderMinute` INTEGER NOT NULL, `reminderDaysMask` INTEGER NOT NULL, `onboardingCompleted` INTEGER NOT NULL, `darkThemeMode` TEXT NOT NULL, `xpPoints` INTEGER NOT NULL, `level` INTEGER NOT NULL, `isPrivate` INTEGER NOT NULL, `accentColorHex` TEXT NOT NULL, `bodyDiagramGender` TEXT NOT NULL, `exerciseDrawingGender` TEXT NOT NULL, `gymCheckinEnabled` INTEGER NOT NULL, `aiCoachEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `badges` (`badgeId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `unlocked` INTEGER NOT NULL, `unlockedAt` INTEGER, `progressCurrent` INTEGER NOT NULL, `progressTarget` INTEGER NOT NULL, `iconName` TEXT NOT NULL, PRIMARY KEY(`badgeId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `workout_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayOfWeek` INTEGER NOT NULL, `planId` TEXT NOT NULL, `planName` TEXT NOT NULL, `timeHour` INTEGER NOT NULL, `timeMinute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `body_scans` (`scanId` TEXT NOT NULL, `userId` INTEGER NOT NULL, `scanDate` INTEGER NOT NULL, `cameraFacing` TEXT NOT NULL, `poseDetectionStatus` TEXT NOT NULL, `landmarkConfidence` REAL NOT NULL, `scanNotes` TEXT NOT NULL, `bodyVisibilityStatus` TEXT NOT NULL, PRIMARY KEY(`scanId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `bmi_records` (`bmiRecordId` TEXT NOT NULL, `userId` INTEGER NOT NULL, `heightCm` REAL NOT NULL, `weightKg` REAL NOT NULL, `bmiValue` REAL NOT NULL, `bmiCategory` TEXT NOT NULL, `assessmentDate` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `linkedScanId` TEXT, PRIMARY KEY(`bmiRecordId`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `body_pose_landmarks` (`landmarkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scanId` TEXT NOT NULL, `landmarkName` TEXT NOT NULL, `normalizedX` REAL NOT NULL, `normalizedY` REAL NOT NULL, `normalizedZ` REAL, `visibilityConfidence` REAL)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT, `title` TEXT NOT NULL, `startDate` TEXT NOT NULL, `timeHour` INTEGER NOT NULL, `timeMinute` INTEGER NOT NULL, `repeatType` TEXT NOT NULL, `repeatDaysMask` INTEGER NOT NULL, `repeatUntil` TEXT, `reminderMinutesBefore` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `note` TEXT)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `calendar_event_exceptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `occurrenceDate` TEXT NOT NULL, `type` TEXT NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `calendar_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_startDate` ON `calendar_events` (`startDate`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_isEnabled` ON `calendar_events` (`isEnabled`)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_event_exceptions_eventId_occurrenceDate` ON `calendar_event_exceptions` (`eventId`, `occurrenceDate`)")
                        db.execSQL("CREATE TABLE IF NOT EXISTS `pending_sync_ops` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `opKey` TEXT NOT NULL, `opType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `createdAtMs` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, `lastAttemptMs` INTEGER, `lastError` TEXT)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_sync_ops_opKey` ON `pending_sync_ops` (`opKey`)")

                        db.execSQL("INSERT INTO `user_profile` VALUES (1, 'Legacy Athlete', 28, 'Male', 'Build Muscle', 'Intermediate', 30, 76.5, 178.0, 1, 1, 1, 1, 0, 8, 0, 127, 1, 'SYSTEM', 400, 2, 0, '#E50914', 'MALE', 'MALE', 1, 0)")
                        db.execSQL("INSERT INTO `body_measurements` (`id`, `timestamp`, `weightKg`, `heightCm`, `chestCm`, `armCm`, `waistCm`, `bmi`, `bmiCategory`, `notes`) VALUES (1, 1700000000000, 76.5, 178.0, 99.0, 36.0, 82.0, 24.1, 'Normal weight', 'Legacy measurement')")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()

            FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }

            val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, migrationDbName)
                .addMigrations(AppDatabase.MIGRATION_9_10, AppDatabase.MIGRATION_10_11)
                .allowMainThreadQueries()
                .build()

            try {
                val restoredProfile = migratedDb.userProfileDao().getUserProfileSync()
                assertNotNull(restoredProfile)
                assertEquals("Legacy Athlete", restoredProfile!!.name)
                assertEquals(76.5f, restoredProfile.weightKg, 0.001f)

                val restoredMeasurements = migratedDb.bodyMeasurementDao().getAllMeasurements().first()
                assertEquals(1, restoredMeasurements.size)
                assertEquals(76.5f, restoredMeasurements.first().weightKg, 0.001f)
                assertEquals("Legacy measurement", restoredMeasurements.first().notes)
                assertEquals(null, restoredMeasurements.first().weekKey)
            } finally {
                migratedDb.close()
            }
        } finally {
            migrationDbFile.delete()
            File(migrationDbFile.path + "-wal").delete()
            File(migrationDbFile.path + "-shm").delete()
        }
    }
}
