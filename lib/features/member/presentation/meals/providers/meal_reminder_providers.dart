import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../../../../data/models/meal_models.dart';
import '../models/meal_reminder_models.dart';
import '../services/meal_reminder_service.dart';

class MealRemindersState {
  final bool masterEnabled;
  final List<MealReminderSetting> settings;
  final bool isLoaded;

  const MealRemindersState({
    this.masterEnabled = true,
    this.settings = const [],
    this.isLoaded = false,
  });

  MealRemindersState copyWith({
    bool? masterEnabled,
    List<MealReminderSetting>? settings,
    bool? isLoaded,
  }) {
    return MealRemindersState(
      masterEnabled: masterEnabled ?? this.masterEnabled,
      settings: settings ?? this.settings,
      isLoaded: isLoaded ?? this.isLoaded,
    );
  }
}

class MealRemindersNotifier extends StateNotifier<MealRemindersState> {
  final MealReminderService _service;
  static const String _keyMaster = 'meal_reminders_master_enabled';
  static const String _keySettings = 'meal_reminders_slots_v1_json';

  MealRemindersNotifier(this._service)
      : super(MealRemindersState(settings: MealReminderSetting.defaultSettings())) {
    _loadAndSchedule();
  }

  Future<void> _loadAndSchedule() async {
    final prefs = await SharedPreferences.getInstance();
    final master = prefs.getBool(_keyMaster) ?? true;
    final jsonStr = prefs.getString(_keySettings);

    List<MealReminderSetting> loaded = [];
    if (jsonStr != null) {
      try {
        final List<dynamic> arr = jsonDecode(jsonStr);
        loaded = arr.map((e) => MealReminderSetting.fromJson(e as Map<String, dynamic>)).toList();
      } catch (_) {}
    }

    if (loaded.isEmpty) {
      loaded = MealReminderSetting.defaultSettings();
    } else {
      // Ensure all 5 slots exist in case of partial list
      for (final def in MealReminderSetting.defaultSettings()) {
        if (!loaded.any((s) => s.slot == def.slot)) {
          loaded.add(def);
        }
      }
    }

    state = MealRemindersState(
      masterEnabled: master,
      settings: loaded,
      isLoaded: true,
    );

    await _syncAlarms();
  }

  Future<void> _syncAlarms() async {
    if (!state.masterEnabled) {
      await _service.cancelAllReminders();
      return;
    }

    for (final setting in state.settings) {
      if (setting.isEnabled) {
        await _service.scheduleMealReminder(setting);
      } else {
        await _service.cancelReminder(setting.notificationId);
      }
    }
  }

  Future<void> toggleMaster(bool enabled) async {
    state = state.copyWith(masterEnabled: enabled);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyMaster, enabled);
    await _syncAlarms();
  }

  Future<void> updateSlotTime(MealSlot slot, int hour, int minute) async {
    final updatedList = state.settings.map((s) {
      if (s.slot == slot) {
        return s.copyWith(hour: hour, minute: minute);
      }
      return s;
    }).toList();

    state = state.copyWith(settings: updatedList);
    await _saveSettings();
    await _syncAlarms();
  }

  Future<void> toggleSlot(MealSlot slot, bool enabled) async {
    final updatedList = state.settings.map((s) {
      if (s.slot == slot) {
        return s.copyWith(isEnabled: enabled);
      }
      return s;
    }).toList();

    state = state.copyWith(settings: updatedList);
    await _saveSettings();
    await _syncAlarms();
  }

  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    final jsonStr = jsonEncode(state.settings.map((s) => s.toJson()).toList());
    await prefs.setString(_keySettings, jsonStr);
  }

  Future<void> sendTestReminder() async {
    await _service.showTestNotification();
  }
}

final mealReminderServiceProvider = Provider<MealReminderService>((ref) {
  return MealReminderService();
});

final mealRemindersProvider =
    StateNotifierProvider<MealRemindersNotifier, MealRemindersState>((ref) {
  final service = ref.watch(mealReminderServiceProvider);
  return MealRemindersNotifier(service);
});
