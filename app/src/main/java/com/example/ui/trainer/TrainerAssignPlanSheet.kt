package com.example.ui.trainer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.coaching.AssignedPlanExercisePayload
import com.example.data.coaching.TrainerMemberLink
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.controlBorder
import com.example.utils.ManilaDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerAssignPlanSheet(
    member: TrainerMemberLink,
    availablePlans: List<WorkoutPlanEntity>,
    availableExercises: List<ExerciseEntity>,
    isBusy: Boolean,
    loadPlanExercises: suspend (String) -> List<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>,
    onDismiss: () -> Unit,
    onAssign: (
        planTitle: String,
        planDescription: String,
        category: String,
        difficulty: String,
        estimatedDurationMinutes: Int,
        note: String?,
        startDateEpochMs: Long,
        exercises: List<AssignedPlanExercisePayload>
    ) -> Unit
) {
    val scope = rememberCoroutineScope()
    var useExistingPlan by remember { mutableStateOf(true) }
    var selectedTemplatePlanId by remember { mutableStateOf<String?>(null) }
    var planTitle by remember { mutableStateOf("") }
    var planDescription by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Full Body") }
    var difficulty by remember { mutableStateOf("Intermediate") }
    var note by remember { mutableStateOf("") }
    var startDateEpochMs by remember { mutableStateOf(manilaStartOfDayOffsetMs(0)) }
    var showDatePicker by remember { mutableStateOf(false) }

    var selectedExercises by remember { mutableStateOf<List<AssignedPlanExercisePayload>>(emptyList()) }
    var exerciseSearch by remember { mutableStateOf("") }

    val filteredLibraryExercises = remember(availableExercises, exerciseSearch) {
        val q = exerciseSearch.trim()
        if (q.isEmpty()) availableExercises.take(25)
        else availableExercises.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.targetMuscles.contains(q, ignoreCase = true)
        }.take(25)
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = ManilaDateTime.utcMillisToDatePickerUtcMs(startDateEpochMs)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            startDateEpochMs = ManilaDateTime.datePickerUtcMsToManilaStartOfDayUtcMs(millis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Set Date")
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Text(
                    text = "Assign Plan to ${member.memberName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Choose an existing routine or build one from the exercise library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = useExistingPlan,
                        onClick = { useExistingPlan = true },
                        label = { Text("From Existing Plans") },
                        modifier = Modifier.weight(1f).testTag("assign_mode_template")
                    )
                    FilterChip(
                        selected = !useExistingPlan,
                        onClick = { useExistingPlan = false },
                        label = { Text("Pick Exercises") },
                        modifier = Modifier.weight(1f).testTag("assign_mode_custom")
                    )
                }
            }

            if (useExistingPlan) {
                item {
                    Text(
                        text = "Select Workout Plan Template",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(availablePlans, key = { it.id }) { plan ->
                            val selected = selectedTemplatePlanId == plan.id
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedTemplatePlanId = plan.id
                                    planTitle = plan.name
                                    planDescription = plan.description
                                    category = plan.category
                                    difficulty = plan.difficulty
                                    scope.launch {
                                        val loaded = loadPlanExercises(plan.id)
                                        selectedExercises = loaded.map { (pe, ex) ->
                                            AssignedPlanExercisePayload(
                                                exerciseId = ex.id,
                                                exerciseName = ex.name,
                                                type = pe.type,
                                                count = pe.count,
                                                restSeconds = pe.restSeconds
                                            )
                                        }
                                    }
                                },
                                label = {
                                    Text(
                                        text = "${plan.name} (${plan.estimatedDurationMinutes}m)",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.testTag("assign_template_chip_${plan.id}")
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = planTitle,
                    onValueChange = { planTitle = it },
                    label = { Text("Plan Title") },
                    placeholder = { Text("e.g., Upper Body Strength Day") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FlameOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("assign_plan_title_input")
                )
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Coach Note (Optional)") },
                    placeholder = { Text("Focus on controlled reps and 90s rest...") },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FlameOrange,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("assign_plan_note_input")
                )
            }

            // Start Date selector
            item {
                Text(
                    text = "Start Date (Manila): ${ManilaDateTime.formatDate(startDateEpochMs)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val todayMs = manilaStartOfDayOffsetMs(0)
                    val tomorrowMs = manilaStartOfDayOffsetMs(1)
                    FilterChip(
                        selected = ManilaDateTime.isSameManilaDay(startDateEpochMs, todayMs),
                        onClick = { startDateEpochMs = todayMs },
                        label = { Text("Today") }
                    )
                    FilterChip(
                        selected = ManilaDateTime.isSameManilaDay(startDateEpochMs, tomorrowMs),
                        onClick = { startDateEpochMs = tomorrowMs },
                        label = { Text("Tomorrow") }
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.testTag("assign_pick_date_button")
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Date")
                    }
                }
            }

            // Exercise library picker
            if (!useExistingPlan || selectedExercises.isEmpty()) {
                item {
                    OutlinedTextField(
                        value = exerciseSearch,
                        onValueChange = { exerciseSearch = it },
                        label = { Text("Search Exercise Library") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("assign_exercise_search_input")
                    )
                }

                items(filteredLibraryExercises, key = { "lib_${it.id}" }) { ex ->
                    val alreadyAdded = selectedExercises.any { it.exerciseId == ex.id }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (alreadyAdded) BorderStroke(1.dp, SuccessGreen) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!alreadyAdded) {
                                    selectedExercises = selectedExercises + AssignedPlanExercisePayload(
                                        exerciseId = ex.id,
                                        exerciseName = ex.name,
                                        type = ex.defaultType,
                                        count = ex.defaultCount,
                                        restSeconds = 30
                                    )
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${ex.category} • ${ex.defaultCount} ${if (ex.defaultType == "TIME") "sec" else "reps"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (alreadyAdded) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                contentDescription = if (alreadyAdded) "Added" else "Add Exercise",
                                tint = if (alreadyAdded) SuccessGreen else FlameOrange
                            )
                        }
                    }
                }
            }

            // Selected exercises preview
            if (selectedExercises.isNotEmpty()) {
                item {
                    Text(
                        text = "Included Exercises (${selectedExercises.size})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(selectedExercises.indices.toList(), key = { idx -> "sel_${idx}_${selectedExercises[idx].exerciseId}" }) { idx ->
                    val item = selectedExercises[idx]
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${idx + 1}. ${item.exerciseName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${item.count} ${if (item.type == "TIME") "sec" else "reps"} • ${item.restSeconds}s rest",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedExercises = selectedExercises.toMutableList().apply { removeAt(idx) }
                                }
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove exercise")
                            }
                        }
                    }
                }
            }

            item {
                val estimatedMins = (selectedExercises.size * 3).coerceIn(10, 90)
                Button(
                    onClick = {
                        onAssign(
                            planTitle.trim().ifBlank { "Coach Routine" },
                            planDescription.trim().ifBlank { "Assigned by your BodyGrind Coach" },
                            category,
                            difficulty,
                            estimatedMins,
                            note.trim().takeIf { it.isNotBlank() },
                            startDateEpochMs,
                            selectedExercises
                        )
                    },
                    enabled = !isBusy && selectedExercises.isNotEmpty() && planTitle.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("assign_plan_submit_button")
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Assign Plan to ${member.memberName}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun manilaStartOfDayOffsetMs(offsetDays: Int): Long {
    val manilaDate = ManilaDateTime.todayManila().plusDays(offsetDays.toLong())
    return ManilaDateTime.manilaDateToStartOfDayUtcMs(manilaDate)
}
