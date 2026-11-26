import 'dart:typed_data';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../../data/models/meal_models.dart';
import '../../../../../data/providers/app_providers.dart';
import '../models/ai_calorie_models.dart';
import '../services/ai_calorie_service.dart';

class AiCalorieTrackerState {
  final AiCalorieTrackerStatus status;
  final String? errorMessage;
  final AiCalorieResult? activeResult;
  final List<AiDetectedFoodItem> editableItems;
  final MealSlot selectedSlot;
  final String? lastImagePreviewPath;
  final String? lastTextPrompt;

  const AiCalorieTrackerState({
    this.status = AiCalorieTrackerStatus.idle,
    this.errorMessage,
    this.activeResult,
    this.editableItems = const [],
    this.selectedSlot = MealSlot.lunch,
    this.lastImagePreviewPath,
    this.lastTextPrompt,
  });

  int get totalCalories => editableItems.fold(0, (sum, item) => sum + item.calories);
  int get totalProteinG => editableItems.fold(0, (sum, item) => sum + item.proteinG);
  int get totalCarbsG => editableItems.fold(0, (sum, item) => sum + item.carbsG);
  int get totalFatG => editableItems.fold(0, (sum, item) => sum + item.fatG);

  AiCalorieTrackerState copyWith({
    AiCalorieTrackerStatus? status,
    String? errorMessage,
    AiCalorieResult? activeResult,
    List<AiDetectedFoodItem>? editableItems,
    MealSlot? selectedSlot,
    String? lastImagePreviewPath,
    String? lastTextPrompt,
  }) {
    return AiCalorieTrackerState(
      status: status ?? this.status,
      errorMessage: errorMessage,
      activeResult: activeResult ?? this.activeResult,
      editableItems: editableItems ?? this.editableItems,
      selectedSlot: selectedSlot ?? this.selectedSlot,
      lastImagePreviewPath: lastImagePreviewPath ?? this.lastImagePreviewPath,
      lastTextPrompt: lastTextPrompt ?? this.lastTextPrompt,
    );
  }
}

class AiCalorieTrackerNotifier extends StateNotifier<AiCalorieTrackerState> {
  final Ref _ref;
  final AiCalorieService _aiService;

  AiCalorieTrackerNotifier(this._ref, this._aiService)
      : super(const AiCalorieTrackerState());

  /// Pick current meal slot based on time of day
  MealSlot _getSlotForCurrentTime() {
    final hour = DateTime.now().hour;
    if (hour >= 5 && hour < 10) return MealSlot.breakfast;
    if (hour >= 10 && hour < 12) return MealSlot.morningSnack;
    if (hour >= 12 && hour < 15) return MealSlot.lunch;
    if (hour >= 15 && hour < 18) return MealSlot.afternoonSnack;
    return MealSlot.dinner;
  }

  /// Analyze a plain text meal description
  Future<void> analyzeText(String description) async {
    state = state.copyWith(
      status: AiCalorieTrackerStatus.analyzing,
      errorMessage: null,
      lastTextPrompt: description,
      lastImagePreviewPath: null,
    );

    try {
      final result = await _aiService.analyzeText(description);
      state = state.copyWith(
        status: AiCalorieTrackerStatus.reviewing,
        activeResult: result,
        editableItems: List.of(result.items),
        selectedSlot: result.suggestedSlot != MealSlot.lunch
            ? result.suggestedSlot
            : _getSlotForCurrentTime(),
      );
    } catch (e) {
      state = state.copyWith(
        status: AiCalorieTrackerStatus.error,
        errorMessage: e.toString().replaceAll('Exception:', '').trim(),
      );
    }
  }

  /// Analyze an image captured from camera or chosen from gallery
  Future<void> analyzeImage(Uint8List imageBytes, {String? imagePath}) async {
    state = state.copyWith(
      status: AiCalorieTrackerStatus.analyzing,
      errorMessage: null,
      lastImagePreviewPath: imagePath,
      lastTextPrompt: null,
    );

    try {
      final result = await _aiService.analyzePhoto(imageBytes);
      state = state.copyWith(
        status: AiCalorieTrackerStatus.reviewing,
        activeResult: result,
        editableItems: List.of(result.items),
        selectedSlot: result.suggestedSlot != MealSlot.lunch
            ? result.suggestedSlot
            : _getSlotForCurrentTime(),
      );
    } catch (e) {
      state = state.copyWith(
        status: AiCalorieTrackerStatus.error,
        errorMessage: e.toString().replaceAll('Exception:', '').trim(),
      );
    }
  }

  void updateItem(int index, AiDetectedFoodItem updated) {
    if (index >= 0 && index < state.editableItems.length) {
      final updatedList = List<AiDetectedFoodItem>.from(state.editableItems);
      updatedList[index] = updated;
      state = state.copyWith(editableItems: updatedList);
    }
  }

  void removeItem(int index) {
    if (index >= 0 && index < state.editableItems.length) {
      final updatedList = List<AiDetectedFoodItem>.from(state.editableItems)..removeAt(index);
      state = state.copyWith(editableItems: updatedList);
    }
  }

  void addItem(AiDetectedFoodItem item) {
    state = state.copyWith(
      editableItems: [...state.editableItems, item],
    );
  }

  void setSlot(MealSlot slot) {
    state = state.copyWith(selectedSlot: slot);
  }

  /// Confirms review, creates a LoggedMeal, and persists to repository
  Future<void> confirmAndSave() async {
    if (state.editableItems.isEmpty) {
      state = state.copyWith(
        status: AiCalorieTrackerStatus.error,
        errorMessage: 'Please include at least one food item.',
      );
      return;
    }

    state = state.copyWith(status: AiCalorieTrackerStatus.saving);

    final items = state.editableItems;
    final title = items.map((i) => i.name).take(3).join(', ') +
        (items.length > 3 ? ' (+${items.length - 3} more)' : '');
    final portions = items.map((i) => '${i.name} (${i.portion})').join('; ');

    final newMeal = LoggedMeal(
      id: 'ai_meal_${DateTime.now().millisecondsSinceEpoch}',
      name: title,
      slot: state.selectedSlot,
      calories: state.totalCalories,
      proteinG: state.totalProteinG,
      carbsG: state.totalCarbsG,
      fatsG: state.totalFatG,
      timestamp: DateTime.now().millisecondsSinceEpoch,
      portion: portions,
      dietaryTags: const ['AI estimate'],
    );

    try {
      await _ref.read(smartMealsRepositoryProvider).logMeal(newMeal);
      state = const AiCalorieTrackerState(status: AiCalorieTrackerStatus.idle);
    } catch (e) {
      state = state.copyWith(
        status: AiCalorieTrackerStatus.error,
        errorMessage: 'Failed to save meal: $e',
      );
    }
  }

  void cancel() {
    state = const AiCalorieTrackerState(status: AiCalorieTrackerStatus.idle);
  }

  void clearError() {
    state = state.copyWith(
      status: AiCalorieTrackerStatus.idle,
      errorMessage: null,
    );
  }
}

final aiCalorieServiceProvider = Provider<AiCalorieService>((ref) {
  return AiCalorieService();
});

final aiCalorieTrackerProvider =
    StateNotifierProvider<AiCalorieTrackerNotifier, AiCalorieTrackerState>((ref) {
  final service = ref.watch(aiCalorieServiceProvider);
  return AiCalorieTrackerNotifier(ref, service);
});
