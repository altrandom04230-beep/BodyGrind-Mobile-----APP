import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_auth/firebase_auth.dart';
import '../../../data/models/app_role.dart';
import '../../../data/providers/app_providers.dart';

class AuthState {
  final bool isLoading;
  final String? errorMessage;

  const AuthState({this.isLoading = false, this.errorMessage});

  AuthState copyWith({bool? isLoading, String? errorMessage}) {
    return AuthState(
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}

class AuthController extends StateNotifier<AuthState> {
  final Ref _ref;

  AuthController(this._ref) : super(const AuthState());

  void selectRole(AppRole role) {
    _ref.read(selectedRoleProvider.notifier).state = role;
  }

  void clearRole() {
    _ref.read(selectedRoleProvider.notifier).state = null;
  }

  Future<bool> signInWithEmailAndPassword({
    required String email,
    required String password,
  }) async {
    final trimmedEmail = email.trim();
    final trimmedPassword = password;

    if (trimmedEmail.isEmpty || trimmedPassword.isEmpty) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Please enter your email and password.',
      );
      return false;
    }

    final emailRegex = RegExp(r'^[^@]+@[^@]+\.[^@]+$');
    if (!emailRegex.hasMatch(trimmedEmail)) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Please enter a valid email address.',
      );
      return false;
    }

    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final authRepo = _ref.read(authRepositoryProvider);
      await authRepo.signInWithEmailAndPassword(
        email: trimmedEmail,
        password: trimmedPassword,
      );
      state = state.copyWith(isLoading: false, errorMessage: null);
      return true;
    } on FirebaseAuthException catch (e) {
      String message;
      switch (e.code) {
        case 'invalid-email':
          message = 'Please enter a valid email address.';
          break;
        case 'user-not-found':
        case 'wrong-password':
        case 'invalid-credential':
          message = 'Incorrect email or password.';
          break;
        case 'too-many-requests':
          message = 'Too many unsuccessful attempts. Please try again later.';
          break;
        case 'network-request-failed':
          message = 'No internet connection. Check your connection and try again.';
          break;
        default:
          message = 'Incorrect email or password.';
      }
      state = state.copyWith(isLoading: false, errorMessage: message);
      return false;
    } catch (e) {
      final msg = e.toString().toLowerCase();
      String message;
      if (msg.contains('network') ||
          msg.contains('connection') ||
          msg.contains('timeout') ||
          msg.contains('offline')) {
        message = 'No internet connection. Check your connection and try again.';
      } else {
        message = 'Something went wrong. Please try again.';
      }
      state = state.copyWith(isLoading: false, errorMessage: message);
      return false;
    }
  }

  Future<void> signOut() async {
    final authRepo = _ref.read(authRepositoryProvider);
    await authRepo.signOut();
    _ref.read(selectedRoleProvider.notifier).state = null;
  }
}

final authControllerProvider =
    StateNotifierProvider<AuthController, AuthState>((ref) {
  return AuthController(ref);
});
