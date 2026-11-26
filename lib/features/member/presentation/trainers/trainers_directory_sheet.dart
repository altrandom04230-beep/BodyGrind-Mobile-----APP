import 'package:flutter/material.dart';
import '../../../../core/constants/app_colors.dart';

class TrainerProfileItem {
  final String id;
  final String name;
  final String title;
  final List<String> specialties;
  final String floorSchedule;
  final bool isAvailableNow;
  final int experienceYears;
  final String bio;
  final String avatarInitial;
  final Color badgeColor;
  final String consultationTip;

  const TrainerProfileItem({
    required this.id,
    required this.name,
    required this.title,
    required this.specialties,
    required this.floorSchedule,
    required this.isAvailableNow,
    required this.experienceYears,
    required this.bio,
    required this.avatarInitial,
    required this.badgeColor,
    required this.consultationTip,
  });
}

class AvailableTrainersData {
  static const List<TrainerProfileItem> trainers = [
    TrainerProfileItem(
      id: 'cobie',
      name: 'Coach Cobie',
      title: 'Head Strength & Hypertrophy Coach',
      specialties: ['Hypertrophy', 'Compound Lifts', 'Biomechanics & Form', 'Injury Prevention'],
      floorSchedule: 'Mon-Sat: 6:00 AM - 2:00 PM',
      isAvailableNow: true,
      experienceYears: 8,
      bio: 'Coach Cobie specializes in helping gym beginners and intermediate lifters build clean muscle through biomechanically sound form, progressive overload, and joint-friendly training.',
      avatarInitial: 'CC',
      badgeColor: AppColors.flameOrange,
      consultationTip: 'Ask Coach Cobie about perfecting your push-up and squat mechanics or setting up your first muscle-building split!',
    ),
    TrainerProfileItem(
      id: 'jun',
      name: 'Coach Jun',
      title: 'Functional Conditioning & Mobility Specialist',
      specialties: ['HIIT & Fat Loss', 'Core Stability', 'Functional Mobility', 'Athletic Conditioning'],
      floorSchedule: 'Mon-Sat: 1:00 PM - 9:00 PM',
      isAvailableNow: true,
      experienceYears: 6,
      bio: 'Coach Jun focuses on high-energy conditioning, explosive endurance, and posture correction. He is on the gym floor to help members stay injury-free and boost cardiovascular capacity.',
      avatarInitial: 'CJ',
      badgeColor: AppColors.electricCyan,
      consultationTip: 'Ask Coach Jun about dynamic warm-up drills, core stability routines, or high-intensity interval pacing!',
    ),
  ];
}

class TrainersDirectorySheet extends StatelessWidget {
  final VoidCallback onDismiss;
  final void Function(String trainerName, String topic)? onConsultWithAi;

  const TrainersDirectorySheet({
    super.key,
    required this.onDismiss,
    this.onConsultWithAi,
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
                Row(
                  children: [
                    Container(
                      width: 38,
                      height: 38,
                      decoration: BoxDecoration(
                        color: AppColors.flameOrange.withOpacity(0.15),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.badge,
                        color: AppColors.flameOrange,
                        size: 22,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Text(
                      'Available Gym Trainers',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: onDismiss,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.flameOrange.withOpacity(0.3)),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline, color: AppColors.flameOrange, size: 20),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Certified coaches are stationed on the gym floor for form checks, spot assistance, and routine adjustments.',
                      style: theme.textTheme.bodySmall,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Flexible(
              child: ListView.separated(
                shrinkWrap: true,
                itemCount: AvailableTrainersData.trainers.length,
                separatorBuilder: (_, __) => const SizedBox(height: 12),
                itemBuilder: (context, index) {
                  final trainer = AvailableTrainersData.trainers[index];
                  return Card(
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
                              CircleAvatar(
                                radius: 24,
                                backgroundColor: trainer.badgeColor,
                                child: Text(
                                  trainer.avatarInitial,
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      trainer.name,
                                      style: theme.textTheme.titleSmall?.copyWith(
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                    Text(
                                      trainer.title,
                                      style: theme.textTheme.bodySmall?.copyWith(
                                        color: trainer.badgeColor,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Container(
                                width: 8,
                                height: 8,
                                decoration: const BoxDecoration(
                                  color: AppColors.successGreen,
                                  shape: BoxShape.circle,
                                ),
                              ),
                              const SizedBox(width: 6),
                              Text(
                                'Available • ${trainer.floorSchedule}',
                                style: theme.textTheme.labelSmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 8),
                          Text(
                            trainer.bio,
                            style: theme.textTheme.bodySmall,
                          ),
                          const SizedBox(height: 10),
                          Wrap(
                            spacing: 6,
                            runSpacing: 4,
                            children: trainer.specialties.map((s) {
                              return Chip(
                                label: Text(s, style: const TextStyle(fontSize: 10)),
                                padding: EdgeInsets.zero,
                                materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                visualDensity: VisualDensity.compact,
                              );
                            }).toList(),
                          ),
                          if (onConsultWithAi != null) ...[
                            const SizedBox(height: 12),
                            SizedBox(
                              width: double.infinity,
                              height: 40,
                              child: FilledButton.tonalIcon(
                                style: FilledButton.styleFrom(
                                  backgroundColor: trainer.badgeColor.withOpacity(0.15),
                                  foregroundColor: trainer.badgeColor,
                                ),
                                icon: const Icon(Icons.chat_bubble_outline, size: 16),
                                label: Text(
                                  'Ask AI About ${trainer.name}',
                                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                                ),
                                onPressed: () {
                                  onDismiss();
                                  onConsultWithAi!(trainer.name, trainer.specialties.first);
                                },
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
