import 'app_role.dart';

/// Trainer Portal domain models matching TrainerModels.kt
class TrainerInfo {
  final String id;
  final String name;
  final String title;
  final String email;
  final String specialization;

  const TrainerInfo({
    required this.id,
    required this.name,
    required this.title,
    required this.email,
    required this.specialization,
  });
}

class MemberWorkoutRecord {
  final String id;
  final String title;
  final String dateLabel;
  final int durationMinutes;
  final int caloriesBurned;
  final bool completed;

  const MemberWorkoutRecord({
    required this.id,
    required this.title,
    required this.dateLabel,
    required this.durationMinutes,
    required this.caloriesBurned,
    required this.completed,
  });
}

class MemberAttendanceRecord {
  final String id;
  final String dateLabel;
  final String timeLabel;
  final String method;
  final String status;

  const MemberAttendanceRecord({
    required this.id,
    required this.dateLabel,
    required this.timeLabel,
    required this.method,
    required this.status,
  });
}

class WeightBmiPoint {
  final String weekLabel;
  final double weightKg;
  final double bmi;

  const WeightBmiPoint({
    required this.weekLabel,
    required this.weightKg,
    required this.bmi,
  });
}

class ExercisePerformanceRecord {
  final String exerciseName;
  final String targetMetric;
  final String previousBest;
  final String currentBest;
  final String trendLabel;
  final bool isImproving;

  const ExercisePerformanceRecord({
    required this.exerciseName,
    required this.targetMetric,
    required this.previousBest,
    required this.currentBest,
    required this.trendLabel,
    required this.isImproving,
  });
}

class AssignedMember {
  final String id;
  final String name;
  final int age;
  final String gender;
  final String email;
  final String joinDate;
  final MemberStatus status;
  final String fitnessGoal;
  final String fitnessLevel;
  final int weeklyTargetWorkouts;
  final int completedWorkoutsThisWeek;
  final int workoutCompletionRate;
  final int attendanceRate;
  final int attendanceStreakDays;
  final int consistencyScore;
  final List<bool> weeklyAdherenceDays;
  final double heightCm;
  final double startingWeightKg;
  final double currentWeightKg;
  final double targetWeightKg;
  final List<WeightBmiPoint> weightBmiHistory;
  final List<MemberWorkoutRecord> workoutHistory;
  final List<MemberAttendanceRecord> attendanceHistory;
  final List<ExercisePerformanceRecord> exercisePerformance;

  const AssignedMember({
    required this.id,
    required this.name,
    required this.age,
    required this.gender,
    required this.email,
    required this.joinDate,
    required this.status,
    required this.fitnessGoal,
    required this.fitnessLevel,
    required this.weeklyTargetWorkouts,
    required this.completedWorkoutsThisWeek,
    required this.workoutCompletionRate,
    required this.attendanceRate,
    required this.attendanceStreakDays,
    required this.consistencyScore,
    required this.weeklyAdherenceDays,
    required this.heightCm,
    required this.startingWeightKg,
    required this.currentWeightKg,
    required this.targetWeightKg,
    required this.weightBmiHistory,
    required this.workoutHistory,
    required this.attendanceHistory,
    required this.exercisePerformance,
  });

  double get currentBmi {
    final hMeters = heightCm / 100.0;
    return hMeters > 0 ? currentWeightKg / (hMeters * hMeters) : 0.0;
  }

  double get weightChangeKg => currentWeightKg - startingWeightKg;
}

class TrainerPendingTask {
  final String id;
  final String memberId;
  final String memberName;
  final String title;
  final String dueLabel;
  final String priorityLabel;
  final bool isCompleted;

  const TrainerPendingTask({
    required this.id,
    required this.memberId,
    required this.memberName,
    required this.title,
    required this.dueLabel,
    required this.priorityLabel,
    this.isCompleted = false,
  });

  TrainerPendingTask copyWith({
    String? id,
    String? memberId,
    String? memberName,
    String? title,
    String? dueLabel,
    String? priorityLabel,
    bool? isCompleted,
  }) {
    return TrainerPendingTask(
      id: id ?? this.id,
      memberId: memberId ?? this.memberId,
      memberName: memberName ?? this.memberName,
      title: title ?? this.title,
      dueLabel: dueLabel ?? this.dueLabel,
      priorityLabel: priorityLabel ?? this.priorityLabel,
      isCompleted: isCompleted ?? this.isCompleted,
    );
  }
}

class TrainerNotification {
  final String id;
  final String title;
  final String message;
  final String timeLabel;
  final bool isUnread;

  const TrainerNotification({
    required this.id,
    required this.title,
    required this.message,
    required this.timeLabel,
    this.isUnread = true,
  });

  TrainerNotification copyWith({
    String? id,
    String? title,
    String? message,
    String? timeLabel,
    bool? isUnread,
  }) {
    return TrainerNotification(
      id: id ?? this.id,
      title: title ?? this.title,
      message: message ?? this.message,
      timeLabel: timeLabel ?? this.timeLabel,
      isUnread: isUnread ?? this.isUnread,
    );
  }
}

class TrainerFeedbackEntry {
  final String id;
  final String memberId;
  final String memberName;
  final FeedbackCategory category;
  final String message;
  final String timestampLabel;

  const TrainerFeedbackEntry({
    required this.id,
    required this.memberId,
    required this.memberName,
    required this.category,
    required this.message,
    required this.timestampLabel,
  });
}
