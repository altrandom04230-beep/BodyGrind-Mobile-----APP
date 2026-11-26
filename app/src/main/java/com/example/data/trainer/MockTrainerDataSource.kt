package com.example.data.trainer

import com.example.utils.ManilaDateTime
import java.time.LocalDate

object MockTrainerDataSource {

    private fun rollingDateTimeUtcMs(daysAgo: Long, hour: Int, minute: Int): Long {
        val date = ManilaDateTime.rollingPastManilaDate(daysAgo)
        return ManilaDateTime.manilaDateTimeToUtcMs(date, hour, minute)
    }

    private fun rollingJoinDateString(daysAgo: Long): String {
        val date = ManilaDateTime.rollingPastManilaDate(daysAgo)
        return ManilaDateTime.formatDate(date)
    }

    private fun rollingJoinDateUtcMs(daysAgo: Long): Long {
        val date = ManilaDateTime.rollingPastManilaDate(daysAgo)
        return ManilaDateTime.manilaDateToStartOfDayUtcMs(date)
    }

    val sampleTrainer = TrainerInfo(
        id = "trainer_01",
        name = "Coach Cobie",
        title = "Head Strength & Conditioning Coach",
        email = "cobie.trainer@bodygrind.local",
        specialization = "Hypertrophy, Mobility & Body Recomposition"
    )

    val sampleMembers: List<AssignedMember>
        get() {
            val nowMs = ManilaDateTime.nowUtcMillis()

            // Member 1: Marcus Reyes
            val m1Workout1Ms = rollingDateTimeUtcMs(0, 7, 15)
            val m1Workout2Ms = rollingDateTimeUtcMs(1, 18, 40)
            val m1Workout3Ms = rollingDateTimeUtcMs(2, 10, 30)
            val m1Workout4Ms = rollingDateTimeUtcMs(4, 9, 0)

            val m1Att1Ms = rollingDateTimeUtcMs(0, 7, 2)
            val m1Att2Ms = rollingDateTimeUtcMs(1, 18, 30)
            val m1Att3Ms = rollingDateTimeUtcMs(2, 7, 10)
            val m1Att4Ms = rollingDateTimeUtcMs(4, 6, 55)

            // Member 2: Elena Santos
            val m2Workout1Ms = rollingDateTimeUtcMs(0, 8, 20)
            val m2Workout2Ms = rollingDateTimeUtcMs(2, 8, 30)
            val m2Workout3Ms = rollingDateTimeUtcMs(3, 7, 45)
            val m2Workout4Ms = rollingDateTimeUtcMs(5, 8, 0)

            val m2Att1Ms = rollingDateTimeUtcMs(0, 8, 5)
            val m2Att2Ms = rollingDateTimeUtcMs(2, 8, 12)
            val m2Att3Ms = rollingDateTimeUtcMs(3, 7, 58)
            val m2Att4Ms = rollingDateTimeUtcMs(5, 8, 15)

            // Member 3: Daniel Lim
            val m3Workout1Ms = rollingDateTimeUtcMs(1, 19, 0)
            val m3Workout2Ms = rollingDateTimeUtcMs(4, 18, 30)
            val m3Workout3Ms = rollingDateTimeUtcMs(5, 19, 15)

            val m3Att1Ms = rollingDateTimeUtcMs(1, 18, 48)
            val m3Att2Ms = rollingDateTimeUtcMs(4, 19, 5)
            val m3Att3Ms = rollingDateTimeUtcMs(8, 18, 52)

            // Member 4: Sofia Navarro
            val m4Workout1Ms = rollingDateTimeUtcMs(0, 6, 30)
            val m4Workout2Ms = rollingDateTimeUtcMs(1, 6, 35)
            val m4Workout3Ms = rollingDateTimeUtcMs(2, 6, 45)

            val m4Att1Ms = rollingDateTimeUtcMs(0, 6, 18)
            val m4Att2Ms = rollingDateTimeUtcMs(1, 6, 22)
            val m4Att3Ms = rollingDateTimeUtcMs(2, 6, 25)

            // Member 5: Carlos Mendoza
            val m5Workout1Ms = rollingDateTimeUtcMs(3, 17, 30)
            val m5Workout2Ms = rollingDateTimeUtcMs(7, 18, 0)

            val m5Att1Ms = rollingDateTimeUtcMs(3, 17, 40)
            val m5Att2Ms = rollingDateTimeUtcMs(7, 17, 50)

            return listOf(
                AssignedMember(
                    id = "mem_01",
                    name = "Marcus Reyes",
                    age = 27,
                    gender = "Male",
                    email = "marcus.reyes@bodygrind.local",
                    joinDate = rollingJoinDateString(100),
                    joinDateUtcMs = rollingJoinDateUtcMs(100),
                    status = MemberStatus.ACTIVE,
                    fitnessGoal = "Build Lean Muscle & Upper Body Strength",
                    fitnessLevel = "Intermediate",
                    weeklyTargetWorkouts = 5,
                    completedWorkoutsThisWeek = 4,
                    workoutCompletionRate = 88,
                    attendanceRate = 92,
                    attendanceStreakDays = 9,
                    consistencyScore = 90,
                    weeklyAdherenceDays = listOf(true, true, false, true, true, false, false),
                    heightCm = 176f,
                    startingWeightKg = 68.5f,
                    currentWeightKg = 72.1f,
                    targetWeightKg = 75.0f,
                    weightBmiHistory = listOf(
                        WeightBmiPoint("W1", 68.5f, 22.1f),
                        WeightBmiPoint("W2", 69.2f, 22.3f),
                        WeightBmiPoint("W3", 70.1f, 22.6f),
                        WeightBmiPoint("W4", 71.0f, 22.9f),
                        WeightBmiPoint("W5", 71.6f, 23.1f),
                        WeightBmiPoint("W6", 72.1f, 23.3f)
                    ),
                    workoutHistory = listOf(
                        MemberWorkoutRecord("w1", "Upper Body Hypertrophy", ManilaDateTime.formatRelativeDateTime(m1Workout1Ms), 48, 360, true, m1Workout1Ms),
                        MemberWorkoutRecord("w2", "Core & Conditioning", ManilaDateTime.formatRelativeDateTime(m1Workout2Ms), 35, 290, true, m1Workout2Ms),
                        MemberWorkoutRecord("w3", "Lower Body Power", ManilaDateTime.formatRelativeDateTime(m1Workout3Ms), 52, 410, true, m1Workout3Ms),
                        MemberWorkoutRecord("w4", "Push Strength Split", ManilaDateTime.formatRelativeDateTime(m1Workout4Ms), 45, 340, true, m1Workout4Ms)
                    ),
                    attendanceHistory = listOf(
                        MemberAttendanceRecord("a1", ManilaDateTime.formatRelativeDay(m1Att1Ms), "07:02 AM", "QR", "Present", m1Att1Ms),
                        MemberAttendanceRecord("a2", ManilaDateTime.formatRelativeDay(m1Att2Ms), "06:30 PM", "QR", "Present", m1Att2Ms),
                        MemberAttendanceRecord("a3", ManilaDateTime.formatRelativeDay(m1Att3Ms), "07:10 AM", "QR", "Present", m1Att3Ms),
                        MemberAttendanceRecord("a4", ManilaDateTime.formatRelativeDay(m1Att4Ms), "06:55 AM", "Front Desk", "Present", m1Att4Ms)
                    ),
                    exercisePerformance = listOf(
                        ExercisePerformanceRecord("Push-Up", "Reps / Set", "18 reps", "26 reps", "+8 reps", true),
                        ExercisePerformanceRecord("Bodyweight Squat", "Reps / Set", "25 reps", "35 reps", "+10 reps", true),
                        ExercisePerformanceRecord("Plank Hold", "Hold Duration", "65 sec", "95 sec", "+30 sec", true),
                        ExercisePerformanceRecord("Chair Triceps Dips", "Reps / Set", "14 reps", "20 reps", "+6 reps", true)
                    )
                ),
                AssignedMember(
                    id = "mem_02",
                    name = "Elena Santos",
                    age = 31,
                    gender = "Female",
                    email = "elena.santos@bodygrind.local",
                    joinDate = rollingJoinDateString(140),
                    joinDateUtcMs = rollingJoinDateUtcMs(140),
                    status = MemberStatus.ACTIVE,
                    fitnessGoal = "Fat Loss & Cardiovascular Endurance",
                    fitnessLevel = "Intermediate",
                    weeklyTargetWorkouts = 4,
                    completedWorkoutsThisWeek = 4,
                    workoutCompletionRate = 94,
                    attendanceRate = 95,
                    attendanceStreakDays = 14,
                    consistencyScore = 95,
                    weeklyAdherenceDays = listOf(true, false, true, true, false, true, false),
                    heightCm = 163f,
                    startingWeightKg = 71.0f,
                    currentWeightKg = 65.4f,
                    targetWeightKg = 62.0f,
                    weightBmiHistory = listOf(
                        WeightBmiPoint("W1", 71.0f, 26.7f),
                        WeightBmiPoint("W2", 69.8f, 26.3f),
                        WeightBmiPoint("W3", 68.7f, 25.9f),
                        WeightBmiPoint("W4", 67.5f, 25.4f),
                        WeightBmiPoint("W5", 66.3f, 25.0f),
                        WeightBmiPoint("W6", 65.4f, 24.6f)
                    ),
                    workoutHistory = listOf(
                        MemberWorkoutRecord("w5", "HIIT Fat Burn Circuit", ManilaDateTime.formatRelativeDateTime(m2Workout1Ms), 32, 345, true, m2Workout1Ms),
                        MemberWorkoutRecord("w6", "Full Body Conditioning", ManilaDateTime.formatRelativeDateTime(m2Workout2Ms), 40, 380, true, m2Workout2Ms),
                        MemberWorkoutRecord("w7", "Abs & Mobility Flow", ManilaDateTime.formatRelativeDateTime(m2Workout3Ms), 28, 210, true, m2Workout3Ms),
                        MemberWorkoutRecord("w8", "Lower Body Endurance", ManilaDateTime.formatRelativeDateTime(m2Workout4Ms), 38, 325, true, m2Workout4Ms)
                    ),
                    attendanceHistory = listOf(
                        MemberAttendanceRecord("a5", ManilaDateTime.formatRelativeDay(m2Att1Ms), "08:05 AM", "QR", "Present", m2Att1Ms),
                        MemberAttendanceRecord("a6", ManilaDateTime.formatRelativeDay(m2Att2Ms), "08:12 AM", "QR", "Present", m2Att2Ms),
                        MemberAttendanceRecord("a7", ManilaDateTime.formatRelativeDay(m2Att3Ms), "07:58 AM", "QR", "Present", m2Att3Ms),
                        MemberAttendanceRecord("a8", ManilaDateTime.formatRelativeDay(m2Att4Ms), "08:15 AM", "QR", "Present", m2Att4Ms)
                    ),
                    exercisePerformance = listOf(
                        ExercisePerformanceRecord("Mountain Climbers", "45s Interval", "36 reps", "52 reps", "+16 reps", true),
                        ExercisePerformanceRecord("High Knees", "45s Interval", "48 reps", "64 reps", "+16 reps", true),
                        ExercisePerformanceRecord("Alternating Lunges", "Reps / Leg", "14 reps", "20 reps", "+6 reps", true),
                        ExercisePerformanceRecord("Glute Bridge", "Reps / Set", "20 reps", "30 reps", "+10 reps", true)
                    )
                ),
                AssignedMember(
                    id = "mem_03",
                    name = "Daniel Lim",
                    age = 35,
                    gender = "Male",
                    email = "daniel.lim@bodygrind.local",
                    joinDate = rollingJoinDateString(60),
                    joinDateUtcMs = rollingJoinDateUtcMs(60),
                    status = MemberStatus.NEEDS_ATTENTION,
                    fitnessGoal = "Lose Weight & Improve Core Stability",
                    fitnessLevel = "Beginner",
                    weeklyTargetWorkouts = 4,
                    completedWorkoutsThisWeek = 2,
                    workoutCompletionRate = 58,
                    attendanceRate = 62,
                    attendanceStreakDays = 1,
                    consistencyScore = 60,
                    weeklyAdherenceDays = listOf(true, false, false, true, false, false, false),
                    heightCm = 173f,
                    startingWeightKg = 89.0f,
                    currentWeightKg = 86.8f,
                    targetWeightKg = 78.0f,
                    weightBmiHistory = listOf(
                        WeightBmiPoint("W1", 89.0f, 29.7f),
                        WeightBmiPoint("W2", 88.4f, 29.5f),
                        WeightBmiPoint("W3", 87.6f, 29.3f),
                        WeightBmiPoint("W4", 87.9f, 29.4f),
                        WeightBmiPoint("W5", 87.2f, 29.1f),
                        WeightBmiPoint("W6", 86.8f, 29.0f)
                    ),
                    workoutHistory = listOf(
                        MemberWorkoutRecord("w9", "Beginner Full Body", ManilaDateTime.formatRelativeDateTime(m3Workout1Ms), 25, 205, true, m3Workout1Ms),
                        MemberWorkoutRecord("w10", "Core Foundations", ManilaDateTime.formatRelativeDateTime(m3Workout2Ms), 20, 155, true, m3Workout2Ms),
                        MemberWorkoutRecord("w11", "Cardio Burn Session", ManilaDateTime.formatRelativeDateTime(m3Workout3Ms), 0, 0, false, m3Workout3Ms)
                    ),
                    attendanceHistory = listOf(
                        MemberAttendanceRecord("a9", ManilaDateTime.formatRelativeDay(m3Att1Ms), "06:48 PM", "QR", "Present", m3Att1Ms),
                        MemberAttendanceRecord("a10", ManilaDateTime.formatRelativeDay(m3Att2Ms), "07:05 PM", "QR", "Present", m3Att2Ms),
                        MemberAttendanceRecord("a11", ManilaDateTime.formatRelativeDay(m3Att3Ms), "06:52 PM", "Front Desk", "Present", m3Att3Ms)
                    ),
                    exercisePerformance = listOf(
                        ExercisePerformanceRecord("Wall Sit", "Hold Duration", "30 sec", "48 sec", "+18 sec", true),
                        ExercisePerformanceRecord("Abdominal Crunches", "Reps / Set", "12 reps", "16 reps", "+4 reps", true),
                        ExercisePerformanceRecord("Plank Hold", "Hold Duration", "35 sec", "38 sec", "+3 sec", false),
                        ExercisePerformanceRecord("Jumping Jacks", "45s Interval", "28 reps", "35 reps", "+7 reps", true)
                    )
                ),
                AssignedMember(
                    id = "mem_04",
                    name = "Sofia Navarro",
                    age = 24,
                    gender = "Female",
                    email = "sofia.navarro@bodygrind.local",
                    joinDate = rollingJoinDateString(48),
                    joinDateUtcMs = rollingJoinDateUtcMs(48),
                    status = MemberStatus.ACTIVE,
                    fitnessGoal = "Improve Flexibility & Athletic Conditioning",
                    fitnessLevel = "Advanced",
                    weeklyTargetWorkouts = 5,
                    completedWorkoutsThisWeek = 5,
                    workoutCompletionRate = 96,
                    attendanceRate = 98,
                    attendanceStreakDays = 21,
                    consistencyScore = 97,
                    weeklyAdherenceDays = listOf(true, true, true, false, true, true, false),
                    heightCm = 168f,
                    startingWeightKg = 59.5f,
                    currentWeightKg = 59.0f,
                    targetWeightKg = 58.5f,
                    weightBmiHistory = listOf(
                        WeightBmiPoint("W1", 59.5f, 21.1f),
                        WeightBmiPoint("W2", 59.4f, 21.0f),
                        WeightBmiPoint("W3", 59.3f, 21.0f),
                        WeightBmiPoint("W4", 59.2f, 21.0f),
                        WeightBmiPoint("W5", 59.1f, 20.9f),
                        WeightBmiPoint("W6", 59.0f, 20.9f)
                    ),
                    workoutHistory = listOf(
                        MemberWorkoutRecord("w12", "Athletic Mobility & Core", ManilaDateTime.formatRelativeDateTime(m4Workout1Ms), 42, 310, true, m4Workout1Ms),
                        MemberWorkoutRecord("w13", "Explosive Conditioning", ManilaDateTime.formatRelativeDateTime(m4Workout2Ms), 45, 395, true, m4Workout2Ms),
                        MemberWorkoutRecord("w14", "Upper & Posture Flow", ManilaDateTime.formatRelativeDateTime(m4Workout3Ms), 36, 260, true, m4Workout3Ms)
                    ),
                    attendanceHistory = listOf(
                        MemberAttendanceRecord("a12", ManilaDateTime.formatRelativeDay(m4Att1Ms), "06:18 AM", "QR", "Present", m4Att1Ms),
                        MemberAttendanceRecord("a13", ManilaDateTime.formatRelativeDay(m4Att2Ms), "06:22 AM", "QR", "Present", m4Att2Ms),
                        MemberAttendanceRecord("a14", ManilaDateTime.formatRelativeDay(m4Att3Ms), "06:25 AM", "QR", "Present", m4Att3Ms)
                    ),
                    exercisePerformance = listOf(
                        ExercisePerformanceRecord("Diamond Push-Up", "Reps / Set", "10 reps", "16 reps", "+6 reps", true),
                        ExercisePerformanceRecord("Bicycle Crunches", "Reps / Set", "30 reps", "44 reps", "+14 reps", true),
                        ExercisePerformanceRecord("Leg Raise", "Reps / Set", "15 reps", "22 reps", "+7 reps", true),
                        ExercisePerformanceRecord("Cobra Stretch", "Mobility Hold", "45 sec", "60 sec", "+15 sec", true)
                    )
                ),
                AssignedMember(
                    id = "mem_05",
                    name = "Carlos Mendoza",
                    age = 30,
                    gender = "Male",
                    email = "carlos.mendoza@bodygrind.local",
                    joinDate = rollingJoinDateString(155),
                    joinDateUtcMs = rollingJoinDateUtcMs(155),
                    status = MemberStatus.PAUSED,
                    fitnessGoal = "Maintain Fitness & Joint Recovery",
                    fitnessLevel = "Beginner",
                    weeklyTargetWorkouts = 3,
                    completedWorkoutsThisWeek = 1,
                    workoutCompletionRate = 64,
                    attendanceRate = 55,
                    attendanceStreakDays = 0,
                    consistencyScore = 58,
                    weeklyAdherenceDays = listOf(false, true, false, false, false, false, false),
                    heightCm = 178f,
                    startingWeightKg = 80.2f,
                    currentWeightKg = 79.4f,
                    targetWeightKg = 77.5f,
                    weightBmiHistory = listOf(
                        WeightBmiPoint("W1", 80.2f, 25.3f),
                        WeightBmiPoint("W2", 80.0f, 25.2f),
                        WeightBmiPoint("W3", 79.7f, 25.2f),
                        WeightBmiPoint("W4", 79.5f, 25.1f),
                        WeightBmiPoint("W5", 79.6f, 25.1f),
                        WeightBmiPoint("W6", 79.4f, 25.1f)
                    ),
                    workoutHistory = listOf(
                        MemberWorkoutRecord("w15", "Low-Impact Mobility", ManilaDateTime.formatRelativeDateTime(m5Workout1Ms), 22, 130, true, m5Workout1Ms),
                        MemberWorkoutRecord("w16", "Beginner Conditioning", ManilaDateTime.formatRelativeDateTime(m5Workout2Ms), 25, 175, true, m5Workout2Ms)
                    ),
                    attendanceHistory = listOf(
                        MemberAttendanceRecord("a15", ManilaDateTime.formatRelativeDay(m5Att1Ms), "05:40 PM", "QR", "Present", m5Att1Ms),
                        MemberAttendanceRecord("a16", ManilaDateTime.formatRelativeDay(m5Att2Ms), "05:50 PM", "QR", "Present", m5Att2Ms)
                    ),
                    exercisePerformance = listOf(
                        ExercisePerformanceRecord("Arm Circle", "Mobility Duration", "40 sec", "60 sec", "+20 sec", true),
                        ExercisePerformanceRecord("Glute Bridge", "Reps / Set", "15 reps", "18 reps", "+3 reps", true),
                        ExercisePerformanceRecord("Wall Sit", "Hold Duration", "35 sec", "40 sec", "+5 sec", false)
                    )
                )
            )
        }

    val samplePendingTasks: List<TrainerPendingTask>
        get() {
            val today = ManilaDateTime.todayManila()
            val tomorrow = today.plusDays(1)
            val dayAfterTomorrow = ManilaDateTime.rollingFutureManilaDate(2, today)

            val dueTodayMs = ManilaDateTime.manilaDateToStartOfDayUtcMs(today)
            val dueTomorrowMs = ManilaDateTime.manilaDateToStartOfDayUtcMs(tomorrow)
            val dueDayAfterMs = ManilaDateTime.manilaDateToStartOfDayUtcMs(dayAfterTomorrow)

            return listOf(
                TrainerPendingTask(
                    id = "task_01",
                    memberId = "mem_03",
                    memberName = "Daniel Lim",
                    title = "Follow up on missed cardio session & adjust weekly pacing",
                    dueLabel = ManilaDateTime.formatRelativeDueDay(today, today),
                    priorityLabel = "High",
                    dueDateUtcMs = dueTodayMs
                ),
                TrainerPendingTask(
                    id = "task_02",
                    memberId = "mem_01",
                    memberName = "Marcus Reyes",
                    title = "Review upper body push-up progression & add overload cue",
                    dueLabel = ManilaDateTime.formatRelativeDueDay(today, today),
                    priorityLabel = "Medium",
                    dueDateUtcMs = dueTodayMs
                ),
                TrainerPendingTask(
                    id = "task_03",
                    memberId = "mem_02",
                    memberName = "Elena Santos",
                    title = "Update Week 7 HIIT intervals after 5.6 kg weight loss milestone",
                    dueLabel = ManilaDateTime.formatRelativeDueDay(tomorrow, today),
                    priorityLabel = "Medium",
                    dueDateUtcMs = dueTomorrowMs
                ),
                TrainerPendingTask(
                    id = "task_04",
                    memberId = "mem_05",
                    memberName = "Carlos Mendoza",
                    title = "Prepare low-impact return-to-gym mobility plan",
                    dueLabel = ManilaDateTime.formatRelativeDueDay(dayAfterTomorrow, today),
                    priorityLabel = "Low",
                    dueDateUtcMs = dueDayAfterMs
                )
            )
        }

    val sampleNotifications: List<TrainerNotification>
        get() {
            val nowMs = ManilaDateTime.nowUtcMillis()
            val notif1Ms = nowMs - 25 * 60_000L
            val notif2Ms = nowMs - 2 * 3600_000L
            val notif3Ms = rollingDateTimeUtcMs(1, 15, 0)
            val notif4Ms = rollingDateTimeUtcMs(2, 11, 0)

            return listOf(
                TrainerNotification(
                    id = "notif_01",
                    title = "Morning QR Check-In",
                    message = "Marcus Reyes checked in at 07:02 AM and completed Upper Body Hypertrophy.",
                    timeLabel = ManilaDateTime.formatRelativeDay(notif1Ms),
                    isUnread = true,
                    timestampUtcMs = notif1Ms
                ),
                TrainerNotification(
                    id = "notif_02",
                    title = "Consistency Milestone",
                    message = "Sofia Navarro reached a 21-day attendance streak (98% monthly rate).",
                    timeLabel = ManilaDateTime.formatRelativeDay(notif2Ms),
                    isUnread = true,
                    timestampUtcMs = notif2Ms
                ),
                TrainerNotification(
                    id = "notif_03",
                    title = "Attendance Alert",
                    message = "Daniel Lim missed his scheduled Cardio Burn Session this week.",
                    timeLabel = ManilaDateTime.formatRelativeDay(notif3Ms),
                    isUnread = true,
                    timestampUtcMs = notif3Ms
                ),
                TrainerNotification(
                    id = "notif_04",
                    title = "Weight Goal Progress",
                    message = "Elena Santos logged 65.4 kg (-5.6 kg since Week 1).",
                    timeLabel = ManilaDateTime.formatRelativeDay(notif4Ms),
                    isUnread = false,
                    timestampUtcMs = notif4Ms
                )
            )
        }

    val initialFeedbackEntries: List<TrainerFeedbackEntry>
        get() {
            val fb1Ms = rollingDateTimeUtcMs(0, 8, 5)
            val fb2Ms = rollingDateTimeUtcMs(1, 16, 30)
            val fb3Ms = rollingDateTimeUtcMs(2, 14, 0)
            val fb4Ms = rollingDateTimeUtcMs(3, 10, 30)

            return listOf(
                TrainerFeedbackEntry(
                    id = "fb_01",
                    memberId = "mem_01",
                    memberName = "Marcus Reyes",
                    category = FeedbackCategory.WORKOUT_FEEDBACK,
                    message = "Strong tempo on today's push-up sets. Keep elbows tucked around 45 degrees on the final set.",
                    timestampLabel = ManilaDateTime.formatRelativeDateTime(fb1Ms),
                    timestampUtcMs = fb1Ms
                ),
                TrainerFeedbackEntry(
                    id = "fb_02",
                    memberId = "mem_02",
                    memberName = "Elena Santos",
                    category = FeedbackCategory.PROGRESS_COMMENT,
                    message = "Consistent 0.9 kg/week drop while maintaining workout energy. Ready to raise HIIT work intervals by 5s.",
                    timestampLabel = ManilaDateTime.formatRelativeDateTime(fb2Ms),
                    timestampUtcMs = fb2Ms
                ),
                TrainerFeedbackEntry(
                    id = "fb_03",
                    memberId = "mem_03",
                    memberName = "Daniel Lim",
                    category = FeedbackCategory.TRAINING_ADJUSTMENT,
                    message = "Swapping one high-impact session for brisk incline walking + core stability to rebuild consistency.",
                    timestampLabel = ManilaDateTime.formatRelativeDateTime(fb3Ms),
                    timestampUtcMs = fb3Ms
                ),
                TrainerFeedbackEntry(
                    id = "fb_04",
                    memberId = "mem_04",
                    memberName = "Sofia Navarro",
                    category = FeedbackCategory.EXERCISE_RECOMMENDATION,
                    message = "Add 3 sets of slow eccentric Diamond Push-Ups and 60s Cobra Stretch post-session.",
                    timestampLabel = ManilaDateTime.formatRelativeDateTime(fb4Ms),
                    timestampUtcMs = fb4Ms
                )
            )
        }
}
