import 'dart:async';
import '../../core/database/seed_data.dart';
import '../models/exercise.dart';
import '../models/workout_plan.dart';
import '../models/workout_session.dart';
import '../models/body_measurement.dart';
import '../models/user_profile.dart';
import '../models/badge.dart';
import '../models/workout_schedule.dart';
import '../models/calendar_event.dart';
import '../models/bmi_record.dart';

/// Local workout repository replacing Room WorkoutRepository.kt
class WorkoutRepository {
  // In-memory reactive state streams
  final _exercisesController = StreamController<List<Exercise>>.broadcast();
  final _plansController = StreamController<List<WorkoutPlan>>.broadcast();
  final _sessionsController = StreamController<List<WorkoutSession>>.broadcast();
  final _measurementsController = StreamController<List<BodyMeasurement>>.broadcast();
  final _profileController = StreamController<UserProfile>.broadcast();
  final _badgesController = StreamController<List<Badge>>.broadcast();
  final _schedulesController = StreamController<List<WorkoutSchedule>>.broadcast();
  final _calendarEventsController = StreamController<List<CalendarEvent>>.broadcast();
  final _calendarExceptionsController = StreamController<List<CalendarEventException>>.broadcast();
  final _bmiRecordsController = StreamController<List<BmiRecord>>.broadcast();

  List<Exercise> _exercises = [];
  List<WorkoutPlan> _plans = [];
  final Map<String, List<WorkoutPlanExercise>> _planExercises = {};
  List<WorkoutSession> _sessions = [];
  List<BodyMeasurement> _measurements = [];
  UserProfile _profile = const UserProfile();
  List<Badge> _badges = [];
  List<WorkoutSchedule> _schedules = [];
  List<CalendarEvent> _calendarEvents = [];
  List<CalendarEventException> _calendarExceptions = [];
  List<BmiRecord> _bmiRecords = [];

  WorkoutRepository() {
    _initializeData();
  }

  void _initializeData() {
    _exercises = List.from(SeedData.exercises);
    _plans = List.from(SeedData.plans);
    for (final pe in SeedData.planExercises) {
      _planExercises.putIfAbsent(pe.planId, () => []).add(pe);
    }
    _measurements = List.from(SeedData.initialMeasurements);
    _badges = List.from(SeedData.badges);
    _schedules = List.from(SeedData.defaultSchedules);

    // Initial seed for BMI records
    final now = DateTime.now().millisecondsSinceEpoch;
    _bmiRecords = [
      BmiRecord(
        bmiRecordId: 'init_bmi_1',
        heightCm: 175.0,
        weightKg: 70.0,
        bmiValue: 22.9,
        bmiCategory: 'Normal weight',
        assessmentDate: now - 86400000 * 2,
        sourceType: 'MANUAL',
      ),
    ];

    _emitAll();
  }

  void _emitAll() {
    _exercisesController.add(List.unmodifiable(_exercises));
    _plansController.add(List.unmodifiable(_plans));
    _sessionsController.add(List.unmodifiable(_sessions));
    _measurementsController.add(List.unmodifiable(_measurements));
    _profileController.add(_profile);
    _badgesController.add(List.unmodifiable(_badges));
    _schedulesController.add(List.unmodifiable(_schedules));
    _calendarEventsController.add(List.unmodifiable(_calendarEvents));
    _calendarExceptionsController.add(List.unmodifiable(_calendarExceptions));
    _bmiRecordsController.add(List.unmodifiable(_bmiRecords));
  }

  // --- EXERCISES ---
  Stream<List<Exercise>> getAllExercises() => _exercisesController.stream;
  List<Exercise> get currentExercises => List.unmodifiable(_exercises);

  Stream<List<Exercise>> getExercisesByCategory(String category) {
    return _exercisesController.stream.map(
      (list) => list.where((e) => e.category.toLowerCase() == category.toLowerCase()).toList(),
    );
  }

  Stream<List<Exercise>> getFavoriteExercises() {
    return _exercisesController.stream.map(
      (list) => list.where((e) => e.isFavorite).toList(),
    );
  }

  Exercise? getExerciseById(String id) {
    try {
      return _exercises.firstWhere((e) => e.id == id);
    } catch (_) {
      return null;
    }
  }

  Future<void> toggleFavorite(String id, bool currentVal) async {
    _exercises = _exercises.map((e) => e.id == id ? e.copyWith(isFavorite: !currentVal) : e).toList();
    _exercisesController.add(List.unmodifiable(_exercises));
  }

  // --- PLANS ---
  Stream<List<WorkoutPlan>> getAllPlans() => _plansController.stream;
  List<WorkoutPlan> get currentPlans => List.unmodifiable(_plans);

  Stream<List<WorkoutPlan>> getPredefinedPlans() {
    return _plansController.stream.map((list) => list.where((p) => !p.isCustom).toList());
  }

  Stream<List<WorkoutPlan>> getCustomPlans() {
    return _plansController.stream.map((list) => list.where((p) => p.isCustom).toList());
  }

  Stream<List<WorkoutPlan>> getPlansByCategory(String category) {
    return _plansController.stream.map(
      (list) => list.where((p) => p.category.toLowerCase() == category.toLowerCase()).toList(),
    );
  }

  Stream<List<WorkoutPlan>> getChallenges() {
    return _plansController.stream.map((list) => list.where((p) => p.isChallenge).toList());
  }

  WorkoutPlan? getPlanById(String id) {
    try {
      return _plans.firstWhere((p) => p.id == id);
    } catch (_) {
      return null;
    }
  }

  Stream<List<WorkoutPlanExercise>> getPlanExercises(String planId) {
    return _plansController.stream.map((_) => List.unmodifiable(_planExercises[planId] ?? []));
  }

  List<WorkoutPlanExercise> getPlanExercisesSync(String planId) {
    return List.unmodifiable(_planExercises[planId] ?? []);
  }

  Future<String> createCustomPlan({
    required String name,
    required String description,
    required String category,
    required String difficulty,
    required List<WorkoutPlanExercise> exercises,
  }) async {
    final planId = 'custom_${DateTime.now().millisecondsSinceEpoch}';
    final totalSecs = exercises.fold<int>(0, (sum, it) {
      final dur = it.type == 'TIME' ? it.count : it.count * 3;
      return sum + dur + it.restSeconds;
    });
    final estimatedMins = (totalSecs / 60).round().clamp(3, 99);

    final plan = WorkoutPlan(
      id: planId,
      name: name,
      description: description,
      category: category,
      difficulty: difficulty,
      estimatedDurationMinutes: estimatedMins,
      isCustom: true,
      createdAt: DateTime.now().millisecondsSinceEpoch,
    );
    _plans.insert(0, plan);

    final planExs = exercises.asMap().entries.map((entry) {
      return entry.value.copyWith(
        id: entry.key + 1,
        planId: planId,
        orderIndex: entry.key,
      );
    }).toList();
    _planExercises[planId] = planExs;

    _plansController.add(List.unmodifiable(_plans));
    return planId;
  }

  Future<void> deleteCustomPlan(String planId) async {
    _plans.removeWhere((p) => p.id == planId);
    _planExercises.remove(planId);
    _plansController.add(List.unmodifiable(_plans));
  }

  // --- SESSIONS & HISTORY ---
  Stream<List<WorkoutSession>> getCompletedSessions() => _sessionsController.stream;
  List<WorkoutSession> get currentSessions => List.unmodifiable(_sessions);

  Stream<int> getTotalWorkoutsCount() {
    return _sessionsController.stream.map((list) => list.length);
  }

  Stream<int> getTotalCaloriesBurned() {
    return _sessionsController.stream.map((list) => list.fold<int>(0, (sum, s) => sum + s.caloriesBurned));
  }

  Stream<int> getTotalDurationSeconds() {
    return _sessionsController.stream.map((list) => list.fold<int>(0, (sum, s) => sum + s.durationSeconds));
  }

  Future<int> completeWorkoutSession({
    required String planId,
    required String planName,
    required String category,
    required int startedAt,
    required int completedAt,
    required int durationSeconds,
    required int caloriesBurned,
    required int exercisesCompleted,
    required int totalExercises,
  }) async {
    final sessionId = DateTime.now().millisecondsSinceEpoch;
    final session = WorkoutSession(
      sessionId: sessionId,
      planId: planId,
      planName: planName,
      category: category,
      startedAt: startedAt,
      completedAt: completedAt,
      durationSeconds: durationSeconds,
      caloriesBurned: caloriesBurned,
      exercisesCompleted: exercisesCompleted,
      totalExercises: totalExercises,
      isFinished: true,
    );
    _sessions.insert(0, session);
    _sessionsController.add(List.unmodifiable(_sessions));

    // Award XP: 100 XP base + 2 XP per minute
    final earnedXp = 100 + (durationSeconds ~/ 60) * 2;
    final newXp = _profile.xpPoints + earnedXp;
    final newLevel = newXp >= 5000
        ? 7
        : newXp >= 3200
            ? 6
            : newXp >= 2000
                ? 5
                : newXp >= 1200
                    ? 4
                    : newXp >= 600
                        ? 3
                        : newXp >= 250
                            ? 2
                            : 1;
    _profile = _profile.copyWith(xpPoints: newXp, level: newLevel);
    _profileController.add(_profile);

    _checkAndUpdateBadges(planId, category, completedAt);
    return sessionId;
  }

  void _checkAndUpdateBadges(String planId, String category, int completedAt) {
    final totalCount = _sessions.length;
    if (totalCount >= 1) _unlockBadge('first_workout', 1);
    if (totalCount >= 5) _unlockBadge('workout_5', 5);
    if (totalCount >= 20) _unlockBadge('workout_20', 20);

    final streak = calculateCurrentStreak();
    if (streak >= 3) _unlockBadge('streak_3', 3);
    if (streak >= 7) _unlockBadge('streak_7', 7);

    if (planId.startsWith('custom_')) {
      _unlockBadge('custom_creator', 1);
    }
    if (category.toLowerCase() == 'abs') {
      final badge = _badges.firstWhere((b) => b.badgeId == 'abs_master', orElse: () => const Badge(badgeId: '', title: '', description: '', category: '', iconName: ''));
      if (badge.badgeId.isNotEmpty) {
        final nextProg = badge.progressCurrent + 1;
        if (nextProg >= 3) {
          _unlockBadge('abs_master', 3);
        } else {
          _updateBadgeProgress('abs_master', nextProg);
        }
      }
    }
  }

  void _unlockBadge(String badgeId, int targetVal) {
    _badges = _badges.map((b) {
      if (b.badgeId == badgeId && !b.unlocked) {
        return b.copyWith(
          unlocked: true,
          unlockedAt: DateTime.now().millisecondsSinceEpoch,
          progressCurrent: targetVal,
        );
      }
      return b;
    }).toList();
    _badgesController.add(List.unmodifiable(_badges));
  }

  void _updateBadgeProgress(String badgeId, int progress) {
    _badges = _badges.map((b) {
      if (b.badgeId == badgeId && !b.unlocked) {
        return b.copyWith(progressCurrent: progress);
      }
      return b;
    }).toList();
    _badgesController.add(List.unmodifiable(_badges));
  }

  int calculateCurrentStreak() {
    if (_sessions.isEmpty) return 0;
    const dayInMillis = 86400000;
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day).millisecondsSinceEpoch;
    final yesterdayStart = todayStart - dayInMillis;

    final sessionDays = _sessions
        .map((s) {
          final dt = DateTime.fromMillisecondsSinceEpoch(s.completedAt);
          return DateTime(dt.year, dt.month, dt.day).millisecondsSinceEpoch;
        })
        .toSet()
        .toList()
      ..sort((a, b) => b.compareTo(a));

    if (sessionDays.isEmpty) return 0;
    final mostRecent = sessionDays.first;
    if (mostRecent < yesterdayStart) return 0;

    var currentCheck = mostRecent;
    var streak = 1;

    for (int i = 1; i < sessionDays.length; i++) {
      final prevDay = sessionDays[i];
      if (currentCheck - prevDay == dayInMillis) {
        streak++;
        currentCheck = prevDay;
      } else {
        break;
      }
    }
    return streak;
  }

  // --- BODY MEASUREMENTS ---
  Stream<List<BodyMeasurement>> getAllMeasurements() => _measurementsController.stream;
  List<BodyMeasurement> get currentMeasurements => List.unmodifiable(_measurements);

  Future<int> logMeasurement({
    required double weightKg,
    required double heightCm,
    double? waistCm,
    double? chestCm,
    double? armCm,
    String notes = '',
    int? timestamp,
  }) async {
    final ts = timestamp ?? DateTime.now().millisecondsSinceEpoch;
    final heightM = heightCm / 100.0;
    final bmi = heightM > 0 ? weightKg / (heightM * heightM) : 22.0;
    final category = bmi < 18.5
        ? 'Underweight'
        : bmi < 24.9
            ? 'Normal weight'
            : bmi < 29.9
                ? 'Overweight'
                : 'Obese';

    final id = DateTime.now().millisecondsSinceEpoch;
    final measurement = BodyMeasurement(
      id: id,
      timestamp: ts,
      weightKg: weightKg,
      heightCm: heightCm,
      chestCm: chestCm,
      armCm: armCm,
      waistCm: waistCm,
      bmi: double.parse(bmi.toStringAsFixed(1)),
      bmiCategory: category,
      notes: notes,
    );

    _measurements.insert(0, measurement);
    _measurementsController.add(List.unmodifiable(_measurements));

    _profile = _profile.copyWith(weightKg: weightKg, heightCm: heightCm);
    _profileController.add(_profile);

    return id;
  }

  Future<void> deleteMeasurementById(int id) async {
    _measurements.removeWhere((m) => m.id == id);
    _measurementsController.add(List.unmodifiable(_measurements));
  }

  // --- PROFILE & SETTINGS ---
  Stream<UserProfile> getUserProfile() => _profileController.stream;
  UserProfile get currentProfile => _profile;

  Future<void> updateProfile(UserProfile profile) async {
    _profile = profile;
    _profileController.add(_profile);
  }

  // --- BADGES ---
  Stream<List<Badge>> getAllBadges() => _badgesController.stream;
  List<Badge> get currentBadges => List.unmodifiable(_badges);

  // --- SCHEDULES ---
  Stream<List<WorkoutSchedule>> getAllSchedules() => _schedulesController.stream;

  Future<void> updateSchedule(WorkoutSchedule schedule) async {
    _schedules = _schedules.map((s) => s.id == schedule.id ? schedule : s).toList();
    _schedulesController.add(List.unmodifiable(_schedules));
  }

  Future<void> addSchedule(WorkoutSchedule schedule) async {
    _schedules.add(schedule.copyWith(id: DateTime.now().millisecondsSinceEpoch));
    _schedulesController.add(List.unmodifiable(_schedules));
  }

  Future<void> deleteSchedule(int id) async {
    _schedules.removeWhere((s) => s.id == id);
    _schedulesController.add(List.unmodifiable(_schedules));
  }

  // --- BMI RECORDS ---
  Stream<List<BmiRecord>> getAllBmiRecords() => _bmiRecordsController.stream;
  List<BmiRecord> get currentBmiRecords => List.unmodifiable(_bmiRecords);

  Future<void> insertBmiRecord(BmiRecord record) async {
    _bmiRecords.insert(0, record);
    _bmiRecordsController.add(List.unmodifiable(_bmiRecords));

    _profile = _profile.copyWith(weightKg: record.weightKg, heightCm: record.heightCm);
    _profileController.add(_profile);
  }

  Future<void> deleteBmiRecord(String id) async {
    _bmiRecords.removeWhere((r) => r.bmiRecordId == id);
    _bmiRecordsController.add(List.unmodifiable(_bmiRecords));
  }

  // --- CALENDAR EVENTS ---
  Stream<List<CalendarEvent>> getAllCalendarEvents() => _calendarEventsController.stream;

  Future<int> insertCalendarEvent(CalendarEvent event) async {
    final id = DateTime.now().millisecondsSinceEpoch;
    _calendarEvents.add(event.copyWith(id: id));
    _calendarEventsController.add(List.unmodifiable(_calendarEvents));
    return id;
  }

  Future<void> updateCalendarEvent(CalendarEvent event) async {
    _calendarEvents = _calendarEvents.map((e) => e.id == event.id ? event : e).toList();
    _calendarEventsController.add(List.unmodifiable(_calendarEvents));
  }

  Future<void> deleteCalendarEvent(int id) async {
    _calendarEvents.removeWhere((e) => e.id == id);
    _calendarEventsController.add(List.unmodifiable(_calendarEvents));
  }

  Future<int> editCalendarEventThisDayOnly(int eventId, String occurrenceDate, CalendarEvent newEvent) async {
    _calendarExceptions.add(CalendarEventException(
      id: DateTime.now().millisecondsSinceEpoch,
      eventId: eventId,
      occurrenceDate: occurrenceDate,
      type: 'SKIPPED',
    ));
    _calendarExceptionsController.add(List.unmodifiable(_calendarExceptions));
    return insertCalendarEvent(newEvent);
  }

  Future<void> deleteCalendarEventThisDayOnly(int eventId, String occurrenceDate) async {
    _calendarExceptions.add(CalendarEventException(
      id: DateTime.now().millisecondsSinceEpoch,
      eventId: eventId,
      occurrenceDate: occurrenceDate,
      type: 'SKIPPED',
    ));
    _calendarExceptionsController.add(List.unmodifiable(_calendarExceptions));
  }

  void dispose() {
    _exercisesController.close();
    _plansController.close();
    _sessionsController.close();
    _measurementsController.close();
    _profileController.close();
    _badgesController.close();
    _schedulesController.close();
    _calendarEventsController.close();
    _calendarExceptionsController.close();
    _bmiRecordsController.close();
  }
}
