package com.example.ui.meals

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.meals.DayMealPlan
import com.example.data.meals.DayOfWeekPlan
import com.example.data.meals.DaySlotPlan
import com.example.data.meals.LoggedMeal
import com.example.data.meals.MealSlot
import com.example.data.meals.SmartMealRecipe
import com.example.ui.theme.*
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartMealsScreen(
    viewModel: SmartMealsViewModel,
    onNavigateBack: () -> Unit = {},
    onOpenAiCalorieTracker: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showFiltersSheet by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var selectedSlotForLogging by remember { mutableStateOf<MealSlot?>(null) }
    var selectedRecipeForDetail by remember { mutableStateOf<SmartMealRecipe?>(null) }
    var slotPlanToEdit by remember { mutableStateOf<DaySlotPlan?>(null) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingLogConfirmation by remember { mutableStateOf<Triple<MealSlot, Int, String>?>(null) }
    var nextSnackbarId by remember { mutableIntStateOf(0) }
    var activeSnackbarEvent by remember { mutableStateOf<Pair<Int, String>?>(null) }

    LaunchedEffect(uiState.selectedDay) {
        pendingLogConfirmation = null
    }

    LaunchedEffect(uiState.mealsBySlot, pendingLogConfirmation) {
        val pending = pendingLogConfirmation ?: return@LaunchedEffect
        val (slot, previousCount, message) = pending
        val currentCount = uiState.mealsBySlot[slot]?.size ?: 0
        if (currentCount > previousCount) {
            pendingLogConfirmation = null
            nextSnackbarId += 1
            activeSnackbarEvent = nextSnackbarId to message
        }
    }

    LaunchedEffect(activeSnackbarEvent) {
        val event = activeSnackbarEvent ?: return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        withTimeoutOrNull(3000L) {
            snackbarHostState.showSnackbar(
                message = event.second,
                duration = SnackbarDuration.Indefinite
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("smart_meals_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Top Header
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Smart Meals",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BoldRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, BoldRed.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clickable { showGoalDialog = true }
                                .testTag("edit_goal_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Goal: ${uiState.goalName} · ${uiState.calorieGoal} kcal/day",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.badgeTextRed,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Goal",
                                    tint = MaterialTheme.colorScheme.badgeTextRed,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Water intake pill
                    val isDark = LocalIsDarkTheme.current
                    val waterAccent = if (isDark) WaterBlueDark else WaterBlueLight
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable { viewModel.addWater(250) }
                                .testTag("add_water_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = "Water Intake",
                                tint = waterAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${uiState.waterIntakeMl} ml",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "+250",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = waterAccent
                            )
                        }
                    }
                }
            }
        }

        // Day selector: Mon-Sun selector at top of Meals screen (Requirement 3)
        item {
            DayOfWeekSelector(
                selectedDay = uiState.selectedDay,
                onSelectDay = { viewModel.selectDay(it) }
            )
        }

        // Calorie & Macro Dashboard Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("meals_calorie_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (screenWidthDp < 360) 14.dp else 20.dp)
                ) {
                    // 0/5 logged status & reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${uiState.loggedMealCount}/${uiState.targetMealCount} logged",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.loggedMealCount > 0) MaterialTheme.colorScheme.badgeTextRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (uiState.loggedMeals.isNotEmpty()) {
                            Text(
                                text = "Clear Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textTertiary,
                                modifier = Modifier
                                    .clickable { viewModel.clearSelectedDay() }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Prominent Calories Display: "**0** of 2,400 kcal"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = "${uiState.consumedCalories}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (screenWidthDp < 360) 34.sp else 42.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "of ${uiState.calorieGoal} kcal",
                                style = if (screenWidthDp < 360) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(bottom = if (screenWidthDp < 360) 3.dp else 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${uiState.remainingCalories}",
                                style = if (screenWidthDp < 360) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = if (uiState.remainingCalories > 0) BoldRed else SuccessGreen
                            )
                            Text(
                                text = "kcal left",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textTertiary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Main Calorie Progress Bar
                    val calorieProgress = (uiState.consumedCalories.toFloat() / uiState.calorieGoal.coerceAtLeast(1))
                        .coerceIn(0f, 1f)

                    LinearProgressIndicator(
                        progress = { calorieProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Macros Breakdown Row (Protein, Carbs, Fats)
                    val isDark = LocalIsDarkTheme.current
                    val carbsColor = if (isDark) ChartWaistAmberDark else ChartWaistAmberLight
                    val fatsColor = if (isDark) ChartChestBlueDark else ChartChestBlueLight
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MacroIndicator(
                            title = "Protein",
                            currentG = uiState.proteinConsumedG,
                            targetG = uiState.proteinTargetG,
                            color = BoldRed,
                            modifier = Modifier.weight(1f)
                        )
                        MacroIndicator(
                            title = "Carbs",
                            currentG = uiState.carbsConsumedG,
                            targetG = uiState.carbsTargetG,
                            color = carbsColor,
                            modifier = Modifier.weight(1f)
                        )
                        MacroIndicator(
                            title = "Fats",
                            currentG = uiState.fatsConsumedG,
                            targetG = uiState.fatsTargetG,
                            color = fatsColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Dietary & Allergy Filters Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { showFiltersSheet = true }
                            .testTag("dietary_filters_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dietary & allergy filters",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        val totalFilterCount = uiState.activeFilters.size + uiState.activeAllergies.size
                        if (totalFilterCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "$totalFilterCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.activeFilters.isNotEmpty() || uiState.activeAllergies.isNotEmpty()) {
                        Text(
                            text = "Reset",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.badgeTextRed,
                            modifier = Modifier.clickable { viewModel.clearFilters() }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Filter Chips Row
                val quickChips = listOf(
                    "High-Protein",
                    "Gluten-Free",
                    "Dairy-Free",
                    "Low-Carb",
                    "Vegan",
                    "Vegetarian",
                    "Keto"
                )

                val sharedFilterChipColors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.colorScheme.badgeTextRed
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickChips) { chipLabel ->
                        val allergenKey = if (chipLabel.endsWith("-Free")) chipLabel.removeSuffix("-Free") else null
                        val isSelected = uiState.activeFilters.contains(chipLabel) ||
                                (allergenKey != null && uiState.activeAllergies.contains(allergenKey)) ||
                                uiState.activeAllergies.contains(chipLabel)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (chipLabel.endsWith("-Free")) {
                                    val allergen = chipLabel.removeSuffix("-Free")
                                    viewModel.toggleAllergyFilter(allergen)
                                } else {
                                    viewModel.toggleDietaryFilter(chipLabel)
                                }
                            },
                            label = { Text(chipLabel, fontSize = 12.sp) },
                            colors = sharedFilterChipColors,
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outline,
                                selectedBorderColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // Section Title: Weekly Meal Slots for Selected Day (Requirement 3: e.g. "Monday Meal Plan (5 Slots)")
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        tint = BoldRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${uiState.selectedDay.fullName} Meal Plan (5 Slots)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.testTag("meal_plan_section_header")
                    )
                }

                Text(
                    text = uiState.selectedDateFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.badgeTextRed,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 5 Daily Meal Slots for Selected Day (Requirement 1, 3, 4)
        items(uiState.dayPlan.slots, key = { it.slot.id }) { slotPlan ->
            val slot = slotPlan.slot
            val loggedInThisSlot = uiState.mealsBySlot[slot] ?: emptyList()
            val isSlotFilled = loggedInThisSlot.isNotEmpty()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .testTag("meal_slot_card_${slot.id}"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, if (isSlotFilled) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Slot Header (fixed at top, never scrolls)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isSlotFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isSlotFilled) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Text(
                                            text = "${slot.ordinal + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = slotPlan.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Target ~${slotPlan.targetKcal} kcal · ${slotPlan.targetProteinG}g P",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Edit slot plan button for this day only (Requirement 4)
                            IconButton(
                                onClick = { slotPlanToEdit = slotPlan },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("edit_slot_button_${slot.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit ${slotPlan.name} Plan",
                                    tint = MaterialTheme.colorScheme.badgeTextRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            // Log Meal Button for Slot
                            FilledTonalButton(
                                onClick = { selectedSlotForLogging = slot },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (isSlotFilled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isSlotFilled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("log_meal_button_${slot.id}")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isSlotFilled) "Add More" else "Log Meal",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Display Logged Meals in this Slot inside a scrollable container with overflow-only scrollbar
                    if (loggedInThisSlot.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        val listScrollState = key(uiState.selectedDay) { rememberScrollState() }
                        val hasOverflow by remember(listScrollState) {
                            derivedStateOf { listScrollState.maxValue > 0 }
                        }
                        val scrollbarThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        val scrollbarTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag("meal_slot_items_container_${slot.id}")
                                .drawWithContent {
                                    drawContent()
                                    val maxScroll = listScrollState.maxValue.toFloat()
                                    if (maxScroll > 0f && size.height > 0f) {
                                        val viewportHeight = size.height
                                        val totalContentHeight = viewportHeight + maxScroll
                                        val thumbWidthPx = 3.dp.toPx()
                                        val cornerRadiusPx = 1.5.dp.toPx()
                                        val minThumbHeightPx = 24.dp.toPx().coerceAtMost(viewportHeight)
                                        val thumbHeight = (viewportHeight * (viewportHeight / totalContentHeight))
                                            .coerceIn(minThumbHeightPx, viewportHeight)
                                        val scrollFraction = (listScrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
                                        val thumbTop = scrollFraction * (viewportHeight - thumbHeight)
                                        val barLeft = size.width - thumbWidthPx

                                        drawRoundRect(
                                            color = scrollbarTrackColor,
                                            topLeft = Offset(barLeft, 0f),
                                            size = Size(thumbWidthPx, viewportHeight),
                                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                        )
                                        drawRoundRect(
                                            color = scrollbarThumbColor,
                                            topLeft = Offset(barLeft, thumbTop),
                                            size = Size(thumbWidthPx, thumbHeight),
                                            cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                        )
                                    }
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(listScrollState)
                                    .padding(end = if (hasOverflow) 8.dp else 0.dp)
                            ) {
                                loggedInThisSlot.forEach { meal ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = meal.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${meal.calories} kcal  •  ${meal.proteinG}g P  •  ${meal.carbsG}g C  •  ${meal.fatsG}g F",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.badgeTextRed
                                            )
                                        }

                                        IconButton(
                                            onClick = { viewModel.deleteMeal(meal.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Delete Meal",
                                                tint = MaterialTheme.colorScheme.textTertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Recommended Smart Gym Meals
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = BoldRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Curated High-Protein Recipes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "${uiState.availableRecipes.size} recipes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Horizontal Showcase of Curated Recipes
        item {
            if (uiState.availableRecipes.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.textTertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No recipes match your active filters",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Try clearing some dietary or allergy tags",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { viewModel.clearFilters() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset Filters")
                        }
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.availableRecipes) { recipe ->
                        RecipeShowcaseCard(
                            recipe = recipe,
                            onClick = { selectedRecipeForDetail = recipe },
                            onQuickLog = { slot ->
                                val previousCount = uiState.mealsBySlot[slot]?.size ?: 0
                                viewModel.logRecipe(recipe, slot)
                                pendingLogConfirmation = Triple(
                                    slot,
                                    previousCount,
                                    "${recipe.name} logged to ${slot.displayName}"
                                )
                            }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("meal_logged_snackbar_host")
    ) { snackbarData ->
        Snackbar(
            shape = RoundedCornerShape(14.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
                .testTag("meal_logged_snackbar")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = snackbarData.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    }

    // Modal Sheet: Log Meal Dialog / Selector for a specific Slot
    selectedSlotForLogging?.let { slot ->
        ModalBottomSheet(
            onDismissRequest = { selectedSlotForLogging = null },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
        ) {
            MealLoggerSheet(
                slot = slot,
                availableRecipes = uiState.availableRecipes.filter { it.defaultSlot == slot || it.defaultSlot.suggestedKcal in (slot.suggestedKcal - 150)..(slot.suggestedKcal + 150) },
                onLogRecipe = { recipe ->
                    viewModel.logRecipe(recipe, slot)
                    selectedSlotForLogging = null
                },
                onLogCustom = { name, kcal, p, c, f ->
                    viewModel.logCustomMeal(name, slot, kcal, p, c, f)
                    selectedSlotForLogging = null
                },
                onDismiss = { selectedSlotForLogging = null }
            )
        }
    }

    // Modal Sheet: Dietary & Allergy Filters
    if (showFiltersSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFiltersSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
        ) {
            DietaryFiltersSheet(
                activeDietary = uiState.activeFilters,
                activeAllergies = uiState.activeAllergies,
                onToggleDietary = { viewModel.toggleDietaryFilter(it) },
                onToggleAllergy = { viewModel.toggleAllergyFilter(it) },
                onClearAll = { viewModel.clearFilters() },
                onDismiss = { showFiltersSheet = false }
            )
        }
    }

    // Modal Sheet: Recipe Details
    selectedRecipeForDetail?.let { recipe ->
        ModalBottomSheet(
            onDismissRequest = { selectedRecipeForDetail = null },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline) }
        ) {
            RecipeDetailSheet(
                recipe = recipe,
                onLogToSlot = { slot ->
                    viewModel.logRecipe(recipe, slot)
                    selectedRecipeForDetail = null
                },
                onDismiss = { selectedRecipeForDetail = null }
            )
        }
    }

    // Goal Edit Dialog
    if (showGoalDialog) {
        GoalSettingsDialog(
            currentGoalName = uiState.goalName,
            currentKcal = uiState.calorieGoal,
            onSaveGoal = { name, kcal, p, c, f ->
                viewModel.updateGoalPreset(name, kcal, p, c, f)
                showGoalDialog = false
            },
            onDismiss = { showGoalDialog = false }
        )
    }

    // Edit Slot Plan Dialog for Selected Day (Requirement 4)
    slotPlanToEdit?.let { slotPlan ->
        EditSlotPlanDialog(
            day = uiState.selectedDay,
            slotPlan = slotPlan,
            onSave = { name, kcal, protein ->
                viewModel.updateSlotPlan(uiState.selectedDay, slotPlan.slot, name, kcal, protein)
                slotPlanToEdit = null
            },
            onDismiss = { slotPlanToEdit = null }
        )
    }
}

// -------------------------------------------------------------
// Subcomponents
// -------------------------------------------------------------

@Composable
fun DayOfWeekSelector(
    selectedDay: DayOfWeekPlan,
    onSelectDay: (DayOfWeekPlan) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = BoldRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Weekly Schedule",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Repeats Weekly",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 7 Day Selector Row (Mon to Sun)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DayOfWeekPlan.entries.forEach { day ->
                    val isSelected = day == selectedDay
                    val isToday = day == DayOfWeekPlan.today()

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSelected -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.outline
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable { onSelectDay(day) }
                            .testTag("day_selector_${day.name.lowercase()}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = day.shortName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (isToday) MaterialTheme.colorScheme.badgeTextRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            } else {
                                Text(
                                    text = "D${day.dayNumber}",
                                    fontSize = 9.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.textTertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditSlotPlanDialog(
    day: DayOfWeekPlan,
    slotPlan: DaySlotPlan,
    onSave: (name: String, targetKcal: Int, targetProteinG: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(slotPlan.name) }
    var kcalStr by remember { mutableStateOf(slotPlan.targetKcal.toString()) }
    var proteinStr by remember { mutableStateOf(slotPlan.targetProteinG.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit ${day.fullName} ${slotPlan.slot.displayName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Slot Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_slot_name_input")
                )

                OutlinedTextField(
                    value = kcalStr,
                    onValueChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            kcalStr = it
                            errorMessage = null
                        }
                    },
                    label = { Text("Target Calories (kcal)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_slot_kcal_input")
                )

                OutlinedTextField(
                    value = proteinStr,
                    onValueChange = {
                        if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                            proteinStr = it
                            errorMessage = null
                        }
                    },
                    label = { Text("Target Protein (g)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_slot_protein_input")
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.badgeTextRed,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val kcal = kcalStr.toIntOrNull()
                    val protein = proteinStr.toIntOrNull()

                    if (trimmedName.isEmpty()) {
                        errorMessage = "Meal slot name cannot be empty."
                        return@Button
                    }
                    if (kcal == null || kcal <= 0) {
                        errorMessage = "Calories must be a positive number."
                        return@Button
                    }
                    if (protein == null || protein < 0) {
                        errorMessage = "Protein must be 0 or higher."
                        return@Button
                    }

                    onSave(trimmedName, kcal, protein)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_slot_plan_button")
            ) {
                Text("Save Changes", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MacroIndicator(
    title: String,
    currentG: Int,
    targetG: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "${currentG}g",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val prog = (currentG.toFloat() / targetG.coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { prog },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.outlineVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "of ${targetG}g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.textTertiary,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun RecipeShowcaseCard(
    recipe: SmartMealRecipe,
    onClick: () -> Unit,
    onQuickLog: (MealSlot) -> Unit
) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = recipe.defaultSlot.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.badgeTextRed
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.textTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${recipe.prepTimeMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.textTertiary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = recipe.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${recipe.calories} kcal  •  ${recipe.proteinG}g Protein",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.badgeTextRed
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = recipe.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action: Quick Log Button
            Button(
                onClick = { onQuickLog(recipe.defaultSlot) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Log to ${recipe.defaultSlot.displayName}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// -------------------------------------------------------------
// Bottom Sheets
// -------------------------------------------------------------

@Composable
fun MealLoggerSheet(
    slot: MealSlot,
    availableRecipes: List<SmartMealRecipe>,
    onLogRecipe: (SmartMealRecipe) -> Unit,
    onLogCustom: (String, Int, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    var selectedTab by remember { mutableIntStateOf(0) }

    // Custom meal form states
    var customName by remember { mutableStateOf("") }
    var customKcal by remember { mutableStateOf("") }
    var customProtein by remember { mutableStateOf("") }
    var customCarbs by remember { mutableStateOf("") }
    var customFat by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Log Meal for ${slot.displayName}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Suggested: ~${slot.suggestedKcal} kcal · ${slot.suggestedProteinG}g protein",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.textTertiary
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs: Smart Suggestions vs Custom Add
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Smart Suggestions (${availableRecipes.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Custom Quick Add", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
            // Curated Suggestions
            if (availableRecipes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching curated meals for this slot with current filters.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isLandscape) 200.dp else 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(availableRecipes) { recipe ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLogRecipe(recipe) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = recipe.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${recipe.calories} kcal  •  ${recipe.proteinG}g P  •  ${recipe.carbsG}g C  •  ${recipe.fatsG}g F",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.badgeTextRed
                                    )
                                }

                                Button(
                                    onClick = { onLogRecipe(recipe) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("Add", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Custom Quick Add Form
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Meal or Food Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customKcal,
                        onValueChange = { customKcal = it },
                        label = { Text("Calories") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = customProtein,
                        onValueChange = { customProtein = it },
                        label = { Text("Protein (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = customCarbs,
                        onValueChange = { customCarbs = it },
                        label = { Text("Carbs (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = customFat,
                        onValueChange = { customFat = it },
                        label = { Text("Fat (g)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        val kcal = customKcal.toIntOrNull() ?: 0
                        val p = customProtein.toIntOrNull() ?: 0
                        val c = customCarbs.toIntOrNull() ?: 0
                        val f = customFat.toIntOrNull() ?: 0
                        val name = customName.ifBlank { "Custom ${slot.displayName}" }
                        onLogCustom(name, kcal, p, c, f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Log Custom Meal", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DietaryFiltersSheet(
    activeDietary: Set<String>,
    activeAllergies: Set<String>,
    onToggleDietary: (String) -> Unit,
    onToggleAllergy: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val dietaryOptions = listOf(
        "High-Protein",
        "Low-Carb",
        "Keto",
        "Clean Eating",
        "Vegetarian",
        "Vegan",
        "Omega-3 Rich"
    )

    val allergyOptions = listOf(
        "Gluten",
        "Dairy",
        "Nuts",
        "Eggs",
        "Soy",
        "Fish",
        "Shellfish"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Dietary & Allergy Filters",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Clear All",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.badgeTextRed,
                modifier = Modifier.clickable { onClearAll() }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "DIETARY PREFERENCES",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.badgeTextRed
        )
        Spacer(modifier = Modifier.height(8.dp))

        dietaryOptions.forEach { option ->
            val isChecked = activeDietary.contains(option)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleDietary(option) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(option, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleDietary(option) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedColor = MaterialTheme.colorScheme.controlBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "EXCLUDE ALLERGENS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.badgeTextRed
        )
        Spacer(modifier = Modifier.height(8.dp))

        allergyOptions.forEach { allergen ->
            val isChecked = activeAllergies.contains(allergen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleAllergy(allergen) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Exclude $allergen", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onToggleAllergy(allergen) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                        uncheckedColor = MaterialTheme.colorScheme.controlBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Apply Filters", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun RecipeDetailSheet(
    recipe: SmartMealRecipe,
    onLogToSlot: (MealSlot) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Text(
                    text = recipe.defaultSlot.displayName,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.badgeTextRed
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = recipe.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "${recipe.calories} kcal  •  ${recipe.proteinG}g Protein  •  ${recipe.carbsG}g Carbs  •  ${recipe.fatsG}g Fat",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.badgeTextRed
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = recipe.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "INGREDIENTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.badgeTextRed
        )

        Spacer(modifier = Modifier.height(6.dp))

        recipe.ingredients.forEach { ingredient ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = ingredient,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log To Slot Buttons
        Text(
            text = "LOG TO TODAY'S MEALS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MealSlot.entries.forEach { slot ->
                FilledTonalButton(
                    onClick = { onLogToSlot(slot) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (slot == recipe.defaultSlot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (slot == recipe.defaultSlot) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = when (slot) {
                            MealSlot.BREAKFAST -> "B'fast"
                            MealSlot.MORNING_SNACK -> "Snack 1"
                            MealSlot.LUNCH -> "Lunch"
                            MealSlot.AFTERNOON_SNACK -> "Snack 2"
                            MealSlot.DINNER -> "Dinner"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun GoalSettingsDialog(
    currentGoalName: String,
    currentKcal: Int,
    onSaveGoal: (String, Int, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var goalName by remember { mutableStateOf(currentGoalName) }
    var targetKcal by remember { mutableStateOf(currentKcal.toString()) }

    val presets = listOf(
        Triple("Build Muscle", 2400, "180g P · 260g C · 70g F"),
        Triple("Lean Shred / Cut", 1800, "160g P · 150g C · 50g F"),
        Triple("Clean Bulking", 2800, "200g P · 320g C · 80g F"),
        Triple("Maintenance", 2100, "150g P · 220g C · 65g F")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text("Calorie & Nutrition Goal", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select a fitness goal preset or enter custom daily calories:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                presets.forEach { (presetName, presetKcal, macroBreakdown) ->
                    val isSelected = goalName == presetName
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                goalName = presetName
                                targetKcal = presetKcal.toString()
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = presetName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.badgeTextRed else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$presetKcal kcal",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.badgeTextRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = macroBreakdown,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.textTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = targetKcal,
                    onValueChange = { targetKcal = it },
                    label = { Text("Daily Calorie Target") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kcal = targetKcal.toIntOrNull() ?: 2400
                    // Calculate balanced macros based on calories
                    val proteinG = (kcal * 0.30f / 4f).toInt()
                    val carbsG = (kcal * 0.45f / 4f).toInt()
                    val fatsG = (kcal * 0.25f / 9f).toInt()
                    onSaveGoal(goalName, kcal, proteinG, carbsG, fatsG)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Goal", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}
