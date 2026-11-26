import 'dart:async';
import 'dart:convert';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/meal_models.dart';

/// Smart Meals repository replacing SmartMealsRepository.kt
class SmartMealsRepository {
  static const String _weeklyPlansKey = 'weekly_meal_plans_v2_json';
  static const String _loggedMealsKey = 'all_logged_meals_v2_json';
  static const String _waterDateKey = 'water_date';
  static const String _waterMlKey = 'water_ml';
  static const String _activeFiltersKey = 'active_filters';
  static const String _activeAllergiesKey = 'active_allergies';

  final _weeklyPlansController = StreamController<Map<DayOfWeekPlan, DayMealPlan>>.broadcast();
  final _allLoggedMealsController = StreamController<List<LoggedMeal>>.broadcast();
  final _loggedMealsController = StreamController<List<LoggedMeal>>.broadcast();
  final _goalSettingsController = StreamController<MealGoalSettings>.broadcast();
  final _activeFiltersController = StreamController<Set<String>>.broadcast();
  final _activeAllergiesController = StreamController<Set<String>>.broadcast();
  final _waterIntakeMlController = StreamController<int>.broadcast();

  Map<DayOfWeekPlan, DayMealPlan> _weeklyPlans = {};
  List<LoggedMeal> _allLoggedMeals = [];
  MealGoalSettings _goalSettings = const MealGoalSettings();
  Set<String> _activeFilters = {};
  Set<String> _activeAllergies = {};
  int _waterIntakeMl = 0;

  String get todayKey => DateFormat('yyyy-MM-dd').format(DateTime.now());

  Stream<Map<DayOfWeekPlan, DayMealPlan>> get weeklyPlans => _weeklyPlansController.stream;
  Stream<List<LoggedMeal>> get allLoggedMeals => _allLoggedMealsController.stream;
  Stream<List<LoggedMeal>> get loggedMeals => _loggedMealsController.stream;
  Stream<MealGoalSettings> get goalSettings => _goalSettingsController.stream;
  Stream<Set<String>> get activeFilters => _activeFiltersController.stream;
  Stream<Set<String>> get activeAllergies => _activeAllergiesController.stream;
  Stream<int> get waterIntakeMl => _waterIntakeMlController.stream;

  Map<DayOfWeekPlan, DayMealPlan> get currentWeeklyPlans => Map.unmodifiable(_weeklyPlans);
  List<LoggedMeal> get currentLoggedMeals => List.unmodifiable(_allLoggedMeals.where((m) => formatDateKey(m.timestamp) == todayKey));
  MealGoalSettings get currentGoalSettings => _goalSettings;
  Set<String> get currentActiveFilters => Set.unmodifiable(_activeFilters);
  Set<String> get currentActiveAllergies => Set.unmodifiable(_activeAllergies);
  int get currentWaterIntakeMl => _waterIntakeMl;
  int get currentWaterIntake => _waterIntakeMl;
  List<SmartMealRecipe> get allRecipes => curatedRecipes;

  SmartMealsRepository() {
    _loadAll();
  }

  Future<void> _loadAll() async {
    final prefs = await SharedPreferences.getInstance();
    _loadSettings(prefs);
    _loadWeeklyPlans(prefs);
    _loadLoggedMeals(prefs);
    _loadFilters(prefs);
    _loadWater(prefs);
  }

  void _loadSettings(SharedPreferences prefs) {
    _goalSettings = MealGoalSettings(
      goalName: prefs.getString('goal_name') ?? 'Build Muscle',
      dailyCalorieTarget: prefs.getInt('daily_kcal') ?? 2400,
      proteinTargetG: prefs.getInt('protein_g') ?? 180,
      carbsTargetG: prefs.getInt('carbs_g') ?? 260,
      fatsTargetG: prefs.getInt('fats_g') ?? 70,
      targetMealCount: 5,
    );
    _goalSettingsController.add(_goalSettings);
  }

  void _loadWeeklyPlans(SharedPreferences prefs) {
    final jsonStr = prefs.getString(_weeklyPlansKey);
    if (jsonStr == null) {
      _weeklyPlans = {
        for (var day in DayOfWeekPlan.values) day: DayMealPlan(day: day),
      };
      _weeklyPlansController.add(Map.unmodifiable(_weeklyPlans));
      _saveWeeklyPlansToDisk();
      return;
    }

    try {
      final Map<String, dynamic> root = jsonDecode(jsonStr);
      final map = <DayOfWeekPlan, DayMealPlan>{};
      for (final day in DayOfWeekPlan.values) {
        if (root.containsKey(day.name)) {
          final List<dynamic> slotsArr = root[day.name];
          final slotsList = slotsArr.map((e) => DaySlotPlan.fromJson(e as Map<String, dynamic>)).toList();
          final completeSlots = MealSlot.values.map((slot) {
            return slotsList.firstWhere((s) => s.slot == slot,
                orElse: () => DaySlotPlan(
                      slot: slot,
                      name: slot.displayName,
                      targetKcal: slot.suggestedKcal,
                      targetProteinG: slot.suggestedProteinG,
                    ));
          }).toList();
          map[day] = DayMealPlan(day: day, slots: completeSlots);
        } else {
          map[day] = DayMealPlan(day: day);
        }
      }
      _weeklyPlans = map;
    } catch (_) {
      _weeklyPlans = {for (var day in DayOfWeekPlan.values) day: DayMealPlan(day: day)};
    }
    _weeklyPlansController.add(Map.unmodifiable(_weeklyPlans));
  }

  Future<void> _saveWeeklyPlansToDisk() async {
    final prefs = await SharedPreferences.getInstance();
    final map = <String, dynamic>{};
    for (final entry in _weeklyPlans.entries) {
      map[entry.key.name] = entry.value.slots.map((s) => s.toJson()).toList();
    }
    await prefs.setString(_weeklyPlansKey, jsonEncode(map));
  }

  DayMealPlan getDayPlan(DayOfWeekPlan day) {
    return _weeklyPlans[day] ?? DayMealPlan(day: day);
  }

  Future<void> updateSlotPlan(DayOfWeekPlan day, MealSlot slot, String name, int targetKcal, int targetProteinG) async {
    final dayPlan = _weeklyPlans[day] ?? DayMealPlan(day: day);
    final updatedSlots = dayPlan.slots.map((s) {
      if (s.slot == slot) {
        return s.copyWith(name: name, targetKcal: targetKcal, targetProteinG: targetProteinG);
      }
      return s;
    }).toList();

    _weeklyPlans[day] = dayPlan.copyWith(slots: updatedSlots);
    _weeklyPlansController.add(Map.unmodifiable(_weeklyPlans));
    await _saveWeeklyPlansToDisk();
  }

  void _loadFilters(SharedPreferences prefs) {
    _activeFilters = (prefs.getStringList(_activeFiltersKey) ?? []).toSet();
    _activeAllergies = (prefs.getStringList(_activeAllergiesKey) ?? []).toSet();
    _activeFiltersController.add(Set.unmodifiable(_activeFilters));
    _activeAllergiesController.add(Set.unmodifiable(_activeAllergies));
  }

  void _loadWater(SharedPreferences prefs) {
    final savedDate = prefs.getString(_waterDateKey);
    if (savedDate == todayKey) {
      _waterIntakeMl = prefs.getInt(_waterMlKey) ?? 0;
    } else {
      _waterIntakeMl = 0;
    }
    _waterIntakeMlController.add(_waterIntakeMl);
  }

  void _loadLoggedMeals(SharedPreferences prefs) {
    final historyJson = prefs.getString(_loggedMealsKey);
    if (historyJson != null) {
      try {
        final List<dynamic> arr = jsonDecode(historyJson);
        _allLoggedMeals = arr.map((e) => LoggedMeal.fromJson(e as Map<String, dynamic>)).toList();
      } catch (_) {
        _allLoggedMeals = [];
      }
    } else {
      _allLoggedMeals = [];
    }
    _allLoggedMealsController.add(List.unmodifiable(_allLoggedMeals));
    _updateTodayMealsFlow();
  }

  void _updateTodayMealsFlow() {
    final todayMeals = _allLoggedMeals.where((m) => formatDateKey(m.timestamp) == todayKey).toList();
    _loggedMealsController.add(List.unmodifiable(todayMeals));
  }

  String formatDateKey(int timestamp) {
    return DateFormat('yyyy-MM-dd').format(DateTime.fromMillisecondsSinceEpoch(timestamp));
  }

  List<LoggedMeal> getMealsForDateKey(String dateKey) {
    return _allLoggedMeals.where((m) => formatDateKey(m.timestamp) == dateKey).toList();
  }

  Future<void> logMeal(LoggedMeal meal) async {
    _allLoggedMeals.add(meal);
    _allLoggedMealsController.add(List.unmodifiable(_allLoggedMeals));
    _updateTodayMealsFlow();
    await _saveAllMealsToDisk();
  }

  Future<void> deleteMeal(String mealId) async {
    _allLoggedMeals.removeWhere((m) => m.id == mealId);
    _allLoggedMealsController.add(List.unmodifiable(_allLoggedMeals));
    _updateTodayMealsFlow();
    await _saveAllMealsToDisk();
  }

  Future<void> clearMealsForDate(String dateKey) async {
    _allLoggedMeals.removeWhere((m) => formatDateKey(m.timestamp) == dateKey);
    _allLoggedMealsController.add(List.unmodifiable(_allLoggedMeals));
    _updateTodayMealsFlow();
    await _saveAllMealsToDisk();
  }

  Future<void> _saveAllMealsToDisk() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = jsonEncode(_allLoggedMeals.map((m) => m.toJson()).toList());
    await prefs.setString(_loggedMealsKey, jsonStr);
  }

  Future<void> updateGoals(String goalName, int dailyKcal, int proteinG, int carbsG, int fatsG) async {
    _goalSettings = MealGoalSettings(
      goalName: goalName,
      dailyCalorieTarget: dailyKcal,
      proteinTargetG: proteinG,
      carbsTargetG: carbsG,
      fatsTargetG: fatsG,
      targetMealCount: 5,
    );
    _goalSettingsController.add(_goalSettings);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('goal_name', goalName);
    await prefs.setInt('daily_kcal', dailyKcal);
    await prefs.setInt('protein_g', proteinG);
    await prefs.setInt('carbs_g', carbsG);
    await prefs.setInt('fats_g', fatsG);
  }

  Future<void> updateGoalSettings(MealGoalSettings settings) async {
    await updateGoals(
      settings.goalName,
      settings.dailyCalorieTarget,
      settings.proteinTargetG,
      settings.carbsTargetG,
      settings.fatsTargetG,
    );
  }

  Future<void> toggleDietaryFilter(String filter) async {
    if (_activeFilters.contains(filter)) {
      _activeFilters.remove(filter);
    } else {
      _activeFilters.add(filter);
    }
    _activeFiltersController.add(Set.unmodifiable(_activeFilters));
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_activeFiltersKey, _activeFilters.toList());
  }

  Future<void> toggleAllergyFilter(String allergy) async {
    if (_activeAllergies.contains(allergy)) {
      _activeAllergies.remove(allergy);
    } else {
      _activeAllergies.add(allergy);
    }
    _activeAllergiesController.add(Set.unmodifiable(_activeAllergies));
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(_activeAllergiesKey, _activeAllergies.toList());
  }

  Future<void> clearAllFilters() async {
    _activeFilters.clear();
    _activeAllergies.clear();
    _activeFiltersController.add(Set.unmodifiable(_activeFilters));
    _activeAllergiesController.add(Set.unmodifiable(_activeAllergies));
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_activeFiltersKey);
    await prefs.remove(_activeAllergiesKey);
  }

  Future<void> addWater(int ml) async {
    _waterIntakeMl += ml;
    _waterIntakeMlController.add(_waterIntakeMl);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_waterDateKey, todayKey);
    await prefs.setInt(_waterMlKey, _waterIntakeMl);
  }

  Future<void> resetWater() async {
    _waterIntakeMl = 0;
    _waterIntakeMlController.add(_waterIntakeMl);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_waterDateKey, todayKey);
    await prefs.setInt(_waterMlKey, 0);
  }

  // Curated Recipes matching Kotlin catalog
  List<SmartMealRecipe> get curatedRecipes => const [
        // Breakfast
        SmartMealRecipe(
          id: 'oats_whey_berries',
          name: 'Power Oats & Whey',
          defaultSlot: MealSlot.breakfast,
          calories: 450,
          proteinG: 38,
          carbsG: 56,
          fatsG: 9,
          prepTimeMinutes: 5,
          description: 'Rolled steel-cut oats stirred with whey isolate, organic blueberries, and almond butter.',
          ingredients: ['60g Rolled Oats', '1 scoop Whey Isolate', '100g Blueberries', '15g Natural Almond Butter', '200ml Almond Milk'],
          dietaryTags: ['High-Protein', 'Vegetarian', 'Clean Eating'],
          allergens: ['Dairy', 'Nuts'],
          iconCategory: 'OATS',
        ),
        SmartMealRecipe(
          id: 'egg_avocado_toast',
          name: 'Egg White & Avocado Sourdough',
          defaultSlot: MealSlot.breakfast,
          calories: 380,
          proteinG: 32,
          carbsG: 30,
          fatsG: 14,
          prepTimeMinutes: 8,
          description: '4 fluffy egg whites plus 1 whole egg served over toasted sourdough with ripe avocado slices.',
          ingredients: ['4 Egg Whites', '1 Whole Egg', '1 slice Sourdough Bread', '40g Sliced Avocado', 'Sea Salt & Red Pepper Flakes'],
          dietaryTags: ['High-Protein', 'Dairy-Free', 'Vegetarian'],
          allergens: ['Eggs', 'Gluten'],
          iconCategory: 'EGG',
        ),
        SmartMealRecipe(
          id: 'greek_yogurt_protein_bowl',
          name: 'Greek Yogurt Power Parfait',
          defaultSlot: MealSlot.breakfast,
          calories: 340,
          proteinG: 35,
          carbsG: 34,
          fatsG: 6,
          prepTimeMinutes: 4,
          description: 'Thick 0% Greek yogurt layered with whey protein, chia seeds, fresh strawberries, and honey drizzle.',
          ingredients: ['200g 0% Greek Yogurt', '1/2 scoop Vanilla Whey', '80g Fresh Strawberries', '10g Chia Seeds', '1 tsp Raw Honey'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Vegetarian'],
          allergens: ['Dairy'],
          iconCategory: 'OATS',
        ),
        SmartMealRecipe(
          id: 'vegan_protein_pancakes',
          name: 'Plant Protein Banana Pancakes',
          defaultSlot: MealSlot.breakfast,
          calories: 420,
          proteinG: 30,
          carbsG: 58,
          fatsG: 7,
          prepTimeMinutes: 12,
          description: 'Oat flour and pea protein batter cooked golden, topped with banana medallions and sugar-free syrup.',
          ingredients: ['50g Oat Flour', '1 scoop Pea Protein', '1 Ripe Banana', '150ml Soy Milk', '1/2 tsp Cinnamon'],
          dietaryTags: ['High-Protein', 'Vegan', 'Vegetarian', 'Dairy-Free'],
          allergens: ['Soy'],
          iconCategory: 'PLANT',
        ),

        // Morning Snack
        SmartMealRecipe(
          id: 'tuna_rice_cakes',
          name: 'Tuna Crunch Rice Cakes',
          defaultSlot: MealSlot.morningSnack,
          calories: 260,
          proteinG: 30,
          carbsG: 25,
          fatsG: 3,
          prepTimeMinutes: 4,
          description: 'Albacore wild tuna mixed with Dijon mustard and cracked pepper atop crispy brown rice cakes.',
          ingredients: ['1 can (120g) Albacore Tuna in Water', '3 Brown Rice Cakes', '1 tsp Dijon Mustard', 'Lemon Juice & Black Pepper'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Dairy-Free', 'Low-Carb'],
          allergens: ['Fish'],
          iconCategory: 'SALMON',
        ),
        SmartMealRecipe(
          id: 'cottage_cheese_walnut',
          name: 'Cottage Cheese & Honey Cup',
          defaultSlot: MealSlot.morningSnack,
          calories: 280,
          proteinG: 28,
          carbsG: 18,
          fatsG: 9,
          prepTimeMinutes: 2,
          description: 'Creamy low-fat cottage cheese paired with crushed walnuts and fresh blueberries.',
          ingredients: ['200g Low-Fat Cottage Cheese', '15g Crushed Walnuts', '50g Blueberries', '1/2 tsp Honey'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Vegetarian', 'Keto-Friendly'],
          allergens: ['Dairy', 'Nuts'],
          iconCategory: 'OATS',
        ),
        SmartMealRecipe(
          id: 'edamame_sea_salt',
          name: 'Steamed Edamame Power Bowl',
          defaultSlot: MealSlot.morningSnack,
          calories: 240,
          proteinG: 22,
          carbsG: 16,
          fatsG: 8,
          prepTimeMinutes: 5,
          description: 'Whole organic edamame pods steamed with mineral sea salt and sesame oil drops.',
          ingredients: ['180g Edamame in Pods', 'Coarse Sea Salt', '1/4 tsp Toasted Sesame Oil'],
          dietaryTags: ['High-Protein', 'Vegan', 'Vegetarian', 'Gluten-Free', 'Dairy-Free'],
          allergens: ['Soy'],
          iconCategory: 'PLANT',
        ),

        // Lunch
        SmartMealRecipe(
          id: 'grilled_chicken_brown_rice',
          name: 'Grilled Chicken & Brown Rice Bowl',
          defaultSlot: MealSlot.lunch,
          calories: 560,
          proteinG: 52,
          carbsG: 58,
          fatsG: 12,
          prepTimeMinutes: 15,
          description: 'Tender grilled chicken breast seasoned with herbs, served over brown jasmine rice and steamed broccoli.',
          ingredients: ['200g Lean Chicken Breast', '150g Cooked Brown Rice', '120g Steamed Broccoli', '1 tsp Extra Virgin Olive Oil', 'Garlic Herb Rub'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Dairy-Free', 'Clean Eating'],
          allergens: [],
          iconCategory: 'CHICKEN',
        ),
        SmartMealRecipe(
          id: 'seared_salmon_sweet_potato',
          name: 'Seared Salmon & Sweet Potato',
          defaultSlot: MealSlot.lunch,
          calories: 590,
          proteinG: 46,
          carbsG: 48,
          fatsG: 22,
          prepTimeMinutes: 18,
          description: 'Pan-seared Atlantic salmon fillet with roasted sweet potato cubes and tender grilled asparagus spears.',
          ingredients: ['180g Atlantic Salmon Fillet', '160g Roasted Sweet Potato', '100g Asparagus', 'Lemon Wedges', '1 tsp Olive Oil'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Dairy-Free', 'Omega-3 Rich'],
          allergens: ['Fish'],
          iconCategory: 'SALMON',
        ),
        SmartMealRecipe(
          id: 'turkey_quinoa_chili',
          name: 'Lean Turkey Quinoa Chili',
          defaultSlot: MealSlot.lunch,
          calories: 520,
          proteinG: 48,
          carbsG: 46,
          fatsG: 13,
          prepTimeMinutes: 20,
          description: '93% lean ground turkey simmered with black beans, diced bell peppers, roasted tomatoes, and quinoa.',
          ingredients: ['180g Extra Lean Ground Turkey', '80g Black Beans', '50g Cooked Quinoa', 'Diced Tomatoes & Bell Peppers', 'Chili Spice Blend'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Dairy-Free'],
          allergens: [],
          iconCategory: 'BEEF',
        ),
        SmartMealRecipe(
          id: 'tofu_edamame_teriyaki_bowl',
          name: 'Crispy Tofu & Quinoa Buddha Bowl',
          defaultSlot: MealSlot.lunch,
          calories: 490,
          proteinG: 36,
          carbsG: 54,
          fatsG: 15,
          prepTimeMinutes: 15,
          description: 'Golden air-fried extra-firm tofu with quinoa, edamame, sliced avocado, and coconut amino glaze.',
          ingredients: ['200g Extra-Firm Tofu', '100g Cooked Quinoa', '60g Edamame', '30g Avocado', 'Tamari Coconut Amino Sauce'],
          dietaryTags: ['High-Protein', 'Vegan', 'Vegetarian', 'Gluten-Free', 'Dairy-Free'],
          allergens: ['Soy'],
          iconCategory: 'PLANT',
        ),

        // Afternoon Snack
        SmartMealRecipe(
          id: 'bodygrind_anabolic_shake',
          name: 'BodyGrind Anabolic Post-Workout Shake',
          defaultSlot: MealSlot.afternoonSnack,
          calories: 340,
          proteinG: 42,
          carbsG: 28,
          fatsG: 6,
          prepTimeMinutes: 3,
          description: 'Double scoop whey isolate blended with half a banana, powdered peanut butter, and unsweetened almond milk.',
          ingredients: ['40g Whey Isolate', '1/2 Frozen Banana', '15g PB Powder', '300ml Unsweetened Almond Milk', 'Ice Cubes'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Vegetarian', 'Fast-Absorbing'],
          allergens: ['Dairy', 'Nuts'],
          iconCategory: 'SHAKE',
        ),
        SmartMealRecipe(
          id: 'beef_jerky_almonds',
          name: 'Grass-Fed Beef Jerky & Raw Almonds',
          defaultSlot: MealSlot.afternoonSnack,
          calories: 290,
          proteinG: 32,
          carbsG: 12,
          fatsG: 12,
          prepTimeMinutes: 1,
          description: 'Natural smoked grass-fed beef jerky paired with roasted unsalted California almonds.',
          ingredients: ['60g Zero-Sugar Grass-Fed Beef Jerky', '20g Raw Almonds'],
          dietaryTags: ['High-Protein', 'Keto', 'Gluten-Free', 'Dairy-Free', 'Low-Carb'],
          allergens: ['Nuts'],
          iconCategory: 'BEEF',
        ),
        SmartMealRecipe(
          id: 'protein_chia_pudding',
          name: 'Chocolate Protein Chia Pudding',
          defaultSlot: MealSlot.afternoonSnack,
          calories: 280,
          proteinG: 26,
          carbsG: 20,
          fatsG: 10,
          prepTimeMinutes: 5,
          description: 'Chia seeds soaked in almond milk, mixed with rich Dutch chocolate plant protein and cacao nibs.',
          ingredients: ['20g Chia Seeds', '1 scoop Chocolate Plant Protein', '200ml Almond Milk', '5g Raw Cacao Nibs'],
          dietaryTags: ['High-Protein', 'Vegan', 'Vegetarian', 'Gluten-Free', 'Dairy-Free'],
          allergens: [],
          iconCategory: 'PLANT',
        ),

        // Dinner
        SmartMealRecipe(
          id: 'sirloin_roasted_potatoes',
          name: 'Lean Sirloin & Herb Baby Potatoes',
          defaultSlot: MealSlot.dinner,
          calories: 620,
          proteinG: 56,
          carbsG: 42,
          fatsG: 21,
          prepTimeMinutes: 20,
          description: 'Seared top sirloin steak cut against the grain, served with rosemary baby potatoes and haricot verts.',
          ingredients: ['200g Lean Top Sirloin Steak', '160g Rosemary Roasted Potatoes', '100g Green Beans', 'Garlic Clove & Olive Oil Spray'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Dairy-Free', 'Iron-Rich'],
          allergens: [],
          iconCategory: 'BEEF',
        ),
        SmartMealRecipe(
          id: 'citrus_garlic_shrimp',
          name: 'Citrus Garlic Shrimp Stir-Fry',
          defaultSlot: MealSlot.dinner,
          calories: 480,
          proteinG: 45,
          carbsG: 48,
          fatsG: 10,
          prepTimeMinutes: 14,
          description: 'Jumbo wild shrimp sautéed in minced garlic and lime zest over steamed jasmine rice with snap peas.',
          ingredients: ['220g Wild Gulf Shrimp', '140g Cooked Jasmine Rice', '80g Sugar Snap Peas', '1 tsp Sesame Oil', 'Fresh Lime & Garlic'],
          dietaryTags: ['High-Protein', 'Gluten-Free', 'Dairy-Free', 'Low-Fat'],
          allergens: ['Shellfish'],
          iconCategory: 'SALMON',
        ),
        SmartMealRecipe(
          id: 'ground_chicken_lettuce_wraps',
          name: 'Asian Chicken & Water Chestnut Wraps',
          defaultSlot: MealSlot.dinner,
          calories: 430,
          proteinG: 48,
          carbsG: 22,
          fatsG: 14,
          prepTimeMinutes: 15,
          description: 'Lean minced chicken breast sautéed with ginger, scallions, and water chestnuts in crisp butter lettuce cups.',
          ingredients: ['220g Lean Minced Chicken Breast', '4 Butter Lettuce Leaves', '40g Diced Water Chestnuts', 'Tamari & Grated Ginger'],
          dietaryTags: ['High-Protein', 'Low-Carb', 'Keto-Friendly', 'Dairy-Free', 'Gluten-Free'],
          allergens: ['Soy'],
          iconCategory: 'CHICKEN',
        ),
        SmartMealRecipe(
          id: 'lentil_shepherds_pie',
          name: 'High-Protein Lentil Shepherd\'s Bowl',
          defaultSlot: MealSlot.dinner,
          calories: 520,
          proteinG: 34,
          carbsG: 68,
          fatsG: 11,
          prepTimeMinutes: 25,
          description: 'Rich brown lentils stewed with carrots and peas, topped with mashed golden Yukon potato whip.',
          ingredients: ['180g Cooked Brown Lentils', '160g Whipped Yukon Potatoes', '60g Carrots & Peas', 'Nutritional Yeast & Herbs'],
          dietaryTags: ['High-Protein', 'Vegan', 'Vegetarian', 'Gluten-Free', 'Dairy-Free'],
          allergens: [],
          iconCategory: 'PLANT',
        ),
      ];

  void dispose() {
    _weeklyPlansController.close();
    _allLoggedMealsController.close();
    _loggedMealsController.close();
    _goalSettingsController.close();
    _activeFiltersController.close();
    _activeAllergiesController.close();
    _waterIntakeMlController.close();
  }
}
