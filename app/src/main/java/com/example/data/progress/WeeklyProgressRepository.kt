package com.example.data.progress

import android.content.Context
import android.content.SharedPreferences
import com.example.utils.UnitConverter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class WeeklyRecord(
    val id: String = UUID.randomUUID().toString(),
    val weekNumber: Int,
    val year: Int,
    val calendarWeek: Int,
    val timestamp: Long = System.currentTimeMillis(),
    // Member Info
    val memberName: String,
    val memberAge: Int,
    val weightKg: Float,
    val heightCm: Float,
    val weightUnit: String = "kg",
    val heightUnit: String = "cm",
    // Body Measurements
    val chestCm: Float,
    val armCm: Float,
    val waistCm: Float,
    val measurementUnit: String = "cm",
    val weekKey: String = WeekDefinition.weekKeyFor(timestamp),
    val roomId: Long? = null
) {
    fun getDisplayWeight(unit: String): Float =
        if (unit.equals("lb", ignoreCase = true)) UnitConverter.kgToLb(weightKg) else weightKg

    fun getDisplayHeight(unit: String): Float =
        if (unit.equals("inches", ignoreCase = true) || unit.equals("in", ignoreCase = true)) {
            UnitConverter.cmToInches(heightCm)
        } else heightCm

    fun getDisplayChest(unit: String): Float =
        if (unit.equals("inches", ignoreCase = true) || unit.equals("in", ignoreCase = true)) {
            UnitConverter.cmToInches(chestCm)
        } else chestCm

    fun getDisplayArm(unit: String): Float =
        if (unit.equals("inches", ignoreCase = true) || unit.equals("in", ignoreCase = true)) {
            UnitConverter.cmToInches(armCm)
        } else armCm

    fun getDisplayWaist(unit: String): Float =
        if (unit.equals("inches", ignoreCase = true) || unit.equals("in", ignoreCase = true)) {
            UnitConverter.cmToInches(waistCm)
        } else waistCm
}

class WeeklyProgressRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bodygrind_weekly_records_prefs", Context.MODE_PRIVATE)

    private val _records = MutableStateFlow<List<WeeklyRecord>>(emptyList())
    val records: StateFlow<List<WeeklyRecord>> = _records.asStateFlow()

    init {
        loadRecords()
    }

    fun loadRecords() {
        val jsonStr = prefs.getString("weekly_records_json", null) ?: run {
            _records.value = emptyList()
            return
        }

        val list = mutableListOf<WeeklyRecord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val ts = obj.optLong("timestamp", System.currentTimeMillis())
                val (isoYear, isoWeek) = WeekDefinition.isoYearAndWeekFor(ts)
                list.add(
                    WeeklyRecord(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        weekNumber = obj.optInt("weekNumber", i + 1),
                        year = obj.optInt("year", isoYear),
                        calendarWeek = obj.optInt("calendarWeek", isoWeek),
                        timestamp = ts,
                        memberName = obj.optString("memberName", "Fitness Athlete"),
                        memberAge = obj.optInt("memberAge", 26),
                        weightKg = obj.optDouble("weightKg", 70.0).toFloat(),
                        heightCm = obj.optDouble("heightCm", 175.0).toFloat(),
                        weightUnit = obj.optString("weightUnit", "kg"),
                        heightUnit = obj.optString("heightUnit", "cm"),
                        chestCm = obj.optDouble("chestCm", 96.0).toFloat(),
                        armCm = obj.optDouble("armCm", 35.0).toFloat(),
                        waistCm = obj.optDouble("waistCm", 82.0).toFloat(),
                        measurementUnit = obj.optString("measurementUnit", "cm"),
                        weekKey = obj.optString("weekKey", "").ifBlank { WeekDefinition.weekKeyFor(ts) }
                    )
                )
            }
        } catch (_: Exception) {}

        _records.value = list.sortedByDescending { it.timestamp }
    }

    fun isRecordSavedThisWeek(): Boolean {
        val currentKey = WeekDefinition.weekKeyFor()
        return _records.value.any { it.weekKey == currentKey }
    }

    fun getRecordForThisWeek(): WeeklyRecord? {
        val currentKey = WeekDefinition.weekKeyFor()
        return _records.value.find { it.weekKey == currentKey }
    }

    fun getNextWeekNumber(): Int {
        val maxWeek = _records.value.maxOfOrNull { it.weekNumber } ?: 0
        return maxWeek + 1
    }

    fun saveRecord(record: WeeklyRecord): Boolean {
        val currentList = _records.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.id == record.id || it.weekKey == record.weekKey
        }
        if (existingIndex != -1) {
            currentList[existingIndex] = record
        } else {
            currentList.add(0, record)
        }
        val sorted = currentList.sortedByDescending { it.timestamp }
        _records.value = sorted
        persistToPrefs(sorted)
        return true
    }

    fun deleteRecord(recordId: String) {
        val updated = _records.value.filter { it.id != recordId }
        _records.value = updated
        persistToPrefs(updated)
    }

    fun consumeUnmigratedUserRecords(): List<WeeklyRecord> {
        if (prefs.getBoolean(KEY_ROOM_MIGRATION_DONE, false)) return emptyList()
        val raw = prefs.getString("weekly_records_json", null)
        if (raw.isNullOrBlank()) {
            prefs.edit().putBoolean(KEY_ROOM_MIGRATION_DONE, true).apply()
            return emptyList()
        }
        loadRecords()
        // Exclude the old hardcoded baseline fake seed if untouched
        val realRecords = _records.value.filter { it.id != "seed_week_1" }
        prefs.edit().putBoolean(KEY_ROOM_MIGRATION_DONE, true).apply()
        return realRecords
    }

    private fun persistToPrefs(list: List<WeeklyRecord>) {
        val array = JSONArray()
        list.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("weekNumber", r.weekNumber)
                put("year", r.year)
                put("calendarWeek", r.calendarWeek)
                put("weekKey", r.weekKey)
                put("timestamp", r.timestamp)
                put("memberName", r.memberName)
                put("memberAge", r.memberAge)
                put("weightKg", r.weightKg)
                put("heightCm", r.heightCm)
                put("weightUnit", r.weightUnit)
                put("heightUnit", r.heightUnit)
                put("chestCm", r.chestCm)
                put("armCm", r.armCm)
                put("waistCm", r.waistCm)
                put("measurementUnit", r.measurementUnit)
            }
            array.put(obj)
        }
        prefs.edit().putString("weekly_records_json", array.toString()).apply()
    }

    companion object {
        private const val KEY_ROOM_MIGRATION_DONE = "room_migration_v10_done"
    }
}

