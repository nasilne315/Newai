package com.example.data

import android.content.Context
import com.example.model.MemoryItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MemoryManager(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("noro_memories")

    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    init {
        listenToFirestore()
    }

    private fun listenToFirestore() {
        collection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
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
                _memories.value = list.sortedByDescending { it.timestamp }
            }
        }
    }

    fun addMemory(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return false
        val id = java.util.UUID.randomUUID().toString()
        val item = MemoryItem(id = id, content = trimmed, timestamp = System.currentTimeMillis())
        collection.document(id).set(
            mapOf(
                "id" to item.id,
                "content" to item.content,
                "timestamp" to item.timestamp
            )
        )
        return true
    }

    fun removeMemory(id: String) {
        collection.document(id).delete()
    }

    fun clearMemories() {
        collection.get().addOnSuccessListener { querySnapshot ->
            for (doc in querySnapshot.documents) {
                doc.reference.delete()
            }
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
