package com.example.ui.workouts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.data.local.entity.WorkoutPlanEntity
import com.example.ui.theme.HomeWorkoutTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class WorkoutsCategoryDropdownTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samplePlans = listOf(
        WorkoutPlanEntity(
            id = "p1",
            name = "Full Body Starter",
            description = "Total body session",
            category = "Full Body",
            difficulty = "Beginner",
            estimatedDurationMinutes = 20
        ),
        WorkoutPlanEntity(
            id = "p2",
            name = "Fat-Burning HIIT Rush",
            description = "High energy cardio",
            category = "Fat-Burning",
            difficulty = "Intermediate",
            estimatedDurationMinutes = 25
        )
    )

    @Test
    fun `category dropdown opens beneath trigger, aligns left, matches difficulty trigger height, and filters`() {
        composeTestRule.setContent {
            HomeWorkoutTheme(darkTheme = false) {
                WorkoutsScreen(
                    plans = samplePlans,
                    exercises = emptyList(),
                    onSelectPlan = {},
                    onSelectExercise = {},
                    onCreateCustomPlan = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        val catTrigger = composeTestRule.onNodeWithTag("category_filter_dropdown")
        val diffTrigger = composeTestRule.onNodeWithTag("difficulty_filter_dropdown")

        val catBounds = catTrigger.getUnclippedBoundsInRoot()
        val diffBounds = diffTrigger.getUnclippedBoundsInRoot()

        // Trigger button geometry matches Difficulty dropdown
        assertEquals(diffBounds.height.value, catBounds.height.value, 1.0f)
        assertEquals(diffBounds.width.value, catBounds.width.value, 2.0f)

        val catNode = catTrigger.fetchSemanticsNode()
        val catPos = catNode.positionOnScreen
        val catSize = catNode.size

        // Open Category dropdown
        catTrigger.performClick()
        composeTestRule.waitForIdle()

        val menuSemantics = composeTestRule.onNodeWithTag("category_filter_menu")
        menuSemantics.assertIsDisplayed()

        // DropdownMenu renders inside a Popup window, so compare positionOnScreen across windows
        val headerNode = composeTestRule.onNodeWithText("Workout Programs").fetchSemanticsNode()
        val menuNode = menuSemantics.fetchSemanticsNode()
        val headerBottomPx = headerNode.positionOnScreen.y + headerNode.size.height
        val catBottomPx = catPos.y + catSize.height
        val menuTopPx = menuNode.positionOnScreen.y

        assertTrue(
            "Category menu top ($menuTopPx px) must be below header bottom ($headerBottomPx px)",
            menuTopPx >= headerBottomPx
        )
        assertTrue(
            "Category menu top ($menuTopPx px) must be at or below trigger bottom ($catBottomPx px)",
            menuTopPx >= catBottomPx - 4f
        )
        assertTrue(
            "Category menu left (${menuNode.positionOnScreen.x} px) must align with trigger left (${catPos.x} px)",
            abs(menuNode.positionOnScreen.x - catPos.x) <= 6f
        )
        assertTrue(
            "Category menu width (${menuNode.size.width} px) should match or fit >= trigger width (${catSize.width} px)",
            menuNode.size.width >= catSize.width - 4
        )

        // Verify scrolling/selecting a lower option filters plans correctly
        composeTestRule.onNodeWithTag("category_filter_Fat-Burning")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("plan_card_p2").assertIsDisplayed()
    }
}
