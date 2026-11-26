import 'package:flutter/material.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/user_profile.dart';

/// 7-step onboarding flow matching OnboardingScreen.kt
class OnboardingScreen extends StatefulWidget {
  final UserProfile initialProfile;
  final ValueChanged<UserProfile> onCompleteOnboarding;

  const OnboardingScreen({
    super.key,
    required this.initialProfile,
    required this.onCompleteOnboarding,
  });

  @override
  State<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends State<OnboardingScreen> {
  int _step = 1;
  static const int _totalSteps = 7;

  late String _fitnessGoal;
  late String _fitnessLevel;
  late int _preferredDuration;
  late Set<String> _selectedDays;
  late TextEditingController _weightController;
  late TextEditingController _heightController;
  late bool _reminderEnabled;

  static const _goals = [
    ('Lose Weight', 'Burn fat and improve cardiovascular conditioning'),
    ('Build Muscle', 'Increase strength and muscle tone at home'),
    ('Improve Endurance', 'Boost stamina and stamina reserves'),
    ('Improve Flexibility', 'Release tension and increase range of motion'),
    ('Maintain Fitness', 'Stay active and preserve daily health'),
    ('Daily Exercise Habit', 'Build unbreakable discipline 15 mins a day'),
  ];

  static const _levels = [
    ('Beginner', 'I rarely work out or am just getting started'),
    ('Intermediate', 'I train 1-3 times a week consistently'),
    ('Advanced', 'I am in great shape and ready for high intensity'),
  ];

  static const _durations = [10, 15, 20, 30];
  static const _weekDays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  @override
  void initState() {
    super.initState();
    _fitnessGoal = widget.initialProfile.fitnessGoal;
    _fitnessLevel = widget.initialProfile.fitnessLevel;
    _preferredDuration = widget.initialProfile.preferredDuration;
    _selectedDays = {'Mon', 'Wed', 'Fri'};
    _weightController = TextEditingController(
      text: widget.initialProfile.weightKg.toInt().toString(),
    );
    _heightController = TextEditingController(
      text: widget.initialProfile.heightCm.toInt().toString(),
    );
    _reminderEnabled = widget.initialProfile.reminderEnabled;
  }

  @override
  void dispose() {
    _weightController.dispose();
    _heightController.dispose();
    super.dispose();
  }

  void _finish() {
    final w = double.tryParse(_weightController.text) ?? 70.0;
    final h = double.tryParse(_heightController.text) ?? 175.0;

    final updated = widget.initialProfile.copyWith(
      fitnessGoal: _fitnessGoal,
      fitnessLevel: _fitnessLevel,
      preferredDuration: _preferredDuration,
      weightKg: w,
      heightCm: h,
      reminderEnabled: _reminderEnabled,
      onboardingCompleted: true,
    );
    widget.onCompleteOnboarding(updated);
  }

  void _onNext() {
    if (_step < _totalSteps) {
      setState(() => _step++);
    } else {
      _finish();
    }
  }

  void _onBack() {
    if (_step > 1) {
      setState(() => _step--);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.scaffoldBackgroundColor,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: _step > 1
            ? IconButton(
                icon: const Icon(Icons.arrow_back),
                onPressed: _onBack,
              )
            : const SizedBox.shrink(),
        title: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: List.generate(_totalSteps, (index) {
            final isCurrent = index + 1 == _step;
            return AnimatedContainer(
              duration: const Duration(milliseconds: 250),
              margin: const EdgeInsets.symmetric(horizontal: 2),
              width: isCurrent ? 16 : 8,
              height: 8,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(4),
                color: isCurrent
                    ? AppColors.boldRed
                    : theme.colorScheme.surfaceContainerHighest,
              ),
            );
          }),
        ),
        actions: [
          if (_step < _totalSteps)
            TextButton(
              onPressed: _finish,
              child: Text(
                'Skip',
                style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
              ),
            )
          else
            const SizedBox(width: 48),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            boxShadow: const [
              BoxShadow(
                color: Colors.black12,
                blurRadius: 8,
                offset: Offset(0, -2),
              ),
            ],
          ),
          child: SizedBox(
            height: 54,
            child: ElevatedButton(
              onPressed: _onNext,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.boldRed,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(27),
                ),
              ),
              child: Text(
                _step == _totalSteps ? 'START MY JOURNEY' : 'CONTINUE',
                style: const TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
              child: _buildStepContent(theme),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildStepContent(ThemeData theme) {
    switch (_step) {
      case 1:
        return _buildGoalStep(theme);
      case 2:
        return _buildLevelStep(theme);
      case 3:
        return _buildDurationStep(theme);
      case 4:
        return _buildDaysStep(theme);
      case 5:
        return _buildMeasurementsStep(theme);
      case 6:
        return _buildReminderStep(theme);
      case 7:
        return _buildSafetyStep(theme);
      default:
        return const SizedBox.shrink();
    }
  }

  Widget _buildGoalStep(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'What is your main goal?',
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 16),
        ..._goals.map((g) {
          final isSelected = _fitnessGoal == g.$1;
          return Card(
            margin: const EdgeInsets.symmetric(vertical: 4),
            color: isSelected
                ? AppColors.boldRed.withOpacity(0.15)
                : theme.colorScheme.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
              side: isSelected
                  ? const BorderSide(color: AppColors.boldRed, width: 2)
                  : BorderSide(color: theme.colorScheme.outline, width: 1),
            ),
            child: InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: () => setState(() => _fitnessGoal = g.$1),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            g.$1,
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            g.$2,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (isSelected)
                      const Icon(
                        Icons.check_circle,
                        color: AppColors.boldRed,
                      ),
                  ],
                ),
              ),
            ),
          );
        }),
      ],
    );
  }

  Widget _buildLevelStep(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Choose your fitness level',
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 16),
        ..._levels.map((lvl) {
          final isSelected = _fitnessLevel == lvl.$1;
          return Card(
            margin: const EdgeInsets.symmetric(vertical: 6),
            color: isSelected
                ? AppColors.boldRed.withOpacity(0.15)
                : theme.colorScheme.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
              side: isSelected
                  ? const BorderSide(color: AppColors.boldRed, width: 2)
                  : BorderSide(color: theme.colorScheme.outline, width: 1),
            ),
            child: InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: () => setState(() => _fitnessLevel = lvl.$1),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            lvl.$1,
                            style: theme.textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            lvl.$2,
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ],
                      ),
                    ),
                    if (isSelected)
                      const Icon(
                        Icons.check_circle,
                        color: AppColors.boldRed,
                      ),
                  ],
                ),
              ),
            ),
          );
        }),
      ],
    );
  }

  Widget _buildDurationStep(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Target workout duration',
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 16),
        ..._durations.map((dur) {
          final isSelected = _preferredDuration == dur;
          return Card(
            margin: const EdgeInsets.symmetric(vertical: 6),
            color: isSelected
                ? AppColors.boldRed.withOpacity(0.15)
                : theme.colorScheme.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(14),
              side: isSelected
                  ? const BorderSide(color: AppColors.boldRed, width: 2)
                  : BorderSide(color: theme.colorScheme.outline, width: 1),
            ),
            child: InkWell(
              borderRadius: BorderRadius.circular(14),
              onTap: () => setState(() => _preferredDuration = dur),
              child: Padding(
                padding: const EdgeInsets.all(18),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '$dur Minutes / Day',
                      style: theme.textTheme.titleMedium?.copyWith(
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    if (isSelected)
                      const Icon(
                        Icons.check_circle,
                        color: AppColors.boldRed,
                      ),
                  ],
                ),
              ),
            ),
          );
        }),
      ],
    );
  }

  Widget _buildDaysStep(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Select workout days',
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 16),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: _weekDays.map((day) {
            final isSelected = _selectedDays.contains(day);
            return InkWell(
              onTap: () {
                setState(() {
                  if (isSelected) {
                    _selectedDays.remove(day);
                  } else {
                    _selectedDays.add(day);
                  }
                });
              },
              borderRadius: BorderRadius.circular(24),
              child: Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: isSelected
                      ? AppColors.boldRed
                      : theme.colorScheme.surfaceContainerHighest,
                ),
                alignment: Alignment.center,
                child: Text(
                  day,
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    fontSize: 13,
                    color: isSelected ? Colors.white : theme.colorScheme.onSurface,
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildMeasurementsStep(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Optional body measurements',
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _weightController,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(
            labelText: 'Weight (kg)',
          ),
        ),
        const SizedBox(height: 16),
        TextField(
          controller: _heightController,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(
            labelText: 'Height (cm)',
          ),
        ),
      ],
    );
  }

  Widget _buildReminderStep(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Set daily workout reminder',
          style: theme.textTheme.headlineSmall?.copyWith(
            fontWeight: FontWeight.w900,
          ),
        ),
        const SizedBox(height: 16),
        Card(
          color: theme.colorScheme.surface,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: theme.colorScheme.outline),
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    const Icon(
                      Icons.notifications_active,
                      color: AppColors.warningAmber,
                      size: 28,
                    ),
                    const SizedBox(width: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Daily Reminder',
                          style: theme.textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Text(
                          'Scheduled at 8:00 AM',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
                Switch(
                  value: _reminderEnabled,
                  onChanged: (val) => setState(() => _reminderEnabled = val),
                  activeColor: AppColors.boldRed,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildSafetyStep(ThemeData theme) {
    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        const SizedBox(height: 24),
        Container(
          width: 80,
          height: 80,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: AppColors.successGreen.withOpacity(0.2),
          ),
          alignment: Alignment.center,
          child: const Icon(
            Icons.health_and_safety,
            size: 48,
            color: AppColors.successGreen,
          ),
        ),
        const SizedBox(height: 16),
        Text(
          "You're All Set!",
          style: theme.textTheme.headlineMedium?.copyWith(
            fontWeight: FontWeight.w900,
            color: AppColors.boldRed,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          'Safety Notice:\nPlease warm up before exercising and listen to your body. If you feel sharp pain or dizziness, stop and rest immediately. Consult your physician before starting any rigorous fitness regimen.',
          style: theme.textTheme.bodyMedium?.copyWith(
            color: theme.colorScheme.onSurfaceVariant,
            height: 1.5,
          ),
          textAlign: TextAlign.Center,
        ),
        const SizedBox(height: 24),
      ],
    );
  }
}
