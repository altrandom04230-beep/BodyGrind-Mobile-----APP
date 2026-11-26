/// Workout Session model matching WorkoutSessionEntity in Entities.kt
class WorkoutSession {
  final int sessionId;
  final String planId;
  final String planName;
  final String category;
  final int startedAt;
  final int completedAt;
  final int durationSeconds;
  final int caloriesBurned;
  final int exercisesCompleted;
  final int totalExercises;
  final bool isFinished;

  const WorkoutSession({
    this.sessionId = 0,
    required this.planId,
    required this.planName,
    required this.category,
    required this.startedAt,
    required this.completedAt,
    required this.durationSeconds,
    required this.caloriesBurned,
    required this.exercisesCompleted,
    required this.totalExercises,
    required this.isFinished,
  });

  Map<String, dynamic> toMap() {
    return {
      if (sessionId > 0) 'sessionId': sessionId,
      'planId': planId,
      'planName': planName,
      'category': category,
      'startedAt': startedAt,
      'completedAt': completedAt,
      'durationSeconds': durationSeconds,
      'caloriesBurned': caloriesBurned,
      'exercisesCompleted': exercisesCompleted,
      'totalExercises': totalExercises,
      'isFinished': isFinished ? 1 : 0,
    };
  }

  factory WorkoutSession.fromMap(Map<String, dynamic> map) {
    return WorkoutSession(
      sessionId: (map['sessionId'] as num?)?.toInt() ?? 0,
      planId: map['planId'] as String,
      planName: map['planName'] as String,
      category: map['category'] as String,
      startedAt: (map['startedAt'] as num).toInt(),
      completedAt: (map['completedAt'] as num).toInt(),
      durationSeconds: (map['durationSeconds'] as num).toInt(),
      caloriesBurned: (map['caloriesBurned'] as num).toInt(),
      exercisesCompleted: (map['exercisesCompleted'] as num).toInt(),
      totalExercises: (map['totalExercises'] as num).toInt(),
      isFinished: (map['isFinished'] as num?) == 1,
    );
  }
}
