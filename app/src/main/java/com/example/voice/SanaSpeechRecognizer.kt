package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
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
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var activeLanguage = "auto"
    private var isDestroyed = false

    // State tracking for Voice Activity Detection (VAD)
    private var isSpeechActive = false
    private var lastSpeechTime = 0L
    private var lastPartialText: String = ""

    // VAD silence checking runnable
    private var vadCheckerRunnable: Runnable? = null
    private var maxSpeechTimeoutRunnable: Runnable? = null

    // Volume state to silence system recognizer beep
    private var originalSystemVolume: Int = -1
    private var originalNotificationVolume: Int = -1
    private var isBeepSuppressed = false

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(tag, "onReadyForSpeech -> ready for user audio")
            _isListening.value = true
            isSpeechActive = false
            lastSpeechTime = 0L
            lastPartialText = ""

            // Restore audio volume after recognizer has initialized silently
            restoreBeepVolume()

            // Safety max duration: never record endlessly (max 12s)
            startMaxSpeechSafetyTimeout()
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "onBeginningOfSpeech -> user started talking")
            isSpeechActive = true
            lastSpeechTime = System.currentTimeMillis()
            onSpeechStart()
            startVadSilenceWatcher()
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = (rmsdB.coerceAtLeast(0f) / 10f).coerceIn(0f, 1f)
            _rmsDb.value = normalized

            // If audio energy indicates speech (> 1.8 dB)
            if (rmsdB > 1.8f) {
                if (!isSpeechActive) {
                    isSpeechActive = true
                    onSpeechStart()
                    startVadSilenceWatcher()
                }
                lastSpeechTime = System.currentTimeMillis()
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(tag, "onEndOfSpeech -> Android detected speech stop")
            stopVadWatchers()
            _isListening.value = false
            _rmsDb.value = 0f
            onSpeechEnd()

            // Stop input recording immediately to prevent endless listening
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(tag, "Error in onEndOfSpeech stopListening", e)
            }
        }

        override fun onError(error: Int) {
            stopVadWatchers()
            restoreBeepVolume()
            _isListening.value = false
            _rmsDb.value = 0f

            // If we captured valid speech before error, deliver it!
            if ((error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) &&
                lastPartialText.isNotBlank()) {
                val text = lastPartialText.trim()
                lastPartialText = ""
                onResult(text)
                return
            }

            // Silent timeout / no match -> handle calmly without rapid beep loop
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                Log.d(tag, "No speech heard during session.")
                onNoSpeechDetected()
                return
            }

            val message = getErrorDescription(error)
            Log.w(tag, "SpeechRecognizer error: $error - $message")
            onError(error, message)
        }

        override fun onResults(results: Bundle?) {
            stopVadWatchers()
            restoreBeepVolume()
            _isListening.value = false
            _rmsDb.value = 0f

            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim() ?: lastPartialText.trim()
            lastPartialText = ""

            if (recognizedText.isNotBlank()) {
                Log.d(tag, "Speech finalized: $recognizedText")
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
                isSpeechActive = true
                lastSpeechTime = System.currentTimeMillis()
                onSpeechStart()
                startVadSilenceWatcher()
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
     * Starts listening with system beep suppression
     */
    fun startListening(languageMode: String = "auto") {
        if (isDestroyed) return
        activeLanguage = languageMode
        lastPartialText = ""
        isSpeechActive = false
        lastSpeechTime = 0L

        mainHandler.post {
            try {
                if (speechRecognizer == null) {
                    initRecognizer()
                }

                // Suppress recognizer start "tut-tut" sound
                suppressBeepVolume()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)

                    // Prompt silence thresholds
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 600L)
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
                restoreBeepVolume()
                Log.e(tag, "startListening error", e)
                onError(-1, e.message ?: "Failed to start speech recognition")
            }
        }
    }

    /**
     * Stop input promptly
     */
    fun stopListening() {
        stopVadWatchers()
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
        stopVadWatchers()
        restoreBeepVolume()
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

    /**
     * Voice Activity Detection Watcher:
     * When user speaks and then goes silent for 750ms -> STOP INPUT immediately!
     */
    private fun startVadSilenceWatcher() {
        if (vadCheckerRunnable != null) return

        vadCheckerRunnable = object : Runnable {
            override fun run() {
                if (_isListening.value && isSpeechActive) {
                    val silenceDuration = System.currentTimeMillis() - lastSpeechTime
                    if (silenceDuration >= 750L) {
                        Log.d(tag, "VAD: User finished speaking ($silenceDuration ms silence) -> STOPPING INPUT")
                        stopVadWatchers()
                        stopListening()
                        return
                    }
                }
                if (_isListening.value) {
                    mainHandler.postDelayed(this, 150L)
                }
            }
        }
        mainHandler.postDelayed(vadCheckerRunnable!!, 150L)
    }

    /**
     * Hard safety cap: Never allow recording to exceed 12 seconds continuously
     */
    private fun startMaxSpeechSafetyTimeout() {
        cancelMaxSpeechTimeout()
        maxSpeechTimeoutRunnable = Runnable {
            if (_isListening.value) {
                Log.d(tag, "Max speech safety timeout reached (12s) -> finalizing input")
                stopVadWatchers()
                stopListening()
            }
        }
        mainHandler.postDelayed(maxSpeechTimeoutRunnable!!, 12000L)
    }

    private fun cancelMaxSpeechTimeout() {
        maxSpeechTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        maxSpeechTimeoutRunnable = null
    }

    private fun stopVadWatchers() {
        vadCheckerRunnable?.let { mainHandler.removeCallbacks(it) }
        vadCheckerRunnable = null
        cancelMaxSpeechTimeout()
    }

    /**
     * Temporarily mute STREAM_SYSTEM and STREAM_NOTIFICATION to eliminate recognizer tut-tut/beep
     */
    private fun suppressBeepVolume() {
        try {
            val am = audioManager ?: return
            if (!isBeepSuppressed) {
                originalSystemVolume = am.getStreamVolume(AudioManager.STREAM_SYSTEM)
                originalNotificationVolume = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
                    am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    am.setStreamMute(AudioManager.STREAM_SYSTEM, true)
                    @Suppress("DEPRECATION")
                    am.setStreamMute(AudioManager.STREAM_NOTIFICATION, true)
                }
                isBeepSuppressed = true
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not suppress recognizer beep", e)
        }
    }

    private fun restoreBeepVolume() {
        try {
            val am = audioManager ?: return
            if (isBeepSuppressed) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
                    am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
                } else {
                    @Suppress("DEPRECATION")
                    am.setStreamMute(AudioManager.STREAM_SYSTEM, false)
                    @Suppress("DEPRECATION")
                    am.setStreamMute(AudioManager.STREAM_NOTIFICATION, false)
                }
                isBeepSuppressed = false
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not restore volume", e)
        }
    }

    private fun destroyRecognizerInternal() {
        stopVadWatchers()
        restoreBeepVolume()
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
