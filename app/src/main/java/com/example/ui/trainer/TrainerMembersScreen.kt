package com.example.ui.trainer

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.coaching.*
import com.example.data.trainer.MemberStatus
import com.example.ui.theme.BoldRed
import com.example.ui.theme.DangerRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.ManilaDateTime
import java.util.Locale

@Composable
fun TrainerMembersScreen(
    linksState: CoachingUiState<List<TrainerMemberLink>>,
    selectedLink: TrainerMemberLink?,
    chatThreads: List<ChatThread>,
    workoutsSummaryState: CoachingUiState<SharedWorkoutsSummary?>,
    streakXpState: CoachingUiState<SharedStreakXp?>,
    bodyStatsState: CoachingUiState<SharedBodyStatsSummary?>,
    assignedPlansState: CoachingUiState<List<AssignedPlanDoc>>,
    isDetailVisible: Boolean,
    isBusy: Boolean,
    currentTrainerId: String = "",
    isTrainerRole: Boolean = false,
    onSelectMember: (String) -> Unit,
    onBackToList: () -> Unit,
    onMonitorProgress: (String) -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenAssignPlan: (TrainerMemberLink) -> Unit,
    onUpdateStatus: (memberId: String, status: MemberStatus) -> Unit,
    onUnlinkMember: (memberId: String) -> Unit,
    onCancelAssignedPlan: (plan: AssignedPlanDoc, memberName: String, reason: String) -> Unit = { _, _, _ -> },
    onDeleteAssignedPlan: (plan: AssignedPlanDoc, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit = { _, _, _ -> }
) {
    if (isDetailVisible && selectedLink != null) {
        TrainerMemberDetailView(
            link = selectedLink,
            workoutsSummaryState = workoutsSummaryState,
            streakXpState = streakXpState,
            bodyStatsState = bodyStatsState,
            assignedPlansState = assignedPlansState,
            isBusy = isBusy,
            currentTrainerId = currentTrainerId,
            isTrainerRole = isTrainerRole,
            onBack = onBackToList,
            onMonitorProgress = { onMonitorProgress(selectedLink.memberId) },
            onOpenMessage = { onOpenMessage(selectedLink.memberId) },
            onOpenAssignPlan = { onOpenAssignPlan(selectedLink) },
            onUpdateStatus = { status -> onUpdateStatus(selectedLink.memberId, status) },
            onUnlink = { onUnlinkMember(selectedLink.memberId) },
            onCancelAssignedPlan = { plan, reason -> onCancelAssignedPlan(plan, selectedLink.memberName, reason) },
            onDeleteAssignedPlan = onDeleteAssignedPlan
        )
    } else {
        LinkedMembersListView(
            linksState = linksState,
            chatThreads = chatThreads,
            onSelectMember = onSelectMember
        )
    }
}

@Composable
private fun LinkedMembersListView(
    linksState: CoachingUiState<List<TrainerMemberLink>>,
    chatThreads: List<ChatThread>,
    onSelectMember: (String) -> Unit
) {
    var statusFilter by remember { mutableStateOf<MemberStatus?>(null) }
    val links = (linksState as? CoachingUiState.Success)?.data.orEmpty()
    val threadByMember = remember(chatThreads) { chatThreads.associateBy { it.memberId } }
    val filteredLinks = remember(links, statusFilter) {
        if (statusFilter == null) links else links.filter { it.memberStatus == statusFilter }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Linked Members (${links.size})",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = {
                            Text(
                                text = "All (${links.size})",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("filter_chip_all")
                    )
                    FilterChip(
                        selected = statusFilter == MemberStatus.ACTIVE,
                        onClick = { statusFilter = MemberStatus.ACTIVE },
                        label = {
                            Text(
                                text = MemberStatus.ACTIVE.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("filter_chip_active")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == MemberStatus.NEEDS_ATTENTION,
                        onClick = { statusFilter = MemberStatus.NEEDS_ATTENTION },
                        label = {
                            Text(
                                text = MemberStatus.NEEDS_ATTENTION.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("filter_chip_needs_attention")
                    )
                    FilterChip(
                        selected = statusFilter == MemberStatus.PAUSED,
                        onClick = { statusFilter = MemberStatus.PAUSED },
                        label = {
                            Text(
                                text = MemberStatus.PAUSED.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("filter_chip_paused")
                    )
                }
            }
        }

        when (linksState) {
            is CoachingUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
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
                        Text(
                            text = linksState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is CoachingUiState.Success -> {
                if (filteredLinks.isEmpty()) {
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
                                Icon(Icons.Default.GroupAdd, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (links.isEmpty()) "No linked members yet" else "No members in this filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ask members to scan your invite QR or enter your invite code from the Dashboard.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(filteredLinks, key = { it.memberId }) { link ->
                        val isPending = link.status == LinkStatus.PENDING_CONFIRMATION
                        val statusColor = when {
                            isPending -> WarningAmber
                            link.memberStatus == MemberStatus.ACTIVE -> SuccessGreen
                            link.memberStatus == MemberStatus.NEEDS_ATTENTION -> WarningAmber
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val statusLabel = if (isPending) "Pending Consent" else link.memberStatus.label
                        val lastSeen = link.lastActiveAtMs?.let { ManilaDateTime.formatDate(it, fallback = "Recently linked") } ?: "Recently linked"
                        val unread = threadByMember[link.memberId]?.trainerUnreadCount ?: 0

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMember(link.memberId) }
                                .testTag("assigned_member_card_${link.memberId}"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                                        .clip(CircleShape)
                                        .background(FlameOrange.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = link.memberName.split(" ").take(2).joinToString("") { it.take(1) }.ifBlank { "M" },
                                        fontWeight = FontWeight.Bold,
                                        color = FlameOrange
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = link.memberName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = statusColor.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = statusLabel,
                                                color = statusColor,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = link.fitnessGoal?.takeIf { it.isNotBlank() } ?: "BodyGrind Athlete",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Last activity: $lastSeen" + if (unread > 0) " • $unread unread" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (unread > 0) FlameOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Open Member",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainerMemberDetailView(
    link: TrainerMemberLink,
    workoutsSummaryState: CoachingUiState<SharedWorkoutsSummary?>,
    streakXpState: CoachingUiState<SharedStreakXp?>,
    bodyStatsState: CoachingUiState<SharedBodyStatsSummary?>,
    assignedPlansState: CoachingUiState<List<AssignedPlanDoc>>,
    isBusy: Boolean,
    currentTrainerId: String,
    isTrainerRole: Boolean,
    onBack: () -> Unit,
    onMonitorProgress: () -> Unit,
    onOpenMessage: () -> Unit,
    onOpenAssignPlan: () -> Unit,
    onUpdateStatus: (MemberStatus) -> Unit,
    onUnlink: () -> Unit,
    onCancelAssignedPlan: (plan: AssignedPlanDoc, reason: String) -> Unit,
    onDeleteAssignedPlan: (plan: AssignedPlanDoc, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var showUnlinkDialog by remember { mutableStateOf(false) }
    var planToCancel by remember { mutableStateOf<AssignedPlanDoc?>(null) }
    var planToDelete by remember { mutableStateOf<AssignedPlanDoc?>(null) }
    var deletePlanError by remember { mutableStateOf<String?>(null) }

    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text("Unlink ${link.memberName}?") },
            text = {
                Text("Unlinking immediately stops all progress sharing and removes uncompleted coach-assigned workout plans.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnlinkDialog = false
                        onUnlink()
                    }
                ) {
                    Text("Unlink", color = BoldRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    planToCancel?.let { target ->
        CancelSessionReasonDialog(
            targetTitle = target.planTitle.ifBlank { "Assigned Workout" },
            scheduledDateLabel = ManilaDateTime.formatDate(target.startDateEpochMs),
            isBusy = isBusy,
            onDismiss = { planToCancel = null },
            onConfirmCancel = { reason ->
                onCancelAssignedPlan(target, reason)
                planToCancel = null
            }
        )
    }

    planToDelete?.let { target ->
        DeleteAssignedPlanDialog(
            planTitle = target.planTitle,
            memberName = link.memberName,
            isDeleting = isBusy,
            errorMessage = deletePlanError,
            onDismiss = {
                if (!isBusy) {
                    planToDelete = null
                    deletePlanError = null
                }
            },
            onConfirmDelete = {
                deletePlanError = null
                onDeleteAssignedPlan(
                    target,
                    {
                        planToDelete = null
                        deletePlanError = null
                    },
                    { err ->
                        deletePlanError = err
                    }
                )
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("member_detail_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Members")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(link.memberName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text(
                        text = listOfNotNull(
                            link.fitnessLevel?.takeIf { it.isNotBlank() },
                            link.fitnessGoal?.takeIf { it.isNotBlank() }
                        ).joinToString(" • ").ifBlank { "Linked Member" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showUnlinkDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("trainer_unlink_member_button")
                ) {
                    Icon(Icons.Default.LinkOff, contentDescription = "Unlink Member", tint = BoldRed)
                }
            }
        }

        if (link.status == LinkStatus.PENDING_CONFIRMATION) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Pending Member Confirmation", fontWeight = FontWeight.Bold, color = WarningAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${link.memberName} must review and accept what data will be shared before workouts, stats, or chat unlock.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Editable Member Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Coaching Status", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemberStatus.entries.forEach { st ->
                            FilterChip(
                                selected = link.memberStatus == st,
                                onClick = { if (!isBusy && link.memberStatus != st) onUpdateStatus(st) },
                                label = {
                                    Text(
                                        text = st.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                modifier = Modifier.weight(1f).testTag("member_status_chip_${st.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions: Assign Plan, Message, Monitor Progress
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenAssignPlan,
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("member_assign_plan_button")
                    ) {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Assign Plan", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenMessage,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).testTag("member_message_button")
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Message")
                    }
                }
                OutlinedButton(
                    onClick = onMonitorProgress,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("member_monitor_progress_button")
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View Detailed Progress Charts")
                }
            }
        }

        // Privacy-Gated Snapshot Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shared Member Snapshot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val workoutsSummary = (workoutsSummaryState as? CoachingUiState.Success)?.data
                    val streakXp = (streakXpState as? CoachingUiState.Success)?.data
                    val bodyStats = (bodyStatsState as? CoachingUiState.Success)?.data

                    ProfileDetailRow(
                        label = "Workouts",
                        value = when (workoutsSummaryState) {
                            is CoachingUiState.Error -> "Hidden by privacy toggle"
                            is CoachingUiState.Loading -> "Loading..."
                            is CoachingUiState.Success -> if (workoutsSummary != null) {
                                "${workoutsSummary.totalWorkouts} total • ${workoutsSummary.completedThisWeek} this week"
                            } else "No workouts yet"
                        }
                    )
                    ProfileDetailRow(
                        label = "Streak & Level",
                        value = when (streakXpState) {
                            is CoachingUiState.Error -> "Hidden by privacy toggle"
                            is CoachingUiState.Loading -> "Loading..."
                            is CoachingUiState.Success -> if (streakXp != null) {
                                "${streakXp.streakDays}d streak • Lvl ${streakXp.level} (${streakXp.xpPoints} XP)"
                            } else "No data yet"
                        }
                    )
                    ProfileDetailRow(
                        label = "BMI & Weight",
                        value = when (bodyStatsState) {
                            is CoachingUiState.Error -> "Hidden by privacy toggle"
                            is CoachingUiState.Loading -> "Loading..."
                            is CoachingUiState.Success -> if (bodyStats != null) {
                                "${String.format(Locale.US, "%.1f", bodyStats.weightKg)} kg • BMI ${String.format(Locale.US, "%.1f", bodyStats.bmi)}"
                            } else "No measurements yet"
                        }
                    )
                }
            }
        }

        // Assigned Workout Plans + Completion Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Assigned Workout Plans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    when (assignedPlansState) {
                        is CoachingUiState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FlameOrange)
                        }

                        is CoachingUiState.Error -> {
                            Text(assignedPlansState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        is CoachingUiState.Success -> {
                            if (assignedPlansState.data.isEmpty()) {
                                Text(
                                    text = "No plans assigned yet. Tap 'Assign Plan' above to send a routine.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                assignedPlansState.data.forEachIndexed { idx, plan ->
                                    val badgeColor = when {
                                        plan.isCancelled -> DangerRed
                                        plan.isEffectivelyCompleted -> SuccessGreen
                                        else -> WarningAmber
                                    }
                                    val badgeText = when {
                                        plan.isCancelled -> "Cancelled"
                                        plan.isEffectivelyCompleted -> "Completed"
                                        else -> "Pending"
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(plan.planTitle, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                val dateText = if (plan.startDateEpochMs > 0L) ManilaDateTime.formatDate(plan.startDateEpochMs, fallback = "Anytime") else "Anytime"
                                                Text(
                                                    text = "Start: $dateText • ${plan.exercises.size} exercises",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (!plan.note.isNullOrBlank()) {
                                                    Text(
                                                        text = "Note: ${plan.note}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = badgeColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = badgeText,
                                                    color = badgeColor,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        if (plan.isCancelled) {
                                            Text(
                                                text = "Reason: ${plan.cancelledReason.orEmpty().ifBlank { "Not specified" }} • By ${plan.cancelledByName.orEmpty().ifBlank { plan.cancelledByRole.orEmpty() }} on ${ManilaDateTime.formatDateTime(plan.cancelledAtMs)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DangerRed
                                            )
                                        }
                                        val showCancelButton = !plan.isCancelled && !plan.isEffectivelyCompleted
                                        val canDeletePlan = isTrainerRole &&
                                            currentTrainerId.isNotBlank() &&
                                            plan.trainerId == currentTrainerId &&
                                            !plan.isDeleted
                                        if (showCancelButton || canDeletePlan) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (showCancelButton) {
                                                    TextButton(
                                                        onClick = { planToCancel = plan },
                                                        enabled = !isBusy,
                                                        modifier = Modifier.testTag("member_detail_cancel_plan_${plan.id}")
                                                    ) {
                                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Cancel Workout", color = DangerRed, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                if (canDeletePlan) {
                                                    TextButton(
                                                        onClick = {
                                                            deletePlanError = null
                                                            planToDelete = plan
                                                        },
                                                        enabled = !isBusy,
                                                        modifier = Modifier.testTag("member_detail_delete_plan_${plan.id}")
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DeleteOutline,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            "Delete",
                                                            color = MaterialTheme.colorScheme.error,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
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
private fun ProfileDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}
