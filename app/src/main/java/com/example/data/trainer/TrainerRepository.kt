package com.example.data.trainer

import com.example.utils.ManilaDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrainerRepository {

    private val _trainerInfo = MutableStateFlow(MockTrainerDataSource.sampleTrainer)
    val trainerInfo: StateFlow<TrainerInfo> = _trainerInfo.asStateFlow()

    private val _assignedMembers = MutableStateFlow(MockTrainerDataSource.sampleMembers)
    val assignedMembers: StateFlow<List<AssignedMember>> = _assignedMembers.asStateFlow()

    private val _pendingTasks = MutableStateFlow(MockTrainerDataSource.samplePendingTasks)
    val pendingTasks: StateFlow<List<TrainerPendingTask>> = _pendingTasks.asStateFlow()

    private val _notifications = MutableStateFlow(MockTrainerDataSource.sampleNotifications)
    val notifications: StateFlow<List<TrainerNotification>> = _notifications.asStateFlow()

    private val _feedbackList = MutableStateFlow(MockTrainerDataSource.initialFeedbackEntries)
    val feedbackList: StateFlow<List<TrainerFeedbackEntry>> = _feedbackList.asStateFlow()

    fun toggleTaskCompleted(taskId: String) {
        _pendingTasks.value = _pendingTasks.value.map { task ->
            if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
        }
    }

    fun markNotificationRead(notificationId: String) {
        _notifications.value = _notifications.value.map { notif ->
            if (notif.id == notificationId) notif.copy(isUnread = false) else notif
        }
    }

    fun submitFeedback(
        memberId: String,
        category: FeedbackCategory,
        message: String
    ) {
        val member = _assignedMembers.value.find { it.id == memberId } ?: return
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return

        val nowMs = ManilaDateTime.nowUtcMillis()
        val timeText = ManilaDateTime.formatRelativeDateTime(nowMs)
        val entry = TrainerFeedbackEntry(
            id = "fb_$nowMs",
            memberId = member.id,
            memberName = member.name,
            category = category,
            message = trimmed,
            timestampLabel = timeText,
            timestampUtcMs = nowMs
        )
        _feedbackList.value = listOf(entry) + _feedbackList.value
    }
}
