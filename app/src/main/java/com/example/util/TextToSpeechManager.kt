package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentSpeakingId = MutableStateFlow<String?>(null)
    val currentSpeakingId: StateFlow<String?> = _currentSpeakingId.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                val trLocale = Locale("tr", "TR")
                val result = tts?.setLanguage(trLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _currentSpeakingId.value = null
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _currentSpeakingId.value = null
                    }
                })
            }
        }
    }

    fun speak(text: String, messageId: String) {
        if (!isInitialized || tts == null) return

        // If currently speaking this message, toggle off / stop
        if (_isSpeaking.value && _currentSpeakingId.value == messageId) {
            stop()
            return
        }

        stop()
        _currentSpeakingId.value = messageId
        _isSpeaking.value = true

        // Clean markdown syntax for smooth audio speech
        val cleanedText = cleanMarkdownForSpeech(text)
        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, messageId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        _currentSpeakingId.value = null
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun cleanMarkdownForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), "kod bloku")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("#+\\s*"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("[-*]\\s+"), "")
            .trim()
    }
}
