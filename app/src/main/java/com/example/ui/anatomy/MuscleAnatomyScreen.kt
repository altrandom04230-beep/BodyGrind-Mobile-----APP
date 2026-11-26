package com.example.ui.anatomy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.ExerciseEntity
import com.example.ui.components.BodyMuscleVisualizer
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.components.MuscleGroup
import com.example.ui.theme.BoldRed
import com.example.ui.theme.controlBorder

private data class MuscleAnatomyInfo(
    val primaryQuery: String,
    val secondaryQuery: String,
    val biomechanicsNote: String,
    val matchKeywords: List<String>
)

private val muscleInfoMap: Map<MuscleGroup, MuscleAnatomyInfo> = mapOf(
    MuscleGroup.CHEST to MuscleAnatomyInfo(
        primaryQuery = "chest, pectorals",
        secondaryQuery = "front delt, tricep",
        biomechanicsNote = "Pectoralis major & minor drive horizontal adduction and pushing movements such as push-ups and chest dips.",
        matchKeywords = listOf("chest", "pec", "push-up", "pushup", "dip")
    ),
    MuscleGroup.ABS to MuscleAnatomyInfo(
        primaryQuery = "abs, abdominals",
        secondaryQuery = "oblique, lower back",
        biomechanicsNote = "Rectus abdominis stabilizes the spine and flexes the trunk during crunches, leg raises, and isometric planks.",
        matchKeywords = listOf("ab", "abs", "core", "crunch", "plank", "leg raise", "situp")
    ),
    MuscleGroup.OBLIQUES to MuscleAnatomyInfo(
        primaryQuery = "obliques",
        secondaryQuery = "abs, core",
        biomechanicsNote = "Internal and external obliques control torso rotation, lateral flexion, and anti-rotation core stability.",
        matchKeywords = listOf("oblique", "bicycle", "twist", "side plank", "core")
    ),
    MuscleGroup.SHOULDERS_FRONT to MuscleAnatomyInfo(
        primaryQuery = "front delt, shoulder",
        secondaryQuery = "chest, tricep",
        biomechanicsNote = "Anterior deltoids elevate and flex the shoulder joint during pike push-ups, arm circles, and overhead presses.",
        matchKeywords = listOf("shoulder", "delt", "arm circle", "push-up", "pike")
    ),
    MuscleGroup.BICEPS to MuscleAnatomyInfo(
        primaryQuery = "biceps",
        secondaryQuery = "forearm, lat",
        biomechanicsNote = "Biceps brachii flex the elbow and supinate the forearm across pulling and isometric hold movements.",
        matchKeywords = listOf("bicep", "arm", "curl", "pull")
    ),
    MuscleGroup.FOREARMS_FRONT to MuscleAnatomyInfo(
        primaryQuery = "forearm",
        secondaryQuery = "bicep, tricep",
        biomechanicsNote = "Forearm flexors and extensors stabilize the wrist and grip during floor pressing and plank holds.",
        matchKeywords = listOf("forearm", "wrist", "plank", "push-up", "dip")
    ),
    MuscleGroup.QUADS to MuscleAnatomyInfo(
        primaryQuery = "quads, quadriceps",
        secondaryQuery = "glute, calf",
        biomechanicsNote = "Quadriceps femoris extend the knee and power squats, lunges, wall sits, and explosive jumps.",
        matchKeywords = listOf("quad", "squat", "lunge", "wall sit", "jump", "high knees")
    ),
    MuscleGroup.CALVES_FRONT to MuscleAnatomyInfo(
        primaryQuery = "tibialis, calf",
        secondaryQuery = "quad",
        biomechanicsNote = "Tibialis anterior and anterior lower leg stabilizers absorb landing impact and control ankle dorsiflexion.",
        matchKeywords = listOf("calf", "calves", "jumping jack", "high knees", "jump")
    ),
    MuscleGroup.TRAPS to MuscleAnatomyInfo(
        primaryQuery = "traps, trapezius",
        secondaryQuery = "rear delt, lower back",
        biomechanicsNote = "Upper, middle, and lower trapezius elevate, retract, and stabilize the scapulae for healthy posture.",
        matchKeywords = listOf("trap", "back", "cobra", "plank", "arm circle")
    ),
    MuscleGroup.LATS to MuscleAnatomyInfo(
        primaryQuery = "lats, latissimus",
        secondaryQuery = "bicep, trap",
        biomechanicsNote = "Latissimus dorsi extend and adduct the shoulder while bracing the thoracic spine.",
        matchKeywords = listOf("lat", "back", "cobra", "pull")
    ),
    MuscleGroup.LOWER_BACK to MuscleAnatomyInfo(
        primaryQuery = "lower back, lumbar",
        secondaryQuery = "glute, abs",
        biomechanicsNote = "Erector spinae extend the lumbar spine and protect trunk alignment during bridges, cobra stretches, and squats.",
        matchKeywords = listOf("lower back", "lumbar", "cobra", "bridge", "plank")
    ),
    MuscleGroup.SHOULDERS_BACK to MuscleAnatomyInfo(
        primaryQuery = "rear delt",
        secondaryQuery = "trap, tricep",
        biomechanicsNote = "Posterior deltoids externally rotate and horizontally abduct the shoulder to balance anterior pressing volume.",
        matchKeywords = listOf("rear delt", "shoulder", "arm circle", "cobra")
    ),
    MuscleGroup.TRICEPS to MuscleAnatomyInfo(
        primaryQuery = "triceps",
        secondaryQuery = "chest, shoulder",
        biomechanicsNote = "Triceps brachii drive elbow extension in chair dips, diamond push-ups, and standard push-ups.",
        matchKeywords = listOf("tricep", "dip", "diamond", "push-up", "pushup")
    ),
    MuscleGroup.FOREARMS_BACK to MuscleAnatomyInfo(
        primaryQuery = "forearm",
        secondaryQuery = "tricep",
        biomechanicsNote = "Posterior forearm extensors keep wrist alignment neutral during weight-bearing floor exercises.",
        matchKeywords = listOf("forearm", "dip", "push-up", "plank")
    ),
    MuscleGroup.GLUTES to MuscleAnatomyInfo(
        primaryQuery = "glutes",
        secondaryQuery = "hamstring, lower back",
        biomechanicsNote = "Gluteus maximus & medius drive hip extension, pelvic stability, bridges, squats, and lunges.",
        matchKeywords = listOf("glute", "bridge", "squat", "lunge", "wall sit")
    ),
    MuscleGroup.HAMSTRINGS to MuscleAnatomyInfo(
        primaryQuery = "hamstrings",
        secondaryQuery = "glute, calf",
        biomechanicsNote = "Hamstrings flex the knee and assist hip extension during bridges, lunges, and deceleration.",
        matchKeywords = listOf("hamstring", "bridge", "lunge", "squat")
    ),
    MuscleGroup.CALVES_BACK to MuscleAnatomyInfo(
        primaryQuery = "calves",
        secondaryQuery = "quad, hamstring",
        biomechanicsNote = "Gastrocnemius and soleus plantarflex the ankle for jumping jacks, high knees, and running power.",
        matchKeywords = listOf("calf", "calves", "jumping", "high knees", "lunge")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MuscleAnatomyScreen(
    allExercises: List<ExerciseEntity>,
    initialIsFemale: Boolean = false,
    onSelectExercise: (ExerciseEntity) -> Unit,
    onNavigateBack: () -> Unit
) {
    var isFemale by remember(initialIsFemale) { mutableStateOf(initialIsFemale) }
    var selectedMuscle by remember { mutableStateOf(MuscleGroup.CHEST) }
    var viewFilter by remember { mutableStateOf("All") } // "All", "Anterior", "Posterior"

    BackHandler {
        onNavigateBack()
    }

    val currentInfo = muscleInfoMap[selectedMuscle] ?: MuscleAnatomyInfo(
        primaryQuery = selectedMuscle.displayName,
        secondaryQuery = "core",
        biomechanicsNote = "Targets ${selectedMuscle.displayName} during functional bodyweight training.",
        matchKeywords = listOf(selectedMuscle.displayName.lowercase())
    )

    val displayedMuscles = remember(viewFilter) {
        when (viewFilter) {
            "Anterior" -> MuscleGroup.entries.filter { it.isFront }
            "Posterior" -> MuscleGroup.entries.filter { !it.isFront }
            else -> MuscleGroup.entries
        }
    }

    val matchingExercises = remember(allExercises, selectedMuscle) {
        allExercises.filter { ex ->
            val combined = "${ex.name} ${ex.targetMuscles} ${ex.primaryMuscles} ${ex.secondaryMuscles} ${ex.category}".lowercase()
            currentInfo.matchKeywords.any { kw -> combined.contains(kw) }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.muscle_anatomy_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = stringResource(R.string.muscle_anatomy_badge),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("muscle_anatomy_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("muscle_anatomy_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
        ) {
            // Male / Female Muscular System Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(false to "Male muscular system", true to "Female muscular system").forEach { (femaleOption, label) ->
                        val isSelected = isFemale == femaleOption
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.controlBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clickable { isFemale = femaleOption }
                                .testTag(if (femaleOption) "anatomy_toggle_female" else "anatomy_toggle_male")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Interactive 3D Muscle Anatomy Card
            item {
                BodyMuscleVisualizer(
                    primaryMuscles = currentInfo.primaryQuery,
                    secondaryMuscles = currentInfo.secondaryQuery,
                    isFemale = isFemale,
                    showTargetMusclesSection = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Target Muscles Header + Orientation Filter
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.muscle_anatomy_target_muscles),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("All", "Anterior", "Posterior").forEach { filter ->
                                val selected = viewFilter == filter
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewFilter = filter },
                                    label = {
                                        Text(
                                            text = filter,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedMuscles, key = { it.name }) { group ->
                            val isSelected = selectedMuscle == group
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedMuscle = group },
                                label = {
                                    Text(
                                        text = group.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.AccessibilityNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BoldRed.copy(alpha = 0.18f),
                                    selectedLabelColor = BoldRed,
                                    selectedLeadingIconColor = BoldRed
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("anatomy_muscle_chip_${group.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Selected Muscle Biomechanical Breakdown Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(BoldRed.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FitnessCenter,
                                    contentDescription = null,
                                    tint = BoldRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedMuscle.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (selectedMuscle.isFront) "Anterior muscular group" else "Posterior muscular group",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = currentInfo.biomechanicsNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Exercises Targeting Selected Muscle
            item {
                Text(
                    text = "Exercises (${matchingExercises.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (matchingExercises.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.FitnessCenter,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Select another muscle group to inspect matching bodyweight exercises.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(matchingExercises, key = { it.id }) { ex ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectExercise(ex) }
                            .testTag("anatomy_exercise_${ex.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseVisualizer(
                                animationType = ex.animationType,
                                exerciseId = ex.id,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                isPaused = true
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ex.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = ex.primaryMuscles.ifEmpty { ex.targetMuscles },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${ex.difficulty} • ${ex.category}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BoldRed
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "View Exercise",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
