import 'package:flutter/material.dart';

/// Centralized Material 3 Typography definitions matching Type.kt
class AppTypography {
  AppTypography._();

  static const TextTheme textTheme = TextTheme(
    headlineLarge: TextStyle(
      fontSize: 32,
      fontWeight: FontWeight.w900,
      letterSpacing: 1.0,
      height: 1.25,
    ),
    headlineMedium: TextStyle(
      fontSize: 28,
      fontWeight: FontWeight.w800,
      letterSpacing: 0.5,
      height: 1.28,
    ),
    headlineSmall: TextStyle(
      fontSize: 24,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.0,
      height: 1.33,
    ),
    titleLarge: TextStyle(
      fontSize: 20,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.15,
      height: 1.4,
    ),
    titleMedium: TextStyle(
      fontSize: 16,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.15,
      height: 1.5,
    ),
    titleSmall: TextStyle(
      fontSize: 14,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.1,
      height: 1.43,
    ),
    bodyLarge: TextStyle(
      fontSize: 16,
      fontWeight: FontWeight.normal,
      letterSpacing: 0.5,
      height: 1.5,
    ),
    bodyMedium: TextStyle(
      fontSize: 14,
      fontWeight: FontWeight.normal,
      letterSpacing: 0.25,
      height: 1.43,
    ),
    bodySmall: TextStyle(
      fontSize: 12,
      fontWeight: FontWeight.normal,
      letterSpacing: 0.4,
      height: 1.33,
    ),
    labelLarge: TextStyle(
      fontSize: 14,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.1,
      height: 1.43,
    ),
    labelMedium: TextStyle(
      fontSize: 12,
      fontWeight: FontWeight.w600,
      letterSpacing: 0.5,
      height: 1.33,
    ),
    labelSmall: TextStyle(
      fontSize: 11,
      fontWeight: FontWeight.w700,
      letterSpacing: 0.8,
      height: 1.45,
    ),
  );
}
