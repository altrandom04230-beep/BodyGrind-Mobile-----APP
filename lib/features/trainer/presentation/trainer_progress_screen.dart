import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/trainer_models.dart';
import '../../../data/providers/app_providers.dart';
import 'widgets/trainer_member_search_bar.dart';

class TrainerProgressScreen extends ConsumerStatefulWidget {
  final String? initialMemberId;

  const TrainerProgressScreen({super.key, this.initialMemberId});

  @override
  ConsumerState<TrainerProgressScreen> createState() => _TrainerProgressScreenState();
}

class _TrainerProgressScreenState extends ConsumerState<TrainerProgressScreen> {
  String? _selectedMemberId;

  @override
  void initState() {
    super.initState();
    _selectedMemberId = widget.initialMemberId;
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final members = ref.watch(assignedMembersStreamProvider).valueOrNull ?? [];

    if (members.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: const Text('Member Progress')),
        body: const Center(child: Text('No assigned members found')),
      );
    }

    final activeMember = _selectedMemberId != null
        ? members.firstWhere((m) => m.id == _selectedMemberId, orElse: () => members.first)
        : members.first;

    final delta = activeMember.weightChangeKg;
    final deltaSign = delta >= 0 ? '+' : '';

    return Scaffold(
      appBar: AppBar(
        title: const Text('Member Progress Monitoring', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // UI FIX 5: Modern Reusable Member Search Bar at the top
          TrainerMemberSearchBar(
            members: members,
            placeholderText: 'Search member to view progress...',
            onMemberSelected: (id) {
              setState(() => _selectedMemberId = id);
            },
          ),
          const SizedBox(height: 12),

          // Horizontal Member Selector Chips
          SizedBox(
            height: 42,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: members.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, idx) {
                final m = members[idx];
                final isSelected = m.id == activeMember.id;

                return FilterChip(
                  selected: isSelected,
                  label: Text(m.name),
                  selectedColor: AppColors.boldRed,
                  labelStyle: TextStyle(
                    color: isSelected ? Colors.white : null,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                  onSelected: (_) => setState(() => _selectedMemberId = m.id),
                );
              },
            ),
          ),
          const SizedBox(height: 16),

          // Fitness Goals Card
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
                  Text('Fitness Goal', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 6),
                  Text(
                    activeMember.fitnessGoal,
                    style: TextStyle(
                      color: theme.colorScheme.primary,
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Level: ${activeMember.fitnessLevel} • Weekly Target: ${activeMember.completedWorkoutsThisWeek}/${activeMember.weeklyTargetWorkouts} workouts',
                    style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Weight Changes & BMI Trends Card
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
                  Text('Weight Changes & BMI Trends', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _buildTrendMetric(
                        'Start -> Current',
                        '${activeMember.startingWeightKg.toStringAsFixed(1)} -> ${activeMember.currentWeightKg.toStringAsFixed(1)} kg',
                      ),
                      _buildTrendMetric(
                        'Net Change',
                        '$deltaSign${delta.toStringAsFixed(1)} kg',
                        color: delta <= 0 ? AppColors.successGreen : AppColors.flameOrange,
                      ),
                      _buildTrendMetric(
                        'Current BMI',
                        activeMember.currentBmi.toStringAsFixed(1),
                        color: AppColors.electricCyan,
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  Text(
                    '6-Week Weight Trend (kg)',
                    style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 90,
                    width: double.infinity,
                    child: _buildMiniLineChart(
                      values: activeMember.weightBmiHistory.map((p) => p.weightKg).toList(),
                      color: theme.colorScheme.primary,
                    ),
                  ),
                  const SizedBox(height: 14),
                  Text(
                    '6-Week BMI Trend',
                    style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 90,
                    width: double.infinity,
                    child: _buildMiniLineChart(
                      values: activeMember.weightBmiHistory.map((p) => p.bmi).toList(),
                      color: AppColors.successGreen,
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // Recent Workout History
          Text('Recent Workouts Logged', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          ...activeMember.workoutHistory.map((workout) {
            return Card(
              elevation: 0,
              margin: const EdgeInsets.only(bottom: 8),
              shape: RoundedCornerShape(12),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: AppColors.flameOrange.withOpacity(0.15),
                  child: const Icon(Icons.fitness_center, color: AppColors.flameOrange, size: 20),
                ),
                title: Text(workout.title, style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text('${workout.durationMinutes} mins • ${workout.caloriesBurned} kcal'),
                trailing: Text(workout.dateLabel, style: theme.textTheme.labelSmall),
              ),
            );
          }),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildTrendMetric(String label, String value, {Color? color}) {
    return Column(
      children: [
        Text(value, style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: color)),
        const SizedBox(height: 2),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }

  Widget _buildMiniLineChart({required List<double> values, required Color color}) {
    if (values.length < 2) {
      return const Center(child: Text('Not enough data points'));
    }

    return CustomPaint(
      painter: _SimpleChartPainter(values: values, color: color),
    );
  }
}

class _SimpleChartPainter extends CustomPainter {
  final List<double> values;
  final Color color;

  _SimpleChartPainter({required this.values, required this.color});

  @override
  void paint(Canvas canvas, Size size) {
    if (values.length < 2) return;

    final minVal = values.reduce((a, b) => a < b ? a : b);
    final maxVal = values.reduce((a, b) => a > b ? a : b);
    final range = (maxVal - minVal) == 0 ? 1.0 : (maxVal - minVal);

    final path = Path();
    final pointPaint = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    final linePaint = Paint()
      ..color = color
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;

    final dx = size.width / (values.length - 1);

    for (int i = 0; i < values.length; i++) {
      final x = i * dx;
      final y = size.height - ((values[i] - minVal) / range * (size.height - 16)) - 8;

      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }

      canvas.drawCircle(Offset(x, y), 3.5, pointPaint);
    }

    canvas.drawPath(path, linePaint);
  }

  @override
  bool shouldRepaint(covariant _SimpleChartPainter oldDelegate) => true;
}
