package com.example.data

import android.content.Context
import com.example.model.TaskItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskManager(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("noro_tasks")

    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    init {
        listenToFirestore()
    }

    private fun listenToFirestore() {
        collection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = mutableListOf<TaskItem>()
                for (doc in snapshot.documents) {
                    val id = doc.getString("id") ?: doc.id
                    val title = doc.getString("title") ?: ""
                    val isCompleted = doc.getBoolean("isCompleted") ?: false
                    val category = doc.getString("category") ?: "Genel"
                    val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    if (title.isNotBlank()) {
                        list.add(TaskItem(id = id, title = title, isCompleted = isCompleted, category = category, timestamp = timestamp))
                    }
                }
                _tasks.value = list.sortedByDescending { it.timestamp }
            }
        }
    }

    fun addTask(title: String, category: String = "Genel") {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val id = java.util.UUID.randomUUID().toString()
        val item = TaskItem(id = id, title = trimmed, isCompleted = false, category = category, timestamp = System.currentTimeMillis())
        collection.document(id).set(
            mapOf(
                "id" to item.id,
                "title" to item.title,
                "isCompleted" to item.isCompleted,
                "category" to item.category,
                "timestamp" to item.timestamp
            )
        )
    }

    fun toggleTask(id: String) {
        val currentTask = _tasks.value.find { it.id == id } ?: return
        val newStatus = !currentTask.isCompleted
        collection.document(id).update("isCompleted", newStatus)
    }

    fun deleteTask(id: String) {
        collection.document(id).delete()
    }

    fun clearCompleted() {
        val completed = _tasks.value.filter { it.isCompleted }
        completed.forEach { task ->
            collection.document(task.id).delete()
        }
    }

    fun getFormattedTasksForSystemPrompt(): String {
        val current = _tasks.value
        if (current.isEmpty()) return ""
        val pending = current.filter { !it.isCompleted }
        val completed = current.filter { it.isCompleted }
        return buildString {
            append("KULLANICININ AKTİF GÖREVLERİ & YAPILACAKLAR LİSTESİ:\n")
            if (pending.isNotEmpty()) {
                append("Bekleyen Görevler:\n")
                pending.forEach { task ->
                    append("- [ ] [${task.category}] ${task.title}\n")
                }
            }
            if (completed.isNotEmpty()) {
                append("Tamamlanan Görevler:\n")
                completed.take(5).forEach { task ->
                    append("- [x] [${task.category}] ${task.title}\n")
                }
            }
            append("Kullanıcı görev planlaması, günlük ajanda veya öncelik istediğinde bu listeyi dikkate alarak rehberlik et.\n")
        }
    }
}
