package com.example.ui.coach

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.coaching.*
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.ui.checkin.QrScannerSheet
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberCoachScreen(
    coachingViewModel: MemberCoachingViewModel,
    userProfile: UserProfileEntity,
    sessions: List<WorkoutSessionEntity>,
    measurements: List<BodyMeasurementEntity>,
    streakDays: Int,
    localAssignedPlans: List<WorkoutPlanEntity>,
    onStartAssignedPlan: (WorkoutPlanEntity) -> Unit,
    onNavigateBack: () -> Unit,
    initialOpenChat: Boolean = false
) {
    val context = LocalContext.current
    val linkState by coachingViewModel.linkState.collectAsStateWithLifecycle()
    val pendingSyncCount by coachingViewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val pendingConfirmInvite by coachingViewModel.pendingConfirmInvite.collectAsStateWithLifecycle()
    val actionMessage by coachingViewModel.actionMessage.collectAsStateWithLifecycle()
    val isBusy by coachingViewModel.isBusy.collectAsStateWithLifecycle()
    val chatThread by coachingViewModel.chatThread.collectAsStateWithLifecycle()
    val chatMessagesState by coachingViewModel.chatMessagesState.collectAsStateWithLifecycle()
    val isSendingMessage by coachingViewModel.isSendingMessage.collectAsStateWithLifecycle()
    val sendMessageError by coachingViewModel.sendMessageError.collectAsStateWithLifecycle()
    val isRefreshingChat by coachingViewModel.isRefreshingChat.collectAsStateWithLifecycle()
    val trainerAvailabilityState by coachingViewModel.trainerAvailabilityState.collectAsStateWithLifecycle()
    val scheduledSessionsState by coachingViewModel.scheduledSessionsState.collectAsStateWithLifecycle()

    var showChat by remember(initialOpenChat) { mutableStateOf(initialOpenChat) }
    var showQrScanner by remember { mutableStateOf(false) }
    var showUnlinkDialog by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }
    var scannerPermissionError by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scannerPermissionError = null
            showQrScanner = true
        } else {
            scannerPermissionError = "Camera permission is required to scan a trainer QR code."
        }
    }

    val link = (linkState as? CoachingUiState.Success)?.data

    LaunchedEffect(showChat, link?.status) {
        coachingViewModel.setChatOpen(showChat && link?.status == LinkStatus.ACTIVE)
    }

    DisposableEffect(Unit) {
        onDispose {
            coachingViewModel.setChatOpen(false)
        }
    }

    BackHandler {
        if (showChat) {
            showChat = false
        } else {
            onNavigateBack()
        }
    }

    if (showChat && link != null && link.status == LinkStatus.ACTIVE) {
        CoachChatScreen(
            peerName = link.trainerName,
            peerSubtitle = link.trainerTitle ?: "Your BodyGrind Coach",
            currentUserId = coachingViewModel.currentUid,
            messagesState = chatMessagesState,
            isSending = isSendingMessage,
            sendError = sendMessageError,
            onSendMessage = { text -> coachingViewModel.sendChatMessage(userProfile.name, text) },
            onBack = { showChat = false },
            isRefreshing = isRefreshingChat,
            onRefresh = {
                coachingViewModel.refreshChat(
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        )
        return
    }

    // Confirmation dialog before accepting a trainer invite (QR or manual code)
    pendingConfirmInvite?.let { invite ->
        LinkConsentConfirmationDialog(
            trainerName = invite.trainerName,
            trainerTitle = invite.trainerTitle,
            specialization = invite.specialization,
            confirmLabel = "Accept & Link",
            onConfirm = { consent ->
                coachingViewModel.confirmAndLinkWithInvite(invite, userProfile, consent)
            },
            onDismiss = { coachingViewModel.dismissConfirmInvite() }
        )
    }

    // Unlink confirmation dialog
    if (showUnlinkDialog && link != null) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text("Unlink from ${link.trainerName}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Unlinking immediately stops all progress sharing and closes coach access to your workouts, streak, XP, and body stats.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkDialog = false
                        coachingViewModel.unlinkCoach()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("confirm_unlink_coach_button")
                ) {
                    Text("Unlink Coach", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Coach & Privacy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("coach_screen_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            if (!actionMessage.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = FlameOrange.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = actionMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { coachingViewModel.clearActionMessage() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            when (linkState) {
                is CoachingUiState.Loading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = FlameOrange)
                        }
                    }
                }

                is CoachingUiState.Error -> {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Could not load coach connection", fontWeight = FontWeight.Bold, color = DangerRed)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text((linkState as CoachingUiState.Error).message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                is CoachingUiState.Success -> {
                    if (link == null) {
                        // CONNECT WITH A TRAINER (QR OR INVITE CODE)
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(FlameOrange.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Sports, contentDescription = null, tint = FlameOrange)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Connect with a Trainer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Scan your trainer's QR code or enter their invite code. You control what data is shared.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                scannerPermissionError = null
                                                showQrScanner = true
                                            } else {
                                                cameraLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        enabled = !isBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("scan_trainer_qr_button")
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Scan Trainer QR Code", fontWeight = FontWeight.Bold)
                                    }

                                    if (!scannerPermissionError.isNullOrBlank()) {
                                        Text(
                                            text = scannerPermissionError!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DangerRed
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                    Text(
                                        text = "Or enter invite code manually",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    OutlinedTextField(
                                        value = inviteCodeInput,
                                        onValueChange = { inviteCodeInput = it.uppercase() },
                                        label = { Text("Trainer Invite Code") },
                                        placeholder = { Text("e.g. BG-ABC123") },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("trainer_invite_code_input")
                                    )

                                    OutlinedButton(
                                        onClick = { coachingViewModel.lookupInviteCode(inviteCodeInput) },
                                        enabled = inviteCodeInput.trim().length >= 4 && !isBusy,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("lookup_trainer_code_button")
                                    ) {
                                        Text("Find Trainer", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else if (link.status == LinkStatus.PENDING_CONFIRMATION) {
                        // TRAINER-INITIATED REQUEST WAITING MEMBER CONSENT
                        item {
                            PendingCoachRequestCard(
                                link = link,
                                isBusy = isBusy,
                                onAccept = { consent -> coachingViewModel.acceptPendingRequest(userProfile, consent) },
                                onDecline = { coachingViewModel.unlinkCoach() }
                            )
                        }
                    } else {
                        // ACTIVE LINKED COACH CARD
                        item {
                            val unread = chatThread?.memberUnreadCount ?: 0
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(BoldRed.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = link.trainerName.split(" ").take(2).joinToString("") { it.take(1) }.uppercase().ifBlank { "C" },
                                                fontWeight = FontWeight.Black,
                                                color = BoldRed
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(link.trainerName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                            Text(
                                                text = link.trainerTitle ?: "Active Coach",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SuccessGreen.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "LINKED",
                                                color = SuccessGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { showChat = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("open_coach_chat_button")
                                        ) {
                                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (unread > 0) "Message ($unread)" else "Message Coach",
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        OutlinedButton(
                                            onClick = { showUnlinkDialog = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                                            border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("unlink_coach_button")
                                        ) {
                                            Icon(Icons.Default.LinkOff, contentDescription = "Unlink", modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Unlink", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // DATA SHARING & PRIVACY TOGGLES
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Progress Sharing Privacy",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (pendingSyncCount > 0) WarningAmber.copy(alpha = 0.16f) else SuccessGreen.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = if (pendingSyncCount > 0) "$pendingSyncCount queued" else "Synced",
                                                color = if (pendingSyncCount > 0) WarningAmber else SuccessGreen,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Control what ${link.trainerName} can see. Changes apply immediately and work offline.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    PrivacyToggleRow(
                                        title = "Workouts & Sessions",
                                        subtitle = "Plan name, date, duration, calories burned, and completion",
                                        checked = link.shareWorkouts,
                                        testTag = "privacy_toggle_workouts",
                                        onCheckedChange = { checked ->
                                            coachingViewModel.updatePrivacy(
                                                shareWorkouts = checked,
                                                shareStreakXp = link.shareStreakXp,
                                                shareBodyStats = link.shareBodyStats
                                            )
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                    PrivacyToggleRow(
                                        title = "Streak, Level & XP",
                                        subtitle = "Current workout streak days, level, and earned XP",
                                        checked = link.shareStreakXp,
                                        testTag = "privacy_toggle_streak_xp",
                                        onCheckedChange = { checked ->
                                            coachingViewModel.updatePrivacy(
                                                shareWorkouts = link.shareWorkouts,
                                                shareStreakXp = checked,
                                                shareBodyStats = link.shareBodyStats
                                            )
                                        }
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                    PrivacyToggleRow(
                                        title = "BMI & Body Stats",
                                        subtitle = "Weight, height, BMI, and body measurements",
                                        checked = link.shareBodyStats,
                                        testTag = "privacy_toggle_body_stats",
                                        onCheckedChange = { checked ->
                                            coachingViewModel.updatePrivacy(
                                                shareWorkouts = link.shareWorkouts,
                                                shareStreakXp = link.shareStreakXp,
                                                shareBodyStats = checked
                                            )
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(
                                        onClick = {
                                            coachingViewModel.manualFlushAndSync(
                                                profile = userProfile,
                                                sessions = sessions,
                                                measurements = measurements,
                                                streakDays = streakDays
                                            )
                                        },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .testTag("sync_progress_now_button")
                                    ) {
                                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sync Progress Now", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // COACH AVAILABILITY & SCHEDULED SESSIONS (READ-ONLY FOR MEMBERS)
                        item {
                            val availability = (trainerAvailabilityState as? CoachingUiState.Success)?.data
                            val scheduledSessions = (scheduledSessionsState as? CoachingUiState.Success)?.data.orEmpty()
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("member_coach_availability_card"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.EventAvailable, contentDescription = null, tint = FlameOrange)
                                        Column {
                                            Text(
                                                text = "${link.trainerName}'s Schedule & Availability",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "All dates & times in Asia/Manila (UTC+8)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (availability != null) {
                                        val enabledSlots = availability.weeklySlots.filter { it.enabled }.sortedBy { it.dayOfWeek }
                                        if (enabledSlots.isEmpty()) {
                                            Text(
                                                text = "Coach has not published weekly hours yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            enabledSlots.forEach { slot ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = ManilaDateTime.dayOfWeekLabel(slot.dayOfWeek),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = "${ManilaDateTime.formatSlotTime(slot.startTime)} – ${ManilaDateTime.formatSlotTime(slot.endTime)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = SuccessGreen
                                                    )
                                                }
                                            }
                                        }

                                        if (availability.dateOverrides.isNotEmpty()) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                            Text(
                                                text = "Upcoming Date Overrides",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            availability.dateOverrides.take(5).forEach { override ->
                                                val desc = if (override.isAvailable) {
                                                    "${ManilaDateTime.formatSlotTime(override.startTime)} – ${ManilaDateTime.formatSlotTime(override.endTime)}"
                                                } else {
                                                    "Unavailable"
                                                }
                                                Text(
                                                    text = "${ManilaDateTime.formatDateKey(override.dateKey)}: $desc" +
                                                        if (override.note.isNotBlank()) " (${override.note})" else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (override.isAvailable) SuccessGreen else DangerRed
                                                )
                                            }
                                        }
                                    }

                                    if (scheduledSessions.isNotEmpty()) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                        Text(
                                            text = "Scheduled Sessions (${scheduledSessions.size})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        scheduledSessions.take(8).forEach { session ->
                                            val statusColor = when {
                                                session.isCancelled -> DangerRed
                                                session.isCompleted -> SuccessGreen
                                                else -> ElectricCyan
                                            }
                                            val statusText = when {
                                                session.isCancelled -> "Cancelled"
                                                session.isCompleted -> "Completed"
                                                else -> "Scheduled"
                                            }
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                    .padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = session.title.ifBlank { "Coaching Session" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = statusColor.copy(alpha = 0.16f)
                                                    ) {
                                                        Text(
                                                            text = statusText,
                                                            color = statusColor,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${session.formattedScheduledDate()} • ${ManilaDateTime.formatSlotTime(session.startTime)} – ${ManilaDateTime.formatSlotTime(session.endTime)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (session.isCancelled) {
                                                    Text(
                                                        text = "Reason: ${session.cancelledReason.orEmpty().ifBlank { "Cancelled by staff" }} (${session.formattedCancelledAt()})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = DangerRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ASSIGNED WORKOUT PLANS FROM COACH
                        item {
                            Text(
                                text = "Assigned Workout Plans (${localAssignedPlans.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (localAssignedPlans.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Text(
                                        text = "No plans assigned by ${link.trainerName} yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(localAssignedPlans, key = { it.id }) { plan ->
                                val isCancelled = plan.status.equals("CANCELLED", ignoreCase = true)
                                val dateStr = remember(plan.startDateEpochMs) {
                                    plan.startDateEpochMs?.takeIf { it > 0L }?.let { ms ->
                                        ManilaDateTime.formatDate(ms, fallback = "Flexible start")
                                    } ?: "Flexible start"
                                }
                                val badgeColor = when {
                                    isCancelled -> DangerRed
                                    plan.isCompletedByMember -> SuccessGreen
                                    else -> ElectricCyan
                                }
                                val badgeLabel = when {
                                    isCancelled -> "Cancelled"
                                    plan.isCompletedByMember -> "Completed"
                                    else -> "Pending"
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (!isCancelled) Modifier.clickable { onStartAssignedPlan(plan) } else Modifier)
                                        .testTag("coach_assigned_plan_${plan.id}"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCancelled) DangerRed.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Assigned by ${plan.assignedByTrainerName ?: link.trainerName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = FlameOrange
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = badgeColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = badgeLabel,
                                                    color = badgeColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "Start: $dateStr • ${plan.estimatedDurationMinutes} mins • ${plan.difficulty}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isCancelled) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Cancelled: ${plan.cancelledReason.orEmpty().ifBlank { "Session cancelled by staff" }} (${ManilaDateTime.formatDateTime(plan.cancelledAtUtcMs)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = DangerRed
                                            )
                                        } else if (!plan.trainerNote.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Note: ${plan.trainerNote}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
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

    if (showQrScanner) {
        ModalBottomSheet(onDismissRequest = { showQrScanner = false }) {
            QrScannerSheet(
                title = "Scan Coach QR Code",
                subtitle = "Point camera at your trainer's invite QR code",
                helperText = "Your trainer can display their invite QR code in their BodyGrind Trainer dashboard.",
                onCodeScanned = { code ->
                    showQrScanner = false
                    coachingViewModel.lookupInviteCode(code)
                },
                onDismiss = { showQrScanner = false }
            )
        }
    }
}

@Composable
private fun LinkConsentConfirmationDialog(
    trainerName: String,
    trainerTitle: String?,
    specialization: String?,
    confirmLabel: String,
    onConfirm: (LinkConsentOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var shareWorkouts by remember { mutableStateOf(true) }
    var shareStreakXp by remember { mutableStateOf(true) }
    var shareBodyStats by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Connect with $trainerName?", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!trainerTitle.isNullOrBlank()) {
                    Text(
                        text = trainerTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FlameOrange
                    )
                }
                if (!specialization.isNullOrBlank()) {
                    Text(
                        text = specialization,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Choose what progress data $trainerName will be allowed to see. You can change these toggles anytime.",
                    style = MaterialTheme.typography.bodySmall
                )
                ConsentCheckboxRow(
                    label = "Workout sessions (plan, date, duration, calories)",
                    checked = shareWorkouts,
                    onCheckedChange = { shareWorkouts = it }
                )
                ConsentCheckboxRow(
                    label = "Streak, level & XP progress",
                    checked = shareStreakXp,
                    onCheckedChange = { shareStreakXp = it }
                )
                ConsentCheckboxRow(
                    label = "BMI & body measurements",
                    checked = shareBodyStats,
                    onCheckedChange = { shareBodyStats = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        LinkConsentOptions(
                            shareWorkouts = shareWorkouts,
                            shareStreakXp = shareStreakXp,
                            shareBodyStats = shareBodyStats
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                modifier = Modifier.testTag("confirm_link_consent_button")
            ) {
                Text(confirmLabel, color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PendingCoachRequestCard(
    link: TrainerMemberLink,
    isBusy: Boolean,
    onAccept: (LinkConsentOptions) -> Unit,
    onDecline: () -> Unit
) {
    var shareWorkouts by remember { mutableStateOf(true) }
    var shareStreakXp by remember { mutableStateOf(true) }
    var shareBodyStats by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, FlameOrange.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Coach Link Request",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = FlameOrange
            )
            Text(
                text = "${link.trainerName} wants to coach you",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select what will be shared before accepting:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ConsentCheckboxRow("Workout sessions", shareWorkouts) { shareWorkouts = it }
            ConsentCheckboxRow("Streak, level & XP", shareStreakXp) { shareStreakXp = it }
            ConsentCheckboxRow("BMI & body measurements", shareBodyStats) { shareBodyStats = it }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Decline")
                }
                Button(
                    onClick = {
                        onAccept(
                            LinkConsentOptions(
                                shareWorkouts = shareWorkouts,
                                shareStreakXp = shareStreakXp,
                                shareBodyStats = shareBodyStats
                            )
                        )
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Accept", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConsentCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = FlameOrange)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrivacyToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = appSwitchColors(),
            modifier = Modifier.testTag(testTag)
        )
    }
}
