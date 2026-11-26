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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ShowChart
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
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import java.util.*
import kotlin.math.abs

enum class LiftingChartMetric {
    LIFTING_VOLUME, // Total estimated weight volume (kg)
    BODY_WEIGHT,    // Bodyweight progress (kg)
    TOTAL_REPS      // Total completed reps
}

data class ChartPointData(
    val timestamp: Long,
    val value: Float,
    val label: String,
    val detail: String
)

/**
 * A Recharts-compatible data visualization component for Android Jetpack Compose.
 * Displays weight lifting progress and volume over the last 30 days based on data stored in Room.
 * Implements Recharts features:
 * - Cartesian grid lines
 * - Smooth monotone cubic Bézier curve
 * - Gradient area fill beneath the curve
 * - Interactive touch/drag Tooltip popover with active vertical crosshair
 * - X-Axis date ticks and Y-Axis scale ticks
 * - 30-Day window filtering and summary metrics
 */
@Composable
fun WeightLiftingProgressChart(
    sessions: List<WorkoutSessionEntity>,
    measurements: List<BodyMeasurementEntity>,
    useMetric: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(LiftingChartMetric.LIFTING_VOLUME) }
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    // 30-Day cutoff timestamp
    val now = remember { System.currentTimeMillis() }
    val thirtyDaysAgo = remember(now) { now - (30L * 24 * 60 * 60 * 1000L) }

    // Filter sessions & measurements in the last 30 days and sort chronologically (oldest to newest)
    val last30DaysSessions = remember(sessions, thirtyDaysAgo) {
        sessions.filter { it.isFinished && it.completedAt >= thirtyDaysAgo }
            .sortedBy { it.completedAt }
    }

    val last30DaysMeasurements = remember(measurements, thirtyDaysAgo) {
        measurements.filter { it.timestamp >= thirtyDaysAgo }
            .sortedBy { it.timestamp }
    }

    // Prepare chart points based on selected metric
    val weightUnitLabel = if (useMetric) "kg" else "lb"
    val pointsData: List<ChartPointData> = remember(selectedMetric, last30DaysSessions, last30DaysMeasurements, useMetric) {
        when (selectedMetric) {
            LiftingChartMetric.LIFTING_VOLUME -> {
                last30DaysSessions.map { session ->
                    // Lifting volume: exercisesCompleted * 12 reps * estimated 20 kg resistance
                    val estimatedVolumeKg = session.exercisesCompleted * 12 * 20f
                    val displayVolume = if (useMetric) estimatedVolumeKg else com.example.utils.UnitConverter.kgToLb(estimatedVolumeKg)
                    ChartPointData(
                        timestamp = session.completedAt,
                        value = displayVolume,
                        label = session.planName,
                        detail = "${session.exercisesCompleted} exercises • ${displayVolume.toInt()} $weightUnitLabel volume"
                    )
                }
            }
            LiftingChartMetric.BODY_WEIGHT -> {
                last30DaysMeasurements.map { m ->
                    val displayWeight = if (useMetric) m.weightKg else com.example.utils.UnitConverter.kgToLb(m.weightKg)
                    ChartPointData(
                        timestamp = m.timestamp,
                        value = displayWeight,
                        label = "Body Weight",
                        detail = "BMI ${String.format(Locale.US, "%.1f", m.bmi)} (${m.bmiCategory})"
                    )
                }
            }
            LiftingChartMetric.TOTAL_REPS -> {
                last30DaysSessions.map { session ->
                    val totalReps = session.exercisesCompleted * 12
                    ChartPointData(
                        timestamp = session.completedAt,
                        value = totalReps.toFloat(),
                        label = session.planName,
                        detail = "$totalReps reps completed • ${session.durationSeconds / 60}m"
                    )
                }
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("weight_lifting_progress_chart"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with 30D Badge & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "30-Day Weight Lifting Progress",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Data visualization synced from Room database",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = BoldRed.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BoldRed.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "LAST 30 DAYS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BoldRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recharts-Style Metric Toggle Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.controlBorder, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Metric 1: Lifting Volume
                val isVolSelected = selectedMetric == LiftingChartMetric.LIFTING_VOLUME
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedMetric = LiftingChartMetric.LIFTING_VOLUME
                            selectedPointIndex = null
                        }
                        .background(if (isVolSelected) BoldRed else Color.Transparent, RoundedCornerShape(9.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = if (isVolSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Volume",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVolSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Metric 2: Body Weight
                val isWeightSelected = selectedMetric == LiftingChartMetric.BODY_WEIGHT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedMetric = LiftingChartMetric.BODY_WEIGHT
                            selectedPointIndex = null
                        }
                        .background(if (isWeightSelected) BoldRed else Color.Transparent, RoundedCornerShape(9.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MonitorWeight,
                            contentDescription = null,
                            tint = if (isWeightSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Weight",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWeightSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Metric 3: Total Reps
                val isRepsSelected = selectedMetric == LiftingChartMetric.TOTAL_REPS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedMetric = LiftingChartMetric.TOTAL_REPS
                            selectedPointIndex = null
                        }
                        .background(if (isRepsSelected) BoldRed else Color.Transparent, RoundedCornerShape(9.dp))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (isRepsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reps",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRepsSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 30-Day Aggregates Summary Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${last30DaysSessions.size}",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = FlameOrange
                        )
                        Text("30D Workouts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    VerticalDivider(modifier = Modifier.height(26.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val totalVolumeKg = last30DaysSessions.sumOf { (it.exercisesCompleted * 12 * 20).toLong() }
                        val totalVolumeDisplay = if (useMetric) {
                            totalVolumeKg
                        } else {
                            com.example.utils.UnitConverter.kgToLb(totalVolumeKg.toFloat()).toLong()
                        }
                        Text(
                            text = if (totalVolumeDisplay >= 1000) "${totalVolumeDisplay / 1000}k $weightUnitLabel" else "$totalVolumeDisplay $weightUnitLabel",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Total Volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    VerticalDivider(modifier = Modifier.height(26.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val totalCal30D = last30DaysSessions.sumOf { it.caloriesBurned }
                        Text(
                            text = "$totalCal30D",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                        Text("Burned Kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart Viewport or Empty State
            if (pointsData.isEmpty()) {
                // Empty State when no data in the last 30 days
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
                            text = "No lifting data in the last 30 days",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (selectedMetric) {
                                LiftingChartMetric.BODY_WEIGHT -> "Log your weight to see your 30-day bodyweight curve"
                                else -> "Complete strength routines to start tracking your 30-day lifting curve"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textTertiary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Interactive Recharts Area & Line Chart
                val minVal = (pointsData.minOf { it.value } * 0.90f).coerceAtLeast(0f)
                val maxVal = (pointsData.maxOf { it.value } * 1.10f).coerceAtLeast(minVal + 1f)
                val range = (maxVal - minVal).coerceAtLeast(1f)

                // Interactive Recharts Tooltip Overlay
                val activePoint = selectedPointIndex?.let { idx -> pointsData.getOrNull(idx) }
                AnimatedVisibility(
                    visible = activePoint != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    activePoint?.let { pt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BoldRed)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = pt.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = ManilaDateTime.formatDate(pt.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    val formattedVal = when (selectedMetric) {
                                        LiftingChartMetric.BODY_WEIGHT -> "${String.format(Locale.US, "%.1f", pt.value)} $weightUnitLabel"
                                        LiftingChartMetric.LIFTING_VOLUME -> "${pt.value.toInt()} $weightUnitLabel volume"
                                        LiftingChartMetric.TOTAL_REPS -> "${pt.value.toInt()} reps"
                                    }
                                    Text(
                                        text = formattedVal,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black,
                                        color = FlameOrange
                                    )
                                    Text(
                                        text = pt.detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }

                val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                val pointInnerColor = MaterialTheme.colorScheme.surface

                // Canvas Chart Rendering with Recharts Grid & Monotone Curves
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(pointsData) {
                                detectTapGestures { offset ->
                                    val canvasW = size.width
                                    val stepX = if (pointsData.size > 1) canvasW / (pointsData.size - 1) else canvasW / 2
                                    val clickedIdx = (offset.x / stepX).toInt().coerceIn(0, pointsData.size - 1)
                                    selectedPointIndex = if (selectedPointIndex == clickedIdx) null else clickedIdx
                                }
                            }
                            .pointerInput(pointsData) {
                                detectDragGestures { change, _ ->
                                    val canvasW = size.width
                                    val stepX = if (pointsData.size > 1) canvasW / (pointsData.size - 1) else canvasW / 2
                                    val draggedIdx = (change.position.x / stepX).toInt().coerceIn(0, pointsData.size - 1)
                                    selectedPointIndex = draggedIdx
                                }
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val paddingTop = 12f
                        val paddingBottom = 22f
                        val usableH = canvasH - paddingTop - paddingBottom

                        // 1. Recharts Cartesian Grid: Horizontal Dashed Reference Lines
                        val gridCount = 3
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        for (i in 0..gridCount) {
                            val gy = paddingTop + (usableH / gridCount) * i
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, gy),
                                end = Offset(canvasW, gy),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )
                        }

                        val stepX = if (pointsData.size > 1) canvasW / (pointsData.size - 1) else canvasW / 2

                        // Map data to canvas coordinates
                        val offsets = pointsData.mapIndexed { idx, pt ->
                            val normY = ((pt.value - minVal) / range).coerceIn(0f, 1f)
                            val x = if (pointsData.size > 1) idx * stepX else canvasW / 2
                            val y = canvasH - paddingBottom - (normY * usableH)
                            Offset(x, y)
                        }

                        // 2. Area Gradient Path (Recharts <Area fill="url(...)">)
                        if (offsets.size > 1) {
                            val areaPath = Path().apply {
                                moveTo(offsets.first().x, canvasH - paddingBottom)
                                offsets.forEach { lineTo(it.x, it.y) }
                                lineTo(offsets.last().x, canvasH - paddingBottom)
                                close()
                            }

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        BoldRed.copy(alpha = 0.40f),
                                        FlameOrange.copy(alpha = 0.15f),
                                        Color.Transparent
                                    ),
                                    startY = paddingTop,
                                    endY = canvasH - paddingBottom
                                )
                            )

                            // 3. Smooth Monotone Cubic Bézier Curve (<Line type="monotone">)
                            val linePath = Path().apply {
                                moveTo(offsets.first().x, offsets.first().y)
                                for (i in 1 until offsets.size) {
                                    val prev = offsets[i - 1]
                                    val curr = offsets[i]
                                    val midX = (prev.x + curr.x) / 2
                                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                                }
                            }

                            drawPath(
                                path = linePath,
                                color = BoldRed,
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        // 4. Data Point Dots (Recharts <Dot />)
                        offsets.forEachIndexed { idx, pt ->
                            val isSelected = selectedPointIndex == idx
                            val radius = if (isSelected) 7.dp.toPx() else 4.5.dp.toPx()

                            if (isSelected) {
                                // Draw active crosshair vertical line
                                drawLine(
                                    color = FlameOrange.copy(alpha = 0.8f),
                                    start = Offset(pt.x, paddingTop),
                                    end = Offset(pt.x, canvasH - paddingBottom),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                // Active glow circle
                                drawCircle(
                                    color = FlameOrange.copy(alpha = 0.35f),
                                    radius = 12.dp.toPx(),
                                    center = pt
                                )
                            }

                            drawCircle(
                                color = if (isSelected) FlameOrange else BoldRed,
                                radius = radius,
                                center = pt
                            )
                            drawCircle(
                                color = pointInnerColor,
                                radius = radius * 0.45f,
                                center = pt
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // X-Axis Date Tick Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val firstDate = ManilaDateTime.formatDate(pointsData.first().timestamp)
                    val lastDate = ManilaDateTime.formatDate(pointsData.last().timestamp)

                    Text(firstDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pointsData.size > 2) {
                        val midDate = ManilaDateTime.formatDate(pointsData[pointsData.size / 2].timestamp)
                        Text(midDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(lastDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap or drag across data points to inspect individual session metrics.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
