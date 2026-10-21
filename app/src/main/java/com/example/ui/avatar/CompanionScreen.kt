package com.example.ui.avatar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ChatMessage
import com.example.model.MessageSender
import com.example.ui.ChatViewModel
import com.example.ui.FormattedAiContent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SendButtonDarkBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.UserBubbleDarkBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AccentCyan = Color(0xFF38BDF8)
private val PrimaryAccent = Color(0xFF0284C7)

/**
 * Pronounced Speech Bubble Shape with an unmistakable top-pointing triangle tail
 * pointing directly upward toward Lynia's mouth.
 */
private val PronouncedSpeechBubbleShape = GenericShape { size, _ ->
    val cornerRadius = 18.dp.value
    val tailWidth = 26.dp.value
    val tailHeight = 14.dp.value
    val tailCenterX = size.width / 2f

    // Start just right of the top-center tail on the top edge
    moveTo(tailCenterX - tailWidth / 2f, tailHeight)
    // Draw the triangular pointer tail pointing straight UP
    lineTo(tailCenterX, 0f)
    lineTo(tailCenterX + tailWidth / 2f, tailHeight)

    // Top right corner
    lineTo(size.width - cornerRadius, tailHeight)
    quadraticTo(size.width, tailHeight, size.width, tailHeight + cornerRadius)

    // Right edge to bottom right corner
    lineTo(size.width, size.height - cornerRadius)
    quadraticTo(size.width, size.height, size.width - cornerRadius, size.height)

    // Bottom edge to bottom left corner
    lineTo(cornerRadius, size.height)
    quadraticTo(0f, size.height, 0f, size.height - cornerRadius)

    // Left edge to top left corner
    lineTo(0f, tailHeight + cornerRadius)
    quadraticTo(0f, tailHeight, cornerRadius, tailHeight)

    close()
}

/**
 * Dedicated Living Companion screen ("Canlı Lynia").
 * - Prominent half-screen size living avatar (`230.dp`) by default.
 * - Pronounced comic-style speech bubble with upward tail pointing directly to Lynia's mouth.
 * - Looks down at the message input box when typing (`companionInputText.isNotBlank()`).
 * - Tracks mouse pointer when hovering/moving.
 * - Expandable previous messages mode where Lynia shrinks higher up.
 */
@Composable
fun CompanionScreen(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val companionMessages by viewModel.companionMessages.collectAsState()
    val companionInputText by viewModel.companionInputText.collectAsState()
    val isCompanionLoading by viewModel.isCompanionLoading.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var isHistoryExpanded by remember { mutableStateOf(false) }

    val lastUserMessage = remember(companionMessages) {
        companionMessages.findLast { it.sender == MessageSender.USER }
    }
    val lastAiMessage = remember(companionMessages) {
        companionMessages.findLast { it.sender == MessageSender.AI }
    }

    val isTypingAnticipating by viewModel.isCompanionTypingAnticipating.collectAsState()

    val isThinking = (isCompanionLoading && (lastAiMessage == null || (lastUserMessage != null && lastAiMessage.timestamp < lastUserMessage.timestamp))) || isTypingAnticipating
    val isSpeaking = (lastAiMessage?.isStreaming == true) || uiState.isSpeaking
    val mood = when {
        isSpeaking -> AvatarMood.SPEAKING
        isThinking -> AvatarMood.THINKING
        else -> AvatarMood.IDLE
    }

    // Larger prominent default size (~half screen area feel) and shrinks when history is expanded
    val avatarSize by animateDpAsState(
        targetValue = if (isHistoryExpanded) 78.dp else 226.dp,
        animationSpec = tween(350),
        label = "avatar_size_anim"
    )

    // Typing state to make Lynia look down at the input box when typing
    val isTyping = companionInputText.isNotBlank()

    // Mouse pointer tracking
    var targetGazeOffset by remember { mutableStateOf<Offset?>(null) }
    var avatarCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()

    // Determine gaze target: typing takes top priority (looking down), then mouse hover, then autonomous
    val effectiveGazeTarget = when {
        isTyping -> Offset(0f, 0.82f) // Looking down at input box
        targetGazeOffset != null -> targetGazeOffset!!
        else -> null
    }

    Scaffold(
        topBar = {
            Surface(
                color = DarkBg,
                border = BorderStroke(0.dp, Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DarkSurface)
                            .border(1.dp, DarkBorder, CircleShape)
                            .testTag("companion_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Normal sohbete dön",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Lynia Canlı",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = TextPrimary
                            )
                        }

                        val statusText = when {
                            isSpeaking -> "Konuşuyor..."
                            isThinking -> if (isTypingAnticipating) "Yazdıklarını okuyorum..." else "Düşünüyor..."
                            targetGazeOffset != null -> "Seni izliyor 👁️"
                            else -> "Canlı • Seni Dinliyor"
                        }
                        val statusColor = when {
                            isSpeaking -> AccentCyan
                            isThinking -> Color(0xFFF59E0B)
                            isTyping -> Color(0xFF38BDF8)
                            targetGazeOffset != null -> Color(0xFF38BDF8)
                            else -> Color(0xFF10B981)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 1.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = statusColor
                            )
                        }
                    }

                    if (companionMessages.isNotEmpty()) {
                        IconButton(
                            onClick = { isHistoryExpanded = !isHistoryExpanded },
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isHistoryExpanded) DarkSurfaceVariant else DarkSurface)
                                .border(1.dp, DarkBorder, CircleShape)
                                .testTag("companion_toggle_history_top")
                        ) {
                            Icon(
                                imageVector = if (isHistoryExpanded) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowUp,
                                contentDescription = if (isHistoryExpanded) "Karaktere Dön" else "Önceki Mesajlar",
                                tint = if (isHistoryExpanded) AccentCyan else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(38.dp))
                    }
                }
            }
        },
        containerColor = DarkBg,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            val pos = change.position
                            if (avatarCenterInRoot != Offset.Zero && !isTyping) {
                                val dx = ((pos.x - avatarCenterInRoot.x) / 160f).coerceIn(-1f, 1f)
                                val dy = ((pos.y - avatarCenterInRoot.y) / 160f).coerceIn(-1f, 1f)
                                targetGazeOffset = Offset(dx, dy)

                                coroutineScope.launch {
                                    delay(2500)
                                    targetGazeOffset = null
                                }
                            }
                        }
                    }
                }
            }
            .imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP SECTION: Prominent Living Face Avatar (~half screen size when normal)
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .padding(top = if (isHistoryExpanded) 2.dp else 6.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        avatarCenterInRoot = bounds.center
                    }
                    .drawBehind {
                        val auraColor = when {
                            isSpeaking -> Color(0xFF00E5FF).copy(alpha = 0.22f)
                            isThinking -> Color(0xFFF59E0B).copy(alpha = 0.18f)
                            else -> Color(0xFF38BDF8).copy(alpha = 0.12f)
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(auraColor, Color.Transparent),
                                center = center,
                                radius = size.width * 0.8f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                LivingFace(
                    mood = mood,
                    isThinking = isThinking,
                    isSpeaking = isSpeaking,
                    targetGaze = effectiveGazeTarget,
                    modifier = Modifier.fillMaxSize(),
                    onTap = {
                        // Playful wink interaction
                    }
                )
            }

            // MIDDLE SECTION: When NOT expanded, show Lynia's authentic Speech Bubble with top tail
            if (!isHistoryExpanded) {
                val speechScrollState = rememberScrollState()
                val speechText = when {
                    isThinking -> "Düşünüyorum..."
                    lastAiMessage != null && lastAiMessage.text.isNotBlank() -> lastAiMessage.text
                    else -> "Merhaba! Seninle her an canlı sohbet etmek için buradayım. Bana dilediğin soruyu sorabilirsin 😊"
                }

                Surface(
                    shape = PronouncedSpeechBubbleShape,
                    color = DarkSurfaceVariant,
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = if (isSpeaking) PrimaryAccent else DarkBorder
                    ),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp, max = 210.dp)
                        .padding(top = 10.dp, bottom = 4.dp)
                        .testTag("companion_mouth_speech_bubble")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, top = 22.dp, bottom = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryAccent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Lynia",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = AccentCyan
                                )
                            }

                            if (lastAiMessage != null && !isThinking) {
                                IconButton(
                                    onClick = {
                                        if (uiState.isSpeaking) {
                                            viewModel.stopSpeaking()
                                        } else {
                                            viewModel.toggleSpeakMessage(speechText, lastAiMessage.id)
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isSpeaking) Icons.Rounded.GraphicEq else Icons.Rounded.VolumeUp,
                                        contentDescription = "Seslendir",
                                        tint = if (uiState.isSpeaking) AccentCyan else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(speechScrollState)
                        ) {
                            if (isThinking) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Cevabımı hazırlıyorum",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "•••",
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = AccentCyan
                                    )
                                }
                            } else {
                                FormattedAiContent(
                                    text = speechText,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                // Expandable history button
                if (companionMessages.size > 2) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clickable { isHistoryExpanded = true }
                            .testTag("companion_expand_history_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Genişlet",
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Önceki Mesajlar (${companionMessages.size} mesaj)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Compact preview of recent messages
                val recentMessages = remember(companionMessages) {
                    companionMessages.takeLast(3).dropLast(1)
                }
                if (recentMessages.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        recentMessages.forEach { msg ->
                            val isUser = msg.sender == MessageSender.USER
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isUser) UserBubbleDarkBlue.copy(alpha = 0.6f) else DarkSurface.copy(alpha = 0.8f),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = (if (isUser) "Sen: " else "Lynia: ") + msg.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isUser) TextPrimary else TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // User's latest message chip right above input bar
                AnimatedVisibility(
                    visible = lastUserMessage != null && lastUserMessage.text.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .testTag("companion_last_user_prompt")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "Sen",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Senin son mesajın",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = lastUserMessage?.text ?: "",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // EXPANDED HISTORY MODE
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .clickable { isHistoryExpanded = false }
                                .testTag("companion_collapse_history_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Daralt",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Karakter Görünümüne Dön",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = AccentCyan
                                )
                            }
                        }
                    }

                    val historyListState = rememberLazyListState()
                    LaunchedEffect(companionMessages.size) {
                        if (companionMessages.isNotEmpty()) {
                            historyListState.animateScrollToItem(companionMessages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = historyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("companion_full_history_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(companionMessages) { message ->
                            CompanionMessageItem(message = message)
                        }
                    }
                }
            }

            // BOTTOM CHAT INPUT BAR: Text Field + Mic + Send Button (Enter sends)
            val canSend = companionInputText.isNotBlank() && !isCompanionLoading

            Surface(
                shape = RoundedCornerShape(26.dp),
                color = DarkSurface,
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("companion_input_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.toggleVoiceRecording() },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isRecordingVoice) Color(0xFFEF4444) else Color.Transparent)
                            .testTag("companion_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Sesle Konuş",
                            tint = if (uiState.isRecordingVoice) Color.White else TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    OutlinedTextField(
                        value = companionInputText,
                        onValueChange = { viewModel.onCompanionInputChanged(it) },
                        placeholder = {
                            Text(
                                text = if (uiState.isRecordingVoice) "Dinleniyor..." else "Lynia ile konuş (Enter: Gönder)...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                                    !keyEvent.isShiftPressed
                                ) {
                                    if (canSend) {
                                        viewModel.sendCompanionMessage()
                                        true
                                    } else false
                                } else false
                            }
                            .testTag("companion_input_textfield"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = PrimaryAccent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (canSend) {
                                    viewModel.sendCompanionMessage()
                                }
                            }
                        )
                    )

                    IconButton(
                        onClick = {
                            if (canSend) {
                                viewModel.sendCompanionMessage()
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (canSend) SendButtonDarkBlue else Color.Transparent)
                            .testTag("companion_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Gönder",
                            tint = if (canSend) Color.White else TextMuted,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompanionMessageItem(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) UserBubbleDarkBlue else DarkSurfaceVariant,
            border = BorderStroke(1.dp, if (isUser) PrimaryAccent.copy(alpha = 0.4f) else DarkBorder),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "Sen" else "Lynia",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isUser) Color(0xFF93C5FD) else AccentCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                FormattedAiContent(
                    text = message.text,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
