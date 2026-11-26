import '../../../data/models/exercise.dart';
import '../../../data/models/rag_models.dart';
import '../../../data/models/user_profile.dart';
import '../../../data/models/workout_plan.dart';
import '../../../data/models/workout_schedule.dart';
import '../../../data/models/workout_session.dart';

class FitnessRagEngine {
  static const List<MapEntry<List<String>, String>> _sportsScienceKnowledge = [
    MapEntry(
      ['warm up', 'warmup', 'stretch', 'stretching', 'injury', 'prevent', 'prepare', 'joints'],
      'WARM-UP & JOINT MOBILITY: Always warm up for 3-5 minutes prior to strenuous training. Prioritize dynamic mobility (arm swings, hip openers, bodyweight squats, torso twists) to raise synovial fluid lubricity in joints and increase neuromuscular recruitment.',
    ),
    MapEntry(
      ['sore', 'soreness', 'doms', 'pain', 'recover', 'recovery', 'rest day', 'inflammation'],
      'RECOVERY & DOMS (Delayed Onset Muscle Soreness): Micro-tears in muscle fibers cause peak soreness 24-48 hours post-workout. Active recovery (light walking, contrast showers, hydration, 7-9 hours sleep) accelerates metabolic clearance. Never push through sharp joint pain.',
    ),
    MapEntry(
      ['lose weight', 'fat burn', 'burn fat', 'cardio', 'calories', 'hiit', 'deficit'],
      'FAT LOSS & EPOC DYNAMICS: High-Intensity Interval Training (HIIT) activates Excess Post-Exercise Oxygen Consumption (EPOC), continuing caloric expenditure for hours after session completion. Combine compound bodyweight movements with short 20-30s rest periods.',
    ),
    MapEntry(
      ['build muscle', 'hypertrophy', 'progressive overload', 'reps', 'strength', 'bulk', 'gains'],
      'CALISTHENIC HYPERTROPHY & PROGRESSIVE OVERLOAD: Muscle growth requires mechanical tension and metabolic stress. Without gym weights, achieve progressive overload by slowing down eccentric lowering (3-4 seconds), increasing range of motion, reducing rest intervals, or advancing exercise leverage.',
    ),
    MapEntry(
      ['water', 'hydrate', 'hydration', 'drink', 'nutrition', 'protein', 'diet', 'meal', 'macro'],
      'NUTRITION & HYDRATION: Hydrate with 500ml water 1 hour before training and sip water between sets. Consume 1.6-2.2g of protein per kg of bodyweight alongside complex carbohydrates within 2 hours post-session to fuel muscle protein synthesis and glycogen restoration.',
    ),
    MapEntry(
      ['breathe', 'breathing', 'breath', 'core', 'brace', 'spine', 'valsalva'],
      'RESPIRATORY BIOMECHANICS & CORE BRACING: Inhale during the eccentric (lowering) phase and exhale forcefully during the concentric (pushing/pulling) phase. Maintain 360-degree intra-abdominal pressure as if bracing for a punch to stabilize the lumbar spine.',
    ),
  ];

  static const List<Map<String, dynamic>> _muscleAnatomyKnowledge = [
    {
      'title': 'Full Body',
      'keywords': ['full body', 'whole body', 'total body'],
      'desc': 'FULL BODY: Engages multiple major muscle groups simultaneously (Chest, Back, Legs, Core), maximizing calorie expenditure, functional neuromuscular coordination, and systemic hormonal response.',
    },
    {
      'title': 'Abs',
      'keywords': ['abs', 'abdominals', 'rectus abdominis', 'six pack'],
      'desc': 'ABS (Rectus Abdominis): Flexes the lumbar spine and compresses abdominal cavity. Best targeted through controlled spinal flexion like Crunches and isometric anti-extension like Planks.',
    },
    {
      'title': 'Core',
      'keywords': ['core', 'midsection', 'transverse abdominis'],
      'desc': 'CORE COMPLEX: Includes transverse abdominis, internal/external obliques, multifidus, and pelvic floor. Stabilizes spine, transfers kinetic energy between upper and lower extremities, and protects internal organs.',
    },
    {
      'title': 'Chest',
      'keywords': ['chest', 'pectoral', 'pecs', 'push up'],
      'desc': 'CHEST (Pectoralis Major & Minor): Drives horizontal adduction and pushing mechanics. Key home exercises include Standard, Wide, Diamond, and Incline/Decline Push-Ups.',
    },
    {
      'title': 'Back',
      'keywords': ['back', 'upper back', 'rhomboids'],
      'desc': 'BACK (Rhomboids, Middle Traps, Teres Major): Retracts and stabilizes scapula, promotes upright posture, and balances anterior pushing volume. Key movements: Cobra, Superman, Prone Y-T-W raises.',
    },
    {
      'title': 'Shoulders',
      'keywords': ['shoulder', 'deltoid', 'delts'],
      'desc': 'SHOULDERS (Deltoids - Anterior, Lateral, Posterior): Provides 360-degree shoulder mobility and overhead pushing power. Pike Push-Ups and Lateral Arm Raises target all deltoid heads.',
    },
    {
      'title': 'Biceps',
      'keywords': ['bicep', 'biceps', 'biceps brachii'],
      'desc': 'BICEPS BRACHII: Flexes elbow joint and supinates forearm. In calisthenics, targeted through Doorframe Rows with underhand grip or Chin-Ups.',
    },
    {
      'title': 'Triceps',
      'keywords': ['tricep', 'triceps', 'triceps brachii'],
      'desc': 'TRICEPS BRACHII: Comprises lateral, long, and medial heads responsible for elbow extension. Targeted through Diamond Push-Ups, Chair Dips, and Plank-to-Pushups.',
    },
    {
      'title': 'Forearms',
      'keywords': ['forearm', 'forearms', 'grip', 'wrist'],
      'desc': 'FOREARMS (Flexor & Extensor Carpi): Controls wrist flexion, extension, and crushing grip strength. Crucial for wrist joint longevity during heavy push-up volume.',
    },
    {
      'title': 'Arms',
      'keywords': ['arms', 'upper arm'],
      'desc': 'ARMS: Dual agonist-antagonist pairing of Biceps and Triceps for total pushing and pulling elbow articulacy.',
    },
    {
      'title': 'Quadriceps',
      'keywords': ['quad', 'quads', 'quadriceps', 'front thigh', 'thighs'],
      'desc': 'QUADRICEPS (Rectus Femoris, Vastus Lateralis/Medialis/Intermedius): Powerful knee extensors. Best developed with Bodyweight Squats, Lunges, and Wall Sits.',
    },
    {
      'title': 'Hamstrings',
      'keywords': ['hamstring', 'hamstrings', 'back thigh', 'posterior thigh'],
      'desc': 'HAMSTRINGS (Biceps Femoris, Semitendinosus, Semimembranosus): Knee flexion and hip extension. Targeted via Glute Bridges with extended foot position and Single-Leg Good Mornings.',
    },
    {
      'title': 'Glutes',
      'keywords': ['glute', 'glutes', 'gluteus', 'butt', 'hips'],
      'desc': 'GLUTEAL GROUP (Gluteus Maximus, Medius, Minimus): Largest power generator in the human body for hip extension and pelvic stabilization. Best trained via Glute Bridges, Frog Pumps, and Bulgarian Split Squats.',
    },
    {
      'title': 'Calves',
      'keywords': ['calf', 'calves', 'gastrocnemius', 'soleus'],
      'desc': 'CALVES (Gastrocnemius & Soleus): Plantar flexes ankle joint and absorbs ground reaction forces. Targeted via Single-Leg Calf Raises and Jumping Rope/Jacks.',
    },
    {
      'title': 'Legs',
      'keywords': ['leg', 'legs', 'lower body'],
      'desc': 'LEGS: Complete lower body kinetic chain encompassing Quads, Hamstrings, Glutes, and Calves for locomotion and vertical power.',
    },
  ];

  static RagSearchResult retrieve({
    required String query,
    required UserProfile? userProfile,
    required List<Exercise> allExercises,
    required List<WorkoutPlan> allPlans,
    List<WorkoutSchedule> allSchedules = const [],
    List<WorkoutSession> recentSessions = const [],
    int streakDays = 0,
  }) {
    final qLower = query.toLowerCase();
    final queryTokens = qLower.split(RegExp(r'[\s,?.!]+')).where((t) => t.length > 2).toList();

    final matchedExercises = <Exercise>[];
    final matchedPlans = <WorkoutPlan>[];
    final matchedSchedules = <WorkoutSchedule>[];
    final sourceItems = <RagSourceItem>[];
    final contextLines = <String>[];

    // 1. User Telemetry Source Tag
    final streak = streakDays;
    final level = userProfile?.level ?? 1;
    final xp = userProfile?.xpPoints ?? 0;
    final goal = userProfile?.fitnessGoal ?? 'General Fitness';

    sourceItems.add(
      RagSourceItem(
        title: 'Live Stats: Streak & Level',
        category: 'TELEMETRY',
        detail: '$streak-Day Streak • Level $level ($xp XP) • Goal: $goal',
      ),
    );
    contextLines.add('--- USER PROFILE & LIVE TELEMETRY ---');
    contextLines.add('Name: ${userProfile?.name ?? "Athlete"}, Level: $level, XP: $xp, Streak: $streak days, Goal: $goal, Duration: ${userProfile?.preferredDuration ?? 30} mins');

    // 2. Score Exercises
    final scoredExercises = allExercises.map((ex) {
      int score = 0;
      final nameLower = ex.name.toLowerCase();
      final catLower = ex.category.toLowerCase();
      final musclesLower = ex.targetMuscles.toLowerCase();

      if (qLower.contains(nameLower)) score += 10;
      if (qLower.contains(catLower)) score += 5;

      for (final t in queryTokens) {
        if (nameLower.contains(t)) score += 4;
        if (catLower.contains(t)) score += 3;
        if (musclesLower.contains(t)) score += 3;
      }
      return MapEntry(ex, score);
    }).where((e) => e.value > 0).toList()
      ..sort((a, b) => b.value.compareTo(a.value));

    for (final entry in scoredExercises.take(3)) {
      final ex = entry.key;
      matchedExercises.add(ex);
      sourceItems.add(
        RagSourceItem(
          title: ex.name,
          category: 'EXERCISE',
          detail: '${ex.targetMuscles} • ${ex.difficulty}',
          exercise: ex,
        ),
      );
      contextLines.add('EXERCISE: ${ex.name} | Category: ${ex.category} | Difficulty: ${ex.difficulty} | Muscles: ${ex.targetMuscles} | Form Tips: ${ex.formTips}');
    }

    // 3. Score Workout Plans
    final scoredPlans = allPlans.map((plan) {
      int score = 0;
      final nameLower = plan.name.toLowerCase();
      final catLower = plan.category.toLowerCase();
      final descLower = plan.description.toLowerCase();

      if (qLower.contains(nameLower)) score += 10;
      if (qLower.contains(catLower)) score += 5;

      for (final t in queryTokens) {
        if (nameLower.contains(t)) score += 4;
        if (catLower.contains(t)) score += 3;
        if (descLower.contains(t)) score += 2;
      }
      return MapEntry(plan, score);
    }).where((e) => e.value > 0).toList()
      ..sort((a, b) => b.value.compareTo(a.value));

    for (final entry in scoredPlans.take(2)) {
      final plan = entry.key;
      matchedPlans.add(plan);
      sourceItems.add(
        RagSourceItem(
          title: plan.name,
          category: 'PLAN',
          detail: '${plan.estimatedDurationMinutes} mins • ${plan.category}',
          workoutPlan: plan,
        ),
      );
      contextLines.add('WORKOUT PLAN: ${plan.name} | Category: ${plan.category} | Duration: ${plan.estimatedDurationMinutes} mins | Target Muscles: ${plan.targetMusclesDescription}');
    }

    // 4. Workout Schedules
    final isScheduleQuery = queryTokens.any((t) => [
          'schedule', 'routine', 'today', 'tomorrow', 'when', 'time', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'
        ].contains(t));

    if (isScheduleQuery && allSchedules.isNotEmpty) {
      final enabled = allSchedules.where((s) => s.isEnabled).toList();
      matchedSchedules.addAll(enabled);
      final days = {1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun'};
      for (final s in enabled.take(3)) {
        sourceItems.add(
          RagSourceItem(
            title: '${days[s.dayOfWeek]}: ${s.planName}',
            category: 'SCHEDULE',
            detail: '${s.timeHour.toString().padLeft(2, '0')}:${s.timeMinute.toString().padLeft(2, '0')}',
          ),
        );
        contextLines.add('SCHEDULE: ${days[s.dayOfWeek]} at ${s.timeHour}:${s.timeMinute.toString().padLeft(2, '0')} -> ${s.planName}');
      }
    }

    // 5. Gym Trainers Knowledge (Coach Cobie & Coach Jun)
    if (qLower.contains('cobie') || qLower.contains('jun') || qLower.contains('coach') || qLower.contains('trainer')) {
      sourceItems.add(
        const RagSourceItem(
          title: 'Coach Cobie & Coach Jun',
          category: 'TRAINER',
          detail: 'Stationed on Gym Floor for Form Checks & Consultations',
        ),
      );
      contextLines.add('GYM TRAINERS: Coach Cobie (Head Strength & Hypertrophy, Mon-Sat 6am-2pm) and Coach Jun (Functional Conditioning & Mobility, Mon-Sat 1pm-9pm) are on the gym floor.');
    }

    // 6. Sports Science & Muscle Anatomy Match
    for (final chunk in _sportsScienceKnowledge) {
      if (chunk.key.any((kw) => qLower.contains(kw))) {
        sourceItems.add(
          RagSourceItem(
            title: 'Sports Science',
            category: 'SCIENCE',
            detail: chunk.value.split(':').first,
          ),
        );
        contextLines.add(chunk.value);
      }
    }

    for (final anat in _muscleAnatomyKnowledge) {
      final keywords = anat['keywords'] as List<String>;
      if (keywords.any((kw) => qLower.contains(kw))) {
        sourceItems.add(
          RagSourceItem(
            title: anat['title'] as String,
            category: 'ANATOMY',
            detail: 'Biomechanics & Drivers',
          ),
        );
        contextLines.add(anat['desc'] as String);
      }
    }

    // Build synthesized local answer
    final directBuffer = StringBuffer();
    if (matchedExercises.isNotEmpty) {
      final primary = matchedExercises.first;
      directBuffer.writeln('For ${primary.name}: Target muscles are ${primary.targetMuscles}. Focus on controlled tempo and full range of motion.');
      if (primary.formTips.isNotEmpty) {
        directBuffer.writeln('Form Tip: ${primary.formTips}');
      }
    } else if (matchedPlans.isNotEmpty) {
      final primaryPlan = matchedPlans.first;
      directBuffer.writeln('Recommended Plan: ${primaryPlan.name} (${primaryPlan.estimatedDurationMinutes} mins, ${primaryPlan.difficulty}). ${primaryPlan.description}');
    } else {
      directBuffer.writeln('BodyGrind AI Coach recommendation based on your ${userProfile?.fitnessGoal ?? "fitness"} goal: Maintain consistency, warm up properly, and hydrate.');
    }

    directBuffer.writeln('\nDisclaimer: Consult a physician before beginning any new exercise regimen.');

    return RagSearchResult(
      augmentedContext: contextLines.join('\n'),
      retrievedSources: sourceItems,
      matchedExercises: matchedExercises,
      matchedPlans: matchedPlans,
      matchedSchedules: matchedSchedules,
      directLocalAnswer: directBuffer.toString(),
    );
  }
}
