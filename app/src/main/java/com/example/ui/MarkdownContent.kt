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
import androidx.compose.ui.platform.testTag
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
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    val formattedFormula = remember(formula) { MathFormatter.formatToReadable(formula) }
    val formulaLines = remember(formattedFormula) {
        formattedFormula.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF131722),
        border = BorderStroke(1.2.dp, Color(0xFF28354D)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("math_block_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B2234))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2563EB).copy(alpha = 0.25f),
                        border = BorderStroke(0.8.dp, Color(0xFF3B82F6))
                    ) {
                        Text(
                            text = "∑ fx",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Matematiksel Formül / İşlem",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color(0xFFCBD5E1)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(formattedFormula))
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
                        contentDescription = "Kopyala",
                        tint = if (isCopied) Color(0xFF10B981) else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCopied) "Kopyalandı" else "Kopyala",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCopied) Color(0xFF10B981) else TextMuted
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = if (formulaLines.size == 1) Alignment.CenterHorizontally else Alignment.Start
            ) {
                formulaLines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.5.sp,
                            lineHeight = 25.sp,
                            letterSpacing = 0.5.sp
                        ),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
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

object MathFormatter {
    private val superscriptMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ', 'x' to 'ˣ', 'y' to 'ʸ', 'a' to 'ᵃ',
        'b' to 'ᵇ', 'c' to 'ᶜ', 'm' to 'ᵐ', 't' to 'ᵗ'
    )

    private val subscriptMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'h' to 'ₕ', 'i' to 'ᵢ', 'j' to 'ⱼ',
        'k' to 'ₖ', 'l' to 'ₗ', 'm' to 'ₘ', 'n' to 'ₙ', 'o' to 'ₒ',
        'p' to 'ₚ', 'r' to 'ᵣ', 's' to 'ₛ', 't' to 'ₜ', 'u' to 'ᵤ',
        'v' to 'ᵥ', 'x' to 'ₓ'
    )

    fun toSuperscript(text: String): String {
        return text.map { superscriptMap[it] ?: it }.joinToString("")
    }

    fun toSubscript(text: String): String {
        return text.map { subscriptMap[it] ?: it }.joinToString("")
    }

    fun formatToReadable(raw: String): String {
        var text = raw.trim()

        if (text.startsWith("$$") && text.endsWith("$$") && text.length >= 4) {
            text = text.removePrefix("$$").removeSuffix("$$").trim()
        }
        if (text.startsWith("\\[") && text.endsWith("\\]")) {
            text = text.removePrefix("\\[").removeSuffix("\\]").trim()
        }
        if (text.startsWith("\\(") && text.endsWith("\\)")) {
            text = text.removePrefix("\\(").removeSuffix("\\)").trim()
        }

        text = text.replace(Regex("""\\begin\{(aligned|cases|matrix|pmatrix|bmatrix|vmatrix|equation\*?)\}"""), "")
        text = text.replace(Regex("""\\end\{(aligned|cases|matrix|pmatrix|bmatrix|vmatrix|equation\*?)\}"""), "")

        text = text.replace(Regex("""\\left\s*([(\[{|.])"""), "$1")
        text = text.replace(Regex("""\\right\s*([)\]}|.])"""), "$1")
        text = text.replace(Regex("""\\(?:big|Big|bigg|Bigg)[lrm]?"""), "")
        text = text.replace(Regex("""\\(?:text|mathrm|mathbf|mathit|textbf|mbox)\{([^{}]+)\}"""), "$1")
        text = text.replace(Regex("""\\displaystyle\s*"""), "")

        text = text.replace(Regex("""\\quad\s*"""), "   ")
        text = text.replace(Regex("""\\qquad\s*"""), "     ")
        text = text.replace(Regex("""\\(?:,|;|!|:)\s*"""), " ")
        text = text.replace("\\ ", " ")

        text = text.replace(Regex("""\\\\\s*"""), "\n")
        text = text.replace(Regex("""\s*&\s*"""), " ")

        val fracRegex = Regex("""\\(?:d?frac)\{([^{}]+)\}\{([^{}]+)\}""")
        var safetyCounter = 0
        while (fracRegex.containsMatchIn(text) && safetyCounter < 10) {
            text = fracRegex.replace(text) { match ->
                val num = match.groupValues[1].trim()
                val den = match.groupValues[2].trim()
                val cleanNum = if (num.length > 1 && (num.contains("+") || num.contains("-"))) "($num)" else num
                val cleanDen = if (den.length > 1 && (den.contains("+") || den.contains("-"))) "($den)" else den
                "$cleanNum / $cleanDen"
            }
            safetyCounter++
        }

        val nthRootRegex = Regex("""\\sqrt\[([^{}\]]+)\]\{([^{}]+)\}""")
        text = nthRootRegex.replace(text) { match ->
            val rootDegree = toSuperscript(match.groupValues[1].trim())
            val inner = match.groupValues[2].trim()
            "${rootDegree}√($inner)"
        }
        val sqrtRegex = Regex("""\\sqrt\{([^{}]+)\}""")
        text = sqrtRegex.replace(text) { match ->
            val inner = match.groupValues[1].trim()
            "√($inner)"
        }

        val expBracedRegex = Regex("""\^\{([^{}]+)\}""")
        text = expBracedRegex.replace(text) { match ->
            val exp = match.groupValues[1].trim()
            if (exp.all { superscriptMap.containsKey(it) }) {
                toSuperscript(exp)
            } else {
                "^($exp)"
            }
        }
        val expSingleRegex = Regex("""\^([0-9nxyabc])""")
        text = expSingleRegex.replace(text) { match ->
            toSuperscript(match.groupValues[1])
        }

        val subBracedRegex = Regex("""_\{([^{}]+)\}""")
        text = subBracedRegex.replace(text) { match ->
            val sub = match.groupValues[1].trim()
            if (sub.all { subscriptMap.containsKey(it) }) {
                toSubscript(sub)
            } else {
                "_($sub)"
            }
        }
        val subSingleRegex = Regex("""_([0-9nxijkm])""")
        text = subSingleRegex.replace(text) { match ->
            toSubscript(match.groupValues[1])
        }

        text = text.replace("\\pm", "±")
        text = text.replace("\\mp", "∓")
        text = text.replace(Regex("""\\(?:times|cdot|ast)"""), " × ")
        text = text.replace(Regex("""\\(?:div)"""), " ÷ ")
        text = text.replace(Regex("""\\(?:leq|le)"""), " ≤ ")
        text = text.replace(Regex("""\\(?:geq|ge)"""), " ≥ ")
        text = text.replace(Regex("""\\(?:neq|ne)"""), " ≠ ")
        text = text.replace("\\approx", " ≈ ")
        text = text.replace("\\equiv", " ≡ ")
        text = text.replace("\\sim", " ~ ")
        text = text.replace("\\propto", " ∝ ")

        text = text.replace(Regex("""\\(?:to|rightarrow)"""), " → ")
        text = text.replace("\\Rightarrow", " ⇒ ")
        text = text.replace(Regex("""\\(?:leftarrow)"""), " ← ")
        text = text.replace("\\Leftarrow", " ⇐ ")
        text = text.replace("\\leftrightarrow", " ↔ ")
        text = text.replace("\\Leftrightarrow", " ⇔ ")

        text = text.replace("\\in", " ∈ ")
        text = text.replace("\\notin", " ∉ ")
        text = text.replace("\\subset", " ⊂ ")
        text = text.replace("\\subseteq", " ⊆ ")
        text = text.replace("\\cup", " ∪ ")
        text = text.replace("\\cap", " ∩ ")
        text = text.replace("\\emptyset", " ∅ ")
        text = text.replace("\\forall", " ∀ ")
        text = text.replace("\\exists", " ∃ ")
        text = text.replace("\\mathbb{R}", "ℝ")
        text = text.replace("\\mathbb{N}", "ℕ")
        text = text.replace("\\mathbb{Z}", "ℤ")
        text = text.replace("\\mathbb{Q}", "ℚ")
        text = text.replace("\\mathbb{C}", "ℂ")

        text = text.replace("\\int", "∫")
        text = text.replace("\\iint", "∬")
        text = text.replace("\\oint", "∮")
        text = text.replace("\\sum", "∑")
        text = text.replace("\\prod", "∏")
        text = text.replace("\\partial", "∂")
        text = text.replace("\\nabla", "∇")
        text = text.replace("\\infty", "∞")
        text = text.replace(Regex("""\\(?:degree|circ)"""), "°")

        val greekMap = mapOf(
            "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\Gamma" to "Γ",
            "\\delta" to "δ", "\\Delta" to "Δ", "\\epsilon" to "ε", "\\varepsilon" to "ε",
            "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ", "\\Theta" to "Θ",
            "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\Lambda" to "Λ",
            "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\Pi" to "Π",
            "\\rho" to "ρ", "\\sigma" to "σ", "\\Sigma" to "Σ", "\\tau" to "τ",
            "\\upsilon" to "υ", "\\phi" to "φ", "\\varphi" to "φ", "\\Phi" to "Φ",
            "\\chi" to "χ", "\\psi" to "ψ", "\\Psi" to "Ψ", "\\omega" to "ω", "\\Omega" to "Ω"
        )
        for ((macro, symbol) in greekMap) {
            text = text.replace(macro, symbol)
        }

        text = text.replace(Regex("""\\(sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|sinh|cosh|tanh|ln|log|exp|lim|det|gcd|max|min)"""), "$1")

        text = text.lines().joinToString("\n") { line ->
            line.replace(Regex("""[ \t]{2,}"""), " ").trim()
        }.trim()

        return text
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
        // Match inline math \(...\), $...$, highlight ==...==, bold **...**, underline __...__ or <u>...</u>, italic *...*, inline code `...`
        val regex = Regex("""(\\\((.*?)\\\)|\$([^$\n]+)\$|==(.*?)==|\*\*(.*?)\*\*|__(.*?)__|<u>(.*?)<\/u>|\*(.*?)\*|`(.*?)`)""")
        val matches = regex.findAll(sanitized)
        for (match in matches) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > cursor) {
                append(sanitized.substring(cursor, start))
            }
            val fullText = match.value
            when {
                fullText.startsWith("\\(") && fullText.endsWith("\\)") -> {
                    val content = match.groupValues[2]
                    val readable = MathFormatter.formatToReadable(content)
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                    ) {
                        append(" $readable ")
                    }
                }
                fullText.startsWith("$") && fullText.endsWith("$") && !fullText.startsWith("$$") -> {
                    val content = match.groupValues[3]
                    val readable = MathFormatter.formatToReadable(content)
                    withStyle(
                        SpanStyle(
                            color = Color(0xFF93C5FD),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.3.sp
                        )
                    ) {
                        append(" $readable ")
                    }
                }
                fullText.startsWith("==") && fullText.endsWith("==") -> {
                    val content = match.groupValues[4]
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
                    val content = match.groupValues[5]
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
                    val content = match.groupValues[6]
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
                    val content = match.groupValues[7]
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
                    val content = match.groupValues[8]
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = TextPrimary)) {
                        append(content)
                    }
                }
                fullText.startsWith("`") && fullText.endsWith("`") -> {
                    val content = match.groupValues[9]
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
            val language = trimmed.removePrefix("```").trim().lowercase().takeIf { it.isNotBlank() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size && lines[i].trim().startsWith("```")) {
                i++
            }
            if (language in listOf("latex", "math", "tex", "formula")) {
                blocks.add(MarkdownBlock.MathBlock(codeLines.joinToString("\n")))
            } else {
                blocks.add(MarkdownBlock.CodeBlock(language, codeLines.joinToString("\n")))
            }
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

        // Multiline or single-line $$ math blocks
        if (trimmed.startsWith("$$")) {
            if (trimmed.endsWith("$$") && trimmed.length > 4) {
                val formula = trimmed.removePrefix("$$").removeSuffix("$$").trim()
                if (formula.isNotBlank()) {
                    blocks.add(MarkdownBlock.MathBlock(formula))
                }
                i++
                continue
            } else {
                val mathLines = mutableListOf<String>()
                val firstContent = trimmed.removePrefix("$$").trim()
                if (firstContent.isNotBlank()) mathLines.add(firstContent)
                i++
                while (i < lines.size && !lines[i].trim().endsWith("$$")) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val lastContent = lines[i].trim().removeSuffix("$$").trim()
                    if (lastContent.isNotBlank()) mathLines.add(lastContent)
                    i++
                }
                if (mathLines.isNotEmpty()) {
                    blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
                }
                continue
            }
        }

        // \[ ... \] LaTeX equation blocks
        if (trimmed.startsWith("\\[")) {
            if (trimmed.endsWith("\\]")) {
                val formula = trimmed.removePrefix("\\[").removeSuffix("\\]").trim()
                if (formula.isNotBlank()) {
                    blocks.add(MarkdownBlock.MathBlock(formula))
                }
                i++
                continue
            } else {
                val mathLines = mutableListOf<String>()
                val first = trimmed.removePrefix("\\[").trim()
                if (first.isNotBlank()) mathLines.add(first)
                i++
                while (i < lines.size && !lines[i].trim().contains("\\]")) {
                    mathLines.add(lines[i])
                    i++
                }
                if (i < lines.size) {
                    val last = lines[i].trim().replace("\\]", "").trim()
                    if (last.isNotBlank()) mathLines.add(last)
                    i++
                }
                if (mathLines.isNotEmpty()) {
                    blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
                }
                continue
            }
        }

        // \begin{aligned} or \begin{cases} or \begin{equation} blocks
        if (trimmed.startsWith("\\begin{aligned}") || trimmed.startsWith("\\begin{cases}") || trimmed.startsWith("\\begin{equation}")) {
            val mathLines = mutableListOf<String>()
            mathLines.add(trimmed)
            i++
            while (i < lines.size && !lines[i].trim().startsWith("\\end{")) {
                mathLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                mathLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.MathBlock(mathLines.joinToString("\n")))
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
