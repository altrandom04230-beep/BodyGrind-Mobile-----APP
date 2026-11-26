import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_core/firebase_core.dart';
import 'core/theme/app_theme.dart';
import 'core/navigation/app_router.dart';
import 'data/providers/app_providers.dart';

import 'features/member/presentation/meals/services/meal_reminder_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  try {
    await Firebase.initializeApp();
  } catch (_) {
    // Graceful fallback if firebase options are not pre-configured
  }

  try {
    await MealReminderService().initialize();
  } catch (_) {}

  runApp(
    const ProviderScope(
      child: BodyGrindApp(),
    ),
  );
}

class BodyGrindApp extends ConsumerWidget {
  const BodyGrindApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    final themeModeStr = ref.watch(themeModeProvider);

    final themeMode = switch (themeModeStr) {
      'DARK' => ThemeMode.dark,
      'SYSTEM' => ThemeMode.system,
      _ => ThemeMode.light,
    };

    return MaterialApp.router(
      title: 'BodyGrind',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: themeMode,
      routerConfig: router,
    );
  }
}
