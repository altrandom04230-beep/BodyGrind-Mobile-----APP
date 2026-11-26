import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../constants/app_colors.dart';
import '../../data/providers/app_providers.dart';

/// Trainer Navigation Shell with top app bar, logout action, and 4-tab bottom navigation
class TrainerShellScreen extends ConsumerWidget {
  final StatefulNavigationShell navigationShell;

  const TrainerShellScreen({
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
                Icons.sports,
                size: 18,
                color: AppColors.boldRed,
              ),
            ),
            const SizedBox(width: 8),
            Text(
              'BODYGRIND TRAINER',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w900,
                letterSpacing: 1.0,
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Log Out',
            icon: const Icon(Icons.logout, color: AppColors.boldRed),
            onPressed: () {
              ref.read(selectedRoleProvider.notifier).state = null;
              context.go('/');
            },
          ),
        ],
      ),
      body: navigationShell,
      bottomNavigationBar: NavigationBar(
        selectedIndex: navigationShell.currentIndex,
        onDestinationSelected: _onTap,
        backgroundColor: theme.colorScheme.surface,
        indicatorColor: AppColors.boldRed.withOpacity(0.18),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard, color: AppColors.boldRed),
            label: 'Dashboard',
          ),
          NavigationDestination(
            icon: Icon(Icons.groups_outlined),
            selectedIcon: Icon(Icons.groups, color: AppColors.boldRed),
            label: 'Members',
          ),
          NavigationDestination(
            icon: Icon(Icons.analytics_outlined),
            selectedIcon: Icon(Icons.analytics, color: AppColors.boldRed),
            label: 'Progress',
          ),
          NavigationDestination(
            icon: Icon(Icons.rate_review_outlined),
            selectedIcon: Icon(Icons.rate_review, color: AppColors.boldRed),
            label: 'Feedback',
          ),
        ],
      ),
    );
  }
}
