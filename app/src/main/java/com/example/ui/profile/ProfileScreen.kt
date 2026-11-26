package com.example.ui.profile

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.coaching.CoachingRepository
import com.example.data.gamification.GamificationRules
import com.example.data.local.entity.BadgeEntity
import com.example.data.local.entity.BodyMeasurementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.data.progress.BiometricValidator
import com.example.ui.progress.ProgressScreen
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import com.example.utils.NotificationHelper
import com.example.utils.UnitConverter
import com.example.utils.WorkoutReminderScheduler
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfileEntity,
    onUpdateProfile: (UserProfileEntity) -> Unit,
    onResetData: () -> Unit,
    onOpenLookAndHome: () -> Unit = {},
    onLogOut: () -> Unit = {},
    initialTab: Int = 0,
    sessions: List<WorkoutSessionEntity> = emptyList(),
    measurements: List<BodyMeasurementEntity> = emptyList(),
    badges: List<BadgeEntity> = emptyList(),
    onLogWeight: (Float, Float, Long) -> Unit = { _, _, _ -> },
    onLogMeasurement: (Float, Float, Float?, Float?, Float?, String, Long) -> Unit = { _, _, _, _, _, _, _ -> },
    onDeleteSession: (Long) -> Unit = {},
    onDeleteMeasurement: (Long) -> Unit = {},
    onOpenBodyPoseBmi: () -> Unit = {},
    activeCoachName: String? = null,
    coachUnreadCount: Int = 0,
    onOpenMyCoach: () -> Unit = {},
    onUpdateUseMetric: (Boolean) -> Unit = { metric ->
        onUpdateProfile(userProfile.copy(useMetric = metric))
    },
    onSaveWeeklyProgressRecord: (
        memberName: String,
        memberAge: Int,
        weightKg: Float,
        heightCm: Float,
        chestCm: Float,
        armCm: Float,
        waistCm: Float,
        useMetric: Boolean,
        onSaved: (Int) -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val coachingRepo = remember(context) { CoachingRepository(context) }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    var canonicalWeightKg by remember { mutableFloatStateOf(userProfile.weightKg.takeIf { it > 0f } ?: 70.0f) }
    var canonicalHeightCm by remember { mutableFloatStateOf(userProfile.heightCm.takeIf { it > 0f } ?: 175.0f) }
    var weightDirty by remember { mutableStateOf(false) }
    var heightDirty by remember { mutableStateOf(false) }

    var nameInput by remember { mutableStateOf(userProfile.name) }
    var birthdateInput by remember { mutableStateOf(userProfile.birthdate.orEmpty()) }
    var genderInput by remember { mutableStateOf(userProfile.gender) }
    var weightInput by remember {
        mutableStateOf(BiometricValidator.formatWeightInput(canonicalWeightKg, userProfile.useMetric))
    }
    var heightInput by remember {
        mutableStateOf(BiometricValidator.formatHeightInput(canonicalHeightCm, userProfile.useMetric))
    }
    var selectedGoal by remember { mutableStateOf(userProfile.fitnessGoal) }
    var selectedLevel by remember { mutableStateOf(userProfile.fitnessLevel) }
    var selectedDuration by remember { mutableIntStateOf(userProfile.preferredDuration) }
    var profileValidationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun openEditProfileDialog() {
        val wKg = userProfile.weightKg.takeIf { it > 0f } ?: 70.0f
        val hCm = userProfile.heightCm.takeIf { it > 0f } ?: 175.0f
        nameInput = userProfile.name
        birthdateInput = userProfile.birthdate.orEmpty()
        genderInput = userProfile.gender
        canonicalWeightKg = wKg
        canonicalHeightCm = hCm
        weightDirty = false
        heightDirty = false
        weightInput = BiometricValidator.formatWeightInput(wKg, userProfile.useMetric)
        heightInput = BiometricValidator.formatHeightInput(hCm, userProfile.useMetric)
        selectedGoal = userProfile.fitnessGoal
        selectedLevel = userProfile.fitnessLevel
        selectedDuration = userProfile.preferredDuration
        profileValidationErrors = emptyMap()
        showEditProfileDialog = true
    }

    fun switchDialogUnit(targetMetric: Boolean) {
        if (targetMetric == userProfile.useMetric) return
        if (weightDirty) {
            BiometricValidator.resolveMetricWeightOrNull(weightInput, userProfile.useMetric, canonicalWeightKg, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalWeightKg = it }
            weightDirty = false
        }
        if (heightDirty) {
            BiometricValidator.resolveMetricLengthOrNull(heightInput, userProfile.useMetric, canonicalHeightCm, true)
                ?.takeIf { it.isFinite() && it > 0f }
                ?.let { canonicalHeightCm = it }
            heightDirty = false
        }
        profileValidationErrors = profileValidationErrors - "weight" - "height"
        onUpdateUseMetric(targetMetric)
    }

    LaunchedEffect(userProfile.useMetric, showEditProfileDialog) {
        if (showEditProfileDialog) {
            if (!weightDirty) {
                weightInput = BiometricValidator.formatWeightInput(canonicalWeightKg, userProfile.useMetric)
            }
            if (!heightDirty) {
                heightInput = BiometricValidator.formatHeightInput(canonicalHeightCm, userProfile.useMetric)
            }
        }
    }

    val goals = listOf(
        "Build Muscle",
        "Lose Weight",
        "Improve Endurance",
        "Improve Flexibility",
        "Maintain Fitness",
        "Daily Exercise Habit"
    )
    val levels = listOf("Beginner", "Intermediate", "Advanced")
    val durations = listOf(15, 30, 45, 60)
    val genders = listOf("Male", "Female", "Prefer not to say")

    // Edit Fitness Profile Dialog (Comprehensive with Biometrics, Goals, and Routine Preferences)
    if (showEditProfileDialog) {
        val dialogScrollState = rememberScrollState()
        val dialogScreenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
        val isCompactDialog = dialogScreenWidth < 440
        val isNarrowDialog = dialogScreenWidth < 380

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .widthIn(max = 520.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Fitness Profile", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(dialogScrollState)
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // SECTION 1: Personal Identity
                    Text(
                        text = "ATHLETE IDENTITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            profileValidationErrors = profileValidationErrors - "name"
                        },
                        label = { Text("Your Name / Handle") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_name_input"),
                        singleLine = true,
                        isError = profileValidationErrors.containsKey("name"),
                        supportingText = profileValidationErrors["name"]?.let { { Text(it, color = DangerRed) } },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    var showBirthdatePicker by remember { mutableStateOf(false) }

                    if (showBirthdatePicker) {
                        val cal = java.util.Calendar.getInstance(ManilaDateTime.MANILA_ZONE_ID)
                        val initialYear = if (birthdateInput.isNotBlank()) {
                            birthdateInput.substringBefore("-").toIntOrNull() ?: (cal.get(java.util.Calendar.YEAR) - 25)
                        } else {
                            cal.get(java.util.Calendar.YEAR) - 25
                        }
                        val initialMonth = if (birthdateInput.isNotBlank() && birthdateInput.contains("-")) {
                            val parts = birthdateInput.split("-")
                            if (parts.size >= 2) (parts[1].toIntOrNull()?.minus(1)) ?: 0 else 0
                        } else 0
                        val initialDay = if (birthdateInput.isNotBlank() && birthdateInput.contains("-")) {
                            val parts = birthdateInput.split("-")
                            if (parts.size >= 3) parts[2].toIntOrNull() ?: 1 else 1
                        } else 1

                        val datePickerDialog = remember(context) {
                            android.app.DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val formatted = String.format("%04d-%02d-%02d", y, m + 1, d)
                                    birthdateInput = formatted
                                    profileValidationErrors = profileValidationErrors - "birthdate"
                                    showBirthdatePicker = false
                                },
                                initialYear,
                                initialMonth,
                                initialDay
                            ).apply {
                                setOnCancelListener { showBirthdatePicker = false }
                                setOnDismissListener { showBirthdatePicker = false }
                            }
                        }

                        DisposableEffect(showBirthdatePicker) {
                            datePickerDialog.show()
                            onDispose { datePickerDialog.dismiss() }
                        }
                    }

                    // Birthdate Picker (calculates age automatically)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBirthdatePicker = true }
                    ) {
                        OutlinedTextField(
                            value = if (birthdateInput.isNotBlank()) ManilaDateTime.formatBirthdate(birthdateInput) else "",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            label = { Text("Birthdate *") },
                            placeholder = { Text("Select your birthdate") },
                            leadingIcon = {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            isError = profileValidationErrors.containsKey("birthdate"),
                            supportingText = {
                                if (profileValidationErrors.containsKey("birthdate")) {
                                    Text(profileValidationErrors["birthdate"] ?: "", color = DangerRed)
                                } else if (birthdateInput.isNotBlank()) {
                                    val calcAge = ManilaDateTime.calculateAge(birthdateInput)
                                    Text("Age: $calcAge yrs (calculated)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                } else {
                                    Text("Required to calculate age, daily calories, and discounts", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = if (profileValidationErrors.containsKey("birthdate")) DangerRed else MaterialTheme.colorScheme.controlBorder,
                                disabledLabelColor = MaterialTheme.colorScheme.primary,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                                disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_birthdate_input"),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Biological Sex / Gender Selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Gender", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Male", "Female").forEach { g ->
                                val isSelected = genderInput.equals(g, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { genderInput = g },
                                    label = { Text(g, fontSize = 12.sp) },
                                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // SECTION 2: Biometrics (Weight & Height with shared unit conversion support)
                    Text(
                        text = "BODY BIOMETRICS (${if (userProfile.useMetric) "METRIC" else "IMPERIAL"})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    if (isCompactDialog) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Weight unit:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = userProfile.useMetric,
                                    onClick = { switchDialogUnit(true) },
                                    label = { Text("kg", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_weight_unit_kg"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = !userProfile.useMetric,
                                    onClick = { switchDialogUnit(false) },
                                    label = { Text("lb", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_weight_unit_lb"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Height unit:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = userProfile.useMetric,
                                    onClick = { switchDialogUnit(true) },
                                    label = { Text("cm", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_height_unit_cm"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = !userProfile.useMetric,
                                    onClick = { switchDialogUnit(false) },
                                    label = { Text("in", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_height_unit_inches"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Wt:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = userProfile.useMetric,
                                    onClick = { switchDialogUnit(true) },
                                    label = { Text("kg", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_weight_unit_kg"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = !userProfile.useMetric,
                                    onClick = { switchDialogUnit(false) },
                                    label = { Text("lb", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_weight_unit_lb"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Ht:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                FilterChip(
                                    selected = userProfile.useMetric,
                                    onClick = { switchDialogUnit(true) },
                                    label = { Text("cm", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_height_unit_cm"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = !userProfile.useMetric,
                                    onClick = { switchDialogUnit(false) },
                                    label = { Text("in", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("profile_height_unit_inches"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    if (isNarrowDialog) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = {
                                    if (it.isEmpty() || (it.count { c -> c == '.' } <= 1 && it.all { c -> c.isDigit() || c == '.' })) {
                                        weightInput = it
                                        weightDirty = true
                                        profileValidationErrors = profileValidationErrors - "weight"
                                    }
                                },
                                label = { Text("Weight") },
                                trailingIcon = { Text(if (userProfile.useMetric) "kg" else "lb", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_weight_input"),
                                singleLine = true,
                                isError = profileValidationErrors.containsKey("weight"),
                                supportingText = profileValidationErrors["weight"]?.let { { Text(it, color = DangerRed) } },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )

                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = {
                                    if (it.isEmpty() || (it.count { c -> c == '.' } <= 1 && it.all { c -> c.isDigit() || c == '.' })) {
                                        heightInput = it
                                        heightDirty = true
                                        profileValidationErrors = profileValidationErrors - "height"
                                    }
                                },
                                label = { Text("Height") },
                                trailingIcon = { Text(if (userProfile.useMetric) "cm" else "inches", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("profile_height_input"),
                                singleLine = true,
                                isError = profileValidationErrors.containsKey("height"),
                                supportingText = profileValidationErrors["height"]?.let { { Text(it, color = DangerRed) } },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = weightInput,
                                onValueChange = {
                                    if (it.isEmpty() || (it.count { c -> c == '.' } <= 1 && it.all { c -> c.isDigit() || c == '.' })) {
                                        weightInput = it
                                        weightDirty = true
                                        profileValidationErrors = profileValidationErrors - "weight"
                                    }
                                },
                                label = { Text("Weight") },
                                trailingIcon = { Text(if (userProfile.useMetric) "kg" else "lb", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_weight_input"),
                                singleLine = true,
                                isError = profileValidationErrors.containsKey("weight"),
                                supportingText = profileValidationErrors["weight"]?.let { { Text(it, color = DangerRed) } },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )

                            OutlinedTextField(
                                value = heightInput,
                                onValueChange = {
                                    if (it.isEmpty() || (it.count { c -> c == '.' } <= 1 && it.all { c -> c.isDigit() || c == '.' })) {
                                        heightInput = it
                                        heightDirty = true
                                        profileValidationErrors = profileValidationErrors - "height"
                                    }
                                },
                                label = { Text("Height") },
                                trailingIcon = { Text(if (userProfile.useMetric) "cm" else "inches", fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_height_input"),
                                singleLine = true,
                                isError = profileValidationErrors.containsKey("height"),
                                supportingText = profileValidationErrors["height"]?.let { { Text(it, color = DangerRed) } },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // SECTION 3: Experience Level
                    Text(
                        text = "FITNESS EXPERIENCE LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        levels.forEach { lvl ->
                            val isSelected = selectedLevel == lvl
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedLevel = lvl },
                                label = { Text(lvl, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // SECTION 4: Primary Fitness Goal
                    Text(
                        text = "PRIMARY FITNESS GOAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    goals.forEach { g ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedGoal = g }
                                .padding(vertical = 3.dp)
                        ) {
                            RadioButton(
                                selected = selectedGoal == g,
                                onClick = { selectedGoal = g },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(g, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // SECTION 5: Preferred Daily Duration
                    Text(
                        text = "PREFERRED SESSION DURATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        durations.forEach { dur ->
                            val isSelected = selectedDuration == dur
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDuration = dur },
                                label = { Text("${dur}m", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedBirthdate = birthdateInput.trim().takeIf { it.isNotBlank() }
                        val validated = BiometricValidator.validateProfileInputs(
                            nameInput = nameInput,
                            ageInput = userProfile.age.toString(),
                            weightInput = weightInput,
                            heightInput = heightInput,
                            useMetric = userProfile.useMetric,
                            canonicalWeightKg = canonicalWeightKg,
                            canonicalHeightCm = canonicalHeightCm,
                            weightDirty = weightDirty,
                            heightDirty = heightDirty,
                            birthdateInput = trimmedBirthdate
                        )
                        if (!validated.isValid) {
                            profileValidationErrors = validated.errors
                            return@Button
                        }

                        profileValidationErrors = emptyMap()
                        onUpdateProfile(
                            userProfile.copy(
                                name = validated.cleanName,
                                birthdate = trimmedBirthdate,
                                gender = genderInput,
                                weightKg = validated.weightKg,
                                heightCm = validated.heightCm,
                                fitnessGoal = selectedGoal,
                                fitnessLevel = selectedLevel,
                                preferredDuration = selectedDuration
                            )
                        )
                        if (trimmedBirthdate != null) {
                            coroutineScope.launch {
                                val currentUid = FirebaseAuth.getInstance().currentUser?.uid
                                if (currentUid != null) {
                                    coachingRepo.updateMemberBirthdate(currentUid, trimmedBirthdate)
                                }
                            }
                        }
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("save_profile_button")
                ) {
                    Text("Save Profile", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Reset All Data?", fontWeight = FontWeight.Bold) },
            text = { Text("This will reset your workout history, achievements, and custom workouts to initial defaults.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetData()
                    }
                ) {
                    Text("Reset", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = BoldRed,
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        text = "Profile",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = BoldRed,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("tab_profile")
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        text = "Progress",
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Progress",
                        modifier = Modifier.size(18.dp)
                    )
                },
                selectedContentColor = BoldRed,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("tab_progress")
            )
        }

        if (selectedTab == 0) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    Text(
                        text = "Profile & Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                }

                // Reminder banner if birthdate is not set
                if (!userProfile.hasBirthdate()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("birthdate_reminder_banner"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Add Your Birthdate",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Add your birthdate to accurately calculate your age, calorie targets, and discounts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { openEditProfileDialog() },
                                    modifier = Modifier.testTag("add_birthdate_banner_button"),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Set Date", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Profile Avatar Card (respects Privacy)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            val profileLevelInfo = GamificationRules.levelInfoForXp(userProfile.xpPoints, userProfile.level)
                            val calculatedAge = userProfile.calculateAge()
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.name.ifBlank { GamificationRules.DEFAULT_DISPLAY_NAME },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    text = "${userProfile.fitnessGoal.ifBlank { GamificationRules.DEFAULT_FITNESS_GOAL }} • $calculatedAge yrs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (userProfile.hasBirthdate()) {
                                    Text(
                                        text = "Born: ${userProfile.birthdate?.let { ManilaDateTime.formatBirthdate(it) }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // Member Type badge & Verification
                                val verificationTag = if (userProfile.isVerified) "Verified" else "Pending Verification"
                                val discountAppliesList = listOfNotNull(
                                    if (userProfile.discountAppliesToBilling) "Billing" else null,
                                    if (userProfile.discountAppliesToPackages) "Packages" else null,
                                    if (userProfile.discountAppliesToSessionFees) "Sessions" else null
                                )
                                Text(
                                    text = "Membership: ${userProfile.memberType} ($verificationTag)",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (userProfile.isVerified) MaterialTheme.colorScheme.primary else FlameOrange
                                )
                                if (userProfile.discountPercent > 0.0 && discountAppliesList.isNotEmpty()) {
                                    Text(
                                        text = "Discount: ${userProfile.discountPercent.toInt()}% off on ${discountAppliesList.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Level ${profileLevelInfo.level} • ${profileLevelInfo.currentXp} XP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            IconButton(
                                onClick = { openEditProfileDialog() },
                                modifier = Modifier.testTag("edit_profile_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                            }
                        }
                    }
                }

                // Progress & Analytics Navigation Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTab = 1 }
                            .testTag("open_progress_tab_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BoldRed.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Analytics, contentDescription = null, tint = BoldRed, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Progress & Analytics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // My Coach & Data Privacy Navigation Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenMyCoach() }
                            .testTag("open_my_coach_card"),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(FlameOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Sports, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("My Coach & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (!activeCoachName.isNullOrBlank()) "Linked to $activeCoachName" else "Connect via QR or code • Privacy controls",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (coachUnreadCount > 0) {
                                    Surface(shape = CircleShape, color = BoldRed, modifier = Modifier.padding(end = 6.dp)) {
                                        Text(
                                            text = "$coachUnreadCount",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Look & Home Settings Navigation Link (Item 5)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenLookAndHome() }
                            .testTag("open_look_and_home_button"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Look & Home Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }



        // Show AI Coach Button Toggle Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (userProfile.aiCoachEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Show AI Coach Button", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = userProfile.aiCoachEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateProfile(userProfile.copy(aiCoachEnabled = enabled))
                        },
                        colors = appSwitchColors()
                    )
                }
            }
        }

        // Display & Units Preferences (Item 3 with UnitConverter)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Metric units toggle
                    val profileScreenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                    if (profileScreenWidth < 400) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("Measurement Units", style = MaterialTheme.typography.bodyMedium)
                                val weightStr = UnitConverter.formatWeight(userProfile.weightKg, userProfile.useMetric)
                                val heightStr = UnitConverter.formatHeight(userProfile.heightCm, userProfile.useMetric)
                                Text("$weightStr • $heightStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = userProfile.useMetric,
                                    onClick = { onUpdateUseMetric(true) },
                                    label = { Text("kg / cm") },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("prefs_unit_metric"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = !userProfile.useMetric,
                                    onClick = { onUpdateUseMetric(false) },
                                    label = { Text("lb / ft") },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("prefs_unit_imperial"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text("Measurement Units", style = MaterialTheme.typography.bodyMedium)
                                val weightStr = UnitConverter.formatWeight(userProfile.weightKg, userProfile.useMetric)
                                val heightStr = UnitConverter.formatHeight(userProfile.heightCm, userProfile.useMetric)
                                Text("$weightStr • $heightStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = userProfile.useMetric,
                                    onClick = { onUpdateUseMetric(true) },
                                    label = { Text("kg / cm") },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("prefs_unit_metric"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                                FilterChip(
                                    selected = !userProfile.useMetric,
                                    onClick = { onUpdateUseMetric(false) },
                                    label = { Text("lb / ft") },
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                        .testTag("prefs_unit_imperial"),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Account & Session
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Account & Session", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onLogOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = FlameOrange),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FlameOrange.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("logout_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Log Out", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // About & Data Reset
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Data & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { showResetConfirmDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                        modifier = Modifier.fillMaxWidth().testTag("reset_data_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset App Data to Defaults")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
} else {
    ProgressScreen(
        userProfile = userProfile,
        sessions = sessions,
        measurements = measurements,
        badges = badges,
        onLogWeight = onLogWeight,
        onLogMeasurement = onLogMeasurement,
        onDeleteSession = onDeleteSession,
        onDeleteMeasurement = onDeleteMeasurement,
        onOpenBodyPoseBmi = onOpenBodyPoseBmi,
        onUpdateUseMetric = onUpdateUseMetric,
        onSaveWeeklyProgressRecord = onSaveWeeklyProgressRecord
    )
}
}
}
