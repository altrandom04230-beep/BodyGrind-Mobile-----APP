package com.example.ui.activeworkout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SquatRepCounterTest {

    @Test
    fun `CameraTrackedExercises supports only squats with REPS type`() {
        assertTrue(CameraTrackedExercises.isSupported("squats", "REPS"))
        assertFalse(CameraTrackedExercises.isSupported("squats", "TIME"))
        assertFalse(CameraTrackedExercises.isSupported("push_ups", "REPS"))
        assertFalse(CameraTrackedExercises.isSupported("lunges", "REPS"))
    }

    @Test
    fun `calculateAngleDegrees computes straight and bent knee angles`() {
        val straight = SquatRepCounter.calculateAngleDegrees(
            firstX = 100f, firstY = 100f,
            midX = 100f, midY = 200f,
            lastX = 100f, lastY = 300f
        )
        assertEquals(180f, straight, 0.5f)

        val rightAngle = SquatRepCounter.calculateAngleDegrees(
            firstX = 0f, firstY = 200f,
            midX = 100f, midY = 200f,
            lastX = 100f, lastY = 300f
        )
        assertEquals(90f, rightAngle, 0.5f)
    }

    @Test
    fun `counts one rep only after down below 100 then up above 160`() {
        val counter = SquatRepCounter()

        // Standing upright: no rep
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 175f))

        // Partial squat (above 100): does not enter down state or count
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 120f))
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 172f))

        // Full squat down (< 100): enters down state, does not count yet
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 92f))
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 88f))
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 140f))

        // Return above 160: counts 1 rep on the up
        assertTrue(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 165f))

        // Staying upright does not double count
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 172f))
    }

    @Test
    fun `paused or not tracking does not change down state or count rep`() {
        val counter = SquatRepCounter()

        // Enter down state
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 90f))

        // While paused, standing up does not count or clear down state
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 175f, paused = true))

        // While not tracking, standing up does not count or clear down state
        assertFalse(counter.processMeasurements(isTracking = false, kneeAngleDegrees = 175f, paused = false))

        // Once tracking and unpaused again, going above 160 completes the rep
        assertTrue(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 170f, paused = false))
    }

    @Test
    fun `reset clears down state`() {
        val counter = SquatRepCounter()

        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 85f))
        counter.reset()
        assertFalse(counter.processMeasurements(isTracking = true, kneeAngleDegrees = 175f))
    }
}
