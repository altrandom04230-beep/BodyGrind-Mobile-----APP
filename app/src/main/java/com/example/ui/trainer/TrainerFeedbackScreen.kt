package com.example.ui.trainer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.trainer.AssignedMember
import com.example.data.trainer.FeedbackCategory
import com.example.data.trainer.TrainerFeedbackEntry
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.controlBorder

@Composable
fun TrainerFeedbackScreen(
    members: List<AssignedMember>,
    selectedMember: AssignedMember?,
    feedbackList: List<TrainerFeedbackEntry>,
    onSelectMember: (String) -> Unit,
    onSubmitFeedback: (memberId: String, category: FeedbackCategory, message: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val activeMember = selectedMember ?: members.firstOrNull()
    var selectedCategory by remember { mutableStateOf(FeedbackCategory.WORKOUT_FEEDBACK) }
    var messageInput by remember { mutableStateOf("") }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }

    val memberFeedback = remember(feedbackList, activeMember?.id) {
        if (activeMember == null) feedbackList else feedbackList.filter { it.memberId == activeMember.id }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Trainer Feedback",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }

        // Quick Member Search Bar
        item {
            TrainerMemberSearchBar(
                members = members,
                onMemberSelected = { memberId ->
                    onSelectMember(memberId)
                    confirmationMessage = null
                },
                placeholderText = "Search member to give feedback..."
            )
        }

        // Member Selector
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members, key = { it.id }) { member ->
                    FilterChip(
                        selected = member.id == activeMember?.id,
                        onClick = {
                            onSelectMember(member.id)
                            confirmationMessage = null
                        },
                        label = { Text(member.name) },
                        modifier = Modifier.testTag("feedback_member_chip_${member.id}")
                    )
                }
            }
        }

        // Feedback Composer Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "New Feedback for ${activeMember?.name ?: "Member"}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FeedbackCategory.entries) { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.label) },
                                modifier = Modifier.testTag("feedback_category_${category.name.lowercase()}")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = messageInput,
                        onValueChange = {
                            messageInput = it
                            confirmationMessage = null
                        },
                        label = { Text("Write ${selectedCategory.label.lowercase()}...") },
                        minLines = 3,
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("trainer_feedback_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (confirmationMessage != null) {
                        Text(
                            text = confirmationMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            if (activeMember != null && messageInput.isNotBlank()) {
                                focusManager.clearFocus()
                                onSubmitFeedback(activeMember.id, selectedCategory, messageInput)
                                messageInput = ""
                                confirmationMessage = "Feedback saved in memory for ${activeMember.name}."
                            }
                        },
                        enabled = activeMember != null && messageInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_trainer_feedback_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Feedback", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Saved In-Memory Feedback History
        item {
            Text(
                text = "Feedback History (${memberFeedback.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (memberFeedback.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "No feedback recorded yet for ${activeMember?.name ?: "this member"}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(memberFeedback, key = { it.id }) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = entry.category.label,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                text = entry.timestampLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = entry.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
