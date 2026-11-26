package com.example.ui.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import com.example.data.local.entity.UserProfileEntity
import com.example.ui.theme.*

@Composable
fun OnboardingScreen(
    initialProfile: UserProfileEntity,
    onCompleteOnboarding: (UserProfileEntity) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val horizontalPadding = if (screenWidthDp < 360) 16.dp else 24.dp

    var step by remember { mutableIntStateOf(1) }
    val totalSteps = 6

    var fitnessGoal by remember { mutableStateOf(initialProfile.fitnessGoal) }
    var fitnessLevel by remember { mutableStateOf(initialProfile.fitnessLevel) }
    var preferredDuration by remember { mutableIntStateOf(initialProfile.preferredDuration) }
    var selectedDays by remember { mutableStateOf(setOf("Mon", "Wed", "Fri")) }
    var weightText by remember { mutableStateOf(initialProfile.weightKg.toInt().toString()) }
    var heightText by remember { mutableStateOf(initialProfile.heightCm.toInt().toString()) }
    var reminderEnabled by remember { mutableStateOf(true) }

    val goals = listOf(
        Pair("Lose Weight", "Burn fat and improve cardiovascular conditioning"),
        Pair("Build Muscle", "Increase strength and muscle tone at home"),
        Pair("Improve Endurance", "Boost stamina and stamina reserves"),
        Pair("Improve Flexibility", "Release tension and increase range of motion"),
        Pair("Maintain Fitness", "Stay active and preserve daily health"),
        Pair("Daily Exercise Habit", "Build unbreakable discipline 15 mins a day")
    )

    val levels = listOf(
        Pair("Beginner", "I rarely work out or am just getting started"),
        Pair("Intermediate", "I train 1-3 times a week consistently"),
        Pair("Advanced", "I am in great shape and ready for high intensity")
    )

    val durations = listOf(10, 15, 20, 30)
    val weekDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun finish() {
        val w = weightText.toFloatOrNull() ?: 70f
        val h = heightText.toFloatOrNull() ?: 175f
        val updated = initialProfile.copy(
            fitnessGoal = fitnessGoal,
            fitnessLevel = fitnessLevel,
            preferredDuration = preferredDuration,
            weightKg = w,
            heightCm = h,
            reminderEnabled = reminderEnabled,
            onboardingCompleted = true
        )
        onCompleteOnboarding(updated)
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 1) {
                    IconButton(onClick = { step-- }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Step dots
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..totalSteps) {
                        Box(
                            modifier = Modifier
                                .size(if (i == step) 16.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == step) FlameOrange else MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }

                if (step < totalSteps) {
                    TextButton(onClick = { finish() }) {
                        Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }
        },
        bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 8.dp) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            if (step < totalSteps) {
                                step++
                            } else {
                                finish()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_next_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = FlameOrange),
                        shape = RoundedCornerShape(27.dp)
                    ) {
                        Text(
                            text = if (step == totalSteps) "START MY JOURNEY" else "CONTINUE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPadding, vertical = 12.dp)
            ) {
                when (step) {
                1 -> {
                    // Goal
                    Column {
                        Text("What is your main goal?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        goals.forEach { (g, desc) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { fitnessGoal = g },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (fitnessGoal == g) FlameOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                ),
                                border = if (fitnessGoal == g) androidx.compose.foundation.BorderStroke(2.dp, FlameOrange) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(g, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (fitnessGoal == g) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FlameOrange)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Level
                    Column {
                        Text("Choose your fitness level", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        levels.forEach { (lvl, desc) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { fitnessLevel = lvl },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (fitnessLevel == lvl) FlameOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                ),
                                border = if (fitnessLevel == lvl) androidx.compose.foundation.BorderStroke(2.dp, FlameOrange) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lvl, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (fitnessLevel == lvl) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FlameOrange)
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Duration
                    Column {
                        Text("Target workout duration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        durations.forEach { dur ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable { preferredDuration = dur },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (preferredDuration == dur) FlameOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                                ),
                                border = if (preferredDuration == dur) androidx.compose.foundation.BorderStroke(2.dp, FlameOrange) else null
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("$dur Minutes / Day", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    if (preferredDuration == dur) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FlameOrange)
                                    }
                                }
                            }
                        }
                    }
                }
                4 -> {
                    // Available workout days
                    Column {
                        Text("Select workout days", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (screenWidthDp < 360) 4.dp else 6.dp)
                        ) {
                            weekDays.forEach { day ->
                                val isSelected = selectedDays.contains(day)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(CircleShape)
                                        .background(if (isSelected) FlameOrange else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable {
                                            selectedDays = if (isSelected) selectedDays - day else selectedDays + day
                                        }
                                ) {
                                    Text(
                                        text = day,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (screenWidthDp < 360) 12.sp else 14.sp,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                5 -> {
                    // Body measurements
                    Column {
                        Text("Optional body measurements", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it },
                            label = { Text("Weight (kg)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it },
                            label = { Text("Height (cm)") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            singleLine = true
                        )
                    }
                }
                6 -> {
                    // Safety Notice
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = if (isLandscape) 12.dp else 36.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(if (isLandscape) 64.dp else 80.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(if (isLandscape) 36.dp else 48.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("You're All Set!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = FlameOrange)

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Safety Notice:\nPlease warm up before exercising and listen to your body. If you feel sharp pain or dizziness, stop and rest immediately. Consult your physician before starting any rigorous fitness regimen.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            }
        }
    }
}
