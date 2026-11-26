/// Application roles for role-based routing (Member vs Trainer)
enum AppRole {
  member,
  trainer;

  static AppRole fromString(String val) {
    return val.toUpperCase() == 'TRAINER' ? AppRole.trainer : AppRole.member;
  }

  String get key => this == AppRole.trainer ? 'TRAINER' : 'MEMBER';
}

/// Status of assigned gym member in Trainer portal
enum MemberStatus {
  active('Active'),
  needsAttention('Needs Attention'),
  paused('Paused');

  final String label;
  const MemberStatus(this.label);
}

/// Category of feedback given by gym trainers
enum FeedbackCategory {
  workoutFeedback('Workout Feedback'),
  exerciseRecommendation('Exercise Recommendations'),
  progressComment('Progress Comments'),
  trainingAdjustment('Training Adjustments'),
  fitnessGuidance('Fitness Guidance');

  final String label;
  const FeedbackCategory(this.label);
}
