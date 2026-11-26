package com.example.ui.progress

import com.example.data.progress.WeeklyRecord
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProgressTrendChartTest {

    private fun sampleRecord(
        weekNumber: Int,
        timestamp: Long,
        weightKg: Float = 75f,
        heightCm: Float = 175f,
        chestCm: Float = 96f,
        armCm: Float = 35f,
        waistCm: Float = 82f
    ): WeeklyRecord {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return WeeklyRecord(
            id = "rec-$weekNumber",
            weekNumber = weekNumber,
            year = cal.get(Calendar.YEAR),
            calendarWeek = cal.get(Calendar.WEEK_OF_YEAR),
            timestamp = timestamp,
            memberName = "Athlete",
            memberAge = 28,
            weightKg = weightKg,
            heightCm = heightCm,
            weightUnit = "kg",
            heightUnit = "cm",
            chestCm = chestCm,
            armCm = armCm,
            waistCm = waistCm,
            measurementUnit = "cm"
        )
    }

    @Test
    fun `Trend Chart - Chronological Sorting - Orders records oldest to newest`() {
        val r1 = sampleRecord(weekNumber = 1, timestamp = 1000L)
        val r2 = sampleRecord(weekNumber = 2, timestamp = 2000L)
        val r3 = sampleRecord(weekNumber = 3, timestamp = 3000L)

        // Pass out of order
        val input = listOf(r3, r1, r2)
        val sorted = input.sortedBy { it.timestamp }

        assertEquals(1, sorted[0].weekNumber)
        assertEquals(2, sorted[1].weekNumber)
        assertEquals(3, sorted[2].weekNumber)
    }

    @Test
    fun `Trend Chart - Weight Metric Extraction - Correctly converts metric and imperial`() {
        val rec = sampleRecord(weekNumber = 1, timestamp = 1000L, weightKg = 80.0f)

        val kg = rec.getDisplayWeight("kg")
        val lb = rec.getDisplayWeight("lb")

        assertEquals(80.0f, kg, 0.01f)
        assertEquals(176.37f, lb, 0.1f) // 80 * 2.20462 = 176.3696
    }

    @Test
    fun `Trend Chart - Body Measurements Extraction - Correctly converts cm and inches`() {
        val rec = sampleRecord(
            weekNumber = 1,
            timestamp = 1000L,
            chestCm = 100.0f,
            armCm = 38.0f,
            waistCm = 84.0f
        )

        val chestCm = rec.getDisplayChest("cm")
        val chestIn = rec.getDisplayChest("inches")
        val armCm = rec.getDisplayArm("cm")
        val armIn = rec.getDisplayArm("inches")
        val waistCm = rec.getDisplayWaist("cm")
        val waistIn = rec.getDisplayWaist("inches")

        assertEquals(100.0f, chestCm, 0.01f)
        assertEquals(39.37f, chestIn, 0.1f) // 100 / 2.54 = 39.37

        assertEquals(38.0f, armCm, 0.01f)
        assertEquals(14.96f, armIn, 0.1f) // 38 / 2.54 = 14.96

        assertEquals(84.0f, waistCm, 0.01f)
        assertEquals(33.07f, waistIn, 0.1f) // 84 / 2.54 = 33.07
    }

    @Test
    fun `Trend Chart - Net Change Calculation - Accurately computes start, latest, net change, min and max`() {
        val r1 = sampleRecord(weekNumber = 1, timestamp = 1000L, weightKg = 80.0f)
        val r2 = sampleRecord(weekNumber = 2, timestamp = 2000L, weightKg = 78.5f)
        val r3 = sampleRecord(weekNumber = 3, timestamp = 3000L, weightKg = 77.0f)

        val records = listOf(r1, r2, r3)
        val weights = records.map { it.getDisplayWeight("kg") }

        val start = weights.first()
        val latest = weights.last()
        val netChange = latest - start
        val min = weights.minOrNull() ?: 0f
        val max = weights.maxOrNull() ?: 0f

        assertEquals(80.0f, start, 0.01f)
        assertEquals(77.0f, latest, 0.01f)
        assertEquals(-3.0f, netChange, 0.01f)
        assertEquals(77.0f, min, 0.01f)
        assertEquals(80.0f, max, 0.01f)
    }

    @Test
    fun `Trend Chart - Date Filtering - Correctly isolates records within selected month and year`() {
        val cal2026Oct = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 20, 10, 0, 0) }
        val cal2026Nov1 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 5, 10, 0, 0) }
        val cal2026Nov2 = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 12, 10, 0, 0) }
        val cal2026Dec = Calendar.getInstance().apply { set(2026, Calendar.DECEMBER, 1, 10, 0, 0) }

        val records = listOf(
            sampleRecord(weekNumber = 1, timestamp = cal2026Oct.timeInMillis, weightKg = 82f),
            sampleRecord(weekNumber = 2, timestamp = cal2026Nov1.timeInMillis, weightKg = 81f),
            sampleRecord(weekNumber = 3, timestamp = cal2026Nov2.timeInMillis, weightKg = 80f),
            sampleRecord(weekNumber = 4, timestamp = cal2026Dec.timeInMillis, weightKg = 79f)
        )

        // Filter: November 2026
        val targetYear = 2026
        val targetMonth = Calendar.NOVEMBER

        val filtered = records.filter { r ->
            val c = Calendar.getInstance().apply { timeInMillis = r.timestamp }
            c.get(Calendar.YEAR) == targetYear && c.get(Calendar.MONTH) == targetMonth
        }

        assertEquals(2, filtered.size)
        assertEquals(2, filtered[0].weekNumber)
        assertEquals(3, filtered[1].weekNumber)

        val weights = filtered.map { it.getDisplayWeight("kg") }
        assertEquals(81.0f, weights.first(), 0.01f)
        assertEquals(80.0f, weights.last(), 0.01f)
        assertEquals(-1.0f, weights.last() - weights.first(), 0.01f)
    }
}
