/// Smart Meal domain models matching SmartMealModels.kt
enum MealSlot {
  breakfast('breakfast', 'Breakfast', 500, 40),
  morningSnack('morning_snack', 'Morning Snack', 300, 25),
  lunch('lunch', 'Lunch', 650, 50),
  afternoonSnack('afternoon_snack', 'Afternoon Snack', 350, 30),
  dinner('dinner', 'Dinner', 600, 45);

  final String id;
  final String displayName;
  final int suggestedKcal;
  final int suggestedProteinG;

  const MealSlot(this.id, this.displayName, this.suggestedKcal, this.suggestedProteinG);

  static MealSlot fromString(String val) {
    return MealSlot.values.firstWhere(
      (s) => s.name.toUpperCase() == val.toUpperCase() || s.id == val,
      orElse: () => MealSlot.lunch,
    );
  }
}

enum DayOfWeekPlan {
  monday(1, 'Mon', 'Monday'),
  tuesday(2, 'Tue', 'Tuesday'),
  wednesday(3, 'Wed', 'Wednesday'),
  thursday(4, 'Thu', 'Thursday'),
  friday(5, 'Fri', 'Friday'),
  saturday(6, 'Sat', 'Saturday'),
  sunday(7, 'Sun', 'Sunday');

  final int dayNumber;
  final String shortName;
  final String fullName;

  const DayOfWeekPlan(this.dayNumber, this.shortName, this.fullName);

  static DayOfWeekPlan fromDateTime(DateTime dt) {
    switch (dt.weekday) {
      case DateTime.monday:
        return DayOfWeekPlan.monday;
      case DateTime.tuesday:
        return DayOfWeekPlan.tuesday;
      case DateTime.wednesday:
        return DayOfWeekPlan.wednesday;
      case DateTime.thursday:
        return DayOfWeekPlan.thursday;
      case DateTime.friday:
        return DayOfWeekPlan.friday;
      case DateTime.saturday:
        return DayOfWeekPlan.saturday;
      case DateTime.sunday:
        return DayOfWeekPlan.sunday;
      default:
        return DayOfWeekPlan.monday;
    }
  }

  static DayOfWeekPlan today() => fromDateTime(DateTime.now());

  static DayOfWeekPlan fromString(String val) {
    return DayOfWeekPlan.values.firstWhere(
      (d) => d.name.toUpperCase() == val.toUpperCase(),
      orElse: () => DayOfWeekPlan.monday,
    );
  }
}

class DaySlotPlan {
  final MealSlot slot;
  final String name;
  final int targetKcal;
  final int targetProteinG;

  const DaySlotPlan({
    required this.slot,
    required this.name,
    required this.targetKcal,
    required this.targetProteinG,
  });

  String get recipeId => slot.id;
  String get recipeName => name;
  int get calories => targetKcal;
  int get proteinGrams => targetProteinG;

  DaySlotPlan copyWith({
    MealSlot? slot,
    String? name,
    int? targetKcal,
    int? targetProteinG,
  }) {
    return DaySlotPlan(
      slot: slot ?? this.slot,
      name: name ?? this.name,
      targetKcal: targetKcal ?? this.targetKcal,
      targetProteinG: targetProteinG ?? this.targetProteinG,
    );
  }

  Map<String, dynamic> toJson() => {
        'slot': slot.name,
        'name': name,
        'targetKcal': targetKcal,
        'targetProteinG': targetProteinG,
      };

  factory DaySlotPlan.fromJson(Map<String, dynamic> json) {
    final slotType = MealSlot.fromString(json['slot'] as String? ?? 'lunch');
    return DaySlotPlan(
      slot: slotType,
      name: json['name'] as String? ?? slotType.displayName,
      targetKcal: (json['targetKcal'] as num?)?.toInt() ?? slotType.suggestedKcal,
      targetProteinG: (json['targetProteinG'] as num?)?.toInt() ?? slotType.suggestedProteinG,
    );
  }
}

class DayMealPlan {
  final DayOfWeekPlan day;
  final List<DaySlotPlan> slots;

  DayMealPlan({
    required this.day,
    List<DaySlotPlan>? slots,
  }) : slots = slots ??
            MealSlot.values
                .map((s) => DaySlotPlan(
                      slot: s,
                      name: s.displayName,
                      targetKcal: s.suggestedKcal,
                      targetProteinG: s.suggestedProteinG,
                    ))
                .toList();

  int get totalTargetKcal => slots.fold(0, (sum, s) => sum + s.targetKcal);
  int get totalTargetProteinG => slots.fold(0, (sum, s) => sum + s.targetProteinG);

  DaySlotPlan findSlot(MealSlot slot) {
    return slots.firstWhere((s) => s.slot == slot,
        orElse: () => DaySlotPlan(
              slot: slot,
              name: slot.displayName,
              targetKcal: slot.suggestedKcal,
              targetProteinG: slot.suggestedProteinG,
            ));
  }

  DayMealPlan copyWith({DayOfWeekPlan? day, List<DaySlotPlan>? slots}) {
    return DayMealPlan(
      day: day ?? this.day,
      slots: slots ?? this.slots,
    );
  }
}

class LoggedMeal {
  final String id;
  final String name;
  final MealSlot slot;
  final int calories;
  final int proteinG;
  final int carbsG;
  final int fatsG;
  final int timestamp;
  final String portion;
  final List<String> dietaryTags;

  const LoggedMeal({
    required this.id,
    required this.name,
    required this.slot,
    required this.calories,
    this.proteinG = 0,
    this.carbsG = 0,
    this.fatsG = 0,
    required this.timestamp,
    this.portion = '1 serving',
    this.dietaryTags = const [],
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'slot': slot.name,
        'calories': calories,
        'proteinG': proteinG,
        'carbsG': carbsG,
        'fatsG': fatsG,
        'timestamp': timestamp,
        'portion': portion,
        'dietaryTags': dietaryTags,
      };

  factory LoggedMeal.fromJson(Map<String, dynamic> json) {
    return LoggedMeal(
      id: json['id'] as String,
      name: json['name'] as String,
      slot: MealSlot.fromString(json['slot'] as String? ?? 'lunch'),
      calories: (json['calories'] as num).toInt(),
      proteinG: (json['proteinG'] as num?)?.toInt() ?? 0,
      carbsG: (json['carbsG'] as num?)?.toInt() ?? 0,
      fatsG: (json['fatsG'] as num?)?.toInt() ?? 0,
      timestamp: (json['timestamp'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
      portion: json['portion'] as String? ?? '1 serving',
      dietaryTags: (json['dietaryTags'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
    );
  }
}

class SmartMealRecipe {
  final String id;
  final String name;
  final MealSlot defaultSlot;
  final int calories;
  final int proteinG;
  final int carbsG;
  final int fatsG;
  final int prepTimeMinutes;
  final String description;
  final List<String> ingredients;
  final List<String> dietaryTags;
  final List<String> allergens;
  final String iconCategory; // CHICKEN, SALMON, BEEF, EGG, OATS, SHAKE, PLANT

  const SmartMealRecipe({
    required this.id,
    required this.name,
    required this.defaultSlot,
    required this.calories,
    required this.proteinG,
    required this.carbsG,
    required this.fatsG,
    required this.prepTimeMinutes,
    required this.description,
    required this.ingredients,
    required this.dietaryTags,
    this.allergens = const [],
    this.iconCategory = 'CHICKEN',
  });

  int get proteinGrams => proteinG;
  int get carbsGrams => carbsG;
  int get fatsGrams => fatsG;
  List<String> get instructions => const [
        'Measure and prepare ingredients as listed.',
        'Cook or combine ingredients according to standard prep guidelines.',
        'Season to taste and serve.'
      ];
}

class MealGoalSettings {
  final String goalName;
  final int dailyCalorieTarget;
  final int proteinTargetG;
  final int carbsTargetG;
  final int fatsTargetG;
  final int targetMealCount;

  const MealGoalSettings({
    this.goalName = 'Build Muscle',
    this.dailyCalorieTarget = 2400,
    this.proteinTargetG = 180,
    this.carbsTargetG = 260,
    this.fatsTargetG = 70,
    this.targetMealCount = 5,
    int? calorieGoal,
    int? proteinGoalGrams,
    int? carbsGoalGrams,
    int? fatGoalGrams,
    int? waterGoalMl,
    String? goalType,
  })  : goalName = goalType ?? goalName,
        dailyCalorieTarget = calorieGoal ?? dailyCalorieTarget,
        proteinTargetG = proteinGoalGrams ?? proteinTargetG,
        carbsTargetG = carbsGoalGrams ?? carbsTargetG,
        fatsTargetG = fatGoalGrams ?? fatsTargetG;

  int get calorieGoal => dailyCalorieTarget;
  int get proteinGoalGrams => proteinTargetG;
  int get carbsGoalGrams => carbsTargetG;
  int get fatGoalGrams => fatsTargetG;
  int get waterGoalMl => 2500;
  String get goalType => goalName;

  MealGoalSettings copyWith({
    String? goalName,
    int? dailyCalorieTarget,
    int? proteinTargetG,
    int? carbsTargetG,
    int? fatsTargetG,
    int? targetMealCount,
  }) {
    return MealGoalSettings(
      goalName: goalName ?? this.goalName,
      dailyCalorieTarget: dailyCalorieTarget ?? this.dailyCalorieTarget,
      proteinTargetG: proteinTargetG ?? this.proteinTargetG,
      carbsTargetG: carbsTargetG ?? this.carbsTargetG,
      fatsTargetG: fatsTargetG ?? this.fatsTargetG,
      targetMealCount: targetMealCount ?? this.targetMealCount,
    );
  }
}
