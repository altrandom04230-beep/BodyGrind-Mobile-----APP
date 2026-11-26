import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/meal_models.dart';
import '../../../../data/providers/app_providers.dart';
import 'widgets/ai_calorie_tracker_section.dart';
import 'widgets/meal_reminders_sheet.dart';
import 'providers/meal_reminder_providers.dart';

class SmartMealsScreen extends ConsumerStatefulWidget {
  const SmartMealsScreen({super.key});

  @override
  ConsumerState<SmartMealsScreen> createState() => _SmartMealsScreenState();
}

class _SmartMealsScreenState extends ConsumerState<SmartMealsScreen> {
  DayOfWeekPlan _selectedDay = DayOfWeekPlan.monday;

  @override
  void initState() {
    super.initState();
    _selectedDay = _getCurrentDayOfWeek();
  }

  DayOfWeekPlan _getCurrentDayOfWeek() {
    switch (DateTime.now().weekday) {
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
      default:
        return DayOfWeekPlan.sunday;
    }
  }

  void _showEditGoalDialog(MealGoalSettings current) {
    int calorieGoal = current.calorieGoal;
    int proteinGoal = current.proteinGoalGrams;
    String goalType = current.goalType;

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: const Text('Nutritional Target', style: TextStyle(fontWeight: FontWeight.bold)),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              DropdownButtonFormField<String>(
                value: goalType,
                decoration: const InputDecoration(labelText: 'Nutrition Goal', isDense: true),
                items: const [
                  DropdownMenuItem(value: 'Muscle Hypertrophy & Strength', child: Text('Hypertrophy (High Protein)')),
                  DropdownMenuItem(value: 'Fat Loss & Lean Definition', child: Text('Fat Loss (Moderate Deficit)')),
                  DropdownMenuItem(value: 'Athletic Conditioning & Endurance', child: Text('Endurance (High Energy)')),
                ],
                onChanged: (val) {
                  if (val != null) setDialogState(() => goalType = val);
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                initialValue: calorieGoal.toString(),
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Daily Calories (kcal)', isDense: true),
                onChanged: (val) {
                  final parsed = int.tryParse(val);
                  if (parsed != null && parsed > 500) calorieGoal = parsed;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                initialValue: proteinGoal.toString(),
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Protein Target (g)', isDense: true),
                onChanged: (val) {
                  final parsed = int.tryParse(val);
                  if (parsed != null && parsed > 20) proteinGoal = parsed;
                },
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx),
              child: const Text('Cancel'),
            ),
            FilledButton(
              style: FilledButton.styleFrom(backgroundColor: AppColors.flameOrange),
              onPressed: () {
                final updated = MealGoalSettings(
                  calorieGoal: calorieGoal,
                  proteinGoalGrams: proteinGoal,
                  carbsGoalGrams: current.carbsGoalGrams,
                  fatGoalGrams: current.fatGoalGrams,
                  waterGoalMl: current.waterGoalMl,
                  goalType: goalType,
                );
                ref.read(smartMealsRepositoryProvider).updateGoalSettings(updated);
                Navigator.pop(ctx);
              },
              child: const Text('Save'),
            ),
          ],
        ),
      ),
    );
  }

  void _showRecipeDetail(SmartMealRecipe recipe) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.8),
        child: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Text(
                      recipe.name,
                      style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Chip(
                    label: Text('${recipe.calories} kcal', style: const TextStyle(fontWeight: FontWeight.bold)),
                    backgroundColor: AppColors.flameOrange.withOpacity(0.15),
                    labelStyle: const TextStyle(color: AppColors.flameOrange),
                    side: BorderSide.none,
                  ),
                  const SizedBox(width: 8),
                  Chip(
                    label: Text('${recipe.proteinGrams}g Protein'),
                    backgroundColor: AppColors.electricCyan.withOpacity(0.15),
                    labelStyle: const TextStyle(color: AppColors.electricCyan),
                    side: BorderSide.none,
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text(recipe.description, style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 16),
              const Text('Ingredients', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 8),
              ...recipe.ingredients.map((ing) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 2),
                  child: Row(
                    children: [
                      const Icon(Icons.fiber_manual_record, size: 8, color: AppColors.flameOrange),
                      const SizedBox(width: 8),
                      Text(ing),
                    ],
                  ),
                );
              }),
              const SizedBox(height: 16),
              const Text('Instructions', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 8),
              ...recipe.instructions.map((step) {
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Text(step, style: const TextStyle(height: 1.4)),
                );
              }),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final weeklyPlans = ref.watch(weeklyMealPlansStreamProvider).valueOrNull ?? {};
    final goalSettings = ref.watch(mealGoalSettingsStreamProvider).valueOrNull ??
        ref.watch(smartMealsRepositoryProvider).currentGoalSettings;
    final waterIntake = ref.watch(waterIntakeMlStreamProvider).valueOrNull ??
        ref.watch(smartMealsRepositoryProvider).currentWaterIntake;
    final recipes = ref.watch(smartMealsRepositoryProvider).allRecipes;
    final remindersState = ref.watch(mealRemindersProvider);

    final todayLoggedMeals = ref.watch(loggedMealsStreamProvider).valueOrNull ?? [];
    final consumedCalories = todayLoggedMeals.fold(0, (sum, m) => sum + m.calories);
    final consumedProtein = todayLoggedMeals.fold(0, (sum, m) => sum + m.proteinG);
    final consumedCarbs = todayLoggedMeals.fold(0, (sum, m) => sum + m.carbsG);
    final consumedFats = todayLoggedMeals.fold(0, (sum, m) => sum + m.fatsG);
    final dailyTarget = goalSettings.calorieGoal > 0 ? goalSettings.calorieGoal : 2400;
    final remainingKcal = (dailyTarget - consumedCalories).clamp(0, 99999);
    final calorieProgress = (consumedCalories / dailyTarget).clamp(0.0, 1.0);

    final currentDayPlan = weeklyPlans[_selectedDay] ??
        ref.watch(smartMealsRepositoryProvider).getDayPlan(_selectedDay);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Smart Meals', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          // Meal Reminders configuration button
          IconButton(
            tooltip: 'Meal Reminders',
            icon: Icon(
              remindersState.masterEnabled
                  ? Icons.notifications_active
                  : Icons.notifications_off_outlined,
              color: remindersState.masterEnabled ? AppColors.flameOrange : Colors.grey,
            ),
            onPressed: () => MealRemindersSheet.show(context),
          ),
          // Water intake button
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: ActionChip(
              avatar: const Icon(Icons.water_drop, color: AppColors.waterBlueLight, size: 16),
              label: Text('$waterIntake ml (+250)', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
              onPressed: () {
                ref.read(smartMealsRepositoryProvider).addWater(250);
              },
            ),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // Day selector: Mon-Sun (Requirement 3)
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              children: DayOfWeekPlan.values.map((day) {
                final isSelected = _selectedDay == day;
                return Padding(
                  padding: const EdgeInsets.only(right: 6),
                  child: FilterChip(
                    selected: isSelected,
                    label: Text(
                      day.name.substring(0, 3).toUpperCase(),
                      style: TextStyle(
                        fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                        color: isSelected ? Colors.white : null,
                      ),
                    ),
                    selectedColor: AppColors.boldRed,
                    onSelected: (_) => setState(() => _selectedDay = day),
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 12),

          // Calorie & Macro Target Card with Dynamic Totals & Kcal Left
          Card(
            elevation: 0,
            shape: RoundedCornerShape(20),
            color: theme.colorScheme.surface,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Goal: ${goalSettings.goalType}',
                            style: theme.textTheme.labelMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Row(
                            crossAxisAlignment: CrossAxisAlignment.baseline,
                            textBaseline: TextBaseline.alphabetic,
                            children: [
                              Text(
                                '$consumedCalories',
                                style: const TextStyle(
                                  fontSize: 28,
                                  fontWeight: FontWeight.w900,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Text(
                                'of $dailyTarget kcal',
                                style: theme.textTheme.bodyMedium?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      Row(
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              Text(
                                '$remainingKcal',
                                style: TextStyle(
                                  fontSize: 22,
                                  fontWeight: FontWeight.w900,
                                  color: remainingKcal > 0 ? AppColors.boldRed : AppColors.successGreen,
                                ),
                              ),
                              Text(
                                'kcal left',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(width: 8),
                          IconButton(
                            icon: const Icon(Icons.edit, size: 18),
                            onPressed: () => _showEditGoalDialog(goalSettings),
                          ),
                        ],
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),

                  // Calorie Target Progress Bar
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: calorieProgress,
                      minHeight: 8,
                      color: AppColors.flameOrange,
                      backgroundColor: theme.colorScheme.surfaceContainerHighest,
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Macros Breakdown Row
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _buildMacroColumn('Protein', '$consumedProtein / ${goalSettings.proteinGoalGrams}g', AppColors.flameOrange),
                      _buildMacroColumn('Carbs', '$consumedCarbs / ${goalSettings.carbsGoalGrams}g', AppColors.electricCyan),
                      _buildMacroColumn('Fat', '$consumedFats / ${goalSettings.fatGoalGrams}g', AppColors.goldYellow),
                      _buildMacroColumn('Hydration', '$waterIntake ml', AppColors.waterBlueLight),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // AI Calorie Tracker Section (Photo & Text Logging)
          const AiCalorieTrackerSection(),
          const SizedBox(height: 16),

          // 5 Meal Slots for Selected Day
          Text(
            '${_selectedDay.name.toUpperCase()} MEAL PLAN',
            style: theme.textTheme.titleSmall?.copyWith(
              fontWeight: FontWeight.bold,
              color: AppColors.flameOrange,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 8),

          ...currentDayPlan.slots.map((slotPlan) {
            final matchingRecipe = recipes.firstWhere(
              (r) => r.id == slotPlan.recipeId,
              orElse: () => recipes.first,
            );

            return Card(
              elevation: 0,
              margin: const EdgeInsets.only(bottom: 10),
              shape: RoundedCornerShape(16),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.35),
              child: InkWell(
                borderRadius: BorderRadius.circular(16),
                onTap: () => _showRecipeDetail(matchingRecipe),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    children: [
                      Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: AppColors.flameOrange.withOpacity(0.15),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.restaurant, color: AppColors.flameOrange, size: 22),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              slotPlan.slot.name,
                              style: const TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: AppColors.flameOrange,
                              ),
                            ),
                            Text(
                              slotPlan.recipeName,
                              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                            ),
                            Text(
                              '${slotPlan.calories} kcal • ${slotPlan.proteinGrams}g Protein',
                              style: theme.textTheme.bodySmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const Icon(Icons.chevron_right, color: Colors.grey),
                    ],
                  ),
                ),
              ),
            );
          }),

          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildMacroColumn(String label, String value, Color color) {
    return Column(
      children: [
        Text(value, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: color)),
        const SizedBox(height: 2),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }
}
