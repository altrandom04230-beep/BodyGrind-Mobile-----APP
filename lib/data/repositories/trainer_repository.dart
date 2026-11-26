import 'dart:async';
import 'package:intl/intl.dart';
import '../models/app_role.dart';
import '../models/trainer_models.dart';

/// Trainer repository managing coach data and member roster matching TrainerRepository.kt
class TrainerRepository {
  final _trainerInfoController = StreamController<TrainerInfo>.broadcast();
  final _assignedMembersController = StreamController<List<AssignedMember>>.broadcast();
  final _pendingTasksController = StreamController<List<TrainerPendingTask>>.broadcast();
  final _notificationsController = StreamController<List<TrainerNotification>>.broadcast();
  final _feedbackListController = StreamController<List<TrainerFeedbackEntry>>.broadcast();

  TrainerInfo _trainerInfo = const TrainerInfo(
    id: 'trainer_01',
    name: 'Coach Cobie',
    title: 'Head Strength & Conditioning Coach',
    email: 'cobie.trainer@bodygrind.local',
    specialization: 'Hypertrophy, Mobility & Body Recomposition',
  );

  List<AssignedMember> _assignedMembers = [];
  List<TrainerPendingTask> _pendingTasks = [];
  List<TrainerNotification> _notifications = [];
  List<TrainerFeedbackEntry> _feedbackList = [];

  Stream<TrainerInfo> get trainerInfo => _trainerInfoController.stream;
  Stream<List<AssignedMember>> get assignedMembers => _assignedMembersController.stream;
  Stream<List<TrainerPendingTask>> get pendingTasks => _pendingTasksController.stream;
  Stream<List<TrainerNotification>> get notifications => _notificationsController.stream;
  Stream<List<TrainerFeedbackEntry>> get feedbackList => _feedbackListController.stream;

  TrainerInfo get currentTrainerInfo => _trainerInfo;
  List<AssignedMember> get currentMembers => List.unmodifiable(_assignedMembers);
  List<TrainerPendingTask> get currentPendingTasks => List.unmodifiable(_pendingTasks);
  List<TrainerNotification> get currentNotifications => List.unmodifiable(_notifications);
  List<TrainerFeedbackEntry> get currentFeedbackList => List.unmodifiable(_feedbackList);

  TrainerRepository() {
    _initializeData();
  }

  void _initializeData() {
    _assignedMembers = [
      const AssignedMember(
        id: 'mem_01',
        name: 'Marcus Reyes',
        age: 27,
        gender: 'Male',
        email: 'marcus.reyes@bodygrind.local',
        joinDate: 'Aug 14, 2026',
        status: MemberStatus.active,
        fitnessGoal: 'Build Lean Muscle & Upper Body Strength',
        fitnessLevel: 'Intermediate',
        weeklyTargetWorkouts: 5,
        completedWorkoutsThisWeek: 4,
        workoutCompletionRate: 88,
        attendanceRate: 92,
        attendanceStreakDays: 9,
        consistencyScore: 90,
        weeklyAdherenceDays: [true, true, false, true, true, false, false],
        heightCm: 176.0,
        startingWeightKg: 68.5,
        currentWeightKg: 72.1,
        targetWeightKg: 75.0,
        weightBmiHistory: [
          WeightBmiPoint(weekLabel: 'W1', weightKg: 68.5, bmi: 22.1),
          WeightBmiPoint(weekLabel: 'W2', weightKg: 69.2, bmi: 22.3),
          WeightBmiPoint(weekLabel: 'W3', weightKg: 70.1, bmi: 22.6),
          WeightBmiPoint(weekLabel: 'W4', weightKg: 71.0, bmi: 22.9),
          WeightBmiPoint(weekLabel: 'W5', weightKg: 71.6, bmi: 23.1),
          WeightBmiPoint(weekLabel: 'W6', weightKg: 72.1, bmi: 23.3),
        ],
        workoutHistory: [
          MemberWorkoutRecord(id: 'w1', title: 'Upper Body Hypertrophy', dateLabel: 'Today, 7:15 AM', durationMinutes: 48, caloriesBurned: 360, completed: true),
          MemberWorkoutRecord(id: 'w2', title: 'Core & Conditioning', dateLabel: 'Yesterday, 6:40 PM', durationMinutes: 35, caloriesBurned: 290, completed: true),
          MemberWorkoutRecord(id: 'w3', title: 'Lower Body Power', dateLabel: '2 days ago', durationMinutes: 52, caloriesBurned: 410, completed: true),
          MemberWorkoutRecord(id: 'w4', title: 'Push Strength Split', dateLabel: '4 days ago', durationMinutes: 45, caloriesBurned: 340, completed: true),
        ],
        attendanceHistory: [
          MemberAttendanceRecord(id: 'a1', dateLabel: 'Today', timeLabel: '07:02 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a2', dateLabel: 'Yesterday', timeLabel: '06:30 PM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a3', dateLabel: '2 days ago', timeLabel: '07:10 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a4', dateLabel: '4 days ago', timeLabel: '06:55 AM', method: 'Front Desk', status: 'Present'),
        ],
        exercisePerformance: [
          ExercisePerformanceRecord(exerciseName: 'Push-Up', targetMetric: 'Reps / Set', previousBest: '18 reps', currentBest: '26 reps', trendLabel: '+8 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Bodyweight Squat', targetMetric: 'Reps / Set', previousBest: '25 reps', currentBest: '35 reps', trendLabel: '+10 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Plank Hold', targetMetric: 'Hold Duration', previousBest: '65 sec', currentBest: '95 sec', trendLabel: '+30 sec', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Chair Triceps Dips', targetMetric: 'Reps / Set', previousBest: '14 reps', currentBest: '20 reps', trendLabel: '+6 reps', isImproving: true),
        ],
      ),
      const AssignedMember(
        id: 'mem_02',
        name: 'Elena Santos',
        age: 31,
        gender: 'Female',
        email: 'elena.santos@bodygrind.local',
        joinDate: 'Jul 03, 2026',
        status: MemberStatus.active,
        fitnessGoal: 'Fat Loss & Cardiovascular Endurance',
        fitnessLevel: 'Intermediate',
        weeklyTargetWorkouts: 4,
        completedWorkoutsThisWeek: 4,
        workoutCompletionRate: 94,
        attendanceRate: 95,
        attendanceStreakDays: 14,
        consistencyScore: 95,
        weeklyAdherenceDays: [true, false, true, true, false, true, false],
        heightCm: 163.0,
        startingWeightKg: 71.0,
        currentWeightKg: 65.4,
        targetWeightKg: 62.0,
        weightBmiHistory: [
          WeightBmiPoint(weekLabel: 'W1', weightKg: 71.0, bmi: 26.7),
          WeightBmiPoint(weekLabel: 'W2', weightKg: 69.8, bmi: 26.3),
          WeightBmiPoint(weekLabel: 'W3', weightKg: 68.7, bmi: 25.9),
          WeightBmiPoint(weekLabel: 'W4', weightKg: 67.5, bmi: 25.4),
          WeightBmiPoint(weekLabel: 'W5', weightKg: 66.3, bmi: 25.0),
          WeightBmiPoint(weekLabel: 'W6', weightKg: 65.4, bmi: 24.6),
        ],
        workoutHistory: [
          MemberWorkoutRecord(id: 'w5', title: 'HIIT Fat Burn Circuit', dateLabel: 'Today, 8:20 AM', durationMinutes: 32, caloriesBurned: 345, completed: true),
          MemberWorkoutRecord(id: 'w6', title: 'Full Body Conditioning', dateLabel: '2 days ago', durationMinutes: 40, caloriesBurned: 380, completed: true),
          MemberWorkoutRecord(id: 'w7', title: 'Abs & Mobility Flow', dateLabel: '3 days ago', durationMinutes: 28, caloriesBurned: 210, completed: true),
          MemberWorkoutRecord(id: 'w8', title: 'Lower Body Endurance', dateLabel: '5 days ago', durationMinutes: 38, caloriesBurned: 325, completed: true),
        ],
        attendanceHistory: [
          MemberAttendanceRecord(id: 'a5', dateLabel: 'Today', timeLabel: '08:05 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a6', dateLabel: '2 days ago', timeLabel: '08:12 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a7', dateLabel: '3 days ago', timeLabel: '07:58 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a8', dateLabel: '5 days ago', timeLabel: '08:15 AM', method: 'QR', status: 'Present'),
        ],
        exercisePerformance: [
          ExercisePerformanceRecord(exerciseName: 'Mountain Climbers', targetMetric: '45s Interval', previousBest: '36 reps', currentBest: '52 reps', trendLabel: '+16 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'High Knees', targetMetric: '45s Interval', previousBest: '48 reps', currentBest: '64 reps', trendLabel: '+16 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Alternating Lunges', targetMetric: 'Reps / Leg', previousBest: '14 reps', currentBest: '20 reps', trendLabel: '+6 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Glute Bridge', targetMetric: 'Reps / Set', previousBest: '20 reps', currentBest: '30 reps', trendLabel: '+10 reps', isImproving: true),
        ],
      ),
      const AssignedMember(
        id: 'mem_03',
        name: 'Daniel Lim',
        age: 35,
        gender: 'Male',
        email: 'daniel.lim@bodygrind.local',
        joinDate: 'Sep 21, 2026',
        status: MemberStatus.needsAttention,
        fitnessGoal: 'Lose Weight & Improve Core Stability',
        fitnessLevel: 'Beginner',
        weeklyTargetWorkouts: 4,
        completedWorkoutsThisWeek: 2,
        workoutCompletionRate: 58,
        attendanceRate: 62,
        attendanceStreakDays: 1,
        consistencyScore: 60,
        weeklyAdherenceDays: [true, false, false, true, false, false, false],
        heightCm: 173.0,
        startingWeightKg: 89.0,
        currentWeightKg: 86.8,
        targetWeightKg: 78.0,
        weightBmiHistory: [
          WeightBmiPoint(weekLabel: 'W1', weightKg: 89.0, bmi: 29.7),
          WeightBmiPoint(weekLabel: 'W2', weightKg: 88.4, bmi: 29.5),
          WeightBmiPoint(weekLabel: 'W3', weightKg: 87.6, bmi: 29.3),
          WeightBmiPoint(weekLabel: 'W4', weightKg: 87.9, bmi: 29.4),
          WeightBmiPoint(weekLabel: 'W5', weightKg: 87.2, bmi: 29.1),
          WeightBmiPoint(weekLabel: 'W6', weightKg: 86.8, bmi: 29.0),
        ],
        workoutHistory: [
          MemberWorkoutRecord(id: 'w9', title: 'Beginner Full Body', dateLabel: 'Yesterday, 7:00 PM', durationMinutes: 25, caloriesBurned: 205, completed: true),
          MemberWorkoutRecord(id: 'w10', title: 'Core Foundations', dateLabel: '4 days ago', durationMinutes: 20, caloriesBurned: 155, completed: true),
          MemberWorkoutRecord(id: 'w11', title: 'Cardio Burn Session', dateLabel: '5 days ago', durationMinutes: 0, caloriesBurned: 0, completed: false),
        ],
        attendanceHistory: [
          MemberAttendanceRecord(id: 'a9', dateLabel: 'Yesterday', timeLabel: '06:48 PM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a10', dateLabel: '4 days ago', timeLabel: '07:05 PM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a11', dateLabel: '8 days ago', timeLabel: '06:52 PM', method: 'Front Desk', status: 'Present'),
        ],
        exercisePerformance: [
          ExercisePerformanceRecord(exerciseName: 'Wall Sit', targetMetric: 'Hold Duration', previousBest: '30 sec', currentBest: '48 sec', trendLabel: '+18 sec', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Abdominal Crunches', targetMetric: 'Reps / Set', previousBest: '12 reps', currentBest: '16 reps', trendLabel: '+4 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Plank Hold', targetMetric: 'Hold Duration', previousBest: '35 sec', currentBest: '38 sec', trendLabel: '+3 sec', isImproving: false),
          ExercisePerformanceRecord(exerciseName: 'Jumping Jacks', targetMetric: '45s Interval', previousBest: '28 reps', currentBest: '35 reps', trendLabel: '+7 reps', isImproving: true),
        ],
      ),
      const AssignedMember(
        id: 'mem_04',
        name: 'Sofia Navarro',
        age: 24,
        gender: 'Female',
        email: 'sofia.navarro@bodygrind.local',
        joinDate: 'Oct 05, 2026',
        status: MemberStatus.active,
        fitnessGoal: 'Improve Flexibility & Athletic Conditioning',
        fitnessLevel: 'Advanced',
        weeklyTargetWorkouts: 5,
        completedWorkoutsThisWeek: 5,
        workoutCompletionRate: 96,
        attendanceRate: 98,
        attendanceStreakDays: 21,
        consistencyScore: 97,
        weeklyAdherenceDays: [true, true, true, false, true, true, false],
        heightCm: 168.0,
        startingWeightKg: 59.5,
        currentWeightKg: 59.0,
        targetWeightKg: 58.5,
        weightBmiHistory: [
          WeightBmiPoint(weekLabel: 'W1', weightKg: 59.5, bmi: 21.1),
          WeightBmiPoint(weekLabel: 'W2', weightKg: 59.4, bmi: 21.0),
          WeightBmiPoint(weekLabel: 'W3', weightKg: 59.3, bmi: 21.0),
          WeightBmiPoint(weekLabel: 'W4', weightKg: 59.2, bmi: 21.0),
          WeightBmiPoint(weekLabel: 'W5', weightKg: 59.1, bmi: 20.9),
          WeightBmiPoint(weekLabel: 'W6', weightKg: 59.0, bmi: 20.9),
        ],
        workoutHistory: [
          MemberWorkoutRecord(id: 'w12', title: 'Athletic Mobility & Core', dateLabel: 'Today, 6:30 AM', durationMinutes: 42, caloriesBurned: 310, completed: true),
          MemberWorkoutRecord(id: 'w13', title: 'Explosive Conditioning', dateLabel: 'Yesterday, 6:35 AM', durationMinutes: 45, caloriesBurned: 395, completed: true),
          MemberWorkoutRecord(id: 'w14', title: 'Upper & Posture Flow', dateLabel: '2 days ago', durationMinutes: 36, caloriesBurned: 260, completed: true),
        ],
        attendanceHistory: [
          MemberAttendanceRecord(id: 'a12', dateLabel: 'Today', timeLabel: '06:18 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a13', dateLabel: 'Yesterday', timeLabel: '06:22 AM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a14', dateLabel: '2 days ago', timeLabel: '06:25 AM', method: 'QR', status: 'Present'),
        ],
        exercisePerformance: [
          ExercisePerformanceRecord(exerciseName: 'Diamond Push-Up', targetMetric: 'Reps / Set', previousBest: '10 reps', currentBest: '16 reps', trendLabel: '+6 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Bicycle Crunches', targetMetric: 'Reps / Set', previousBest: '30 reps', currentBest: '44 reps', trendLabel: '+14 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Leg Raise', targetMetric: 'Reps / Set', previousBest: '15 reps', currentBest: '22 reps', trendLabel: '+7 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Cobra Stretch', targetMetric: 'Mobility Hold', previousBest: '45 sec', currentBest: '60 sec', trendLabel: '+15 sec', isImproving: true),
        ],
      ),
      const AssignedMember(
        id: 'mem_05',
        name: 'Carlos Mendoza',
        age: 30,
        gender: 'Male',
        email: 'carlos.mendoza@bodygrind.local',
        joinDate: 'Jun 18, 2026',
        status: MemberStatus.paused,
        fitnessGoal: 'Maintain Fitness & Joint Recovery',
        fitnessLevel: 'Beginner',
        weeklyTargetWorkouts: 3,
        completedWorkoutsThisWeek: 1,
        workoutCompletionRate: 64,
        attendanceRate: 55,
        attendanceStreakDays: 0,
        consistencyScore: 58,
        weeklyAdherenceDays: [false, true, false, false, false, false, false],
        heightCm: 178.0,
        startingWeightKg: 80.2,
        currentWeightKg: 79.4,
        targetWeightKg: 77.5,
        weightBmiHistory: [
          WeightBmiPoint(weekLabel: 'W1', weightKg: 80.2, bmi: 25.3),
          WeightBmiPoint(weekLabel: 'W2', weightKg: 80.0, bmi: 25.2),
          WeightBmiPoint(weekLabel: 'W3', weightKg: 79.7, bmi: 25.2),
          WeightBmiPoint(weekLabel: 'W4', weightKg: 79.5, bmi: 25.1),
          WeightBmiPoint(weekLabel: 'W5', weightKg: 79.6, bmi: 25.1),
          WeightBmiPoint(weekLabel: 'W6', weightKg: 79.4, bmi: 25.1),
        ],
        workoutHistory: [
          MemberWorkoutRecord(id: 'w15', title: 'Low-Impact Mobility', dateLabel: '3 days ago', durationMinutes: 22, caloriesBurned: 130, completed: true),
          MemberWorkoutRecord(id: 'w16', title: 'Beginner Conditioning', dateLabel: '7 days ago', durationMinutes: 25, caloriesBurned: 175, completed: true),
        ],
        attendanceHistory: [
          MemberAttendanceRecord(id: 'a15', dateLabel: '3 days ago', timeLabel: '05:40 PM', method: 'QR', status: 'Present'),
          MemberAttendanceRecord(id: 'a16', dateLabel: '7 days ago', timeLabel: '05:50 PM', method: 'QR', status: 'Present'),
        ],
        exercisePerformance: [
          ExercisePerformanceRecord(exerciseName: 'Arm Circle', targetMetric: 'Mobility Duration', previousBest: '40 sec', currentBest: '60 sec', trendLabel: '+20 sec', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Glute Bridge', targetMetric: 'Reps / Set', previousBest: '15 reps', currentBest: '18 reps', trendLabel: '+3 reps', isImproving: true),
          ExercisePerformanceRecord(exerciseName: 'Wall Sit', targetMetric: 'Hold Duration', previousBest: '35 sec', currentBest: '40 sec', trendLabel: '+5 sec', isImproving: false),
        ],
      ),
    ];

    _pendingTasks = [
      const TrainerPendingTask(
        id: 'task_01',
        memberId: 'mem_03',
        memberName: 'Daniel Lim',
        title: 'Follow up on missed cardio session & adjust weekly pacing',
        dueLabel: 'Due Today',
        priorityLabel: 'High',
      ),
      const TrainerPendingTask(
        id: 'task_02',
        memberId: 'mem_01',
        memberName: 'Marcus Reyes',
        title: 'Review upper body push-up progression & add overload cue',
        dueLabel: 'Due Today',
        priorityLabel: 'Medium',
      ),
      const TrainerPendingTask(
        id: 'task_03',
        memberId: 'mem_02',
        memberName: 'Elena Santos',
        title: 'Update Week 7 HIIT intervals after 5.6 kg weight loss milestone',
        dueLabel: 'Tomorrow',
        priorityLabel: 'Medium',
      ),
      const TrainerPendingTask(
        id: 'task_04',
        memberId: 'mem_05',
        memberName: 'Carlos Mendoza',
        title: 'Prepare low-impact return-to-gym mobility plan',
        dueLabel: 'In 2 days',
        priorityLabel: 'Low',
      ),
    ];

    _notifications = [
      const TrainerNotification(
        id: 'notif_01',
        title: 'Morning QR Check-In',
        message: 'Marcus Reyes checked in at 07:02 AM and completed Upper Body Hypertrophy.',
        timeLabel: '25m ago',
        isUnread: true,
      ),
      const TrainerNotification(
        id: 'notif_02',
        title: 'Consistency Milestone',
        message: 'Sofia Navarro reached a 21-day attendance streak (98% monthly rate).',
        timeLabel: '2h ago',
        isUnread: true,
      ),
      const TrainerNotification(
        id: 'notif_03',
        title: 'Attendance Alert',
        message: 'Daniel Lim missed his scheduled Cardio Burn Session this week.',
        timeLabel: 'Yesterday',
        isUnread: true,
      ),
      const TrainerNotification(
        id: 'notif_04',
        title: 'Weight Goal Progress',
        message: 'Elena Santos logged 65.4 kg (-5.6 kg since Week 1).',
        timeLabel: '2 days ago',
        isUnread: false,
      ),
    ];

    _feedbackList = [
      const TrainerFeedbackEntry(
        id: 'fb_01',
        memberId: 'mem_01',
        memberName: 'Marcus Reyes',
        category: FeedbackCategory.workoutFeedback,
        message: "Strong tempo on today's push-up sets. Keep elbows tucked around 45 degrees on the final set.",
        timestampLabel: 'Today, 8:05 AM',
      ),
      const TrainerFeedbackEntry(
        id: 'fb_02',
        memberId: 'mem_02',
        memberName: 'Elena Santos',
        category: FeedbackCategory.progressComment,
        message: 'Consistent 0.9 kg/week drop while maintaining workout energy. Ready to raise HIIT work intervals by 5s.',
        timestampLabel: 'Yesterday, 4:30 PM',
      ),
      const TrainerFeedbackEntry(
        id: 'fb_03',
        memberId: 'mem_03',
        memberName: 'Daniel Lim',
        category: FeedbackCategory.trainingAdjustment,
        message: 'Swapping one high-impact session for brisk incline walking + core stability to rebuild consistency.',
        timestampLabel: '2 days ago',
      ),
      const TrainerFeedbackEntry(
        id: 'fb_04',
        memberId: 'mem_04',
        memberName: 'Sofia Navarro',
        category: FeedbackCategory.exerciseRecommendation,
        message: 'Add 3 sets of slow eccentric Diamond Push-Ups and 60s Cobra Stretch post-session.',
        timestampLabel: '3 days ago',
      ),
    ];

    _emitAll();
  }

  void _emitAll() {
    _trainerInfoController.add(_trainerInfo);
    _assignedMembersController.add(List.unmodifiable(_assignedMembers));
    _pendingTasksController.add(List.unmodifiable(_pendingTasks));
    _notificationsController.add(List.unmodifiable(_notifications));
    _feedbackListController.add(List.unmodifiable(_feedbackList));
  }

  void toggleTaskCompleted(String taskId) {
    _pendingTasks = _pendingTasks.map((task) {
      return task.id == taskId ? task.copyWith(isCompleted: !task.isCompleted) : task;
    }).toList();
    _pendingTasksController.add(List.unmodifiable(_pendingTasks));
  }

  void markNotificationRead(String notificationId) {
    _notifications = _notifications.map((notif) {
      return notif.id == notificationId ? notif.copyWith(isUnread: false) : notif;
    }).toList();
    _notificationsController.add(List.unmodifiable(_notifications));
  }

  void submitFeedback({
    required String memberId,
    required FeedbackCategory category,
    required String message,
  }) {
    final member = _assignedMembers.firstWhere(
      (m) => m.id == memberId,
      orElse: () => throw Exception('Member not found'),
    );
    final trimmed = message.trim();
    if (trimmed.isEmpty) return;

    final timeText = DateFormat('MMM d, h:mm a').format(DateTime.now());
    final entry = TrainerFeedbackEntry(
      id: 'fb_${DateTime.now().millisecondsSinceEpoch}',
      memberId: member.id,
      memberName: member.name,
      category: category,
      message: trimmed,
      timestampLabel: 'Just now ($timeText)',
    );

    _feedbackList.insert(0, entry);
    _feedbackListController.add(List.unmodifiable(_feedbackList));
  }

  void dispose() {
    _trainerInfoController.close();
    _assignedMembersController.close();
    _pendingTasksController.close();
    _notificationsController.close();
    _feedbackListController.close();
  }
}
