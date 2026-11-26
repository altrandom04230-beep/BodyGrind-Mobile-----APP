package com.example.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.progress.WeeklyRecord
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object WeeklyRecordsCsvExporter {

    /**
     * Generates standard RFC 4180 compliant CSV string from a list of weekly records.
     */
    fun generateCsvString(records: List<WeeklyRecord>): String {
        val dateFormat = ManilaDateTime.simpleDateFormat("yyyy-MM-dd HH:mm")
        val sb = StringBuilder()

        // CSV Header
        sb.append("Week,Date,Member Name,Age,Weight (kg),Weight (lb),Height (cm),Height (in),Chest (cm),Chest (in),Arm (cm),Arm (in),Waist (cm),Waist (in)\n")

        // Order chronologically for logical tracking
        val chronological = records.sortedBy { it.timestamp }
        for (r in chronological) {
            val dateStr = dateFormat.format(Date(r.timestamp))
            val nameEscaped = escapeCsvField(r.memberName)
            val wKg = String.format(Locale.US, "%.1f", r.weightKg)
            val wLb = String.format(Locale.US, "%.1f", r.getDisplayWeight("lb"))
            val hCm = String.format(Locale.US, "%.1f", r.heightCm)
            val hIn = String.format(Locale.US, "%.1f", r.getDisplayHeight("inches"))
            val cCm = String.format(Locale.US, "%.1f", r.chestCm)
            val cIn = String.format(Locale.US, "%.1f", r.getDisplayChest("inches"))
            val aCm = String.format(Locale.US, "%.1f", r.armCm)
            val aIn = String.format(Locale.US, "%.1f", r.getDisplayArm("inches"))
            val waCm = String.format(Locale.US, "%.1f", r.waistCm)
            val waIn = String.format(Locale.US, "%.1f", r.getDisplayWaist("inches"))

            sb.append("${r.weekNumber},$dateStr,$nameEscaped,${r.memberAge},$wKg,$wLb,$hCm,$hIn,$cCm,$cIn,$aCm,$aIn,$waCm,$waIn\n")
        }

        return sb.toString()
    }

    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    /**
     * Writes CSV string to a temporary cache file ready for sharing.
     */
    fun writeCsvFile(context: Context, records: List<WeeklyRecord>, filterName: String? = null): File? {
        val csvContent = generateCsvString(records)
        return try {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val timeStamp = ManilaDateTime.simpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            val suffix = if (!filterName.isNullOrBlank()) {
                "_" + filterName.replace(Regex("[^a-zA-Z0-9]"), "_").lowercase()
            } else ""
            val file = File(exportDir, "bodygrind_weekly_progress_${timeStamp}${suffix}.csv")

            FileWriter(file).use { writer ->
                writer.write(csvContent)
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Launches the system share intent for the exported CSV file.
     */
    fun shareCsvFile(context: Context, file: File, recordCount: Int) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "BodyGrind Weekly Progress Export")
                putExtra(Intent.EXTRA_TEXT, "Exported BodyGrind weekly fitness and body measurement records ($recordCount records).")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, "Export Weekly Records CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share CSV file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Copies CSV text directly to clipboard.
     */
    fun copyToClipboard(context: Context, csvText: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("BodyGrind Weekly Records CSV", csvText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "CSV copied to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
