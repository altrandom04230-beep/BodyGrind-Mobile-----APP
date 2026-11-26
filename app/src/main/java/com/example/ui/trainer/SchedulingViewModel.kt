package com.example.ui.trainer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.coaching.AssignedPlanDoc
import com.example.data.coaching.CoachingRepository
import com.example.data.coaching.CoachingSessionPrefs
import com.example.data.coaching.CoachingUiState
import com.example.data.coaching.LinkStatus
import com.example.data.coaching.TrainerMemberLink
import com.example.data.local.AppDatabase
import com.example.data.scheduling.AvailabilityDateOverride
import com.example.data.scheduling.CancellationNotificationDoc
import com.example.data.scheduling.ScheduledSessionDoc
import com.example.data.scheduling.SchedulingRepository
import com.example.data.scheduling.TrainerAvailabilityDoc
import com.example.data.scheduling.WeeklyAvailabilitySlot
import com.example.data.trainer.AppRole
import com.example.utils.ManilaDateTime
import com.example.utils.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulingViewModel @JvmOverloads constructor(
    application: Application,
    private val schedulingRepository: SchedulingRepository = SchedulingRepository(application.applicationContext),
    private val coachingRepository: CoachingRepository = CoachingRepository(
        application.applicationContext,
        AppDatabase.getDatabase(application)
    )
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val currentUid: String
        get() = schedulingRepository.currentUid().orEmpty()

    private val _targetTrainerId = MutableStateFlow("")
    val targetTrainerId: StateFlow<String> = _targetTrainerId.asStateFlow()

    private val _targetTrainerName = MutableStateFlow("")
    val targetTrainerName: StateFlow<String> = _targetTrainerName.asStateFlow()

    private val _actorRole = MutableStateFlow(AppRole.TRAINER)
    val actorRole: StateFlow<AppRole> = _actorRole.asStateFlow()

    private val _actorName = MutableStateFlow("Coach")
    val actorName: StateFlow<String> = _actorName.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _assignedPlans = MutableStateFlow<List<AssignedPlanDoc>>(emptyList())
    val assignedPlans: StateFlow<List<AssignedPlanDoc>> = _assignedPlans.asStateFlow()

    private val notifiedAlertIds = mutableSetOf<String>()

    val availabilityState: StateFlow<CoachingUiState<TrainerAvailabilityDoc>> =
        _targetTrainerId.flatMapLatest { trainerId ->
            if (trainerId.isBlank()) {
                flowOf(CoachingUiState.Success(TrainerAvailabilityDoc()))
            } else {
                schedulingRepository.observeTrainerAvailability(trainerId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Loading)

    val sessionsState: StateFlow<CoachingUiState<List<ScheduledSessionDoc>>> =
        _targetTrainerId.flatMapLatest { trainerId ->
            if (trainerId.isBlank()) {
                flowOf(CoachingUiState.Success(emptyList()))
            } else {
                schedulingRepository.observeScheduledSessionsForTrainer(trainerId)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Loading)

    val allAdminSessionsState: StateFlow<CoachingUiState<List<ScheduledSessionDoc>>> =
        _actorRole.flatMapLatest { role ->
            if (role == AppRole.ADMIN) {
                schedulingRepository.observeAllScheduledSessionsForAdmin()
            } else {
                flowOf(CoachingUiState.Success(emptyList()))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(emptyList()))

    val linkedMembers: StateFlow<List<TrainerMemberLink>> =
        _targetTrainerId.flatMapLatest { trainerId ->
            if (trainerId.isBlank()) {
                flowOf(emptyList())
            } else {
                coachingRepository.observeTrainerLinks(trainerId).map { state ->
                    (state as? CoachingUiState.Success)?.data
                        ?.filter { it.status == LinkStatus.ACTIVE }
                        .orEmpty()
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cancellationNotifications: StateFlow<List<CancellationNotificationDoc>> =
        _actorRole.flatMapLatest { role ->
            val uid = currentUid
            if (uid.isBlank() || role == AppRole.MEMBER) {
                flowOf(emptyList())
            } else {
                schedulingRepository.observeCancellationNotifications(role, uid)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unreadNotificationCount: StateFlow<Int> = cancellationNotifications
        .map { list -> list.count { !it.isReadBy(currentUid) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        viewModelScope.launch {
            schedulingRepository.registerFcmTokenForCurrentUser()
        }
        viewModelScope.launch {
            combine(_targetTrainerId, linkedMembers) { trainerId, members ->
                trainerId to members.map { it.memberId }
            }.collect { (trainerId, memberIds) ->
                if (trainerId.isBlank() || memberIds.isEmpty()) {
                    _assignedPlans.value = emptyList()
                } else {
                    _assignedPlans.value = schedulingRepository.fetchAssignedPlansForMembers(
                        memberIds = memberIds,
                        trainerId = trainerId
                    )
                }
            }
        }
        viewModelScope.launch {
            cancellationNotifications.collect { alerts ->
                val uid = currentUid
                for (alert in alerts) {
                    if (!alert.isReadBy(uid) && alert.cancelledByUid != uid && notifiedAlertIds.add(alert.id)) {
                        val formattedDate = ManilaDateTime.formatDateKey(alert.scheduledDateKey)
                        NotificationHelper.showCancellationNotification(
                            context = appContext,
                            title = "Session Cancelled: ${alert.sessionTitle}",
                            body = "$formattedDate • Cancelled by ${alert.cancelledByName} (${alert.cancelledByRole}). Reason: ${alert.reason}"
                        )
                    }
                }
            }
        }
    }

    fun configure(
        trainerId: String,
        trainerName: String,
        actorRole: AppRole,
        actorName: String
    ) {
        _targetTrainerId.value = trainerId
        _targetTrainerName.value = trainerName.ifBlank { "Coach" }
        _actorRole.value = actorRole
        _actorName.value = actorName.ifBlank {
            if (actorRole == AppRole.ADMIN) "Admin" else "Coach"
        }
    }

    fun refreshAssignedPlans() {
        val trainerId = _targetTrainerId.value
        val memberIds = linkedMembers.value.map { it.memberId }
        if (trainerId.isBlank() || memberIds.isEmpty()) return
        viewModelScope.launch {
            _assignedPlans.value = schedulingRepository.fetchAssignedPlansForMembers(
                memberIds = memberIds,
                trainerId = trainerId
            )
        }
    }

    fun saveAvailability(
        weeklySlots: List<WeeklyAvailabilitySlot>,
        dateOverrides: List<AvailabilityDateOverride>,
        onSuccess: () -> Unit = {}
    ) {
        val trainerId = _targetTrainerId.value
        if (trainerId.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            try {
                schedulingRepository.saveTrainerAvailability(
                    trainerId = trainerId,
                    trainerName = _targetTrainerName.value,
                    weeklySlots = weeklySlots,
                    dateOverrides = dateOverrides,
                    actorRole = _actorRole.value
                ).onSuccess {
                    _actionMessage.value = "Trainer availability saved (Asia/Manila)."
                    onSuccess()
                }.onFailure { err ->
                    _actionMessage.value = err.message ?: "Failed to save trainer availability."
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun createSession(
        memberId: String,
        memberName: String,
        title: String,
        notes: String,
        scheduledDate: LocalDate,
        startTime: String,
        endTime: String,
        assignedPlanId: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val trainerId = _targetTrainerId.value
        if (trainerId.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            schedulingRepository.createScheduledSession(
                trainerId = trainerId,
                trainerName = _targetTrainerName.value,
                memberId = memberId,
                memberName = memberName,
                title = title,
                notes = notes,
                scheduledDate = scheduledDate,
                startTime = startTime,
                endTime = endTime,
                actorRole = _actorRole.value,
                assignedPlanId = assignedPlanId
            ).onSuccess {
                _actionMessage.value = "Session scheduled for ${ManilaDateTime.formatDate(scheduledDate)}."
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Failed to schedule session."
            }
            _isBusy.value = false
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            schedulingRepository.deleteScheduledSession(sessionId)
                .onSuccess {
                    _actionMessage.value = "Session deleted."
                }
                .onFailure { err ->
                    _actionMessage.value = err.message ?: "Failed to delete session."
                }
            _isBusy.value = false
        }
    }

    fun cancelSession(
        session: ScheduledSessionDoc,
        reason: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            schedulingRepository.cancelScheduledSession(
                session = session,
                reason = reason,
                actorRole = _actorRole.value,
                actorName = _actorName.value
            ).onSuccess {
                _actionMessage.value = "Session cancelled. Trainer & Admin notified."
                refreshAssignedPlans()
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Failed to cancel session."
            }
            _isBusy.value = false
        }
    }

    fun cancelAssignedPlan(
        plan: AssignedPlanDoc,
        memberName: String,
        reason: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            schedulingRepository.cancelAssignedPlan(
                plan = plan,
                memberName = memberName,
                reason = reason,
                actorRole = _actorRole.value,
                actorName = _actorName.value
            ).onSuccess {
                _actionMessage.value = "Assigned workout cancelled. Trainer & Admin notified."
                refreshAssignedPlans()
                onSuccess()
            }.onFailure { err ->
                _actionMessage.value = err.message ?: "Failed to cancel assigned workout."
            }
            _isBusy.value = false
        }
    }

    fun deleteAssignedPlan(
        plan: AssignedPlanDoc,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_actorRole.value != AppRole.TRAINER ||
            currentUid.isBlank() ||
            plan.trainerId != currentUid ||
            plan.isDeleted
        ) {
            val msg = "Couldn't delete the plan. Please try again."
            Log.w(
                "SchedulingViewModel",
                "Blocked deleteAssignedPlan: role=${_actorRole.value} uid=$currentUid planTrainer=${plan.trainerId} deleted=${plan.isDeleted}"
            )
            onError(msg)
            return
        }
        val actorName = (_actorName.value.takeIf { it.isNotBlank() && it != "Coach" && it != "Admin" }
            ?: CoachingSessionPrefs.getSavedName(appContext, currentUid))
            ?.trim()
            ?.takeIf { it.isNotBlank() }
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
                        refreshAssignedPlans()
                        onSuccess()
                    }
                    .onFailure { err ->
                        Log.e("SchedulingViewModel", "Failed to delete assigned plan ${plan.id}", err)
                        onError("Couldn't delete the plan. Please try again.")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("SchedulingViewModel", "Unexpected error deleting assigned plan ${plan.id}", e)
                onError("Couldn't delete the plan. Please try again.")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            schedulingRepository.markCancellationNotificationRead(notificationId)
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
