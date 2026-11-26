package com.example.ui.trainer

import android.app.Application
import com.example.data.trainer.CertificateStatus
import com.example.data.trainer.TrainerProfileDoc
import com.example.data.trainer.TrainerProfileValidator
import org.junit.Assert.*
import org.junit.Test

class TrainerProfileValidationTest {

    @Test
    fun testViewModelHasApplicationConstructorForFactory() {
        val ctor = TrainerPortalViewModel::class.java.getConstructor(Application::class.java)
        assertNotNull(ctor)
    }

    @Test
    fun testNameValidation() {
        assertNotNull(TrainerProfileValidator.validateName(""))
        assertNotNull(TrainerProfileValidator.validateName("   "))
        assertNotNull(TrainerProfileValidator.validateName("A".repeat(101)))
        assertNull(TrainerProfileValidator.validateName("A".repeat(100)))
        assertNull(TrainerProfileValidator.validateName("Coach Rico Santos"))
        assertNull(TrainerProfileValidator.validateName("A"))
    }

    @Test
    fun testUserIdValidation() {
        assertNotNull(TrainerProfileValidator.validateUserId(""))
        assertNotNull(TrainerProfileValidator.validateUserId("   "))
        assertNotNull(TrainerProfileValidator.validateUserId("user/with/slash"))
        assertNotNull(TrainerProfileValidator.validateUserId("user with space"))
        assertNotNull(TrainerProfileValidator.validateUserId("a".repeat(129)))
        assertNull(TrainerProfileValidator.validateUserId("trainer_cobie_01"))
        assertNull(TrainerProfileValidator.validateUserId("uid-12345_ABC"))
    }

    @Test
    fun testEmailValidation() {
        assertNotNull(TrainerProfileValidator.validateEmail(""))
        assertNotNull(TrainerProfileValidator.validateEmail("not-an-email"))
        assertNotNull(TrainerProfileValidator.validateEmail("user@domain"))
        assertNotNull(TrainerProfileValidator.validateEmail("user@.com"))
        assertNotNull(TrainerProfileValidator.validateEmail("${"a".repeat(155)}@b.com"))
        assertNull(TrainerProfileValidator.validateEmail("rico.santos@bodygrind.com"))
        assertNull(TrainerProfileValidator.validateEmail("trainer_01@fit.ph"))
    }

    @Test
    fun testPhoneValidationDigitsOnly() {
        assertNotNull(TrainerProfileValidator.validatePhone(""))
        assertNotNull(TrainerProfileValidator.validatePhone("12345")) // too short (< 7)
        assertNotNull(TrainerProfileValidator.validatePhone("1234567890123456")) // too long (> 15)
        assertNotNull(TrainerProfileValidator.validatePhone("0917-123-4567")) // dashes not allowed
        assertNotNull(TrainerProfileValidator.validatePhone("0917 123 4567")) // spaces not allowed
        assertNotNull(TrainerProfileValidator.validatePhone("phone1234567")) // letters not allowed
        assertNotNull(TrainerProfileValidator.validatePhone("+639171234567")) // '+' not allowed (digits only)
        assertNull(TrainerProfileValidator.validatePhone("09171234567"))
        assertNull(TrainerProfileValidator.validatePhone("639171234567"))
        assertNull(TrainerProfileValidator.validatePhone("1234567"))
    }

    @Test
    fun testSpecializationValidation() {
        assertNotNull(TrainerProfileValidator.validateSpecialization(""))
        assertNotNull(TrainerProfileValidator.validateSpecialization("A".repeat(201)))
        assertNull(TrainerProfileValidator.validateSpecialization("A".repeat(200)))
        assertNull(TrainerProfileValidator.validateSpecialization("Strength, Conditioning & Hypertrophy"))
    }

    @Test
    fun testCertificateFileValidation() {
        // Disallowed MIME
        assertNotNull(TrainerProfileValidator.validateCertificateFile("text/plain", 1024L))
        assertNotNull(TrainerProfileValidator.validateCertificateFile("application/zip", 1024L))
        assertNotNull(TrainerProfileValidator.validateCertificateFile(null, 1024L))

        // Empty or exceeded size (> 5 MB)
        assertNotNull(TrainerProfileValidator.validateCertificateFile("application/pdf", 0L))
        val exactLimit = 5 * 1024 * 1024L
        val overLimit = exactLimit + 1L
        assertNull(TrainerProfileValidator.validateCertificateFile("application/pdf", exactLimit))
        assertNotNull(TrainerProfileValidator.validateCertificateFile("application/pdf", overLimit))

        // Valid files including normalized image/jpg
        assertEquals("image/jpeg", TrainerProfileValidator.normalizeMimeType("image/jpg"))
        assertNull(TrainerProfileValidator.validateCertificateFile("image/jpg", 500 * 1024L))
        assertNull(TrainerProfileValidator.validateCertificateFile("application/pdf", 2 * 1024 * 1024L))
        assertNull(TrainerProfileValidator.validateCertificateFile("image/jpeg", 500 * 1024L))
        assertNull(TrainerProfileValidator.validateCertificateFile("image/png", 1024 * 1024L))
    }

    @Test
    fun testCertificateStatusParsingAndHasCertificate() {
        assertEquals(CertificateStatus.PENDING, CertificateStatus.fromRaw(null))
        assertEquals(CertificateStatus.PENDING, CertificateStatus.fromRaw(""))
        assertEquals(CertificateStatus.PENDING, CertificateStatus.fromRaw("pending"))
        assertEquals(CertificateStatus.APPROVED, CertificateStatus.fromRaw("approved"))
        assertEquals(CertificateStatus.REJECTED, CertificateStatus.fromRaw("rejected"))
        assertEquals(CertificateStatus.APPROVED, CertificateStatus.fromRaw("APPROVED"))
        assertEquals(CertificateStatus.PENDING, CertificateStatus.fromRaw("unknown_status"))

        assertFalse(TrainerProfileDoc(trainerId = "t1", certificateUrl = null).hasCertificate)
        assertFalse(TrainerProfileDoc(trainerId = "t1", certificateUrl = "   ").hasCertificate)
        assertTrue(TrainerProfileDoc(trainerId = "t1", certificateUrl = "https://example.com/cert.pdf").hasCertificate)
    }

    @Test
    fun testUiStateRoleAccessAndFilterTrainers() {
        assertFalse(TrainerPortalUiState(isAdmin = false, isTrainer = false).hasPortalAccess)
        assertTrue(TrainerPortalUiState(isAdmin = true, isTrainer = false).hasPortalAccess)
        assertTrue(TrainerPortalUiState(isAdmin = false, isTrainer = true).hasPortalAccess)

        val t1 = TrainerProfileDoc(trainerId = "t1", name = "Coach A", certificateStatus = "pending")
        val t2 = TrainerProfileDoc(trainerId = "t2", name = "Coach B", certificateStatus = "approved")
        val t3 = TrainerProfileDoc(trainerId = "t3", name = "Coach C", certificateStatus = "rejected")

        val stateAll = TrainerPortalUiState(allTrainers = listOf(t1, t2, t3), filterStatus = null)
        assertEquals(3, stateAll.filteredTrainers.size)

        val statePending = TrainerPortalUiState(allTrainers = listOf(t1, t2, t3), filterStatus = CertificateStatus.PENDING)
        assertEquals(1, statePending.filteredTrainers.size)
        assertEquals("t1", statePending.filteredTrainers.first().trainerId)

        val stateApproved = TrainerPortalUiState(allTrainers = listOf(t1, t2, t3), filterStatus = CertificateStatus.APPROVED)
        assertEquals(1, stateApproved.filteredTrainers.size)
        assertEquals("t2", stateApproved.filteredTrainers.first().trainerId)

        val stateRejected = TrainerPortalUiState(allTrainers = listOf(t1, t2, t3), filterStatus = CertificateStatus.REJECTED)
        assertEquals(1, stateRejected.filteredTrainers.size)
        assertEquals("t3", stateRejected.filteredTrainers.first().trainerId)
    }
}
