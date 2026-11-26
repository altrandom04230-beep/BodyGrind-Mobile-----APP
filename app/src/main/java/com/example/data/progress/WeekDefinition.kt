package com.example.data.progress

import com.example.utils.ManilaDateTime
import com.example.utils.UnitConverter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Single authoritative definition of a calendar week and biometric validation rules
 * shared across Profile, Progress, BMI, and Coaching sync.
 *
 * Week definition:
 * - A week runs Monday 00:00:00.000 through Sunday 23:59:59.999 in the device's local timezone.
 * - Identified by the local date of its Monday start (`YYYY-MM-DD`, e.g., "2026-11-16"),
 *   which avoids Dec/Jan year-boundary ambiguity present in Calendar.YEAR + WEEK_OF_YEAR.
 */
object WeekDefinition {

    const val MIN_AGE = 10
    const val MAX_AGE = 100
    const val MIN_WEIGHT_KG = 20f
    const val MAX_WEIGHT_KG = 300f
    const val MIN_HEIGHT_CM = 100f
    const val MAX_HEIGHT_CM = 250f
    const val MIN_CHEST_CM = 30f
    const val MAX_CHEST_CM = 250f
    const val MIN_ARM_CM = 10f
    const val MAX_ARM_CM = 100f
    const val MIN_WAIST_CM = 30f
    const val MAX_WAIST_CM = 250f

    fun startOfWeekMillis(
        timestampMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE
    ): Long {
        val cal = Calendar.getInstance(timeZone).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = timestampMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
        }
        return cal.timeInMillis
    }

    /**
     * Canonical local week identifier formatted as "yyyy-MM-dd" of the Monday starting that week.
     */
    fun weekKeyFor(
        timestampMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE
    ): String {
        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = startOfWeekMillis(timestampMs, timeZone)
        }
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * Returns (isoWeekYear, isoWeekOfYear) using Thursday of the Monday-anchored local week.
     */
    fun isoYearAndWeekFor(
        timestampMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE
    ): Pair<Int, Int> {
        val cal = Calendar.getInstance(timeZone).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = startOfWeekMillis(timestampMs, timeZone)
            add(Calendar.DAY_OF_YEAR, 3) // Thursday determines ISO week-year safely
        }
        return Pair(cal.get(Calendar.YEAR), cal.get(Calendar.WEEK_OF_YEAR))
    }

    fun isSameWeek(
        timestampA: Long,
        timestampB: Long = System.currentTimeMillis(),
        timeZone: TimeZone = ManilaDateTime.MANILA_TIMEZONE
    ): Boolean = weekKeyFor(timestampA, timeZone) == weekKeyFor(timestampB, timeZone)
}

data class ValidatedProfileBiometrics(
    val cleanName: String,
    val age: Int,
    val weightKg: Float,
    val heightCm: Float,
    val errors: Map<String, String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class ValidatedWeeklyProgressInputs(
    val cleanName: String,
    val age: Int,
    val weightKg: Float,
    val heightCm: Float,
    val chestCm: Float,
    val armCm: Float,
    val waistCm: Float,
    val errors: Map<String, String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object BiometricValidator {

    /**
     * Formats canonical metric value into display/input text using [UnitConverter] only.
     */
    fun formatWeightInput(weightKg: Float, useMetric: Boolean): String {
        val displayVal = if (useMetric) weightKg else UnitConverter.kgToLb(weightKg)
        return String.format(Locale.US, "%.1f", displayVal)
    }

    fun formatHeightInput(heightCm: Float, useMetric: Boolean): String {
        val displayVal = if (useMetric) heightCm else UnitConverter.cmToInches(heightCm)
        return String.format(Locale.US, "%.1f", displayVal)
    }

    fun formatCircumferenceInput(cm: Float, useMetric: Boolean): String {
        val displayVal = if (useMetric) cm else UnitConverter.cmToInches(cm)
        return String.format(Locale.US, "%.1f", displayVal)
    }

    /**
     * Resolves canonical metric weight without introducing round-trip rounding drift when
     * the user did not manually edit the field since last canonical load or unit toggle.
     */
    fun resolveMetricWeightOrNull(
        rawInput: String,
        useMetric: Boolean,
        canonicalWeightKg: Float?,
        isDirty: Boolean
    ): Float? {
        val trimmed = rawInput.trim()
        val parsed = trimmed.toFloatOrNull() ?: return null
        if (!isDirty && canonicalWeightKg != null) {
            val expectedFormatted = formatWeightInput(canonicalWeightKg, useMetric).toFloatOrNull()
            if (expectedFormatted != null && abs(parsed - expectedFormatted) < 0.05f) {
                return canonicalWeightKg
            }
        }
        return if (useMetric) parsed else UnitConverter.lbToKg(parsed)
    }

    fun resolveMetricLengthOrNull(
        rawInput: String,
        useMetric: Boolean,
        canonicalCm: Float?,
        isDirty: Boolean
    ): Float? {
        val trimmed = rawInput.trim()
        val parsed = trimmed.toFloatOrNull() ?: return null
        if (!isDirty && canonicalCm != null) {
            val expectedFormatted = formatHeightInput(canonicalCm, useMetric).toFloatOrNull()
            if (expectedFormatted != null && abs(parsed - expectedFormatted) < 0.05f) {
                return canonicalCm
            }
        }
        return if (useMetric) parsed else UnitConverter.inchesToCm(parsed)
    }

    fun validateProfileInputs(
        nameInput: String,
        ageInput: String,
        weightInput: String,
        heightInput: String,
        useMetric: Boolean,
        canonicalWeightKg: Float? = null,
        canonicalHeightCm: Float? = null,
        weightDirty: Boolean = true,
        heightDirty: Boolean = true,
        birthdateInput: String? = null
    ): ValidatedProfileBiometrics {
        val errors = mutableMapOf<String, String>()

        val cleanName = nameInput.trim()
        if (cleanName.isEmpty()) {
            errors["name"] = "Member name is required"
        }

        val parsedAge: Int? = if (!birthdateInput.isNullOrBlank()) {
            if (ManilaDateTime.isValidBirthdate(birthdateInput.trim())) {
                ManilaDateTime.calculateAge(birthdateInput.trim())
            } else {
                errors["birthdate"] = "Please select a valid birthdate (must be between 10 and 120 years old)"
                null
            }
        } else {
            val a = ageInput.trim().toIntOrNull()
            if (a == null || a !in WeekDefinition.MIN_AGE..WeekDefinition.MAX_AGE) {
                errors["age"] = "Age must be between ${WeekDefinition.MIN_AGE} and ${WeekDefinition.MAX_AGE}"
            }
            a
        }

        val weightKg = resolveMetricWeightOrNull(weightInput, useMetric, canonicalWeightKg, weightDirty)
        if (weightKg == null || !weightKg.isFinite() ||
            weightKg < WeekDefinition.MIN_WEIGHT_KG || weightKg > WeekDefinition.MAX_WEIGHT_KG
        ) {
            errors["weight"] = if (useMetric) {
                "Weight must be between 20 and 300 kg"
            } else {
                "Weight must be between 44.1 and 661.4 lb"
            }
        }

        val heightCm = resolveMetricLengthOrNull(heightInput, useMetric, canonicalHeightCm, heightDirty)
        if (heightCm == null || !heightCm.isFinite() ||
            heightCm < WeekDefinition.MIN_HEIGHT_CM || heightCm > WeekDefinition.MAX_HEIGHT_CM
        ) {
            errors["height"] = if (useMetric) {
                "Height must be between 100 and 250 cm"
            } else {
                "Height must be between 39.4 and 98.4 in"
            }
        }

        return ValidatedProfileBiometrics(
            cleanName = cleanName,
            age = parsedAge ?: 0,
            weightKg = weightKg ?: 0f,
            heightCm = heightCm ?: 0f,
            errors = errors
        )
    }

    fun validateWeeklyProgressInputs(
        nameInput: String,
        ageInput: String,
        weightInput: String,
        heightInput: String,
        chestInput: String,
        armInput: String,
        waistInput: String,
        useMetric: Boolean,
        canonicalWeightKg: Float? = null,
        canonicalHeightCm: Float? = null,
        canonicalChestCm: Float? = null,
        canonicalArmCm: Float? = null,
        canonicalWaistCm: Float? = null,
        weightDirty: Boolean = true,
        heightDirty: Boolean = true,
        chestDirty: Boolean = true,
        armDirty: Boolean = true,
        waistDirty: Boolean = true
    ): ValidatedWeeklyProgressInputs {
        val base = validateProfileInputs(
            nameInput = nameInput,
            ageInput = ageInput,
            weightInput = weightInput,
            heightInput = heightInput,
            useMetric = useMetric,
            canonicalWeightKg = canonicalWeightKg,
            canonicalHeightCm = canonicalHeightCm,
            weightDirty = weightDirty,
            heightDirty = heightDirty
        )
        val errors = base.errors.toMutableMap()

        val chestCm = resolveMetricLengthOrNull(chestInput, useMetric, canonicalChestCm, chestDirty)
        if (chestCm == null || !chestCm.isFinite() ||
            chestCm < WeekDefinition.MIN_CHEST_CM || chestCm > WeekDefinition.MAX_CHEST_CM
        ) {
            errors["chest"] = if (useMetric) {
                "Chest must be between 30 and 250 cm"
            } else {
                "Chest must be between 11.8 and 98.4 in"
            }
        }

        val armCm = resolveMetricLengthOrNull(armInput, useMetric, canonicalArmCm, armDirty)
        if (armCm == null || !armCm.isFinite() ||
            armCm < WeekDefinition.MIN_ARM_CM || armCm > WeekDefinition.MAX_ARM_CM
        ) {
            errors["arm"] = if (useMetric) {
                "Arm must be between 10 and 100 cm"
            } else {
                "Arm must be between 3.9 and 39.4 in"
            }
        }

        val waistCm = resolveMetricLengthOrNull(waistInput, useMetric, canonicalWaistCm, waistDirty)
        if (waistCm == null || !waistCm.isFinite() ||
            waistCm < WeekDefinition.MIN_WAIST_CM || waistCm > WeekDefinition.MAX_WAIST_CM
        ) {
            errors["waist"] = if (useMetric) {
                "Waist must be between 30 and 250 cm"
            } else {
                "Waist must be between 11.8 and 98.4 in"
            }
        }

        return ValidatedWeeklyProgressInputs(
            cleanName = base.cleanName,
            age = base.age,
            weightKg = base.weightKg,
            heightCm = base.heightCm,
            chestCm = chestCm ?: 0f,
            armCm = armCm ?: 0f,
            waistCm = waistCm ?: 0f,
            errors = errors
        )
    }
}
