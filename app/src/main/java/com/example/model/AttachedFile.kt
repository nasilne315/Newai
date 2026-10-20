package com.example.model

data class AttachedFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val isImage: Boolean = false,
    val isPdf: Boolean = false,
    val isText: Boolean = false,
    val cachedContent: String? = null,
    val cachedBase64: String? = null,
    val localCachedPath: String? = null
) {
    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return ""
            val kb = sizeBytes / 1024.0
            return if (kb < 1024) {
                "%.1f KB".format(kb)
            } else {
                "%.1f MB".format(kb / 1024.0)
            }
        }
}
