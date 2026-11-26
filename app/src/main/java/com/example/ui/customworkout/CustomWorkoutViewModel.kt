package com.example.ui.customworkout

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.WorkoutPlanExerciseEntity
import com.example.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SelectedCustomExercise(
    val exercise: ExerciseEntity,
    var type: String = "REPS", // "REPS" or "TIME"
    var count: Int = 12,
    var restSeconds: Int = 20
)

class CustomWorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WorkoutRepository(AppDatabase.getDatabase(application))

    val allExercises = repository.getAllExercises()

    private val _planName = MutableStateFlow("")
    val planName: StateFlow<String> = _planName.asStateFlow()

    private val _planDescription = MutableStateFlow("")
    val planDescription: StateFlow<String> = _planDescription.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Full Body")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _selectedDifficulty = MutableStateFlow("Intermediate")
    val selectedDifficulty: StateFlow<String> = _selectedDifficulty.asStateFlow()

    private val _selectedExercises = MutableStateFlow<List<SelectedCustomExercise>>(emptyList())
    val selectedExercises: StateFlow<List<SelectedCustomExercise>> = _selectedExercises.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun updateName(name: String) { _planName.value = name }
    fun updateDescription(desc: String) { _planDescription.value = desc }
    fun updateCategory(cat: String) { _selectedCategory.value = cat }
    fun updateDifficulty(diff: String) { _selectedDifficulty.value = diff }

    fun addExercise(exercise: ExerciseEntity) {
        val current = _selectedExercises.value.toMutableList()
        current.add(
            SelectedCustomExercise(
                exercise = exercise,
                type = exercise.defaultType,
                count = exercise.defaultCount,
                restSeconds = 20
            )
        )
        _selectedExercises.value = current
    }

    fun removeExercise(index: Int) {
        val current = _selectedExercises.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _selectedExercises.value = current
        }
    }

    fun moveExerciseUp(index: Int) {
        if (index > 0) {
            val current = _selectedExercises.value.toMutableList()
            val item = current.removeAt(index)
            current.add(index - 1, item)
            _selectedExercises.value = current
        }
    }

    fun moveExerciseDown(index: Int) {
        val current = _selectedExercises.value.toMutableList()
        if (index < current.size - 1) {
            val item = current.removeAt(index)
            current.add(index + 1, item)
            _selectedExercises.value = current
        }
    }

    fun updateExerciseType(index: Int, type: String) {
        val current = _selectedExercises.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            val newCount = if (type == "TIME") 30 else 12
            current[index] = item.copy(type = type, count = newCount)
            _selectedExercises.value = current
        }
    }

    fun updateExerciseCount(index: Int, count: Int) {
        val current = _selectedExercises.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(count = count.coerceAtLeast(1))
            _selectedExercises.value = current
        }
    }

    fun updateExerciseRest(index: Int, rest: Int) {
        val current = _selectedExercises.value.toMutableList()
        if (index in current.indices) {
            val item = current[index]
            current[index] = item.copy(restSeconds = rest.coerceAtLeast(0))
            _selectedExercises.value = current
        }
    }

    fun savePlan(onSaved: (String) -> Unit) {
        if (_planName.value.isBlank() || _selectedExercises.value.isEmpty()) return

        _isSaving.value = true
        viewModelScope.launch {
            val exerciseEntities = _selectedExercises.value.mapIndexed { idx, it ->
                WorkoutPlanExerciseEntity(
                    planId = "",
                    exerciseId = it.exercise.id,
                    orderIndex = idx,
                    type = it.type,
                    count = it.count,
                    restSeconds = it.restSeconds
                )
            }

            val savedPlanId = repository.createCustomPlan(
                name = _planName.value.trim(),
                description = _planDescription.value.trim().ifEmpty { "Personalized custom home workout routine" },
                category = _selectedCategory.value,
                difficulty = _selectedDifficulty.value,
                exercises = exerciseEntities
            )
            _isSaving.value = false
            onSaved(savedPlanId)
        }
    }
}
