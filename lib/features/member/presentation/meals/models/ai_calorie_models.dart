import '../../../../../data/models/meal_models.dart';

/// Single item detected by Gemini AI Calorie Tracker
class AiDetectedFoodItem {
  final String name;
  final String portion;
  final int calories;
  final int proteinG;
  final int carbsG;
  final int fatG;

  const AiDetectedFoodItem({
    required this.name,
    this.portion = '1 serving',
    this.calories = 0,
    this.proteinG = 0,
    this.carbsG = 0,
    this.fatG = 0,
  });

  AiDetectedFoodItem copyWith({
    String? name,
    String? portion,
    int? calories,
    int? proteinG,
    int? carbsG,
    int? fatG,
  }) {
    return AiDetectedFoodItem(
      name: name ?? this.name,
      portion: portion ?? this.portion,
      calories: calories ?? this.calories,
      proteinG: proteinG ?? this.proteinG,
      carbsG: carbsG ?? this.carbsG,
      fatG: fatG ?? this.fatG,
    );
  }

  Map<String, dynamic> toJson() => {
        'name': name,
        'portion': portion,
        'calories': calories,
        'protein_g': proteinG,
        'carbs_g': carbsG,
        'fat_g': fatG,
      };

  factory AiDetectedFoodItem.fromJson(Map<String, dynamic> json) {
    return AiDetectedFoodItem(
      name: json['name'] as String? ?? 'Food Item',
      portion: json['portion'] as String? ?? '1 serving',
      calories: (json['calories'] as num?)?.toInt() ?? 0,
      proteinG: (json['protein_g'] as num?)?.toInt() ?? 0,
      carbsG: (json['carbs_g'] as num?)?.toInt() ?? 0,
      fatG: (json['fat_g'] as num?)?.toInt() ?? 0,
    );
  }
}

/// Parsed container response from Gemini AI
class AiCalorieResult {
  final bool isFood;
  final String confidence; // 'high', 'medium', 'low'
  final String? errorMessage;
  final List<AiDetectedFoodItem> items;
  final int totalCalories;
  final int totalProteinG;
  final int totalCarbsG;
  final int totalFatG;
  final MealSlot suggestedSlot;

  const AiCalorieResult({
    this.isFood = true,
    this.confidence = 'high',
    this.errorMessage,
    this.items = const [],
    this.totalCalories = 0,
    this.totalProteinG = 0,
    this.totalCarbsG = 0,
    this.totalFatG = 0,
    this.suggestedSlot = MealSlot.lunch,
  });

  AiCalorieResult copyWith({
    bool? isFood,
    String? confidence,
    String? errorMessage,
    List<AiDetectedFoodItem>? items,
    int? totalCalories,
    int? totalProteinG,
    int? totalCarbsG,
    int? totalFatG,
    MealSlot? suggestedSlot,
  }) {
    return AiCalorieResult(
      isFood: isFood ?? this.isFood,
      confidence: confidence ?? this.confidence,
      errorMessage: errorMessage ?? this.errorMessage,
      items: items ?? this.items,
      totalCalories: totalCalories ?? this.totalCalories,
      totalProteinG: totalProteinG ?? this.totalProteinG,
      totalCarbsG: totalCarbsG ?? this.totalCarbsG,
      totalFatG: totalFatG ?? this.totalFatG,
      suggestedSlot: suggestedSlot ?? this.suggestedSlot,
    );
  }

  factory AiCalorieResult.fromJson(Map<String, dynamic> json) {
    final isFood = json['is_food'] as bool? ?? true;
    final confidence = (json['confidence'] as String? ?? 'high').toLowerCase();
    final errorMessage = json['error_message'] as String?;

    final itemsRaw = json['items'] as List<dynamic>? ?? [];
    final items = itemsRaw
        .map((e) => AiDetectedFoodItem.fromJson(e as Map<String, dynamic>))
        .toList();

    final calcCalories = (json['total_calories'] as num?)?.toInt() ??
        items.fold(0, (sum, i) => sum + i.calories);
    final calcProtein = (json['total_protein_g'] as num?)?.toInt() ??
        items.fold(0, (sum, i) => sum + i.proteinG);
    final calcCarbs = (json['total_carbs_g'] as num?)?.toInt() ??
        items.fold(0, (sum, i) => sum + i.carbsG);
    final calcFat = (json['total_fat_g'] as num?)?.toInt() ??
        items.fold(0, (sum, i) => sum + i.fatG);

    final slotStr = json['suggested_slot'] as String? ?? 'lunch';
    final suggestedSlot = MealSlot.fromString(slotStr);

    return AiCalorieResult(
      isFood: isFood,
      confidence: confidence,
      errorMessage: errorMessage,
      items: items,
      totalCalories: calcCalories,
      totalProteinG: calcProtein,
      totalCarbsG: calcCarbs,
      totalFatG: calcFat,
      suggestedSlot: suggestedSlot,
    );
  }
}

/// UI state for AI Calorie Tracker flow
enum AiCalorieTrackerStatus {
  idle,
  analyzing,
  reviewing,
  saving,
  error,
}
