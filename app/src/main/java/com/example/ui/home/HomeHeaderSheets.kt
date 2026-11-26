package com.example.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gamification.GamificationRules
import com.example.data.gamification.LevelProgressInfo
import com.example.data.gamification.StreakDayStatus
import com.example.data.local.entity.BadgeEntity
import com.example.ui.theme.BoldRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.GoldYellow
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.badgeTextRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreakDetailsBottomSheet(
    currentStreak: Int,
    longestStreak: Int,
    last30Days: List<StreakDayStatus>,
    onDismiss: () -> Unit
) {
    val resolvedDays = if (last30Days.isNotEmpty()) {
        last30Days
    } else {
        GamificationRules.computeStreakSummary(emptyList()).last30Days
    }
    val workedOutToday = resolvedDays.lastOrNull()?.hasWorkout == true
    val activeDaysCount = resolvedDays.count { it.hasWorkout }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("streak_details_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Streak flame",
                            tint = MaterialTheme.colorScheme.badgeTextRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Workout Streak",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = when {
                                workedOutToday -> "You trained today — streak is locked in!"
                                currentStreak > 0 -> "Complete a workout today to extend your streak!"
                                else -> "Finish a workout today to start a new streak."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Current & Longest Streak Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StreakStatCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("streak_current_value"),
                    icon = Icons.Default.LocalFireDepartment,
                    tint = MaterialTheme.colorScheme.badgeTextRed,
                    value = "$currentStreak DAY${if (currentStreak != 1) "S" else ""}",
                    label = "Current Streak"
                )
                StreakStatCard(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("streak_longest_value"),
                    icon = Icons.Default.EmojiEvents,
                    tint = GoldYellow,
                    value = "$longestStreak DAY${if (longestStreak != 1) "S" else ""}",
                    label = "Longest Streak"
                )
            }

            // Last 30 Days Calendar-style grid (6 columns x 5 rows, oldest -> today)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("streak_30_day_grid")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Last 30 Days",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$activeDaysCount active day${if (activeDaysCount != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val rows = resolvedDays.chunked(6)
                    rows.forEachIndexed { rowIdx, weekRow ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            weekRow.forEachIndexed { colIdx, day ->
                                val dayIndex = rowIdx * 6 + colIdx
                                StreakCalendarCell(
                                    day = day,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("streak_day_cell_$dayIndex")
                                )
                            }
                            repeat((6 - weekRow.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendChip(
                            color = FlameOrange,
                            label = "Workout day"
                        )
                        LegendChip(
                            color = MaterialTheme.colorScheme.surface,
                            label = "Rest day",
                            hasBorder = true
                        )
                        LegendChip(
                            color = BoldRed,
                            label = "Today",
                            outlinedOnly = true
                        )
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("streak_sheet_close_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlameOrange)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StreakStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StreakCalendarCell(
    day: StreakDayStatus,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        day.hasWorkout -> FlameOrange
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        day.hasWorkout -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = when {
        day.isToday && day.hasWorkout -> GoldYellow
        day.isToday -> BoldRed
        day.hasWorkout -> FlameOrange
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }
    val borderWidth = if (day.isToday) 2.dp else 1.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = day.weekdayShort,
            fontSize = 9.sp,
            color = textColor.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Text(
            text = day.dayNumber.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
        if (day.hasWorkout) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Workout completed",
                tint = textColor,
                modifier = Modifier.size(11.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(11.dp))
        }
    }
}

@Composable
private fun LegendChip(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    hasBorder: Boolean = false,
    outlinedOnly: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (outlinedOnly) androidx.compose.ui.graphics.Color.Transparent else color)
                .then(
                    if (hasBorder || outlinedOnly) {
                        Modifier.border(
                            width = if (outlinedOnly) 2.dp else 1.dp,
                            color = if (outlinedOnly) color else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                    } else Modifier
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelAndBadgesBottomSheet(
    levelInfo: LevelProgressInfo,
    badges: List<BadgeEntity>,
    onViewProgress: () -> Unit,
    onDismiss: () -> Unit
) {
    val earnedBadges = badges.filter { it.unlocked }
    val lockedBadges = badges.filter { !it.unlocked }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("level_badges_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Level & XP Progress Header
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(FlameOrange, CircleShape)
                            ) {
                                Text(
                                    text = "L${levelInfo.level}",
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 18.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = levelInfo.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "${levelInfo.currentXp} Total XP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FlameOrange
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { levelInfo.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = FlameOrange,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${levelInfo.currentLevelMinXp} XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (levelInfo.isMaxLevel || levelInfo.nextLevelXp == null) {
                                    "Max Level Reached!"
                                } else {
                                    "${levelInfo.xpNeededForNextLevel} XP to Level ${levelInfo.level + 1} (${levelInfo.nextLevelXp} XP)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // How XP Is Earned
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("how_xp_earned_section")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = FlameOrange,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "How XP Is Earned",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        GamificationRules.XP_RULES_DESCRIPTIONS.forEach { rule ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(14.dp)
                                )
                                Text(
                                    text = rule,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(GamificationRules.LEVELS, key = { it.level }) { spec ->
                                val isCurrent = spec.level == levelInfo.level
                                val isReached = levelInfo.level >= spec.level
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isCurrent) FlameOrange else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isReached) FlameOrange else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "L${spec.level}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${spec.minXp} XP",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Badges Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Badges & Achievements",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${earnedBadges.size} / ${badges.size} Unlocked",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = FlameOrange
                    )
                }
            }

            if (badges.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Badges are loading or will appear once initialized.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                if (earnedBadges.isNotEmpty()) {
                    item {
                        Text(
                            text = "Earned",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                    items(earnedBadges, key = { "earned_${it.badgeId}" }) { badge ->
                        SheetBadgeRow(badge = badge)
                    }
                }

                if (lockedBadges.isNotEmpty()) {
                    item {
                        Text(
                            text = "Locked",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(lockedBadges, key = { "locked_${it.badgeId}" }) { badge ->
                        SheetBadgeRow(badge = badge)
                    }
                }
            }

            // Action Buttons
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onViewProgress()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("level_sheet_view_progress_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Full Progress & Analytics", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("level_sheet_close_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetBadgeRow(badge: BadgeEntity) {
    val containerColor = if (badge.unlocked) {
        GoldYellow.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val iconTint = if (badge.unlocked) GoldYellow else MaterialTheme.colorScheme.onSurfaceVariant
    val icon = badgeIconForName(badge.iconName, badge.unlocked)
    val progressRatio = if (badge.progressTarget > 0) {
        (badge.progressCurrent.toFloat() / badge.progressTarget.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sheet_badge_${badge.badgeId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (badge.unlocked) GoldYellow.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (badge.unlocked) "Unlocked badge" else "Locked badge",
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badge.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (badge.unlocked) "Earned" else "${badge.progressCurrent}/${badge.progressTarget}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (badge.unlocked) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!badge.unlocked) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = FlameOrange,
                        trackColor = MaterialTheme.colorScheme.surface
                    )
                }
            }
        }
    }
}

private fun badgeIconForName(iconName: String, unlocked: Boolean): ImageVector {
    if (!unlocked) return Icons.Default.Lock
    return when (iconName) {
        "flag" -> Icons.Default.Flag
        "local_fire_department" -> Icons.Default.LocalFireDepartment
        "military_tech" -> Icons.Default.MilitaryTech
        "fitness_center" -> Icons.Default.FitnessCenter
        "emoji_events" -> Icons.Default.EmojiEvents
        "edit_note" -> Icons.Default.EditNote
        "whatshot" -> Icons.Default.Whatshot
        "shield" -> Icons.Default.Shield
        else -> Icons.Default.EmojiEvents
    }
}
