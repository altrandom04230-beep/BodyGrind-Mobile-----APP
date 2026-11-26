package com.example.ui.workouts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlanDetailScreen(
    plan: WorkoutPlanEntity,
    planExercises: List<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>,
    onNavigateBack: () -> Unit,
    onStartWorkout: (String) -> Unit,
    onExerciseClick: (ExerciseEntity) -> Unit,
    onDeleteCustomPlan: (() -> Unit)? = null
) {
    BackHandler { onNavigateBack() }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Custom Plan?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this custom workout plan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCustomPlan?.invoke()
                    }
                ) {
                    Text("Delete", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(plan.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("plan_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (plan.isCustom && onDeleteCustomPlan != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Plan", tint = DangerRed)
                        }
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
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { onStartWorkout(plan.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("start_workout_fab"),
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("START WORKOUT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                // Hero Banner Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(plan.category, fontWeight = FontWeight.SemiBold) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = FlameOrange.copy(alpha = 0.15f), labelColor = FlameOrange)
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text(plan.difficulty) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = ElectricCyanDark.copy(alpha = 0.15f), labelColor = ElectricCyanDark)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = plan.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = plan.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!plan.assignedByTrainerName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BoldRed.copy(alpha = 0.10f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Assigned by Coach ${plan.assignedByTrainerName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = BoldRed
                                        )
                                        Text(
                                            text = if (plan.isCompletedByMember) "Completed" else "Pending",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (plan.isCompletedByMember) SuccessGreen else FlameOrange
                                        )
                                    }
                                    if (!plan.trainerNote.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Coach Note: ${plan.trainerNote}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Timer, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${plan.estimatedDurationMinutes} mins", fontWeight = FontWeight.Bold)
                                }
                                Text("Duration", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${planExercises.size} exercises", fontWeight = FontWeight.Bold)
                                }
                                Text("Workout Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "ANATOMICAL FOCUS:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = FlameOrange
                        )
                        Text(
                            text = plan.targetMusclesDescription.ifEmpty { "${plan.category} muscle groups & core stabilizers" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "EDUCATIONAL OVERVIEW & PHYSIOLOGY:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = ElectricCyan
                        )
                        Text(
                            text = plan.educationalOverview.ifEmpty {
                                "Structured circuit combining compound bodyweight motor patterns to optimize neuromuscular adaptation, joint mobility, and caloric afterburn without joint strain."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Exercise Routine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            itemsIndexed(planExercises) { index, (planEx, ex) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExerciseClick(ex) }
                        .testTag("plan_exercise_row_$index"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Order Index
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .background(FlameOrange.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, color = FlameOrange, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Exercise Visualizer Thumbnail
                        ExerciseVisualizer(
                            animationType = ex.animationType,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            isPaused = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ex.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (planEx.type == "TIME") "${planEx.count}s" else "x${planEx.count} reps",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = FlameOrange
                            )
                            Text(
                                text = ex.targetMuscles,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = "View Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
