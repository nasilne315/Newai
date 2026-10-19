package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ChatMessage
import com.example.model.GeneratedFile
import com.example.model.MessageSender
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.ModelBadgeBg
import com.example.ui.theme.ModelBadgeBorder
import com.example.ui.theme.SearchGlow
import com.example.ui.theme.SendButtonDarkBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThinkingGlow
import com.example.ui.theme.UserBubbleDarkBlue
import com.example.ui.theme.UserBubbleDarkBlueBorder
import com.example.util.FileExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    listState: LazyListState,
    isSpeaking: Boolean = false,
    speakingMessageId: String? = null,
    onToggleSpeak: (text: String, id: String) -> Unit = { _, _ -> },
    onSuggestionClick: (String) -> Unit = {},
    onOpenFile: (GeneratedFile) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        if (messages.isEmpty() && !isLoading) {
            EmptyConversationView(onSuggestionClick = onSuggestionClick)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("chat_messages_lazy_column"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    when (message.sender) {
                        MessageSender.USER -> {
                            UserMessageItem(message = message)
                        }
                        MessageSender.AI -> {
                            AiMessageItem(
                                message = message,
                                onOpenFile = onOpenFile,
                                isSpeaking = isSpeaking,
                                speakingMessageId = speakingMessageId,
                                onToggleSpeak = onToggleSpeak
                            )
                        }
                    }
                }
                if (isLoading && messages.none { it.isStreaming }) {
                    item {
                        AiLoadingItem()
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyConversationView(
    onSuggestionClick: (String) -> Unit
) {
    val greetingList = listOf(
        "Hoş geldiniz efendim.",
        "Sıra sizde.",
        "Bugün ne yapıyoruz?",
        "Aklınızda ne var?",
        "Nasıl yardımcı olabilirim?"
    )
    val greeting = remember { greetingList.random() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(DarkSurface)
                    .border(BorderStroke(1.dp, DarkBorder), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = SendButtonDarkBlue,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    letterSpacing = 0.4.sp
                ),
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Kişisel asistanın hazır. Gününü planlayabilir, görevlerini inceleyebilir, kod yazabilir veya webde araştırma yapabilirsin.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.widthIn(max = 380.dp)
            ) {
                SuggestionItem(
                    icon = Icons.Rounded.TrackChanges,
                    title = "Bugünümü Planla & Önceliklendir",
                    subtitle = "Görevlerime ve hedeflerime göre gün planı hazırla",
                    onClick = { onSuggestionClick("Kayıtlı görevlerimi ve önceliklerimi inceleyerek bugün için verimli bir zaman ve eylem planı çıkarır mısın?") }
                )
                SuggestionItem(
                    icon = Icons.Rounded.Html,
                    title = "Canlı Web Sayfası (HTML) Kodla",
                    subtitle = "Oluşturup uygulama içinde canlı önizle",
                    onClick = { onSuggestionClick("Bana tek dosyada çalışan şık, modern bir hesap makinesi HTML sayfası oluşturur musun?") }
                )
                SuggestionItem(
                    icon = Icons.Rounded.Description,
                    title = "Not veya Rapor Belgesi (.txt) Yaz",
                    subtitle = "Doğrudan cihaza kaydedilebilir belge oluştur",
                    onClick = { onSuggestionClick("Bana haftalık verimlilik ve odaklanma stratejileri içeren bir not belgesi hazırla.") }
                )
                SuggestionItem(
                    icon = Icons.Rounded.Search,
                    title = "Canlı İnternet Araması & Güncel Bilgi",
                    subtitle = "Web kaynaklarıyla en son gelişmeleri araştır",
                    onClick = { onSuggestionClick("Bugün dünyada ve teknolojide öne çıkan en güncel haberler nelerdir? Kaynaklarıyla özetle.") }
                )
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SendButtonDarkBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun UserMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("user_message_row"),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = 20.dp,
                bottomEnd = 6.dp
            ),
            color = UserBubbleDarkBlue,
            border = BorderStroke(1.dp, UserBubbleDarkBlueBorder),
            modifier = Modifier
                .widthIn(max = 330.dp)
                .testTag("user_message_bubble")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                val attached = message.attachedFile
                if (attached != null) {
                    if (attached.isImage) {
                        AsyncImage(
                            model = attached.uri,
                            contentDescription = "User image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F2342))
                                .border(BorderStroke(1.dp, Color(0xFF1E3A5F)), RoundedCornerShape(10.dp))
                                .padding(8.dp)
                        ) {
                            if (attached.isPdf) {
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
                                    text = attached.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (attached.formattedSize.isNotBlank()) {
                                    Text(
                                        text = attached.formattedSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                } else if (message.imageUri != null) {
                    AsyncImage(
                        model = message.imageUri,
                        contentDescription = "User image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = 8.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AiMessageItem(
    message: ChatMessage,
    onOpenFile: (GeneratedFile) -> Unit,
    isSpeaking: Boolean = false,
    speakingMessageId: String? = null,
    onToggleSpeak: (text: String, id: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var isThinkingExpanded by remember { mutableStateOf(false) }
    var isSourcesExpanded by remember { mutableStateOf(false) }
    var copiedToClipboard by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Automatically detect generated files (Artifacts) from code blocks (HTML, TXT, SVG, etc.)
    val extractedFiles = remember(message.text) {
        if (message.text.isNotBlank()) {
            FileExporter.extractGeneratedFiles(message.text)
        } else emptyList()
    }
    val allFiles = (message.generatedFiles + extractedFiles).distinctBy { it.fileName }

    // Detect if a file is currently being generated while streaming
    val generatingFileName = remember(message.text, message.isStreaming) {
        if (message.isStreaming) {
            FileExporter.getGeneratingFileName(message.text)
        } else null
    }

    // Strip code blocks so raw code is NEVER shown in the chat text
    val displayText = remember(message.text, allFiles, message.isStreaming) {
        FileExporter.stripFileCodeBlocks(message.text, allFiles, message.isStreaming)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_message_container"),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 4.dp)
        ) {
            // Thinking trace
            if (message.thinkingProcess != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ModelBadgeBg,
                    border = BorderStroke(1.dp, ModelBadgeBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isThinkingExpanded = !isThinkingExpanded }
                        .testTag("thinking_process_pill")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = null,
                                    tint = ThinkingGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Düşünce Süreci",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = ThinkingGlow
                                )
                            }
                            Icon(
                                imageVector = if (isThinkingExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = ThinkingGlow,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        AnimatedVisibility(
                            visible = isThinkingExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    text = message.thinkingProcess,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        lineHeight = 18.sp
                                    ),
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Message Body
            if (displayText.isNotBlank()) {
                FormattedAiContent(
                    text = displayText,
                    modifier = Modifier.testTag("ai_plain_text_message")
                )
            } else if (message.text.isEmpty() && message.isStreaming) {
                AiLoadingItem()
            }

            // Actively generating file card (spinning indicator while file code is being created)
            if (generatingFileName != null) {
                Spacer(modifier = Modifier.height(8.dp))
                GeneratingFileCard(
                    fileName = generatingFileName,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            // Artifact File Cards (Generated HTML, TXT, SVG, JSON files)
            // Displays sleek file card in chat; tapping opens full-screen preview with download & share buttons
            if (allFiles.isNotEmpty()) {
                if (displayText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                allFiles.forEach { file ->
                    ArtifactCard(
                        file = file,
                        onOpenFile = onOpenFile,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // Image display
            if (message.imageUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = message.imageUri,
                    contentDescription = "Generated image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, DarkBorder), RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // Bottom Actions Bar: Copy | Sources
            if (!message.isStreaming && message.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Copy button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                            .border(BorderStroke(1.dp, DarkBorder), RoundedCornerShape(8.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(message.text))
                                copiedToClipboard = true
                                coroutineScope.launch {
                                    delay(2000)
                                    copiedToClipboard = false
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("copy_message_button")
                    ) {
                        Icon(
                            imageVector = if (copiedToClipboard) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                            contentDescription = "Mesajı kopyala",
                            tint = if (copiedToClipboard) Color(0xFF10B981) else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (copiedToClipboard) "Kopyalandı" else "Kopyala",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (copiedToClipboard) Color(0xFF10B981) else TextSecondary
                        )
                    }

                    // TTS Read Aloud / Stop button
                    val isThisSpeaking = isSpeaking && speakingMessageId == message.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isThisSpeaking) Color(0xFF132F2A) else DarkSurface)
                            .border(
                                BorderStroke(1.dp, if (isThisSpeaking) Color(0xFF10B981) else DarkBorder),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleSpeak(displayText, message.id) }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("tts_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (isThisSpeaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isThisSpeaking) "Durdur" else "Sesli dinle",
                            tint = if (isThisSpeaking) Color(0xFF10B981) else TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isThisSpeaking) "Durdur" else "Dinle",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isThisSpeaking) Color(0xFF10B981) else TextSecondary
                        )
                    }

                    // Collapsed sources button
                    if (message.groundingSources.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSourcesExpanded) Color(0xFF132A46) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSourcesExpanded) Color(0xFF38BDF8) else DarkBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isSourcesExpanded = !isSourcesExpanded }
                                .testTag("sources_toggle_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = "Kaynaklar",
                                    tint = SearchGlow,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Kaynaklar (${message.groundingSources.size})",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = SearchGlow
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = if (isSourcesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = SearchGlow,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

                // Expandable sources card right under the action row
                AnimatedVisibility(
                    visible = isSourcesExpanded && message.groundingSources.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF161A22),
                        border = BorderStroke(1.dp, Color(0xFF243042)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("expanded_sources_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (message.searchQueries.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Arama Sorgusu: " + message.searchQueries.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider(color = DarkBorder.copy(alpha = 0.5f), thickness = 0.8.dp)
                            }

                            message.groundingSources.forEachIndexed { index, source ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(source.url))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Ignored
                                            }
                                        }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = SearchGlow
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = source.title,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = Color(0xFFE2E8F0),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = source.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Kaynağa git",
                                        tint = SearchGlow,
                                        modifier = Modifier.size(14.dp)
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
private fun AiLoadingItem(modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("ai_loading_indicator")
    ) {
        CircularProgressIndicator(
            color = TextSecondary,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Düşünüyor...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
        )
    }
}
