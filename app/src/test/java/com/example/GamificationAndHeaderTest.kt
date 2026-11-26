package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.gamification.GamificationRules
import com.example.data.local.AppDatabase
import com.example.data.local.DataSeeder
import com.example.data.local.entity.BadgeEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.repository.WorkoutRepository
import com.example.ui.home.HomeScreen
import com.example.ui.theme.HomeWorkoutTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GamificationAndHeaderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private val migrationDbName = "migration_8_9_test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(migrationDbName)
    }

    @After
    fun teardown() {
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase(migrationDbName)
    }

    private fun epochAtLocal(
        year: Int,
        month: Int, // 0-based (Calendar.MARCH, etc.)
        day: Int,
        hour: Int,
        minute: Int,
        timeZone: TimeZone
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `streak handles empty, same-day duplicates, yesterday preservation, and missed day reset`() {
        val tz = TimeZone.getTimeZone("America/Los_Angeles")
        val now = epochAtLocal(2026, Calendar.NOVEMBER, 17, 10, 0, tz)

        // 0 workouts
        val empty = GamificationRules.computeStreakSummary(emptyList(), now, tz)
        assertEquals(0, empty.currentStreak)
        assertEquals(0, empty.longestStreak)
        assertEquals(30, empty.last30Days.size)
        assertTrue(empty.last30Days.none { it.hasWorkout })
        assertTrue(empty.last30Days.last().isToday)

        // Multiple workouts on same day count once; yesterday-only keeps streak alive
        val yesterdayMorning = epochAtLocal(2026, Calendar.NOVEMBER, 16, 7, 30, tz)
        val yesterdayEvening = epochAtLocal(2026, Calendar.NOVEMBER, 16, 19, 15, tz)
        val twoDaysAgo = epochAtLocal(2026, Calendar.NOVEMBER, 15, 18, 0, tz)
        val yesterdayActive = GamificationRules.computeStreakSummary(
            listOf(twoDaysAgo, yesterdayMorning, yesterdayEvening),
            now,
            tz
        )
        assertEquals(2, yesterdayActive.currentStreak)
        assertEquals(2, yesterdayActive.longestStreak)
        assertFalse(yesterdayActive.hasWorkedOutToday)

        // Full missed day (no workout today or yesterday) resets current streak to 0, keeps longestStreak
        val olderRun = listOf(
            epochAtLocal(2026, Calendar.NOVEMBER, 10, 9, 0, tz),
            epochAtLocal(2026, Calendar.NOVEMBER, 11, 9, 0, tz),
            epochAtLocal(2026, Calendar.NOVEMBER, 12, 9, 0, tz),
            epochAtLocal(2026, Calendar.NOVEMBER, 15, 9, 0, tz)
        )
        val broken = GamificationRules.computeStreakSummary(olderRun, now, tz)
        assertEquals(0, broken.currentStreak)
        assertEquals(3, broken.longestStreak)
    }

    @Test
    fun `streak survives DST spring-forward 23h day and fall-back 25h day`() {
        val nyTz = TimeZone.getTimeZone("America/New_York")

        // Spring forward in 2026 is March 8 (23-hour day)
        val springTimestamps = listOf(
            epochAtLocal(2026, Calendar.MARCH, 7, 21, 0, nyTz),
            epochAtLocal(2026, Calendar.MARCH, 8, 8, 0, nyTz),
            epochAtLocal(2026, Calendar.MARCH, 9, 7, 30, nyTz)
        )
        val springSummary = GamificationRules.computeStreakSummary(
            completedTimestamps = springTimestamps,
            nowMillis = epochAtLocal(2026, Calendar.MARCH, 9, 12, 0, nyTz),
            timeZone = nyTz
        )
        assertEquals(3, springSummary.currentStreak)
        assertEquals(3, springSummary.longestStreak)

        // Fall back in 2026 is November 1 (25-hour day)
        val fallTimestamps = listOf(
            epochAtLocal(2026, Calendar.OCTOBER, 31, 20, 0, nyTz),
            epochAtLocal(2026, Calendar.NOVEMBER, 1, 8, 0, nyTz),
            epochAtLocal(2026, Calendar.NOVEMBER, 2, 8, 0, nyTz)
        )
        val fallSummary = GamificationRules.computeStreakSummary(
            completedTimestamps = fallTimestamps,
            nowMillis = epochAtLocal(2026, Calendar.NOVEMBER, 2, 18, 0, nyTz),
            timeZone = nyTz
        )
        assertEquals(3, fallSummary.currentStreak)
        assertEquals(3, fallSummary.longestStreak)
    }

    @Test
    fun `GamificationRules XP formula and level thresholds remain consistent and monotonic`() {
        assertEquals(100, GamificationRules.calculateWorkoutXp(0))
        assertEquals(100, GamificationRules.calculateWorkoutXp(45))
        assertEquals(130, GamificationRules.calculateWorkoutXp(15 * 60))

        assertEquals(1, GamificationRules.levelForXp(0))
        assertEquals(1, GamificationRules.levelForXp(249))
        assertEquals(2, GamificationRules.levelForXp(250))
        assertEquals(3, GamificationRules.levelForXp(600))
        assertEquals(4, GamificationRules.levelForXp(1200))
        assertEquals(5, GamificationRules.levelForXp(2000))
        assertEquals(6, GamificationRules.levelForXp(3200))
        assertEquals(7, GamificationRules.levelForXp(5000))

        val info = GamificationRules.levelInfoForXp(xpPoints = 300, storedLevel = 2)
        assertEquals(2, info.level)
        assertEquals("Active Starter", info.title)
        assertEquals(300, info.xpNeededForNextLevel)

        // Never downgrade stored level
        val higherStored = GamificationRules.levelInfoForXp(xpPoints = 100, storedLevel = 3)
        assertEquals(3, higherStored.level)
    }

    @Test
    fun `migration 8 to 9 preserves existing sessions, profile, and marks old sessions xpAwarded`() = runBlocking {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(migrationDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `targetMuscles` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `defaultType` TEXT NOT NULL, `defaultCount` INTEGER NOT NULL, `instructions` TEXT NOT NULL, `tips` TEXT NOT NULL, `caloriesPerMinute` REAL NOT NULL, `animationType` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `primaryMuscles` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, `educationalDescription` TEXT NOT NULL, `functionalBenefit` TEXT NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `estimatedDurationMinutes` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `isChallenge` INTEGER NOT NULL, `totalDays` INTEGER NOT NULL, `currentDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `targetMusclesDescription` TEXT NOT NULL, `educationalOverview` TEXT NOT NULL, `assignedByTrainerId` TEXT, `assignedByTrainerName` TEXT, `trainerNote` TEXT, `startDateEpochMs` INTEGER, `remoteAssignmentId` TEXT, `isCompletedByMember` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `workout_plan_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `type` TEXT NOT NULL, `count` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `workout_sessions` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT NOT NULL, `planName` TEXT NOT NULL, `category` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `caloriesBurned` INTEGER NOT NULL, `exercisesCompleted` INTEGER NOT NULL, `totalExercises` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `chestCm` REAL, `armCm` REAL, `waistCm` REAL, `bmi` REAL NOT NULL, `bmiCategory` TEXT NOT NULL, `notes` TEXT NOT NULL)")
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

                    db.execSQL("INSERT INTO `user_profile` VALUES (1, 'Jordan', 28, 'Male', 'Lose Weight', 'Intermediate', 20, 75.0, 178.0, 1, 1, 1, 1, 1, 8, 0, 127, 1, 'DARK', 700, 3, 0, '#E50914', 'MALE', 'MALE', 1, 0)")
                    db.execSQL("INSERT INTO `workout_sessions` VALUES (10, 'plan_abs_beginner', 'Abs Sculpt', 'Abs', 1700000000000, 1700000600000, 600, 120, 5, 5, 1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { it.writableDatabase }

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, migrationDbName)
            .addMigrations(
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .allowMainThreadQueries()
            .build()

        try {
            val profile = migratedDb.userProfileDao().getUserProfileSync()
            assertNotNull(profile)
            assertEquals("Jordan", profile!!.name)
            assertEquals(700, profile.xpPoints)
            assertEquals(3, profile.level)

            val session = migratedDb.workoutSessionDao().getSessionById(10L)
            assertNotNull(session)
            assertTrue("Old sessions must default to xpAwarded=true after migration", session!!.xpAwarded)
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `completeWorkoutSession awards XP once per session, updates calories_500 badge, and syncAccountIdentity never overwrites custom name`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            DataSeeder.seedIfNeeded(db)
            val repo = WorkoutRepository(db, context)

            // 1. Account identity sync fills default name
            repo.syncAccountIdentityIfDefault("Taylor Swift", "Improve Endurance")
            val synced = db.userProfileDao().getUserProfileSync()!!
            assertEquals("Taylor Swift", synced.name)
            assertEquals("Improve Endurance", synced.fitnessGoal)

            // 2. Subsequent sync does NOT overwrite customized name
            repo.updateProfile(synced.copy(name = "Custom Name", xpPoints = 200, level = 1))
            repo.syncAccountIdentityIfDefault("Another Account", "Build Muscle")
            val preserved = db.userProfileDao().getUserProfileSync()!!
            assertEquals("Custom Name", preserved.name)
            assertEquals(200, preserved.xpPoints)

            // 3. Complete a 15-min Abs workout burning 520 kcal (earnedXp = 100 + 30 = 130 -> 330 XP -> Level 2)
            val now = System.currentTimeMillis()
            val result = repo.completeWorkoutSession(
                planId = "plan_abs_beginner",
                planName = "Abs Sculpt Beginner",
                category = "Abs",
                startedAt = now - 900_000L,
                completedAt = now,
                durationSeconds = 900,
                caloriesBurned = 520,
                exercisesCompleted = 5,
                totalExercises = 5
            )
            assertEquals(130, result.earnedXp)
            assertEquals(330, result.newTotalXp)
            assertEquals(2, result.newLevel)
            assertEquals("First Step", result.unlockedBadgeTitle)

            // Verify idempotency: re-awarding same session grants 0 extra XP and doesn't double abs_master
            val duplicateCall = repo.awardSessionRewardsIfNeeded(result.sessionId)
            assertEquals(0, duplicateCall.earnedXp)
            assertEquals(330, duplicateCall.newTotalXp)

            val caloriesBadge = db.badgeDao().getBadgeById("calories_500")
            assertTrue(caloriesBadge!!.unlocked)
            val absBadge = db.badgeDao().getBadgeById("abs_master")
            assertEquals(1, absBadge!!.progressCurrent)
        } finally {
            db.close()
        }
    }

    @Test
    fun `HomeScreen header displays defaults or live user data and opens profile, streak sheet, and level badges sheet`() {
        var openedProfile = false
        val profile = UserProfileEntity(
            name = "Alex Morgan",
            fitnessGoal = "Lose Weight",
            xpPoints = 650,
            level = 3
        )
        val badges = listOf(
            BadgeEntity("first_workout", "First Step", "Finish 1 workout", "Milestone", unlocked = true, progressCurrent = 1, progressTarget = 1, iconName = "flag"),
            BadgeEntity("workout_5", "High Five", "Finish 5 workouts", "Workout", unlocked = false, progressCurrent = 2, progressTarget = 5, iconName = "fitness_center")
        )
        val now = System.currentTimeMillis()
        val streakSummary = GamificationRules.computeStreakSummary(listOf(now, now - 86_400_000L))

        composeTestRule.setContent {
            HomeWorkoutTheme(darkTheme = false) {
                HomeScreen(
                    userProfile = profile,
                    streakDays = streakSummary.currentStreak,
                    longestStreakDays = streakSummary.longestStreak,
                    last30DaysStreak = streakSummary.last30Days,
                    recommendedPlan = WorkoutPlanEntity("p1", "Starter", "Desc", "Full Body", "Beginner", 15),
                    popularPlans = emptyList(),
                    challenges = emptyList(),
                    recentSessions = emptyList(),
                    badges = badges,
                    totalWorkouts = 2,
                    totalCalories = 320,
                    onSelectPlan = {},
                    onStartRecommended = {},
                    onCreateCustomPlan = {},
                    onViewAllPlans = {},
                    onViewProgress = {},
                    onOpenProfile = { openedProfile = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // Greeting, goal, streak, and level badge
        composeTestRule.onNodeWithText("Hello, Alex Morgan!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goal: Lose Weight").assertIsDisplayed()
        composeTestRule.onNodeWithText("${streakSummary.currentStreak} DAYS").assertIsDisplayed()
        composeTestRule.onNodeWithText("L3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fitness Enthusiast").assertIsDisplayed()
        composeTestRule.onNodeWithText("650 XP").assertIsDisplayed()

        // Tapping greeting opens Profile
        composeTestRule.onNodeWithTag("home_greeting_profile_button").performClick()
        assertTrue(openedProfile)

        // Tapping streak badge opens Streak details sheet with 30-day calendar
        composeTestRule.onNodeWithTag("home_streak_badge").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("streak_details_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Longest Streak").assertIsDisplayed()
        composeTestRule.onNodeWithTag("streak_30_day_grid").assertIsDisplayed()
        composeTestRule.onNodeWithTag("streak_day_cell_29").assertExists()
        composeTestRule.onNodeWithTag("streak_sheet_close_button").performClick()
        composeTestRule.waitForIdle()

        // Tapping level card opens Level/XP & Badges sheet with rules + earned/locked badges
        composeTestRule.onNodeWithTag("gamification_level_card").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("level_badges_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sheet_badge_first_workout").assertExists()
        composeTestRule.onNodeWithTag("sheet_badge_workout_5").assertExists()
    }
}
