/// Workout Plan and Plan Exercise models matching Entities.kt
class WorkoutPlan {
  final String id;
  final String name;
  final String description;
  final String category;
  final String difficulty;
  final int estimatedDurationMinutes;
  final bool isCustom;
  final bool isChallenge;
  final int totalDays;
  final int currentDay;
  final int createdAt;
  final String targetMusclesDescription;
  final String educationalOverview;

  const WorkoutPlan({
    required this.id,
    required this.name,
    required this.description,
    required this.category,
    required this.difficulty,
    required this.estimatedDurationMinutes,
    this.isCustom = false,
    this.isChallenge = false,
    this.totalDays = 1,
    this.currentDay = 1,
    required this.createdAt,
    this.targetMusclesDescription = '',
    this.educationalOverview = '',
  });

  WorkoutPlan copyWith({
    String? id,
    String? name,
    String? description,
    String? category,
    String? difficulty,
    int? estimatedDurationMinutes,
    bool? isCustom,
    bool? isChallenge,
    int? totalDays,
    int? currentDay,
    int? createdAt,
    String? targetMusclesDescription,
    String? educationalOverview,
  }) {
    return WorkoutPlan(
      id: id ?? this.id,
      name: name ?? this.name,
      description: description ?? this.description,
      category: category ?? this.category,
      difficulty: difficulty ?? this.difficulty,
      estimatedDurationMinutes: estimatedDurationMinutes ?? this.estimatedDurationMinutes,
      isCustom: isCustom ?? this.isCustom,
      isChallenge: isChallenge ?? this.isChallenge,
      totalDays: totalDays ?? this.totalDays,
      currentDay: currentDay ?? this.currentDay,
      createdAt: createdAt ?? this.createdAt,
      targetMusclesDescription: targetMusclesDescription ?? this.targetMusclesDescription,
      educationalOverview: educationalOverview ?? this.educationalOverview,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'description': description,
      'category': category,
      'difficulty': difficulty,
      'estimatedDurationMinutes': estimatedDurationMinutes,
      'isCustom': isCustom ? 1 : 0,
      'isChallenge': isChallenge ? 1 : 0,
      'totalDays': totalDays,
      'currentDay': currentDay,
      'createdAt': createdAt,
      'targetMusclesDescription': targetMusclesDescription,
      'educationalOverview': educationalOverview,
    };
  }

  factory WorkoutPlan.fromMap(Map<String, dynamic> map) {
    return WorkoutPlan(
      id: map['id'] as String,
      name: map['name'] as String,
      description: map['description'] as String,
      category: map['category'] as String,
      difficulty: map['difficulty'] as String,
      estimatedDurationMinutes: (map['estimatedDurationMinutes'] as num).toInt(),
      isCustom: (map['isCustom'] as num?) == 1,
      isChallenge: (map['isChallenge'] as num?) == 1,
      totalDays: (map['totalDays'] as num?)?.toInt() ?? 1,
      currentDay: (map['currentDay'] as num?)?.toInt() ?? 1,
      createdAt: (map['createdAt'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
      targetMusclesDescription: map['targetMusclesDescription'] as String? ?? '',
      educationalOverview: map['educationalOverview'] as String? ?? '',
    );
  }
}

class WorkoutPlanExercise {
  final int id;
  final String planId;
  final String exerciseId;
  final int orderIndex;
  final String type; // TIME, REPS
  final int count;
  final int restSeconds;

  const WorkoutPlanExercise({
    this.id = 0,
    required this.planId,
    required this.exerciseId,
    required this.orderIndex,
    required this.type,
    required this.count,
    this.restSeconds = 20,
  });

  WorkoutPlanExercise copyWith({
    int? id,
    String? planId,
    String? exerciseId,
    int? orderIndex,
    String? type,
    int? count,
    int? restSeconds,
  }) {
    return WorkoutPlanExercise(
      id: id ?? this.id,
      planId: planId ?? this.planId,
      exerciseId: exerciseId ?? this.exerciseId,
      orderIndex: orderIndex ?? this.orderIndex,
      type: type ?? this.type,
      count: count ?? this.count,
      restSeconds: restSeconds ?? this.restSeconds,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      if (id > 0) 'id': id,
      'planId': planId,
      'exerciseId': exerciseId,
      'orderIndex': orderIndex,
      'type': type,
      'count': count,
      'restSeconds': restSeconds,
    };
  }

  factory WorkoutPlanExercise.fromMap(Map<String, dynamic> map) {
    return WorkoutPlanExercise(
      id: (map['id'] as num?)?.toInt() ?? 0,
      planId: map['planId'] as String,
      exerciseId: map['exerciseId'] as String,
      orderIndex: (map['orderIndex'] as num).toInt(),
      type: map['type'] as String,
      count: (map['count'] as num).toInt(),
      restSeconds: (map['restSeconds'] as num?)?.toInt() ?? 20,
    );
  }
}
