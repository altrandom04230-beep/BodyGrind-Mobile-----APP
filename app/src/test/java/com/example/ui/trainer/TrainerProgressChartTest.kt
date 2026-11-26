package com.example.ui.trainer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.coaching.SharedMeasurementItem
import com.example.ui.theme.FlameOrange
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrainerProgressChartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleMeasurement(id: String, weightKg: Float, timestampMs: Long): SharedMeasurementItem {
        return SharedMeasurementItem(
            id = id,
            memberId = "member-1",
            timestampMs = timestampMs,
            weightKg = weightKg,
            heightCm = 175f,
            bmi = 22.5f,
            bmiCategory = "Normal"
        )
    }

    @Test
    fun `trend chart dots have touch targets of at least 44x44 px`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 18, 10, 0, 0) }
        val items = listOf(
            sampleMeasurement("1", 70.0f, cal.timeInMillis),
            sampleMeasurement("2", 71.5f, cal.timeInMillis + 86400000L),
            sampleMeasurement("3", 69.8f, cal.timeInMillis + 86400000L * 2)
        )

        composeTestRule.setContent {
            MaterialTheme {
                var selectedIndex by remember { mutableStateOf<Int?>(null) }
                SimpleLineTrendChart(
                    measurements = items,
                    lineColor = FlameOrange,
                    selectedDotIndex = selectedIndex,
                    onSelectDot = { selectedIndex = it },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            }
        }

        // Each dot must have touch target of at least 44dp x 44dp
        composeTestRule.onNodeWithTag("trend_chart_dot_0")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)

        composeTestRule.onNodeWithTag("trend_chart_dot_1")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)

        composeTestRule.onNodeWithTag("trend_chart_dot_2")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun `tapping a dot shows tooltip with date and weight, and tapping another dot switches tooltip`() {
        val cal = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 18, 10, 0, 0) }
        val t1 = cal.timeInMillis
        val t2 = t1 + 86400000L * 7
        val items = listOf(
            sampleMeasurement("1", 70.0f, t1),
            sampleMeasurement("2", 72.3f, t2)
        )

        composeTestRule.setContent {
            MaterialTheme {
                var selectedIndex by remember { mutableStateOf<Int?>(null) }
                SimpleLineTrendChart(
                    measurements = items,
                    lineColor = FlameOrange,
                    selectedDotIndex = selectedIndex,
                    onSelectDot = { selectedIndex = it },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            }
        }

        // Initially no tooltip
        composeTestRule.onNodeWithTag("trend_chart_tooltip").assertDoesNotExist()

        // Tap dot 0
        composeTestRule.onNodeWithTag("trend_chart_dot_0").performClick()
        composeTestRule.onNodeWithTag("trend_chart_tooltip").assertIsDisplayed()
        composeTestRule.onNodeWithText("70.0 kg").assertIsDisplayed()

        // Tap dot 1 -> switches to dot 1
        composeTestRule.onNodeWithTag("trend_chart_dot_1").performClick()
        composeTestRule.onNodeWithTag("trend_chart_tooltip").assertIsDisplayed()
        composeTestRule.onNodeWithText("72.3 kg").assertIsDisplayed()
        composeTestRule.onNodeWithText("70.0 kg").assertDoesNotExist()

        // Tap dot 1 again -> closes tooltip
        composeTestRule.onNodeWithTag("trend_chart_dot_1").performClick()
        composeTestRule.onNodeWithTag("trend_chart_tooltip").assertDoesNotExist()
    }

    @Test
    fun `entry without date stores only weight in tooltip`() {
        val items = listOf(
            sampleMeasurement("1", 68.5f, 0L),
            sampleMeasurement("2", 69.0f, 0L)
        )

        composeTestRule.setContent {
            MaterialTheme {
                var selectedIndex by remember { mutableStateOf<Int?>(null) }
                SimpleLineTrendChart(
                    measurements = items,
                    lineColor = FlameOrange,
                    selectedDotIndex = selectedIndex,
                    onSelectDot = { selectedIndex = it },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            }
        }

        composeTestRule.onNodeWithTag("trend_chart_dot_0").performClick()
        composeTestRule.onNodeWithTag("trend_chart_tooltip").assertIsDisplayed()
        composeTestRule.onNodeWithText("68.5 kg").assertIsDisplayed()
    }
}
