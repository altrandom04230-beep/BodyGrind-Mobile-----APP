package com.example.ui.activeworkout

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.FlameOrange
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import com.google.mlkit.vision.pose.PoseLandmark
import java.util.concurrent.Executors

private val BODY_OUTLINE_CONNECTIONS = listOf(
    PoseLandmark.LEFT_EAR to PoseLandmark.LEFT_EYE,
    PoseLandmark.LEFT_EYE to PoseLandmark.NOSE,
    PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE,
    PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EAR,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
    PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
    PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
    PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
    PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
    PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
    PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
    PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
    PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
    PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
    PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
    PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
    PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX
)

@Composable
fun PoseCameraPreview(
    repsCompleted: Int,
    targetReps: Int,
    isPaused: Boolean,
    trackingState: PoseTrackingState,
    onTrackingStateChanged: (PoseTrackingState) -> Unit,
    onRepDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val squatRepCounter = remember { SquatRepCounter() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var overlayData by remember { mutableStateOf<PoseOverlayData?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val currentIsPaused by rememberUpdatedState(isPaused)
    val currentOnTrackingStateChanged by rememberUpdatedState(onTrackingStateChanged)
    val currentOnRepDetected by rememberUpdatedState(onRepDetected)

    LaunchedEffect(isPaused) {
        squatRepCounter.setPaused(isPaused)
        if (isPaused) {
            overlayData = null
            currentOnTrackingStateChanged(PoseTrackingState.NOT_TRACKING)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Throwable) {
            }
            squatRepCounter.close()
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, previewViewRef, isPaused) {
        var disposed = false
        val previewView = previewViewRef

        if (isPaused || previewView == null) {
            try {
                cameraProvider?.unbindAll()
            } catch (_: Throwable) {
            }
        } else {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                if (disposed || currentIsPaused) return@addListener
                try {
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val analyzer = squatRepCounter.createAnalyzer { overlay, evaluation ->
                        mainExecutor.execute {
                            if (!disposed && !currentIsPaused) {
                                overlayData = overlay
                                currentOnTrackingStateChanged(evaluation.trackingState)
                                if (evaluation.repDetected) {
                                    currentOnRepDetected()
                                }
                            }
                        }
                    }
                    imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    cameraError = null
                } catch (_: Exception) {
                    cameraError = "Camera preview unavailable. Tap card below to count."
                    currentOnTrackingStateChanged(PoseTrackingState.NOT_TRACKING)
                }
            }, mainExecutor)
        }

        onDispose {
            disposed = true
            try {
                cameraProvider?.unbindAll()
            } catch (_: Throwable) {
            }
        }
    }

    val statusColor = if (trackingState == PoseTrackingState.TRACKING) SuccessGreen else WarningAmber
    val statusText = if (trackingState == PoseTrackingState.TRACKING) "Tracking" else "Not tracking"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
            .border(
                width = 2.dp,
                color = if (isPaused) Color.Gray.copy(alpha = 0.5f) else statusColor.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("pose_camera_preview_box")
    ) {
        if (!isPaused) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        previewViewRef = this
                    }
                },
                update = { view ->
                    if (previewViewRef !== view) {
                        previewViewRef = view
                    }
                }
            )
        }

        // Body outline overlay (lines between detected landmarks)
        val currentOverlay = overlayData
        if (!isPaused && currentOverlay != null && currentOverlay.landmarks.isNotEmpty()) {
            val outlineColor = if (currentOverlay.trackingState == PoseTrackingState.TRACKING) {
                SuccessGreen
            } else {
                WarningAmber.copy(alpha = 0.8f)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val imgW = currentOverlay.uprightWidth.toFloat().coerceAtLeast(1f)
                val imgH = currentOverlay.uprightHeight.toFloat().coerceAtLeast(1f)
                val scale = maxOf(size.width / imgW, size.height / imgH)
                val offsetX = (size.width - imgW * scale) / 2f
                val offsetY = (size.height - imgH * scale) / 2f

                fun toOffset(point: NormalizedLandmarkPoint): Offset {
                    // Front camera PreviewView is horizontally mirrored
                    val mirroredNormX = 1f - point.x
                    return Offset(
                        x = mirroredNormX * imgW * scale + offsetX,
                        y = point.y * imgH * scale + offsetY
                    )
                }

                for ((startType, endType) in BODY_OUTLINE_CONNECTIONS) {
                    val startPt = currentOverlay.landmarks[startType]
                    val endPt = currentOverlay.landmarks[endType]
                    if (startPt != null && endPt != null) {
                        drawLine(
                            color = outlineColor,
                            start = toOffset(startPt),
                            end = toOffset(endPt),
                            strokeWidth = 5f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                for (pt in currentOverlay.landmarks.values) {
                    drawCircle(
                        color = Color.White,
                        radius = 7f,
                        center = toOffset(pt)
                    )
                    drawCircle(
                        color = outlineColor,
                        radius = 5f,
                        center = toOffset(pt)
                    )
                }
            }
        }

        // Top HUD: Status label + Rep count "x / target"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("pose_tracking_status")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) Color.Gray else statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPaused) "Paused" else statusText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Surface(
                color = Color.Black.copy(alpha = 0.68f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("camera_rep_count_overlay")
            ) {
                Text(
                    text = "$repsCompleted / $targetReps",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        if (isPaused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = "Camera paused",
                        tint = FlameOrange,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Camera Paused",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else if (cameraError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = cameraError.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else if (trackingState == PoseTrackingState.NOT_TRACKING) {
            Surface(
                color = Color.Black.copy(alpha = 0.72f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .testTag("pose_not_tracking_hint")
            ) {
                Text(
                    text = "Step back so your head and feet are in view, or tap to count.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
