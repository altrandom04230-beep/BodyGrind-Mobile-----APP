/// Exercise domain model matching ExerciseEntity in Entities.kt
class Exercise {
  final String id;
  final String name;
  final String category;
  final String targetMuscles;
  final String difficulty;
  final String defaultType; // TIME, REPS
  final int defaultCount;
  final String instructions;
  final String tips;
  final double caloriesPerMinute;
  final String animationType;
  final bool isFavorite;
  final String primaryMuscles;
  final String secondaryMuscles;
  final String educationalDescription;
  final String functionalBenefit;

  const Exercise({
    required this.id,
    required this.name,
    required this.category,
    required this.targetMuscles,
    required this.difficulty,
    required this.defaultType,
    required this.defaultCount,
    required this.instructions,
    required this.tips,
    this.caloriesPerMinute = 7.5,
    required this.animationType,
    this.isFavorite = false,
    this.primaryMuscles = '',
    this.secondaryMuscles = '',
    this.educationalDescription = '',
    this.functionalBenefit = '',
  });

  Exercise copyWith({
    String? id,
    String? name,
    String? category,
    String? targetMuscles,
    String? difficulty,
    String? defaultType,
    int? defaultCount,
    String? instructions,
    String? tips,
    double? caloriesPerMinute,
    String? animationType,
    bool? isFavorite,
    String? primaryMuscles,
    String? secondaryMuscles,
    String? educationalDescription,
    String? functionalBenefit,
  }) {
    return Exercise(
      id: id ?? this.id,
      name: name ?? this.name,
      category: category ?? this.category,
      targetMuscles: targetMuscles ?? this.targetMuscles,
      difficulty: difficulty ?? this.difficulty,
      defaultType: defaultType ?? this.defaultType,
      defaultCount: defaultCount ?? this.defaultCount,
      instructions: instructions ?? this.instructions,
      tips: tips ?? this.tips,
      caloriesPerMinute: caloriesPerMinute ?? this.caloriesPerMinute,
      animationType: animationType ?? this.animationType,
      isFavorite: isFavorite ?? this.isFavorite,
      primaryMuscles: primaryMuscles ?? this.primaryMuscles,
      secondaryMuscles: secondaryMuscles ?? this.secondaryMuscles,
      educationalDescription: educationalDescription ?? this.educationalDescription,
      functionalBenefit: functionalBenefit ?? this.functionalBenefit,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'category': category,
      'targetMuscles': targetMuscles,
      'difficulty': difficulty,
      'defaultType': defaultType,
      'defaultCount': defaultCount,
      'instructions': instructions,
      'tips': tips,
      'caloriesPerMinute': caloriesPerMinute,
      'animationType': animationType,
      'isFavorite': isFavorite ? 1 : 0,
      'primaryMuscles': primaryMuscles,
      'secondaryMuscles': secondaryMuscles,
      'educationalDescription': educationalDescription,
      'functionalBenefit': functionalBenefit,
    };
  }

  factory Exercise.fromMap(Map<String, dynamic> map) {
    return Exercise(
      id: map['id'] as String,
      name: map['name'] as String,
      category: map['category'] as String,
      targetMuscles: map['targetMuscles'] as String,
      difficulty: map['difficulty'] as String,
      defaultType: map['defaultType'] as String,
      defaultCount: (map['defaultCount'] as num).toInt(),
      instructions: map['instructions'] as String,
      tips: map['tips'] as String,
      caloriesPerMinute: (map['caloriesPerMinute'] as num?)?.toDouble() ?? 7.5,
      animationType: map['animationType'] as String,
      isFavorite: (map['isFavorite'] as num?) == 1,
      primaryMuscles: map['primaryMuscles'] as String? ?? '',
      secondaryMuscles: map['secondaryMuscles'] as String? ?? '',
      educationalDescription: map['educationalDescription'] as String? ?? '',
      functionalBenefit: map['functionalBenefit'] as String? ?? '',
    );
  }
}
