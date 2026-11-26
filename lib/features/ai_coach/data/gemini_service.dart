import 'package:google_generative_ai/google_generative_ai.dart';
import '../../../core/constants/app_strings.dart';

class GeminiService {
  final String? apiKey;

  GeminiService({this.apiKey});

  Future<String> generateContent({
    required String prompt,
    String? systemInstruction,
  }) async {
    final key = apiKey?.trim();
    if (key == null || key.isEmpty || key == 'MY_GEMINI_API_KEY') {
      throw Exception('GEMINI_API_KEY is not configured.');
    }

    try {
      final model = GenerativeModel(
        model: AppStrings.geminiModel,
        apiKey: key,
        systemInstruction: systemInstruction != null
            ? Content.system(systemInstruction)
            : null,
        generationConfig: GenerationConfig(
          temperature: 0.7,
          topP: 0.95,
          topK: 40,
        ),
      );

      final response = await model.generateContent([Content.text(prompt)]);
      final text = response.text;
      if (text != null && text.isNotEmpty) {
        return _sanitize(text);
      }
      throw Exception('Empty response from model');
    } catch (e) {
      rethrow;
    }
  }

  String _sanitize(String raw) {
    return raw
        .replaceAll(RegExp(r'\*\*(.*?)\*\*'), r'$1')
        .replaceAll(RegExp(r'\*(.*?)\*'), r'$1')
        .replaceAll(RegExp(r'(?m)^\s*\*\s+'), '• ')
        .replaceAll('*', '')
        .trim();
  }
}
