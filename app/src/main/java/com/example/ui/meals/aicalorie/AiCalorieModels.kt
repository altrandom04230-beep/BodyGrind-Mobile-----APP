package com.example.ui.meals.aicalorie

import com.example.data.meals.MealSlot
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Temporary editable item parsed from Gemini AI response.
 */
data class EstimatedFoodItem(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var portion: String = "1 serving",
    var calories: Int = 0,
    var proteinG: Int = 0,
    var carbsG: Int = 0,
    var fatG: Int = 0
)

/**
 * Parsed AI analysis result before user confirms and saves.
 */
data class AiMealEstimateResult(
    val isFood: Boolean = true,
    val confidence: String = "medium", // "high", "medium", "low"
    val unclearReason: String = "",
    val items: List<EstimatedFoodItem> = emptyList(),
    val totalCalories: Int = 0,
    val totalProteinG: Int = 0,
    val totalCarbsG: Int = 0,
    val totalFatG: Int = 0
)

/**
 * Defensive JSON parser that extracts food items, macronutrients, and confidence.
 */
object AiCalorieJsonParser {

    fun parse(rawJsonString: String): Result<AiMealEstimateResult> {
        return try {
            val cleanJson = extractJsonPayload(rawJsonString)
            val root = JSONObject(cleanJson)

            val isFood = root.optBoolean("is_food", true)
            val unclearReason = root.optString("unclear_reason", "")
            val confidence = root.optString("confidence", "medium").lowercase().let {
                if (it in listOf("high", "medium", "low")) it else "medium"
            }

            if (!isFood) {
                return Result.success(
                    AiMealEstimateResult(
                        isFood = false,
                        confidence = "low",
                        unclearReason = if (unclearReason.isNotBlank()) unclearReason
                        else "No recognizable food items could be detected. Please take a clearer photo or enter a descriptive meal text."
                    )
                )
            }

            val itemsList = mutableListOf<EstimatedFoodItem>()
            val itemsArr = root.optJSONArray("items") ?: JSONArray()

            for (i in 0 until itemsArr.length()) {
                val itemObj = itemsArr.optJSONObject(i) ?: continue
                val name = itemObj.optString("name", "Food Item").trim()
                val portion = itemObj.optString("portion", "1 serving").trim()
                val calories = itemObj.optInt("calories", itemObj.optInt("kcal", 0))
                val proteinG = itemObj.optInt("protein_g", itemObj.optInt("protein", 0))
                val carbsG = itemObj.optInt("carbs_g", itemObj.optInt("carbs", 0))
                val fatG = itemObj.optInt("fat_g", itemObj.optInt("fat", itemObj.optInt("fats_g", 0)))

                itemsList.add(
                    EstimatedFoodItem(
                        name = if (name.isNotBlank()) name else "Item ${i + 1}",
                        portion = if (portion.isNotBlank()) portion else "1 serving",
                        calories = calories.coerceAtLeast(0),
                        proteinG = proteinG.coerceAtLeast(0),
                        carbsG = carbsG.coerceAtLeast(0),
                        fatG = fatG.coerceAtLeast(0)
                    )
                )
            }

            // Parse or compute totals
            val totalsObj = root.optJSONObject("totals")
            val totalKcal = totalsObj?.optInt("calories", totalsObj.optInt("kcal", 0))
                ?: itemsList.sumOf { it.calories }
            val totalProtein = totalsObj?.optInt("protein_g", totalsObj.optInt("protein", 0))
                ?: itemsList.sumOf { it.proteinG }
            val totalCarbs = totalsObj?.optInt("carbs_g", totalsObj.optInt("carbs", 0))
                ?: itemsList.sumOf { it.carbsG }
            val totalFat = totalsObj?.optInt("fat_g", totalsObj.optInt("fat", totalsObj.optInt("fats_g", 0)))
                ?: itemsList.sumOf { it.fatG }

            val finalCalories = if (totalKcal > 0) totalKcal else itemsList.sumOf { it.calories }

            // Guard against empty or zero-calorie output silently passing
            if (itemsList.isEmpty() || finalCalories <= 0) {
                return Result.success(
                    AiMealEstimateResult(
                        isFood = false,
                        confidence = "low",
                        unclearReason = "Could not estimate nutrition for this input. Please provide a clearer food photo or a more specific meal description (e.g. '2 boiled eggs, 1 slice whole wheat bread')."
                    )
                )
            }

            Result.success(
                AiMealEstimateResult(
                    isFood = true,
                    confidence = confidence,
                    items = itemsList,
                    totalCalories = finalCalories,
                    totalProteinG = totalProtein.coerceAtLeast(0),
                    totalCarbsG = totalCarbs.coerceAtLeast(0),
                    totalFatG = totalFat.coerceAtLeast(0)
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("Unable to parse AI response. Details: ${e.localizedMessage ?: "Invalid JSON format"}")
            )
        }
    }

    private fun extractJsonPayload(raw: String): String {
        var str = raw.trim()
        // Strip markdown fences ```json ... ```
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        } else if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        str = str.trim()

        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            str = str.substring(firstBrace, lastBrace + 1)
        }
        return str
    }
}
