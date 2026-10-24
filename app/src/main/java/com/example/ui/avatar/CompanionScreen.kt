package com.example.ui.avatar

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AccentCyan = Color(0xFF38BDF8)
private val PrimaryAccent = Color(0xFF0284C7)

/**
 * Pronounced Speech Bubble Shape with an upward triangle tail
 * pointing directly toward Lynia's mouth.
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
 * - Prominent living avatar (~half screen size when idle, adapts smoothly when keyboard opens).
 * - Fixed bottom bar pinned directly above the keyboard via imePadding & navigationBarsPadding.
 * - Continuous real-time mouse gaze tracking with 2.5-second stillness timeout to autonomous wandering.
 * - Unblocked typing: instant speech interruption, mouth stops moving immediately when typing begins.
 * - Clean top header without cluttering status labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanionScreen(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.toggleCompanionVoiceRecording()
            } else {
                android.widget.Toast.makeText(context, "Sesle konuşmak için mikrofon izni gerekiyor.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

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

    // Typing and keyboard visibility detection
    val isTyping = companionInputText.isNotBlank()
    val isImeVisible = WindowInsets.isImeVisible
    val isKeyboardOpen = isImeVisible || isTyping

    // Speaking and thinking states: immediately silenced while user is typing
    val isSpeaking = ((lastAiMessage?.isStreaming == true) || uiState.isSpeaking) && !isTyping
    val isThinking = (isCompanionLoading && (lastAiMessage == null || (lastUserMessage != null && lastAiMessage.timestamp < lastUserMessage.timestamp))) && !isTyping

    // Mouse pointer tracking with 2.5-second idle timeout
    var targetGazeOffset by remember { mutableStateOf<Offset?>(null) }
    var avatarCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    val coroutineScope = rememberCoroutineScope()
    var mouseTimeoutJob by remember { mutableStateOf<Job?>(null) }

    // Dynamic mood & expressive gesture state engine
    var activeTapReaction by remember { mutableStateOf<AvatarMood?>(null) }
    var customGesture by remember { mutableStateOf<HandGesture?>(null) }
    var customLeftGesture by remember { mutableStateOf<HandGesture?>(null) }
    var customRightGesture by remember { mutableStateOf<HandGesture?>(null) }
    var tapCounter by remember { mutableStateOf(0) }
    var isSleepy by remember { mutableStateOf(false) }

    // Sleepiness timer: after 20s of total inactivity, Lynia gets sleepy
    LaunchedEffect(isTyping, isSpeaking, isThinking, targetGazeOffset, companionMessages.size) {
        isSleepy = false
        delay(20000)
        if (!isTyping && !isSpeaking && !isThinking) {
            isSleepy = true
        }
    }

    // Semantic emotion detection from the conversation context
    val messageMood = remember(lastAiMessage, lastUserMessage) {
        val textToCheck = (lastAiMessage?.text ?: lastUserMessage?.text ?: "").lowercase()
        when {
            textToCheck.contains("tebrik") || textToCheck.contains("harika") || textToCheck.contains("süper") ||
            textToCheck.contains("başardık") || textToCheck.contains("yaşasın") || textToCheck.contains("mükemmel") ||
            textToCheck.contains("kutlarım") || textToCheck.contains("🎉") || textToCheck.contains("👏") -> AvatarMood.EXCITED

            textToCheck.contains("şaşır") || textToCheck.contains("inanılmaz") || textToCheck.contains("vay") ||
            textToCheck.contains("nasıl yani") || textToCheck.contains("gerçekten mi") || textToCheck.contains("oha") ||
            textToCheck.contains("😮") || textToCheck.contains("şaka mı") -> AvatarMood.SURPRISED

            textToCheck.contains("bilmiyorum") || textToCheck.contains("emin değilim") || textToCheck.contains("anlamadım") ||
            textToCheck.contains("karıştı") || textToCheck.contains("fakat") || textToCheck.contains("acaba") -> AvatarMood.CONFUSED

            textToCheck.contains("sevgi") || textToCheck.contains("seviyorum") || textToCheck.contains("canım") ||
            textToCheck.contains("teşekkür") || textToCheck.contains("harikasın") || textToCheck.contains("tatlı") ||
            textToCheck.contains("❤️") || textToCheck.contains("😊") -> AvatarMood.HEART

            textToCheck.contains("haha") || textToCheck.contains("komik") || textToCheck.contains("gül") ||
            textToCheck.contains("eğlenceli") || textToCheck.contains("😂") || textToCheck.contains("😄") -> AvatarMood.GIGGLING

            textToCheck.contains("tamam") || textToCheck.contains("olur") || textToCheck.contains("kesinlikle") ||
            textToCheck.contains("tabii") || textToCheck.contains("elbette") || textToCheck.contains("hazır") ||
            textToCheck.contains("👍") -> AvatarMood.WINKING

            else -> null
        }
    }

    val mood = when {
        activeTapReaction != null -> activeTapReaction!!
        isTyping -> AvatarMood.READING
        isSpeaking -> (messageMood ?: AvatarMood.SPEAKING)
        isThinking -> AvatarMood.THINKING
        messageMood != null -> messageMood
        isSleepy -> AvatarMood.SLEEPY
        else -> AvatarMood.IDLE
    }

    // Dynamic responsive avatar sizing (generous size even when keyboard is open so it doesn't shrink too much)
    val avatarSize by animateDpAsState(
        targetValue = when {
            isHistoryExpanded -> 70.dp
            isKeyboardOpen -> 130.dp
            else -> 216.dp
        },
        animationSpec = tween(280),
        label = "avatar_size_anim"
    )

    // Determine gaze target: typing takes priority (watching input box), then mouse hover, then autonomous
    val effectiveGazeTarget = when {
        isTyping -> Offset(0f, 0.85f) // Attentively watching user type
        targetGazeOffset != null -> targetGazeOffset
        else -> null
    }

    val canSend = companionInputText.isNotBlank()

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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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

                    // Clean minimal header without distracting status labels
                    Text(
                        text = "Lynia",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary
                    )

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
        bottomBar = {
            // Pinned Bottom Bar: Sits cleanly above keyboard with proper insets
            Surface(
                color = DarkBg,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // User's latest message prompt chip (shown when not expanded and keyboard not active)
                    if (!isHistoryExpanded && !isKeyboardOpen && lastUserMessage != null && lastUserMessage.text.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DarkSurface,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .testTag("companion_last_user_prompt")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF334155)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "Sen",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lastUserMessage.text,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Input Box with Mic and Send Button
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = DarkSurface,
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("companion_input_bar")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.toggleCompanionVoiceRecording()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
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
                                onValueChange = { newText ->
                                    // Interrupt any speaking audio immediately when user starts typing
                                    if (uiState.isSpeaking) {
                                        viewModel.stopSpeaking()
                                    }
                                    viewModel.onCompanionInputChanged(newText)
                                },
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
        },
        containerColor = DarkBg,
        contentWindowInsets = WindowInsets.statusBars,
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
                                val dx = ((pos.x - avatarCenterInRoot.x) / 140f).coerceIn(-1f, 1f)
                                val dy = ((pos.y - avatarCenterInRoot.y) / 140f).coerceIn(-1f, 1f)
                                targetGazeOffset = Offset(dx, dy)

                                // Cancel previous idle timeout and reset to autonomous gaze after 2.5s stillness
                                mouseTimeoutJob?.cancel()
                                mouseTimeoutJob = coroutineScope.launch {
                                    delay(2500)
                                    targetGazeOffset = null
                                }
                            }
                        }
                    }
                }
            }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP SECTION: Living Face Avatar (large when idle, adapts smoothly when keyboard opens)
            Box(
                modifier = Modifier
                    .width(avatarSize * 1.25f)
                    .height(avatarSize)
                    .padding(top = if (isHistoryExpanded) 2.dp else 4.dp)
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
                    customLeftGesture = customLeftGesture ?: customGesture,
                    customRightGesture = customRightGesture ?: customGesture,
                    modifier = Modifier.fillMaxSize(),
                    onTap = {
                        coroutineScope.launch {
                            val tapGestures = listOf(
                                Pair(HandGesture.DOUBLE_WAVE, AvatarMood.EXCITED),
                                Pair(HandGesture.PEACE_SIGN, AvatarMood.WINKING),
                                Pair(HandGesture.CLAPPING, AvatarMood.HAPPY),
                                Pair(HandGesture.HEART_SIDE, AvatarMood.HEART),
                                Pair(HandGesture.OK_SIGN, AvatarMood.HAPPY),
                                Pair(HandGesture.FIST_BUMP, AvatarMood.EXCITED)
                            )
                            val (gest, moodSel) = tapGestures[tapCounter % tapGestures.size]
                            tapCounter++
                            customGesture = gest
                            activeTapReaction = moodSel
                            delay(2400)
                            if (customGesture == gest) {
                                customGesture = null
                                activeTapReaction = null
                            }
                        }
                    },
                    onHandTap = { isLeft ->
                        coroutineScope.launch {
                            if (isLeft) {
                                customLeftGesture = HandGesture.PEACE_SIGN
                                activeTapReaction = AvatarMood.WINKING
                            } else {
                                customRightGesture = HandGesture.THUMBS_UP
                                activeTapReaction = AvatarMood.HAPPY
                            }
                            delay(2200)
                            customLeftGesture = null
                            customRightGesture = null
                            activeTapReaction = null
                        }
                    }
                )
            }

            // Quick Hand Movement & Gesture Bar (Visible when keyboard is closed)
            if (!isHistoryExpanded && !isKeyboardOpen) {
                val gestureScrollState = rememberScrollState()
                val gestureOptions = listOf(
                    Triple("👋 El Salla", HandGesture.DOUBLE_WAVE, AvatarMood.EXCITED),
                    Triple("👏 Alkışla", HandGesture.CLAPPING, AvatarMood.HAPPY),
                    Triple("✌️ Zafer", HandGesture.PEACE_SIGN, AvatarMood.WINKING),
                    Triple("👌 Harika", HandGesture.OK_SIGN, AvatarMood.HAPPY),
                    Triple("☝️ İşaret", HandGesture.POINTING_UP, AvatarMood.THINKING),
                    Triple("🤲 Anlatım", HandGesture.PALMS_UP, AvatarMood.SPEAKING),
                    Triple("👊 Yumruk", HandGesture.FIST_BUMP, AvatarMood.EXCITED),
                    Triple("❤️ Kalp", HandGesture.HEART_SIDE, AvatarMood.HEART),
                    Triple("🤔 Düşün", HandGesture.THINKING, AvatarMood.THINKING),
                    Triple("🥱 Esne", HandGesture.SLEEPY_RUB, AvatarMood.SLEEPY),
                    Triple("⌨️ Hava Klavyesi", HandGesture.TYPING_MIMIC, AvatarMood.READING)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(gestureScrollState)
                        .padding(top = 2.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gestureOptions.forEach { (label, gesture, moodTarget) ->
                        val isSelected = customGesture == gesture
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) AccentCyan.copy(alpha = 0.22f) else DarkSurface,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) AccentCyan else DarkBorder
                            ),
                            modifier = Modifier
                                .clickable {
                                    coroutineScope.launch {
                                        customGesture = gesture
                                        activeTapReaction = moodTarget
                                        delay(2600)
                                        if (customGesture == gesture) {
                                            customGesture = null
                                            activeTapReaction = null
                                        }
                                    }
                                }
                                .testTag("gesture_chip_${gesture.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) AccentCyan else TextSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // MIDDLE SECTION: Normal Mode or Expanded History Mode
            if (!isHistoryExpanded) {
                val speechScrollState = rememberScrollState()
                val speechText = when {
                    isThinking -> "Düşünüyorum..."
                    lastAiMessage != null && lastAiMessage.text.isNotBlank() -> lastAiMessage.text
                    else -> "Merhaba! Seninle her an canlı sohbet etmek için buradayım. Bana dilediğin soruyu sorabilirsin 😊"
                }

                // Authentic speech bubble with upward triangular pointer
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
                        .heightIn(
                            min = if (isKeyboardOpen) 60.dp else 85.dp,
                            max = if (isKeyboardOpen) 115.dp else 200.dp
                        )
                        .padding(top = 8.dp, bottom = 4.dp)
                        .testTag("companion_mouth_speech_bubble")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 18.dp,
                                end = 18.dp,
                                top = if (isKeyboardOpen) 18.dp else 22.dp,
                                bottom = if (isKeyboardOpen) 10.dp else 14.dp
                            )
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
                                        imageVector = if (uiState.isSpeaking) Icons.Rounded.GraphicEq else Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "Seslendir",
                                        tint = if (uiState.isSpeaking) AccentCyan else TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

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
                                SelectionContainer {
                                    FormattedAiContent(
                                        text = speechText,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
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

                // Preview of previous messages when keyboard is closed
                if (!isKeyboardOpen) {
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
                }
            } else {
                // EXPANDED HISTORY VIEW
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
        }
    }
}

@Composable
private fun CompanionMessageItem(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER
    val clipboardManager = LocalClipboardManager.current
    var isCopied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "Sen" else "Lynia",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isUser) Color(0xFF93C5FD) else AccentCyan
                    )

                    if (message.text.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(message.text))
                                    isCopied = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(2000)
                                        isCopied = false
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                contentDescription = "Kopyala",
                                tint = if (isCopied) Color(0xFF10B981) else TextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (isCopied) "Kopyalandı" else "Kopyala",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isCopied) Color(0xFF10B981) else TextMuted
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    FormattedAiContent(
                        text = message.text,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
