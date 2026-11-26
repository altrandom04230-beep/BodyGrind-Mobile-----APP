import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../models/app_role.dart';
import '../models/exercise.dart';
import '../models/workout_plan.dart';
import '../models/workout_session.dart';
import '../models/user_profile.dart';
import '../models/badge.dart';
import '../models/workout_schedule.dart';
import '../models/bmi_record.dart';
import '../models/weekly_record.dart';
import '../models/meal_models.dart';
import '../models/trainer_models.dart';
import '../repositories/workout_repository.dart';
import '../repositories/weekly_progress_repository.dart';
import '../repositories/smart_meals_repository.dart';
import '../repositories/trainer_repository.dart';
import '../repositories/auth_repository.dart';
import '../repositories/attendance_firestore_repository.dart';

// --- REPOSITORY SINGLETON PROVIDERS ---

final workoutRepositoryProvider = Provider<WorkoutRepository>((ref) {
  final repo = WorkoutRepository();
  ref.onDispose(repo.dispose);
  return repo;
});

final weeklyProgressRepositoryProvider = Provider<WeeklyProgressRepository>((ref) {
  final repo = WeeklyProgressRepository();
  ref.onDispose(repo.dispose);
  return repo;
});

final smartMealsRepositoryProvider = Provider<SmartMealsRepository>((ref) {
  final repo = SmartMealsRepository();
  ref.onDispose(repo.dispose);
  return repo;
});

final trainerRepositoryProvider = Provider<TrainerRepository>((ref) {
  final repo = TrainerRepository();
  ref.onDispose(repo.dispose);
  return repo;
});

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  return AuthRepository();
});

final attendanceRepositoryProvider = Provider<AttendanceFirestoreRepository>((ref) {
  return AttendanceFirestoreRepository();
});

// --- AUTH & ROLE STATE PROVIDERS ---

/// Currently selected portal role (null = MockLoginScreen, member = Member Flow, trainer = Trainer Portal)
final selectedRoleProvider = StateProvider<AppRole?>((ref) => null);

/// Firebase Auth State Stream
final authStateProvider = StreamProvider<User?>((ref) {
  return ref.watch(authRepositoryProvider).authStateChanges;
});

/// Application Theme Mode: "DARK", "LIGHT", "SYSTEM"
final themeModeProvider = StateProvider<String>((ref) => 'LIGHT');

// --- REACTIVE STREAM PROVIDERS (MEMBER DOMAIN) ---

final exercisesStreamProvider = StreamProvider<List<Exercise>>((ref) {
  return ref.watch(workoutRepositoryProvider).getAllExercises();
});

final plansStreamProvider = StreamProvider<List<WorkoutPlan>>((ref) {
  return ref.watch(workoutRepositoryProvider).getAllPlans();
});

final completedSessionsStreamProvider = StreamProvider<List<WorkoutSession>>((ref) {
  return ref.watch(workoutRepositoryProvider).getCompletedSessions();
});

final userProfileStreamProvider = StreamProvider<UserProfile>((ref) {
  return ref.watch(workoutRepositoryProvider).getUserProfile();
});

final badgesStreamProvider = StreamProvider<List<Badge>>((ref) {
  return ref.watch(workoutRepositoryProvider).getAllBadges();
});

final schedulesStreamProvider = StreamProvider<List<WorkoutSchedule>>((ref) {
  return ref.watch(workoutRepositoryProvider).getAllSchedules();
});

final bmiRecordsStreamProvider = StreamProvider<List<BmiRecord>>((ref) {
  return ref.watch(workoutRepositoryProvider).getAllBmiRecords();
});

final streakDaysProvider = Provider<int>((ref) {
  ref.watch(completedSessionsStreamProvider);
  return ref.watch(workoutRepositoryProvider).calculateCurrentStreak();
});

final recommendedPlanProvider = Provider<WorkoutPlan?>((ref) {
  final plansAsync = ref.watch(plansStreamProvider);
  return plansAsync.when(
    data: (plans) => plans.isNotEmpty ? plans.first : null,
    loading: () => null,
    error: (_, __) => null,
  );
});

// --- REACTIVE STREAM PROVIDERS (WEEKLY PROGRESS DOMAIN) ---

final weeklyRecordsStreamProvider = StreamProvider<List<WeeklyRecord>>((ref) {
  return ref.watch(weeklyProgressRepositoryProvider).records;
});

// --- REACTIVE STREAM PROVIDERS (SMART MEALS DOMAIN) ---

final weeklyMealPlansStreamProvider = StreamProvider<Map<DayOfWeekPlan, DayMealPlan>>((ref) {
  return ref.watch(smartMealsRepositoryProvider).weeklyPlans;
});

final loggedMealsStreamProvider = StreamProvider<List<LoggedMeal>>((ref) {
  return ref.watch(smartMealsRepositoryProvider).loggedMeals;
});

final allLoggedMealsStreamProvider = StreamProvider<List<LoggedMeal>>((ref) {
  return ref.watch(smartMealsRepositoryProvider).allLoggedMeals;
});

final mealGoalSettingsStreamProvider = StreamProvider<MealGoalSettings>((ref) {
  return ref.watch(smartMealsRepositoryProvider).goalSettings;
});

final waterIntakeMlStreamProvider = StreamProvider<int>((ref) {
  return ref.watch(smartMealsRepositoryProvider).waterIntakeMl;
});

// --- REACTIVE STREAM PROVIDERS (TRAINER DOMAIN) ---

final trainerInfoStreamProvider = StreamProvider<TrainerInfo>((ref) {
  return ref.watch(trainerRepositoryProvider).trainerInfo;
});

final assignedMembersStreamProvider = StreamProvider<List<AssignedMember>>((ref) {
  return ref.watch(trainerRepositoryProvider).assignedMembers;
});

final pendingTasksStreamProvider = StreamProvider<List<TrainerPendingTask>>((ref) {
  return ref.watch(trainerRepositoryProvider).pendingTasks;
});

final trainerNotificationsStreamProvider = StreamProvider<List<TrainerNotification>>((ref) {
  return ref.watch(trainerRepositoryProvider).notifications;
});

final feedbackListStreamProvider = StreamProvider<List<TrainerFeedbackEntry>>((ref) {
  return ref.watch(trainerRepositoryProvider).feedbackList;
});
