/// User Profile model matching UserProfileEntity in Entities.kt
class UserProfile {
  final int id;
  final String name;
  final int age;
  final String gender;
  final String fitnessGoal;
  final String fitnessLevel;
  final int preferredDuration;
  final double weightKg;
  final double heightCm;
  final bool useMetric;
  final bool soundEnabled;
  final bool voiceCuesEnabled;
  final bool vibrationEnabled;
  final bool reminderEnabled;
  final int reminderHour;
  final int reminderMinute;
  final int reminderDaysMask;
  final bool onboardingCompleted;
  final String darkThemeMode; // "DARK", "LIGHT", "SYSTEM"
  final int xpPoints;
  final int level;
  final bool isPrivate;
  final String accentColorHex;
  final String bodyDiagramGender; // "MALE", "FEMALE"
  final String exerciseDrawingGender; // "MALE", "FEMALE"
  final bool gymCheckinEnabled;
  final bool aiCoachEnabled;

  const UserProfile({
    this.id = 1,
    this.name = 'Fitness Athlete',
    this.age = 26,
    this.gender = 'Prefer not to say',
    this.fitnessGoal = 'Build Muscle',
    this.fitnessLevel = 'Beginner',
    this.preferredDuration = 15,
    this.weightKg = 70.0,
    this.heightCm = 175.0,
    this.useMetric = true,
    this.soundEnabled = true,
    this.voiceCuesEnabled = true,
    this.vibrationEnabled = true,
    this.reminderEnabled = true,
    this.reminderHour = 8,
    this.reminderMinute = 0,
    this.reminderDaysMask = 127,
    this.onboardingCompleted = false,
    this.darkThemeMode = 'LIGHT',
    this.xpPoints = 0,
    this.level = 1,
    this.isPrivate = false,
    this.accentColorHex = '#E50914',
    this.bodyDiagramGender = 'MALE',
    this.exerciseDrawingGender = 'MALE',
    this.gymCheckinEnabled = true,
    this.aiCoachEnabled = false,
  });

  UserProfile copyWith({
    int? id,
    String? name,
    int? age,
    String? gender,
    String? fitnessGoal,
    String? fitnessLevel,
    int? preferredDuration,
    double? weightKg,
    double? heightCm,
    bool? useMetric,
    bool? soundEnabled,
    bool? voiceCuesEnabled,
    bool? vibrationEnabled,
    bool? reminderEnabled,
    int? reminderHour,
    int? reminderMinute,
    int? reminderDaysMask,
    bool? onboardingCompleted,
    String? darkThemeMode,
    int? xpPoints,
    int? level,
    bool? isPrivate,
    String? accentColorHex,
    String? bodyDiagramGender,
    String? exerciseDrawingGender,
    bool? gymCheckinEnabled,
    bool? aiCoachEnabled,
  }) {
    return UserProfile(
      id: id ?? this.id,
      name: name ?? this.name,
      age: age ?? this.age,
      gender: gender ?? this.gender,
      fitnessGoal: fitnessGoal ?? this.fitnessGoal,
      fitnessLevel: fitnessLevel ?? this.fitnessLevel,
      preferredDuration: preferredDuration ?? this.preferredDuration,
      weightKg: weightKg ?? this.weightKg,
      heightCm: heightCm ?? this.heightCm,
      useMetric: useMetric ?? this.useMetric,
      soundEnabled: soundEnabled ?? this.soundEnabled,
      voiceCuesEnabled: voiceCuesEnabled ?? this.voiceCuesEnabled,
      vibrationEnabled: vibrationEnabled ?? this.vibrationEnabled,
      reminderEnabled: reminderEnabled ?? this.reminderEnabled,
      reminderHour: reminderHour ?? this.reminderHour,
      reminderMinute: reminderMinute ?? this.reminderMinute,
      reminderDaysMask: reminderDaysMask ?? this.reminderDaysMask,
      onboardingCompleted: onboardingCompleted ?? this.onboardingCompleted,
      darkThemeMode: darkThemeMode ?? this.darkThemeMode,
      xpPoints: xpPoints ?? this.xpPoints,
      level: level ?? this.level,
      isPrivate: isPrivate ?? this.isPrivate,
      accentColorHex: accentColorHex ?? this.accentColorHex,
      bodyDiagramGender: bodyDiagramGender ?? this.bodyDiagramGender,
      exerciseDrawingGender: exerciseDrawingGender ?? this.exerciseDrawingGender,
      gymCheckinEnabled: gymCheckinEnabled ?? this.gymCheckinEnabled,
      aiCoachEnabled: aiCoachEnabled ?? this.aiCoachEnabled,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'age': age,
      'gender': gender,
      'fitnessGoal': fitnessGoal,
      'fitnessLevel': fitnessLevel,
      'preferredDuration': preferredDuration,
      'weightKg': weightKg,
      'heightCm': heightCm,
      'useMetric': useMetric ? 1 : 0,
      'soundEnabled': soundEnabled ? 1 : 0,
      'voiceCuesEnabled': voiceCuesEnabled ? 1 : 0,
      'vibrationEnabled': vibrationEnabled ? 1 : 0,
      'reminderEnabled': reminderEnabled ? 1 : 0,
      'reminderHour': reminderHour,
      'reminderMinute': reminderMinute,
      'reminderDaysMask': reminderDaysMask,
      'onboardingCompleted': onboardingCompleted ? 1 : 0,
      'darkThemeMode': darkThemeMode,
      'xpPoints': xpPoints,
      'level': level,
      'isPrivate': isPrivate ? 1 : 0,
      'accentColorHex': accentColorHex,
      'bodyDiagramGender': bodyDiagramGender,
      'exerciseDrawingGender': exerciseDrawingGender,
      'gymCheckinEnabled': gymCheckinEnabled ? 1 : 0,
      'aiCoachEnabled': aiCoachEnabled ? 1 : 0,
    };
  }

  factory UserProfile.fromMap(Map<String, dynamic> map) {
    return UserProfile(
      id: (map['id'] as num?)?.toInt() ?? 1,
      name: map['name'] as String? ?? 'Fitness Athlete',
      age: (map['age'] as num?)?.toInt() ?? 26,
      gender: map['gender'] as String? ?? 'Prefer not to say',
      fitnessGoal: map['fitnessGoal'] as String? ?? 'Build Muscle',
      fitnessLevel: map['fitnessLevel'] as String? ?? 'Beginner',
      preferredDuration: (map['preferredDuration'] as num?)?.toInt() ?? 15,
      weightKg: (map['weightKg'] as num?)?.toDouble() ?? 70.0,
      heightCm: (map['heightCm'] as num?)?.toDouble() ?? 175.0,
      useMetric: (map['useMetric'] as num?) == 1,
      soundEnabled: (map['soundEnabled'] as num?) == 1,
      voiceCuesEnabled: (map['voiceCuesEnabled'] as num?) == 1,
      vibrationEnabled: (map['vibrationEnabled'] as num?) == 1,
      reminderEnabled: (map['reminderEnabled'] as num?) == 1,
      reminderHour: (map['reminderHour'] as num?)?.toInt() ?? 8,
      reminderMinute: (map['reminderMinute'] as num?)?.toInt() ?? 0,
      reminderDaysMask: (map['reminderDaysMask'] as num?)?.toInt() ?? 127,
      onboardingCompleted: (map['onboardingCompleted'] as num?) == 1,
      darkThemeMode: map['darkThemeMode'] as String? ?? 'LIGHT',
      xpPoints: (map['xpPoints'] as num?)?.toInt() ?? 0,
      level: (map['level'] as num?)?.toInt() ?? 1,
      isPrivate: (map['isPrivate'] as num?) == 1,
      accentColorHex: map['accentColorHex'] as String? ?? '#E50914',
      bodyDiagramGender: map['bodyDiagramGender'] as String? ?? 'MALE',
      exerciseDrawingGender: map['exerciseDrawingGender'] as String? ?? 'MALE',
      gymCheckinEnabled: (map['gymCheckinEnabled'] as num?) == 1,
      aiCoachEnabled: (map['aiCoachEnabled'] as num?) == 1,
    );
  }
}
