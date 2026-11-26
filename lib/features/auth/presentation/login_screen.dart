import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_colors.dart';
import '../controllers/auth_controller.dart';

/// Firebase Auth Login Screen matching LoginScreen.kt
class LoginScreen extends ConsumerStatefulWidget {
  final VoidCallback onLoginSuccess;

  const LoginScreen({
    super.key,
    required this.onLoginSuccess,
  });

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _passwordVisible = false;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _attemptLogin() async {
    FocusScope.of(context).unfocus();
    final success = await ref
        .read(authControllerProvider.notifier)
        .signInWithEmailAndPassword(
          email: _emailController.text,
          password: _passwordController.text,
        );

    if (success && mounted) {
      widget.onLoginSuccess();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final authState = ref.watch(authControllerProvider);

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
                  // App Brand Logo
                  Container(
                    width: 96,
                    height: 96,
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
                      size: 48,
                      color: Colors.white,
                    ),
                  ),
                  const SizedBox(height: 20),

                  // App Title
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
                    'Member & Trainer Portal',
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.boldRed,
                    ),
                  ),
                  const SizedBox(height: 24),

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
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Email Field
                          TextField(
                            controller: _emailController,
                            keyboardType: TextInputType.emailAddress,
                            textInputAction: TextInputAction.next,
                            decoration: InputDecoration(
                              labelText: 'Email Address',
                              prefixIcon: Icon(
                                Icons.email,
                                color: AppColors.boldRed,
                              ),
                            ),
                          ),
                          const SizedBox(height: 16),

                          // Password Field
                          TextField(
                            controller: _passwordController,
                            obscureText: !_passwordVisible,
                            textInputAction: TextInputAction.done,
                            onSubmitted: (_) => _attemptLogin(),
                            decoration: InputDecoration(
                              labelText: 'Password',
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
                          const SizedBox(height: 16),

                          // Error Banner
                          if (authState.errorMessage != null) ...[
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(
                                color: AppColors.boldRed.withOpacity(0.12),
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                  color: AppColors.boldRed.withOpacity(0.5),
                                ),
                              ),
                              child: Row(
                                children: [
                                  const Icon(
                                    Icons.error_outline,
                                    color: AppColors.boldRed,
                                    size: 20,
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Text(
                                      authState.errorMessage!,
                                      style: theme.textTheme.bodySmall?.copyWith(
                                        color: AppColors.boldRed,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 16),
                          ],

                          // Sign In Button
                          SizedBox(
                            height: 50,
                            child: ElevatedButton(
                              onPressed: authState.isLoading ? null : _attemptLogin,
                              style: ElevatedButton.styleFrom(
                                backgroundColor: AppColors.boldRed,
                                foregroundColor: Colors.white,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(14),
                                ),
                              ),
                              child: authState.isLoading
                                  ? const SizedBox(
                                      width: 22,
                                      height: 22,
                                      child: CircularProgressIndicator(
                                        strokeWidth: 2.5,
                                        color: Colors.white,
                                      ),
                                    )
                                  : const Row(
                                      mainAxisAlignment: MainAxisAlignment.center,
                                      children: [
                                        Icon(Icons.login, size: 20),
                                        SizedBox(width: 8),
                                        Text(
                                          'Sign In',
                                          style: TextStyle(
                                            fontWeight: FontWeight.bold,
                                            fontSize: 16,
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
                  const SizedBox(height: 24),

                  // Info Notice
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 10,
                    ),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainer.withOpacity(0.5),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Icon(
                          Icons.info,
                          size: 18,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: Text(
                            'New member or trainer? Accounts are created exclusively by front desk staff in the admin portal.',
                            style: theme.textTheme.bodySmall?.copyWith(
                              color: theme.colorScheme.onSurfaceVariant,
                            ),
                          ),
                        ),
                      ],
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
