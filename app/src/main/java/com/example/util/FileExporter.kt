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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val uri: Uri? = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(content.toByteArray(Charsets.UTF_8))
                        outputStream.flush()
                    }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    Toast.makeText(context, "$fileName İndirilenler klasörüne kaydedildi.", Toast.LENGTH_SHORT).show()
                    return true
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val targetFile = File(downloadsDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    fos.write(content.toByteArray(Charsets.UTF_8))
                    fos.flush()
                }
                Toast.makeText(context, "$fileName İndirilenler klasörüne kaydedildi.", Toast.LENGTH_SHORT).show()
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
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val tempFile = File(exportDir, fileName)
            FileOutputStream(tempFile).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, content.take(500))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Dosyayı Paylaş: $fileName").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Paylaşım hatası: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Extracts files from message text.
     * When the user doesn't ask for a file, regular code blocks (```python, ```html, ```js) remain as CODE.
     * When a downloadable file is explicitly intended:
     * - ```file:filename.ext
     * - ```lang:filename.ext (e.g. ```html:index.html, ```txt:notlar.txt)
     * - ```download:filename.ext
     * - ```file
     * - Or when markdown explicitly indicates a downloadable file
     */
    fun extractGeneratedFiles(markdownText: String): List<GeneratedFile> {
        val files = mutableListOf<GeneratedFile>()
        val regex = Regex("""```([a-zA-Z0-9_\-\.]+)(?::([a-zA-Z0-9_\-\.]+))?\s*([\s\S]*?)```""")
        val matches = regex.findAll(markdownText)
        var fileCount = 1

        val hasFileIntent = markdownText.contains("indirilebilir", ignoreCase = true) ||
                markdownText.contains("dosya şeklinde", ignoreCase = true) ||
                markdownText.contains("dosya olarak", ignoreCase = true) ||
                markdownText.contains("dosyası:", ignoreCase = true) ||
                markdownText.contains("dosyası oluşturdum", ignoreCase = true)

        for (match in matches) {
            val tag = match.groupValues[1].lowercase().trim()
            val explicitName = match.groupValues[2].trim()
            val code = match.groupValues[3].trim()
            if (code.isBlank()) continue

            // 1. Explicit filename given in fence: ```file:index.html, ```html:sayfa.html, ```txt:notlar.txt
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

            // 2. Explicit file block: ```file or ```download
            if (tag == "file" || tag == "download") {
                val ext = if (code.contains("<html", ignoreCase = true)) "html" else "txt"
                val name = if (ext == "html") "index.html" else "dosya_$fileCount.txt"
                files.add(GeneratedFile(fileName = name, fileExtension = ext, content = code))
                fileCount++
                continue
            }

            // 3. If surrounding text explicitly requested/indicated a downloadable file
            if (hasFileIntent) {
                when (tag) {
                    "html", "htm" -> {
                        val name = if (fileCount == 1) "index.html" else "sayfa_$fileCount.html"
                        files.add(GeneratedFile(fileName = name, fileExtension = "html", content = code))
                        fileCount++
                    }
                    "svg" -> {
                        val name = if (fileCount == 1) "cizim.svg" else "cizim_$fileCount.svg"
                        files.add(GeneratedFile(fileName = name, fileExtension = "svg", content = code))
                        fileCount++
                    }
                    "txt", "text" -> {
                        val name = if (fileCount == 1) "notlar.txt" else "belge_$fileCount.txt"
                        files.add(GeneratedFile(fileName = name, fileExtension = "txt", content = code))
                        fileCount++
                    }
                }
            }
            // Otherwise, regular code snippets (HTML, Python, Kotlin, JS, etc.) remain as pure code in chat!
        }
        return files
    }

    /**
     * Strips file code blocks from the chat message text so that when a file is provided,
     * the chat doesn't display both the huge code block and the file card simultaneously.
     */
    fun stripFileCodeBlocks(markdownText: String, files: List<GeneratedFile>): String {
        if (files.isEmpty()) return markdownText

        var result = markdownText
        val regex = Regex("""```([a-zA-Z0-9_\-\.]+)(?::([a-zA-Z0-9_\-\.]+))?\s*([\s\S]*?)```""")

        result = regex.replace(result) { matchResult ->
            val tag = matchResult.groupValues[1].lowercase().trim()
            val explicitName = matchResult.groupValues[2].trim()
            val code = matchResult.groupValues[3].trim()

            val isExtracted = files.any { file ->
                file.content.trim() == code || (explicitName.isNotBlank() && file.fileName.equals(explicitName, ignoreCase = true))
            }

            if (isExtracted) {
                "" // Strip this code block from the message body
            } else {
                matchResult.value // Keep regular code blocks in chat
            }
        }

        return result.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }

    /**
     * Injects dark mode meta tags, viewport, and styling into HTML if it doesn't already have dark styles,
     * ensuring the live preview is consistent with the app's dark theme and scrolls properly.
     */
    fun prepareDarkHtml(rawHtml: String): String {
        val darkReset = """
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
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
                }
                body {
                    background-color: #141518;
                    color: #e2e8f0;
                    margin: 0;
                    padding: 16px;
                    box-sizing: border-box;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    overflow-y: auto;
                    -webkit-overflow-scrolling: touch;
                }
            </style>
        """.trimIndent()

        // If the HTML already defines its own explicit background color, don't force background on body
        val safeReset = if (rawHtml.contains("background-color", ignoreCase = true) || rawHtml.contains("background:", ignoreCase = true)) {
            """
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
            <meta name="color-scheme" content="dark light">
            <style id="app-dark-theme-reset">
                :root { color-scheme: dark; }
                html { background-color: #141518; color: #e2e8f0; }
                body { box-sizing: border-box; padding-top: 12px; }
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
