package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MessageSender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ChatHistoryManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("noro_chat_sessions_prefs", Context.MODE_PRIVATE)
    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val firestore get() = FirebaseHelper.getFirestore(context)
    private val sessionsCollection get() = firestore?.collection("noro_chat_sessions")

    init {
        loadLocalSessions()
        listenToSessions()
    }

    private fun loadLocalSessions() {
        try {
            val jsonString = prefs.getString("saved_sessions", null) ?: return
            val array = JSONArray(jsonString)
            val list = mutableListOf<ChatSession>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ChatSession(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        previewText = obj.optString("previewText", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        personaId = obj.optString("personaId", "general")
                    )
                )
            }
            _sessions.value = list
        } catch (e: Exception) {
            Log.e("ChatHistoryManager", "Error loading local sessions", e)
        }
    }

    private fun saveLocalSessions(list: List<ChatSession>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("title", item.title)
                    put("previewText", item.previewText)
                    put("timestamp", item.timestamp)
                    put("personaId", item.personaId)
                }
                array.put(obj)
            }
            prefs.edit().putString("saved_sessions", array.toString()).apply()
        } catch (e: Exception) {
            Log.e("ChatHistoryManager", "Error saving local sessions", e)
        }
    }

    private fun listenToSessions() {
        try {
            sessionsCollection?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatHistoryManager", "Firestore sessions listener error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = mutableListOf<ChatSession>()
                    for (doc in snapshot.documents) {
                        val id = doc.getString("id") ?: doc.id
                        val title = doc.getString("title") ?: "Sohbet"
                        val previewText = doc.getString("previewText") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val personaId = doc.getString("personaId") ?: "general"
                        list.add(ChatSession(id = id, title = title, previewText = previewText, timestamp = timestamp, personaId = personaId))
                    }
                    val sorted = list.sortedByDescending { it.timestamp }
                    _sessions.value = sorted
                    saveLocalSessions(sorted)
                }
            }
        } catch (e: Throwable) {
            Log.e("ChatHistoryManager", "Failed to register Firestore sessions listener", e)
        }
    }

    fun saveCurrentChat(sessionId: String, messages: List<ChatMessage>, personaId: String) {
        val nonStreamingMessages = messages.filter { !it.isStreaming && it.text.isNotBlank() }
        if (nonStreamingMessages.isEmpty()) return

        val firstUserMsg = nonStreamingMessages.firstOrNull { it.sender == MessageSender.USER }?.text ?: "Sohbet"
        val title = if (firstUserMsg.length > 35) firstUserMsg.take(35) + "..." else firstUserMsg
        val lastMsg = nonStreamingMessages.lastOrNull()?.text ?: ""
        val preview = if (lastMsg.length > 50) lastMsg.take(50) + "..." else lastMsg
        val timestamp = System.currentTimeMillis()

        val session = ChatSession(
            id = sessionId,
            title = title,
            previewText = preview,
            timestamp = timestamp,
            personaId = personaId
        )

        val current = _sessions.value.filter { it.id != sessionId }.toMutableList()
        current.add(0, session)
        _sessions.value = current
        saveLocalSessions(current)

        try {
            val sessionMap = mapOf(
                "id" to sessionId,
                "title" to title,
                "previewText" to preview,
                "timestamp" to timestamp,
                "personaId" to personaId
            )
            sessionsCollection?.document(sessionId)?.set(sessionMap)

            val messagesCollection = sessionsCollection?.document(sessionId)?.collection("messages")
            for (msg in nonStreamingMessages) {
                val msgMap = mutableMapOf<String, Any>(
                    "id" to msg.id,
                    "sender" to msg.sender.name,
                    "text" to msg.text,
                    "timestamp" to msg.timestamp
                )
                if (msg.imageUri != null) msgMap["imageUri"] = msg.imageUri
                messagesCollection?.document(msg.id)?.set(msgMap)
            }
        } catch (e: Throwable) {
            Log.e("ChatHistoryManager", "Error saving session to Firestore", e)
        }
    }

    fun loadSessionMessages(sessionId: String, onLoaded: (List<ChatMessage>) -> Unit) {
        try {
            val coll = sessionsCollection?.document(sessionId)?.collection("messages")
            if (coll == null) {
                onLoaded(emptyList())
                return
            }
            coll.get()
                .addOnSuccessListener { snapshot ->
                    val list = mutableListOf<ChatMessage>()
                    for (doc in snapshot.documents) {
                        val id = doc.getString("id") ?: doc.id
                        val senderStr = doc.getString("sender") ?: "USER"
                        val sender = if (senderStr == "AI") MessageSender.AI else MessageSender.USER
                        val text = doc.getString("text") ?: ""
                        val imageUri = doc.getString("imageUri")
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        list.add(
                            ChatMessage(
                                id = id,
                                sender = sender,
                                text = text,
                                imageUri = imageUri,
                                timestamp = timestamp,
                                isStreaming = false
                            )
                        )
                    }
                    onLoaded(list.sortedBy { it.timestamp })
                }
                .addOnFailureListener {
                    onLoaded(emptyList())
                }
        } catch (e: Throwable) {
            Log.e("ChatHistoryManager", "Error loading session messages", e)
            onLoaded(emptyList())
        }
    }

    fun deleteSession(sessionId: String) {
        val current = _sessions.value.filter { it.id != sessionId }
        _sessions.value = current
        saveLocalSessions(current)

        try {
            sessionsCollection?.document(sessionId)?.collection("messages")?.get()?.addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    doc.reference.delete()
                }
                sessionsCollection?.document(sessionId)?.delete()
            }
        } catch (e: Throwable) {
            Log.e("ChatHistoryManager", "Error deleting session from Firestore", e)
        }
    }
}
