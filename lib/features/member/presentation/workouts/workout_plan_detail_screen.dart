import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/exercise.dart';
import '../../../../data/providers/app_providers.dart';
import '../exercise/exercise_detail_sheet.dart';

class WorkoutPlanDetailScreen extends ConsumerWidget {
  final String planId;

  const WorkoutPlanDetailScreen({
    super.key,
    required this.planId,
  });

  void _showExerciseDetail(BuildContext context, WidgetRef ref, Exercise exercise) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ExerciseDetailSheet(
        exercise: exercise,
        onDismiss: () => Navigator.pop(ctx),
        onToggleFavorite: (ex) {
          ref.read(workoutRepositoryProvider).toggleExerciseFavorite(ex.id, !ex.isFavorite);
          Navigator.pop(ctx);
        },
      ),
    );
  }

  void _confirmDeletePlan(BuildContext context, WidgetRef ref, String planId) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Custom Plan?', style: TextStyle(fontWeight: FontWeight.bold)),
        content: const Text('Are you sure you want to delete this custom workout plan?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.pop(ctx);
              await ref.read(workoutRepositoryProvider).deleteCustomPlan(planId);
              if (context.mounted) {
                context.pop();
              }
            },
            child: const Text('Delete', style: TextStyle(color: AppColors.dangerRed, fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final plansAsync = ref.watch(plansStreamProvider);

    return plansAsync.when(
      data: (plans) {
        final plan = plans.firstWhere(
          (p) => p.id == planId,
          orElse: () => plans.first,
        );

        final planExercisesAsync = ref.watch(
          StreamProvider((ref) => ref.watch(workoutRepositoryProvider).getPlanExercises(plan.id)),
        );

        return Scaffold(
          appBar: AppBar(
            title: Text(plan.name, style: const TextStyle(fontWeight: FontWeight.bold)),
            actions: [
              if (plan.isCustom)
                IconButton(
                  icon: const Icon(Icons.delete_outline, color: AppColors.dangerRed),
                  onPressed: () => _confirmDeletePlan(context, ref, plan.id),
                ),
            ],
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
              child: SizedBox(
                width: double.infinity,
                height: 52,
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.flameOrange,
                    shape: RoundedCornerShape(26),
                  ),
                  onPressed: () {
                    context.push('/member/workouts/active/${plan.id}');
                  },
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('START WORKOUT', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                ),
              ),
            ),
          ),
          body: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              // Hero Banner Info Card
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
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Chip(
                            label: Text(plan.category, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11)),
                            backgroundColor: AppColors.flameOrange.withOpacity(0.15),
                            labelStyle: const TextStyle(color: AppColors.flameOrange),
                            side: BorderSide.none,
                          ),
                          Chip(
                            label: Text(plan.difficulty, style: const TextStyle(fontSize: 11)),
                            backgroundColor: AppColors.electricCyanDark.withOpacity(0.15),
                            labelStyle: const TextStyle(color: AppColors.electricCyanDark),
                            side: BorderSide.none,
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(
                        plan.name,
                        style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        plan.description,
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceAround,
                        children: [
                          Column(
                            children: [
                              Row(
                                children: [
                                  const Icon(Icons.timer_outlined, size: 18, color: AppColors.electricCyan),
                                  const SizedBox(width: 4),
                                  Text(
                                    '${plan.estimatedDurationMinutes} mins',
                                    style: const TextStyle(fontWeight: FontWeight.bold),
                                  ),
                                ],
                              ),
                              Text('Duration', style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                            ],
                          ),
                          Column(
                            children: [
                              Row(
                                children: [
                                  const Icon(Icons.fitness_center, size: 18, color: AppColors.flameOrange),
                                  const SizedBox(width: 4),
                                  Text(
                                    '${planExercisesAsync.valueOrNull?.length ?? 0} exercises',
                                    style: const TextStyle(fontWeight: FontWeight.bold),
                                  ),
                                ],
                              ),
                              Text('Workout Size', style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      const Divider(),
                      const SizedBox(height: 8),
                      Text(
                        'ANATOMICAL FOCUS:',
                        style: theme.textTheme.labelSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: AppColors.flameOrange,
                        ),
                      ),
                      Text(
                        plan.targetMusclesDescription.isNotEmpty
                            ? plan.targetMusclesDescription
                            : '${plan.category} muscle groups & core stabilizers',
                        style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'EDUCATIONAL OVERVIEW & PHYSIOLOGY:',
                        style: theme.textTheme.labelSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: AppColors.electricCyan,
                        ),
                      ),
                      Text(
                        plan.educationalOverview.isNotEmpty
                            ? plan.educationalOverview
                            : 'Structured circuit combining compound bodyweight motor patterns to optimize neuromuscular adaptation, joint mobility, and caloric afterburn without joint strain.',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 20),

              // Routine Exercises Header
              Text(
                'Exercise Routine',
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 10),

              planExercisesAsync.when(
                data: (planExList) {
                  return Column(
                    children: planExList.map((pair) {
                      final planEx = pair.key;
                      final ex = pair.value;
                      return Card(
                        elevation: 0,
                        margin: const EdgeInsets.only(bottom: 10),
                        shape: RoundedCornerShape(16),
                        color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                        child: InkWell(
                          borderRadius: BorderRadius.circular(16),
                          onTap: () => _showExerciseDetail(context, ref, ex),
                          child: Padding(
                            padding: const EdgeInsets.all(12),
                            child: Row(
                              children: [
                                CircleAvatar(
                                  radius: 14,
                                  backgroundColor: AppColors.flameOrange.withOpacity(0.2),
                                  child: Text(
                                    '${planEx.orderIndex + 1}',
                                    style: const TextStyle(
                                      color: AppColors.flameOrange,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 12,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 12),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        ex.name,
                                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                                      ),
                                      Text(
                                        ex.targetMuscles,
                                        style: theme.textTheme.bodySmall?.copyWith(
                                          color: theme.colorScheme.onSurfaceVariant,
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                                Text(
                                  planEx.durationSeconds > 0
                                      ? '${planEx.durationSeconds}s'
                                      : '${planEx.reps} reps',
                                  style: const TextStyle(
                                    fontWeight: FontWeight.bold,
                                    color: AppColors.flameOrange,
                                  ),
                                ),
                                const SizedBox(width: 8),
                                const Icon(Icons.info_outline, size: 18, color: Colors.grey),
                              ],
                            ),
                          ),
                        ),
                      );
                    }).toList(),
                  );
                },
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (_, __) => const Text('Failed to load routine exercises'),
              ),
              const SizedBox(height: 24),
            ],
          ),
        );
      },
      loading: () => const Scaffold(body: Center(child: CircularProgressIndicator())),
      error: (_, __) => const Scaffold(body: Center(child: Text('Plan not found'))),
    );
  }
}
