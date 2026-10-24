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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
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
    customLeftGesture: HandGesture? = null,
    customRightGesture: HandGesture? = null,
    onTap: (() -> Unit)? = null,
    onHandTap: ((isLeftHand: Boolean) -> Unit)? = null
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

    // Finger flutter ripple animation for air-typing and natural idle fidgets
    val flutterAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(260, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "finger_flutter"
    )

    // Thinking cheek tap oscillation
    val thinkingTapAnim by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_tap"
    )

    // Clapping rhythmic pulse (hands meet in center and bounce back)
    val clappingPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(190, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "clapping_pulse"
    )

    // Organic out-of-phase floating for living hands
    val idleFloatLeft by infiniteTransition.animateFloat(
        initialValue = -3.2f,
        targetValue = 3.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_float_l"
    )
    val idleFloatRight by infiniteTransition.animateFloat(
        initialValue = 3.2f,
        targetValue = -3.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_float_r"
    )

    // Conversational gesture cycle when speaking
    var speechGesturePhase by remember { mutableStateOf(0) }
    LaunchedEffect(isSpeaking) {
        if (isSpeaking) {
            speechGesturePhase = 0
            while (isActive) {
                delay(1400)
                speechGesturePhase = (speechGesturePhase + 1) % 3
            }
        } else {
            speechGesturePhase = 0
        }
    }

    // Pointer / Mouse Tracking, Real-time Text Reading Saccades, or Autonomous Eye Wander
    LaunchedEffect(targetGaze, isThinking, mood) {
        if (targetGaze != null && targetGaze.y > 0.65f) {
            // User is typing! Active realistic reading saccade:
            // Eyes look down toward input box (gazeY = 0.82f) and sweep horizontally from left to right as if reading words!
            launch { gazeY.animateTo(0.82f, tween(110, easing = FastOutSlowInEasing)) }
            while (isActive) {
                gazeX.snapTo(-0.28f)
                gazeX.animateTo(0.28f, tween(850, easing = LinearEasing))
                delay(120)
                gazeX.animateTo(-0.28f, tween(90, easing = FastOutSlowInEasing))
                delay(80)
            }
        } else if (targetGaze != null) {
            // Instant, ultra-responsive mouse cursor tracking
            launch { gazeX.animateTo(targetGaze.x.coerceIn(-0.95f, 0.95f), tween(70, easing = LinearEasing)) }
            launch { gazeY.animateTo(targetGaze.y.coerceIn(-0.85f, 0.85f), tween(70, easing = LinearEasing)) }
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

    // Floating Hand animation drivers (-1f to 1f relative to canvas center and dimensions)
    // Default resting stance: Hands hang DOWNWARDS at sides
    val leftFactorX = remember { Animatable(-0.38f) }
    val leftFactorY = remember { Animatable(0.22f) }
    val leftRot = remember { Animatable(6f) }

    val rightFactorX = remember { Animatable(0.38f) }
    val rightFactorY = remember { Animatable(0.22f) }
    val rightRot = remember { Animatable(-6f) }

    val waveAnim = remember { Animatable(0f) }
    var isWaving by remember { mutableStateOf(false) }
    val doubleWaveAnim = remember { Animatable(0f) }
    var isDoubleWaving by remember { mutableStateOf(false) }

    // Initial greeting wave when companion face first appears
    LaunchedEffect(Unit) {
        delay(400)
        isWaving = true
        waveAnim.animateTo(24f, tween(160, easing = FastOutSlowInEasing))
        waveAnim.animateTo(-18f, tween(160, easing = FastOutSlowInEasing))
        waveAnim.animateTo(22f, tween(160, easing = FastOutSlowInEasing))
        waveAnim.animateTo(-16f, tween(160, easing = FastOutSlowInEasing))
        waveAnim.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
        isWaving = false
    }

    // Periodic idle micro-fidgets (subtle natural wrist flexes)
    LaunchedEffect(mood, isThinking, isSpeaking) {
        if (!isThinking && !isSpeaking && mood == AvatarMood.IDLE) {
            while (isActive) {
                delay(Random.nextLong(4500, 8500))
                launch {
                    leftRot.animateTo(13f, tween(320, easing = FastOutSlowInEasing))
                    leftRot.animateTo(6f, tween(380, easing = FastOutSlowInEasing))
                }
                delay(220)
                launch {
                    rightRot.animateTo(-13f, tween(320, easing = FastOutSlowInEasing))
                    rightRot.animateTo(-6f, tween(380, easing = FastOutSlowInEasing))
                }
            }
        }
    }

    // Dynamic hand posing based on mood & state
    val isLookingAtTyping = targetGaze != null && targetGaze.y > 0.65f
    val isPointingLeft = targetGaze != null && targetGaze.x < -0.4f && !isLookingAtTyping
    val isPointingRight = targetGaze != null && targetGaze.x > 0.4f && !isLookingAtTyping

    val effectiveMood = when {
        isWinking -> AvatarMood.WINKING
        isSpeaking -> AvatarMood.SPEAKING
        isThinking -> AvatarMood.THINKING
        else -> mood
    }

    val (leftGesture, rightGesture) = when {
        customLeftGesture != null && customRightGesture != null ->
            Pair(customLeftGesture, customRightGesture)
        customLeftGesture != null ->
            Pair(customLeftGesture, HandGesture.DEFAULT_DOWN)
        customRightGesture != null ->
            Pair(HandGesture.DEFAULT_DOWN, customRightGesture)
        isWaving ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.WAVING)
        isDoubleWaving ->
            Pair(HandGesture.DOUBLE_WAVE, HandGesture.DOUBLE_WAVE)
        isThinking ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.THINKING)
        isLookingAtTyping ->
            Pair(HandGesture.TYPING_MIMIC, HandGesture.TYPING_MIMIC)
        isSpeaking -> when (speechGesturePhase) {
            0 -> Pair(HandGesture.SPEAKING_DOWN, HandGesture.PALMS_UP)
            1 -> Pair(HandGesture.PALMS_UP, HandGesture.SPEAKING_DOWN)
            else -> Pair(HandGesture.PALMS_UP, HandGesture.POINTING_UP)
        }
        effectiveMood == AvatarMood.HAPPY ->
            Pair(HandGesture.CLAPPING, HandGesture.CLAPPING)
        effectiveMood == AvatarMood.EXCITED ->
            Pair(HandGesture.DOUBLE_WAVE, HandGesture.DOUBLE_WAVE)
        effectiveMood == AvatarMood.SURPRISED ->
            Pair(HandGesture.CHEEK_TOUCH, HandGesture.CHEEK_TOUCH)
        effectiveMood == AvatarMood.CONFUSED ->
            Pair(HandGesture.SHRUG, HandGesture.SHRUG)
        effectiveMood == AvatarMood.WINKING ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.PEACE_SIGN)
        effectiveMood == AvatarMood.GIGGLING ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.COVER_MOUTH)
        effectiveMood == AvatarMood.HEART ->
            Pair(HandGesture.HEART_SIDE, HandGesture.HEART_SIDE)
        effectiveMood == AvatarMood.SLEEPY ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.SLEEPY_RUB)
        isPointingLeft ->
            Pair(HandGesture.POINTING_AT_USER, HandGesture.DEFAULT_DOWN)
        isPointingRight ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.POINTING_AT_USER)
        else ->
            Pair(HandGesture.DEFAULT_DOWN, HandGesture.DEFAULT_DOWN)
    }

    val isClapping = leftGesture == HandGesture.CLAPPING && rightGesture == HandGesture.CLAPPING

    LaunchedEffect(effectiveMood, isThinking, isWaving, isDoubleWaving, isSpeaking, isLookingAtTyping, isPointingLeft, isPointingRight, leftGesture, rightGesture) {
        val (targetLX, targetLY, targetLRot) = when {
            isClapping -> Triple(-0.16f, 0.08f, 28f)
            leftGesture == HandGesture.PALMS_UP -> Triple(-0.32f, 0.12f, -16f)
            leftGesture == HandGesture.PEACE_SIGN -> Triple(-0.32f, -0.04f, -10f)
            leftGesture == HandGesture.OK_SIGN -> Triple(-0.32f, 0.02f, -10f)
            leftGesture == HandGesture.POINTING_UP -> Triple(-0.28f, -0.04f, -6f)
            leftGesture == HandGesture.POINTING_AT_USER -> Triple(-0.38f, 0.12f, 18f)
            leftGesture == HandGesture.FIST_BUMP -> Triple(-0.24f, 0.10f, 4f)
            leftGesture == HandGesture.DOUBLE_WAVE || isDoubleWaving -> Triple(-0.34f, -0.06f, -14f)
            leftGesture == HandGesture.TYPING_MIMIC || isLookingAtTyping -> Triple(-0.24f, 0.25f, -4f)
            effectiveMood == AvatarMood.EXCITED || effectiveMood == AvatarMood.HAPPY -> Triple(-0.32f, -0.02f, -14f)
            effectiveMood == AvatarMood.SURPRISED -> Triple(-0.28f, 0.08f, -18f)
            effectiveMood == AvatarMood.CONFUSED -> Triple(-0.38f, 0.14f, 26f)
            effectiveMood == AvatarMood.HEART -> Triple(-0.14f, 0.22f, 24f)
            isSpeaking -> Triple(-0.38f, 0.20f, 10f)
            else -> Triple(-0.38f, 0.22f, 6f)
        }

        val (targetRX, targetRY, targetRRot) = when {
            isClapping -> Triple(0.16f, 0.08f, -28f)
            isWaving -> Triple(0.38f, -0.06f, 0f)
            isDoubleWaving || rightGesture == HandGesture.DOUBLE_WAVE -> Triple(0.34f, -0.06f, 14f)
            isThinking || rightGesture == HandGesture.THINKING -> Triple(0.18f, 0.20f, -22f)
            rightGesture == HandGesture.PEACE_SIGN -> Triple(0.32f, -0.04f, 10f)
            rightGesture == HandGesture.OK_SIGN -> Triple(0.32f, 0.02f, 10f)
            rightGesture == HandGesture.POINTING_UP -> Triple(0.28f, -0.04f, 6f)
            rightGesture == HandGesture.POINTING_AT_USER -> Triple(0.38f, 0.12f, -18f)
            rightGesture == HandGesture.PALMS_UP -> Triple(0.32f, 0.12f, 16f)
            rightGesture == HandGesture.FIST_BUMP -> Triple(0.24f, 0.10f, -4f)
            rightGesture == HandGesture.SLEEPY_RUB -> Triple(0.16f, -0.06f, -18f)
            rightGesture == HandGesture.TYPING_MIMIC || isLookingAtTyping -> Triple(0.24f, 0.25f, 4f)
            effectiveMood == AvatarMood.EXCITED || effectiveMood == AvatarMood.HAPPY -> Triple(0.32f, -0.02f, 14f)
            effectiveMood == AvatarMood.SURPRISED -> Triple(0.28f, 0.08f, 18f)
            effectiveMood == AvatarMood.CONFUSED -> Triple(0.38f, 0.14f, -26f)
            effectiveMood == AvatarMood.WINKING -> Triple(0.34f, 0.06f, -10f)
            effectiveMood == AvatarMood.GIGGLING -> Triple(0.14f, 0.22f, -16f)
            effectiveMood == AvatarMood.HEART -> Triple(0.14f, 0.22f, -24f)
            isSpeaking -> Triple(0.38f, 0.20f, -10f)
            else -> Triple(0.38f, 0.22f, -6f)
        }

        val duration = if (isThinking || isLookingAtTyping || isClapping) 300 else 220
        launch { leftFactorX.animateTo(targetLX, tween(duration, easing = FastOutSlowInEasing)) }
        launch { leftFactorY.animateTo(targetLY, tween(duration, easing = FastOutSlowInEasing)) }
        launch { leftRot.animateTo(targetLRot, tween(duration, easing = FastOutSlowInEasing)) }

        launch { rightFactorX.animateTo(targetRX, tween(duration, easing = FastOutSlowInEasing)) }
        launch { rightFactorY.animateTo(targetRY, tween(duration, easing = FastOutSlowInEasing)) }
        if (!isWaving && !isDoubleWaving) {
            launch { rightRot.animateTo(targetRRot, tween(duration, easing = FastOutSlowInEasing)) }
        }
    }

    Box(
        modifier = modifier
            .testTag("living_face_canvas_container")
            .then(
                if (onTap != null || onHandTap != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val isLeftArea = offset.x < size.width * 0.35f
                            val isRightArea = offset.x > size.width * 0.65f
                            if (isLeftArea) {
                                if (onHandTap != null) onHandTap(true) else onTap?.invoke()
                            } else if (isRightArea) {
                                if (onHandTap != null) onHandTap(false) else onTap?.invoke()
                            } else {
                                onTap?.invoke()
                            }
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

            // 1. Floating Living Hands
            val handW = canvasW * 0.175f
            val handH = handW * 1.28f

            val isClapActive = leftGesture == HandGesture.CLAPPING && rightGesture == HandGesture.CLAPPING
            val clapDistance = if (isClapActive) clappingPulse * 0.06f else 0f

            val leftHandCenter = Offset(
                x = centerX + (leftFactorX.value + clapDistance) * canvasW,
                y = centerY + leftFactorY.value * canvasH + if (isSpeaking) -speechPulse * 5f else idleFloatLeft
            )
            val currentLeftRot = leftRot.value + (if (isDoubleWaving) -waveAnim.value else 0f) + (if (isSpeaking) -speechPulse * 8f else ambientBreath * 1.2f)

            val rightHandCenter = Offset(
                x = centerX + (rightFactorX.value - clapDistance) * canvasW,
                y = centerY + rightFactorY.value * canvasH + if (isSpeaking) -(1f - speechPulse) * 5f else idleFloatRight
            )
            val currentRightRot = (if (isWaving || isDoubleWaving) waveAnim.value else rightRot.value) + (if (isSpeaking) (1f - speechPulse) * 8f else -ambientBreath * 1.2f)

            drawHumanHand(
                wristCenter = leftHandCenter,
                handWidth = handW,
                handHeight = handH,
                rotationDegrees = currentLeftRot,
                isLeft = true,
                gesture = leftGesture,
                speechPulse = speechPulse,
                fingerFlutter = flutterAnim,
                thinkingTap = thinkingTapAnim,
                clapProgress = clappingPulse
            )

            drawHumanHand(
                wristCenter = rightHandCenter,
                handWidth = handW,
                handHeight = handH,
                rotationDegrees = currentRightRot,
                isLeft = false,
                gesture = rightGesture,
                speechPulse = speechPulse,
                fingerFlutter = flutterAnim,
                thinkingTap = thinkingTapAnim,
                clapProgress = clappingPulse
            )

            // 2. Rosy Cheek Blush (on Happy, Excited, Giggling, or Heart)
            drawCheekBlush(
                centerX = centerX,
                centerY = centerY,
                canvasW = canvasW,
                canvasH = canvasH,
                mood = effectiveMood
            )

            // 2. Eyebrows
            drawEyebrows(
                centerX = centerX,
                eyeCenterY = eyeCenterY,
                eyeSpacing = eyeSpacing,
                eyeWidth = eyeWidth,
                mood = effectiveMood,
                ambient = ambientBreath
            )

            // 3. Eyes
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

            // 4. Mouth
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
 * Draws expressive eyebrows matching all 11 AvatarMoods
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
        AvatarMood.HAPPY, AvatarMood.EXCITED -> listOf(-6f, -16f, -16f, -6f)
        AvatarMood.SURPRISED -> listOf(-10f, -22f, -22f, -10f)
        AvatarMood.CONFUSED -> listOf(-12f, -18f, 4f, 6f)
        AvatarMood.WINKING -> listOf(-8f, -14f, -2f, 2f)
        AvatarMood.GIGGLING -> listOf(-4f, -12f, -12f, -4f)
        AvatarMood.SLEEPY -> listOf(6f, 2f, 2f, 6f)
        AvatarMood.HEART -> listOf(-4f, -14f, -14f, -4f)
        AvatarMood.READING -> listOf(-2f, -13f, -13f, -2f)
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
 * Draws an eye with glowing iris, pupil, specular highlights, and eyelid clipping,
 * with special renderings for happy/excited crescents, winks, sleepiness, and surprise.
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
    // 1. Natural Blink or Winking Right Eye
    val isWinkingThisEye = mood == AvatarMood.WINKING && !isLeft
    if (blinkProgress >= 0.92f || isWinkingThisEye) {
        val lashPath = Path().apply {
            moveTo(center.x - width / 2f, center.y)
            quadraticTo(
                center.x,
                center.y + height * (if (isWinkingThisEye) 0.32f else 0.28f),
                center.x + width / 2f,
                center.y
            )
        }
        drawPath(
            path = lashPath,
            color = if (isWinkingThisEye) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
            style = Stroke(width = width * 0.14f, cap = StrokeCap.Round)
        )
        return
    }

    // 2. Joyful Smiling Crescent Eyes (Happy, Excited, Giggling)
    if (mood == AvatarMood.HAPPY || mood == AvatarMood.EXCITED || mood == AvatarMood.GIGGLING) {
        val smileEyePath = Path().apply {
            moveTo(center.x - width / 2f, center.y + height * 0.14f)
            quadraticTo(
                center.x,
                center.y - height * 0.38f,
                center.x + width / 2f,
                center.y + height * 0.14f
            )
        }
        drawPath(
            path = smileEyePath,
            color = Color(0xFF38BDF8),
            style = Stroke(width = width * 0.15f, cap = StrokeCap.Round)
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

        val irisRadius = width * (if (mood == AvatarMood.SURPRISED) 0.44f else 0.38f)
        val pupilRadius = irisRadius * (if (mood == AvatarMood.SURPRISED) 0.58f else 0.52f)

        // Vibrant Iris Gradient: Electric Cyan/Azure or Warm Pink Gleam for Heart
        val irisColors = if (mood == AvatarMood.HEART) {
            listOf(
                Color(0xFFF472B6),
                Color(0xFF0284C7),
                Color(0xFF0F172A)
            )
        } else {
            listOf(
                Color(0xFF22D3EE),
                Color(0xFF0284C7),
                Color(0xFF0F172A)
            )
        }

        val irisBrush = Brush.radialGradient(
            colors = irisColors,
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
            radius = pupilRadius * (if (mood == AvatarMood.SURPRISED || mood == AvatarMood.HEART) 0.42f else 0.36f),
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

        // Eyelid dropping down on blink or sleepiness
        val effectiveLidProgress = if (mood == AvatarMood.SLEEPY) {
            0.62f.coerceAtLeast(blinkProgress)
        } else {
            blinkProgress
        }

        if (effectiveLidProgress > 0f) {
            val lidHeight = eyeRect.height * effectiveLidProgress
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
 * Draws soft glowing rosy blush circles on cheeks for happy, excited, giggling, and heart states
 */
private fun DrawScope.drawCheekBlush(
    centerX: Float,
    centerY: Float,
    canvasW: Float,
    canvasH: Float,
    mood: AvatarMood
) {
    if (mood !in listOf(AvatarMood.HAPPY, AvatarMood.EXCITED, AvatarMood.GIGGLING, AvatarMood.HEART)) return

    val blushY = centerY + canvasH * 0.09f
    val blushSpacing = canvasW * 0.46f
    val radius = canvasW * 0.095f
    val alpha = if (mood == AvatarMood.HEART || mood == AvatarMood.EXCITED) 0.38f else 0.22f
    val blushColor = Color(0xFFFB7185).copy(alpha = alpha)

    for (sign in listOf(-1f, 1f)) {
        val cx = centerX + sign * blushSpacing / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(blushColor, Color.Transparent),
                center = Offset(cx, blushY),
                radius = radius
            ),
            center = Offset(cx, blushY),
            radius = radius
        )
    }
}

/**
 * Draws the mouth with dynamic speech movement, smile, thinking, surprise, shrug, and giggles
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

        AvatarMood.HAPPY, AvatarMood.EXCITED -> {
            // Wide beaming open smile showing joy
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.52f, mouthY - 4f)
                quadraticTo(
                    centerX,
                    mouthY + mouthWidth * 0.40f,
                    centerX + mouthWidth * 0.52f,
                    mouthY - 4f
                )
            }
            drawPath(
                path = path,
                color = Color(0xFF38BDF8),
                style = Stroke(width = strokeWidth * 1.15f, cap = StrokeCap.Round)
            )
        }

        AvatarMood.SURPRISED -> {
            // Surprised circular 'O' mouth
            val oRadius = mouthWidth * 0.18f
            drawCircle(color = Color(0xFF1E293B), center = Offset(centerX, mouthY), radius = oRadius)
            drawCircle(color = Color(0xFF38BDF8), center = Offset(centerX, mouthY), radius = oRadius, style = Stroke(width = strokeWidth))
        }

        AvatarMood.CONFUSED -> {
            // Quirky asymmetric wave smirk
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.42f, mouthY + 3f)
                quadraticTo(centerX - mouthWidth * 0.10f, mouthY - 5f, centerX + mouthWidth * 0.42f, mouthY + 5f)
            }
            drawPath(path = path, color = mouthColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        }

        AvatarMood.WINKING -> {
            // Confident side smirk
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.38f, mouthY + 2f)
                quadraticTo(centerX + mouthWidth * 0.08f, mouthY + mouthWidth * 0.22f, centerX + mouthWidth * 0.46f, mouthY - 4f)
            }
            drawPath(path = path, color = Color(0xFF38BDF8), style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round))
        }

        AvatarMood.GIGGLING -> {
            // Sweet small chuckling mouth
            val gigW = mouthWidth * 0.46f
            val path = Path().apply {
                moveTo(centerX - gigW / 2f, mouthY)
                quadraticTo(centerX, mouthY + gigW * 0.32f, centerX + gigW / 2f, mouthY)
            }
            drawPath(path = path, color = Color(0xFFFB7185), style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round))
        }

        AvatarMood.SLEEPY -> {
            // Sleepy relaxed small mouth
            val sleepW = mouthWidth * 0.34f
            drawLine(
                color = mouthColor.copy(alpha = 0.7f),
                start = Offset(centerX - sleepW / 2f, mouthY),
                end = Offset(centerX + sleepW / 2f, mouthY),
                strokeWidth = strokeWidth * 0.8f,
                cap = StrokeCap.Round
            )
        }

        AvatarMood.HEART -> {
            // Sweet affectionate smile
            val path = Path().apply {
                moveTo(centerX - mouthWidth * 0.44f, mouthY - 2f)
                quadraticTo(centerX, mouthY + mouthWidth * 0.26f, centerX + mouthWidth * 0.44f, mouthY - 2f)
            }
            drawPath(path = path, color = Color(0xFFFB7185), style = Stroke(width = strokeWidth * 1.1f, cap = StrokeCap.Round))
        }

        AvatarMood.READING -> {
            // Attentive focused curve while reading user's text
            val readW = mouthWidth * 0.42f
            val path = Path().apply {
                moveTo(centerX - readW / 2f, mouthY)
                quadraticTo(centerX, mouthY + readW * 0.16f, centerX + readW / 2f, mouthY)
            }
            drawPath(path = path, color = mouthColor, style = Stroke(width = strokeWidth * 0.95f, cap = StrokeCap.Round))
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

private data class HumanFinger(
    val name: String,
    val xOffset: Float,
    val length: Float,
    val widthFactor: Float = 1.0f,
    val angleDegrees: Float = 0f,
    val isCurled: Boolean = false,
    val isPinky: Boolean = false
)

/**
 * Draws an anatomically proportional, realistic white human hand.
 * Supports natural DOWNWARD-facing hands for resting/speaking/idle/air-typing stances,
 * as well as raised expressive gestures (peace sign, ok sign, pointing, clapping, palms up, fist bump, etc.).
 */
private fun DrawScope.drawHumanHand(
    wristCenter: Offset,
    handWidth: Float,
    handHeight: Float,
    rotationDegrees: Float,
    isLeft: Boolean,
    gesture: HandGesture = HandGesture.DEFAULT_DOWN,
    speechPulse: Float = 0f,
    fingerFlutter: Float = 0f,
    thinkingTap: Float = 0f,
    clapProgress: Float = 0f
) {
    rotate(degrees = rotationDegrees, pivot = wristCenter) {
        val hw = handWidth
        val hh = handHeight
        val strokeWidth = hw * 0.048f
        val isPointingDown = gesture == HandGesture.DEFAULT_DOWN ||
                gesture == HandGesture.SPEAKING_DOWN ||
                gesture == HandGesture.TYPING_MIMIC

        // Dynamic elongation factor:
        val stretchFactor = when {
            gesture == HandGesture.WAVING || gesture == HandGesture.DOUBLE_WAVE || gesture == HandGesture.CELEBRATING -> 1.15f
            gesture == HandGesture.SPEAKING_DOWN -> 1.0f + speechPulse * 0.08f
            else -> 1.0f
        }

        // Pure white human skin/glove fill with subtle 3D volumetric shading
        val whiteFillBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFFFFFF),       // Pure crisp white highlight
                Color(0xFFF8FAFC),       // Clean light off-white
                Color(0xFFE2E8F0)        // Soft silver-gray shadow at bottom
            ),
            startY = if (isPointingDown) wristCenter.y - hh * 0.15f else wristCenter.y - hh * 0.70f,
            endY = if (isPointingDown) wristCenter.y + hh * 0.85f else wristCenter.y + hh * 0.45f
        )
        val outlineColor = Color(0xFF334155)     // Slate-700 crisp outline
        val creaseColor = Color(0xFF94A3B8)      // Subtle silver knuckle crease
        val nailColor = Color(0xFFCBD5E1)        // Delicate fingernail highlight
        val auraColor = Color(0xFFE2E8F0).copy(alpha = 0.16f)

        // 1. Soft atmospheric aura behind white hand
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(auraColor, Color.Transparent),
                center = wristCenter,
                radius = hw * 0.95f
            ),
            center = wristCenter,
            radius = hw * 0.95f
        )

        val baseFingerW = hw * 0.175f

        // =========================================================================
        // MODE A: DOWNWARD FACING HANDS (Default resting, speaking & air-typing)
        // Wrist is at top, fingers point DOWNWARDS naturally
        // =========================================================================
        if (isPointingDown) {
            val knuckleY = wristCenter.y + hh * 0.18f

            val flutterShift = if (gesture == HandGesture.TYPING_MIMIC) {
                val base = fingerFlutter * 6.28318f
                if (isLeft) {
                    listOf(
                        kotlin.math.sin(base.toDouble()).toFloat() * hh * 0.08f,
                        kotlin.math.sin((base + 1.2f).toDouble()).toFloat() * hh * 0.08f,
                        kotlin.math.sin((base + 2.4f).toDouble()).toFloat() * hh * 0.08f,
                        kotlin.math.sin((base + 3.6f).toDouble()).toFloat() * hh * 0.08f
                    )
                } else {
                    listOf(
                        kotlin.math.sin((base + 3.6f).toDouble()).toFloat() * hh * 0.08f,
                        kotlin.math.sin((base + 2.4f).toDouble()).toFloat() * hh * 0.08f,
                        kotlin.math.sin((base + 1.2f).toDouble()).toFloat() * hh * 0.08f,
                        kotlin.math.sin(base.toDouble()).toFloat() * hh * 0.08f
                    )
                }
            } else {
                listOf(0f, 0f, 0f, 0f)
            }

            // 4 Fingers extending DOWNWARDS
            val fingerConfigs: List<HumanFinger> = if (isLeft) {
                // Screen-left hand (Viewer's left):
                // Thumb on OUTER/LEFT side. Fingers from left (outer) to right (inner/towards body):
                // Index (-0.22) -> Middle (-0.07, longest) -> Ring (+0.08) -> Pinky (+0.23, shortest)
                listOf(
                    HumanFinger("Index", -hw * 0.22f, hh * 0.50f * stretchFactor + flutterShift[0], 1.0f, -3f),
                    HumanFinger("Middle", -hw * 0.07f, hh * 0.58f * stretchFactor + flutterShift[1], 1.03f, 0f),
                    HumanFinger("Ring", hw * 0.08f, hh * 0.53f * stretchFactor + flutterShift[2], 0.97f, 3f),
                    HumanFinger("Pinky", hw * 0.23f, hh * 0.40f * stretchFactor + flutterShift[3], 0.82f, 6f, isPinky = true)
                )
            } else {
                // Screen-right hand (Viewer's right):
                // Thumb on OUTER/RIGHT side. Fingers from left (inner) to right (outer):
                // Pinky (-0.23, shortest) -> Ring (-0.08) -> Middle (+0.07, longest) -> Index (+0.22)
                listOf(
                    HumanFinger("Pinky", -hw * 0.23f, hh * 0.40f * stretchFactor + flutterShift[0], 0.82f, -6f, isPinky = true),
                    HumanFinger("Ring", -hw * 0.08f, hh * 0.53f * stretchFactor + flutterShift[1], 0.97f, -3f),
                    HumanFinger("Middle", hw * 0.07f, hh * 0.58f * stretchFactor + flutterShift[2], 1.03f, 0f),
                    HumanFinger("Index", hw * 0.22f, hh * 0.50f * stretchFactor + flutterShift[3], 1.0f, 3f)
                )
            }

            for (f in fingerConfigs) {
                val fx = wristCenter.x + f.xOffset
                val fw = baseFingerW * f.widthFactor
                val fy = knuckleY + f.length

                rotate(degrees = f.angleDegrees, pivot = Offset(fx, knuckleY)) {
                    val fRect = Rect(
                        left = fx - fw / 2f,
                        top = knuckleY - hh * 0.08f,
                        right = fx + fw / 2f,
                        bottom = fy
                    )
                    val corner = CornerRadius(fw * 0.48f, fw * 0.48f)

                    drawRoundRect(
                        brush = whiteFillBrush,
                        topLeft = Offset(fRect.left, fRect.top),
                        size = Size(fRect.width, fRect.height),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(fRect.left, fRect.top),
                        size = Size(fRect.width, fRect.height),
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidth)
                    )

                    // Knuckle joint creases (pointing down)
                    val pipY = knuckleY + f.length * 0.46f
                    drawLine(
                        color = creaseColor,
                        start = Offset(fx - fw * 0.32f, pipY),
                        end = Offset(fx + fw * 0.32f, pipY),
                        strokeWidth = strokeWidth * 0.70f,
                        cap = StrokeCap.Round
                    )
                    val dipY = knuckleY + f.length * 0.74f
                    drawLine(
                        color = creaseColor.copy(alpha = 0.85f),
                        start = Offset(fx - fw * 0.24f, dipY),
                        end = Offset(fx + fw * 0.24f, dipY),
                        strokeWidth = strokeWidth * 0.60f,
                        cap = StrokeCap.Round
                    )

                    // Downward fingernail arc
                    val nailY = fy - fw * 0.38f
                    val nailPath = Path().apply {
                        moveTo(fx - fw * 0.22f, nailY)
                        quadraticTo(fx, nailY + fw * 0.16f, fx + fw * 0.22f, nailY)
                    }
                    drawPath(
                        path = nailPath,
                        color = nailColor.copy(alpha = 0.70f),
                        style = Stroke(width = strokeWidth * 0.55f, cap = StrokeCap.Round)
                    )
                }
            }

            // Downward Thumb
            val thumbW = baseFingerW * 1.22f
            val thumbH = hh * 0.42f
            val thumbOriginY = wristCenter.y + hh * 0.04f
            val (thumbOriginX, thumbAngle) = if (isLeft) {
                Pair(wristCenter.x - hw * 0.36f, -28f)
            } else {
                Pair(wristCenter.x + hw * 0.36f, 28f)
            }

            rotate(degrees = thumbAngle, pivot = Offset(thumbOriginX, thumbOriginY)) {
                val thumbRect = Rect(
                    left = thumbOriginX - thumbW / 2f,
                    top = thumbOriginY - hh * 0.10f,
                    right = thumbOriginX + thumbW / 2f,
                    bottom = thumbOriginY + thumbH
                )
                val thumbCorner = CornerRadius(thumbW * 0.48f, thumbW * 0.48f)

                drawRoundRect(
                    brush = whiteFillBrush,
                    topLeft = Offset(thumbRect.left, thumbRect.top),
                    size = Size(thumbRect.width, thumbRect.height),
                    cornerRadius = thumbCorner
                )
                drawRoundRect(
                    color = outlineColor,
                    topLeft = Offset(thumbRect.left, thumbRect.top),
                    size = Size(thumbRect.width, thumbRect.height),
                    cornerRadius = thumbCorner,
                    style = Stroke(width = strokeWidth)
                )

                val thumbCreaseY = thumbOriginY + thumbH * 0.50f
                drawLine(
                    color = creaseColor,
                    start = Offset(thumbOriginX - thumbW * 0.30f, thumbCreaseY),
                    end = Offset(thumbOriginX + thumbW * 0.30f, thumbCreaseY),
                    strokeWidth = strokeWidth * 0.75f,
                    cap = StrokeCap.Round
                )

                val thumbNailY = thumbOriginY + thumbH - thumbW * 0.38f
                val thumbNailPath = Path().apply {
                    moveTo(thumbOriginX - thumbW * 0.24f, thumbNailY)
                    quadraticTo(thumbOriginX, thumbNailY + thumbW * 0.16f, thumbOriginX + thumbW * 0.24f, thumbNailY)
                }
                drawPath(
                    path = thumbNailPath,
                    color = nailColor.copy(alpha = 0.70f),
                    style = Stroke(width = strokeWidth * 0.55f, cap = StrokeCap.Round)
                )
            }

            // Downward Palm Base
            val palmW = hw * 0.84f
            val palmH = hh * 0.52f
            val palmTopLeft = Offset(wristCenter.x - palmW / 2f, wristCenter.y - hh * 0.14f)
            val palmCorner = CornerRadius(palmW * 0.36f, palmW * 0.36f)

            drawRoundRect(
                brush = whiteFillBrush,
                topLeft = palmTopLeft,
                size = Size(palmW, palmH),
                cornerRadius = palmCorner
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = palmTopLeft,
                size = Size(palmW, palmH),
                cornerRadius = palmCorner,
                style = Stroke(width = strokeWidth)
            )

            // Thenar muscle pad highlight
            val thenarSign = if (isLeft) -1f else 1f
            val thenarCenterX = wristCenter.x + thenarSign * hw * 0.22f
            val thenarCenterY = wristCenter.y + hh * 0.12f
            drawCircle(
                color = Color(0xFFF1F5F9).copy(alpha = 0.45f),
                center = Offset(thenarCenterX, thenarCenterY),
                radius = hw * 0.16f
            )

            // Palmar lifeline crease
            val palmLinePath = Path().apply {
                val startX = wristCenter.x + thenarSign * hw * 0.24f
                val startY = wristCenter.y + hh * 0.04f
                val midX = wristCenter.x + thenarSign * hw * 0.06f
                val midY = wristCenter.y + hh * 0.20f
                val endX = wristCenter.x - thenarSign * hw * 0.14f
                val endY = wristCenter.y + hh * 0.26f
                moveTo(startX, startY)
                quadraticTo(midX, midY, endX, endY)
            }
            drawPath(
                path = palmLinePath,
                color = creaseColor.copy(alpha = 0.65f),
                style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round)
            )

            return@rotate
        }

        // =========================================================================
        // MODE B: RAISED GESTURES (Thinking, Peace, OK, Pointing, Palms Up, Fist, Clapping, etc.)
        // =========================================================================
        val fingerConfigs: List<HumanFinger> = when (gesture) {
            HandGesture.THINKING -> {
                val tapOffset = thinkingTap * hh * 0.035f
                listOf(
                    HumanFinger("Index", -hw * 0.14f, hh * 0.72f + tapOffset, 1.0f, -4f + thinkingTap * 3f, false),
                    HumanFinger("Middle", hw * 0.03f, hh * 0.24f, 1.02f, 2f, true),
                    HumanFinger("Ring", hw * 0.17f, hh * 0.20f, 0.96f, 4f, true),
                    HumanFinger("Pinky", hw * 0.28f, hh * 0.17f, 0.82f, 6f, true, isPinky = true)
                )
            }
            HandGesture.PEACE_SIGN -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.16f, hh * 0.64f, 1.0f, -12f, false),
                        HumanFinger("Middle", -hw * 0.01f, hh * 0.68f, 1.02f, 10f, false),
                        HumanFinger("Ring", hw * 0.13f, hh * 0.22f, 0.96f, 4f, true),
                        HumanFinger("Pinky", hw * 0.25f, hh * 0.18f, 0.82f, 6f, true, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.25f, hh * 0.18f, 0.82f, -6f, true, isPinky = true),
                        HumanFinger("Ring", -hw * 0.13f, hh * 0.22f, 0.96f, -4f, true),
                        HumanFinger("Middle", hw * 0.01f, hh * 0.68f, 1.02f, -10f, false),
                        HumanFinger("Index", hw * 0.16f, hh * 0.64f, 1.0f, 12f, false)
                    )
                }
            }
            HandGesture.OK_SIGN -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.18f, hh * 0.32f, 0.98f, 26f, true),
                        HumanFinger("Middle", -hw * 0.05f, hh * 0.60f, 1.02f, -4f, false),
                        HumanFinger("Ring", hw * 0.10f, hh * 0.54f, 0.96f, 6f, false),
                        HumanFinger("Pinky", hw * 0.25f, hh * 0.44f, 0.82f, 14f, false, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.25f, hh * 0.44f, 0.82f, -14f, false, isPinky = true),
                        HumanFinger("Ring", -hw * 0.10f, hh * 0.54f, 0.96f, -6f, false),
                        HumanFinger("Middle", hw * 0.05f, hh * 0.60f, 1.02f, 4f, false),
                        HumanFinger("Index", hw * 0.18f, hh * 0.32f, 0.98f, -26f, true)
                    )
                }
            }
            HandGesture.POINTING_UP -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.18f, hh * 0.72f, 1.03f, 0f, false),
                        HumanFinger("Middle", -hw * 0.05f, hh * 0.23f, 1.02f, -1f, true),
                        HumanFinger("Ring", hw * 0.09f, hh * 0.21f, 0.96f, 2f, true),
                        HumanFinger("Pinky", hw * 0.23f, hh * 0.18f, 0.82f, 4f, true, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.23f, hh * 0.18f, 0.82f, -4f, true, isPinky = true),
                        HumanFinger("Ring", -hw * 0.09f, hh * 0.21f, 0.96f, -2f, true),
                        HumanFinger("Middle", hw * 0.05f, hh * 0.23f, 1.02f, 1f, true),
                        HumanFinger("Index", hw * 0.18f, hh * 0.72f, 1.03f, 0f, false)
                    )
                }
            }
            HandGesture.POINTING_AT_USER -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.24f, hh * 0.66f, 1.05f, -22f, false),
                        HumanFinger("Middle", -hw * 0.08f, hh * 0.24f, 1.02f, -4f, true),
                        HumanFinger("Ring", hw * 0.08f, hh * 0.22f, 0.96f, 2f, true),
                        HumanFinger("Pinky", hw * 0.22f, hh * 0.19f, 0.82f, 6f, true, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.22f, hh * 0.19f, 0.82f, -6f, true, isPinky = true),
                        HumanFinger("Ring", -hw * 0.08f, hh * 0.22f, 0.96f, -2f, true),
                        HumanFinger("Middle", hw * 0.08f, hh * 0.24f, 1.02f, 4f, true),
                        HumanFinger("Index", hw * 0.24f, hh * 0.66f, 1.05f, 22f, false)
                    )
                }
            }
            HandGesture.PALMS_UP -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.24f, hh * 0.50f, 1.0f, -8f, false),
                        HumanFinger("Middle", -hw * 0.08f, hh * 0.56f, 1.02f, 0f, false),
                        HumanFinger("Ring", hw * 0.08f, hh * 0.52f, 0.97f, 7f, false),
                        HumanFinger("Pinky", hw * 0.24f, hh * 0.42f, 0.82f, 14f, false, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.24f, hh * 0.42f, 0.82f, -14f, false, isPinky = true),
                        HumanFinger("Ring", -hw * 0.08f, hh * 0.52f, 0.97f, -7f, false),
                        HumanFinger("Middle", hw * 0.08f, hh * 0.56f, 1.02f, 0f, false),
                        HumanFinger("Index", hw * 0.24f, hh * 0.50f, 1.0f, 8f, false)
                    )
                }
            }
            HandGesture.FIST_BUMP -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.22f, hh * 0.24f, 1.0f, -2f, true),
                        HumanFinger("Middle", -hw * 0.07f, hh * 0.25f, 1.02f, 0f, true),
                        HumanFinger("Ring", hw * 0.08f, hh * 0.24f, 0.97f, 2f, true),
                        HumanFinger("Pinky", hw * 0.22f, hh * 0.21f, 0.82f, 4f, true, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.22f, hh * 0.21f, 0.82f, -4f, true, isPinky = true),
                        HumanFinger("Ring", -hw * 0.08f, hh * 0.24f, 0.97f, -2f, true),
                        HumanFinger("Middle", hw * 0.07f, hh * 0.25f, 1.02f, 0f, true),
                        HumanFinger("Index", hw * 0.22f, hh * 0.24f, 1.0f, 2f, true)
                    )
                }
            }
            HandGesture.CLAPPING -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.22f, hh * 0.52f, 1.0f, -4f, false),
                        HumanFinger("Middle", -hw * 0.07f, hh * 0.58f, 1.02f, 0f, false),
                        HumanFinger("Ring", hw * 0.08f, hh * 0.54f, 0.97f, 3f, false),
                        HumanFinger("Pinky", hw * 0.22f, hh * 0.44f, 0.82f, 6f, false, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.22f, hh * 0.44f, 0.82f, -6f, false, isPinky = true),
                        HumanFinger("Ring", -hw * 0.08f, hh * 0.54f, 0.97f, -3f, false),
                        HumanFinger("Middle", hw * 0.07f, hh * 0.58f, 1.02f, 0f, false),
                        HumanFinger("Index", hw * 0.22f, hh * 0.52f, 1.0f, 4f, false)
                    )
                }
            }
            HandGesture.SLEEPY_RUB -> {
                listOf(
                    HumanFinger("Pinky", -hw * 0.20f, hh * 0.26f, 0.82f, -4f, true, isPinky = true),
                    HumanFinger("Ring", -hw * 0.07f, hh * 0.30f, 0.96f, -2f, true),
                    HumanFinger("Middle", hw * 0.07f, hh * 0.32f, 1.02f, 1f, true),
                    HumanFinger("Index", hw * 0.20f, hh * 0.30f, 1.0f, 3f, true)
                )
            }
            HandGesture.THUMBS_UP -> {
                listOf(
                    HumanFinger("Pinky", -hw * 0.22f, hh * 0.20f, 0.82f, -4f, true, isPinky = true),
                    HumanFinger("Ring", -hw * 0.08f, hh * 0.22f, 0.96f, -2f, true),
                    HumanFinger("Middle", hw * 0.07f, hh * 0.24f, 1.02f, 1f, true),
                    HumanFinger("Index", hw * 0.22f, hh * 0.22f, 1.0f, 3f, true)
                )
            }
            HandGesture.COVER_MOUTH -> {
                listOf(
                    HumanFinger("Pinky", -hw * 0.20f, hh * 0.36f, 0.82f, -3f, false, isPinky = true),
                    HumanFinger("Ring", -hw * 0.07f, hh * 0.44f, 0.96f, -1f, false),
                    HumanFinger("Middle", hw * 0.07f, hh * 0.48f, 1.02f, 1f, false),
                    HumanFinger("Index", hw * 0.20f, hh * 0.42f, 1.0f, 3f, false)
                )
            }
            HandGesture.CHEEK_TOUCH -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.22f, hh * 0.46f, 1.0f, -4f, false),
                        HumanFinger("Middle", -hw * 0.07f, hh * 0.52f, 1.02f, -1f, false),
                        HumanFinger("Ring", hw * 0.08f, hh * 0.48f, 0.97f, 2f, false),
                        HumanFinger("Pinky", hw * 0.22f, hh * 0.38f, 0.82f, 5f, false, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.22f, hh * 0.38f, 0.82f, -5f, false, isPinky = true),
                        HumanFinger("Ring", -hw * 0.08f, hh * 0.48f, 0.97f, -2f, false),
                        HumanFinger("Middle", hw * 0.07f, hh * 0.52f, 1.02f, 1f, false),
                        HumanFinger("Index", hw * 0.22f, hh * 0.46f, 1.0f, 4f, false)
                    )
                }
            }
            HandGesture.SHRUG -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.24f, hh * 0.46f, 1.0f, -8f, false),
                        HumanFinger("Middle", -hw * 0.08f, hh * 0.52f, 1.02f, -2f, false),
                        HumanFinger("Ring", hw * 0.08f, hh * 0.48f, 0.97f, 4f, false),
                        HumanFinger("Pinky", hw * 0.24f, hh * 0.38f, 0.82f, 10f, false, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.24f, hh * 0.38f, 0.82f, -10f, false, isPinky = true),
                        HumanFinger("Ring", -hw * 0.08f, hh * 0.48f, 0.97f, -4f, false),
                        HumanFinger("Middle", hw * 0.08f, hh * 0.52f, 1.02f, 2f, false),
                        HumanFinger("Index", hw * 0.24f, hh * 0.46f, 1.0f, 8f, false)
                    )
                }
            }
            HandGesture.HEART_SIDE -> {
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.16f, hh * 0.54f, 1.0f, 14f, false),
                        HumanFinger("Middle", -hw * 0.04f, hh * 0.26f, 1.02f, 4f, true),
                        HumanFinger("Ring", hw * 0.10f, hh * 0.22f, 0.96f, 4f, true),
                        HumanFinger("Pinky", hw * 0.22f, hh * 0.18f, 0.82f, 6f, true, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.22f, hh * 0.18f, 0.82f, -6f, true, isPinky = true),
                        HumanFinger("Ring", -hw * 0.10f, hh * 0.22f, 0.96f, -4f, true),
                        HumanFinger("Middle", hw * 0.04f, hh * 0.26f, 1.02f, -4f, true),
                        HumanFinger("Index", hw * 0.16f, hh * 0.54f, 1.0f, -14f, false)
                    )
                }
            }
            else -> {
                // Waving / Double Wave / Celebrating
                if (isLeft) {
                    listOf(
                        HumanFinger("Index", -hw * 0.25f, hh * 0.54f * stretchFactor, 1.0f, -10f, false),
                        HumanFinger("Middle", -hw * 0.08f, hh * 0.63f * stretchFactor, 1.02f, -3f, false),
                        HumanFinger("Ring", hw * 0.09f, hh * 0.57f * stretchFactor, 0.97f, 4f, false),
                        HumanFinger("Pinky", hw * 0.26f, hh * 0.44f * stretchFactor, 0.82f, 11f, false, isPinky = true)
                    )
                } else {
                    listOf(
                        HumanFinger("Pinky", -hw * 0.26f, hh * 0.44f * stretchFactor, 0.82f, -11f, false, isPinky = true),
                        HumanFinger("Ring", -hw * 0.09f, hh * 0.57f * stretchFactor, 0.97f, -4f, false),
                        HumanFinger("Middle", hw * 0.08f, hh * 0.63f * stretchFactor, 1.02f, 3f, false),
                        HumanFinger("Index", hw * 0.25f, hh * 0.54f * stretchFactor, 1.0f, 10f, false)
                    )
                }
            }
        }

        val knuckleY = wristCenter.y - hh * 0.16f
        for (f in fingerConfigs) {
            val fx = wristCenter.x + f.xOffset
            val fw = baseFingerW * f.widthFactor

            rotate(degrees = f.angleDegrees, pivot = Offset(fx, knuckleY)) {
                if (f.isCurled) {
                    val curlH = f.length + hh * 0.08f
                    val curlRect = Rect(
                        left = fx - fw / 2f,
                        top = knuckleY - curlH * 0.65f,
                        right = fx + fw / 2f,
                        bottom = knuckleY + curlH * 0.35f
                    )
                    val corner = CornerRadius(fw * 0.48f, fw * 0.48f)
                    drawRoundRect(
                        brush = whiteFillBrush,
                        topLeft = Offset(curlRect.left, curlRect.top),
                        size = Size(curlRect.width, curlRect.height),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(curlRect.left, curlRect.top),
                        size = Size(curlRect.width, curlRect.height),
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidth)
                    )
                    val foldY = knuckleY - curlH * 0.15f
                    drawLine(
                        color = creaseColor,
                        start = Offset(fx - fw * 0.28f, foldY),
                        end = Offset(fx + fw * 0.28f, foldY),
                        strokeWidth = strokeWidth * 0.75f,
                        cap = StrokeCap.Round
                    )
                } else {
                    val fy = knuckleY - f.length
                    val fRect = Rect(
                        left = fx - fw / 2f,
                        top = fy,
                        right = fx + fw / 2f,
                        bottom = knuckleY + hh * 0.08f
                    )
                    val corner = CornerRadius(fw * 0.48f, fw * 0.48f)

                    drawRoundRect(
                        brush = whiteFillBrush,
                        topLeft = Offset(fRect.left, fRect.top),
                        size = Size(fRect.width, fRect.height),
                        cornerRadius = corner
                    )
                    drawRoundRect(
                        color = outlineColor,
                        topLeft = Offset(fRect.left, fRect.top),
                        size = Size(fRect.width, fRect.height),
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidth)
                    )

                    val pipY = fy + f.length * 0.46f
                    drawLine(
                        color = creaseColor,
                        start = Offset(fx - fw * 0.32f, pipY),
                        end = Offset(fx + fw * 0.32f, pipY),
                        strokeWidth = strokeWidth * 0.70f,
                        cap = StrokeCap.Round
                    )
                    val dipY = fy + f.length * 0.24f
                    drawLine(
                        color = creaseColor.copy(alpha = 0.85f),
                        start = Offset(fx - fw * 0.24f, dipY),
                        end = Offset(fx + fw * 0.24f, dipY),
                        strokeWidth = strokeWidth * 0.60f,
                        cap = StrokeCap.Round
                    )

                    val nailPath = Path().apply {
                        val nailY = fy + fw * 0.38f
                        moveTo(fx - fw * 0.22f, nailY)
                        quadraticTo(fx, fy + fw * 0.16f, fx + fw * 0.22f, nailY)
                    }
                    drawPath(
                        path = nailPath,
                        color = nailColor.copy(alpha = 0.70f),
                        style = Stroke(width = strokeWidth * 0.55f, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Thumb in Raised Gestures
        val thumbW = baseFingerW * 1.22f
        val thumbH = if (gesture == HandGesture.THUMBS_UP) hh * 0.52f else hh * 0.42f
        val thumbOriginY = wristCenter.y + hh * 0.02f

        val (thumbOriginX, thumbAngle) = when (gesture) {
            HandGesture.THINKING -> Pair(wristCenter.x - hw * 0.28f, -22f)
            HandGesture.THUMBS_UP -> Pair(wristCenter.x + hw * 0.24f, 0f)
            HandGesture.PEACE_SIGN -> if (isLeft) Pair(wristCenter.x - hw * 0.16f, 18f) else Pair(wristCenter.x + hw * 0.16f, -18f)
            HandGesture.OK_SIGN -> if (isLeft) Pair(wristCenter.x - hw * 0.28f, -28f) else Pair(wristCenter.x + hw * 0.28f, 28f)
            HandGesture.POINTING_UP -> if (isLeft) Pair(wristCenter.x - hw * 0.16f, 16f) else Pair(wristCenter.x + hw * 0.16f, -16f)
            HandGesture.POINTING_AT_USER -> if (isLeft) Pair(wristCenter.x - hw * 0.20f, 12f) else Pair(wristCenter.x + hw * 0.20f, -12f)
            HandGesture.PALMS_UP -> if (isLeft) Pair(wristCenter.x - hw * 0.38f, -40f) else Pair(wristCenter.x + hw * 0.38f, 40f)
            HandGesture.FIST_BUMP -> if (isLeft) Pair(wristCenter.x - hw * 0.20f, 0f) else Pair(wristCenter.x + hw * 0.20f, 0f)
            HandGesture.CLAPPING -> if (isLeft) Pair(wristCenter.x - hw * 0.32f, -20f) else Pair(wristCenter.x + hw * 0.32f, 20f)
            HandGesture.SLEEPY_RUB -> Pair(wristCenter.x - hw * 0.18f, -10f)
            HandGesture.HEART_SIDE -> if (isLeft) Pair(wristCenter.x - hw * 0.12f, 32f) else Pair(wristCenter.x + hw * 0.12f, -32f)
            HandGesture.WAVING, HandGesture.DOUBLE_WAVE, HandGesture.CELEBRATING -> if (isLeft) Pair(wristCenter.x - hw * 0.38f, -44f) else Pair(wristCenter.x + hw * 0.38f, 44f)
            else -> if (isLeft) Pair(wristCenter.x - hw * 0.36f, -34f) else Pair(wristCenter.x + hw * 0.36f, 34f)
        }

        rotate(degrees = thumbAngle, pivot = Offset(thumbOriginX, thumbOriginY)) {
            val thumbRect = Rect(
                left = thumbOriginX - thumbW / 2f,
                top = thumbOriginY - thumbH,
                right = thumbOriginX + thumbW / 2f,
                bottom = thumbOriginY + hh * 0.14f
            )
            val thumbCorner = CornerRadius(thumbW * 0.48f, thumbW * 0.48f)

            drawRoundRect(
                brush = whiteFillBrush,
                topLeft = Offset(thumbRect.left, thumbRect.top),
                size = Size(thumbRect.width, thumbRect.height),
                cornerRadius = thumbCorner
            )
            drawRoundRect(
                color = outlineColor,
                topLeft = Offset(thumbRect.left, thumbRect.top),
                size = Size(thumbRect.width, thumbRect.height),
                cornerRadius = thumbCorner,
                style = Stroke(width = strokeWidth)
            )

            val thumbCreaseY = thumbOriginY - thumbH * 0.44f
            drawLine(
                color = creaseColor,
                start = Offset(thumbOriginX - thumbW * 0.30f, thumbCreaseY),
                end = Offset(thumbOriginX + thumbW * 0.30f, thumbCreaseY),
                strokeWidth = strokeWidth * 0.75f,
                cap = StrokeCap.Round
            )

            val thumbNailPath = Path().apply {
                val nailY = thumbOriginY - thumbH + thumbW * 0.40f
                moveTo(thumbOriginX - thumbW * 0.24f, nailY)
                quadraticTo(thumbOriginX, thumbOriginY - thumbH + thumbW * 0.18f, thumbOriginX + thumbW * 0.24f, nailY)
            }
            drawPath(
                path = thumbNailPath,
                color = nailColor.copy(alpha = 0.70f),
                style = Stroke(width = strokeWidth * 0.55f, cap = StrokeCap.Round)
            )
        }

        // Palm Base in Raised Gestures
        val palmW = hw * 0.84f
        val palmH = hh * 0.54f
        val palmTopLeft = Offset(wristCenter.x - palmW / 2f, wristCenter.y - palmH * 0.36f)
        val palmCorner = CornerRadius(palmW * 0.36f, palmW * 0.36f)

        drawRoundRect(
            brush = whiteFillBrush,
            topLeft = palmTopLeft,
            size = Size(palmW, palmH),
            cornerRadius = palmCorner
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = palmTopLeft,
            size = Size(palmW, palmH),
            cornerRadius = palmCorner,
            style = Stroke(width = strokeWidth)
        )

        val thenarSign = if (gesture == HandGesture.THINKING && !isLeft) -1f else if (isLeft) -1f else 1f
        val thenarCenterX = wristCenter.x + thenarSign * hw * 0.22f
        val thenarCenterY = wristCenter.y + hh * 0.04f
        drawCircle(
            color = Color(0xFFF1F5F9).copy(alpha = 0.45f),
            center = Offset(thenarCenterX, thenarCenterY),
            radius = hw * 0.16f
        )

        val palmLinePath = Path().apply {
            val startX = wristCenter.x + thenarSign * hw * 0.24f
            val startY = wristCenter.y - hh * 0.08f
            val midX = wristCenter.x + thenarSign * hw * 0.06f
            val midY = wristCenter.y + hh * 0.06f
            val endX = wristCenter.x - thenarSign * hw * 0.14f
            val endY = wristCenter.y + hh * 0.14f
            moveTo(startX, startY)
            quadraticTo(midX, midY, endX, endY)
        }
        drawPath(
            path = palmLinePath,
            color = creaseColor.copy(alpha = 0.65f),
            style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round)
        )
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
