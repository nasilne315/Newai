package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.model.AttachedFile
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ModelBadgeBg
import com.example.ui.theme.ModelBadgeBorder
import com.example.ui.theme.SendButtonDarkBlue
import com.example.ui.theme.SendButtonDisabled
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.FileUtils

@Composable
fun ChatInputBar(
    inputText: String,
    onInputTextChanged: (String) -> Unit,
    attachedFile: AttachedFile?,
    isRecordingVoice: Boolean,
    isLoading: Boolean,
    onAttachFile: (AttachedFile?) -> Unit,
    onRemoveAttachedFile: () -> Unit,
    onToggleVoiceRecording: () -> Unit,
    onSendMessage: () -> Unit,
    isWebSearchEnabled: Boolean = true,
    onToggleWebSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            val currentUri = tempCameraUri
            if (success && currentUri != null) {
                val file = FileUtils.getAttachedFile(context, currentUri)
                onAttachFile(file)
            }
        }
    )

    fun launchCamera() {
        try {
            val photoDir = java.io.File(context.cacheDir, "camera_photos").apply { mkdirs() }
            val photoFile = java.io.File.createTempFile("camera_photo_", ".jpg", photoDir)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Kamera başlatılamadı: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                launchCamera()
            } else {
                android.widget.Toast.makeText(context, "Fotoğraf çekmek için kamera izni gerekiyor.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                val file = FileUtils.getAttachedFile(context, uri)
                onAttachFile(file)
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                onToggleVoiceRecording()
            }
        }
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecordingVoice) 1.22f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val canSend = (inputText.isNotBlank() || attachedFile != null) && !isLoading
    val sendButtonColor by animateColorAsState(
        targetValue = if (canSend) SendButtonDarkBlue else SendButtonDisabled,
        label = "sendButtonColor"
    )

    val handleSendMessage: () -> Unit = {
        if (canSend) {
            onSendMessage()
            coroutineScope.launch {
                // Automatically retain focus in the text field after sending
                delay(50)
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    // Keep focus retained whenever loading completes so the user never has to click again
    LaunchedEffect(isLoading) {
        if (!isLoading) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
            .testTag("floating_input_wrapper")
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = DarkSurfaceVariant,
            border = BorderStroke(1.2.dp, DarkBorder),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("floating_pill_frame")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                AnimatedVisibility(
                    visible = attachedFile != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    if (attachedFile != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ModelBadgeBg)
                                .border(BorderStroke(1.dp, ModelBadgeBorder), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("attached_file_preview")
                        ) {
                            if (attachedFile.isImage) {
                                AsyncImage(
                                    model = attachedFile.uri,
                                    contentDescription = "Image preview",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else if (attachedFile.isPdf) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFDC2626)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureAsPdf,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2563EB)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = attachedFile.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (attachedFile.formattedSize.isNotBlank()) {
                                    Text(
                                        text = attachedFile.formattedSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dosyayı kaldır",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onRemoveAttachedFile() }
                            )
                        }
                    }
                }

                if (isRecordingVoice) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Dinleniyor... Konuşun (canlı yazıya dökülüyor)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Color(0xFFFCA5A5)
                        )
                    }
                }

                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChanged,
                    enabled = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .focusRequester(focusRequester)
                        .pointerHoverIcon(PointerIcon.Text)
                        .onPreviewKeyEvent { event ->
                            val isEnter = event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (isEnter) {
                                if (!event.isShiftPressed) {
                                    // Hardware Enter key sends message immediately without newline
                                    if (event.type == KeyEventType.KeyDown && canSend) {
                                        handleSendMessage()
                                    }
                                    true
                                } else {
                                    // Shift+Enter inserts a new line
                                    false
                                }
                            } else {
                                false
                            }
                        }
                        .testTag("chat_input_text_field"),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    cursorBrush = SolidColor(TextPrimary),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (canSend) {
                                handleSendMessage()
                            }
                        }
                    ),
                    decorationBox = { innerTextField ->
                        if (inputText.isEmpty() && !isRecordingVoice) {
                            Text(
                                text = if (isLoading) "Yapay zeka yanıtlıyor... (yazmaya devam edebilirsiniz)" else "Bir mesaj yazın veya sesli konuşun...",
                                style = TextStyle(
                                    color = if (isLoading) TextSecondary.copy(alpha = 0.7f) else TextMuted,
                                    fontSize = 15.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                filePickerLauncher.launch("*/*")
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(36.dp)
                                .pointerHoverIcon(if (!isLoading) PointerIcon.Hand else PointerIcon.Default)
                                .testTag("attach_file_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = "Dosya ekle",
                                tint = if (!isLoading) TextSecondary else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val hasCameraPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasCameraPermission) {
                                    launchCamera()
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(36.dp)
                                .pointerHoverIcon(if (!isLoading) PointerIcon.Hand else PointerIcon.Default)
                                .testTag("camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Fotoğraf çek",
                                tint = if (!isLoading) TextSecondary else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleWebSearch,
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(36.dp)
                                .pointerHoverIcon(if (!isLoading) PointerIcon.Hand else PointerIcon.Default)
                                .testTag("web_search_toggle_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(if (isWebSearchEnabled) Color(0xFF0284C7).copy(alpha = 0.25f) else Color.Transparent)
                                    .border(
                                        width = if (isWebSearchEnabled) 1.dp else 0.dp,
                                        color = if (isWebSearchEnabled) Color(0xFF38BDF8) else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = if (isWebSearchEnabled) "Web Arama: Açık" else "Web Arama: Kapalı",
                                    tint = if (isWebSearchEnabled) Color(0xFF38BDF8) else if (!isLoading) TextSecondary else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasAudioPermission) {
                                    onToggleVoiceRecording()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .size(36.dp)
                                .scale(micScale)
                                .pointerHoverIcon(if (!isLoading) PointerIcon.Hand else PointerIcon.Default)
                                .testTag("voice_to_text_button")
                        ) {
                            if (isRecordingVoice) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Stop,
                                        contentDescription = "Kaydı bitir",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Sesle yaz",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(sendButtonColor)
                                .pointerHoverIcon(if (canSend) PointerIcon.Hand else PointerIcon.Default)
                                .clickable(enabled = canSend, onClick = handleSendMessage)
                                .testTag("send_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Gönder",
                                tint = if (canSend) Color.White else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
