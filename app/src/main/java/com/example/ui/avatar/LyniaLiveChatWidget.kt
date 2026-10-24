package com.example.ui.avatar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.LyniaLivePreReadStatus
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private val AccentCyan = Color(0xFF38BDF8)
private val PrimaryEmerald = Color(0xFF10B981)
private val WarmAmber = Color(0xFFF59E0B)

/**
 * Living Lynia Companion bar embedded directly in the normal ChatScreen.
 * Features:
 * - Real-time reading gaze: Lynia looks down at the input text and sweeps horizontally.
 * - Live finger flutter & air-typing mimic gestures while user types.
 * - Speculative pre-read status banner showing memory preparation and instant response readiness.
 * - Interactive gesture trigger chips (Wave, High five, Clap, Peace, Think).
 * - Tap to switch to full companion mode or minimize into sleek compact pill.
 */
@Composable
fun LyniaLiveChatWidget(
    inputText: String,
    isTyping: Boolean,
    isLoading: Boolean,
    isSpeaking: Boolean,
    preReadStatus: LyniaLivePreReadStatus,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit,
    onOpenCompanion: () -> Unit,
    onTriggerGesture: (HandGesture) -> Unit,
    activeCustomGesture: HandGesture?,
    modifier: Modifier = Modifier
) {
    var isMinimized by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_reading")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val wordCount = remember(inputText) {
        inputText.trim().split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    // Determine avatar mood based on state
    val mood = when {
        isLoading -> AvatarMood.THINKING
        isSpeaking -> AvatarMood.SPEAKING
        isTyping && preReadStatus == LyniaLivePreReadStatus.PREPARED -> AvatarMood.EXCITED
        isTyping -> AvatarMood.READING
        else -> AvatarMood.IDLE
    }

    // Target gaze: look down at input field when typing
    val targetGaze = if (isTyping) Offset(0f, 0.85f) else null

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            if (isMinimized) {
                // Minimized floating pill
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(16.dp))
                        .background(DarkSurface)
                        .border(BorderStroke(1.dp, if (isTyping) AccentCyan else DarkBorder), RoundedCornerShape(16.dp))
                        .clickable { isMinimized = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("lynia_live_minimized_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isTyping) AccentCyan else PrimaryEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTyping) "Lynia okuyor..." else "Lynia Canlı Okuma",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Genişlet",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                // Full interactive living bar
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = DarkSurface.copy(alpha = 0.95f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = when {
                            preReadStatus == LyniaLivePreReadStatus.PREPARED -> AccentCyan
                            isTyping -> AccentCyan.copy(alpha = 0.6f)
                            else -> DarkBorder
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lynia_live_reading_bar")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Top info & avatar row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Left: Living Avatar (Face + Hands)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { onOpenCompanion() }
                                    .testTag("lynia_live_avatar_tap")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 68.dp, height = 54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF070B14))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LivingFace(
                                        mood = mood,
                                        isThinking = isLoading,
                                        isSpeaking = isSpeaking,
                                        targetGaze = targetGaze,
                                        customLeftGesture = activeCustomGesture,
                                        customRightGesture = activeCustomGesture,
                                        modifier = Modifier.size(width = 66.dp, height = 52.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Status message & indicator
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Lynia",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.4.sp
                                            ),
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Status Pill Badge
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = when {
                                                preReadStatus == LyniaLivePreReadStatus.PREPARED -> Color(0xFF064E3B)
                                                isTyping -> Color(0xFF0C4A6E)
                                                else -> DarkSurfaceVariant
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                when {
                                                    preReadStatus == LyniaLivePreReadStatus.PREPARED -> PrimaryEmerald
                                                    isTyping -> AccentCyan.copy(alpha = pulseAlpha)
                                                    else -> DarkBorder
                                                }
                                            )
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                if (preReadStatus == LyniaLivePreReadStatus.PREPARED) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Bolt,
                                                        contentDescription = null,
                                                        tint = PrimaryEmerald,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(
                                                        text = "Hızlı Yanıt Hazır",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = Color(0xFFA7F3D0),
                                                        fontSize = 10.sp
                                                    )
                                                } else if (isTyping) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentCyan.copy(alpha = pulseAlpha))
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Canlı Okuyor",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = Color(0xFFBAE6FD),
                                                        fontSize = 10.sp
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(PrimaryEmerald)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "Dinlemede",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Dynamic Subtitle explaining what Lynia is doing
                                    val statusSubtitle = when {
                                        isLoading -> "Cevap üretiliyor..."
                                        isSpeaking -> "Sesli olarak konuşuyor..."
                                        preReadStatus == LyniaLivePreReadStatus.PREPARED ->
                                            "⚡ $wordCount kelime okundu, gönderildiğinde anında cevap verecek!"
                                        isTyping ->
                                            "Yazdıklarını harf harf takip ediyor ($wordCount kelime)..."
                                        else ->
                                            "Yazarken Lynia anında okur ve hazır bekler"
                                    }

                                    Text(
                                        text = statusSubtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            preReadStatus == LyniaLivePreReadStatus.PREPARED -> PrimaryEmerald
                                            isTyping -> AccentCyan
                                            else -> TextMuted
                                        },
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            // Right controls: Open full companion + Minimize button
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onOpenCompanion,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("open_full_companion_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInFull,
                                        contentDescription = "Tam Ekran Lynia",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { isMinimized = true },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("minimize_lynia_bar_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Küçült",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Bottom gesture chips row: Lets user interact with Lynia right from chat
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "El Hareketi:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 10.sp
                            )

                            QuickGestureChip(
                                label = "👋 Selam",
                                isSelected = activeCustomGesture == HandGesture.WAVING || activeCustomGesture == HandGesture.DOUBLE_WAVE,
                                onClick = { onTriggerGesture(HandGesture.DOUBLE_WAVE) }
                            )

                            QuickGestureChip(
                                label = "👏 Alkış",
                                isSelected = activeCustomGesture == HandGesture.CLAPPING,
                                onClick = { onTriggerGesture(HandGesture.CLAPPING) }
                            )

                            QuickGestureChip(
                                label = "✌️ Zafer",
                                isSelected = activeCustomGesture == HandGesture.PEACE_SIGN,
                                onClick = { onTriggerGesture(HandGesture.PEACE_SIGN) }
                            )

                            QuickGestureChip(
                                label = "👊 Çak",
                                isSelected = activeCustomGesture == HandGesture.FIST_BUMP,
                                onClick = { onTriggerGesture(HandGesture.FIST_BUMP) }
                            )

                            QuickGestureChip(
                                label = "🤔 Düşün",
                                isSelected = activeCustomGesture == HandGesture.THINKING,
                                onClick = { onTriggerGesture(HandGesture.THINKING) }
                            )

                            QuickGestureChip(
                                label = "⌨️ Yazma",
                                isSelected = activeCustomGesture == HandGesture.TYPING_MIMIC,
                                onClick = { onTriggerGesture(HandGesture.TYPING_MIMIC) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickGestureChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else DarkSurfaceVariant,
        border = BorderStroke(1.dp, if (isSelected) AccentCyan else DarkBorder),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("gesture_chip_$label")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (isSelected) AccentCyan else TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
