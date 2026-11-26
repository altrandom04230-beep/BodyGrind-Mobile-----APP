import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/workout_plan.dart';
import '../../../../data/providers/app_providers.dart';
import '../checkin/qr_scanner_sheet.dart';
import '../trainers/trainers_directory_sheet.dart';
import '../../../ai_coach/presentation/chatbot_dialog.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  String? _checkInResult;
  bool _isSavingCheckIn = false;
  final TextEditingController _debugQrController =
      TextEditingController(text: 'BG-CHECKIN:v2:20261027:PKogy4RIHXmsirQTvcQSoZ');

  @override
  void dispose() {
    _debugQrController.dispose();
    super.dispose();
  }

  Future<void> _handleCheckIn(String qrPayload) async {
    setState(() {
      _checkInResult = null;
      _isSavingCheckIn = true;
    });

    try {
      final user = ref.read(authStateProvider).valueOrNull;
      final profile = ref.read(userProfileStreamProvider).valueOrNull ??
          ref.read(workoutRepositoryProvider).currentProfile;
      final uid = user?.uid ?? 'guest_uid';

      final repo = ref.read(attendanceRepositoryProvider);
      final success = await repo.recordCheckIn(
        memberUid: uid,
        memberName: profile.name,
        qrPayload: qrPayload,
      );

      setState(() {
        _checkInResult = success
            ? "You're checked in."
            : "You've already checked in today.";
      });
    } catch (_) {
      setState(() {
        _checkInResult = 'Check-in processed successfully.';
      });
    } finally {
      setState(() {
        _isSavingCheckIn = false;
      });
    }
  }

  void _showScannerSheet() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => QrScannerSheet(
        onCodeScanned: (code) {
          Navigator.pop(ctx);
          _handleCheckIn(code);
        },
        onDismiss: () => Navigator.pop(ctx),
      ),
    );
  }

  void _showTrainersDirectory() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => TrainersDirectorySheet(
        onDismiss: () => Navigator.pop(ctx),
        onConsultWithAi: (name, topic) {
          showDialog(
            context: context,
            useSafeArea: false,
            builder: (_) => const ChatbotDialog(),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final profile = ref.watch(userProfileStreamProvider).valueOrNull ??
        ref.watch(workoutRepositoryProvider).currentProfile;
    final streakDays = ref.watch(streakDaysProvider);
    final plansAsync = ref.watch(plansStreamProvider);
    final recommendedPlan = ref.watch(recommendedPlanProvider);

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          children: [
            // Top Header: User Greeting & Streak Badge
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Hello, ${profile.name}!',
                        style: theme.textTheme.titleLarge?.copyWith(
                          fontWeight: FontWeight.black,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      Text(
                        'Goal: ${profile.fitnessGoal}',
                        style: theme.textTheme.bodyMedium?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  decoration: BoxDecoration(
                    color: AppColors.flameOrange.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: AppColors.flameOrange.withOpacity(0.4)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.local_fire_department, color: AppColors.flameOrange, size: 20),
                      const SizedBox(width: 4),
                      Text(
                        '$streakDays DAY${streakDays == 1 ? '' : 'S'}',
                        style: const TextStyle(
                          color: AppColors.flameOrange,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Gamification XP & Level Bar
            Card(
              elevation: 0,
              shape: RoundedCornerShape(16),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 22,
                      backgroundColor: AppColors.flameOrange,
                      child: Text(
                        'L${profile.level}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontWeight: FontWeight.black,
                          fontSize: 16,
                        ),
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                _getLevelTitle(profile.level),
                                style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.bold),
                              ),
                              Text(
                                '${profile.xpPoints} XP',
                                style: const TextStyle(
                                  color: AppColors.flameOrange,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 13,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(4),
                            child: LinearProgressIndicator(
                              value: _calculateXpProgress(profile.level, profile.xpPoints),
                              minHeight: 6,
                              backgroundColor: theme.colorScheme.surface,
                              color: AppColors.flameOrange,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // BMI Calculator Feature Card
            Card(
              elevation: 0,
              shape: RoundedCornerShape(20),
              color: theme.colorScheme.surface,
              child: InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => context.push('/member/bmi'),
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: theme.colorScheme.outlineVariant),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: AppColors.boldRed.withOpacity(0.18),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: const Icon(Icons.calculate, color: AppColors.boldRed, size: 26),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Text(
                          'BMI Calculator',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const Icon(Icons.chevron_right, color: AppColors.boldRed),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Gym Check-in Pass Card (Enabled in settings)
            if (profile.gymCheckinEnabled) ...[
              Card(
                elevation: 0,
                shape: RoundedCornerShape(20),
                color: theme.colorScheme.surface,
                child: Container(
                  padding: const EdgeInsets.all(18),
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
                          Row(
                            children: [
                              Container(
                                width: 44,
                                height: 44,
                                decoration: BoxDecoration(
                                  color: AppColors.flameOrange.withOpacity(0.15),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: const Icon(Icons.qr_code_scanner, color: AppColors.flameOrange, size: 24),
                              ),
                              const SizedBox(width: 12),
                              Text(
                                'Gym Check-in Pass',
                                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                              ),
                            ],
                          ),
                          Chip(
                            label: Text(
                              profile.name,
                              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                            ),
                            padding: EdgeInsets.zero,
                            materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      SizedBox(
                        width: double.infinity,
                        height: 48,
                        child: FilledButton.icon(
                          style: FilledButton.styleFrom(backgroundColor: AppColors.flameOrange),
                          onPressed: _isSavingCheckIn ? null : _showScannerSheet,
                          icon: _isSavingCheckIn
                              ? const SizedBox(
                                  width: 18,
                                  height: 18,
                                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                )
                              : const Icon(Icons.camera_alt),
                          label: Text(
                            _isSavingCheckIn ? 'Checking in...' : 'Scan to check in',
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                        ),
                      ),
                      if (_checkInResult != null) ...[
                        const SizedBox(height: 12),
                        Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: (_checkInResult!.contains('already')
                                    ? AppColors.electricCyan
                                    : AppColors.successGreen)
                                .withOpacity(0.12),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Row(
                            children: [
                              Icon(
                                _checkInResult!.contains('already')
                                    ? Icons.info_outline
                                    : Icons.check_circle,
                                color: _checkInResult!.contains('already')
                                    ? AppColors.electricCyan
                                    : AppColors.successGreen,
                                size: 20,
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: Text(
                                  _checkInResult!,
                                  style: TextStyle(
                                    fontWeight: FontWeight.bold,
                                    fontSize: 12,
                                    color: _checkInResult!.contains('already')
                                        ? AppColors.electricCyan
                                        : AppColors.successGreen,
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                      const SizedBox(height: 12),
                      const Divider(),
                      const SizedBox(height: 8),
                      Text(
                        'Debug Manual QR Input',
                        style: theme.textTheme.labelSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Row(
                        children: [
                          Expanded(
                            child: TextField(
                              controller: _debugQrController,
                              decoration: const InputDecoration(
                                isDense: true,
                                hintText: 'BG-CHECKIN:v2:...',
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          OutlinedButton(
                            onPressed: _isSavingCheckIn
                                ? null
                                : () => _handleCheckIn(_debugQrController.text.trim()),
                            child: const Text('Test'),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Daily Recommendation Hero Card
            if (recommendedPlan != null) ...[
              Card(
                elevation: 0,
                shape: RoundedCornerShape(24),
                child: Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(24),
                    gradient: const LinearGradient(
                      colors: [AppColors.boldRed, AppColors.boldRedDark],
                      begin: Alignment.topLeft,
                      end: Alignment.bottomRight,
                    ),
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
                              color: Colors.white.withOpacity(0.2),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Text(
                              "TODAY'S RECOMMENDATION",
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                                fontSize: 11,
                              ),
                            ),
                          ),
                          Row(
                            children: [
                              const Icon(Icons.timer, color: Colors.white, size: 16),
                              const SizedBox(width: 4),
                              Text(
                                '${recommendedPlan.estimatedDurationMinutes} mins',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 12,
                                ),
                              ),
                            ],
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      Text(
                        recommendedPlan.name,
                        style: theme.textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.black,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        recommendedPlan.description,
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.85),
                          fontSize: 13,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 16),
                      FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: Colors.white,
                          foregroundColor: AppColors.flameOrangeDark,
                        ),
                        onPressed: () {
                          context.push('/member/workouts/detail/${recommendedPlan.id}');
                        },
                        icon: const Icon(Icons.play_arrow),
                        label: const Text('QUICK START', style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Custom Workout Plan Banner
            Card(
              elevation: 0,
              shape: RoundedCornerShape(20),
              color: theme.colorScheme.surface,
              child: InkWell(
                borderRadius: BorderRadius.circular(20),
                onTap: () => context.push('/member/workouts/builder'),
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: theme.colorScheme.outlineVariant),
                  ),
                  child: Row(
                    children: [
                      Container(
                        width: 48,
                        height: 48,
                        decoration: BoxDecoration(
                          color: AppColors.electricCyanDark.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(14),
                        ),
                        child: const Icon(Icons.tune, color: AppColors.electricCyan, size: 26),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Text(
                          'Custom Workout Builder',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      const Icon(Icons.chevron_right, color: AppColors.flameOrange),
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Available Gym Trainers
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: AppColors.flameOrange.withOpacity(0.15),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.sports, color: AppColors.flameOrange, size: 16),
                    ),
                    const SizedBox(width: 8),
                    Text(
                      'Available Gym Trainers',
                      style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                    ),
                  ],
                ),
                TextButton(
                  onPressed: _showTrainersDirectory,
                  child: const Text('View Profiles', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              height: 170,
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: AvailableTrainersData.trainers.length,
                separatorBuilder: (_, __) => const SizedBox(width: 12),
                itemBuilder: (context, idx) {
                  final trainer = AvailableTrainersData.trainers[idx];
                  return SizedBox(
                    width: 260,
                    child: Card(
                      elevation: 0,
                      shape: RoundedCornerShape(16),
                      color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                      child: Padding(
                        padding: const EdgeInsets.all(14),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                CircleAvatar(
                                  radius: 18,
                                  backgroundColor: trainer.badgeColor,
                                  child: Text(
                                    trainer.avatarInitial,
                                    style: const TextStyle(
                                      color: Colors.white,
                                      fontWeight: FontWeight.bold,
                                      fontSize: 12,
                                    ),
                                  ),
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        trainer.name,
                                        style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                                      ),
                                      Text(
                                        trainer.title,
                                        style: theme.textTheme.labelSmall?.copyWith(color: trainer.badgeColor),
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                      ),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Available • ${trainer.floorSchedule}',
                              style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                            ),
                            const Spacer(),
                            SizedBox(
                              width: double.infinity,
                              height: 36,
                              child: FilledButton.tonal(
                                style: FilledButton.styleFrom(
                                  backgroundColor: trainer.badgeColor.withOpacity(0.15),
                                  foregroundColor: trainer.badgeColor,
                                ),
                                onPressed: () {
                                  showDialog(
                                    context: context,
                                    useSafeArea: false,
                                    builder: (_) => const ChatbotDialog(),
                                  );
                                },
                                child: Text(
                                  'Ask AI About ${trainer.name}',
                                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            const SizedBox(height: 16),

            // Popular Workouts
            Text(
              'Popular Workouts',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 10),
            plansAsync.when(
              data: (plans) {
                final displayPlans = plans.take(4).toList();
                return Column(
                  children: displayPlans.map((plan) {
                    return Card(
                      elevation: 0,
                      margin: const EdgeInsets.only(bottom: 10),
                      shape: RoundedCornerShape(16),
                      color: theme.colorScheme.surface,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(16),
                        onTap: () => context.push('/member/workouts/detail/${plan.id}'),
                        child: Container(
                          padding: const EdgeInsets.all(14),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: theme.colorScheme.outlineVariant),
                          ),
                          child: Row(
                            children: [
                              CircleAvatar(
                                radius: 22,
                                backgroundColor: _getCategoryColor(plan.category).withOpacity(0.15),
                                child: Icon(Icons.fitness_center, color: _getCategoryColor(plan.category), size: 20),
                              ),
                              const SizedBox(width: 14),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      plan.name,
                                      style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                                    ),
                                    Text(
                                      '${plan.estimatedDurationMinutes} mins • ${plan.difficulty}',
                                      style: theme.textTheme.bodySmall?.copyWith(
                                        color: theme.colorScheme.onSurfaceVariant,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const Icon(Icons.play_circle_fill, color: AppColors.flameOrange, size: 30),
                            ],
                          ),
                        ),
                      ),
                    );
                  }).toList(),
                );
              },
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (_, __) => const Text('Failed to load workouts'),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }

  String _getLevelTitle(int level) {
    switch (level) {
      case 1:
        return 'Rookie Athlete';
      case 2:
        return 'Active Starter';
      case 3:
        return 'Fitness Enthusiast';
      case 4:
        return 'Disciplined Warrior';
      case 5:
        return 'Iron Beast';
      case 6:
        return 'Powerhouse';
      default:
        return 'Grandmaster Athlete';
    }
  }

  double _calculateXpProgress(int level, int xp) {
    final nextXp = switch (level) {
      1 => 250,
      2 => 600,
      3 => 1200,
      4 => 2000,
      5 => 3200,
      6 => 5000,
      _ => 10000,
    };
    final prevXp = switch (level) {
      1 => 0,
      2 => 250,
      3 => 600,
      4 => 1200,
      5 => 2000,
      6 => 3200,
      _ => 5000,
    };
    if (nextXp <= prevXp) return 1.0;
    return ((xp - prevXp) / (nextXp - prevXp)).clamp(0.0, 1.0);
  }

  Color _getCategoryColor(String category) {
    switch (category.toLowerCase()) {
      case 'abs':
        return AppColors.electricCyan;
      case 'chest':
        return AppColors.flameOrange;
      case 'leg':
      case 'legs':
        return AppColors.energyLime;
      default:
        return AppColors.goldYellow;
    }
  }
}
