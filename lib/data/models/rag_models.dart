import 'exercise.dart';
import 'workout_plan.dart';
import 'workout_schedule.dart';

/// RAG AI Coach retrieved items matching FitnessRagEngine.kt
class RagSourceItem {
  final String title;
  final String category; // EXERCISE, PLAN, SCHEDULE, TRAINER, ANATOMY, SCIENCE, TELEMETRY
  final String detail;
  final Exercise? exercise;
  final WorkoutPlan? workoutPlan;

  const RagSourceItem({
    required this.title,
    required this.category,
    required this.detail,
    this.exercise,
    this.workoutPlan,
  });
}

class RagSearchResult {
  final String augmentedContext;
  final List<RagSourceItem> retrievedSources;
  final List<Exercise> matchedExercises;
  final List<WorkoutPlan> matchedPlans;
  final List<WorkoutSchedule> matchedSchedules;
  final String directLocalAnswer;

  const RagSearchResult({
    required this.augmentedContext,
    required this.retrievedSources,
    required this.matchedExercises,
    required this.matchedPlans,
    required this.matchedSchedules,
    required this.directLocalAnswer,
  });
}
