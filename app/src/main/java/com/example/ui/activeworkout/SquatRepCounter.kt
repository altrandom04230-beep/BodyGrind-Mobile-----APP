package com.example.ui.activeworkout

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan2

data class NormalizedLandmarkPoint(
    val x: Float,
    val y: Float,
    val inFrameLikelihood: Float
)

data class PoseOverlayData(
    val uprightWidth: Int,
    val uprightHeight: Int,
    val trackingState: PoseTrackingState,
    val landmarks: Map<Int, NormalizedLandmarkPoint>
)

data class PoseEvaluationResult(
    val trackingState: PoseTrackingState,
    val repDetected: Boolean,
    val kneeAngleDegrees: Float?
)

class SquatRepCounter {

    companion object {
        const val DOWN_ANGLE_THRESHOLD_DEGREES = 100f
        const val UP_ANGLE_THRESHOLD_DEGREES = 160f
        const val MIN_LANDMARK_LIKELIHOOD = 0.5f

        private val OVERLAY_LANDMARK_TYPES = intArrayOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE,
            PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_EAR,
            PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL,
            PoseLandmark.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.RIGHT_FOOT_INDEX
        )

        fun calculateAngleDegrees(
            firstX: Float,
            firstY: Float,
            midX: Float,
            midY: Float,
            lastX: Float,
            lastY: Float
        ): Float {
            val radians = atan2(lastY - midY, lastX - midX) - atan2(firstY - midY, firstX - midX)
            var degrees = abs(Math.toDegrees(radians.toDouble())).toFloat()
            if (degrees > 180f) {
                degrees = 360f - degrees
            }
            return degrees
        }
    }

    @Volatile
    private var isInDownPosition: Boolean = false

    @Volatile
    private var isPaused: Boolean = false

    private val isClosed = AtomicBoolean(false)

    private val poseDetector: PoseDetector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        PoseDetection.getClient(options)
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun reset() {
        isInDownPosition = false
    }

    fun close() {
        if (isClosed.compareAndSet(false, true)) {
            isInDownPosition = false
            try {
                poseDetector.close()
            } catch (_: Throwable) {
            }
        }
    }

    fun processMeasurements(
        isTracking: Boolean,
        kneeAngleDegrees: Float?,
        paused: Boolean = isPaused
    ): Boolean {
        if (paused || !isTracking || kneeAngleDegrees == null) {
            return false
        }
        if (!isInDownPosition) {
            if (kneeAngleDegrees < DOWN_ANGLE_THRESHOLD_DEGREES) {
                isInDownPosition = true
            }
            return false
        } else {
            if (kneeAngleDegrees > UP_ANGLE_THRESHOLD_DEGREES) {
                isInDownPosition = false
                return true
            }
            return false
        }
    }

    fun evaluatePose(pose: Pose, paused: Boolean = isPaused): PoseEvaluationResult {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val requiredLandmarks = listOf(leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle)
        val allVisible = requiredLandmarks.all { lm ->
            lm != null && lm.inFrameLikelihood >= MIN_LANDMARK_LIKELIHOOD
        }

        if (!allVisible) {
            return PoseEvaluationResult(
                trackingState = PoseTrackingState.NOT_TRACKING,
                repDetected = false,
                kneeAngleDegrees = null
            )
        }

        val lh = leftHip!!
        val rh = rightHip!!
        val lk = leftKnee!!
        val rk = rightKnee!!
        val la = leftAnkle!!
        val ra = rightAnkle!!

        val leftLikelihood = (lh.inFrameLikelihood + lk.inFrameLikelihood + la.inFrameLikelihood) / 3f
        val rightLikelihood = (rh.inFrameLikelihood + rk.inFrameLikelihood + ra.inFrameLikelihood) / 3f

        val kneeAngle = if (leftLikelihood >= rightLikelihood) {
            calculateAngleDegrees(
                lh.position.x, lh.position.y,
                lk.position.x, lk.position.y,
                la.position.x, la.position.y
            )
        } else {
            calculateAngleDegrees(
                rh.position.x, rh.position.y,
                rk.position.x, rk.position.y,
                ra.position.x, ra.position.y
            )
        }

        val repDetected = processMeasurements(
            isTracking = true,
            kneeAngleDegrees = kneeAngle,
            paused = paused
        )

        return PoseEvaluationResult(
            trackingState = PoseTrackingState.TRACKING,
            repDetected = repDetected,
            kneeAngleDegrees = kneeAngle
        )
    }

    fun extractOverlayData(
        pose: Pose,
        uprightWidth: Int,
        uprightHeight: Int,
        trackingState: PoseTrackingState
    ): PoseOverlayData {
        val w = uprightWidth.coerceAtLeast(1).toFloat()
        val h = uprightHeight.coerceAtLeast(1).toFloat()
        val landmarksMap = HashMap<Int, NormalizedLandmarkPoint>(OVERLAY_LANDMARK_TYPES.size)
        for (type in OVERLAY_LANDMARK_TYPES) {
            val lm = pose.getPoseLandmark(type)
            if (lm != null && lm.inFrameLikelihood >= MIN_LANDMARK_LIKELIHOOD) {
                landmarksMap[type] = NormalizedLandmarkPoint(
                    x = (lm.position.x / w).coerceIn(0f, 1f),
                    y = (lm.position.y / h).coerceIn(0f, 1f),
                    inFrameLikelihood = lm.inFrameLikelihood
                )
            }
        }
        return PoseOverlayData(
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            trackingState = trackingState,
            landmarks = landmarksMap
        )
    }

    fun createAnalyzer(
        onPoseUpdated: (PoseOverlayData, PoseEvaluationResult) -> Unit
    ): ImageAnalysis.Analyzer {
        return ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
            analyzeFrame(imageProxy, onPoseUpdated)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun analyzeFrame(
        imageProxy: ImageProxy,
        onPoseUpdated: (PoseOverlayData, PoseEvaluationResult) -> Unit
    ) {
        if (isClosed.get() || isPaused) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        try {
            poseDetector.process(inputImage)
                .addOnSuccessListener { pose ->
                    if (!isClosed.get() && !isPaused) {
                        val evaluation = evaluatePose(pose, paused = isPaused)
                        val overlay = extractOverlayData(
                            pose = pose,
                            uprightWidth = uprightWidth,
                            uprightHeight = uprightHeight,
                            trackingState = evaluation.trackingState
                        )
                        onPoseUpdated(overlay, evaluation)
                    }
                }
                .addOnFailureListener {
                    if (!isClosed.get() && !isPaused) {
                        onPoseUpdated(
                            PoseOverlayData(
                                uprightWidth = uprightWidth,
                                uprightHeight = uprightHeight,
                                trackingState = PoseTrackingState.NOT_TRACKING,
                                landmarks = emptyMap()
                            ),
                            PoseEvaluationResult(
                                trackingState = PoseTrackingState.NOT_TRACKING,
                                repDetected = false,
                                kneeAngleDegrees = null
                            )
                        )
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } catch (_: Throwable) {
            imageProxy.close()
        }
    }
}
