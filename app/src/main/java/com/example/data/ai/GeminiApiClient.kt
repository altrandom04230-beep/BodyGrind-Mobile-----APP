package com.example.data.ai

import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        const val MODEL_NAME = "gemini-3.8-flash"
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateContent(
        prompt: String,
        systemInstruction: String? = null
    ): Result<String> = executeGeminiRequest(
        prompt = prompt,
        systemInstruction = systemInstruction,
        imageBytes = null,
        mimeType = "image/jpeg",
        forceJsonResponse = false
    )

    suspend fun generateContentWithImage(
        prompt: String,
        imageBytes: ByteArray?,
        mimeType: String = "image/jpeg",
        systemInstruction: String? = null,
        forceJsonResponse: Boolean = false
    ): Result<String> = executeGeminiRequest(
        prompt = prompt,
        systemInstruction = systemInstruction,
        imageBytes = imageBytes,
        mimeType = mimeType,
        forceJsonResponse = forceJsonResponse
    )

    suspend fun analyzeMealNutrition(
        textDescription: String,
        imageBytes: ByteArray? = null,
        mimeType: String = "image/jpeg"
    ): Result<String> {
        val systemInstruction = """
            You are an expert nutritional analyst for BodyGrind fitness app.
            Analyze the provided food item(s), photo, or meal description.
            Return a STRICT JSON response ONLY without markdown backticks or commentary.
            The JSON must follow this exact schema:
            {
              "is_food": true,
              "confidence": "high",
              "unclear_reason": "",
              "items": [
                {
                  "name": "Scrambled Eggs",
                  "portion": "2 large eggs",
                  "calories": 140,
                  "protein_g": 12,
                  "carbs_g": 1,
                  "fat_g": 10
                }
              ],
              "totals": {
                "calories": 140,
                "protein_g": 12,
                "carbs_g": 1,
                "fat_g": 10
              }
            }
            If the input or image is NOT food or is completely unclear, return:
            {
              "is_food": false,
              "confidence": "low",
              "unclear_reason": "No recognizable food items found. Please take a clearer photo or enter a descriptive food text.",
              "items": [],
              "totals": { "calories": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0 }
            }
            Confidence must be one of: "high", "medium", "low".
            All numbers must be estimated realistic non-negative integers.
        """.trimIndent()

        val prompt = buildString {
            if (textDescription.isNotBlank()) {
                append("Meal description: ").append(textDescription.trim())
            }
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Please analyze the attached meal image in detail.")
            }
        }

        return executeGeminiRequest(
            prompt = prompt,
            systemInstruction = systemInstruction,
            imageBytes = imageBytes,
            mimeType = mimeType,
            forceJsonResponse = true
        )
    }

    private suspend fun executeGeminiRequest(
        prompt: String,
        systemInstruction: String?,
        imageBytes: ByteArray?,
        mimeType: String,
        forceJsonResponse: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(
                IllegalStateException("GEMINI_API_KEY is not set. Please configure it in the Secrets panel.")
            )
        }

        try {
            val rootJson = JSONObject()

            // System instruction
            if (!systemInstruction.isNullOrBlank()) {
                val sysPart = JSONObject().put("text", systemInstruction)
                val sysParts = JSONArray().put(sysPart)
                rootJson.put("systemInstruction", JSONObject().put("parts", sysParts))
            }

            // User content
            val userParts = JSONArray()
            if (prompt.isNotBlank()) {
                userParts.put(JSONObject().put("text", prompt))
            }
            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val base64Data = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                val inlineDataObj = JSONObject().apply {
                    put("mimeType", mimeType)
                    put("data", base64Data)
                }
                userParts.put(JSONObject().put("inlineData", inlineDataObj))
            }

            val contentObj = JSONObject().put("parts", userParts)
            val contentsArr = JSONArray().put(contentObj)
            rootJson.put("contents", contentsArr)

            // Generation config
            val configObj = JSONObject().apply {
                put("temperature", if (forceJsonResponse) 0.2 else 0.7)
                put("topP", 0.95)
                put("topK", 40)
                if (forceJsonResponse) {
                    put("responseMimeType", "application/json")
                }
            }
            rootJson.put("generationConfig", configObj)

            val requestBodyString = rootJson.toString()
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"
            val requestBody = requestBodyString.toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                val respJson = JSONObject(responseBody)
                val candidates = respJson.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "")
                        if (text.isNotBlank()) {
                            return@withContext Result.success(text)
                        }
                    }
                }
                return@withContext Result.failure(
                    Exception("No content returned from Gemini model $MODEL_NAME")
                )
            } else {
                val code = response.code
                return@withContext if (code == 404) {
                    Result.failure(Exception("Model $MODEL_NAME returned HTTP 404"))
                } else {
                    Result.failure(
                        Exception("Gemini API error (HTTP $code): ${responseBody ?: "empty response"}")
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
