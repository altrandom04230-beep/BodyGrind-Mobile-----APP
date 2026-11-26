import '../../../../../data/models/meal_models.dart';

/// Configuration for a specific meal slot reminder
class MealReminderSetting {
  final MealSlot slot;
  final bool isEnabled;
  final int hour;
  final int minute;

  const MealReminderSetting({
    required this.slot,
    this.isEnabled = true,
    required this.hour,
    required this.minute,
  });

  /// Unique notification ID for this slot
  int get notificationId => 100 + slot.index;

  /// User-friendly formatted time string (e.g. "8:00 AM", "1:30 PM")
  String get timeFormatted {
    final period = hour >= 12 ? 'PM' : 'AM';
    final displayHour = hour % 12 == 0 ? 12 : hour % 12;
    final displayMin = minute.toString().padLeft(2, '0');
    return '$displayHour:$displayMin $period';
  }

  MealReminderSetting copyWith({
    MealSlot? slot,
    bool? isEnabled,
    int? hour,
    int? minute,
  }) {
    return MealReminderSetting(
      slot: slot ?? this.slot,
      isEnabled: isEnabled ?? this.isEnabled,
      hour: hour ?? this.hour,
      minute: minute ?? this.minute,
    );
  }

  Map<String, dynamic> toJson() => {
        'slot': slot.name,
        'isEnabled': isEnabled,
        'hour': hour,
        'minute': minute,
      };

  factory MealReminderSetting.fromJson(Map<String, dynamic> json) {
    return MealReminderSetting(
      slot: MealSlot.fromString(json['slot'] as String? ?? 'breakfast'),
      isEnabled: json['isEnabled'] as bool? ?? true,
      hour: (json['hour'] as num?)?.toInt() ?? 8,
      minute: (json['minute'] as num?)?.toInt() ?? 0,
    );
  }

  /// Default baseline reminder schedules for all 5 meal slots
  static List<MealReminderSetting> defaultSettings() => const [
        MealReminderSetting(slot: MealSlot.breakfast, hour: 8, minute: 0),
        MealReminderSetting(slot: MealSlot.morningSnack, hour: 10, minute: 30),
        MealReminderSetting(slot: MealSlot.lunch, hour: 13, minute: 0),
        MealReminderSetting(slot: MealSlot.afternoonSnack, hour: 16, minute: 30),
        MealReminderSetting(slot: MealSlot.dinner, hour: 19, minute: 30),
      ];
}
