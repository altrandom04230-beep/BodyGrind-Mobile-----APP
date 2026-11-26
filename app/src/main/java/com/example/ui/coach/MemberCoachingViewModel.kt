package com.example.ui.coach

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.coaching.*
import com.example.data.local.AppDatabase
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.data.scheduling.ScheduledSessionDoc
import com.example.data.scheduling.SchedulingRepository
import com.example.data.scheduling.TrainerAvailabilityDoc
import com.example.data.trainer.AppRole
import com.example.utils.NotificationHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MemberCoachingViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val database = AppDatabase.getDatabase(application)
    private val repository = CoachingRepository(appContext, database)
    private val schedulingRepository = SchedulingRepository(appContext)

    val currentUid: String = repository.currentUid().orEmpty()

    val linkState: StateFlow<CoachingUiState<TrainerMemberLink?>> =
        if (currentUid.isBlank()) {
            MutableStateFlow(CoachingUiState.Success(null))
        } else {
            repository.observeMemberLink(currentUid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Loading)
        }

    val pendingSyncCount: StateFlow<Int> = repository.observePendingSyncCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val assignedPlansState: StateFlow<CoachingUiState<List<AssignedPlanDoc>>> =
        if (currentUid.isBlank()) {
            MutableStateFlow(CoachingUiState.Success(emptyList()))
        } else {
            repository.observeMemberAssignedPlans(currentUid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Loading)
        }

    val activeLink: StateFlow<TrainerMemberLink?> = linkState.map { state ->
        (state as? CoachingUiState.Success)?.data
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatThread: StateFlow<ChatThread?> = activeLink.flatMapLatest { link ->
        if (link != null && link.status == LinkStatus.ACTIVE) {
            repository.observeChatThread(link.trainerId, link.memberId)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val trainerAvailabilityState: StateFlow<CoachingUiState<TrainerAvailabilityDoc?>> =
        activeLink.flatMapLatest { link ->
            if (link != null && link.status == LinkStatus.ACTIVE) {
                schedulingRepository.observeTrainerAvailability(link.trainerId).map { state ->
                    when (state) {
                        is CoachingUiState.Loading -> CoachingUiState.Loading
                        is CoachingUiState.Error -> CoachingUiState.Error(state.message)
                        is CoachingUiState.Success -> CoachingUiState.Success(state.data)
                    }
                }
            } else {
                flowOf(CoachingUiState.Success(null))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(null))

    val scheduledSessionsState: StateFlow<CoachingUiState<List<ScheduledSessionDoc>>> =
        if (currentUid.isBlank()) {
            MutableStateFlow(CoachingUiState.Success(emptyList()))
        } else {
            schedulingRepository.observeScheduledSessionsForMember(currentUid)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CoachingUiState.Success(emptyList()))
        }

    private val _isRefreshingChat = MutableStateFlow(false)
    val isRefreshingChat: StateFlow<Boolean> = _isRefreshingChat.asStateFlow()

    private val _chatMessagesState = MutableStateFlow<CoachingUiState<List<ChatMessage>>>(CoachingUiState.Loading)
    val chatMessagesState: StateFlow<CoachingUiState<List<ChatMessage>>> = _chatMessagesState.asStateFlow()

    private var lastKnownMessages: List<ChatMessage> = emptyList()

    init {
        viewModelScope.launch {
            activeLink.flatMapLatest { link ->
                if (link != null && link.status == LinkStatus.ACTIVE) {
                    repository.observeChatMessages(link.trainerId, link.memberId)
                } else {
                    flowOf(CoachingUiState.Success(emptyList()))
                }
            }.collect { state ->
                when (state) {
                    is CoachingUiState.Success -> {
                        lastKnownMessages = state.data
                        _chatMessagesState.value = state
                    }
                    is CoachingUiState.Loading -> {
                        if (_chatMessagesState.value !is CoachingUiState.Success) {
                            _chatMessagesState.value = state
                        }
                    }
                    is CoachingUiState.Error -> {
                        if (_chatMessagesState.value !is CoachingUiState.Success) {
                            _chatMessagesState.value = state
                        }
                    }
                }
            }
        }
    }

    fun refreshChat(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val link = activeLink.value
        if (link == null || link.status != LinkStatus.ACTIVE) {
            onError("Couldn't refresh. Try again.")
            return
        }
        if (_isRefreshingChat.value) return
        _isRefreshingChat.value = true
        viewModelScope.launch {
            try {
                val result = repository.fetchChatMessages(link.trainerId, link.memberId)
                if (result.isSuccess) {
                    val list = result.getOrThrow()
                    lastKnownMessages = list
                    _chatMessagesState.value = CoachingUiState.Success(list)
                    onSuccess()
                } else {
                    onError("Couldn't refresh. Try again.")
                }
            } catch (_: Exception) {
                onError("Couldn't refresh. Try again.")
            } finally {
                _isRefreshingChat.value = false
            }
        }
    }

    private val _isChatOpen = MutableStateFlow(false)
    fun setChatOpen(open: Boolean) {
        _isChatOpen.value = open
        if (open) {
            val link = activeLink.value
            if (link != null && link.status == LinkStatus.ACTIVE) {
                viewModelScope.launch {
                    repository.markChatRead(link.trainerId, link.memberId, AppRole.MEMBER)
                }
            }
        }
    }

    private val _pendingConfirmInvite = MutableStateFlow<TrainerInviteDoc?>(null)
    val pendingConfirmInvite: StateFlow<TrainerInviteDoc?> = _pendingConfirmInvite.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _sendMessageError = MutableStateFlow<String?>(null)
    val sendMessageError: StateFlow<String?> = _sendMessageError.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerConnectivityFlush()
        observeAssignedPlansSyncToRoom()
        observeIncomingMessageNotifications()
    }

    private fun registerConnectivityFlush() {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                viewModelScope.launch {
                    repository.flushPendingQueue()
                }
            }
        }
        networkCallback = cb
        try {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, cb)
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { cb ->
            try { cm?.unregisterNetworkCallback(cb) } catch (_: Throwable) {}
        }
    }

    private fun observeAssignedPlansSyncToRoom() {
        viewModelScope.launch {
            combine(assignedPlansState, activeLink) { plansState, link ->
                plansState to link
            }.collect { (plansState, link) ->
                if (currentUid.isBlank()) return@collect
                val remotePlans = (plansState as? CoachingUiState.Success)?.data ?: return@collect
                repository.syncAssignedPlansToRoom(currentUid, remotePlans, link)
            }
        }
    }

    private fun observeIncomingMessageNotifications() {
        viewModelScope.launch {
            chatThread.collect { thread ->
                if (thread == null) return@collect
                if (_isChatOpen.value) {
                    if (thread.memberUnreadCount > 0) {
                        repository.markChatRead(thread.trainerId, thread.memberId, AppRole.MEMBER)
                    }
                    return@collect
                }
                val lastSender = thread.lastSenderId
                val lastMsgAt = thread.lastMessageAtMs ?: 0L
                val lastMsg = thread.lastMessage
                if (!lastSender.isNullOrBlank() && lastSender != currentUid && !lastMsg.isNullOrBlank() && thread.memberUnreadCount > 0) {
                    val prevSeen = CoachingSessionPrefs.getLastNotifiedMessageTime(appContext, thread.pairId)
                    if (lastMsgAt > prevSeen) {
                        CoachingSessionPrefs.setLastNotifiedMessageTime(appContext, thread.pairId, lastMsgAt)
                        NotificationHelper.showCoachMessageNotification(
                            context = appContext,
                            senderName = thread.trainerName.ifBlank { "Coach" },
                            messageText = lastMsg
                        )
                    }
                }
            }
        }
    }

    fun syncMemberDataIfLinked(
        profile: UserProfileEntity,
        sessions: List<WorkoutSessionEntity>,
        measurements: List<BodyMeasurementEntity>,
        streakDays: Int
    ) {
        viewModelScope.launch {
            repository.flushPendingQueue()
            repository.queueOrSyncMemberProgress(
                link = activeLink.value,
                profile = profile,
                sessions = sessions,
                measurements = measurements,
                streakDays = streakDays
            )
        }
    }

    fun manualFlushAndSync(
        profile: UserProfileEntity,
        sessions: List<WorkoutSessionEntity>,
        measurements: List<BodyMeasurementEntity>,
        streakDays: Int
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.queueOrSyncMemberProgress(
                link = activeLink.value,
                profile = profile,
                sessions = sessions,
                measurements = measurements,
                streakDays = streakDays
            )
            val flushed = repository.flushPendingQueue()
            _actionMessage.value = if (FirestoreProvider.isNetworkAvailable(appContext)) {
                if (flushed > 0) "Synced $flushed queued updates with your coach." else "Progress is up to date."
            } else {
                "Offline: updates are queued and will sync automatically when connected."
            }
            _isBusy.value = false
        }
    }

    fun lookupInviteCode(rawInput: String) {
        viewModelScope.launch {
            _isBusy.value = true
            _actionMessage.value = null
            repository.lookupTrainerInvite(rawInput)
                .onSuccess { invite ->
                    _pendingConfirmInvite.value = invite
                }
                .onFailure { err ->
                    _actionMessage.value = toFriendlyFirestoreMessage(err)
                }
            _isBusy.value = false
        }
    }

    fun dismissConfirmInvite() {
        _pendingConfirmInvite.value = null
    }

    fun confirmAndLinkWithInvite(
        invite: TrainerInviteDoc,
        memberProfile: UserProfileEntity,
        consent: LinkConsentOptions
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.linkMemberToTrainerWithInvite(invite, memberProfile, consent)
                .onSuccess {
                    _pendingConfirmInvite.value = null
                    _actionMessage.value = "Connected with Coach ${invite.trainerName}!"
                }
                .onFailure { err ->
                    _actionMessage.value = toFriendlyFirestoreMessage(err)
                }
            _isBusy.value = false
        }
    }

    fun acceptPendingRequest(memberProfile: UserProfileEntity, consent: LinkConsentOptions) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.acceptPendingLinkRequest(memberProfile, consent)
                .onSuccess {
                    _actionMessage.value = "Coaching link accepted!"
                }
                .onFailure { err ->
                    _actionMessage.value = toFriendlyFirestoreMessage(err)
                }
            _isBusy.value = false
        }
    }

    fun unlinkCoach() {
        val uid = currentUid.ifBlank { return }
        viewModelScope.launch {
            _isBusy.value = true
            repository.unlinkTrainerMember(uid)
                .onSuccess {
                    _actionMessage.value = "Unlinked from coach. All progress sharing has stopped."
                }
                .onFailure { err ->
                    _actionMessage.value = toFriendlyFirestoreMessage(err)
                }
            _isBusy.value = false
        }
    }

    fun updatePrivacy(
        shareWorkouts: Boolean,
        shareStreakXp: Boolean,
        shareBodyStats: Boolean
    ) {
        viewModelScope.launch {
            repository.updatePrivacySettings(shareWorkouts, shareStreakXp, shareBodyStats)
                .onFailure { err ->
                    if (FirestoreProvider.isNetworkAvailable(appContext)) {
                        _actionMessage.value = toFriendlyFirestoreMessage(err)
                    } else {
                        _actionMessage.value = "Offline: privacy change queued and will sync when online."
                    }
                }
        }
    }

    fun sendChatMessage(memberName: String, text: String) {
        val link = activeLink.value ?: return
        if (link.status != LinkStatus.ACTIVE) return
        viewModelScope.launch {
            _isSendingMessage.value = true
            _sendMessageError.value = null
            repository.sendChatMessage(
                trainerId = link.trainerId,
                memberId = link.memberId,
                trainerName = link.trainerName,
                memberName = memberName.ifBlank { link.memberName },
                senderRole = AppRole.MEMBER,
                senderName = memberName.ifBlank { link.memberName },
                text = text
            ).onFailure { err ->
                _sendMessageError.value = toFriendlyFirestoreMessage(err)
            }
            _isSendingMessage.value = false
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
