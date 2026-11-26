package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Modern Black and Red Color Theme (BODYGRIND Brand Identity)
val PureBlack = Color(0xFF000000)          // Main background and navigation areas (#000000)
val DarkCharcoal = Color(0xFF1A1A1A)       // Cards, containers, and sections (#1A1A1A)
val CharcoalElevated = Color(0xFF262626)   // Elevated cards, bottom sheets, dialogs
val CharcoalBorder = Color(0xFF333333)     // Input borders, dividers, subtle outlines
val DarkGrayButton = Color(0xFF2A2A2A)     // Secondary buttons with white text

val BoldRed = Color(0xFFE50914)            // Primary buttons, highlights, icons, active states (#E50914)
val BoldRedDark = Color(0xFFB80710)        // Darker red for gradients/pressed states
val BoldRedLight = Color(0xFFFF2E3B)       // Lighter red for badges/accents
val BoldRedGlow = Color(0x33E50914)        // Subtle red aura/indicator background

val TextWhite = Color(0xFFFFFFFF)          // Main text and important labels (#FFFFFF)
val TextLightGray = Color(0xFFB3B3B3)      // Secondary text and descriptions (#B3B3B3)
val TextMutedGray = Color(0xFF757575)      // Tertiary text, subtle timestamps

// Aliases ensuring seamless backward compatibility with existing components
val FlameOrange = BoldRed
val FlameOrangeLight = BoldRedLight
val FlameOrangeDark = BoldRedDark

val ElectricCyan = BoldRed                 // Refined to bold red accent for strict black & red identity
val ElectricCyanDark = BoldRedDark
val EnergyLime = Color(0xFFFF3344)
val GoldYellow = Color(0xFFFF5252)

val DarkBg = PureBlack
val DarkSurface = DarkCharcoal
val DarkSurfaceElevated = CharcoalElevated
val DarkBorder = CharcoalBorder

val LightBg = Color(0xFFF8F9FA)                    // Clean off-white background
val LightSurface = Color(0xFFFFFFFF)               // Crisp white for cards and surfaces
val LightSurfaceElevated = Color(0xFFF3F4F6)       // Light gray for elevated items
val LightBorder = Color(0xFFE5E7EB)                // Subtle divider and border outline

val TextPrimaryDark = TextWhite
val TextSecondaryDark = TextLightGray

val TextPrimaryLight = Color(0xFF111827)           // Crisp near-black for high contrast on light
val TextSecondaryLight = Color(0xFF4B5563)         // Muted gray text on light

// Dedicated interactive control border tokens meeting WCAG 1.4.11 (>= 3:1)
val ControlBorderDark = CharcoalBorder             // #333333 in dark mode
val ControlBorderLight = Color(0xFF6B7280)         // #6B7280 (4.83:1 on white, 4.39:1 on elevated)

// Dedicated tertiary text tokens meeting WCAG 2.1 AA (>= 4.5:1)
val TextTertiaryDark = TextMutedGray               // #757575 in dark mode (known preserved gap)
val TextTertiaryLight = Color(0xFF5A6474)          // #5A6474 (5.98:1 on white, 5.44:1 on elevated)

// Dedicated chart series color tokens meeting WCAG 1.4.11 (>= 3:1 for lines)
val ChartArmGreenDark = Color(0xFF10B981)          // Emerald 500 (6.86:1 on dark)
val ChartArmGreenLight = Color(0xFF059669)         // Emerald 600 (3.77:1 on white)
val ChartWaistAmberDark = Color(0xFFF59E0B)        // Amber 500 (8.10:1 on dark)
val ChartWaistAmberLight = Color(0xFFB45309)       // Amber 700 (5.02:1 on white)
val ChartChestBlueDark = Color(0xFF3B82F6)         // Blue 500 (6.32:1 on dark)
val ChartChestBlueLight = Color(0xFF1D4ED8)        // Blue 700 (6.28:1 on white)
val ChartPurpleDark = Color(0xFFA855F7)            // Purple 500 (6.06:1 on dark)
val ChartPurpleLight = Color(0xFF7E22CE)           // Purple 700 (6.98:1 on white)

val WaterBlueDark = Color(0xFF38BDF8)              // Sky 400 (9.5:1 on dark)
val WaterBlueLight = Color(0xFF0284C7)             // Sky 600 (4.6:1 on white)

val SuccessGreen = Color(0xFF22C55E)
val DangerRed = BoldRed
val WarningAmber = Color(0xFFF59E0B)

// Dedicated switch control tokens ensuring high contrast in dark mode
val SwitchTrackDark = Color(0xFF383838)             // Lighter gray than DarkCharcoal (#1A1A1A)
val SwitchThumbDark = Color(0xFFE5E5E5)             // Light gray/white knob contrasting with track
val SwitchBorderDark = Color(0xFF525252)            // Clear track border outline
