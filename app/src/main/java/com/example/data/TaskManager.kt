package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.TaskItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class TaskManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("personal_assistant_tasks_prefs", Context.MODE_PRIVATE)
    private val _tasks = MutableStateFlow<List<TaskItem>>(emptyList())
    val tasks: StateFlow<List<TaskItem>> = _tasks.asStateFlow()

    private val firestore get() = FirebaseHelper.getFirestore(context)
    private val collection get() = firestore?.collection("noro_tasks")

    init {
        loadLocalTasks()
        listenToFirestore()
    }

    private fun loadLocalTasks() {
        try {
            val jsonString = prefs.getString("saved_tasks", null) ?: return
            val array = JSONArray(jsonString)
            val list = mutableListOf<TaskItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TaskItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        title = obj.getString("title"),
                        isCompleted = obj.optBoolean("isCompleted", false),
                        category = obj.optString("category", "Genel"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _tasks.value = list
        } catch (e: Exception) {
            Log.e("TaskManager", "Error reading local tasks", e)
        }
    }

    private fun saveLocalTasks(list: List<TaskItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("isCompleted", item.isCompleted)
                    put("category", item.category)
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_tasks", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("TaskManager", "Error saving local tasks", e)
        }
    }

    private fun listenToFirestore() {
        try {
            collection?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("TaskManager", "Firestore tasks listener error", error)
                    return@addSnapshotListener
                }
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
                    val sorted = list.sortedByDescending { it.timestamp }
                    _tasks.value = sorted
                    saveLocalTasks(sorted)
                }
            }
        } catch (e: Throwable) {
            Log.e("TaskManager", "Failed to register Firestore listener", e)
        }
    }

    fun addTask(title: String, category: String = "Genel") {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        val id = java.util.UUID.randomUUID().toString()
        val item = TaskItem(id = id, title = trimmed, isCompleted = false, category = category, timestamp = System.currentTimeMillis())
        
        val current = _tasks.value.toMutableList()
        current.add(0, item)
        _tasks.value = current
        saveLocalTasks(current)

        try {
            collection?.document(id)?.set(
                mapOf(
                    "id" to item.id,
                    "title" to item.title,
                    "isCompleted" to item.isCompleted,
                    "category" to item.category,
                    "timestamp" to item.timestamp
                )
            )
        } catch (e: Throwable) {
            Log.e("TaskManager", "Error writing task to Firestore", e)
        }
    }

    fun toggleTask(id: String) {
        val current = _tasks.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current[index].copy(isCompleted = !current[index].isCompleted)
            current[index] = updated
            _tasks.value = current
            saveLocalTasks(current)
            try {
                collection?.document(id)?.update("isCompleted", updated.isCompleted)
            } catch (e: Throwable) {
                Log.e("TaskManager", "Error updating task in Firestore", e)
            }
        }
    }

    fun deleteTask(id: String) {
        val current = _tasks.value.filter { it.id != id }
        _tasks.value = current
        saveLocalTasks(current)
        try {
            collection?.document(id)?.delete()
        } catch (e: Throwable) {
            Log.e("TaskManager", "Error deleting task from Firestore", e)
        }
    }

    fun clearCompleted() {
        val remaining = _tasks.value.filter { !it.isCompleted }
        val completed = _tasks.value.filter { it.isCompleted }
        _tasks.value = remaining
        saveLocalTasks(remaining)
        try {
            completed.forEach { task ->
                collection?.document(task.id)?.delete()
            }
        } catch (e: Throwable) {
            Log.e("TaskManager", "Error clearing completed tasks from Firestore", e)
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
