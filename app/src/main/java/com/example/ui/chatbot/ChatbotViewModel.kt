package com.example.ui.chatbot

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.FitnessRagEngine
import com.example.data.ai.GeminiApiClient
import com.example.data.ai.RagSourceItem
import com.example.data.local.entity.ExerciseEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.data.local.entity.WorkoutScheduleEntity
import com.example.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val matchedExercises: List<ExerciseEntity> = emptyList(),
    val matchedPlans: List<WorkoutPlanEntity> = emptyList(),
    val retrievedSources: List<RagSourceItem> = emptyList(),
    val isGenerating: Boolean = false
)

class ChatbotViewModel(application: Application) : AndroidViewModel(application) {

    private val geminiClient = GeminiApiClient()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    init {
        // Welcome message with no asterisks and initial RAG badge
        _messages.value = listOf(
            ChatMessage(
                text = "👋 Hello! I am your AI Fitness Coach powered by Gemini and our in-app RAG dataset. Ask me about proper exercise form, custom workout routines, posture corrections, your workout schedule, or tips from Coach Cobie and Coach Jun!\n\nFollow-up: What fitness goal or target muscle would you like to focus on today?\n\nDisclaimer: Always listen to your body and train safely.",
                isUser = false,
                retrievedSources = listOf(
                    RagSourceItem(title = "RAG Engine", category = "AI", detail = "Connected to Live Database"),
                    RagSourceItem(title = "25 Muscle Groups", category = "ANATOMY", detail = "Target Biomechanics"),
                    RagSourceItem(title = "Gym Trainers", category = "TRAINER", detail = "Coach Cobie & Coach Jun")
                )
            )
        )
    }

    private fun sanitizeText(raw: String): String {
        return raw
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1") // Remove bold asterisks
            .replace(Regex("\\*(.*?)\\*"), "$1")       // Remove italic asterisks
            .replace(Regex("(?m)^\\s*\\*\\s+"), "• ") // Convert list asterisks to bullets
            .replace("*", "")                          // Strip any lone asterisks
            .trim()
    }

    fun sendMessage(
        query: String,
        userProfile: UserProfileEntity?,
        allExercises: List<ExerciseEntity>,
        allPlans: List<WorkoutPlanEntity>,
        allSchedules: List<WorkoutScheduleEntity> = emptyList(),
        recentSessions: List<WorkoutSessionEntity> = emptyList(),
        streakDays: Int = 0
    ) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || _isGenerating.value) return

        val userMessage = ChatMessage(text = trimmed, isUser = true)
        val botPlaceholderId = UUID.randomUUID().toString()
        val botPlaceholder = ChatMessage(
            id = botPlaceholderId,
            text = "Retrieving in-app database & reasoning...",
            isUser = false,
            isGenerating = true
        )

        _messages.value = _messages.value + userMessage + botPlaceholder
        _isGenerating.value = true

        viewModelScope.launch {
            // Check fitness intent first
            val intentCheck = com.example.data.ai.FitnessIntentClassifier.classify(trimmed)
            if (!intentCheck.isFitnessRelated) {
                val refusal = intentCheck.refusalMessage ?: "I am your AI Fitness Coach. I can only assist with gym workouts, exercises, training techniques, and fitness nutrition."
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == botPlaceholderId) {
                        msg.copy(
                            text = refusal,
                            isGenerating = false
                        )
                    } else {
                        msg
                    }
                }
                _isGenerating.value = false
                return@launch
            }

            // 1. Multi-Source RAG Retrieval
            val ragResult = FitnessRagEngine.retrieve(
                query = trimmed,
                userProfile = userProfile,
                allExercises = allExercises,
                allPlans = allPlans,
                allSchedules = allSchedules,
                recentSessions = recentSessions,
                streakDays = streakDays
            )

            // 2. System Instruction + Augmented Prompt (Gym / Fitness Only)
            val systemInstruction = """
                You are the dedicated, encouraging AI Fitness Coach for the BodyGrind Android application.
                CRITICAL SCOPE & DOMAIN RESTRICTIONS:
                - You are exclusively a gym and fitness coach. Only answer questions related to workouts, calisthenics, gym training, exercise biomechanics, posture, workout schedules, and fitness-related nutrition or hydration.
                - If the question is off-topic (e.g. digestive foods, curing diseases, medication, politics, programming), respond with a polite, brief refusal redirecting them to exercise routines, workout form, or fitness nutrition.
                
                CRITICAL FORMATTING RULES:
                - Do NOT use asterisks (*) or markdown bold (**) in your response text. Output clean plain text only.
                - Use bullet points (•) for lists instead of asterisks.
                
                CONTENT REQUIREMENTS:
                1. Provide safe, motivating, biomechanically sound advice for home workouts with zero equipment.
                2. Incorporate the user's current fitness profile and stats provided in the context.
                3. Always ask 1-2 thoughtful follow-up questions at the end to better tailor recommendations to the user's fitness journey.
                4. Always conclude with a brief health disclaimer: 'Disclaimer: Consult a physician before beginning any new exercise regimen.'
            """.trimIndent()

            val augmentedPrompt = """
                ${ragResult.augmentedContext}

                --- USER QUESTION ---
                $trimmed
            """.trimIndent()

            // 3. Generate via Gemini or RAG Fallback
            val result = geminiClient.generateContent(
                prompt = augmentedPrompt,
                systemInstruction = systemInstruction
            )

            val rawAnswer = result.getOrElse {
                ragResult.directLocalAnswer
            }

            // Remove any asterisks
            val sanitizedAnswer = sanitizeText(rawAnswer)

            // Update messages list replacing placeholder
            _messages.value = _messages.value.map { msg ->
                if (msg.id == botPlaceholderId) {
                    msg.copy(
                        text = sanitizedAnswer,
                        matchedExercises = ragResult.matchedExercises,
                        matchedPlans = ragResult.matchedPlans,
                        retrievedSources = ragResult.retrievedSources,
                        isGenerating = false
                    )
                } else {
                    msg
                }
            }
            _isGenerating.value = false
        }
    }

    fun retryMessage(
        botMessageId: String,
        userProfile: UserProfileEntity?,
        allExercises: List<ExerciseEntity>,
        allPlans: List<WorkoutPlanEntity>,
        allSchedules: List<WorkoutScheduleEntity> = emptyList(),
        recentSessions: List<WorkoutSessionEntity> = emptyList(),
        streakDays: Int = 0
    ) {
        if (_isGenerating.value) return

        val currentList = _messages.value
        val botIndex = currentList.indexOfFirst { it.id == botMessageId }
        if (botIndex == -1) return

        // Find the user query preceding this bot message
        var queryToRetry = ""
        for (i in (botIndex - 1) downTo 0) {
            if (currentList[i].isUser) {
                queryToRetry = currentList[i].text
                break
            }
        }
        if (queryToRetry.isBlank()) {
            queryToRetry = "Can you provide general workout tips and advice for beginners?"
        }

        // Set bot message to thinking
        _messages.value = currentList.map { msg ->
            if (msg.id == botMessageId) {
                msg.copy(text = "Retrieving in-app database & reasoning...", isGenerating = true)
            } else {
                msg
            }
        }
        _isGenerating.value = true

        viewModelScope.launch {
            val intentCheck = com.example.data.ai.FitnessIntentClassifier.classify(queryToRetry)
            if (!intentCheck.isFitnessRelated) {
                val refusal = intentCheck.refusalMessage ?: "I am your AI Fitness Coach. I can only assist with gym workouts, exercises, training techniques, and fitness nutrition."
                _messages.value = _messages.value.map { msg ->
                    if (msg.id == botMessageId) {
                        msg.copy(text = refusal, isGenerating = false)
                    } else {
                        msg
                    }
                }
                _isGenerating.value = false
                return@launch
            }

            val ragResult = FitnessRagEngine.retrieve(
                query = queryToRetry,
                userProfile = userProfile,
                allExercises = allExercises,
                allPlans = allPlans,
                allSchedules = allSchedules,
                recentSessions = recentSessions,
                streakDays = streakDays
            )

            val systemInstruction = """
                You are the dedicated, encouraging AI Fitness Coach for the BodyGrind Android application.
                CRITICAL SCOPE & DOMAIN RESTRICTIONS:
                - You are exclusively a gym and fitness coach. Only answer questions related to workouts, calisthenics, gym training, exercise biomechanics, posture, workout schedules, and fitness-related nutrition or hydration.
                - If the question is off-topic (e.g. digestive foods, curing diseases, medication, politics, programming), respond with a polite, brief refusal redirecting them to exercise routines, workout form, or fitness nutrition.
                
                CRITICAL FORMATTING RULES:
                - Do NOT use asterisks (*) or markdown bold (**) in your response text. Output clean plain text only.
                - Use bullet points (•) for lists instead of asterisks.
                
                CONTENT REQUIREMENTS:
                1. Provide safe, motivating, biomechanically sound advice for home workouts with zero equipment.
                2. Incorporate the user's current fitness profile and stats provided in the context.
                3. Always ask 1-2 thoughtful follow-up questions at the end to better tailor recommendations to the user's fitness journey.
                4. Always conclude with a brief health disclaimer: 'Disclaimer: Consult a physician before beginning any new exercise regimen.'
            """.trimIndent()

            val augmentedPrompt = """
                ${ragResult.augmentedContext}

                --- USER QUESTION ---
                $queryToRetry
            """.trimIndent()

            val result = geminiClient.generateContent(
                prompt = augmentedPrompt,
                systemInstruction = systemInstruction
            )

            val rawAnswer = result.getOrElse {
                ragResult.directLocalAnswer
            }

            val sanitizedAnswer = sanitizeText(rawAnswer)

            _messages.value = _messages.value.map { msg ->
                if (msg.id == botMessageId) {
                    msg.copy(
                        text = sanitizedAnswer,
                        matchedExercises = ragResult.matchedExercises,
                        matchedPlans = ragResult.matchedPlans,
                        retrievedSources = ragResult.retrievedSources,
                        isGenerating = false
                    )
                } else {
                    msg
                }
            }
            _isGenerating.value = false
        }
    }

    fun clearChat() {
        _messages.value = listOf(
            ChatMessage(
                text = "Chat history cleared. How can I help your fitness journey today?\n\nFollow-up: What workout would you like to tackle?\n\nDisclaimer: Exercise within your personal limits.",
                isUser = false
            )
        )
    }
}
