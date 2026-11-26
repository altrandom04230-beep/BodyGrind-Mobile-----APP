package com.example.ui.meals.aicalorie

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.media.ExifInterface
import com.example.data.ai.GeminiApiClient
import com.example.data.meals.LoggedMeal
import com.example.data.meals.MealSlot
import com.example.ui.meals.SmartMealsViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCalorieTrackerScreen(
    mealsViewModel: SmartMealsViewModel,
    onNavigateBack: () -> Unit,
    onMealLogged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val geminiClient = remember { GeminiApiClient() }

    // Input States
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var mealDescription by remember { mutableStateOf("") }

    // Analysis States
    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisError by remember { mutableStateOf<String?>(null) }
    var permissionNotice by remember { mutableStateOf<String?>(null) }

    // Results States
    var analysisResult by remember { mutableStateOf<AiMealEstimateResult?>(null) }
    val editableItems = remember { mutableStateListOf<EstimatedFoodItem>() }
    var mealTitle by remember { mutableStateOf("") }

    // Meal Slot Selection (Smart default based on current hour)
    var selectedMealSlot by remember {
        val hour = com.example.utils.ManilaDateTime.manilaCalendar().get(Calendar.HOUR_OF_DAY)
        val defaultSlot = when {
            hour < 10 -> MealSlot.BREAKFAST
            hour < 12 -> MealSlot.MORNING_SNACK
            hour < 15 -> MealSlot.LUNCH
            hour < 18 -> MealSlot.AFTERNOON_SNACK
            else -> MealSlot.DINNER
        }
        mutableStateOf(defaultSlot)
    }

    // Dynamic Live Totals Calculation
    val totalCalories by remember {
        derivedStateOf { editableItems.sumOf { it.calories } }
    }
    val totalProtein by remember {
        derivedStateOf { editableItems.sumOf { it.proteinG } }
    }
    val totalCarbs by remember {
        derivedStateOf { editableItems.sumOf { it.carbsG } }
    }
    val totalFat by remember {
        derivedStateOf { editableItems.sumOf { it.fatG } }
    }

    // Photo Pickers
    val pickVisualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val (bmp, bytes) = loadAndCompressUri(context, uri)
                withContext(Dispatchers.Main) {
                    selectedBitmap = bmp
                    selectedImageBytes = bytes
                    analysisError = null
                }
            }
        }
    }

    val takePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            selectedBitmap = bitmap
            selectedImageBytes = bitmapToCompressedJpegBytes(bitmap)
            analysisError = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                takePreviewLauncher.launch(null)
            } catch (_: Exception) {
                // Fallback to gallery picker if camera app is unavailable
                pickVisualMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        } else {
            permissionNotice = "Camera permission was denied. You can select a photo from your gallery or type a description."
            // Fallback gracefully to gallery picker per requirements
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    fun onTakePhotoClicked() {
        val hasCameraPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPerm) {
            try {
                takePreviewLauncher.launch(null)
            } catch (_: Exception) {
                pickVisualMediaLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun analyzeMeal() {
        if (!isOnline(context)) {
            analysisError = "No internet connection detected. Please check your Wi-Fi or mobile data and try again."
            return
        }

        if (selectedImageBytes == null && mealDescription.isBlank()) {
            analysisError = "Please take a meal photo or type a description to analyze."
            return
        }

        isAnalyzing = true
        analysisError = null
        permissionNotice = null

        scope.launch {
            val responseResult = geminiClient.analyzeMealNutrition(
                textDescription = mealDescription,
                imageBytes = selectedImageBytes
            )

            isAnalyzing = false

            responseResult.fold(
                onSuccess = { rawJson ->
                    val parseResult = AiCalorieJsonParser.parse(rawJson)
                    parseResult.fold(
                        onSuccess = { estimate ->
                            if (!estimate.isFood) {
                                analysisError = estimate.unclearReason.ifBlank {
                                    "Could not identify recognizable food items. Please try with a clearer photo or a more descriptive meal text."
                                }
                                analysisResult = null
                                editableItems.clear()
                            } else {
                                analysisResult = estimate
                                editableItems.clear()
                                editableItems.addAll(estimate.items)
                                mealTitle = estimate.items.joinToString(", ") { it.name }.take(60)
                            }
                        },
                        onFailure = { err ->
                            analysisError = "Failed to parse nutritional estimate: ${err.message}. Please try again."
                        }
                    )
                },
                onFailure = { err ->
                    analysisError = if (err.message?.contains("GEMINI_API_KEY") == true) {
                        "Gemini API key is not configured. Please set GEMINI_API_KEY in the Secrets panel."
                    } else if (err is java.net.SocketTimeoutException) {
                        "Request timed out. Please try again with a clearer photo or shorter text."
                    } else if (err.message?.contains("404") == true) {
                        val details = err.message?.takeIf { it.contains(GeminiApiClient.MODEL_NAME) }
                            ?: "Model ${GeminiApiClient.MODEL_NAME} returned HTTP 404"
                        "AI analysis error: $details"
                    } else {
                        "AI analysis error: ${err.localizedMessage ?: "Unable to connect"}. Please try again."
                    }
                }
            )
        }
    }

    fun saveMeal() {
        if (editableItems.isEmpty() || totalCalories <= 0) {
            analysisError = "Cannot save an empty or zero-calorie meal. Please add or edit food items."
            return
        }

        val name = mealTitle.trim().ifBlank {
            editableItems.firstOrNull()?.name ?: "AI Logged Meal"
        }

        val combinedPortions = editableItems.joinToString(", ") { "${it.name} (${it.portion})" }

        val loggedMeal = LoggedMeal(
            name = name,
            slot = selectedMealSlot,
            calories = totalCalories,
            proteinG = totalProtein,
            carbsG = totalCarbs,
            fatsG = totalFat,
            portion = combinedPortions,
            dietaryTags = listOf("AI Estimated")
        )

        mealsViewModel.logMeal(loggedMeal)
        onMealLogged()
    }

    BackHandler {
        onNavigateBack()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BoldRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Calorie Tracker",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("ai_calorie_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Smart Meals",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("ai_calorie_tracker_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Introduction Banner Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = BoldRed.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Restaurant,
                                        contentDescription = null,
                                        tint = BoldRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Smart Nutritional Estimation",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Take a photo or type ingredients to estimate macros",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Permission Denial Banner (if any)
            if (!permissionNotice.isNullOrBlank()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = permissionNotice!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Step 1: Input Section Card (Photo + Text)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "1. Meal Input",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Photo Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onTakePhotoClicked() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("ai_calorie_camera_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BoldRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Take Photo",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Camera",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    pickVisualMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("ai_calorie_gallery_button"),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Gallery",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        // Photo Preview if selected
                        selectedBitmap?.let { bmp ->
                            Spacer(modifier = Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Selected meal photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(36.dp)
                                        .clickable {
                                            selectedBitmap = null
                                            selectedImageBytes = null
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Photo",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "Photo Attached",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Text Input
                        OutlinedTextField(
                            value = mealDescription,
                            onValueChange = {
                                mealDescription = it
                                analysisError = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_calorie_description_input"),
                            label = {
                                Text("Describe meal (e.g. 2 eggs, buttered toast, black coffee)")
                            },
                            placeholder = {
                                Text("Optional if photo is attached, or type ingredients and portions")
                            },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Analyze Button
                        val canAnalyze = (selectedImageBytes != null || mealDescription.isNotBlank()) && !isAnalyzing

                        Button(
                            onClick = { analyzeMeal() },
                            enabled = canAnalyze,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("ai_calorie_analyze_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BoldRed,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Analyzing with Gemini AI...",
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (analysisResult != null) "Re-Analyze Meal" else "Analyze Meal with AI",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // Error Card with Retry Button
            if (!analysisError.isNullOrBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_calorie_error_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, DangerRed)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = DangerRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Analysis Issue",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = DangerRed
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = analysisError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { analyzeMeal() },
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .testTag("ai_calorie_retry_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            // Step 2: Editable Results Card
            if (analysisResult != null && editableItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_calorie_results_card"),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BoldRed.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            // Results Header & Confidence Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "2. Estimated Nutrition",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Confidence Pill
                                val (confColor, confLabel) = when (analysisResult?.confidence) {
                                    "high" -> Pair(SuccessGreen, "High Confidence")
                                    "low" -> Pair(DangerRed, "Low Confidence")
                                    else -> Pair(WarningAmber, "Medium Confidence")
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = confColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, confColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = confLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = confColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Every figure is an estimate. Tap any value to edit before saving.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Overall Meal Title
                            OutlinedTextField(
                                value = mealTitle,
                                onValueChange = { mealTitle = it },
                                label = { Text("Meal Title") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ai_calorie_meal_title_input"),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Editable Items List
                            Text(
                                text = "Food Items (${editableItems.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            editableItems.forEachIndexed { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = item.name,
                                                onValueChange = { newName ->
                                                    editableItems[index] = item.copy(name = newName)
                                                },
                                                label = { Text("Item ${index + 1}") },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_calorie_item_name_${index}"),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            OutlinedTextField(
                                                value = item.portion,
                                                onValueChange = { newPortion ->
                                                    editableItems[index] = item.copy(portion = newPortion)
                                                },
                                                label = { Text("Portion") },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_calorie_item_portion_${index}"),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            if (editableItems.size > 1) {
                                                IconButton(
                                                    onClick = { editableItems.removeAt(index) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete Item",
                                                        tint = DangerRed,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Macro Editors
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = if (item.calories == 0) "" else item.calories.toString(),
                                                onValueChange = { str ->
                                                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                                                    editableItems[index] = item.copy(calories = num)
                                                },
                                                label = { Text("kcal", fontSize = 11.sp) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_calorie_item_kcal_${index}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = if (item.proteinG == 0) "" else item.proteinG.toString(),
                                                onValueChange = { str ->
                                                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                                                    editableItems[index] = item.copy(proteinG = num)
                                                },
                                                label = { Text("P(g)", fontSize = 11.sp) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_calorie_item_protein_${index}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = if (item.carbsG == 0) "" else item.carbsG.toString(),
                                                onValueChange = { str ->
                                                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                                                    editableItems[index] = item.copy(carbsG = num)
                                                },
                                                label = { Text("C(g)", fontSize = 11.sp) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_calorie_item_carbs_${index}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            OutlinedTextField(
                                                value = if (item.fatG == 0) "" else item.fatG.toString(),
                                                onValueChange = { str ->
                                                    val num = str.filter { it.isDigit() }.toIntOrNull() ?: 0
                                                    editableItems[index] = item.copy(fatG = num)
                                                },
                                                label = { Text("F(g)", fontSize = 11.sp) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("ai_calorie_item_fat_${index}"),
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                singleLine = true,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Add Item Button
                            TextButton(
                                onClick = {
                                    editableItems.add(
                                        EstimatedFoodItem(
                                            name = "New Item",
                                            portion = "1 serving",
                                            calories = 100,
                                            proteinG = 5,
                                            carbsG = 15,
                                            fatG = 3
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("ai_calorie_add_item_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Food Item")
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Live Totals Display Card
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Estimated Totals",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$totalCalories kcal",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Black,
                                            color = BoldRed
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Protein: ${totalProtein}g",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = BoldRed
                                        )
                                        Text(
                                            text = "Carbs: ${totalCarbs}g",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = WarningAmber
                                        )
                                        Text(
                                            text = "Fat: ${totalFat}g",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = WaterBlueLight
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Select Meal Slot (5 slots)
                            Text(
                                text = "Select Meal Slot",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(MealSlot.entries) { slot ->
                                    val isSelected = slot == selectedMealSlot
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedMealSlot = slot },
                                        label = {
                                            Text(
                                                text = slot.displayName,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BoldRed.copy(alpha = 0.2f),
                                            selectedLabelColor = BoldRed
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Save Button
                            val canSave = totalCalories > 0 && editableItems.isNotEmpty()

                            Button(
                                onClick = { saveMeal() },
                                enabled = canSave,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("save_ai_meal_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BoldRed,
                                    contentColor = Color.White,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Log to ${selectedMealSlot.displayName} ($totalCalories kcal)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            if (!canSave) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Cannot save meal with 0 calories or without food items.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DangerRed,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * Compresses bitmap to JPEG byte array with scaling.
 */
private fun bitmapToCompressedJpegBytes(bitmap: Bitmap, maxDim: Int = 1024): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val scale = if (width > maxDim || height > maxDim) {
        maxDim.toFloat() / maxOf(width, height)
    } else {
        1f
    }

    val scaledBitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(
            bitmap,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }

    val stream = ByteArrayOutputStream()
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
    return stream.toByteArray()
}

/**
 * Loads a URI from content resolver, handles EXIF orientation rotation, and compresses.
 */
private fun loadAndCompressUri(context: Context, uri: Uri, maxDim: Int = 1024): Pair<Bitmap?, ByteArray?> {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val originalBmp = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        if (originalBmp == null) return Pair(null, null)

        // Read EXIF orientation
        val exifStream = context.contentResolver.openInputStream(uri)
        val orientation = if (exifStream != null) {
            val exif = ExifInterface(exifStream)
            val ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            exifStream.close()
            ori
        } else {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        val rotatedBmp = if (matrix.isIdentity) {
            originalBmp
        } else {
            Bitmap.createBitmap(originalBmp, 0, 0, originalBmp.width, originalBmp.height, matrix, true)
        }

        val bytes = bitmapToCompressedJpegBytes(rotatedBmp, maxDim)
        Pair(rotatedBmp, bytes)
    } catch (_: Exception) {
        Pair(null, null)
    }
}
