package com.example.ui.trainer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.example.data.trainer.AssignedMember

/**
 * Shared, reusable search bar for Trainer screens (Progress, Feedback)
 * allowing trainers to quickly filter and select an assigned member by name.
 */
@Composable
fun TrainerMemberSearchBar(
    members: List<AssignedMember>,
    onMemberSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholderText: String = "Search member by name..."
) {
    val focusManager = LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownVisible by remember { mutableStateOf(false) }

    val filteredMembers = remember(searchQuery, members) {
        val query = searchQuery.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            members.filter { it.name.contains(query, ignoreCase = true) }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                isDropdownVisible = it.isNotBlank()
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trainer_member_search_input"),
            placeholder = {
                Text(
                    text = placeholderText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            searchQuery = ""
                            isDropdownVisible = false
                        },
                        modifier = Modifier.testTag("trainer_member_search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    isDropdownVisible = false
                }
            )
        )

        DropdownMenu(
            expanded = isDropdownVisible && searchQuery.isNotBlank(),
            onDismissRequest = { isDropdownVisible = false },
            properties = PopupProperties(focusable = false),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (filteredMembers.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No members found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { /* No action */ },
                    enabled = false
                )
            } else {
                filteredMembers.forEach { member ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = member.name.split(" ").take(2).joinToString("") { it.take(1) },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${member.fitnessGoal} • ${member.status.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${member.workoutCompletionRate}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        onClick = {
                            onMemberSelected(member.id)
                            searchQuery = member.name
                            isDropdownVisible = false
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.testTag("search_result_member_${member.id}")
                    )
                }
            }
        }
    }
}
