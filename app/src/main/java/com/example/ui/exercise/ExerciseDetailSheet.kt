package com.example.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ExerciseEntity
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailSheet(
    exercise: ExerciseEntity,
    isFemaleBodyDiagram: Boolean = false,
    isFemaleExerciseDrawing: Boolean = false,
    onDismiss: () -> Unit,
    onToggleFavorite: (ExerciseEntity) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Header: Name and Favorite Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = exercise.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { onToggleFavorite(exercise) },
                    modifier = Modifier.testTag("exercise_favorite_button")
                ) {
                    Icon(
                        if (exercise.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (exercise.isFavorite) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Animated Visualizer
            ExerciseVisualizer(
                animationType = exercise.animationType,
                exerciseId = exercise.id,
                isFemale = isFemaleExerciseDrawing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                isPaused = false
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual Target Muscle Indicator (Human Body Map for Beginners)
            com.example.ui.components.BodyMuscleVisualizer(
                primaryMuscles = exercise.primaryMuscles.ifEmpty { exercise.targetMuscles },
                secondaryMuscles = exercise.secondaryMuscles,
                isFemale = isFemaleBodyDiagram,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Target Muscles & Educational Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Target Muscles & Anatomy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "PRIMARY DRIVERS:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = FlameOrange
                    )
                    Text(
                        text = exercise.primaryMuscles.ifEmpty { exercise.targetMuscles },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SECONDARY STABILIZERS:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = ElectricCyan
                    )
                    Text(
                        text = exercise.secondaryMuscles.ifEmpty { "Core stabilizers, kinetic chain balance" },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "EDUCATIONAL BIOMECHANICS:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = exercise.educationalDescription.ifEmpty {
                            "A functional closed-kinetic-chain movement that promotes neuromuscular coordination and sustained muscle fiber recruitment."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (exercise.functionalBenefit.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "FUNCTIONAL REAL-WORLD BENEFIT:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = SuccessGreen
                        )
                        Text(
                            text = exercise.functionalBenefit,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Posture Guidance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Posture Guidance", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = exercise.tips,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step-by-Step Instructions Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Step-by-Step Instructions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = exercise.instructions,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlameOrange)
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        }
    }
}
