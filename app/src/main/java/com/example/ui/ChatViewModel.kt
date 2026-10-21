package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ApiKeyStatus
import com.example.data.ChatHistoryManager
import com.example.data.GeminiRepository
import com.example.data.MemoryManager
import com.example.data.TaskManager
import com.example.model.AssistantPersona
import com.example.model.AttachedFile
import com.example.model.ChatMessage
import com.example.model.ChatSession
import com.example.model.MemoryItem
import com.example.model.MessageSender
import com.example.model.TaskItem
import com.example.util.SpeechToTextManager
import com.example.util.TextToSpeechManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

import com.example.model.RuleItem

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val attachedFile: AttachedFile? = null,
    val isLoading: Boolean = false,
    val isRecordingVoice: Boolean = false,
    val isSpeaking: Boolean = false,
    val speakingMessageId: String? = null,
    val activePersona: AssistantPersona = AssistantPersona.GENERAL,
    val isDrawerOpen: Boolean = false,
    val isMoreMenuOpen: Boolean = false,
    val isApiKeyDialogOpen: Boolean = false,
    val isPersonaDialogOpen: Boolean = false,
    val isMemoryDialogOpen: Boolean = false,
    val isTasksDialogOpen: Boolean = false,
    val isWebSearchEnabled: Boolean = false,
    val currentSessionId: String = java.util.UUID.randomUUID().toString(),
    val errorMessage: String? = null,
    val infoNotice: String? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GeminiRepository(application.applicationContext)
    private val speechToTextManager = SpeechToTextManager(application.applicationContext)
    private val textToSpeechManager = TextToSpeechManager(application.applicationContext)
    private val chatHistoryManager = ChatHistoryManager(application.applicationContext)

    val memoryManager: MemoryManager = repository.memoryManager
    val taskManager: TaskManager = repository.taskManager

    val apiKeyStatus: StateFlow<ApiKeyStatus> = repository.apiKeyManager.status
    val chatSessions: StateFlow<List<ChatSession>> = chatHistoryManager.sessions
    val memories: StateFlow<List<MemoryItem>> = memoryManager.memories
    val rules: StateFlow<List<RuleItem>> = memoryManager.rules
    val tasks: StateFlow<List<TaskItem>> = taskManager.tasks

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _companionMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val companionMessages: StateFlow<List<ChatMessage>> = _companionMessages.asStateFlow()

    private val _companionInputText = MutableStateFlow("")
    val companionInputText: StateFlow<String> = _companionInputText.asStateFlow()

    private val _isCompanionLoading = MutableStateFlow(false)
    val isCompanionLoading: StateFlow<Boolean> = _isCompanionLoading.asStateFlow()

    init {
        repository.warmupConnection()
        _companionMessages.value = chatHistoryManager.loadCompanionMessages()
        viewModelScope.launch {
            speechToTextManager.isListening.collect { listening ->
                _uiState.update { it.copy(isRecordingVoice = listening) }
            }
        }
        viewModelScope.launch {
            textToSpeechManager.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
        viewModelScope.launch {
            textToSpeechManager.currentSpeakingId.collect { id ->
                _uiState.update { it.copy(speakingMessageId = id) }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        val oldText = _uiState.value.inputText
        if (oldText.isEmpty() && newText.isNotEmpty()) {
            repository.warmupConnection()
        }
        _uiState.update { it.copy(inputText = newText) }
    }

    fun attachFile(file: AttachedFile?) {
        _uiState.update { it.copy(attachedFile = file) }
    }

    fun removeAttachedFile() {
        _uiState.update { it.copy(attachedFile = null) }
    }

    fun setDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDrawerOpen = isOpen) }
    }

    fun setMoreMenuOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isMoreMenuOpen = isOpen) }
    }

    fun setApiKeyDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isApiKeyDialogOpen = isOpen, isMoreMenuOpen = false) }
    }

    fun setPersonaDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isPersonaDialogOpen = isOpen, isMoreMenuOpen = false) }
    }

    fun setMemoryDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isMemoryDialogOpen = isOpen, isMoreMenuOpen = false) }
    }

    fun setTasksDialogOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isTasksDialogOpen = isOpen, isMoreMenuOpen = false) }
    }

    fun setPersona(persona: AssistantPersona) {
        _uiState.update { it.copy(activePersona = persona) }
    }

    fun addMemory(content: String) {
        val success = memoryManager.addMemory(content)
        if (success) {
            _uiState.update { it.copy(infoNotice = "Hafızaya kaydedildi: '$content'") }
        }
    }

    fun removeMemory(id: String) {
        memoryManager.removeMemory(id)
    }

    fun clearMemories() {
        memoryManager.clearMemories()
    }

    fun addRule(content: String): Boolean {
        return memoryManager.addRule(content)
    }

    fun removeRule(id: String) {
        memoryManager.removeRule(id)
    }

    fun clearRules() {
        memoryManager.clearRules()
    }

    fun toggleWebSearch() {
        _uiState.update { it.copy(isWebSearchEnabled = !it.isWebSearchEnabled) }
    }

    fun addTask(title: String, category: String) {
        taskManager.addTask(title, category)
    }

    fun toggleTask(id: String) {
        taskManager.toggleTask(id)
    }

    fun deleteTask(id: String) {
        taskManager.deleteTask(id)
    }

    fun clearCompletedTasks() {
        taskManager.clearCompleted()
    }

    fun toggleSpeakMessage(text: String, messageId: String) {
        textToSpeechManager.speak(text, messageId)
    }

    fun stopSpeaking() {
        textToSpeechManager.stop()
    }

    fun saveApiKeys(k1: String, k2: String, k3: String, model: String? = null) {
        repository.apiKeyManager.saveKeys(k1, k2, k3, model)
        _uiState.update { it.copy(infoNotice = "API anahtarları ve model başarıyla kaydedildi.") }
    }

    fun clearNotice() {
        _uiState.update { it.copy(infoNotice = null, errorMessage = null) }
    }

    fun toggleVoiceRecording() {
        if (_uiState.value.isRecordingVoice) {
            speechToTextManager.stopListening()
        } else {
            speechToTextManager.startListening(
                initialText = _uiState.value.inputText,
                onLiveResult = { liveText ->
                    _uiState.update { it.copy(inputText = liveText) }
                },
                onError = { error ->
                    _uiState.update { it.copy(errorMessage = error) }
                }
            )
        }
    }

    fun startNewChat() {
        speechToTextManager.stopListening()
        textToSpeechManager.stop()
        val current = _uiState.value
        if (current.messages.isNotEmpty()) {
            chatHistoryManager.saveCurrentChat(
                sessionId = current.currentSessionId,
                messages = current.messages,
                personaId = current.activePersona.id
            )
        }
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                attachedFile = null,
                currentSessionId = java.util.UUID.randomUUID().toString(),
                isMoreMenuOpen = false
            )
        }
    }

    fun selectSession(sessionId: String) {
        speechToTextManager.stopListening()
        textToSpeechManager.stop()
        val current = _uiState.value
        if (current.messages.isNotEmpty()) {
            chatHistoryManager.saveCurrentChat(
                sessionId = current.currentSessionId,
                messages = current.messages,
                personaId = current.activePersona.id
            )
        }

        chatHistoryManager.loadSessionMessages(sessionId) { loaded ->
            _uiState.update {
                it.copy(
                    messages = loaded,
                    currentSessionId = sessionId,
                    inputText = "",
                    attachedFile = null,
                    isMoreMenuOpen = false
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        chatHistoryManager.deleteSession(sessionId)
        if (_uiState.value.currentSessionId == sessionId) {
            startNewChat()
        }
    }

    fun clearHistory() {
        speechToTextManager.stopListening()
        textToSpeechManager.stop()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                isMoreMenuOpen = false
            )
        }
    }

    fun sendMessage(customPrompt: String? = null) {
        val currentState = _uiState.value
        val text = (customPrompt ?: currentState.inputText).trim()
        val attachedFile = currentState.attachedFile

        if (text.isEmpty() && attachedFile == null) return
        if (currentState.isLoading) return

        if (currentState.isRecordingVoice) {
            speechToTextManager.stopListening()
        }

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = text,
            attachedFile = attachedFile,
            imageUri = if (attachedFile?.isImage == true) attachedFile.uri else null
        )

        val aiMessageId = java.util.UUID.randomUUID().toString()
        val initialAiMessage = ChatMessage(
            id = aiMessageId,
            sender = MessageSender.AI,
            text = "",
            isStreaming = true
        )

        val updatedMessages = currentState.messages + userMessage + initialAiMessage
        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = "",
                attachedFile = null,
                isLoading = true,
                errorMessage = null,
                infoNotice = null
            )
        }

        viewModelScope.launch {
            repository.sendMessageStream(
                userPrompt = text,
                attachedFile = attachedFile,
                conversationHistory = currentState.messages + userMessage,
                persona = currentState.activePersona,
                isWebSearchEnabled = currentState.isWebSearchEnabled
            ).collect { streamUpdate ->
                if (streamUpdate.error != null) {
                    _uiState.update { state ->
                        val finalMessages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                if (msg.text.isNotBlank()) {
                                    msg.copy(isStreaming = false)
                                } else {
                                    msg.copy(text = streamUpdate.error, isStreaming = false)
                                }
                            } else msg
                        }
                        state.copy(
                            messages = finalMessages,
                            isLoading = false,
                            errorMessage = streamUpdate.error
                        )
                    }
                } else {
                    _uiState.update { state ->
                        val accumulated = streamUpdate.accumulatedText
                        var textToDisplay = accumulated
                        var extractedNotice: String? = null

                        val finalMessages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                if (streamUpdate.isDone) {
                                    val memoryRegex = "\\[AUTO_MEMORY:\\s*(.*?)\\]".toRegex()
                                    val matchResult = memoryRegex.find(accumulated)
                                    if (matchResult != null) {
                                        val memoryContent = matchResult.groupValues[1].trim()
                                        if (memoryContent.isNotBlank()) {
                                            memoryManager.addMemory(memoryContent)
                                            extractedNotice = "Hafızaya otomatik kaydedildi: '$memoryContent'"
                                        }
                                        textToDisplay = accumulated.replace(memoryRegex, "").trim()
                                    }
                                }

                                msg.copy(
                                    text = textToDisplay,
                                    groundingSources = streamUpdate.groundingSources.ifEmpty { msg.groundingSources },
                                    searchQueries = streamUpdate.searchQueries.ifEmpty { msg.searchQueries },
                                    isStreaming = !streamUpdate.isDone
                                )
                            } else msg
                        }

                        if (streamUpdate.isDone) {
                            chatHistoryManager.saveCurrentChat(
                                sessionId = state.currentSessionId,
                                messages = finalMessages,
                                personaId = state.activePersona.id
                            )
                        }

                        state.copy(
                            messages = finalMessages,
                            isLoading = false,
                            infoNotice = extractedNotice ?: streamUpdate.failoverNotice ?: state.infoNotice
                        )
                    }
                }
            }
        }
    }

    private val _isCompanionTypingAnticipating = MutableStateFlow(false)
    val isCompanionTypingAnticipating: StateFlow<Boolean> = _isCompanionTypingAnticipating.asStateFlow()

    private var typingJob: kotlinx.coroutines.Job? = null

    fun onCompanionInputChanged(newText: String) {
        if (_companionInputText.value.isEmpty() && newText.isNotEmpty()) {
            repository.warmupConnection()
        }
        _companionInputText.value = newText

        typingJob?.cancel()
        if (newText.trim().length > 2) {
            _isCompanionTypingAnticipating.value = true
            typingJob = viewModelScope.launch {
                delay(1200) // Keep anticipating state active while user pauses typing
            }
        } else {
            _isCompanionTypingAnticipating.value = false
        }
    }

    fun sendCompanionMessage(customPrompt: String? = null) {
        val text = (customPrompt ?: _companionInputText.value).trim()
        if (text.isEmpty() || _isCompanionLoading.value) return

        typingJob?.cancel()
        _isCompanionTypingAnticipating.value = false

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = text
        )

        val aiMessageId = java.util.UUID.randomUUID().toString()
        val initialAiMessage = ChatMessage(
            id = aiMessageId,
            sender = MessageSender.AI,
            text = "",
            isStreaming = true
        )

        val currentList = _companionMessages.value
        val updated = currentList + userMessage + initialAiMessage
        _companionMessages.value = updated
        _companionInputText.value = ""
        _isCompanionLoading.value = true

        viewModelScope.launch {
            repository.sendMessageStream(
                userPrompt = text,
                attachedFile = null,
                conversationHistory = currentList + userMessage,
                persona = AssistantPersona.GENERAL,
                isWebSearchEnabled = false
            ).collect { streamUpdate ->
                if (streamUpdate.error != null) {
                    val final = _companionMessages.value.map { msg ->
                        if (msg.id == aiMessageId) {
                            if (msg.text.isNotBlank()) msg.copy(isStreaming = false)
                            else msg.copy(text = streamUpdate.error, isStreaming = false)
                        } else msg
                    }
                    _companionMessages.value = final
                    _isCompanionLoading.value = false
                    chatHistoryManager.saveCompanionMessages(final)
                } else {
                    val accumulated = streamUpdate.accumulatedText
                    var textToDisplay = accumulated
                    val final = _companionMessages.value.map { msg ->
                        if (msg.id == aiMessageId) {
                            if (streamUpdate.isDone) {
                                val memoryRegex = "\\[AUTO_MEMORY:\\s*(.*?)\\]".toRegex()
                                val matchResult = memoryRegex.find(accumulated)
                                if (matchResult != null) {
                                    val memoryContent = matchResult.groupValues[1].trim()
                                    if (memoryContent.isNotBlank()) {
                                        memoryManager.addMemory(memoryContent)
                                    }
                                    textToDisplay = accumulated.replace(memoryRegex, "").trim()
                                }
                            }
                            msg.copy(
                                text = textToDisplay,
                                isStreaming = !streamUpdate.isDone
                            )
                        } else msg
                    }
                    _companionMessages.value = final
                    if (streamUpdate.isDone) {
                        _isCompanionLoading.value = false
                        chatHistoryManager.saveCompanionMessages(final)
                    }
                }
            }
        }
    }

    fun clearCompanionHistory() {
        _companionMessages.value = emptyList()
        chatHistoryManager.saveCompanionMessages(emptyList())
    }

    override fun onCleared() {
        super.onCleared()
        speechToTextManager.destroy()
        textToSpeechManager.shutdown()
    }
}
