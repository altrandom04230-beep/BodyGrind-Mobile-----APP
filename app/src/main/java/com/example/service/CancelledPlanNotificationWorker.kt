package com.example.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.coaching.CoachingRepository
import com.example.data.coaching.CoachingSessionPrefs
import com.example.data.trainer.AppRole
import com.example.utils.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class CancelledPlanNotificationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            if (FirebaseApp.getApps(applicationContext).isEmpty()) {
                FirebaseApp.initializeApp(applicationContext)
            }
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.d(TAG, "No signed-in user; skipping cancelled workout plan check.")
                return Result.success()
            }
            val uid = currentUser.uid
            val coachingRepository = CoachingRepository(applicationContext)

            val cachedRole = CoachingSessionPrefs.getSavedRole(applicationContext, uid)
            if (cachedRole != null && cachedRole != AppRole.MEMBER) {
                Log.d(TAG, "Signed-in user $uid has role $cachedRole; skipping member cancellation check.")
                return Result.success()
            }
            if (cachedRole == null) {
                val accountResult = coachingRepository.fetchUserAccount(uid)
                val accountDoc = accountResult.getOrElse { error ->
                    Log.w(TAG, "Could not verify user role for $uid; retrying later.", error)
                    return Result.retry()
                }
                if (accountDoc == null || accountDoc.accountType != AppRole.MEMBER) {
                    Log.d(TAG, "User $uid is not a member (${accountDoc?.accountType}); skipping.")
                    return Result.success()
                }
            }

            val plansResult = coachingRepository.fetchMemberAssignedPlans(uid)
            val plans = plansResult.getOrElse { error ->
                if (error is FirebaseFirestoreException &&
                    error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    Log.e(
                        TAG,
                        "PERMISSION_DENIED reading users/$uid/assignedPlans: ${error.message}",
                        error
                    )
                } else {
                    Log.w(
                        TAG,
                        "Failed reading users/$uid/assignedPlans: ${error.message}",
                        error
                    )
                }
                return Result.retry()
            }

            val cancelledPlans = plans.filter { it.isCancelled }

            if (!CoachingSessionPrefs.isCancelledPlanNotifierInitialized(applicationContext, uid)) {
                val initialIds = cancelledPlans.map { it.id }.filter { it.isNotBlank() }
                CoachingSessionPrefs.markCancelledPlansNotified(applicationContext, uid, initialIds)
                Log.d(
                    TAG,
                    "Initial run for member $uid: recorded ${initialIds.size} existing cancelled plan(s) without notifying."
                )
                return Result.success()
            }

            val alreadyNotifiedIds = CoachingSessionPrefs.getNotifiedCancelledPlanIds(applicationContext, uid)
            val newlySeenIds = mutableSetOf<String>()

            for (plan in cancelledPlans) {
                if (plan.id.isBlank() || plan.id in alreadyNotifiedIds) continue
                newlySeenIds.add(plan.id)

                val cancelledByMember = plan.cancelledByUid == uid ||
                    plan.cancelledByRole.equals("MEMBER", ignoreCase = true)
                if (cancelledByMember) {
                    Log.d(TAG, "Skipping self-cancelled plan ${plan.id} for member $uid.")
                    continue
                }

                val planTitle = plan.planTitle.trim().ifBlank { "Workout Plan" }
                val cancellerName = plan.cancelledByName?.trim().takeIf { !it.isNullOrBlank() }
                    ?: plan.cancelledByRole?.trim().takeIf { !it.isNullOrBlank() }
                    ?: plan.trainerName.trim().takeIf { it.isNotBlank() }
                    ?: "your coach"
                val reason = plan.cancelledReason?.trim().orEmpty()
                val body = if (reason.isNotBlank()) {
                    "Your workout \"$planTitle\" was cancelled by $cancellerName. Reason: $reason"
                } else {
                    "Your workout \"$planTitle\" was cancelled by $cancellerName."
                }

                NotificationHelper.showCancellationNotification(
                    context = applicationContext,
                    title = "Workout plan cancelled",
                    body = body,
                    notificationId = deterministicNotificationId(plan.id),
                    openDestination = NotificationHelper.DESTINATION_MY_COACH
                )
                Log.d(TAG, "Posted cancellation notification for plan ${plan.id} ($planTitle).")
            }

            if (newlySeenIds.isNotEmpty()) {
                CoachingSessionPrefs.markCancelledPlansNotified(applicationContext, uid, newlySeenIds)
            }

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error checking cancelled workout plans: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        const val TAG = "CancelledPlanWorker"
        const val UNIQUE_PERIODIC_WORK_NAME = "member_cancelled_plan_periodic_check"
        const val UNIQUE_ONE_TIME_WORK_NAME = "member_cancelled_plan_immediate_check"

        private fun connectedConstraints(): Constraints =
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CancelledPlanNotificationWorker>(
                15L,
                TimeUnit.MINUTES
            )
                .setConstraints(connectedConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<CancelledPlanNotificationWorker>()
                .setConstraints(connectedConstraints())
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
            workManager.cancelUniqueWork(UNIQUE_ONE_TIME_WORK_NAME)
        }

        fun deterministicNotificationId(planId: String): Int {
            val positiveHash = planId.hashCode() and 0x7FFFFFFF
            return 30_000 + (positiveHash % 1_000_000)
        }
    }
}
