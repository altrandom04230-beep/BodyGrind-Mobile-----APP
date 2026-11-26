package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testBmiCalculation_normalWeight() {
        val weightKg = 70.0f
        val heightCm = 175.0f
        val heightM = heightCm / 100f
        val bmi = weightKg / (heightM * heightM)

        assertTrue(bmi in 22.8f..22.9f)
        val category = when {
            bmi < 18.5f -> "Underweight"
            bmi < 24.9f -> "Normal weight"
            bmi < 29.9f -> "Overweight"
            else -> "Obese"
        }
        assertEquals("Normal weight", category)
    }

    @Test
    fun testBmiCalculation_overweight() {
        val weightKg = 90.0f
        val heightCm = 175.0f
        val heightM = heightCm / 100f
        val bmi = weightKg / (heightM * heightM)

        val category = when {
            bmi < 18.5f -> "Underweight"
            bmi < 24.9f -> "Normal weight"
            bmi < 29.9f -> "Overweight"
            else -> "Obese"
        }
        assertEquals("Overweight", category)
    }

    @Test
    fun testLevelProgression() {
        fun calculateLevel(xp: Int): Int = when {
            xp >= 5000 -> 7
            xp >= 3200 -> 6
            xp >= 2000 -> 5
            xp >= 1200 -> 4
            xp >= 600 -> 3
            xp >= 250 -> 2
            else -> 1
        }

        assertEquals(1, calculateLevel(0))
        assertEquals(1, calculateLevel(150))
        assertEquals(2, calculateLevel(250))
        assertEquals(3, calculateLevel(600))
        assertEquals(4, calculateLevel(1200))
        assertEquals(5, calculateLevel(2000))
        assertEquals(6, calculateLevel(3200))
        assertEquals(7, calculateLevel(5000))
    }

    @Test
    fun testCalorieEstimation() {
        val durationSeconds = 900 // 15 mins
        val caloriesPerMinute = 8.0f
        val estimatedCal = ((durationSeconds / 60f) * caloriesPerMinute).toInt()
        assertEquals(120, estimatedCal)
    }

    @Test
    fun testRagEngineRetrieval_exerciseMatch() {
        val exercises = listOf(
            com.example.data.local.entity.ExerciseEntity(
                id = "push_ups",
                name = "Push-Ups",
                category = "Chest",
                targetMuscles = "Chest, Shoulders, Triceps",
                difficulty = "Beginner",
                defaultType = "REPS",
                defaultCount = 12,
                instructions = "Lower chest to ground",
                tips = "Keep core tight",
                animationType = "PUSH_UP"
            )
        )
        val plans = listOf(
            com.example.data.local.entity.WorkoutPlanEntity(
                id = "plan_full_body_beg",
                name = "Full Body Starter",
                description = "Classic beginner workout",
                category = "Full Body",
                difficulty = "Beginner",
                estimatedDurationMinutes = 12
            )
        )

        val result = com.example.data.ai.FitnessRagEngine.retrieve(
            query = "How to do push ups correctly?",
            userProfile = null,
            allExercises = exercises,
            allPlans = plans,
            recentSessions = emptyList(),
            streakDays = 3
        )

        assertEquals(1, result.matchedExercises.size)
        assertEquals("push_ups", result.matchedExercises[0].id)
        assertTrue(result.augmentedContext.contains("Push-Ups"))
        assertTrue(result.augmentedContext.contains("Keep core tight"))
    }
}
