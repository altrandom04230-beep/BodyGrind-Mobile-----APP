package com.example.data.meals

import com.example.utils.ManilaDateTime
import java.util.Calendar
import java.util.UUID

enum class MealSlot(
    val id: String,
    val displayName: String,
    val suggestedKcal: Int,
    val suggestedProteinG: Int
) {
    BREAKFAST("breakfast", "Breakfast", 500, 40),
    MORNING_SNACK("morning_snack", "Morning Snack", 300, 25),
    LUNCH("lunch", "Lunch", 650, 50),
    AFTERNOON_SNACK("afternoon_snack", "Afternoon Snack", 350, 30),
    DINNER("dinner", "Dinner", 600, 45)
}

enum class DayOfWeekPlan(
    val dayNumber: Int,
    val shortName: String,
    val fullName: String
) {
    MONDAY(1, "Mon", "Monday"),
    TUESDAY(2, "Tue", "Tuesday"),
    WEDNESDAY(3, "Wed", "Wednesday"),
    THURSDAY(4, "Thu", "Thursday"),
    FRIDAY(5, "Fri", "Friday"),
    SATURDAY(6, "Sat", "Saturday"),
    SUNDAY(7, "Sun", "Sunday");

    companion object {
        fun fromCalendar(cal: Calendar): DayOfWeekPlan {
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> MONDAY
                Calendar.TUESDAY -> TUESDAY
                Calendar.WEDNESDAY -> WEDNESDAY
                Calendar.THURSDAY -> THURSDAY
                Calendar.FRIDAY -> FRIDAY
                Calendar.SATURDAY -> SATURDAY
                Calendar.SUNDAY -> SUNDAY
                else -> MONDAY
            }
        }

        fun today(): DayOfWeekPlan {
            return fromCalendar(ManilaDateTime.manilaCalendar())
        }
    }
}

data class DaySlotPlan(
    val slot: MealSlot,
    val name: String = slot.displayName,
    val targetKcal: Int = slot.suggestedKcal,
    val targetProteinG: Int = slot.suggestedProteinG
)

data class DayMealPlan(
    val day: DayOfWeekPlan,
    val slots: List<DaySlotPlan> = MealSlot.entries.map { DaySlotPlan(it) }
) {
    val totalTargetKcal: Int get() = slots.sumOf { it.targetKcal }
    val totalTargetProteinG: Int get() = slots.sumOf { it.targetProteinG }

    fun findSlot(slot: MealSlot): DaySlotPlan {
        return slots.find { it.slot == slot } ?: DaySlotPlan(slot)
    }
}

data class LoggedMeal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val slot: MealSlot,
    val calories: Int,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatsG: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val portion: String = "1 serving",
    val dietaryTags: List<String> = emptyList()
)

data class SmartMealRecipe(
    val id: String,
    val name: String,
    val defaultSlot: MealSlot,
    val calories: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatsG: Int,
    val prepTimeMinutes: Int,
    val description: String,
    val ingredients: List<String>,
    val dietaryTags: List<String>,
    val allergens: List<String> = emptyList(),
    val iconCategory: String = "CHICKEN" // CHICKEN, SALMON, BEEF, EGG, OATS, SHAKE, PLANT
)

data class MealGoalSettings(
    val goalName: String = "Build Muscle",
    val dailyCalorieTarget: Int = 2400,
    val proteinTargetG: Int = 180,
    val carbsTargetG: Int = 260,
    val fatsTargetG: Int = 70,
    val targetMealCount: Int = 5
)
