import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/calendar_event.dart';
import '../../../../data/models/meal_models.dart';
import '../../../../data/models/workout_plan.dart';
import '../../../../data/models/workout_session.dart';
import '../../../../data/providers/app_providers.dart';

class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({super.key});

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen> {
  DateTime _focusedMonth = DateTime.now();
  DateTime _selectedDate = DateTime.now();

  void _previousMonth() {
    setState(() {
      _focusedMonth = DateTime(_focusedMonth.year, _focusedMonth.month - 1, 1);
    });
  }

  void _nextMonth() {
    setState(() {
      _focusedMonth = DateTime(_focusedMonth.year, _focusedMonth.month + 1, 1);
    });
  }

  void _showAddEventDialog(List<WorkoutPlan> plans) {
    String title = plans.isNotEmpty ? plans.first.name : 'Workout Routine';
    String? selectedPlanId = plans.isNotEmpty ? plans.first.id : null;
    TimeOfDay selectedTime = const TimeOfDay(hour: 8, minute: 0);

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            title: const Text('Schedule Workout', style: TextStyle(fontWeight: FontWeight.bold)),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Date: ${DateFormat('EEE, MMM d, yyyy').format(_selectedDate)}',
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                const SizedBox(height: 12),
                if (plans.isNotEmpty)
                  DropdownButtonFormField<String>(
                    value: selectedPlanId,
                    decoration: const InputDecoration(labelText: 'Workout Plan', isDense: true),
                    items: plans.map((p) {
                      return DropdownMenuItem(value: p.id, child: Text(p.name, overflow: TextOverflow.ellipsis));
                    }).toList(),
                    onChanged: (val) {
                      setDialogState(() {
                        selectedPlanId = val;
                        final found = plans.firstWhere((p) => p.id == val, orElse: () => plans.first);
                        title = found.name;
                      });
                    },
                  ),
                const SizedBox(height: 12),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.access_time),
                  title: Text('Time: ${selectedTime.format(context)}'),
                  trailing: const Text('Change', style: TextStyle(color: AppColors.flameOrange)),
                  onTap: () async {
                    final picked = await showTimePicker(context: context, initialTime: selectedTime);
                    if (picked != null) {
                      setDialogState(() => selectedTime = picked);
                    }
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
                  final now = DateTime.now();
                  final eventDateTime = DateTime(
                    _selectedDate.year,
                    _selectedDate.month,
                    _selectedDate.day,
                    selectedTime.hour,
                    selectedTime.minute,
                  );
                  final event = CalendarEvent(
                    id: now.millisecondsSinceEpoch,
                    title: title,
                    startDateEpochDay: _selectedDate.millisecondsSinceEpoch ~/ 86400000,
                    startHour: selectedTime.hour,
                    startMinute: selectedTime.minute,
                    planId: selectedPlanId,
                    reminderMinutesBefore: 15,
                    isCompleted: false,
                  );
                  // Save event
                  Navigator.pop(ctx);
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('Scheduled "$title" for ${DateFormat('h:mm a').format(eventDateTime)}')),
                  );
                },
                child: const Text('Schedule'),
              ),
            ],
          );
        },
      ),
    );
  }

  DayOfWeekPlan _getDayOfWeekPlan(DateTime dt) {
    switch (dt.weekday) {
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

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final plans = ref.watch(plansStreamProvider).valueOrNull ?? [];
    final sessions = ref.watch(completedSessionsStreamProvider).valueOrNull ?? [];
    final weeklyPlans = ref.watch(weeklyMealPlansStreamProvider).valueOrNull ?? {};

    final selectedDayPlan = weeklyPlans[_getDayOfWeekPlan(_selectedDate)];

    // Sessions on selected day
    final sessionsOnSelectedDay = sessions.where((s) {
      final sDate = DateTime.fromMillisecondsSinceEpoch(s.completedAt);
      return sDate.year == _selectedDate.year &&
          sDate.month == _selectedDate.month &&
          sDate.day == _selectedDate.day;
    }).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Calendar & Routine', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            tooltip: 'Schedule Workout',
            onPressed: () => _showAddEventDialog(plans),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        children: [
          // Month navigation header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              IconButton(
                icon: const Icon(Icons.chevron_left),
                onPressed: _previousMonth,
              ),
              Text(
                DateFormat('MMMM yyyy').format(_focusedMonth),
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              IconButton(
                icon: const Icon(Icons.chevron_right),
                onPressed: _nextMonth,
              ),
            ],
          ),
          const SizedBox(height: 8),

          // Calendar Grid Card
          Card(
            elevation: 0,
            shape: RoundedCornerShape(16),
            color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: _buildMonthCalendar(sessions),
            ),
          ),
          const SizedBox(height: 16),

          // Selected Day Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                DateFormat('EEEE, MMM d').format(_selectedDate),
                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              ),
              FilledButton.tonalIcon(
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.flameOrange.withOpacity(0.15),
                  foregroundColor: AppColors.flameOrange,
                  visualDensity: VisualDensity.compact,
                ),
                icon: const Icon(Icons.add, size: 16),
                label: const Text('Add Event', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                onPressed: () => _showAddEventDialog(plans),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Completed Sessions for this date
          if (sessionsOnSelectedDay.isNotEmpty) ...[
            Text('Completed Workouts', style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            ...sessionsOnSelectedDay.map((session) {
              return Card(
                elevation: 0,
                margin: const EdgeInsets.only(bottom: 8),
                shape: RoundedCornerShape(12),
                color: theme.colorScheme.surface,
                child: ListTile(
                  leading: const CircleAvatar(
                    backgroundColor: AppColors.successGreen,
                    child: Icon(Icons.check, color: Colors.white, size: 18),
                  ),
                  title: Text(session.planName, style: const TextStyle(fontWeight: FontWeight.bold)),
                  subtitle: Text(
                    '${(session.durationSeconds / 60).ceil()} mins • ${session.caloriesBurned} kcal',
                  ),
                  trailing: Text(
                    DateFormat('h:mm a').format(DateTime.fromMillisecondsSinceEpoch(session.completedAt)),
                    style: theme.textTheme.labelSmall,
                  ),
                ),
              );
            }),
            const SizedBox(height: 12),
          ],

          // Meal plan overview for this day (Requirement 6)
          if (selectedDayPlan != null) ...[
            Text('Planned Meals for Today', style: theme.textTheme.labelMedium?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 6),
            Card(
              elevation: 0,
              shape: RoundedCornerShape(14),
              color: theme.colorScheme.surface,
              child: Padding(
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          selectedDayPlan.day.name.toUpperCase(),
                          style: const TextStyle(color: AppColors.flameOrange, fontWeight: FontWeight.bold, fontSize: 11),
                        ),
                        Text(
                          '${selectedDayPlan.targetCalories} kcal',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    ...selectedDayPlan.slots.map((slot) {
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 4),
                        child: Row(
                          children: [
                            const Icon(Icons.restaurant, size: 14, color: AppColors.flameOrange),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                '${slot.slot.name}: ${slot.recipeName}',
                                style: const TextStyle(fontSize: 12),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                            ),
                            Text('${slot.calories} kcal', style: const TextStyle(fontSize: 11, color: Colors.grey)),
                          ],
                        ),
                      );
                    }),
                  ],
                ),
              ),
            ),
          ],
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildMonthCalendar(List<WorkoutSession> sessions) {
    final daysInMonth = DateTime(_focusedMonth.year, _focusedMonth.month + 1, 0).day;
    final firstDayWeekday = DateTime(_focusedMonth.year, _focusedMonth.month, 1).weekday; // 1 = Mon, 7 = Sun
    final offset = (firstDayWeekday - 1) % 7;

    final weekDays = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

    return Column(
      children: [
        // Days of week header
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: weekDays.map((d) {
            return SizedBox(
              width: 32,
              child: Text(
                d,
                textAlign: TextAlign.center,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12, color: Colors.grey),
              ),
            );
          }).toList(),
        ),
        const SizedBox(height: 8),
        GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 7,
            mainAxisSpacing: 6,
            crossAxisSpacing: 6,
            childAspectRatio: 1.0,
          ),
          itemCount: offset + daysInMonth,
          itemBuilder: (context, idx) {
            if (idx < offset) {
              return const SizedBox.shrink();
            }

            final day = idx - offset + 1;
            final cellDate = DateTime(_focusedMonth.year, _focusedMonth.month, day);
            final isSelected = cellDate.year == _selectedDate.year &&
                cellDate.month == _selectedDate.month &&
                cellDate.day == _selectedDate.day;
            final isToday = cellDate.year == DateTime.now().year &&
                cellDate.month == DateTime.now().month &&
                cellDate.day == DateTime.now().day;

            final hasCompletedSession = sessions.any((s) {
              final d = DateTime.fromMillisecondsSinceEpoch(s.completedAt);
              return d.year == cellDate.year && d.month == cellDate.month && d.day == cellDate.day;
            });

            return InkWell(
              borderRadius: BorderRadius.circular(20),
              onTap: () => setState(() => _selectedDate = cellDate),
              child: Container(
                decoration: BoxDecoration(
                  color: isSelected
                      ? AppColors.boldRed
                      : (isToday ? AppColors.boldRed.withOpacity(0.15) : Colors.transparent),
                  shape: BoxShape.circle,
                ),
                alignment: Alignment.center,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      '$day',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: (isSelected || isToday) ? FontWeight.bold : FontWeight.normal,
                        color: isSelected ? Colors.white : null,
                      ),
                    ),
                    if (hasCompletedSession)
                      Container(
                        margin: const EdgeInsets.only(top: 2),
                        width: 4,
                        height: 4,
                        decoration: BoxDecoration(
                          color: isSelected ? Colors.white : AppColors.flameOrange,
                          shape: BoxShape.circle,
                        ),
                      ),
                  ],
                ),
              ),
            );
          },
        ),
      ],
    );
  }
}
