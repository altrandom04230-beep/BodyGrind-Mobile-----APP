package com.example.ui.activeworkout

enum class PoseTrackingState {
    TRACKING,
    NOT_TRACKING
}

object CameraTrackedExercises {
    val SQUAT_EXERCISE_IDS: Set<String> = setOf("squats")

    fun isSupported(exerciseId: String, planType: String): Boolean {
        return planType == "REPS" && exerciseId in SQUAT_EXERCISE_IDS
    }
}
