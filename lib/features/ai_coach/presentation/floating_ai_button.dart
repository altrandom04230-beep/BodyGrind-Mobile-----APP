import 'package:flutter/material.dart';
import '../../../core/constants/app_colors.dart';
import 'chatbot_dialog.dart';

class FloatingAiButton extends StatefulWidget {
  const FloatingAiButton({super.key});

  @override
  State<FloatingAiButton> createState() => _FloatingAiButtonState();
}

class _FloatingAiButtonState extends State<FloatingAiButton> with SingleTickerProviderStateMixin {
  Offset _offset = const Offset(20, 100);
  bool _isDragging = false;
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1400),
    )..repeat(reverse: true);

    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.06).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  void _openChatbot() {
    showDialog(
      context: context,
      useSafeArea: false,
      builder: (_) => const ChatbotDialog(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Positioned(
      right: _offset.dx,
      bottom: _offset.dy,
      child: GestureDetector(
        onPanStart: (_) => setState(() => _isDragging = true),
        onPanUpdate: (details) {
          setState(() {
            _offset = Offset(
              (_offset.dx - details.delta.dx).clamp(10.0, 300.0),
              (_offset.dy - details.delta.dy).clamp(80.0, 600.0),
            );
          });
        },
        onPanEnd: (_) => setState(() => _isDragging = false),
        child: ScaleTransition(
          scale: _isDragging ? const AlwaysStoppedAnimation(1.08) : _pulseAnimation,
          child: Material(
            elevation: _isDragging ? 12 : 6,
            shape: RoundedCornerShape(28),
            child: InkWell(
              borderRadius: BorderRadius.circular(28),
              onTap: _openChatbot,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [AppColors.flameOrange, AppColors.flameOrangeDark],
                  ),
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(
                    color: _isDragging ? Colors.white : AppColors.boldRedLight.withOpacity(0.8),
                    width: 1.5,
                  ),
                ),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(
                        color: Colors.white.withOpacity(0.2),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(Icons.auto_awesome, color: Colors.white, size: 18),
                    ),
                    const SizedBox(width: 8),
                    Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: const [
                            Text(
                              'AI Coach',
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.black,
                                fontSize: 13,
                              ),
                            ),
                            SizedBox(width: 4),
                            Text(
                              '⋮⋮',
                              style: TextStyle(
                                color: Colors.white70,
                                fontWeight: FontWeight.bold,
                                fontSize: 10,
                              ),
                            ),
                          ],
                        ),
                        Text(
                          _isDragging ? 'Dragging...' : 'RAG Assistant',
                          style: const TextStyle(
                            color: Colors.white70,
                            fontSize: 9,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
