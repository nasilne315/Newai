package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatQuote
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SendButtonDarkBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class MathBlock(val formula: String) : MarkdownBlock()
    data class Callout(val text: String) : MarkdownBlock()
    object Divider : MarkdownBlock()
    data class SectionBox(val title: String?, val content: String) : MarkdownBlock()
}

@Composable
fun FormattedAiContent(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    HeadingBlockView(block)
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = TextPrimary
                    )
                }
                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, end = 10.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SendButtonDarkBlue)
                        )
                        Text(
                            text = formatInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 23.sp
                            ),
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = DarkSurfaceVariant,
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                        ) {
                            Text(
                                text = block.number,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFF60A5FA),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = formatInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 23.sp
                            ),
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownBlock.Callout -> {
                    CalloutBlockView(block.text)
                }
                is MarkdownBlock.SectionBox -> {
                    SectionBoxView(block)
                }
                is MarkdownBlock.Divider -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        HorizontalDivider(
                            color = DarkBorder,
                            thickness = 1.dp
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    RenderTableBlock(block)
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockView(block)
                }
                is MarkdownBlock.MathBlock -> {
                    MathBlockView(block.formula)
                }
            }
        }
    }
}

@Composable
private fun HeadingBlockView(heading: MarkdownBlock.Heading) {
    val (color, fontSize, hasBottomLine) = when (heading.level) {
        1 -> Triple(Color(0xFF60A5FA), 19.sp, true)
        2 -> Triple(Color(0xFF93C5FD), 17.sp, true)
        else -> Triple(Color(0xFFE2E8F0), 16.sp, false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height((fontSize.value * 1.1).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatInlineMarkdown(heading.text),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                    lineHeight = (fontSize.value * 1.3).sp,
                    letterSpacing = 0.3.sp
                ),
                color = color
            )
        }
        if (hasBottomLine) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                color = DarkBorder.copy(alpha = 0.6f),
                thickness = 0.8.dp,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun SectionBoxView(section: MarkdownBlock.SectionBox) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Color(0xFF2B3A55)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (!section.title.isNullOrBlank()) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF60A5FA)
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                HorizontalDivider(color = DarkBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = formatInlineMarkdown(section.content),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                ),
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun CalloutBlockView(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurfaceVariant.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatQuote,
                contentDescription = null,
                tint = Color(0xFF60A5FA),
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatInlineMarkdown(text),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = 14.5.sp,
                    lineHeight = 22.sp
                ),
                color = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
private fun CodeBlockView(block: MarkdownBlock.CodeBlock) {
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF16171A),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (block.language ?: "code").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF93C5FD)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(block.code))
                            isCopied = true
                            coroutineScope.launch {
                                delay(2000)
                                isCopied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) Color(0xFF10B981) else TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Kopyalandı" else "Kopyala",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) Color(0xFF10B981) else TextMuted
                    )
                }
            }

            Text(
                text = block.code,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                ),
                color = TextPrimary,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun MathBlockView(formula: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Color(0xFF384357)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formula,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                ),
                color = Color(0xFF93C5FD)
            )
        }
    }
}

@Composable
private fun RenderTableBlock(table: MarkdownBlock.Table) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        val horizontalScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .horizontalScroll(horizontalScrollState)
                .padding(8.dp)
        ) {
            if (table.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    table.headers.forEachIndexed { i, header ->
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = header.trim(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF93C5FD)
                                )
                            )
                        }
                        if (i < table.headers.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(16.dp)
                                    .background(DarkBorder)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            table.rows.forEachIndexed { rowIndex, row ->
                val rowBg = if (rowIndex % 2 == 1) DarkBg.copy(alpha = 0.55f) else Color.Transparent
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(rowBg)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .padding(horizontal = 6.dp)
                        ) {
                            Text(
                                text = formatInlineMarkdown(cell.trim()),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                color = TextPrimary
                            )
                        }
                        if (colIndex < row.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(DarkBorder.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
                if (rowIndex < table.rows.size - 1) {
                    HorizontalDivider(color = DarkBorder.copy(alpha = 0.4f), thickness = 0.6.dp)
                }
            }
        }
    }
}

fun formatInlineMarkdown(input: String): AnnotatedString {
    val sanitized = input
        .replace(Regex("""^\s*#{1,6}\s*"""), "")
        .replace("##", "")
        .replace("&&", "")
        .trim()

    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(==(.*?)==|\*\*(.*?)\*\*|__(.*?)__|<u>(.*?)<\/u>|\*(.*?)\*|`(.*?)`)""")
        val matches = regex.findAll(sanitized)
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                append(sanitized.substring(cursor, start))
            }
            val fullText = match.value
            when {
                fullText.startsWith("==") && fullText.endsWith("==") -> {
                    val content = match.groupValues[2]
                    withStyle(
                        SpanStyle(
                            background = Color(0xFF1E3A5F),
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append(" $content ")
                    }
                }
                fullText.startsWith("**") && fullText.endsWith("**") -> {
                    val content = match.groupValues[3]
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9)
                        )
                    ) {
                        append(content)
                    }
                }
                (fullText.startsWith("__") && fullText.endsWith("__")) -> {
                    val content = match.groupValues[4]
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(content)
                    }
                }
                (fullText.startsWith("<u>") && fullText.endsWith("</u>")) -> {
                    val content = match.groupValues[5]
                    withStyle(
                        SpanStyle(
                            textDecoration = TextDecoration.Underline,
                            color = Color(0xFF93C5FD)
                        )
                    ) {
                        append(content)
                    }
                }
                fullText.startsWith("*") && fullText.endsWith("*") -> {
                    val content = match.groupValues[6]
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = TextPrimary)) {
                        append(content)
                    }
                }
                fullText.startsWith("`") && fullText.endsWith("`") -> {
                    val content = match.groupValues[7]
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = DarkSurfaceVariant,
                            color = Color(0xFF93C5FD)
                        )
                    ) {
                        append(" $content ")
                    }
                }
                else -> {
                    append(fullText)
                }
            }
            cursor = end
        }
        if (cursor < sanitized.length) {
            append(sanitized.substring(cursor))
        }
    }
}

fun parseMarkdown(raw: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.lines()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            i++
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks.add(MarkdownBlock.Divider)
            i++
            continue
        }

        if (trimmed.startsWith("```")) {
            val language = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            continue
        }

        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            val parsedTable = parseTable(tableLines)
            if (parsedTable != null) {
                blocks.add(parsedTable)
                continue
            }
        }

        if (trimmed.startsWith(">")) {
            val calloutLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trim().startsWith(">")) {
                calloutLines.add(lines[i].trim().removePrefix(">").trim())
                i++
            }
            blocks.add(MarkdownBlock.Callout(calloutLines.joinToString(" ")))
            continue
        }

        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length
            val headingText = trimmed.drop(level).trim()
            blocks.add(MarkdownBlock.Heading(level, headingText))
            i++
            continue
        }

        if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && trimmed.length > 2) {
            val itemText = trimmed.drop(2).trim()
            blocks.add(MarkdownBlock.BulletItem(itemText))
            i++
            continue
        }

        val numberedMatch = Regex("""^(\d+)\.\s+(.*)""").find(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val content = numberedMatch.groupValues[2]
            blocks.add(MarkdownBlock.NumberedItem(num, content))
            i++
            continue
        }

        if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) {
            val formula = trimmed.removePrefix("$$").removeSuffix("$$").trim()
            blocks.add(MarkdownBlock.MathBlock(formula))
            i++
            continue
        }

        blocks.add(MarkdownBlock.Paragraph(trimmed))
        i++
    }
    return blocks
}

private fun parseTable(lines: List<String>): MarkdownBlock.Table? {
    if (lines.size < 2) return null
    fun splitCells(line: String): List<String> {
        return line.trim()
            .removePrefix("|")
            .removeSuffix("|")
            .split("|")
            .map { it.trim() }
    }
    val headerCells = splitCells(lines[0])
    val isSeparatorLine = lines[1].replace("-", "").replace(":", "").replace("|", "").trim().isEmpty()
    val startIndex = if (isSeparatorLine) 2 else 1
    val rows = mutableListOf<List<String>>()
    for (k in startIndex until lines.size) {
        val rowCells = splitCells(lines[k])
        if (rowCells.isNotEmpty()) {
            rows.add(rowCells)
        }
    }
    return MarkdownBlock.Table(headerCells, rows)
}
