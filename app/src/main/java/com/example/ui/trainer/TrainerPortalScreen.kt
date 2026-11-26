package com.example.ui.trainer

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.coaching.CoachingUiState
import com.example.data.trainer.AppRole
import com.example.data.trainer.TrainerProfileDoc
import com.example.ui.theme.BoldRed
import com.example.utils.ManilaDateTime
import com.google.firebase.auth.FirebaseAuth

enum class TrainerPortalViewMode {
    ADMIN_MEMBERS,
    ADMIN_MANAGEMENT,
    ADMIN_CANCELLATIONS,
    MY_PROFILE
}

@Composable
fun TrainerPortalScreen(
    portalViewModel: TrainerPortalViewModel? = null,
    onOpenMySchedule: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val activeUid = remember {
        try {
            FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }
    val vm: TrainerPortalViewModel = portalViewModel ?: viewModel(
        key = "trainer_portal_${activeUid.ifBlank { "signed_out" }}"
    )
    val adminSchedulingVm: SchedulingViewModel = viewModel(
        key = "admin_cancellations_${activeUid.ifBlank { "signed_out" }}"
    )

    val uiState by vm.uiState.collectAsStateWithLifecycle()
    var selectedViewMode by remember(activeUid) { mutableStateOf(TrainerPortalViewMode.MY_PROFILE) }
    var initializedAdminDefault by remember(activeUid) { mutableStateOf(false) }
    var scheduleTargetTrainer by remember { mutableStateOf<TrainerProfileDoc?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isAdmin, activeUid) {
        if (uiState.isAdmin) {
            adminSchedulingVm.configure(
                trainerId = activeUid,
                trainerName = uiState.currentTrainer?.name?.ifBlank { "Admin" } ?: "Admin",
                actorRole = AppRole.ADMIN,
                actorName = uiState.currentTrainer?.name?.ifBlank { "Admin" } ?: "Admin"
            )
            if (!initializedAdminDefault) {
                selectedViewMode = TrainerPortalViewMode.ADMIN_MEMBERS
                initializedAdminDefault = true
            }
        }
    }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.clearMessages()
        }
        uiState.successMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            vm.clearMessages()
        }
    }

    // Admin managing a specific trainer's schedule & availability
    val targetTrainer = scheduleTargetTrainer
    if (targetTrainer != null) {
        BackHandler { scheduleTargetTrainer = null }
        TrainerSchedulingScreen(
            trainerId = targetTrainer.trainerId,
            trainerName = targetTrainer.name.ifBlank { "Trainer" },
            actorRole = AppRole.ADMIN,
            actorName = uiState.currentTrainer?.name?.ifBlank { "Admin" } ?: "Admin",
            onBack = { scheduleTargetTrainer = null }
        )
        return
    }

    val allAdminSessionsState by adminSchedulingVm.allAdminSessionsState.collectAsStateWithLifecycle()
    val adminCancellationAlerts by adminSchedulingVm.cancellationNotifications.collectAsStateWithLifecycle()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && !uiState.hasPortalAccess -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = BoldRed)
                    }
                }

                !uiState.hasPortalAccess -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("trainer_portal_access_denied"),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Access Restricted",
                                    tint = BoldRed,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "Access Restricted",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Only trainer and administrator accounts can access the Trainer Portal.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                else -> {
                    // Admin tab switcher between Members, Trainers, Cancellations, and own Profile
                    if (uiState.isAdmin) {
                        val selectedIndex = when (selectedViewMode) {
                            TrainerPortalViewMode.ADMIN_MEMBERS -> 0
                            TrainerPortalViewMode.ADMIN_MANAGEMENT -> 1
                            TrainerPortalViewMode.ADMIN_CANCELLATIONS -> 2
                            TrainerPortalViewMode.MY_PROFILE -> 3
                        }
                        TabRow(
                            selectedTabIndex = selectedIndex,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = BoldRed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_portal_tab_row")
                        ) {
                            Tab(
                                selected = selectedViewMode == TrainerPortalViewMode.ADMIN_MEMBERS,
                                onClick = { selectedViewMode = TrainerPortalViewMode.ADMIN_MEMBERS },
                                text = { Text("Members", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.Default.Group, contentDescription = null) },
                                modifier = Modifier.testTag("tab_admin_members")
                            )
                            Tab(
                                selected = selectedViewMode == TrainerPortalViewMode.ADMIN_MANAGEMENT,
                                onClick = { selectedViewMode = TrainerPortalViewMode.ADMIN_MANAGEMENT },
                                text = { Text("Trainers", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null) },
                                modifier = Modifier.testTag("tab_admin_roster")
                            )
                            Tab(
                                selected = selectedViewMode == TrainerPortalViewMode.ADMIN_CANCELLATIONS,
                                onClick = { selectedViewMode = TrainerPortalViewMode.ADMIN_CANCELLATIONS },
                                text = { Text("Cancellations", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.Default.Cancel, contentDescription = null) },
                                modifier = Modifier.testTag("tab_admin_cancellations")
                            )
                            Tab(
                                selected = selectedViewMode == TrainerPortalViewMode.MY_PROFILE,
                                onClick = { selectedViewMode = TrainerPortalViewMode.MY_PROFILE },
                                text = { Text("My Profile", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                icon = { Icon(Icons.Default.Person, contentDescription = null) },
                                modifier = Modifier.testTag("tab_my_profile")
                            )
                        }
                    }

                    if (uiState.isLoading && uiState.currentTrainer == null && uiState.allTrainers.isEmpty() && uiState.allMembers.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = BoldRed)
                        }
                    } else {
                        when {
                            uiState.isAdmin && selectedViewMode == TrainerPortalViewMode.ADMIN_MEMBERS -> {
                                AdminMemberManagementSection(
                                    members = uiState.allMembers,
                                    isBusy = uiState.isSaving,
                                    onUpdateMember = { targetUserId, memberType, isVerified, discountPercent, appliesBilling, appliesPackages, appliesSessions, onDone ->
                                        vm.updateMemberTypeAndDiscountAdmin(
                                            targetUserId = targetUserId,
                                            memberType = memberType,
                                            isVerified = isVerified,
                                            discountPercent = discountPercent,
                                            discountAppliesToBilling = appliesBilling,
                                            discountAppliesToPackages = appliesPackages,
                                            discountAppliesToSessionFees = appliesSessions,
                                            onComplete = onDone
                                        )
                                    }
                                )
                            }

                            uiState.isAdmin && selectedViewMode == TrainerPortalViewMode.ADMIN_MANAGEMENT -> {
                                AdminTrainerManagementSection(
                                    trainers = uiState.allTrainers,
                                    currentFilter = uiState.filterStatus,
                                    isBusy = uiState.isSaving,
                                    onFilterChange = { vm.setFilterStatus(it) },
                                    onApproveCert = { vm.adminApproveCertificate(it) },
                                    onRejectCert = { vm.adminRejectCertificate(it) },
                                    onAddTrainer = { uid, name, email, phone, spec, onSuccess ->
                                        vm.adminAddTrainer(uid, name, email, phone, spec, onSuccess)
                                    },
                                    onEditTrainer = { trainerId, name, email, phone, spec, onSuccess ->
                                        vm.adminUpdateTrainer(trainerId, name, email, phone, spec, onSuccess)
                                    },
                                    onDeleteTrainer = { vm.adminDeleteTrainer(it) },
                                    onOpenSchedule = { trainer ->
                                        scheduleTargetTrainer = trainer
                                    }
                                )
                            }

                            uiState.isAdmin && selectedViewMode == TrainerPortalViewMode.ADMIN_CANCELLATIONS -> {
                                val allSessions = (allAdminSessionsState as? CoachingUiState.Success)?.data.orEmpty()
                                val cancelledSessions = remember(allSessions) { allSessions.filter { it.isCancelled } }
                                val unreadAlerts = remember(adminCancellationAlerts, activeUid) {
                                    adminCancellationAlerts.filter { !it.isReadBy(activeUid) }
                                }
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("admin_cancellations_list"),
                                    contentPadding = PaddingValues(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (unreadAlerts.isNotEmpty()) {
                                        item {
                                            CancellationAlertsCard(
                                                alerts = unreadAlerts,
                                                onMarkRead = { adminSchedulingVm.markNotificationRead(it) }
                                            )
                                        }
                                    }
                                    item {
                                        Text(
                                            text = "All Cancelled Sessions (${cancelledSessions.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (cancelledSessions.isEmpty() && adminCancellationAlerts.isEmpty()) {
                                        item {
                                            Card(
                                                shape = RoundedCornerShape(14.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "No cancelled sessions or workouts recorded yet.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        items(cancelledSessions, key = { it.id }) { session ->
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
                                        // Also show cancelled assigned plans recorded in cancellationNotifications (where sessionId == null)
                                        val planCancellations = adminCancellationAlerts.filter { it.sessionId.isNullOrBlank() }
                                        items(planCancellations, key = { "plan_alert_${it.id}" }) { alert ->
                                            CancelledAuditCard(
                                                title = alert.sessionTitle.ifBlank { "Assigned Workout" },
                                                memberName = alert.memberName,
                                                trainerName = alert.trainerName,
                                                scheduledDateLabel = ManilaDateTime.formatDateKey(alert.scheduledDateKey),
                                                reason = alert.reason,
                                                cancelledByName = alert.cancelledByName,
                                                cancelledByRole = alert.cancelledByRole,
                                                cancelledAtLabel = ManilaDateTime.formatDateTime(alert.cancelledAtMs)
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (onOpenMySchedule != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = onOpenMySchedule,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("account_open_schedule_button")
                                            ) {
                                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = BoldRed, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Manage Schedule & Availability", fontWeight = FontWeight.Bold, color = BoldRed)
                                            }
                                        }
                                    }
                                    TrainerProfileSection(
                                        profile = uiState.currentTrainer,
                                        isSaving = uiState.isSaving,
                                        isUploading = uiState.isUploading,
                                        onSaveProfile = { name, phone, onSuccess ->
                                            vm.saveProfile(name, phone, onSuccess)
                                        },
                                        onUploadCertificate = { uri, fileName, mimeType, fileSize ->
                                            vm.uploadCertificate(
                                                uri = uri,
                                                fileName = fileName,
                                                mimeType = mimeType,
                                                reportedSizeBytes = fileSize,
                                                contentResolver = vm.getApplication<Application>().contentResolver
                                            )
                                        }
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
