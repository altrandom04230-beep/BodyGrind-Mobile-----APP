package com.example.ui.trainer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.coaching.*
import com.example.data.local.AppDatabase
import com.example.data.local.DataSeeder
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.data.scheduling.CancellationNotificationDoc
import com.example.data.scheduling.SchedulingRepository
import com.example.data.trainer.AppRole
import com.example.data.trainer.MemberStatus
import com.example.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TrainerViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getDatabase(application)
    private val coachingRepository = CoachingRepository(appContext, database)
    private val schedulingRepository = SchedulingRepository(appContext)
    private val workoutPlanDao = database.workoutPlanDao()
    private val exerciseDao = database.exerciseDao()

    val currentUid: String = coachingRepository.currentUid().orEmpty()

    private val _trainerAccount = MutableStateFlow<UserAccountDoc?>(null)
    val trainerAccount: StateFlow<UserAccountDoc?> = _trainerAccount.asStateFlow()

    val currentRole: StateFlow<AppRole?> = _trainerAccount.map { account ->
        account?.accountType
            ?: if (currentUid.isNotBlank()) CoachingSessionPrefs.getSavedRole(appContext, currentUid) else null
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        if (currentUid.isNotBlank()) CoachingSessionPrefs.getSavedRole(appContext, currentUid) else null
    )

    private val _inviteCode = MutableStateFlow(
        if (currentUid.isNotBlank()) CoachingSessionPrefs.getSavedInviteCode(appContext, currentUid) else null
    )
    val inviteCode: StateFlow<String?> = _inviteCode.asStateFlow()

    private val _inviteError = MutableStateFlow<String?>(null)
    val inviteError: StateFlow<String?> = _inviteError.asStateFlow()

    val linksState: StateFlow<CoachingUiState<List<TrainerMemberLink>>> =
        if (currentUid.isBlank()) {
            MutableStateFlow(CoachingUiState.Success(emptyList()))
        } else {
            coachingRepository.observeTrainerLinks(currentUid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Loading)
        }

    val links: StateFlow<List<TrainerMemberLink>> = linksState.map { state ->
        (state as? CoachingUiState.Success)?.data.orEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeLinks: StateFlow<List<TrainerMemberLink>> = links.map { list ->
        list.filter { it.status == LinkStatus.ACTIVE }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatThreads: StateFlow<List<ChatThread>> =
        if (currentUid.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            coachingRepository.observeTrainerChatThreads(currentUid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    val totalUnreadMessages: StateFlow<Int> = combine(chatThreads, activeLinks) { threads, active ->
        val activeIds = active.map { it.memberId }.toSet()
        threads.filter { it.memberId in activeIds }.sumOf { it.trainerUnreadCount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val cancellationNotifications: StateFlow<List<CancellationNotificationDoc>> =
        if (currentUid.isBlank()) {
            MutableStateFlow(emptyList())
        } else {
            schedulingRepository.observeCancellationNotifications(AppRole.TRAINER, currentUid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        }

    val unreadCancellationAlerts: StateFlow<Int> = cancellationNotifications
        .map { list -> list.count { !it.isReadBy(currentUid) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedMemberId = MutableStateFlow("")
    val selectedMemberId: StateFlow<String> = _selectedMemberId.asStateFlow()

    val selectedMemberLink: StateFlow<TrainerMemberLink?> = combine(links, selectedMemberId) { list, id ->
        list.find { it.memberId == id } ?: list.firstOrNull { it.status == LinkStatus.ACTIVE } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val selectedMemberActiveLink: StateFlow<TrainerMemberLink?> = selectedMemberLink.map { link ->
        if (link != null && link.status == LinkStatus.ACTIVE) link else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedMemberWorkoutsSummaryState: StateFlow<CoachingUiState<SharedWorkoutsSummary?>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            when {
                link == null -> flowOf(CoachingUiState.Success(null))
                !link.shareWorkouts -> flowOf(CoachingUiState.Error("Hidden by ${link.memberName}'s privacy settings."))
                else -> coachingRepository.observeMemberWorkoutsSummary(link.memberId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(null))

    val selectedMemberWorkoutsState: StateFlow<CoachingUiState<List<SharedWorkoutItem>>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            when {
                link == null -> flowOf(CoachingUiState.Success(emptyList()))
                !link.shareWorkouts -> flowOf(CoachingUiState.Error("Hidden by ${link.memberName}'s privacy settings."))
                else -> coachingRepository.observeMemberWorkouts(link.memberId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(emptyList()))

    val selectedMemberStreakXpState: StateFlow<CoachingUiState<SharedStreakXp?>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            when {
                link == null -> flowOf(CoachingUiState.Success(null))
                !link.shareStreakXp -> flowOf(CoachingUiState.Error("Hidden by ${link.memberName}'s privacy settings."))
                else -> coachingRepository.observeMemberStreakXp(link.memberId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(null))

    val selectedMemberBodyStatsState: StateFlow<CoachingUiState<SharedBodyStatsSummary?>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            when {
                link == null -> flowOf(CoachingUiState.Success(null))
                !link.shareBodyStats -> flowOf(CoachingUiState.Error("Hidden by ${link.memberName}'s privacy settings."))
                else -> coachingRepository.observeMemberBodyStats(link.memberId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(null))

    val selectedMemberMeasurementsState: StateFlow<CoachingUiState<List<SharedMeasurementItem>>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            when {
                link == null -> flowOf(CoachingUiState.Success(emptyList()))
                !link.shareBodyStats -> flowOf(CoachingUiState.Error("Hidden by ${link.memberName}'s privacy settings."))
                else -> coachingRepository.observeMemberMeasurements(link.memberId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(emptyList()))

    val selectedMemberAssignedPlansState: StateFlow<CoachingUiState<List<AssignedPlanDoc>>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            if (link == null) {
                flowOf(CoachingUiState.Success(emptyList()))
            } else {
                coachingRepository.observeMemberAssignedPlans(link.memberId).map { state ->
                    if (state is CoachingUiState.Success) {
                        CoachingUiState.Success(state.data.filterNot { it.isDeleted })
                    } else {
                        state
                    }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(emptyList()))

    val selectedChatMessagesState: StateFlow<CoachingUiState<List<ChatMessage>>> =
        selectedMemberActiveLink.flatMapLatest { link ->
            if (link == null) {
                flowOf(CoachingUiState.Success(emptyList()))
            } else {
                coachingRepository.observeChatMessages(link.trainerId, link.memberId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(emptyList()))

    val availablePlans: StateFlow<List<WorkoutPlanEntity>> = workoutPlanDao.getAllPlans()
        .map { plans -> plans.filter { it.remoteAssignmentId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableExercises: StateFlow<List<ExerciseEntity>> = exerciseDao.getAllExercises()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _openChatMemberId = MutableStateFlow<String?>(null)
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _sendMessageError = MutableStateFlow<String?>(null)
    val sendMessageError: StateFlow<String?> = _sendMessageError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    init {
        viewModelScope.launch {
            DataSeeder.seedIfNeeded(database)
        }
        viewModelScope.launch {
            schedulingRepository.registerFcmTokenForCurrentUser()
        }
        loadTrainerProfileAndInvite()
        observeIncomingMessageNotifications()
    }

    private fun loadTrainerProfileAndInvite(forceRetryUserWrite: Boolean = false) {
        if (currentUid.isBlank()) return
        viewModelScope.launch {
            _inviteError.value = null
            val authUser = try { FirebaseAuth.getInstance().currentUser } catch (_: Throwable) { null }
            val fallbackName = CoachingSessionPrefs.getSavedName(appContext, currentUid)
                ?: authUser?.displayName?.takeIf { it.isNotBlank() }
                ?: authUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: "Coach"

            val fetched = coachingRepository.fetchUserAccount(currentUid).getOrNull()
            val account = if (fetched != null) {
                fetched
            } else if (!forceRetryUserWrite && CoachingSyncHealth.hasUserDocWritePermissionError(currentUid)) {
                UserAccountDoc(
                    userId = currentUid,
                    name = fallbackName,
                    email = authUser?.email.orEmpty(),
                    accountType = AppRole.TRAINER
                )
            } else {
                coachingRepository.ensureUserAccountDoc(
                    uid = currentUid,
                    role = AppRole.TRAINER,
                    name = fallbackName,
                    email = authUser?.email.orEmpty()
                ).getOrNull() ?: UserAccountDoc(
                    userId = currentUid,
                    name = fallbackName,
                    email = authUser?.email.orEmpty(),
                    accountType = AppRole.TRAINER
                )
            }
            _trainerAccount.value = account

            coachingRepository.ensureTrainerInviteCode(
                trainerId = currentUid,
                trainerName = account.name.ifBlank { fallbackName },
                trainerTitle = account.trainerTitle,
                specialization = account.specialization,
                preferredCode = account.trainerInviteCode
            ).onSuccess { code ->
                _inviteCode.value = code
                _inviteError.value = null
            }.onFailure { err ->
                _inviteError.value = toFriendlyFirestoreMessage(err)
            }
        }
    }

    fun loadInviteCode() {
        CoachingSyncHealth.clearUserWritePermissionLimited(currentUid)
        loadTrainerProfileAndInvite(forceRetryUserWrite = true)
    }

    private fun observeIncomingMessageNotifications() {
        viewModelScope.launch {
            chatThreads.collect { threads ->
                for (thread in threads) {
                    if (_openChatMemberId.value == thread.memberId) {
                        if (thread.trainerUnreadCount > 0) {
                            coachingRepository.markChatRead(thread.trainerId, thread.memberId, AppRole.TRAINER)
                        }
                        continue
                    }
                    val lastSender = thread.lastSenderId
                    val lastMsgAt = thread.lastMessageAtMs ?: 0L
                    val lastMsg = thread.lastMessage
                    if (!lastSender.isNullOrBlank() && lastSender != currentUid && !lastMsg.isNullOrBlank() && thread.trainerUnreadCount > 0) {
                        val prevSeen = CoachingSessionPrefs.getLastNotifiedMessageTime(appContext, thread.pairId)
                        if (lastMsgAt > prevSeen) {
                            CoachingSessionPrefs.setLastNotifiedMessageTime(appContext, thread.pairId, lastMsgAt)
                            NotificationHelper.showCoachMessageNotification(
                                context = appContext,
                                senderName = thread.memberName.ifBlank { "Member" },
                                messageText = lastMsg
                            )
                        }
                    }
                }
            }
        }
    }

    fun selectMember(memberId: String) {
        _selectedMemberId.value = memberId
    }

    fun setChatOpenForMember(memberId: String?) {
        _openChatMemberId.value = memberId
        if (!memberId.isNullOrBlank() && currentUid.isNotBlank()) {
            viewModelScope.launch {
                coachingRepository.markChatRead(currentUid, memberId, AppRole.TRAINER)
            }
        }
    }

    fun updateMemberStatus(memberId: String, status: MemberStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            coachingRepository.updateLinkMemberStatus(memberId, status)
                .onSuccess {
                    _actionMessage.value = "Updated status to ${status.label}."
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "Failed to update member status."
                }
            _isBusy.value = false
        }
    }

    fun unlinkMember(memberId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            coachingRepository.unlinkTrainerMember(memberId)
                .onSuccess {
                    if (_selectedMemberId.value == memberId) {
                        _selectedMemberId.value = ""
                    }
                    _actionMessage.value = "Member unlinked. Data sharing has stopped immediately."
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "Failed to unlink member."
                }
            _isBusy.value = false
        }
    }

    suspend fun getPlanExercisesSync(planId: String): List<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>> {
        val pes = workoutPlanDao.getPlanExercisesSync(planId)
        val result = mutableListOf<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>()
        for (pe in pes) {
            val ex = exerciseDao.getExerciseById(pe.exerciseId)
            if (ex != null) {
                result.add(pe to ex)
            }
        }
        return result
    }

    fun assignPlanToMember(
        memberId: String,
        planTitle: String,
        planDescription: String,
        category: String,
        difficulty: String,
        estimatedDurationMinutes: Int,
        note: String?,
        startDateEpochMs: Long,
        exercises: List<AssignedPlanExercisePayload>,
        onSuccess: () -> Unit = {}
    ) {
        val coachName = _trainerAccount.value?.name?.ifBlank { "Coach" } ?: "Coach"
        viewModelScope.launch {
            _isBusy.value = true
            coachingRepository.assignPlanToMember(
                memberId = memberId,
                trainerName = coachName,
                planTitle = planTitle,
                planDescription = planDescription,
                category = category,
                difficulty = difficulty,
                estimatedDurationMinutes = estimatedDurationMinutes,
                note = note,
                startDateEpochMs = startDateEpochMs,
                exercises = exercises
            ).onSuccess {
                _actionMessage.value = "Assigned \"$planTitle\" to member!"
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Failed to assign workout plan."
            }
            _isBusy.value = false
        }
    }

    fun sendMessageToMember(memberId: String, text: String) {
        val link = links.value.find { it.memberId == memberId } ?: return
        if (link.status != LinkStatus.ACTIVE) return
        val coachName = _trainerAccount.value?.name?.ifBlank { link.trainerName } ?: link.trainerName
        viewModelScope.launch {
            _isSendingMessage.value = true
            _sendMessageError.value = null
            coachingRepository.sendChatMessage(
                trainerId = link.trainerId,
                memberId = link.memberId,
                trainerName = coachName,
                memberName = link.memberName,
                senderRole = AppRole.TRAINER,
                senderName = coachName,
                text = text
            ).onFailure { err ->
                _sendMessageError.value = err.message ?: "Failed to send message."
            }
            _isSendingMessage.value = false
        }
    }

    fun cancelAssignedPlan(plan: AssignedPlanDoc, memberName: String, reason: String) {
        val actorRole = _trainerAccount.value?.accountType ?: AppRole.TRAINER
        val actorName = _trainerAccount.value?.name?.takeIf { it.isNotBlank() }
            ?: CoachingSessionPrefs.getSavedName(appContext, currentUid)
            ?: "Coach"
        viewModelScope.launch {
            _isBusy.value = true
            try {
                schedulingRepository.cancelAssignedPlan(
                    plan = plan,
                    memberName = memberName,
                    reason = reason,
                    actorRole = actorRole,
                    actorName = actorName
                ).onSuccess {
                    _actionMessage.value = "Cancelled \"${plan.planTitle.ifBlank { "Assigned Workout" }}\" for ${memberName.ifBlank { "member" }}."
                }.onFailure { err ->
                    _actionMessage.value = err.message ?: "Failed to cancel assigned workout."
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun deleteAssignedPlan(
        plan: AssignedPlanDoc,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val savedRole = if (currentUid.isNotBlank()) CoachingSessionPrefs.getSavedRole(appContext, currentUid) else null
        val actorRole = _trainerAccount.value?.accountType ?: savedRole
        if (actorRole != AppRole.TRAINER ||
            currentUid.isBlank() ||
            plan.trainerId != currentUid ||
            plan.isDeleted
        ) {
            val msg = "Couldn't delete the plan. Please try again."
            Log.w(
                "TrainerViewModel",
                "Blocked deleteAssignedPlan: role=$actorRole uid=$currentUid planTrainer=${plan.trainerId} deleted=${plan.isDeleted}"
            )
            onError(msg)
            return
        }
        val actorName = _trainerAccount.value?.name?.trim()?.takeIf { it.isNotBlank() }
            ?: CoachingSessionPrefs.getSavedName(appContext, currentUid)?.trim()?.takeIf { it.isNotBlank() }
            ?: "Trainer"
        viewModelScope.launch {
            _isBusy.value = true
            try {
                coachingRepository.deleteAssignedPlan(
                    memberId = plan.memberId,
                    assignmentId = plan.id,
                    actorUid = currentUid,
                    actorName = actorName
                )
                    .onSuccess {
                        _actionMessage.value = "Plan deleted."
                        onSuccess()
                    }
                    .onFailure { err ->
                        Log.e("TrainerViewModel", "Failed to delete assigned plan ${plan.id}", err)
                        onError("Couldn't delete the plan. Please try again.")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TrainerViewModel", "Unexpected error deleting assigned plan ${plan.id}", e)
                onError("Couldn't delete the plan. Please try again.")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
