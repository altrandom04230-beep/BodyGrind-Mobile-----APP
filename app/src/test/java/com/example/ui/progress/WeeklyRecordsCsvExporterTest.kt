package com.example.ui.progress

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.progress.WeeklyRecord
import com.example.utils.WeeklyRecordsCsvExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeeklyRecordsCsvExporterTest {

    private fun createSampleRecord(
        weekNumber: Int = 1,
        timestamp: Long = 1700000000000L,
        name: String = "Alex Stone",
        age: Int = 28,
        weightKg: Float = 75.0f,
        heightCm: Float = 175.0f,
        chestCm: Float = 96.0f,
        armCm: Float = 35.0f,
        waistCm: Float = 82.0f
    ): WeeklyRecord {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return WeeklyRecord(
            id = "rec-$weekNumber",
            weekNumber = weekNumber,
            year = cal.get(Calendar.YEAR),
            calendarWeek = cal.get(Calendar.WEEK_OF_YEAR),
            timestamp = timestamp,
            memberName = name,
            memberAge = age,
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
    fun `generateCsvString - Empty List - Produces valid header only`() {
        val csv = WeeklyRecordsCsvExporter.generateCsvString(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertEquals(
            "Week,Date,Member Name,Age,Weight (kg),Weight (lb),Height (cm),Height (in),Chest (cm),Chest (in),Arm (cm),Arm (in),Waist (cm),Waist (in)",
            lines[0]
        )
    }

    @Test
    fun `generateCsvString - Populated Records - Formats rows with metric and imperial units`() {
        val rec1 = createSampleRecord(
            weekNumber = 1,
            timestamp = 1700000000000L,
            name = "John Connor",
            age = 30,
            weightKg = 80.0f,
            heightCm = 180.0f,
            chestCm = 100.0f,
            armCm = 38.0f,
            waistCm = 84.0f
        )
        val rec2 = createSampleRecord(
            weekNumber = 2,
            timestamp = 1700604800000L,
            name = "John Connor",
            age = 30,
            weightKg = 79.2f,
            heightCm = 180.0f,
            chestCm = 100.5f,
            armCm = 38.5f,
            waistCm = 83.0f
        )

        val csv = WeeklyRecordsCsvExporter.generateCsvString(listOf(rec1, rec2))
        val lines = csv.trim().lines()

        assertEquals(3, lines.size) // 1 header + 2 data rows
        assertTrue(lines[0].startsWith("Week,Date,Member Name,Age"))

        // Row 1 assertions
        val row1Cols = lines[1].split(",")
        assertEquals("1", row1Cols[0])
        assertEquals("John Connor", row1Cols[2])
        assertEquals("30", row1Cols[3])
        assertEquals("80.0", row1Cols[4]) // Weight kg
        assertEquals("176.4", row1Cols[5]) // Weight lb (80 * 2.20462 = 176.37)
        assertEquals("180.0", row1Cols[6]) // Height cm
        assertEquals("70.9", row1Cols[7])  // Height inches (180 / 2.54 = 70.866)
        assertEquals("100.0", row1Cols[8]) // Chest cm
        assertEquals("39.4", row1Cols[9])  // Chest inches (100 / 2.54 = 39.37)
        assertEquals("38.0", row1Cols[10]) // Arm cm
        assertEquals("15.0", row1Cols[11]) // Arm inches (38 / 2.54 = 14.96)
        assertEquals("84.0", row1Cols[12]) // Waist cm
        assertEquals("33.1", row1Cols[13]) // Waist inches (84 / 2.54 = 33.07)

        // Row 2 assertions
        val row2Cols = lines[2].split(",")
        assertEquals("2", row2Cols[0])
        assertEquals("79.2", row2Cols[4]) // Weight kg
        assertEquals("83.0", row2Cols[12]) // Waist cm
    }

    @Test
    fun `generateCsvString - Special Characters - Escapes commas and quotes in member names according to RFC 4180`() {
        val recWithComma = createSampleRecord(weekNumber = 1, name = "Doe, John")
        val recWithQuotes = createSampleRecord(weekNumber = 2, name = "Sarah \"The Beast\" Connor")

        val csv = WeeklyRecordsCsvExporter.generateCsvString(listOf(recWithComma, recWithQuotes))
        val lines = csv.trim().lines()

        assertTrue("Name with comma must be wrapped in quotes", lines[1].contains("\"Doe, John\""))
        assertTrue("Name with quotes must double the quotes and be wrapped", lines[2].contains("\"Sarah \"\"The Beast\"\" Connor\""))
    }

    @Test
    fun `generateCsvString - Chronological Sorting - Orders rows oldest to newest`() {
        val older = createSampleRecord(weekNumber = 1, timestamp = 1600000000000L, name = "Older")
        val newer = createSampleRecord(weekNumber = 2, timestamp = 1700000000000L, name = "Newer")

        // Pass in reverse order
        val csv = WeeklyRecordsCsvExporter.generateCsvString(listOf(newer, older))
        val lines = csv.trim().lines()

        assertTrue("First data line should be older record (Week 1)", lines[1].startsWith("1,"))
        assertTrue("Second data line should be newer record (Week 2)", lines[2].startsWith("2,"))
    }

    @Test
    fun `writeCsvFile - Successfully creates file in cache directory with filter name suffix`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val records = listOf(
            createSampleRecord(weekNumber = 1, name = "Tester"),
            createSampleRecord(weekNumber = 2, name = "Tester")
        )

        val file = WeeklyRecordsCsvExporter.writeCsvFile(context, records, "November 2026")
        assertNotNull("Generated file should not be null", file)
        assertTrue("Generated file should exist", file!!.exists())
        assertTrue("File should be in cache/exports directory", file.path.contains("exports"))
        assertTrue("File name should include filter suffix", file.name.contains("november_2026"))
        assertTrue("File name should end with dot csv", file.name.endsWith(".csv"))

        val content = file.readText()
        val lines = content.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(content.contains("Tester"))

        // Cleanup
        file.delete()
    }

    @Test
    fun `Filter Integration - Exporting filtered list produces CSV matching only filtered records`() {
        val cal2025 = Calendar.getInstance().apply {
            set(2025, Calendar.JANUARY, 15, 10, 0, 0)
        }
        val cal2026Nov = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 10, 10, 0, 0)
        }
        val cal2026Dec = Calendar.getInstance().apply {
            set(2026, Calendar.DECEMBER, 5, 10, 0, 0)
        }

        val r1 = createSampleRecord(weekNumber = 1, timestamp = cal2025.timeInMillis, name = "Rec 2025")
        val r2 = createSampleRecord(weekNumber = 2, timestamp = cal2026Nov.timeInMillis, name = "Rec Nov 2026")
        val r3 = createSampleRecord(weekNumber = 3, timestamp = cal2026Dec.timeInMillis, name = "Rec Dec 2026")

        val allRecords = listOf(r1, r2, r3)

        // Filter: Year 2026 and Month November
        val filtered = allRecords.filter { r ->
            val cal = Calendar.getInstance().apply { timeInMillis = r.timestamp }
            cal.get(Calendar.YEAR) == 2026 && cal.get(Calendar.MONTH) == Calendar.NOVEMBER
        }

        assertEquals(1, filtered.size)
        assertEquals("Rec Nov 2026", filtered[0].memberName)

        val csv = WeeklyRecordsCsvExporter.generateCsvString(filtered)
        val lines = csv.trim().lines()
        assertEquals(2, lines.size) // Header + 1 record
        assertTrue(lines[1].contains("Rec Nov 2026"))
        assertFalse(lines[1].contains("Rec 2025"))
        assertFalse(lines[1].contains("Rec Dec 2026"))
    }
}
