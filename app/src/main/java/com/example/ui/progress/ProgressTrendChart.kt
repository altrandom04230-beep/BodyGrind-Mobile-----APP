package com.example.ui.progress

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.progress.WeekDefinition
import com.example.data.progress.WeeklyRecord
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import java.util.*
import kotlin.math.abs

fun BodyMeasurementEntity.toWeeklyRecord(): WeeklyRecord {
    val (isoYear, isoWeek) = WeekDefinition.isoYearAndWeekFor(timestamp)
    val resolvedWeekNumber = weekNumber ?: isoWeek
    val resolvedWeekKey = weekKey?.takeIf { it.isNotBlank() } ?: WeekDefinition.weekKeyFor(timestamp)
    val resolvedName = memberName?.takeIf { it.isNotBlank() }
        ?: if (notes.isNotBlank()) notes else "Athlete Log"
    return WeeklyRecord(
        id = "room_$id",
        weekNumber = resolvedWeekNumber,
        year = isoYear,
        calendarWeek = isoWeek,
        timestamp = timestamp,
        memberName = resolvedName,
        memberAge = memberAge ?: 26,
        weightKg = weightKg,
        heightCm = heightCm,
        weightUnit = "kg",
        heightUnit = "cm",
        chestCm = chestCm ?: 96f,
        armCm = armCm ?: 35f,
        waistCm = waistCm ?: 82f,
        measurementUnit = "cm",
        weekKey = resolvedWeekKey,
        roomId = id
    )
}

enum class ChartTimeframe(val label: String, val days: Int?, val testId: String) {
    LAST_3_MONTHS("Last 3 Months", 90, "timeframe_3_months"),
    LAST_1_MONTH("1 Month", 30, "timeframe_1_month"),
    LAST_6_MONTHS("6 Months", 180, "timeframe_6_months"),
    ALL_TIME("All Time", null, "timeframe_all_time")
}

enum class ProgressChartMetric(val label: String) {
    WEIGHT("Weight"),
    CHEST("Chest"),
    ARM("Arm"),
    WAIST("Waist"),
    ALL_MEASUREMENTS("All Trends");

    val color: Color
        @Composable get() = when (this) {
            WEIGHT -> BoldRed
            CHEST -> if (LocalIsDarkTheme.current) ChartChestBlueDark else ChartChestBlueLight
            ARM -> if (LocalIsDarkTheme.current) ChartArmGreenDark else ChartArmGreenLight
            WAIST -> if (LocalIsDarkTheme.current) ChartWaistAmberDark else ChartWaistAmberLight
            ALL_MEASUREMENTS -> if (LocalIsDarkTheme.current) ChartPurpleDark else ChartPurpleLight
        }
}

/**
 * A Recharts-inspired interactive data visualization component for Android Jetpack Compose.
 * Visualizes weight and body measurement trends over the filtered date range (defaulting to the last 3 months).
 *
 * Implements key Recharts design patterns:
 * - Cartesian grid lines with Y-Axis scale marks
 * - Smooth monotone cubic Bézier curve paths
 * - Gradient area fill under active curve
 * - Interactive scrub/tap Tooltip popover with vertical crosshair guide
 * - Multi-series overlay mode for Chest, Arm, Waist and Weight
 * - Trend summary statistics (Start, Latest, Net Change, Min/Max)
 */
@Composable
fun ProgressTrendChart(
    records: List<WeeklyRecord> = emptyList(),
    measurements: List<BodyMeasurementEntity> = emptyList(),
    weightUnit: String = "kg",
    measurementUnit: String = "cm",
    dateFilterLabel: String = "Last 3 Months",
    initialTimeframe: ChartTimeframe = ChartTimeframe.LAST_3_MONTHS,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(ProgressChartMetric.WEIGHT) }
    var selectedTimeframe by remember { mutableStateOf(initialTimeframe) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    // Combine user-entered records from WeeklyRecord and Room BodyMeasurementEntity
    val allUnifiedRecords = remember(records, measurements) {
        val list = mutableListOf<WeeklyRecord>()
        list.addAll(records)
        measurements.forEach { m ->
            val alreadyPresent = list.any {
                (it.roomId != null && it.roomId == m.id) ||
                    (!m.weekKey.isNullOrBlank() && it.weekKey == m.weekKey) ||
                    kotlin.math.abs(it.timestamp - m.timestamp) < 3600000L
            }
            if (!alreadyPresent) {
                list.add(m.toWeeklyRecord())
            }
        }
        list.sortedBy { it.timestamp }
    }

    // Filter chronologically based on active timeframe (default: Last 3 Months)
    val chronologicalRecords = remember(allUnifiedRecords, selectedTimeframe) {
        val days = selectedTimeframe.days
        if (days == null) {
            allUnifiedRecords
        } else {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 86_400_000L)
            allUnifiedRecords.filter { it.timestamp >= cutoff }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("progress_trend_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: Title, Subtitle, and Filter Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Trend Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BoldRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BoldRed.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateFilterLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BoldRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recharts Timeframe Selector (Last 3 Months, 1 Month, 6 Months, All Time)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartTimeframe.values().forEach { tf ->
                    val isSelected = selectedTimeframe == tf
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTimeframe = tf
                            selectedPointIndex = null
                        },
                        label = {
                            Text(
                                text = tf.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BoldRed.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.testTag(tf.testId)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Recharts Metric Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressChartMetric.values().forEach { metric ->
                    val isSelected = selectedMetric == metric
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedMetric = metric
                            selectedPointIndex = null
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(metric.color, CircleShape)
                            )
                        },
                        label = {
                            val unitSuffix = when (metric) {
                                ProgressChartMetric.WEIGHT -> " ($weightUnit)"
                                ProgressChartMetric.CHEST,
                                ProgressChartMetric.ARM,
                                ProgressChartMetric.WAIST -> " ($measurementUnit)"
                                ProgressChartMetric.ALL_MEASUREMENTS -> ""
                            }
                            Text(
                                text = "${metric.label}$unitSuffix",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = metric.color.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) metric.color else MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier.testTag("chart_metric_${metric.name.lowercase()}_chip")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content: Empty State, Baseline State, or Interactive Chart
            if (chronologicalRecords.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No records for $dateFilterLabel",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Save weekly records or change the filter to view progress curves",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (chronologicalRecords.size == 1) {
                // Single Record (Baseline) state
                val single = chronologicalRecords.first()
                val currentUnit = when (selectedMetric) {
                    ProgressChartMetric.WEIGHT -> weightUnit
                    ProgressChartMetric.ALL_MEASUREMENTS -> ""
                    else -> measurementUnit
                }
                val singleVal = when (selectedMetric) {
                    ProgressChartMetric.WEIGHT -> single.getDisplayWeight(weightUnit)
                    ProgressChartMetric.CHEST -> single.getDisplayChest(measurementUnit)
                    ProgressChartMetric.ARM -> single.getDisplayArm(measurementUnit)
                    ProgressChartMetric.WAIST -> single.getDisplayWaist(measurementUnit)
                    ProgressChartMetric.ALL_MEASUREMENTS -> single.getDisplayWeight(weightUnit)
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Week ${single.weekNumber} Baseline Established",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = ManilaDateTime.formatDate(single.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = selectedMetric.color.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (selectedMetric == ProgressChartMetric.ALL_MEASUREMENTS) "Baseline" else "${String.format(Locale.US, "%.1f", singleVal)} $currentUnit",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = selectedMetric.color
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Log your next weekly record to begin drawing Recharts-style trajectory curves and week-by-week deltas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Interactive Chart Mode (2+ records)

                // Summary Stats Bar
                if (selectedMetric != ProgressChartMetric.ALL_MEASUREMENTS) {
                    val values = chronologicalRecords.map { rec ->
                        when (selectedMetric) {
                            ProgressChartMetric.WEIGHT -> rec.getDisplayWeight(weightUnit)
                            ProgressChartMetric.CHEST -> rec.getDisplayChest(measurementUnit)
                            ProgressChartMetric.ARM -> rec.getDisplayArm(measurementUnit)
                            ProgressChartMetric.WAIST -> rec.getDisplayWaist(measurementUnit)
                            else -> 0f
                        }
                    }
                    val startVal = values.first()
                    val latestVal = values.last()
                    val netChange = latestVal - startVal
                    val minVal = values.minOrNull() ?: 0f
                    val maxVal = values.maxOrNull() ?: 0f
                    val activeUnit = when (selectedMetric) {
                        ProgressChartMetric.WEIGHT -> weightUnit
                        else -> measurementUnit
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Start
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", startVal)} $activeUnit",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Start", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.textTertiary)
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            // Latest
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", latestVal)} $activeUnit",
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text("Latest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            // Net Change
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val changeSign = if (netChange > 0) "+" else ""
                                val changeColor = if (abs(netChange) < 0.05f) MaterialTheme.colorScheme.onSurfaceVariant
                                else if (netChange < 0) SuccessGreen
                                else BoldRed

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            abs(netChange) < 0.05f -> Icons.Default.TrendingFlat
                                            netChange > 0 -> Icons.Default.TrendingUp
                                            else -> Icons.Default.TrendingDown
                                        },
                                        contentDescription = null,
                                        tint = changeColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "$changeSign${String.format(Locale.US, "%.1f", netChange)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = changeColor
                                    )
                                }
                                Text("Net Change", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.textTertiary)
                            }

                            VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)

                            // Range Min/Max
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", minVal)} - ${String.format(Locale.US, "%.1f", maxVal)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = selectedMetric.color
                                )
                                Text("Min - Max", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.textTertiary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    // Multi-Series Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isDark = LocalIsDarkTheme.current
                        val chestColor = if (isDark) ChartChestBlueDark else ChartChestBlueLight
                        val armColor = if (isDark) ChartArmGreenDark else ChartArmGreenLight
                        val waistColor = if (isDark) ChartWaistAmberDark else ChartWaistAmberLight
                        LegendItem("Chest ($measurementUnit)", chestColor)
                        LegendItem("Arm ($measurementUnit)", armColor)
                        LegendItem("Waist ($measurementUnit)", waistColor)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Interactive Recharts Tooltip Overlay
                val activePointIndex = selectedPointIndex
                val activeRecord = activePointIndex?.let { chronologicalRecords.getOrNull(it) }

                AnimatedVisibility(
                    visible = activeRecord != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    activeRecord?.let { rec ->
                        val prevRec = if (activePointIndex != null && activePointIndex > 0) {
                            chronologicalRecords.getOrNull(activePointIndex - 1)
                        } else null

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("chart_tooltip_overlay"),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, selectedMetric.color)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Week ${rec.weekNumber} • ${rec.memberName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = ManilaDateTime.formatDate(rec.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    if (selectedMetric != ProgressChartMetric.ALL_MEASUREMENTS) {
                                        val activeUnit = when (selectedMetric) {
                                            ProgressChartMetric.WEIGHT -> weightUnit
                                            else -> measurementUnit
                                        }
                                        val curVal = when (selectedMetric) {
                                            ProgressChartMetric.WEIGHT -> rec.getDisplayWeight(weightUnit)
                                            ProgressChartMetric.CHEST -> rec.getDisplayChest(measurementUnit)
                                            ProgressChartMetric.ARM -> rec.getDisplayArm(measurementUnit)
                                            ProgressChartMetric.WAIST -> rec.getDisplayWaist(measurementUnit)
                                            else -> 0f
                                        }

                                        val prevVal = prevRec?.let { pr ->
                                            when (selectedMetric) {
                                                ProgressChartMetric.WEIGHT -> pr.getDisplayWeight(weightUnit)
                                                ProgressChartMetric.CHEST -> pr.getDisplayChest(measurementUnit)
                                                ProgressChartMetric.ARM -> pr.getDisplayArm(measurementUnit)
                                                ProgressChartMetric.WAIST -> pr.getDisplayWaist(measurementUnit)
                                                else -> 0f
                                            }
                                        }

                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", curVal)} $activeUnit",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = selectedMetric.color
                                        )

                                        if (prevVal != null) {
                                            val d = curVal - prevVal
                                            val sign = if (d > 0) "+" else ""
                                            val dColor = if (abs(d) < 0.05f) MaterialTheme.colorScheme.textTertiary else if (d < 0) SuccessGreen else BoldRed
                                            Text(
                                                text = "$sign${String.format(Locale.US, "%.1f", d)} vs prev wk",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = dColor
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "C: ${String.format(Locale.US, "%.1f", rec.getDisplayChest(measurementUnit))} • A: ${String.format(Locale.US, "%.1f", rec.getDisplayArm(measurementUnit))} • W: ${String.format(Locale.US, "%.1f", rec.getDisplayWaist(measurementUnit))}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Weight: ${String.format(Locale.US, "%.1f", rec.getDisplayWeight(weightUnit))} $weightUnit",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BoldRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Recharts Canvas Chart Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("recharts_weight_line_chart")
                ) {
                    val count = chronologicalRecords.size

                    // Compute Global Min & Max for the Viewport
                    val allSeriesValues = remember(selectedMetric, chronologicalRecords, weightUnit, measurementUnit) {
                        when (selectedMetric) {
                            ProgressChartMetric.WEIGHT -> chronologicalRecords.map { it.getDisplayWeight(weightUnit) }
                            ProgressChartMetric.CHEST -> chronologicalRecords.map { it.getDisplayChest(measurementUnit) }
                            ProgressChartMetric.ARM -> chronologicalRecords.map { it.getDisplayArm(measurementUnit) }
                            ProgressChartMetric.WAIST -> chronologicalRecords.map { it.getDisplayWaist(measurementUnit) }
                            ProgressChartMetric.ALL_MEASUREMENTS -> {
                                val c = chronologicalRecords.map { it.getDisplayChest(measurementUnit) }
                                val a = chronologicalRecords.map { it.getDisplayArm(measurementUnit) }
                                val w = chronologicalRecords.map { it.getDisplayWaist(measurementUnit) }
                                c + a + w
                            }
                        }
                    }

                    val rawMin = allSeriesValues.minOrNull() ?: 0f
                    val rawMax = allSeriesValues.maxOrNull() ?: 100f
                    val paddingBuffer = ((rawMax - rawMin) * 0.12f).coerceAtLeast(1.5f)
                    val chartMin = (rawMin - paddingBuffer).coerceAtLeast(0f)
                    val chartMax = (rawMax + paddingBuffer).coerceAtLeast(chartMin + 2f)
                    val valueRange = (chartMax - chartMin).coerceAtLeast(1f)

                    val isDark = LocalIsDarkTheme.current
                    val chestColor = if (isDark) ChartChestBlueDark else ChartChestBlueLight
                    val armColor = if (isDark) ChartArmGreenDark else ChartArmGreenLight
                    val waistColor = if (isDark) ChartWaistAmberDark else ChartWaistAmberLight
                    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    val pointInnerColor = MaterialTheme.colorScheme.surface

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(chronologicalRecords) {
                                detectTapGestures { offset ->
                                    val leftP = 42.dp.toPx()
                                    val rightP = 16.dp.toPx()
                                    val chartW = (size.width - leftP - rightP).coerceAtLeast(1f)
                                    if (count > 1) {
                                        val step = chartW / (count - 1)
                                        val relX = (offset.x - leftP).coerceIn(0f, chartW)
                                        val nearestIdx = ((relX / step) + 0.5f).toInt().coerceIn(0, count - 1)
                                        selectedPointIndex = if (selectedPointIndex == nearestIdx) null else nearestIdx
                                    }
                                }
                            }
                            .pointerInput(chronologicalRecords) {
                                detectDragGestures { change, _ ->
                                    val leftP = 42.dp.toPx()
                                    val rightP = 16.dp.toPx()
                                    val chartW = (size.width - leftP - rightP).coerceAtLeast(1f)
                                    if (count > 1) {
                                        val step = chartW / (count - 1)
                                        val relX = (change.position.x - leftP).coerceIn(0f, chartW)
                                        val nearestIdx = ((relX / step) + 0.5f).toInt().coerceIn(0, count - 1)
                                        selectedPointIndex = nearestIdx
                                    }
                                }
                            }
                    ) {
                        val leftPad = 42.dp.toPx()
                        val rightPad = 16.dp.toPx()
                        val topPad = 14.dp.toPx()
                        val bottomPad = 26.dp.toPx()

                        val chartW = size.width - leftPad - rightPad
                        val chartH = size.height - topPad - bottomPad

                        if (chartW <= 0 || chartH <= 0 || count < 2) return@Canvas

                        val stepX = chartW / (count - 1)
                        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                        // 1. Draw Cartesian Grid Lines (4 horizontal rows)
                        val gridLines = 4
                        for (i in 0..gridLines) {
                            val ratio = i / gridLines.toFloat()
                            val y = topPad + chartH * (1f - ratio)
                            drawLine(
                                color = gridLineColor,
                                start = Offset(leftPad, y),
                                end = Offset(size.width - rightPad, y),
                                strokeWidth = 1f,
                                pathEffect = dashedEffect
                            )
                        }

                        // Helper function to map data points to canvas coordinates
                        fun mapSeries(values: List<Float>): List<Offset> {
                            return values.mapIndexed { idx, v ->
                                val x = leftPad + idx * stepX
                                val yRatio = ((v - chartMin) / valueRange).coerceIn(0f, 1f)
                                val y = topPad + chartH * (1f - yRatio)
                                Offset(x, y)
                            }
                        }

                        // Helper function to draw a series with Recharts smooth Bézier curve & gradient fill
                        fun renderCurve(values: List<Float>, color: Color, drawArea: Boolean) {
                            val pts = mapSeries(values)
                            if (pts.size < 2) return

                            // Gradient Area
                            if (drawArea) {
                                val areaPath = buildSmoothAreaPath(pts, topPad + chartH)
                                drawPath(
                                    path = areaPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.0f)),
                                        startY = topPad,
                                        endY = topPad + chartH
                                    )
                                )
                            }

                            // Smooth Line
                            val linePath = buildSmoothPath(pts)
                            drawPath(
                                path = linePath,
                                color = color,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Point Dots
                            pts.forEachIndexed { idx, pt ->
                                val isHovered = selectedPointIndex == idx
                                drawCircle(
                                    color = color,
                                    radius = if (isHovered) 6.dp.toPx() else 3.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = pointInnerColor,
                                    radius = if (isHovered) 3.dp.toPx() else 1.8.dp.toPx(),
                                    center = pt
                                )
                            }
                        }

                        // 2. Render active series
                        when (selectedMetric) {
                            ProgressChartMetric.WEIGHT -> {
                                val vals = chronologicalRecords.map { it.getDisplayWeight(weightUnit) }
                                renderCurve(vals, BoldRed, drawArea = true)
                            }
                            ProgressChartMetric.CHEST -> {
                                val vals = chronologicalRecords.map { it.getDisplayChest(measurementUnit) }
                                renderCurve(vals, chestColor, drawArea = true)
                            }
                            ProgressChartMetric.ARM -> {
                                val vals = chronologicalRecords.map { it.getDisplayArm(measurementUnit) }
                                renderCurve(vals, armColor, drawArea = true)
                            }
                            ProgressChartMetric.WAIST -> {
                                val vals = chronologicalRecords.map { it.getDisplayWaist(measurementUnit) }
                                renderCurve(vals, waistColor, drawArea = true)
                            }
                            ProgressChartMetric.ALL_MEASUREMENTS -> {
                                val chestVals = chronologicalRecords.map { it.getDisplayChest(measurementUnit) }
                                val armVals = chronologicalRecords.map { it.getDisplayArm(measurementUnit) }
                                val waistVals = chronologicalRecords.map { it.getDisplayWaist(measurementUnit) }

                                renderCurve(chestVals, chestColor, drawArea = false)
                                renderCurve(armVals, armColor, drawArea = false)
                                renderCurve(waistVals, waistColor, drawArea = false)
                            }
                        }

                        // 3. Render Tooltip Crosshair Guide
                        selectedPointIndex?.let { idx ->
                            if (idx in 0 until count) {
                                val crosshairX = leftPad + idx * stepX
                                drawLine(
                                    color = crosshairColor,
                                    start = Offset(crosshairX, topPad),
                                    end = Offset(crosshairX, topPad + chartH),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                                )
                            }
                        }
                    }

                    // Y-Axis Labels overlay
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = 10.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val activeUnit = when (selectedMetric) {
                            ProgressChartMetric.WEIGHT -> weightUnit
                            ProgressChartMetric.ALL_MEASUREMENTS -> ""
                            else -> measurementUnit
                        }
                        Text(
                            text = "${chartMax.toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.textTertiary
                        )
                        Text(
                            text = "${((chartMax + chartMin) / 2f).toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.textTertiary
                        )
                        Text(
                            text = "${chartMin.toInt()}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.textTertiary
                        )
                    }

                    // X-Axis Date Ticks overlay
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 42.dp, end = 16.dp)
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val firstRecord = chronologicalRecords.firstOrNull()
                        val lastRecord = chronologicalRecords.lastOrNull()
                        if (firstRecord != null && lastRecord != null) {
                            Text(
                                text = "Wk ${firstRecord.weekNumber}",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.textTertiary,
                                fontWeight = FontWeight.Bold
                            )
                            if (count > 2) {
                                val midRecord = chronologicalRecords[count / 2]
                                Text(
                                    text = "Wk ${midRecord.weekNumber}",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.textTertiary
                                )
                            }
                            Text(
                                text = "Wk ${lastRecord.weekNumber}",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.textTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap or drag across the chart curve to inspect exact weekly measurements.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Builds a smooth monotone cubic Bézier curve connecting points.
 */
private fun buildSmoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }
    return path
}

/**
 * Builds a closed area path with smooth monotone cubic Bézier curve for gradient filling.
 */
private fun buildSmoothAreaPath(points: List<Offset>, bottomY: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, bottomY)
    path.lineTo(points[0].x, points[0].y)

    if (points.size == 1) {
        path.lineTo(points[0].x + 20f, points[0].y)
        path.lineTo(points[0].x + 20f, bottomY)
        path.close()
        return path
    }

    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        path.lineTo(points[1].x, bottomY)
        path.close()
        return path
    }

    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }

        val cp1x = p1.x + (p2.x - p0.x) / 6f
        val cp1y = p1.y + (p2.y - p0.y) / 6f
        val cp2x = p2.x - (p3.x - p1.x) / 6f
        val cp2y = p2.y - (p3.y - p1.y) / 6f

        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
    }

    path.lineTo(points.last().x, bottomY)
    path.close()
    return path
}
