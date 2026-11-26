package com.example.data.gamification

import com.example.utils.ManilaDateTime
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class LevelSpec(
    val level: Int,
    val title: String,
    val minXp: Int,
    val nextLevelXp: Int?
)

data class LevelProgressInfo(
    val level: Int,
    val title: String,
    val currentXp: Int,
    val currentLevelMinXp: Int,
    val nextLevelXp: Int?,
    val xpIntoLevel: Int,
    val xpNeededForNextLevel: Int,
    val progressFraction: Float,
    val isMaxLevel: Boolean
)

data class LocalCalendarDay(
    val year: Int,
    val month: Int, // 0-based Calendar month (0..11)
    val dayOfMonth: Int
) : Comparable<LocalCalendarDay> {

    override fun compareTo(other: LocalCalendarDay): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return dayOfMonth.compareTo(other.dayOfMonth)
    }

    fun plusDays(days: Int, timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE): LocalCalendarDay {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, days)
        }
        return LocalCalendarDay(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun minusDays(days: Int, timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE): LocalCalendarDay {
        return plusDays(-days, timeZone)
    }

    fun toMiddayMillis(timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month, dayOfMonth, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

data class StreakDayStatus(
    val day: LocalCalendarDay,
    val dayNumber: Int,
    val weekdayShort: String,
    val monthShort: String,
    val workoutCount: Int,
    val isToday: Boolean
) {
    val hasWorkout: Boolean get() = workoutCount > 0
}

data class StreakSummary(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val hasWorkedOutToday: Boolean = false,
    val last30Days: List<StreakDayStatus> = emptyList()
)

object GamificationRules {

    const val DEFAULT_DISPLAY_NAME = "Fitness Athlete"
    const val DEFAULT_FITNESS_GOAL = "Build Muscle"

    const val BASE_WORKOUT_XP = 100
    const val XP_PER_MINUTE = 2

    val LEVELS: List<LevelSpec> = listOf(
        LevelSpec(level = 1, title = "Rookie Athlete", minXp = 0, nextLevelXp = 250),
        LevelSpec(level = 2, title = "Active Starter", minXp = 250, nextLevelXp = 600),
        LevelSpec(level = 3, title = "Fitness Enthusiast", minXp = 600, nextLevelXp = 1200),
        LevelSpec(level = 4, title = "Disciplined Warrior", minXp = 1200, nextLevelXp = 2000),
        LevelSpec(level = 5, title = "Iron Beast", minXp = 2000, nextLevelXp = 3200),
        LevelSpec(level = 6, title = "Powerhouse", minXp = 3200, nextLevelXp = 5000),
        LevelSpec(level = 7, title = "Grandmaster Athlete", minXp = 5000, nextLevelXp = null)
    )

    val XP_RULES_DESCRIPTIONS: List<String> = listOf(
        "$BASE_WORKOUT_XP Base XP for every finished workout session",
        "+$XP_PER_MINUTE Bonus XP for each full minute of active training",
        "No double awards: each finished session grants XP once",
        "Earn badges for workout count, streaks, custom plans, and calories burned"
    )

    fun calculateWorkoutXp(durationSeconds: Int): Int {
        val safeDuration = durationSeconds.coerceAtLeast(0)
        val minutes = safeDuration / 60
        return BASE_WORKOUT_XP + (minutes * XP_PER_MINUTE)
    }

    fun levelForXp(xpPoints: Int): Int {
        val safeXp = xpPoints.coerceAtLeast(0)
        return LEVELS.lastOrNull { safeXp >= it.minXp }?.level ?: 1
    }

    fun titleForLevel(level: Int): String {
        return LEVELS.firstOrNull { it.level == level }?.title
            ?: LEVELS.last().title
    }

    fun levelInfoForXp(xpPoints: Int, storedLevel: Int = 1): LevelProgressInfo {
        val safeXp = xpPoints.coerceAtLeast(0)
        val computedSpec = LEVELS.lastOrNull { safeXp >= it.minXp } ?: LEVELS.first()
        // Never downgrade a user's stored level if it was already higher
        val effectiveLevel = maxOf(computedSpec.level, storedLevel.coerceIn(1, LEVELS.last().level))
        val effectiveSpec = LEVELS.first { it.level == effectiveLevel }

        val nextXp = effectiveSpec.nextLevelXp
        return if (nextXp == null) {
            LevelProgressInfo(
                level = effectiveSpec.level,
                title = effectiveSpec.title,
                currentXp = safeXp,
                currentLevelMinXp = effectiveSpec.minXp,
                nextLevelXp = null,
                xpIntoLevel = (safeXp - effectiveSpec.minXp).coerceAtLeast(0),
                xpNeededForNextLevel = 0,
                progressFraction = 1f,
                isMaxLevel = true
            )
        } else {
            val span = (nextXp - effectiveSpec.minXp).coerceAtLeast(1)
            val into = (safeXp - effectiveSpec.minXp).coerceAtLeast(0)
            val remaining = (nextXp - safeXp).coerceAtLeast(0)
            val fraction = (into.toFloat() / span.toFloat()).coerceIn(0f, 1f)
            LevelProgressInfo(
                level = effectiveSpec.level,
                title = effectiveSpec.title,
                currentXp = safeXp,
                currentLevelMinXp = effectiveSpec.minXp,
                nextLevelXp = nextXp,
                xpIntoLevel = into,
                xpNeededForNextLevel = remaining,
                progressFraction = fraction,
                isMaxLevel = false
            )
        }
    }

    fun toLocalCalendarDay(
        timestampMillis: Long,
        timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE
    ): LocalCalendarDay {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = timestampMillis
        }
        return LocalCalendarDay(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun computeStreakSummary(
        completedTimestamps: List<Long>,
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE
    ): StreakSummary {
        val today = toLocalCalendarDay(nowMillis, timeZone)
        val yesterday = today.minusDays(1, timeZone)

        val countsByDay = completedTimestamps
            .map { toLocalCalendarDay(it, timeZone) }
            .groupingBy { it }
            .eachCount()

        val hasWorkedOutToday = (countsByDay[today] ?: 0) > 0

        // Current consecutive streak:
        // Active if today has a workout OR yesterday has a workout (not lost before today ends).
        var currentStreak = 0
        var cursor: LocalCalendarDay? = when {
            countsByDay.containsKey(today) -> today
            countsByDay.containsKey(yesterday) -> yesterday
            else -> null
        }
        while (cursor != null && countsByDay.containsKey(cursor)) {
            currentStreak++
            cursor = cursor.minusDays(1, timeZone)
        }

        // Longest streak across all recorded sessions (excluding any future-dated anomalies > today)
        val uniqueDaysAsc = countsByDay.keys.sorted()
        var longestStreak = 0
        var runningStreak = 0
        var prevDay: LocalCalendarDay? = null
        for (day in uniqueDaysAsc) {
            if (prevDay != null && day == prevDay.plusDays(1, timeZone)) {
                runningStreak++
            } else {
                runningStreak = 1
            }
            if (runningStreak > longestStreak) {
                longestStreak = runningStreak
            }
            prevDay = day
        }

        val weekdayFormat = SimpleDateFormat("EEE", Locale.US).apply { this.timeZone = timeZone }
        val monthFormat = SimpleDateFormat("MMM", Locale.US).apply { this.timeZone = timeZone }

        val last30Days = (29 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo, timeZone)
            val middayMs = day.toMiddayMillis(timeZone)
            StreakDayStatus(
                day = day,
                dayNumber = day.dayOfMonth,
                weekdayShort = weekdayFormat.format(middayMs).take(1),
                monthShort = monthFormat.format(middayMs),
                workoutCount = countsByDay[day] ?: 0,
                isToday = daysAgo == 0
            )
        }

        return StreakSummary(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            hasWorkedOutToday = hasWorkedOutToday,
            last30Days = last30Days
        )
    }
}
