package com.example.ui.customworkout

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.ExerciseEntity
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomWorkoutBuilderScreen(
    viewModel: CustomWorkoutViewModel,
    onNavigateBack: () -> Unit,
    onPlanCreated: (String) -> Unit
) {
    val planName by viewModel.planName.collectAsStateWithLifecycle()
    val planDescription by viewModel.planDescription.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedDifficulty by viewModel.selectedDifficulty.collectAsStateWithLifecycle()
    val selectedExercises by viewModel.selectedExercises.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val allExercises by viewModel.allExercises.collectAsStateWithLifecycle(emptyList())

    var showExercisePicker by remember { mutableStateOf(false) }

    BackHandler { onNavigateBack() }

    val categories = listOf(
        "Full Body",
        "Abs",
        "Core",
        "Chest",
        "Back",
        "Shoulders",
        "Biceps",
        "Triceps",
        "Forearms",
        "Arms",
        "Quadriceps (Front Thighs)",
        "Hamstrings (Back Thighs)",
        "Glutes",
        "Calves",
        "Legs",
        "Hip Flexors",
        "Obliques",
        "Lower Back",
        "Traps (Trapezius)",
        "Lats (Latissimus Dorsi)",
        "Cardio",
        "Mobility",
        "Flexibility",
        "Balance",
        "Stability"
    )
    val difficulties = listOf("Beginner", "Intermediate", "Advanced")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Custom Workout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { showExercisePicker = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("add_exercise_to_plan_button"),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Exercise", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            viewModel.savePlan { planId ->
                                onPlanCreated(planId)
                            }
                        },
                        enabled = planName.isNotBlank() && selectedExercises.isNotEmpty() && !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("save_custom_plan_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Plan", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Name & Description
                OutlinedTextField(
                    value = planName,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("Workout Name *") },
                    placeholder = { Text("e.g., Morning HIIT Core") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_plan_name_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = planDescription,
                    onValueChange = { viewModel.updateDescription(it) },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Brief notes on goals or focus areas") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_plan_desc_input"),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Category Chips (All 25 Target Categories)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Target Category (${categories.size} options)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(selectedCategory, style = MaterialTheme.typography.labelMedium, color = FlameOrange, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories.size) { i ->
                        val cat = categories[i]
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { viewModel.updateCategory(cat) },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FlameOrange,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Difficulty
            item {
                Text("Difficulty", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    difficulties.forEach { diff ->
                        FilterChip(
                            selected = selectedDifficulty == diff,
                            onClick = { viewModel.updateDifficulty(diff) },
                            label = { Text(diff) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyanDark,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Exercises Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Exercises (${selectedExercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (selectedExercises.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showExercisePicker = true }
                            .testTag("empty_exercise_prompt"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No exercises added yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Tap here to pick exercises from the library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(selectedExercises) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Order number badge
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(FlameOrange.copy(alpha = 0.15f), CircleShape)
                                ) {
                                    Text("${index + 1}", fontWeight = FontWeight.Bold, color = FlameOrange)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.exercise.targetMuscles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Up/Down Reordering buttons
                                IconButton(
                                    onClick = { viewModel.moveExerciseUp(index) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                                }
                                IconButton(
                                    onClick = { viewModel.moveExerciseDown(index) },
                                    enabled = index < selectedExercises.size - 1,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                                }

                                IconButton(
                                    onClick = { viewModel.removeExercise(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = DangerRed)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Controls for Reps / Duration and Rest (FlowRow ensures no overflow on narrow 320dp screens)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Type Toggle (REPS vs TIME)
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "REPS",
                                        modifier = Modifier
                                            .clickable { viewModel.updateExerciseType(index, "REPS") }
                                            .background(if (item.type == "REPS") FlameOrange else Color.Transparent)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = if (item.type == "REPS") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "TIME",
                                        modifier = Modifier
                                            .clickable { viewModel.updateExerciseType(index, "TIME") }
                                            .background(if (item.type == "TIME") FlameOrange else Color.Transparent)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = if (item.type == "TIME") Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Stepper for count
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.updateExerciseCount(index, item.count - if (item.type == "TIME") 5 else 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.RemoveCircleOutline, contentDescription = "Decrease")
                                    }
                                    Text(
                                        text = if (item.type == "TIME") "${item.count}s" else "x${item.count}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { viewModel.updateExerciseCount(index, item.count + if (item.type == "TIME") 5 else 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Increase")
                                    }
                                }

                                // Rest pill
                                AssistChip(
                                    onClick = {
                                        val nextRest = when (item.restSeconds) {
                                            15 -> 20
                                            20 -> 30
                                            30 -> 10
                                            else -> 15
                                        }
                                        viewModel.updateExerciseRest(index, nextRest)
                                    },
                                    label = { Text("Rest: ${item.restSeconds}s") }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Modal Sheet to pick exercises
    if (showExercisePicker) {
        ModalBottomSheet(
            onDismissRequest = { showExercisePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Select Exercises",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(allExercises.size) { idx ->
                        val ex = allExercises[idx]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addExercise(ex)
                                    showExercisePicker = false
                                }
                                .testTag("picker_exercise_${ex.id}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ExerciseVisualizer(
                                    animationType = ex.animationType,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    isPaused = true
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ex.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(ex.targetMuscles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = FlameOrange)
                            }
                        }
                    }
                }
            }
        }
    }
}
