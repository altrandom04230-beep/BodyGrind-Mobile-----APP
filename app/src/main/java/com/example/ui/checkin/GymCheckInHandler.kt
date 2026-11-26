package com.example.ui.checkin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object GymCheckInHandler {

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun processCheckIn(context: Context, qrText: String): String {
        // a) Parse the QR (no internet needed)
        val parts = qrText.split(":")
        if (parts.size != 4 || parts[0] != "BG-CHECKIN" || parts[1] != "v2") {
            return "This isn't a BodyGrind check-in code."
        }
        val dayKey = parts[2]
        val code = parts[3]

        // b) Signed in?
        val user = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Throwable) {
            null
        }
        if (user == null) {
            return "Please sign in to check in."
        }
        val uid = user.uid

        // c) Internet connection?
        if (!isNetworkAvailable(context)) {
            return "No internet connection. Check your connection and try again."
        }

        val db = try {
            val dbId = try {
                context.getString(com.example.R.string.firestore_database_id)
            } catch (_: Throwable) {
                null
            }
            if (!dbId.isNullOrBlank()) {
                FirebaseFirestore.getInstance(dbId)
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (_: Throwable) {
            return "Something went wrong. Please try again."
        }

        // d) Read users/{uid} and do the status check (15-second limit)
        val me = try {
            withTimeoutOrNull(15_000L) {
                db.collection("users").document(uid).get().await()
            } ?: return "No internet connection. Check your connection and try again."
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "No internet connection. Check your connection and try again."
            }
            return "Something went wrong. Please try again."
        } catch (e: FirebaseNetworkException) {
            return "No internet connection. Check your connection and try again."
        } catch (e: Exception) {
            val msg = e.message?.lowercase().orEmpty()
            if (msg.contains("network") || msg.contains("connection") || msg.contains("timeout") || msg.contains("unavailable")) {
                return "No internet connection. Check your connection and try again."
            }
            return "Something went wrong. Please try again."
        }

        if (!me.exists()) {
            return "This account can't check in by QR. Please see the front desk."
        }

        val accountType = me.getString("accountType")
        val status = me.getString("status")?.lowercase()
        val userName = me.getString("name") ?: ""

        if (accountType != "MEMBER" && accountType != "TRAINER") {
            return "This account can't check in by QR. Please see the front desk."
        }

        if (accountType == "MEMBER") {
            when (status) {
                "active", "at-risk" -> {
                    // Allowed to check in
                }
                "paused" -> {
                    return "Your membership is paused. Please see the front desk to reactivate it."
                }
                "expired", "cancelled" -> {
                    return "Your membership isn't active. Please see the front desk."
                }
                else -> {
                    return "Your membership isn't active. Please see the front desk."
                }
            }
        } else if (accountType == "TRAINER") {
            if (status != "active") {
                return "Your account is inactive. Please see the front desk."
            }
        }

        // e) Save to attendanceLogs/{dayKey}_{uid} with exactly the 8 fields (15-second limit)
        val checkIn = hashMapOf(
            "userId" to uid,
            "userType" to accountType,
            "userName" to userName,
            "checkInTime" to FieldValue.serverTimestamp(),
            "method" to "QR",
            "status" to "present",
            "dayKey" to dayKey,
            "qrToken" to code
        )

        try {
            val writeResult = withTimeoutOrNull(15_000L) {
                db.collection("attendanceLogs").document("${dayKey}_$uid").set(checkIn).await()
                true
            }
            if (writeResult == null) {
                return "No internet connection. Check your connection and try again."
            }
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                // Follow-up read on the same path
                return try {
                    val followUpDoc = withTimeoutOrNull(15_000L) {
                        db.collection("attendanceLogs").document("${dayKey}_$uid").get().await()
                    }
                    if (followUpDoc != null && followUpDoc.exists()) {
                        "You've already checked in today."
                    } else {
                        "This code has expired. Scan the code on the screen again."
                    }
                } catch (_: Exception) {
                    "This code has expired. Scan the code on the screen again."
                }
            } else if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "No internet connection. Check your connection and try again."
            } else {
                return "Something went wrong. Please try again."
            }
        } catch (e: FirebaseNetworkException) {
            return "No internet connection. Check your connection and try again."
        } catch (e: Exception) {
            val msg = e.message?.lowercase().orEmpty()
            if (msg.contains("network") || msg.contains("connection") || msg.contains("timeout") || msg.contains("unavailable")) {
                return "No internet connection. Check your connection and try again."
            }
            return "Something went wrong. Please try again."
        }

        // Success: update lastActiveAt on users/{uid}
        try {
            db.collection("users").document(uid).update("lastActiveAt", FieldValue.serverTimestamp()).await()
        } catch (_: Exception) {
            // If that update fails, still show "You're checked in."
        }

        return "You're checked in."
    }
}
