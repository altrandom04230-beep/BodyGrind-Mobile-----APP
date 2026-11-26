package com.example.utils

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Single authoritative date & time utility for BodyGrind.
 *
 * Design rules:
 * 1. All persisted timestamps are stored in UTC (epoch milliseconds or Firestore [Timestamp]).
 * 2. All display, scheduling, day-boundary, and comparison logic uses `Asia/Manila` (UTC+8, no DST),
 *    completely independent of the Android device's local timezone.
 * 3. All user-facing dates on member and trainer screens use the single canonical format `"MMM dd, yyyy"`
 *    (zero-padded day, e.g., `"Mar 05, 2026"`).
 */
object ManilaDateTime {

    const val TIMEZONE_ID: String = "Asia/Manila"
    const val CANONICAL_DATE_PATTERN: String = "MMM dd, yyyy"
    const val CANONICAL_DATE_TIME_PATTERN: String = "MMM dd, yyyy • h:mm a"

    val MANILA_ZONE: ZoneId = ZoneId.of(TIMEZONE_ID)
    val MANILA_TIMEZONE: TimeZone = TimeZone.getTimeZone(TIMEZONE_ID)
    val MANILA_ZONE_ID: TimeZone get() = MANILA_TIMEZONE

    private val canonicalDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(CANONICAL_DATE_PATTERN, Locale.US)

    private val canonicalDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(CANONICAL_DATE_TIME_PATTERN, Locale.US)

    private val isoDateKeyFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_LOCAL_DATE

    private val monthYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    private val time12Formatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    /** Current UTC epoch milliseconds. */
    fun nowUtcMillis(): Long = System.currentTimeMillis()

    /** Current date in `Asia/Manila`. */
    fun todayManila(nowUtcMs: Long = nowUtcMillis()): LocalDate =
        Instant.ofEpochMilli(nowUtcMs).atZone(MANILA_ZONE).toLocalDate()

    /** Current [YearMonth] in `Asia/Manila`. */
    fun currentYearMonthManila(nowUtcMs: Long = nowUtcMillis()): YearMonth =
        YearMonth.from(todayManila(nowUtcMs))

    /** Current [ZonedDateTime] in `Asia/Manila`. */
    fun nowManilaZoned(nowUtcMs: Long = nowUtcMillis()): ZonedDateTime =
        Instant.ofEpochMilli(nowUtcMs).atZone(MANILA_ZONE)

    /** Converts a UTC epoch millisecond timestamp into its `Asia/Manila` [LocalDate]. */
    fun utcMillisToManilaDate(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(MANILA_ZONE).toLocalDate()

    /**
     * Converts a `Asia/Manila` [LocalDate] into the UTC epoch millisecond timestamp representing
     * 00:00:00.000 `Asia/Manila` on that calendar day.
     */
    fun manilaDateToStartOfDayUtcMs(date: LocalDate): Long =
        date.atStartOfDay(MANILA_ZONE).toInstant().toEpochMilli()

    /**
     * Converts a `Asia/Manila` [LocalDate] + hour/minute into UTC epoch milliseconds.
     */
    fun manilaDateTimeToUtcMs(date: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(date, LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)), MANILA_ZONE)
            .toInstant()
            .toEpochMilli()

    /** Start of today (00:00:00 Asia/Manila) expressed in UTC epoch milliseconds. */
    fun startOfTodayManilaUtcMs(nowUtcMs: Long = nowUtcMillis()): Long =
        manilaDateToStartOfDayUtcMs(todayManila(nowUtcMs))

    /** Start of the Manila day containing [utcMillis], expressed in UTC epoch milliseconds. */
    fun startOfDayManilaUtcMs(utcMillis: Long): Long =
        manilaDateToStartOfDayUtcMs(utcMillisToManilaDate(utcMillis))

    /**
     * True iff two UTC timestamps fall on the exact same calendar day in `Asia/Manila`.
     */
    fun isSameManilaDay(utcMillisA: Long, utcMillisB: Long): Boolean {
        if (utcMillisA <= 0L || utcMillisB <= 0L) return false
        return utcMillisToManilaDate(utcMillisA) == utcMillisToManilaDate(utcMillisB)
    }

    /**
     * Converts a `Asia/Manila` [LocalDate] into UTC midnight millis for Material 3 `DatePicker`.
     * Material 3 `DatePicker` ALWAYS interprets and returns calendar dates as UTC 00:00:00.
     */
    fun manilaDateToDatePickerUtcMs(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /**
     * Converts a stored UTC epoch millisecond timestamp into Material 3 `DatePicker` UTC midnight millis
     * representing its `Asia/Manila` calendar date.
     */
    fun utcMillisToDatePickerUtcMs(utcMillis: Long): Long =
        manilaDateToDatePickerUtcMs(utcMillisToManilaDate(utcMillis))

    /**
     * Converts Material 3 `DatePicker` `selectedDateMillis` (which is UTC midnight of the chosen
     * calendar date) into a [LocalDate].
     */
    fun datePickerUtcMsToManilaDate(pickerUtcMillis: Long): LocalDate =
        Instant.ofEpochMilli(pickerUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    /**
     * Converts Material 3 `DatePicker` `selectedDateMillis` into the canonical UTC epoch millis
     * representing 00:00:00 `Asia/Manila` on the selected date.
     *
     * This prevents the off-by-one day and year-boundary shift that occurred when UTC midnight from
     * Material 3 `DatePicker` was formatted in a negative-offset local timezone (e.g. UTC-8).
     */
    fun datePickerUtcMsToManilaStartOfDayUtcMs(pickerUtcMillis: Long): Long =
        manilaDateToStartOfDayUtcMs(datePickerUtcMsToManilaDate(pickerUtcMillis))

    /** Formats a [LocalDate] in the canonical `"MMM dd, yyyy"` format (e.g. `"Mar 05, 2026"`). */
    fun formatDate(date: LocalDate): String = date.format(canonicalDateFormatter)

    /**
     * Formats a UTC epoch millisecond timestamp in `Asia/Manila` using `"MMM dd, yyyy"`.
     * Returns [fallback] if [utcMillis] is null or invalid (`<= 0L`).
     */
    fun formatDate(utcMillis: Long?, fallback: String = "—"): String {
        val safe = normalizeEpochMillis(utcMillis) ?: return fallback
        return utcMillisToManilaDate(safe).format(canonicalDateFormatter)
    }

    /**
     * Formats an ISO `"yyyy-MM-dd"` date key (e.g. `"2026-03-05"`) as `"MMM dd, yyyy"`.
     */
    fun formatDateKey(dateKey: String?, fallback: String = "—"): String {
        if (dateKey.isNullOrBlank()) return fallback
        return parseDateKey(dateKey)?.let { formatDate(it) } ?: fallback
    }

    /**
     * Formats a UTC epoch millisecond timestamp in `Asia/Manila` as `"MMM dd, yyyy • h:mm a"`.
     * Used wherever both the canonical date and clock time must be displayed (e.g. cancellation timestamp).
     */
    fun formatDateTime(utcMillis: Long?, fallback: String = "—"): String {
        val safe = normalizeEpochMillis(utcMillis) ?: return fallback
        return Instant.ofEpochMilli(safe).atZone(MANILA_ZONE).format(canonicalDateTimeFormatter)
    }

    /** Formats a UTC epoch millisecond timestamp as Manila clock time (`"h:mm a"`). */
    fun formatTime12(utcMillis: Long?, fallback: String = ""): String {
        val safe = normalizeEpochMillis(utcMillis) ?: return fallback
        return Instant.ofEpochMilli(safe).atZone(MANILA_ZONE).format(time12Formatter)
    }

    /** Formats an `"HH:mm"` 24-hour slot string (e.g. `"14:30"`) into `"2:30 PM"`. */
    fun formatSlotTime(hhMm: String): String {
        val parts = hhMm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: return hhMm
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return LocalTime.of(h, m).format(time12Formatter)
    }

    /** Formats a [YearMonth] as `"MMMM yyyy"` (e.g. `"March 2026"`). */
    fun formatMonthYear(yearMonth: YearMonth): String = yearMonth.format(monthYearFormatter)

    /**
     * Formats a [LocalDate] as a relative day in Asia/Manila (UTC+8):
     * - Returns `"Today"` ONLY when [date] matches [today].
     * - Returns `"Tomorrow"` ONLY when [date] matches [today] plus 1 day.
     * - For any other date, returns `"MMM dd, yyyy"` (zero-padded day, e.g. `"Mar 05, 2026"`).
     */
    fun formatRelativeDay(
        date: LocalDate,
        today: LocalDate = todayManila()
    ): String {
        return when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> formatDate(date)
        }
    }

    /**
     * Formats a UTC epoch millisecond timestamp as a relative day in Asia/Manila (UTC+8):
     * - Returns `"Today"` ONLY when the timestamp falls on today's Manila date.
     * - Returns `"Tomorrow"` ONLY when the timestamp falls on tomorrow's Manila date.
     * - For any other date, returns `"MMM dd, yyyy"`.
     * Returns [fallback] if [utcMillis] is null or invalid (`<= 0L`).
     */
    fun formatRelativeDay(
        utcMillis: Long?,
        fallback: String = "—",
        nowUtcMs: Long = nowUtcMillis()
    ): String {
        val safe = normalizeEpochMillis(utcMillis) ?: return fallback
        val date = utcMillisToManilaDate(safe)
        val today = todayManila(nowUtcMs)
        return formatRelativeDay(date, today)
    }

    /**
     * Formats a UTC epoch millisecond timestamp as a relative date and time in Asia/Manila (UTC+8):
     * - If today in Manila: `"Today, 6:30 AM"`
     * - If tomorrow in Manila: `"Tomorrow, 6:30 AM"`
     * - Any other date: `"MMM dd, yyyy, 6:30 AM"` (e.g. `"Mar 05, 2026, 6:30 AM"`)
     * Preserves the existing 12-hour `"h:mm a"` clock style.
     */
    fun formatRelativeDateTime(
        utcMillis: Long?,
        fallback: String = "—",
        nowUtcMs: Long = nowUtcMillis()
    ): String {
        val safe = normalizeEpochMillis(utcMillis) ?: return fallback
        val zdt = Instant.ofEpochMilli(safe).atZone(MANILA_ZONE)
        val date = zdt.toLocalDate()
        val timeStr = zdt.format(time12Formatter)
        val today = todayManila(nowUtcMs)
        return when (date) {
            today -> "Today, $timeStr"
            today.plusDays(1) -> "Tomorrow, $timeStr"
            else -> "${formatDate(date)}, $timeStr"
        }
    }

    /**
     * Formats a task due date relative to Manila today:
     * - If today in Manila: `"Due Today"`
     * - If tomorrow in Manila: `"Tomorrow"`
     * - Any other date: `"MMM dd, yyyy"`
     */
    fun formatRelativeDueDay(
        date: LocalDate,
        today: LocalDate = todayManila()
    ): String {
        return when (date) {
            today -> "Due Today"
            today.plusDays(1) -> "Tomorrow"
            else -> formatDate(date)
        }
    }

    fun formatRelativeDueDay(
        utcMillis: Long?,
        fallback: String = "—",
        nowUtcMs: Long = nowUtcMillis()
    ): String {
        val safe = normalizeEpochMillis(utcMillis) ?: return fallback
        val date = utcMillisToManilaDate(safe)
        val today = todayManila(nowUtcMs)
        return formatRelativeDueDay(date, today)
    }

    /**
     * Computes a rolling past [LocalDate] in Asia/Manila, bounded to stay within the current
     * Manila calendar year (no earlier than Jan 01 of the current year) so demo and seed data
     * never shows a past year when the app is run on any day of the year.
     */
    fun rollingPastManilaDate(
        daysAgo: Long,
        today: LocalDate = todayManila()
    ): LocalDate {
        val safeDays = daysAgo.coerceAtLeast(0L)
        val target = today.minusDays(safeDays)
        val startOfYear = LocalDate.of(today.year, 1, 1)
        return if (target.isBefore(startOfYear)) {
            val dayOfYear = today.dayOfYear
            if (dayOfYear <= 1) {
                startOfYear
            } else {
                val clampedOffset = (safeDays % (dayOfYear - 1)) + 1
                today.minusDays(clampedOffset)
            }
        } else {
            target
        }
    }

    /**
     * Computes a rolling future [LocalDate] in Asia/Manila, bounded to stay within the current
     * Manila calendar year (no later than Dec 31 of the current year).
     */
    fun rollingFutureManilaDate(
        daysAhead: Long,
        today: LocalDate = todayManila()
    ): LocalDate {
        val safeDays = daysAhead.coerceAtLeast(0L)
        val target = today.plusDays(safeDays)
        val endOfYear = LocalDate.of(today.year, 12, 31)
        return if (target.isAfter(endOfYear)) endOfYear else target
    }

    /** Returns ISO date key `"yyyy-MM-dd"` in `Asia/Manila`. */
    fun toDateKey(date: LocalDate): String = date.format(isoDateKeyFormatter)

    /** Returns ISO date key `"yyyy-MM-dd"` in `Asia/Manila` for a UTC epoch millisecond value. */
    fun toDateKey(utcMillis: Long): String = toDateKey(utcMillisToManilaDate(utcMillis))

    /** Parses ISO `"yyyy-MM-dd"` safely into a [LocalDate]. */
    fun parseDateKey(dateKey: String?): LocalDate? {
        if (dateKey.isNullOrBlank()) return null
        return try {
            LocalDate.parse(dateKey.trim(), isoDateKeyFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** Creates a `java.util.Calendar` anchored to `Asia/Manila` (never device default timezone). */
    fun manilaCalendar(utcMillis: Long = nowUtcMillis()): Calendar =
        Calendar.getInstance(MANILA_TIMEZONE).apply { timeInMillis = utcMillis }

    /**
     * Thread-safe helper creating a `SimpleDateFormat` pinned to `Asia/Manila` and `Locale.US`.
     */
    fun simpleDateFormat(pattern: String = CANONICAL_DATE_PATTERN): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = MANILA_TIMEZONE
        }

    /**
     * Normalizes an epoch numeric value to UTC milliseconds.
     * - Rejects `null` and values `<= 0L` (prevents accidental `Jan 01, 1970` rendering).
     * - Automatically converts 10-digit Unix epoch seconds (`1_000_000_000L..99_999_999_999L`) to millis.
     */
    fun normalizeEpochMillis(raw: Long?): Long? {
        if (raw == null || raw <= 0L) return null
        return if (raw in 1_000_000_000L..99_999_999_999L) raw * 1000L else raw
    }

    /**
     * Safely parses any Firestore timestamp representation ([Timestamp], [Date], [Number] millis/seconds,
     * or ISO-8601 string) into UTC epoch milliseconds without ever returning `0L` (`1970`).
     */
    fun parseFirestoreUtcMillis(raw: Any?): Long? {
        return when (raw) {
            null -> null
            is Timestamp -> normalizeEpochMillis(raw.toDate().time)
            is Date -> normalizeEpochMillis(raw.time)
            is Number -> normalizeEpochMillis(raw.toLong())
            is String -> {
                val trimmed = raw.trim()
                trimmed.toLongOrNull()?.let { return normalizeEpochMillis(it) }
                try {
                    normalizeEpochMillis(Instant.parse(trimmed).toEpochMilli())
                } catch (_: Throwable) {
                    parseDateKey(trimmed)?.let { manilaDateToStartOfDayUtcMs(it) }
                }
            }
            else -> null
        }
    }

    /**
     * Converts a `startDateEpochMs` saved by legacy clients (which may have stored UTC midnight
     * from Material 3 `DatePicker` instead of Manila midnight) cleanly into its intended `Asia/Manila`
     * calendar date.
     *
     * Note: UTC midnight (`00:00:00Z`) is `08:00:00` in `Asia/Manila` (same calendar date!).
     * Manila midnight (`00:00:00+08:00`) is `16:00:00Z` the previous UTC day, which converts back
     * to the exact same `Asia/Manila` date. So `utcMillisToManilaDate` preserves BOTH new Manila-aligned
     * timestamps and legacy UTC-midnight DatePicker timestamps accurately!
     */
    fun assignedPlanManilaDate(startDateEpochMs: Long): LocalDate? {
        val safe = normalizeEpochMillis(startDateEpochMs) ?: return null
        return utcMillisToManilaDate(safe)
    }

    fun dayOfWeekLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Day $dayOfWeek"
    }

    fun dayOfWeekShortLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "D$dayOfWeek"
    }

    /**
     * Calculates the age in years as of today in Asia/Manila (UTC+8) from an ISO `"yyyy-MM-dd"` birthdate.
     * Returns defaultAge (default 25) if birthdate is null, unparseable, or in the future.
     */
    fun calculateAge(birthdateStr: String?, today: LocalDate = todayManila(), defaultAge: Int = 25): Int {
        if (birthdateStr.isNullOrBlank()) return defaultAge
        val birthDate = parseDateKey(birthdateStr) ?: return defaultAge
        if (birthDate.isAfter(today)) return defaultAge
        return java.time.Period.between(birthDate, today).years
    }

    /**
     * Formats an ISO `"yyyy-MM-dd"` birthdate string into canonical `"MMM dd, yyyy"` format (e.g. `"Mar 05, 2026"`).
     */
    fun formatBirthdate(birthdateStr: String?, fallback: String = "Not set"): String {
        if (birthdateStr.isNullOrBlank()) return fallback
        val date = parseDateKey(birthdateStr) ?: return fallback
        return formatDate(date)
    }

    /**
     * Validates that a birthdate string represents a realistic member age (10..120 years).
     */
    fun isValidBirthdate(birthdateStr: String?, today: LocalDate = todayManila()): Boolean {
        if (birthdateStr.isNullOrBlank()) return false
        val birthDate = parseDateKey(birthdateStr) ?: return false
        if (birthDate.isAfter(today)) return false
        val age = java.time.Period.between(birthDate, today).years
        return age in 10..120
    }
}
