package com.example.ui.meals

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.meals.DayOfWeekPlan
import com.example.data.meals.DaySlotPlan
import com.example.data.meals.DayMealPlan
import com.example.data.meals.LoggedMeal
import com.example.data.meals.MealSlot
import com.example.data.meals.SmartMealsRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmartMealsTest {

    private lateinit var context: Context
    private lateinit var repository: SmartMealsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        repository = SmartMealsRepository(context)
        repository.clearTodayMeals()
        repository.clearAllFilters()
        repository.resetWater()
    }

    @Test
    fun testCuratedRecipes_areAvailableAndNonEmpty() {
        val recipes = repository.curatedRecipes
        assertTrue("Expected at least 10 curated fitness recipes", recipes.size >= 10)

        // Every recipe should have positive calories, protein, and valid slot
        recipes.forEach { recipe ->
            assertTrue(recipe.name.isNotBlank())
            assertTrue(recipe.calories > 0)
            assertTrue(recipe.proteinG > 0)
            assertNotNull(recipe.defaultSlot)
            assertTrue(recipe.ingredients.isNotEmpty())
        }
    }

    @Test
    fun testLogMeal_incrementsLoggedListAndCalories() {
        assertEquals(0, repository.loggedMeals.value.size)

        val meal = LoggedMeal(
            name = "Power Oats & Whey",
            slot = MealSlot.BREAKFAST,
            calories = 450,
            proteinG = 38,
            carbsG = 56,
            fatsG = 9
        )
        repository.logMeal(meal)

        val updated = repository.loggedMeals.value
        assertEquals(1, updated.size)
        assertEquals("Power Oats & Whey", updated[0].name)
        assertEquals(450, updated[0].calories)
        assertEquals(38, updated[0].proteinG)
    }

    @Test
    fun testDeleteMeal_removesFromList() {
        val meal1 = LoggedMeal(
            id = "meal_1",
            name = "Grilled Chicken Breast",
            slot = MealSlot.LUNCH,
            calories = 560,
            proteinG = 52,
            carbsG = 58,
            fatsG = 12
        )
        val meal2 = LoggedMeal(
            id = "meal_2",
            name = "Tuna Crunch",
            slot = MealSlot.MORNING_SNACK,
            calories = 260,
            proteinG = 30,
            carbsG = 25,
            fatsG = 3
        )
        repository.logMeal(meal1)
        repository.logMeal(meal2)
        assertEquals(2, repository.loggedMeals.value.size)

        repository.deleteMeal("meal_1")
        assertEquals(1, repository.loggedMeals.value.size)
        assertEquals("meal_2", repository.loggedMeals.value[0].id)
    }

    @Test
    fun testDietaryFilter_toggling() {
        assertTrue(repository.activeFilters.value.isEmpty())

        repository.toggleDietaryFilter("High-Protein")
        assertTrue(repository.activeFilters.value.contains("High-Protein"))

        repository.toggleDietaryFilter("High-Protein")
        assertFalse(repository.activeFilters.value.contains("High-Protein"))
    }

    @Test
    fun testAllergyFilter_exclusion() {
        repository.toggleAllergyFilter("Dairy")
        assertTrue(repository.activeAllergies.value.contains("Dairy"))

        val allRecipes = repository.curatedRecipes
        val dairyFreeRecipes = allRecipes.filter { recipe ->
            recipe.allergens.none { it.contains("Dairy", ignoreCase = true) }
        }
        assertTrue(dairyFreeRecipes.isNotEmpty())
        assertTrue(dairyFreeRecipes.none { it.allergens.contains("Dairy") })
    }

    @Test
    fun testGoalSettings_update() {
        assertEquals("Build Muscle", repository.goalSettings.value.goalName)
        assertEquals(2400, repository.goalSettings.value.dailyCalorieTarget)

        repository.updateGoals("Lean Shred / Cut", 1800, 160, 150, 50)
        assertEquals("Lean Shred / Cut", repository.goalSettings.value.goalName)
        assertEquals(1800, repository.goalSettings.value.dailyCalorieTarget)
        assertEquals(160, repository.goalSettings.value.proteinTargetG)
    }

    @Test
    fun testWaterIntake_tracking() {
        assertEquals(0, repository.waterIntakeMl.value)

        repository.addWater(250)
        assertEquals(250, repository.waterIntakeMl.value)

        repository.addWater(500)
        assertEquals(750, repository.waterIntakeMl.value)

        repository.resetWater()
        assertEquals(0, repository.waterIntakeMl.value)
    }

    // ========================================================
    // WEEKLY MEAL PLAN & PER-DAY EDITING TESTS (REQUIREMENTS 1-5)
    // ========================================================

    @Test
    fun testWeeklyPlans_allSevenDaysHaveFiveSlotsEach() {
        val weeklyMap = repository.weeklyPlans.value
        assertEquals("Weekly plan must hold all 7 days", 7, weeklyMap.size)

        for (day in DayOfWeekPlan.entries) {
            val dayPlan = repository.getDayPlan(day)
            assertEquals(day, dayPlan.day)
            assertEquals("Each day must contain 5 meal slots", 5, dayPlan.slots.size)

            val slots = dayPlan.slots.map { it.slot }
            assertEquals(
                listOf(
                    MealSlot.BREAKFAST,
                    MealSlot.MORNING_SNACK,
                    MealSlot.LUNCH,
                    MealSlot.AFTERNOON_SNACK,
                    MealSlot.DINNER
                ),
                slots
            )

            // Default target sum: 500 + 300 + 650 + 350 + 600 = 2400 kcal
            assertEquals(2400, dayPlan.totalTargetKcal)
            // Default protein sum: 40 + 25 + 50 + 30 + 45 = 190 g
            assertEquals(190, dayPlan.totalTargetProteinG)
        }
    }

    @Test
    fun testPerDayEditing_modifiesOnlySelectedDayAndPersistsAcrossSessions() {
        // Edit Monday's breakfast
        repository.updateSlotPlan(
            day = DayOfWeekPlan.MONDAY,
            slot = MealSlot.BREAKFAST,
            name = "Power Oats & Berries",
            targetKcal = 720,
            targetProteinG = 52
        )

        // Monday breakfast should be updated
        val mondayPlan = repository.getDayPlan(DayOfWeekPlan.MONDAY)
        val mondayBreakfast = mondayPlan.findSlot(MealSlot.BREAKFAST)
        assertEquals("Power Oats & Berries", mondayBreakfast.name)
        assertEquals(720, mondayBreakfast.targetKcal)
        assertEquals(52, mondayBreakfast.targetProteinG)

        // Tuesday breakfast MUST remain untouched (Requirement 4: isolation)
        val tuesdayPlan = repository.getDayPlan(DayOfWeekPlan.TUESDAY)
        val tuesdayBreakfast = tuesdayPlan.findSlot(MealSlot.BREAKFAST)
        assertEquals("Breakfast", tuesdayBreakfast.name)
        assertEquals(500, tuesdayBreakfast.targetKcal)
        assertEquals(40, tuesdayBreakfast.targetProteinG)

        // Wednesday dinner MUST remain untouched
        val wednesdayDinner = repository.getDayPlan(DayOfWeekPlan.WEDNESDAY).findSlot(MealSlot.DINNER)
        assertEquals("Dinner", wednesdayDinner.name)
        assertEquals(600, wednesdayDinner.targetKcal)
        assertEquals(45, wednesdayDinner.targetProteinG)

        // Verify persistence across repository reloads / sessions
        val reloadedRepository = SmartMealsRepository(context)
        val reloadedMondayBreakfast = reloadedRepository.getDayPlan(DayOfWeekPlan.MONDAY).findSlot(MealSlot.BREAKFAST)
        assertEquals("Power Oats & Berries", reloadedMondayBreakfast.name)
        assertEquals(720, reloadedMondayBreakfast.targetKcal)
        assertEquals(52, reloadedMondayBreakfast.targetProteinG)

        val reloadedTuesdayBreakfast = reloadedRepository.getDayPlan(DayOfWeekPlan.TUESDAY).findSlot(MealSlot.BREAKFAST)
        assertEquals("Breakfast", reloadedTuesdayBreakfast.name)
        assertEquals(500, reloadedTuesdayBreakfast.targetKcal)
    }

    @Test
    fun testMealLogging_tiedToSpecificCalendarDatesAndPersistsIndependently() {
        val mondayCal = MealCalendarHelper.getCalendarDateForDay(DayOfWeekPlan.MONDAY)
        val wednesdayCal = MealCalendarHelper.getCalendarDateForDay(DayOfWeekPlan.WEDNESDAY)

        val mondayDateKey = MealCalendarHelper.formatDateKey(mondayCal)
        val wednesdayDateKey = MealCalendarHelper.formatDateKey(wednesdayCal)

        // Clean slate for test dates
        repository.clearMealsForDate(mondayDateKey)
        repository.clearMealsForDate(wednesdayDateKey)

        val mondayMeal = LoggedMeal(
            id = "mon_meal_1",
            name = "Monday Steak & Eggs",
            slot = MealSlot.LUNCH,
            calories = 680,
            proteinG = 58,
            timestamp = mondayCal.timeInMillis
        )
        val wednesdayMeal = LoggedMeal(
            id = "wed_meal_1",
            name = "Wednesday Grilled Salmon",
            slot = MealSlot.DINNER,
            calories = 540,
            proteinG = 46,
            timestamp = wednesdayCal.timeInMillis
        )

        repository.logMeal(mondayMeal)
        repository.logMeal(wednesdayMeal)

        // Query by date key
        val mondayMeals = repository.getMealsForDateKey(mondayDateKey)
        val wednesdayMeals = repository.getMealsForDateKey(wednesdayDateKey)

        assertEquals(1, mondayMeals.size)
        assertEquals("Monday Steak & Eggs", mondayMeals[0].name)
        assertEquals(680, mondayMeals[0].calories)

        assertEquals(1, wednesdayMeals.size)
        assertEquals("Wednesday Grilled Salmon", wednesdayMeals[0].name)
        assertEquals(540, wednesdayMeals[0].calories)

        // Clear Monday meals only
        repository.clearMealsForDate(mondayDateKey)
        assertEquals(0, repository.getMealsForDateKey(mondayDateKey).size)
        // Wednesday meals MUST still be intact (Requirement 5)
        val retainedWednesdayMeals = repository.getMealsForDateKey(wednesdayDateKey)
        assertEquals(1, retainedWednesdayMeals.size)
        assertEquals("Wednesday Grilled Salmon", retainedWednesdayMeals[0].name)
    }

    @Test
    fun testDayOfWeekPlan_fromCalendarAndDateHelpers() {
        val cal = Calendar.getInstance()

        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        assertEquals(DayOfWeekPlan.MONDAY, DayOfWeekPlan.fromCalendar(cal))

        cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY)
        assertEquals(DayOfWeekPlan.FRIDAY, DayOfWeekPlan.fromCalendar(cal))

        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        assertEquals(DayOfWeekPlan.SUNDAY, DayOfWeekPlan.fromCalendar(cal))

        assertNotNull(DayOfWeekPlan.today())
    }
}
