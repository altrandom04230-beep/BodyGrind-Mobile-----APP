package com.example.data.ai

object FitnessIntentClassifier {

    private val fitnessKeywords = listOf(
        "workout", "exercise", "training", "gym", "calisthenic", "routine", "reps", "sets",
        "muscle", "chest", "abs", "core", "arm", "bicep", "tricep", "leg", "quad", "hamstring",
        "glute", "shoulder", "back", "lat", "squat", "pushup", "push-up", "pullup", "pull-up",
        "plank", "lunge", "crunch", "jumping jack", "cardio", "hiit", "warmup", "warm up",
        "stretch", "stretching", "form", "posture", "injury", "sore", "soreness", "doms",
        "recovery", "protein", "calorie", "deficit", "macros", "creatine", "hydration",
        "water", "bulk", "cut", "fat burn", "hypertrophy", "progressive overload", "fitness",
        "bodyweight", "weight loss", "gain weight", "endurance", "flexibility", "stamina",
        "bmi", "coach", "cobie", "jun", "schedule", "streak"
    )

    private val offTopicKeywords = listOf(
        // Digestive remedies & general non-fitness gastrointestinal queries
        "digestion", "digestive", "digest", "stomach ache", "constipation", "diarrhea",
        "bloating", "acid reflux", "heartburn", "indigestion", "nausea",
        // General medicine, disease, medications
        "headache", "fever", "cough", "cold", "flu", "infection", "antibiotic",
        "diabetes cure", "cancer", "blood pressure cure", "prescribe", "diagnosis",
        // Technology & programming
        "python", "kotlin code", "javascript", "html", "css", "programming", "bug fix",
        "algorithm", "database query", "sql", "git",
        // Politics & general knowledge
        "president", "election", "politics", "war", "movie", "celebrity", "crypto",
        "stock market", "weather forecast", "dating advice", "recipe for cake"
    )

    data class ClassificationResult(
        val isFitnessRelated: Boolean,
        val matchedKeyword: String? = null,
        val refusalMessage: String? = null
    )

    fun classify(query: String): ClassificationResult {
        val normalized = query.lowercase().trim()

        // 1. Explicit off-topic check (takes precedence for medical/digestion/tech queries)
        for (offKeyword in offTopicKeywords) {
            if (normalized.contains(offKeyword)) {
                return ClassificationResult(
                    isFitnessRelated = false,
                    matchedKeyword = offKeyword,
                    refusalMessage = "I am your dedicated AI Fitness Coach, so I can only help with workouts, bodyweight exercises, training technique, and fitness-related nutrition or hydration. For digestion issues, medical treatments, or non-fitness topics, please consult a healthcare professional."
                )
            }
        }

        // 2. Positive fitness topic check
        val hasFitnessKeyword = fitnessKeywords.any { normalized.contains(it) }
        if (hasFitnessKeyword) {
            return ClassificationResult(isFitnessRelated = true)
        }

        // 3. Fallback: If very short query or no fitness concepts detected
        // E.g. "what is the capital of France?", "tell me a joke"
        val generalOffTopicGreetings = listOf("hi", "hello", "hey", "help", "who are you", "what can you do")
        if (generalOffTopicGreetings.any { normalized == it || normalized.startsWith("$it ") }) {
            return ClassificationResult(isFitnessRelated = true) // Allow standard fitness assistant greeting
        }

        // Otherwise, if completely off-topic with no fitness nexus:
        return ClassificationResult(
            isFitnessRelated = false,
            refusalMessage = "I am your dedicated AI Fitness Coach, so I can only help with workouts, exercises, training routines, and fitness nutrition. Feel free to ask me for exercise advice, form tips, or a custom home workout!"
        )
    }
}
