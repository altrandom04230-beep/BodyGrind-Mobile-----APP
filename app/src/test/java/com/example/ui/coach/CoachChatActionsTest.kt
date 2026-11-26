package com.example.ui.coach

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.coaching.ChatMessage
import com.example.data.coaching.CoachingUiState
import com.example.ui.theme.HomeWorkoutTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CoachChatActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleMessages = listOf(
        ChatMessage(
            id = "msg_1",
            senderId = "coach_123",
            senderName = "Rico Santos",
            text = "Keep up the great work today!",
            createdAtMs = 1700000000000L
        ),
        ChatMessage(
            id = "msg_2",
            senderId = "member_456",
            senderName = "Alex",
            text = "Thanks Coach! Completed leg day.",
            createdAtMs = 1700000060000L
        )
    )

    @Test
    fun `refresh button is present in header and triggers onRefresh when tapped`() {
        var refreshTapped = false

        composeRule.setContent {
            HomeWorkoutTheme {
                CoachChatScreen(
                    peerName = "Rico Santos",
                    peerSubtitle = "Your BodyGrind Coach",
                    currentUserId = "member_456",
                    messagesState = CoachingUiState.Success(sampleMessages),
                    isSending = false,
                    sendError = null,
                    onSendMessage = {},
                    onBack = {},
                    isRefreshing = false,
                    onRefresh = { refreshTapped = true }
                )
            }
        }

        val refreshBtn = composeRule.onNodeWithTag("chat_refresh_button")
        refreshBtn.assertIsDisplayed()
        refreshBtn.performClick()
        assertTrue("onRefresh must be invoked when refresh button is tapped", refreshTapped)
    }

    @Test
    fun `refresh button is disabled while isRefreshing is true`() {
        var refreshTapped = false

        composeRule.setContent {
            HomeWorkoutTheme {
                CoachChatScreen(
                    peerName = "Rico Santos",
                    peerSubtitle = "Your BodyGrind Coach",
                    currentUserId = "member_456",
                    messagesState = CoachingUiState.Success(sampleMessages),
                    isSending = false,
                    sendError = null,
                    onSendMessage = {},
                    onBack = {},
                    isRefreshing = true,
                    onRefresh = { refreshTapped = true }
                )
            }
        }

        val refreshBtn = composeRule.onNodeWithTag("chat_refresh_button")
        refreshBtn.assertIsDisplayed()
        refreshBtn.assertIsNotEnabled()
        refreshBtn.performClick()
        assertFalse("onRefresh must not be invoked while isRefreshing", refreshTapped)
    }

    @Test
    fun `long-press on message bubble opens copy menu and copy button is displayed`() {
        composeRule.setContent {
            HomeWorkoutTheme {
                CoachChatScreen(
                    peerName = "Rico Santos",
                    peerSubtitle = "Your BodyGrind Coach",
                    currentUserId = "member_456",
                    messagesState = CoachingUiState.Success(sampleMessages),
                    isSending = false,
                    sendError = null,
                    onSendMessage = {},
                    onBack = {},
                    isRefreshing = false,
                    onRefresh = {}
                )
            }
        }

        // Initially copy menu is not displayed
        composeRule.onNodeWithTag("chat_copy_button_msg_1").assertDoesNotExist()

        // Long press on received message bubble
        composeRule.onNodeWithTag("chat_bubble_msg_1").performTouchInput {
            longClick()
        }

        // Copy button should now exist and be displayed
        composeRule.onNodeWithTag("chat_copy_button_msg_1").assertIsDisplayed()

        // Long-press on sent message bubble
        composeRule.onNodeWithTag("chat_bubble_msg_2").performTouchInput {
            longClick()
        }
        composeRule.onNodeWithTag("chat_copy_button_msg_2").assertIsDisplayed()
    }

    @Test
    fun `tapping copy button on message bubble closes menu`() {
        composeRule.setContent {
            HomeWorkoutTheme {
                CoachChatScreen(
                    peerName = "Rico Santos",
                    peerSubtitle = "Your BodyGrind Coach",
                    currentUserId = "member_456",
                    messagesState = CoachingUiState.Success(sampleMessages),
                    isSending = false,
                    sendError = null,
                    onSendMessage = {},
                    onBack = {},
                    isRefreshing = false,
                    onRefresh = {}
                )
            }
        }

        // Long press bubble
        composeRule.onNodeWithTag("chat_bubble_msg_1").performTouchInput {
            longClick()
        }
        composeRule.onNodeWithTag("chat_copy_button_msg_1").assertIsDisplayed()

        // Tap copy button
        composeRule.onNodeWithTag("chat_copy_button_msg_1").performClick()

        // Menu should close
        composeRule.onNodeWithTag("chat_copy_button_msg_1").assertDoesNotExist()
    }
}
