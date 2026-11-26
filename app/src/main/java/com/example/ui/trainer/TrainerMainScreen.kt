package com.example.ui.trainer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.coaching.LinkStatus
import com.example.data.coaching.TrainerMemberLink
import com.example.data.trainer.AppRole
import com.example.ui.theme.BoldRed

sealed class TrainerTab {
    data object Dashboard : TrainerTab()
    data object Members : TrainerTab()
    data object Progress : TrainerTab()
    data object Messages : TrainerTab()
    data object Account : TrainerTab()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerMainScreen(
    onLogOut: () -> Unit,
    trainerViewModel: TrainerViewModel = viewModel()
) {
    val trainerAccount by trainerViewModel.trainerAccount.collectAsStateWithLifecycle()
    val currentRole by trainerViewModel.currentRole.collectAsStateWithLifecycle()
    val inviteCode by trainerViewModel.inviteCode.collectAsStateWithLifecycle()
    val inviteError by trainerViewModel.inviteError.collectAsStateWithLifecycle()
    val linksState by trainerViewModel.linksState.collectAsStateWithLifecycle()
    val links by trainerViewModel.links.collectAsStateWithLifecycle()
    val activeLinks by trainerViewModel.activeLinks.collectAsStateWithLifecycle()
    val selectedMemberLink by trainerViewModel.selectedMemberLink.collectAsStateWithLifecycle()
    val chatThreads by trainerViewModel.chatThreads.collectAsStateWithLifecycle()
    val totalUnreadMessages by trainerViewModel.totalUnreadMessages.collectAsStateWithLifecycle()

    val workoutsSummaryState by trainerViewModel.selectedMemberWorkoutsSummaryState.collectAsStateWithLifecycle()
    val workoutsState by trainerViewModel.selectedMemberWorkoutsState.collectAsStateWithLifecycle()
    val streakXpState by trainerViewModel.selectedMemberStreakXpState.collectAsStateWithLifecycle()
    val bodyStatsState by trainerViewModel.selectedMemberBodyStatsState.collectAsStateWithLifecycle()
    val measurementsState by trainerViewModel.selectedMemberMeasurementsState.collectAsStateWithLifecycle()
    val assignedPlansState by trainerViewModel.selectedMemberAssignedPlansState.collectAsStateWithLifecycle()
    val chatMessagesState by trainerViewModel.selectedChatMessagesState.collectAsStateWithLifecycle()

    val availablePlans by trainerViewModel.availablePlans.collectAsStateWithLifecycle()
    val availableExercises by trainerViewModel.availableExercises.collectAsStateWithLifecycle()

    val isBusy by trainerViewModel.isBusy.collectAsStateWithLifecycle()
    val isSendingMessage by trainerViewModel.isSendingMessage.collectAsStateWithLifecycle()
    val sendMessageError by trainerViewModel.sendMessageError.collectAsStateWithLifecycle()
    val actionMessage by trainerViewModel.actionMessage.collectAsStateWithLifecycle()
    val unreadCancellationAlerts by trainerViewModel.unreadCancellationAlerts.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf<TrainerTab>(TrainerTab.Dashboard) }
    var viewingMemberDetail by remember { mutableStateOf(false) }
    var viewingChat by remember { mutableStateOf(false) }
    var viewingSchedule by remember { mutableStateOf(false) }
    var assignPlanForMember by remember { mutableStateOf<TrainerMemberLink?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
            trainerViewModel.clearActionMessage()
        }
    }

    LaunchedEffect(currentTab, viewingChat, selectedMemberLink?.memberId) {
        if (currentTab == TrainerTab.Messages && viewingChat && selectedMemberLink?.status == LinkStatus.ACTIVE) {
            trainerViewModel.setChatOpenForMember(selectedMemberLink?.memberId)
        } else {
            trainerViewModel.setChatOpenForMember(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            trainerViewModel.setChatOpenForMember(null)
        }
    }

    BackHandler(enabled = viewingSchedule || viewingChat || viewingMemberDetail || currentTab != TrainerTab.Dashboard) {
        when {
            viewingSchedule -> viewingSchedule = false
            viewingChat -> viewingChat = false
            viewingMemberDetail -> viewingMemberDetail = false
            else -> currentTab = TrainerTab.Dashboard
        }
    }

    val activeChatMember = if (viewingChat && selectedMemberLink?.status == LinkStatus.ACTIVE) {
        selectedMemberLink
    } else null

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!viewingChat && !viewingSchedule) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "Trainer Portal",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = onLogOut,
                            modifier = Modifier.testTag("trainer_logout_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Log Out",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        },
        bottomBar = {
            if (!viewingChat && !viewingSchedule) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    val navItems = listOf(
                        Triple(TrainerTab.Dashboard, "Dashboard", Icons.Default.Dashboard),
                        Triple(TrainerTab.Members, "Members", Icons.Default.Groups),
                        Triple(TrainerTab.Progress, "Progress", Icons.Default.Analytics),
                        Triple(TrainerTab.Messages, "Messages", Icons.Default.Chat),
                        Triple(TrainerTab.Account, "Account", Icons.Default.AccountCircle)
                    )

                    navItems.forEach { (tab, label, icon) ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                viewingSchedule = false
                                viewingMemberDetail = false
                                viewingChat = false
                                currentTab = tab
                            },
                            icon = {
                                if (tab == TrainerTab.Messages && totalUnreadMessages > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = BoldRed) {
                                                Text(totalUnreadMessages.toString())
                                            }
                                        }
                                    ) {
                                        Icon(icon, contentDescription = label)
                                    }
                                } else {
                                    Icon(icon, contentDescription = label)
                                }
                            },
                            label = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BoldRed,
                                selectedTextColor = BoldRed,
                                indicatorColor = BoldRed.copy(alpha = 0.18f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("trainer_nav_${label.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
            ) {
            if (viewingSchedule) {
                TrainerSchedulingScreen(
                    trainerId = trainerViewModel.currentUid,
                    trainerName = trainerAccount?.name?.ifBlank { "Coach" } ?: "Coach",
                    actorRole = currentRole ?: AppRole.TRAINER,
                    actorName = trainerAccount?.name?.ifBlank { "Coach" } ?: "Coach",
                    onBack = { viewingSchedule = false }
                )
            } else when (currentTab) {
                is TrainerTab.Dashboard -> {
                    TrainerDashboardScreen(
                        trainerAccount = trainerAccount,
                        inviteCode = inviteCode,
                        inviteError = inviteError,
                        linksState = linksState,
                        chatThreads = chatThreads,
                        totalUnreadMessages = totalUnreadMessages,
                        unreadCancellationAlerts = unreadCancellationAlerts,
                        onRetryInvite = { trainerViewModel.loadInviteCode() },
                        onOpenMember = { memberId ->
                            trainerViewModel.selectMember(memberId)
                            viewingMemberDetail = true
                            viewingChat = false
                            currentTab = TrainerTab.Members
                        },
                        onOpenMessages = {
                            viewingMemberDetail = false
                            viewingChat = false
                            currentTab = TrainerTab.Messages
                        },
                        onOpenSchedule = {
                            viewingSchedule = true
                        }
                    )
                }

                is TrainerTab.Members -> {
                    TrainerMembersScreen(
                        linksState = linksState,
                        selectedLink = selectedMemberLink,
                        chatThreads = chatThreads,
                        workoutsSummaryState = workoutsSummaryState,
                        streakXpState = streakXpState,
                        bodyStatsState = bodyStatsState,
                        assignedPlansState = assignedPlansState,
                        isDetailVisible = viewingMemberDetail,
                        isBusy = isBusy,
                        currentTrainerId = trainerViewModel.currentUid,
                        isTrainerRole = currentRole == AppRole.TRAINER,
                        onSelectMember = { memberId ->
                            trainerViewModel.selectMember(memberId)
                            viewingMemberDetail = true
                        },
                        onBackToList = { viewingMemberDetail = false },
                        onMonitorProgress = { memberId ->
                            trainerViewModel.selectMember(memberId)
                            viewingMemberDetail = false
                            currentTab = TrainerTab.Progress
                        },
                        onOpenMessage = { memberId ->
                            trainerViewModel.selectMember(memberId)
                            viewingMemberDetail = false
                            viewingChat = true
                            currentTab = TrainerTab.Messages
                        },
                        onOpenAssignPlan = { memberLink ->
                            assignPlanForMember = memberLink
                        },
                        onUpdateStatus = { memberId, status ->
                            trainerViewModel.updateMemberStatus(memberId, status)
                        },
                        onUnlinkMember = { memberId ->
                            trainerViewModel.unlinkMember(memberId)
                            viewingMemberDetail = false
                        },
                        onCancelAssignedPlan = { plan, memberName, reason ->
                            trainerViewModel.cancelAssignedPlan(plan, memberName, reason)
                        },
                        onDeleteAssignedPlan = { plan, onSuccess, onError ->
                            trainerViewModel.deleteAssignedPlan(plan, onSuccess = onSuccess, onError = onError)
                        }
                    )
                }

                is TrainerTab.Progress -> {
                    TrainerProgressScreen(
                        links = links,
                        selectedLink = selectedMemberLink,
                        workoutsSummaryState = workoutsSummaryState,
                        workoutsState = workoutsState,
                        streakXpState = streakXpState,
                        bodyStatsState = bodyStatsState,
                        measurementsState = measurementsState,
                        assignedPlansState = assignedPlansState,
                        onSelectMember = { trainerViewModel.selectMember(it) }
                    )
                }

                is TrainerTab.Messages -> {
                    TrainerMessagesScreen(
                        activeLinks = activeLinks,
                        chatThreads = chatThreads,
                        currentTrainerId = trainerViewModel.currentUid,
                        activeChatMember = activeChatMember,
                        messagesState = chatMessagesState,
                        isSendingMessage = isSendingMessage,
                        sendMessageError = sendMessageError,
                        onOpenMemberChat = { memberId ->
                            trainerViewModel.selectMember(memberId)
                            viewingChat = true
                        },
                        onCloseMemberChat = { viewingChat = false },
                        onSendMessage = { memberId, text ->
                            trainerViewModel.sendMessageToMember(memberId, text)
                        }
                    )
                }

                is TrainerTab.Account -> {
                    TrainerPortalScreen(
                        onOpenMySchedule = { viewingSchedule = true }
                    )
                }
            }
            }
        }
    }

    assignPlanForMember?.let { targetMember ->
        TrainerAssignPlanSheet(
            member = targetMember,
            availablePlans = availablePlans,
            availableExercises = availableExercises,
            isBusy = isBusy,
            loadPlanExercises = { planId -> trainerViewModel.getPlanExercisesSync(planId) },
            onDismiss = { assignPlanForMember = null },
            onAssign = { title, desc, cat, diff, dur, note, startMs, exercises ->
                trainerViewModel.assignPlanToMember(
                    memberId = targetMember.memberId,
                    planTitle = title,
                    planDescription = desc,
                    category = cat,
                    difficulty = diff,
                    estimatedDurationMinutes = dur,
                    note = note,
                    startDateEpochMs = startMs,
                    exercises = exercises,
                    onSuccess = { assignPlanForMember = null }
                )
            }
        )
    }
}
