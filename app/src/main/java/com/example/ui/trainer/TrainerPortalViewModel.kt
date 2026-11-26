package com.example.ui.trainer

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.coaching.CoachingMappers
import com.example.data.coaching.CoachingRepository
import com.example.data.coaching.CoachingSessionPrefs
import com.example.data.coaching.FirestoreProvider
import com.example.data.coaching.UserAccountDoc
import com.example.data.coaching.toFriendlyFirestoreMessage
import com.example.data.trainer.AppRole
import com.example.data.trainer.CertificateStatus
import com.example.data.trainer.TrainerPortalRepository
import com.example.data.trainer.TrainerProfileDoc
import com.example.data.trainer.TrainerProfileValidator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TrainerPortalUiState(
    val currentTrainer: TrainerProfileDoc? = null,
    val allTrainers: List<TrainerProfileDoc> = emptyList(),
    val allMembers: List<UserAccountDoc> = emptyList(),
    val isAdmin: Boolean = false,
    val isTrainer: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isUploading: Boolean = false,
    val filterStatus: CertificateStatus? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val hasPortalAccess: Boolean
        get() = isAdmin || isTrainer

    val filteredTrainers: List<TrainerProfileDoc>
        get() = if (filterStatus == null) allTrainers else allTrainers.filter { it.statusEnum == filterStatus }
}

class TrainerPortalViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: TrainerPortalRepository = TrainerPortalRepository(application.applicationContext),
    private val coachingRepo: CoachingRepository = CoachingRepository(application.applicationContext),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    val currentUid: String
        get() = auth.currentUser?.uid.orEmpty()

    private val _uiState = MutableStateFlow(TrainerPortalUiState(isLoading = true))
    val uiState: StateFlow<TrainerPortalUiState> = _uiState.asStateFlow()

    init {
        loadUserRoleAndStartObserving()
    }

    fun loadUserRoleAndStartObserving() {
        val uid = currentUid
        if (uid.isBlank()) {
            _uiState.update { it.copy(isLoading = false, isTrainer = false, isAdmin = false) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            var isAdmin = false
            var isTrainer = false
            var resolvedFromServer = false

            try {
                val db = FirestoreProvider.getDb(appContext)
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    val parsed = CoachingMappers.parseUserAccount(userDoc)
                    if (parsed != null) {
                        val rawRole = parsed.role?.trim()?.lowercase()
                        isAdmin = rawRole == "admin" || parsed.accountType == AppRole.ADMIN
                        isTrainer = rawRole == "trainer" || parsed.accountType == AppRole.TRAINER
                        resolvedFromServer = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("TrainerPortalVM", "Failed reading user account for role check", e)
            }

            if (!resolvedFromServer) {
                val cachedRole = CoachingSessionPrefs.getSavedRole(appContext, uid)
                isAdmin = cachedRole == AppRole.ADMIN
                isTrainer = cachedRole == AppRole.TRAINER
            }

            val hasAccess = isAdmin || isTrainer
            _uiState.update {
                it.copy(
                    isAdmin = isAdmin,
                    isTrainer = isTrainer,
                    isLoading = hasAccess
                )
            }

            if (!hasAccess) {
                return@launch
            }

            // Observe current user's trainer profile
            launch {
                try {
                    repository.observeTrainerProfile(uid).collect { result ->
                        result.fold(
                            onSuccess = { profile ->
                                _uiState.update { state ->
                                    state.copy(
                                        currentTrainer = profile,
                                        isLoading = false
                                    )
                                }
                            },
                            onFailure = { err ->
                                _uiState.update { state ->
                                    state.copy(
                                        isLoading = false,
                                        errorMessage = err.localizedMessage ?: "Failed to load profile."
                                    )
                                }
                            }
                        )
                    }
                } catch (e: CancellationException) {
                    if (!coroutineContext.isActive) {
                        throw e
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = toFriendlyFirestoreMessage(e)
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = e.localizedMessage ?: "Failed to load profile."
                        )
                    }
                }
            }

            // If admin, observe all trainer profiles and all members
            if (isAdmin) {
                launch {
                    repository.observeAllTrainers().collect { result ->
                        result.fold(
                            onSuccess = { trainers ->
                                _uiState.update { state ->
                                    state.copy(
                                        allTrainers = trainers,
                                        isLoading = false
                                    )
                                }
                            },
                            onFailure = { err ->
                                _uiState.update { state ->
                                    state.copy(
                                        isLoading = false,
                                        errorMessage = err.localizedMessage ?: "Failed to load trainers."
                                    )
                                }
                            }
                        )
                    }
                }
                launch {
                    coachingRepo.observeAllMembersForAdmin().collect { result ->
                        result.onSuccess { members ->
                            _uiState.update { state ->
                                state.copy(
                                    allMembers = members,
                                    isLoading = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateMemberTypeAndDiscountAdmin(
        targetUserId: String,
        memberType: String,
        isVerified: Boolean,
        discountPercent: Double,
        discountAppliesToBilling: Boolean,
        discountAppliesToPackages: Boolean,
        discountAppliesToSessionFees: Boolean,
        onComplete: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (!_uiState.value.isAdmin) {
            onComplete(false, "Only admins can modify member types and discounts.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val adminUid = currentUid
            val result = coachingRepo.updateMemberTypeAndDiscountAdmin(
                targetUserId = targetUserId,
                memberType = memberType,
                isVerified = isVerified,
                adminUid = adminUid,
                discountPercent = discountPercent,
                discountAppliesToBilling = discountAppliesToBilling,
                discountAppliesToPackages = discountAppliesToPackages,
                discountAppliesToSessionFees = discountAppliesToSessionFees
            )
            _uiState.update { it.copy(isSaving = false) }
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "Member updated successfully.") }
                    onComplete(true, null)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to update member."
                    _uiState.update { it.copy(errorMessage = msg) }
                    onComplete(false, msg)
                }
            )
        }
    }

    fun setFilterStatus(status: CertificateStatus?) {
        _uiState.update { it.copy(filterStatus = status) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    fun saveProfile(
        name: String,
        phone: String,
        onSuccess: () -> Unit = {}
    ) {
        val uid = currentUid
        if (uid.isBlank() || !_uiState.value.hasPortalAccess) {
            _uiState.update { it.copy(errorMessage = "You do not have permission to edit this profile.") }
            return
        }

        val validationErr = TrainerProfileValidator.validateName(name)
            ?: TrainerProfileValidator.validatePhone(phone)
        if (validationErr != null) {
            _uiState.update { it.copy(errorMessage = validationErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.saveTrainerProfile(
                trainerId = uid,
                name = name,
                phone = phone
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "Profile updated successfully."
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to save profile."
                        )
                    }
                }
            )
        }
    }

    fun uploadCertificate(
        uri: Uri,
        fileName: String,
        mimeType: String,
        reportedSizeBytes: Long?,
        contentResolver: ContentResolver
    ) {
        val uid = currentUid
        if (uid.isBlank() || !_uiState.value.hasPortalAccess) {
            _uiState.update { it.copy(errorMessage = "You do not have permission to upload a certificate.") }
            return
        }

        val preCheckErr = TrainerProfileValidator.validateCertificateFile(mimeType, reportedSizeBytes)
        if (preCheckErr != null) {
            _uiState.update { it.copy(errorMessage = preCheckErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isUploading = true, errorMessage = null) }
            val result = repository.uploadCertificate(
                trainerId = uid,
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                contentResolver = contentResolver,
                reportedSizeBytes = reportedSizeBytes
            )
            result.fold(
                onSuccess = { updatedDoc ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            currentTrainer = updatedDoc,
                            successMessage = "Certificate uploaded. Status set to Pending Review."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            errorMessage = err.localizedMessage ?: "Failed to upload certificate."
                        )
                    }
                }
            )
        }
    }

    // --- Admin Actions ---

    fun adminApproveCertificate(trainerId: String) {
        if (!_uiState.value.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Admin privileges required.") }
            return
        }
        val adminUid = currentUid
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.reviewCertificate(trainerId, adminUid, approved = true)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, successMessage = "Certificate approved.") }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to approve certificate."
                        )
                    }
                }
            )
        }
    }

    fun adminRejectCertificate(trainerId: String) {
        if (!_uiState.value.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Admin privileges required.") }
            return
        }
        val adminUid = currentUid
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.reviewCertificate(trainerId, adminUid, approved = false)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, successMessage = "Certificate rejected.") }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to reject certificate."
                        )
                    }
                }
            )
        }
    }

    fun adminAddTrainer(
        userId: String,
        name: String,
        email: String,
        phone: String,
        specialization: String,
        onSuccess: () -> Unit
    ) {
        if (!_uiState.value.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Admin privileges required.") }
            return
        }

        val validationErr = TrainerProfileValidator.validateUserId(userId)
            ?: TrainerProfileValidator.validateName(name)
            ?: TrainerProfileValidator.validateEmail(email)
            ?: TrainerProfileValidator.validatePhone(phone)
            ?: TrainerProfileValidator.validateSpecialization(specialization)
        if (validationErr != null) {
            _uiState.update { it.copy(errorMessage = validationErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.addTrainerProfileAdmin(
                userId = userId,
                name = name,
                email = email,
                phone = phone,
                specialization = specialization
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "Trainer profile added for user: ${userId.trim()}"
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to add trainer profile."
                        )
                    }
                }
            )
        }
    }

    fun adminUpdateTrainer(
        trainerId: String,
        name: String,
        email: String,
        phone: String,
        specialization: String,
        onSuccess: () -> Unit
    ) {
        if (!_uiState.value.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Admin privileges required.") }
            return
        }

        val validationErr = TrainerProfileValidator.validateUserId(trainerId)
            ?: TrainerProfileValidator.validateName(name)
            ?: TrainerProfileValidator.validateEmail(email)
            ?: TrainerProfileValidator.validatePhone(phone)
            ?: TrainerProfileValidator.validateSpecialization(specialization)
        if (validationErr != null) {
            _uiState.update { it.copy(errorMessage = validationErr) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.adminUpdateTrainerProfile(
                trainerId = trainerId,
                name = name,
                email = email,
                phone = phone,
                specialization = specialization
            )
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "Trainer profile updated."
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to update trainer profile."
                        )
                    }
                }
            )
        }
    }

    fun adminDeleteTrainer(trainerId: String) {
        if (!_uiState.value.isAdmin) {
            _uiState.update { it.copy(errorMessage = "Admin privileges required.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val result = repository.deleteTrainerProfileAdmin(trainerId)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = "Trainer profile removed."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Failed to delete trainer profile."
                        )
                    }
                }
            )
        }
    }
}
