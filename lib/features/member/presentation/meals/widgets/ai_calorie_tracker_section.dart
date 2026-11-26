import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/meal_models.dart';
import '../../../../data/providers/app_providers.dart';
import '../models/ai_calorie_models.dart';
import '../providers/ai_calorie_providers.dart';
import 'meal_reminders_sheet.dart';

class AiCalorieTrackerSection extends ConsumerStatefulWidget {
  const AiCalorieTrackerSection({super.key});

  @override
  ConsumerState<AiCalorieTrackerSection> createState() => _AiCalorieTrackerSectionState();
}

class _AiCalorieTrackerSectionState extends ConsumerState<AiCalorieTrackerSection> {
  final ImagePicker _picker = ImagePicker();

  void _showLogFoodOptions(BuildContext context) {
    showModalBottomSheet(
      context: context,
      shape: const RoundedCornerShape(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (ctx) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
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
              Text(
                'Log Food with AI',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
              ),
              const SizedBox(height: 16),
              ListTile(
                leading: Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: AppColors.flameOrange.withOpacity(0.15),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.camera_alt, color: AppColors.flameOrange),
                ),
                title: const Text('Take Photo', style: TextStyle(fontWeight: FontWeight.bold)),
                contentPadding: const EdgeInsets.symmetric(vertical: 4),
                minVerticalPadding: 14,
                onTap: () {
                  Navigator.pop(ctx);
                  _handleImagePick(ImageSource.camera);
                },
              ),
              ListTile(
                leading: Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: AppColors.electricCyan.withOpacity(0.15),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.photo_library, color: AppColors.electricCyan),
                ),
                title: const Text('Choose from Gallery', style: TextStyle(fontWeight: FontWeight.bold)),
                contentPadding: const EdgeInsets.symmetric(vertical: 4),
                minVerticalPadding: 14,
                onTap: () {
                  Navigator.pop(ctx);
                  _handleImagePick(ImageSource.gallery);
                },
              ),
              ListTile(
                leading: Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: AppColors.goldYellow.withOpacity(0.15),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.edit_note, color: AppColors.goldYellow),
                ),
                title: const Text('Describe in Text', style: TextStyle(fontWeight: FontWeight.bold)),
                contentPadding: const EdgeInsets.symmetric(vertical: 4),
                minVerticalPadding: 14,
                onTap: () {
                  Navigator.pop(ctx);
                  _showTextPromptDialog(context);
                },
              ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _handleImagePick(ImageSource source) async {
    try {
      final XFile? file = await _picker.pickImage(
        source: source,
        maxWidth: 1024,
        maxHeight: 1024,
        imageQuality: 85,
      );
      if (file == null) return;

      final bytes = await file.readAsBytes();
      if (!mounted) return;
      ref.read(aiCalorieTrackerProvider.notifier).analyzeImage(bytes, imagePath: file.path);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Could not access image: ${e.toString().replaceAll("Exception:", "").trim()}'),
          backgroundColor: AppColors.boldRed,
        ),
      );
    }
  }

  void _showTextPromptDialog(BuildContext context) {
    final textController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Describe Your Meal', style: TextStyle(fontWeight: FontWeight.bold)),
        content: TextField(
          controller: textController,
          autofocus: true,
          maxLines: 3,
          decoration: const InputDecoration(
            hintText: 'e.g., 2 scrambled eggs, 1 slice of wheat toast, and black coffee',
            border: OutlineInputBorder(),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.flameOrange),
            onPressed: () {
              final query = textController.text.trim();
              if (query.isNotEmpty) {
                Navigator.pop(ctx);
                ref.read(aiCalorieTrackerProvider.notifier).analyzeText(query);
              }
            },
            child: const Text('Analyze'),
          ),
        ],
      ),
    );
  }

  void _showEditItemDialog(BuildContext context, int index, AiDetectedFoodItem item) {
    final nameCtrl = TextEditingController(text: item.name);
    final portionCtrl = TextEditingController(text: item.portion);
    final calCtrl = TextEditingController(text: item.calories.toString());
    final protCtrl = TextEditingController(text: item.proteinG.toString());
    final carbsCtrl = TextEditingController(text: item.carbsG.toString());
    final fatCtrl = TextEditingController(text: item.fatG.toString());

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Edit Food Item', style: TextStyle(fontWeight: FontWeight.bold)),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: nameCtrl,
                decoration: const InputDecoration(labelText: 'Item Name', isDense: true),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: portionCtrl,
                decoration: const InputDecoration(labelText: 'Portion', isDense: true),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: calCtrl,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Calories (kcal)', isDense: true),
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: protCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Protein (g)', isDense: true),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: carbsCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Carbs (g)', isDense: true),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: fatCtrl,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Fat (g)', isDense: true),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.flameOrange),
            onPressed: () {
              final updated = item.copyWith(
                name: nameCtrl.text.trim().isNotEmpty ? nameCtrl.text.trim() : item.name,
                portion: portionCtrl.text.trim().isNotEmpty ? portionCtrl.text.trim() : item.portion,
                calories: int.tryParse(calCtrl.text) ?? item.calories,
                proteinG: int.tryParse(protCtrl.text) ?? item.proteinG,
                carbsG: int.tryParse(carbsCtrl.text) ?? item.carbsG,
                fatG: int.tryParse(fatCtrl.text) ?? item.fatG,
              );
              ref.read(aiCalorieTrackerProvider.notifier).updateItem(index, updated);
              Navigator.pop(ctx);
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final trackerState = ref.watch(aiCalorieTrackerProvider);
    final loggedMeals = ref.watch(loggedMealsStreamProvider).valueOrNull ?? [];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Section Header & Action Button
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: AppColors.flameOrange.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(Icons.auto_awesome, color: AppColors.flameOrange, size: 18),
                ),
                const SizedBox(width: 8),
                Text(
                  'AI CALORIE TRACKER',
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    letterSpacing: 0.5,
                  ),
                ),
              ],
            ),
            Row(
              children: [
                IconButton(
                  tooltip: 'Meal Reminders',
                  icon: const Icon(Icons.alarm, size: 22, color: AppColors.flameOrange),
                  onPressed: () => MealRemindersSheet.show(context),
                ),
                const SizedBox(width: 4),
                SizedBox(
                  height: 48,
                  child: FilledButton.icon(
                    style: FilledButton.styleFrom(
                      backgroundColor: AppColors.flameOrange,
                      shape: RoundedCornerShape(12),
                      padding: const EdgeInsets.symmetric(horizontal: 16),
                    ),
                    icon: const Icon(Icons.add, size: 18),
                    label: const Text('Log food', style: TextStyle(fontWeight: FontWeight.bold)),
                    onPressed: () => _showLogFoodOptions(context),
                  ),
                ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 12),

        // Analyzing State Card
        if (trackerState.status == AiCalorieTrackerStatus.analyzing ||
            trackerState.status == AiCalorieTrackerStatus.saving) ...[
          Card(
            elevation: 0,
            shape: RoundedCornerShape(16),
            color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2.5, color: AppColors.flameOrange),
                  ),
                  const SizedBox(width: 16),
                  Text(
                    trackerState.status == AiCalorieTrackerStatus.analyzing
                        ? 'Analyzing nutrition with AI...'
                        : 'Saving meal to log...',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],

        // Error Banner
        if (trackerState.status == AiCalorieTrackerStatus.error &&
            trackerState.errorMessage != null) ...[
          Card(
            elevation: 0,
            shape: RoundedCornerShape(16),
            color: AppColors.boldRed.withOpacity(0.1),
            child: Padding(
              padding: const EdgeInsets.all(14),
              child: Row(
                children: [
                  const Icon(Icons.error_outline, color: AppColors.boldRed, size: 24),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      trackerState.errorMessage!,
                      style: const TextStyle(color: AppColors.boldRed, fontSize: 13, fontWeight: FontWeight.w600),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close, size: 18, color: AppColors.boldRed),
                    onPressed: () => ref.read(aiCalorieTrackerProvider.notifier).clearError(),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],

        // Interactive Review Card (Before Saving)
        if (trackerState.status == AiCalorieTrackerStatus.reviewing) ...[
          Card(
            elevation: 0,
            shape: RoundedCornerShape(20),
            color: theme.colorScheme.surface,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: AppColors.flameOrange, width: 1.5),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                        decoration: BoxDecoration(
                          color: AppColors.flameOrange.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.auto_awesome, size: 14, color: AppColors.flameOrange),
                            SizedBox(width: 4),
                            Text(
                              'AI estimate',
                              style: TextStyle(
                                color: AppColors.flameOrange,
                                fontWeight: FontWeight.bold,
                                fontSize: 11,
                              ),
                            ),
                          ],
                        ),
                      ),
                      // Meal Slot Selector Dropdown
                      DropdownButton<MealSlot>(
                        value: trackerState.selectedSlot,
                        underline: const SizedBox(),
                        icon: const Icon(Icons.keyboard_arrow_down, size: 18),
                        items: MealSlot.values.map((s) {
                          return DropdownMenuItem(
                            value: s,
                            child: Text(s.displayName, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                          );
                        }).toList(),
                        onChanged: (val) {
                          if (val != null) {
                            ref.read(aiCalorieTrackerProvider.notifier).setSlot(val);
                          }
                        },
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),

                  // Detected Food Items (Editable & Removable)
                  Text('Detected Items (${trackerState.editableItems.length})',
                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                  const SizedBox(height: 8),

                  ...trackerState.editableItems.asMap().entries.map((entry) {
                    final index = entry.key;
                    final item = entry.value;

                    return Container(
                      margin: const EdgeInsets.only(bottom: 8),
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.35),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            child: InkWell(
                              onTap: () => _showEditItemDialog(context, index, item),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Row(
                                    children: [
                                      Expanded(
                                        child: Text(
                                          item.name,
                                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                                        ),
                                      ),
                                      const Icon(Icons.edit, size: 14, color: Colors.grey),
                                    ],
                                  ),
                                  const SizedBox(height: 2),
                                  Text(
                                    '${item.portion} • ${item.calories} kcal (AI estimate)',
                                    style: theme.textTheme.bodySmall?.copyWith(color: AppColors.flameOrange),
                                  ),
                                  Text(
                                    'P: ${item.proteinG}g  C: ${item.carbsG}g  F: ${item.fatG}g',
                                    style: theme.textTheme.labelSmall?.copyWith(
                                      color: theme.colorScheme.onSurfaceVariant,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.remove_circle_outline, color: AppColors.boldRed, size: 20),
                            onPressed: () => ref.read(aiCalorieTrackerProvider.notifier).removeItem(index),
                            tooltip: 'Remove item',
                          ),
                        ],
                      ),
                    );
                  }),

                  const SizedBox(height: 12),
                  // Totals Summary
                  Container(
                    padding: const EdgeInsets.all(12),
                    decoration: BoxDecoration(
                      color: AppColors.flameOrange.withOpacity(0.08),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: [
                        _buildTotalNutrient('Calories', '${trackerState.totalCalories} kcal', AppColors.flameOrange),
                        _buildTotalNutrient('Protein', '${trackerState.totalProteinG}g', AppColors.flameOrange),
                        _buildTotalNutrient('Carbs', '${trackerState.totalCarbsG}g', AppColors.electricCyan),
                        _buildTotalNutrient('Fat', '${trackerState.totalFatG}g', AppColors.goldYellow),
                      ],
                    ),
                  ),

                  const SizedBox(height: 16),
                  // Confirm or Cancel Actions
                  Row(
                    children: [
                      Expanded(
                        child: SizedBox(
                          height: 48,
                          child: OutlinedButton(
                            style: OutlinedButton.styleFrom(
                              shape: RoundedCornerShape(12),
                            ),
                            onPressed: () => ref.read(aiCalorieTrackerProvider.notifier).cancel(),
                            child: const Text('Cancel'),
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: SizedBox(
                          height: 48,
                          child: FilledButton(
                            style: FilledButton.styleFrom(
                              backgroundColor: AppColors.flameOrange,
                              shape: RoundedCornerShape(12),
                            ),
                            onPressed: () => ref.read(aiCalorieTrackerProvider.notifier).confirmAndSave(),
                            child: const Text('Confirm & Log', style: TextStyle(fontWeight: FontWeight.bold)),
                          ),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),
        ],

        // Today's Logged Meals List
        Text(
          "TODAY'S LOGGED MEALS (${loggedMeals.length})",
          style: theme.textTheme.titleSmall?.copyWith(
            fontWeight: FontWeight.bold,
            letterSpacing: 0.5,
            color: theme.colorScheme.onSurfaceVariant,
          ),
        ),
        const SizedBox(height: 8),

        if (loggedMeals.isEmpty)
          Card(
            elevation: 0,
            shape: RoundedCornerShape(14),
            color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
            child: const Padding(
              padding: EdgeInsets.symmetric(vertical: 20, horizontal: 16),
              child: Center(
                child: Text(
                  'No meals logged today yet. Tap "Log food" to track.',
                  style: TextStyle(color: Colors.grey, fontSize: 13),
                ),
              ),
            ),
          )
        else
          ...loggedMeals.map((meal) {
            final isAiEstimated = meal.dietaryTags.contains('AI estimate');

            return Card(
              elevation: 0,
              margin: const EdgeInsets.only(bottom: 8),
              shape: RoundedCornerShape(14),
              color: theme.colorScheme.surface,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 40,
                      height: 40,
                      decoration: BoxDecoration(
                        color: AppColors.flameOrange.withOpacity(0.12),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.restaurant, color: AppColors.flameOrange, size: 20),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Text(
                                meal.slot.displayName.toUpperCase(),
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                  color: AppColors.flameOrange,
                                ),
                              ),
                              if (isAiEstimated) ...[
                                const SizedBox(width: 6),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                                  decoration: BoxDecoration(
                                    color: AppColors.flameOrange.withOpacity(0.12),
                                    borderRadius: BorderRadius.circular(4),
                                  ),
                                  child: const Text(
                                    'AI estimate',
                                    style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: AppColors.flameOrange),
                                  ),
                                ),
                              ],
                            ],
                          ),
                          Text(
                            meal.name,
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                            maxLines: 1,
                            overflow: TextOverflow.Ellipsis,
                          ),
                          const SizedBox(height: 2),
                          Text(
                            '${meal.calories} kcal • P: ${meal.proteinG}g  C: ${meal.carbsG}g  F: ${meal.fatsG}g',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    IconButton(
                      icon: const Icon(Icons.delete_outline, color: Colors.grey, size: 20),
                      tooltip: 'Delete meal',
                      onPressed: () {
                        ref.read(smartMealsRepositoryProvider).deleteMeal(meal.id);
                      },
                    ),
                  ],
                ),
              ),
            );
          }),
        const SizedBox(height: 16),
      ],
    );
  }

  Widget _buildTotalNutrient(String label, String value, Color color) {
    return Column(
      children: [
        Text(value, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: color)),
        Text(label, style: const TextStyle(fontSize: 10, color: Colors.grey)),
      ],
    );
  }
}
