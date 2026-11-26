package com.example.ui.trainer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.coaching.AssignedPlanDoc
import com.example.data.coaching.CoachingUiState
import com.example.data.scheduling.AvailabilityDateOverride
import com.example.data.scheduling.CancellationNotificationDoc
import com.example.data.scheduling.ScheduledSessionDoc
import com.example.data.scheduling.SchedulingValidator
import com.example.data.scheduling.TrainerAvailabilityDoc
import com.example.data.scheduling.WeeklyAvailabilitySlot
import com.example.data.trainer.AppRole
import com.example.ui.theme.BoldRed
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.example.utils.ManilaDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerSchedulingScreen(
    trainerId: String,
    trainerName: String,
    actorRole: AppRole,
    actorName: String,
    onBack: () -> Unit,
    schedulingViewModel: SchedulingViewModel = viewModel(key = "scheduling_${trainerId}_${actorRole.name}")
) {
    BackHandler { onBack() }

    LaunchedEffect(trainerId, trainerName, actorRole, actorName) {
        schedulingViewModel.configure(
            trainerId = trainerId,
            trainerName = trainerName,
            actorRole = actorRole,
            actorName = actorName
        )
    }

    val availabilityState by schedulingViewModel.availabilityState.collectAsStateWithLifecycle()
    val sessionsState by schedulingViewModel.sessionsState.collectAsStateWithLifecycle()
    val linkedMembers by schedulingViewModel.linkedMembers.collectAsStateWithLifecycle()
    val assignedPlans by schedulingViewModel.assignedPlans.collectAsStateWithLifecycle()
    val cancellationNotifications by schedulingViewModel.cancellationNotifications.collectAsStateWithLifecycle()
    val isBusy by schedulingViewModel.isBusy.collectAsStateWithLifecycle()
    val actionMessage by schedulingViewModel.actionMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            schedulingViewModel.clearActionMessage()
        }
    }

    val canEdit = remember(actorRole, trainerId, schedulingViewModel.currentUid) {
        SchedulingValidator.canEditTrainerAvailability(actorRole, schedulingViewModel.currentUid, trainerId)
    }
    val canCancel = remember(actorRole) { SchedulingValidator.canCancelRole(actorRole) }

    val loadedAvailability = (availabilityState as? CoachingUiState.Success)?.data
    var editedSlots by remember(loadedAvailability) {
        mutableStateOf(loadedAvailability?.weeklySlots ?: TrainerAvailabilityDoc.defaultWeeklySlots())
    }
    var editedOverrides by remember(loadedAvailability) {
        mutableStateOf(loadedAvailability?.dateOverrides ?: emptyList())
    }

    var showAddOverrideDialog by remember { mutableStateOf(false) }
    var showCreateSessionDialog by remember { mutableStateOf(false) }
    var sessionToCancel by remember { mutableStateOf<ScheduledSessionDoc?>(null) }
    var sessionToDelete by remember { mutableStateOf<ScheduledSessionDoc?>(null) }
    var planToCancel by remember { mutableStateOf<AssignedPlanDoc?>(null) }
    var planToDelete by remember { mutableStateOf<AssignedPlanDoc?>(null) }
    var deletePlanError by remember { mutableStateOf<String?>(null) }

    val allSessions = (sessionsState as? CoachingUiState.Success)?.data.orEmpty()
    val activeSessions = remember(allSessions) { allSessions.filter { !it.isCancelled } }
    val cancelledSessions = remember(allSessions) { allSessions.filter { it.isCancelled } }
    val activePlans = remember(assignedPlans) { assignedPlans.filter { !it.isCancelled } }
    val cancelledPlans = remember(assignedPlans) { assignedPlans.filter { it.isCancelled } }
    val unreadAlerts = remember(cancellationNotifications, schedulingViewModel.currentUid) {
        cancellationNotifications.filter { !it.isReadBy(schedulingViewModel.currentUid) }
    }
    val memberNamesById = remember(linkedMembers) {
        linkedMembers.associate { it.memberId to it.memberName.ifBlank { it.memberId } }
    }
    val memberRawNamesById = remember(linkedMembers) {
        linkedMembers.associate { it.memberId to it.memberName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Schedule & Availability",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${trainerName.ifBlank { "Trainer" }} • Asia/Manila (UTC+8)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("scheduling_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEdit) {
                        IconButton(
                            onClick = { showCreateSessionDialog = true },
                            modifier = Modifier.testTag("scheduling_add_session_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Schedule Session", tint = BoldRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("trainer_scheduling_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 1. In-App Cancellation Alerts Banner
            if (unreadAlerts.isNotEmpty()) {
                item {
                    CancellationAlertsCard(
                        alerts = unreadAlerts,
                        onMarkRead = { schedulingViewModel.markNotificationRead(it) }
                    )
                }
            }

            // 2. Trainer Availability (Weekly Recurring Slots + Date Overrides)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = BoldRed)
                                Column {
                                    Text(
                                        text = "Trainer Availability",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Weekly recurring hours + date overrides (Asia/Manila)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (availabilityState is CoachingUiState.Loading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = BoldRed, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            editedSlots.sortedBy { it.dayOfWeek }.forEach { slot ->
                                WeeklySlotRow(
                                    slot = slot,
                                    canEdit = canEdit,
                                    onUpdate = { updated ->
                                        editedSlots = editedSlots.map {
                                            if (it.dayOfWeek == updated.dayOfWeek) updated else it
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Date Overrides (${editedOverrides.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (canEdit) {
                                    OutlinedButton(
                                        onClick = { showAddOverrideDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("add_date_override_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Date Override", fontSize = 12.sp)
                                    }
                                }
                            }

                            if (editedOverrides.isEmpty()) {
                                Text(
                                    text = "No specific date overrides set.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                editedOverrides.sortedBy { it.dateKey }.forEach { override ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ManilaDateTime.formatDateKey(override.dateKey),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val desc = if (override.isAvailable) {
                                                "${ManilaDateTime.formatSlotTime(override.startTime)} – ${ManilaDateTime.formatSlotTime(override.endTime)}"
                                            } else {
                                                "Unavailable / Blocked"
                                            }
                                            Text(
                                                text = if (override.note.isNotBlank()) "$desc • ${override.note}" else desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (override.isAvailable) SuccessGreen else DangerRed
                                            )
                                        }
                                        if (canEdit) {
                                            IconButton(
                                                onClick = {
                                                    editedOverrides = editedOverrides.filterNot { it.dateKey == override.dateKey }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = "Remove Override",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (canEdit) {
                                Button(
                                    onClick = {
                                        schedulingViewModel.saveAvailability(
                                            weeklySlots = editedSlots,
                                            dateOverrides = editedOverrides
                                        )
                                    },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("save_trainer_availability_button")
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save Availability", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Scheduled Sessions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BoldRed)
                        Text(
                            text = "Scheduled Sessions (${activeSessions.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (canEdit) {
                        TextButton(
                            onClick = { showCreateSessionDialog = true },
                            modifier = Modifier.testTag("schedule_new_session_text_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Session", color = BoldRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (activeSessions.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active scheduled sessions for this trainer yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(activeSessions, key = { it.id }) { session ->
                    ScheduledSessionCard(
                        session = session,
                        canCancel = canCancel,
                        canDelete = canEdit,
                        isBusy = isBusy,
                        onCancel = { sessionToCancel = session },
                        onDelete = { sessionToDelete = session }
                    )
                }
            }

            // 4. Assigned Workout Plans & Session Dates
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = BoldRed)
                    Text(
                        text = "Assigned Workout Plans (${activePlans.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (activePlans.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No active assigned workout plans found for linked members.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(activePlans, key = { it.id }) { plan ->
                    val canDeletePlan = actorRole == AppRole.TRAINER &&
                        schedulingViewModel.currentUid.isNotBlank() &&
                        schedulingViewModel.currentUid == plan.trainerId &&
                        !plan.isDeleted
                    AssignedPlanScheduleCard(
                        plan = plan,
                        memberName = memberNamesById[plan.memberId] ?: "Member",
                        canCancel = canCancel && !plan.isEffectivelyCompleted,
                        canDelete = canDeletePlan,
                        isBusy = isBusy,
                        onCancel = { planToCancel = plan },
                        onDelete = {
                            deletePlanError = null
                            planToDelete = plan
                        }
                    )
                }
            }

            // 5. Cancelled Sessions & Workouts Audit Log (Visible to both Trainer and Admin)
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = DangerRed)
                    Text(
                        text = "Cancelled Sessions & Workouts (${cancelledSessions.size + cancelledPlans.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (cancelledSessions.isEmpty() && cancelledPlans.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No cancelled sessions or workouts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(cancelledSessions, key = { "cancelled_${it.id}" }) { session ->
                    CancelledAuditCard(
                        title = session.title.ifBlank { "Scheduled Session" },
                        memberName = session.memberName,
                        trainerName = session.trainerName,
                        scheduledDateLabel = "${session.formattedScheduledDate()} • ${ManilaDateTime.formatSlotTime(session.startTime)} – ${ManilaDateTime.formatSlotTime(session.endTime)}",
                        reason = session.cancelledReason.orEmpty(),
                        cancelledByName = session.cancelledByName.orEmpty(),
                        cancelledByRole = session.cancelledByRole.orEmpty(),
                        cancelledAtLabel = session.formattedCancelledAt()
                    )
                }
                items(cancelledPlans, key = { "cancelled_plan_${it.id}" }) { plan ->
                    val canDeleteCancelledPlan = actorRole == AppRole.TRAINER &&
                        schedulingViewModel.currentUid.isNotBlank() &&
                        schedulingViewModel.currentUid == plan.trainerId &&
                        !plan.isDeleted
                    CancelledAuditCard(
                        title = plan.planTitle.ifBlank { "Assigned Workout" },
                        memberName = memberNamesById[plan.memberId] ?: "Member",
                        trainerName = plan.trainerName,
                        scheduledDateLabel = ManilaDateTime.formatDate(plan.startDateEpochMs),
                        reason = plan.cancelledReason.orEmpty(),
                        cancelledByName = plan.cancelledByName.orEmpty(),
                        cancelledByRole = plan.cancelledByRole.orEmpty(),
                        cancelledAtLabel = ManilaDateTime.formatDateTime(plan.cancelledAtMs),
                        canDelete = canDeleteCancelledPlan,
                        isBusy = isBusy,
                        deleteButtonTestTag = "delete_assigned_plan_btn_${plan.id}",
                        onDelete = {
                            deletePlanError = null
                            planToDelete = plan
                        }
                    )
                }
            }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }

    if (showAddOverrideDialog) {
        AddDateOverrideDialog(
            onDismiss = { showAddOverrideDialog = false },
            onConfirm = { newOverride ->
                editedOverrides = editedOverrides
                    .filterNot { it.dateKey == newOverride.dateKey }
                    .plus(newOverride)
                    .sortedBy { it.dateKey }
                showAddOverrideDialog = false
            }
        )
    }

    if (showCreateSessionDialog) {
        CreateScheduledSessionDialog(
            linkedMembers = linkedMembers,
            isBusy = isBusy,
            onDismiss = { showCreateSessionDialog = false },
            onCreate = { memberId, memberName, title, notes, date, startTime, endTime ->
                schedulingViewModel.createSession(
                    memberId = memberId,
                    memberName = memberName,
                    title = title,
                    notes = notes,
                    scheduledDate = date,
                    startTime = startTime,
                    endTime = endTime,
                    onSuccess = { showCreateSessionDialog = false }
                )
            }
        )
    }

    sessionToCancel?.let { target ->
        CancelSessionReasonDialog(
            targetTitle = target.title.ifBlank { "Scheduled Session" },
            scheduledDateLabel = target.formattedScheduledDate(),
            isBusy = isBusy,
            onDismiss = { sessionToCancel = null },
            onConfirmCancel = { reason ->
                schedulingViewModel.cancelSession(target, reason) {
                    sessionToCancel = null
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
                schedulingViewModel.cancelAssignedPlan(
                    plan = target,
                    memberName = memberNamesById[target.memberId] ?: "Member",
                    reason = reason
                ) {
                    planToCancel = null
                }
            }
        )
    }

    planToDelete?.let { target ->
        DeleteAssignedPlanDialog(
            planTitle = target.planTitle,
            memberName = memberRawNamesById[target.memberId],
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
                schedulingViewModel.deleteAssignedPlan(
                    plan = target,
                    onSuccess = {
                        planToDelete = null
                        deletePlanError = null
                    },
                    onError = { err ->
                        deletePlanError = err
                    }
                )
            }
        )
    }

    sessionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete Session", fontWeight = FontWeight.Bold) },
            text = {
                Text("Permanently delete \"${target.title}\" (${target.formattedScheduledDate()})? Use Cancel instead if you want to keep a cancellation record and notify Trainer & Admin.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        schedulingViewModel.deleteSession(target.id)
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { sessionToDelete = null }) {
                    Text("Back")
                }
            }
        )
    }
}

@Composable
private fun WeeklySlotRow(
    slot: WeeklyAvailabilitySlot,
    canEdit: Boolean,
    onUpdate: (WeeklyAvailabilitySlot) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = ManilaDateTime.dayOfWeekLabel(slot.dayOfWeek),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (slot.enabled) {
                        "${ManilaDateTime.formatSlotTime(slot.startTime)} – ${ManilaDateTime.formatSlotTime(slot.endTime)}"
                    } else {
                        "Off"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (slot.enabled) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canEdit) {
                    Switch(
                        checked = slot.enabled,
                        onCheckedChange = { onUpdate(slot.copy(enabled = it)) },
                        modifier = Modifier.testTag("availability_day_switch_${slot.dayOfWeek}")
                    )
                }
            }
        }
        if (canEdit && slot.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = slot.startTime,
                    onValueChange = { onUpdate(slot.copy(startTime = it)) },
                    label = { Text("Start (HH:mm)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = slot.endTime,
                    onValueChange = { onUpdate(slot.copy(endTime = it)) },
                    label = { Text("End (HH:mm)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ScheduledSessionCard(
    session: ScheduledSessionDoc,
    canCancel: Boolean,
    canDelete: Boolean,
    isBusy: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("scheduled_session_card_${session.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title.ifBlank { "Scheduled Session" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Member: ${session.memberName.ifBlank { session.memberId }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val statusColor = if (session.isCompleted) SuccessGreen else WarningAmber
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (session.isCompleted) "Completed" else "Scheduled",
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = BoldRed, modifier = Modifier.size(16.dp))
                Text(
                    text = "${session.formattedScheduledDate()} • ${ManilaDateTime.formatSlotTime(session.startTime)} – ${ManilaDateTime.formatSlotTime(session.endTime)} (Manila)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (session.notes.isNotBlank()) {
                Text(
                    text = session.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (canCancel || canDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canCancel && !session.isCompleted) {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("cancel_session_btn_${session.id}")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel Session", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (canDelete) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onDelete,
                            enabled = !isBusy,
                            modifier = Modifier.testTag("delete_session_btn_${session.id}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete Session",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignedPlanScheduleCard(
    plan: AssignedPlanDoc,
    memberName: String,
    canCancel: Boolean,
    canDelete: Boolean,
    isBusy: Boolean,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.planTitle.ifBlank { "Assigned Plan" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Member: $memberName • ${ManilaDateTime.formatDate(plan.startDateEpochMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val badgeColor = if (plan.isEffectivelyCompleted) SuccessGreen else WarningAmber
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = if (plan.isEffectivelyCompleted) "Completed" else "Assigned",
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (canCancel || canDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canCancel) {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("cancel_assigned_plan_btn_${plan.id}")
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel Workout", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (canDelete) {
                        OutlinedButton(
                            onClick = onDelete,
                            enabled = !isBusy,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("delete_assigned_plan_btn_${plan.id}")
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CancelledAuditCard(
    title: String,
    memberName: String,
    trainerName: String,
    scheduledDateLabel: String,
    reason: String,
    cancelledByName: String,
    cancelledByRole: String,
    cancelledAtLabel: String,
    modifier: Modifier = Modifier,
    canDelete: Boolean = false,
    isBusy: Boolean = false,
    deleteButtonTestTag: String? = null,
    onDelete: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cancelled_session_audit_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DangerRed.copy(alpha = 0.18f)
                ) {
                    Text(
                        text = "CANCELLED",
                        color = DangerRed,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Scheduled: $scheduledDateLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (memberName.isNotBlank() || trainerName.isNotBlank()) {
                Text(
                    text = "Trainer: ${trainerName.ifBlank { "Coach" }} • Member: ${memberName.ifBlank { "Member" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "Reason: ${reason.ifBlank { "Not specified" }}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = DangerRed
            )
            val roleLabel = cancelledByRole.replaceFirstChar { it.uppercase() }.ifBlank { "Staff" }
            Text(
                text = "Cancelled by ${cancelledByName.ifBlank { roleLabel }} ($roleLabel) on $cancelledAtLabel (Asia/Manila)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Excluded from completed totals, billing, trainer payouts & package balances.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canDelete && onDelete != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = if (deleteButtonTestTag != null) Modifier.testTag(deleteButtonTestTag) else Modifier
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CancellationAlertsCard(
    alerts: List<CancellationNotificationDoc>,
    onMarkRead: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DangerRed.copy(alpha = 0.10f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("in_app_cancellation_alerts_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = DangerRed)
                Text(
                    text = "Cancellation Alerts (${alerts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DangerRed
                )
            }

            alerts.take(5).forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${alert.sessionTitle} (${ManilaDateTime.formatDateKey(alert.scheduledDateKey)})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reason: ${alert.reason}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DangerRed
                        )
                        Text(
                            text = "By ${alert.cancelledByName} (${alert.cancelledByRole}) • ${ManilaDateTime.formatDateTime(alert.cancelledAtMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onMarkRead(alert.id) },
                        modifier = Modifier.testTag("mark_cancellation_alert_read_${alert.id}")
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Mark alert as read",
                            tint = SuccessGreen
                        )
                    }
                }
            }
        }
    }
}
