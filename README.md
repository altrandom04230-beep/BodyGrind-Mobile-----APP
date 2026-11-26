# Home Workout: Android Fitness & Workout Tracking Application

A full-featured, offline-first Android home fitness application built natively in **Kotlin** and **Jetpack Compose** with Material 3. Inspired by the classic "Home Workout – No Equipment" application, it provides guided home workouts, custom workout plan creation, animated exercise demonstrations, active interval timers, gamification achievements, and local-first Room database tracking.

---

## 🌟 Key Features

### 1. Home Dashboard & Daily Habit Loop
- **Daily Recommendations**: Automatically suggests targeted routines based on your fitness goals and workout history.
- **Quick-Start**: Immediate 1-tap start for quick exercise sessions.
- **Workout Streaks & Activity Summary**: Real-time streak tracking with fire badge indicators.
- **Challenges**: Multi-day progressive programs (7-Day Total Fitness Kickstart, 30-Day Home Transformation).
- **Gamification Level Bar**: Live XP points, user level progression (Rookie to Grandmaster Athlete), and tier progression.

### 2. Custom Workout Plan Builder (Personalized Programs)
- **Interactive Builder**: Create your own customized workout plans.
- **Exercise Selector**: Pick from the comprehensive exercise library with category and muscle group tags.
- **Custom Parameters**: Set repetitions (e.g., 14 reps) or timed duration (e.g., 30s) for each exercise.
- **Reordering & Rest Times**: Rearrange exercises in any order using simple up/down controls, configure custom rest intervals (15s, 20s, 30s), and save directly into the Room database.
- **Immediate Execution**: Launch and perform custom workouts with the same guided session engine as predefined plans.

### 3. Animated Exercise Visualizer & Demonstrations
- **Real Animated Demonstration Canvas**: No missing media or static placeholders. Uses custom Compose Canvas animations rendering dynamic, fluid humanoid athletic poses for:
  - Push-ups, Squats, Jumping Jacks, Plank holds, Abdominal Crunches, Alternating Lunges, High Knees, Mountain Climbers, Wall Sits, Arm Circles, Bicycle Crunches, Cobra Stretches, and Leg Raises.
- **Posture Guidance & Tips**: Clear instructions and common mistake warnings for each movement.

### 4. Active Workout Session & Rest Timers
- **Pre-Workout Countdown**: 5-second preparation count with audio and haptic cues.
- **Exercise Execution Engine**:
  - Timed exercises: High-contrast circular countdown ring with pause/resume, skip, restart, and +15s controls.
  - Repetition exercises: Tap-to-count cards and bold "DONE" confirmation.
  - Screen wake-lock: Prevents display timeout during active training.
- **Rest Interval Timer**:
  - Dedicated countdown between sets.
  - Quick "+20s" addition button and "Skip Rest" button.
  - Preview of next exercise with animated demo and target muscles.
- **Audio & Haptic Feedback**: Native `ToneGenerator` beeps on 3-2-1 countdowns and rest completion chimes, combined with device vibration.
- **Session Summary Screen**: Celebration fanfare, duration, estimated calories burned, exercises finished, XP awarded, and badge unlock alerts.

### 5. Gamification, Badges & Weekly League
- **Milestone Badges**:
  - *First Step*: Complete first workout.
  - *3-Day Spark* & *Iron Will*: 3-day and 7-day workout streaks.
  - *Core of Steel*: Complete 3 dedicated Abs workouts.
  - *Master Architect*: Build and complete a custom workout routine.
  - *Calorie Torch*: Burn over 500 estimated calories.
  - *Dawn Patrol*: Finish a workout before 9:00 AM.
- **Weekly League Division**: Ranks users into Bronze, Silver, Gold, Platinum, and Diamond divisions based on weekly training XP.

### 6. Calendar & Workout Schedules
- **Interactive Calendar**: View monthly activity consistency with highlighted workout days.
- **Recurring Schedule**: Set weekly workout days (e.g., Mon, Wed, Fri at 8:00 AM) with toggle switches.
- **Daily History Log**: Inspect all sessions completed on any selected calendar date.

### 7. Body Measurements & BMI Tracker
- **BMI Calculation**: Calculates BMI using weight and height with classification (Underweight, Normal weight, Overweight, Obese).
- **Educational Disclaimer**: Clearly states BMI is a general screening metric, not a medical diagnosis.
- **Weight Trend Chart**: Native canvas line chart illustrating weight progress over time.

### 8. User Profile & Settings
- **Audio & Vibration Toggles**: Enable or disable beeps, chimes, and haptics.
- **Daily Reminders**: Notification scheduler with an immediate "Send Test Notification" action button.
- **Units & Theme**: Toggle between Metric (kg/cm) and Imperial (lb/ft), and switch between dark and light modes.
- **Local-First & Offline**: 100% private data stored on-device in Room SQLite.

### 9. 7-Step Onboarding Experience
- Guides new users through Goal selection, Fitness level, Preferred duration, Workout days, Body measurements, Reminder preference, and a Safety Notice.

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.2.10
- **UI Toolkit**: Jetpack Compose with Material 3 (M3)
- **Local Database**: Room Database 2.7.0 (with KSP)
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern
- **Concurrency**: Kotlin Coroutines & Flow
- **Audio & Haptics**: Android ToneGenerator + VibratorManager
- **Notifications**: Android NotificationManager & NotificationChannel

---

## 🚀 Building & Running

1. Open the project folder in **Android Studio Ladybug (or newer)**.
2. Ensure **JDK 17 or JDK 21** is configured in Gradle Settings.
3. Gradle will automatically sync dependencies via `gradle/libs.versions.toml`.
4. Run on an Android Device or Emulator running **Android 8.0 (API 26) or higher**.
5. To execute unit tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
