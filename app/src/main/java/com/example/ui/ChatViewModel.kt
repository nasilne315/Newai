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
import kotlinx.coroutines.Job

enum class LyniaLivePreReadStatus {
    IDLE,
    READING,
    PREPARED
}

data class PredictiveChatCache(
    val prompt: String,
    @Volatile var accumulatedText: String = "",
    @Volatile var groundingSources: List<com.example.model.GroundingSource> = emptyList(),
    @Volatile var searchQueries: List<String> = emptyList(),
    @Volatile var failoverNotice: String? = null,
    @Volatile var isDone: Boolean = false,
    @Volatile var error: String? = null,
    var job: Job? = null
)

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
    val infoNotice: String? = null,
    val preReadStatus: LyniaLivePreReadStatus = LyniaLivePreReadStatus.IDLE,
    val isLyniaLiveBarVisible: Boolean = true,
    val activeCustomGesture: com.example.ui.avatar.HandGesture? = null
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

    private var predictiveDebounceJob: Job? = null
    private var predictiveChatCache: PredictiveChatCache? = null
    private var activePromotedAiId: String? = null
    private var gestureResetJob: Job? = null

    fun onInputTextChanged(newText: String) {
        val oldText = _uiState.value.inputText
        if (oldText.isEmpty() && newText.isNotEmpty()) {
            repository.warmupConnection()
        }

        _uiState.update { it.copy(inputText = newText) }

        // Cancel debounce job on every keystroke
        predictiveDebounceJob?.cancel()

        val trimmed = newText.trim()

        // If input is cleared or too short (< 3 chars), cancel any ongoing speculative stream
        if (trimmed.length < 3) {
            predictiveChatCache?.job?.cancel()
            predictiveChatCache = null
            _uiState.update { it.copy(preReadStatus = LyniaLivePreReadStatus.IDLE) }
            return
        }

        // If chat is currently loading an active response or has an attached file, do not pre-fetch
        if (_uiState.value.isLoading || _uiState.value.attachedFile != null) {
            return
        }

        // Adaptive debounce: faster if user types punctuation like '?', '.', '!'
        val isEndingPunctuation = trimmed.endsWith("?") || trimmed.endsWith(".") || trimmed.endsWith("!")
        val debounceDelay = if (isEndingPunctuation) 260L else 480L

        predictiveDebounceJob = viewModelScope.launch {
            delay(debounceDelay)

            // Verify that the user hasn't changed the input during the delay
            if (_uiState.value.inputText.trim() != trimmed) return@launch

            // If we already have a finished pre-computation for the exact same prompt, we're already prepared
            val currentCache = predictiveChatCache
            if (currentCache != null && currentCache.prompt.equals(trimmed, ignoreCase = true) && currentCache.isDone) {
                _uiState.update { it.copy(preReadStatus = LyniaLivePreReadStatus.PREPARED) }
                return@launch
            }

            // Cancel any previous predictive stream
            currentCache?.job?.cancel()

            _uiState.update { it.copy(preReadStatus = LyniaLivePreReadStatus.READING) }

            val newCache = PredictiveChatCache(prompt = trimmed)
            predictiveChatCache = newCache

            newCache.job = viewModelScope.launch {
                val currentState = _uiState.value
                val speculativeUserMessage = ChatMessage(
                    sender = MessageSender.USER,
                    text = trimmed
                )

                try {
                    repository.sendMessageStream(
                        userPrompt = trimmed,
                        attachedFile = null,
                        conversationHistory = currentState.messages + speculativeUserMessage,
                        persona = currentState.activePersona,
                        isWebSearchEnabled = currentState.isWebSearchEnabled
                    ).collect { update ->
                        newCache.accumulatedText = update.accumulatedText
                        newCache.groundingSources = update.groundingSources
                        newCache.searchQueries = update.searchQueries
                        newCache.failoverNotice = update.failoverNotice
                        newCache.isDone = update.isDone
                        newCache.error = update.error

                        if (update.isDone) {
                            _uiState.update { it.copy(preReadStatus = LyniaLivePreReadStatus.PREPARED) }
                        }

                        // If user pressed Send while this stream was running in background,
                        // activePromotedAiId is set: stream straight to the UI message!
                        val promotedId = activePromotedAiId
                        if (promotedId != null) {
                            applyStreamUpdateToUi(promotedId, update)
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Pre-generation cancelled gracefully when user continues typing
                }
            }
        }
    }

    fun toggleLyniaLiveBar() {
        _uiState.update { it.copy(isLyniaLiveBarVisible = !it.isLyniaLiveBarVisible) }
    }

    fun triggerLyniaLiveGesture(gesture: com.example.ui.avatar.HandGesture) {
        gestureResetJob?.cancel()
        _uiState.update { it.copy(activeCustomGesture = gesture) }
        gestureResetJob = viewModelScope.launch {
            delay(3200)
            _uiState.update { it.copy(activeCustomGesture = null) }
        }
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

    fun toggleCompanionVoiceRecording() {
        if (_uiState.value.isRecordingVoice) {
            speechToTextManager.stopListening()
        } else {
            stopSpeaking()
            speechToTextManager.startListening(
                initialText = _companionInputText.value,
                onLiveResult = { liveText ->
                    _companionInputText.value = liveText
                },
                onError = { error ->
                    _uiState.update { it.copy(errorMessage = error) }
                }
            )
        }
    }

    fun startNewChat() {
        predictiveDebounceJob?.cancel()
        predictiveChatCache?.job?.cancel()
        predictiveChatCache = null
        activePromotedAiId = null
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
        predictiveDebounceJob?.cancel()
        predictiveChatCache?.job?.cancel()
        predictiveChatCache = null
        activePromotedAiId = null
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
        predictiveDebounceJob?.cancel()
        predictiveChatCache?.job?.cancel()
        predictiveChatCache = null
        activePromotedAiId = null
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

        predictiveDebounceJob?.cancel()
        gestureResetJob?.cancel()

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = text,
            attachedFile = attachedFile,
            imageUri = if (attachedFile?.isImage == true) attachedFile.uri else null
        )

        val aiMessageId = java.util.UUID.randomUUID().toString()

        // Check if predictive background answer was already formulated while the user was typing!
        val cached = predictiveChatCache
        val isCacheMatch = (attachedFile == null) &&
                (cached != null) &&
                cached.prompt.equals(text, ignoreCase = true) &&
                (cached.error == null)

        if (isCacheMatch) {
            checkNotNull(cached)
            if (cached.isDone && cached.accumulatedText.isNotBlank()) {
                // CASE 1: 100% PRE-COMPUTED IN ADVANCE!
                // Zero millisecond wait time - instant answer delivered the exact moment Send is clicked!
                var textToDisplay = cached.accumulatedText
                var extractedNotice: String? = cached.failoverNotice

                val memoryRegex = "\\[AUTO_MEMORY:\\s*(.*?)\\]".toRegex()
                val matchResult = memoryRegex.find(cached.accumulatedText)
                if (matchResult != null) {
                    val memoryContent = matchResult.groupValues[1].trim()
                    if (memoryContent.isNotBlank()) {
                        memoryManager.addMemory(memoryContent)
                        extractedNotice = "Hafızaya otomatik kaydedildi: '$memoryContent'"
                    }
                    textToDisplay = cached.accumulatedText.replace(memoryRegex, "").trim()
                }

                val finalAiMessage = ChatMessage(
                    id = aiMessageId,
                    sender = MessageSender.AI,
                    text = textToDisplay,
                    groundingSources = cached.groundingSources,
                    searchQueries = cached.searchQueries,
                    isStreaming = false
                )

                val updatedMessages = currentState.messages + userMessage + finalAiMessage
                _uiState.update {
                    it.copy(
                        messages = updatedMessages,
                        inputText = "",
                        attachedFile = null,
                        isLoading = false,
                        errorMessage = null,
                        infoNotice = extractedNotice ?: it.infoNotice,
                        preReadStatus = LyniaLivePreReadStatus.IDLE,
                        activeCustomGesture = null
                    )
                }

                chatHistoryManager.saveCurrentChat(
                    sessionId = currentState.currentSessionId,
                    messages = updatedMessages,
                    personaId = currentState.activePersona.id
                )

                predictiveChatCache = null
                activePromotedAiId = null
                return
            } else if (cached.job?.isActive == true) {
                // CASE 2: Active stream is already in-flight from typing pre-read!
                // Seamlessly promote the running stream directly into the visible chat message!
                activePromotedAiId = aiMessageId
                val initialAiMessage = ChatMessage(
                    id = aiMessageId,
                    sender = MessageSender.AI,
                    text = cached.accumulatedText,
                    groundingSources = cached.groundingSources,
                    searchQueries = cached.searchQueries,
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
                        infoNotice = null,
                        preReadStatus = LyniaLivePreReadStatus.IDLE,
                        activeCustomGesture = null
                    )
                }
                return
            }
        }

        // Standard generation (cache miss, modified prompt, or file attached)
        cached?.job?.cancel()
        predictiveChatCache = null
        activePromotedAiId = null

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
                infoNotice = null,
                preReadStatus = LyniaLivePreReadStatus.IDLE,
                activeCustomGesture = null
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
                applyStreamUpdateToUi(aiMessageId, streamUpdate)
            }
        }
    }

    private fun applyStreamUpdateToUi(aiMessageId: String, streamUpdate: com.example.data.StreamUpdate) {
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
            if (activePromotedAiId == aiMessageId) {
                activePromotedAiId = null
                predictiveChatCache = null
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
                    if (activePromotedAiId == aiMessageId) {
                        activePromotedAiId = null
                        predictiveChatCache = null
                    }
                }

                state.copy(
                    messages = finalMessages,
                    isLoading = !streamUpdate.isDone,
                    infoNotice = extractedNotice ?: streamUpdate.failoverNotice ?: state.infoNotice
                )
            }
        }
    }

    private val _isPreThinking = MutableStateFlow(false)
    val isPreThinking: StateFlow<Boolean> = _isPreThinking.asStateFlow()

    private var preThinkingJob: kotlinx.coroutines.Job? = null
    private var preThoughtPrompt: String = ""
    private var preThoughtAccumulated: String = ""
    private var isPreThoughtDone: Boolean = false
    private var companionStreamJob: kotlinx.coroutines.Job? = null

    fun onCompanionInputChanged(newText: String) {
        if (_companionInputText.value.isEmpty() && newText.isNotEmpty()) {
            repository.warmupConnection()
            // Immediately stop any TTS speech when user begins typing
            stopSpeaking()
        }
        _companionInputText.value = newText

        // Debounced Speculative Pre-Thinking:
        // As the user types and pauses for a moment, Lynia reads the text in real-time
        // and begins formulating/pre-fetching the response in the background for instant replies!
        preThinkingJob?.cancel()
        _isPreThinking.value = false

        val trimmed = newText.trim()
        if (trimmed.length >= 5) {
            preThinkingJob = viewModelScope.launch {
                delay(650) // Typing pause threshold
                _isPreThinking.value = true
                preThoughtPrompt = trimmed
                preThoughtAccumulated = ""
                isPreThoughtDone = false

                try {
                    repository.sendMessageStream(
                        userPrompt = trimmed,
                        attachedFile = null,
                        conversationHistory = _companionMessages.value,
                        persona = AssistantPersona.GENERAL,
                        isWebSearchEnabled = false
                    ).collect { update ->
                        if (update.error == null && update.accumulatedText.isNotBlank()) {
                            preThoughtAccumulated = update.accumulatedText
                            isPreThoughtDone = update.isDone
                        }
                    }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // Normal typing cancellation
                } catch (_: Exception) {
                    // Silent failover
                } finally {
                    _isPreThinking.value = false
                }
            }
        }
    }

    fun sendCompanionMessage(customPrompt: String? = null) {
        val text = (customPrompt ?: _companionInputText.value).trim()
        if (text.isEmpty()) return

        // Stop TTS speech
        stopSpeaking()

        // Check if Lynia already pre-thought and formulated this answer while user was typing!
        val canUsePreThought = preThoughtPrompt.isNotBlank() &&
            (text == preThoughtPrompt || (text.startsWith(preThoughtPrompt) && text.length - preThoughtPrompt.length <= 4)) &&
            preThoughtAccumulated.isNotBlank()

        val capturedPreThought = if (canUsePreThought) preThoughtAccumulated else null
        val capturedIsDone = if (canUsePreThought) isPreThoughtDone else false

        preThinkingJob?.cancel()
        _isPreThinking.value = false

        // Interrupt any ongoing streaming response cleanly
        companionStreamJob?.cancel()

        val cleanCurrentList = _companionMessages.value.map { msg ->
            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
        }

        val userMessage = ChatMessage(
            sender = MessageSender.USER,
            text = text
        )

        val aiMessageId = java.util.UUID.randomUUID().toString()
        val initialAiMessage = ChatMessage(
            id = aiMessageId,
            sender = MessageSender.AI,
            text = capturedPreThought ?: "",
            isStreaming = !(capturedPreThought != null && capturedIsDone)
        )

        val updated = cleanCurrentList + userMessage + initialAiMessage
        _companionMessages.value = updated
        _companionInputText.value = ""

        if (capturedPreThought != null && capturedIsDone) {
            // Instant Response! Already pre-read and computed while user was typing!
            _isCompanionLoading.value = false
            chatHistoryManager.saveCompanionMessages(updated)
            return
        }

        _isCompanionLoading.value = true

        companionStreamJob = viewModelScope.launch {
            repository.sendMessageStream(
                userPrompt = text,
                attachedFile = null,
                conversationHistory = cleanCurrentList + userMessage,
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
