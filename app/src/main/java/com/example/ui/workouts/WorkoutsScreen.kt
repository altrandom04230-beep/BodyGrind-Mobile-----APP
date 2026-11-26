package com.example.ui.workouts

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.theme.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    plans: List<WorkoutPlanEntity>,
    exercises: List<ExerciseEntity>,
    onSelectPlan: (WorkoutPlanEntity) -> Unit,
    onSelectExercise: (ExerciseEntity) -> Unit,
    onCreateCustomPlan: () -> Unit,
    onOpenMuscleAnatomy: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Workout Plans, 1: Exercise Library
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedDifficultyFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    val categories = listOf("All", "Coach", "Custom", "Full Body", "Abs", "Chest", "Leg", "Arm", "Fat-Burning", "Morning", "Bedtime")
    val difficulties = listOf("All", "Beginner", "Intermediate", "Advanced")

    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var difficultyDropdownExpanded by remember { mutableStateOf(false) }

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isTabletOrDesktop = screenWidthDp >= 768

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp
            val screenHeightDp = configuration.screenHeightDp
            val isCompactWidth = screenWidthDp < 360
            val density = LocalDensity.current
            val maxCategoryMenuHeight = (screenHeightDp * 0.35f).dp.coerceIn(160.dp, 280.dp)
            var categoryTriggerWidthPx by remember { mutableIntStateOf(0) }
            val categoryTriggerWidthDp = with(density) { categoryTriggerWidthPx.toDp() }

            // Screen Title & Primary Segmented Tab Switcher
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = if (isCompactWidth) "Workouts" else "Workout Programs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Primary Segmented Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = BoldRed,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = BoldRed
                        )
                    },
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        selectedContentColor = BoldRed,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = if (isCompactWidth) "Plans (${plans.size})" else "Workout Plans (${plans.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompactWidth) 11.sp else 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        selectedContentColor = BoldRed,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Text(
                                text = if (isCompactWidth) "Library (${exercises.size})" else "Exercise Library (${exercises.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isCompactWidth) 11.sp else 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            if (selectedTab == 0) {
                // WORKOUT PLANS SUB-TAB
                Column(modifier = Modifier.fillMaxSize()) {
                    // Compact Filter Dropdowns & Build Custom Plan Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedCard(
                                onClick = { categoryDropdownExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { categoryTriggerWidthPx = it.width }
                                    .testTag("category_filter_dropdown"),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedCategoryFilter != "All") BoldRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Category",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = selectedCategoryFilter,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (selectedCategoryFilter != "All") BoldRed else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Category",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false },
                                offset = DpOffset(0.dp, 4.dp),
                                modifier = Modifier
                                    .widthIn(min = categoryTriggerWidthDp)
                                    .heightIn(max = maxCategoryMenuHeight)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .testTag("category_filter_menu")
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = cat,
                                                fontWeight = if (selectedCategoryFilter == cat) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedCategoryFilter == cat) BoldRed else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedCategoryFilter = cat
                                            categoryDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("category_filter_$cat")
                                    )
                                }
                            }
                        }

                        // Difficulty Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedCard(
                                onClick = { difficultyDropdownExpanded = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("difficulty_filter_dropdown"),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.outlinedCardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (selectedDifficultyFilter != "All") BoldRed else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Difficulty",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = selectedDifficultyFilter,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (selectedDifficultyFilter != "All") BoldRed else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select Difficulty",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = difficultyDropdownExpanded,
                                onDismissRequest = { difficultyDropdownExpanded = false },
                                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                            ) {
                                difficulties.forEach { diff ->
                                    DropdownMenuItem(
                                        leadingIcon = if (diff != "All") {
                                            {
                                                when (diff) {
                                                    "Beginner" -> Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = SuccessGreen)
                                                    "Intermediate" -> Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldYellow)
                                                    "Advanced" -> Icon(Icons.Default.Whatshot, contentDescription = null, modifier = Modifier.size(16.dp), tint = DangerRed)
                                                    else -> null
                                                }
                                            }
                                        } else null,
                                        text = {
                                            Text(
                                                text = diff,
                                                fontWeight = if (selectedDifficultyFilter == diff) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selectedDifficultyFilter == diff) BoldRed else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            selectedDifficultyFilter = diff
                                            difficultyDropdownExpanded = false
                                        },
                                        modifier = Modifier.testTag("difficulty_filter_$diff")
                                    )
                                }
                            }
                        }

                        // Small "+" icon button to build custom plan
                        FilledTonalIconButton(
                            onClick = onCreateCustomPlan,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("create_custom_plan_button"),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = BoldRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Build Custom Plan",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    val filteredPlans = plans.filter { plan ->
                        val matchesCategory = when (selectedCategoryFilter) {
                            "All" -> true
                            "Coach" -> !plan.assignedByTrainerName.isNullOrBlank()
                            "Custom" -> plan.isCustom
                            else -> plan.category.equals(selectedCategoryFilter, ignoreCase = true)
                        }
                        val matchesDifficulty = when (selectedDifficultyFilter) {
                            "All" -> true
                            else -> plan.difficulty.equals(selectedDifficultyFilter, ignoreCase = true)
                        }
                        matchesCategory && matchesDifficulty
                    }.sortedByDescending { !it.assignedByTrainerName.isNullOrBlank() }

                    if (filteredPlans.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(54.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("No plans in this category", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                if (selectedCategoryFilter == "Custom") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = onCreateCustomPlan,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Create Custom Plan")
                                    }
                                }
                            }
                        }
                    } else {
                        val planRows = if (isTabletOrDesktop) filteredPlans.chunked(2) else filteredPlans.chunked(1)
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(planRows) { rowPlans ->
                                if (isTabletOrDesktop) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        for (plan in rowPlans) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                WorkoutPlanCard(plan = plan, onSelectPlan = onSelectPlan)
                                            }
                                        }
                                        if (rowPlans.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                } else {
                                    WorkoutPlanCard(plan = rowPlans.first(), onSelectPlan = onSelectPlan)
                                }
                            }
                        }
                    }
                }
            } else {
                // EXERCISE LIBRARY SUB-TAB
                Column(modifier = Modifier.fillMaxSize()) {
                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search exercises (e.g. push-ups, squats)...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("exercise_search_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )

                    // Favorites toggle chip row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showOnlyFavorites,
                            onClick = { showOnlyFavorites = !showOnlyFavorites },
                            label = { Text("Favorites Only") },
                            leadingIcon = {
                                Icon(
                                    if (showOnlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (showOnlyFavorites) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }

                    val filteredExercises = exercises.filter { ex ->
                        val matchesQuery = ex.name.contains(searchQuery, ignoreCase = true) ||
                                ex.targetMuscles.contains(searchQuery, ignoreCase = true) ||
                                ex.category.contains(searchQuery, ignoreCase = true)
                        val matchesFav = if (showOnlyFavorites) ex.isFavorite else true
                        matchesQuery && matchesFav
                    }

                    val exerciseRows = if (isTabletOrDesktop) filteredExercises.chunked(2) else filteredExercises.chunked(1)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(exerciseRows) { rowExercises ->
                            if (isTabletOrDesktop) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    for (ex in rowExercises) {
                                        Box(modifier = Modifier.weight(1f)) {
                                            ExerciseLibraryCard(ex = ex, onSelectExercise = onSelectExercise)
                                        }
                                    }
                                    if (rowExercises.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            } else {
                                ExerciseLibraryCard(ex = rowExercises.first(), onSelectExercise = onSelectExercise)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutPlanCard(
    plan: WorkoutPlanEntity,
    onSelectPlan: (WorkoutPlanEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectPlan(plan) }
            .testTag("plan_card_${plan.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!plan.assignedByTrainerName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = BoldRed.copy(alpha = 0.16f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "COACH • ${plan.assignedByTrainerName.uppercase()}",
                                color = BoldRed,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (plan.isCustom) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.2f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "CUSTOM",
                                color = ElectricCyan,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = plan.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = FlameOrange
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = plan.difficulty,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = plan.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = plan.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )

            if (!plan.assignedByTrainerName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (plan.isCompletedByMember) SuccessGreen.copy(alpha = 0.16f) else GoldYellow.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = if (plan.isCompletedByMember) "Completed" else "Assigned",
                            color = if (plan.isCompletedByMember) SuccessGreen else GoldYellow,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (!plan.trainerNote.isNullOrBlank()) {
                        Text(
                            text = plan.trainerNote,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${plan.estimatedDurationMinutes} mins",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View Routine",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = FlameOrange
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ExerciseLibraryCard(
    ex: ExerciseEntity,
    onSelectExercise: (ExerciseEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectExercise(ex) }
            .testTag("library_exercise_${ex.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExerciseVisualizer(
                animationType = ex.animationType,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
                isPaused = true
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(ex.targetMuscles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${ex.difficulty} • ${ex.category}",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlameOrange
                )
            }

            if (ex.isFavorite) {
                Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = DangerRed, modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Info, contentDescription = "Instructions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
