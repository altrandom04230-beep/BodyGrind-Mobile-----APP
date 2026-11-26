package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BoldRed
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val PrimaryTargetColor = BoldRed
private val SecondaryCoreColor = Color(0xFF38BDF8)
private val OtherMusclesColor = Color(0xFF475569)

enum class MuscleGroup(val displayName: String, val isFront: Boolean) {
    CHEST("Chest / Pectorals", true),
    ABS("Abdominals / Core", true),
    OBLIQUES("Obliques", true),
    SHOULDERS_FRONT("Front Deltoids", true),
    BICEPS("Biceps", true),
    FOREARMS_FRONT("Forearms", true),
    QUADS("Quadriceps (Front Thighs)", true),
    CALVES_FRONT("Tibialis / Calves", true),

    TRAPS("Trapezius (Upper Back)", false),
    LATS("Latissimus (Mid Back)", false),
    LOWER_BACK("Lower Back / Lumbar", false),
    SHOULDERS_BACK("Rear Deltoids", false),
    TRICEPS("Triceps", false),
    FOREARMS_BACK("Forearms", false),
    GLUTES("Glutes (Hips)", false),
    HAMSTRINGS("Hamstrings (Back Thighs)", false),
    CALVES_BACK("Calves (Gastrocnemius)", false)
}

enum class MuscleRole {
    PRIMARY,
    SECONDARY,
    NONE
}

@Composable
fun BodyMuscleVisualizer(
    primaryMuscles: String,
    secondaryMuscles: String = "",
    isFemale: Boolean = false,
    modifier: Modifier = Modifier,
    showTargetMusclesSection: Boolean = true,
    onMuscleSelected: ((MuscleGroup) -> Unit)? = null
) {
    // 3D Orbit Rotation Angle in Degrees (0° = Anterior/Front, 90° = Right Lateral, 180° = Posterior/Back, 270° = Left Lateral)
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    var isAutoRotating by remember { mutableStateOf(false) }

    val activeMuscles = remember(primaryMuscles, secondaryMuscles) {
        val map = mutableMapOf<MuscleGroup, MuscleRole>()
        val pLower = primaryMuscles.lowercase()
        val sLower = secondaryMuscles.lowercase()

        fun checkRole(keywords: List<String>): MuscleRole {
            if (keywords.any { pLower.contains(it) }) return MuscleRole.PRIMARY
            if (keywords.any { sLower.contains(it) }) return MuscleRole.SECONDARY
            return MuscleRole.NONE
        }

        map[MuscleGroup.CHEST] = checkRole(listOf("chest", "pec", "pectoral", "pushup", "push-up"))
        map[MuscleGroup.ABS] = checkRole(listOf("ab", "abs", "core", "crunch", "plank", "situp", "full body"))
        map[MuscleGroup.OBLIQUES] = checkRole(listOf("oblique", "twist", "side plank", "core"))
        map[MuscleGroup.SHOULDERS_FRONT] = checkRole(listOf("shoulder", "delt", "front delt", "overhead", "press"))
        map[MuscleGroup.BICEPS] = checkRole(listOf("bicep", "arm", "curl", "pullup", "pull-up", "chin-up"))
        map[MuscleGroup.FOREARMS_FRONT] = checkRole(listOf("forearm", "wrist", "grip"))
        map[MuscleGroup.QUADS] = checkRole(listOf("quad", "thigh", "squat", "lunge", "jump", "leg", "knee"))
        map[MuscleGroup.CALVES_FRONT] = checkRole(listOf("calf", "calves", "jump", "tibialis"))

        map[MuscleGroup.TRAPS] = checkRole(listOf("trap", "trapezius", "upper back", "shrug", "back"))
        map[MuscleGroup.LATS] = checkRole(listOf("lat", "latissimus", "back", "row", "pull", "pullup"))
        map[MuscleGroup.LOWER_BACK] = checkRole(listOf("lower back", "lumbar", "erector", "deadlift", "bridge"))
        map[MuscleGroup.SHOULDERS_BACK] = checkRole(listOf("rear delt", "shoulder", "back"))
        map[MuscleGroup.TRICEPS] = checkRole(listOf("tricep", "dip", "pushdown", "extension", "push"))
        map[MuscleGroup.FOREARMS_BACK] = checkRole(listOf("forearm", "grip"))
        map[MuscleGroup.GLUTES] = checkRole(listOf("glute", "butt", "hip", "bridge", "squat", "lunge", "thrust"))
        map[MuscleGroup.HAMSTRINGS] = checkRole(listOf("hamstring", "back thigh", "deadlift", "posterior"))
        map[MuscleGroup.CALVES_BACK] = checkRole(listOf("calf", "calves", "jump", "raise", "running"))

        map
    }

    val targetEntries = remember(activeMuscles) {
        activeMuscles.entries
            .filter { it.value != MuscleRole.NONE }
            .sortedBy { if (it.value == MuscleRole.PRIMARY) 0 else 1 }
    }

    // Auto-select starting 3D view based on primary target location
    LaunchedEffect(primaryMuscles) {
        val hasBackPrimary = activeMuscles.entries.any { !it.key.isFront && it.value == MuscleRole.PRIMARY }
        val hasFrontPrimary = activeMuscles.entries.any { it.key.isFront && it.value == MuscleRole.PRIMARY }
        rotationAngle = if (hasBackPrimary && !hasFrontPrimary) {
            180f // Start facing posterior
        } else {
            0f // Start facing anterior
        }
    }

    // Smooth continuous auto-rotation if toggled
    LaunchedEffect(isAutoRotating) {
        while (isAutoRotating) {
            delay(16)
            rotationAngle = (rotationAngle + 0.8f) % 360f
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_pulse"
    )

    Card(
        modifier = modifier.testTag("body_muscle_visualizer"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with 3D Status and Auto-Spin Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessibilityNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.muscle_anatomy_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = stringResource(R.string.muscle_anatomy_badge),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(
                                if (isFemale) R.string.muscle_anatomy_female_subtitle
                                else R.string.muscle_anatomy_male_subtitle
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                // Auto-Spin 3D Orbit Toggle (48.dp touch target)
                IconButton(
                    onClick = { isAutoRotating = !isAutoRotating },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("toggle_auto_rotate_3d")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isAutoRotating) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.RotateRight,
                            contentDescription = "Auto 3D Spin",
                            tint = if (isAutoRotating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3D Canvas with Horizontal Drag Gesture for 360° Rotation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF1A2334), Color(0xFF0A0E16))
                        )
                    )
                    .border(1.dp, Color(0xFF232D3F), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            isAutoRotating = false
                            rotationAngle = (rotationAngle - dragAmount.x * 0.7f + 360f) % 360f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f

                    // Draw 3D Perspective Hologram Grid Floor
                    drawHolographicPedestal(cx = cx, h = h, rotationAngle = rotationAngle)

                    // Draw Volumetric 3D Anatomical Human Model
                    draw3DAnatomyModel(
                        cx = cx,
                        h = h,
                        angle = rotationAngle,
                        isFemale = isFemale,
                        activeMuscles = activeMuscles,
                        glowPulse = glowPulse
                    )
                }

                // View Angle Telemetry Badge (Top Left)
                val normalizedAngle = ((rotationAngle % 360f) + 360f) % 360f
                val angleLabel = when {
                    normalizedAngle >= 337.5f || normalizedAngle < 22.5f -> "ANTERIOR (FRONT)"
                    normalizedAngle < 67.5f -> "RIGHT 3/4 OBLIQUE"
                    normalizedAngle < 112.5f -> "RIGHT LATERAL (SIDE)"
                    normalizedAngle < 157.5f -> "POSTERIOR 3/4 VIEW"
                    normalizedAngle < 202.5f -> "POSTERIOR (BACK)"
                    normalizedAngle < 247.5f -> "LEFT 3/4 VIEW"
                    normalizedAngle < 292.5f -> "LEFT LATERAL (SIDE)"
                    else -> "LEFT 3/4 OBLIQUE"
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(PrimaryTargetColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$angleLabel • ${normalizedAngle.roundToInt()}°",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Interactive Drag Hint Overlay (Bottom Center)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.muscle_anatomy_drag_hint),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Preset Angle Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val presets = listOf(
                    Triple("Front", 0f, "toggle_front_view"),
                    Triple("3/4 View", 45f, "toggle_oblique_view"),
                    Triple("Side", 90f, "toggle_side_view"),
                    Triple("Back", 180f, "toggle_back_view")
                )

                presets.forEach { (label, targetAngle, tag) ->
                    val isCurrent = abs(((rotationAngle - targetAngle + 180f) % 360f + 360f) % 360f - 180f) < 25f
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable {
                                isAutoRotating = false
                                rotationAngle = targetAngle
                            }
                            .testTag(tag)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Color Legend matching artifact copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendItem(color = PrimaryTargetColor, label = "Primary target")
                LegendItem(color = SecondaryCoreColor, label = "Secondary / core")
                LegendItem(color = OtherMusclesColor, label = "Other muscles")
            }

            if (showTargetMusclesSection && targetEntries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.muscle_anatomy_target_muscles),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(targetEntries, key = { it.key.name }) { (group, role) ->
                        val pillColor = if (role == MuscleRole.PRIMARY) PrimaryTargetColor else SecondaryCoreColor
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = pillColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, pillColor.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clickable {
                                    isAutoRotating = false
                                    rotationAngle = if (group.isFront) 0f else 180f
                                    onMuscleSelected?.invoke(group)
                                }
                                .testTag("target_muscle_chip_${group.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(pillColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = group.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// -------------------------------------------------------------
// 3D Projection & Volumetric Canvas Rendering Engine
// -------------------------------------------------------------

private fun DrawScope.drawHolographicPedestal(cx: Float, h: Float, rotationAngle: Float) {
    val floorY = h * 0.94f
    val rx = h * 0.32f
    val ry = h * 0.065f

    drawOval(
        color = SecondaryCoreColor.copy(alpha = 0.10f),
        topLeft = Offset(cx - rx * 1.15f, floorY - ry * 1.15f),
        size = Size(rx * 2.3f, ry * 2.3f)
    )

    drawOval(
        color = SecondaryCoreColor.copy(alpha = 0.35f),
        topLeft = Offset(cx - rx, floorY - ry),
        size = Size(rx * 2f, ry * 2f),
        style = Stroke(width = 1.5f)
    )

    val rad = Math.toRadians(rotationAngle.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    drawLine(
        color = PrimaryTargetColor.copy(alpha = 0.50f),
        start = Offset(cx - rx * 0.85f * cosA, floorY - ry * 0.85f * sinA),
        end = Offset(cx + rx * 0.85f * cosA, floorY + ry * 0.85f * sinA),
        strokeWidth = 1.5f
    )

    drawOval(
        color = Color.Black.copy(alpha = 0.6f),
        topLeft = Offset(cx - rx * 0.45f, floorY - ry * 0.5f),
        size = Size(rx * 0.9f, ry * 1.0f)
    )
}

private fun DrawScope.draw3DAnatomyModel(
    cx: Float,
    h: Float,
    angle: Float,
    isFemale: Boolean,
    activeMuscles: Map<MuscleGroup, MuscleRole>,
    glowPulse: Float
) {
    val rad = Math.toRadians(angle.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()

    // 3D Depth Shading Helper with distinct Primary / Secondary / Base colors
    fun resolveColor(group: MuscleGroup, normalZ: Float): Color {
        val role = activeMuscles[group] ?: MuscleRole.NONE
        val lightFactor = (0.58f + 0.42f * normalZ).coerceIn(0.38f, 1f)

        return when (role) {
            MuscleRole.PRIMARY -> {
                Color(
                    red = (0.90f * lightFactor).coerceIn(0.55f, 1f),
                    green = (0.06f * lightFactor).coerceIn(0.03f, 0.2f),
                    blue = (0.10f * lightFactor).coerceIn(0.05f, 0.22f),
                    alpha = (0.96f * glowPulse).coerceIn(0.78f, 1f)
                )
            }
            MuscleRole.SECONDARY -> {
                Color(
                    red = (0.12f * lightFactor).coerceIn(0.05f, 0.35f),
                    green = (0.74f * lightFactor).coerceIn(0.45f, 0.92f),
                    blue = (0.98f * lightFactor).coerceIn(0.62f, 1f),
                    alpha = (0.92f * glowPulse).coerceIn(0.70f, 1f)
                )
            }
            MuscleRole.NONE -> {
                val base = 0.24f * lightFactor
                Color(red = base + 0.06f, green = base + 0.09f, blue = base + 0.14f, alpha = 0.96f)
            }
        }
    }

    val headY = h * 0.10f
    val headRadius = if (isFemale) h * 0.045f else h * 0.049f

    // 1. Head (Volumetric 3D Sphere with Specular Core)
    val headLightX = cx - headRadius * 0.3f * cosA
    drawCircle(
        color = Color(0xFF334155),
        radius = headRadius,
        center = Offset(cx, headY)
    )
    drawCircle(
        color = Color(0xFF64748B).copy(alpha = 0.45f),
        radius = headRadius * 0.65f,
        center = Offset(headLightX, headY - headRadius * 0.2f)
    )
    drawCircle(
        color = Color(0xFF1E293B),
        radius = headRadius,
        center = Offset(cx, headY),
        style = Stroke(width = 1.5f)
    )

    // Neck
    drawRoundRect(
        color = Color(0xFF283344),
        topLeft = Offset(cx - h * 0.02f, headY + headRadius * 0.75f),
        size = Size(h * 0.04f, h * 0.036f),
        cornerRadius = CornerRadius(4f, 4f)
    )

    // 3D Torso Widths & Gender Morphology
    val shoulderSpan = if (isFemale) h * 0.165f else h * 0.215f
    val waistSpan = if (isFemale) h * 0.11f else h * 0.135f
    val hipSpan = if (isFemale) h * 0.165f else h * 0.148f

    // 2. Trapezius / Upper Back (Posterior dominant)
    val trapNormalZ = -cosA
    if (trapNormalZ > -0.25f) {
        val trapColor = resolveColor(MuscleGroup.TRAPS, trapNormalZ)
        val trapWidth = shoulderSpan * 0.52f * abs(cosA).coerceAtLeast(0.32f)
        val path = Path().apply {
            moveTo(cx, headY + headRadius * 0.85f)
            lineTo(cx - trapWidth, h * 0.165f)
            lineTo(cx, h * 0.25f)
            lineTo(cx + trapWidth, h * 0.165f)
            close()
        }
        drawPath(path, trapColor)
    }

    // 3. Pectoralis Major (Anterior sculpted pec plates)
    val pecNormalZ = cosA
    if (pecNormalZ > -0.2f) {
        val chestColor = resolveColor(MuscleGroup.CHEST, pecNormalZ)
        val chestY = h * 0.168f
        val chestH = h * 0.072f
        val pecWidth = (shoulderSpan * 0.40f) * abs(cosA).coerceAtLeast(0.25f)

        val leftPec = Path().apply {
            moveTo(cx - 2f * cosA, chestY + 4f)
            lineTo(cx - pecWidth - 2f * cosA, chestY)
            lineTo(cx - pecWidth * 0.92f - 2f * cosA, chestY + chestH * 0.85f)
            lineTo(cx - 2f * cosA, chestY + chestH)
            close()
        }
        val rightPec = Path().apply {
            moveTo(cx + 2f * cosA, chestY + 4f)
            lineTo(cx + pecWidth + 2f * cosA, chestY)
            lineTo(cx + pecWidth * 0.92f + 2f * cosA, chestY + chestH * 0.85f)
            lineTo(cx + 2f * cosA, chestY + chestH)
            close()
        }
        drawPath(leftPec, chestColor)
        drawPath(rightPec, chestColor)
    }

    // 4. Latissimus Dorsi (Wide V-Taper Wings, Posterior dominant)
    val latNormalZ = -cosA
    if (latNormalZ > -0.35f) {
        val latColor = resolveColor(MuscleGroup.LATS, latNormalZ)
        val latY = h * 0.205f
        val latH = h * 0.105f
        val latW = shoulderSpan * 0.34f * abs(cosA).coerceAtLeast(0.32f)

        val leftLat = Path().apply {
            moveTo(cx - latW * 0.55f, latY)
            lineTo(cx - latW * 1.45f, latY + latH * 0.2f)
            lineTo(cx - waistSpan * 0.55f, latY + latH)
            lineTo(cx - latW * 0.45f, latY + latH * 0.9f)
            close()
        }
        val rightLat = Path().apply {
            moveTo(cx + latW * 0.55f, latY)
            lineTo(cx + latW * 1.45f, latY + latH * 0.2f)
            lineTo(cx + waistSpan * 0.55f, latY + latH)
            lineTo(cx + latW * 0.45f, latY + latH * 0.9f)
            close()
        }
        drawPath(leftLat, latColor)
        drawPath(rightLat, latColor)
    }

    // 4b. Lower Back / Erector Spinae (Posterior lumbar columns)
    if (latNormalZ > -0.15f) {
        val lowerBackColor = resolveColor(MuscleGroup.LOWER_BACK, latNormalZ)
        val lbY = h * 0.26f
        val lbH = h * 0.072f
        val lbW = waistSpan * 0.24f * abs(cosA).coerceAtLeast(0.28f)
        drawRoundRect(
            color = lowerBackColor,
            topLeft = Offset(cx - lbW - 2f, lbY),
            size = Size(lbW, lbH),
            cornerRadius = CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = lowerBackColor,
            topLeft = Offset(cx + 2f, lbY),
            size = Size(lbW, lbH),
            cornerRadius = CornerRadius(6f, 6f)
        )
    }

    // 5. Rectus Abdominis (6-Pack)
    if (pecNormalZ > -0.1f) {
        val absColor = resolveColor(MuscleGroup.ABS, pecNormalZ)
        val absY = h * 0.245f
        val abCellW = (waistSpan * 0.30f) * abs(cosA).coerceAtLeast(0.22f)
        val abCellH = h * 0.026f

        for (row in 0..2) {
            val rowY = absY + row * (abCellH + 3f)
            drawRoundRect(
                color = absColor,
                topLeft = Offset(cx - abCellW - 1.5f * cosA, rowY),
                size = Size(abCellW, abCellH),
                cornerRadius = CornerRadius(5f, 5f)
            )
            drawRoundRect(
                color = absColor,
                topLeft = Offset(cx + 1.5f * cosA, rowY),
                size = Size(abCellW, abCellH),
                cornerRadius = CornerRadius(5f, 5f)
            )
        }
    }

    // 6. External Obliques (Lateral Walls)
    val obliqueNormal = abs(sinA) + 0.4f * cosA
    val obliqueColor = resolveColor(MuscleGroup.OBLIQUES, obliqueNormal)
    val obY = h * 0.25f
    val obH = h * 0.08f
    val obW = waistSpan * 0.22f
    drawRoundRect(
        color = obliqueColor,
        topLeft = Offset(cx - waistSpan * 0.68f, obY),
        size = Size(obW, obH),
        cornerRadius = CornerRadius(6f, 6f)
    )
    drawRoundRect(
        color = obliqueColor,
        topLeft = Offset(cx + waistSpan * 0.46f, obY),
        size = Size(obW, obH),
        cornerRadius = CornerRadius(6f, 6f)
    )

    // 7. Shoulders (Deltoids - Full 3D Cap)
    val deltGroup = if (cosA >= 0f) MuscleGroup.SHOULDERS_FRONT else MuscleGroup.SHOULDERS_BACK
    val deltColor = resolveColor(deltGroup, abs(cosA))
    val shoulderY = h * 0.162f
    val shoulderW = h * 0.052f
    val shoulderH = h * 0.075f

    val leftShoulderX = cx - (shoulderSpan * 0.56f) - (shoulderSpan * 0.1f * sinA)
    val rightShoulderX = cx + (shoulderSpan * 0.44f) + (shoulderSpan * 0.1f * sinA)

    drawRoundRect(
        color = deltColor,
        topLeft = Offset(leftShoulderX, shoulderY),
        size = Size(shoulderW, shoulderH),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawRoundRect(
        color = deltColor,
        topLeft = Offset(rightShoulderX, shoulderY),
        size = Size(shoulderW, shoulderH),
        cornerRadius = CornerRadius(14f, 14f)
    )

    // 8. Upper Arms: Biceps (Anterior) vs Triceps (Posterior)
    val armGroup = if (cosA >= 0f) MuscleGroup.BICEPS else MuscleGroup.TRICEPS
    val armColor = resolveColor(armGroup, abs(cosA))
    val bicepY = shoulderY + shoulderH * 0.85f
    val armW = h * 0.042f
    val armH = h * 0.085f

    drawRoundRect(
        color = armColor,
        topLeft = Offset(leftShoulderX + 2f, bicepY),
        size = Size(armW, armH),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = armColor,
        topLeft = Offset(rightShoulderX + 2f, bicepY),
        size = Size(armW, armH),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Forearms
    val forearmColor = resolveColor(if (cosA >= 0f) MuscleGroup.FOREARMS_FRONT else MuscleGroup.FOREARMS_BACK, 0.7f)
    val forearmY = bicepY + armH
    val forearmH = h * 0.08f
    drawRoundRect(
        color = forearmColor,
        topLeft = Offset(leftShoulderX + 4f, forearmY),
        size = Size(armW * 0.85f, forearmH),
        cornerRadius = CornerRadius(8f, 8f)
    )
    drawRoundRect(
        color = forearmColor,
        topLeft = Offset(rightShoulderX + 4f, forearmY),
        size = Size(armW * 0.85f, forearmH),
        cornerRadius = CornerRadius(8f, 8f)
    )

    // 9. Glutes & Pelvis
    val pelvisY = h * 0.335f
    val gluteNormalZ = -cosA
    if (gluteNormalZ > -0.2f) {
        val gluteColor = resolveColor(MuscleGroup.GLUTES, gluteNormalZ)
        val gluteW = hipSpan * 0.45f * abs(cosA).coerceAtLeast(0.3f)
        val gluteH = h * 0.09f
        drawRoundRect(
            color = gluteColor,
            topLeft = Offset(cx - gluteW - 2f, pelvisY),
            size = Size(gluteW, gluteH),
            cornerRadius = CornerRadius(14f, 14f)
        )
        drawRoundRect(
            color = gluteColor,
            topLeft = Offset(cx + 2f, pelvisY),
            size = Size(gluteW, gluteH),
            cornerRadius = CornerRadius(14f, 14f)
        )
    } else {
        val pelvicColor = resolveColor(MuscleGroup.ABS, 0.6f)
        val pelvW = hipSpan * 0.40f * abs(cosA).coerceAtLeast(0.25f)
        drawRoundRect(
            color = pelvicColor,
            topLeft = Offset(cx - pelvW, pelvisY),
            size = Size(pelvW * 2f, h * 0.07f),
            cornerRadius = CornerRadius(8f, 8f)
        )
    }

    // 10. Thighs: Quadriceps (Anterior) vs Hamstrings (Posterior)
    val thighGroup = if (cosA >= 0f) MuscleGroup.QUADS else MuscleGroup.HAMSTRINGS
    val thighColor = resolveColor(thighGroup, abs(cosA))
    val thighY = pelvisY + h * 0.085f
    val thighW = h * 0.062f
    val thighH = h * 0.17f
    val legGap = h * 0.022f

    drawRoundRect(
        color = thighColor,
        topLeft = Offset(cx - thighW - legGap * 0.5f, thighY),
        size = Size(thighW, thighH),
        cornerRadius = CornerRadius(14f, 14f)
    )
    drawRoundRect(
        color = thighColor,
        topLeft = Offset(cx + legGap * 0.5f, thighY),
        size = Size(thighW, thighH),
        cornerRadius = CornerRadius(14f, 14f)
    )

    // Knees (Patellar Joint)
    val kneeY = thighY + thighH
    val kneeH = h * 0.035f
    val kneeW = thighW * 0.8f
    drawRoundRect(
        color = Color(0xFF283344),
        topLeft = Offset(cx - thighW - legGap * 0.5f + (thighW - kneeW) / 2f, kneeY),
        size = Size(kneeW, kneeH),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawRoundRect(
        color = Color(0xFF283344),
        topLeft = Offset(cx + legGap * 0.5f + (thighW - kneeW) / 2f, kneeY),
        size = Size(kneeW, kneeH),
        cornerRadius = CornerRadius(5f, 5f)
    )

    // 11. Calves / Lower Legs
    val calfGroup = if (cosA >= 0f) MuscleGroup.CALVES_FRONT else MuscleGroup.CALVES_BACK
    val calfColor = resolveColor(calfGroup, abs(cosA))
    val calfY = kneeY + kneeH
    val calfW = thighW * 0.85f
    val calfH = h * 0.16f

    drawRoundRect(
        color = calfColor,
        topLeft = Offset(cx - thighW - legGap * 0.5f + (thighW - calfW) / 2f, calfY),
        size = Size(calfW, calfH),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawRoundRect(
        color = calfColor,
        topLeft = Offset(cx + legGap * 0.5f + (thighW - calfW) / 2f, calfY),
        size = Size(calfW, calfH),
        cornerRadius = CornerRadius(12f, 12f)
    )

    // Feet
    val footY = calfY + calfH
    val footH = h * 0.025f
    val footW = calfW * 1.15f
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(cx - thighW - legGap * 0.5f, footY),
        size = Size(footW, footH),
        cornerRadius = CornerRadius(4f, 4f)
    )
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(cx + legGap * 0.5f, footY),
        size = Size(footW, footH),
        cornerRadius = CornerRadius(4f, 4f)
    )
}
