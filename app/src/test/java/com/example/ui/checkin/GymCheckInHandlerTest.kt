package com.example.ui.checkin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GymCheckInHandlerTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testParseQr_invalidPrefix_returnsFriendlyError() = runBlocking {
        val result = GymCheckInHandler.processCheckIn(context, "INVALID:v2:20261027:PKogy4RIHXmsirQTvcQSoZ")
        assertEquals("This isn't a BodyGrind check-in code.", result)
    }

    @Test
    fun testParseQr_v1Format_returnsFriendlyError() = runBlocking {
        val result = GymCheckInHandler.processCheckIn(context, "BG-CHECKIN:v1:20261027:PKogy4RIHXmsirQTvcQSoZ")
        assertEquals("This isn't a BodyGrind check-in code.", result)
    }

    @Test
    fun testParseQr_notEnoughParts_returnsFriendlyError() = runBlocking {
        val result = GymCheckInHandler.processCheckIn(context, "BG-CHECKIN:v2:20261027")
        assertEquals("This isn't a BodyGrind check-in code.", result)
    }

    @Test
    fun testParseQr_tooManyParts_returnsFriendlyError() = runBlocking {
        val result = GymCheckInHandler.processCheckIn(context, "BG-CHECKIN:v2:20261027:PKogy4RIHXmsirQTvcQSoZ:extra")
        assertEquals("This isn't a BodyGrind check-in code.", result)
    }

    @Test
    fun testParseQr_randomUrl_returnsFriendlyError() = runBlocking {
        val result = GymCheckInHandler.processCheckIn(context, "https://bodygrind.com/checkin")
        assertEquals("This isn't a BodyGrind check-in code.", result)
    }

    @Test
    fun testCheckIn_notSignedIn_returnsPleaseSignIn() = runBlocking {
        // Without active Firebase Auth user, valid QR prompts to sign in
        val result = GymCheckInHandler.processCheckIn(context, "BG-CHECKIN:v2:20261027:PKogy4RIHXmsirQTvcQSoZ")
        assertEquals("Please sign in to check in.", result)
    }
}
