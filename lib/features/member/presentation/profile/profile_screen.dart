import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/user_profile.dart';
import '../../../../data/models/weekly_record.dart';
import '../../../../data/providers/app_providers.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  final int initialTab;

  const ProfileScreen({super.key, this.initialTab = 0});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this, initialIndex: widget.initialTab);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  // --- DIALOGS FOR PROFILE TAB ---

  void _showEditProfileDialog(UserProfile profile) {
    String name = profile.name;
    int age = profile.age;
    String gender = profile.gender;
    double weightKg = profile.weightKg;
    double heightCm = profile.heightCm;
    String goal = profile.fitnessGoal;
    String level = profile.fitnessLevel;
    int duration = profile.preferredDuration;

    final weightController = TextEditingController(
      text: profile.useMetric
          ? weightKg.toStringAsFixed(1)
          : (weightKg / 0.45359237).toStringAsFixed(1),
    );
    final heightController = TextEditingController(
      text: profile.useMetric
          ? heightCm.toStringAsFixed(0)
          : (heightCm / 2.54).toStringAsFixed(0),
    );

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            title: const Text('Edit Fitness Profile', style: TextStyle(fontWeight: FontWeight.bold)),
            content: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  TextFormField(
                    initialValue: name,
                    decoration: const InputDecoration(labelText: 'Name / Handle', isDense: true),
                    onChanged: (val) => name = val,
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: TextFormField(
                          initialValue: age.toString(),
                          keyboardType: TextInputType.number,
                          decoration: const InputDecoration(labelText: 'Age (yrs)', isDense: true),
                          onChanged: (val) {
                            final parsed = int.tryParse(val);
                            if (parsed != null) age = parsed;
                          },
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: DropdownButtonFormField<String>(
                          value: ['Male', 'Female', 'Prefer not to say'].contains(gender)
                              ? gender
                              : 'Male',
                          decoration: const InputDecoration(labelText: 'Gender', isDense: true),
                          items: const [
                            DropdownMenuItem(value: 'Male', child: Text('Male')),
                            DropdownMenuItem(value: 'Female', child: Text('Female')),
                            DropdownMenuItem(value: 'Prefer not to say', child: Text('Other')),
                          ],
                          onChanged: (val) {
                            if (val != null) setDialogState(() => gender = val);
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: weightController,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: InputDecoration(
                            labelText: 'Weight',
                            suffixText: profile.useMetric ? 'kg' : 'lb',
                            isDense: true,
                          ),
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: TextField(
                          controller: heightController,
                          keyboardType: const TextInputType.numberWithOptions(decimal: true),
                          decoration: InputDecoration(
                            labelText: 'Height',
                            suffixText: profile.useMetric ? 'cm' : 'in',
                            isDense: true,
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: goal,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Primary Goal', isDense: true),
                    items: const [
                      DropdownMenuItem(value: 'Build Muscle', child: Text('Build Muscle')),
                      DropdownMenuItem(value: 'Lose Weight', child: Text('Lose Weight')),
                      DropdownMenuItem(value: 'Improve Endurance', child: Text('Improve Endurance')),
                      DropdownMenuItem(value: 'Improve Flexibility', child: Text('Improve Flexibility')),
                      DropdownMenuItem(value: 'Maintain Fitness', child: Text('Maintain Fitness')),
                      DropdownMenuItem(value: 'Daily Exercise Habit', child: Text('Daily Exercise Habit')),
                    ],
                    onChanged: (val) {
                      if (val != null) setDialogState(() => goal = val);
                    },
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<String>(
                    value: level,
                    decoration: const InputDecoration(labelText: 'Experience Level', isDense: true),
                    items: const [
                      DropdownMenuItem(value: 'Beginner', child: Text('Beginner')),
                      DropdownMenuItem(value: 'Intermediate', child: Text('Intermediate')),
                      DropdownMenuItem(value: 'Advanced', child: Text('Advanced')),
                    ],
                    onChanged: (val) {
                      if (val != null) setDialogState(() => level = val);
                    },
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    value: duration,
                    decoration: const InputDecoration(labelText: 'Preferred Duration', isDense: true),
                    items: const [
                      DropdownMenuItem(value: 15, child: Text('15 minutes')),
                      DropdownMenuItem(value: 30, child: Text('30 minutes')),
                      DropdownMenuItem(value: 45, child: Text('45 minutes')),
                      DropdownMenuItem(value: 60, child: Text('60 minutes')),
                    ],
                    onChanged: (val) {
                      if (val != null) setDialogState(() => duration = val);
                    },
                  ),
                ],
              ),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
              FilledButton(
                style: FilledButton.styleFrom(backgroundColor: AppColors.boldRed),
                onPressed: () {
                  final parsedW = double.tryParse(weightController.text) ?? profile.weightKg;
                  final parsedH = double.tryParse(heightController.text) ?? profile.heightCm;

                  final finalW = profile.useMetric ? parsedW : (parsedW * 0.45359237);
                  final finalH = profile.useMetric ? parsedH : (parsedH * 2.54);

                  final updated = profile.copyWith(
                    name: name.trim().isEmpty ? 'Athlete' : name.trim(),
                    age: age.clamp(12, 100),
                    gender: gender,
                    weightKg: finalW.clamp(30.0, 300.0),
                    heightCm: finalH.clamp(100.0, 250.0),
                    fitnessGoal: goal,
                    fitnessLevel: level,
                    preferredDuration: duration,
                  );
                  ref.read(workoutRepositoryProvider).updateProfile(updated);
                  Navigator.pop(ctx);
                },
                child: const Text('Save Profile'),
              ),
            ],
          );
        },
      ),
    );
  }

  void _showTimePickerDialog(UserProfile profile) async {
    final initialTime = TimeOfDay(hour: profile.reminderHour, minute: profile.reminderMinute);
    final picked = await showTimePicker(context: context, initialTime: initialTime);
    if (picked != null) {
      final updated = profile.copyWith(
        reminderHour: picked.hour,
        reminderMinute: picked.minute,
        reminderEnabled: true,
      );
      ref.read(workoutRepositoryProvider).updateProfile(updated);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Daily workout reminder time updated!')),
        );
      }
    }
  }

  // --- DIALOGS FOR PROGRESS TAB (UI FIX 1: LOG MEASUREMENT FORM) ---

  void _showLogMeasurementForm(UserProfile profile) {
    final weightCtrl = TextEditingController(text: profile.weightKg.toStringAsFixed(1));
    final heightCtrl = TextEditingController(text: profile.heightCm.toStringAsFixed(1));
    final chestCtrl = TextEditingController(text: '96.0');
    final armCtrl = TextEditingController(text: '35.0');
    final waistCtrl = TextEditingController(text: '82.0');

    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        padding: EdgeInsets.fromLTRB(20, 12, 20, MediaQuery.of(ctx).viewInsets.bottom + 24),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(
                child: Container(
                  width: 40,
                  height: 4,
                  margin: const EdgeInsets.only(bottom: 16),
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.outlineVariant,
                    borderRadius: BorderRadius.circular(2),
                  ),
                ),
              ),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Log Weekly Measurement',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  IconButton(icon: const Icon(Icons.close), onPressed: () => Navigator.pop(ctx)),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: weightCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: 'Weight (kg)', isDense: true),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: TextField(
                      controller: heightCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: 'Height (cm)', isDense: true),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: chestCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: 'Chest (cm)', isDense: true),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: armCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: 'Arms (cm)', isDense: true),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: TextField(
                      controller: waistCtrl,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      decoration: const InputDecoration(labelText: 'Waist (cm)', isDense: true),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              SizedBox(
                width: double.infinity,
                height: 48,
                child: FilledButton(
                  style: FilledButton.styleFrom(backgroundColor: AppColors.boldRed),
                  onPressed: () {
                    final w = double.tryParse(weightCtrl.text) ?? profile.weightKg;
                    final h = double.tryParse(heightCtrl.text) ?? profile.heightCm;
                    final ch = double.tryParse(chestCtrl.text) ?? 96.0;
                    final arm = double.tryParse(armCtrl.text) ?? 35.0;
                    final waist = double.tryParse(waistCtrl.text) ?? 82.0;

                    final now = DateTime.now();
                    final rec = WeeklyRecord(
                      id: '${now.millisecondsSinceEpoch}',
                      weekNumber: ((now.day / 7).ceil()),
                      year: now.year,
                      memberName: profile.name,
                      memberAge: profile.age,
                      weightKg: w,
                      heightCm: h,
                      chestCm: ch,
                      armCm: arm,
                      waistCm: waist,
                      timestamp: now.millisecondsSinceEpoch,
                    );

                    ref.read(weeklyProgressRepositoryProvider).addRecord(rec);
                    Navigator.pop(ctx);
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('Measurement recorded successfully!')),
                    );
                  },
                  child: const Text('Save Measurement', style: TextStyle(fontWeight: FontWeight.bold)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _exportCsv(List<WeeklyRecord> records) {
    if (records.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No measurement records to export.')),
      );
      return;
    }

    final buffer = StringBuffer();
    buffer.writeln('Week,Year,Member,Weight (kg),Height (cm),Chest (cm),Arm (cm),Waist (cm),Date');
    for (final r in records) {
      final dateStr = DateFormat('yyyy-MM-dd').format(DateTime.fromMillisecondsSinceEpoch(r.timestamp));
      buffer.writeln('${r.weekNumber},${r.year},${r.memberName},${r.weightKg},${r.heightCm},${r.chestCm},${r.armCm},${r.waistCm},$dateStr');
    }

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Exported CSV Data', style: TextStyle(fontWeight: FontWeight.bold)),
        content: SingleChildScrollView(
          child: SelectableText(buffer.toString(), style: const TextStyle(fontFamily: 'monospace', fontSize: 11)),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Close')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final profile = ref.watch(userProfileStreamProvider).valueOrNull ??
        ref.watch(workoutRepositoryProvider).currentProfile;
    final weeklyRecords = ref.watch(weeklyRecordsStreamProvider).valueOrNull ?? [];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Profile & Progress', style: TextStyle(fontWeight: FontWeight.bold)),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: AppColors.boldRed,
          labelColor: AppColors.boldRed,
          unselectedLabelColor: theme.colorScheme.onSurfaceVariant,
          labelStyle: const TextStyle(fontWeight: FontWeight.bold),
          tabs: const [
            Tab(icon: Icon(Icons.person, size: 18), text: 'Profile'),
            Tab(icon: Icon(Icons.analytics, size: 18), text: 'Progress'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // TAB 0: PROFILE & SETTINGS
          _buildProfileTab(context, theme, profile),

          // TAB 1: PROGRESS & ANALYTICS (WITH COMPACT LOG MEASUREMENT BUTTON - UI FIX 1)
          _buildProgressTab(context, theme, profile, weeklyRecords),
        ],
      ),
    );
  }

  Widget _buildProfileTab(BuildContext context, ThemeData theme, UserProfile profile) {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        // Profile Avatar Card
        Card(
          elevation: 0,
          shape: RoundedCornerShape(20),
          color: theme.colorScheme.surface,
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: theme.colorScheme.outlineVariant),
            ),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 30,
                  backgroundColor: AppColors.boldRed,
                  child: const Icon(Icons.person, color: Colors.white, size: 36),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        profile.name,
                        style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                      ),
                      Text(
                        '${profile.fitnessGoal} • ${profile.age} yrs',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      ),
                      Text(
                        'Level ${profile.level} • ${profile.xpPoints} XP',
                        style: const TextStyle(
                          color: AppColors.flameOrange,
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.edit_outlined),
                  onPressed: () => _showEditProfileDialog(profile),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Look & Home Settings Navigation Card
        Card(
          elevation: 0,
          shape: RoundedCornerShape(18),
          color: theme.colorScheme.surface,
          child: InkWell(
            borderRadius: BorderRadius.circular(18),
            onTap: () => context.push('/member/settings'),
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.palette_outlined, color: AppColors.boldRed),
                      const SizedBox(width: 12),
                      Text('Look & Home Settings', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                    ],
                  ),
                  const Icon(Icons.chevron_right, color: Colors.grey),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Daily Workout Reminder Card
        Card(
          elevation: 0,
          shape: RoundedCornerShape(18),
          color: theme.colorScheme.surface,
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: theme.colorScheme.outlineVariant),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Daily Workout Reminder', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                const SizedBox(height: 10),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    InkWell(
                      onTap: () => _showTimePickerDialog(profile),
                      child: Row(
                        children: [
                          const Icon(Icons.alarm, color: AppColors.goldYellow, size: 22),
                          const SizedBox(width: 10),
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '${profile.reminderHour.toString().padLeft(2, '0')}:${profile.reminderMinute.toString().padLeft(2, '0')}',
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                              ),
                              Text('Tap to adjust time', style: theme.textTheme.labelSmall?.copyWith(color: AppColors.flameOrange)),
                            ],
                          ),
                        ],
                      ),
                    ),
                    Switch(
                      value: profile.reminderEnabled,
                      onChanged: (val) {
                        ref.read(workoutRepositoryProvider).updateProfile(
                              profile.copyWith(reminderEnabled: val),
                            );
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Preferences: Units System Card
        Card(
          elevation: 0,
          shape: RoundedCornerShape(18),
          color: theme.colorScheme.surface,
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: theme.colorScheme.outlineVariant),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Measurement Units', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                SegmentedButton<bool>(
                  segments: const [
                    ButtonSegment(value: true, label: Text('kg / cm')),
                    ButtonSegment(value: false, label: Text('lb / in')),
                  ],
                  selected: {profile.useMetric},
                  onSelectionChanged: (val) {
                    ref.read(workoutRepositoryProvider).updateProfile(
                          profile.copyWith(useMetric: val.first),
                        );
                  },
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),

        // Logout Button
        SizedBox(
          width: double.infinity,
          height: 48,
          child: OutlinedButton.icon(
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.dangerRed,
              side: const BorderSide(color: AppColors.dangerRed),
            ),
            onPressed: () {
              ref.read(authRepositoryProvider).signOut();
              ref.read(selectedRoleProvider.notifier).state = null;
              context.go('/');
            },
            icon: const Icon(Icons.logout),
            label: const Text('Log Out', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildProgressTab(
    BuildContext context,
    ThemeData theme,
    UserProfile profile,
    List<WeeklyRecord> records,
  ) {
    final latestRecord = records.isNotEmpty ? records.first : null;
    final previousRecord = records.length > 1 ? records[1] : null;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        // Title & Compact Log Measurement Button (UI FIX 1)
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Progress & Analytics',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
            ),
            // UI FIX 1: Proportionate, compact button (not fill-max-width, min 48dp touch target)
            SizedBox(
              height: 48,
              child: FilledButton.tonalIcon(
                style: FilledButton.styleFrom(
                  backgroundColor: AppColors.boldRed.withOpacity(0.15),
                  foregroundColor: AppColors.boldRed,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  shape: RoundedCornerShape(10),
                ),
                icon: const Icon(Icons.add_chart, size: 16),
                label: const Text(
                  'Log Measurement',
                  style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold),
                ),
                onPressed: () => _showLogMeasurementForm(profile),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Latest Biometrics Comparison Card
        if (latestRecord != null) ...[
          Card(
            elevation: 0,
            shape: RoundedCornerShape(20),
            color: theme.colorScheme.surface,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Latest Metrics (Week ${latestRecord.weekNumber})',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                      ),
                      Text(
                        DateFormat('MMM d, yyyy').format(DateTime.fromMillisecondsSinceEpoch(latestRecord.timestamp)),
                        style: theme.textTheme.labelSmall?.copyWith(color: Colors.grey),
                      ),
                    ],
                  ),
                  const SizedBox(height: 14),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceAround,
                    children: [
                      _buildMetricTile('Weight', '${latestRecord.weightKg.toStringAsFixed(1)} kg', previousRecord?.weightKg, latestRecord.weightKg),
                      _buildMetricTile('Chest', '${latestRecord.chestCm.toStringAsFixed(1)} cm', previousRecord?.chestCm, latestRecord.chestCm),
                      _buildMetricTile('Arms', '${latestRecord.armCm.toStringAsFixed(1)} cm', previousRecord?.armCm, latestRecord.armCm),
                      _buildMetricTile('Waist', '${latestRecord.waistCm.toStringAsFixed(1)} cm', previousRecord?.waistCm, latestRecord.waistCm),
                    ],
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],

        // Export Actions Row
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              'Weekly Records (${records.length})',
              style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            OutlinedButton.icon(
              style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
              icon: const Icon(Icons.download, size: 16),
              label: const Text('Export CSV', style: TextStyle(fontSize: 12)),
              onPressed: () => _exportCsv(records),
            ),
          ],
        ),
        const SizedBox(height: 10),

        if (records.isEmpty)
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
              borderRadius: BorderRadius.circular(16),
            ),
            alignment: Alignment.center,
            child: Column(
              children: [
                const Icon(Icons.monitor_weight_outlined, size: 40, color: Colors.grey),
                const SizedBox(height: 8),
                const Text('No measurement history yet'),
                const SizedBox(height: 4),
                TextButton(
                  onPressed: () => _showLogMeasurementForm(profile),
                  child: const Text('Log your first measurement'),
                ),
              ],
            ),
          )
        else
          ...records.map((rec) {
            return Card(
              elevation: 0,
              margin: const EdgeInsets.only(bottom: 8),
              shape: RoundedCornerShape(12),
              color: theme.colorScheme.surface,
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: AppColors.flameOrange.withOpacity(0.15),
                  child: Text('W${rec.weekNumber}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: AppColors.flameOrange)),
                ),
                title: Text('${rec.weightKg.toStringAsFixed(1)} kg • ${rec.heightCm.toStringAsFixed(0)} cm', style: const TextStyle(fontWeight: FontWeight.bold)),
                subtitle: Text('Chest: ${rec.chestCm}cm • Arms: ${rec.armCm}cm • Waist: ${rec.waistCm}cm'),
                trailing: IconButton(
                  icon: const Icon(Icons.delete_outline, size: 20, color: Colors.grey),
                  onPressed: () {
                    ref.read(weeklyProgressRepositoryProvider).deleteRecord(rec.id);
                  },
                ),
              ),
            );
          }),
        const SizedBox(height: 24),
      ],
    );
  }

  Widget _buildMetricTile(String label, String value, double? prev, double current) {
    String diff = '';
    Color diffColor = Colors.grey;
    if (prev != null) {
      final delta = current - prev;
      if (delta > 0) {
        diff = '+${delta.toStringAsFixed(1)}';
        diffColor = AppColors.successGreen;
      } else if (delta < 0) {
        diff = delta.toStringAsFixed(1);
        diffColor = AppColors.flameOrange;
      } else {
        diff = '0.0';
      }
    }

    return Column(
      children: [
        Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
        const SizedBox(height: 2),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
        if (diff.isNotEmpty)
          Text(diff, style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: diffColor)),
      ],
    );
  }
}
