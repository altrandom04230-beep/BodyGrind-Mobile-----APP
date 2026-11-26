package com.example.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.coaching.ChatMessage
import com.example.data.coaching.ChatThread
import com.example.data.coaching.CoachingUiState
import com.example.data.coaching.TrainerMemberLink
import com.example.ui.coach.CoachChatScreen
import com.example.ui.theme.BoldRed
import com.example.ui.theme.FlameOrange
import com.example.utils.ManilaDateTime

@Composable
fun TrainerMessagesScreen(
    activeLinks: List<TrainerMemberLink>,
    chatThreads: List<ChatThread>,
    currentTrainerId: String,
    activeChatMember: TrainerMemberLink?,
    messagesState: CoachingUiState<List<ChatMessage>>,
    isSendingMessage: Boolean,
    sendMessageError: String?,
    onOpenMemberChat: (String) -> Unit,
    onCloseMemberChat: () -> Unit,
    onSendMessage: (memberId: String, text: String) -> Unit
) {
    if (activeChatMember != null) {
        CoachChatScreen(
            peerName = activeChatMember.memberName,
            peerSubtitle = activeChatMember.fitnessGoal?.takeIf { it.isNotBlank() } ?: "Linked Member",
            currentUserId = currentTrainerId,
            messagesState = messagesState,
            isSending = isSendingMessage,
            sendError = sendMessageError,
            onSendMessage = { text -> onSendMessage(activeChatMember.memberId, text) },
            onBack = onCloseMemberChat
        )
        return
    }

    val threadByMember = remember(chatThreads) { chatThreads.associateBy { it.memberId } }
    val sortedMembers = remember(activeLinks, threadByMember) {
        activeLinks.sortedByDescending { link ->
            threadByMember[link.memberId]?.lastMessageAtMs ?: link.lastActiveAtMs ?: 0L
        }
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
                text = "Member Messages",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Direct 1-on-1 coaching chat with your linked members.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (sortedMembers.isEmpty()) {
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
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = FlameOrange,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No linked members yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Share your invite code or QR from the Dashboard so members can connect and message you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sortedMembers, key = { it.memberId }) { link ->
                val thread = threadByMember[link.memberId]
                val unread = thread?.trainerUnreadCount ?: 0
                val lastMsg = thread?.lastMessage?.takeIf { it.isNotBlank() }
                    ?: "Tap to start a conversation"
                val timeLabel = thread?.lastMessageAtMs?.let { ManilaDateTime.formatRelativeDay(it, fallback = "") }?.takeIf { it.isNotBlank() }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMemberChat(link.memberId) }
                        .testTag("trainer_chat_thread_${link.memberId}"),
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
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(FlameOrange.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = link.memberName.split(" ").take(2).joinToString("") { it.take(1) }.ifBlank { "M" },
                                fontWeight = FontWeight.Black,
                                color = FlameOrange
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = link.memberName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (timeLabel != null) {
                                    Text(
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (unread > 0) {
                            Surface(
                                shape = CircleShape,
                                color = BoldRed
                            ) {
                                Text(
                                    text = unread.toString(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Open Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
