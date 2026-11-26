package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

object ThemePreferences {
    private const val PREFS_NAME = "bodygrind_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    const val MODE_LIGHT = "LIGHT"
    const val MODE_DARK = "DARK"
    const val MODE_SYSTEM = "SYSTEM"

    fun getThemeMode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, MODE_LIGHT) ?: MODE_LIGHT
    }

    fun setThemeMode(context: Context, mode: String) {
        val validMode = when (mode) {
            MODE_DARK, MODE_SYSTEM -> mode
            else -> MODE_LIGHT
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, validMode)
            .commit()
    }
}

data class AppPreferences(
    val themeMode: String = "LIGHT", // "DARK", "LIGHT", "SYSTEM"
    val useMetric: Boolean = true,
    val accentColorHex: String = "#FF5722",
    val isPrivate: Boolean = false,
    val bodyDiagramGender: String = "MALE", // "MALE", "FEMALE"
    val exerciseDrawingGender: String = "MALE", // "MALE", "FEMALE"
    val gymCheckinEnabled: Boolean = true,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 0,
    val reminderEnabled: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_METRIC = booleanPreferencesKey("use_metric")
        val ACCENT_COLOR_HEX = stringPreferencesKey("accent_color_hex")
        val IS_PRIVATE = booleanPreferencesKey("is_private")
        val BODY_DIAGRAM_GENDER = stringPreferencesKey("body_diagram_gender")
        val EXERCISE_DRAWING_GENDER = stringPreferencesKey("exercise_drawing_gender")
        val GYM_CHECKIN_ENABLED = booleanPreferencesKey("gym_checkin_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    }

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppPreferences(
                themeMode = preferences[PreferencesKeys.THEME_MODE] ?: "LIGHT",
                useMetric = preferences[PreferencesKeys.USE_METRIC] ?: true,
                accentColorHex = preferences[PreferencesKeys.ACCENT_COLOR_HEX] ?: "#FF5722",
                isPrivate = preferences[PreferencesKeys.IS_PRIVATE] ?: false,
                bodyDiagramGender = preferences[PreferencesKeys.BODY_DIAGRAM_GENDER] ?: "MALE",
                exerciseDrawingGender = preferences[PreferencesKeys.EXERCISE_DRAWING_GENDER] ?: "MALE",
                gymCheckinEnabled = preferences[PreferencesKeys.GYM_CHECKIN_ENABLED] ?: true,
                reminderHour = preferences[PreferencesKeys.REMINDER_HOUR] ?: 8,
                reminderMinute = preferences[PreferencesKeys.REMINDER_MINUTE] ?: 0,
                reminderEnabled = preferences[PreferencesKeys.REMINDER_ENABLED] ?: true
            )
        }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode
        }
    }

    suspend fun updateUseMetric(useMetric: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_METRIC] = useMetric
        }
    }

    suspend fun updateAccentColorHex(hex: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCENT_COLOR_HEX] = hex
        }
    }

    suspend fun updateIsPrivate(isPrivate: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_PRIVATE] = isPrivate
        }
    }

    suspend fun updateBodyDiagramGender(gender: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BODY_DIAGRAM_GENDER] = gender
        }
    }

    suspend fun updateExerciseDrawingGender(gender: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EXERCISE_DRAWING_GENDER] = gender
        }
    }

    suspend fun updateGymCheckinEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GYM_CHECKIN_ENABLED] = enabled
        }
    }

    suspend fun updateReminderTime(hour: Int, minute: Int, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REMINDER_HOUR] = hour
            preferences[PreferencesKeys.REMINDER_MINUTE] = minute
            preferences[PreferencesKeys.REMINDER_ENABLED] = enabled
        }
    }
}
