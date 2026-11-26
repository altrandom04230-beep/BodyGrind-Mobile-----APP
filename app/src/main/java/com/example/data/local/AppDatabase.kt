package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.*
import com.example.data.local.entity.*

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutPlanEntity::class,
        WorkoutPlanExerciseEntity::class,
        WorkoutSessionEntity::class,
        BodyMeasurementEntity::class,
        UserProfileEntity::class,
        BadgeEntity::class,
        WorkoutScheduleEntity::class,
        BodyScanEntity::class,
        BmiRecordEntity::class,
        BodyPoseLandmarkEntity::class,
        CalendarEventEntity::class,
        CalendarEventExceptionEntity::class,
        PendingSyncEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutPlanDao(): WorkoutPlanDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun bodyMeasurementDao(): BodyMeasurementDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun badgeDao(): BadgeDao
    abstract fun workoutScheduleDao(): WorkoutScheduleDao
    abstract fun bodyScanDao(): BodyScanDao
    abstract fun bmiRecordDao(): BmiRecordDao
    abstract fun bodyPoseLandmarkDao(): BodyPoseLandmarkDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `calendar_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `planId` TEXT,
                        `title` TEXT NOT NULL,
                        `startDate` TEXT NOT NULL,
                        `timeHour` INTEGER NOT NULL,
                        `timeMinute` INTEGER NOT NULL,
                        `repeatType` TEXT NOT NULL,
                        `repeatDaysMask` INTEGER NOT NULL,
                        `repeatUntil` TEXT,
                        `reminderMinutesBefore` INTEGER NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_startDate` ON `calendar_events` (`startDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_calendar_events_isEnabled` ON `calendar_events` (`isEnabled`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `calendar_event_exceptions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `eventId` INTEGER NOT NULL,
                        `occurrenceDate` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        FOREIGN KEY(`eventId`) REFERENCES `calendar_events`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_calendar_event_exceptions_eventId_occurrenceDate` ON `calendar_event_exceptions` (`eventId`, `occurrenceDate`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `calendar_events` ADD COLUMN `note` TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `aiCoachEnabled` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `chestCm` REAL")
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `armCm` REAL")
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `assignedByTrainerId` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `assignedByTrainerName` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `trainerNote` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `startDateEpochMs` INTEGER")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `remoteAssignmentId` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `isCompletedByMember` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `pending_sync_ops` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `opKey` TEXT NOT NULL,
                        `opType` TEXT NOT NULL,
                        `payloadJson` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `attempts` INTEGER NOT NULL,
                        `lastAttemptMs` INTEGER,
                        `lastError` TEXT
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_sync_ops_opKey` ON `pending_sync_ops` (`opKey`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `xpAwarded` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `weekKey` TEXT")
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `weekNumber` INTEGER")
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `memberName` TEXT")
                db.execSQL("ALTER TABLE `body_measurements` ADD COLUMN `memberAge` INTEGER")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `cancelledReason` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `cancelledByUid` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `cancelledByName` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `cancelledByRole` TEXT")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `cancelledAtUtcMs` INTEGER")
                db.execSQL("ALTER TABLE `workout_plans` ADD COLUMN `excludeFromBilling` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'COMPLETED'")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `cancelledReason` TEXT")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `cancelledByUid` TEXT")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `cancelledByName` TEXT")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `cancelledByRole` TEXT")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `cancelledAtUtcMs` INTEGER")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `excludeFromBilling` INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE `calendar_event_exceptions` ADD COLUMN `cancelledReason` TEXT")
                db.execSQL("ALTER TABLE `calendar_event_exceptions` ADD COLUMN `cancelledByUid` TEXT")
                db.execSQL("ALTER TABLE `calendar_event_exceptions` ADD COLUMN `cancelledByName` TEXT")
                db.execSQL("ALTER TABLE `calendar_event_exceptions` ADD COLUMN `cancelledByRole` TEXT")
                db.execSQL("ALTER TABLE `calendar_event_exceptions` ADD COLUMN `cancelledAtUtcMs` INTEGER")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `birthdate` TEXT")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `memberType` TEXT NOT NULL DEFAULT 'Regular'")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `isVerified` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `discountPercent` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `discountAppliesToBilling` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `discountAppliesToPackages` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `user_profile` ADD COLUMN `discountAppliesToSessionFees` INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "home_workout_database"
                )
                    .addMigrations(
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12
                    )
                    .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
        fun setInstanceForTesting(database: AppDatabase?) {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = database
            }
        }

        fun resetInstanceForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
