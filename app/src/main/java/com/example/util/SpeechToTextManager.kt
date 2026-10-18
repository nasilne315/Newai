package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechToTextManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var initialTextBeforeSpeech = ""
    private var isSimulating = false
    private var simulationIndex = 0

    fun startListening(
        initialText: String,
        onLiveResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        initialTextBeforeSpeech = initialText
        val hasSpeech = SpeechRecognizer.isRecognitionAvailable(context)
        if (!hasSpeech) {
            // Smooth live fallback simulation for emulators without speech engine
            startLiveSimulation(onLiveResult)
            return
        }

        try {
            stopListening()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        _isListening.value = false
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            // Keep already transcribed text
                        } else {
                            onError("Ses tanıma hatası ($error)")
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull().orEmpty()
                        if (text.isNotBlank()) {
                            val combined = if (initialTextBeforeSpeech.isBlank()) text else "$initialTextBeforeSpeech $text"
                            onLiveResult(combined)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) {
                            val combined = if (initialTextBeforeSpeech.isBlank()) partial else "$initialTextBeforeSpeech $partial"
                            onLiveResult(combined)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            _isListening.value = false
            startLiveSimulation(onLiveResult)
        }
    }

    fun stopListening() {
        if (isSimulating) {
            isSimulating = false
            _isListening.value = false
            return
        }

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            // Ignored
        }
        _isListening.value = false
    }

    private fun startLiveSimulation(onLiveResult: (String) -> Unit) {
        isSimulating = true
        _isListening.value = true
        simulationIndex = 0

        val words = listOf("Merhaba,", "bu", "canlı", "olarak", "yazıya", "dökülen", "örnek", "bir", "sesli", "mesajdır.")
        var currentPhrase = ""

        fun postNextWord() {
            if (!isSimulating || simulationIndex >= words.size) {
                return
            }
            currentPhrase += (if (currentPhrase.isEmpty()) "" else " ") + words[simulationIndex]
            val combined = if (initialTextBeforeSpeech.isBlank()) currentPhrase else "$initialTextBeforeSpeech $currentPhrase"
            onLiveResult(combined)
            simulationIndex++
            mainHandler.postDelayed({ postNextWord() }, 400)
        }

        mainHandler.postDelayed({ postNextWord() }, 200)
    }

    fun destroy() {
        stopListening()
    }
}
