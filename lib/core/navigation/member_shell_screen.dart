import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../constants/app_colors.dart';
import '../../data/providers/app_providers.dart';

import '../../features/ai_coach/presentation/floating_ai_button.dart';

/// Member Navigation Shell with top app bar, theme switcher, 5-tab bottom navigation, and Floating AI Coach
class MemberShellScreen extends ConsumerWidget {
  final StatefulNavigationShell navigationShell;

  const MemberShellScreen({
    super.key,
    required this.navigationShell,
  });

  void _onTap(int index) {
    navigationShell.goBranch(
      index,
      initialLocation: index == navigationShell.currentIndex,
    );
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final themeMode = ref.watch(themeModeProvider);
    final isDark = theme.brightness == Brightness.dark;
    final profile = ref.watch(userProfileStreamProvider).valueOrNull ??
        ref.watch(workoutRepositoryProvider).currentProfile;

    return Scaffold(
      appBar: AppBar(
        backgroundColor: theme.colorScheme.surface,
        elevation: 0,
        centerTitle: true,
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: AppColors.boldRed.withOpacity(0.2),
                shape: BoxShape.circle,
              ),
              child: const Icon(
                Icons.fitness_center,
                size: 18,
                color: AppColors.boldRed,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'BODYGRIND',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: 1.0,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: isDark ? 'Switch to Light Theme' : 'Switch to Dark Theme',
            icon: Icon(
              isDark ? Icons.light_mode : Icons.dark_mode,
              color: isDark ? const Color(0xFFFFD54F) : const Color(0xFFF59E0B),
            ),
            onPressed: () {
              final newMode = themeMode == 'DARK' ? 'LIGHT' : 'DARK';
              ref.read(themeModeProvider.notifier).state = newMode;
            },
          ),
        ],
      ),
      body: Stack(
        children: [
          navigationShell,
          if (profile.aiCoachEnabled) const FloatingAiButton(),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: _onTap,
        backgroundColor: theme.colorScheme.surface,
        indicatorColor: AppColors.boldRed.withOpacity(0.18),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home, color: AppColors.boldRed),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.fitness_center_outlined),
            selectedIcon: Icon(Icons.fitness_center, color: AppColors.boldRed),
            label: 'Workouts',
          ),
          NavigationDestination(
            icon: Icon(Icons.calendar_month_outlined),
            selectedIcon: Icon(Icons.calendar_month, color: AppColors.boldRed),
            label: 'Calendar',
          ),
          NavigationDestination(
            icon: Icon(Icons.restaurant_outlined),
            selectedIcon: Icon(Icons.restaurant, color: AppColors.boldRed),
            label: 'Meals',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person, color: AppColors.boldRed),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}
