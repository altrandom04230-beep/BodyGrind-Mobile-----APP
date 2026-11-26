import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as tz;
import 'package:timezone/timezone.dart' as tz;
import '../models/meal_reminder_models.dart';

class MealReminderService {
  static final MealReminderService _instance = MealReminderService._internal();
  factory MealReminderService() => _instance;
  MealReminderService._internal();

  final FlutterLocalNotificationsPlugin _notificationsPlugin =
      FlutterLocalNotificationsPlugin();
  bool _isInitialized = false;

  static const String _channelId = 'meal_reminders_channel';
  static const String _channelName = 'Meal Reminders';
  static const String _channelDesc =
      'Scheduled reminders to log your meals and stay on track with your calorie target.';

  Future<void> initialize() async {
    if (_isInitialized) return;

    try {
      tz.initializeTimeZones();
      final now = DateTime.now();
      // Set a resilient local location
      try {
        tz.setLocalLocation(tz.local);
      } catch (_) {
        tz.setLocalLocation(tz.getLocation('UTC'));
      }
    } catch (_) {}

    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const initSettings = InitializationSettings(android: androidSettings);

    await _notificationsPlugin.initialize(
      initSettings,
      onDidReceiveNotificationResponse: (response) {
        // App is opened from notification
      },
    );

    // Request Android 13+ POST_NOTIFICATIONS permission
    try {
      final androidImpl = _notificationsPlugin
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>();
      await androidImpl?.requestNotificationsPermission();
    } catch (_) {}

    _isInitialized = true;
  }

  /// Calculates the next occurrence of a given hour & minute in local time
  tz.TZDateTime _nextInstanceOfTime(int hour, int minute) {
    final now = tz.TZDateTime.now(tz.local);
    var scheduledDate = tz.TZDateTime(
      tz.local,
      now.year,
      now.month,
      now.day,
      hour,
      minute,
    );
    if (scheduledDate.isBefore(now)) {
      scheduledDate = scheduledDate.add(const Duration(days: 1));
    }
    return scheduledDate;
  }

  /// Schedules daily recurring local notification for a meal slot
  Future<void> scheduleMealReminder(MealReminderSetting setting) async {
    await initialize();

    final notificationId = setting.notificationId;

    if (!setting.isEnabled) {
      await cancelReminder(notificationId);
      return;
    }

    const androidDetails = AndroidNotificationDetails(
      _channelId,
      _channelName,
      channelDescription: _channelDesc,
      importance: Importance.high,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
    );

    const details = NotificationDetails(android: androidDetails);

    final title = 'Time for ${setting.slot.displayName}!';
    final body =
        'Log your ${setting.slot.displayName.toLowerCase()} in BodyGrind to hit your target calories.';

    try {
      await _notificationsPlugin.zonedSchedule(
        notificationId,
        title,
        body,
        _nextInstanceOfTime(setting.hour, setting.minute),
        details,
        androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
        uiLocalNotificationDateInterpretation:
            UILocalNotificationDateInterpretation.absoluteTime,
        matchDateTimeComponents: DateTimeComponents.time, // repeats daily!
      );
    } catch (e) {
      // Fallback if exact alarms permission is restricted on specific Android OEM
      try {
        await _notificationsPlugin.zonedSchedule(
          notificationId,
          title,
          body,
          _nextInstanceOfTime(setting.hour, setting.minute),
          details,
          androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
          uiLocalNotificationDateInterpretation:
              UILocalNotificationDateInterpretation.absoluteTime,
          matchDateTimeComponents: DateTimeComponents.time,
        );
      } catch (_) {}
    }
  }

  /// Cancels an existing notification schedule by ID
  Future<void> cancelReminder(int id) async {
    await initialize();
    try {
      await _notificationsPlugin.cancel(id);
    } catch (_) {}
  }

  /// Cancels all scheduled reminders
  Future<void> cancelAllReminders() async {
    await initialize();
    try {
      await _notificationsPlugin.cancelAll();
    } catch (_) {}
  }

  /// Sends an immediate test notification to verify delivery
  Future<void> showTestNotification({
    String title = 'Time for Lunch!',
    String body = 'Test Reminder: Log your food in BodyGrind to hit your calorie goal.',
  }) async {
    await initialize();
    const androidDetails = AndroidNotificationDetails(
      _channelId,
      _channelName,
      channelDescription: _channelDesc,
      importance: Importance.high,
      priority: Priority.high,
      icon: '@mipmap/ic_launcher',
    );
    const details = NotificationDetails(android: androidDetails);
    await _notificationsPlugin.show(999, title, body, details);
  }
}
