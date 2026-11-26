package com.example.ui.meals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.meals.DayMealPlan
import com.example.data.meals.DayOfWeekPlan
import com.example.data.meals.LoggedMeal
import com.example.data.meals.MealGoalSettings
import com.example.data.meals.MealSlot
import com.example.data.meals.SmartMealRecipe
import com.example.data.meals.SmartMealsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class SmartMealsUiState(
    val selectedDay: DayOfWeekPlan = DayOfWeekPlan.today(),
    val dayPlan: DayMealPlan = DayMealPlan(DayOfWeekPlan.today()),
    val selectedDateFormatted: String = "",
    val isToday: Boolean = true,
    val goalName: String = "Build Muscle",
    val calorieGoal: Int = 2400,
    val consumedCalories: Int = 0,
    val remainingCalories: Int = 2400,
    val loggedMealCount: Int = 0,
    val targetMealCount: Int = 5,
    val proteinConsumedG: Int = 0,
    val proteinTargetG: Int = 180,
    val carbsConsumedG: Int = 0,
    val carbsTargetG: Int = 260,
    val fatsConsumedG: Int = 0,
    val fatsTargetG: Int = 70,
    val loggedMeals: List<LoggedMeal> = emptyList(),
    val mealsBySlot: Map<MealSlot, List<LoggedMeal>> = emptyMap(),
    val activeFilters: Set<String> = emptySet(),
    val activeAllergies: Set<String> = emptySet(),
    val availableRecipes: List<SmartMealRecipe> = emptyList(),
    val waterIntakeMl: Int = 0,
    val waterGoalMl: Int = 3000
)

class SmartMealsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SmartMealsRepository(application)

    // Requirement 3: Day selector defaulting to current day of the week
    private val _selectedDay = MutableStateFlow(DayOfWeekPlan.today())
    val selectedDay: StateFlow<DayOfWeekPlan> = _selectedDay.asStateFlow()

    private val filterWaterFlow = combine(
        repository.activeFilters,
        repository.activeAllergies,
        repository.waterIntakeMl
    ) { filters, allergies, water ->
        Triple(filters, allergies, water)
    }

    val uiState: StateFlow<SmartMealsUiState> = combine(
        _selectedDay,
        repository.weeklyPlans,
        repository.allLoggedMeals,
        repository.goalSettings,
        filterWaterFlow
    ) { currentDay, weeklyPlans, allMeals, goals, filterWater ->
        val filters = filterWater.first
        val allergies = filterWater.second
        val water = filterWater.third
        val currentPlan = weeklyPlans[currentDay] ?: DayMealPlan(currentDay)
        val targetCal = MealCalendarHelper.getCalendarDateForDay(currentDay)
        val dateKey = MealCalendarHelper.formatDateKey(targetCal)
        val formattedDate = MealCalendarHelper.formatHeaderDate(targetCal.timeInMillis)
        val isToday = currentDay == DayOfWeekPlan.today()

        // Filter meals specifically logged for this day's calendar date
        val dayMeals = allMeals.filter {
            MealCalendarHelper.formatDateKey(it.timestamp) == dateKey
        }

        val consumedKcal = dayMeals.sumOf { it.calories }
        val consumedProtein = dayMeals.sumOf { it.proteinG }
        val consumedCarbs = dayMeals.sumOf { it.carbsG }
        val consumedFats = dayMeals.sumOf { it.fatsG }

        // Day's slot mapping
        val slotMap = MealSlot.entries.associateWith { slot ->
            dayMeals.filter { it.slot == slot }
        }

        val slotsWithMealsCount = slotMap.count { it.value.isNotEmpty() }

        // Daily calorie & protein target computed from this day's 5 slots (or user global goal)
        val dayCalorieTarget = currentPlan.totalTargetKcal.takeIf { it > 0 } ?: goals.dailyCalorieTarget
        val dayProteinTarget = currentPlan.totalTargetProteinG.takeIf { it > 0 } ?: goals.proteinTargetG

        // Filter curated recipes
        val filteredRecipes = repository.curatedRecipes.filter { recipe ->
            val matchesDietary = if (filters.isEmpty()) {
                true
            } else {
                filters.all { filter ->
                    recipe.dietaryTags.any { it.contains(filter, ignoreCase = true) }
                }
            }

            val passesAllergies = if (allergies.isEmpty()) {
                true
            } else {
                allergies.none { allergy ->
                    recipe.allergens.any { it.contains(allergy, ignoreCase = true) }
                }
            }

            matchesDietary && passesAllergies
        }

        SmartMealsUiState(
            selectedDay = currentDay,
            dayPlan = currentPlan,
            selectedDateFormatted = formattedDate,
            isToday = isToday,
            goalName = goals.goalName,
            calorieGoal = dayCalorieTarget,
            consumedCalories = consumedKcal,
            remainingCalories = (dayCalorieTarget - consumedKcal).coerceAtLeast(0),
            loggedMealCount = slotsWithMealsCount,
            targetMealCount = currentPlan.slots.size,
            proteinConsumedG = consumedProtein,
            proteinTargetG = dayProteinTarget,
            carbsConsumedG = consumedCarbs,
            carbsTargetG = goals.carbsTargetG,
            fatsConsumedG = consumedFats,
            fatsTargetG = goals.fatsTargetG,
            loggedMeals = dayMeals,
            mealsBySlot = slotMap,
            activeFilters = filters,
            activeAllergies = allergies,
            availableRecipes = filteredRecipes,
            waterIntakeMl = water,
            waterGoalMl = 3000
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SmartMealsUiState()
    )

    fun selectDay(day: DayOfWeekPlan) {
        _selectedDay.value = day
    }

    /**
     * Requirement 4: Per-day editing of slot's name, kcal target, and protein target.
     */
    fun updateSlotPlan(
        day: DayOfWeekPlan,
        slot: MealSlot,
        name: String,
        targetKcal: Int,
        targetProteinG: Int
    ) {
        repository.updateSlotPlan(day, slot, name, targetKcal, targetProteinG)
    }

    /**
     * Requirement 5: Log Meal saves to actual calendar date of selected day.
     */
    fun logCustomMeal(
        name: String,
        slot: MealSlot,
        calories: Int,
        proteinG: Int,
        carbsG: Int,
        fatsG: Int,
        portion: String = "1 serving"
    ) {
        val targetCal = MealCalendarHelper.getCalendarDateForDay(_selectedDay.value)
        val meal = LoggedMeal(
            name = name,
            slot = slot,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatsG = fatsG,
            portion = portion,
            timestamp = targetCal.timeInMillis
        )
        repository.logMeal(meal)
    }

    fun logMeal(meal: LoggedMeal) {
        val targetCal = MealCalendarHelper.getCalendarDateForDay(_selectedDay.value)
        val finalMeal = meal.copy(timestamp = targetCal.timeInMillis)
        repository.logMeal(finalMeal)
    }


    fun logRecipe(recipe: SmartMealRecipe, slot: MealSlot) {
        val targetCal = MealCalendarHelper.getCalendarDateForDay(_selectedDay.value)
        val meal = LoggedMeal(
            name = recipe.name,
            slot = slot,
            calories = recipe.calories,
            proteinG = recipe.proteinG,
            carbsG = recipe.carbsG,
            fatsG = recipe.fatsG,
            portion = "1 serving",
            dietaryTags = recipe.dietaryTags,
            timestamp = targetCal.timeInMillis
        )
        repository.logMeal(meal)
    }

    fun deleteMeal(mealId: String) {
        repository.deleteMeal(mealId)
    }

    fun clearSelectedDay() {
        val targetCal = MealCalendarHelper.getCalendarDateForDay(_selectedDay.value)
        repository.clearMealsForDate(MealCalendarHelper.formatDateKey(targetCal))
    }

    fun clearToday() {
        repository.clearTodayMeals()
    }

    fun toggleDietaryFilter(filter: String) {
        repository.toggleDietaryFilter(filter)
    }

    fun toggleAllergyFilter(allergy: String) {
        repository.toggleAllergyFilter(allergy)
    }

    fun clearFilters() {
        repository.clearAllFilters()
    }

    fun updateGoalPreset(goalName: String, dailyKcal: Int, proteinG: Int, carbsG: Int, fatsG: Int) {
        repository.updateGoals(goalName, dailyKcal, proteinG, carbsG, fatsG)
    }

    fun addWater(ml: Int) {
        repository.addWater(ml)
    }

    fun resetWater() {
        repository.resetWater()
    }
}
