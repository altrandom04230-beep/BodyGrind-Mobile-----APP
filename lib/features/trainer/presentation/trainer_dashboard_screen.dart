import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/trainer_models.dart';
import '../../../data/providers/app_providers.dart';

class TrainerDashboardScreen extends ConsumerWidget {
  const TrainerDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final trainerInfo = ref.watch(trainerInfoStreamProvider).valueOrNull ??
        ref.watch(trainerRepositoryProvider).currentTrainerInfo;
    final members = ref.watch(assignedMembersStreamProvider).valueOrNull ?? [];
    final pendingTasks = ref.watch(pendingTasksStreamProvider).valueOrNull ?? [];
    final notifications = ref.watch(trainerNotificationsStreamProvider).valueOrNull ?? [];

    final totalAssigned = members.length;
    final activeCount = members.where((m) => m.status == MemberStatus.active).length;
    final avgCompletion = members.isNotEmpty
        ? (members.map((m) => m.workoutCompletionRate).reduce((a, b) => a + b) ~/ members.length)
        : 0;
    final avgAttendance = members.isNotEmpty
        ? (members.map((m) => m.attendanceRate).reduce((a, b) => a + b) ~/ members.length)
        : 0;

    final recentCheckIns = members
        .expand((m) => m.attendanceHistory.take(1).map((r) => MapEntry(m, r)))
        .take(4)
        .toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Trainer Dashboard', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // Welcome Card (UI Fix 4: No logout button in this card)
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
                  Text(
                    'Welcome, ${trainerInfo.name}',
                    style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.black),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    trainerInfo.title,
                    style: TextStyle(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    trainerInfo.specialization,
                    style: theme.textTheme.bodySmall?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // KPI Grid: 4 Stat Cards
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  theme,
                  title: 'Total Assigned',
                  value: '$totalAssigned',
                  subtitle: 'Members',
                  icon: Icons.groups,
                  color: theme.colorScheme.primary,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildStatCard(
                  theme,
                  title: 'Active Members',
                  value: '$activeCount',
                  subtitle: 'On-track this week',
                  icon: Icons.verified_user,
                  color: AppColors.successGreen,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _buildStatCard(
                  theme,
                  title: 'Workout Completion',
                  value: '$avgCompletion%',
                  subtitle: 'Team average',
                  icon: Icons.task_alt,
                  color: theme.colorScheme.primary,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _buildStatCard(
                  theme,
                  title: 'Recent Attendance',
                  value: '$avgAttendance%',
                  subtitle: 'Monthly check-ins',
                  icon: Icons.qr_code_scanner,
                  color: AppColors.warningAmber,
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),

          // Recent Attendance Section
          Card(
            elevation: 0,
            shape: RoundedCornerShape(18),
            color: theme.colorScheme.surface,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.schedule, color: theme.colorScheme.primary, size: 20),
                      const SizedBox(width: 8),
                      Text('Recent Attendance', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  ...recentCheckIns.map((entry) {
                    final member = entry.key;
                    final record = entry.value;

                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(member.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                              Text(
                                '${record.dateLabel} at ${record.timeLabel} • ${record.method}',
                                style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                              ),
                            ],
                          ),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppColors.successGreen.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              record.status,
                              style: const TextStyle(
                                color: AppColors.successGreen,
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ],
                      ),
                    );
                  }),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Member Progress Overview Section
          Card(
            elevation: 0,
            shape: RoundedCornerShape(18),
            color: theme.colorScheme.surface,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.insights, color: theme.colorScheme.primary, size: 20),
                      const SizedBox(width: 8),
                      Text('Member Progress Overview', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                    ],
                  ),
                  const SizedBox(height: 12),
                  ...members.map((member) {
                    final deltaSign = member.weightChangeKg >= 0 ? '+' : '';
                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 6),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(member.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                              Text(
                                '${member.workoutCompletionRate}% workouts • $deltaSign${member.weightChangeKg.toStringAsFixed(1)} kg',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: theme.colorScheme.primary,
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 6),
                          ClipRRect(
                            borderRadius: BorderRadius.circular(3),
                            child: LinearProgressIndicator(
                              value: member.workoutCompletionRate / 100.0,
                              minHeight: 6,
                              color: member.workoutCompletionRate >= 80 ? AppColors.successGreen : AppColors.warningAmber,
                              backgroundColor: theme.colorScheme.surfaceContainerHighest,
                            ),
                          ),
                        ],
                      ),
                    );
                  }),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Pending Tasks Section
          if (pendingTasks.isNotEmpty) ...[
            Card(
              elevation: 0,
              shape: RoundedCornerShape(18),
              color: theme.colorScheme.surface,
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.checklist, color: theme.colorScheme.primary, size: 20),
                        const SizedBox(width: 8),
                        Text('Pending Tasks', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 10),
                    ...pendingTasks.map((task) {
                      return CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        value: task.isCompleted,
                        onChanged: (_) {
                          ref.read(trainerRepositoryProvider).toggleTask(task.id);
                        },
                        title: Text(
                          task.title,
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            decoration: task.isCompleted ? TextDecoration.lineThrough : null,
                          ),
                        ),
                        subtitle: Text('${task.memberName} • Due ${task.dueLabel}'),
                      );
                    }),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
          ],

          // Notifications Section
          if (notifications.isNotEmpty) ...[
            Card(
              elevation: 0,
              shape: RoundedCornerShape(18),
              color: theme.colorScheme.surface,
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(18),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.notifications_active_outlined, color: AppColors.flameOrange, size: 20),
                        const SizedBox(width: 8),
                        Text('Recent Alerts', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                      ],
                    ),
                    const SizedBox(height: 10),
                    ...notifications.map((notif) {
                      return ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: CircleAvatar(
                          radius: 14,
                          backgroundColor: notif.isUnread ? AppColors.boldRed : Colors.grey,
                          child: const Icon(Icons.circle, size: 8, color: Colors.white),
                        ),
                        title: Text(notif.title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                        subtitle: Text(notif.message, style: const TextStyle(fontSize: 11)),
                        trailing: Text(notif.timeLabel, style: theme.textTheme.labelSmall),
                      );
                    }),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 20),
          ],

          const SizedBox(height: 16),
        ],
      ),
    );
  }

  Widget _buildStatCard(
    ThemeData theme, {
    required String title,
    required String value,
    required String subtitle,
    required IconData icon,
    required Color color,
  }) {
    return Card(
      elevation: 0,
      shape: RoundedCornerShape(16),
      color: theme.colorScheme.surface,
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: theme.colorScheme.outlineVariant),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(title, style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                Icon(icon, size: 18, color: color),
              ],
            ),
            const SizedBox(height: 6),
            Text(value, style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.black)),
            Text(subtitle, style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          ],
        ),
      ),
    );
  }
}
