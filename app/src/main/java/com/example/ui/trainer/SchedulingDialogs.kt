package com.example.ui.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.coaching.TrainerMemberLink
import com.example.data.scheduling.AvailabilityDateOverride
import com.example.data.scheduling.SchedulingValidator
import com.example.ui.theme.BoldRed
import com.example.ui.theme.DangerRed
import com.example.utils.ManilaDateTime
import java.time.LocalDate

/**
 * Reusable cancellation confirmation dialog requiring a reason.
 * Separate from delete; notifies affected trainer and admin via in-app, push, and email.
 */
@Composable
fun CancelSessionReasonDialog(
    targetTitle: String,
    scheduledDateLabel: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmCancel: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                tint = DangerRed
            )
        },
        title = {
            Text(
                text = "Cancel Scheduled Session",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "\"$targetTitle\" ($scheduledDateLabel) will be marked as Cancelled — not deleted and never counted as completed or billed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Both the affected trainer and admin will be notified via in-app alert, push notification, and email.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        validationError = null
                    },
                    label = { Text("Cancellation Reason (Required)") },
                    placeholder = { Text("e.g., Trainer illness, client requested reschedule...") },
                    minLines = 2,
                    maxLines = 4,
                    isError = validationError != null,
                    supportingText = validationError?.let { err -> { Text(err, color = DangerRed) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val err = SchedulingValidator.validateCancellationReason(reason)
                    if (err != null) {
                        validationError = err
                    } else {
                        onConfirmCancel(reason.trim())
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                modifier = Modifier.testTag("confirm_cancel_session_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Confirm Cancellation", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isBusy
            ) {
                Text("Back")
            }
        }
    )
}

/**
 * Confirmation dialog for permanently deleting a trainer-assigned workout plan.
 */
@Composable
fun DeleteAssignedPlanDialog(
    planTitle: String,
    memberName: String?,
    isDeleting: Boolean,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    val safePlanTitle = planTitle.trim().ifBlank { "Assigned Plan" }
    val safeMemberName = memberName?.trim()?.takeIf { it.isNotBlank() } ?: "this member"

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !isDeleting,
            dismissOnClickOutside = !isDeleting
        ),
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Delete this plan?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "\"$safePlanTitle\" will be removed from your list and $safeMemberName's app. The admin keeps a record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("delete_assigned_plan_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_assigned_plan_button")
            ) {
                Text(
                    text = if (isDeleting) "Deleting…" else "Delete",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("dismiss_delete_assigned_plan_button")
            ) {
                Text("Back")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDateOverrideDialog(
    onDismiss: () -> Unit,
    onConfirm: (AvailabilityDateOverride) -> Unit
) {
    var selectedDate by remember { mutableStateOf(ManilaDateTime.todayManila().plusDays(1)) }
    var isAvailable by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("15:00") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Date Override", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Override weekly availability for a specific Asia/Manila date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("override_date_picker_button")
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: ${ManilaDateTime.formatDate(selectedDate)}", fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAvailable) "Custom Hours" else "Blocked / Unavailable",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isAvailable) "Trainer is available for custom times" else "Trainer is unavailable all day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isAvailable,
                        onCheckedChange = { isAvailable = it; errorMsg = null }
                    )
                }

                if (isAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it; errorMsg = null },
                            label = { Text("Start (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it; errorMsg = null },
                            label = { Text("End (HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("e.g., Holiday, Seminar, Half-day") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isAvailable && !SchedulingValidator.isValidTimeSlot(startTime, endTime)) {
                        errorMsg = "Enter valid 24-hour times (HH:mm) with start before end."
                    } else {
                        onConfirm(
                            AvailabilityDateOverride(
                                dateKey = ManilaDateTime.toDateKey(selectedDate),
                                isAvailable = isAvailable,
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                note = note.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                modifier = Modifier.testTag("confirm_add_override_button")
            ) {
                Text("Add Override", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ManilaDateTime.manilaDateToDatePickerUtcMs(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDate = ManilaDateTime.datePickerUtcMsToManilaDate(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduledSessionDialog(
    linkedMembers: List<TrainerMemberLink>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        memberId: String,
        memberName: String,
        title: String,
        notes: String,
        scheduledDate: LocalDate,
        startTime: String,
        endTime: String
    ) -> Unit
) {
    var selectedMember by remember(linkedMembers) { mutableStateOf(linkedMembers.firstOrNull()) }
    var memberDropdownExpanded by remember { mutableStateOf(false) }
    var manualMemberId by remember { mutableStateOf("") }
    var manualMemberName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(ManilaDateTime.todayManila()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:00") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Coaching Session", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (linkedMembers.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = memberDropdownExpanded,
                        onExpandedChange = { memberDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.memberName?.ifBlank { selectedMember?.memberId.orEmpty() } ?: "Select member",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Member") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = memberDropdownExpanded,
                            onDismissRequest = { memberDropdownExpanded = false }
                        ) {
                            linkedMembers.forEach { link ->
                                DropdownMenuItem(
                                    text = { Text(link.memberName.ifBlank { link.memberId }) },
                                    onClick = {
                                        selectedMember = link
                                        memberDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = manualMemberId,
                        onValueChange = { manualMemberId = it.trim(); errorMsg = null },
                        label = { Text("Member User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualMemberName,
                        onValueChange = { manualMemberName = it; errorMsg = null },
                        label = { Text("Member Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("Session Title") },
                    placeholder = { Text("e.g., 1-on-1 Strength Assessment") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_session_title_input")
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_session_date_button")
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date (Manila): ${ManilaDateTime.formatDate(selectedDate)}", fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it; errorMsg = null },
                        label = { Text("Start (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it; errorMsg = null },
                        label = { Text("End (HH:mm)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg.orEmpty(),
                        color = DangerRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mId = selectedMember?.memberId ?: manualMemberId.trim()
                    val mName = selectedMember?.memberName?.ifBlank { "Member" } ?: manualMemberName.trim().ifBlank { "Member" }
                    when {
                        mId.isBlank() -> errorMsg = "Please select or enter a member."
                        title.trim().isEmpty() -> errorMsg = "Session title is required."
                        !SchedulingValidator.isValidTimeSlot(startTime, endTime) ->
                            errorMsg = "Enter valid 24-hour times (HH:mm) with start before end."
                        else -> onCreate(mId, mName, title.trim(), notes.trim(), selectedDate, startTime.trim(), endTime.trim())
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                modifier = Modifier.testTag("confirm_create_session_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("Schedule", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ManilaDateTime.manilaDateToDatePickerUtcMs(selectedDate)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            selectedDate = ManilaDateTime.datePickerUtcMsToManilaDate(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
