package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.model.AttachedFile
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object FileUtils {
    fun getAttachedFile(context: Context, uri: Uri): AttachedFile {
        var name = "dosya"
        var size: Long = 0

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "dosya"
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
        } catch (e: Exception) {
            name = uri.lastPathSegment ?: "dosya"
        }

        val mimeType = context.contentResolver.getType(uri) ?: when {
            name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            name.endsWith(".txt", ignoreCase = true) -> "text/plain"
            name.endsWith(".csv", ignoreCase = true) -> "text/csv"
            name.endsWith(".json", ignoreCase = true) -> "application/json"
            name.endsWith(".jpg", ignoreCase = true) || name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            name.endsWith(".png", ignoreCase = true) -> "image/png"
            else -> "application/octet-stream"
        }

        val lowerName = name.lowercase(Locale.ROOT)
        val isImage = mimeType.startsWith("image/") ||
                lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif")
        val isPdf = mimeType == "application/pdf" || lowerName.endsWith(".pdf")
        val isText = mimeType.startsWith("text/") ||
                lowerName.endsWith(".txt") || lowerName.endsWith(".csv") || lowerName.endsWith(".md") ||
                lowerName.endsWith(".json") || lowerName.endsWith(".xml") || lowerName.endsWith(".html") ||
                lowerName.endsWith(".htm") || lowerName.endsWith(".css") || lowerName.endsWith(".js") ||
                lowerName.endsWith(".ts") || lowerName.endsWith(".jsx") || lowerName.endsWith(".tsx") ||
                lowerName.endsWith(".py") || lowerName.endsWith(".kt") || lowerName.endsWith(".java") ||
                lowerName.endsWith(".cpp") || lowerName.endsWith(".c") || lowerName.endsWith(".h") ||
                lowerName.endsWith(".cs") || lowerName.endsWith(".sh") || lowerName.endsWith(".sql") ||
                lowerName.endsWith(".yaml") || lowerName.endsWith(".yml") || lowerName.endsWith(".log")

        var cachedText: String? = null
        var cachedBase64: String? = null
        var localCachedPath: String? = null

        try {
            val cacheFolder = File(context.filesDir, "cached_user_files").apply { mkdirs() }
            val safeFileName = "${System.currentTimeMillis()}_${name.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val localFile = File(cacheFolder, safeFileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                FileOutputStream(localFile).use { it.write(bytes) }
                localCachedPath = localFile.absolutePath

                if (isText) {
                    cachedText = String(bytes, Charsets.UTF_8)
                } else if (isPdf || isImage || bytes.size <= 15 * 1024 * 1024) {
                    cachedBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }

                if (size <= 0) {
                    size = bytes.size.toLong()
                }
            }
        } catch (e: Exception) {
            // Fallback if local copy fails
        }

        return AttachedFile(
            uri = uri.toString(),
            name = name,
            mimeType = mimeType,
            sizeBytes = size,
            isImage = isImage,
            isPdf = isPdf,
            isText = isText,
            cachedContent = cachedText,
            cachedBase64 = cachedBase64,
            localCachedPath = localCachedPath
        )
    }

    fun readFileContent(context: Context, file: AttachedFile): String? {
        if (!file.cachedContent.isNullOrBlank()) {
            return file.cachedContent
        }
        if (!file.localCachedPath.isNullOrBlank()) {
            val f = File(file.localCachedPath)
            if (f.exists()) {
                return try { f.readText(Charsets.UTF_8) } catch (e: Exception) { null }
            }
        }
        return try {
            val uri = Uri.parse(file.uri)
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    }

    fun readFileBase64(context: Context, file: AttachedFile): String? {
        if (!file.cachedBase64.isNullOrBlank()) {
            return file.cachedBase64
        }
        if (!file.localCachedPath.isNullOrBlank()) {
            val f = File(file.localCachedPath)
            if (f.exists()) {
                return try { Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) } catch (e: Exception) { null }
            }
        }
        return try {
            val uri = Uri.parse(file.uri)
            val bytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: return null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
