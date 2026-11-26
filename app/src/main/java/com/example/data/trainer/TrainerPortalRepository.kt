package com.example.data.trainer

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.coaching.FirestoreProvider
import com.example.data.coaching.OperationType
import com.example.data.coaching.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class TrainerPortalRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getDb(context),
        storage = try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            Log.w("TrainerPortalRepository", "FirebaseStorage init fallback", e)
            FirebaseStorage.getInstance("gs://bodygrind-gym-app.firebasestorage.app")
        }
    )

    private fun requireSignedInUid(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in to perform this operation.")
    }

    /**
     * Real-time observation of a single trainer's profile from users/{trainerId}.
     */
    fun observeTrainerProfile(trainerId: String): Flow<Result<TrainerProfileDoc?>> = callbackFlow {
        if (trainerId.isBlank()) {
            trySend(Result.failure(IllegalArgumentException("Trainer user ID cannot be empty.")))
            close()
            return@callbackFlow
        }

        val listener = db.collection("users").document(trainerId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    val msg = handleFirestoreError(error, OperationType.GET, "users/$trainerId")
                    trySend(Result.failure(IllegalStateException(msg, error)))
                    close()
                    return@addSnapshotListener
                }

                val doc = if (snap != null && snap.exists()) {
                    val data = snap.data.orEmpty()
                    val phoneRaw = when (val rawPhone = data["phone"]) {
                        is String -> rawPhone
                        is Number -> rawPhone.toString()
                        else -> ""
                    }
                    TrainerProfileDoc(
                        trainerId = snap.id,
                        name = (data["name"] as? String).orEmpty(),
                        email = (data["email"] as? String).orEmpty(),
                        phone = phoneRaw,
                        specialization = (data["specialty"] as? String).orEmpty(),
                        certificateUrl = null,
                        certificateStoragePath = null,
                        certificateFileName = null,
                        certificateMimeType = null,
                        certificateStatus = CertificateStatus.PENDING.rawValue,
                        certificateReviewedBy = null,
                        certificateReviewedAt = null,
                        createdAt = runCatching {
                            snap.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull() ?: (data["createdAt"] as? Timestamp),
                        updatedAt = runCatching {
                            snap.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.getOrNull() ?: (data["updatedAt"] as? Timestamp)
                    )
                } else {
                    null
                }
                trySend(Result.success(doc))
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time observation of all trainers (accessible to Admins only).
     */
    fun observeAllTrainers(): Flow<Result<List<TrainerProfileDoc>>> {
        return db.collection("trainers")
            .snapshots()
            .map { snap ->
                val list = snap.documents.mapNotNull { doc ->
                    doc.toObject(TrainerProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.copy(trainerId = doc.id)
                }.sortedBy { it.name.lowercase() }
                Result.success(list)
            }
            .catch { error ->
                if (error is CancellationException) throw error
                val msg = if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, "trainers")
                } else {
                    error.localizedMessage ?: "Failed to load trainer list."
                }
                emit(Result.failure(IllegalStateException(msg, error)))
            }
    }

    /**
     * One-shot fetch of a trainer's profile.
     */
    suspend fun getTrainerProfile(trainerId: String): Result<TrainerProfileDoc?> {
        return try {
            requireSignedInUid()
            val snap = db.collection("trainers").document(trainerId).get().await()
            val doc = if (snap.exists()) {
                snap.toObject(TrainerProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.copy(trainerId = snap.id)
            } else null
            Result.success(doc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = handleFirestoreError(e, OperationType.GET, "trainers/$trainerId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    /**
     * Save or update a trainer's own editable profile fields (name, phone) on users/{trainerId}.
     * Firestore rules only allow trainers to update name, phone, and updatedAt on their own user record.
     */
    suspend fun saveTrainerProfile(
        trainerId: String,
        name: String,
        phone: String
    ): Result<Unit> {
        val uidErr = TrainerProfileValidator.validateUserId(trainerId)
        if (uidErr != null) return Result.failure(IllegalArgumentException(uidErr))
        val nameErr = TrainerProfileValidator.validateName(name)
        if (nameErr != null) return Result.failure(IllegalArgumentException(nameErr))
        val phoneErr = TrainerProfileValidator.validatePhone(phone)
        if (phoneErr != null) return Result.failure(IllegalArgumentException(phoneErr))

        return try {
            requireSignedInUid()
            val trimmedId = trainerId.trim()
            val updates = mapOf<String, Any>(
                "name" to name.trim(),
                "phone" to phone.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(trimmedId).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = handleFirestoreError(e, OperationType.UPDATE, "users/$trainerId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    /**
     * Admin action: Edit any existing trainer profile while preserving createdAt and certificate fields.
     */
    suspend fun adminUpdateTrainerProfile(
        trainerId: String,
        name: String,
        email: String,
        phone: String,
        specialization: String
    ): Result<Unit> {
        val uidErr = TrainerProfileValidator.validateUserId(trainerId)
        if (uidErr != null) return Result.failure(IllegalArgumentException(uidErr))
        val nameErr = TrainerProfileValidator.validateName(name)
        if (nameErr != null) return Result.failure(IllegalArgumentException(nameErr))
        val emailErr = TrainerProfileValidator.validateEmail(email)
        if (emailErr != null) return Result.failure(IllegalArgumentException(emailErr))
        val phoneErr = TrainerProfileValidator.validatePhone(phone)
        if (phoneErr != null) return Result.failure(IllegalArgumentException(phoneErr))
        val specErr = TrainerProfileValidator.validateSpecialization(specialization)
        if (specErr != null) return Result.failure(IllegalArgumentException(specErr))

        return try {
            requireSignedInUid()
            val trimmedId = trainerId.trim()
            val docRef = db.collection("trainers").document(trimmedId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                return Result.failure(IllegalStateException("Trainer profile not found."))
            }

            val updates = mapOf<String, Any>(
                "name" to name.trim(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "specialization" to specialization.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = handleFirestoreError(e, OperationType.UPDATE, "trainers/$trainerId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    /**
     * Upload or replace a certificate in Firebase Storage and update the Firestore trainer document.
     * Enforces 5 MB max size, allowed MIME types (PDF, JPG, PNG), and forces certificateStatus to "pending"
     * while clearing prior review fields via FieldValue.delete().
     */
    suspend fun uploadCertificate(
        trainerId: String,
        uri: Uri,
        fileName: String,
        mimeType: String,
        contentResolver: ContentResolver,
        reportedSizeBytes: Long? = null
    ): Result<TrainerProfileDoc> {
        val normalizedMime = TrainerProfileValidator.normalizeMimeType(mimeType)
        val preCheckErr = TrainerProfileValidator.validateCertificateFile(normalizedMime, reportedSizeBytes)
        if (preCheckErr != null) return Result.failure(IllegalArgumentException(preCheckErr))
        val safeMime = normalizedMime!!

        val safeDisplayName = fileName.trim().ifBlank { "certificate" }.take(200)
        val sanitizedFileName = safeDisplayName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(120)
        val timestamp = System.currentTimeMillis()
        val storagePath = "trainers/$trainerId/certificates/${timestamp}_$sanitizedFileName"

        var uploadedNewFile = false
        val newFileRef = storage.reference.child(storagePath)

        return try {
            requireSignedInUid()

            val docRef = db.collection("trainers").document(trainerId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                return Result.failure(
                    IllegalStateException("Please save your trainer profile information before uploading a certificate.")
                )
            }
            val oldStoragePath = existing.getString("certificateStoragePath")

            val bytes = withContext(Dispatchers.IO) {
                readBoundedBytes(contentResolver, uri, TrainerProfileValidator.MAX_FILE_SIZE_BYTES)
            } ?: return Result.failure(IllegalStateException("Could not read certificate file content."))

            val fileErr = TrainerProfileValidator.validateCertificateFile(safeMime, bytes.size.toLong())
            if (fileErr != null) return Result.failure(IllegalArgumentException(fileErr))

            val metadata = StorageMetadata.Builder()
                .setContentType(safeMime)
                .setCustomMetadata("uploadedBy", trainerId)
                .build()

            newFileRef.putBytes(bytes, metadata).await()
            uploadedNewFile = true
            val downloadUrl = newFileRef.downloadUrl.await().toString()

            val updates = mapOf<String, Any>(
                "certificateUrl" to downloadUrl,
                "certificateStoragePath" to storagePath,
                "certificateFileName" to safeDisplayName,
                "certificateMimeType" to safeMime,
                "certificateStatus" to "pending",
                "certificateReviewedBy" to FieldValue.delete(),
                "certificateReviewedAt" to FieldValue.delete(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()

            // Best-effort cleanup of the previous certificate file after replacement succeeds
            if (!oldStoragePath.isNullOrBlank() && oldStoragePath != storagePath) {
                try {
                    storage.reference.child(oldStoragePath).delete().await()
                } catch (e: Exception) {
                    Log.w("TrainerPortalRepository", "Could not delete previous certificate: $oldStoragePath", e)
                }
            }

            val updatedSnap = docRef.get().await()
            val parsed = updatedSnap.toObject(TrainerProfileDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.copy(trainerId = updatedSnap.id)
                ?: TrainerProfileDoc(
                    trainerId = trainerId,
                    name = existing.getString("name").orEmpty(),
                    email = existing.getString("email").orEmpty(),
                    phone = existing.getString("phone").orEmpty(),
                    specialization = existing.getString("specialization").orEmpty(),
                    certificateUrl = downloadUrl,
                    certificateStoragePath = storagePath,
                    certificateFileName = safeDisplayName,
                    certificateMimeType = safeMime,
                    certificateStatus = "pending"
                )
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (uploadedNewFile) {
                try {
                    newFileRef.delete().await()
                } catch (cleanupErr: Exception) {
                    Log.w("TrainerPortalRepository", "Failed to clean up orphaned certificate upload", cleanupErr)
                }
            }
            Log.e("TrainerPortalRepository", "Failed certificate upload", e)
            val friendly = handleFirestoreError(e, OperationType.UPDATE, "trainers/$trainerId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    private fun readBoundedBytes(
        contentResolver: ContentResolver,
        uri: Uri,
        maxBytes: Long
    ): ByteArray? {
        return contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) {
                    throw IllegalArgumentException("Certificate file size cannot exceed 5 MB.")
                }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    /**
     * Admin review action: Approve or Reject an uploaded certificate.
     */
    suspend fun reviewCertificate(
        trainerId: String,
        adminUid: String,
        approved: Boolean
    ): Result<Unit> {
        return try {
            requireSignedInUid()
            val docRef = db.collection("trainers").document(trainerId)
            val existing = docRef.get().await()
            if (!existing.exists()) {
                return Result.failure(IllegalStateException("Trainer profile not found."))
            }
            if (existing.getString("certificateUrl").isNullOrBlank()) {
                return Result.failure(IllegalStateException("Cannot review certificate: no certificate has been uploaded yet."))
            }

            val newStatus = if (approved) "approved" else "rejected"
            val updates = mapOf<String, Any>(
                "certificateStatus" to newStatus,
                "certificateReviewedBy" to adminUid,
                "certificateReviewedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = handleFirestoreError(e, OperationType.UPDATE, "trainers/$trainerId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    /**
     * Admin action: Create a trainer profile record for an existing user in /users/{userId} only.
     * Does NOT create a new Firebase Auth account and does NOT alter /users/{userId} role fields.
     */
    suspend fun addTrainerProfileAdmin(
        userId: String,
        name: String,
        email: String,
        phone: String,
        specialization: String
    ): Result<Unit> {
        val uidErr = TrainerProfileValidator.validateUserId(userId)
        if (uidErr != null) return Result.failure(IllegalArgumentException(uidErr))
        val nameErr = TrainerProfileValidator.validateName(name)
        if (nameErr != null) return Result.failure(IllegalArgumentException(nameErr))
        val emailErr = TrainerProfileValidator.validateEmail(email)
        if (emailErr != null) return Result.failure(IllegalArgumentException(emailErr))
        val phoneErr = TrainerProfileValidator.validatePhone(phone)
        if (phoneErr != null) return Result.failure(IllegalArgumentException(phoneErr))
        val specErr = TrainerProfileValidator.validateSpecialization(specialization)
        if (specErr != null) return Result.failure(IllegalArgumentException(specErr))

        return try {
            requireSignedInUid()
            val trimmedUid = userId.trim()

            // Verify target user exists in /users/{userId}; do not alter their user document or create an Auth account.
            val userSnap = db.collection("users").document(trimmedUid).get().await()
            if (!userSnap.exists()) {
                return Result.failure(IllegalStateException("No existing user account found for ID: $trimmedUid"))
            }

            val docRef = db.collection("trainers").document(trimmedUid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                return Result.failure(IllegalStateException("A trainer profile already exists for user ID: $trimmedUid"))
            }

            val payload = mapOf<String, Any>(
                "trainerId" to trimmedUid,
                "name" to name.trim(),
                "email" to email.trim(),
                "phone" to phone.trim(),
                "specialization" to specialization.trim(),
                "certificateStatus" to "pending",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = handleFirestoreError(e, OperationType.CREATE, "trainers/$userId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }

    /**
     * Admin action: Delete a trainer profile and best-effort remove its uploaded certificate from Storage.
     */
    suspend fun deleteTrainerProfileAdmin(trainerId: String): Result<Unit> {
        return try {
            requireSignedInUid()
            val docRef = db.collection("trainers").document(trainerId)
            val snap = docRef.get().await()
            val storagePath = snap.getString("certificateStoragePath")

            docRef.delete().await()

            if (!storagePath.isNullOrBlank()) {
                try {
                    storage.reference.child(storagePath).delete().await()
                } catch (e: Exception) {
                    Log.w("TrainerPortalRepository", "Could not delete certificate file from Storage: $storagePath", e)
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val friendly = handleFirestoreError(e, OperationType.DELETE, "trainers/$trainerId")
            Result.failure(IllegalStateException(friendly, e))
        }
    }
}
