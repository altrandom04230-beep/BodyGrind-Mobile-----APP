/// Body measurement domain model matching BodyMeasurementEntity in Entities.kt
class BodyMeasurement {
  final int id;
  final int timestamp;
  final double weightKg;
  final double heightCm;
  final double? chestCm;
  final double? armCm;
  final double? waistCm;
  final double bmi;
  final String bmiCategory;
  final String notes;

  const BodyMeasurement({
    this.id = 0,
    required this.timestamp,
    required this.weightKg,
    required this.heightCm,
    this.chestCm,
    this.armCm,
    this.waistCm,
    required this.bmi,
    required this.bmiCategory,
    this.notes = '',
  });

  Map<String, dynamic> toMap() {
    return {
      if (id > 0) 'id': id,
      'timestamp': timestamp,
      'weightKg': weightKg,
      'heightCm': heightCm,
      'chestCm': chestCm,
      'armCm': armCm,
      'waistCm': waistCm,
      'bmi': bmi,
      'bmiCategory': bmiCategory,
      'notes': notes,
    };
  }

  factory BodyMeasurement.fromMap(Map<String, dynamic> map) {
    return BodyMeasurement(
      id: (map['id'] as num?)?.toInt() ?? 0,
      timestamp: (map['timestamp'] as num).toInt(),
      weightKg: (map['weightKg'] as num).toDouble(),
      heightCm: (map['heightCm'] as num).toDouble(),
      chestCm: (map['chestCm'] as num?)?.toDouble(),
      armCm: (map['armCm'] as num?)?.toDouble(),
      waistCm: (map['waistCm'] as num?)?.toDouble(),
      bmi: (map['bmi'] as num).toDouble(),
      bmiCategory: map['bmiCategory'] as String,
      notes: map['notes'] as String? ?? '',
    );
  }
}
