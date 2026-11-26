import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/exercise.dart';
import '../../../../data/models/workout_plan.dart';
import '../../../../data/models/workout_session.dart';
import '../../../../data/providers/app_providers.dart';

enum ActiveWorkoutPhase {
  countdown,
  activeExercise,
  restInterval,
  completed,
}

class ActiveWorkoutScreen extends ConsumerStatefulWidget {
  final String planId;

  const ActiveWorkoutScreen({
    super.key,
    required this.planId,
  });

  @override
  ConsumerState<ActiveWorkoutScreen> createState() => _ActiveWorkoutScreenState();
}

class _ActiveWorkoutScreenState extends ConsumerState<ActiveWorkoutScreen> {
  ActiveWorkoutPhase _phase = ActiveWorkoutPhase.countdown;
  int _countdownSeconds = 5;
  int _currentExerciseIndex = 0;
  int _timerSeconds = 0;
  int _restSeconds = 30;
  int _completedReps = 0;
  bool _isPaused = false;
  bool _soundEnabled = true;
  Timer? _ticker;

  int _totalElapsedSeconds = 0;
  Timer? _sessionTimer;

  List<MapEntry<dynamic, Exercise>> _exercises = [];
  WorkoutPlan? _plan;

  @override
  void initState() {
    super.initState();
    _startSessionTimer();
    _startCountdown();
  }

  @override
  void dispose() {
    _ticker?.cancel();
    _sessionTimer?.cancel();
    super.dispose();
  }

  void _startSessionTimer() {
    _sessionTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!_isPaused && _phase != ActiveWorkoutPhase.completed) {
        _totalElapsedSeconds++;
      }
    });
  }

  void _startCountdown() {
    setState(() {
      _phase = ActiveWorkoutPhase.countdown;
      _countdownSeconds = 5;
    });

    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_countdownSeconds > 1) {
        setState(() => _countdownSeconds--);
      } else {
        timer.cancel();
        _startExercise(_currentExerciseIndex);
      }
    });
  }

  void _startExercise(int index) {
    if (index >= _exercises.length) {
      _completeWorkout();
      return;
    }

    final currentPair = _exercises[index];
    final planEx = currentPair.key;

    setState(() {
      _phase = ActiveWorkoutPhase.activeExercise;
      _currentExerciseIndex = index;
      _timerSeconds = planEx.durationSeconds > 0 ? planEx.durationSeconds : 0;
      _completedReps = planEx.reps;
      _isPaused = false;
    });

    _ticker?.cancel();
    if (planEx.durationSeconds > 0) {
      _ticker = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!_isPaused) {
          if (_timerSeconds > 1) {
            setState(() => _timerSeconds--);
          } else {
            timer.cancel();
            _startRestInterval();
          }
        }
      });
    }
  }

  void _startRestInterval() {
    if (_currentExerciseIndex >= _exercises.length - 1) {
      _completeWorkout();
      return;
    }

    setState(() {
      _phase = ActiveWorkoutPhase.restInterval;
      _restSeconds = 20;
      _isPaused = false;
    });

    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!_isPaused) {
        if (_restSeconds > 1) {
          setState(() => _restSeconds--);
        } else {
          timer.cancel();
          _startExercise(_currentExerciseIndex + 1);
        }
      }
    });
  }

  void _completeWorkout() {
    _ticker?.cancel();
    _sessionTimer?.cancel();

    final now = DateTime.now();
    final calories = ((_totalElapsedSeconds / 60) * 8.5).round().clamp(50, 600);
    final session = WorkoutSession(
      id: '${now.millisecondsSinceEpoch}',
      planId: widget.planId,
      planName: _plan?.name ?? 'Workout',
      durationSeconds: _totalElapsedSeconds,
      caloriesBurned: calories,
      completedAt: now.millisecondsSinceEpoch,
      exercisesCompleted: _exercises.length,
      xpEarned: 100,
    );

    ref.read(workoutRepositoryProvider).recordCompletedSession(session);

    setState(() {
      _phase = ActiveWorkoutPhase.completed;
    });
  }

  void _confirmQuit() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Quit Workout?', style: TextStyle(fontWeight: FontWeight.bold)),
        content: const Text('Are you sure you want to quit? Your progress for this session will not be saved.'),
        actions: [
          OutlinedButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Resume'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: AppColors.dangerRed),
            onPressed: () {
              Navigator.pop(ctx);
              context.pop();
            },
            child: const Text('Quit'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final plans = ref.watch(plansStreamProvider).valueOrNull ?? [];
    if (_plan == null && plans.isNotEmpty) {
      _plan = plans.firstWhere((p) => p.id == widget.planId, orElse: () => plans.first);
    }

    final exercisesAsync = ref.watch(
      StreamProvider((ref) => ref.watch(workoutRepositoryProvider).getPlanExercises(widget.planId)),
    );

    if (_exercises.isEmpty && exercisesAsync.valueOrNull != null) {
      _exercises = exercisesAsync.valueOrNull!;
    }

    return PopScope(
      canPop: _phase == ActiveWorkoutPhase.completed,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop && _phase != ActiveWorkoutPhase.completed) {
          _confirmQuit();
        }
      },
      child: Scaffold(
        appBar: _phase != ActiveWorkoutPhase.completed
            ? AppBar(
                title: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(_plan?.name ?? 'Workout', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    if (_phase == ActiveWorkoutPhase.activeExercise && _exercises.isNotEmpty)
                      Text(
                        'Exercise ${_currentExerciseIndex + 1} of ${_exercises.length}',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                  ],
                ),
                leading: IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: _confirmQuit,
                ),
                actions: [
                  IconButton(
                    icon: Icon(_soundEnabled ? Icons.volume_up : Icons.volume_off),
                    onPressed: () => setState(() => _soundEnabled = !_soundEnabled),
                  ),
                ],
              )
            : null,
        body: SafeArea(
          child: switch (_phase) {
            ActiveWorkoutPhase.countdown => _buildCountdownView(theme),
            ActiveWorkoutPhase.activeExercise => _buildActiveExerciseView(theme),
            ActiveWorkoutPhase.restInterval => _buildRestIntervalView(theme),
            ActiveWorkoutPhase.completed => _buildCompletedView(theme),
          },
        ),
      ),
    );
  }

  Widget _buildCountdownView(ThemeData theme) {
    final nextName = _exercises.isNotEmpty ? _exercises.first.value.name : 'First Exercise';

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              'READY TO GO!',
              style: TextStyle(
                color: AppColors.flameOrange,
                fontSize: 28,
                fontWeight: FontWeight.black,
              ),
            ),
            const SizedBox(height: 32),
            Container(
              width: 140,
              height: 140,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.flameOrange.withOpacity(0.15),
                border: Border.all(color: AppColors.flameOrange, width: 4),
              ),
              alignment: Alignment.center,
              child: Text(
                '$_countdownSeconds',
                style: const TextStyle(
                  fontSize: 64,
                  fontWeight: FontWeight.black,
                  color: AppColors.flameOrange,
                ),
              ),
            ),
            const SizedBox(height: 32),
            Text(
              'NEXT UP:',
              style: theme.textTheme.labelMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              nextName,
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActiveExerciseView(ThemeData theme) {
    if (_exercises.isEmpty || _currentExerciseIndex >= _exercises.length) {
      return const Center(child: CircularProgressIndicator());
    }

    final pair = _exercises[_currentExerciseIndex];
    final planEx = pair.key;
    final exercise = pair.value;
    final isDuration = planEx.durationSeconds > 0;

    return Padding(
      padding: const EdgeInsets.all(20),
      child: Column(
        children: [
          // Linear progress through workout
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: (_currentExerciseIndex + 1) / _exercises.length,
              minHeight: 6,
              color: AppColors.flameOrange,
              backgroundColor: theme.colorScheme.surfaceContainerHighest,
            ),
          ),
          const SizedBox(height: 20),

          // Exercise Visualizer Container
          Expanded(
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                borderRadius: BorderRadius.circular(20),
              ),
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      color: AppColors.flameOrange.withOpacity(0.18),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(Icons.fitness_center, color: AppColors.flameOrange, size: 36),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    exercise.name,
                    style: theme.textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 6),
                  Text(
                    exercise.targetMuscles,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: AppColors.flameOrange,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 24),
                  if (isDuration)
                    Text(
                      '${_timerSeconds}s',
                      style: const TextStyle(
                        fontSize: 54,
                        fontWeight: FontWeight.black,
                        color: AppColors.electricCyan,
                      ),
                    )
                  else
                    Column(
                      children: [
                        Text(
                          'x$_completedReps',
                          style: const TextStyle(
                            fontSize: 48,
                            fontWeight: FontWeight.black,
                            color: AppColors.flameOrange,
                          ),
                        ),
                        const SizedBox(height: 8),
                        OutlinedButton.icon(
                          onPressed: () => setState(() => _completedReps++),
                          icon: const Icon(Icons.add),
                          label: const Text('+1 Rep'),
                        ),
                      ],
                    ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Control Actions Row
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              IconButton.outlined(
                icon: const Icon(Icons.skip_previous),
                onPressed: _currentExerciseIndex > 0
                    ? () => _startExercise(_currentExerciseIndex - 1)
                    : null,
              ),
              IconButton.filled(
                style: IconButton.styleFrom(
                  backgroundColor: AppColors.flameOrange,
                  padding: const EdgeInsets.all(16),
                ),
                icon: Icon(_isPaused ? Icons.play_arrow : Icons.pause, size: 32),
                onPressed: () => setState(() => _isPaused = !_isPaused),
              ),
              IconButton.filledTonal(
                style: IconButton.styleFrom(
                  backgroundColor: AppColors.successGreen.withOpacity(0.2),
                  foregroundColor: AppColors.successGreen,
                  padding: const EdgeInsets.all(14),
                ),
                icon: const Icon(Icons.check, size: 28),
                onPressed: _startRestInterval,
              ),
              IconButton.outlined(
                icon: const Icon(Icons.skip_next),
                onPressed: () => _startExercise(_currentExerciseIndex + 1),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildRestIntervalView(ThemeData theme) {
    final nextName = (_currentExerciseIndex + 1 < _exercises.length)
        ? _exercises[_currentExerciseIndex + 1].value.name
        : 'Finish Workout';

    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text(
            'REST TIME',
            style: TextStyle(
              fontSize: 24,
              fontWeight: FontWeight.bold,
              color: AppColors.electricCyan,
            ),
          ),
          const SizedBox(height: 24),
          Container(
            width: 140,
            height: 140,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: AppColors.electricCyan.withOpacity(0.12),
              border: Border.all(color: AppColors.electricCyan, width: 4),
            ),
            alignment: Alignment.center,
            child: Text(
              '$_restSeconds',
              style: const TextStyle(
                fontSize: 54,
                fontWeight: FontWeight.black,
                color: AppColors.electricCyan,
              ),
            ),
          ),
          const SizedBox(height: 24),
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              OutlinedButton(
                onPressed: () => setState(() => _restSeconds += 20),
                child: const Text('+20s Rest'),
              ),
              const SizedBox(width: 12),
              FilledButton(
                style: FilledButton.styleFrom(backgroundColor: AppColors.electricCyan),
                onPressed: () {
                  _ticker?.cancel();
                  _startExercise(_currentExerciseIndex + 1);
                },
                child: const Text('Skip Rest', style: TextStyle(color: Colors.black, fontWeight: FontWeight.bold)),
              ),
            ],
          ),
          const SizedBox(height: 32),
          Text(
            'UP NEXT:',
            style: theme.textTheme.labelMedium?.copyWith(
              color: theme.colorScheme.onSurfaceVariant,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            nextName,
            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }

  Widget _buildCompletedView(ThemeData theme) {
    final minutes = (_totalElapsedSeconds / 60).ceil();
    final calories = ((_totalElapsedSeconds / 60) * 8.5).round().clamp(50, 600);

    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 90,
              height: 90,
              decoration: const BoxDecoration(
                color: AppColors.successGreen,
                shape: BoxShape.circle,
              ),
              child: const Icon(Icons.check, color: Colors.white, size: 54),
            ),
            const SizedBox(height: 20),
            Text(
              'WORKOUT COMPLETED!',
              style: theme.textTheme.headlineSmall?.copyWith(
                fontWeight: FontWeight.black,
                color: AppColors.flameOrange,
              ),
            ),
            const SizedBox(height: 20),
            // Metrics summary card
            Card(
              elevation: 0,
              shape: RoundedCornerShape(16),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    Column(
                      children: [
                        Text('$minutes mins', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                        Text('Duration', style: theme.textTheme.labelSmall),
                      ],
                    ),
                    Column(
                      children: [
                        Text('$calories kcal', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                        Text('Burned', style: theme.textTheme.labelSmall),
                      ],
                    ),
                    Column(
                      children: [
                        const Text('+100 XP', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16, color: AppColors.flameOrange)),
                        Text('Reward', style: theme.textTheme.labelSmall),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),
            SizedBox(
              width: double.infinity,
              height: 50,
              child: FilledButton(
                style: FilledButton.styleFrom(backgroundColor: AppColors.flameOrange),
                onPressed: () => context.pop(),
                child: const Text('DONE', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
