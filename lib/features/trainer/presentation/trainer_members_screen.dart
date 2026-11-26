import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/app_role.dart';
import '../../../data/models/trainer_models.dart';
import '../../../data/providers/app_providers.dart';

class TrainerMembersScreen extends ConsumerStatefulWidget {
  final String? initialMemberId;

  const TrainerMembersScreen({super.key, this.initialMemberId});

  @override
  ConsumerState<TrainerMembersScreen> createState() => _TrainerMembersScreenState();
}

class _TrainerMembersScreenState extends ConsumerState<TrainerMembersScreen> {
  String? _selectedMemberId;
  MemberStatus? _statusFilter;

  @override
  void initState() {
    super.initState();
    _selectedMemberId = widget.initialMemberId;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final members = ref.watch(assignedMembersStreamProvider).valueOrNull ?? [];

    final selectedMember = _selectedMemberId != null
        ? members.firstWhere((m) => m.id == _selectedMemberId, orElse: () => members.first)
        : null;

    if (selectedMember != null) {
      return _buildMemberDetailView(theme, selectedMember);
    }

    return _buildMembersListView(theme, members);
  }

  Widget _buildMembersListView(ThemeData theme, List<AssignedMember> members) {
    final filteredMembers = _statusFilter == null
        ? members
        : members.where((m) => m.status == _statusFilter).toList();

    final activeCount = members.where((m) => m.status == MemberStatus.active).length;
    final attentionCount = members.where((m) => m.status == MemberStatus.needsAttention).length;
    final pausedCount = members.where((m) => m.status == MemberStatus.paused).length;

    return Scaffold(
      appBar: AppBar(
        title: Text('Assigned Members (${members.length})', style: const TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // UI FIX 3: 4 buttons arranged in an evenly spaced 2x2 grid layout
          Column(
            children: [
              Row(
                children: [
                  Expanded(
                    child: _buildFilterButton(
                      label: 'All (${members.length})',
                      isSelected: _statusFilter == null,
                      onTap: () => setState(() => _statusFilter = null),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _buildFilterButton(
                      label: 'Active ($activeCount)',
                      isSelected: _statusFilter == MemberStatus.active,
                      onTap: () => setState(() => _statusFilter = MemberStatus.active),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: _buildFilterButton(
                      label: 'Attention ($attentionCount)',
                      isSelected: _statusFilter == MemberStatus.needsAttention,
                      onTap: () => setState(() => _statusFilter = MemberStatus.needsAttention),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: _buildFilterButton(
                      label: 'Paused ($pausedCount)',
                      isSelected: _statusFilter == MemberStatus.paused,
                      onTap: () => setState(() => _statusFilter = MemberStatus.paused),
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 16),

          // Member Cards List
          ...filteredMembers.map((member) {
            final statusColor = switch (member.status) {
              MemberStatus.active => AppColors.successGreen,
              MemberStatus.needsAttention => AppColors.warningAmber,
              MemberStatus.paused => theme.colorScheme.onSurfaceVariant,
            };
            final initials = member.name.split(' ').take(2).map((s) => s.isNotEmpty ? s[0] : '').join();

            return Card(
              elevation: 0,
              margin: const EdgeInsets.only(bottom: 12),
              shape: RoundedCornerShape(18),
              color: theme.colorScheme.surface,
              child: InkWell(
                borderRadius: BorderRadius.circular(18),
                onTap: () => setState(() => _selectedMemberId = member.id),
                child: Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(18),
                    border: Border.all(color: theme.colorScheme.outlineVariant),
                  ),
                  child: Row(
                    children: [
                      CircleAvatar(
                        radius: 24,
                        backgroundColor: theme.colorScheme.primary.withOpacity(0.15),
                        child: Text(
                          initials,
                          style: TextStyle(
                            color: theme.colorScheme.primary,
                            fontWeight: FontWeight.bold,
                            fontSize: 14,
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
                                Text(member.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: statusColor.withOpacity(0.15),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(
                                    member.status.label,
                                    style: TextStyle(color: statusColor, fontSize: 10, fontWeight: FontWeight.bold),
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              member.fitnessGoal,
                              style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                            ),
                            const SizedBox(height: 6),
                            Text(
                              'Completion ${member.workoutCompletionRate}% • ${member.completedWorkoutsThisWeek}/${member.weeklyTargetWorkouts} this week',
                              style: TextStyle(
                                color: theme.colorScheme.primary,
                                fontSize: 11,
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
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

  Widget _buildFilterButton({
    required String label,
    required bool isSelected,
    required VoidCallback onTap,
  }) {
    final theme = Theme.of(context);

    return SizedBox(
      height: 44,
      child: OutlinedButton(
        style: OutlinedButton.styleFrom(
          backgroundColor: isSelected ? AppColors.boldRed : Colors.transparent,
          foregroundColor: isSelected ? Colors.white : theme.colorScheme.onSurface,
          side: BorderSide(
            color: isSelected ? AppColors.boldRed : theme.colorScheme.outlineVariant,
          ),
          shape: RoundedCornerShape(12),
          padding: const EdgeInsets.symmetric(horizontal: 8),
        ),
        onPressed: onTap,
        child: Text(
          label,
          style: TextStyle(
            fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
            fontSize: 12,
          ),
          textAlign: TextAlign.center,
          overflow: TextOverflow.ellipsis,
        ),
      ),
    );
  }

  Widget _buildMemberDetailView(ThemeData theme, AssignedMember member) {
    final initials = member.name.split(' ').take(2).map((s) => s.isNotEmpty ? s[0] : '').join();

    return Scaffold(
      appBar: AppBar(
        title: Text(member.name, style: const TextStyle(fontWeight: FontWeight.bold)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () => setState(() => _selectedMemberId = null),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // Header Card
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
                children: [
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 28,
                        backgroundColor: theme.colorScheme.primary.withOpacity(0.15),
                        child: Text(
                          initials,
                          style: TextStyle(
                            color: theme.colorScheme.primary,
                            fontWeight: FontWeight.bold,
                            fontSize: 18,
                          ),
                        ),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(member.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                            Text('${member.age} yrs • ${member.gender} • ${member.email}', style: theme.textTheme.bodySmall),
                            Text('Joined: ${member.joinDate}', style: theme.textTheme.labelSmall?.copyWith(color: Colors.grey)),
                          ],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  // Quick Actions
                  Row(
                    children: [
                      Expanded(
                        child: FilledButton.tonalIcon(
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.flameOrange.withOpacity(0.15),
                            foregroundColor: AppColors.flameOrange,
                          ),
                          icon: const Icon(Icons.insights, size: 16),
                          label: const Text('Progress', style: TextStyle(fontWeight: FontWeight.bold)),
                          onPressed: () => context.go('/trainer/progress'),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: FilledButton.tonalIcon(
                          style: FilledButton.styleFrom(
                            backgroundColor: AppColors.electricCyan.withOpacity(0.15),
                            foregroundColor: AppColors.electricCyan,
                          ),
                          icon: const Icon(Icons.chat_bubble_outline, size: 16),
                          label: const Text('Feedback', style: TextStyle(fontWeight: FontWeight.bold)),
                          onPressed: () => context.go('/trainer/feedback'),
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Biometrics & Targets
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
                  Text('Biometrics & Goals', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _buildDetailStat('Weight', '${member.currentWeightKg.toStringAsFixed(1)} kg'),
                      _buildDetailStat('Height', '${member.heightCm.toStringAsFixed(0)} cm'),
                      _buildDetailStat('BMI', member.currentBmi.toStringAsFixed(1)),
                      _buildDetailStat('Goal Wt', '${member.targetWeightKg.toStringAsFixed(1)} kg'),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Exercise Performance Records
          if (member.exercisePerformance.isNotEmpty) ...[
            Text('Exercise Performance Benchmarks', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            ...member.exercisePerformance.map((perf) {
              return Card(
                elevation: 0,
                margin: const EdgeInsets.only(bottom: 8),
                shape: RoundedCornerShape(14),
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(perf.exerciseName, style: const TextStyle(fontWeight: FontWeight.bold)),
                          Text('Metric: ${perf.targetMetric}', style: theme.textTheme.bodySmall),
                        ],
                      ),
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.end,
                        children: [
                          Text(
                            '${perf.previousBest} -> ${perf.currentBest}',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                          ),
                          Text(
                            perf.trendLabel,
                            style: TextStyle(
                              color: perf.isImproving ? AppColors.successGreen : AppColors.flameOrange,
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              );
            }),
          ],
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildDetailStat(String label, String value) {
    return Column(
      children: [
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
        const SizedBox(height: 2),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }
}
