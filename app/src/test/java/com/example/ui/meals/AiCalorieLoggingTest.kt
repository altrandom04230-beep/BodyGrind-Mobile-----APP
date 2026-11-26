package com.example.ui.meals

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.meals.LoggedMeal
import com.example.data.meals.MealSlot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiCalorieLoggingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var viewModel: SmartMealsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        viewModel = SmartMealsViewModel(app)
        viewModel.clearToday()
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLogMeal_updatesRemainingCaloriesAndKcalLeft() = runTest(testDispatcher) {
        val initialCalorieGoal = viewModel.uiState.value.calorieGoal

        val testMeal = LoggedMeal(
            name = "Scrambled Eggs & Whole Wheat Toast",
            slot = MealSlot.BREAKFAST,
            calories = 350,
            proteinG = 24,
            carbsG = 28,
            fatsG = 14,
            portion = "2 eggs, 1 toast",
            dietaryTags = listOf("AI Estimated")
        )

        viewModel.logMeal(testMeal)
        testScheduler.advanceUntilIdle()
        ShadowLooper.idleMainLooper()

        val updatedUi = viewModel.uiState.first { it.consumedCalories > 0 }
        assertEquals(350, updatedUi.consumedCalories)
        assertEquals((initialCalorieGoal - 350).coerceAtLeast(0), updatedUi.remainingCalories)

        val loggedList = updatedUi.loggedMeals
        assertTrue("Logged meals should contain the new meal", loggedList.any { it.name == testMeal.name && it.calories == 350 })
    }
}
