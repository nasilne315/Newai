package com.example.model

data class TaskItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
    val category: String = "Genel", // Genel, İş, Proje, Kişisel
    val timestamp: Long = System.currentTimeMillis()
)
