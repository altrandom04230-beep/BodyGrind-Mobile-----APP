import 'package:flutter/material.dart';
import '../constants/app_colors.dart';
import 'app_typography.dart';

/// Centralized Material 3 ThemeData matching com.example.ui.theme.Theme.kt
class AppTheme {
  AppTheme._();

  /// Primary Dark Theme (Default for BODYGRIND brand)
  static ThemeData get darkTheme {
    const colorScheme = ColorScheme(
      brightness: Brightness.dark,
      primary: AppColors.boldRed,
      onPrimary: AppColors.textWhite,
      primaryContainer: Color(0x38E50914),
      onPrimaryContainer: AppColors.boldRedLight,
      secondary: AppColors.boldRedLight,
      onSecondary: AppColors.textWhite,
      secondaryContainer: AppColors.darkCharcoal,
      onSecondaryContainer: AppColors.boldRedLight,
      tertiary: AppColors.boldRedDark,
      onTertiary: AppColors.textWhite,
      error: AppColors.boldRed,
      onError: AppColors.textWhite,
      surface: AppColors.darkCharcoal,
      onSurface: AppColors.textWhite,
      surfaceContainerHighest: AppColors.charcoalBorder,
      surfaceContainer: AppColors.charcoalElevated,
      outline: AppColors.charcoalBorder,
      outlineVariant: Color(0xFF262626),
      shadow: Colors.black,
      scrim: Colors.black,
      inverseSurface: AppColors.textWhite,
      onInverseSurface: AppColors.textPrimaryLight,
      inversePrimary: AppColors.boldRed,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.pureBlack,
      canvasColor: AppColors.pureBlack,
      cardColor: AppColors.darkCharcoal,
      dividerColor: AppColors.charcoalBorder,
      textTheme: AppTypography.textTheme.apply(
        bodyColor: AppColors.textWhite,
        displayColor: AppColors.textWhite,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.pureBlack,
        foregroundColor: AppColors.textWhite,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardTheme(
        color: AppColors.darkCharcoal,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.charcoalBorder, width: 1),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.boldRed,
          foregroundColor: AppColors.textWhite,
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.darkCharcoal,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.controlBorderDark),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.controlBorderDark),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.boldRed, width: 2),
        ),
        errorBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.boldRed),
        ),
        labelStyle: const TextStyle(color: AppColors.textLightGray),
        hintStyle: const TextStyle(color: AppColors.textMutedGray),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.pureBlack,
        selectedItemColor: AppColors.boldRed,
        unselectedItemColor: AppColors.textMutedGray,
        type: BottomNavigationBarType.fixed,
        elevation: 8,
      ),
    );
  }

  /// Light Theme option matching lightColorScheme in Theme.kt
  static ThemeData get lightTheme {
    const colorScheme = ColorScheme(
      brightness: Brightness.light,
      primary: AppColors.boldRed,
      onPrimary: AppColors.textWhite,
      primaryContainer: Color(0x1FE50914),
      onPrimaryContainer: AppColors.boldRedDark,
      secondary: AppColors.boldRed,
      onSecondary: AppColors.textWhite,
      secondaryContainer: Color(0xFFF3F4F6),
      onSecondaryContainer: Color(0xFF1F2937),
      tertiary: AppColors.boldRedDark,
      onTertiary: AppColors.textWhite,
      error: AppColors.boldRed,
      onError: AppColors.textWhite,
      surface: AppColors.lightSurface,
      onSurface: AppColors.textPrimaryLight,
      surfaceContainerHighest: Color(0xFFD1D5DB),
      surfaceContainer: Color(0xFFF3F4F6),
      outline: AppColors.lightBorder,
      outlineVariant: Color(0xFFE5E7EB),
      shadow: Colors.black12,
      scrim: Colors.black,
      inverseSurface: AppColors.textPrimaryLight,
      onInverseSurface: AppColors.textWhite,
      inversePrimary: AppColors.boldRedLight,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: AppColors.lightBg,
      canvasColor: AppColors.lightBg,
      cardColor: AppColors.lightSurface,
      dividerColor: AppColors.lightBorder,
      textTheme: AppTypography.textTheme.apply(
        bodyColor: AppColors.textPrimaryLight,
        displayColor: AppColors.textPrimaryLight,
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.lightBg,
        foregroundColor: AppColors.textPrimaryLight,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardTheme(
        color: AppColors.lightSurface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.lightBorder, width: 1),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.boldRed,
          foregroundColor: AppColors.textWhite,
          minimumSize: const Size(48, 48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          textStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: AppColors.lightSurface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.controlBorderLight),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.controlBorderLight),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: const BorderSide(color: AppColors.boldRed, width: 2),
        ),
        labelStyle: const TextStyle(color: AppColors.textSecondaryLight),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: AppColors.lightSurface,
        selectedItemColor: AppColors.boldRed,
        unselectedItemColor: AppColors.textSecondaryLight,
        type: BottomNavigationBarType.fixed,
        elevation: 8,
      ),
    );
  }
}
