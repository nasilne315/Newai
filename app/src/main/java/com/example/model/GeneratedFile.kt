package com.example.model

data class GeneratedFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val fileExtension: String, // "html", "txt", "md", "json", "svg"
    val content: String,
    val sizeBytes: Long = content.toByteArray(Charsets.UTF_8).size.toLong()
) {
    val isPdf: Boolean get() = fileExtension.equals("pdf", ignoreCase = true)
    val isHtml: Boolean get() = fileExtension.equals("html", ignoreCase = true) || fileExtension.equals("htm", ignoreCase = true)
    val isTxt: Boolean get() = fileExtension.equals("txt", ignoreCase = true) || fileExtension.equals("text", ignoreCase = true)
    val isSvg: Boolean get() = fileExtension.equals("svg", ignoreCase = true)
    val isJson: Boolean get() = fileExtension.equals("json", ignoreCase = true)
    val isMarkdown: Boolean get() = fileExtension.equals("md", ignoreCase = true) || fileExtension.equals("markdown", ignoreCase = true)

    val mimeType: String
        get() = when {
            isPdf -> "application/pdf"
            isHtml -> "text/html"
            isSvg -> "image/svg+xml"
            isJson -> "application/json"
            isMarkdown -> "text/markdown"
            else -> "text/plain"
        }

    val typeDisplayName: String
        get() = when {
            isPdf -> "PDF Dokümanı"
            isHtml -> "HTML Belgesi"
            isTxt -> "Metin Belgesi"
            isSvg -> "SVG Vektör"
            isJson -> "JSON Verisi"
            isMarkdown -> "Markdown Belgesi"
            else -> "${fileExtension.uppercase()} Dosyası"
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 B"
            val kb = sizeBytes / 1024.0
            return if (kb < 1) {
                "$sizeBytes B"
            } else if (kb < 1024) {
                "%.1f KB".format(kb)
            } else {
                "%.1f MB".format(kb / 1024.0)
            }
        }
}
