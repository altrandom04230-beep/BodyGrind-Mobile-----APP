package com.example.data.meals

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.utils.ManilaDateTime
import org.json.JSONArray
import org.json.JSONObject

class SmartMealsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_meals_prefs", Context.MODE_PRIVATE)

    val todayKey: String
        get() = ManilaDateTime.toDateKey(ManilaDateTime.todayManila())

    // Weekly meal plans (Monday to Sunday, 5 slots each)
    private val _weeklyPlans = MutableStateFlow<Map<DayOfWeekPlan, DayMealPlan>>(emptyMap())
    val weeklyPlans: StateFlow<Map<DayOfWeekPlan, DayMealPlan>> = _weeklyPlans.asStateFlow()

    // Persistent collection of all logged meals tied to their calendar date
    private val _allLoggedMeals = MutableStateFlow<List<LoggedMeal>>(emptyList())
    val allLoggedMeals: StateFlow<List<LoggedMeal>> = _allLoggedMeals.asStateFlow()

    // Backward-compatible flow of meals for today
    private val _loggedMeals = MutableStateFlow<List<LoggedMeal>>(emptyList())
    val loggedMeals: StateFlow<List<LoggedMeal>> = _loggedMeals.asStateFlow()

    private val _goalSettings = MutableStateFlow(MealGoalSettings())
    val goalSettings: StateFlow<MealGoalSettings> = _goalSettings.asStateFlow()

    private val _activeFilters = MutableStateFlow<Set<String>>(emptySet())
    val activeFilters: StateFlow<Set<String>> = _activeFilters.asStateFlow()

    private val _activeAllergies = MutableStateFlow<Set<String>>(emptySet())
    val activeAllergies: StateFlow<Set<String>> = _activeAllergies.asStateFlow()

    private val _waterIntakeMl = MutableStateFlow(0)
    val waterIntakeMl: StateFlow<Int> = _waterIntakeMl.asStateFlow()

    init {
        loadSettings()
        loadWeeklyPlans()
        loadLoggedMeals()
        loadFilters()
        loadWater()
    }

    private fun loadSettings() {
        val goalName = prefs.getString("goal_name", "Build Muscle") ?: "Build Muscle"
        val dailyKcal = prefs.getInt("daily_kcal", 2400)
        val proteinG = prefs.getInt("protein_g", 180)
        val carbsG = prefs.getInt("carbs_g", 260)
        val fatsG = prefs.getInt("fats_g", 70)
        _goalSettings.value = MealGoalSettings(
            goalName = goalName,
            dailyCalorieTarget = dailyKcal,
            proteinTargetG = proteinG,
            carbsTargetG = carbsG,
            fatsTargetG = fatsG,
            targetMealCount = 5
        )
    }

    /**
     * Requirement 1 & 2: 7 day plans (Mon-Sun).
     * Migration: On first load, copy the existing 5-slot plan into all 7 days so no data is lost.
     */
    private fun loadWeeklyPlans() {
        val jsonStr = prefs.getString("weekly_meal_plans_v2_json", null)
        if (jsonStr == null) {
            // Migration on first load: copy default/current 5-slot plan into all 7 days
            val initialPlans = DayOfWeekPlan.entries.associateWith { day ->
                DayMealPlan(
                    day = day,
                    slots = MealSlot.entries.map { slot ->
                        DaySlotPlan(
                            slot = slot,
                            name = slot.displayName,
                            targetKcal = slot.suggestedKcal,
                            targetProteinG = slot.suggestedProteinG
                        )
                    }
                )
            }
            _weeklyPlans.value = initialPlans
            saveWeeklyPlansToDisk(initialPlans)
            return
        }

        try {
            val rootObj = JSONObject(jsonStr)
            val plansMap = mutableMapOf<DayOfWeekPlan, DayMealPlan>()
            for (day in DayOfWeekPlan.entries) {
                val dayArr = rootObj.optJSONArray(day.name)
                if (dayArr != null) {
                    val slotsList = mutableListOf<DaySlotPlan>()
                    for (i in 0 until dayArr.length()) {
                        val slotObj = dayArr.getJSONObject(i)
                        val slotType = try {
                            MealSlot.valueOf(slotObj.getString("slot"))
                        } catch (_: Exception) {
                            MealSlot.entries.getOrElse(i) { MealSlot.BREAKFAST }
                        }
                        slotsList.add(
                            DaySlotPlan(
                                slot = slotType,
                                name = slotObj.optString("name", slotType.displayName),
                                targetKcal = slotObj.optInt("targetKcal", slotType.suggestedKcal),
                                targetProteinG = slotObj.optInt("targetProteinG", slotType.suggestedProteinG)
                            )
                        )
                    }
                    val completeSlots = MealSlot.entries.map { slot ->
                        slotsList.find { it.slot == slot } ?: DaySlotPlan(slot)
                    }
                    plansMap[day] = DayMealPlan(day, completeSlots)
                } else {
                    plansMap[day] = DayMealPlan(day)
                }
            }
            _weeklyPlans.value = plansMap
        } catch (_: Exception) {
            val fallback = DayOfWeekPlan.entries.associateWith { DayMealPlan(it) }
            _weeklyPlans.value = fallback
            saveWeeklyPlansToDisk(fallback)
        }
    }

    private fun saveWeeklyPlansToDisk(plans: Map<DayOfWeekPlan, DayMealPlan>) {
        try {
            val rootObj = JSONObject()
            for ((day, dayPlan) in plans) {
                val dayArr = JSONArray()
                for (slotPlan in dayPlan.slots) {
                    val slotObj = JSONObject()
                    slotObj.put("slot", slotPlan.slot.name)
                    slotObj.put("name", slotPlan.name)
                    slotObj.put("targetKcal", slotPlan.targetKcal)
                    slotObj.put("targetProteinG", slotPlan.targetProteinG)
                    dayArr.put(slotObj)
                }
                rootObj.put(day.name, dayArr)
            }
            prefs.edit().putString("weekly_meal_plans_v2_json", rootObj.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getDayPlan(day: DayOfWeekPlan): DayMealPlan {
        return _weeklyPlans.value[day] ?: DayMealPlan(day)
    }

    /**
     * Requirement 4: Per-day editing. Editing one day must not change the other days.
     */
    fun updateSlotPlan(
        day: DayOfWeekPlan,
        slot: MealSlot,
        name: String,
        targetKcal: Int,
        targetProteinG: Int
    ) {
        val currentMap = _weeklyPlans.value.toMutableMap()
        val dayPlan = currentMap[day] ?: DayMealPlan(day)
        val updatedSlots = dayPlan.slots.map { s ->
            if (s.slot == slot) {
                s.copy(name = name, targetKcal = targetKcal, targetProteinG = targetProteinG)
            } else {
                s
            }
        }
        currentMap[day] = dayPlan.copy(slots = updatedSlots)
        _weeklyPlans.value = currentMap
        saveWeeklyPlansToDisk(currentMap)
    }

    private fun loadFilters() {
        _activeFilters.value = prefs.getStringSet("active_filters", emptySet()) ?: emptySet()
        _activeAllergies.value = prefs.getStringSet("active_allergies", emptySet()) ?: emptySet()
    }

    private fun loadWater() {
        val savedDate = prefs.getString("water_date", "")
        if (savedDate == todayKey) {
            _waterIntakeMl.value = prefs.getInt("water_ml", 0)
        } else {
            _waterIntakeMl.value = 0
        }
    }

    /**
     * Requirement 5: Logging must save to actual calendar date.
     * Previously logged meals stay tied to their original dates and remain unchanged.
     */
    private fun loadLoggedMeals() {
        val historyJson = prefs.getString("all_logged_meals_v2_json", null)
        val list = mutableListOf<LoggedMeal>()

        if (historyJson != null) {
            try {
                val arr = JSONArray(historyJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(parseLoggedMeal(obj))
                }
            } catch (_: Exception) {
                list.clear()
            }
        } else {
            // Migration: Preserve old logged_meals_json entries
            val oldJson = prefs.getString("logged_meals_json", "[]") ?: "[]"
            try {
                val arr = JSONArray(oldJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(parseLoggedMeal(obj))
                }
                saveAllMealsToDisk(list)
            } catch (_: Exception) {
                list.clear()
            }
        }

        _allLoggedMeals.value = list
        updateTodayMealsFlow(list)
    }

    private fun updateTodayMealsFlow(allMeals: List<LoggedMeal>) {
        val todayMeals = allMeals.filter { formatDateKey(it.timestamp) == todayKey }
        _loggedMeals.value = todayMeals
    }

    private fun parseLoggedMeal(obj: JSONObject): LoggedMeal {
        val slotStr = obj.optString("slot", MealSlot.LUNCH.name)
        val slot = try {
            MealSlot.valueOf(slotStr)
        } catch (_: Exception) {
            MealSlot.LUNCH
        }

        val tagsArr = obj.optJSONArray("dietaryTags")
        val tags = mutableListOf<String>()
        if (tagsArr != null) {
            for (j in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(j))
            }
        }

        return LoggedMeal(
            id = obj.getString("id"),
            name = obj.getString("name"),
            slot = slot,
            calories = obj.getInt("calories"),
            proteinG = obj.optInt("proteinG", 0),
            carbsG = obj.optInt("carbsG", 0),
            fatsG = obj.optInt("fatsG", 0),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            portion = obj.optString("portion", "1 serving"),
            dietaryTags = tags
        )
    }

    private fun serializeLoggedMeal(meal: LoggedMeal): JSONObject {
        val obj = JSONObject()
        obj.put("id", meal.id)
        obj.put("name", meal.name)
        obj.put("slot", meal.slot.name)
        obj.put("calories", meal.calories)
        obj.put("proteinG", meal.proteinG)
        obj.put("carbsG", meal.carbsG)
        obj.put("fatsG", meal.fatsG)
        obj.put("timestamp", meal.timestamp)
        obj.put("portion", meal.portion)
        val tagsArr = JSONArray()
        meal.dietaryTags.forEach { tagsArr.put(it) }
        obj.put("dietaryTags", tagsArr)
        return obj
    }

    private fun saveAllMealsToDisk(meals: List<LoggedMeal>) {
        val jsonArr = JSONArray()
        for (meal in meals) {
            jsonArr.put(serializeLoggedMeal(meal))
        }
        prefs.edit()
            .putString("all_logged_meals_v2_json", jsonArr.toString())
            .apply()
        updateTodayMealsFlow(meals)
    }

    fun logMeal(meal: LoggedMeal) {
        val updated = _allLoggedMeals.value + meal
        _allLoggedMeals.value = updated
        saveAllMealsToDisk(updated)
    }

    fun deleteMeal(mealId: String) {
        val updated = _allLoggedMeals.value.filter { it.id != mealId }
        _allLoggedMeals.value = updated
        saveAllMealsToDisk(updated)
    }

    fun clearMealsForDate(dateKey: String) {
        val updated = _allLoggedMeals.value.filter { formatDateKey(it.timestamp) != dateKey }
        _allLoggedMeals.value = updated
        saveAllMealsToDisk(updated)
    }

    fun clearTodayMeals() {
        clearMealsForDate(todayKey)
    }

    fun getMealsForDateKey(dateKey: String): List<LoggedMeal> {
        return _allLoggedMeals.value.filter { formatDateKey(it.timestamp) == dateKey }
    }

    fun formatDateKey(timestamp: Long): String {
        return ManilaDateTime.toDateKey(timestamp)
    }

    fun updateGoals(goalName: String, dailyKcal: Int, proteinG: Int, carbsG: Int, fatsG: Int) {
        val newSettings = MealGoalSettings(
            goalName = goalName,
            dailyCalorieTarget = dailyKcal,
            proteinTargetG = proteinG,
            carbsTargetG = carbsG,
            fatsTargetG = fatsG,
            targetMealCount = 5
        )
        _goalSettings.value = newSettings
        prefs.edit()
            .putString("goal_name", goalName)
            .putInt("daily_kcal", dailyKcal)
            .putInt("protein_g", proteinG)
            .putInt("carbs_g", carbsG)
            .putInt("fats_g", fatsG)
            .apply()
    }

    fun toggleDietaryFilter(filter: String) {
        val current = _activeFilters.value.toMutableSet()
        if (current.contains(filter)) {
            current.remove(filter)
        } else {
            current.add(filter)
        }
        _activeFilters.value = current
        prefs.edit().putStringSet("active_filters", current).apply()
    }

    fun toggleAllergyFilter(allergy: String) {
        val current = _activeAllergies.value.toMutableSet()
        if (current.contains(allergy)) {
            current.remove(allergy)
        } else {
            current.add(allergy)
        }
        _activeAllergies.value = current
        prefs.edit().putStringSet("active_allergies", current).apply()
    }

    fun clearAllFilters() {
        _activeFilters.value = emptySet()
        _activeAllergies.value = emptySet()
        prefs.edit()
            .remove("active_filters")
            .remove("active_allergies")
            .apply()
    }

    fun addWater(ml: Int) {
        val current = _waterIntakeMl.value + ml
        _waterIntakeMl.value = current
        prefs.edit()
            .putString("water_date", todayKey)
            .putInt("water_ml", current)
            .apply()
    }

    fun resetWater() {
        _waterIntakeMl.value = 0
        prefs.edit()
            .putString("water_date", todayKey)
            .putInt("water_ml", 0)
            .apply()
    }

    // Curated high-protein Smart Gym Meals catalog
    val curatedRecipes: List<SmartMealRecipe> = listOf(
        // Breakfast
        SmartMealRecipe(
            id = "oats_whey_berries",
            name = "Power Oats & Whey",
            defaultSlot = MealSlot.BREAKFAST,
            calories = 450,
            proteinG = 38,
            carbsG = 56,
            fatsG = 9,
            prepTimeMinutes = 5,
            description = "Rolled steel-cut oats stirred with whey isolate, organic blueberries, and almond butter.",
            ingredients = listOf("60g Rolled Oats", "1 scoop Whey Isolate", "100g Blueberries", "15g Natural Almond Butter", "200ml Almond Milk"),
            dietaryTags = listOf("High-Protein", "Vegetarian", "Clean Eating"),
            allergens = listOf("Dairy", "Nuts"),
            iconCategory = "OATS"
        ),
        SmartMealRecipe(
            id = "egg_avocado_toast",
            name = "Egg White & Avocado Sourdough",
            defaultSlot = MealSlot.BREAKFAST,
            calories = 380,
            proteinG = 32,
            carbsG = 30,
            fatsG = 14,
            prepTimeMinutes = 8,
            description = "4 fluffy egg whites plus 1 whole egg served over toasted sourdough with ripe avocado slices.",
            ingredients = listOf("4 Egg Whites", "1 Whole Egg", "1 slice Sourdough Bread", "40g Sliced Avocado", "Sea Salt & Red Pepper Flakes"),
            dietaryTags = listOf("High-Protein", "Dairy-Free", "Vegetarian"),
            allergens = listOf("Eggs", "Gluten"),
            iconCategory = "EGG"
        ),
        SmartMealRecipe(
            id = "greek_yogurt_protein_bowl",
            name = "Greek Yogurt Power Parfait",
            defaultSlot = MealSlot.BREAKFAST,
            calories = 340,
            proteinG = 35,
            carbsG = 34,
            fatsG = 6,
            prepTimeMinutes = 4,
            description = "Thick 0% Greek yogurt layered with whey protein, chia seeds, fresh strawberries, and honey drizzle.",
            ingredients = listOf("200g 0% Greek Yogurt", "1/2 scoop Vanilla Whey", "80g Fresh Strawberries", "10g Chia Seeds", "1 tsp Raw Honey"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Vegetarian"),
            allergens = listOf("Dairy"),
            iconCategory = "OATS"
        ),
        SmartMealRecipe(
            id = "vegan_protein_pancakes",
            name = "Plant Protein Banana Pancakes",
            defaultSlot = MealSlot.BREAKFAST,
            calories = 420,
            proteinG = 30,
            carbsG = 58,
            fatsG = 7,
            prepTimeMinutes = 12,
            description = "Oat flour and pea protein batter cooked golden, topped with banana medallions and sugar-free syrup.",
            ingredients = listOf("50g Oat Flour", "1 scoop Pea Protein", "1 Ripe Banana", "150ml Soy Milk", "1/2 tsp Cinnamon"),
            dietaryTags = listOf("High-Protein", "Vegan", "Vegetarian", "Dairy-Free"),
            allergens = listOf("Soy"),
            iconCategory = "PLANT"
        ),

        // Morning Snack
        SmartMealRecipe(
            id = "tuna_rice_cakes",
            name = "Tuna Crunch Rice Cakes",
            defaultSlot = MealSlot.MORNING_SNACK,
            calories = 260,
            proteinG = 30,
            carbsG = 25,
            fatsG = 3,
            prepTimeMinutes = 4,
            description = "Albacore wild tuna mixed with Dijon mustard and cracked pepper atop crispy brown rice cakes.",
            ingredients = listOf("1 can (120g) Albacore Tuna in Water", "3 Brown Rice Cakes", "1 tsp Dijon Mustard", "Lemon Juice & Black Pepper"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Dairy-Free", "Low-Carb"),
            allergens = listOf("Fish"),
            iconCategory = "SALMON"
        ),
        SmartMealRecipe(
            id = "cottage_cheese_walnut",
            name = "Cottage Cheese & Honey Cup",
            defaultSlot = MealSlot.MORNING_SNACK,
            calories = 280,
            proteinG = 28,
            carbsG = 18,
            fatsG = 9,
            prepTimeMinutes = 2,
            description = "Creamy low-fat cottage cheese paired with crushed walnuts and fresh blueberries.",
            ingredients = listOf("200g Low-Fat Cottage Cheese", "15g Crushed Walnuts", "50g Blueberries", "1/2 tsp Honey"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Vegetarian", "Keto-Friendly"),
            allergens = listOf("Dairy", "Nuts"),
            iconCategory = "OATS"
        ),
        SmartMealRecipe(
            id = "edamame_sea_salt",
            name = "Steamed Edamame Power Bowl",
            defaultSlot = MealSlot.MORNING_SNACK,
            calories = 240,
            proteinG = 22,
            carbsG = 16,
            fatsG = 8,
            prepTimeMinutes = 5,
            description = "Whole organic edamame pods steamed with mineral sea salt and sesame oil drops.",
            ingredients = listOf("180g Edamame in Pods", "Coarse Sea Salt", "1/4 tsp Toasted Sesame Oil"),
            dietaryTags = listOf("High-Protein", "Vegan", "Vegetarian", "Gluten-Free", "Dairy-Free"),
            allergens = listOf("Soy"),
            iconCategory = "PLANT"
        ),

        // Lunch
        SmartMealRecipe(
            id = "grilled_chicken_brown_rice",
            name = "Grilled Chicken & Brown Rice Bowl",
            defaultSlot = MealSlot.LUNCH,
            calories = 560,
            proteinG = 52,
            carbsG = 58,
            fatsG = 12,
            prepTimeMinutes = 15,
            description = "Tender grilled chicken breast seasoned with herbs, served over brown jasmine rice and steamed broccoli.",
            ingredients = listOf("200g Lean Chicken Breast", "150g Cooked Brown Rice", "120g Steamed Broccoli", "1 tsp Extra Virgin Olive Oil", "Garlic Herb Rub"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Dairy-Free", "Clean Eating"),
            allergens = emptyList(),
            iconCategory = "CHICKEN"
        ),
        SmartMealRecipe(
            id = "seared_salmon_sweet_potato",
            name = "Seared Salmon & Sweet Potato",
            defaultSlot = MealSlot.LUNCH,
            calories = 590,
            proteinG = 46,
            carbsG = 48,
            fatsG = 22,
            prepTimeMinutes = 18,
            description = "Pan-seared Atlantic salmon fillet with roasted sweet potato cubes and tender grilled asparagus spears.",
            ingredients = listOf("180g Atlantic Salmon Fillet", "160g Roasted Sweet Potato", "100g Asparagus", "Lemon Wedges", "1 tsp Olive Oil"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Dairy-Free", "Omega-3 Rich"),
            allergens = listOf("Fish"),
            iconCategory = "SALMON"
        ),
        SmartMealRecipe(
            id = "turkey_quinoa_chili",
            name = "Lean Turkey Quinoa Chili",
            defaultSlot = MealSlot.LUNCH,
            calories = 520,
            proteinG = 48,
            carbsG = 46,
            fatsG = 13,
            prepTimeMinutes = 20,
            description = "93% lean ground turkey simmered with black beans, diced bell peppers, roasted tomatoes, and quinoa.",
            ingredients = listOf("180g Extra Lean Ground Turkey", "80g Black Beans", "50g Cooked Quinoa", "Diced Tomatoes & Bell Peppers", "Chili Spice Blend"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Dairy-Free"),
            allergens = emptyList(),
            iconCategory = "BEEF"
        ),
        SmartMealRecipe(
            id = "tofu_edamame_teriyaki_bowl",
            name = "Crispy Tofu & Quinoa Buddha Bowl",
            defaultSlot = MealSlot.LUNCH,
            calories = 490,
            proteinG = 36,
            carbsG = 54,
            fatsG = 15,
            prepTimeMinutes = 15,
            description = "Golden air-fried extra-firm tofu with quinoa, edamame, sliced avocado, and coconut amino glaze.",
            ingredients = listOf("200g Extra-Firm Tofu", "100g Cooked Quinoa", "60g Edamame", "30g Avocado", "Tamari Coconut Amino Sauce"),
            dietaryTags = listOf("High-Protein", "Vegan", "Vegetarian", "Gluten-Free", "Dairy-Free"),
            allergens = listOf("Soy"),
            iconCategory = "PLANT"
        ),

        // Afternoon Snack
        SmartMealRecipe(
            id = "bodygrind_anabolic_shake",
            name = "BodyGrind Anabolic Post-Workout Shake",
            defaultSlot = MealSlot.AFTERNOON_SNACK,
            calories = 340,
            proteinG = 42,
            carbsG = 28,
            fatsG = 6,
            prepTimeMinutes = 3,
            description = "Double scoop whey isolate blended with half a banana, powdered peanut butter, and unsweetened almond milk.",
            ingredients = listOf("40g Whey Isolate", "1/2 Frozen Banana", "15g PB Powder", "300ml Unsweetened Almond Milk", "Ice Cubes"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Vegetarian", "Fast-Absorbing"),
            allergens = listOf("Dairy", "Nuts"),
            iconCategory = "SHAKE"
        ),
        SmartMealRecipe(
            id = "beef_jerky_almonds",
            name = "Grass-Fed Beef Jerky & Raw Almonds",
            defaultSlot = MealSlot.AFTERNOON_SNACK,
            calories = 290,
            proteinG = 32,
            carbsG = 12,
            fatsG = 12,
            prepTimeMinutes = 1,
            description = "Natural smoked grass-fed beef jerky paired with roasted unsalted California almonds.",
            ingredients = listOf("60g Zero-Sugar Grass-Fed Beef Jerky", "20g Raw Almonds"),
            dietaryTags = listOf("High-Protein", "Keto", "Gluten-Free", "Dairy-Free", "Low-Carb"),
            allergens = listOf("Nuts"),
            iconCategory = "BEEF"
        ),
        SmartMealRecipe(
            id = "protein_chia_pudding",
            name = "Chocolate Protein Chia Pudding",
            defaultSlot = MealSlot.AFTERNOON_SNACK,
            calories = 280,
            proteinG = 26,
            carbsG = 20,
            fatsG = 10,
            prepTimeMinutes = 5,
            description = "Chia seeds soaked in almond milk, mixed with rich Dutch chocolate plant protein and cacao nibs.",
            ingredients = listOf("20g Chia Seeds", "1 scoop Chocolate Plant Protein", "200ml Almond Milk", "5g Raw Cacao Nibs"),
            dietaryTags = listOf("High-Protein", "Vegan", "Vegetarian", "Gluten-Free", "Dairy-Free"),
            allergens = emptyList(),
            iconCategory = "PLANT"
        ),

        // Dinner
        SmartMealRecipe(
            id = "sirloin_roasted_potatoes",
            name = "Lean Sirloin & Herb Baby Potatoes",
            defaultSlot = MealSlot.DINNER,
            calories = 620,
            proteinG = 56,
            carbsG = 42,
            fatsG = 21,
            prepTimeMinutes = 20,
            description = "Seared top sirloin steak cut against the grain, served with rosemary baby potatoes and haricot verts.",
            ingredients = listOf("200g Lean Top Sirloin Steak", "160g Rosemary Roasted Potatoes", "100g Green Beans", "Garlic Clove & Olive Oil Spray"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Dairy-Free", "Iron-Rich"),
            allergens = emptyList(),
            iconCategory = "BEEF"
        ),
        SmartMealRecipe(
            id = "citrus_garlic_shrimp",
            name = "Citrus Garlic Shrimp Stir-Fry",
            defaultSlot = MealSlot.DINNER,
            calories = 480,
            proteinG = 45,
            carbsG = 48,
            fatsG = 10,
            prepTimeMinutes = 14,
            description = "Jumbo wild shrimp sautéed in minced garlic and lime zest over steamed jasmine rice with snap peas.",
            ingredients = listOf("220g Wild Gulf Shrimp", "140g Cooked Jasmine Rice", "80g Sugar Snap Peas", "1 tsp Sesame Oil", "Fresh Lime & Garlic"),
            dietaryTags = listOf("High-Protein", "Gluten-Free", "Dairy-Free", "Low-Fat"),
            allergens = listOf("Shellfish"),
            iconCategory = "SALMON"
        ),
        SmartMealRecipe(
            id = "ground_chicken_lettuce_wraps",
            name = "Asian Chicken & Water Chestnut Wraps",
            defaultSlot = MealSlot.DINNER,
            calories = 430,
            proteinG = 48,
            carbsG = 22,
            fatsG = 14,
            prepTimeMinutes = 15,
            description = "Lean minced chicken breast sautéed with ginger, scallions, and water chestnuts in crisp butter lettuce cups.",
            ingredients = listOf("220g Lean Minced Chicken Breast", "4 Butter Lettuce Leaves", "40g Diced Water Chestnuts", "Tamari & Grated Ginger"),
            dietaryTags = listOf("High-Protein", "Low-Carb", "Keto-Friendly", "Dairy-Free", "Gluten-Free"),
            allergens = listOf("Soy"),
            iconCategory = "CHICKEN"
        ),
        SmartMealRecipe(
            id = "lentil_shepherds_pie",
            name = "High-Protein Lentil Shepherd's Bowl",
            defaultSlot = MealSlot.DINNER,
            calories = 520,
            proteinG = 34,
            carbsG = 68,
            fatsG = 11,
            prepTimeMinutes = 25,
            description = "Rich brown lentils stewed with carrots and peas, topped with mashed golden Yukon potato whip.",
            ingredients = listOf("180g Cooked Brown Lentils", "160g Whipped Yukon Potatoes", "60g Carrots & Peas", "Nutritional Yeast & Herbs"),
            dietaryTags = listOf("High-Protein", "Vegan", "Vegetarian", "Gluten-Free", "Dairy-Free"),
            allergens = emptyList(),
            iconCategory = "PLANT"
        )
    )
}
