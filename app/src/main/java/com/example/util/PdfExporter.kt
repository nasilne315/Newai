package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    private const val PAGE_WIDTH = 595 // A4 standard width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 standard height in points (72 dpi)
    private const val MARGIN_HORIZONTAL = 46
    private const val MARGIN_TOP = 48
    private const val MARGIN_BOTTOM = 52
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2) // 503 pt
    private const val FOOTER_Y = 802

    sealed class DocElement {
        data class Title(val text: String) : DocElement()
        data class Heading1(val text: String) : DocElement()
        data class Heading2(val text: String) : DocElement()
        data class Paragraph(val text: String) : DocElement()
        data class Bullet(val text: String) : DocElement()
        data class Numbered(val number: String, val text: String) : DocElement()
        data class Callout(val label: String, val text: String) : DocElement()
        object Divider : DocElement()
    }

    /**
     * Cleans raw AI markdown and removes any code block syntax, tags, raw LaTeX, and markdown hashes.
     * Ensures ZERO code or raw tags bleed into the rendered PDF.
     */
    fun sanitizeText(raw: String): String {
        var text = raw

        // Remove opening/closing code fences like ```file:name.pdf or ```markdown
        text = text.replace(Regex("""```[a-zA-Z0-9_\-\.:]*"""), "")
        text = text.replace("```", "")

        // Remove HTML tags
        text = text.replace(Regex("""<[^>]+>"""), "")

        // Remove LaTeX environments while keeping content clean
        text = text.replace(Regex("""\\begin\{[^}]*\}"""), "")
        text = text.replace(Regex("""\\end\{[^}]*\}"""), "")
        text = text.replace(Regex("""\\text\{([^}]*)\}"""), "$1")
        text = text.replace(Regex("""\\mathbf\{([^}]*)\}"""), "$1")
        text = text.replace(Regex("""\\left|\\right"""), "")
        text = text.replace(Regex("""\$\$([^\$]+)\$\$"""), "$1")
        text = text.replace(Regex("""\$([^\$]+)\$"""), "$1")

        // Remove markdown bold/italic asterisks and underscores
        text = text.replace(Regex("""\*\*\*([^*]+)\*\*\*"""), "$1")
        text = text.replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        text = text.replace(Regex("""\*([^*]+)\*"""), "$1")
        text = text.replace(Regex("""___([^_]+)___"""), "$1")
        text = text.replace(Regex("""__([^_]+)__"""), "$1")
        text = text.replace(Regex("""_([^_]+)_"""), "$1")

        // Remove inline backticks
        text = text.replace(Regex("""`([^`]+)`"""), "$1")
        text = text.replace("`", "")

        // Remove markdown link syntax [title](url) -> title
        text = text.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")

        return text.trim()
    }

    /**
     * Parses the sanitized document content into structured document elements
     * to apply high-fidelity typographic layout in the PDF.
     */
    fun parseDocument(rawContent: String, fallbackTitle: String): Pair<String, List<DocElement>> {
        val sanitized = sanitizeText(rawContent)
        val lines = sanitized.lines()
        val elements = mutableListOf<DocElement>()

        var docTitle = ""
        var isFirstHeadingFound = false

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.isBlank()) {
                i++
                continue
            }

            // Check for horizontal divider line
            if (line.matches(Regex("""^[-*_]{3,}$"""))) {
                elements.add(DocElement.Divider)
                i++
                continue
            }

            // Check for Headings: # Heading, ## Heading, ### Heading or ALL CAPS lines
            val headingMatch = Regex("""^(#{1,4})\s*(.*)$""").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                val text = headingMatch.groupValues[2].trim()

                if (!isFirstHeadingFound && level <= 2) {
                    docTitle = text
                    isFirstHeadingFound = true
                } else if (level == 1) {
                    elements.add(DocElement.Heading1(text))
                } else {
                    elements.add(DocElement.Heading2(text))
                }
                i++
                continue
            }

            // Check for bullet list item: - , * , • , +
            val bulletMatch = Regex("""^[-*•+]\s+(.*)$""").find(line)
            if (bulletMatch != null) {
                val bulletText = bulletMatch.groupValues[1].trim()
                elements.add(DocElement.Bullet(bulletText))
                i++
                continue
            }

            // Check for numbered list item: 1. , 2) , A.
            val numberedMatch = Regex("""^(\d{1,3}[.)]|[A-Za-z][.)])\s+(.*)$""").find(line)
            if (numberedMatch != null) {
                val num = numberedMatch.groupValues[1].trim()
                val numText = numberedMatch.groupValues[2].trim()
                elements.add(DocElement.Numbered(num, numText))
                i++
                continue
            }

            // Check for Callout box: "Not:", "Önemli:", "Püf Noktası:", "Uyarı:", "İpucu:"
            val calloutMatch = Regex("""^(Not|Önemli|Püf Noktası|Uyarı|İpucu|Formül|Özet|Dikkat):\s*(.*)$""", RegexOption.IGNORE_CASE).find(line)
            if (calloutMatch != null) {
                val label = calloutMatch.groupValues[1].trim()
                val body = calloutMatch.groupValues[2].trim()
                elements.add(DocElement.Callout(label, body))
                i++
                continue
            }

            // Treat short capitalized standalone line as Heading2 if not too long
            if (line.length in 3..55 && line.all { it.isUpperCase() || it.isWhitespace() || it.isDigit() || it in ".,:-" }) {
                if (!isFirstHeadingFound) {
                    docTitle = line
                    isFirstHeadingFound = true
                } else {
                    elements.add(DocElement.Heading1(line))
                }
                i++
                continue
            }

            // Standard Paragraph - accumulate until blank line or next element
            val paragraphBuilder = StringBuilder(line)
            var nextIndex = i + 1
            while (nextIndex < lines.size) {
                val nextLine = lines[nextIndex].trim()
                if (nextLine.isBlank() ||
                    nextLine.startsWith("#") ||
                    nextLine.matches(Regex("""^[-*•+]\s+.*""")) ||
                    nextLine.matches(Regex("""^(\d{1,3}[.)]|[A-Za-z][.)])\s+.*""")) ||
                    nextLine.matches(Regex("""^[-*_]{3,}$""")) ||
                    Regex("""^(Not|Önemli|Püf Noktası|Uyarı|İpucu|Formül|Özet|Dikkat):.*""", RegexOption.IGNORE_CASE).matches(nextLine)
                ) {
                    break
                }
                paragraphBuilder.append(" ").append(nextLine)
                nextIndex++
            }

            val fullPara = paragraphBuilder.toString().trim()
            if (fullPara.isNotBlank()) {
                if (!isFirstHeadingFound && fullPara.length < 60 && !fullPara.endsWith(".")) {
                    docTitle = fullPara
                    isFirstHeadingFound = true
                } else {
                    elements.add(DocElement.Paragraph(fullPara))
                }
            }
            i = nextIndex
        }

        if (docTitle.isBlank()) {
            docTitle = fallbackTitle.substringBeforeLast(".").replace("_", " ").trim()
        }

        return Pair(docTitle, elements)
    }

    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
                .setAlignment(alignment)
                .setLineSpacing(2f, 1.18f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width.coerceAtLeast(10), alignment, 1.18f, 2f, false)
        }
    }

    /**
     * Generates a 100% genuine, professional, multi-page PDF document
     * and saves it to the app's cache directory ready for preview, download, or sharing.
     */
    fun generatePdf(context: Context, fileName: String, rawContent: String): File {
        val cleanName = if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val targetFile = File(exportDir, cleanName)

        val (docTitle, elements) = parseDocument(rawContent, cleanName)

        val pdfDocument = PdfDocument()

        // Setup Paints
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 17f
            color = Color.rgb(15, 23, 42) // Slate 900
        }

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8.5f
            color = Color.rgb(100, 116, 139) // Slate 500
        }

        val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 12.5f
            color = Color.rgb(30, 41, 59) // Slate 800
        }

        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 10.5f
            color = Color.rgb(51, 65, 85) // Slate 700
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 9.5f
            color = Color.rgb(30, 41, 59) // Slate 800
        }

        val bulletSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235) // Royal Blue 600
            style = Paint.Style.FILL
        }

        val numberBadgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9.5f
            color = Color.rgb(37, 99, 235) // Blue 600
        }

        val calloutTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = 9.5f
            color = Color.rgb(30, 58, 138) // Dark Blue
        }

        val calloutBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 9f
            color = Color.rgb(30, 41, 59)
        }

        val calloutBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(241, 245, 249) // Soft Slate 100
            style = Paint.Style.FILL
        }

        val calloutBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235) // Blue 600 accent bar
            style = Paint.Style.FILL
        }

        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(226, 232, 240) // Slate 200
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }

        val accentHeaderBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(37, 99, 235) // Primary Blue Bar
            style = Paint.Style.FILL
        }

        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = 8f
            color = Color.rgb(148, 163, 184) // Slate 400
        }

        // Layout measurements: calculate elements height
        class RenderableItem(val element: DocElement, val layout: StaticLayout?, val extraHeight: Float)

        val renderables = mutableListOf<RenderableItem>()
        for (el in elements) {
            when (el) {
                is DocElement.Title -> {
                    val l = createStaticLayout(el.text, titlePaint, CONTENT_WIDTH)
                    renderables.add(RenderableItem(el, l, 16f))
                }
                is DocElement.Heading1 -> {
                    val l = createStaticLayout(el.text, h1Paint, CONTENT_WIDTH - 12)
                    renderables.add(RenderableItem(el, l, 22f))
                }
                is DocElement.Heading2 -> {
                    val l = createStaticLayout(el.text, h2Paint, CONTENT_WIDTH)
                    renderables.add(RenderableItem(el, l, 16f))
                }
                is DocElement.Paragraph -> {
                    val l = createStaticLayout(el.text, bodyPaint, CONTENT_WIDTH)
                    renderables.add(RenderableItem(el, l, 10f))
                }
                is DocElement.Bullet -> {
                    val l = createStaticLayout(el.text, bodyPaint, CONTENT_WIDTH - 18)
                    renderables.add(RenderableItem(el, l, 7f))
                }
                is DocElement.Numbered -> {
                    val l = createStaticLayout(el.text, bodyPaint, CONTENT_WIDTH - 24)
                    renderables.add(RenderableItem(el, l, 7f))
                }
                is DocElement.Callout -> {
                    val textToMeasure = "${el.label}: ${el.text}"
                    val l = createStaticLayout(textToMeasure, calloutBodyPaint, CONTENT_WIDTH - 24)
                    renderables.add(RenderableItem(el, l, 18f))
                }
                is DocElement.Divider -> {
                    renderables.add(RenderableItem(el, null, 14f))
                }
            }
        }

        // Pagination pass: Partition renderables into distinct pages
        val pages = mutableListOf<MutableList<RenderableItem>>()
        var currentPageItems = mutableListOf<RenderableItem>()

        // Page 1 header height calculation
        val titleLayout = createStaticLayout(docTitle, titlePaint, CONTENT_WIDTH)
        val p1HeaderHeight = 4f /* top bar */ + 14f + titleLayout.height + 6f + 12f /* meta */ + 16f /* divider + spacing */
        var currentY = MARGIN_TOP + p1HeaderHeight
        val maxY = FOOTER_Y - 14

        for (item in renderables) {
            val itemHeight = (item.layout?.height?.toFloat() ?: 0f) + item.extraHeight
            if (currentY + itemHeight > maxY && currentPageItems.isNotEmpty()) {
                pages.add(currentPageItems)
                currentPageItems = mutableListOf()
                // Running header on subsequent pages takes 28pt
                currentY = MARGIN_TOP + 28f
            }
            currentPageItems.add(item)
            currentY += itemHeight
        }
        if (currentPageItems.isNotEmpty() || pages.isEmpty()) {
            pages.add(currentPageItems)
        }

        val totalPages = pages.size
        val dateString = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("tr")).format(Date())

        // Drawing pass
        for (pageIndex in 0 until totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Page Background: Crisp white
            canvas.drawColor(Color.WHITE)

            var drawY = MARGIN_TOP.toFloat()

            if (pageIndex == 0) {
                // Page 1: Elegant Header
                // Blue accent strip at top
                canvas.drawRect(MARGIN_HORIZONTAL.toFloat(), drawY, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), drawY + 3.5f, accentHeaderBarPaint)
                drawY += 14f

                // Document Title
                canvas.save()
                canvas.translate(MARGIN_HORIZONTAL.toFloat(), drawY)
                titleLayout.draw(canvas)
                canvas.restore()
                drawY += titleLayout.height + 6f

                // Meta Info (Date & Tag)
                val metaText = "$dateString  •  Nöro Asistan Çalışma Raporu"
                canvas.drawText(metaText, MARGIN_HORIZONTAL.toFloat(), drawY + 8f, metaPaint)
                drawY += 16f

                // Divider Line
                canvas.drawLine(MARGIN_HORIZONTAL.toFloat(), drawY, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), drawY, dividerPaint)
                drawY += 14f
            } else {
                // Subsequent pages: Running Header
                val runningTitle = if (docTitle.length > 50) docTitle.take(48) + "..." else docTitle
                canvas.drawText(runningTitle, MARGIN_HORIZONTAL.toFloat(), drawY + 8f, metaPaint)
                val pageNumberShort = "${pageIndex + 1} / $totalPages"
                val pNumWidth = metaPaint.measureText(pageNumberShort)
                canvas.drawText(pageNumberShort, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat() - pNumWidth, drawY + 8f, metaPaint)
                drawY += 14f
                canvas.drawLine(MARGIN_HORIZONTAL.toFloat(), drawY, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), drawY, dividerPaint)
                drawY += 14f
            }

            // Draw items for this page
            val pageItems = pages[pageIndex]
            for (item in pageItems) {
                when (val el = item.element) {
                    is DocElement.Heading1 -> {
                        drawY += 6f
                        // Vertical blue pill accent next to heading
                        val barHeight = item.layout?.height?.toFloat() ?: 14f
                        canvas.drawRoundRect(
                            RectF(
                                MARGIN_HORIZONTAL.toFloat(),
                                drawY + 1f,
                                MARGIN_HORIZONTAL.toFloat() + 3.5f,
                                drawY + barHeight - 1f
                            ),
                            2f,
                            2f,
                            accentHeaderBarPaint
                        )
                        canvas.save()
                        canvas.translate(MARGIN_HORIZONTAL.toFloat() + 10f, drawY)
                        item.layout?.draw(canvas)
                        canvas.restore()
                        drawY += (item.layout?.height?.toFloat() ?: 0f) + 12f
                    }
                    is DocElement.Heading2 -> {
                        drawY += 4f
                        canvas.save()
                        canvas.translate(MARGIN_HORIZONTAL.toFloat(), drawY)
                        item.layout?.draw(canvas)
                        canvas.restore()
                        drawY += (item.layout?.height?.toFloat() ?: 0f) + 8f
                    }
                    is DocElement.Paragraph -> {
                        canvas.save()
                        canvas.translate(MARGIN_HORIZONTAL.toFloat(), drawY)
                        item.layout?.draw(canvas)
                        canvas.restore()
                        drawY += (item.layout?.height?.toFloat() ?: 0f) + 10f
                    }
                    is DocElement.Bullet -> {
                        // Round bullet point
                        canvas.drawCircle(MARGIN_HORIZONTAL.toFloat() + 4f, drawY + 6.5f, 2.3f, bulletSymbolPaint)
                        canvas.save()
                        canvas.translate(MARGIN_HORIZONTAL.toFloat() + 16f, drawY)
                        item.layout?.draw(canvas)
                        canvas.restore()
                        drawY += (item.layout?.height?.toFloat() ?: 0f) + 6f
                    }
                    is DocElement.Numbered -> {
                        canvas.drawText(el.number, MARGIN_HORIZONTAL.toFloat(), drawY + 9f, numberBadgePaint)
                        canvas.save()
                        canvas.translate(MARGIN_HORIZONTAL.toFloat() + 20f, drawY)
                        item.layout?.draw(canvas)
                        canvas.restore()
                        drawY += (item.layout?.height?.toFloat() ?: 0f) + 6f
                    }
                    is DocElement.Callout -> {
                        val cardH = (item.layout?.height?.toFloat() ?: 0f) + 14f
                        val cardRect = RectF(
                            MARGIN_HORIZONTAL.toFloat(),
                            drawY,
                            (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(),
                            drawY + cardH
                        )
                        // Soft card background
                        canvas.drawRoundRect(cardRect, 4f, 4f, calloutBgPaint)
                        // Left accent bar
                        canvas.drawRoundRect(
                            RectF(
                                MARGIN_HORIZONTAL.toFloat(),
                                drawY,
                                MARGIN_HORIZONTAL.toFloat() + 3.5f,
                                drawY + cardH
                            ),
                            2f,
                            2f,
                            calloutBarPaint
                        )
                        canvas.save()
                        canvas.translate(MARGIN_HORIZONTAL.toFloat() + 12f, drawY + 7f)
                        item.layout?.draw(canvas)
                        canvas.restore()
                        drawY += cardH + 10f
                    }
                    is DocElement.Divider -> {
                        drawY += 4f
                        canvas.drawLine(MARGIN_HORIZONTAL.toFloat(), drawY, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), drawY, dividerPaint)
                        drawY += 10f
                    }
                    else -> {}
                }
            }

            // Draw Footer on every page
            canvas.drawLine(MARGIN_HORIZONTAL.toFloat(), FOOTER_Y.toFloat(), (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(), FOOTER_Y.toFloat(), dividerPaint)
            val leftFooter = "Nöro Asistan • Yapay Zeka Dokümanı"
            canvas.drawText(leftFooter, MARGIN_HORIZONTAL.toFloat(), FOOTER_Y + 14f, footerPaint)

            val pageLabel = "Sayfa ${pageIndex + 1} / $totalPages"
            val labelWidth = footerPaint.measureText(pageLabel)
            canvas.drawText(pageLabel, (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat() - labelWidth, FOOTER_Y + 14f, footerPaint)

            pdfDocument.finishPage(page)
        }

        // Write to file
        FileOutputStream(targetFile).use { fos ->
            pdfDocument.writeTo(fos)
            fos.flush()
        }
        pdfDocument.close()

        return targetFile
    }

    /**
     * Renders each page of the generated PDF into a crisp Bitmap using native Android PdfRenderer.
     * Used for ultra-smooth in-app WYSIWYG preview without needing third party libraries.
     */
    fun renderPdfPages(pdfFile: File, targetWidth: Int = 1080): List<Bitmap> {
        if (!pdfFile.exists() || pdfFile.length() <= 0) return emptyList()
        val bitmaps = mutableListOf<Bitmap>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            for (i in 0 until count) {
                val page = renderer.openPage(i)
                val scale = targetWidth.toFloat() / page.width.toFloat()
                val targetHeight = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd?.close() } catch (ignored: Exception) {}
        }
        return bitmaps
    }

    /**
     * Opens the generated PDF in the device's native PDF reader (Google Drive, Adobe Acrobat, etc.)
     */
    fun openPdfExternal(context: Context, pdfFile: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "PDF Belgesini Aç").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "PDF görüntüleyici uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }
}
