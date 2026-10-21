package com.example.ui.avatar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Living face avatar featuring classic expressive facial elements:
 * - Eyes with natural gaze saccades, mouse/pointer tracking, and auto-blinking
 * - Eyebrows with emotional tilts (idle, thinking, speaking, happy)
 * - Mouth that moves dynamically when speaking/answering
 */
@Composable
fun LivingFace(
    mood: AvatarMood,
    isThinking: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    targetGaze: Offset? = null,
    onTap: (() -> Unit)? = null
) {
    // Gaze offsets (-1f to 1f)
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }

    // Blink progress: 0f = open, 1f = fully closed
    val blinkProgress = remember { Animatable(0f) }

    // Eyebrow and mouth animation drivers
    val infiniteTransition = rememberInfiniteTransition(label = "facial_ambient")

    // Speech mouth oscillation (active when speaking)
    val speechPulse by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speech_pulse"
    )

    // Gentle ambient breathing / subtle head micro-movement
    val ambientBreath by infiniteTransition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_breath"
    )

    // Pointer / Mouse Tracking or Autonomous Eye Gaze Saccades
    LaunchedEffect(targetGaze, isThinking, mood) {
        if (targetGaze != null) {
            // Smoothly track the mouse cursor / pointer location
            launch { gazeX.animateTo(targetGaze.x.coerceIn(-0.95f, 0.95f), tween(110, easing = FastOutSlowInEasing)) }
            launch { gazeY.animateTo(targetGaze.y.coerceIn(-0.85f, 0.85f), tween(110, easing = FastOutSlowInEasing)) }
        } else {
            // Autonomous lifelike gaze wander
            while (isActive) {
                if (isThinking) {
                    launch { gazeX.animateTo(0.35f, tween(300)) }
                    launch { gazeY.animateTo(-0.65f, tween(300)) }
                    delay(1200)
                    launch { gazeX.animateTo(-0.25f, tween(400)) }
                    delay(1400)
                } else {
                    val nextX = (Random.nextFloat() * 1.3f - 0.65f)
                    val nextY = (Random.nextFloat() * 0.7f - 0.35f)
                    launch {
                        gazeX.animateTo(
                            targetValue = nextX,
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        gazeY.animateTo(
                            targetValue = nextY,
                            animationSpec = tween(
                                durationMillis = 350,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    delay(Random.nextLong(1800, 3800))
                }
            }
        }
    }

    // Auto-blinking loop with natural variance
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(Random.nextLong(2400, 4800))
            blinkProgress.animateTo(1f, tween(75, easing = LinearEasing))
            blinkProgress.animateTo(0f, tween(95, easing = FastOutSlowInEasing))

            if (Random.nextFloat() < 0.25f) {
                delay(130)
                blinkProgress.animateTo(1f, tween(60, easing = LinearEasing))
                blinkProgress.animateTo(0f, tween(85, easing = FastOutSlowInEasing))
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    var isWinking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag("living_face_canvas_container")
            .then(
                if (onTap != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTap()
                        coroutineScope.launch {
                            isWinking = true
                            blinkProgress.animateTo(1f, tween(110))
                            delay(160)
                            blinkProgress.animateTo(0f, tween(120))
                            isWinking = false
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val centerX = canvasW / 2f
            val centerY = canvasH / 2f + ambientBreath

            val eyeSpacing = canvasW * 0.28f
            val eyeWidth = canvasW * 0.22f
            val eyeHeight = eyeWidth * 0.95f
            val eyeCenterY = centerY - canvasH * 0.08f

            val leftEyeCenter = Offset(centerX - eyeSpacing / 2f, eyeCenterY)
            val rightEyeCenter = Offset(centerX + eyeSpacing / 2f, eyeCenterY)

            val effectiveMood = when {
                isSpeaking -> AvatarMood.SPEAKING
                isThinking -> AvatarMood.THINKING
                isWinking -> AvatarMood.HAPPY
                else -> mood
            }

            // 1. Eyebrows
            drawEyebrows(
                centerX = centerX,
                eyeCenterY = eyeCenterY,
                eyeSpacing = eyeSpacing,
                eyeWidth = eyeWidth,
                mood = effectiveMood,
                ambient = ambientBreath
            )

            // 2. Eyes
            drawEye(
                center = leftEyeCenter,
                width = eyeWidth,
                height = eyeHeight,
                gazeX = gazeX.value,
                gazeY = gazeY.value,
                blinkProgress = blinkProgress.value,
                mood = effectiveMood,
                isLeft = true
            )
            drawEye(
                center = rightEyeCenter,
                width = eyeWidth,
                height = eyeHeight,
                gazeX = gazeX.value,
                gazeY = gazeY.value,
                blinkProgress = blinkProgress.value,
                mood = effectiveMood,
                isLeft = false
            )

            // 3. Mouth
            drawMouth(
                centerX = centerX,
                mouthY = centerY + canvasH * 0.24f,
                mouthWidth = canvasW * 0.32f,
                mood = effectiveMood,
                speechPulse = speechPulse
            )
        }
    }
}

/**
 * Draws classic expressive eyebrows
 */
private fun DrawScope.drawEyebrows(
    centerX: Float,
    eyeCenterY: Float,
    eyeSpacing: Float,
    eyeWidth: Float,
    mood: AvatarMood,
    ambient: Float
) {
    val browWidth = eyeWidth * 0.92f
    val browBaseY = eyeCenterY - eyeWidth * 0.62f + ambient * 0.5f
    val strokeWidth = eyeWidth * 0.12f
    val browColor = Color(0xFFE2E8F0)

    val leftOuterX = (centerX - eyeSpacing / 2f) - browWidth / 2f
    val leftInnerX = (centerX - eyeSpacing / 2f) + browWidth / 2f

    val rightInnerX = (centerX + eyeSpacing / 2f) - browWidth / 2f
    val rightOuterX = (centerX + eyeSpacing / 2f) + browWidth / 2f

    val (leftYOffsetOuter, leftYOffsetInner, rightYOffsetInner, rightYOffsetOuter) = when (mood) {
        AvatarMood.IDLE -> listOf(0f, -4f, -4f, 0f)
        AvatarMood.THINKING -> listOf(4f, -14f, 2f, 8f)
        AvatarMood.SPEAKING -> listOf(-4f, -10f, -10f, -4f)
        AvatarMood.HAPPY -> listOf(-2f, -12f, -12f, -2f)
    }

    val leftPath = Path().apply {
        moveTo(leftOuterX, browBaseY + leftYOffsetOuter)
        quadraticTo(
            (leftOuterX + leftInnerX) / 2f,
            browBaseY + leftYOffsetInner - 6f,
            leftInnerX,
            browBaseY + leftYOffsetInner
        )
    }

    val rightPath = Path().apply {
        moveTo(rightInnerX, browBaseY + rightYOffsetInner)
        quadraticTo(
            (rightInnerX + rightOuterX) / 2f,
            browBaseY + rightYOffsetInner - 6f,
            rightOuterX,
            browBaseY + rightYOffsetOuter
        )
    }

    drawPath(
        path = leftPath,
        color = browColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = rightPath,
        color = browColor,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/**
 * Draws an eye with glowing iris, pupil, specular highlights, and eyelid clipping
 */
private fun DrawScope.drawEye(
    center: Offset,
    width: Float,
    height: Float,
    gazeX: Float,
    gazeY: Float,
    blinkProgress: Float,
    mood: AvatarMood,
    isLeft: Boolean
) {
    if (blinkProgress >= 0.92f) {
        val lashPath = Path().apply {
            moveTo(center.x - width / 2f, center.y)
            quadraticTo(
                center.x,
                center.y + height * 0.28f,
                center.x + width / 2f,
                center.y
            )
        }
        drawPath(
            path = lashPath,
            color = Color(0xFFCBD5E1),
            style = Stroke(width = width * 0.12f, cap = StrokeCap.Round)
        )
        return
    }

    val eyeRect = Rect(
        left = center.x - width / 2f,
        top = center.y - height / 2f,
        right = center.x + width / 2f,
        bottom = center.y + height / 2f
    )

    val eyeClipPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = eyeRect,
                cornerRadius = CornerRadius(width * 0.45f, height * 0.45f)
            )
        )
    }

    clipPath(eyeClipPath) {
        // Sclera (White eye background)
        drawRect(
            color = Color(0xFFF8FAFC),
            topLeft = Offset(eyeRect.left, eyeRect.top),
            size = Size(eyeRect.width, eyeRect.height)
        )

        // Pupil / Iris offset from gaze
        val maxGazeOffsetX = width * 0.22f
        val maxGazeOffsetY = height * 0.20f
        val pupilCenter = Offset(
            x = center.x + gazeX * maxGazeOffsetX,
            y = center.y + gazeY * maxGazeOffsetY
        )

        val irisRadius = width * 0.38f
        val pupilRadius = irisRadius * 0.52f

        // Vibrant Iris Gradient: Electric Cyan/Azure
        val irisBrush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF22D3EE),
                Color(0xFF0284C7),
                Color(0xFF0F172A)
            ),
            center = pupilCenter,
            radius = irisRadius
        )

        drawCircle(
            brush = irisBrush,
            radius = irisRadius,
            center = pupilCenter
        )

        // Deep Pupil
        drawCircle(
            color = Color(0xFF090D16),
            radius = pupilRadius,
            center = pupilCenter
        )

        // Specular Sparkle Highlights
        val highlight1Center = Offset(
            x = pupilCenter.x - pupilRadius * 0.42f,
            y = pupilCenter.y - pupilRadius * 0.42f
        )
        drawCircle(
            color = Color.White,
            radius = pupilRadius * 0.36f,
            center = highlight1Center
        )

        val highlight2Center = Offset(
            x = pupilCenter.x + pupilRadius * 0.42f,
            y = pupilCenter.y + pupilRadius * 0.32f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = pupilRadius * 0.18f,
            center = highlight2Center
        )

        // Eyelid dropping down on blink
        if (blinkProgress > 0f) {
            val lidHeight = eyeRect.height * blinkProgress
            drawRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(eyeRect.left - 2f, eyeRect.top - 2f),
                size = Size(eyeRect.width + 4f, lidHeight)
            )
        }
    }

    // Outer soft glow outline
    drawRoundRect(
        color = Color(0xFF38BDF8).copy(alpha = 0.55f),
        topLeft = Offset(eyeRect.left, eyeRect.top),
        size = Size(eyeRect.width, eyeRect.height),
        cornerRadius = CornerRadius(width * 0.45f, height * 0.45f),
        style = Stroke(width = 2.dp.toPx())
    )
}

/**
 * Draws the mouth with dynamic speech movement, smile, and thinking states
 */
private fun DrawScope.drawMouth(
    centerX: Float,
    mouthY: Float,
    mouthWidth: Float,
    mood: AvatarMood,
    speechPulse: Float
) {
    val mouthColor = Color(0xFFE2E8F0)
    val strokeWidth = mouthWidth * 0.08f

    when (mood) {
        AvatarMood.SPEAKING -> {
            val openH = mouthWidth * (0.20f + speechPulse * 0.32f)
            val openW = mouthWidth * (0.75f + speechPulse * 0.15f)

            val mouthRect = Rect(
                left = centerX - openW / 2f,
                top = mouthY - openH / 2f,
                right = centerX + openW / 2f,
                bottom = mouthY + openH / 2f
            )

            drawRoundRect(
                color = Color(0xFF1E293B),
                topLeft = Offset(mouthRect.left, mouthRect.top),
                size = Size(mouthRect.width, mouthRect.height),
                cornerRadius = CornerRadius(openW * 0.5f, openH * 0.5f)
            )

            drawRoundRect(
                color = Color(0xFFF43F5E).copy(alpha = 0.7f),
                topLeft = Offset(mouthRect.left + openW * 0.2f, mouthY + openH * 0.05f),
                size = Size(openW * 0.6f, openH * 0.4f),
                cornerRadius = CornerRadius(openW * 0.3f, openH * 0.2f)
            )

            drawRoundRect(
                color = mouthColor,
                topLeft = Offset(mouthRect.left, mouthRect.top),
                size = Size(mouthRect.width, mouthRect.height),
                cornerRadius = CornerRadius(openW * 0.5f, openH * 0.5f),
                style = Stroke(width = strokeWidth)
            )
        }

        AvatarMood.THINKING -> {
            val thinkW = mouthWidth * 0.55f
            val path = Path().apply {
                moveTo(centerX - thinkW / 2f, mouthY)
                quadraticTo(
                    centerX,
                    mouthY + 4f,
                    centerX + thinkW / 2f,
                    mouthY - 2f
                )
            }
            drawPath(
                path = path,
                color = mouthColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        AvatarMood.HAPPY -> {
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.5f, mouthY - 4f)
                quadraticTo(
                    centerX,
                    mouthY + mouthWidth * 0.38f,
                    centerX + mouthWidth * 0.5f,
                    mouthY - 4f
                )
            }
            drawPath(
                path = path,
                color = Color(0xFF38BDF8),
                style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round)
            )
        }

        AvatarMood.IDLE -> {
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.45f, mouthY)
                quadraticTo(
                    centerX,
                    mouthY + mouthWidth * 0.20f,
                    centerX + mouthWidth * 0.45f,
                    mouthY
                )
            }
            drawPath(
                path = path,
                color = mouthColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Compact animated eyes for the ChatHeader.
 * Fits cleanly where "Lynia" was, blinks naturally, and looks around.
 * Clicking opens the dedicated companion screen.
 */
@Composable
fun MiniHeaderEyes(
    onClick: () -> Unit,
    isThinking: Boolean = false,
    isSpeaking: Boolean = false,
    modifier: Modifier = Modifier
) {
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    val blinkProgress = remember { Animatable(0f) }

    LaunchedEffect(isThinking) {
        while (isActive) {
            val nextX = if (isThinking) 0.35f else (Random.nextFloat() * 1.2f - 0.6f)
            val nextY = if (isThinking) -0.4f else (Random.nextFloat() * 0.6f - 0.3f)
            gazeX.animateTo(nextX, tween(300, easing = FastOutSlowInEasing))
            gazeY.animateTo(nextY, tween(300, easing = FastOutSlowInEasing))
            delay(Random.nextLong(2200, 4000))
        }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(Random.nextLong(2800, 5000))
            blinkProgress.animateTo(1f, tween(70))
            blinkProgress.animateTo(0f, tween(90))
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DarkSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = modifier
            .height(38.dp)
            .clickable(onClick = onClick)
            .testTag("mini_avatar_header_button")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .width(34.dp)
                    .height(18.dp)
            ) {
                val w = size.width
                val h = size.height
                val eyeW = 12.dp.toPx()
                val eyeH = 12.dp.toPx()
                val leftCenter = Offset(w * 0.28f, h / 2f)
                val rightCenter = Offset(w * 0.72f, h / 2f)

                listOf(leftCenter, rightCenter).forEach { eyeC ->
                    if (blinkProgress.value >= 0.85f) {
                        drawLine(
                            color = Color.White,
                            start = Offset(eyeC.x - eyeW / 2f, eyeC.y),
                            end = Offset(eyeC.x + eyeW / 2f, eyeC.y),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    } else {
                        drawRoundRect(
                            color = Color(0xFFF1F5F9),
                            topLeft = Offset(eyeC.x - eyeW / 2f, eyeC.y - eyeH / 2f),
                            size = Size(eyeW, eyeH),
                            cornerRadius = CornerRadius(eyeW * 0.45f, eyeH * 0.45f)
                        )

                        val pupilOffsetX = gazeX.value * 2.5f
                        val pupilOffsetY = gazeY.value * 2.0f
                        val pCenter = Offset(eyeC.x + pupilOffsetX, eyeC.y + pupilOffsetY)

                        drawCircle(
                            color = Color(0xFF0284C7),
                            radius = eyeW * 0.32f,
                            center = pCenter
                        )
                        drawCircle(
                            color = Color(0xFF090D16),
                            radius = eyeW * 0.18f,
                            center = pCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = eyeW * 0.09f,
                            center = Offset(pCenter.x - 1.5f, pCenter.y - 1.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "Lynia",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                ),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.width(5.dp))

            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSpeaking) Color(0xFF38BDF8)
                        else if (isThinking) Color(0xFFF59E0B)
                        else Color(0xFF10B981)
                    )
            )
        }
    }
}
