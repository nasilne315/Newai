package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.MemoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class MemoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("personal_assistant_memory_prefs", Context.MODE_PRIVATE)
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    private val firestore get() = FirebaseHelper.getFirestore(context)
    private val collection get() = firestore?.collection("noro_memories")

    init {
        loadLocalMemories()
        listenToFirestore()
    }

    private fun loadLocalMemories() {
        try {
            val jsonString = prefs.getString("saved_memories", null) ?: return
            val array = JSONArray(jsonString)
            val list = mutableListOf<MemoryItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MemoryItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        content = obj.getString("content"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _memories.value = list
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error reading local memories", e)
        }
    }

    private fun saveLocalMemories(list: List<MemoryItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("content", item.content)
                    put("timestamp", item.timestamp)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_memories", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error saving local memories", e)
        }
    }

    private fun listenToFirestore() {
        try {
            collection?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MemoryManager", "Firestore listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<MemoryItem>()
                    for (doc in snapshot.documents) {
                        val id = doc.getString("id") ?: doc.id
                        val content = doc.getString("content") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        if (content.isNotBlank()) {
                            list.add(MemoryItem(id = id, content = content, timestamp = timestamp))
                        }
                    }
                    val sorted = list.sortedByDescending { it.timestamp }
                    _memories.value = sorted
                    saveLocalMemories(sorted)
                }
            }
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Failed to register Firestore listener", e)
        }
    }

    fun addMemory(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return false
        val id = java.util.UUID.randomUUID().toString()
        val item = MemoryItem(id = id, content = trimmed, timestamp = System.currentTimeMillis())
        
        val current = _memories.value.toMutableList()
        current.add(0, item)
        _memories.value = current
        saveLocalMemories(current)

        try {
            collection?.document(id)?.set(
                mapOf(
                    "id" to item.id,
                    "content" to item.content,
                    "timestamp" to item.timestamp
                )
            )
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error saving memory to Firestore", e)
        }
        return true
    }

    fun removeMemory(id: String) {
        val current = _memories.value.filter { it.id != id }
        _memories.value = current
        saveLocalMemories(current)
        try {
            collection?.document(id)?.delete()
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error deleting memory from Firestore", e)
        }
    }

    fun clearMemories() {
        _memories.value = emptyList()
        saveLocalMemories(emptyList())
        try {
            collection?.get()?.addOnSuccessListener { querySnapshot ->
                for (doc in querySnapshot.documents) {
                    doc.reference.delete()
                }
            }
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error clearing Firestore memories", e)
        }
    }

    fun getFormattedMemoriesForSystemPrompt(): String {
        val current = _memories.value
        if (current.isEmpty()) return ""
        return buildString {
            append("KULLANICI HAKKINDA KAYITLI KİŞİSEL HAFIZA BİLGİLERİ (Beni Hatırla):\n")
            current.forEach { memory ->
                append("• ").append(memory.content).append("\n")
            }
            append("Bu hafıza bilgilerini kullanıcının tercihlerine, ilgi alanlarına ve geçmiş isteklerine uygun kişiselleştirilmiş yanıtlar vermek için her zaman göz önünde bulundur.\n")
        }
    }
}
