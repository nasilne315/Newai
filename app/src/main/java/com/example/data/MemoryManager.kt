package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.MemoryItem
import com.example.model.RuleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class MemoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("personal_assistant_memory_prefs", Context.MODE_PRIVATE)
    
    // Memories (About User)
    private val _memories = MutableStateFlow<List<MemoryItem>>(emptyList())
    val memories: StateFlow<List<MemoryItem>> = _memories.asStateFlow()

    // Rules (Custom Instructions & Guidelines for the AI)
    private val _rules = MutableStateFlow<List<RuleItem>>(emptyList())
    val rules: StateFlow<List<RuleItem>> = _rules.asStateFlow()

    private val firestore get() = FirebaseHelper.getFirestore(context)
    private val memoriesCollection get() = firestore?.collection("noro_memories")
    private val rulesCollection get() = firestore?.collection("noro_rules")

    init {
        loadLocalData()
        listenToFirestore()
    }

    private fun loadLocalData() {
        // Load memories
        try {
            val jsonMemories = prefs.getString("saved_memories", null)
            if (jsonMemories != null) {
                val array = JSONArray(jsonMemories)
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
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error reading local memories", e)
        }

        // Load rules
        try {
            val jsonRules = prefs.getString("saved_rules", null)
            if (jsonRules != null) {
                val array = JSONArray(jsonRules)
                val list = mutableListOf<RuleItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        RuleItem(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            content = obj.getString("content"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                        )
                    )
                }
                _rules.value = list
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error reading local rules", e)
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

    private fun saveLocalRules(list: List<RuleItem>) {
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
            prefs.edit().putString("saved_rules", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error saving local rules", e)
        }
    }

    private fun listenToFirestore() {
        // Listen to memories
        try {
            memoriesCollection?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MemoryManager", "Firestore memories listener error", error)
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
            Log.e("MemoryManager", "Failed to register Firestore memories listener", e)
        }

        // Listen to rules
        try {
            rulesCollection?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MemoryManager", "Firestore rules listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<RuleItem>()
                    for (doc in snapshot.documents) {
                        val id = doc.getString("id") ?: doc.id
                        val content = doc.getString("content") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        if (content.isNotBlank()) {
                            list.add(RuleItem(id = id, content = content, timestamp = timestamp))
                        }
                    }
                    val sorted = list.sortedByDescending { it.timestamp }
                    _rules.value = sorted
                    saveLocalRules(sorted)
                }
            }
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Failed to register Firestore rules listener", e)
        }
    }

    // --- Memories Methods ---
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
            memoriesCollection?.document(id)?.set(
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
            memoriesCollection?.document(id)?.delete()
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error deleting memory from Firestore", e)
        }
    }

    fun clearMemories() {
        _memories.value = emptyList()
        saveLocalMemories(emptyList())
        try {
            memoriesCollection?.get()?.addOnSuccessListener { querySnapshot ->
                for (doc in querySnapshot.documents) {
                    doc.reference.delete()
                }
            }
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error clearing Firestore memories", e)
        }
    }

    // --- Rules Methods ---
    fun addRule(content: String): Boolean {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return false
        val id = java.util.UUID.randomUUID().toString()
        val item = RuleItem(id = id, content = trimmed, timestamp = System.currentTimeMillis())

        val current = _rules.value.toMutableList()
        current.add(0, item)
        _rules.value = current
        saveLocalRules(current)

        try {
            rulesCollection?.document(id)?.set(
                mapOf(
                    "id" to item.id,
                    "content" to item.content,
                    "timestamp" to item.timestamp
                )
            )
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error saving rule to Firestore", e)
        }
        return true
    }

    fun removeRule(id: String) {
        val current = _rules.value.filter { it.id != id }
        _rules.value = current
        saveLocalRules(current)
        try {
            rulesCollection?.document(id)?.delete()
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error deleting rule from Firestore", e)
        }
    }

    fun clearRules() {
        _rules.value = emptyList()
        saveLocalRules(emptyList())
        try {
            rulesCollection?.get()?.addOnSuccessListener { querySnapshot ->
                for (doc in querySnapshot.documents) {
                    doc.reference.delete()
                }
            }
        } catch (e: Throwable) {
            Log.e("MemoryManager", "Error clearing Firestore rules", e)
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
            append("Bu hafıza bilgilerini kullanıcının tercihlerine, eğitim düzeyine, ilgi alanlarına ve geçmiş isteklerine uygun kişiselleştirilmiş yanıtlar vermek için her zaman göz önünde bulundur.\n")
        }
    }

    fun getFormattedRulesForSystemPrompt(): String {
        val current = _rules.value
        if (current.isEmpty()) return ""
        return buildString {
            append("KULLANICININ BELİRLEDİĞİ KESİN KURALLAR (RULES):\n")
            append("Kullanıcıyla sohbet ederken, soru çözerken, kod yazarken veya planlama yaparken AŞAĞIDAKİ KURALLARA KESİNLİKLE VE İSTİSNASIZ BİR ŞEKİLDE UY:\n")
            current.forEach { rule ->
                append("• ").append(rule.content).append("\n")
            }
            append("Bu kurallar senin 1 numaralı önceliğindir ve tüm yanıtlarında dikkate alınmalıdır.\n")
        }
    }
}
