import 'package:flutter/material.dart';

/// Modern Black and Red Color Theme (BODYGRIND Brand Identity)
/// Ported exactly from com.example.ui.theme.Color.kt
class AppColors {
  AppColors._();

  // Dark Theme Base Tokens
  static const Color pureBlack = Color(0xFF000000);
  static const Color darkCharcoal = Color(0xFF1A1A1A);
  static const Color charcoalElevated = Color(0xFF262626);
  static const Color charcoalBorder = Color(0xFF333333);
  static const Color darkGrayButton = Color(0xFF2A2A2A);

  // Brand Accent: Bold Red (#E50914)
  static const Color boldRed = Color(0xFFE50914);
  static const Color boldRedDark = Color(0xFFB80710);
  static const Color boldRedLight = Color(0xFFFF2E3B);
  static const Color boldRedGlow = Color(0x33E50914);

  // Text Tokens (Dark)
  static const Color textWhite = Color(0xFFFFFFFF);
  static const Color textLightGray = Color(0xFFB3B3B3);
  static const Color textMutedGray = Color(0xFF757575);

  // Aliases for compatibility with legacy names
  static const Color flameOrange = boldRed;
  static const Color flameOrangeLight = boldRedLight;
  static const Color flameOrangeDark = boldRedDark;
  static const Color electricCyan = boldRed;
  static const Color electricCyanDark = boldRedDark;

  // Light Theme Tokens
  static const Color lightBg = Color(0xFFF8F9FA);
  static const Color lightSurface = Color(0xFFFFFFFF);
  static const Color lightSurfaceElevated = Color(0xFFF3F4F6);
  static const Color lightBorder = Color(0xFFE5E7EB);
  static const Color textPrimaryLight = Color(0xFF111827);
  static const Color textSecondaryLight = Color(0xFF4B5563);
  static const Color textTertiaryLight = Color(0xFF5A6474);

  // Interactive Control Borders (WCAG 1.4.11 >= 3:1)
  static const Color controlBorderDark = charcoalBorder;
  static const Color controlBorderLight = Color(0xFF6B7280);

  // Status & Utility Colors
  static const Color successGreen = Color(0xFF22C55E);
  static const Color dangerRed = boldRed;
  static const Color warningAmber = Color(0xFFF59E0B);

  // Chart Series Colors
  static const Color chartArmGreenDark = Color(0xFF10B981);
  static const Color chartArmGreenLight = Color(0xFF059669);
  static const Color chartWaistAmberDark = Color(0xFFF59E0B);
  static const Color chartWaistAmberLight = Color(0xFFB45309);
  static const Color chartChestBlueDark = Color(0xFF3B82F6);
  static const Color chartChestBlueLight = Color(0xFF1D4ED8);
  static const Color chartPurpleDark = Color(0xFFA855F7);
  static const Color chartPurpleLight = Color(0xFF7E22CE);
  static const Color waterBlueDark = Color(0xFF38BDF8);
  static const Color waterBlueLight = Color(0xFF0284C7);
}
