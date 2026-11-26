import 'dart:io';
import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;

// Drift Table Definitions matching Room Entities

class ExercisesTable extends Table {
  @override
  String get tableName => 'exercises';

  TextColumn get id => text()();
  TextColumn get name => text()();
  TextColumn get category => text()();
  TextColumn get targetMuscles => text()();
  TextColumn get difficulty => text()();
  TextColumn get defaultType => text()();
  IntColumn get defaultCount => integer()();
  TextColumn get instructions => text()();
  TextColumn get tips => text()();
  RealColumn get caloriesPerMinute => real().withDefault(const Constant(7.5))();
  TextColumn get animationType => text()();
  BoolColumn get isFavorite => boolean().withDefault(const Constant(false))();
  TextColumn get primaryMuscles => text().withDefault(const Constant(''))();
  TextColumn get secondaryMuscles => text().withDefault(const Constant(''))();
  TextColumn get educationalDescription => text().withDefault(const Constant(''))();
  TextColumn get functionalBenefit => text().withDefault(const Constant(''))();

  @override
  Set<Column> get primaryKey => {id};
}

class WorkoutPlansTable extends Table {
  @override
  String get tableName => 'workout_plans';

  TextColumn get id => text()();
  TextColumn get name => text()();
  TextColumn get description => text()();
  TextColumn get category => text()();
  TextColumn get difficulty => text()();
  IntColumn get estimatedDurationMinutes => integer()();
  BoolColumn get isCustom => boolean().withDefault(const Constant(false))();
  BoolColumn get isChallenge => boolean().withDefault(const Constant(false))();
  IntColumn get totalDays => integer().withDefault(const Constant(1))();
  IntColumn get currentDay => integer().withDefault(const Constant(1))();
  IntColumn get createdAt => integer()();
  TextColumn get targetMusclesDescription => text().withDefault(const Constant(''))();
  TextColumn get educationalOverview => text().withDefault(const Constant(''))();

  @override
  Set<Column> get primaryKey => {id};
}

class WorkoutPlanExercisesTable extends Table {
  @override
  String get tableName => 'workout_plan_exercises';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get planId => text()();
  TextColumn get exerciseId => text()();
  IntColumn get orderIndex => integer()();
  TextColumn get type => text()();
  IntColumn get count => integer()();
  IntColumn get restSeconds => integer().withDefault(const Constant(20))();
}

class WorkoutSessionsTable extends Table {
  @override
  String get tableName => 'workout_sessions';

  IntColumn get sessionId => integer().autoIncrement()();
  TextColumn get planId => text()();
  TextColumn get planName => text()();
  TextColumn get category => text()();
  IntColumn get startedAt => integer()();
  IntColumn get completedAt => integer()();
  IntColumn get durationSeconds => integer()();
  IntColumn get caloriesBurned => integer()();
  IntColumn get exercisesCompleted => integer()();
  IntColumn get totalExercises => integer()();
  BoolColumn get isFinished => boolean()();
}

class BodyMeasurementsTable extends Table {
  @override
  String get tableName => 'body_measurements';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get timestamp => integer()();
  RealColumn get weightKg => real()();
  RealColumn get heightCm => real()();
  RealColumn get chestCm => real().nullable()();
  RealColumn get armCm => real().nullable()();
  RealColumn get waistCm => real().nullable()();
  RealColumn get bmi => real()();
  TextColumn get bmiCategory => text()();
  TextColumn get notes => text().withDefault(const Constant(''))();
}

class UserProfilesTable extends Table {
  @override
  String get tableName => 'user_profile';

  IntColumn get id => integer().withDefault(const Constant(1))();
  TextColumn get name => text().withDefault(const Constant('Fitness Athlete'))();
  IntColumn get age => integer().withDefault(const Constant(26))();
  TextColumn get gender => text().withDefault(const Constant('Prefer not to say'))();
  TextColumn get fitnessGoal => text().withDefault(const Constant('Build Muscle'))();
  TextColumn get fitnessLevel => text().withDefault(const Constant('Beginner'))();
  IntColumn get preferredDuration => integer().withDefault(const Constant(15))();
  RealColumn get weightKg => real().withDefault(const Constant(70.0))();
  RealColumn get heightCm => real().withDefault(const Constant(175.0))();
  BoolColumn get useMetric => boolean().withDefault(const Constant(true))();
  BoolColumn get soundEnabled => boolean().withDefault(const Constant(true))();
  BoolColumn get voiceCuesEnabled => boolean().withDefault(const Constant(true))();
  BoolColumn get vibrationEnabled => boolean().withDefault(const Constant(true))();
  BoolColumn get reminderEnabled => boolean().withDefault(const Constant(true))();
  IntColumn get reminderHour => integer().withDefault(const Constant(8))();
  IntColumn get reminderMinute => integer().withDefault(const Constant(0))();
  IntColumn get reminderDaysMask => integer().withDefault(const Constant(127))();
  BoolColumn get onboardingCompleted => boolean().withDefault(const Constant(false))();
  TextColumn get darkThemeMode => text().withDefault(const Constant('LIGHT'))();
  IntColumn get xpPoints => integer().withDefault(const Constant(0))();
  IntColumn get level => integer().withDefault(const Constant(1))();
  BoolColumn get isPrivate => boolean().withDefault(const Constant(false))();
  TextColumn get accentColorHex => text().withDefault(const Constant('#E50914'))();
  TextColumn get bodyDiagramGender => text().withDefault(const Constant('MALE'))();
  TextColumn get exerciseDrawingGender => text().withDefault(const Constant('MALE'))();
  BoolColumn get gymCheckinEnabled => boolean().withDefault(const Constant(true))();
  BoolColumn get aiCoachEnabled => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}

class BadgesTable extends Table {
  @override
  String get tableName => 'badges';

  TextColumn get badgeId => text()();
  TextColumn get title => text()();
  TextColumn get description => text()();
  TextColumn get category => text()();
  BoolColumn get unlocked => boolean().withDefault(const Constant(false))();
  IntColumn get unlockedAt => integer().nullable()();
  IntColumn get progressCurrent => integer().withDefault(const Constant(0))();
  IntColumn get progressTarget => integer().withDefault(const Constant(1))();
  TextColumn get iconName => text()();

  @override
  Set<Column> get primaryKey => {badgeId};
}

class WorkoutSchedulesTable extends Table {
  @override
  String get tableName => 'workout_schedules';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get dayOfWeek => integer()();
  TextColumn get planId => text()();
  TextColumn get planName => text()();
  IntColumn get timeHour => integer().withDefault(const Constant(8))();
  IntColumn get timeMinute => integer().withDefault(const Constant(0))();
  BoolColumn get isEnabled => boolean().withDefault(const Constant(true))();
}

class CalendarEventsTable extends Table {
  @override
  String get tableName => 'calendar_events';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get planId => text().nullable()();
  TextColumn get title => text()();
  TextColumn get startDate => text()();
  IntColumn get timeHour => integer()();
  IntColumn get timeMinute => integer()();
  TextColumn get repeatType => text()();
  IntColumn get repeatDaysMask => integer().withDefault(const Constant(0))();
  TextColumn get repeatUntil => text().nullable()();
  IntColumn get reminderMinutesBefore => integer().withDefault(const Constant(-1))();
  BoolColumn get isEnabled => boolean().withDefault(const Constant(true))();
  IntColumn get createdAt => integer()();
  TextColumn get note => text().nullable()();
}

class CalendarEventExceptionsTable extends Table {
  @override
  String get tableName => 'calendar_event_exceptions';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get eventId => integer()();
  TextColumn get occurrenceDate => text()();
  TextColumn get type => text().withDefault(const Constant('SKIPPED'))();
}

class BmiRecordsTable extends Table {
  @override
  String get tableName => 'bmi_records';

  TextColumn get bmiRecordId => text()();
  IntColumn get userId => integer().withDefault(const Constant(1))();
  RealColumn get heightCm => real()();
  RealColumn get weightKg => real()();
  RealColumn get bmiValue => real()();
  TextColumn get bmiCategory => text()();
  IntColumn get assessmentDate => integer()();
  TextColumn get sourceType => text().withDefault(const Constant('MANUAL'))();
  TextColumn get linkedScanId => text().nullable()();

  @override
  Set<Column> get primaryKey => {bmiRecordId};
}

class BodyScansTable extends Table {
  @override
  String get tableName => 'body_scans';

  TextColumn get scanId => text()();
  IntColumn get userId => integer().withDefault(const Constant(1))();
  IntColumn get scanDate => integer()();
  TextColumn get cameraFacing => text().withDefault(const Constant('FRONT'))();
  TextColumn get poseDetectionStatus => text().withDefault(const Constant('COMPLETE'))();
  RealColumn get landmarkConfidence => real().withDefault(const Constant(0.85))();
  TextColumn get scanNotes => text().withDefault(const Constant(''))();
  TextColumn get bodyVisibilityStatus => text().withDefault(const Constant('FULL_BODY'))();

  @override
  Set<Column> get primaryKey => {scanId};
}

class BodyPoseLandmarksTable extends Table {
  @override
  String get tableName => 'body_pose_landmarks';

  IntColumn get landmarkId => integer().autoIncrement()();
  TextColumn get scanId => text()();
  TextColumn get landmarkName => text()();
  RealColumn get normalizedX => real()();
  RealColumn get normalizedY => real()();
  RealColumn get normalizedZ => real().nullable()();
  RealColumn get visibilityConfidence => real().nullable()();
}

/// Drift Database executor & query interface
LazyDatabase openConnection() {
  return LazyDatabase(() async {
    final dbFolder = await getApplicationDocumentsDirectory();
    final file = File(p.join(dbFolder.path, 'bodygrind.sqlite'));
    return NativeDatabase.createInBackground(file);
  });
}
