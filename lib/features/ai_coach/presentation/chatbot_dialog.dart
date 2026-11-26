import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/exercise.dart';
import '../../../data/models/rag_models.dart';
import '../../../data/models/workout_plan.dart';
import '../../../data/providers/app_providers.dart';
import '../../member/presentation/exercise/exercise_detail_sheet.dart';
import '../data/fitness_intent_classifier.dart';
import '../data/fitness_rag_engine.dart';
import '../data/gemini_service.dart';

class ChatMessageItem {
  final String id;
  final String text;
  final bool isUser;
  final List<RagSourceItem> retrievedSources;
  final List<Exercise> matchedExercises;
  final List<WorkoutPlan> matchedPlans;
  final bool isGenerating;

  const ChatMessageItem({
    required this.id,
    required this.text,
    required this.isUser,
    this.retrievedSources = const [],
    this.matchedExercises = const [],
    this.matchedPlans = const [],
    this.isGenerating = false,
  });
}

class ChatbotDialog extends ConsumerStatefulWidget {
  const ChatbotDialog({super.key});

  @override
  ConsumerState<ChatbotDialog> createState() => _ChatbotDialogState();
}

class _ChatbotDialogState extends ConsumerState<ChatbotDialog> {
  final TextEditingController _inputController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final List<ChatMessageItem> _messages = [];
  bool _isGenerating = false;

  final List<String> _suggestionChips = [
    "What's my workout schedule this week?",
    "Recommend a workout for Chest & Triceps",
    "Tell me about Coach Cobie and Coach Jun",
    "What's my streak and workout level?",
    "How can I relieve sore muscles?",
  ];

  @override
  void initState() {
    super.initState();
    _messages.add(
      const ChatMessageItem(
        id: 'welcome_1',
        text:
            '👋 Hello! I am your AI Fitness Coach powered by Gemini and our in-app RAG dataset. Ask me about proper exercise form, custom workout routines, posture corrections, your workout schedule, or tips from Coach Cobie and Coach Jun!\n\nFollow-up: What fitness goal or target muscle would you like to focus on today?\n\nDisclaimer: Always listen to your body and train safely.',
        isUser: false,
        retrievedSources: [
          RagSourceItem(title: 'RAG Engine', category: 'AI', detail: 'Connected to In-App DB'),
          RagSourceItem(title: '25 Muscle Groups', category: 'ANATOMY', detail: 'Target Biomechanics'),
          RagSourceItem(title: 'Gym Trainers', category: 'TRAINER', detail: 'Coach Cobie & Jun'),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _inputController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  Future<void> _handleSendMessage(String query) async {
    final text = query.trim();
    if (text.isEmpty || _isGenerating) return;

    _inputController.clear();
    final now = DateTime.now().millisecondsSinceEpoch;

    final userMsg = ChatMessageItem(
      id: 'user_$now',
      text: text,
      isUser: true,
    );

    final botPlaceholderId = 'bot_$now';
    final botPlaceholder = ChatMessageItem(
      id: botPlaceholderId,
      text: 'Retrieving in-app database & reasoning...',
      isUser: false,
      isGenerating: true,
    );

    setState(() {
      _messages.add(userMsg);
      _messages.add(botPlaceholder);
      _isGenerating = true;
    });
    _scrollToBottom();

    // 1. Check fitness intent
    final intentCheck = FitnessIntentClassifier.classify(text);
    if (!intentCheck.isFitnessRelated) {
      final refusal = intentCheck.refusalMessage ??
          'I am your AI Fitness Coach. I can only assist with gym workouts, exercises, training techniques, and fitness nutrition.';
      setState(() {
        final idx = _messages.indexWhere((m) => m.id == botPlaceholderId);
        if (idx != -1) {
          _messages[idx] = ChatMessageItem(
            id: botPlaceholderId,
            text: refusal,
            isUser: false,
            isGenerating: false,
          );
        }
        _isGenerating = false;
      });
      _scrollToBottom();
      return;
    }

    // 2. Multi-source RAG Retrieval
    final profile = ref.read(userProfileStreamProvider).valueOrNull ??
        ref.read(workoutRepositoryProvider).currentProfile;
    final allExercises = ref.read(exercisesStreamProvider).valueOrNull ?? [];
    final allPlans = ref.read(plansStreamProvider).valueOrNull ?? [];
    final allSchedules = ref.read(schedulesStreamProvider).valueOrNull ?? [];
    final recentSessions = ref.read(completedSessionsStreamProvider).valueOrNull ?? [];
    final streakDays = ref.read(streakDaysProvider);

    final ragResult = FitnessRagEngine.retrieve(
      query: text,
      userProfile: profile,
      allExercises: allExercises,
      allPlans: allPlans,
      allSchedules: allSchedules,
      recentSessions: recentSessions,
      streakDays: streakDays,
    );

    // 3. Generate response via GeminiService or fallback
    String finalAnswer = ragResult.directLocalAnswer;
    try {
      final geminiService = GeminiService();
      final systemInstruction = '''
You are the dedicated AI Fitness Coach for the BodyGrind application.
CRITICAL SCOPE & DOMAIN RESTRICTIONS:
- You are exclusively a gym and fitness coach. Only answer questions related to workouts, exercises, biomechanics, schedules, and fitness nutrition.
- If off-topic, refuse politely.
- Do NOT use asterisks (*) or markdown bold (**). Output clean plain text only.
- Use bullet points (•) for lists.
- Always conclude with: 'Disclaimer: Consult a physician before beginning any new exercise regimen.'
''';
      final prompt = '''
${ragResult.augmentedContext}

--- USER QUESTION ---
$text
''';
      final generated = await geminiService.generateContent(
        prompt: prompt,
        systemInstruction: systemInstruction,
      );
      finalAnswer = generated;
    } catch (_) {
      // Fallback to local RAG synthesized answer
      finalAnswer = ragResult.directLocalAnswer;
    }

    setState(() {
      final idx = _messages.indexWhere((m) => m.id == botPlaceholderId);
      if (idx != -1) {
        _messages[idx] = ChatMessageItem(
          id: botPlaceholderId,
          text: finalAnswer,
          isUser: false,
          isGenerating: false,
          retrievedSources: ragResult.retrievedSources,
          matchedExercises: ragResult.matchedExercises,
          matchedPlans: ragResult.matchedPlans,
        );
      }
      _isGenerating = false;
    });
    _scrollToBottom();
  }

  void _showExerciseDetail(Exercise exercise) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => ExerciseDetailSheet(
        exercise: exercise,
        onDismiss: () => Navigator.pop(ctx),
        onToggleFavorite: (ex) {
          ref.read(workoutRepositoryProvider).toggleExerciseFavorite(ex.id, !ex.isFavorite);
          Navigator.pop(ctx);
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Dialog.fullscreen(
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => Navigator.pop(context),
          ),
          title: Row(
            children: [
              CircleAvatar(
                radius: 16,
                backgroundColor: AppColors.flameOrange,
                child: const Icon(Icons.auto_awesome, color: Colors.white, size: 18),
              ),
              const SizedBox(width: 10),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('AI Fitness Coach', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  Row(
                    children: [
                      Container(
                        width: 6,
                        height: 6,
                        decoration: const BoxDecoration(
                          color: AppColors.successGreen,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        'RAG Grounded • Gemini',
                        style: theme.textTheme.labelSmall?.copyWith(fontSize: 10),
                      ),
                    ],
                  ),
                ],
              ),
            ],
          ),
          actions: [
            IconButton(
              icon: const Icon(Icons.delete_sweep_outlined),
              tooltip: 'Clear Chat',
              onPressed: () {
                setState(() => _messages.clear());
              },
            ),
          ],
        ),
        body: Column(
          children: [
            // Suggestion chips row
            Container(
              height: 44,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: ListView.separated(
                scrollDirection: Axis.horizontal,
                itemCount: _suggestionChips.length,
                separatorBuilder: (_, __) => const SizedBox(width: 6),
                itemBuilder: (context, idx) {
                  final chip = _suggestionChips[idx];
                  return ActionChip(
                    label: Text(chip, style: const TextStyle(fontSize: 11)),
                    padding: EdgeInsets.zero,
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    onPressed: () => _handleSendMessage(chip),
                  );
                },
              ),
            ),
            const Divider(height: 1),

            // Messages ListView
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.all(16),
                itemCount: _messages.length,
                itemBuilder: (context, index) {
                  final message = _messages[index];
                  return _buildMessageBubble(theme, message);
                },
              ),
            ),

            // Bottom Input Bar
            Container(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.05),
                    blurRadius: 6,
                    offset: const Offset(0, -2),
                  ),
                ],
              ),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _inputController,
                      decoration: InputDecoration(
                        hintText: 'Ask about form, routines, plans...',
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(24)),
                        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                        isDense: true,
                      ),
                      onSubmitted: _handleSendMessage,
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    style: IconButton.styleFrom(backgroundColor: AppColors.flameOrange),
                    icon: _isGenerating
                        ? const SizedBox(
                            width: 18,
                            height: 18,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Icon(Icons.send, size: 18),
                    onPressed: _isGenerating
                        ? null
                        : () => _handleSendMessage(_inputController.text),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageBubble(ThemeData theme, ChatMessageItem message) {
    if (message.isUser) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Flexible(
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                decoration: BoxDecoration(
                  color: AppColors.flameOrange,
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(16),
                    topRight: Radius.circular(4),
                    bottomLeft: Radius.circular(16),
                    bottomRight: Radius.circular(16),
                  ),
                ),
                child: Text(
                  message.text,
                  style: const TextStyle(color: Colors.white, fontSize: 14),
                ),
              ),
            ),
          ],
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(
            radius: 16,
            backgroundColor: AppColors.flameOrange.withOpacity(0.2),
            child: const Icon(Icons.auto_awesome, color: AppColors.flameOrange, size: 16),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                    borderRadius: const BorderRadius.only(
                      topLeft: Radius.circular(4),
                      topRight: Radius.circular(16),
                      bottomLeft: Radius.circular(16),
                      bottomRight: Radius.circular(16),
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      // RAG Sources Header
                      if (message.retrievedSources.isNotEmpty) ...[
                        Wrap(
                          spacing: 4,
                          runSpacing: 4,
                          children: message.retrievedSources.map((source) {
                            return Container(
                              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                              decoration: BoxDecoration(
                                color: AppColors.flameOrange.withOpacity(0.12),
                                borderRadius: BorderRadius.circular(6),
                              ),
                              child: Text(
                                '${source.category}: ${source.title}',
                                style: const TextStyle(
                                  color: AppColors.flameOrange,
                                  fontSize: 10,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            );
                          }).toList(),
                        ),
                        const SizedBox(height: 8),
                      ],
                      Text(message.text, style: const TextStyle(fontSize: 14, height: 1.4)),
                    ],
                  ),
                ),

                // Matched Exercise Cards
                if (message.matchedExercises.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  ...message.matchedExercises.map((ex) {
                    return Card(
                      elevation: 0,
                      margin: const EdgeInsets.only(bottom: 6),
                      shape: RoundedCornerShape(12),
                      color: theme.colorScheme.surface,
                      child: ListTile(
                        dense: true,
                        leading: const Icon(Icons.fitness_center, color: AppColors.flameOrange, size: 20),
                        title: Text(ex.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text(ex.targetMuscles),
                        trailing: FilledButton.tonal(
                          style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
                          onPressed: () => _showExerciseDetail(ex),
                          child: const Text('View Form', style: TextStyle(fontSize: 11)),
                        ),
                      ),
                    );
                  }),
                ],

                // Matched Workout Plan Cards
                if (message.matchedPlans.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  ...message.matchedPlans.map((plan) {
                    return Card(
                      elevation: 0,
                      margin: const EdgeInsets.only(bottom: 6),
                      shape: RoundedCornerShape(12),
                      color: theme.colorScheme.surface,
                      child: ListTile(
                        dense: true,
                        leading: const Icon(Icons.play_circle_fill, color: AppColors.boldRed, size: 24),
                        title: Text(plan.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text('${plan.estimatedDurationMinutes} mins • ${plan.difficulty}'),
                        trailing: FilledButton.tonal(
                          style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
                          onPressed: () {
                            context.push('/member/workouts/detail/${plan.id}');
                          },
                          child: const Text('Open Plan', style: TextStyle(fontSize: 11)),
                        ),
                      ),
                    );
                  }),
                ],

                // Action buttons: Copy & Share
                Row(
                  children: [
                    IconButton(
                      icon: const Icon(Icons.copy, size: 16),
                      tooltip: 'Copy Message',
                      onPressed: () {
                        Clipboard.setData(ClipboardData(text: message.text));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Message copied to clipboard')),
                        );
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
