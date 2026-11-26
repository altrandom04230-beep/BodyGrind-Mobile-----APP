import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../data/models/app_role.dart';
import '../../data/models/user_profile.dart';
import '../../data/providers/app_providers.dart';
import '../../features/auth/presentation/mock_login_screen.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/onboarding_screen.dart';
import '../../features/member/presentation/home/home_screen.dart';
import '../../features/member/presentation/workouts/workouts_screen.dart';
import '../../features/member/presentation/workouts/workout_plan_detail_screen.dart';
import '../../features/member/presentation/active_workout/active_workout_screen.dart';
import '../../features/member/presentation/custom_workout/custom_workout_builder_screen.dart';
import '../../features/member/presentation/calendar/calendar_screen.dart';
import '../../features/member/presentation/meals/smart_meals_screen.dart';
import '../../features/member/presentation/profile/profile_screen.dart';
import '../../features/member/presentation/settings/look_and_home_settings_screen.dart';
import '../../features/member/presentation/bmi/bmi_calculator_screen.dart';
import '../../features/trainer/presentation/trainer_dashboard_screen.dart';
import '../../features/trainer/presentation/trainer_members_screen.dart';
import '../../features/trainer/presentation/trainer_progress_screen.dart';
import '../../features/trainer/presentation/trainer_feedback_screen.dart';
import 'member_shell_screen.dart';
import 'trainer_shell_screen.dart';

final _rootNavigatorKey = GlobalKey<NavigatorState>(debugLabel: 'root');

/// Riverpod provider for GoRouter instance with reactive role-based redirection
final routerProvider = Provider<GoRouter>((ref) {
  final selectedRole = ref.watch(selectedRoleProvider);
  final authUser = ref.watch(authStateProvider).valueOrNull;
  final profile = ref.watch(userProfileStreamProvider).valueOrNull ??
      ref.watch(workoutRepositoryProvider).currentProfile;

  return GoRouter(
    navigatorKey: _rootNavigatorKey,
    initialLocation: '/',
    redirect: (context, state) {
      final loc = state.uri.path;

      // 1. Unselected Role -> MockLoginScreen
      if (selectedRole == null) {
        return loc == '/' ? null : '/';
      }

      // 2. Trainer Role Routing
      if (selectedRole == AppRole.trainer) {
        if (loc.startsWith('/member') || loc.startsWith('/auth') || loc == '/') {
          return '/trainer/dashboard';
        }
        return null;
      }

      // 3. Member Role Routing
      if (selectedRole == AppRole.member) {
        // Must authenticate via Firebase Login first
        if (authUser == null) {
          return loc == '/auth/login' ? null : '/auth/login';
        }

        // Onboarding required if not yet completed
        if (!profile.onboardingCompleted) {
          return loc == '/member/onboarding' ? null : '/member/onboarding';
        }

        // Redirect away from login / mock selection to home
        if (loc == '/' || loc == '/auth/login' || loc.startsWith('/trainer')) {
          return '/member/home';
        }
      }

      return null;
    },
    routes: [
      // Mock Login / Role Selection Route
      GoRoute(
        path: '/',
        builder: (context, state) => MockLoginScreen(
          onLoginWithRole: (role) {
            ref.read(selectedRoleProvider.notifier).state = role;
          },
        ),
      ),

      // Firebase Member Login Route
      GoRoute(
        path: '/auth/login',
        builder: (context, state) => LoginScreen(
          onLoginSuccess: () {
            // State will trigger redirect
          },
        ),
      ),

      // Member Onboarding Route
      GoRoute(
        path: '/member/onboarding',
        builder: (context, state) => OnboardingScreen(
          initialProfile: profile,
          onCompleteOnboarding: (updated) {
            ref.read(workoutRepositoryProvider).updateProfile(updated);
            context.go('/member/home');
          },
        ),
      ),

      // Member Sub-Routes (Pushed on top of tabs)
      GoRoute(
        path: '/member/workouts/detail/:id',
        builder: (context, state) => WorkoutPlanDetailScreen(
          planId: state.pathParameters['id']!,
        ),
      ),
      GoRoute(
        path: '/member/workouts/active/:id',
        builder: (context, state) => ActiveWorkoutScreen(
          planId: state.pathParameters['id']!,
        ),
      ),
      GoRoute(
        path: '/member/workouts/builder',
        builder: (context, state) => const CustomWorkoutBuilderScreen(),
      ),
      GoRoute(
        path: '/member/bmi',
        builder: (context, state) => const BmiCalculatorScreen(),
      ),
      GoRoute(
        path: '/member/settings',
        builder: (context, state) => const LookAndHomeSettingsScreen(),
      ),

      // Member Shell with 5 Bottom Navigation Branches
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return MemberShellScreen(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/member/home',
                builder: (context, state) => const HomeScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/member/workouts',
                builder: (context, state) => const WorkoutsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/member/calendar',
                builder: (context, state) => const CalendarScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/member/meals',
                builder: (context, state) => const SmartMealsScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/member/profile',
                builder: (context, state) => const ProfileScreen(),
              ),
            ],
          ),
        ],
      ),

      // Trainer Shell with 4 Bottom Navigation Branches
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          return TrainerShellScreen(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/trainer/dashboard',
                builder: (context, state) => const TrainerDashboardScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/trainer/members',
                builder: (context, state) => const TrainerMembersScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/trainer/progress',
                builder: (context, state) => const TrainerProgressScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/trainer/feedback',
                builder: (context, state) => const TrainerFeedbackScreen(),
              ),
            ],
          ),
        ],
      ),
    ],
  );
});
