package com.example.data.trainer

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CertificateStatus(val rawValue: String, val displayLabel: String) {
    PENDING("pending", "Pending Review"),
    APPROVED("approved", "Approved"),
    REJECTED("rejected", "Rejected");

    companion object {
        fun fromRaw(raw: String?): CertificateStatus {
            val normalized = raw?.trim()?.lowercase() ?: return PENDING
            return entries.firstOrNull { it.rawValue == normalized || it.name.lowercase() == normalized }
                ?: PENDING
        }
    }
}

@Keep
@IgnoreExtraProperties
data class TrainerProfileDoc(
    val trainerId: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val specialization: String = "",
    val certificateUrl: String? = null,
    val certificateStoragePath: String? = null,
    val certificateFileName: String? = null,
    val certificateMimeType: String? = null,
    val certificateStatus: String = "pending",
    val certificateReviewedBy: String? = null,
    @ServerTimestamp val certificateReviewedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val statusEnum: CertificateStatus
        get() = CertificateStatus.fromRaw(certificateStatus)

    val hasCertificate: Boolean
        get() = !certificateUrl.isNullOrBlank()
}

object TrainerProfileValidator {
    val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    val PHONE_REGEX = Regex("^[0-9]{7,15}$")
    val USER_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")

    const val MAX_NAME_LENGTH = 100
    const val MAX_EMAIL_LENGTH = 160
    const val MAX_SPEC_LENGTH = 200
    const val MAX_USER_ID_LENGTH = 128
    const val MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB

    val ALLOWED_MIME_TYPES = setOf("application/pdf", "image/jpeg", "image/png")

    fun normalizeMimeType(mimeType: String?): String? {
        val normalized = mimeType?.trim()?.lowercase() ?: return null
        return if (normalized == "image/jpg") "image/jpeg" else normalized
    }

    fun validateUserId(userId: String): String? {
        val trimmed = userId.trim()
        return when {
            trimmed.isEmpty() -> "User ID cannot be empty."
            trimmed.length > MAX_USER_ID_LENGTH -> "User ID is too long."
            !USER_ID_REGEX.matches(trimmed) -> "User ID contains invalid characters."
            else -> null
        }
    }

    fun validateName(name: String): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> "Name cannot be empty."
            trimmed.length > MAX_NAME_LENGTH -> "Name must be $MAX_NAME_LENGTH characters or fewer."
            else -> null
        }
    }

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> "Email cannot be empty."
            trimmed.length > MAX_EMAIL_LENGTH -> "Email must be $MAX_EMAIL_LENGTH characters or fewer."
            !EMAIL_REGEX.matches(trimmed) -> "Please enter a valid email address."
            else -> null
        }
    }

    fun validatePhone(phone: String): String? {
        val trimmed = phone.trim()
        return when {
            trimmed.isEmpty() -> "Phone number cannot be empty."
            !PHONE_REGEX.matches(trimmed) -> "Phone number must contain digits only (7-15 digits)."
            else -> null
        }
    }

    fun validateSpecialization(specialization: String): String? {
        val trimmed = specialization.trim()
        return when {
            trimmed.isEmpty() -> "Specialization cannot be empty."
            trimmed.length > MAX_SPEC_LENGTH -> "Specialization must be $MAX_SPEC_LENGTH characters or fewer."
            else -> null
        }
    }

    fun validateCertificateFile(mimeType: String?, sizeBytes: Long?): String? {
        val normalized = normalizeMimeType(mimeType)
        if (normalized == null || !ALLOWED_MIME_TYPES.contains(normalized)) {
            return "Certificate must be a PDF, JPG, or PNG file."
        }
        if (sizeBytes != null && sizeBytes <= 0L) {
            return "Selected certificate file is empty."
        }
        if (sizeBytes != null && sizeBytes > MAX_FILE_SIZE_BYTES) {
            return "Certificate file size cannot exceed 5 MB."
        }
        return null
    }
}
