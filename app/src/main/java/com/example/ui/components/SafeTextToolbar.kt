package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * Pure Compose implementation of TextToolbar.
 * Completely eliminates Android DecorView FloatingActionMode lifecycle mismatches
 * ("Destroying unexpected ActionMode instance of TYPE_FLOATING") by handling
 * Copy, Cut, Paste, and Select All entirely within Jetpack Compose Popups.
 */
class SafeComposeTextToolbar : TextToolbar {
    override var status: TextToolbarStatus by mutableStateOf(TextToolbarStatus.Hidden)
        private set

    var menuRect: Rect? by mutableStateOf(null)
        private set

    var onCopy: (() -> Unit)? = null
        private set
    var onPaste: (() -> Unit)? = null
        private set
    var onCut: (() -> Unit)? = null
        private set
    var onSelectAll: (() -> Unit)? = null
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        menuRect = rect
        onCopy = onCopyRequested
        onPaste = onPasteRequested
        onCut = onCutRequested
        onSelectAll = onSelectAllRequested
        status = TextToolbarStatus.Shown
    }

    override fun hide() {
        status = TextToolbarStatus.Hidden
        menuRect = null
        onCopy = null
        onPaste = null
        onCut = null
        onSelectAll = null
    }

    fun handleCopy() {
        val action = onCopy
        hide()
        action?.invoke()
    }

    fun handlePaste() {
        val action = onPaste
        hide()
        action?.invoke()
    }

    fun handleCut() {
        val action = onCut
        hide()
        action?.invoke()
    }

    fun handleSelectAll() {
        val action = onSelectAll
        hide()
        action?.invoke()
    }
}

@Composable
fun ProvideSafeTextToolbar(content: @Composable () -> Unit) {
    val toolbar = remember { SafeComposeTextToolbar() }

    CompositionLocalProvider(LocalTextToolbar provides toolbar) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()

            if (toolbar.status == TextToolbarStatus.Shown && toolbar.menuRect != null) {
                val rect = toolbar.menuRect!!
                val leftPx = rect.left.toInt().coerceAtLeast(16)
                val topPx = (rect.top - 140).toInt().coerceAtLeast(16)

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(leftPx, topPx),
                    onDismissRequest = { toolbar.hide() },
                    properties = PopupProperties(focusable = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (toolbar.onCut != null) {
                                TextButton(onClick = { toolbar.handleCut() }) {
                                    Text("Cut", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (toolbar.onCopy != null) {
                                TextButton(onClick = { toolbar.handleCopy() }) {
                                    Text("Copy", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (toolbar.onPaste != null) {
                                TextButton(onClick = { toolbar.handlePaste() }) {
                                    Text("Paste", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            if (toolbar.onSelectAll != null) {
                                TextButton(onClick = { toolbar.handleSelectAll() }) {
                                    Text("Select All", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
