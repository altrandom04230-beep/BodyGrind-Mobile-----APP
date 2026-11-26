import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../../../data/models/app_role.dart';
import '../controllers/auth_controller.dart';

/// Role Selection & Mock Login Screen matching MockLoginScreen.kt
class MockLoginScreen extends ConsumerStatefulWidget {
  final ValueChanged<AppRole> onLoginWithRole;

  const MockLoginScreen({
    super.key,
    required this.onLoginWithRole,
  });

  @override
  ConsumerState<MockLoginScreen> createState() => _MockLoginScreenState();
}

class _MockLoginScreenState extends ConsumerState<MockLoginScreen> {
  AppRole _selectedRole = AppRole.member;
  late TextEditingController _emailController;
  late TextEditingController _passwordController;
  bool _passwordVisible = false;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController(text: 'member@bodygrind.local');
    _passwordController = TextEditingController(text: '••••••••');
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  void _selectRole(AppRole role) {
    setState(() {
      _selectedRole = role;
      _emailController.text = role == AppRole.member
          ? 'member@bodygrind.local'
          : 'cobie.trainer@bodygrind.local';
    });
  }

  void _submit() {
    FocusScope.of(context).unfocus();
    ref.read(authControllerProvider.notifier).selectRole(_selectedRole);
    widget.onLoginWithRole(_selectedRole);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      backgroundColor: theme.scaffoldBackgroundColor,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 28),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  // App Brand Logo / Avatar
                  Container(
                    width: 92,
                    height: 92,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: AppColors.boldRed,
                      boxShadow: [
                        BoxShadow(
                          color: AppColors.boldRed.withOpacity(0.35),
                          blurRadius: 16,
                          offset: const Offset(0, 4),
                        ),
                      ],
                    ),
                    alignment: Alignment.center,
                    child: const Icon(
                      Icons.fitness_center,
                      size: 46,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Brand Title
                  Text(
                    'BodyGrind',
                    style: theme.textTheme.headlineLarge?.copyWith(
                      fontWeight: FontWeight.w900,
                      letterSpacing: 1.0,
                    ),
                  ),
                  const SizedBox(height: 4),

                  // Subtitle
                  Text(
                    'Choose Your Role to Continue',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.boldRed,
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Login Card
                  Card(
                    color: theme.colorScheme.surface,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(20),
                      side: BorderSide(
                        color: theme.colorScheme.outline,
                        width: 1,
                      ),
                    ),
                    child: Padding(
                      padding: const EdgeInsets.all(20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'SELECT ROLE',
                            style: theme.textTheme.labelSmall?.copyWith(
                              fontWeight: FontWeight.bold,
                              color: theme.colorScheme.onSurfaceVariant,
                              letterSpacing: 1.0,
                            ),
                          ),
                          const SizedBox(height: 16),

                          // Role Option Cards (Member vs Trainer)
                          Row(
                            children: [
                              Expanded(
                                child: _RoleOptionCard(
                                  title: 'Member',
                                  subtitle: 'Workouts & Check-In',
                                  icon: Icons.person,
                                  isSelected: _selectedRole == AppRole.member,
                                  onTap: () => _selectRole(AppRole.member),
                                ),
                              ),
                              const SizedBox(width: 10),
                              Expanded(
                                child: _RoleOptionCard(
                                  title: 'Trainer',
                                  subtitle: 'Coach Portal',
                                  icon: Icons.sports,
                                  isSelected: _selectedRole == AppRole.trainer,
                                  onTap: () => _selectRole(AppRole.trainer),
                                ),
                              ),
                            ],
                          ),
                          const SizedBox(height: 16),

                          // Mock Email Input
                          TextField(
                            controller: _emailController,
                            keyboardType: TextInputType.emailAddress,
                            textInputAction: TextInputAction.next,
                            decoration: InputDecoration(
                              labelText: 'Mock Email',
                              prefixIcon: Icon(
                                Icons.email,
                                color: AppColors.boldRed,
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),

                          // Mock Password Input
                          TextField(
                            controller: _passwordController,
                            obscureText: !_passwordVisible,
                            textInputAction: TextInputAction.done,
                            onSubmitted: (_) => _submit(),
                            decoration: InputDecoration(
                              labelText: 'Mock Password',
                              prefixIcon: Icon(
                                Icons.lock,
                                color: AppColors.boldRed,
                              ),
                              suffixIcon: IconButton(
                                icon: Icon(
                                  _passwordVisible
                                      ? Icons.visibility_off
                                      : Icons.visibility,
                                ),
                                onPressed: () {
                                  setState(() {
                                    _passwordVisible = !_passwordVisible;
                                  });
                                },
                              ),
                            ),
                          ),
                          const SizedBox(height: 20),

                          // Action Button
                          SizedBox(
                            width: double.infinity,
                            height: 52,
                            child: ElevatedButton(
                              onPressed: _submit,
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.boldRed,
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(14),
                                ),
                              ),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(
                                    _selectedRole == AppRole.trainer
                                        ? Icons.sports
                                        : Icons.login,
                                    size: 20,
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    _selectedRole == AppRole.member
                                        ? 'Continue as Member'
                                        : 'Continue as Trainer',
                                    style: const TextStyle(
                                      fontWeight: FontWeight.bold,
                                      fontSize: 15,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _RoleOptionCard extends StatelessWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final bool isSelected;
  final VoidCallback onTap;

  const _RoleOptionCard({
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.isSelected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(14),
      child: Container(
        constraints: const BoxConstraints(minHeight: 76),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(14),
          color: isSelected
              ? AppColors.boldRed.withOpacity(0.14)
              : theme.colorScheme.surfaceContainer,
          border: Border.all(
            color: isSelected ? AppColors.boldRed : theme.colorScheme.outline,
            width: isSelected ? 2 : 1,
          ),
        ),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  icon,
                  size: 20,
                  color: isSelected
                      ? AppColors.boldRed
                      : theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Text(
                  title,
                  style: theme.textTheme.titleSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: isSelected
                        ? AppColors.boldRed
                        : theme.colorScheme.onSurface,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              subtitle,
              style: theme.textTheme.labelSmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
                fontWeight: FontWeight.normal,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
