import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/app_role.dart';
import '../../../data/models/trainer_models.dart';
import '../../../data/providers/app_providers.dart';
import 'widgets/trainer_member_search_bar.dart';

class TrainerFeedbackScreen extends ConsumerStatefulWidget {
  final String? initialMemberId;

  const TrainerFeedbackScreen({super.key, this.initialMemberId});

  @override
  ConsumerState<TrainerFeedbackScreen> createState() => _TrainerFeedbackScreenState();
}

class _TrainerFeedbackScreenState extends ConsumerState<TrainerFeedbackScreen> {
  String? _selectedMemberId;
  FeedbackCategory _selectedCategory = FeedbackCategory.workoutFeedback;
  final TextEditingController _messageController = TextEditingController();
  String? _confirmationMessage;

  @override
  void initState() {
    super.initState();
    _selectedMemberId = widget.initialMemberId;
  }

  @override
  void dispose() {
    _messageController.dispose();
    super.dispose();
  }

  void _submitFeedback(AssignedMember member) {
    final text = _messageController.text.trim();
    if (text.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Please enter a feedback message.')),
      );
      return;
    }

    ref.read(trainerRepositoryProvider).submitFeedback(
          memberId: member.id,
          memberName: member.name,
          category: _selectedCategory,
          message: text,
        );

    setState(() {
      _messageController.clear();
      _confirmationMessage = 'Feedback sent to ${member.name}!';
    });
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final members = ref.watch(assignedMembersStreamProvider).valueOrNull ?? [];
    final allFeedback = ref.watch(feedbackListStreamProvider).valueOrNull ?? [];

    if (members.isEmpty) {
      return Scaffold(
        appBar: AppBar(title: const Text('Trainer Feedback')),
        body: const Center(child: Text('No assigned members found')),
      );
    }

    final activeMember = _selectedMemberId != null
        ? members.firstWhere((m) => m.id == _selectedMemberId, orElse: () => members.first)
        : members.first;

    final memberFeedback = allFeedback.where((f) => f.memberId == activeMember.id).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('Trainer Feedback', style: TextStyle(fontWeight: FontWeight.bold)),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        children: [
          // UI FIX 5: Shared Reusable Member Search Bar at the top
          TrainerMemberSearchBar(
            members: members,
            placeholderText: 'Search member to give feedback...',
            onMemberSelected: (id) {
              setState(() {
                _selectedMemberId = id;
                _confirmationMessage = null;
              });
            },
          ),
          const SizedBox(height: 12),

          // Horizontal Member Selector Chips
          SizedBox(
            height: 42,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: members.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (context, idx) {
                final m = members[idx];
                final isSelected = m.id == activeMember.id;

                return FilterChip(
                  selected: isSelected,
                  label: Text(m.name),
                  selectedColor: AppColors.boldRed,
                  labelStyle: TextStyle(
                    color: isSelected ? Colors.white : null,
                    fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
                  ),
                  onSelected: (_) {
                    setState(() {
                      _selectedMemberId = m.id;
                      _confirmationMessage = null;
                    });
                  },
                );
              },
            ),
          ),
          const SizedBox(height: 16),

          // Feedback Composer Card
          Card(
            elevation: 0,
            shape: RoundedCornerShape(18),
            color: theme.colorScheme.surface,
            child: Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.colorScheme.outlineVariant),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'New Feedback for ${activeMember.name}',
                    style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 10),

                  // Category Selector Chips
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: Row(
                      children: FeedbackCategory.values.map((cat) {
                        final isCatSelected = _selectedCategory == cat;
                        return Padding(
                          padding: const EdgeInsets.only(right: 6),
                          child: FilterChip(
                            selected: isCatSelected,
                            label: Text(cat.label, style: const TextStyle(fontSize: 11)),
                            selectedColor: AppColors.flameOrange,
                            labelStyle: TextStyle(
                              color: isCatSelected ? Colors.white : null,
                              fontWeight: isCatSelected ? FontWeight.bold : FontWeight.normal,
                            ),
                            onSelected: (_) => setState(() => _selectedCategory = cat),
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                  const SizedBox(height: 12),

                  // Feedback text input
                  TextField(
                    controller: _messageController,
                    maxLines: 4,
                    decoration: InputDecoration(
                      hintText: 'Write ${_selectedCategory.label.toLowerCase()}...',
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                  ),
                  const SizedBox(height: 12),

                  if (_confirmationMessage != null) ...[
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.successGreen.withOpacity(0.12),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.check_circle, color: AppColors.successGreen, size: 18),
                          const SizedBox(width: 8),
                          Text(
                            _confirmationMessage!,
                            style: const TextStyle(color: AppColors.successGreen, fontWeight: FontWeight.bold, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 10),
                  ],

                  SizedBox(
                    width: double.infinity,
                    height: 46,
                    child: FilledButton.icon(
                      style: FilledButton.styleFrom(
                        backgroundColor: AppColors.flameOrange,
                        shape: RoundedCornerShape(12),
                      ),
                      onPressed: () => _submitFeedback(activeMember),
                      icon: const Icon(Icons.send, size: 18),
                      label: const Text('Submit Feedback', style: TextStyle(fontWeight: FontWeight.bold)),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // Previous Feedback Log for Member
          Text(
            'Feedback History (${memberFeedback.length})',
            style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),

          if (memberFeedback.isEmpty)
            Container(
              padding: const EdgeInsets.all(24),
              decoration: BoxDecoration(
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.3),
                borderRadius: BorderRadius.circular(16),
              ),
              alignment: Alignment.center,
              child: const Text('No feedback recorded yet for this member'),
            )
          else
            ...memberFeedback.map((entry) {
              return Card(
                elevation: 0,
                margin: const EdgeInsets.only(bottom: 10),
                shape: RoundedCornerShape(14),
                color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.4),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: AppColors.flameOrange.withOpacity(0.15),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              entry.category.label,
                              style: const TextStyle(
                                color: AppColors.flameOrange,
                                fontSize: 10,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                          Text(entry.timestampLabel, style: theme.textTheme.labelSmall),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Text(entry.message, style: const TextStyle(height: 1.4)),
                    ],
                  ),
                ),
              );
            }),
          const SizedBox(height: 24),
        ],
      ),
    );
  }
}
