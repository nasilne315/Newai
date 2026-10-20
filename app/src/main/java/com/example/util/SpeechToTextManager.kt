package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechToTextManager(private val context: Context) {
    private val tag = "SpeechToTextManager"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var initialTextBeforeSpeech = ""
    private var isExplicitlyStopped = false

    fun startListening(
        initialText: String,
        onLiveResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        initialTextBeforeSpeech = initialText.trim()
        isExplicitlyStopped = false

        mainHandler.post {
            try {
                // Destroy any previous instance cleanly
                cleanUpRecognizer()

                val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
                if (!isAvailable) {
                    Log.w(tag, "Speech recognition reported as unavailable by package manager, attempting creation")
                }

                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(tag, "onReadyForSpeech")
                            _isListening.value = true
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d(tag, "onBeginningOfSpeech")
                            _isListening.value = true
                        }

                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            Log.d(tag, "onEndOfSpeech")
                        }

                        override fun onError(error: Int) {
                            Log.e(tag, "SpeechRecognizer onError: $error")
                            _isListening.value = false

                            if (isExplicitlyStopped) return

                            when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> {
                                    // User paused or didn't speak; keep transcribed text intact
                                    Log.d(tag, "No match found")
                                }
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                    Log.d(tag, "Speech timeout")
                                }
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                    onError("Mikrofon izni verilmedi.")
                                }
                                SpeechRecognizer.ERROR_AUDIO -> {
                                    onError("Ses kaydı hatası oluştu.")
                                }
                                SpeechRecognizer.ERROR_NETWORK -> {
                                    onError("Ses tanıma için internet bağlantısı gereklidir.")
                                }
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                                    onError("Ses tanıma ağ zaman aşımına uğradı.")
                                }
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                                    cleanUpRecognizer()
                                }
                                else -> {
                                    onError("Ses algılanamadı ($error). Tekrar deneyebilirsiniz.")
                                }
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            Log.d(tag, "onResults")
                            _isListening.value = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val recognizedText = matches?.firstOrNull()?.trim().orEmpty()
                            if (recognizedText.isNotBlank()) {
                                val combined = if (initialTextBeforeSpeech.isBlank()) {
                                    recognizedText
                                } else {
                                    "$initialTextBeforeSpeech $recognizedText"
                                }
                                onLiveResult(combined)
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partialText = matches?.firstOrNull()?.trim().orEmpty()
                            if (partialText.isNotBlank()) {
                                val combined = if (initialTextBeforeSpeech.isBlank()) {
                                    partialText
                                } else {
                                    "$initialTextBeforeSpeech $partialText"
                                }
                                onLiveResult(combined)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "tr-TR")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra("android.speech.extra.DICTATION_MODE", true)
                }

                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                Log.e(tag, "Failed to start speech recognizer", e)
                _isListening.value = false
                onError("Ses tanıma başlatılamadı: ${e.localizedMessage ?: "Cihaz desteklemiyor olabilir."}")
            }
        }
    }

    fun stopListening() {
        isExplicitlyStopped = true
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping speech recognizer", e)
            } finally {
                cleanUpRecognizer()
                _isListening.value = false
            }
        }
    }

    private fun cleanUpRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignored
        } finally {
            speechRecognizer = null
        }
    }

    fun destroy() {
        stopListening()
    }
}
