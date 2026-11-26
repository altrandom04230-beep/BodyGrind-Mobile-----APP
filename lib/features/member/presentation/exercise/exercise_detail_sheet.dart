import 'package:flutter/material.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/exercise.dart';

class ExerciseDetailSheet extends StatelessWidget {
  final Exercise exercise;
  final VoidCallback onDismiss;
  final ValueChanged<Exercise> onToggleFavorite;

  const ExerciseDetailSheet({
    super.key,
    required this.exercise,
    required this.onDismiss,
    required this.onToggleFavorite,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          exercise.name,
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          '${exercise.category} • ${exercise.difficulty}',
                          style: theme.textTheme.labelMedium?.copyWith(
                            color: AppColors.flameOrange,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: Icon(
                      exercise.isFavorite ? Icons.favorite : Icons.favorite_border,
                      color: exercise.isFavorite ? AppColors.dangerRed : theme.colorScheme.onSurfaceVariant,
                    ),
                    onPressed: () => onToggleFavorite(exercise),
                  ),
                  IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: onDismiss,
                  ),
                ],
              ),
              const SizedBox(height: 16),
              // Exercise Visualizer Container
              Container(
                height: 160,
                width: double.infinity,
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(color: theme.colorScheme.outlineVariant.withOpacity(0.5)),
                ),
                alignment: Alignment.center,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Container(
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        color: AppColors.flameOrange.withOpacity(0.2),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.fitness_center, color: AppColors.flameOrange, size: 28),
                    ),
                    const SizedBox(height: 10),
                    Text(
                      exercise.animationType,
                      style: theme.textTheme.labelMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.onSurface,
                      ),
                    ),
                    Text(
                      'Targeting: ${exercise.targetMuscles}',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              // Target Muscles Breakdown
              Card(
                elevation: 0,
                shape: RoundedCornerShape(16),
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.accessibility_new, color: AppColors.flameOrange, size: 20),
                          const SizedBox(width: 8),
                          Text(
                            'Muscles & Anatomy',
                            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        'PRIMARY DRIVERS:',
                        style: theme.textTheme.labelSmall?.copyWith(
                          color: AppColors.flameOrange,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        exercise.primaryMuscles.isNotEmpty ? exercise.primaryMuscles : exercise.targetMuscles,
                        style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      if (exercise.secondaryMuscles.isNotEmpty) ...[
                        const SizedBox(height: 8),
                        Text(
                          'SECONDARY STABILIZERS:',
                          style: theme.textTheme.labelSmall?.copyWith(
                            color: AppColors.electricCyan,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          exercise.secondaryMuscles,
                          style: theme.textTheme.bodySmall,
                        ),
                      ],
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              // Instructions Card
              Card(
                elevation: 0,
                shape: RoundedCornerShape(16),
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.list_alt, color: AppColors.electricCyan, size: 20),
                          const SizedBox(width: 8),
                          Text(
                            'Step-by-Step Execution',
                            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Text(
                        exercise.instructions,
                        style: theme.textTheme.bodyMedium?.copyWith(height: 1.4),
                      ),
                    ],
                  ),
                ),
              ),
              if (exercise.formTips.isNotEmpty) ...[
                const SizedBox(height: 16),
                // Form Tips Card
                Card(
                  elevation: 0,
                  shape: RoundedCornerShape(16),
                  color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(Icons.tips_and_updates, color: AppColors.goldYellow, size: 20),
                            const SizedBox(width: 8),
                            Text(
                              'Form Cues & Tips',
                              style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                        const SizedBox(height: 10),
                        Text(
                          exercise.formTips,
                          style: theme.textTheme.bodySmall?.copyWith(height: 1.4),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
