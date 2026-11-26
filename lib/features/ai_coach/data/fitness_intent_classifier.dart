class FitnessIntentResult {
  final bool isFitnessRelated;
  final String? refusalMessage;

  const FitnessIntentResult({
    required this.isFitnessRelated,
    this.refusalMessage,
  });
}

class FitnessIntentClassifier {
  static const List<String> _fitnessKeywords = [
    'workout', 'exercise', 'pushup', 'push-up', 'push up', 'squat', 'plank',
    'lunge', 'bicep', 'tricep', 'chest', 'back', 'abs', 'core', 'leg', 'shoulder',
    'muscle', 'hypertrophy', 'reps', 'set', 'rest', 'stretch', 'warmup', 'warm up',
    'cardio', 'hiit', 'burn', 'calorie', 'deficit', 'surplus', 'protein', 'diet',
    'meal', 'hydration', 'water', 'streak', 'level', 'xp', 'cobie', 'jun', 'coach',
    'trainer', 'bmi', 'weight', 'body', 'gym', 'calisthenic', 'posture', 'sore',
    'doms', 'recover', 'routine', 'plan', 'schedule', 'form', 'technique', 'arm',
    'glute', 'hamstring', 'quad', 'calf', 'trap', 'lat', 'oblique', 'mobility',
    'flexibility', 'injury', 'brace', 'breathe', 'tempo'
  ];

  static const List<String> _offTopicKeywords = [
    'digestive food', 'cure cancer', 'treat disease', 'medicine', 'prescription',
    'headache pill', 'stock market', 'crypto', 'coding', 'python', 'javascript',
    'flutter', 'politics', 'president', 'war', 'movie', 'celebrity'
  ];

  static FitnessIntentResult classify(String query) {
    final qLower = query.toLowerCase().trim();

    // Explicit off-topic check
    for (final off in _offTopicKeywords) {
      if (qLower.contains(off)) {
        return const FitnessIntentResult(
          isFitnessRelated: false,
          refusalMessage:
              'I am your AI Fitness Coach. I can only assist with gym workouts, exercises, training techniques, and fitness nutrition.',
        );
      }
    }

    // Keyword match
    for (final kw in _fitnessKeywords) {
      if (qLower.contains(kw)) {
        return const FitnessIntentResult(isFitnessRelated: true);
      }
    }

    // Default to true for open fitness Q&A unless clearly unrelated
    return const FitnessIntentResult(isFitnessRelated: true);
  }
}
