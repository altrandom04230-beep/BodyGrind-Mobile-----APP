import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:google_generative_ai/google_generative_ai.dart';
import '../../../../core/constants/app_strings.dart';
import '../models/ai_calorie_models.dart';

class AiCalorieService {
  final String? _apiKey;

  AiCalorieService({String? apiKey}) : _apiKey = apiKey;

  static const String _systemInstruction = '''
You are an expert nutritional AI for the BodyGrind fitness app.
Analyze the provided food photo or meal text description.
Identify every distinct food item, estimate realistic portions, calories (kcal), protein (g), carbs (g), and fat (g).
Output STRICT RAW JSON ONLY. Do not wrap in markdown quotes or backticks (do not output ```json or ```).
Do not include any conversational text, greeting, or explanation.
If the input does not appear to be food or confidence is too low to estimate, output:
{"is_food": false, "confidence": "low", "error_message": "Could not identify clear food items. Please provide more detail or retake the photo."}

JSON Schema:
{
  "is_food": true,
  "confidence": "high",
  "items": [
    {
      "name": "Food Name",
      "portion": "Estimated Portion",
      "calories": 150,
      "protein_g": 10,
      "carbs_g": 20,
      "fat_g": 5
    }
  ],
  "total_calories": 150,
  "total_protein_g": 10,
  "total_carbs_g": 20,
  "total_fat_g": 5,
  "suggested_slot": "breakfast"
}
''';

  Future<String> _resolveKey() async {
    if (_apiKey != null && _apiKey.isNotEmpty && _apiKey != 'MY_GEMINI_API_KEY') {
      return _apiKey;
    }
    const envKey = String.fromEnvironment('GEMINI_API_KEY');
    if (envKey.isNotEmpty && envKey != 'MY_GEMINI_API_KEY') {
      return envKey;
    }
    try {
      final envFile = File('.env');
      if (await envFile.exists()) {
        final lines = await envFile.readAsLines();
        for (final line in lines) {
          final trimmed = line.trim();
          if (trimmed.startsWith('GEMINI_API_KEY=')) {
            final val = trimmed.substring('GEMINI_API_KEY='.length).trim();
            if (val.isNotEmpty && val != 'MY_GEMINI_API_KEY') {
              return val;
            }
          }
        }
      }
    } catch (_) {}
    throw Exception('GEMINI_API_KEY is not configured in environment or .env');
  }

  /// Analyze food from a plain text description (e.g. "2 eggs and a slice of toast")
  Future<AiCalorieResult> analyzeText(String mealDescription) async {
    final trimmed = mealDescription.trim();
    if (trimmed.isEmpty) {
      throw Exception('Please enter a meal description to analyze.');
    }

    final key = await _resolveKey();
    final model = GenerativeModel(
      model: AppStrings.geminiModel,
      apiKey: key,
      systemInstruction: Content.system(_systemInstruction),
      generationConfig: GenerationConfig(
        temperature: 0.2,
        topP: 0.95,
        topK: 40,
        responseMimeType: 'application/json',
      ),
    );

    final prompt = 'Analyze this meal and return strict JSON for nutrition: $trimmed';
    return _generateWithRetry(model, [Content.text(prompt)]);
  }

  /// Analyze food from photo bytes (captured via camera or gallery)
  Future<AiCalorieResult> analyzePhoto(Uint8List imageBytes, {String mimeType = 'image/jpeg'}) async {
    if (imageBytes.isEmpty) {
      throw Exception('Photo data is empty. Please retake the photo.');
    }

    final key = await _resolveKey();
    final model = GenerativeModel(
      model: AppStrings.geminiModel,
      apiKey: key,
      systemInstruction: Content.system(_systemInstruction),
      generationConfig: GenerationConfig(
        temperature: 0.2,
        topP: 0.95,
        topK: 40,
        responseMimeType: 'application/json',
      ),
    );

    final content = Content.multi([
      TextPart('Analyze all food items visible in this photo and return strict JSON for estimated portions, calories, and macros:'),
      DataPart(mimeType, imageBytes),
    ]);

    return _generateWithRetry(model, [content]);
  }

  /// Generates content and parses JSON safely with 1-time retry on parse failure
  Future<AiCalorieResult> _generateWithRetry(
    GenerativeModel model,
    List<Content> initialContent,
  ) async {
    try {
      final response = await model.generateContent(initialContent);
      final text = response.text;
      if (text == null || text.trim().isEmpty) {
        throw const FormatException('Empty response received from AI.');
      }
      return _parseResult(text);
    } catch (firstError) {
      // Execute 1 retry with a corrective prompt if it was a formatting/JSON error
      try {
        final corrective = Content.text(
          'CRITICAL: Your previous response was invalid. Return ONLY strict, raw JSON conforming to: {"is_food": true, "confidence": "high", "items": [{"name": "item", "portion": "1 serving", "calories": 100, "protein_g": 5, "carbs_g": 10, "fat_g": 2}], "total_calories": 100, "total_protein_g": 5, "total_carbs_g": 10, "total_fat_g": 2, "suggested_slot": "lunch"}. No markdown, no comments.',
        );
        final retryResponse = await model.generateContent([...initialContent, corrective]);
        final retryText = retryResponse.text;
        if (retryText != null && retryText.trim().isNotEmpty) {
          return _parseResult(retryText);
        }
      } catch (_) {}

      // Re-throw meaningful user-facing message
      if (firstError is FormatException) {
        throw Exception('AI returned invalid food data format. Please try again with more detail.');
      }
      throw Exception('AI analysis failed: ${firstError.toString().replaceAll("Exception:", "").trim()}');
    }
  }

  AiCalorieResult _parseResult(String rawJson) {
    final cleaned = _sanitizeJsonString(rawJson);
    final Map<String, dynamic> decoded = jsonDecode(cleaned);
    final result = AiCalorieResult.fromJson(decoded);

    if (!result.isFood || result.confidence == 'low') {
      if (result.items.isEmpty) {
        throw Exception(result.errorMessage ?? 'Low confidence in food detection. Please retake photo or add more detail.');
      }
    }

    return result;
  }

  String _sanitizeJsonString(String raw) {
    var cleaned = raw.trim();
    if (cleaned.startsWith('```json')) {
      cleaned = cleaned.substring(7);
    } else if (cleaned.startsWith('```')) {
      cleaned = cleaned.substring(3);
    }
    if (cleaned.endsWith('```')) {
      cleaned = cleaned.substring(0, cleaned.length - 3);
    }
    return cleaned.trim();
  }
}
