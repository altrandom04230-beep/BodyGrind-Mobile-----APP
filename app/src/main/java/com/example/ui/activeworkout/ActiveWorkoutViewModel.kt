package com.example.ui.activeworkout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.data.repository.WorkoutRepository
import com.example.utils.AudioHapticHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class WorkoutSessionState {
    data class Countdown(val secondsLeft: Int, val nextExercise: ExerciseEntity?) : WorkoutSessionState()
    data class ActiveExercise(
        val exerciseIndex: Int,
        val exercise: ExerciseEntity,
        val planExercise: WorkoutPlanExerciseEntity,
        val secondsRemaining: Int,
        val totalSeconds: Int,
        val repsCompleted: Int,
        val targetReps: Int,
        val isPaused: Boolean
    ) : WorkoutSessionState()
    data class RestInterval(
        val secondsRemaining: Int,
        val totalSeconds: Int,
        val nextExerciseIndex: Int,
        val nextExercise: ExerciseEntity,
        val nextPlanExercise: WorkoutPlanExerciseEntity,
        val isPaused: Boolean
    ) : WorkoutSessionState()
    data class Completed(
        val durationSeconds: Int,
        val caloriesBurned: Int,
        val exercisesCompleted: Int,
        val totalExercises: Int,
        val earnedXp: Int,
        val unlockedBadgeTitle: String?
    ) : WorkoutSessionState()
}

class ActiveWorkoutViewModel(
    application: Application,
    private val planId: String
) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(AppDatabase.getDatabase(application), application.applicationContext)
    private val audioHapticHelper = AudioHapticHelper(application)

    private val _uiState = MutableStateFlow<WorkoutSessionState>(WorkoutSessionState.Countdown(5, null))
    val uiState: StateFlow<WorkoutSessionState> = _uiState.asStateFlow()

    private val _plan = MutableStateFlow<WorkoutPlanEntity?>(null)
    val plan: StateFlow<WorkoutPlanEntity?> = _plan.asStateFlow()

    private val _exercisesList = MutableStateFlow<List<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>>(emptyList())
    val exercisesList: StateFlow<List<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>> = _exercisesList.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _cameraEnabledForWorkout = MutableStateFlow(false)
    val cameraEnabledForWorkout: StateFlow<Boolean> = _cameraEnabledForWorkout.asStateFlow()

    val isCameraSupported: StateFlow<Boolean> = _uiState
        .map { state ->
            state is WorkoutSessionState.ActiveExercise &&
                CameraTrackedExercises.isSupported(state.exercise.id, state.planExercise.type)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isCameraOn: StateFlow<Boolean> = combine(isCameraSupported, _cameraEnabledForWorkout) { supported, enabled ->
        supported && enabled
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _trackingState = MutableStateFlow(PoseTrackingState.NOT_TRACKING)
    val trackingState: StateFlow<PoseTrackingState> = _trackingState.asStateFlow()

    private var currentExerciseIndex = 0
    private var timerJob: Job? = null
    private var startTimeMillis = 0L
    private var totalWorkoutSeconds = 0
    private var totalCalories = 0
    private var hasFinished = false

    init {
        loadPlanAndStart()
    }

    private fun loadPlanAndStart() {
        viewModelScope.launch {
            val userProfile = repository.getUserProfileSync()
            _soundEnabled.value = userProfile?.soundEnabled ?: true

            val planEntity = repository.getPlanById(planId)
            _plan.value = planEntity

            val planExercises = repository.getPlanExercisesSync(planId)
            val fullList = mutableListOf<Pair<WorkoutPlanExerciseEntity, ExerciseEntity>>()
            for (pe in planExercises) {
                val ex = repository.getExerciseById(pe.exerciseId)
                if (ex != null) {
                    fullList.add(Pair(pe, ex))
                }
            }
            _exercisesList.value = fullList

            if (fullList.isNotEmpty()) {
                startInitialCountdown(fullList.first().second)
            }
        }
    }

    private fun startInitialCountdown(firstExercise: ExerciseEntity) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            startTimeMillis = System.currentTimeMillis()
            for (sec in 5 downTo 1) {
                _uiState.value = WorkoutSessionState.Countdown(sec, firstExercise)
                audioHapticHelper.playCountdownBeep(_soundEnabled.value, true)
                delay(1000L)
            }
            audioHapticHelper.playGoChime(_soundEnabled.value, true)
            startExercise(0)
        }
    }

    private fun startExercise(index: Int) {
        timerJob?.cancel()
        _trackingState.value = PoseTrackingState.NOT_TRACKING
        val list = _exercisesList.value
        if (index >= list.size) {
            finishWorkout()
            return
        }

        currentExerciseIndex = index
        val (planEx, ex) = list[index]

        if (planEx.type == "TIME") {
            val duration = planEx.count
            _uiState.value = WorkoutSessionState.ActiveExercise(
                exerciseIndex = index,
                exercise = ex,
                planExercise = planEx,
                secondsRemaining = duration,
                totalSeconds = duration,
                repsCompleted = 0,
                targetReps = 0,
                isPaused = false
            )
            runTimeExerciseTimer(duration)
        } else {
            // REPS
            _uiState.value = WorkoutSessionState.ActiveExercise(
                exerciseIndex = index,
                exercise = ex,
                planExercise = planEx,
                secondsRemaining = 0,
                totalSeconds = 0,
                repsCompleted = 0,
                targetReps = planEx.count,
                isPaused = false
            )
        }
    }

    private fun runTimeExerciseTimer(startSeconds: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = startSeconds
            while (remaining > 0) {
                delay(1000L)
                val current = _uiState.value
                if (current is WorkoutSessionState.ActiveExercise && !current.isPaused) {
                    remaining--
                    totalWorkoutSeconds++
                    val calPerSec = current.exercise.caloriesPerMinute / 60f
                    totalCalories += calPerSec.toInt().coerceAtLeast(1)

                    _uiState.value = current.copy(secondsRemaining = remaining)

                    if (remaining in 1..3) {
                        audioHapticHelper.playCountdownBeep(_soundEnabled.value, true)
                    }
                }
            }
            // Exercise completed!
            completeCurrentExerciseAndRest()
        }
    }

    fun completeCurrentExerciseAndRest() {
        timerJob?.cancel()
        _trackingState.value = PoseTrackingState.NOT_TRACKING
        val list = _exercisesList.value
        val nextIdx = currentExerciseIndex + 1

        if (nextIdx >= list.size) {
            finishWorkout()
            return
        }

        val currentPlanEx = list[currentExerciseIndex].first
        val restTime = if (currentPlanEx.restSeconds > 0) currentPlanEx.restSeconds else 20
        val (nextPlanEx, nextEx) = list[nextIdx]

        _uiState.value = WorkoutSessionState.RestInterval(
            secondsRemaining = restTime,
            totalSeconds = restTime,
            nextExerciseIndex = nextIdx,
            nextExercise = nextEx,
            nextPlanExercise = nextPlanEx,
            isPaused = false
        )

        runRestTimer(restTime, nextIdx)
    }

    private fun runRestTimer(restSeconds: Int, nextIdx: Int) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var remaining = restSeconds
            while (remaining > 0) {
                delay(1000L)
                val current = _uiState.value
                if (current is WorkoutSessionState.RestInterval && !current.isPaused) {
                    remaining--
                    _uiState.value = current.copy(secondsRemaining = remaining)

                    if (remaining in 1..3) {
                        audioHapticHelper.playCountdownBeep(_soundEnabled.value, true)
                    }
                }
            }
            audioHapticHelper.playRestFinishedChime(_soundEnabled.value, true)
            startExercise(nextIdx)
        }
    }

    fun togglePause() {
        val current = _uiState.value
        if (current is WorkoutSessionState.ActiveExercise) {
            val nextPaused = !current.isPaused
            if (nextPaused) {
                _trackingState.value = PoseTrackingState.NOT_TRACKING
            }
            _uiState.value = current.copy(isPaused = nextPaused)
        } else if (current is WorkoutSessionState.RestInterval) {
            _uiState.value = current.copy(isPaused = !current.isPaused)
        }
    }

    fun skipCurrentExercise() {
        completeCurrentExerciseAndRest()
    }

    fun previousExercise() {
        if (currentExerciseIndex > 0) {
            startExercise(currentExerciseIndex - 1)
        }
    }

    fun restartCurrentExercise() {
        startExercise(currentExerciseIndex)
    }

    fun addRestTime(secondsToAdd: Int) {
        val current = _uiState.value
        if (current is WorkoutSessionState.RestInterval) {
            val newRemaining = current.secondsRemaining + secondsToAdd
            val newTotal = current.totalSeconds + secondsToAdd
            _uiState.value = current.copy(secondsRemaining = newRemaining, totalSeconds = newTotal)
        }
    }

    fun skipRest() {
        val current = _uiState.value
        if (current is WorkoutSessionState.RestInterval) {
            timerJob?.cancel()
            startExercise(current.nextExerciseIndex)
        }
    }

    fun incrementReps() {
        val current = _uiState.value
        if (current is WorkoutSessionState.ActiveExercise && current.targetReps > 0) {
            val newReps = current.repsCompleted + 1
            if (newReps >= current.targetReps) {
                completeCurrentExerciseAndRest()
            } else {
                _uiState.value = current.copy(repsCompleted = newReps)
            }
        }
    }

    fun setCameraEnabled(on: Boolean) {
        _cameraEnabledForWorkout.value = on
        if (!on) {
            _trackingState.value = PoseTrackingState.NOT_TRACKING
        }
    }

    fun updateTrackingState(state: PoseTrackingState) {
        _trackingState.value = if (isCameraOn.value) state else PoseTrackingState.NOT_TRACKING
    }

    fun onCameraRepDetected() {
        if (hasFinished) return
        val current = _uiState.value
        if (current is WorkoutSessionState.ActiveExercise &&
            !current.isPaused &&
            current.planExercise.type == "REPS" &&
            CameraTrackedExercises.isSupported(current.exercise.id, current.planExercise.type)
        ) {
            incrementReps()
        }
    }

    fun toggleSound() {
        _soundEnabled.value = !_soundEnabled.value
    }

    private fun finishWorkout() {
        if (hasFinished) return
        hasFinished = true
        timerJob?.cancel()
        _trackingState.value = PoseTrackingState.NOT_TRACKING
        audioHapticHelper.playCompletedFanfare(_soundEnabled.value, true)

        val totalTime = if (totalWorkoutSeconds > 0) totalWorkoutSeconds else {
            ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt().coerceAtLeast(30)
        }
        val estimatedCal = (totalCalories).coerceAtLeast((totalTime * 0.12f).toInt().coerceAtLeast(15))
        val list = _exercisesList.value
        val totalEx = list.size
        val p = _plan.value

        viewModelScope.launch {
            val result = repository.completeWorkoutSession(
                planId = planId,
                planName = p?.name ?: "Home Workout",
                category = p?.category ?: "Full Body",
                startedAt = startTimeMillis,
                completedAt = System.currentTimeMillis(),
                durationSeconds = totalTime,
                caloriesBurned = estimatedCal,
                exercisesCompleted = totalEx,
                totalExercises = totalEx
            )

            _uiState.value = WorkoutSessionState.Completed(
                durationSeconds = totalTime,
                caloriesBurned = estimatedCal,
                exercisesCompleted = totalEx,
                totalExercises = totalEx,
                earnedXp = result.earnedXp,
                unlockedBadgeTitle = result.unlockedBadgeTitle
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        audioHapticHelper.release()
    }
}
