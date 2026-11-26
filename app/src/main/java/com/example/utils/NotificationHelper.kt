package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity

object NotificationHelper {

    private const val CHANNEL_ID = "workout_reminders"
    private const val CHANNEL_NAME = "Daily Workout Reminders"
    private const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to stay consistent with your daily home workout routines"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun showWorkoutReminder(context: Context, title: String, message: String) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission handled gracefully
        }
    }

    private const val CHAT_CHANNEL_ID = "coach_messages"
    private const val CHAT_CHANNEL_NAME = "Coach & Member Messages"
    private const val CHAT_NOTIFICATION_ID = 2002

    fun showCoachMessageNotification(context: Context, senderName: String, messageText: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHAT_CHANNEL_ID,
                CHAT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when your coach or member sends you a message"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            CHAT_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHAT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("New message from $senderName")
            .setContentText(messageText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(CHAT_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted
        }
    }

    private const val CANCELLATION_CHANNEL_ID = "session_cancellations"
    private const val CANCELLATION_CHANNEL_NAME = "Session Cancellations"
    const val EXTRA_OPEN_DESTINATION = "extra_open_destination"
    const val DESTINATION_MY_COACH = "my_coach"

    fun showCancellationNotification(
        context: Context,
        title: String,
        body: String,
        notificationId: Int = (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
        openDestination: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CANCELLATION_CHANNEL_ID,
                CANCELLATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for members, trainers, and admins when a scheduled session or workout plan is cancelled"
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!openDestination.isNullOrBlank()) {
                putExtra(EXTRA_OPEN_DESTINATION, openDestination)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CANCELLATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ POST_NOTIFICATIONS permission not granted
        }
    }
}
