package com.example.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.ui.checkin.GymCheckInHandler
import com.example.ui.checkin.QrScannerSheet
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.gamification.GamificationRules
import com.example.data.gamification.StreakDayStatus
import com.example.data.local.entity.BadgeEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userProfile: UserProfileEntity,
    streakDays: Int,
    recommendedPlan: WorkoutPlanEntity?,
    popularPlans: List<WorkoutPlanEntity>,
    challenges: List<WorkoutPlanEntity>,
    recentSessions: List<WorkoutSessionEntity>,
    badges: List<BadgeEntity>,
    totalWorkouts: Int,
    totalCalories: Int,
    onSelectPlan: (WorkoutPlanEntity) -> Unit,
    onStartRecommended: () -> Unit,
    onCreateCustomPlan: () -> Unit,
    onViewAllPlans: () -> Unit,
    onViewProgress: () -> Unit,
    onOpenTrainersDirectory: () -> Unit = {},
    onConsultTrainer: (name: String, topic: String) -> Unit = { _, _ -> },
    onOpenBodyPoseBmi: () -> Unit = {},
    activeCoachLink: com.example.data.coaching.TrainerMemberLink? = null,
    coachUnreadCount: Int = 0,
    onOpenMyCoach: () -> Unit = {},
    onOpenCoachChat: () -> Unit = {},
    longestStreakDays: Int = streakDays,
    last30DaysStreak: List<StreakDayStatus> = emptyList(),
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showScannerSheet by remember { mutableStateOf(false) }
    var showStreakSheet by remember { mutableStateOf(false) }
    var showLevelBadgesSheet by remember { mutableStateOf(false) }
    var isSavingCheckIn by remember { mutableStateOf(false) }
    var checkInResult by remember { mutableStateOf<String?>(null) }
    val levelInfo = remember(userProfile.xpPoints, userProfile.level) {
        GamificationRules.levelInfoForXp(userProfile.xpPoints, userProfile.level)
    }
    val displayName = userProfile.name.ifBlank { GamificationRules.DEFAULT_DISPLAY_NAME }
    val displayGoal = userProfile.fitnessGoal.ifBlank { GamificationRules.DEFAULT_FITNESS_GOAL }

    fun handleCheckIn(qrPayload: String) {
        checkInResult = null
        isSavingCheckIn = true
        coroutineScope.launch {
            try {
                val msg = GymCheckInHandler.processCheckIn(context, qrPayload)
                checkInResult = msg
            } catch (_: Exception) {
                checkInResult = "Something went wrong. Please try again."
            } finally {
                isSavingCheckIn = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkInResult = null
            showScannerSheet = true
        } else {
            checkInResult = "Camera permission is needed to scan the check-in code."
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isDark = LocalIsDarkTheme.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Header: User Greeting & Streak Badge
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenProfile() }
                        .defaultMinSize(minHeight = 48.dp)
                        .padding(vertical = 2.dp, horizontal = 2.dp)
                        .testTag("home_greeting_profile_button"),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hello, $displayName!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Goal: $displayGoal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Streak pill (opens Streak & 30-day calendar sheet)
                Surface(
                    onClick = { showStreakSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_streak_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocalFireDepartment, contentDescription = "Streak", tint = MaterialTheme.colorScheme.badgeTextRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$streakDays DAY${if (streakDays != 1) "S" else ""}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.badgeTextRed,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Gamification XP & Level Bar
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLevelBadgesSheet = true }
                    .testTag("gamification_level_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .background(FlameOrange, CircleShape)
                    ) {
                        Text(
                            text = "L${levelInfo.level}",
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = levelInfo.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${levelInfo.currentXp} XP", fontWeight = FontWeight.ExtraBold, color = FlameOrange, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearProgressIndicator(
                            progress = { levelInfo.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = FlameOrange,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }

        // BMI Calculator Feature Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenBodyPoseBmi() }
                    .testTag("body_pose_bmi_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(BoldRed.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BMI Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = BoldRed
                    )
                }
            }
        }

        // Gym Check-in Pass Card (Enabled in Look & Home Settings)
        if (userProfile.gymCheckinEnabled) {
            item {
                val memberName = userProfile.name

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gym_checkin_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(FlameOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = FlameOrange,
                                    modifier = Modifier.size(26.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Gym Check-in Pass",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = memberName,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Scan to check in button
                        Button(
                            onClick = {
                                checkInResult = null
                                val hasCamPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCamPerm) {
                                    showScannerSheet = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !isSavingCheckIn,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("scan_qr_button")
                        ) {
                            if (isSavingCheckIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Checking in...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan to check in", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Result Message Display
                        if (checkInResult != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val isSuccess = checkInResult == "You're checked in."
                            val isAlreadyCheckedIn = checkInResult == "You've already checked in today."
                            val bannerColor = when {
                                isSuccess -> SuccessGreen
                                isAlreadyCheckedIn -> ElectricCyan
                                else -> DangerRed
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = bannerColor.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, bannerColor.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("checkin_result_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isSuccess -> Icons.Default.CheckCircle
                                            isAlreadyCheckedIn -> Icons.Default.Info
                                            else -> Icons.Default.ErrorOutline
                                        },
                                        contentDescription = null,
                                        tint = bannerColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = checkInResult!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = bannerColor,
                                        modifier = Modifier.testTag("checkin_result_text")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily Recommendation Hero Card
        item {
            if (recommendedPlan != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlan(recommendedPlan) }
                        .testTag("recommended_workout_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(BoldRed, BoldRedDark)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = "TODAY'S RECOMMENDATION",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${recommendedPlan.estimatedDurationMinutes} mins",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = recommendedPlan.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = recommendedPlan.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Button(
                                onClick = onStartRecommended,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = FlameOrangeDark
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("quick_start_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QUICK START", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Custom Workout Plan Banner (Prompt requirement!)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCreateCustomPlan() }
                    .testTag("create_custom_plan_banner"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .background(ElectricCyanDark.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(28.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Custom Workout Builder",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FlameOrange)
                }
            }
        }

        // My Coach Link / Quick Message Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenMyCoach() }
                    .testTag("home_my_coach_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (isDark) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(FlameOrange.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sports,
                            contentDescription = null,
                            tint = FlameOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val isLinkedActive = activeCoachLink != null && activeCoachLink.status == com.example.data.coaching.LinkStatus.ACTIVE
                        val isPending = activeCoachLink != null && activeCoachLink.status == com.example.data.coaching.LinkStatus.PENDING_CONFIRMATION
                        Text(
                            text = when {
                                isLinkedActive -> "Coach ${activeCoachLink?.trainerName}"
                                isPending -> "Coach Request from ${activeCoachLink?.trainerName}"
                                else -> "My Coach & Privacy"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                isLinkedActive -> "Tap to manage privacy sharing, plans & chat"
                                isPending -> "Tap to review & confirm what to share"
                                else -> "Scan coach QR or enter invite code"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (activeCoachLink?.status == com.example.data.coaching.LinkStatus.ACTIVE) {
                        if (coachUnreadCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = BoldRed,
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "$coachUnreadCount",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onOpenCoachChat,
                            modifier = Modifier.testTag("home_coach_chat_button")
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "Message Coach", tint = FlameOrange)
                        }
                    } else {
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FlameOrange)
                    }
                }
            }
        }

        // Popular Workout Routines
        item {
            Column {
                Text("Popular Workouts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))

                val isTabletOrDesktop = screenWidthDp >= 768
                val plansToShow = popularPlans.take(4)
                if (isTabletOrDesktop) {
                    val rows = plansToShow.chunked(2)
                    rows.forEach { rowPlans ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (plan in rowPlans) {
                                Box(modifier = Modifier.weight(1f)) {
                                    PopularPlanCard(plan = plan, onSelectPlan = onSelectPlan)
                                }
                            }
                            if (rowPlans.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    plansToShow.forEach { plan ->
                        Box(modifier = Modifier.padding(bottom = 10.dp)) {
                            PopularPlanCard(plan = plan, onSelectPlan = onSelectPlan)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    if (showScannerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showScannerSheet = false }
        ) {
            QrScannerSheet(
                onCodeScanned = { scannedCode ->
                    showScannerSheet = false
                    handleCheckIn(scannedCode)
                },
                onDismiss = { showScannerSheet = false }
            )
        }
    }

    if (showStreakSheet) {
        StreakDetailsBottomSheet(
            currentStreak = streakDays,
            longestStreak = longestStreakDays,
            last30Days = last30DaysStreak,
            onDismiss = { showStreakSheet = false }
        )
    }

    if (showLevelBadgesSheet) {
        LevelAndBadgesBottomSheet(
            levelInfo = levelInfo,
            badges = badges,
            onViewProgress = onViewProgress,
            onDismiss = { showLevelBadgesSheet = false }
        )
    }
}

@Composable
private fun PopularPlanCard(
    plan: WorkoutPlanEntity,
    onSelectPlan: (WorkoutPlanEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectPlan(plan) }
            .testTag("popular_plan_${plan.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        when (plan.category) {
                            "Abs" -> ElectricCyan.copy(alpha = 0.2f)
                            "Chest" -> FlameOrange.copy(alpha = 0.2f)
                            "Leg" -> EnergyLime.copy(alpha = 0.2f)
                            else -> GoldYellow.copy(alpha = 0.2f)
                        },
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = when (plan.category) {
                        "Abs" -> ElectricCyan
                        "Chest" -> FlameOrange
                        "Leg" -> EnergyLime
                        else -> GoldYellow
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${plan.estimatedDurationMinutes} mins • ${plan.difficulty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(Icons.Default.PlayCircleFilled, contentDescription = "Start", tint = FlameOrange, modifier = Modifier.size(32.dp))
        }
    }
}
