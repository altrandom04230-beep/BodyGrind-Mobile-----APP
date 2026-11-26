package com.example

import com.example.data.coaching.MemberType
import com.example.data.coaching.UserAccountDoc
import com.example.data.local.entity.UserProfileEntity
import com.example.utils.ManilaDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MemberTypeAndDiscountTest {

    private val fixedToday = LocalDate.of(2026, 3, 5)

    @Test
    fun calculateAge_exactBirthdayCalculations() {
        // Born exactly 30 years ago today
        assertEquals(30, ManilaDateTime.calculateAge("1996-03-05", fixedToday))

        // Birthday is tomorrow -> 29 years old
        assertEquals(29, ManilaDateTime.calculateAge("1996-03-06", fixedToday))

        // Birthday was yesterday -> 30 years old
        assertEquals(30, ManilaDateTime.calculateAge("1996-03-04", fixedToday))

        // Senior Citizen exact 60th birthday today
        assertEquals(60, ManilaDateTime.calculateAge("1966-03-05", fixedToday))

        // Senior Citizen turning 60 tomorrow -> currently 59
        assertEquals(59, ManilaDateTime.calculateAge("1966-03-06", fixedToday))

        // Invalid or null returns default 25
        assertEquals(25, ManilaDateTime.calculateAge(null, fixedToday))
        assertEquals(25, ManilaDateTime.calculateAge("invalid-date", fixedToday))
    }

    @Test
    fun formatBirthdate_zeroPaddedCanonicalFormat() {
        assertEquals("Mar 05, 1996", ManilaDateTime.formatBirthdate("1996-03-05"))
        assertEquals("Jan 01, 1966", ManilaDateTime.formatBirthdate("1966-01-01"))
        assertEquals("Nov 22, 2000", ManilaDateTime.formatBirthdate("2000-11-22"))
    }

    @Test
    fun isValidBirthdate_checksRange10To120Years() {
        assertTrue(ManilaDateTime.isValidBirthdate("1996-03-05", fixedToday)) // Age 30
        assertTrue(ManilaDateTime.isValidBirthdate("1966-03-05", fixedToday)) // Age 60
        assertTrue(ManilaDateTime.isValidBirthdate("2016-03-05", fixedToday)) // Age 10

        assertFalse(ManilaDateTime.isValidBirthdate("2020-03-05", fixedToday)) // Age 6 (under 10)
        assertFalse(ManilaDateTime.isValidBirthdate("1890-03-05", fixedToday)) // Age 136 (over 120)
        assertFalse(ManilaDateTime.isValidBirthdate("not-a-date", fixedToday))
        assertFalse(ManilaDateTime.isValidBirthdate("", fixedToday))
    }

    @Test
    fun userProfileEntity_calculateAge_and_SeniorEligibility() {
        val youngProfile = UserProfileEntity(
            id = 1,
            name = "John Doe",
            birthdate = "1996-03-05",
            age = 26 // legacy field
        )
        // Age is derived dynamically from birthdate
        assertEquals(30, youngProfile.calculateAge(fixedToday))
        assertFalse(youngProfile.isSeniorCitizenEligible(fixedToday))

        val seniorProfile = UserProfileEntity(
            id = 2,
            name = "Jane Senior",
            birthdate = "1966-03-05",
            age = 26
        )
        assertEquals(60, seniorProfile.calculateAge(fixedToday))
        assertTrue(seniorProfile.isSeniorCitizenEligible(fixedToday))

        // User without birthdate falls back safely to legacy manual age
        val legacyProfile = UserProfileEntity(
            id = 3,
            name = "Legacy User",
            birthdate = null,
            age = 45
        )
        assertEquals(45, legacyProfile.calculateAge(fixedToday))
        assertFalse(legacyProfile.hasBirthdate())
    }

    @Test
    fun userProfileEntity_dailyCalorieTarget_usesCalculatedAge() {
        // Mifflin-St Jeor formula verification:
        // Male, 80kg, 180cm, born 1996-03-05 (age 30 in 2026), Maintain Weight (multiplier 1.35)
        // BMR = (10 * 80) + (6.25 * 180) - (5 * 30) + 5 = 800 + 1125 - 150 + 5 = 1780
        // Target = (1780 * 1.35).toInt() = 2403
        val profile = UserProfileEntity(
            id = 1,
            name = "Calorie Tester",
            birthdate = "1996-03-05",
            weightKg = 80f,
            heightCm = 180f,
            gender = "Male",
            fitnessGoal = "Maintain Weight"
        )
        assertEquals(2403, profile.calculateDailyCalorieTarget(fixedToday))
    }

    @Test
    fun memberTypes_and_seniorReviewFlagging() {
        val seniorMemberDoc = UserAccountDoc(
            userId = "senior123",
            name = "Robert Senior",
            email = "robert@example.com",
            birthdate = "1960-01-01",
            calculatedAge = 66,
            memberType = "Regular", // Still regular -> needs review
            isVerified = false
        )
        // Age 66 should trigger senior review needed
        assertTrue(seniorMemberDoc.isSeniorEligible())
        assertTrue(seniorMemberDoc.isDiscountedType == false) // Regular is not discounted type

        // When admin approves Senior Citizen type
        val verifiedSenior = seniorMemberDoc.copy(
            memberType = MemberType.SENIOR_CITIZEN.displayName,
            isVerified = true,
            discountPercent = 20.0,
            discountAppliesToBilling = true,
            discountAppliesToPackages = true
        )
        assertEquals("Senior Citizen", verifiedSenior.memberType)
        assertTrue(verifiedSenior.isVerified)
        assertTrue(verifiedSenior.isDiscountedType)
        assertEquals(20.0, verifiedSenior.discountPercent, 0.001)
        assertTrue(verifiedSenior.discountAppliesToBilling)
        assertTrue(verifiedSenior.discountAppliesToPackages)
        assertFalse(verifiedSenior.discountAppliesToSessionFees)
    }

    @Test
    fun memberTypeValues_exactlyFourAllowed() {
        val allowedTypes = MemberType.entries.map { it.displayName }
        assertEquals(4, allowedTypes.size)
        assertTrue(allowedTypes.contains("Regular"))
        assertTrue(allowedTypes.contains("Student"))
        assertTrue(allowedTypes.contains("Senior Citizen"))
        assertTrue(allowedTypes.contains("PWD"))
    }
}
