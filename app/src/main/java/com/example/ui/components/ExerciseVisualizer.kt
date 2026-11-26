package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RawRes
import com.example.R
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FlameOrange
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ExerciseVisualizer(
    animationType: String,
    modifier: Modifier = Modifier,
    exerciseId: String? = null,
    isPaused: Boolean = false,
    isFemale: Boolean = false,
    forceVideo: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = ElectricCyan
) {
    val videoResId: Int? = remember(exerciseId, animationType) {
        ExerciseVideoRegistry.getVideoResId(exerciseId = exerciseId, animationType = animationType)
    }

    BoxWithConstraints(modifier = modifier) {
        val isThumbnail = maxWidth < 110.dp || maxHeight < 110.dp
        var preferCanvasView by rememberSaveable(exerciseId, animationType) { mutableStateOf(false) }

        if (videoResId != null && (forceVideo || !isThumbnail) && !preferCanvasView) {
            key(videoResId) {
                ExerciseVideoPlayer(
                    videoResId = videoResId,
                    isPaused = isPaused,
                    modifier = Modifier.fillMaxSize(),
                    onToggleViewMode = { preferCanvasView = true },
                    fallbackContent = {
                        ExerciseCanvasAnimation(
                            animationType = animationType,
                            isPaused = isPaused,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                ExerciseCanvasAnimation(
                    animationType = animationType,
                    isPaused = isPaused,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    modifier = Modifier.fillMaxSize()
                )

                // If user toggled to canvas view and a real video is available, allow switching back to Video
                if (videoResId != null && !isThumbnail && preferCanvasView) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { preferCanvasView = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Switch to Real Video",
                                tint = FlameOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "REAL VIDEO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExerciseVideoPlayer(
    @RawRes videoResId: Int,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
    onToggleViewMode: (() -> Unit)? = null,
    fallbackContent: @Composable () -> Unit
) {
    val context = LocalContext.current
    var isMuted by rememberSaveable { mutableStateOf(true) }
    var isVideoReady by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(videoResId) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
                mediaPlayerRef = null
            } catch (_: Throwable) {}
        }
    }

    if (hasError) {
        fallbackContent()
        return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val surface = Surface(surfaceTexture)
                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    val afd = ctx.resources.openRawResourceFd(videoResId)
                                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                    afd.close()
                                    isLooping = true
                                    val vol = if (isMuted) 0f else 1f
                                    setVolume(vol, vol)
                                    setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                                        if (vWidth > 0 && vHeight > 0 && width > 0 && height > 0) {
                                            val viewAspect = width.toFloat() / height.toFloat()
                                            val videoAspect = vWidth.toFloat() / vHeight.toFloat()
                                            val matrix = Matrix()
                                            if (videoAspect > viewAspect) {
                                                val scaleY = viewAspect / videoAspect
                                                matrix.setScale(1f, scaleY, width / 2f, height / 2f)
                                            } else {
                                                val scaleX = videoAspect / viewAspect
                                                matrix.setScale(scaleX, 1f, width / 2f, height / 2f)
                                            }
                                            setTransform(matrix)
                                        }
                                    }
                                    setOnPreparedListener { prepMp ->
                                        isVideoReady = true
                                        val vWidth = prepMp.videoWidth
                                        val vHeight = prepMp.videoHeight
                                        if (vWidth > 0 && vHeight > 0 && width > 0 && height > 0) {
                                            val viewAspect = width.toFloat() / height.toFloat()
                                            val videoAspect = vWidth.toFloat() / vHeight.toFloat()
                                            val matrix = Matrix()
                                            if (videoAspect > viewAspect) {
                                                val scaleY = viewAspect / videoAspect
                                                matrix.setScale(1f, scaleY, width / 2f, height / 2f)
                                            } else {
                                                val scaleX = videoAspect / viewAspect
                                                matrix.setScale(scaleX, 1f, width / 2f, height / 2f)
                                            }
                                            setTransform(matrix)
                                        }
                                        if (!isPaused) {
                                            prepMp.start()
                                        }
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        hasError = true
                                        true
                                    }
                                    prepareAsync()
                                }
                                mediaPlayerRef = mp
                            } catch (e: Exception) {
                                hasError = true
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            try {
                                mediaPlayerRef?.stop()
                                mediaPlayerRef?.release()
                                mediaPlayerRef = null
                            } catch (_: Throwable) {}
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                    }
                }
            },
            update = {
                try {
                    val vol = if (isMuted) 0f else 1f
                    mediaPlayerRef?.setVolume(vol, vol)
                    if (isPaused) {
                        if (mediaPlayerRef?.isPlaying == true) {
                            mediaPlayerRef?.pause()
                        }
                    } else {
                        if (mediaPlayerRef?.isPlaying == false && isVideoReady) {
                            mediaPlayerRef?.start()
                        }
                    }
                } catch (_: Throwable) {}
            }
        )

        // Loading spinner while preparing
        if (!isVideoReady && !hasError) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = FlameOrange,
                strokeWidth = 2.5.dp
            )
        }

        // Top-Left: Real Video Indicator Badge
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(FlameOrange, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "REAL DEMO",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Top-Right: Sound toggle and Switch-to-Animation toggle
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onToggleViewMode != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    IconButton(
                        onClick = onToggleViewMode,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Animation,
                            contentDescription = "Switch to Canvas Diagram",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.65f)
            ) {
                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        val newVol = if (isMuted) 0f else 1f
                        mediaPlayerRef?.setVolume(newVol, newVol)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute video" else "Mute video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExerciseCanvasAnimation(
    animationType: String,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = ElectricCyan
) {
    val infiniteTransition = rememberInfiniteTransition(label = "exercise_animation")
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycle_progress"
    )

    val effectiveProgress = if (isPaused) 0.5f else cycleProgress

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val strokeW = (w * 0.035f).coerceIn(6f, 14f)
            val headRadius = (w * 0.055f).coerceIn(10f, 22f)

            // Smooth sine wave progress (0 -> 1 -> 0)
            val sineVal = (sin(effectiveProgress * 2 * PI.toFloat() - PI.toFloat() / 2f) + 1f) / 2f

            when (animationType) {
                "PUSH_UP" -> drawPushUp(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "DIAMOND_PUSH_UP" -> drawPushUp(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "SQUAT" -> drawSquat(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "JUMPING_JACKS" -> drawJumpingJacks(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "PLANK" -> drawPlank(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "CRUNCHES" -> drawCrunches(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "GLUTE_BRIDGE" -> drawCrunches(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "LUNGE" -> drawLunge(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "HIGH_KNEES" -> drawHighKnees(cx, cy, w, h, effectiveProgress, strokeW, headRadius, primaryColor, secondaryColor)
                "MOUNTAIN_CLIMBERS" -> drawMountainClimbers(cx, cy, w, h, effectiveProgress, strokeW, headRadius, primaryColor, secondaryColor)
                "WALL_SIT" -> drawWallSit(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "ARM_CIRCLES" -> drawArmCircles(cx, cy, w, h, effectiveProgress, strokeW, headRadius, primaryColor, secondaryColor)
                "COBRA_STRETCH" -> drawCobraStretch(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "LEG_RAISES" -> drawLegRaises(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                "BICYCLE_CRUNCHES" -> drawBicycleCrunches(cx, cy, w, h, effectiveProgress, strokeW, headRadius, primaryColor, secondaryColor)
                "TRICEPS_DIPS" -> drawGenericExercise(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
                else -> drawGenericExercise(cx, cy, w, h, sineVal, strokeW, headRadius, primaryColor, secondaryColor)
            }
        }
    }
}

// Push-Up Drawing
private fun DrawScope.drawPushUp(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.3f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val chestDrop = sine * (h * 0.15f)
    val shoulderX = cx + w * 0.18f
    val shoulderY = cy + h * 0.05f + chestDrop
    val hipX = cx - w * 0.05f
    val hipY = cy + h * 0.08f + chestDrop * 0.6f
    val feetX = cx - w * 0.32f
    val feetY = floorY

    // Body line (Head, Torso, Legs)
    drawCircle(pColor, radius = hr, center = Offset(shoulderX + w * 0.08f, shoulderY - h * 0.04f))
    drawLine(pColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(hipX, hipY), Offset(feetX, feetY), strokeWidth = sw, cap = StrokeCap.Round)

    // Arm (Hands on floor)
    val handX = shoulderX
    val handY = floorY
    val elbowX = shoulderX - (h * 0.08f) * (1f - sine * 0.6f)
    val elbowY = (shoulderY + handY) / 2f + chestDrop * 0.3f
    drawLine(sColor, Offset(shoulderX, shoulderY), Offset(elbowX, elbowY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(elbowX, elbowY), Offset(handX, handY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Squat Drawing
private fun DrawScope.drawSquat(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.4f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.15f, floorY), Offset(w * 0.85f, floorY), strokeWidth = 3f)

    val drop = sine * (h * 0.22f)
    val hipBack = sine * (w * 0.12f)

    val headY = cy - h * 0.35f + drop
    val shoulderY = headY + h * 0.1f
    val hipX = cx - hipBack
    val hipY = shoulderY + h * 0.22f

    val kneeX = cx + (w * 0.06f) * (1f - sine * 0.3f)
    val kneeY = floorY - (h * 0.22f) * (1f - sine * 0.6f)
    val feetX = cx

    // Head & Torso
    drawCircle(pColor, radius = hr, center = Offset(cx - hipBack * 0.3f, headY))
    drawLine(pColor, Offset(cx - hipBack * 0.3f, shoulderY), Offset(hipX, hipY), strokeWidth = sw, cap = StrokeCap.Round)

    // Legs
    drawLine(sColor, Offset(hipX, hipY), Offset(kneeX, kneeY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(kneeX, kneeY), Offset(feetX, floorY), strokeWidth = sw, cap = StrokeCap.Round)

    // Arms reached forward for balance
    val handX = cx + w * 0.22f + sine * (w * 0.06f)
    val handY = shoulderY + drop * 0.2f
    drawLine(pColor.copy(alpha = 0.8f), Offset(cx - hipBack * 0.3f, shoulderY), Offset(handX, handY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Jumping Jacks Drawing
private fun DrawScope.drawJumpingJacks(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.42f
    val jumpUp = sine * (h * 0.08f)

    val headY = cy - h * 0.28f - jumpUp
    val shoulderY = headY + h * 0.1f
    val hipY = shoulderY + h * 0.25f

    // Legs width based on sine
    val legSpread = w * 0.05f + sine * (w * 0.22f)

    // Head & Torso
    drawCircle(pColor, radius = hr, center = Offset(cx, headY))
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx, hipY), strokeWidth = sw, cap = StrokeCap.Round)

    // Legs
    drawLine(sColor, Offset(cx, hipY), Offset(cx - legSpread, floorY - jumpUp), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(cx, hipY), Offset(cx + legSpread, floorY - jumpUp), strokeWidth = sw, cap = StrokeCap.Round)

    // Arms (low to high overhead)
    val armSpread = w * 0.15f + (1f - sine) * (w * 0.12f)
    val handY = (shoulderY + h * 0.22f) * (1f - sine) + (headY - h * 0.12f) * sine
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx - armSpread, handY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx + armSpread, handY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Plank Drawing
private fun DrawScope.drawPlank(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.25f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val breathing = sine * (h * 0.02f)
    val headX = cx + w * 0.32f
    val headY = cy + h * 0.02f - breathing
    val shoulderX = cx + w * 0.22f
    val shoulderY = cy + h * 0.06f - breathing
    val hipX = cx - w * 0.05f
    val hipY = cy + h * 0.08f - breathing
    val feetX = cx - w * 0.35f
    val feetY = floorY

    // Core glow circle
    drawCircle(
        color = sColor.copy(alpha = 0.25f + sine * 0.2f),
        radius = w * 0.1f,
        center = Offset(hipX, hipY),
        style = Stroke(width = 4f)
    )

    // Head, Torso, Legs
    drawCircle(pColor, radius = hr, center = Offset(headX, headY))
    drawLine(pColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(hipX, hipY), Offset(feetX, feetY), strokeWidth = sw, cap = StrokeCap.Round)

    // Forearm
    val elbowX = shoulderX
    val elbowY = floorY
    val handX = shoulderX + w * 0.1f
    drawLine(sColor, Offset(shoulderX, shoulderY), Offset(elbowX, elbowY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(elbowX, elbowY), Offset(handX, floorY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Crunches Drawing
private fun DrawScope.drawCrunches(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.28f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val hipX = cx - w * 0.05f
    val hipY = floorY - h * 0.02f
    val kneeX = cx + w * 0.18f
    val kneeY = floorY - h * 0.22f
    val feetX = cx + w * 0.32f

    val shoulderLift = sine * (h * 0.18f)
    val shoulderX = cx - w * 0.22f + sine * (w * 0.06f)
    val shoulderY = floorY - h * 0.04f - shoulderLift
    val headX = shoulderX - w * 0.06f
    val headY = shoulderY - h * 0.06f

    // Legs bent
    drawLine(sColor, Offset(hipX, hipY), Offset(kneeX, kneeY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(kneeX, kneeY), Offset(feetX, floorY), strokeWidth = sw, cap = StrokeCap.Round)

    // Torso & Head
    drawLine(pColor, Offset(hipX, hipY), Offset(shoulderX, shoulderY), strokeWidth = sw, cap = StrokeCap.Round)
    drawCircle(pColor, radius = hr, center = Offset(headX, headY))

    // Hands behind head
    val elbowX = headX - w * 0.05f
    val elbowY = headY + h * 0.04f
    drawLine(pColor.copy(alpha = 0.8f), Offset(shoulderX, shoulderY), Offset(elbowX, elbowY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor.copy(alpha = 0.8f), Offset(elbowX, elbowY), Offset(headX, headY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Lunge Drawing
private fun DrawScope.drawLunge(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.38f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.15f, floorY), Offset(w * 0.85f, floorY), strokeWidth = 3f)

    val drop = sine * (h * 0.15f)
    val hipY = cy + h * 0.05f + drop
    val shoulderY = hipY - h * 0.22f
    val headY = shoulderY - h * 0.1f

    // Front leg
    val fKneeX = cx + w * 0.18f
    val fKneeY = floorY - (h * 0.16f) * (1f - sine * 0.5f)
    val fFootX = fKneeX

    // Back leg
    val bKneeX = cx - w * 0.16f
    val bKneeY = floorY - (h * 0.18f) * (1f - sine)
    val bFootX = cx - w * 0.32f

    // Head & Torso upright
    drawCircle(pColor, radius = hr, center = Offset(cx, headY))
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx, hipY), strokeWidth = sw, cap = StrokeCap.Round)

    // Front leg
    drawLine(sColor, Offset(cx, hipY), Offset(fKneeX, fKneeY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(fKneeX, fKneeY), Offset(fFootX, floorY), strokeWidth = sw, cap = StrokeCap.Round)

    // Back leg
    drawLine(sColor.copy(alpha = 0.7f), Offset(cx, hipY), Offset(bKneeX, bKneeY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor.copy(alpha = 0.7f), Offset(bKneeX, bKneeY), Offset(bFootX, floorY), strokeWidth = sw, cap = StrokeCap.Round)
}

// High Knees Drawing
private fun DrawScope.drawHighKnees(
    cx: Float, cy: Float, w: Float, h: Float,
    prog: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.38f
    val legPhase = sin(prog * 2 * PI.toFloat())

    val headY = cy - h * 0.3f
    val shoulderY = headY + h * 0.1f
    val hipY = shoulderY + h * 0.22f

    drawCircle(pColor, radius = hr, center = Offset(cx, headY))
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx, hipY), strokeWidth = sw, cap = StrokeCap.Round)

    // Leg 1 (Left)
    val lLift = (legPhase.coerceAtLeast(0f)) * (h * 0.25f)
    val lKneeY = floorY - h * 0.15f - lLift
    val lFootY = (floorY - lLift * 0.7f).coerceAtMost(floorY)
    drawLine(sColor, Offset(cx, hipY), Offset(cx - w * 0.08f, lKneeY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(cx - w * 0.08f, lKneeY), Offset(cx - w * 0.08f, lFootY), strokeWidth = sw, cap = StrokeCap.Round)

    // Leg 2 (Right)
    val rLift = ((-legPhase).coerceAtLeast(0f)) * (h * 0.25f)
    val rKneeY = floorY - h * 0.15f - rLift
    val rFootY = (floorY - rLift * 0.7f).coerceAtMost(floorY)
    drawLine(pColor, Offset(cx, hipY), Offset(cx + w * 0.08f, rKneeY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(cx + w * 0.08f, rKneeY), Offset(cx + w * 0.08f, rFootY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Mountain Climbers Drawing
private fun DrawScope.drawMountainClimbers(
    cx: Float, cy: Float, w: Float, h: Float,
    prog: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.28f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val shoulderX = cx + w * 0.22f
    val shoulderY = cy + h * 0.05f
    val hipX = cx - w * 0.08f
    val hipY = cy + h * 0.04f

    drawCircle(pColor, radius = hr, center = Offset(shoulderX + w * 0.08f, shoulderY - h * 0.04f))
    drawLine(pColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    // Arms supporting
    drawLine(sColor, Offset(shoulderX, shoulderY), Offset(shoulderX, floorY), strokeWidth = sw, cap = StrokeCap.Round)

    val phase = sin(prog * 2 * PI.toFloat())
    val leg1Drive = (phase.coerceAtLeast(0f)) * (w * 0.22f)
    val leg2Drive = ((-phase).coerceAtLeast(0f)) * (w * 0.22f)

    // Leg 1
    val knee1X = hipX + leg1Drive
    val foot1X = cx - w * 0.35f + leg1Drive * 0.8f
    drawLine(sColor, Offset(hipX, hipY), Offset(knee1X, cy + h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(knee1X, cy + h * 0.15f), Offset(foot1X, floorY), strokeWidth = sw, cap = StrokeCap.Round)

    // Leg 2
    val knee2X = hipX + leg2Drive
    val foot2X = cx - w * 0.35f + leg2Drive * 0.8f
    drawLine(pColor.copy(alpha = 0.7f), Offset(hipX, hipY), Offset(knee2X, cy + h * 0.16f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor.copy(alpha = 0.7f), Offset(knee2X, cy + h * 0.16f), Offset(foot2X, floorY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Wall Sit Drawing
private fun DrawScope.drawWallSit(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.38f
    val wallX = cx - w * 0.15f
    // Wall line
    drawLine(Color.Gray.copy(alpha = 0.6f), Offset(wallX, cy - h * 0.4f), Offset(wallX, floorY), strokeWidth = 5f)
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val hipY = cy + h * 0.12f
    val shoulderY = hipY - h * 0.25f
    val headY = shoulderY - h * 0.1f
    val kneeX = wallX + w * 0.25f
    val footX = kneeX

    drawCircle(pColor, radius = hr, center = Offset(wallX + hr, headY))
    drawLine(pColor, Offset(wallX, shoulderY), Offset(wallX, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(wallX, hipY), Offset(kneeX, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(kneeX, hipY), Offset(footX, floorY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Arm Circles Drawing
private fun DrawScope.drawArmCircles(
    cx: Float, cy: Float, w: Float, h: Float,
    prog: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val headY = cy - h * 0.25f
    val shoulderY = headY + h * 0.1f
    val hipY = shoulderY + h * 0.25f
    val floorY = cy + h * 0.4f

    drawCircle(pColor, radius = hr, center = Offset(cx, headY))
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(cx, hipY), Offset(cx - w * 0.08f, floorY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(cx, hipY), Offset(cx + w * 0.08f, floorY), strokeWidth = sw, cap = StrokeCap.Round)

    // Rotating arms
    val angle = prog * 2 * PI.toFloat()
    val r = w * 0.25f
    val rHandX = cx + cos(angle) * r
    val rHandY = shoulderY + sin(angle) * (r * 0.5f)
    val lHandX = cx - cos(angle) * r
    val lHandY = shoulderY - sin(angle) * (r * 0.5f)

    drawLine(sColor, Offset(cx, shoulderY), Offset(rHandX, rHandY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(cx, shoulderY), Offset(lHandX, lHandY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Cobra Stretch Drawing
private fun DrawScope.drawCobraStretch(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.25f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val arch = sine * (h * 0.15f)
    val shoulderX = cx + w * 0.18f
    val shoulderY = cy + h * 0.05f - arch
    val headX = shoulderX + w * 0.05f
    val headY = shoulderY - h * 0.08f
    val hipX = cx - w * 0.1f
    val hipY = floorY
    val feetX = cx - w * 0.4f

    drawCircle(pColor, radius = hr, center = Offset(headX, headY))
    drawLine(pColor, Offset(shoulderX, shoulderY), Offset(hipX, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(hipX, hipY), Offset(feetX, floorY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(shoulderX, shoulderY), Offset(shoulderX, floorY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Leg Raises Drawing
private fun DrawScope.drawLegRaises(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.25f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val hipX = cx
    val hipY = floorY
    val headX = cx - w * 0.32f
    val headY = floorY - h * 0.02f

    drawCircle(pColor, radius = hr, center = Offset(headX, headY - hr))
    drawLine(pColor, Offset(headX, headY), Offset(hipX, hipY), strokeWidth = sw, cap = StrokeCap.Round)

    // Leg angle from flat (0) to 80 degrees up
    val legAngle = sine * (PI.toFloat() * 0.45f)
    val legLen = w * 0.35f
    val feetX = hipX + cos(legAngle) * legLen
    val feetY = hipY - sin(legAngle) * legLen
    drawLine(sColor, Offset(hipX, hipY), Offset(feetX, feetY), strokeWidth = sw, cap = StrokeCap.Round)
}

// Bicycle Crunches Drawing
private fun DrawScope.drawBicycleCrunches(
    cx: Float, cy: Float, w: Float, h: Float,
    prog: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.25f
    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(w * 0.1f, floorY), Offset(w * 0.9f, floorY), strokeWidth = 3f)

    val hipX = cx - w * 0.05f
    val hipY = floorY
    val shoulderX = cx - w * 0.22f
    val shoulderY = floorY - h * 0.12f
    val headX = shoulderX - w * 0.06f
    val headY = shoulderY - h * 0.06f

    drawCircle(pColor, radius = hr, center = Offset(headX, headY))
    drawLine(pColor, Offset(hipX, hipY), Offset(shoulderX, shoulderY), strokeWidth = sw, cap = StrokeCap.Round)

    val phase = sin(prog * 2 * PI.toFloat())
    val knee1X = hipX + (0.5f + phase * 0.3f) * (w * 0.22f)
    val knee1Y = floorY - h * 0.2f
    drawLine(sColor, Offset(hipX, hipY), Offset(knee1X, knee1Y), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(knee1X, knee1Y), Offset(knee1X + w * 0.15f, floorY - h * 0.05f), strokeWidth = sw, cap = StrokeCap.Round)
}

// Generic Exercise Fallback
private fun DrawScope.drawGenericExercise(
    cx: Float, cy: Float, w: Float, h: Float,
    sine: Float, sw: Float, hr: Float, pColor: Color, sColor: Color
) {
    val floorY = cy + h * 0.38f
    val headY = cy - h * 0.28f + sine * (h * 0.05f)
    val shoulderY = headY + h * 0.1f
    val hipY = shoulderY + h * 0.22f

    drawCircle(pColor, radius = hr, center = Offset(cx, headY))
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx, hipY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(cx, hipY), Offset(cx - w * 0.1f, floorY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(sColor, Offset(cx, hipY), Offset(cx + w * 0.1f, floorY), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx - w * 0.18f, shoulderY + h * 0.1f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(pColor, Offset(cx, shoulderY), Offset(cx + w * 0.18f, shoulderY + h * 0.1f), strokeWidth = sw, cap = StrokeCap.Round)
}
