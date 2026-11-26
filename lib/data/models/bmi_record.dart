/// BMI domain models matching BmiRecordEntity and BmiCalculator.kt
class BmiRecord {
  final String bmiRecordId;
  final int userId;
  final double heightCm;
  final double weightKg;
  final double bmiValue;
  final String bmiCategory; // "Underweight", "Normal weight", "Overweight", "Obesity"
  final int assessmentDate;
  final String sourceType; // "MANUAL"
  final String? linkedScanId;

  const BmiRecord({
    required this.bmiRecordId,
    this.userId = 1,
    required this.heightCm,
    required this.weightKg,
    required this.bmiValue,
    required this.bmiCategory,
    required this.assessmentDate,
    this.sourceType = 'MANUAL',
    this.linkedScanId,
  });

  Map<String, dynamic> toMap() {
    return {
      'bmiRecordId': bmiRecordId,
      'userId': userId,
      'heightCm': heightCm,
      'weightKg': weightKg,
      'bmiValue': bmiValue,
      'bmiCategory': bmiCategory,
      'assessmentDate': assessmentDate,
      'sourceType': sourceType,
      'linkedScanId': linkedScanId,
    };
  }

  factory BmiRecord.fromMap(Map<String, dynamic> map) {
    return BmiRecord(
      bmiRecordId: map['bmiRecordId'] as String,
      userId: (map['userId'] as num?)?.toInt() ?? 1,
      heightCm: (map['heightCm'] as num).toDouble(),
      weightKg: (map['weightKg'] as num).toDouble(),
      bmiValue: (map['bmiValue'] as num).toDouble(),
      bmiCategory: map['bmiCategory'] as String,
      assessmentDate: (map['assessmentDate'] as num).toInt(),
      sourceType: map['sourceType'] as String? ?? 'MANUAL',
      linkedScanId: map['linkedScanId'] as String?,
    );
  }
}

class BmiValidationResult {
  final bool isValid;
  final String? errorMessage;

  const BmiValidationResult(this.isValid, [this.errorMessage]);
}

class BmiAnalysisResult {
  final double bmi;
  final String category;
  final String categoryColorHex;
  final String explanation;
  final List<String> recommendations;
  final double healthyWeightRangeMin;
  final double healthyWeightRangeMax;
  final String clinicalGuidanceNotice;

  const BmiAnalysisResult({
    required this.bmi,
    required this.category,
    required this.categoryColorHex,
    required this.explanation,
    required this.recommendations,
    required this.healthyWeightRangeMin,
    required this.healthyWeightRangeMax,
    this.clinicalGuidanceNotice =
        'Important Notice: BMI is a general screening indicator and does not directly measure body fat percentage or muscle distribution. Consult a certified coach or healthcare professional for individualized assessments.',
  });
}
