package com.example.ui.posebmi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.BmiRecordEntity
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import com.example.utils.UnitConverter
import java.util.*

enum class ChartMetric {
    BMI, WEIGHT
}

@Composable
fun BmiTrendChartCard(
    records: List<BmiRecordEntity>,
    useMetric: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedMetric by remember { mutableStateOf(ChartMetric.BMI) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header with Metric Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedMetric == ChartMetric.BMI) "BMI Trend Analytics" else "Body Weight Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when (records.size) {
                            0 -> "No assessments yet"
                            1 -> "1 assessment recorded"
                            else -> "${records.size} assessments recorded"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Metric Toggle (BMI vs Weight)
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.controlBorder, RoundedCornerShape(12.dp))
                        .padding(3.dp)
                ) {
                    val isBmi = selectedMetric == ChartMetric.BMI
                    Box(
                        modifier = Modifier
                            .clickable { selectedMetric = ChartMetric.BMI }
                            .background(
                                if (isBmi) BoldRed else Color.Transparent,
                                RoundedCornerShape(9.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BMI",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBmi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clickable { selectedMetric = ChartMetric.WEIGHT }
                            .background(
                                if (!isBmi) BoldRed else Color.Transparent,
                                RoundedCornerShape(9.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (useMetric) "Weight (kg)" else "Weight (lb)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isBmi) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (records.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No history recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Complete a body scan or record BMI to view progress trends",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.textTertiary
                        )
                    }
                }
            } else {
                // Sort chronologically ascending for line graph
                val sorted = records.sortedBy { it.assessmentDate }
                val values = sorted.map {
                    if (selectedMetric == ChartMetric.BMI) it.bmiValue
                    else if (useMetric) it.weightKg else UnitConverter.kgToLb(it.weightKg)
                }

                val minVal = (values.minOrNull() ?: 0f) * 0.95f
                val maxVal = (values.maxOrNull() ?: 100f) * 1.05f
                val valRange = (maxVal - minVal).coerceAtLeast(1f)

                val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                val pointInnerColor = MaterialTheme.colorScheme.surface

                Column {
                    // Line Canvas
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height
                        val paddingBottom = 28f
                        val paddingTop = 16f
                        val usableH = canvasH - paddingBottom - paddingTop

                        val stepX = if (sorted.size > 1) canvasW / (sorted.size - 1) else canvasW / 2

                        val points = sorted.mapIndexed { index, record ->
                            val v = if (selectedMetric == ChartMetric.BMI) record.bmiValue
                            else if (useMetric) record.weightKg else UnitConverter.kgToLb(record.weightKg)
                            val normY = (v - minVal) / valRange
                            val x = if (sorted.size > 1) index * stepX else canvasW / 2
                            val y = canvasH - paddingBottom - (normY * usableH)
                            Offset(x, y)
                        }

                        // Horizontal Reference Grid Lines
                        val gridCount = 3
                        for (i in 0..gridCount) {
                            val gy = paddingTop + (usableH / gridCount) * i
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, gy),
                                end = Offset(canvasW, gy),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        if (points.isNotEmpty()) {
                            // Area Gradient Path
                            val areaPath = Path().apply {
                                moveTo(points.first().x, canvasH - paddingBottom)
                                points.forEach { lineTo(it.x, it.y) }
                                lineTo(points.last().x, canvasH - paddingBottom)
                                close()
                            }

                            drawPath(
                                path = areaPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        BoldRed.copy(alpha = 0.35f),
                                        BoldRed.copy(alpha = 0.0f)
                                    )
                                )
                            )

                            // Main Line Path
                            val linePath = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                for (i in 1 until points.size) {
                                    val prev = points[i - 1]
                                    val curr = points[i]
                                    // Smooth cubic curve
                                    val cx = (prev.x + curr.x) / 2
                                    cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                                }
                            }

                            drawPath(
                                path = linePath,
                                color = BoldRed,
                                style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // Point Nodes
                            points.forEach { pt ->
                                drawCircle(
                                    color = BoldRed,
                                    radius = 6.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = pointInnerColor,
                                    radius = 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date Axis Labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val firstDate = sorted.firstOrNull()?.assessmentDate?.let { ManilaDateTime.formatDate(it) } ?: ""
                        val lastDate = sorted.lastOrNull()?.assessmentDate?.let { ManilaDateTime.formatDate(it) } ?: ""

                        Text(firstDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (sorted.size > 2) {
                            val midDate = ManilaDateTime.formatDate(sorted[sorted.size / 2].assessmentDate)
                            Text(midDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(lastDate, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
