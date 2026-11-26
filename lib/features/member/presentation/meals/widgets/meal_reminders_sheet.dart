import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/meal_models.dart';
import '../models/meal_reminder_models.dart';
import '../providers/meal_reminder_providers.dart';

class MealRemindersSheet extends ConsumerWidget {
  const MealRemindersSheet({super.key});

  static void show(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => const MealRemindersSheet(),
    );
  }

  IconData _getSlotIcon(MealSlot slot) {
    switch (slot) {
      case MealSlot.breakfast:
        return Icons.wb_sunny_outlined;
      case MealSlot.morningSnack:
        return Icons.bakery_dining_outlined;
      case MealSlot.lunch:
        return Icons.lunch_dining_outlined;
      case MealSlot.afternoonSnack:
        return Icons.local_cafe_outlined;
      case MealSlot.dinner:
        return Icons.nightlight_round_outlined;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final remindersState = ref.watch(mealRemindersProvider);
    final notifier = ref.read(mealRemindersProvider.notifier);

    return Container(
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: EdgeInsets.fromLTRB(
        20,
        14,
        20,
        MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      constraints: BoxConstraints(
        maxHeight: MediaQuery.of(context).size.height * 0.85,
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Drag handle
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

          // Header with Master Toggle
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.flameOrange.withOpacity(0.15),
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: const Icon(Icons.notifications_active, color: AppColors.flameOrange, size: 20),
                  ),
                  const SizedBox(width: 12),
                  Text(
                    'Meal Reminders',
                    style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                ],
              ),
              Switch(
                value: remindersState.masterEnabled,
                activeColor: AppColors.flameOrange,
                onChanged: (val) => notifier.toggleMaster(val),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            'Receive reminders at your scheduled meal times so you never miss logging food.',
            style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
          const SizedBox(height: 12),
          const Divider(),
          const SizedBox(height: 8),

          // Slot Reminders List
          Flexible(
            child: ListView.separated(
              shrinkWrap: true,
              itemCount: remindersState.settings.length,
              separatorBuilder: (_, __) => const SizedBox(height: 6),
              itemBuilder: (context, index) {
                final setting = remindersState.settings[index];
                final isEnabled = remindersState.masterEnabled && setting.isEnabled;

                return Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  decoration: BoxDecoration(
                    color: isEnabled
                        ? theme.colorScheme.surfaceContainerHighest.withOpacity(0.35)
                        : theme.colorScheme.surfaceContainerHighest.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(14),
                  ),
                  child: Row(
                    children: [
                      Icon(
                        _getSlotIcon(setting.slot),
                        color: isEnabled ? AppColors.flameOrange : Colors.grey,
                        size: 22,
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              setting.slot.displayName,
                              style: TextStyle(
                                fontWeight: FontWeight.bold,
                                fontSize: 14,
                                color: isEnabled ? null : Colors.grey,
                              ),
                            ),
                            Text(
                              'Target: ${setting.slot.suggestedKcal} kcal',
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      ),

                      // Time Selector Button (Minimum 48dp touch target)
                      SizedBox(
                        height: 48,
                        child: OutlinedButton(
                          style: OutlinedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 12),
                            shape: RoundedCornerShape(10),
                            side: BorderSide(
                              color: isEnabled ? AppColors.flameOrange : Colors.grey.withOpacity(0.4),
                            ),
                          ),
                          onPressed: remindersState.masterEnabled
                              ? () async {
                                  final picked = await showTimePicker(
                                    context: context,
                                    initialTime: TimeOfDay(
                                      hour: setting.hour,
                                      minute: setting.minute,
                                    ),
                                  );
                                  if (picked != null) {
                                    notifier.updateSlotTime(
                                      setting.slot,
                                      picked.hour,
                                      picked.minute,
                                    );
                                  }
                                }
                              : null,
                          child: Text(
                            setting.timeFormatted,
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 13,
                              color: isEnabled ? AppColors.flameOrange : Colors.grey,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),

                      // Individual Slot Switch
                      Checkbox(
                        value: setting.isEnabled,
                        activeColor: AppColors.flameOrange,
                        onChanged: remindersState.masterEnabled
                            ? (val) {
                                if (val != null) {
                                  notifier.toggleSlot(setting.slot, val);
                                }
                              }
                            : null,
                      ),
                    ],
                  ),
                );
              },
            ),
          ),

          const SizedBox(height: 16),

          // Action Buttons: Send Test Reminder & Close
          Row(
            children: [
              Expanded(
                child: SizedBox(
                  height: 48,
                  child: OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      shape: RoundedCornerShape(12),
                    ),
                    icon: const Icon(Icons.send_outlined, size: 16),
                    label: const Text('Test Reminder', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                    onPressed: () async {
                      await notifier.sendTestReminder();
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Test notification sent! Check your notification shade.'),
                            duration: Duration(seconds: 2),
                          ),
                        );
                      }
                    },
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
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Done', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
