package com.example.ui.progress

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BadgeEntity
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.data.progress.BiometricValidator
import com.example.data.progress.WeekDefinition
import com.example.data.progress.WeeklyRecord
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import com.example.utils.WeeklyRecordsCsvExporter
import java.io.File
import java.util.*

@Composable
private fun progressTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    userProfile: UserProfileEntity,
    sessions: List<WorkoutSessionEntity> = emptyList(),
    measurements: List<BodyMeasurementEntity> = emptyList(),
    badges: List<BadgeEntity> = emptyList(),
    onLogWeight: (Float, Float, Long) -> Unit = { _, _, _ -> },
    onLogMeasurement: (Float, Float, Float?, Float?, Float?, String, Long) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteSession: (Long) -> Unit = {},
    onDeleteMeasurement: (Long) -> Unit = {},
    onOpenBodyPoseBmi: () -> Unit = {},
    onUpdateUseMetric: (Boolean) -> Unit = {},
    onSaveWeeklyProgressRecord: (
        memberName: String,
        memberAge: Int,
        weightKg: Float,
        heightCm: Float,
        chestCm: Float,
        armCm: Float,
        waistCm: Float,
        useMetric: Boolean,
        onSaved: (Int) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current

    // Authoritative weekly records derived from Room BodyMeasurementEntity rows tagged with weekKey
    val records = remember(measurements) {
        measurements
            .filter { !it.weekKey.isNullOrBlank() }
            .sortedByDescending { it.timestamp }
            .map { it.toWeeklyRecord() }
    }

    val currentWeekKey = WeekDefinition.weekKeyFor()
    val thisWeekRecord = remember(records, currentWeekKey) {
        records.find { it.weekKey == currentWeekKey }
    }
    val isSavedThisWeek = thisWeekRecord != null

    var showEntryFormSheet by remember { mutableStateOf(false) }

    // Shared unit preference from UserProfileEntity
    val useMetric = userProfile.useMetric
    val weightUnit = if (useMetric) "kg" else "lb"
    val heightUnit = if (useMetric) "cm" else "inches"
    val measurementUnit = if (useMetric) "cm" else "inches"

    // Canonical internal metric backing state (prevents round-trip conversion drift)
    var canonicalWeightKg by remember {
        mutableFloatStateOf(userProfile.weightKg.takeIf { it > 0f } ?: 70.0f)
    }
    var canonicalHeightCm by remember {
        mutableFloatStateOf(userProfile.heightCm.takeIf { it > 0f } ?: 175.0f)
    }
    var canonicalChestCm by remember {
        mutableFloatStateOf(thisWeekRecord?.chestCm ?: records.firstOrNull()?.chestCm ?: 96.0f)
    }
    var canonicalArmCm by remember {
        mutableFloatStateOf(thisWeekRecord?.armCm ?: records.firstOrNull()?.armCm ?: 35.0f)
    }
    var canonicalWaistCm by remember {
        mutableFloatStateOf(thisWeekRecord?.waistCm ?: records.firstOrNull()?.waistCm ?: 82.0f)
    }

    var weightDirty by remember { mutableStateOf(false) }
    var heightDirty by remember { mutableStateOf(false) }
    var chestDirty by remember { mutableStateOf(false) }
    var armDirty by remember { mutableStateOf(false) }
    var waistDirty by remember { mutableStateOf(false) }

    // Section 1: Member Info Fields (pre-filled & synced from UserProfileEntity)
    var nameInput by remember { mutableStateOf(userProfile.name) }
    var ageInput by remember { mutableStateOf(userProfile.age.toString()) }
    var weightInput by remember {
        mutableStateOf(BiometricValidator.formatWeightInput(canonicalWeightKg, useMetric))
    }
    var heightInput by remember {
        mutableStateOf(BiometricValidator.formatHeightInput(canonicalHeightCm, useMetric))
    }

    // Section 2: Body Measurement Fields
    var chestInput by remember {
        mutableStateOf(BiometricValidator.formatCircumferenceInput(canonicalChestCm, useMetric))
    }
    var armInput by remember {
        mutableStateOf(BiometricValidator.formatCircumferenceInput(canonicalArmCm, useMetric))
    }
    var waistInput by remember {
        mutableStateOf(BiometricValidator.formatCircumferenceInput(canonicalWaistCm, useMetric))
    }

    // Validation & Error Handling
    var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }
    var recordToDelete by remember { mutableStateOf<WeeklyRecord?>(null) }
    var isEditingThisWeek by remember { mutableStateOf(false) }

    fun switchUnit(targetMetric: Boolean) {
        if (targetMetric == useMetric) return
        if (weightDirty) {
            BiometricValidator.resolveMetricWeightOrNull(weightInput, useMetric, canonicalWeightKg, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalWeightKg = it }
            weightDirty = false
        }
        if (heightDirty) {
            BiometricValidator.resolveMetricLengthOrNull(heightInput, useMetric, canonicalHeightCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalHeightCm = it }
            heightDirty = false
        }
        if (chestDirty) {
            BiometricValidator.resolveMetricLengthOrNull(chestInput, useMetric, canonicalChestCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalChestCm = it }
            chestDirty = false
        }
        if (armDirty) {
            BiometricValidator.resolveMetricLengthOrNull(armInput, useMetric, canonicalArmCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalArmCm = it }
            armDirty = false
        }
        if (waistDirty) {
            BiometricValidator.resolveMetricLengthOrNull(waistInput, useMetric, canonicalWaistCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalWaistCm = it }
            waistDirty = false
        }
        validationErrors = emptyMap()
        onUpdateUseMetric(targetMetric)
    }

    // Master sync: UserProfileEntity -> Progress Member Information fields whenever profile opens/updates
    LaunchedEffect(userProfile.name, userProfile.age, userProfile.weightKg, userProfile.heightCm) {
        nameInput = userProfile.name
        ageInput = userProfile.age.toString()
        val profileWeight = userProfile.weightKg.takeIf { it > 0f } ?: 70.0f
        val profileHeight = userProfile.heightCm.takeIf { it > 0f } ?: 175.0f
        canonicalWeightKg = profileWeight
        canonicalHeightCm = profileHeight
        weightDirty = false
        heightDirty = false
        weightInput = BiometricValidator.formatWeightInput(profileWeight, userProfile.useMetric)
        heightInput = BiometricValidator.formatHeightInput(profileHeight, userProfile.useMetric)
        validationErrors = validationErrors - "name" - "age" - "weight" - "height"
    }

    // Sync circumferences from this week's record (or latest weekly record) when available
    val latestWeeklyRecord = thisWeekRecord ?: records.firstOrNull()
    LaunchedEffect(
        latestWeeklyRecord?.roomId,
        latestWeeklyRecord?.chestCm,
        latestWeeklyRecord?.armCm,
        latestWeeklyRecord?.waistCm
    ) {
        latestWeeklyRecord?.let { rec ->
            canonicalChestCm = rec.chestCm
            canonicalArmCm = rec.armCm
            canonicalWaistCm = rec.waistCm
            chestDirty = false
            armDirty = false
            waistDirty = false
            chestInput = BiometricValidator.formatCircumferenceInput(rec.chestCm, userProfile.useMetric)
            armInput = BiometricValidator.formatCircumferenceInput(rec.armCm, userProfile.useMetric)
            waistInput = BiometricValidator.formatCircumferenceInput(rec.waistCm, userProfile.useMetric)
        }
    }

    // Reformat displayed fields cleanly whenever the shared useMetric setting changes (zero drift)
    LaunchedEffect(userProfile.useMetric) {
        if (!weightDirty) {
            weightInput = BiometricValidator.formatWeightInput(canonicalWeightKg, userProfile.useMetric)
        }
        if (!heightDirty) {
            heightInput = BiometricValidator.formatHeightInput(canonicalHeightCm, userProfile.useMetric)
        }
        if (!chestDirty) {
            chestInput = BiometricValidator.formatCircumferenceInput(canonicalChestCm, userProfile.useMetric)
        }
        if (!armDirty) {
            armInput = BiometricValidator.formatCircumferenceInput(canonicalArmCm, userProfile.useMetric)
        }
        if (!waistDirty) {
            waistInput = BiometricValidator.formatCircumferenceInput(canonicalWaistCm, userProfile.useMetric)
        }
    }

    // Latest and previous records for comparison
    val latestRecord = records.firstOrNull()
    val previousRecord = records.getOrNull(1)

    // Date Range & Month/Year Filter States
    var filterDialogVisible by remember { mutableStateOf(false) }
    var selectedFilterYear by remember { mutableStateOf<Int?>(null) } // null = All years
    var selectedFilterMonth by remember { mutableStateOf<Int?>(null) } // null = All months (0 = Jan, 11 = Dec)
    var quickFilterPreset by remember { mutableStateOf("ALL") } // "ALL", "THIS_MONTH", "THIS_YEAR", "CUSTOM"

    var dialogSelectedYear by remember { mutableStateOf<Int?>(null) }
    var dialogSelectedMonth by remember { mutableStateOf<Int?>(null) }

    // CSV Export States
    var exportDialogVisible by remember { mutableStateOf(false) }
    var exportedCsvFile by remember { mutableStateOf<File?>(null) }
    var exportedCsvText by remember { mutableStateOf("") }

    val currentCal = remember { ManilaDateTime.manilaCalendar() }
    val availableYears = remember(records) {
        (records.map { it.year } + currentCal.get(Calendar.YEAR)).distinct().sortedDescending()
    }

    val monthNames = remember {
        listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
    val shortMonthNames = remember {
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    }

    val filteredRecords = remember(records, selectedFilterYear, selectedFilterMonth) {
        if (selectedFilterYear == null && selectedFilterMonth == null) {
            records
        } else {
            records.filter { record ->
                val cal = ManilaDateTime.manilaCalendar(record.timestamp)
                val recYear = cal.get(Calendar.YEAR)
                val recMonth = cal.get(Calendar.MONTH)
                val yearMatch = (selectedFilterYear == null || recYear == selectedFilterYear)
                val monthMatch = (selectedFilterMonth == null || recMonth == selectedFilterMonth)
                yearMatch && monthMatch
            }
        }
    }

    // Month & Year Filter Dialog
    if (filterDialogVisible) {
        AlertDialog(
            onDismissRequest = { filterDialogVisible = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DateRange, contentDescription = null, tint = BoldRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Filter by Month & Year", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Filter your weekly record history by a specific year and month.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Year Selection
                    Column {
                        Text("Select Year", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = dialogSelectedYear == null,
                                onClick = { dialogSelectedYear = null },
                                label = { Text("All Years", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BoldRed,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                            availableYears.forEach { yr ->
                                FilterChip(
                                    selected = dialogSelectedYear == yr,
                                    onClick = { dialogSelectedYear = yr },
                                    label = { Text(yr.toString(), fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = BoldRed,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }

                    // Month Selection
                    Column {
                        Text("Select Month", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        FilterChip(
                            selected = dialogSelectedMonth == null,
                            onClick = { dialogSelectedMonth = null },
                            label = { Text("All Months", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BoldRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        shortMonthNames.chunked(4).forEach { rowMonths ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowMonths.forEach { mStr ->
                                    val mIndex = shortMonthNames.indexOf(mStr)
                                    FilterChip(
                                        selected = dialogSelectedMonth == mIndex,
                                        onClick = { dialogSelectedMonth = mIndex },
                                        label = { Text(mStr, fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BoldRed,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFilterYear = dialogSelectedYear
                        selectedFilterMonth = dialogSelectedMonth
                        quickFilterPreset = if (dialogSelectedYear == null && dialogSelectedMonth == null) "ALL" else "CUSTOM"
                        filterDialogVisible = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    modifier = Modifier.testTag("apply_date_filter_button")
                ) {
                    Text("Apply Filter")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        dialogSelectedYear = null
                        dialogSelectedMonth = null
                        selectedFilterYear = null
                        selectedFilterMonth = null
                        quickFilterPreset = "ALL"
                        filterDialogVisible = false
                    },
                    modifier = Modifier.testTag("clear_date_filter_button")
                ) {
                    Text("Reset All")
                }
            }
        )
    }

    // Delete confirmation dialog
    if (recordToDelete != null) {
        val rec = recordToDelete!!
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text("Delete Weekly Record?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete the record for Week ${rec.weekNumber}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        rec.roomId?.let { onDeleteMeasurement(it) }
                        recordToDelete = null
                    }
                ) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export CSV Dialog
    if (exportDialogVisible) {
        val filterDesc = when {
            selectedFilterMonth != null && selectedFilterYear != null ->
                "${monthNames[selectedFilterMonth!!]} $selectedFilterYear"
            selectedFilterMonth != null ->
                "${monthNames[selectedFilterMonth!!]} (All Years)"
            selectedFilterYear != null ->
                "Year $selectedFilterYear"
            else ->
                "All Time"
        }

        AlertDialog(
            onDismissRequest = { exportDialogVisible = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = BoldRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Records (CSV)", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Export your weekly progress records as a CSV spreadsheet file for personal tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Records included:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${filteredRecords.size} records", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Active filter:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(filterDesc, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BoldRed)
                            }
                            if (exportedCsvFile != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("File:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(exportedCsvFile!!.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                }
                            }
                        }
                    }

                    Text("CSV Preview:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 150.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = exportedCsvText.ifEmpty { "Generating CSV..." },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.testTag("export_preview_text")
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = exportedCsvFile ?: WeeklyRecordsCsvExporter.writeCsvFile(context, filteredRecords, filterDesc)
                        if (file != null) {
                            WeeklyRecordsCsvExporter.shareCsvFile(context, file, filteredRecords.size)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    modifier = Modifier.testTag("export_share_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Save File")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            WeeklyRecordsCsvExporter.copyToClipboard(context, exportedCsvText)
                        },
                        modifier = Modifier.testTag("export_copy_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { exportDialogVisible = false },
                        modifier = Modifier.testTag("export_dialog_dismiss")
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }

    if (showEntryFormSheet) {
        val curWeightKg = userProfile.weightKg.takeIf { it > 0f } ?: 70.0f
        val curHeightCm = userProfile.heightCm.takeIf { it > 0f } ?: 175.0f

        MeasurementDataEntrySheet(
            initialWeightKg = curWeightKg,
            initialHeightCm = curHeightCm,
            useMetric = useMetric,
            onUpdateUseMetric = { switchUnit(it) },
            onDismiss = { showEntryFormSheet = false },
            onSubmit = { wKg, hCm, chestCm, armCm, waistCm, notes, timestamp ->
                onLogMeasurement(wKg, hCm, chestCm, armCm, waistCm, notes, timestamp)
                saveSuccessMessage = "Body measurements saved to Room database!"
                showEntryFormSheet = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("progress_lazy_column"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Title: Progress & Analytics with Log Measurement Data Entry Form Action
        item {
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.screenWidthDp >= 640
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress & Analytics",
                        style = if (configuration.screenWidthDp >= 768) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )

                    FilledTonalButton(
                        onClick = { showEntryFormSheet = true },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("open_measurement_entry_form_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BoldRed.copy(alpha = 0.15f),
                            contentColor = BoldRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddChart,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = BoldRed
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Measurement",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BoldRed
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Progress & Analytics",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )

                    FilledTonalButton(
                        onClick = { showEntryFormSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .testTag("open_measurement_entry_form_button"),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = BoldRed.copy(alpha = 0.15f),
                            contentColor = BoldRed
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddChart,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = BoldRed
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Measurement",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BoldRed
                        )
                    }
                }
            }
        }

        // =========================================================================
        // SECTION 1: PROGRESS BAR / RECORD OF MEMBER
        // =========================================================================

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(BoldRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("1", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "PROGRESS BAR / RECORD OF MEMBER",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Progress Bar Card (Member Journey Progress)
        item {
            val totalWeeksLogged = records.size
            val targetMilestoneWeeks = 12
            val progressFraction = (totalWeeksLogged.toFloat() / targetMilestoneWeeks.toFloat()).coerceIn(0f, 1f)
            val percentage = (progressFraction * 100).toInt()

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = BoldRed, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Member Journey Progress",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BoldRed.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$percentage% ($totalWeeksLogged/$targetMilestoneWeeks Wks)",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BoldRed
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = BoldRed,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (totalWeeksLogged == 0) {
                            "Start your journey today by saving your Week 1 member record!"
                        } else {
                            "$totalWeeksLogged weekly records saved. Weekly records unlock every week to track your consistency."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Week-by-Week Comparison Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CompareArrows, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Week-by-Week Comparison",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (latestRecord != null && previousRecord != null) {
                            Text(
                                text = "Wk ${latestRecord.weekNumber} vs Wk ${previousRecord.weekNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (latestRecord != null && previousRecord != null) {
                        val currWeight = latestRecord.getDisplayWeight(weightUnit)
                        val prevWeight = previousRecord.getDisplayWeight(weightUnit)
                        val deltaWeight = currWeight - prevWeight

                        val currHeight = latestRecord.getDisplayHeight(heightUnit)
                        val prevHeight = previousRecord.getDisplayHeight(heightUnit)
                        val deltaHeight = currHeight - prevHeight

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Weight comparison
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", currWeight, weightUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Current Weight", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                ComparisonBadge(delta = deltaWeight, unit = weightUnit, isWeight = true)
                            }

                            VerticalDivider(modifier = Modifier.height(36.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                            // Height comparison
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", currHeight, heightUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Height", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                if (kotlin.math.abs(deltaHeight) < 0.05f) {
                                    Text("Unchanged", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                } else {
                                    ComparisonBadge(delta = deltaHeight, unit = heightUnit, isWeight = false)
                                }
                            }
                        }
                    } else if (latestRecord != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Week 1 baseline recorded (${String.format(Locale.US, "%.1f %s", latestRecord.getDisplayWeight(weightUnit), weightUnit)}). Next week's entry will show direct week-by-week comparisons!",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No records yet. Save your weekly record below to start your comparison.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Member Information Input Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Member Information",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Unit Selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Weight unit selector: kg or lb
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Weight unit:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            SegmentedUnitSelector(
                                selectedUnit = weightUnit,
                                options = listOf("kg", "lb"),
                                tagPrefix = "progress_weight_unit",
                                onUnitSelected = { newUnit ->
                                    switchUnit(newUnit.equals("kg", ignoreCase = true))
                                }
                            )
                        }

                        // Height unit selector: cm or inches
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Height unit:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            SegmentedUnitSelector(
                                selectedUnit = heightUnit,
                                options = listOf("cm", "inches"),
                                tagPrefix = "progress_height_unit",
                                onUnitSelected = { newUnit ->
                                    switchUnit(newUnit.equals("cm", ignoreCase = true))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Member Name
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            validationErrors = validationErrors - "name"
                        },
                        label = { Text("Member Name *") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_name_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("name"),
                        supportingText = validationErrors["name"]?.let { { Text(it, color = DangerRed) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Member Age (numbers only)
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) {
                                ageInput = it.take(3)
                                validationErrors = validationErrors - "age"
                            }
                        },
                        label = { Text("Age *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_age_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("age"),
                        supportingText = validationErrors["age"]?.let { { Text(it, color = DangerRed) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Weight (numbers only)
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                weightInput = it
                                weightDirty = true
                                validationErrors = validationErrors - "weight"
                            }
                        },
                        label = { Text("Weight ($weightUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_weight_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("weight"),
                        supportingText = validationErrors["weight"]?.let { { Text(it, color = DangerRed) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Height (numbers only)
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                heightInput = it
                                heightDirty = true
                                validationErrors = validationErrors - "height"
                            }
                        },
                        label = { Text("Height ($heightUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("member_height_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("height"),
                        supportingText = validationErrors["height"]?.let { { Text(it, color = DangerRed) } }
                    )

                    // Weekly status notice
                    Spacer(modifier = Modifier.height(10.dp))
                    if (isSavedThisWeek && !isEditingThisWeek) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SuccessGreen.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Weekly record saved for this week. You can add a new record next week.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SuccessGreen
                                    )
                                }
                                TextButton(onClick = { isEditingThisWeek = true }) {
                                    Text("Edit", color = BoldRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    } else if (isEditingThisWeek) {
                        Text(
                            text = "Editing this week's record. Changes will update your active weekly entry.",
                            style = MaterialTheme.typography.labelSmall,
                            color = FlameOrange
                        )
                    } else {
                        Text(
                            text = "Weekly record only: you can save 1 record per week and add a new record the following week.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // =========================================================================
        // SECTION 2: BODY MEASUREMENT
        // =========================================================================

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(FlameOrange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("2", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "BODY MEASUREMENT",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Body Measurement Input Fields Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Measurement Inputs",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // Body Measurement Unit Selector: cm or inches
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Unit:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            SegmentedUnitSelector(
                                selectedUnit = measurementUnit,
                                options = listOf("cm", "inches"),
                                tagPrefix = "progress_measurement_unit",
                                onUnitSelected = { newUnit ->
                                    switchUnit(newUnit.equals("cm", ignoreCase = true))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Chest input (numbers only)
                    OutlinedTextField(
                        value = chestInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                chestInput = it
                                chestDirty = true
                                validationErrors = validationErrors - "chest"
                            }
                        },
                        label = { Text("Chest ($measurementUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("body_chest_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("chest"),
                        supportingText = validationErrors["chest"]?.let { { Text(it, color = DangerRed) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Arm input (numbers only)
                    OutlinedTextField(
                        value = armInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                armInput = it
                                armDirty = true
                                validationErrors = validationErrors - "arm"
                            }
                        },
                        label = { Text("Arm ($measurementUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("body_arm_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("arm"),
                        supportingText = validationErrors["arm"]?.let { { Text(it, color = DangerRed) } }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Waist input (numbers only)
                    OutlinedTextField(
                        value = waistInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                waistInput = it
                                waistDirty = true
                                validationErrors = validationErrors - "waist"
                            }
                        },
                        label = { Text("Waist ($measurementUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("body_waist_input"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("waist"),
                        supportingText = validationErrors["waist"]?.let { { Text(it, color = DangerRed) } }
                    )
                }
            }
        }

        // Change in Each Measurement Compared with the Previous Week Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Body Measurement Changes vs Previous Week",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (latestRecord != null && previousRecord != null) {
                        val currChest = latestRecord.getDisplayChest(measurementUnit)
                        val prevChest = previousRecord.getDisplayChest(measurementUnit)
                        val deltaChest = currChest - prevChest

                        val currArm = latestRecord.getDisplayArm(measurementUnit)
                        val prevArm = previousRecord.getDisplayArm(measurementUnit)
                        val deltaArm = currArm - prevArm

                        val currWaist = latestRecord.getDisplayWaist(measurementUnit)
                        val prevWaist = previousRecord.getDisplayWaist(measurementUnit)
                        val deltaWaist = currWaist - prevWaist

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Chest Delta
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", currChest, measurementUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Chest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                ComparisonBadge(delta = deltaChest, unit = measurementUnit, isWeight = false)
                            }

                            VerticalDivider(modifier = Modifier.height(36.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                            // Arm Delta
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", currArm, measurementUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Arm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                ComparisonBadge(delta = deltaArm, unit = measurementUnit, isWeight = false)
                            }

                            VerticalDivider(modifier = Modifier.height(36.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                            // Waist Delta
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = String.format(Locale.US, "%.1f %s", currWaist, measurementUnit),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Waist", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                ComparisonBadge(delta = deltaWaist, unit = measurementUnit, isWeight = false)
                            }
                        }
                    } else if (latestRecord != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Baseline body measurements logged (Chest: ${String.format(Locale.US, "%.1f %s", latestRecord.getDisplayChest(measurementUnit), measurementUnit)}, Arm: ${String.format(Locale.US, "%.1f %s", latestRecord.getDisplayArm(measurementUnit), measurementUnit)}, Waist: ${String.format(Locale.US, "%.1f %s", latestRecord.getDisplayWaist(measurementUnit), measurementUnit)}). Changes will calculate next week!",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Save your weekly record to establish baseline chest, arm, and waist measurements.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Unified Save Button (Saved Together with the Weekly Record)
        item {
            val canSave = !isSavedThisWeek || isEditingThisWeek

            if (saveSuccessMessage != null) {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = saveSuccessMessage ?: "",
                            color = SuccessGreen,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (validationErrors.isNotEmpty()) {
                Surface(
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = DangerRed, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Please fix the required fields:",
                                color = DangerRed,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        validationErrors.values.forEach { err ->
                            Text("• $err", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    val result = BiometricValidator.validateWeeklyProgressInputs(
                        nameInput = nameInput,
                        ageInput = ageInput,
                        weightInput = weightInput,
                        heightInput = heightInput,
                        chestInput = chestInput,
                        armInput = armInput,
                        waistInput = waistInput,
                        useMetric = useMetric,
                        canonicalWeightKg = canonicalWeightKg,
                        canonicalHeightCm = canonicalHeightCm,
                        canonicalChestCm = canonicalChestCm,
                        canonicalArmCm = canonicalArmCm,
                        canonicalWaistCm = canonicalWaistCm,
                        weightDirty = weightDirty,
                        heightDirty = heightDirty,
                        chestDirty = chestDirty,
                        armDirty = armDirty,
                        waistDirty = waistDirty
                    )

                    if (!result.isValid) {
                        validationErrors = result.errors
                        saveSuccessMessage = null
                        return@Button
                    }

                    validationErrors = emptyMap()
                    canonicalWeightKg = result.weightKg
                    canonicalHeightCm = result.heightCm
                    canonicalChestCm = result.chestCm
                    canonicalArmCm = result.armCm
                    canonicalWaistCm = result.waistCm
                    weightDirty = false
                    heightDirty = false
                    chestDirty = false
                    armDirty = false
                    waistDirty = false

                    onSaveWeeklyProgressRecord(
                        result.cleanName,
                        result.age,
                        result.weightKg,
                        result.heightCm,
                        result.chestCm,
                        result.armCm,
                        result.waistCm,
                        useMetric
                    ) { weekNum ->
                        saveSuccessMessage = "Weekly Record for Week $weekNum saved successfully!"
                        isEditingThisWeek = false
                    }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BoldRed,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_weekly_record_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditingThisWeek) {
                        "UPDATE THIS WEEK'S RECORD"
                    } else if (isSavedThisWeek) {
                        "WEEK RECORD ALREADY SAVED (NEXT WEEK TO LOG)"
                    } else {
                        "SAVE WEEKLY RECORD"
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // =========================================================================
        // LIST OF PAST WEEKLY RECORDS
        // =========================================================================

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Past Weekly Records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (records.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    val filterName = when {
                                        selectedFilterMonth != null && selectedFilterYear != null ->
                                            "${monthNames[selectedFilterMonth!!]}_$selectedFilterYear"
                                        selectedFilterMonth != null ->
                                            monthNames[selectedFilterMonth!!]
                                        selectedFilterYear != null ->
                                            "$selectedFilterYear"
                                        else -> "all_time"
                                    }
                                    exportedCsvText = WeeklyRecordsCsvExporter.generateCsvString(filteredRecords)
                                    exportedCsvFile = WeeklyRecordsCsvExporter.writeCsvFile(context, filteredRecords, filterName)
                                    exportDialogVisible = true
                                },
                                enabled = filteredRecords.isNotEmpty(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .testTag("export_csv_button"),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = BoldRed.copy(alpha = 0.15f),
                                    contentColor = BoldRed,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (filteredRecords.isNotEmpty()) BoldRed else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Export CSV",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                val countText = if (selectedFilterYear != null || selectedFilterMonth != null) {
                                    "${filteredRecords.size} of ${records.size} records"
                                } else {
                                    "${records.size} ${if (records.size == 1) "record" else "records"}"
                                }
                                Text(
                                    text = countText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Date Range & Month/Year Filter Selector
                if (records.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // All Time
                        FilterChip(
                            selected = quickFilterPreset == "ALL" && selectedFilterYear == null && selectedFilterMonth == null,
                            onClick = {
                                quickFilterPreset = "ALL"
                                selectedFilterYear = null
                                selectedFilterMonth = null
                            },
                            label = { Text("All Time", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BoldRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("filter_all_time_chip")
                        )

                        // This Month
                        FilterChip(
                            selected = quickFilterPreset == "THIS_MONTH",
                            onClick = {
                                val now = ManilaDateTime.manilaCalendar()
                                selectedFilterYear = now.get(Calendar.YEAR)
                                selectedFilterMonth = now.get(Calendar.MONTH)
                                quickFilterPreset = "THIS_MONTH"
                            },
                            label = { Text("This Month", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BoldRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("filter_this_month_chip")
                        )

                        // This Year
                        FilterChip(
                            selected = quickFilterPreset == "THIS_YEAR",
                            onClick = {
                                val now = ManilaDateTime.manilaCalendar()
                                selectedFilterYear = now.get(Calendar.YEAR)
                                selectedFilterMonth = null
                                quickFilterPreset = "THIS_YEAR"
                            },
                            label = { Text("This Year", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BoldRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("filter_this_year_chip")
                        )

                        // Custom Month / Year Selector Button
                        val isCustomActive = (selectedFilterYear != null || selectedFilterMonth != null) && quickFilterPreset == "CUSTOM"
                        FilterChip(
                            selected = isCustomActive,
                            onClick = {
                                dialogSelectedYear = selectedFilterYear
                                dialogSelectedMonth = selectedFilterMonth
                                filterDialogVisible = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            label = {
                                val labelText = if (isCustomActive) {
                                    val mPart = selectedFilterMonth?.let { shortMonthNames[it] } ?: ""
                                    val yPart = selectedFilterYear?.toString() ?: ""
                                    if (mPart.isNotEmpty() && yPart.isNotEmpty()) "$mPart $yPart"
                                    else mPart.ifEmpty { yPart }
                                } else "Month / Year"
                                Text(labelText, fontSize = 11.sp)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BoldRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("filter_custom_date_button")
                        )
                    }

                    // Active filter indicator badge when filtered
                    if (selectedFilterYear != null || selectedFilterMonth != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BoldRed.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BoldRed.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val filterDesc = when {
                                    selectedFilterMonth != null && selectedFilterYear != null ->
                                        "${monthNames[selectedFilterMonth!!]} $selectedFilterYear"
                                    selectedFilterMonth != null ->
                                        "${monthNames[selectedFilterMonth!!]} (All Years)"
                                    else ->
                                        "Year $selectedFilterYear"
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FilterList, contentDescription = null, tint = BoldRed, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Filter: $filterDesc",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        selectedFilterYear = null
                                        selectedFilterMonth = null
                                        quickFilterPreset = "ALL"
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Clear", color = BoldRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recharts-inspired Trend Analytics Chart over Filtered Date Range
        if (records.isNotEmpty() || measurements.isNotEmpty()) {
            item {
                val activeFilterLabel = when {
                    selectedFilterMonth != null && selectedFilterYear != null ->
                        "${monthNames[selectedFilterMonth!!]} $selectedFilterYear"
                    selectedFilterMonth != null ->
                        "${monthNames[selectedFilterMonth!!]} (All Years)"
                    selectedFilterYear != null ->
                        "Year $selectedFilterYear"
                    else ->
                        "Last 3 Months"
                }

                val filteredMeasurementsForChart = remember(measurements, selectedFilterYear, selectedFilterMonth) {
                    if (selectedFilterYear == null && selectedFilterMonth == null) {
                        measurements
                    } else {
                        measurements.filter { m ->
                            val cal = ManilaDateTime.manilaCalendar(m.timestamp)
                            val recYear = cal.get(Calendar.YEAR)
                            val recMonth = cal.get(Calendar.MONTH)
                            (selectedFilterYear == null || recYear == selectedFilterYear) &&
                                (selectedFilterMonth == null || recMonth == selectedFilterMonth)
                        }
                    }
                }

                ProgressTrendChart(
                    records = filteredRecords,
                    measurements = filteredMeasurementsForChart,
                    weightUnit = weightUnit,
                    measurementUnit = measurementUnit,
                    dateFilterLabel = activeFilterLabel
                )
            }
        }

        if (records.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No weekly records saved yet. Fill out the member information and body measurements above to record your first weekly entry!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else if (filteredRecords.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = BoldRed, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No records found for this period",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val filterDesc = when {
                            selectedFilterMonth != null && selectedFilterYear != null ->
                                "${monthNames[selectedFilterMonth!!]} $selectedFilterYear"
                            selectedFilterMonth != null ->
                                "${monthNames[selectedFilterMonth!!]} (All Years)"
                            else ->
                                "Year $selectedFilterYear"
                        }
                        Text(
                            text = "No weekly records found for $filterDesc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                selectedFilterYear = null
                                selectedFilterMonth = null
                                quickFilterPreset = "ALL"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Show All Records", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        } else {
            items(filteredRecords, key = { it.id }) { record ->
                val dateStr = ManilaDateTime.formatDateTime(record.timestamp)
                val recWeight = record.getDisplayWeight(weightUnit)
                val recHeight = record.getDisplayHeight(heightUnit)
                val recChest = record.getDisplayChest(measurementUnit)
                val recArm = record.getDisplayArm(measurementUnit)
                val recWaist = record.getDisplayWaist(measurementUnit)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BoldRed.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "WEEK ${record.weekNumber}",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = BoldRed
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { recordToDelete = record },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("delete_weekly_record_${record.id}")
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Delete record",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Member details
                        Text(
                            text = "${record.memberName} • ${record.memberAge} yrs",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Weight: ${String.format(Locale.US, "%.1f %s", recWeight, weightUnit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Height: ${String.format(Locale.US, "%.1f %s", recHeight, heightUnit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Body measurements
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Chest: ${String.format(Locale.US, "%.1f %s", recChest, measurementUnit)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = FlameOrange
                            )
                            Text(
                                text = "Arm: ${String.format(Locale.US, "%.1f %s", recArm, measurementUnit)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = FlameOrange
                            )
                            Text(
                                text = "Waist: ${String.format(Locale.US, "%.1f %s", recWaist, measurementUnit)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = FlameOrange
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

/**
 * Mobile-first segmented button for unit selectors (kg/lb, cm/inches)
 */
@Composable
private fun SegmentedUnitSelector(
    selectedUnit: String,
    options: List<String>,
    tagPrefix: String? = null,
    onUnitSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedUnit.equals(option, ignoreCase = true)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) BoldRed else Color.Transparent)
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .then(
                        if (tagPrefix != null) Modifier.testTag("${tagPrefix}_${option.lowercase()}")
                        else Modifier
                    )
                    .clickable { onUnitSelected(option) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Visual badge for week-by-week change indicators
 */
@Composable
private fun ComparisonBadge(
    delta: Float,
    unit: String,
    isWeight: Boolean
) {
    val sign = if (delta > 0) "+" else ""
    val formatted = "$sign${String.format(Locale.US, "%.1f %s", delta, unit)}"

    val badgeColor = when {
        kotlin.math.abs(delta) < 0.05f -> ElectricCyan
        isWeight && delta < 0f -> SuccessGreen // Weight loss typically green in gym app
        isWeight && delta > 0f -> FlameOrange
        !isWeight && delta > 0f -> SuccessGreen // Muscle growth (chest/arm) green
        else -> FlameOrange
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = badgeColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                when {
                    kotlin.math.abs(delta) < 0.05f -> Icons.Default.TrendingFlat
                    delta > 0f -> Icons.Default.TrendingUp
                    else -> Icons.Default.TrendingDown
                },
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

/**
 * Validates decimal inputs (allowing positive decimal numbers only)
 */
private fun isValidDecimalInput(input: String): Boolean {
    if (input.isEmpty()) return true
    // Allow single decimal point
    if (input.count { it == '.' } > 1) return false
    return input.all { it.isDigit() || it == '.' }
}

/**
 * Data entry form for users to track weight and body measurements over time,
 * storing them in Room for historical analysis and trend visualization.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementDataEntrySheet(
    initialWeightKg: Float,
    initialHeightCm: Float,
    useMetric: Boolean = true,
    onUpdateUseMetric: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onSubmit: (weightKg: Float, heightCm: Float, chestCm: Float?, armCm: Float?, waistCm: Float?, notes: String, timestamp: Long) -> Unit
) {
    val activeWeightUnit = if (useMetric) "kg" else "lb"
    val activeHeightUnit = if (useMetric) "cm" else "inches"
    val activeMeasurementUnit = if (useMetric) "cm" else "inches"

    var canonicalWeightKg by remember { mutableFloatStateOf(initialWeightKg.takeIf { it > 0f } ?: 70.0f) }
    var canonicalHeightCm by remember { mutableFloatStateOf(initialHeightCm.takeIf { it > 0f } ?: 175.0f) }
    var canonicalChestCm by remember { mutableFloatStateOf(96.0f) }
    var canonicalArmCm by remember { mutableFloatStateOf(35.0f) }
    var canonicalWaistCm by remember { mutableFloatStateOf(82.0f) }

    var weightDirty by remember { mutableStateOf(false) }
    var heightDirty by remember { mutableStateOf(false) }
    var chestDirty by remember { mutableStateOf(false) }
    var armDirty by remember { mutableStateOf(false) }
    var waistDirty by remember { mutableStateOf(false) }

    var weightInput by remember { mutableStateOf(BiometricValidator.formatWeightInput(canonicalWeightKg, useMetric)) }
    var heightInput by remember { mutableStateOf(BiometricValidator.formatHeightInput(canonicalHeightCm, useMetric)) }
    var chestInput by remember { mutableStateOf(BiometricValidator.formatCircumferenceInput(canonicalChestCm, useMetric)) }
    var armInput by remember { mutableStateOf(BiometricValidator.formatCircumferenceInput(canonicalArmCm, useMetric)) }
    var waistInput by remember { mutableStateOf(BiometricValidator.formatCircumferenceInput(canonicalWaistCm, useMetric)) }
    var notesInput by remember { mutableStateOf("") }
    var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun switchSheetUnit(targetMetric: Boolean) {
        if (targetMetric == useMetric) return
        if (weightDirty) {
            BiometricValidator.resolveMetricWeightOrNull(weightInput, useMetric, canonicalWeightKg, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalWeightKg = it }
            weightDirty = false
        }
        if (heightDirty) {
            BiometricValidator.resolveMetricLengthOrNull(heightInput, useMetric, canonicalHeightCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalHeightCm = it }
            heightDirty = false
        }
        if (chestDirty) {
            BiometricValidator.resolveMetricLengthOrNull(chestInput, useMetric, canonicalChestCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalChestCm = it }
            chestDirty = false
        }
        if (armDirty) {
            BiometricValidator.resolveMetricLengthOrNull(armInput, useMetric, canonicalArmCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalArmCm = it }
            armDirty = false
        }
        if (waistDirty) {
            BiometricValidator.resolveMetricLengthOrNull(waistInput, useMetric, canonicalWaistCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalWaistCm = it }
            waistDirty = false
        }
        validationErrors = emptyMap()
        onUpdateUseMetric(targetMetric)
    }

    LaunchedEffect(useMetric) {
        if (!weightDirty) weightInput = BiometricValidator.formatWeightInput(canonicalWeightKg, useMetric)
        if (!heightDirty) heightInput = BiometricValidator.formatHeightInput(canonicalHeightCm, useMetric)
        if (!chestDirty && chestInput.isNotBlank()) chestInput = BiometricValidator.formatCircumferenceInput(canonicalChestCm, useMetric)
        if (!armDirty && armInput.isNotBlank()) armInput = BiometricValidator.formatCircumferenceInput(canonicalArmCm, useMetric)
        if (!waistDirty && waistInput.isNotBlank()) waistInput = BiometricValidator.formatCircumferenceInput(canonicalWaistCm, useMetric)
    }

    // Live BMI Calculation
    val liveBmi = remember(weightInput, heightInput, useMetric, weightDirty, heightDirty, canonicalWeightKg, canonicalHeightCm) {
        val wKg = BiometricValidator.resolveMetricWeightOrNull(weightInput, useMetric, canonicalWeightKg, weightDirty)
        val hCm = BiometricValidator.resolveMetricLengthOrNull(heightInput, useMetric, canonicalHeightCm, heightDirty)
        if (wKg != null && wKg > 0f && hCm != null && hCm > 50f) {
            val hM = hCm / 100f
            wKg / (hM * hM)
        } else null
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) },
        modifier = Modifier.testTag("measurement_entry_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(BoldRed.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.MonitorWeight,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Track Body Measurements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_measurement_entry_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Live BMI Preview Card
            liveBmi?.let { bmi ->
                val bmiCategory = when {
                    bmi < 18.5f -> "Underweight" to FlameOrange
                    bmi < 25f -> "Normal weight" to SuccessGreen
                    bmi < 30f -> "Overweight" to FlameOrange
                    else -> "Obese" to DangerRed
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Estimated BMI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", bmi),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = bmiCategory.second.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, bmiCategory.second.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = bmiCategory.first,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = bmiCategory.second
                            )
                        }
                    }
                }
            }

            // Unit Selectors (shared with profile unit preference)
            val sheetConfig = LocalConfiguration.current
            val isSmallScreen = sheetConfig.screenWidthDp < 480
            if (isSmallScreen) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Weight Unit:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SegmentedUnitSelector(
                            selectedUnit = activeWeightUnit,
                            options = listOf("kg", "lb"),
                            onUnitSelected = { newUnit -> switchSheetUnit(newUnit.equals("kg", ignoreCase = true)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dimensions Unit:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SegmentedUnitSelector(
                            selectedUnit = activeMeasurementUnit,
                            options = listOf("cm", "inches"),
                            onUnitSelected = { newUnit -> switchSheetUnit(newUnit.equals("cm", ignoreCase = true)) }
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Weight:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        SegmentedUnitSelector(
                            selectedUnit = activeWeightUnit,
                            options = listOf("kg", "lb"),
                            onUnitSelected = { newUnit -> switchSheetUnit(newUnit.equals("kg", ignoreCase = true)) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dimensions:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        SegmentedUnitSelector(
                            selectedUnit = activeMeasurementUnit,
                            options = listOf("cm", "inches"),
                            onUnitSelected = { newUnit -> switchSheetUnit(newUnit.equals("cm", ignoreCase = true)) }
                        )
                    }
                }
            }

            // Weight & Height
            val isNarrowPhone = sheetConfig.screenWidthDp < 360
            if (isNarrowPhone) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                weightInput = it
                                weightDirty = true
                                validationErrors = validationErrors - "weight"
                            }
                        },
                        label = { Text("Weight ($activeWeightUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_entry_weight"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("weight"),
                        supportingText = validationErrors["weight"]?.let { { Text(it, color = DangerRed) } }
                    )

                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                heightInput = it
                                heightDirty = true
                                validationErrors = validationErrors - "height"
                            }
                        },
                        label = { Text("Height ($activeHeightUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_entry_height"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("height"),
                        supportingText = validationErrors["height"]?.let { { Text(it, color = DangerRed) } }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                weightInput = it
                                weightDirty = true
                                validationErrors = validationErrors - "weight"
                            }
                        },
                        label = { Text("Weight ($activeWeightUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_entry_weight"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("weight"),
                        supportingText = validationErrors["weight"]?.let { { Text(it, color = DangerRed) } }
                    )

                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                heightInput = it
                                heightDirty = true
                                validationErrors = validationErrors - "height"
                            }
                        },
                        label = { Text("Height ($activeHeightUnit) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_entry_height"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("height"),
                        supportingText = validationErrors["height"]?.let { { Text(it, color = DangerRed) } }
                    )
                }
            }

            // Body Measurements: Chest, Arm, Waist
            Text(
                text = "Circumferences ($activeMeasurementUnit)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isCompactCircumferences = sheetConfig.screenWidthDp < 420
            if (isCompactCircumferences) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chestInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                chestInput = it
                                chestDirty = true
                                validationErrors = validationErrors - "chest"
                            }
                        },
                        label = { Text("Chest ($activeMeasurementUnit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_entry_chest"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("chest"),
                        supportingText = validationErrors["chest"]?.let { { Text(it, color = DangerRed) } }
                    )

                    OutlinedTextField(
                        value = armInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                armInput = it
                                armDirty = true
                                validationErrors = validationErrors - "arm"
                            }
                        },
                        label = { Text("Arm ($activeMeasurementUnit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_entry_arm"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("arm"),
                        supportingText = validationErrors["arm"]?.let { { Text(it, color = DangerRed) } }
                    )

                    OutlinedTextField(
                        value = waistInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                waistInput = it
                                waistDirty = true
                                validationErrors = validationErrors - "waist"
                            }
                        },
                        label = { Text("Waist ($activeMeasurementUnit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_entry_waist"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("waist"),
                        supportingText = validationErrors["waist"]?.let { { Text(it, color = DangerRed) } }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = chestInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                chestInput = it
                                chestDirty = true
                                validationErrors = validationErrors - "chest"
                            }
                        },
                        label = { Text("Chest") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_entry_chest"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("chest"),
                        supportingText = validationErrors["chest"]?.let { { Text(it, color = DangerRed) } }
                    )

                    OutlinedTextField(
                        value = armInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                armInput = it
                                armDirty = true
                                validationErrors = validationErrors - "arm"
                            }
                        },
                        label = { Text("Arm") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_entry_arm"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("arm"),
                        supportingText = validationErrors["arm"]?.let { { Text(it, color = DangerRed) } }
                    )

                    OutlinedTextField(
                        value = waistInput,
                        onValueChange = {
                            if (isValidDecimalInput(it)) {
                                waistInput = it
                                waistDirty = true
                                validationErrors = validationErrors - "waist"
                            }
                        },
                        label = { Text("Waist") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_entry_waist"),
                        singleLine = true,
                        colors = progressTextFieldColors(),
                        isError = validationErrors.containsKey("waist"),
                        supportingText = validationErrors["waist"]?.let { { Text(it, color = DangerRed) } }
                    )
                }
            }

            // Notes
            OutlinedTextField(
                value = notesInput,
                onValueChange = { notesInput = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("e.g. Morning fasted check-in") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_entry_notes"),
                colors = progressTextFieldColors(),
                singleLine = true
            )

            // Validation Summary Error if any
            if (validationErrors.isNotEmpty()) {
                Surface(
                    color = DangerRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        validationErrors.values.forEach { err ->
                            Text("• $err", color = DangerRed, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Action Buttons: Cancel and Submit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("cancel_measurement_entry_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val errors = mutableMapOf<String, String>()
                        val wKg = BiometricValidator.resolveMetricWeightOrNull(weightInput, useMetric, canonicalWeightKg, weightDirty)
                        if (wKg == null || !wKg.isFinite() || wKg < WeekDefinition.MIN_WEIGHT_KG || wKg > WeekDefinition.MAX_WEIGHT_KG) {
                            errors["weight"] = if (useMetric) "Weight must be between 20 and 300 kg" else "Weight must be between 44.1 and 661.4 lb"
                        }
                        val hCm = BiometricValidator.resolveMetricLengthOrNull(heightInput, useMetric, canonicalHeightCm, heightDirty)
                        if (hCm == null || !hCm.isFinite() || hCm < WeekDefinition.MIN_HEIGHT_CM || hCm > WeekDefinition.MAX_HEIGHT_CM) {
                            errors["height"] = if (useMetric) "Height must be between 100 and 250 cm" else "Height must be between 39.4 and 98.4 in"
                        }
                        val cCm = if (chestInput.isBlank()) null else {
                            BiometricValidator.resolveMetricLengthOrNull(chestInput, useMetric, canonicalChestCm, chestDirty)
                                .also {
                                    if (it == null || !it.isFinite() || it < WeekDefinition.MIN_CHEST_CM || it > WeekDefinition.MAX_CHEST_CM) {
                                        errors["chest"] = if (useMetric) "Chest must be between 30 and 250 cm" else "Chest must be between 11.8 and 98.4 in"
                                    }
                                }
                        }
                        val aCm = if (armInput.isBlank()) null else {
                            BiometricValidator.resolveMetricLengthOrNull(armInput, useMetric, canonicalArmCm, armDirty)
                                .also {
                                    if (it == null || !it.isFinite() || it < WeekDefinition.MIN_ARM_CM || it > WeekDefinition.MAX_ARM_CM) {
                                        errors["arm"] = if (useMetric) "Arm must be between 10 and 100 cm" else "Arm must be between 3.9 and 39.4 in"
                                    }
                                }
                        }
                        val wstCm = if (waistInput.isBlank()) null else {
                            BiometricValidator.resolveMetricLengthOrNull(waistInput, useMetric, canonicalWaistCm, waistDirty)
                                .also {
                                    if (it == null || !it.isFinite() || it < WeekDefinition.MIN_WAIST_CM || it > WeekDefinition.MAX_WAIST_CM) {
                                        errors["waist"] = if (useMetric) "Waist must be between 30 and 250 cm" else "Waist must be between 11.8 and 98.4 in"
                                    }
                                }
                        }

                        if (errors.isNotEmpty()) {
                            validationErrors = errors
                            return@Button
                        }

                        onSubmit(
                            wKg!!,
                            hCm!!,
                            cCm,
                            aCm,
                            wstCm,
                            notesInput.trim(),
                            System.currentTimeMillis()
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("submit_measurement_entry_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save to Room", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
