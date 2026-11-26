package com.example.ui.posebmi

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class BmiValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null
)

data class BmiAnalysisResult(
    val bmi: Float,
    val category: String,
    val categoryColorHex: String,
    val explanation: String,
    val recommendations: List<String>,
    val healthyWeightRangeMin: Float,
    val healthyWeightRangeMax: Float,
    val clinicalGuidanceNotice: String = "Important Notice: BMI is a general screening indicator and does not directly measure body fat percentage or muscle distribution. Consult a certified coach or healthcare professional for individualized assessments."
)

object BmiCalculator {

    private val CM_PER_INCH = BigDecimal("2.54")
    private val KG_PER_LB = BigDecimal("0.45359237")
    private val INCHES_PER_FOOT = BigDecimal("12")

    fun validateRawInputs(heightStr: String, weightStr: String): BmiValidationResult {
        if (heightStr.trim().isEmpty()) {
            return BmiValidationResult(false, "Please enter your height.")
        }
        if (weightStr.trim().isEmpty()) {
            return BmiValidationResult(false, "Please enter your weight.")
        }
        val heightBd = try {
            BigDecimal(heightStr.trim())
        } catch (e: NumberFormatException) {
            return BmiValidationResult(false, "Please enter a valid height.")
        }
        val weightBd = try {
            BigDecimal(weightStr.trim())
        } catch (e: NumberFormatException) {
            return BmiValidationResult(false, "Please enter a valid weight.")
        }
        val height = heightBd.toFloat()
        val weight = weightBd.toFloat()
        if (height <= 0f) {
            return BmiValidationResult(false, "Height must be greater than zero.")
        }
        if (weight <= 0f) {
            return BmiValidationResult(false, "Weight must be greater than zero.")
        }
        if (height < 50f || height > 260f) {
            return BmiValidationResult(false, "Height must be between 50 cm and 260 cm.")
        }
        if (weight < 20f || weight > 350f) {
            return BmiValidationResult(false, "Weight must be between 20 kg and 350 kg.")
        }
        return BmiValidationResult(true)
    }

    fun validateInputs(heightCm: Float, weightKg: Float): BmiValidationResult {
        return validateRawInputs(heightCm.toString(), weightKg.toString())
    }

    fun validateRawImperialInputs(
        feetStr: String,
        inchesStr: String,
        weightLbStr: String
    ): BmiValidationResult {
        val trimmedFeet = feetStr.trim()
        val trimmedInches = inchesStr.trim()
        val trimmedWeight = weightLbStr.trim()

        if (trimmedFeet.isEmpty() && trimmedInches.isEmpty()) {
            return BmiValidationResult(false, "Please enter your height.")
        }
        if (trimmedWeight.isEmpty()) {
            return BmiValidationResult(false, "Please enter your weight.")
        }

        val feetBd = if (trimmedFeet.isEmpty()) {
            BigDecimal.ZERO
        } else {
            try {
                BigDecimal(trimmedFeet)
            } catch (e: NumberFormatException) {
                return BmiValidationResult(false, "Please enter a valid height.")
            }
        }

        val inchesBd = if (trimmedInches.isEmpty()) {
            BigDecimal.ZERO
        } else {
            try {
                BigDecimal(trimmedInches)
            } catch (e: NumberFormatException) {
                return BmiValidationResult(false, "Please enter a valid height.")
            }
        }

        val weightLbBd = try {
            BigDecimal(trimmedWeight)
        } catch (e: NumberFormatException) {
            return BmiValidationResult(false, "Please enter a valid weight.")
        }

        if (feetBd < BigDecimal.ZERO || inchesBd < BigDecimal.ZERO) {
            return BmiValidationResult(false, "Height must be greater than zero.")
        }
        if (trimmedFeet.isNotEmpty() && inchesBd >= INCHES_PER_FOOT) {
            return BmiValidationResult(false, "Inches must be less than 12.")
        }

        val totalInchesBd = feetBd.multiply(INCHES_PER_FOOT).add(inchesBd)
        if (totalInchesBd <= BigDecimal.ZERO) {
            return BmiValidationResult(false, "Height must be greater than zero.")
        }
        if (weightLbBd <= BigDecimal.ZERO) {
            return BmiValidationResult(false, "Weight must be greater than zero.")
        }

        val heightCm = totalInchesBd.multiply(CM_PER_INCH).toFloat()
        val weightKg = weightLbBd.multiply(KG_PER_LB).toFloat()

        if (heightCm < 50f || heightCm > 260f) {
            return BmiValidationResult(false, "Height must be between 1 ft 8 in and 8 ft 6 in (50–260 cm).")
        }
        if (weightKg < 20f || weightKg > 350f) {
            return BmiValidationResult(false, "Weight must be between 44.1 lb and 771.6 lb (20–350 kg).")
        }

        return BmiValidationResult(true)
    }

    /**
     * Converts imperial feet + inches to centimeters using exact 1 in = 2.54 cm.
     */
    fun feetAndInchesToCm(feetStr: String, inchesStr: String): BigDecimal {
        val feetBd = if (feetStr.trim().isEmpty()) BigDecimal.ZERO else BigDecimal(feetStr.trim())
        val inchesBd = if (inchesStr.trim().isEmpty()) BigDecimal.ZERO else BigDecimal(inchesStr.trim())
        val totalInches = feetBd.multiply(INCHES_PER_FOOT).add(inchesBd)
        return totalInches.multiply(CM_PER_INCH)
    }

    /**
     * Converts pounds to kilograms using exact 1 lb = 0.45359237 kg.
     */
    fun lbToKg(weightLbStr: String): BigDecimal {
        val weightLbBd = BigDecimal(weightLbStr.trim())
        return weightLbBd.multiply(KG_PER_LB)
    }

    /**
     * Converts centimeters into display strings (feet, inches) rounded cleanly to 1 decimal for inches.
     */
    fun cmToFeetAndInchesStrings(cm: Float): Pair<String, String> {
        val totalInches = cm / 2.54
        var feet = kotlin.math.floor(totalInches / 12.0).toInt()
        var remInches = ((totalInches - feet * 12.0) * 10.0).let { kotlin.math.round(it) / 10.0 }
        if (remInches >= 12.0) {
            feet += 1
            remInches = 0.0
        }
        val inchesStr = if (remInches % 1.0 == 0.0) {
            remInches.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", remInches)
        }
        return Pair(feet.toString(), inchesStr)
    }

    /**
     * Converts kilograms into a 1-decimal pound string.
     */
    fun kgToLbString(kg: Float): String {
        val lb = kg / 0.45359237
        return String.format(Locale.US, "%.1f", lb)
    }

    /**
     * Converts pounds into a 1-decimal kilogram string.
     */
    fun lbToKgString(lb: Float): String {
        val kg = lb * 0.45359237
        return String.format(Locale.US, "%.1f", kg)
    }

    /**
     * Converts centimeters into a clean string (integer if whole or 1 decimal).
     */
    fun formatCmInput(cm: Double): String {
        val rounded = kotlin.math.round(cm * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
    }

    /**
     * Exact formula using BigDecimal:
     * BMI = weightKg * 10000 / (heightCm * heightCm), rounded with HALF_UP to 1 decimal place in a single division.
     */
    fun calculateBmi(heightCmStr: String, weightKgStr: String): BigDecimal {
        val weightBd = BigDecimal(weightKgStr.trim())
        val heightBd = BigDecimal(heightCmStr.trim())
        val numerator = weightBd.multiply(BigDecimal("10000"))
        val denominator = heightBd.multiply(heightBd)
        return numerator.divide(denominator, 1, RoundingMode.HALF_UP)
    }

    fun calculateBmi(heightCm: Float, weightKg: Float): Float {
        return calculateBmi(heightCm.toString(), weightKg.toString()).toFloat()
    }

    /**
     * Computes BMI from imperial feet, inches, and pounds with a single HALF_UP division to 1 decimal place.
     */
    fun calculateBmiFromImperial(feetStr: String, inchesStr: String, weightLbStr: String): BigDecimal {
        val heightCmBd = feetAndInchesToCm(feetStr, inchesStr)
        val weightKgBd = lbToKg(weightLbStr)
        val numerator = weightKgBd.multiply(BigDecimal("10000"))
        val denominator = heightCmBd.multiply(heightCmBd)
        return numerator.divide(denominator, 1, RoundingMode.HALF_UP)
    }

    fun classifyAdultBmi(bmiBd: BigDecimal, heightCm: Float, age: Int? = null): BmiAnalysisResult {
        val heightM = heightCm / 100f
        val minHealthyWeight = String.format(Locale.US, "%.1f", 18.5f * (heightM * heightM)).toFloat()
        val maxHealthyWeight = String.format(Locale.US, "%.1f", 24.9f * (heightM * heightM)).toFloat()

        val underweightCutoff = BigDecimal("18.5")
        val normalCutoff = BigDecimal("25.0")
        val overweightCutoff = BigDecimal("30.0")

        val bmiFloat = bmiBd.toFloat()
        val isSenior = age != null && age >= 60

        val baseResult = when {
            bmiBd < underweightCutoff -> BmiAnalysisResult(
                bmi = bmiFloat,
                category = "Underweight",
                categoryColorHex = "#FFB300", // Amber highlight
                explanation = if (isSenior) {
                    "Your BMI indicates underweight status for older adults (age $age). In senior wellness, preserving muscle mass and nutritional density is vital for bone density and joint protection."
                } else {
                    "Your BMI indicates underweight status for standard adult metrics. Nutrient-dense nutrition and targeted hypertrophy resistance training can help build healthy muscle mass."
                },
                recommendations = if (isSenior) {
                    listOf(
                        "Prioritize adequate protein and nutrient-dense whole foods",
                        "Incorporate gentle resistance and functional mobility training",
                        "Consult your physician or coach Jun/Cobie for senior wellness"
                    )
                } else {
                    listOf(
                        "Prioritize caloric surplus with lean proteins and complex carbohydrates",
                        "Focus on progressive compound lifting (Squat, Deadlift, Bench)",
                        "Track weekly weight increases aiming for 0.25 - 0.5 kg gain per week"
                    )
                },
                healthyWeightRangeMin = minHealthyWeight,
                healthyWeightRangeMax = maxHealthyWeight
            )
            bmiBd < normalCutoff -> BmiAnalysisResult(
                bmi = bmiFloat,
                category = "Normal weight",
                categoryColorHex = "#22C55E", // Green
                explanation = if (isSenior) {
                    "Your BMI is in the healthy range for active senior athletes (age $age). Maintain steady balance, joint mobility, and lean muscle retention."
                } else {
                    "Your BMI is within the standard healthy weight range for adult athletes. Maintain your consistent training volume and balanced macronutrient distribution."
                },
                recommendations = if (isSenior) {
                    listOf(
                        "Maintain functional mobility, balance, and joint-friendly resistance exercises",
                        "Stay hydrated and ensure adequate dietary calcium and protein",
                        "Aim for 3-4 structured low-impact activity sessions per week"
                    )
                } else {
                    listOf(
                        "Maintain current training split with 3-5 sessions per week",
                        "Focus on athletic performance, strength progression, and mobility",
                        "Keep hydration optimal and prioritize 7-9 hours of nightly recovery"
                    )
                },
                healthyWeightRangeMin = minHealthyWeight,
                healthyWeightRangeMax = maxHealthyWeight
            )
            bmiBd < overweightCutoff -> BmiAnalysisResult(
                bmi = bmiFloat,
                category = "Overweight",
                categoryColorHex = "#E50914", // Bold Red
                explanation = if (isSenior) {
                    "Your BMI is slightly elevated (age $age). For older adults, modest reserve weight can be protective; prioritize cardiovascular conditioning and mobility over aggressive deficits."
                } else {
                    "Your BMI is in the overweight range. For dedicated strength athletes, high lean muscle mass can elevate BMI. If fat loss is your goal, maintain a modest caloric deficit combined with high-intensity circuit training."
                },
                recommendations = if (isSenior) {
                    listOf(
                        "Combine low-impact cardio (walking, swimming) with core stability drills",
                        "Maintain balanced portions without drastic caloric restriction",
                        "Prioritize joint comfort and mobility drills daily"
                    )
                } else {
                    listOf(
                        "Combine hypertrophy lifting with 20-30 mins of moderate cardio or HIIT",
                        "Maintain a moderate 300-500 kcal deficit while keeping protein high",
                        "Monitor waist-to-hip ratio alongside weight changes"
                    )
                },
                healthyWeightRangeMin = minHealthyWeight,
                healthyWeightRangeMax = maxHealthyWeight
            )
            else -> BmiAnalysisResult(
                bmi = bmiFloat,
                category = "Obesity",
                categoryColorHex = "#E50914", // Bold Red
                explanation = if (isSenior) {
                    "Your BMI is in the obesity range (age $age). Structured low-impact cardiovascular activity and joint-preserving movements are recommended."
                } else {
                    "Your BMI is in the obesity classification (30.0+). Structured low-impact cardiovascular training, calorie-conscious meal planning, and steady routine consistency are recommended."
                },
                recommendations = if (isSenior) {
                    listOf(
                        "Start with low-impact walking and seated resistance movements",
                        "Focus on steady nutrient-rich meals and hydration",
                        "Work closely with a healthcare specialist or certified trainer"
                    )
                } else {
                    listOf(
                        "Start with low-impact exercises (walking, stationary bike, bodyweight)",
                        "Establish structured dietary habits with whole foods and hydration",
                        "Consult gym trainers (Coach Cobie & Jun) or a health specialist for a custom roadmap"
                    )
                },
                healthyWeightRangeMin = minHealthyWeight,
                healthyWeightRangeMax = maxHealthyWeight
            )
        }
        return baseResult
    }

    fun classifyAdultBmi(bmi: Float, heightCm: Float, age: Int? = null): BmiAnalysisResult {
        val bmiBd = BigDecimal(bmi.toString()).setScale(1, RoundingMode.HALF_UP)
        return classifyAdultBmi(bmiBd, heightCm, age)
    }
}
