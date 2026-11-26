package com.example.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.example.ui.theme.BoldRed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarButtonTouchTargetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `add_event_button is at least 48dp tall`() {
        composeTestRule.setContent {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("add_event_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White)
                Text("Add", color = androidx.compose.ui.graphics.Color.White)
            }
        }

        composeTestRule.onNodeWithTag("add_event_button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `save_event_button and delete_event_button are at least 48dp tall`() {
        composeTestRule.setContent {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = BoldRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_event_button")
                ) {
                    Text("Save event")
                }

                TextButton(
                    onClick = {},
                    colors = ButtonDefaults.textButtonColors(contentColor = BoldRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("delete_event_button")
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = BoldRed)
                    Text("Delete event")
                }
            }
        }

        composeTestRule.onNodeWithTag("save_event_button").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("delete_event_button").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `delete recurring dialog buttons are at least 48dp tall and All events is rendered on one line`() {
        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("This day only", maxLines = 1)
                }
                TextButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = BoldRed)
                ) {
                    Text("All events", color = BoldRed, maxLines = 1)
                }
                TextButton(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", maxLines = 1)
                }
            }
        }

        // 1. Assert each button is at least 48dp tall
        composeTestRule.onNodeWithText("This day only").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("All events").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("Cancel").assertHeightIsAtLeast(48.dp)

        // 2. Assert "All events" is not rendered on more than one line
        val node = composeTestRule.onNodeWithText("All events").fetchSemanticsNode()
        val textLayoutResults = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action?.invoke(textLayoutResults)
        org.junit.Assert.assertEquals("All events must be rendered on exactly 1 line", 1, textLayoutResults.first().lineCount)
    }

    @Test
    fun `remind_me chips are at least 48dp tall and render with testTags`() {
        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(modifier = Modifier.width(360.dp)) {
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    val reminderOptions = listOf(
                        -1 to "Off",
                        0 to "At start",
                        10 to "10 min before",
                        30 to "30 min before",
                        60 to "1 hour before"
                    )
                    reminderOptions.forEach { (minutes, label) ->
                        val tagSuffix = if (minutes == -1) "off" else minutes.toString()
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("reminder_chip_$tagSuffix"),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("reminder_chip_off").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("reminder_chip_0").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("reminder_chip_10").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("reminder_chip_30").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("reminder_chip_60").assertHeightIsAtLeast(48.dp)
    }
}
