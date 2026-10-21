package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.GeneratedFile
import java.io.File
import java.io.FileOutputStream

object FileExporter {

    fun saveToDownloads(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain"
    ): Boolean {
        try {
            val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)
            val effectiveMime = if (isPdf) "application/pdf" else mimeType
            val effectiveName = if (isPdf && !fileName.endsWith(".pdf", ignoreCase = true)) "$fileName.pdf" else fileName

            val bytes = if (isPdf) {
                val pdfFile = PdfExporter.generatePdf(context, effectiveName, content)
                pdfFile.readBytes()
            } else {
                content.toByteArray(Charsets.UTF_8)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, effectiveName)
                    put(MediaStore.Downloads.MIME_TYPE, effectiveMime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    Toast.makeText(context, "$effectiveName İndirilenler klasörüne kaydedildi.", Toast.LENGTH_SHORT).show()
                    return true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, effectiveName)
                FileOutputStream(targetFile).use { fos ->
                    fos.write(bytes)
                    fos.flush()
                }
                Toast.makeText(context, "$effectiveName İndirilenler klasörüne kaydedildi.", Toast.LENGTH_SHORT).show()
                return true
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Kaydetme hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    fun shareFile(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain"
    ) {
        try {
            val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || fileName.endsWith(".pdf", ignoreCase = true)
            val effectiveMime = if (isPdf) "application/pdf" else mimeType
            val effectiveName = if (isPdf && !fileName.endsWith(".pdf", ignoreCase = true)) "$fileName.pdf" else fileName

            val tempFile = if (isPdf) {
                PdfExporter.generatePdf(context, effectiveName, content)
            } else {
                val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
                val f = File(exportDir, effectiveName)
                FileOutputStream(f).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
                f
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = effectiveMime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, effectiveName)
                putExtra(Intent.EXTRA_TEXT, if (isPdf) "Nöro Asistan tarafından hazırlanan $effectiveName belgesi ektedir." else content.take(500))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Dosyayı Paylaş: $effectiveName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Paylaşım hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extracts files from message text.
     * Every HTML, code, SVG, script, or document is extracted as a downloadable file,
     * ensuring users always receive files instead of raw code blocks.
     */
    fun extractGeneratedFiles(markdownText: String): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()
        val regex = Regex("""```([a-zA-Z0-9_\-\.]+)(?::([a-zA-Z0-9_\-\.]+))?\s*([\s\S]*?)```""")
        val matches = regex.findAll(markdownText)
        var fileCount = 1

        for (match in matches) {
            val tag = match.groupValues[1].lowercase().trim()
            val explicitName = match.groupValues[2].trim()
            val code = match.groupValues[3].trim()
            if (code.isBlank()) continue

            // 1. Explicit filename given: ```file:index.html, ```html:sayfa.html
            if (explicitName.isNotBlank()) {
                val ext = explicitName.substringAfterLast('.', tag).ifBlank { tag }
                files.add(
                    GeneratedFile(
                        fileName = explicitName,
                        fileExtension = ext,
                        content = code
                    )
                )
                continue
            }

            // 2. Explicit file/download tag
            if (tag == "file" || tag == "download") {
                val ext = when {
                    code.contains("<html", ignoreCase = true) -> "html"
                    code.contains("<svg", ignoreCase = true) -> "svg"
                    code.startsWith("{") || code.startsWith("[") -> "json"
                    else -> "txt"
                }
                val name = if (ext == "html") "index.html" else "dosya_$fileCount.$ext"
                files.add(GeneratedFile(fileName = name, fileExtension = ext, content = code))
                fileCount++
                continue
            }

            // Skip mathematical expressions / formulas (should be rendered in chat, not as downloadable files)
            if (tag in listOf("latex", "math", "tex", "formula")) {
                continue
            }

            // 3. Any standard code or markup block -> ALWAYS convert to downloadable file
            val (name, ext) = when (tag) {
                "pdf" -> (if (fileCount == 1) "dokuman.pdf" else "dokuman_$fileCount.pdf") to "pdf"
                "html", "htm" -> (if (fileCount == 1) "index.html" else "sayfa_$fileCount.html") to "html"
                "svg" -> (if (fileCount == 1) "cizim.svg" else "cizim_$fileCount.svg") to "svg"
                "py", "python" -> (if (fileCount == 1) "script.py" else "script_$fileCount.py") to "py"
                "js", "javascript" -> (if (fileCount == 1) "script.js" else "script_$fileCount.js") to "js"
                "css" -> (if (fileCount == 1) "style.css" else "style_$fileCount.css") to "css"
                "json" -> (if (fileCount == 1) "data.json" else "data_$fileCount.json") to "json"
                "kt", "kotlin" -> (if (fileCount == 1) "Main.kt" else "Main_$fileCount.kt") to "kt"
                "txt", "text" -> (if (fileCount == 1) "notlar.txt" else "belge_$fileCount.txt") to "txt"
                else -> (if (tag.length in 2..5) "dosya_$fileCount.$tag" to tag else "dosya_$fileCount.txt" to "txt")
            }
            files.add(GeneratedFile(fileName = name, fileExtension = ext, content = code))
            fileCount++
        }
        return files
    }

    /**
     * Detects if a code or file block is currently being streamed (an unclosed ``` fence exists).
     * Returns the name of the file being generated, or null if no file is being generated.
     */
    fun getGeneratingFileName(text: String): String? {
        val openFenceIndex = text.lastIndexOf("```")
        if (openFenceIndex != -1) {
            val afterFence = text.substring(openFenceIndex + 3)
            // If there's no closing fence after this open fence, it is actively generating
            if (!afterFence.contains("```")) {
                val header = afterFence.lines().firstOrNull()?.trim() ?: ""
                if (header.lowercase() in listOf("latex", "math", "tex", "formula")) {
                    return null // Math block, not a file!
                }
                val fileName = when {
                    header.startsWith("file:", ignoreCase = true) -> header.substringAfter("file:").trim()
                    header.startsWith("download:", ignoreCase = true) -> header.substringAfter("download:").trim()
                    header.contains(":") -> header.substringAfter(":").trim()
                    header.equals("pdf", ignoreCase = true) -> "dokuman.pdf"
                    header.equals("html", ignoreCase = true) || header.equals("htm", ignoreCase = true) -> "index.html"
                    header.equals("svg", ignoreCase = true) -> "cizim.svg"
                    header.equals("py", ignoreCase = true) || header.equals("python", ignoreCase = true) -> "script.py"
                    header.equals("kt", ignoreCase = true) || header.equals("kotlin", ignoreCase = true) -> "Main.kt"
                    header.equals("js", ignoreCase = true) || header.equals("javascript", ignoreCase = true) -> "script.js"
                    header.equals("css", ignoreCase = true) -> "style.css"
                    header.equals("json", ignoreCase = true) -> "data.json"
                    header.equals("txt", ignoreCase = true) || header.equals("text", ignoreCase = true) -> "belge.txt"
                    header.isNotBlank() && !header.contains(" ") -> "dosya.$header"
                    else -> "dosya.html"
                }
                return fileName.ifBlank { "dosya.html" }
            }
        }
        return null
    }

    /**
     * Strips file code blocks from the chat message text so raw code is NEVER displayed in the chat bubble.
     * If streaming, it also hides the actively generating unclosed code block completely.
     */
    fun stripFileCodeBlocks(markdownText: String, files: List<GeneratedFile>, isStreaming: Boolean = false): String {
        var result = markdownText
        val regex = Regex("""```([a-zA-Z0-9_\-\.]+)(?::([a-zA-Z0-9_\-\.]+))?\s*([\s\S]*?)```""")

        // 1. Strip completed code blocks
        result = regex.replace(result) { matchResult ->
            val tag = matchResult.groupValues[1].lowercase().trim()
            val explicitName = matchResult.groupValues[2].trim()
            val code = matchResult.groupValues[3].trim()

            // Do NOT strip math formulas!
            if (tag in listOf("latex", "math", "tex", "formula")) {
                return@replace matchResult.value
            }

            val isExtracted = files.any { file ->
                file.content.trim() == code || (explicitName.isNotBlank() && file.fileName.equals(explicitName, ignoreCase = true))
            }

            if (isExtracted || tag in listOf("file", "download", "pdf", "html", "htm", "svg", "css", "js", "javascript", "python", "py", "kotlin", "kt", "json", "txt", "text")) {
                "" // Strip all code blocks completely
            } else {
                matchResult.value
            }
        }

        // 2. If actively streaming, strip the unclosed code block so raw code is hidden while being generated
        if (isStreaming) {
            val openFenceIndex = result.lastIndexOf("```")
            if (openFenceIndex != -1) {
                val afterFence = result.substring(openFenceIndex + 3)
                if (!afterFence.contains("```")) {
                    result = result.substring(0, openFenceIndex).trimEnd()
                }
            }
        }

        return result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    fun prepareDarkHtml(rawHtml: String): String {
        val darkReset = """
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <meta name="color-scheme" content="dark light">
            <style id="app-dark-theme-reset">
                :root {
                    color-scheme: dark;
                }
                html {
                    background-color: #141518;
                    color: #e2e8f0;
                    margin: 0;
                    padding: 0;
                    overflow: auto;
                    -webkit-overflow-scrolling: touch;
                }
                body {
                    background-color: #141518;
                    color: #e2e8f0;
                    margin: 0;
                    padding: 16px;
                    box-sizing: border-box;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    overflow: auto;
                    -webkit-overflow-scrolling: touch;
                }
            </style>
        """.trimIndent()

        val safeReset = if (rawHtml.contains("background-color", ignoreCase = true) || rawHtml.contains("background:", ignoreCase = true)) {
            """
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
            <meta name="color-scheme" content="dark light">
            <style id="app-dark-theme-reset">
                :root { color-scheme: dark; }
                html { background-color: #141518; color: #e2e8f0; overflow: auto; }
                body { box-sizing: border-box; padding-top: 12px; overflow: auto; }
            </style>
            """.trimIndent()
        } else {
            darkReset
        }

        return when {
            rawHtml.contains("<head>", ignoreCase = true) -> {
                rawHtml.replaceFirst(Regex("(?i)<head>"), "<head>$safeReset")
            }
            rawHtml.contains("<html", ignoreCase = true) -> {
                rawHtml.replaceFirst(Regex("(?i)<html[^>]*>"), "$0<head>$safeReset</head>")
            }
            else -> {
                """
                <!DOCTYPE html>
                <html lang="tr">
                <head>$safeReset</head>
                <body>$rawHtml</body>
                </html>
                """.trimIndent()
            }
        }
    }
}
