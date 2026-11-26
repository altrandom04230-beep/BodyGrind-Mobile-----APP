/// Weekly Progress Record model matching WeeklyRecord in WeeklyProgressRepository.kt
class WeeklyRecord {
  final String id;
  final int weekNumber;
  final int year;
  final int calendarWeek;
  final int timestamp;
  final String memberName;
  final int memberAge;
  final double weightKg;
  final double heightCm;
  final String weightUnit;
  final String heightUnit;
  final double chestCm;
  final double armCm;
  final double waistCm;
  final String measurementUnit;

  const WeeklyRecord({
    required this.id,
    required this.weekNumber,
    required this.year,
    required this.calendarWeek,
    required this.timestamp,
    required this.memberName,
    required this.memberAge,
    required this.weightKg,
    required this.heightCm,
    this.weightUnit = 'kg',
    this.heightUnit = 'cm',
    required this.chestCm,
    required this.armCm,
    required this.waistCm,
    this.measurementUnit = 'cm',
  });

  double getDisplayWeight(String unit) =>
      unit.toLowerCase() == 'lb' ? weightKg * 2.20462 : weightKg;

  double getDisplayHeight(String unit) =>
      (unit.toLowerCase() == 'inches' || unit.toLowerCase() == 'in')
          ? heightCm / 2.54
          : heightCm;

  double getDisplayChest(String unit) =>
      (unit.toLowerCase() == 'inches' || unit.toLowerCase() == 'in')
          ? chestCm / 2.54
          : chestCm;

  double getDisplayArm(String unit) =>
      (unit.toLowerCase() == 'inches' || unit.toLowerCase() == 'in')
          ? armCm / 2.54
          : armCm;

  double getDisplayWaist(String unit) =>
      (unit.toLowerCase() == 'inches' || unit.toLowerCase() == 'in')
          ? waistCm / 2.54
          : waistCm;

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'weekNumber': weekNumber,
      'year': year,
      'calendarWeek': calendarWeek,
      'timestamp': timestamp,
      'memberName': memberName,
      'memberAge': memberAge,
      'weightKg': weightKg,
      'heightCm': heightCm,
      'weightUnit': weightUnit,
      'heightUnit': heightUnit,
      'chestCm': chestCm,
      'armCm': armCm,
      'waistCm': waistCm,
      'measurementUnit': measurementUnit,
    };
  }

  factory WeeklyRecord.fromJson(Map<String, dynamic> json) {
    return WeeklyRecord(
      id: json['id'] as String,
      weekNumber: (json['weekNumber'] as num).toInt(),
      year: (json['year'] as num).toInt(),
      calendarWeek: (json['calendarWeek'] as num).toInt(),
      timestamp: (json['timestamp'] as num).toInt(),
      memberName: json['memberName'] as String? ?? 'Fitness Athlete',
      memberAge: (json['memberAge'] as num?)?.toInt() ?? 26,
      weightKg: (json['weightKg'] as num).toDouble(),
      heightCm: (json['heightCm'] as num).toDouble(),
      weightUnit: json['weightUnit'] as String? ?? 'kg',
      heightUnit: json['heightUnit'] as String? ?? 'cm',
      chestCm: (json['chestCm'] as num).toDouble(),
      armCm: (json['armCm'] as num).toDouble(),
      waistCm: (json['waistCm'] as num).toDouble(),
      measurementUnit: json['measurementUnit'] as String? ?? 'cm',
    );
  }
}
