package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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
                applyTurkishMaleVoice()

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

    /**
     * Configures a natural, masculine Turkish voice timbre with clear pronunciation.
     */
    private fun applyTurkishMaleVoice() {
        val engine = tts ?: return
        try {
            val trLocale = Locale("tr", "TR")
            val langResult = engine.setLanguage(trLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.language = Locale.getDefault()
            }

            // Inspect available voices in the device's TTS engine
            val voices: Set<Voice>? = engine.voices
            if (!voices.isNullOrEmpty()) {
                val trVoices = voices.filter { v ->
                    v.locale.language.equals("tr", ignoreCase = true)
                }

                if (trVoices.isNotEmpty()) {
                    // Try to match male voice tags (Google TTS tr-tr-x-dfg/cda, male, erkek, etc.)
                    val maleVoice = trVoices.firstOrNull { voice ->
                        val name = voice.name.lowercase(Locale.ROOT)
                        val features = voice.features?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
                        name.contains("male") || name.contains("erkek") ||
                            name.contains("dfg") || name.contains("cda") || name.contains("efg") ||
                            features.any { it.contains("male") || it.contains("gender=male") }
                    } ?: trVoices.firstOrNull { voice ->
                        // Fallback: any voice without female tag
                        val name = voice.name.lowercase(Locale.ROOT)
                        !name.contains("female") && !name.contains("kadin") && !name.contains("fem")
                    } ?: trVoices.firstOrNull()

                    if (maleVoice != null) {
                        engine.voice = maleVoice
                    }
                }
            }
        } catch (_: Exception) {
            // Graceful fallback to default engine settings
        }

        // 0.90f gives a resonant, natural, warm Turkish male baritone frequency
        engine.setPitch(0.90f)
        // 1.0f ensures articulate, clear pronunciation of Turkish phonemes
        engine.setSpeechRate(1.0f)
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

        // Ensure voice parameters remain consistent
        applyTurkishMaleVoice()

        // Clean markdown and emojis for smooth, articulate Turkish audio
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
            .replace(Regex("```[\\s\\S]*?```"), "kod bölümü")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("#+\\s*"), "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
            .replace(Regex("[-*•]\\s+"), "")
            // Remove emoji / non-speech symbols that confuse TTS pronunciation
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
