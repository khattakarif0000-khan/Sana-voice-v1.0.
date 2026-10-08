package com.example.voice

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

class SanaSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (Int, String) -> Unit,
    private val onNoSpeechDetected: () -> Unit,
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit
) {
    private val tag = "SanaSpeechRecognizer"
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var activeLanguage = "auto"
    private var isDestroyed = false
    private var lastPartialText: String = ""

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "onReadyForSpeech")
            _isListening.value = true
            lastPartialText = ""
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "onBeginningOfSpeech -> user started talking")
            onSpeechStart()
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
            _rmsDb.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "onEndOfSpeech -> speech ended")
            _isListening.value = false
            _rmsDb.value = 0f
            onSpeechEnd()
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _rmsDb.value = 0f

            // If we captured valid partial speech before timeout/error, deliver it
            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                lastPartialText.isNotBlank()) {
                val text = lastPartialText.trim()
                lastPartialText = ""
                onResult(text)
                return
            }

            // Normal silence / timeout
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.d(tag, "SpeechRecognizer: silence/timeout ($error)")
                onNoSpeechDetected()
                return
            }

            val message = getErrorDescription(error)
            Log.w(tag, "SpeechRecognizer error: $error - $message")
            onError(error, message)
        }

        override fun onResults(results: Bundle?) {
            _isListening.value = false
            _rmsDb.value = 0f

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: lastPartialText.trim()
            lastPartialText = ""

            if (recognizedText.isNotBlank()) {
                Log.d(tag, "Speech recognized: $recognizedText")
                onResult(recognizedText)
            } else {
                onNoSpeechDetected()
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim()
            if (!partial.isNullOrBlank()) {
                lastPartialText = partial
                onSpeechStart()
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "Speech recognition is NOT available on this device.")
            return
        }
        mainHandler.post {
            try {
                destroyRecognizerInternal()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(recognitionListener)
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to create SpeechRecognizer", e)
            }
        }
    }

    /**
     * Starts listening. Always cleans up any previous session first to ensure only 1 session exists.
     */
    fun startListening(languageMode: String = "auto") {
        if (isDestroyed) return
        activeLanguage = languageMode
        lastPartialText = ""

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    // Prompt silence thresholds
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)

                    when (languageMode.lowercase()) {
                        "ur" -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK")
                        }
                        "en" -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
                        }
                        else -> {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("ur-PK", "en-US", "ur"))
                        }
                    }
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(tag, "startListening error", e)
                onError(-1, e.message ?: "Failed to start speech recognition")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
                _rmsDb.value = 0f
            } catch (e: Exception) {
                Log.e(tag, "stopListening error", e)
            }
        }
    }

    fun cancel() {
        lastPartialText = ""
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                _isListening.value = false
                _rmsDb.value = 0f
            } catch (e: Exception) {
                Log.e(tag, "cancel error", e)
            }
        }
    }

    private fun destroyRecognizerInternal() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(tag, "destroyRecognizer error", e)
        }
    }

    fun destroy() {
        isDestroyed = true
        destroyRecognizerInternal()
    }

    private fun getErrorDescription(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timed out"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard"
            else -> "Speech recognition error ($errorCode)"
        }
    }
}
