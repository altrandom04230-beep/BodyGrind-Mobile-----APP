import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../data/providers/app_providers.dart';

class LookAndHomeSettingsScreen extends ConsumerWidget {
  const LookAndHomeSettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final currentThemeMode = ref.watch(themeModeProvider);
    final profile = ref.watch(userProfileStreamProvider).valueOrNull ??
        ref.watch(workoutRepositoryProvider).currentProfile;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Look & Home Settings', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // Theme Mode Card
            Card(
              elevation: 0,
              shape: RoundedCornerShape(16),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.palette_outlined, color: theme.colorScheme.primary),
                        const SizedBox(width: 8),
                        Text(
                          'Theme Mode',
                          style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      child: SegmentedButton<String>(
                        segments: const [
                          ButtonSegment(value: 'DARK', label: Text('Dark')),
                          ButtonSegment(value: 'LIGHT', label: Text('Light')),
                          ButtonSegment(value: 'SYSTEM', label: Text('System')),
                        ],
                        selected: {currentThemeMode},
                        onSelectionChanged: (val) {
                          final newMode = val.first;
                          ref.read(themeModeProvider.notifier).state = newMode;
                          ref.read(workoutRepositoryProvider).updateProfile(
                                profile.copyWith(darkThemeMode: newMode),
                              );
                        },
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),
            // Gym Check-in Pass Card
            Card(
              elevation: 0,
              shape: RoundedCornerShape(16),
              color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.qr_code_scanner, color: theme.colorScheme.primary),
                        const SizedBox(width: 12),
                        Text(
                          'Gym Check-in Pass On Home',
                          style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                    Switch(
                      value: profile.gymCheckinEnabled,
                      onChanged: (val) {
                        ref.read(workoutRepositoryProvider).updateProfile(
                              profile.copyWith(gymCheckinEnabled: val),
                            );
                      },
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
