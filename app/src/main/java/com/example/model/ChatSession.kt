package com.example.model

data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val previewText: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val personaId: String = "general"
)
