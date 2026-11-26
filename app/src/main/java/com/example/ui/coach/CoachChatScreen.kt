package com.example.ui.coach

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.coaching.ChatMessage
import com.example.data.coaching.CoachingUiState
import com.example.ui.theme.BoldRed
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.controlBorder
import com.example.utils.ManilaDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachChatScreen(
    peerName: String,
    peerSubtitle: String,
    currentUserId: String,
    messagesState: CoachingUiState<List<ChatMessage>>,
    isSending: Boolean,
    sendError: String?,
    onSendMessage: (String) -> Unit,
    onBack: () -> Unit,
    showTopBar: Boolean = true,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    BackHandler {
        if (selectedMessageId != null) {
            selectedMessageId = null
        } else {
            onBack()
        }
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = (messagesState as? CoachingUiState.Success)?.data.orEmpty()

    LaunchedEffect(messages.size, isRefreshing) {
        if (!isRefreshing && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column {
                            Text(
                                text = peerName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = peerSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (onRefresh != null) {
                            IconButton(
                                onClick = onRefresh,
                                enabled = !isRefreshing,
                                modifier = Modifier.testTag("chat_refresh_button")
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = FlameOrange,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh chat",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (messagesState) {
                    is CoachingUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = FlameOrange)
                        }
                    }

                    is CoachingUiState.Error -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = messagesState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    is CoachingUiState.Success -> {
                        if (messages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(FlameOrange.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = FlameOrange,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Start a conversation with $peerName",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Direct coaching messages sync in real time.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    val isMine = msg.senderId == currentUserId
                                    val isSelected = selectedMessageId == msg.id
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                                    ) {
                                        Box(
                                            modifier = Modifier.widthIn(max = 300.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(
                                                    topStart = 16.dp,
                                                    topEnd = 16.dp,
                                                    bottomStart = if (isMine) 16.dp else 4.dp,
                                                    bottomEnd = if (isMine) 4.dp else 16.dp
                                                ),
                                                color = if (isMine) BoldRed else MaterialTheme.colorScheme.surfaceVariant,
                                                border = if (isSelected) {
                                                    BorderStroke(
                                                        2.dp,
                                                        if (isMine) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.primary
                                                    )
                                                } else null,
                                                tonalElevation = if (isSelected) 4.dp else 0.dp,
                                                modifier = Modifier
                                                    .pointerInput(msg.id) {
                                                        detectTapGestures(
                                                            onLongPress = {
                                                                selectedMessageId = if (selectedMessageId == msg.id) null else msg.id
                                                            },
                                                            onTap = {
                                                                if (selectedMessageId == msg.id) {
                                                                    selectedMessageId = null
                                                                }
                                                            }
                                                        )
                                                    }
                                                    .testTag("chat_bubble_${msg.id}")
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                                    Text(
                                                        text = msg.text,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = ManilaDateTime.formatDateTime(msg.createdAtMs),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = if (isMine) Color.White.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            DropdownMenu(
                                                expanded = isSelected,
                                                onDismissRequest = {
                                                    if (selectedMessageId == msg.id) {
                                                        selectedMessageId = null
                                                    }
                                                },
                                                modifier = Modifier.testTag("chat_message_menu_${msg.id}")
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Copy", style = MaterialTheme.typography.bodyMedium) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentCopy,
                                                            contentDescription = "Copy",
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    },
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(msg.text))
                                                        selectedMessageId = null
                                                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.testTag("chat_copy_button_${msg.id}")
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!sendError.isNullOrBlank()) {
                Text(
                    text = sendError,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Write a message...") },
                        singleLine = false,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotEmpty() && !isSending) {
                                    onSendMessage(trimmed)
                                    inputText = ""
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.controlBorder
                        ),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("coach_chat_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = {
                            val trimmed = inputText.trim()
                            if (trimmed.isNotEmpty() && !isSending) {
                                onSendMessage(trimmed)
                                inputText = ""
                            }
                        },
                        enabled = inputText.trim().isNotEmpty() && !isSending,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = FlameOrange,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("coach_chat_send_button")
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message")
                        }
                    }
                }
            }
        }
    }
}
