import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/exercise.dart';
import '../../../../data/models/workout_plan.dart';
import '../../../../data/models/workout_plan_exercise.dart';
import '../../../../data/providers/app_providers.dart';

class CustomWorkoutBuilderScreen extends ConsumerStatefulWidget {
  const CustomWorkoutBuilderScreen({super.key});

  @override
  ConsumerState<CustomWorkoutBuilderScreen> createState() => _CustomWorkoutBuilderScreenState();
}

class _CustomWorkoutBuilderScreenState extends ConsumerState<CustomWorkoutBuilderScreen> {
  final TextEditingController _nameController = TextEditingController();
  final TextEditingController _descController = TextEditingController();

  String _selectedCategory = 'Full Body';
  String _selectedDifficulty = 'Beginner';
  final List<Exercise> _selectedExercises = [];
  bool _isSaving = false;

  final List<String> _categories = [
    'Full Body',
    'Abs',
    'Core',
    'Chest',
    'Back',
    'Shoulders',
    'Biceps',
    'Triceps',
    'Forearms',
    'Arms',
    'Quadriceps (Front Thighs)',
    'Hamstrings (Back Thighs)',
    'Glutes',
    'Calves',
    'Legs',
    'Cardio',
    'Mobility',
    'Flexibility',
  ];

  final List<String> _difficulties = ['Beginner', 'Intermediate', 'Advanced'];

  @override
  void dispose() {
    _nameController.dispose();
    _descController.dispose();
    super.dispose();
  }

  void _showExercisePicker(List<Exercise> allExercises) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        return Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
          constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.8),
          child: Column(
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
                  Text(
                    'Select Exercise to Add',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.pop(ctx),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Expanded(
                child: ListView.separated(
                  itemCount: allExercises.length,
                  separatorBuilder: (_, __) => const Divider(height: 1),
                  itemBuilder: (context, idx) {
                    final ex = allExercises[idx];
                    final isAlreadySelected = _selectedExercises.any((e) => e.id == ex.id);

                    return ListTile(
                      leading: Container(
                        width: 40,
                        height: 40,
                        decoration: BoxDecoration(
                          color: AppColors.flameOrange.withOpacity(0.15),
                          shape: BoxShape.circle,
                        ),
                        child: const Icon(Icons.fitness_center, color: AppColors.flameOrange, size: 20),
                      ),
                      title: Text(ex.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                      subtitle: Text('${ex.category} • ${ex.targetMuscles}', maxLines: 1),
                      trailing: isAlreadySelected
                          ? const Icon(Icons.check_circle, color: AppColors.successGreen)
                          : const Icon(Icons.add_circle_outline, color: AppColors.flameOrange),
                      onTap: () {
                        if (!isAlreadySelected) {
                          setState(() => _selectedExercises.add(ex));
                        }
                        Navigator.pop(ctx);
                      },
                    );
                  },
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Future<void> _savePlan() async {
    final name = _nameController.text.trim();
    if (name.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a workout plan name.')),
      );
      return;
    }
    if (_selectedExercises.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please add at least one exercise to your plan.')),
      );
      return;
    }

    setState(() => _isSaving = true);

    try {
      final now = DateTime.now();
      final planId = 'custom_${now.millisecondsSinceEpoch}';
      final estimatedMinutes = (_selectedExercises.length * 2.5).ceil().clamp(5, 60);

      final plan = WorkoutPlan(
        id: planId,
        name: name,
        description: _descController.text.trim().isNotEmpty
            ? _descController.text.trim()
            : 'Custom workout designed for $_selectedCategory',
        category: _selectedCategory,
        difficulty: _selectedDifficulty,
        estimatedDurationMinutes: estimatedMinutes,
        isCustom: true,
        targetMusclesDescription: '$_selectedCategory and supportive kinetic chain',
        educationalOverview: 'Personalized routine created using BodyGrind Custom Workout Builder.',
      );

      final planExercises = <WorkoutPlanExercise>[];
      for (int i = 0; i < _selectedExercises.length; i++) {
        final ex = _selectedExercises[i];
        planExercises.add(
          WorkoutPlanExercise(
            planId: planId,
            exerciseId: ex.id,
            orderIndex: i,
            reps: 12,
            durationSeconds: 0,
            restSecondsAfter: 20,
          ),
        );
      }

      await ref.read(workoutRepositoryProvider).createCustomPlan(plan, planExercises);

      if (mounted) {
        context.pop();
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Failed to save plan. Please try again.')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final allExercises = ref.watch(exercisesStreamProvider).valueOrNull ?? [];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Create Custom Workout', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.08),
                blurRadius: 8,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          child: Row(
            children: [
              Expanded(
                child: OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    shape: RoundedCornerShape(26),
                    minimumSize: const Size.fromHeight(50),
                  ),
                  icon: const Icon(Icons.add),
                  label: const Text('Add Exercise'),
                  onPressed: () => _showExercisePicker(allExercises),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.flameOrange,
                    shape: RoundedCornerShape(26),
                    minimumSize: const Size.fromHeight(50),
                  ),
                  onPressed: _isSaving ? null : _savePlan,
                  icon: _isSaving
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.check),
                  label: const Text('Save Plan', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),
            ],
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          TextField(
            controller: _nameController,
            decoration: const InputDecoration(
              labelText: 'Workout Name *',
              hintText: 'e.g. Morning HIIT Core',
            ),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _descController,
            maxLines: 2,
            decoration: const InputDecoration(
              labelText: 'Description (Optional)',
              hintText: 'Brief notes on focus areas',
            ),
          ),
          const SizedBox(height: 16),
          // Category Selector
          Text(
            'Target Category',
            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<String>(
            value: _selectedCategory,
            isExpanded: true,
            decoration: InputDecoration(
              isDense: true,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
            ),
            items: _categories.map((c) {
              return DropdownMenuItem(value: c, child: Text(c, overflow: TextOverflow.ellipsis));
            }).toList(),
            onChanged: (val) {
              if (val != null) setState(() => _selectedCategory = val);
            },
          ),
          const SizedBox(height: 16),
          // Difficulty Selector
          Text(
            'Difficulty Level',
            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          Row(
            children: _difficulties.map((diff) {
              final isSelected = _selectedDifficulty == diff;
              return Padding(
                padding: const EdgeInsets.only(right: 8),
                child: FilterChip(
                  selected: isSelected,
                  label: Text(diff),
                  onSelected: (val) {
                    if (val) setState(() => _selectedDifficulty = diff);
                  },
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 20),
          // Selected Exercises List
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Selected Exercises (${_selectedExercises.length})',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              if (_selectedExercises.isNotEmpty)
                TextButton(
                  onPressed: () => setState(() => _selectedExercises.clear()),
                  child: const Text('Clear All', style: TextStyle(color: AppColors.dangerRed)),
                ),
            ],
          ),
          const SizedBox(height: 8),
          if (_selectedExercises.isEmpty)
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              alignment: Alignment.center,
              child: Column(
                children: [
                  const Icon(Icons.fitness_center_outlined, size: 40, color: Colors.grey),
                  const SizedBox(height: 8),
                  const Text('No exercises added yet'),
                  const SizedBox(height: 4),
                  TextButton.icon(
                    icon: const Icon(Icons.add),
                    label: const Text('Tap to add exercises'),
                    onPressed: () => _showExercisePicker(allExercises),
                  ),
                ],
              ),
            )
          else
            ReorderableListView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _selectedExercises.length,
              onReorder: (oldIdx, newIdx) {
                setState(() {
                  if (newIdx > oldIdx) newIdx -= 1;
                  final item = _selectedExercises.removeAt(oldIdx);
                  _selectedExercises.insert(newIdx, item);
                });
              },
              itemBuilder: (context, idx) {
                final ex = _selectedExercises[idx];
                return Card(
                  key: ValueKey(ex.id),
                  elevation: 0,
                  margin: const EdgeInsets.only(bottom: 8),
                  shape: RoundedCornerShape(12),
                  color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                  child: ListTile(
                    leading: CircleAvatar(
                      radius: 14,
                      backgroundColor: AppColors.flameOrange,
                      child: Text(
                        '${idx + 1}',
                        style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                      ),
                    ),
                    title: Text(ex.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                    subtitle: Text('${ex.category} • ${ex.targetMuscles}', maxLines: 1),
                    trailing: IconButton(
                      icon: const Icon(Icons.remove_circle_outline, color: AppColors.dangerRed),
                      onPressed: () => setState(() => _selectedExercises.removeAt(idx)),
                    ),
                  ),
                );
              },
            ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
