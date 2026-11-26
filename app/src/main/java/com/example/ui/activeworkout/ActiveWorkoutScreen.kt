package com.example.ui.activeworkout

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveWorkoutScreen(
    viewModel: ActiveWorkoutViewModel,
    onFinish: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val exercisesList by viewModel.exercisesList.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val isCameraSupported by viewModel.isCameraSupported.collectAsStateWithLifecycle()
    val isCameraOn by viewModel.isCameraOn.collectAsStateWithLifecycle()
    val trackingState by viewModel.trackingState.collectAsStateWithLifecycle()

    var showExitDialog by remember { mutableStateOf(false) }
    var cameraPermissionError by remember { mutableStateOf<String?>(null) }

    // Keep screen on during workout
    val context = LocalContext.current
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraPermissionError = null
            viewModel.setCameraEnabled(true)
        } else {
            viewModel.setCameraEnabled(false)
            cameraPermissionError = "Camera permission is needed to count reps"
        }
    }
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler {
        if (uiState is WorkoutSessionState.Completed) {
            onFinish()
        } else {
            showExitDialog = true
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Quit Workout?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to quit? Your progress for this session will not be saved.") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onFinish()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                    modifier = Modifier.testTag("confirm_quit_button")
                ) {
                    Text("Quit")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false }) {
                    Text("Resume")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (uiState !is WorkoutSessionState.Completed) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = plan?.name ?: "Workout",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState is WorkoutSessionState.ActiveExercise) {
                                val active = uiState as WorkoutSessionState.ActiveExercise
                                Text(
                                    text = "Exercise ${active.exerciseIndex + 1} of ${exercisesList.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { showExitDialog = true },
                            modifier = Modifier.testTag("close_workout_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Workout")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSound() }) {
                            Icon(
                                if (soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = "Toggle Sound",
                                tint = if (soundEnabled) FlameOrange else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is WorkoutSessionState.Countdown -> {
                    CountdownView(seconds = state.secondsLeft, nextExerciseName = state.nextExercise?.name)
                }
                is WorkoutSessionState.ActiveExercise -> {
                    ActiveExerciseView(
                        state = state,
                        totalExercises = exercisesList.size,
                        isCameraSupported = isCameraSupported,
                        isCameraOn = isCameraOn,
                        trackingState = trackingState,
                        cameraPermissionError = cameraPermissionError,
                        onUseCameraToggle = { checked ->
                            if (checked) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    cameraPermissionError = null
                                    viewModel.setCameraEnabled(true)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            } else {
                                cameraPermissionError = null
                                viewModel.setCameraEnabled(false)
                            }
                        },
                        onTrackingStateChanged = { viewModel.updateTrackingState(it) },
                        onCameraRepDetected = { viewModel.onCameraRepDetected() },
                        onPauseToggle = { viewModel.togglePause() },
                        onSkip = { viewModel.skipCurrentExercise() },
                        onPrevious = { viewModel.previousExercise() },
                        onRestart = { viewModel.restartCurrentExercise() },
                        onIncrementReps = { viewModel.incrementReps() },
                        onCompleteTimeOrReps = { viewModel.completeCurrentExerciseAndRest() }
                    )
                }
                is WorkoutSessionState.RestInterval -> {
                    RestIntervalView(
                        state = state,
                        onAddRest = { viewModel.addRestTime(20) },
                        onSkipRest = { viewModel.skipRest() },
                        onPauseToggle = { viewModel.togglePause() }
                    )
                }
                is WorkoutSessionState.Completed -> {
                    WorkoutCompletedView(state = state, onDone = onFinish)
                }
            }
        }
    }
}

@Composable
private fun CountdownView(seconds: Int, nextExerciseName: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = "READY TO GO!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = FlameOrange
            )
            Spacer(modifier = Modifier.height(32.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(FlameOrange.copy(alpha = 0.15f))
                    .border(4.dp, FlameOrange, CircleShape)
            ) {
                Text(
                    text = "$seconds",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp),
                    fontWeight = FontWeight.Black,
                    color = FlameOrange
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            if (nextExerciseName != null) {
                Text(
                    text = "First Exercise:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = nextExerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActiveExerciseView(
    state: WorkoutSessionState.ActiveExercise,
    totalExercises: Int,
    isCameraSupported: Boolean,
    isCameraOn: Boolean,
    trackingState: PoseTrackingState,
    cameraPermissionError: String?,
    onUseCameraToggle: (Boolean) -> Unit,
    onTrackingStateChanged: (PoseTrackingState) -> Unit,
    onCameraRepDetected: () -> Unit,
    onPauseToggle: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    onRestart: () -> Unit,
    onIncrementReps: () -> Unit,
    onCompleteTimeOrReps: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showMuscleMapSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Linear workout progress
        val progress = (state.exerciseIndex + 1).toFloat() / totalExercises.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = FlameOrange,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Exercise Visualizer Animation
        ExerciseVisualizer(
            animationType = state.exercise.animationType,
            exerciseId = state.exercise.id,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp),
            isPaused = state.isPaused
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Exercise Title & Muscle Tag
        Text(
            text = state.exercise.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        AssistChip(
            onClick = { showMuscleMapSheet = true },
            label = { Text("Target Muscles: ${state.exercise.targetMuscles}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) },
            leadingIcon = {
                Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(16.dp), tint = FlameOrange)
            },
            trailingIcon = {
                Icon(Icons.Default.Visibility, contentDescription = "View Muscle Map", modifier = Modifier.size(16.dp), tint = ElectricCyan)
            },
            modifier = Modifier.testTag("active_target_muscles_chip")
        )

        if (showMuscleMapSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMuscleMapSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = state.exercise.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    com.example.ui.components.BodyMuscleVisualizer(
                        primaryMuscles = state.exercise.primaryMuscles.ifEmpty { state.exercise.targetMuscles },
                        secondaryMuscles = state.exercise.secondaryMuscles,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showMuscleMapSheet = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange)
                    ) {
                        Text("Back to Workout", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Timer or Reps Display
        if (state.planExercise.type == "TIME") {
            // Circular countdown timer
            val total = state.totalSeconds.coerceAtLeast(1)
            val timeProgress = (state.secondsRemaining.toFloat() / total).coerceIn(0f, 1f)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
            ) {
                CircularProgressIndicator(
                    progress = { timeProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    color = FlameOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.secondsRemaining}s",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (state.isPaused) "PAUSED" else "REMAINING",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state.isPaused) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Reps counter
            if (isCameraSupported) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .testTag("use_camera_row")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = FlameOrange,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Use camera",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-count reps with pose tracking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isCameraOn,
                            onCheckedChange = onUseCameraToggle,
                            colors = appSwitchColors(),
                            modifier = Modifier.testTag("use_camera_switch")
                        )
                    }
                }

                if (!cameraPermissionError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = cameraPermissionError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .testTag("camera_permission_error")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isCameraOn) {
                    PoseCameraPreview(
                        repsCompleted = state.repsCompleted,
                        targetReps = state.targetReps,
                        isPaused = state.isPaused,
                        trackingState = trackingState,
                        onTrackingStateChanged = onTrackingStateChanged,
                        onRepDetected = onCameraRepDetected,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(280.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clickable { onIncrementReps() }
                    .testTag("rep_card_tap"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "x${state.targetReps}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = FlameOrange
                    )
                    Text(
                        text = "Completed: ${state.repsCompleted} / ${state.targetReps}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tap card to count rep (+1)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCompleteTimeOrReps,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(50.dp)
                    .testTag("done_reps_button"),
                colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                shape = RoundedCornerShape(25.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DONE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Playback Controls Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = state.exerciseIndex > 0,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }

            IconButton(
                onClick = onRestart,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Restart")
            }

            // Big Pause / Resume button
            IconButton(
                onClick = onPauseToggle,
                modifier = Modifier
                    .size(64.dp)
                    .background(FlameOrange, CircleShape)
                    .testTag("pause_resume_button")
            ) {
                Icon(
                    if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onSkip,
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("skip_exercise_button")
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Skip")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Posture & Instruction guidance
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null, tint = GoldYellow, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Posture Guidance & Tips", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.exercise.tips,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.exercise.instructions,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun RestIntervalView(
    state: WorkoutSessionState.RestInterval,
    onAddRest: () -> Unit,
    onSkipRest: () -> Unit,
    onPauseToggle: () -> Unit
) {
    val total = state.totalSeconds.coerceAtLeast(1)
    val progress = (state.secondsRemaining.toFloat() / total).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "REST INTERVAL",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Big circular rest countdown
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(160.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 12.dp,
                    color = ElectricCyan,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.secondsRemaining}s",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = ElectricCyan
                    )
                    Text(
                        text = if (state.isPaused) "PAUSED" else "REST",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action row (+20s, Pause, Skip)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onAddRest,
                    modifier = Modifier.testTag("add_rest_button"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("+20s")
                }

                FilledTonalButton(
                    onClick = onSkipRest,
                    modifier = Modifier.testTag("skip_rest_button"),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Skip Rest", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Next Exercise Preview Card
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
                    Text(
                        text = "NEXT UP",
                        style = MaterialTheme.typography.labelMedium,
                        color = FlameOrange,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (state.nextPlanExercise.type == "TIME") "${state.nextPlanExercise.count}s" else "x${state.nextPlanExercise.count}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = state.nextExercise.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = state.nextExercise.targetMuscles,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExerciseVisualizer(
                    animationType = state.nextExercise.animationType,
                    exerciseId = state.nextExercise.id,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    isPaused = false
                )
            }
        }
    }
}

@Composable
private fun WorkoutCompletedView(
    state: WorkoutSessionState.Completed,
    onDone: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Trophy Badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(GoldYellow.copy(alpha = 0.2f))
                .border(3.dp, GoldYellow, CircleShape)
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = "Trophy",
                tint = GoldYellow,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "WORKOUT CRUSHED!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = FlameOrange
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics Grid Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Duration
                val mins = state.durationSeconds / 60
                val secs = state.durationSeconds % 60
                val timeStr = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
                StatItem(label = "Duration", value = timeStr, icon = Icons.Default.Timer, color = ElectricCyan)

                // Calories
                StatItem(label = "Est. Calories", value = "${state.caloriesBurned} kcal", icon = Icons.Default.LocalFireDepartment, color = FlameOrange)

                // Exercises
                StatItem(label = "Exercises", value = "${state.exercisesCompleted}/${state.totalExercises}", icon = Icons.Default.CheckCircle, color = SuccessGreen)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // XP & Gamification Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(FlameOrange.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = FlameOrange)
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "+${state.earnedXp} XP EARNED!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FlameOrange
                    )
                    if (!state.unlockedBadgeTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Badge Unlocked: ${state.unlockedBadgeTitle}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("finish_workout_button"),
            colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("FINISH & SAVE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
