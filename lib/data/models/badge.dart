/// Gamification Badge model matching BadgeEntity in Entities.kt
class Badge {
  final String badgeId;
  final String title;
  final String description;
  final String category; // Streak, Workout, Challenge, Milestone
  final bool unlocked;
  final int? unlockedAt;
  final int progressCurrent;
  final int progressTarget;
  final String iconName;

  const Badge({
    required this.badgeId,
    required this.title,
    required this.description,
    required this.category,
    this.unlocked = false,
    this.unlockedAt,
    this.progressCurrent = 0,
    this.progressTarget = 1,
    required this.iconName,
  });

  Badge copyWith({
    String? badgeId,
    String? title,
    String? description,
    String? category,
    bool? unlocked,
    int? unlockedAt,
    int? progressCurrent,
    int? progressTarget,
    String? iconName,
  }) {
    return Badge(
      badgeId: badgeId ?? this.badgeId,
      title: title ?? this.title,
      description: description ?? this.description,
      category: category ?? this.category,
      unlocked: unlocked ?? this.unlocked,
      unlockedAt: unlockedAt ?? this.unlockedAt,
      progressCurrent: progressCurrent ?? this.progressCurrent,
      progressTarget: progressTarget ?? this.progressTarget,
      iconName: iconName ?? this.iconName,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'badgeId': badgeId,
      'title': title,
      'description': description,
      'category': category,
      'unlocked': unlocked ? 1 : 0,
      'unlockedAt': unlockedAt,
      'progressCurrent': progressCurrent,
      'progressTarget': progressTarget,
      'iconName': iconName,
    };
  }

  factory Badge.fromMap(Map<String, dynamic> map) {
    return Badge(
      badgeId: map['badgeId'] as String,
      title: map['title'] as String,
      description: map['description'] as String,
      category: map['category'] as String,
      unlocked: (map['unlocked'] as num?) == 1,
      unlockedAt: (map['unlockedAt'] as num?)?.toInt(),
      progressCurrent: (map['progressCurrent'] as num?)?.toInt() ?? 0,
      progressTarget: (map['progressTarget'] as num?)?.toInt() ?? 1,
      iconName: map['iconName'] as String,
    );
  }
}
