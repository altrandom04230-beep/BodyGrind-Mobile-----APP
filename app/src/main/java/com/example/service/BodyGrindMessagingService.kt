package com.example.service

import android.util.Log
import com.example.data.coaching.FirestoreProvider
import com.example.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives Firebase Cloud Messaging (FCM) push notifications for session/workout cancellations
 * and keeps the signed-in user's device token registered in Firestore at
 * `/users/{uid}/fcmTokens/{token}` for server-side FCM fan-out.
 */
class BodyGrindMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Throwable) {
            null
        } ?: return

        if (token.isBlank()) return
        try {
            val db = FirestoreProvider.getDb(applicationContext)
            db.collection("users")
                .document(uid)
                .collection("fcmTokens")
                .document(token)
                .set(
                    mapOf(
                        "token" to token,
                        "platform" to "android",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to persist refreshed FCM token", t)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        val title = remoteMessage.notification?.title
            ?: data["title"]
            ?: "Session Cancelled"
        val body = remoteMessage.notification?.body
            ?: data["body"]
            ?: buildFallbackBody(data)

        NotificationHelper.showCancellationNotification(
            context = applicationContext,
            title = title,
            body = body
        )
    }

    private fun buildFallbackBody(data: Map<String, String>): String {
        val sessionTitle = data["sessionTitle"].orEmpty().ifBlank { "Scheduled session" }
        val reason = data["reason"].orEmpty()
        val cancelledBy = data["cancelledByName"].orEmpty()
        return buildString {
            append(sessionTitle)
            if (cancelledBy.isNotBlank()) append(" was cancelled by $cancelledBy")
            if (reason.isNotBlank()) append(". Reason: $reason")
        }
    }

    companion object {
        private const val TAG = "BodyGrindFcmService"
    }
}
