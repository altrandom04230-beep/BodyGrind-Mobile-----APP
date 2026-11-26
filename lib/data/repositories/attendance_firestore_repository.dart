import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';

/// Gym Attendance & Firestore Check-In repository matching GymCheckInHandler.kt
class AttendanceFirestoreRepository {
  final FirebaseFirestore _firestore;
  final FirebaseAuth _auth;

  AttendanceFirestoreRepository({
    FirebaseFirestore? firestore,
    FirebaseAuth? auth,
  })  : _firestore = firestore ?? FirebaseFirestore.instance,
        _auth = auth ?? FirebaseAuth.instance;

  Future<String> processCheckIn(String qrText) async {
    // a) Parse the QR
    final parts = qrText.split(':');
    if (parts.length != 4 || parts[0] != 'BG-CHECKIN' || parts[1] != 'v2') {
      return "This isn't a BodyGrind check-in code.";
    }
    final dayKey = parts[2];
    final code = parts[3];

    // b) Signed in?
    final user = _auth.currentUser;
    if (user == null) {
      return 'Please sign in to check in.';
    }
    final uid = user.uid;

    // c) Read users/{uid} with 15-second timeout
    DocumentSnapshot<Map<String, dynamic>> me;
    try {
      me = await _firestore
          .collection('users')
          .doc(uid)
          .get()
          .timeout(const Duration(seconds: 15));
    } catch (e) {
      final msg = e.toString().toLowerCase();
      if (msg.contains('network') ||
          msg.contains('connection') ||
          msg.contains('timeout') ||
          msg.contains('unavailable')) {
        return 'No internet connection. Check your connection and try again.';
      }
      return 'Something went wrong. Please try again.';
    }

    if (!me.exists || me.data() == null) {
      return "This account can't check in by QR. Please see the front desk.";
    }

    final data = me.data()!;
    final accountType = data['accountType'] as String?;
    final status = (data['status'] as String?)?.toLowerCase();
    final userName = data['name'] as String? ?? '';

    if (accountType != 'MEMBER' && accountType != 'TRAINER') {
      return "This account can't check in by QR. Please see the front desk.";
    }

    if (accountType == 'MEMBER') {
      switch (status) {
        case 'active':
        case 'at-risk':
          // Allowed to check in
          break;
        case 'paused':
          return 'Your membership is paused. Please see the front desk to reactivate it.';
        case 'expired':
        case 'cancelled':
        default:
          return "Your membership isn't active. Please see the front desk.";
      }
    } else if (accountType == 'TRAINER') {
      if (status != 'active') {
        return 'Your account is inactive. Please see the front desk.';
      }
    }

    // d) Write to attendanceLogs/{dayKey}_{uid} with exactly the 8 fields
    final checkIn = <String, dynamic>{
      'userId': uid,
      'userType': accountType,
      'userName': userName,
      'checkInTime': FieldValue.serverTimestamp(),
      'method': 'QR',
      'status': 'present',
      'dayKey': dayKey,
      'qrToken': code,
    };

    try {
      await _firestore
          .collection('attendanceLogs')
          .doc('${dayKey}_$uid')
          .set(checkIn)
          .timeout(const Duration(seconds: 15));
    } on FirebaseException catch (e) {
      if (e.code == 'permission-denied') {
        try {
          final followUp = await _firestore
              .collection('attendanceLogs')
              .doc('${dayKey}_$uid')
              .get()
              .timeout(const Duration(seconds: 15));
          if (followUp.exists) {
            return "You've already checked in today.";
          } else {
            return 'This code has expired. Scan the code on the screen again.';
          }
        } catch (_) {
          return 'This code has expired. Scan the code on the screen again.';
        }
      } else if (e.code == 'unavailable') {
        return 'No internet connection. Check your connection and try again.';
      } else {
        return 'Something went wrong. Please try again.';
      }
    } catch (e) {
      final msg = e.toString().toLowerCase();
      if (msg.contains('network') ||
          msg.contains('connection') ||
          msg.contains('timeout') ||
          msg.contains('unavailable')) {
        return 'No internet connection. Check your connection and try again.';
      }
      return 'Something went wrong. Please try again.';
    }

    // e) Update lastActiveAt on users/{uid}
    try {
      await _firestore.collection('users').doc(uid).update({
        'lastActiveAt': FieldValue.serverTimestamp(),
      });
    } catch (_) {
      // Ignored: still checked in
    }

    return "You're checked in.";
  }
}
