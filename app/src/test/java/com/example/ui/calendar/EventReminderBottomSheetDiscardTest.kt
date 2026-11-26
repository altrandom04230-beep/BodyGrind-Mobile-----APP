package com.example.ui.calendar

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.local.entity.WorkoutPlanEntity
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EventReminderBottomSheetDiscardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val samplePlans = listOf(
        WorkoutPlanEntity(
            id = "plan_1",
            name = "Push Day",
            description = "Chest, Shoulders & Triceps",
            category = "Strength",
            difficulty = "Intermediate",
            estimatedDurationMinutes = 60
        ),
        WorkoutPlanEntity(
            id = "plan_2",
            name = "Pull Day",
            description = "Back & Biceps",
            category = "Strength",
            difficulty = "Intermediate",
            estimatedDurationMinutes = 60
        )
    )

    @Test
    fun testProperties() {
        val props = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
        assertFalse(props.shouldDismissOnBackPress)
    }

    @Test
    fun noUnsavedChanges_clickingCancelCallsOnDismissImmediatelyWithoutPrompt() {
        var dismissed = false

        composeTestRule.setContent {
            EventReminderBottomSheet(
                initialDate = LocalDate.of(2026, 4, 15),
                editingEvent = null,
                allPlans = samplePlans,
                onDismiss = { dismissed = true },
                onSaveNew = {},
                onUpdateAll = {},
                onEditThisDayOnly = {},
                onDeleteAll = {},
                onDeleteThisDayOnly = {}
            )
        }

        // Cancel button click when nothing changed
        composeTestRule.onNodeWithText("Cancel").performClick()

        assertTrue("onDismiss should be called immediately when no changes were made", dismissed)
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun withUnsavedChanges_clickingCancelShowsDiscardChangesDialogAndKeepEditingPreservesSheet() {
        var dismissed = false

        composeTestRule.setContent {
            EventReminderBottomSheet(
                initialDate = LocalDate.of(2026, 4, 15),
                editingEvent = null,
                allPlans = samplePlans,
                onDismiss = { dismissed = true },
                onSaveNew = {},
                onUpdateAll = {},
                onEditThisDayOnly = {},
                onDeleteAll = {},
                onDeleteThisDayOnly = {}
            )
        }

        // Make an unsaved change: switch event type to Rest day
        composeTestRule.onNodeWithText("Rest day").performClick()

        // Click Cancel button
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Verify Discard changes dialog appears
        assertFalse("onDismiss must NOT be called when unsaved changes exist", dismissed)
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("You have unsaved changes. Are you sure you want to discard them?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()

        // Tap Keep editing
        composeTestRule.onNodeWithText("Keep editing").performClick()

        // Verify dialog is gone
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertFalse("onDismiss must still NOT be called after Keep editing", dismissed)

        // Verify the bottom sheet is still open, visible, and fully interactive
        composeTestRule.onNodeWithText("Save event").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout plan").assertIsDisplayed()

        // Verify user can continue interacting: switch to Custom note
        composeTestRule.onNodeWithText("Custom note").performClick()
        composeTestRule.onNodeWithTag("custom_event_title_input").assertIsDisplayed()
    }

    @Test
    fun withUnsavedChanges_clickingCloseIconShowsPromptAndDiscardConfirmsDismissal() {
        var dismissed = false

        composeTestRule.setContent {
            EventReminderBottomSheet(
                initialDate = LocalDate.of(2026, 4, 15),
                editingEvent = null,
                allPlans = samplePlans,
                onDismiss = { dismissed = true },
                onSaveNew = {},
                onUpdateAll = {},
                onEditThisDayOnly = {},
                onDeleteAll = {},
                onDeleteThisDayOnly = {}
            )
        }

        // Switch to Custom note and enter text
        composeTestRule.onNodeWithText("Custom note").performClick()
        composeTestRule.onNodeWithTag("custom_event_title_input").performTextInput("Morning Cardio")

        // Click the close 'X' button
        composeTestRule.onNodeWithContentDescription("Close").performClick()

        // Verify prompt is shown
        assertFalse("onDismiss must NOT be called yet", dismissed)
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()

        // Click Discard
        composeTestRule.onNodeWithText("Discard").performClick()

        // Verify onDismiss is now called
        assertTrue("onDismiss should be called after clicking Discard", dismissed)
    }

    @Test
    fun noUnsavedChanges_backPressCallsOnDismissImmediatelyWithoutPrompt() {
        var dismissed = false

        composeTestRule.setContent {
            EventReminderBottomSheet(
                initialDate = LocalDate.of(2026, 4, 15),
                editingEvent = null,
                allPlans = samplePlans,
                onDismiss = { dismissed = true },
                onSaveNew = {},
                onUpdateAll = {},
                onEditThisDayOnly = {},
                onDeleteAll = {},
                onDeleteThisDayOnly = {}
            )
        }

        // Send back press with no changes via activity back dispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        assertTrue("onDismiss should be called immediately when no changes were made", dismissed)
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun withUnsavedChanges_backPressShowsDiscardPromptAndKeepEditingPreservesSheet() {
        var dismissed = false

        composeTestRule.setContent {
            EventReminderBottomSheet(
                initialDate = LocalDate.of(2026, 4, 15),
                editingEvent = null,
                allPlans = samplePlans,
                onDismiss = { dismissed = true },
                onSaveNew = {},
                onUpdateAll = {},
                onEditThisDayOnly = {},
                onDeleteAll = {},
                onDeleteThisDayOnly = {}
            )
        }

        // Make an unsaved change: switch event type to Rest day
        composeTestRule.onNodeWithText("Rest day").performClick()

        // Send back press via activity back dispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        // Verify Discard changes dialog appears
        assertFalse("onDismiss must NOT be called when unsaved changes exist", dismissed)
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()
        composeTestRule.onNodeWithText("You have unsaved changes. Are you sure you want to discard them?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep editing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Discard").assertIsDisplayed()

        // Tap Keep editing
        composeTestRule.onNodeWithText("Keep editing").performClick()

        // Verify dialog is gone
        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertFalse("onDismiss must still NOT be called after Keep editing", dismissed)

        // Verify sheet content is still displayed and clickable
        composeTestRule.onNodeWithText("Save event").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Workout plan").assertIsDisplayed()

        // Verify user can continue interacting: switch to Custom note
        composeTestRule.onNodeWithText("Custom note").performClick()
        composeTestRule.onNodeWithTag("custom_event_title_input").assertIsDisplayed()
    }

    @Test
    fun withUnsavedChanges_backPressFollowedByDiscardConfirmsDismissal() {
        var dismissed = false

        composeTestRule.setContent {
            EventReminderBottomSheet(
                initialDate = LocalDate.of(2026, 4, 15),
                editingEvent = null,
                allPlans = samplePlans,
                onDismiss = { dismissed = true },
                onSaveNew = {},
                onUpdateAll = {},
                onEditThisDayOnly = {},
                onDeleteAll = {},
                onDeleteThisDayOnly = {}
            )
        }

        // Change note
        composeTestRule.onNodeWithTag("event_note_input").performTextInput("Test note")

        // Send back press via activity back dispatcher
        composeTestRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        // Verify dialog is shown
        composeTestRule.onNodeWithText("Discard changes?").assertIsDisplayed()

        // Click Discard
        composeTestRule.onNodeWithText("Discard").performClick()

        assertTrue("onDismiss should be called after clicking Discard", dismissed)
    }
}
