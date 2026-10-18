package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.AttachedFile
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
                lowerName.endsWith(".png") || lowerName.endsWith(".webp")
        val isPdf = mimeType == "application/pdf" || lowerName.endsWith(".pdf")
        val isText = mimeType.startsWith("text/") ||
                lowerName.endsWith(".txt") || lowerName.endsWith(".csv") || lowerName.endsWith(".md") ||
                lowerName.endsWith(".json") || lowerName.endsWith(".xml") || lowerName.endsWith(".html") ||
                lowerName.endsWith(".kt") || lowerName.endsWith(".java") || lowerName.endsWith(".py") ||
                lowerName.endsWith(".js") || lowerName.endsWith(".ts") || lowerName.endsWith(".log")

        return AttachedFile(
            uri = uri.toString(),
            name = name,
            mimeType = mimeType,
            sizeBytes = size,
            isImage = isImage,
            isPdf = isPdf,
            isText = isText
        )
    }
}
