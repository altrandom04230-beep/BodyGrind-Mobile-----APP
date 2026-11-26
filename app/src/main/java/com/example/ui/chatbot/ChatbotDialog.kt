package com.example.ui.chatbot

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.RagSourceItem
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutScheduleEntity
import com.example.data.local.entity.WorkoutSessionEntity
import com.example.ui.components.ExerciseVisualizer
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotDialog(
    viewModel: ChatbotViewModel,
    userProfile: UserProfileEntity?,
    allExercises: List<ExerciseEntity>,
    allPlans: List<WorkoutPlanEntity>,
    allSchedules: List<WorkoutScheduleEntity> = emptyList(),
    recentSessions: List<WorkoutSessionEntity>,
    streakDays: Int,
    onDismiss: () -> Unit,
    onSelectExercise: (ExerciseEntity) -> Unit,
    onSelectPlan: (WorkoutPlanEntity) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var showClearChatConfirmDialog by remember { mutableStateOf(false) }
    var showTopDisclaimer by rememberSaveable { mutableStateOf(true) }
    var showRagInfoDialog by remember { mutableStateOf(false) }

    // Text To Speech Management
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(context) {
        var ttsInstance: TextToSpeech? = null
        ttsInstance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsInstance?.language = Locale.US
            }
        }
        tts = ttsInstance
        onDispose {
            try {
                ttsInstance.stop()
                ttsInstance.shutdown()
            } catch (_: Exception) {}
        }
    }

    fun toggleTts(messageId: String, textToSpeak: String) {
        if (speakingMessageId == messageId) {
            tts?.stop()
            speakingMessageId = null
        } else {
            tts?.stop()
            speakingMessageId = messageId
            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, messageId)
        }
    }

    fun shareMessage(text: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share Workout Advice")
            context.startActivity(shareIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open share dialog", Toast.LENGTH_SHORT).show()
        }
    }

    // Speech Recognition Launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spokenText else "$inputText $spokenText"
            }
        }
    }

    // Auto-scroll to bottom when messages update
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val suggestionChips = listOf(
        "What's my workout schedule this week?",
        "Recommend a workout for Chest & Triceps",
        "Tell me about Coach Cobie and Coach Jun",
        "What's my streak and workout level?",
        "How can I relieve sore muscles?"
    )

    if (showRagInfoDialog) {
        AlertDialog(
            onDismissRequest = { showRagInfoDialog = false },
            icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(28.dp)) },
            title = { Text("In-App RAG Dataset Active", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your AI Fitness Coach uses Retrieval-Augmented Generation (RAG) to query your device database in real time:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("• In-App Exercise Library (Form tips, biomechanics, calories)", style = MaterialTheme.typography.bodySmall)
                    Text("• Predefined & Custom Workout Plans (All difficulties)", style = MaterialTheme.typography.bodySmall)
                    Text("• Weekly Workout Schedules (Monday-Sunday schedules)", style = MaterialTheme.typography.bodySmall)
                    Text("• Gym Trainers Knowledge (Coach Cobie & Coach Jun)", style = MaterialTheme.typography.bodySmall)
                    Text("• 25 Target Muscle Groups (Target Biomechanics & Educational Insights)", style = MaterialTheme.typography.bodySmall)
                    Text("• Live Athlete Telemetry (Streak, Level, XP, BMI, Burned Calories)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showRagInfoDialog = false }) {
                    Text("Got It", color = FlameOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showClearChatConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirmDialog = false },
            title = { Text("Clear Chat History?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear your current conversation history with your AI Fitness Coach?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearChatConfirmDialog = false
                        tts?.stop()
                        speakingMessageId = null
                        viewModel.clearChat()
                    }
                ) {
                    Text("Clear", color = DangerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = {
            tts?.stop()
            speakingMessageId = null
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable { showRagInfoDialog = true }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .background(FlameOrange.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "AI Fitness Coach",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = FlameOrange.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "RAG ACTIVE",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FlameOrange,
                                    maxLines = 1
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SuccessGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Live Dataset Augmented • Voice Enabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))

                Row {
                    IconButton(
                        onClick = {
                            val transcript = messages.joinToString("\n\n") { msg ->
                                "${if (msg.isUser) "Me" else "AI Coach"}: ${msg.text}"
                            }
                            if (transcript.isNotBlank()) {
                                shareMessage(transcript)
                            }
                        },
                        modifier = Modifier.testTag("share_chat_transcript_button")
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share Chat Transcript",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { showClearChatConfirmDialog = true },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(
                        onClick = {
                            tts?.stop()
                            speakingMessageId = null
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_chatbot_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Suggestion Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestionChips) { chip ->
                    SuggestionChip(
                        onClick = {
                            viewModel.sendMessage(
                                query = chip,
                                userProfile = userProfile,
                                allExercises = allExercises,
                                allPlans = allPlans,
                                allSchedules = allSchedules,
                                recentSessions = recentSessions,
                                streakDays = streakDays
                            )
                        },
                        label = { Text(chip, style = MaterialTheme.typography.labelSmall) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // One-time Top Disclaimer Popup / Banner with "X" close button
            if (showTopDisclaimer) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GoldYellow.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldYellow.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = "Medical & Fitness Disclaimer",
                            tint = GoldYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Disclaimer: Fitness guidance is for educational purposes. Consult a coach (e.g. Coach Cobie or Coach Jun) or doctor before starting strenuous regimens.",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { showTopDisclaimer = false },
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("dismiss_disclaimer_button")
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss disclaimer",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Message Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        isSpeaking = speakingMessageId == msg.id,
                        onToggleTts = { toggleTts(msg.id, msg.text) },
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onShare = { shareMessage(msg.text) },
                        onRetry = {
                            viewModel.retryMessage(
                                botMessageId = msg.id,
                                userProfile = userProfile,
                                allExercises = allExercises,
                                allPlans = allPlans,
                                allSchedules = allSchedules,
                                recentSessions = recentSessions,
                                streakDays = streakDays
                            )
                        },
                        onEditUserMessage = { text ->
                            inputText = text
                        },
                        onSelectExercise = onSelectExercise,
                        onSelectPlan = onSelectPlan
                    )
                }
            }

            // Bottom Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask about exercises, form, or routines...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chatbot_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(
                                            RecognizerIntent.EXTRA_PROMPT,
                                            "Speak to your AI Fitness Coach..."
                                        )
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Voice input not supported or speech service unavailable on this device", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.testTag("voice_input_button")
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = FlameOrange
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val textToSend = inputText
                                inputText = ""
                                viewModel.sendMessage(
                                    query = textToSend,
                                    userProfile = userProfile,
                                    allExercises = allExercises,
                                    allPlans = allPlans,
                                    allSchedules = allSchedules,
                                    recentSessions = recentSessions,
                                    streakDays = streakDays
                                )
                            }
                        },
                        enabled = inputText.isNotBlank() && !isGenerating,
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                if (inputText.isNotBlank() && !isGenerating) FlameOrange else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .testTag("send_chat_button")
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FlameOrange, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isSpeaking: Boolean,
    onToggleTts: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
    onEditUserMessage: (String) -> Unit,
    onSelectExercise: (ExerciseEntity) -> Unit,
    onSelectPlan: (WorkoutPlanEntity) -> Unit
) {
    val isUser = message.isUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            if (!isUser) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .background(FlameOrange.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(Icons.Default.FitnessCenter, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    ),
                    color = if (isUser) FlameOrange else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (message.isGenerating) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = FlameOrange, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Retrieving fitness dataset & reasoning...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Action Buttons for Chat Bubble
                if (!message.isGenerating) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier
                            .align(if (isUser) Alignment.End else Alignment.Start)
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isUser) {
                            // User Bubble Actions: Copy, Edit, Share (pushed to end edge, flush with bubble)
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("user_copy_button_${message.id}")
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            IconButton(
                                onClick = { onEditUserMessage(message.text) },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("user_edit_button_${message.id}")
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit prompt",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("user_share_button_${message.id}")
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share message",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        } else {
                            // AI Bubble Actions: Copy, Share, TTS, Retry (aligned with AI bubble)
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("ai_copy_button_${message.id}")
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy AI response",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }

                            IconButton(
                                onClick = onShare,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("ai_share_button_${message.id}")
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share AI response",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }

                            IconButton(
                                onClick = onToggleTts,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("ai_tts_button_${message.id}")
                            ) {
                                Icon(
                                    if (isSpeaking) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isSpeaking) "Stop TTS" else "Read Aloud (TTS)",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSpeaking) FlameOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }

                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("ai_retry_button_${message.id}")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Regenerate Answer",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Explicit RAG Knowledge Sources Retrieved Chips
        if (message.retrievedSources.isNotEmpty() && !isUser && !message.isGenerating) {
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(start = 40.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "RAG",
                        tint = FlameOrange,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RAG Sources Retrieved (${message.retrievedSources.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(message.retrievedSources) { src ->
                        val isClickable = src.exerciseEntity != null || src.workoutPlanEntity != null
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (src.category) {
                                "EXERCISE" -> FlameOrange.copy(alpha = 0.12f)
                                "PLAN" -> ElectricCyan.copy(alpha = 0.12f)
                                "SCHEDULE" -> GoldYellow.copy(alpha = 0.12f)
                                "TRAINER" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when (src.category) {
                                    "EXERCISE" -> FlameOrange.copy(alpha = 0.35f)
                                    "PLAN" -> ElectricCyan.copy(alpha = 0.35f)
                                    "SCHEDULE" -> GoldYellow.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                }
                            ),
                            modifier = if (isClickable) {
                                Modifier.clickable {
                                    src.exerciseEntity?.let { onSelectExercise(it) }
                                    src.workoutPlanEntity?.let { onSelectPlan(it) }
                                }
                            } else Modifier
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when (src.category) {
                                    "EXERCISE" -> Icons.Default.FitnessCenter
                                    "PLAN" -> Icons.Default.PlayCircleFilled
                                    "SCHEDULE" -> Icons.Default.CalendarToday
                                    "TRAINER" -> Icons.Default.Sports
                                    "ANATOMY" -> Icons.Default.AccessibilityNew
                                    "TELEMETRY" -> Icons.Default.LocalFireDepartment
                                    else -> Icons.Default.CheckCircle
                                }
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(11.dp),
                                    tint = when (src.category) {
                                        "EXERCISE" -> FlameOrange
                                        "PLAN" -> ElectricCyan
                                        "SCHEDULE" -> GoldYellow
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = src.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Attached RAG Reference Cards (Exercises)
        if (message.matchedExercises.isNotEmpty() && !message.isGenerating) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(start = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Referenced In-App Exercises:",
                    style = MaterialTheme.typography.labelSmall,
                    color = FlameOrange,
                    fontWeight = FontWeight.Bold
                )

                message.matchedExercises.forEach { ex ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectExercise(ex) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExerciseVisualizer(
                                animationType = ex.animationType,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                isPaused = true
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(ex.targetMuscles, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("View", color = FlameOrange, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Attached RAG Reference Cards (Plans)
        if (message.matchedPlans.isNotEmpty() && !message.isGenerating) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(start = 40.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Referenced Workout Routine:",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )

                message.matchedPlans.forEach { plan ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPlan(plan) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("${plan.estimatedDurationMinutes} mins • ${plan.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Start", color = ElectricCyan, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}
