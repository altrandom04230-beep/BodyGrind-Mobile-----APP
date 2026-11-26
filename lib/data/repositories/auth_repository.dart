import 'package:firebase_auth/firebase_auth.dart';

/// Firebase Authentication repository wrapping FirebaseAuth
class AuthRepository {
  final FirebaseAuth _auth;

  AuthRepository({FirebaseAuth? auth}) : _auth = auth ?? FirebaseAuth.instance;

  Stream<User?> get authStateChanges => _auth.authStateChanges();
  User? get currentUser => _auth.currentUser;

  Future<void> signInWithEmailAndPassword({
    required String email,
    required String password,
  }) async {
    final trimmedEmail = email.trim();
    if (trimmedEmail.isEmpty || password.isEmpty) {
      throw FirebaseAuthException(
        code: 'empty-credentials',
        message: 'Please enter your email and password.',
      );
    }

    await _auth.signInWithEmailAndPassword(
      email: trimmedEmail,
      password: password,
    );
  }

  Future<void> signOut() async {
    await _auth.signOut();
  }
}
