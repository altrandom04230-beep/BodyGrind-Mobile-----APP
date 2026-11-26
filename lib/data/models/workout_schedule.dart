/// Workout schedule model matching WorkoutScheduleEntity in Entities.kt
class WorkoutSchedule {
  final int id;
  final int dayOfWeek; // 1 to 7 (1=Monday, 7=Sunday)
  final String planId;
  final String planName;
  final int timeHour;
  final int timeMinute;
  final bool isEnabled;

  const WorkoutSchedule({
    this.id = 0,
    required this.dayOfWeek,
    required this.planId,
    required this.planName,
    this.timeHour = 8,
    this.timeMinute = 0,
    this.isEnabled = true,
  });

  WorkoutSchedule copyWith({
    int? id,
    int? dayOfWeek,
    String? planId,
    String? planName,
    int? timeHour,
    int? timeMinute,
    bool? isEnabled,
  }) {
    return WorkoutSchedule(
      id: id ?? this.id,
      dayOfWeek: dayOfWeek ?? this.dayOfWeek,
      planId: planId ?? this.planId,
      planName: planName ?? this.planName,
      timeHour: timeHour ?? this.timeHour,
      timeMinute: timeMinute ?? this.timeMinute,
      isEnabled: isEnabled ?? this.isEnabled,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      if (id > 0) 'id': id,
      'dayOfWeek': dayOfWeek,
      'planId': planId,
      'planName': planName,
      'timeHour': timeHour,
      'timeMinute': timeMinute,
      'isEnabled': isEnabled ? 1 : 0,
    };
  }

  factory WorkoutSchedule.fromMap(Map<String, dynamic> map) {
    return WorkoutSchedule(
      id: (map['id'] as num?)?.toInt() ?? 0,
      dayOfWeek: (map['dayOfWeek'] as num).toInt(),
      planId: map['planId'] as String,
      planName: map['planName'] as String,
      timeHour: (map['timeHour'] as num?)?.toInt() ?? 8,
      timeMinute: (map['timeMinute'] as num?)?.toInt() ?? 0,
      isEnabled: (map['isEnabled'] as num?) == 1,
    );
  }
}
