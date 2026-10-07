package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class SanaTextToSpeech(
    private val context: Context,
    private val onSpeechStarted: () -> Unit,
    private val onSpeechDone: () -> Unit,
    private val onSpeechError: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private val tag = "SanaTextToSpeech"
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private var activeUtteranceId: String? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupVoiceDefaults()
            setupProgressListener()
            Log.d(tag, "TTS initialized successfully.")
        } else {
            Log.e(tag, "TTS initialization failed with status $status")
            onSpeechError("Text-To-Speech engine failed to initialize.")
        }
    }

    private fun setupVoiceDefaults() {
        val tts = tts ?: return
        try {
            // Check for Urdu support first
            val urduLocale = Locale("ur", "PK")
            val urduResult = tts.isLanguageAvailable(urduLocale)
            if (urduResult >= TextToSpeech.LANG_AVAILABLE) {
                tts.language = urduLocale
            } else {
                val urFallback = Locale("ur")
                if (tts.isLanguageAvailable(urFallback) >= TextToSpeech.LANG_AVAILABLE) {
                    tts.language = urFallback
                } else {
                    tts.language = Locale.US
                }
            }

            // Get available voices
            val voices = tts.voices
            if (voices != null) {
                _availableVoices.value = voices.filter { !it.isNetworkConnectionRequired }.toList()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error setting up voice defaults", e)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    _isSpeaking.value = true
                    onSpeechStarted()
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                    onSpeechDone()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == activeUtteranceId) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                    onSpeechError("TTS speech generation failed.")
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == activeUtteranceId) {
                    _isSpeaking.value = false
                    abandonAudioFocus()
                    onSpeechError("TTS error code: $errorCode")
                }
            }
        })
    }

    /**
     * Speaks text using current settings.
     */
    fun speak(
        text: String,
        speed: Float = 1.05f,
        pitch: Float = 1.15f,
        languageHint: String = "auto"
    ) {
        if (!isInitialized || tts == null) {
            Log.w(tag, "TTS not ready to speak.")
            onSpeechError("TTS engine not ready.")
            return
        }

        // Clean formatting tags like [EMOTION: HAPPY] from speech
        val cleanedText = cleanTextForSpeech(text)
        if (cleanedText.isBlank()) {
            onSpeechDone()
            return
        }

        requestAudioFocus()

        try {
            tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
            tts?.setPitch(pitch.coerceIn(0.5f, 2.0f))

            // Adapt language if detectable
            if (containsUrduScript(cleanedText)) {
                val urLocale = Locale("ur", "PK")
                if (tts?.isLanguageAvailable(urLocale) ?: -1 >= TextToSpeech.LANG_AVAILABLE) {
                    tts?.language = urLocale
                }
            } else if (languageHint == "en") {
                tts?.language = Locale.US
            }

            val utteranceId = UUID.randomUUID().toString()
            activeUtteranceId = utteranceId

            val params = HashMap<String, String>()
            params[TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID] = utteranceId

            tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, params)
        } catch (e: Exception) {
            Log.e(tag, "TTS speak exception", e)
            abandonAudioFocus()
            onSpeechError("TTS failed: ${e.message}")
        }
    }

    /**
     * Instantly halt speech output for natural interruption.
     */
    fun stop() {
        try {
            activeUtteranceId = null
            tts?.stop()
            _isSpeaking.value = false
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping TTS", e)
        }
    }

    fun isReady(): Boolean = isInitialized

    private fun cleanTextForSpeech(raw: String): String {
        return raw
            .replace(Regex("\\[EMOTION:[^\\]]+\\]"), "")
            .replace(Regex("\\[ACTION:[^\\]]+\\]"), "")
            .replace(Regex("[*#_`~]"), "")
            .trim()
    }

    private fun containsUrduScript(text: String): Boolean {
        // Arabic/Urdu Unicode range: \u0600-\u06FF, \u0750-\u077F, \uFB50-\uFDFF, \uFE70-\uFEFF
        val urduPattern = Regex("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF]")
        return urduPattern.containsMatchIn(text)
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stop()
                    }
                }
                .build()

            audioFocusRequest = focusRequest
            am.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    fun destroy() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
