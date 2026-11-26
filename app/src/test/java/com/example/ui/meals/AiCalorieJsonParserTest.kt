package com.example.ui.meals

import com.example.ui.meals.aicalorie.AiCalorieJsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiCalorieJsonParserTest {

    @Test
    fun testParse_validStrictJson_parsesCorrectly() {
        val json = """
            {
              "is_food": true,
              "confidence": "high",
              "unclear_reason": "",
              "items": [
                {
                  "name": "Scrambled Eggs",
                  "portion": "2 large eggs",
                  "calories": 140,
                  "protein_g": 12,
                  "carbs_g": 1,
                  "fat_g": 10
                },
                {
                  "name": "Whole Wheat Toast with Butter",
                  "portion": "1 slice",
                  "calories": 110,
                  "protein_g": 3,
                  "carbs_g": 14,
                  "fat_g": 5
                }
              ],
              "totals": {
                "calories": 250,
                "protein_g": 15,
                "carbs_g": 15,
                "fat_g": 15
              }
            }
        """.trimIndent()

        val result = AiCalorieJsonParser.parse(json)
        assertTrue(result.isSuccess)
        val estimate = result.getOrThrow()
        assertTrue(estimate.isFood)
        assertEquals("high", estimate.confidence)
        assertEquals(2, estimate.items.size)
        assertEquals("Scrambled Eggs", estimate.items[0].name)
        assertEquals(140, estimate.items[0].calories)
        assertEquals(12, estimate.items[0].proteinG)
        assertEquals(250, estimate.totalCalories)
        assertEquals(15, estimate.totalProteinG)
    }

    @Test
    fun testParse_markdownFences_strippedAndParsedSuccessfully() {
        val markdownJson = """
            ```json
            {
              "is_food": true,
              "confidence": "medium",
              "items": [
                {
                  "name": "Grilled Salmon",
                  "portion": "180g fillet",
                  "calories": 360,
                  "protein_g": 40,
                  "carbs_g": 0,
                  "fat_g": 20
                }
              ],
              "totals": {
                "calories": 360,
                "protein_g": 40,
                "carbs_g": 0,
                "fat_g": 20
              }
            }
            ```
        """.trimIndent()

        val result = AiCalorieJsonParser.parse(markdownJson)
        assertTrue(result.isSuccess)
        val estimate = result.getOrThrow()
        assertTrue(estimate.isFood)
        assertEquals(1, estimate.items.size)
        assertEquals("Grilled Salmon", estimate.items[0].name)
        assertEquals(360, estimate.totalCalories)
        assertEquals(40, estimate.totalProteinG)
    }

    @Test
    fun testParse_nonFoodInput_returnsIsFoodFalseWithReason() {
        val nonFoodJson = """
            {
              "is_food": false,
              "confidence": "low",
              "unclear_reason": "The image shows a laptop and a notebook, which are not food items.",
              "items": [],
              "totals": { "calories": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0 }
            }
        """.trimIndent()

        val result = AiCalorieJsonParser.parse(nonFoodJson)
        assertTrue(result.isSuccess)
        val estimate = result.getOrThrow()
        assertFalse(estimate.isFood)
        assertEquals("low", estimate.confidence)
        assertTrue(estimate.unclearReason.contains("not food"))
    }

    @Test
    fun testParse_zeroCaloriesOrEmptyItems_rejectedAsNonFood() {
        val zeroCalJson = """
            {
              "is_food": true,
              "confidence": "low",
              "items": [],
              "totals": { "calories": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0 }
            }
        """.trimIndent()

        val result = AiCalorieJsonParser.parse(zeroCalJson)
        assertTrue(result.isSuccess)
        val estimate = result.getOrThrow()
        assertFalse("Empty or zero-calorie output should not be treated as valid food", estimate.isFood)
    }

    @Test
    fun testParse_missingFields_defensivelyDefaulted() {
        val partialJson = """
            {
              "items": [
                {
                  "name": "Apple",
                  "calories": 95
                }
              ]
            }
        """.trimIndent()

        val result = AiCalorieJsonParser.parse(partialJson)
        assertTrue(result.isSuccess)
        val estimate = result.getOrThrow()
        assertTrue(estimate.isFood)
        assertEquals(1, estimate.items.size)
        assertEquals("Apple", estimate.items[0].name)
        assertEquals("1 serving", estimate.items[0].portion)
        assertEquals(95, estimate.items[0].calories)
        assertEquals(0, estimate.items[0].proteinG)
        assertEquals(0, estimate.items[0].carbsG)
        assertEquals(0, estimate.items[0].fatG)
        assertEquals(95, estimate.totalCalories)
    }
}
