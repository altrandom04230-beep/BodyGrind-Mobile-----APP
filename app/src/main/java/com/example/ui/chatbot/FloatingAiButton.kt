package com.example.ui.chatbot

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BoldRed
import com.example.ui.theme.BoldRedLight
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.FlameOrangeDark
import com.example.ui.theme.GoldYellow

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun FloatingAiButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "ai_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val defaultBottomPadding = if (isLandscape) 18.dp else 96.dp

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .padding(end = 16.dp, bottom = defaultBottomPadding)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
            .testTag("floating_ai_coach_button"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            shadowElevation = if (isDragging) 14.dp else 8.dp,
            modifier = Modifier
                .scale(if (isDragging) 1.08f else pulseScale)
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(FlameOrange, FlameOrangeDark)
                        )
                    )
                    .border(
                        width = if (isDragging) 2.5.dp else 1.5.dp,
                        color = if (isDragging) Color.White else BoldRedLight.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = "AI Fitness Coach",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AI Coach",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // Subtle drag grip indicator (dots)
                        Text(
                            text = "⋮⋮",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isDragging) "Dragging..." else "RAG Assistant (Movable)",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}
