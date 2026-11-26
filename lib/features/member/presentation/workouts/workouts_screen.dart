import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/exercise.dart';
import '../../../../data/models/workout_plan.dart';
import '../../../../data/providers/app_providers.dart';
import '../exercise/exercise_detail_sheet.dart';
import '../trainers/trainers_directory_sheet.dart';
import '../../../ai_coach/presentation/chatbot_dialog.dart';

class WorkoutsScreen extends ConsumerStatefulWidget {
  const WorkoutsScreen({super.key});

  @override
  ConsumerState<WorkoutsScreen> createState() => _WorkoutsScreenState();
}

class _WorkoutsScreenState extends ConsumerState<WorkoutsScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;

  String _selectedCategory = 'All';
  String _selectedDifficulty = 'All';
  String _searchQuery = '';
  bool _showOnlyFavorites = false;

  final List<String> _categories = [
    'All',
    'Custom',
    'Full Body',
    'Abs',
    'Chest',
    'Leg',
    'Arm',
    'Fat-Burning',
    'Morning',
    'Bedtime',
  ];

  final List<String> _difficulties = [
    'All',
    'Beginner',
    'Intermediate',
    'Advanced',
  ];

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  void _showTrainersDirectory() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => TrainersDirectorySheet(
        onDismiss: () => Navigator.pop(ctx),
        onConsultWithAi: (name, topic) {
          showDialog(
            context: context,
            useSafeArea: false,
            builder: (_) => const ChatbotDialog(),
          );
        },
      ),
    );
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
    final plansAsync = ref.watch(plansStreamProvider);
    final exercisesAsync = ref.watch(exercisesStreamProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Workout Programs', style: TextStyle(fontWeight: FontWeight.bold)),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: ActionChip(
              avatar: const Icon(Icons.sports, size: 16, color: AppColors.flameOrange),
              label: const Text('Coaches', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
              onPressed: _showTrainersDirectory,
            ),
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: AppColors.boldRed,
          labelColor: AppColors.boldRed,
          unselectedLabelColor: theme.colorScheme.onSurfaceVariant,
          labelStyle: const TextStyle(fontWeight: FontWeight.bold),
          tabs: [
            Tab(text: 'Plans (${plansAsync.valueOrNull?.length ?? 0})'),
            Tab(text: 'Exercise Library (${exercisesAsync.valueOrNull?.length ?? 0})'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          // TAB 1: WORKOUT PLANS
          plansAsync.when(
            data: (plans) => _buildPlansTab(context, theme, plans),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => const Center(child: Text('Failed to load plans')),
          ),

          // TAB 2: EXERCISE LIBRARY
          exercisesAsync.when(
            data: (exercises) => _buildExerciseLibraryTab(context, theme, exercises),
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => const Center(child: Text('Failed to load exercises')),
          ),
        ],
      ),
    );
  }

  Widget _buildPlansTab(BuildContext context, ThemeData theme, List<WorkoutPlan> plans) {
    final filteredPlans = plans.where((plan) {
      final matchesCategory = switch (_selectedCategory) {
        'All' => true,
        'Custom' => plan.isCustom,
        _ => plan.category.toLowerCase() == _selectedCategory.toLowerCase(),
      };
      final matchesDifficulty = switch (_selectedDifficulty) {
        'All' => true,
        _ => plan.difficulty.toLowerCase() == _selectedDifficulty.toLowerCase(),
      };
      return matchesCategory && matchesDifficulty;
    }).toList();

    return Column(
      children: [
        // Compact Filter Dropdowns Side-by-Side & Small "+" Custom Plan Button (UI FIX 2)
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: Row(
            children: [
              // Category Dropdown
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: _selectedCategory,
                  isExpanded: true,
                  decoration: InputDecoration(
                    labelText: 'Category',
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  items: _categories.map((cat) {
                    return DropdownMenuItem(
                      value: cat,
                      child: Text(
                        cat,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: _selectedCategory == cat ? FontWeight.bold : FontWeight.normal,
                          color: _selectedCategory == cat ? AppColors.boldRed : null,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    );
                  }).toList(),
                  onChanged: (val) {
                    if (val != null) setState(() => _selectedCategory = val);
                  },
                ),
              ),
              const SizedBox(width: 8),
              // Difficulty Dropdown
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: _selectedDifficulty,
                  isExpanded: true,
                  decoration: InputDecoration(
                    labelText: 'Difficulty',
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
                    border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  items: _difficulties.map((diff) {
                    return DropdownMenuItem(
                      value: diff,
                      child: Text(
                        diff,
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: _selectedDifficulty == diff ? FontWeight.bold : FontWeight.normal,
                          color: _selectedDifficulty == diff ? AppColors.boldRed : null,
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    );
                  }).toList(),
                  onChanged: (val) {
                    if (val != null) setState(() => _selectedDifficulty = val);
                  },
                ),
              ),
              const SizedBox(width: 8),
              // Small "+" icon button beside dropdowns with 48x48 touch target (UI Fix 2b)
              Semantics(
                label: 'Build Custom Plan',
                child: SizedBox(
                  width: 48,
                  height: 48,
                  child: IconButton.filled(
                    style: IconButton.styleFrom(
                      backgroundColor: AppColors.boldRed,
                      foregroundColor: Colors.white,
                      shape: RoundedCornerShape(10),
                    ),
                    tooltip: 'Build Custom Plan',
                    icon: const Icon(Icons.add, size: 24),
                    onPressed: () => context.push('/member/workouts/builder'),
                  ),
                ),
              ),
            ],
          ),
        ),

        // Plans List or Empty State
        Expanded(
          child: filteredPlans.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.fitness_center, size: 54, color: Colors.grey),
                      const SizedBox(height: 12),
                      const Text(
                        'No plans in this category',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                      ),
                      if (_selectedCategory == 'Custom') ...[
                        const SizedBox(height: 12),
                        FilledButton(
                          onPressed: () => context.push('/member/workouts/builder'),
                          child: const Text('Create Custom Plan'),
                        ),
                      ],
                    ],
                  ),
                )
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                  itemCount: filteredPlans.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, index) {
                    final plan = filteredPlans[index];
                    return Card(
                      elevation: 0,
                      shape: RoundedCornerShape(18),
                      color: theme.colorScheme.surface,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(18),
                        onTap: () => context.push('/member/workouts/detail/${plan.id}'),
                        child: Container(
                          padding: const EdgeInsets.all(16),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(18),
                            border: Border.all(color: theme.colorScheme.outlineVariant),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Row(
                                    children: [
                                      if (plan.isCustom)
                                        Container(
                                          margin: const EdgeInsets.only(right: 6),
                                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                          decoration: BoxDecoration(
                                            color: AppColors.electricCyan.withOpacity(0.15),
                                            borderRadius: BorderRadius.circular(6),
                                          ),
                                          child: const Text(
                                            'CUSTOM',
                                            style: TextStyle(
                                              color: AppColors.electricCyan,
                                              fontSize: 10,
                                              fontWeight: FontWeight.bold,
                                            ),
                                          ),
                                        ),
                                      Text(
                                        plan.category.toUpperCase(),
                                        style: const TextStyle(
                                          color: AppColors.flameOrange,
                                          fontWeight: FontWeight.bold,
                                          fontSize: 11,
                                        ),
                                      ),
                                    ],
                                  ),
                                  Chip(
                                    label: Text(plan.difficulty, style: const TextStyle(fontSize: 10)),
                                    padding: EdgeInsets.zero,
                                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                                    visualDensity: VisualDensity.compact,
                                  ),
                                ],
                              ),
                              const SizedBox(height: 8),
                              Text(
                                plan.name,
                                style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                plan.description,
                                style: theme.textTheme.bodySmall?.copyWith(
                                  color: theme.colorScheme.onSurfaceVariant,
                                ),
                                maxLines: 2,
                                overflow: TextOverflow.ellipsis,
                              ),
                              const SizedBox(height: 12),
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Row(
                                    children: [
                                      const Icon(Icons.timer_outlined, size: 16, color: AppColors.electricCyan),
                                      const SizedBox(width: 4),
                                      Text(
                                        '${plan.estimatedDurationMinutes} mins',
                                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                                      ),
                                    ],
                                  ),
                                  const Row(
                                    children: [
                                      Text(
                                        'View Routine',
                                        style: TextStyle(
                                          color: AppColors.flameOrange,
                                          fontSize: 12,
                                          fontWeight: FontWeight.bold,
                                        ),
                                      ),
                                      Icon(Icons.chevron_right, size: 16, color: AppColors.flameOrange),
                                    ],
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }

  Widget _buildExerciseLibraryTab(BuildContext context, ThemeData theme, List<Exercise> exercises) {
    final filteredExercises = exercises.where((ex) {
      final matchesQuery = ex.name.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          ex.targetMuscles.toLowerCase().contains(_searchQuery.toLowerCase()) ||
          ex.category.toLowerCase().contains(_searchQuery.toLowerCase());
      final matchesFav = _showOnlyFavorites ? ex.isFavorite : true;
      return matchesQuery && matchesFav;
    }).toList();

    return Column(
      children: [
        // Search bar
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 6),
          child: TextField(
            decoration: InputDecoration(
              hintText: 'Search exercises (e.g. push-ups, squats)...',
              prefixIcon: const Icon(Icons.search),
              suffixIcon: _searchQuery.isNotEmpty
                  ? IconButton(
                      icon: const Icon(Icons.clear),
                      onPressed: () => setState(() => _searchQuery = ''),
                    )
                  : null,
              isDense: true,
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(14)),
            ),
            onChanged: (val) => setState(() => _searchQuery = val),
          ),
        ),

        // Filter chips (Favorites only)
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Row(
            children: [
              FilterChip(
                selected: _showOnlyFavorites,
                label: const Text('Favorites Only'),
                avatar: Icon(
                  _showOnlyFavorites ? Icons.favorite : Icons.favorite_border,
                  size: 16,
                  color: _showOnlyFavorites ? AppColors.dangerRed : null,
                ),
                onSelected: (val) => setState(() => _showOnlyFavorites = val),
              ),
            ],
          ),
        ),

        // Exercise List
        Expanded(
          child: filteredExercises.isEmpty
              ? const Center(
                  child: Text('No exercises found'),
                )
              : ListView.separated(
                  padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                  itemCount: filteredExercises.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 10),
                  itemBuilder: (context, index) {
                    final exercise = filteredExercises[index];
                    return Card(
                      elevation: 0,
                      shape: RoundedCornerShape(16),
                      color: theme.colorScheme.surface,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(16),
                        onTap: () => _showExerciseDetail(exercise),
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(color: theme.colorScheme.outlineVariant),
                          ),
                          child: Row(
                            children: [
                              Container(
                                width: 56,
                                height: 56,
                                decoration: BoxDecoration(
                                  color: AppColors.flameOrange.withOpacity(0.15),
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: const Icon(Icons.fitness_center, color: AppColors.flameOrange, size: 28),
                              ),
                              const SizedBox(width: 14),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(
                                      exercise.name,
                                      style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
                                    ),
                                    Text(
                                      exercise.targetMuscles,
                                      style: theme.textTheme.bodySmall?.copyWith(
                                        color: theme.colorScheme.onSurfaceVariant,
                                      ),
                                    ),
                                    const SizedBox(height: 2),
                                    Text(
                                      '${exercise.difficulty} • ${exercise.category}',
                                      style: const TextStyle(
                                        color: AppColors.flameOrange,
                                        fontSize: 11,
                                        fontWeight: FontWeight.bold,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              IconButton(
                                icon: Icon(
                                  exercise.isFavorite ? Icons.favorite : Icons.favorite_border,
                                  color: exercise.isFavorite ? AppColors.dangerRed : theme.colorScheme.onSurfaceVariant,
                                ),
                                onPressed: () {
                                  ref.read(workoutRepositoryProvider).toggleExerciseFavorite(
                                        exercise.id,
                                        !exercise.isFavorite,
                                      );
                                },
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  },
                ),
        ),
      ],
    );
  }
}
