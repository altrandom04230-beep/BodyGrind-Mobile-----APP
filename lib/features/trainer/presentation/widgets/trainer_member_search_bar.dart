import 'package:flutter/material.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../data/models/trainer_models.dart';

class TrainerMemberSearchBar extends StatefulWidget {
  final List<AssignedMember> members;
  final ValueChanged<String> onMemberSelected;
  final String placeholderText;

  const TrainerMemberSearchBar({
    super.key,
    required this.members,
    required this.onMemberSelected,
    this.placeholderText = 'Search member by name...',
  });

  @override
  State<TrainerMemberSearchBar> createState() => _TrainerMemberSearchBarState();
}

class _TrainerMemberSearchBarState extends State<TrainerMemberSearchBar> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();
  final LayerLink _layerLink = LayerLink();
  OverlayEntry? _overlayEntry;
  List<AssignedMember> _filteredMembers = [];

  @override
  void initState() {
    super.initState();
    _controller.addListener(_onSearchChanged);
    _focusNode.addListener(() {
      if (!_focusNode.hasFocus) {
        _hideOverlay();
      }
    });
  }

  @override
  void dispose() {
    _hideOverlay();
    _controller.removeListener(_onSearchChanged);
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _onSearchChanged() {
    final query = _controller.text.trim().toLowerCase();
    if (query.isEmpty) {
      _hideOverlay();
      return;
    }

    _filteredMembers = widget.members
        .where((m) => m.name.toLowerCase().contains(query))
        .toList();

    _showOverlay();
  }

  void _showOverlay() {
    _hideOverlay();
    final overlayState = Overlay.of(context);
    final renderBox = context.findRenderObject() as RenderBox?;
    if (renderBox == null) return;
    final size = renderBox.size;

    _overlayEntry = OverlayEntry(
      builder: (context) {
        final theme = Theme.of(context);

        return Positioned(
          width: size.width,
          child: CompositedTransformFollower(
            link: _layerLink,
            showWhenUnlinked: false,
            offset: Offset(0.0, size.height + 6.0),
            child: Material(
              elevation: 6,
              shape: RoundedCornerShape(14),
              color: theme.colorScheme.surface,
              child: Container(
                constraints: const BoxConstraints(maxHeight: 250),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: theme.colorScheme.outlineVariant),
                ),
                child: _filteredMembers.isEmpty
                    ? Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
                        child: Row(
                          children: [
                            Icon(Icons.search_off, size: 20, color: theme.colorScheme.onSurfaceVariant),
                            const SizedBox(width: 10),
                            Text(
                              'No members found',
                              style: theme.textTheme.bodyMedium?.copyWith(
                                color: theme.colorScheme.onSurfaceVariant,
                              ),
                            ),
                          ],
                        ),
                      )
                    : ListView.separated(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        shrinkWrap: true,
                        itemCount: _filteredMembers.length,
                        separatorBuilder: (_, __) => const Divider(height: 1),
                        itemBuilder: (context, idx) {
                          final member = _filteredMembers[idx];
                          final initials = member.name.split(' ').take(2).map((s) => s.isNotEmpty ? s[0] : '').join();

                          return ListTile(
                            dense: true,
                            leading: CircleAvatar(
                              radius: 16,
                              backgroundColor: theme.colorScheme.primary.withOpacity(0.15),
                              child: Text(
                                initials,
                                style: TextStyle(
                                  color: theme.colorScheme.primary,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 11,
                                ),
                              ),
                            ),
                            title: Text(member.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                            subtitle: Text('${member.fitnessGoal} • ${member.status.label}'),
                            trailing: Text(
                              '${member.workoutCompletionRate}%',
                              style: TextStyle(
                                color: theme.colorScheme.primary,
                                fontWeight: FontWeight.bold,
                                fontSize: 12,
                              ),
                            ),
                            onTap: () {
                              _controller.text = member.name;
                              widget.onMemberSelected(member.id);
                              _hideOverlay();
                              _focusNode.unfocus();
                            },
                          );
                        },
                      ),
              ),
            ),
          ),
        );
      },
    );

    overlayState.insert(_overlayEntry!);
  }

  void _hideOverlay() {
    _overlayEntry?.remove();
    _overlayEntry = null;
  }

  @override
  Widget build(BuildContext context) {
    return CompositedTransformTarget(
      link: _layerLink,
      child: TextField(
        controller: _controller,
        focusNode: _focusNode,
        decoration: InputDecoration(
          hintText: widget.placeholderText,
          prefixIcon: const Icon(Icons.search, size: 20),
          suffixIcon: _controller.text.isNotEmpty
              ? IconButton(
                  icon: const Icon(Icons.clear, size: 18),
                  onPressed: () {
                    _controller.clear();
                    _hideOverlay();
                  },
                )
              : null,
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
    );
  }
}
