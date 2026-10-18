package com.example.model

enum class MessageSender {
    USER,
    AI
}

data class GroundingSource(
    val title: String,
    val url: String
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val imageUri: String? = null,
    val attachedFile: AttachedFile? = null,
    val isImageGeneration: Boolean = false,
    val modelId: String? = null,
    val modelDisplayName: String? = null,
    val thinkingProcess: String? = null,
    val imageResolution: String? = null,
    val groundingSources: List<GroundingSource> = emptyList(),
    val searchQueries: List<String> = emptyList(),
    val generatedFiles: List<GeneratedFile> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
)
