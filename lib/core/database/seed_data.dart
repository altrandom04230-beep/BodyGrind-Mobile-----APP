import '../../data/models/exercise.dart';
import '../../data/models/workout_plan.dart';
import '../../data/models/badge.dart';
import '../../data/models/workout_schedule.dart';
import '../../data/models/body_measurement.dart';

/// Seed data matching com.example.data.local.DataSeeder.kt exactly
class SeedData {
  SeedData._();

  static List<BodyMeasurement> get initialMeasurements {
    final now = DateTime.now().millisecondsSinceEpoch;
    const dayMs = 86400000;
    return [
      BodyMeasurement(
        timestamp: now - 75 * dayMs,
        weightKg: 72.8,
        heightCm: 175.0,
        chestCm: 98.0,
        armCm: 34.0,
        waistCm: 85.5,
        bmi: 23.77,
        bmiCategory: 'Normal weight',
        notes: 'Starting 3-month check-in',
      ),
      BodyMeasurement(
        timestamp: now - 50 * dayMs,
        weightKg: 72.0,
        heightCm: 175.0,
        chestCm: 97.8,
        armCm: 34.2,
        waistCm: 84.8,
        bmi: 23.51,
        bmiCategory: 'Normal weight',
        notes: 'Cutting phase entry',
      ),
      BodyMeasurement(
        timestamp: now - 30 * dayMs,
        weightKg: 71.4,
        heightCm: 175.0,
        chestCm: 97.2,
        armCm: 34.6,
        waistCm: 84.0,
        bmi: 23.31,
        bmiCategory: 'Normal weight',
        notes: '1 month check-in',
      ),
      BodyMeasurement(
        timestamp: now - 14 * dayMs,
        weightKg: 70.7,
        heightCm: 175.0,
        chestCm: 96.8,
        armCm: 34.8,
        waistCm: 83.0,
        bmi: 23.08,
        bmiCategory: 'Normal weight',
        notes: 'Steady progress',
      ),
      BodyMeasurement(
        timestamp: now - 2 * dayMs,
        weightKg: 70.0,
        heightCm: 175.0,
        chestCm: 96.5,
        armCm: 35.0,
        waistCm: 82.0,
        bmi: 22.86,
        bmiCategory: 'Normal weight',
        notes: 'Target weight reached',
      ),
    ];
  }

  static List<Exercise> get exercises => [
        const Exercise(
          id: 'jumping_jacks',
          name: 'Jumping Jacks',
          category: 'Full Body',
          targetMuscles: 'Full Body, Calves, Shoulders',
          difficulty: 'Beginner',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Start with feet together and arms at your sides. Jump feet out to the sides while raising arms overhead. Jump back to starting position rapidly.',
          tips: 'Keep knees slightly bent on landing to absorb shock. Maintain a rhythmic breathing pattern.',
          caloriesPerMinute: 8.5,
          animationType: 'JUMPING_JACKS',
          primaryMuscles: 'Gastrocnemius, Soleus (Calves), Deltoids',
          secondaryMuscles: 'Glutes, Hip Abductors, Core Stabilizers',
          educationalDescription:
              'A foundational plyometric calisthenic that raises heart rate, stimulates lymphatic circulation, and activates the entire kinetic chain with zero equipment.',
          functionalBenefit:
              'Enhances cardiovascular responsiveness, ankle elasticity, coordination, and rapid metabolic calorie burn.',
        ),
        const Exercise(
          id: 'push_ups',
          name: 'Push-Ups',
          category: 'Chest',
          targetMuscles: 'Chest, Shoulders, Triceps, Core',
          difficulty: 'Beginner',
          defaultType: 'REPS',
          defaultCount: 12,
          instructions:
              'Start in a high plank position with hands slightly wider than shoulder-width. Lower your chest until it nearly touches the floor, keeping body in a straight line, then push back up.',
          tips: "Don't let your hips sag or hike up. Elbows should flare out at about a 45-degree angle.",
          caloriesPerMinute: 7.0,
          animationType: 'PUSH_UP',
          primaryMuscles: 'Pectoralis Major (Chest), Triceps Brachii',
          secondaryMuscles: 'Anterior Deltoids, Serratus Anterior, Rectus Abdominis',
          educationalDescription:
              'The premier closed-kinetic-chain horizontal press. It drives upper-body pushing hypertrophy while demanding strict anterior core bracing.',
          functionalBenefit:
              'Increases functional upper-body pushing power, improves shoulder stability, and prevents rotational chest asymmetries.',
        ),
        const Exercise(
          id: 'squats',
          name: 'Bodyweight Squats',
          category: 'Leg',
          targetMuscles: 'Quadriceps, Hamstrings, Glutes',
          difficulty: 'Beginner',
          defaultType: 'REPS',
          defaultCount: 16,
          instructions:
              'Stand with feet shoulder-width apart. Push hips back and bend knees to lower your body as if sitting in a chair. Lower until thighs are parallel to ground, then press through heels to stand.',
          tips: 'Keep chest tall and core engaged. Ensure knees track over toes and do not cave inward.',
          caloriesPerMinute: 7.5,
          animationType: 'SQUAT',
          primaryMuscles: 'Quadriceps Femoris, Gluteus Maximus',
          secondaryMuscles: 'Hamstrings, Soleus, Transverse Abdominis',
          educationalDescription:
              'A primal compound knee-dominant pattern that stimulates major lower-limb muscle groups, driving systemic growth hormone release and hip mobility.',
          functionalBenefit:
              'Directly improves jumping power, stair-climbing stamina, knee joint resilience, and hip flexibility.',
        ),
        const Exercise(
          id: 'plank',
          name: 'Plank Hold',
          category: 'Abs',
          targetMuscles: 'Core, Abs, Lower Back, Shoulders',
          difficulty: 'Beginner',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Rest on your forearms and toes. Keep your entire body in a rigid straight line from head to heels. Engage your glutes and core deeply.',
          tips: 'Breathe steadily. Avoid arching your lower back or dropping your head.',
          caloriesPerMinute: 5.0,
          animationType: 'PLANK',
          primaryMuscles: 'Transverse Abdominis, Rectus Abdominis',
          secondaryMuscles: 'Internal & External Obliques, Erector Spinae, Gluteals',
          educationalDescription:
              'An isometric anti-extension core discipline that conditions the abdominal wall to protect the lumbar spine against shearing forces.',
          functionalBenefit:
              'Eliminates lower-back postural strain, builds a tight natural weight-belt waistline, and stabilizes upright athletic posture.',
        ),
        const Exercise(
          id: 'abdominal_crunches',
          name: 'Abdominal Crunches',
          category: 'Abs',
          targetMuscles: 'Upper Abs, Core',
          difficulty: 'Beginner',
          defaultType: 'REPS',
          defaultCount: 15,
          instructions:
              'Lie on your back with knees bent and feet flat on the floor. Place fingertips behind your head. Curl shoulders upward toward knees, contracting abs, then lower slowly.',
          tips: 'Do not pull on your neck with your hands. Focus on contracting your abdomen.',
          caloriesPerMinute: 6.0,
          animationType: 'CRUNCHES',
          primaryMuscles: 'Rectus Abdominis (Upper Fibers)',
          secondaryMuscles: 'External Obliques, Transverse Abdominis',
          educationalDescription:
              'Isolates trunk flexion by bringing the rib cage closer to the pelvis, achieving peak concentric contraction in the abdominal muscle bellies.',
          functionalBenefit:
              'Develops distinct abdominal definition, improves torso curling mobility, and strengthens anterior core stamina.',
        ),
        const Exercise(
          id: 'lunges',
          name: 'Alternating Lunges',
          category: 'Leg',
          targetMuscles: 'Quads, Glutes, Hamstrings, Calves',
          difficulty: 'Beginner',
          defaultType: 'REPS',
          defaultCount: 14,
          instructions:
              'Step forward with one leg and lower your hips until both knees are bent at about a 90-degree angle. Push off front foot to return and switch legs.',
          tips: 'Keep front knee aligned above ankle. Keep torso upright.',
          caloriesPerMinute: 7.0,
          animationType: 'LUNGE',
          primaryMuscles: 'Quadriceps, Gluteus Maximus',
          secondaryMuscles: 'Hamstrings, Calves, Hip Abductors & Adductors',
          educationalDescription:
              'A unilateral split-stance movement that corrects bilateral strength discrepancies, demands pelvic equilibrium, and challenges hip stabilizers.',
          functionalBenefit:
              'Eliminates left-right leg muscle imbalances, improves sprint propulsion, and lengthens tight hip flexors.',
        ),
        const Exercise(
          id: 'mountain_climbers',
          name: 'Mountain Climbers',
          category: 'Fat-Burning',
          targetMuscles: 'Core, Shoulders, Hip Flexors, Cardio',
          difficulty: 'Intermediate',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Start in a plank position. Drive one knee toward your chest quickly, then switch legs in a running motion while maintaining a stable upper body.',
          tips: 'Keep hips level and do not let them bounce high in the air.',
          caloriesPerMinute: 10.0,
          animationType: 'MOUNTAIN_CLIMBERS',
          primaryMuscles: 'Rectus Abdominis, Iliopsoas (Hip Flexors)',
          secondaryMuscles: 'Anterior Deltoids, Pectoralis Major, Quadriceps',
          educationalDescription:
              'Dynamic high-cadence plank progression combining isometric shoulder/core stability with rapid alternating hip flexion for cardiovascular conditioning.',
          functionalBenefit:
              'Builds athletic agility, explosive leg turnover, and elevated metabolic afterburn.',
        ),
        const Exercise(
          id: 'high_knees',
          name: 'High Knees',
          category: 'Fat-Burning',
          targetMuscles: 'Quads, Calves, Hip Flexors, Cardiovascular',
          difficulty: 'Beginner',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Run in place while driving your knees up to waist height with each stride, pumping arms vigorously in sync.',
          tips: 'Stay on the balls of your feet and maintain a brisk cadence.',
          caloriesPerMinute: 9.5,
          animationType: 'HIGH_KNEES',
          primaryMuscles: 'Psoas Major, Iliacus, Quadriceps',
          secondaryMuscles: 'Gastrocnemius, Soleus, Abdominal Slings',
          educationalDescription:
              'Explosive running drill emphasizing triple flexion (hip, knee, ankle) at a high stroke rate to maximize VO2 and sprint mechanics.',
          functionalBenefit:
              'Enhances sprinting power, cardiorespiratory endurance, and hip flexor responsiveness.',
        ),
        const Exercise(
          id: 'wall_sit',
          name: 'Wall Sit',
          category: 'Leg',
          targetMuscles: 'Quadriceps, Glutes, Calves',
          difficulty: 'Intermediate',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Slide your back down a wall until thighs are parallel to ground, knees bent at 90 degrees. Hold position with arms folded or at sides.',
          tips: 'Press lower back firmly against the wall. Knees should not extend past toes.',
          caloriesPerMinute: 5.5,
          animationType: 'WALL_SIT',
          primaryMuscles: 'Quadriceps (Rectus Femoris, Vastus Medialis/Lateralis)',
          secondaryMuscles: 'Glutes, Hamstrings, Gastrocnemius',
          educationalDescription:
              'Static 90-degree isometric squat against vertical resistance that stresses slow-twitch endurance fibers and strengthens patellar tendons.',
          functionalBenefit:
              'Relieves knee pain through tendon adaptation, improves skiing/skating posture, and builds mental toughness.',
        ),
        const Exercise(
          id: 'bicycle_crunches',
          name: 'Bicycle Crunches',
          category: 'Abs',
          targetMuscles: 'Obliques, Lower Abs, Hip Flexors',
          difficulty: 'Intermediate',
          defaultType: 'REPS',
          defaultCount: 20,
          instructions:
              'Lie on back with hands behind head, knees raised. Rotate torso to bring right elbow toward left knee while extending right leg. Alternate sides smoothly.',
          tips: 'Move with controlled tempo rather than rushing; squeeze obliques on each twist.',
          caloriesPerMinute: 7.5,
          animationType: 'BICYCLE_CRUNCHES',
          primaryMuscles: 'Internal & External Obliques, Rectus Abdominis',
          secondaryMuscles: 'Iliopsoas, Transverse Abdominis',
          educationalDescription:
              'Combines rotational torso twist with contralateral knee drive, recruiting both transverse abdominis and oblique slings in a continuous kinetic cycle.',
          functionalBenefit:
              'Shrinks waistline looseness, fortifies rotational torso power, and sculpts side abdominal lines.',
        ),
        const Exercise(
          id: 'leg_raises',
          name: 'Leg Raises',
          category: 'Abs',
          targetMuscles: 'Lower Abs, Hip Flexors',
          difficulty: 'Intermediate',
          defaultType: 'REPS',
          defaultCount: 14,
          instructions:
              'Lie flat on back with legs straight. Slowly lift legs up until perpendicular to torso, then lower them down slowly without touching floor.',
          tips: 'Press lower back firmly into the floor throughout the movement.',
          caloriesPerMinute: 6.5,
          animationType: 'LEG_RAISES',
          primaryMuscles: 'Lower Rectus Abdominis, Iliopsoas',
          secondaryMuscles: 'Tensor Fasciae Latae, Adductors',
          educationalDescription:
              'Posterior pelvic tilt exercise that lifts the heavy lever of the legs against gravity, demanding maximum lower abdominal contraction.',
          functionalBenefit:
              'Strengthens deep pelvic stabilizers and eliminates lower-belly weakness.',
        ),
        const Exercise(
          id: 'arm_circles',
          name: 'Arm Circles',
          category: 'Warm-Up',
          targetMuscles: 'Shoulders, Deltoids, Upper Back',
          difficulty: 'Beginner',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Extend arms straight out to the sides at shoulder height. Make continuous circular motions forward, then reverse halfway through.',
          tips: 'Keep shoulders relaxed down away from ears. Keep core tight.',
          caloriesPerMinute: 4.5,
          animationType: 'ARM_CIRCLES',
          primaryMuscles: 'Anterior, Lateral & Posterior Deltoids',
          secondaryMuscles: 'Supraspinatus, Infraspinatus, Trapezius',
          educationalDescription:
              'Continuous circular circumduction that stimulates synovial fluid secretion throughout the glenohumeral capsule.',
          functionalBenefit:
              'Prevents shoulder impingement, warms rotator cuff muscles, and improves overhead mobility.',
        ),
        const Exercise(
          id: 'cobra_stretch',
          name: 'Cobra Stretch',
          category: 'Bedtime',
          targetMuscles: 'Abs, Spine, Lower Back, Chest',
          difficulty: 'Beginner',
          defaultType: 'TIME',
          defaultCount: 30,
          instructions:
              'Lie face down with palms near shoulders. Gently push chest up while keeping hips grounded, gazing slightly upwards.',
          tips: 'Do not force lumbar spine into deep arch. Breathe deeply and relax.',
          caloriesPerMinute: 3.5,
          animationType: 'COBRA_STRETCH',
          primaryMuscles: 'Erector Spinae, Multifidus',
          secondaryMuscles: 'Pectorals, Abdominals, Anterior Neck',
          educationalDescription:
              'A gentle prone spinal extension posture that counteracts chronic desk hunches and decompresses anterior intervertebral discs.',
          functionalBenefit:
              'Restores spinal lordosis, opens tight chest muscles, and relieves postural back ache.',
        ),
        const Exercise(
          id: 'triceps_dips',
          name: 'Chair Triceps Dips',
          category: 'Arm',
          targetMuscles: 'Triceps, Front Shoulders',
          difficulty: 'Beginner',
          defaultType: 'REPS',
          defaultCount: 12,
          instructions:
              'Sit on edge of a sturdy chair/couch, hands beside hips. Slide butt off edge and bend elbows to 90 degrees to lower body, then push back up.',
          tips: 'Keep back close to the chair edge. Do not sink into neck.',
          caloriesPerMinute: 6.0,
          animationType: 'TRICEPS_DIPS',
          primaryMuscles: 'Triceps Brachii (Lateral, Long & Medial Heads)',
          secondaryMuscles: 'Anterior Deltoids, Pectoralis Minor, Rhomboids',
          educationalDescription:
              'Bodyweight elbow extension that isolates the posterior arm muscles under body mass leverage.',
          functionalBenefit:
              'Builds defined arm extension strength, locks out pushing movements, and tones the back of arms.',
        ),
        const Exercise(
          id: 'diamond_push_ups',
          name: 'Diamond Push-Ups',
          category: 'Arm',
          targetMuscles: 'Triceps, Inner Chest, Core',
          difficulty: 'Advanced',
          defaultType: 'REPS',
          defaultCount: 10,
          instructions:
              'Form push-up position with thumbs and index fingers touching to create a diamond shape under center chest. Lower chest and press up.',
          tips: 'Keep elbows tucked close to sides throughout.',
          caloriesPerMinute: 8.0,
          animationType: 'DIAMOND_PUSH_UP',
          primaryMuscles: 'Triceps Brachii, Sternal Pectoralis',
          secondaryMuscles: 'Anterior Deltoids, Serratus Anterior, Core',
          educationalDescription:
              'Close-hand push-up variation that dramatically shifts mechanical moment arms onto the elbow extensors and inner chest fibers.',
          functionalBenefit:
              'Massively increases triceps horsepower and pushing lockout capability.',
        ),
        const Exercise(
          id: 'butt_bridge',
          name: 'Glute Bridge',
          category: 'Butt',
          targetMuscles: 'Glutes, Hamstrings, Lower Back',
          difficulty: 'Beginner',
          defaultType: 'REPS',
          defaultCount: 16,
          instructions:
              'Lie on back with knees bent and feet flat. Squeeze glutes and raise hips toward ceiling until thighs and torso align. Lower gently.',
          tips: 'Pause at top for 1-2 seconds to maximize glute contraction.',
          caloriesPerMinute: 5.5,
          animationType: 'GLUTE_BRIDGE',
          primaryMuscles: 'Gluteus Maximus, Hamstrings',
          secondaryMuscles: 'Erector Spinae, Transverse Abdominis',
          educationalDescription:
              'Hip hinge supine extension that activates dormant gluteal fibers without placing axial spinal compressive loads.',
          functionalBenefit:
              'Eliminates glute amnesia from prolonged sitting, reduces lower back pain, and powers sprinting strides.',
        ),
      ];

  static List<WorkoutPlan> get plans {
    final now = DateTime.now().millisecondsSinceEpoch;
    return [
      WorkoutPlan(
        id: 'plan_full_body_beg',
        name: 'Full Body Starter',
        description: 'Classic balanced no-equipment workout to build foundational strength and rev metabolism.',
        category: 'Full Body',
        difficulty: 'Beginner',
        estimatedDurationMinutes: 12,
        createdAt: now,
        targetMusclesDescription: 'Full Body, Chest, Quads, Core, Glutes',
        educationalOverview:
            'Designed to activate major compound muscle groups systematically. Balances pushing, knee-dominant squatting, and anti-extension core stabilization for holistic metabolic priming.',
      ),
      WorkoutPlan(
        id: 'plan_abs_beginner',
        name: 'Abs Sculpt Beginner',
        description: 'Target upper and lower abdominal muscles with zero neck strain for a firm core.',
        category: 'Abs',
        difficulty: 'Beginner',
        estimatedDurationMinutes: 10,
        createdAt: now,
        targetMusclesDescription: 'Rectus Abdominis, Obliques, Transverse Abdominis',
        educationalOverview:
            'Emphasizes multi-angle core activation: concentric upper curling, isometric hollow-body planking, and rotational oblique engagement for total trunk stability.',
      ),
      WorkoutPlan(
        id: 'plan_chest_builder',
        name: 'Chest & Upper Body Blast',
        description: 'High-impact push-up variations to broaden chest definition and arm power.',
        category: 'Chest',
        difficulty: 'Intermediate',
        estimatedDurationMinutes: 14,
        createdAt: now,
        targetMusclesDescription: 'Pectoralis Major, Anterior Deltoids, Triceps',
        educationalOverview:
            'Combines standard leverage push-ups with diamond close-grip presses and triceps dips to thoroughly exhaust horizontal push motor units.',
      ),
      WorkoutPlan(
        id: 'plan_fat_burning_hiit',
        name: 'Fat-Burning HIIT Rush',
        description: 'Cardio-intensive interval drills that maximize post-exercise oxygen burn.',
        category: 'Fat-Burning',
        difficulty: 'Intermediate',
        estimatedDurationMinutes: 15,
        createdAt: now,
        targetMusclesDescription: 'Cardiovascular, Quads, Calves, Core',
        educationalOverview:
            'Leverages short, intense bursts of high knees and mountain climbers to trigger rapid lactate accumulation and high EPOC calorie consumption.',
      ),
      WorkoutPlan(
        id: 'plan_leg_power',
        name: 'Leg & Glute Toner',
        description: 'Strengthen quads, hamstrings, and glutes with zero gym equipment.',
        category: 'Leg',
        difficulty: 'Beginner',
        estimatedDurationMinutes: 13,
        createdAt: now,
        targetMusclesDescription: 'Quadriceps, Gluteus Maximus, Hamstrings, Calves',
        educationalOverview:
            'Alternates bilateral squat depth with unilateral lunge stability and wall-sit time under tension to build strong, shapely lower limbs.',
      ),
      WorkoutPlan(
        id: 'plan_arms_toning',
        name: 'Arm & Shoulder Define',
        description: 'Build defined triceps, sculpted shoulders, and grip endurance.',
        category: 'Arm',
        difficulty: 'Intermediate',
        estimatedDurationMinutes: 12,
        createdAt: now,
        targetMusclesDescription: 'Triceps Brachii, Deltoids, Forearms',
        educationalOverview:
            'Zero-equipment arm routine utilizing bodyweight angles, chair dips, diamond positions, and rotational holds to isolate arm extensors.',
      ),
      WorkoutPlan(
        id: 'plan_morning_wake',
        name: 'Morning Energy Awakening',
        description: 'Invigorating mobility and gentle body activation to start the day charged.',
        category: 'Morning',
        difficulty: 'Beginner',
        estimatedDurationMinutes: 8,
        createdAt: now,
        targetMusclesDescription: 'Shoulders, Spine, Quads, Full Body',
        educationalOverview:
            'Gently elevates resting heart rate, lubricates synovial joints, and activates dormant spinal stabilizers after sleep.',
      ),
      WorkoutPlan(
        id: 'plan_bedtime_stretch',
        name: 'Bedtime Tension Relief',
        description: 'Calming spinal decompression and soothing stretches for deep restorative sleep.',
        category: 'Bedtime',
        difficulty: 'Beginner',
        estimatedDurationMinutes: 10,
        createdAt: now,
        targetMusclesDescription: 'Erector Spinae, Glutes, Hamstrings, Chest',
        educationalOverview:
            'Triggers the parasympathetic nervous system through sustained cobra and bridge extensions, lowering cortisol and releasing postural tension.',
      ),
      WorkoutPlan(
        id: 'plan_7_day_challenge',
        name: '7-Day Total Fitness Kickstart',
        description: 'A progressive 7-day challenge designed to build habit, stamina, and full-body tone.',
        category: 'Full Body',
        difficulty: 'Beginner',
        estimatedDurationMinutes: 15,
        isChallenge: true,
        totalDays: 7,
        currentDay: 1,
        createdAt: now,
        targetMusclesDescription: 'Full Body, Core, Chest, Legs',
        educationalOverview:
            'Progressive daily stimulus engineered to habituate the central nervous system to daily exercise without overreaching.',
      ),
      WorkoutPlan(
        id: 'plan_30_day_transformation',
        name: '30-Day Home Transformation',
        description: 'Our signature month-long challenge taking you from beginner to peak fitness condition.',
        category: 'Full Body',
        difficulty: 'Intermediate',
        estimatedDurationMinutes: 18,
        isChallenge: true,
        totalDays: 30,
        currentDay: 1,
        createdAt: now,
        targetMusclesDescription: 'Full Body Hypertrophy & Conditioning',
        educationalOverview:
            'Comprehensive periodized program cycling through endurance, hypertrophy, and active recovery days to deliver tangible body transformation.',
      ),
    ];
  }

  static List<WorkoutPlanExercise> get planExercises => [
        // Full Body Starter
        const WorkoutPlanExercise(planId: 'plan_full_body_beg', exerciseId: 'jumping_jacks', orderIndex: 0, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_full_body_beg', exerciseId: 'squats', orderIndex: 1, type: 'REPS', count: 14, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_full_body_beg', exerciseId: 'push_ups', orderIndex: 2, type: 'REPS', count: 10, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_full_body_beg', exerciseId: 'lunges', orderIndex: 3, type: 'REPS', count: 12, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_full_body_beg', exerciseId: 'plank', orderIndex: 4, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_full_body_beg', exerciseId: 'cobra_stretch', orderIndex: 5, type: 'TIME', count: 25, restSeconds: 0),

        // Abs Sculpt Beginner
        const WorkoutPlanExercise(planId: 'plan_abs_beginner', exerciseId: 'jumping_jacks', orderIndex: 0, type: 'TIME', count: 25, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_abs_beginner', exerciseId: 'abdominal_crunches', orderIndex: 1, type: 'REPS', count: 16, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_abs_beginner', exerciseId: 'mountain_climbers', orderIndex: 2, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_abs_beginner', exerciseId: 'leg_raises', orderIndex: 3, type: 'REPS', count: 12, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_abs_beginner', exerciseId: 'bicycle_crunches', orderIndex: 4, type: 'REPS', count: 18, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_abs_beginner', exerciseId: 'plank', orderIndex: 5, type: 'TIME', count: 30, restSeconds: 0),

        // Chest & Upper Body Blast
        const WorkoutPlanExercise(planId: 'plan_chest_builder', exerciseId: 'arm_circles', orderIndex: 0, type: 'TIME', count: 30, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_chest_builder', exerciseId: 'push_ups', orderIndex: 1, type: 'REPS', count: 14, restSeconds: 25),
        const WorkoutPlanExercise(planId: 'plan_chest_builder', exerciseId: 'triceps_dips', orderIndex: 2, type: 'REPS', count: 12, restSeconds: 25),
        const WorkoutPlanExercise(planId: 'plan_chest_builder', exerciseId: 'diamond_push_ups', orderIndex: 3, type: 'REPS', count: 8, restSeconds: 25),
        const WorkoutPlanExercise(planId: 'plan_chest_builder', exerciseId: 'push_ups', orderIndex: 4, type: 'REPS', count: 10, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_chest_builder', exerciseId: 'cobra_stretch', orderIndex: 5, type: 'TIME', count: 30, restSeconds: 0),

        // Fat-Burning HIIT
        const WorkoutPlanExercise(planId: 'plan_fat_burning_hiit', exerciseId: 'jumping_jacks', orderIndex: 0, type: 'TIME', count: 30, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_fat_burning_hiit', exerciseId: 'high_knees', orderIndex: 1, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_fat_burning_hiit', exerciseId: 'mountain_climbers', orderIndex: 2, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_fat_burning_hiit', exerciseId: 'squats', orderIndex: 3, type: 'REPS', count: 16, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_fat_burning_hiit', exerciseId: 'high_knees', orderIndex: 4, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_fat_burning_hiit', exerciseId: 'plank', orderIndex: 5, type: 'TIME', count: 35, restSeconds: 0),

        // Leg & Glute Toner
        const WorkoutPlanExercise(planId: 'plan_leg_power', exerciseId: 'squats', orderIndex: 0, type: 'REPS', count: 16, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_leg_power', exerciseId: 'lunges', orderIndex: 1, type: 'REPS', count: 14, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_leg_power', exerciseId: 'wall_sit', orderIndex: 2, type: 'TIME', count: 30, restSeconds: 25),
        const WorkoutPlanExercise(planId: 'plan_leg_power', exerciseId: 'butt_bridge', orderIndex: 3, type: 'REPS', count: 16, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_leg_power', exerciseId: 'squats', orderIndex: 4, type: 'REPS', count: 12, restSeconds: 0),

        // Morning Wake
        const WorkoutPlanExercise(planId: 'plan_morning_wake', exerciseId: 'arm_circles', orderIndex: 0, type: 'TIME', count: 30, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_morning_wake', exerciseId: 'jumping_jacks', orderIndex: 1, type: 'TIME', count: 25, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_morning_wake', exerciseId: 'squats', orderIndex: 2, type: 'REPS', count: 12, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_morning_wake', exerciseId: 'cobra_stretch', orderIndex: 3, type: 'TIME', count: 30, restSeconds: 0),

        // Bedtime Stretch
        const WorkoutPlanExercise(planId: 'plan_bedtime_stretch', exerciseId: 'arm_circles', orderIndex: 0, type: 'TIME', count: 30, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_bedtime_stretch', exerciseId: 'cobra_stretch', orderIndex: 1, type: 'TIME', count: 35, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_bedtime_stretch', exerciseId: 'butt_bridge', orderIndex: 2, type: 'REPS', count: 12, restSeconds: 15),
        const WorkoutPlanExercise(planId: 'plan_bedtime_stretch', exerciseId: 'plank', orderIndex: 3, type: 'TIME', count: 25, restSeconds: 0),

        // 7-day challenge
        const WorkoutPlanExercise(planId: 'plan_7_day_challenge', exerciseId: 'jumping_jacks', orderIndex: 0, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_7_day_challenge', exerciseId: 'squats', orderIndex: 1, type: 'REPS', count: 15, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_7_day_challenge', exerciseId: 'push_ups', orderIndex: 2, type: 'REPS', count: 12, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_7_day_challenge', exerciseId: 'mountain_climbers', orderIndex: 3, type: 'TIME', count: 30, restSeconds: 20),
        const WorkoutPlanExercise(planId: 'plan_7_day_challenge', exerciseId: 'plank', orderIndex: 4, type: 'TIME', count: 30, restSeconds: 0),
      ];

  static List<Badge> get badges => const [
        Badge(
          badgeId: 'first_workout',
          title: 'First Step',
          description: 'Complete your very first workout session',
          category: 'Milestone',
          iconName: 'flag',
        ),
        Badge(
          badgeId: 'streak_3',
          title: '3-Day Spark',
          description: 'Maintain a 3-day active workout streak',
          category: 'Streak',
          progressTarget: 3,
          iconName: 'local_fire_department',
        ),
        Badge(
          badgeId: 'streak_7',
          title: 'Iron Will',
          description: 'Achieve a full 7-day workout streak',
          category: 'Streak',
          progressTarget: 7,
          iconName: 'military_tech',
        ),
        Badge(
          badgeId: 'workout_5',
          title: 'High Five',
          description: 'Complete 5 total workout sessions',
          category: 'Workout',
          progressTarget: 5,
          iconName: 'fitness_center',
        ),
        Badge(
          badgeId: 'workout_20',
          title: 'Unstoppable',
          description: 'Finish 20 complete workout sessions',
          category: 'Workout',
          progressTarget: 20,
          iconName: 'emoji_events',
        ),
        Badge(
          badgeId: 'custom_creator',
          title: 'Master Architect',
          description: 'Build and complete your own custom workout plan',
          category: 'Milestone',
          iconName: 'edit_note',
        ),
        Badge(
          badgeId: 'calories_500',
          title: 'Calorie Torch',
          description: 'Burn over 500 estimated calories total',
          category: 'Milestone',
          progressTarget: 500,
          iconName: 'whatshot',
        ),
        Badge(
          badgeId: 'abs_master',
          title: 'Core of Steel',
          description: 'Complete 3 dedicated Abs workouts',
          category: 'Workout',
          progressTarget: 3,
          iconName: 'shield',
        ),
      ];

  static List<WorkoutSchedule> get defaultSchedules => const [
        WorkoutSchedule(id: 1, dayOfWeek: 1, planId: 'plan_full_body_beg', planName: 'Full Body Starter', timeHour: 8, timeMinute: 0, isEnabled: true),
        WorkoutSchedule(id: 2, dayOfWeek: 2, planId: 'plan_chest_builder', planName: 'Chest & Upper Body Blast', timeHour: 8, timeMinute: 0, isEnabled: false),
        WorkoutSchedule(id: 3, dayOfWeek: 3, planId: 'plan_abs_beginner', planName: 'Abs Sculpt Beginner', timeHour: 8, timeMinute: 0, isEnabled: true),
        WorkoutSchedule(id: 4, dayOfWeek: 4, planId: 'plan_leg_power', planName: 'Leg & Glute Toner', timeHour: 8, timeMinute: 0, isEnabled: false),
        WorkoutSchedule(id: 5, dayOfWeek: 5, planId: 'plan_fat_burning_hiit', planName: 'Fat-Burning HIIT Rush', timeHour: 8, timeMinute: 0, isEnabled: true),
        WorkoutSchedule(id: 6, dayOfWeek: 6, planId: 'plan_morning_wake', planName: 'Morning Energy Awakening', timeHour: 9, timeMinute: 0, isEnabled: false),
        WorkoutSchedule(id: 7, dayOfWeek: 7, planId: 'plan_bedtime_stretch', planName: 'Bedtime Tension Relief', timeHour: 20, timeMinute: 0, isEnabled: false),
      ];
}
