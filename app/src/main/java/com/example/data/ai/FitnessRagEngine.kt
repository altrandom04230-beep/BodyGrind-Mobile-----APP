package com.example.data.ai

import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutScheduleEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.ui.trainers.TrainersData
import java.util.Locale

data class RagSourceItem(
    val title: String,
    val category: String, // "EXERCISE", "PLAN", "SCHEDULE", "TRAINER", "ANATOMY", "SCIENCE", "TELEMETRY"
    val detail: String,
    val exerciseEntity: ExerciseEntity? = null,
    val workoutPlanEntity: WorkoutPlanEntity? = null
)

data class RagSearchResult(
    val augmentedContext: String,
    val retrievedSources: List<RagSourceItem>,
    val matchedExercises: List<ExerciseEntity>,
    val matchedPlans: List<WorkoutPlanEntity>,
    val matchedSchedules: List<WorkoutScheduleEntity>,
    val directLocalAnswer: String
)

object FitnessRagEngine {

    // 1. Evidence-Based Sports Science & Physiology Knowledge Chunks
    private val sportsScienceKnowledge = listOf(
        Pair(
            listOf("warm up", "warmup", "stretch", "stretching", "injury", "prevent", "prepare", "joints"),
            "WARM-UP & JOINT MOBILITY: Always warm up for 3-5 minutes prior to strenuous training. Prioritize dynamic mobility (arm swings, hip openers, bodyweight squats, torso twists) to raise synovial fluid lubricity in joints and increase neuromuscular recruitment."
        ),
        Pair(
            listOf("sore", "soreness", "doms", "pain", "recover", "recovery", "rest day", "inflammation"),
            "RECOVERY & DOMS (Delayed Onset Muscle Soreness): Micro-tears in muscle fibers cause peak soreness 24-48 hours post-workout. Active recovery (light walking, contrast showers, hydration, 7-9 hours sleep) accelerates metabolic clearance. Never push through sharp joint pain."
        ),
        Pair(
            listOf("lose weight", "fat burn", "burn fat", "cardio", "calories", "hiit", "deficit"),
            "FAT LOSS & EPOC DYNAMICS: High-Intensity Interval Training (HIIT) activates Excess Post-Exercise Oxygen Consumption (EPOC), continuing caloric expenditure for hours after session completion. Combine compound bodyweight movements with short 20-30s rest periods."
        ),
        Pair(
            listOf("build muscle", "hypertrophy", "progressive overload", "reps", "strength", "bulk", "gains"),
            "CALISTHENIC HYPERTROPHY & PROGRESSIVE OVERLOAD: Muscle growth requires mechanical tension and metabolic stress. Without gym weights, achieve progressive overload by slowing down eccentric lowering (3-4 seconds), increasing range of motion, reducing rest intervals, or advancing exercise leverage."
        ),
        Pair(
            listOf("water", "hydrate", "hydration", "drink", "nutrition", "protein", "diet", "meal", "macro"),
            "NUTRITION & HYDRATION: Hydrate with 500ml water 1 hour before training and sip water between sets. Consume 1.6-2.2g of protein per kg of bodyweight alongside complex carbohydrates within 2 hours post-session to fuel muscle protein synthesis and glycogen restoration."
        ),
        Pair(
            listOf("breathe", "breathing", "breath", "core", "brace", "spine", "valsalva"),
            "RESPIRATORY BIOMECHANICS & CORE BRACING: Inhale during the eccentric (lowering) phase and exhale forcefully during the concentric (pushing/pulling) phase. Maintain 360-degree intra-abdominal pressure as if bracing for a punch to stabilize the lumbar spine."
        )
    )

    // 2. Comprehensive Anatomy & Educational Descriptions for All 25 Target Muscle Categories
    private val muscleAnatomyKnowledge = listOf(
        Triple("Full Body", listOf("full body", "whole body", "total body"), "FULL BODY: Engages multiple major muscle groups simultaneously (Chest, Back, Legs, Core), maximizing calorie expenditure, functional neuromuscular coordination, and systemic hormonal response."),
        Triple("Abs", listOf("abs", "abdominals", "rectus abdominis", "six pack"), "ABS (Rectus Abdominis): Flexes the lumbar spine and compresses abdominal cavity. Best targeted through controlled spinal flexion like Crunches and isometric anti-extension like Planks."),
        Triple("Core", listOf("core", "midsection", "transverse abdominis"), "CORE COMPLEX: Includes transverse abdominis, internal/external obliques, multifidus, and pelvic floor. Stabilizes spine, transfers kinetic energy between upper and lower extremities, and protects internal organs."),
        Triple("Chest", listOf("chest", "pectoral", "pecs", "push up"), "CHEST (Pectoralis Major & Minor): Drives horizontal adduction and pushing mechanics. Key home exercises include Standard, Wide, Diamond, and Incline/Decline Push-Ups."),
        Triple("Back", listOf("back", "upper back", "rhomboids"), "BACK (Rhomboids, Middle Traps, Teres Major): Retracts and stabilizes scapula, promotes upright posture, and balances anterior pushing volume. Key movements: Cobra, Superman, Prone Y-T-W raises."),
        Triple("Shoulders", listOf("shoulder", "deltoid", "delts"), "SHOULDERS (Deltoids - Anterior, Lateral, Posterior): Provides 360-degree shoulder mobility and overhead pushing power. Pike Push-Ups and Lateral Arm Raises target all deltoid heads."),
        Triple("Biceps", listOf("bicep", "biceps", "biceps brachii"), "BICEPS BRACHII: Flexes elbow joint and supinates forearm. In calisthenics, targeted through Doorframe Rows with underhand grip or Chin-Ups."),
        Triple("Triceps", listOf("tricep", "triceps", "triceps brachii"), "TRICEPS BRACHII: Comprises lateral, long, and medial heads responsible for elbow extension. Targeted through Diamond Push-Ups, Chair Dips, and Plank-to-Pushups."),
        Triple("Forearms", listOf("forearm", "forearms", "grip", "wrist"), "FOREARMS (Flexor & Extensor Carpi): Controls wrist flexion, extension, and crushing grip strength. Crucial for wrist joint longevity during heavy push-up volume."),
        Triple("Arms", listOf("arms", "upper arm"), "ARMS: Dual agonist-antagonist pairing of Biceps and Triceps for total pushing and pulling elbow articulacy."),
        Triple("Quadriceps", listOf("quad", "quads", "quadriceps", "front thigh", "thighs"), "QUADRICEPS (Rectus Femoris, Vastus Lateralis/Medialis/Intermedius): Powerful knee extensors. Best developed with Bodyweight Squats, Lunges, and Wall Sits."),
        Triple("Hamstrings", listOf("hamstring", "hamstrings", "back thigh", "posterior thigh"), "HAMSTRINGS (Biceps Femoris, Semitendinosus, Semimembranosus): Knee flexion and hip extension. Targeted via Glute Bridges with extended foot position and Single-Leg Good Mornings."),
        Triple("Glutes", listOf("glute", "glutes", "gluteus", "butt", "hips"), "GLUTEAL GROUP (Gluteus Maximus, Medius, Minimus): Largest power generator in the human body for hip extension and pelvic stabilization. Best trained via Glute Bridges, Frog Pumps, and Bulgarian Split Squats."),
        Triple("Calves", listOf("calf", "calves", "gastrocnemius", "soleus"), "CALVES (Gastrocnemius & Soleus): Plantar flexes ankle joint and absorbs ground reaction forces. Targeted via Single-Leg Calf Raises and Jumping Rope/Jacks."),
        Triple("Legs", listOf("leg", "legs", "lower body"), "LEGS: Complete lower body kinetic chain encompassing Quads, Hamstrings, Glutes, and Calves for locomotion and vertical power."),
        Triple("Hip Flexors", listOf("hip flexor", "hip flexors", "psoas", "iliopsoas"), "HIP FLEXORS (Psoas Major & Iliacus): Elevates femur towards torso. Counterbalances glute tension; tight hip flexors from prolonged sitting require stretching and active high-knee conditioning."),
        Triple("Obliques", listOf("oblique", "obliques", "side abs", "waist"), "OBLIQUES (Internal & External): Controls rotational trunk torque and lateral flexion. Targeted via Bicycle Crunches, Russian Twists, and Side Planks."),
        Triple("Lower Back", listOf("lower back", "erector spinae", "lumbar"), "LOWER BACK (Erector Spinae): Maintains neutral spinal posture against gravity. Strengthened with gentle Supermans and Bird-Dogs."),
        Triple("Traps", listOf("trap", "traps", "trapezius", "neck"), "TRAPEZIUS: Elevates, retracts, and depresses shoulder blades. Upper traps support neck stability, while lower traps prevent shoulder impingement."),
        Triple("Lats", listOf("lat", "lats", "latissimus dorsi"), "LATISSIMUS DORSI: Broadest back muscle; pulls upper arms down and back. Targeted with Towel Lat Pulls and Sliding Floor Pull-Downs."),
        Triple("Cardio", listOf("cardio", "aerobic", "endurance", "heart rate"), "CARDIO: Enhances cardiovascular stroke volume, mitochondrial density, and VO2 max through continuous rhythmic movement like Mountain Climbers and High Knees."),
        Triple("Mobility", listOf("mobility", "range of motion", "joint health"), "MOBILITY: Active ability to move joints through their full physiological range with neuromuscular control. Prevents stiffness and athletic injuries."),
        Triple("Flexibility", listOf("flexibility", "stretching", "supple", "elasticity"), "FLEXIBILITY: Passive extensibility of soft tissues and muscle fibers, facilitating tension release post-workout."),
        Triple("Balance", listOf("balance", "proprioception", "equilibrium"), "BALANCE & PROPRIOCEPTION: Enhances spatial awareness and stabilizer firing through single-leg stands and offset bodyweight holds."),
        Triple("Stability", listOf("stability", "anti-rotational", "steady"), "STABILITY: Muscular resistance to unwanted movement or spinal torsion, reinforced through Bird-Dog and Plank variations.")
    )

    fun retrieve(
        query: String,
        userProfile: UserProfileEntity?,
        allExercises: List<ExerciseEntity>,
        allPlans: List<WorkoutPlanEntity>,
        allSchedules: List<WorkoutScheduleEntity> = emptyList(),
        recentSessions: List<WorkoutSessionEntity> = emptyList(),
        streakDays: Int = 0
    ): RagSearchResult {
        val qLower = query.lowercase(Locale.getDefault())
        val queryTokens = qLower.split(Regex("[\\s,?.!]+")).filter { it.length > 2 }

        val matchedExercises = mutableListOf<ExerciseEntity>()
        val matchedPlans = mutableListOf<WorkoutPlanEntity>()
        val matchedSchedules = mutableListOf<WorkoutScheduleEntity>()
        val matchedScience = mutableListOf<String>()
        val matchedAnatomy = mutableListOf<Triple<String, List<String>, String>>()
        val matchedTrainers = mutableListOf<String>()
        val sourceItems = mutableListOf<RagSourceItem>()

        // 1. Retrieve User Telemetry Source Tag
        sourceItems.add(
            RagSourceItem(
                title = "Live Stats: Streak & Level",
                category = "TELEMETRY",
                detail = "$streakDays-Day Streak • Level ${userProfile?.level ?: 1} (${userProfile?.xpPoints ?: 0} XP) • Goal: ${userProfile?.fitnessGoal ?: "General"}"
            )
        )

        // 2. Retrieve & Score In-App Exercises
        val scoredExercises = allExercises.map { ex ->
            var score = 0
            val exNameLower = ex.name.lowercase(Locale.getDefault())
            val exCategoryLower = ex.category.lowercase(Locale.getDefault())
            val exMusclesLower = ex.targetMuscles.lowercase(Locale.getDefault())

            if (qLower.contains(exNameLower)) score += 10
            if (qLower.contains(exCategoryLower)) score += 5

            queryTokens.forEach { token ->
                if (exNameLower.contains(token)) score += 4
                if (exCategoryLower.contains(token)) score += 3
                if (exMusclesLower.contains(token)) score += 3
            }
            Pair(ex, score)
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        scoredExercises.take(3).forEach { (ex, _) ->
            matchedExercises.add(ex)
            sourceItems.add(
                RagSourceItem(
                    title = ex.name,
                    category = "EXERCISE",
                    detail = "${ex.targetMuscles} • ${ex.difficulty}",
                    exerciseEntity = ex
                )
            )
        }

        // 3. Retrieve & Score In-App Workout Plans
        val scoredPlans = allPlans.map { plan ->
            var score = 0
            val pNameLower = plan.name.lowercase(Locale.getDefault())
            val pCatLower = plan.category.lowercase(Locale.getDefault())
            val pDescLower = plan.description.lowercase(Locale.getDefault())

            if (qLower.contains(pNameLower)) score += 10
            if (qLower.contains(pCatLower)) score += 5

            queryTokens.forEach { token ->
                if (pNameLower.contains(token)) score += 4
                if (pCatLower.contains(token)) score += 3
                if (pDescLower.contains(token)) score += 2
            }
            Pair(plan, score)
        }.filter { it.second > 0 }.sortedByDescending { it.second }

        scoredPlans.take(2).forEach { (plan, _) ->
            matchedPlans.add(plan)
            sourceItems.add(
                RagSourceItem(
                    title = plan.name,
                    category = "PLAN",
                    detail = "${plan.estimatedDurationMinutes} mins • ${plan.category}",
                    workoutPlanEntity = plan
                )
            )
        }

        // 4. Retrieve & Score Workout Schedules
        val dayNames = mapOf(
            1 to "Monday", 2 to "Tuesday", 3 to "Wednesday",
            4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday"
        )
        val scheduleQueryMatched = queryTokens.any {
            listOf("schedule", "sched", "today", "tomorrow", "routine", "mon", "tue", "wed", "thu", "fri", "sat", "sun").contains(it)
        } || qLower.contains("when") || qLower.contains("time")

        if (scheduleQueryMatched && allSchedules.isNotEmpty()) {
            val enabledSchedules = allSchedules.filter { it.isEnabled }
            matchedSchedules.addAll(enabledSchedules)
            enabledSchedules.take(3).forEach { sched ->
                val day = dayNames[sched.dayOfWeek] ?: "Day ${sched.dayOfWeek}"
                sourceItems.add(
                    RagSourceItem(
                        title = "$day: ${sched.planName}",
                        category = "SCHEDULE",
                        detail = String.format(Locale.US, "%02d:%02d", sched.timeHour, sched.timeMinute)
                    )
                )
            }
        }

        // 5. Retrieve Gym Trainers Knowledge (Coach Cobie & Coach Jun)
        for (trainer in TrainersData.availableTrainers) {
            val tNameLower = trainer.name.lowercase(Locale.getDefault())
            val isMentioned = qLower.contains(tNameLower) ||
                    (tNameLower.contains("cobie") && qLower.contains("cobie")) ||
                    (tNameLower.contains("jun") && qLower.contains("jun")) ||
                    qLower.contains("trainer") || qLower.contains("coach")

            if (isMentioned) {
                matchedTrainers.add(
                    "${trainer.name} (${trainer.title}, ${trainer.experienceYears} yrs exp). Floor Hours: ${trainer.floorSchedule}. Specialties: ${trainer.specialties.joinToString(", ")}. Bio: ${trainer.bio}. Advice Tip: ${trainer.consultationTip}"
                )
                sourceItems.add(
                    RagSourceItem(
                        title = trainer.name,
                        category = "TRAINER",
                        detail = trainer.title
                    )
                )
            }
        }

        // 6. Retrieve Anatomy & Biomechanics Chunks for all 25 Muscle Groups
        for (anatomy in muscleAnatomyKnowledge) {
            val (muscleName, keywords, description) = anatomy
            if (keywords.any { qLower.contains(it) }) {
                matchedAnatomy.add(anatomy)
                sourceItems.add(
                    RagSourceItem(
                        title = muscleName,
                        category = "ANATOMY",
                        detail = description.take(60) + "..."
                    )
                )
            }
        }

        // 7. Retrieve Evidence-Based Sports Science Principles
        for ((keywords, text) in sportsScienceKnowledge) {
            if (keywords.any { qLower.contains(it) }) {
                matchedScience.add(text)
                val headline = text.substringBefore(":").trim()
                sourceItems.add(
                    RagSourceItem(
                        title = headline,
                        category = "SCIENCE",
                        detail = text.substringAfter(":").trim().take(60) + "..."
                    )
                )
            }
        }

        // 8. Assemble Complete Augmented Context for Gemini
        val augmentedContext = buildString {
            appendLine("=== USER FITNESS PROFILE & REAL-TIME STATS ===")
            appendLine("Name: ${userProfile?.name ?: "Athlete"}")
            appendLine("Fitness Goal: ${userProfile?.fitnessGoal ?: "General Fitness"}")
            appendLine("Fitness Level: ${userProfile?.fitnessLevel ?: "Beginner"}")
            val w = userProfile?.weightKg ?: 70f
            val h = userProfile?.heightCm ?: 175f
            val hM = h / 100f
            val bmi = if (hM > 0) w / (hM * hM) else 22f
            appendLine("Weight: ${w}kg, Height: ${h}cm, Calculated BMI: ${String.format(Locale.US, "%.1f", bmi)}")
            appendLine("Current Consecutive Streak: $streakDays days")
            appendLine("Total Completed Workout Sessions: ${recentSessions.size}")
            appendLine("Total Calories Burned Logged: ${recentSessions.sumOf { it.caloriesBurned }} kcal")
            appendLine("User XP: ${userProfile?.xpPoints ?: 0} points (Level ${userProfile?.level ?: 1})")
            appendLine()

            if (matchedSchedules.isNotEmpty()) {
                appendLine("=== RETRIEVED USER WORKOUT SCHEDULE ===")
                matchedSchedules.forEach { s ->
                    val day = dayNames[s.dayOfWeek] ?: "Day ${s.dayOfWeek}"
                    appendLine("• $day at ${String.format(Locale.US, "%02d:%02d", s.timeHour, s.timeMinute)}: Plan '${s.planName}' (Active: ${s.isEnabled})")
                }
                appendLine()
            }

            if (matchedExercises.isNotEmpty()) {
                appendLine("=== RETRIEVED IN-APP EXERCISE LIBRARY ===")
                matchedExercises.forEach { ex ->
                    appendLine("• Exercise: ${ex.name} [Category: ${ex.category}, Difficulty: ${ex.difficulty}]")
                    appendLine("  Target Muscles: ${ex.targetMuscles} (Primary: ${ex.primaryMuscles}, Secondary: ${ex.secondaryMuscles})")
                    appendLine("  Instructions: ${ex.instructions}")
                    appendLine("  Form/Posture Cues: ${ex.tips}")
                    if (ex.educationalDescription.isNotBlank()) {
                        appendLine("  Anatomical & Educational Insight: ${ex.educationalDescription}")
                    }
                    if (ex.functionalBenefit.isNotBlank()) {
                        appendLine("  Functional Life Benefit: ${ex.functionalBenefit}")
                    }
                    appendLine("  Est. Calorie Burn: ~${ex.caloriesPerMinute} kcal/min")
                }
                appendLine()
            }

            if (matchedPlans.isNotEmpty()) {
                appendLine("=== RETRIEVED IN-APP WORKOUT ROUTINES ===")
                matchedPlans.forEach { p ->
                    appendLine("• Workout Plan: ${p.name} (${p.category}, ${p.difficulty})")
                    appendLine("  Duration: ${p.estimatedDurationMinutes} minutes | ${if (p.isCustom) "User Custom Routine" else "Official Preset"}")
                    appendLine("  Description: ${p.description}")
                    if (p.targetMusclesDescription.isNotBlank()) {
                        appendLine("  Target Muscle Breakdown: ${p.targetMusclesDescription}")
                    }
                    if (p.educationalOverview.isNotBlank()) {
                        appendLine("  Educational Purpose: ${p.educationalOverview}")
                    }
                }
                appendLine()
            }

            if (matchedTrainers.isNotEmpty()) {
                appendLine("=== RETRIEVED GYM TRAINERS DIRECTORY ===")
                matchedTrainers.forEach { appendLine("• $it") }
                appendLine()
            }

            if (matchedAnatomy.isNotEmpty()) {
                appendLine("=== RETRIEVED TARGET MUSCLE ANATOMY & BIOMECHANICS ===")
                matchedAnatomy.forEach { appendLine("• ${it.third}") }
                appendLine()
            }

            if (matchedScience.isNotEmpty()) {
                appendLine("=== RETRIEVED EVIDENCE-BASED SPORTS SCIENCE ===")
                matchedScience.forEach { appendLine("• $it") }
                appendLine()
            }
        }

        // 9. Build Comprehensive Local Fallback Answer
        val directAnswer = buildFallbackAnswer(
            qLower,
            matchedExercises,
            matchedPlans,
            matchedSchedules,
            matchedTrainers,
            matchedAnatomy,
            matchedScience,
            userProfile,
            streakDays
        )

        return RagSearchResult(
            augmentedContext = augmentedContext,
            retrievedSources = sourceItems.distinctBy { it.title },
            matchedExercises = matchedExercises.take(3),
            matchedPlans = matchedPlans.take(2),
            matchedSchedules = matchedSchedules.take(3),
            directLocalAnswer = directAnswer
        )
    }

    private fun buildFallbackAnswer(
        q: String,
        exercises: List<ExerciseEntity>,
        plans: List<WorkoutPlanEntity>,
        schedules: List<WorkoutScheduleEntity>,
        trainers: List<String>,
        anatomy: List<Triple<String, List<String>, String>>,
        science: List<String>,
        userProfile: UserProfileEntity?,
        streakDays: Int
    ): String {
        return buildString {
            if (trainers.isNotEmpty()) {
                append("🏋️‍♂️ In-App Gym Trainers Guidance:\n")
                trainers.forEach { append("• $it\n\n") }
                append("Follow-up: Would you like to schedule a session aligned with Coach Cobie's strength focus or Coach Jun's mobility circuits?\n\n")
                append("Disclaimer: Certified coaches can supervise and adjust your form in person.")
                return@buildString
            }

            if (schedules.isNotEmpty() && (q.contains("sched") || q.contains("routine") || q.contains("when") || q.contains("today"))) {
                append("📅 Your Scheduled Workout Sessions:\n")
                val dayNames = mapOf(1 to "Monday", 2 to "Tuesday", 3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday")
                schedules.forEach { s ->
                    val day = dayNames[s.dayOfWeek] ?: "Day ${s.dayOfWeek}"
                    append("• $day at ${String.format(Locale.US, "%02d:%02d", s.timeHour, s.timeMinute)}: ${s.planName}\n")
                }
                append("\nFollow-up: Would you like to start today's scheduled routine or customize your workout days?\n\n")
                append("Disclaimer: Consistency is key; take rest days if fatigue sets in.")
                return@buildString
            }

            if (q.contains("streak") || q.contains("stats") || q.contains("level") || q.contains("progress")) {
                append("🔥 Your Real-Time Fitness Telemetry:\n")
                append("• Workout Streak: $streakDays Consecutive Days!\n")
                append("• Athlete Level: Level ${userProfile?.level ?: 1} (${userProfile?.xpPoints ?: 0} XP)\n")
                append("• Focus Goal: ${userProfile?.fitnessGoal ?: "Fitness"}\n\n")
                append("Follow-up: Would you like to do a quick 10-minute session today to keep your $streakDays-day streak burning?\n\n")
                append("Disclaimer: Always listen to your body and train safely.")
                return@buildString
            }

            if (exercises.isNotEmpty()) {
                val ex = exercises.first()
                append("💪 ${ex.name} [Target: ${ex.targetMuscles}]:\n")
                append("• Posture & Form: ${ex.tips}\n")
                append("• Instructions: ${ex.instructions}\n")
                if (ex.educationalDescription.isNotBlank()) {
                    append("• Educational Insight: ${ex.educationalDescription}\n")
                }
                if (ex.functionalBenefit.isNotBlank()) {
                    append("• Functional Life Benefit: ${ex.functionalBenefit}\n")
                }
                append("\nFollow-up: Do you need an easier regression or an advanced progression for ${ex.name}?\n\n")
                append("Disclaimer: If you experience sharp joint or tendon pain, stop immediately.")
                return@buildString
            }

            if (plans.isNotEmpty()) {
                val plan = plans.first()
                append("🏋️‍♂️ Recommended Plan: ${plan.name} (${plan.difficulty})\n")
                append("• Estimated Duration: ${plan.estimatedDurationMinutes} minutes\n")
                append("• Focus: ${plan.description}\n")
                if (plan.targetMusclesDescription.isNotBlank()) {
                    append("• Muscle Group Focus: ${plan.targetMusclesDescription}\n")
                }
                append("\nFollow-up: Would you like to preview the exercise order or start this routine now?\n\n")
                append("Disclaimer: Warm up before starting high-intensity interval sessions.")
                return@buildString
            }

            if (anatomy.isNotEmpty()) {
                append("🧬 Target Muscle Science:\n")
                anatomy.forEach { append("• ${it.third}\n\n") }
                append("Follow-up: Would you like specific bodyweight exercises that isolate these muscle fibers?\n\n")
                append("Disclaimer: Proper biomechanical alignment prevents chronic muscle imbalances.")
                return@buildString
            }

            if (science.isNotEmpty()) {
                append("✨ Sports Science & Recovery Coach:\n")
                science.forEach { append("• $it\n\n") }
                append("Follow-up: What area of your training or recovery would you like to optimize next?\n\n")
                append("Disclaimer: General fitness information only; not medical advice.")
                return@buildString
            }

            append("👋 Hi ${userProfile?.name ?: "Athlete"}! I am your AI Fitness Coach powered by our in-app RAG dataset. I can guide your workouts, analyze muscle biomechanics, look up your scheduled routines, and share advice from Coach Cobie and Coach Jun.\n\n")
            append("Follow-up: Which muscle group or fitness goal would you like to work on today?\n\n")
            append("Disclaimer: Always warm up and consult a healthcare professional before starting new workout programs.")
        }
    }
}
