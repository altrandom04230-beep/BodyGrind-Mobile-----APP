package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.coaching.CoachingMappers
import com.example.data.coaching.CoachingSessionPrefs
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PendingSyncEntity
import com.example.data.trainer.AppRole
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoachingIntegrationTest {

    private lateinit var context: Context
    private val testDbName = "migration_7_8_test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
    }

    @After
    fun teardown() {
        AppDatabase.resetInstanceForTesting()
        context.deleteDatabase(testDbName)
    }

    @Test
    fun `migration 7 to 8 preserves workout_plans and creates pending_sync_ops`() = runBlocking {
        // 1. Create a v7 schema DB and insert an existing workout_plans row
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `exercises` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `targetMuscles` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `defaultType` TEXT NOT NULL, `defaultCount` INTEGER NOT NULL, `instructions` TEXT NOT NULL, `tips` TEXT NOT NULL, `caloriesPerMinute` REAL NOT NULL, `animationType` TEXT NOT NULL, `isFavorite` INTEGER NOT NULL, `primaryMuscles` TEXT NOT NULL, `secondaryMuscles` TEXT NOT NULL, `educationalDescription` TEXT NOT NULL, `functionalBenefit` TEXT NOT NULL, PRIMARY KEY(`id`))
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `workout_plans` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `estimatedDurationMinutes` INTEGER NOT NULL, `isCustom` INTEGER NOT NULL, `isChallenge` INTEGER NOT NULL, `totalDays` INTEGER NOT NULL, `currentDay` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `targetMusclesDescription` TEXT NOT NULL, `educationalOverview` TEXT NOT NULL, PRIMARY KEY(`id`))
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `workout_plan_exercises` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT NOT NULL, `exerciseId` TEXT NOT NULL, `orderIndex` INTEGER NOT NULL, `type` TEXT NOT NULL, `count` INTEGER NOT NULL, `restSeconds` INTEGER NOT NULL)
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `workout_sessions` (`sessionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT NOT NULL, `planName` TEXT NOT NULL, `category` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `completedAt` INTEGER NOT NULL, `durationSeconds` INTEGER NOT NULL, `caloriesBurned` INTEGER NOT NULL, `exercisesCompleted` INTEGER NOT NULL, `totalExercises` INTEGER NOT NULL, `isFinished` INTEGER NOT NULL)
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `body_measurements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `chestCm` REAL, `armCm` REAL, `waistCm` REAL, `bmi` REAL NOT NULL, `bmiCategory` TEXT NOT NULL, `notes` TEXT NOT NULL)
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `user_profile` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `age` INTEGER NOT NULL, `gender` TEXT NOT NULL, `fitnessGoal` TEXT NOT NULL, `fitnessLevel` TEXT NOT NULL, `preferredDuration` INTEGER NOT NULL, `weightKg` REAL NOT NULL, `heightCm` REAL NOT NULL, `useMetric` INTEGER NOT NULL, `soundEnabled` INTEGER NOT NULL, `voiceCuesEnabled` INTEGER NOT NULL, `vibrationEnabled` INTEGER NOT NULL, `reminderEnabled` INTEGER NOT NULL, `reminderHour` INTEGER NOT NULL, `reminderMinute` INTEGER NOT NULL, `reminderDaysMask` INTEGER NOT NULL, `onboardingCompleted` INTEGER NOT NULL, `darkThemeMode` TEXT NOT NULL, `xpPoints` INTEGER NOT NULL, `level` INTEGER NOT NULL, `isPrivate` INTEGER NOT NULL, `accentColorHex` TEXT NOT NULL, `bodyDiagramGender` TEXT NOT NULL, `exerciseDrawingGender` TEXT NOT NULL, `gymCheckinEnabled` INTEGER NOT NULL, `aiCoachEnabled` INTEGER NOT NULL, PRIMARY KEY(`id`))
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `badges` (`badgeId` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `category` TEXT NOT NULL, `unlocked` INTEGER NOT NULL, `unlockedAt` INTEGER, `progressCurrent` INTEGER NOT NULL, `progressTarget` INTEGER NOT NULL, `iconName` TEXT NOT NULL, PRIMARY KEY(`badgeId`))
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `workout_schedules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dayOfWeek` INTEGER NOT NULL, `planId` TEXT NOT NULL, `planName` TEXT NOT NULL, `timeHour` INTEGER NOT NULL, `timeMinute` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL)
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `body_scans` (`scanId` TEXT NOT NULL, `userId` INTEGER NOT NULL, `scanDate` INTEGER NOT NULL, `cameraFacing` TEXT NOT NULL, `poseDetectionStatus` TEXT NOT NULL, `landmarkConfidence` REAL NOT NULL, `scanNotes` TEXT NOT NULL, `bodyVisibilityStatus` TEXT NOT NULL, PRIMARY KEY(`scanId`))
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `bmi_records` (`bmiRecordId` TEXT NOT NULL, `userId` INTEGER NOT NULL, `heightCm` REAL NOT NULL, `weightKg` REAL NOT NULL, `bmiValue` REAL NOT NULL, `bmiCategory` TEXT NOT NULL, `assessmentDate` INTEGER NOT NULL, `sourceType` TEXT NOT NULL, `linkedScanId` TEXT, PRIMARY KEY(`bmiRecordId`))
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `body_pose_landmarks` (`landmarkId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `scanId` TEXT NOT NULL, `landmarkName` TEXT NOT NULL, `normalizedX` REAL NOT NULL, `normalizedY` REAL NOT NULL, `normalizedZ` REAL, `visibilityConfidence` REAL)
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `calendar_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` TEXT, `title` TEXT NOT NULL, `startDate` TEXT NOT NULL, `timeHour` INTEGER NOT NULL, `timeMinute` INTEGER NOT NULL, `repeatType` TEXT NOT NULL, `repeatDaysMask` INTEGER NOT NULL, `repeatUntil` TEXT, `reminderMinutesBefore` INTEGER NOT NULL, `isEnabled` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `note` TEXT)
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `calendar_event_exceptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `eventId` INTEGER NOT NULL, `occurrenceDate` TEXT NOT NULL, `type` TEXT NOT NULL, FOREIGN KEY(`eventId`) REFERENCES `calendar_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_startDate` ON `calendar_events` (`startDate`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_isEnabled` ON `calendar_events` (`isEnabled`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_event_exceptions_eventId_occurrenceDate` ON `calendar_event_exceptions` (`eventId`, `occurrenceDate`)")

                    db.execSQL("""
                        INSERT INTO `workout_plans` (`id`, `name`, `description`, `category`, `difficulty`, `estimatedDurationMinutes`, `isCustom`, `isChallenge`, `totalDays`, `currentDay`, `createdAt`, `targetMusclesDescription`, `educationalOverview`)
                        VALUES ('custom_user_1', 'My Saved Routine', 'Existing user plan', 'Full Body', 'Intermediate', 25, 1, 0, 1, 1, 1700000000000, 'Full body', 'Overview')
                    """.trimIndent())
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            helper.writableDatabase // triggers v7 creation + seed insert
        }

        // 2. Open with Room at current version (v11) + MIGRATION_7_8 -> MIGRATION_10_11
        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11
            )
            .allowMainThreadQueries()
            .build()

        try {
            val savedPlan = migratedDb.workoutPlanDao().getPlanById("custom_user_1")
            assertNotNull("Existing v7 workout plan must be preserved", savedPlan)
            assertEquals("My Saved Routine", savedPlan!!.name)
            assertNull(savedPlan.assignedByTrainerId)
            assertNull(savedPlan.remoteAssignmentId)
            assertFalse(savedPlan.isCompletedByMember)

            // Verify pending_sync_ops works with unique opKey replacement
            migratedDb.pendingSyncDao().upsert(
                PendingSyncEntity(opKey = "SYNC_WORKOUTS", opType = "SYNC_WORKOUTS", payloadJson = "{\"a\":1}")
            )
            migratedDb.pendingSyncDao().upsert(
                PendingSyncEntity(opKey = "SYNC_WORKOUTS", opType = "SYNC_WORKOUTS", payloadJson = "{\"a\":2}")
            )
            val ops = migratedDb.pendingSyncDao().getAllSync()
            assertEquals(1, ops.size)
            assertEquals("{\"a\":2}", ops.first().payloadJson)
        } finally {
            migratedDb.close()
        }
    }

    @Test
    fun `invite payload parsing and pairId formatting work accurately`() {
        assertEquals("BG-TRAINER:BG-ABC123", CoachingMappers.buildInviteQrPayload("bg-abc123"))
        assertEquals("BG-ABC123", CoachingMappers.parseInviteCodeFromInput("BG-TRAINER:bg-abc123"))
        assertEquals("BG-ABC123", CoachingMappers.parseInviteCodeFromInput("  bg-abc123 "))
        assertEquals("t1__m1", CoachingMappers.pairId("t1", "m1"))
    }

    @Test
    fun `coaching session prefs stores and retrieves role and invite code per uid`() {
        CoachingSessionPrefs.saveUserMeta(context, "uid_trainer", AppRole.TRAINER, "Coach Sam")
        CoachingSessionPrefs.saveInviteCode(context, "uid_trainer", "BG-XYZ999")
        CoachingSessionPrefs.saveUserMeta(context, "uid_member", AppRole.MEMBER, "Alex")

        assertEquals(AppRole.TRAINER, CoachingSessionPrefs.getSavedRole(context, "uid_trainer"))
        assertEquals("Coach Sam", CoachingSessionPrefs.getSavedName(context, "uid_trainer"))
        assertEquals("BG-XYZ999", CoachingSessionPrefs.getSavedInviteCode(context, "uid_trainer"))
        assertEquals(AppRole.MEMBER, CoachingSessionPrefs.getSavedRole(context, "uid_member"))
    }

    @Test
    fun `CoachingSyncHealth tracks permission limited uids and formats PERMISSION_DENIED`() {
        val uid = "4xrhtzQdNzfuYRXVbFvCYfOq8tf1"
        com.example.data.coaching.CoachingSyncHealth.clearUserWritePermissionLimited(uid)
        assertFalse(com.example.data.coaching.CoachingSyncHealth.hasUserDocWritePermissionError(uid))

        com.example.data.coaching.CoachingSyncHealth.markUserWritePermissionLimited(uid)
        assertTrue(com.example.data.coaching.CoachingSyncHealth.hasUserDocWritePermissionError(uid))
        assertNotNull(com.example.data.coaching.CoachingSyncHealth.rulesPublishWarning.value)

        val permEx = com.google.firebase.firestore.FirebaseFirestoreException(
            "PERMISSION_DENIED: Missing or insufficient permissions.",
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
        )
        val friendly = com.example.data.coaching.toFriendlyFirestoreMessage(permEx)
        assertTrue(friendly.contains("firestore.rules", ignoreCase = true))

        com.example.data.coaching.CoachingSyncHealth.clearUserWritePermissionLimited(uid)
        assertFalse(com.example.data.coaching.CoachingSyncHealth.hasUserDocWritePermissionError(uid))
    }
}
