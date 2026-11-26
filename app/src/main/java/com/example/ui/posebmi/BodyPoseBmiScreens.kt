package com.example.ui.posebmi

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.utils.ManilaDateTime
import com.example.utils.UnitConverter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyPoseBmiMainScreen(
    viewModel: BodyPoseBmiViewModel,
    onNavigateBack: () -> Unit
) {
    val currentSubScreen by viewModel.currentSubScreen.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(BoldRed.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Calculate,
                                contentDescription = null,
                                tint = BoldRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (currentSubScreen) {
                                BodyPoseBmiSubScreen.SCREEN_A_INTRO -> "BMI Calculator"
                                BodyPoseBmiSubScreen.SCREEN_D_BMI_RESULTS -> "BMI Analytics"
                                BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD -> "BMI Dashboard"
                            },
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when (currentSubScreen) {
                                BodyPoseBmiSubScreen.SCREEN_A_INTRO -> onNavigateBack()
                                BodyPoseBmiSubScreen.SCREEN_D_BMI_RESULTS -> viewModel.navigateTo(BodyPoseBmiSubScreen.SCREEN_A_INTRO)
                                BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD -> viewModel.navigateTo(BodyPoseBmiSubScreen.SCREEN_A_INTRO)
                            }
                        },
                        modifier = Modifier.testTag("pose_bmi_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (currentSubScreen != BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD) {
                        IconButton(
                            onClick = { viewModel.navigateTo(BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD) },
                            modifier = Modifier.testTag("nav_to_bmi_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "History",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentSubScreen) {
                BodyPoseBmiSubScreen.SCREEN_A_INTRO -> {
                    ScreenA_BmiCalculator(
                        viewModel = viewModel,
                        onViewHistory = { viewModel.navigateTo(BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD) }
                    )
                }
                BodyPoseBmiSubScreen.SCREEN_D_BMI_RESULTS -> {
                    ScreenD_BmiResults(
                        viewModel = viewModel,
                        onViewHistory = { viewModel.navigateTo(BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD) }
                    )
                }
                BodyPoseBmiSubScreen.SCREEN_E_HISTORY_DASHBOARD -> {
                    ScreenE_HistoryDashboard(
                        viewModel = viewModel,
                        onNewScan = { viewModel.navigateTo(BodyPoseBmiSubScreen.SCREEN_A_INTRO) }
                    )
                }
            }
        }
    }
}

// ============================================================================
// SCREEN A: BMI CALCULATOR
// ============================================================================
@Composable
fun ScreenA_BmiCalculator(
    viewModel: BodyPoseBmiViewModel,
    onViewHistory: () -> Unit
) {
    val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
    val heightInput by viewModel.heightCmInput.collectAsStateWithLifecycle()
    val weightInput by viewModel.weightKgInput.collectAsStateWithLifecycle()
    val heightFeetInput by viewModel.heightFeetInput.collectAsStateWithLifecycle()
    val heightInchesInput by viewModel.heightInchesInput.collectAsStateWithLifecycle()
    val weightLbInput by viewModel.weightLbInput.collectAsStateWithLifecycle()
    val inputError by viewModel.inputErrorMessage.collectAsStateWithLifecycle()
    val calculatedResult by viewModel.calculatedBmiResult.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Hero Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bmi_hero_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "BMI Calculator & Analysis",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Inputs Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Measurements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = useMetric,
                                onClick = { viewModel.setUseMetric(true) },
                                label = { Text("kg / cm", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BoldRed,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("unit_chip_metric")
                            )
                            FilterChip(
                                selected = !useMetric,
                                onClick = { viewModel.setUseMetric(false) },
                                label = { Text("lb / ft", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BoldRed,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("unit_chip_imperial")
                            )
                        }
                    }

                    if (useMetric) {
                        OutlinedTextField(
                            value = heightInput,
                            onValueChange = { viewModel.onMetricHeightChanged(it) },
                            label = { Text("Height (cm)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("height_input_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BoldRed,
                                unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { viewModel.onMetricWeightChanged(it) },
                            label = { Text("Weight (kg)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weight_input_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BoldRed,
                                unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = heightFeetInput,
                                onValueChange = { viewModel.onImperialHeightFeetChanged(it) },
                                label = { Text("Height (ft)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("height_feet_input_field"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BoldRed,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )

                            OutlinedTextField(
                                value = heightInchesInput,
                                onValueChange = { viewModel.onImperialHeightInchesChanged(it) },
                                label = { Text("Inches (in)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("height_inches_input_field"),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BoldRed,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }

                        OutlinedTextField(
                            value = weightLbInput,
                            onValueChange = { viewModel.onImperialWeightLbChanged(it) },
                            label = { Text("Weight (lb)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weight_lb_input_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BoldRed,
                                unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    if (!inputError.isNullOrBlank()) {
                        Text(
                            text = inputError ?: "",
                            color = BoldRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("bmi_input_error")
                        )
                    }
                }
            }
        }

        // Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { viewModel.saveAssessment() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("calculate_analyze_bmi_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed)
                ) {
                    Icon(
                        Icons.Default.Calculate,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Calculate & Analyze BMI",
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp
                    )
                }

                OutlinedButton(
                    onClick = onViewHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("view_history_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = DarkGrayButton,
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VIEW BMI HISTORY & CHARTS",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Result Card displayed when calculation succeeds
        if (calculatedResult != null) {
            val res = calculatedResult!!
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bmi_result_card"),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, BoldRed.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ASSESSED BODY MASS INDEX",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${res.bmi}",
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BoldRed,
                            modifier = Modifier.testTag("bmi_category_pill")
                        ) {
                            Text(
                                text = res.category.uppercase(),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Healthy Weight Range & Height / Weight Details
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Healthy Weight Range (BMI 18.5 – 24.9)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val rangeText = if (useMetric) {
                                    "${res.healthyWeightRangeMin} – ${res.healthyWeightRangeMax} kg"
                                } else {
                                    "${BmiCalculator.kgToLbString(res.healthyWeightRangeMin)} – ${BmiCalculator.kgToLbString(res.healthyWeightRangeMax)} lb"
                                }
                                Text(
                                    text = rangeText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BoldRed
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val detailText = if (useMetric) {
                                    "For entered height: $heightInput cm • Recorded weight: $weightInput kg"
                                } else {
                                    val ftText = heightFeetInput.ifBlank { "0" }
                                    val inText = heightInchesInput.ifBlank { "0" }
                                    "For entered height: $ftText ft $inText in • Recorded weight: $weightLbInput lb"
                                }
                                Text(
                                    text = detailText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved to BMI history",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// ============================================================================
// SCREEN D: BMI RESULTS & CLASSIFICATION
// ============================================================================
@Composable
fun ScreenD_BmiResults(
    viewModel: BodyPoseBmiViewModel,
    onViewHistory: () -> Unit
) {
    val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
    val analysis = viewModel.calculatedBmiResult.collectAsStateWithLifecycle().value ?: return
    val h = viewModel.heightCmInput.collectAsStateWithLifecycle().value
    val w = viewModel.weightKgInput.collectAsStateWithLifecycle().value
    val hFt = viewModel.heightFeetInput.collectAsStateWithLifecycle().value
    val hIn = viewModel.heightInchesInput.collectAsStateWithLifecycle().value
    val wLb = viewModel.weightLbInput.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Primary Score Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bmi_result_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, BoldRed.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "ASSESSED BODY MASS INDEX",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${analysis.bmi}",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BoldRed,
                        modifier = Modifier.testTag("bmi_category_pill")
                    ) {
                        Text(
                            text = analysis.category.uppercase(),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        val healthyRangeStr = if (useMetric) {
                            "Healthy Weight Range: ${analysis.healthyWeightRangeMin} – ${analysis.healthyWeightRangeMax} kg"
                        } else {
                            "Healthy Weight Range: ${BmiCalculator.kgToLbString(analysis.healthyWeightRangeMin)} – ${BmiCalculator.kgToLbString(analysis.healthyWeightRangeMax)} lb"
                        }
                        Text(
                            text = healthyRangeStr,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Height & Weight Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (useMetric) {
                            Text("Height: $h cm", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("•", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Weight: $w kg", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("Height: ${hFt.ifBlank { "0" }} ft ${hIn.ifBlank { "0" }} in", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("•", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Weight: $wLb lb", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Adult Classifications Reference
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Standard Adult BMI Categories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val categories = listOf(
                        Pair("Underweight", "Below 18.5"),
                        Pair("Normal weight", "18.5 – 24.9"),
                        Pair("Overweight", "25.0 – 29.9"),
                        Pair("Obesity", "30.0 or higher")
                    )

                    categories.forEach { (cat, range) ->
                        val isCurrent = analysis.category.equals(cat, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isCurrent) BoldRed.copy(alpha = 0.18f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                cat,
                                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal,
                                color = if (isCurrent) BoldRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                range,
                                fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Analysis & Guidance
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Analysis & Guidance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(analysis.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp)

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Recommended Action Plan:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = BoldRed)
                    Spacer(modifier = Modifier.height(6.dp))

                    analysis.recommendations.forEach { rec ->
                        Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Text("•", color = BoldRed, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(rec, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = analysis.clinicalGuidanceNotice,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Action Buttons
        item {
            OutlinedButton(
                onClick = onViewHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("view_history_from_results_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkGrayButton),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("VIEW BMI DASHBOARD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

// ============================================================================
// SCREEN E: BMI HISTORY & ANALYTICS DASHBOARD
// ============================================================================
@Composable
fun ScreenE_HistoryDashboard(
    viewModel: BodyPoseBmiViewModel,
    onNewScan: () -> Unit
) {
    val useMetric by viewModel.useMetric.collectAsStateWithLifecycle()
    val history by viewModel.bmiHistory.collectAsStateWithLifecycle()
    val latestRecord by viewModel.latestBmiRecord.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // BMI Summary Card (Latest Status)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bmi_latest_summary_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CURRENT BMI STATUS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        latestRecord?.let {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BoldRed
                            ) {
                                Text(
                                    text = it.bmiCategory.uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (latestRecord != null) {
                        val rec = latestRecord!!
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rec.bmiValue}",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(UnitConverter.formatWeight(rec.weightKg, useMetric), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                                Text(UnitConverter.formatHeight(rec.heightCm, useMetric), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Last assessment: ${ManilaDateTime.formatDateTime(rec.assessmentDate)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "No assessments recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Progress Indicators (Comparison with previous assessment)
        if (history.size >= 2) {
            item {
                val current = history[0]
                val previous = history[1]
                val weightDiff = current.weightKg - previous.weightKg
                val bmiDiff = current.bmiValue - previous.bmiValue

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Weight Change Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Weight Change", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            val displayDiff = if (useMetric) weightDiff else UnitConverter.kgToLb(weightDiff)
                            val unitLabel = if (useMetric) "kg" else "lb"
                            val sign = if (displayDiff > 0) "+" else ""
                            Text(
                                text = "$sign${String.format(Locale.US, "%.1f", displayDiff)} $unitLabel",
                                fontWeight = FontWeight.Black,
                                color = if (weightDiff <= 0) SuccessGreen else BoldRed,
                                fontSize = 18.sp
                            )
                            Text("vs previous log", fontSize = 10.sp, color = MaterialTheme.colorScheme.textTertiary)
                        }
                    }

                    // BMI Change Card
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("BMI Change", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            val sign = if (bmiDiff > 0) "+" else ""
                            Text(
                                text = "$sign${String.format(Locale.US, "%.1f", bmiDiff)}",
                                fontWeight = FontWeight.Black,
                                color = if (bmiDiff <= 0) SuccessGreen else BoldRed,
                                fontSize = 18.sp
                            )
                            Text("vs previous log", fontSize = 10.sp, color = MaterialTheme.colorScheme.textTertiary)
                        }
                    }
                }
            }
        }

        // Trend Chart Card (BmiTrendChart continues to show BMI history over time)
        item {
            BmiTrendChartCard(records = history, useMetric = useMetric)
        }

        // Calculate New BMI Action Banner
        item {
            Button(
                onClick = onNewScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("dashboard_new_scan_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed)
            ) {
                Icon(Icons.Default.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("CALCULATE NEW BMI", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
            }
        }

        // History Log List
        item {
            Text(
                text = "Assessment Log (${history.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No prior BMI assessments recorded.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(history) { record ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_record_${record.bmiRecordId}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(BoldRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${record.bmiValue}",
                                fontWeight = FontWeight.Black,
                                color = BoldRed,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    record.bmiCategory,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text("• Manual", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "${UnitConverter.formatWeight(record.weightKg, useMetric)} • ${UnitConverter.formatHeight(record.heightCm, useMetric)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = ManilaDateTime.formatDateTime(record.assessmentDate),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.textTertiary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.deleteBmiRecord(record) },
                            modifier = Modifier.testTag("delete_record_${record.bmiRecordId}")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}
