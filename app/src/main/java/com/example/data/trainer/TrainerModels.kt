package com.example.data.trainer

enum class AppRole {
    MEMBER,
    TRAINER,
    ADMIN
}

enum class MemberStatus(val label: String) {
    ACTIVE("Active"),
    NEEDS_ATTENTION("Needs Attention"),
    PAUSED("Paused")
}

enum class FeedbackCategory(val label: String) {
    WORKOUT_FEEDBACK("Workout Feedback"),
    EXERCISE_RECOMMENDATION("Exercise Recommendations"),
    PROGRESS_COMMENT("Progress Comments"),
    TRAINING_ADJUSTMENT("Training Adjustments"),
    FITNESS_GUIDANCE("Fitness Guidance")
}

data class TrainerInfo(
    val id: String,
    val name: String,
    val title: String,
    val email: String,
    val specialization: String
)

data class MemberWorkoutRecord(
    val id: String,
    val title: String,
    val dateLabel: String,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val completed: Boolean,
    val timestampUtcMs: Long = 0L
)

data class MemberAttendanceRecord(
    val id: String,
    val dateLabel: String,
    val timeLabel: String,
    val method: String,
    val status: String,
    val timestampUtcMs: Long = 0L
)

data class WeightBmiPoint(
    val weekLabel: String,
    val weightKg: Float,
    val bmi: Float
)

data class ExercisePerformanceRecord(
    val exerciseName: String,
    val targetMetric: String,
    val previousBest: String,
    val currentBest: String,
    val trendLabel: String,
    val isImproving: Boolean
)

data class AssignedMember(
    val id: String,
    val name: String,
    val age: Int,
    val gender: String,
    val email: String,
    val joinDate: String,
    val status: MemberStatus,
    val fitnessGoal: String,
    val fitnessLevel: String,
    val weeklyTargetWorkouts: Int,
    val completedWorkoutsThisWeek: Int,
    val workoutCompletionRate: Int,
    val attendanceRate: Int,
    val attendanceStreakDays: Int,
    val consistencyScore: Int,
    val weeklyAdherenceDays: List<Boolean>,
    val heightCm: Float,
    val startingWeightKg: Float,
    val currentWeightKg: Float,
    val targetWeightKg: Float,
    val weightBmiHistory: List<WeightBmiPoint>,
    val workoutHistory: List<MemberWorkoutRecord>,
    val attendanceHistory: List<MemberAttendanceRecord>,
    val exercisePerformance: List<ExercisePerformanceRecord>,
    val joinDateUtcMs: Long = 0L
) {
    val currentBmi: Float
        get() {
            val hMeters = heightCm / 100f
            return if (hMeters > 0f) currentWeightKg / (hMeters * hMeters) else 0f
        }

    val weightChangeKg: Float
        get() = currentWeightKg - startingWeightKg
}

data class TrainerPendingTask(
    val id: String,
    val memberId: String,
    val memberName: String,
    val title: String,
    val dueLabel: String,
    val priorityLabel: String,
    val isCompleted: Boolean = false,
    val dueDateUtcMs: Long = 0L
)

data class TrainerNotification(
    val id: String,
    val title: String,
    val message: String,
    val timeLabel: String,
    val isUnread: Boolean = true,
    val timestampUtcMs: Long = 0L
)

data class TrainerFeedbackEntry(
    val id: String,
    val memberId: String,
    val memberName: String,
    val category: FeedbackCategory,
    val message: String,
    val timestampLabel: String,
    val timestampUtcMs: Long = 0L
)
