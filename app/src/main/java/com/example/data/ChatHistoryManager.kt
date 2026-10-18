package com.example.data

import android.content.Context
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.GroundingSource
import com.example.model.MessageSender
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatHistoryManager(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val sessionsCollection = db.collection("noro_chat_sessions")

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    init {
        listenToSessions()
    }

    private fun listenToSessions() {
        sessionsCollection.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
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
                _sessions.value = list.sortedByDescending { it.timestamp }
            }
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

        val sessionMap = mapOf(
            "id" to sessionId,
            "title" to title,
            "previewText" to preview,
            "timestamp" to timestamp,
            "personaId" to personaId
        )
        sessionsCollection.document(sessionId).set(sessionMap)

        val messagesCollection = sessionsCollection.document(sessionId).collection("messages")
        for (msg in nonStreamingMessages) {
            val msgMap = mutableMapOf<String, Any>(
                "id" to msg.id,
                "sender" to msg.sender.name,
                "text" to msg.text,
                "timestamp" to msg.timestamp
            )
            if (msg.imageUri != null) msgMap["imageUri"] = msg.imageUri
            messagesCollection.document(msg.id).set(msgMap)
        }
    }

    fun loadSessionMessages(sessionId: String, onLoaded: (List<ChatMessage>) -> Unit) {
        sessionsCollection.document(sessionId).collection("messages")
            .get()
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
    }

    fun deleteSession(sessionId: String) {
        sessionsCollection.document(sessionId).collection("messages").get().addOnSuccessListener { snapshot ->
            for (doc in snapshot.documents) {
                doc.reference.delete()
            }
            sessionsCollection.document(sessionId).delete()
        }
    }
}
