package com.example.ui.trainer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.coaching.*
import com.example.ui.theme.DangerRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.ManilaDateTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TrainerProgressScreen(
    links: List<TrainerMemberLink>,
    selectedLink: TrainerMemberLink?,
    workoutsSummaryState: CoachingUiState<SharedWorkoutsSummary?>,
    workoutsState: CoachingUiState<List<SharedWorkoutItem>>,
    streakXpState: CoachingUiState<SharedStreakXp?>,
    bodyStatsState: CoachingUiState<SharedBodyStatsSummary?>,
    measurementsState: CoachingUiState<List<SharedMeasurementItem>>,
    assignedPlansState: CoachingUiState<List<AssignedPlanDoc>>,
    onSelectMember: (String) -> Unit
) {
    val activeLinks = remember(links) { links.filter { it.status == LinkStatus.ACTIVE } }
    val member = selectedLink ?: activeLinks.firstOrNull()
    var searchQuery by remember { mutableStateOf("") }
    val filteredLinks = remember(activeLinks, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) activeLinks else activeLinks.filter { it.memberName.contains(q, ignoreCase = true) }
    }
    var selectedTrendDotIndex by remember(member?.memberId) { mutableStateOf<Int?>(null) }
    BackHandler(enabled = selectedTrendDotIndex != null) {
        selectedTrendDotIndex = null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Member Progress Monitoring",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        if (activeLinks.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No Active Member Links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Once a member links with your invite code and accepts sharing, their workouts, streak, XP, and body stats appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Filter search + Member Selector Chips
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter linked members...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("trainer_progress_search")
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredLinks, key = { it.memberId }) { itemLink ->
                    FilterChip(
                        selected = itemLink.memberId == member?.memberId,
                        onClick = { onSelectMember(itemLink.memberId) },
                        label = { Text(itemLink.memberName) },
                        modifier = Modifier.testTag("progress_member_chip_${itemLink.memberId}")
                    )
                }
            }
        }

        if (member == null) return@LazyColumn

        if (member.status == LinkStatus.PENDING_CONFIRMATION) {
            item {
                PrivacyNoticeCard(
                    title = "Awaiting Member Consent",
                    message = "${member.memberName} has not accepted the coaching link yet."
                )
            }
            return@LazyColumn
        }

        // 1. Streak, Level & XP
        item {
            when (streakXpState) {
                is CoachingUiState.Loading -> LoadingCard("Streak, Level & XP")
                is CoachingUiState.Error -> PrivacyNoticeCard(
                    title = "Streak, Level & XP",
                    message = streakXpState.message
                )
                is CoachingUiState.Success -> {
                    val data = streakXpState.data
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Streak, Level & XP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (data == null) {
                                Text(
                                    "No streak or XP data synced yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricTile("Streak", "${data.streakDays} days")
                                    MetricTile("Level", "Level ${data.level}")
                                    MetricTile("XP Points", "${data.xpPoints} XP")
                                }
                                if (!data.fitnessGoal.isNullOrBlank()) {
                                    Text(
                                        text = "Goal: ${data.fitnessGoal} (${data.fitnessLevel ?: "Active"})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. BMI & Body Measurements
        item {
            when (bodyStatsState) {
                is CoachingUiState.Loading -> LoadingCard("BMI & Body Measurements")
                is CoachingUiState.Error -> PrivacyNoticeCard(
                    title = "BMI & Body Measurements",
                    message = bodyStatsState.message
                )
                is CoachingUiState.Success -> {
                    val stats = bodyStatsState.data
                    val measList = (measurementsState as? CoachingUiState.Success)?.data.orEmpty()
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(selectedTrendDotIndex) {
                                if (selectedTrendDotIndex != null) {
                                    detectTapGestures {
                                        selectedTrendDotIndex = null
                                    }
                                }
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("BMI & Body Measurements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (stats == null) {
                                Text(
                                    "No body stats synced yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricTile("Weight", "${String.format(Locale.US, "%.1f", stats.weightKg)} kg")
                                    MetricTile("Height", "${stats.heightCm.toInt()} cm")
                                    MetricTile("BMI", "${String.format(Locale.US, "%.1f", stats.bmi)} (${stats.bmiCategory})")
                                }
                                if (stats.chestCm != null || stats.armCm != null || stats.waistCm != null) {
                                    Text(
                                        text = listOfNotNull(
                                            stats.chestCm?.let { "Chest ${String.format(Locale.US, "%.1f", it)} cm" },
                                            stats.armCm?.let { "Arm ${String.format(Locale.US, "%.1f", it)} cm" },
                                            stats.waistCm?.let { "Waist ${String.format(Locale.US, "%.1f", it)} cm" }
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val sortedMeas = remember(measList) { measList.sortedBy { it.timestampMs } }
                                if (sortedMeas.size >= 2) {
                                    Text("Weight Trend (kg)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    SimpleLineTrendChart(
                                        measurements = sortedMeas,
                                        lineColor = FlameOrange,
                                        selectedDotIndex = selectedTrendDotIndex,
                                        onSelectDot = { selectedTrendDotIndex = it },
                                        modifier = Modifier.fillMaxWidth().height(90.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Workouts Summary & Sessions
        item {
            when (workoutsSummaryState) {
                is CoachingUiState.Loading -> LoadingCard("Workout Sessions")
                is CoachingUiState.Error -> PrivacyNoticeCard(
                    title = "Workout Sessions",
                    message = workoutsSummaryState.message
                )
                is CoachingUiState.Success -> {
                    val summary = workoutsSummaryState.data
                    val sessions = (workoutsState as? CoachingUiState.Success)?.data.orEmpty()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Workout Sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (summary != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    MetricTile("Total Workouts", "${summary.totalWorkouts}")
                                    MetricTile("This Week", "${summary.completedThisWeek}")
                                    MetricTile("Calories", "${summary.totalCaloriesBurned} kcal")
                                }
                            }
                            if (sessions.isEmpty()) {
                                Text(
                                    text = "No completed workout sessions shared yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                sessions.take(10).forEachIndexed { idx, w ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(w.planName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            val dateStr = if (w.completedAtMs > 0L) ManilaDateTime.formatDate(w.completedAtMs, fallback = "Recent") else "Recent"
                                            Text(
                                                text = "$dateStr • ${w.durationMinutes} min • ${w.caloriesBurned} kcal",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(
                                            imageVector = if (w.completed) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                            contentDescription = if (w.completed) "Completed" else "Incomplete",
                                            tint = if (w.completed) SuccessGreen else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    if (idx < sessions.take(10).lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Assigned Plans Completion
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Assigned Plans Completion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    when (assignedPlansState) {
                        is CoachingUiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FlameOrange)
                        is CoachingUiState.Error -> Text(assignedPlansState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is CoachingUiState.Success -> {
                            if (assignedPlansState.data.isEmpty()) {
                                Text(
                                    text = "No coach-assigned plans for ${member.memberName}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                assignedPlansState.data.forEachIndexed { idx, p ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(p.planTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                            val dateStr = if (p.startDateEpochMs > 0L) ManilaDateTime.formatDate(p.startDateEpochMs, fallback = "Anytime") else "Anytime"
                                            Text(
                                                text = "Scheduled: $dateStr",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (p.isCancelled && !p.cancelledReason.isNullOrBlank()) {
                                                Text(
                                                    text = "Cancelled: ${p.cancelledReason}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = DangerRed
                                                )
                                            }
                                        }
                                        val badgeColor = when {
                                            p.isCancelled -> DangerRed
                                            p.isEffectivelyCompleted -> SuccessGreen
                                            else -> WarningAmber
                                        }
                                        val badgeLabel = when {
                                            p.isCancelled -> "Cancelled"
                                            p.isEffectivelyCompleted -> "Completed"
                                            else -> "Pending"
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = badgeColor.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = badgeLabel,
                                                color = badgeColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    if (idx < assignedPlansState.data.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacyNoticeCard(title: String, message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoadingCard(title: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = FlameOrange, strokeWidth = 2.dp)
            Text("Loading $title...", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SimpleLineTrendChart(
    measurements: List<SharedMeasurementItem>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    selectedDotIndex: Int? = null,
    onSelectDot: (Int?) -> Unit = {}
) {
    if (measurements.size < 2) return
    val values = remember(measurements) { measurements.map { it.weightKg } }
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val density = LocalDensity.current
    val touchTargetSize = 48.dp
    val touchTargetSizePx = with(density) { touchTargetSize.toPx() }
    val touchRadiusPx = touchTargetSizePx / 2f

    BoxWithConstraints(
        modifier = modifier
            .pointerInput(values, selectedDotIndex) {
                detectTapGestures { tapOffset ->
                    val minVal = values.minOrNull() ?: 0f
                    val maxVal = values.maxOrNull() ?: 1f
                    val range = (maxVal - minVal).takeIf { it > 0.01f } ?: 1f
                    val pad = 12f
                    val usableW = size.width - pad * 2
                    val usableH = size.height - pad * 2

                    val points = values.mapIndexed { index, v ->
                        val x = pad + (index.toFloat() / (values.size - 1)) * usableW
                        val y = pad + (1f - (v - minVal) / range) * usableH
                        Offset(x, y)
                    }

                    val tappedIndex = points.indexOfFirst { pt ->
                        val dx = tapOffset.x - pt.x
                        val dy = tapOffset.y - pt.y
                        (dx * dx + dy * dy) <= touchRadiusPx * touchRadiusPx
                    }
                    if (tappedIndex >= 0) {
                        onSelectDot(if (selectedDotIndex == tappedIndex) null else tappedIndex)
                    } else {
                        onSelectDot(null)
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val minVal = values.minOrNull() ?: 0f
        val maxVal = values.maxOrNull() ?: 1f
        val range = (maxVal - minVal).takeIf { it > 0.01f } ?: 1f
        val pad = 12f
        val usableW = widthPx - pad * 2
        val usableH = heightPx - pad * 2

        val points = remember(values, widthPx, heightPx) {
            values.mapIndexed { index, v ->
                val x = pad + (index.toFloat() / (values.size - 1)) * usableW
                val y = pad + (1f - (v - minVal) / range) * usableH
                Offset(x, y)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawLine(
                color = gridColor,
                start = Offset(pad, size.height / 2f),
                end = Offset(size.width - pad, size.height / 2f),
                strokeWidth = 2f
            )

            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))

            points.forEachIndexed { index, pt ->
                if (index == selectedDotIndex) {
                    // Highlighted active dot while its tooltip is open
                    drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 18f, center = pt)
                    drawCircle(color = lineColor, radius = 12f, center = pt, style = Stroke(width = 3.5f))
                    drawCircle(color = Color.White, radius = 7f, center = pt)
                    drawCircle(color = lineColor, radius = 4f, center = pt)
                } else {
                    // Unselected dot: exactly original
                    drawCircle(color = lineColor, radius = 8f, center = pt)
                }
            }
        }

        // Tappable touch targets for each dot (at least 44x44 px; configured as 48dp x 48dp)
        points.forEachIndexed { index, pt ->
            val item = measurements.getOrNull(index)
            val dateStr = if (item != null && item.timestampMs > 0L) ManilaDateTime.formatDate(item.timestampMs, fallback = "") else ""
            val weightStr = "${String.format(Locale.US, "%.1f", item?.weightKg ?: 0f)} kg"
            val label = if (dateStr.isNotBlank()) "$dateStr: $weightStr" else weightStr

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (pt.x - touchTargetSizePx / 2f).roundToInt(),
                            y = (pt.y - touchTargetSizePx / 2f).roundToInt()
                        )
                    }
                    .size(touchTargetSize)
                    .testTag("trend_chart_dot_$index")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = label
                    ) {
                        onSelectDot(if (selectedDotIndex == index) null else index)
                    }
            )
        }

        // Tooltip displayed above the selected dot
        if (selectedDotIndex != null) {
            val selIdx = selectedDotIndex
            val selPoint = points.getOrNull(selIdx)
            val selItem = measurements.getOrNull(selIdx)
            if (selPoint != null && selItem != null) {
                val dateStr = if (selItem.timestampMs > 0L) ManilaDateTime.formatDate(selItem.timestampMs, fallback = "") else ""
                val weightStr = "${String.format(Locale.US, "%.1f", selItem.weightKg)} kg"

                var tooltipWidthPx by remember(selIdx) { mutableIntStateOf(0) }
                var tooltipHeightPx by remember(selIdx) { mutableIntStateOf(0) }

                val effectiveWidth = if (tooltipWidthPx > 0) tooltipWidthPx.toFloat() else with(density) { 95.dp.toPx() }
                val effectiveHeight = if (tooltipHeightPx > 0) tooltipHeightPx.toFloat() else with(density) { 42.dp.toPx() }

                // Horizontally clamp tooltip so it remains fully inside the card and screen boundaries
                val idealLeft = selPoint.x - effectiveWidth / 2f
                val minLeft = 0f
                val maxLeft = (widthPx - effectiveWidth).coerceAtLeast(0f)
                val clampedLeft = idealLeft.coerceIn(minLeft, maxLeft)

                // Tooltip sits above the dot
                val idealTop = selPoint.y - effectiveHeight - 12f
                val minTop = -with(density) { 36.dp.toPx() }
                val clampedTop = idealTop.coerceAtLeast(minTop)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = clampedLeft.roundToInt(),
                                y = clampedTop.roundToInt()
                            )
                        }
                        .onSizeChanged {
                            tooltipWidthPx = it.width
                            tooltipHeightPx = it.height
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onSelectDot(null)
                        }
                        .testTag("trend_chart_tooltip")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (dateStr.isNotBlank()) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = weightStr,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimpleLineTrendChart(
    values: List<Float>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    SimpleLineTrendChart(
        measurements = values.map { SharedMeasurementItem(weightKg = it) },
        lineColor = lineColor,
        modifier = modifier
    )
}
