package com.example.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Html
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GeneratedFile
import com.example.ui.theme.DarkBg
import com.example.ui.theme.TextMuted
import com.example.util.FileExporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullScreenFilePreview(
    file: GeneratedFile,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    // Toggle states for visual modes
    var isHtmlLiveMode by remember { mutableStateOf(file.isHtml) }
    var isSvgPreviewMode by remember { mutableStateOf(file.isSvg) }
    var isMdPreviewMode by remember { mutableStateOf(file.isMarkdown) }

    val accentColor = when {
        file.isHtml -> Color(0xFFE44D26)
        file.isTxt -> Color(0xFF3B82F6)
        file.isSvg -> Color(0xFFEC4899)
        file.isJson -> Color(0xFF10B981)
        file.isMarkdown -> Color(0xFF8B5CF6)
        else -> Color(0xFF06B6D4)
    }

    val typeIcon = when {
        file.isHtml -> Icons.Rounded.Html
        file.isTxt -> Icons.Rounded.Description
        file.isSvg -> Icons.Rounded.Image
        file.isJson -> Icons.Rounded.DataObject
        file.isMarkdown -> Icons.Rounded.Description
        else -> Icons.Rounded.Code
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .testTag("fullscreen_file_preview_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // 1. Üst Başlık Çubuğu: Sol Geri Butonu + Dosya Adı + Sağ Gri İndir/Paylaş Butonları
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161920))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Sol Üst Geri Butonu + Dosya Bilgisi
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${file.typeDisplayName} • ${file.formattedSize}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Sağ Üst Gri Butonlar: İndir ve Paylaş
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gri İndir Butonu
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF222630),
                        border = BorderStroke(1.dp, Color(0xFF333A48)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                FileExporter.saveToDownloads(
                                    context = context,
                                    fileName = file.fileName,
                                    content = file.content,
                                    mimeType = file.mimeType
                                )
                            }
                            .testTag("preview_download_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "İndir",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "İndir",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    // Gri Paylaş Butonu
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF222630),
                        border = BorderStroke(1.dp, Color(0xFF333A48)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                FileExporter.shareFile(
                                    context = context,
                                    fileName = file.fileName,
                                    content = file.content,
                                    mimeType = file.mimeType
                                )
                            }
                            .testTag("preview_share_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Paylaş",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Paylaş",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // 2. Mod Seçici Bar: Canlı Web / Kaynak Kod Butonları
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1E26))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    file.isHtml -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PreviewModeChip(
                                label = "Canlı Web",
                                icon = Icons.Rounded.PlayArrow,
                                isSelected = isHtmlLiveMode,
                                onClick = { isHtmlLiveMode = true }
                            )
                            PreviewModeChip(
                                label = "Kaynak Kod",
                                icon = Icons.Rounded.Code,
                                isSelected = !isHtmlLiveMode,
                                onClick = { isHtmlLiveMode = false }
                            )
                        }
                    }
                    file.isSvg -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PreviewModeChip(
                                label = "Vektör Görünümü",
                                icon = Icons.Rounded.Image,
                                isSelected = isSvgPreviewMode,
                                onClick = { isSvgPreviewMode = true }
                            )
                            PreviewModeChip(
                                label = "SVG Kodu",
                                icon = Icons.Rounded.Code,
                                isSelected = !isSvgPreviewMode,
                                onClick = { isSvgPreviewMode = false }
                            )
                        }
                    }
                    file.isMarkdown -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PreviewModeChip(
                                label = "Okuma Modu",
                                icon = Icons.Rounded.Description,
                                isSelected = isMdPreviewMode,
                                onClick = { isMdPreviewMode = true }
                            )
                            PreviewModeChip(
                                label = "Ham Metin",
                                icon = Icons.Rounded.Code,
                                isSelected = !isMdPreviewMode,
                                onClick = { isMdPreviewMode = false }
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Belge İçeriği • ${file.content.lines().size} Satır",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                // Kopyala Butonu
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(file.content))
                            isCopied = true
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("preview_copy_button")
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = if (isCopied) Color(0xFF10B981) else Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Kopyalandı" else "Kopyala",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) Color(0xFF10B981) else Color(0xFF94A3B8)
                    )
                }
            }

            // Çizgi Ayracı: Canlı Web ve Kaynak Kod barı ile altındaki içerik arasına net sınır çizer
            HorizontalDivider(color = Color(0xFF262C38), thickness = 1.dp)

            // 3. Dosya İçeriği Ekranı (Kesinlikle Mod Barının ve Çizginin Altından Başlar)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .background(Color(0xFF141518))
            ) {
                when {
                    // 1. Canlı HTML Web Görünümü (Koyu Arka Plan Destekli)
                    file.isHtml && isHtmlLiveMode -> {
                        val darkHtml = remember(file.content) { FileExporter.prepareDarkHtml(file.content) }
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = WebViewClient()
                                    setBackgroundColor(android.graphics.Color.parseColor("#141518"))
                                    isVerticalScrollBarEnabled = true
                                    isHorizontalScrollBarEnabled = true
                                    overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = false
                                    }
                                    loadDataWithBaseURL(null, darkHtml, "text/html", "UTF-8", null)
                                }
                            },
                            update = { webView ->
                                webView.setBackgroundColor(android.graphics.Color.parseColor("#141518"))
                                webView.loadDataWithBaseURL(null, darkHtml, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        )
                    }

                    // 2. SVG Vektör Görünümü
                    file.isSvg && isSvgPreviewMode -> {
                        val svgHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                <style>
                                    body {
                                        margin: 0;
                                        padding: 24px;
                                        display: flex;
                                        justify-content: center;
                                        align-items: center;
                                        min-height: 90vh;
                                        background-color: #141518;
                                        box-sizing: border-box;
                                    }
                                    svg {
                                        max-width: 95%;
                                        max-height: 80vh;
                                        height: auto;
                                    }
                                </style>
                            </head>
                            <body>${file.content}</body>
                            </html>
                        """.trimIndent()

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    webViewClient = WebViewClient()
                                    setBackgroundColor(android.graphics.Color.parseColor("#141518"))
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = false
                                    loadDataWithBaseURL(null, svgHtml, "text/html", "UTF-8", null)
                                }
                            },
                            update = { webView ->
                                webView.setBackgroundColor(android.graphics.Color.parseColor("#141518"))
                                webView.loadDataWithBaseURL(null, svgHtml, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        )
                    }

                    // 3. Markdown Okuma Modu
                    file.isMarkdown && isMdPreviewMode -> {
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF141518))
                                .verticalScroll(scrollState)
                                .padding(horizontal = 20.dp, vertical = 16.dp)
                        ) {
                            FormattedAiContent(text = file.content)
                        }
                    }

                    // 4. Koyu Temalı Kaynak Kod / Metin Görüntüleyici
                    else -> {
                        FullScreenCodeViewer(
                            content = file.content,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF2B3240) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, Color(0xFF3E4758)) else null,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun FullScreenCodeViewer(
    content: String,
    modifier: Modifier = Modifier
) {
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lines = remember(content) { content.lines() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF0F1115))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
        ) {
            // Satır Numaraları Kolonu (Üstten 16dp boşlukla başlar, kodla birlikte senkron kayar)
            Column(
                modifier = Modifier
                    .background(Color(0xFF151820))
                    .padding(top = 16.dp, bottom = 48.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.End
            ) {
                lines.indices.forEach { index ->
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 22.sp
                        ),
                        color = Color(0xFF475569)
                    )
                }
            }

            // Kod / Metin İçeriği (Yatay kaydırma ve üstten 16dp boşlukla satır 1 tam görünür)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(horizontalScroll)
                    .padding(top = 16.dp, bottom = 48.dp, start = 12.dp, end = 16.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.8.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}
