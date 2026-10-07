package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SanaApplication
import com.example.ai.GeminiClient
import com.example.ai.LocalCommandParser
import com.example.ai.SanaAiResponse
import com.example.data.MemoryEntity
import com.example.data.SanaSettingsData
import com.example.model.ActionResult
import com.example.model.ChatMessage
import com.example.model.ConversationState
import com.example.model.DiagnosticStatus
import com.example.model.EmotionType
import com.example.model.MessageRole
import com.example.phone.PhoneControlManager
import com.example.service.SanaNotificationListenerService
import com.example.service.SanaVoiceService
import com.example.voice.SanaAudioTrackPlayer
import com.example.voice.SanaSpeechRecognizer
import com.example.voice.SanaTextToSpeech
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SanaViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "SanaViewModel"
    private val context: Context get() = getApplication<Application>().applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val db = SanaApplication.instance.database
    private val memoryDao = db.memoryDao()
    private val prefs = SanaApplication.instance.preferences
    val settings: StateFlow<SanaSettingsData> = prefs.settings

    val phoneControl = PhoneControlManager(context)
    val geminiClient = GeminiClient(context)

    // State Machine
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(EmotionType.NEUTRAL)
    val currentEmotion: StateFlow<EmotionType> = _currentEmotion.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _diagnosticStatus = MutableStateFlow(DiagnosticStatus())
    val diagnosticStatus: StateFlow<DiagnosticStatus> = _diagnosticStatus.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memories: StateFlow<List<MemoryEntity>> = _memories.asStateFlow()

    // Hands-free continuous loop master switch
    private val _isHandsFreeActive = MutableStateFlow(false)
    val isHandsFreeActive: StateFlow<Boolean> = _isHandsFreeActive.asStateFlow()

    // Live audio wave amplitude (0f - 1f)
    private val _audioWaveLevel = MutableStateFlow(0f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    // Dual-Layer Voice Engine: Gemini Native AudioTrack + Local TTS Fallback
    private var speechRecognizer: SanaSpeechRecognizer? = null
    private var audioTrackPlayer: SanaAudioTrackPlayer? = null
    private var textToSpeech: SanaTextToSpeech? = null

    // Self-healing retry backoff
    private var consecutiveErrors = 0
    private var recoveryJob: Job? = null

    init {
        loadMemories()
        initVoiceEngines()
        updateDiagnostics()

        // Initial welcome message
        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.SANA,
                text = "السلام علیکم! میں ثناء (SANA) ہوں۔ آپ کی ذہین اور باوفا وائس ساتھی۔ مائیکروفون دبائیں اور مجھ سے بے جھجھک بات کریں۔",
                emotion = EmotionType.HAPPY
            )
        )
    }

    private fun loadMemories() {
        viewModelScope.launch {
            memoryDao.getAllMemoriesFlow().collect { list ->
                _memories.value = list
            }
        }
    }

    private fun initVoiceEngines() {
        speechRecognizer = SanaSpeechRecognizer(
            context = context,
            onResult = { text ->
                handleSpeechRecognized(text)
            },
            onError = { code, desc ->
                handleSpeechError(code, desc)
            },
            onSpeechStart = {
                // If SANA is currently speaking and user speaks, trigger immediate natural interruption!
                if (_conversationState.value == ConversationState.SPEAKING) {
                    handleUserInterruption()
                } else if (_conversationState.value == ConversationState.LISTENING) {
                    _audioWaveLevel.value = 0.5f
                }
            },
            onSpeechEnd = {
                _audioWaveLevel.value = 0f
            }
        )

        // Observe RMS amplitude from real mic
        viewModelScope.launch {
            speechRecognizer?.rmsDb?.collect { rms ->
                if (_conversationState.value == ConversationState.LISTENING) {
                    _audioWaveLevel.value = rms
                }
            }
        }

        // Primary: Native 24kHz Gemini AudioTrack Player
        audioTrackPlayer = SanaAudioTrackPlayer(
            context = context,
            onPlaybackStarted = {
                _conversationState.value = ConversationState.SPEAKING
                _diagnosticStatus.value = _diagnosticStatus.value.copy(ttsReady = true)
                _audioWaveLevel.value = 0.65f
            },
            onPlaybackFinished = {
                _audioWaveLevel.value = 0f
                handleAudioFinished()
            },
            onPlaybackError = { errorMsg ->
                Log.w(tag, "AudioTrack error: $errorMsg, falling back to TTS")
                _audioWaveLevel.value = 0f
                handleAudioFinished()
            }
        )

        // Secondary / Fallback: Android TextToSpeech engine
        textToSpeech = SanaTextToSpeech(
            context = context,
            onSpeechStarted = {
                _conversationState.value = ConversationState.SPEAKING
                _diagnosticStatus.value = _diagnosticStatus.value.copy(ttsReady = true)
                _audioWaveLevel.value = 0.55f
            },
            onSpeechDone = {
                _audioWaveLevel.value = 0f
                handleAudioFinished()
            },
            onSpeechError = { errorMsg ->
                Log.e(tag, "TTS error: $errorMsg")
                _audioWaveLevel.value = 0f
                _diagnosticStatus.value = _diagnosticStatus.value.copy(
                    lastError = "TTS: $errorMsg"
                )
                handleAudioFinished()
            }
        )
    }

    // -------------------------------------------------------------
    // Core Hands-Free Conversation Lifecycle
    // -------------------------------------------------------------

    /**
     * One-Tap Start / Stop Master Method
     */
    fun toggleConversation(hasAudioPermission: Boolean) {
        if (_isHandsFreeActive.value) {
            stopConversation()
        } else {
            startConversation(hasAudioPermission)
        }
    }

    fun startConversation(hasAudioPermission: Boolean) {
        if (!hasAudioPermission) {
            _conversationState.value = ConversationState.ERROR
            _diagnosticStatus.value = _diagnosticStatus.value.copy(
                lastError = "Microphone permission required to start conversation."
            )
            return
        }

        _isHandsFreeActive.value = true
        consecutiveErrors = 0
        SanaVoiceService.start(context)

        startListeningLoop()
    }

    fun stopConversation() {
        _isHandsFreeActive.value = false
        recoveryJob?.cancel()
        speechRecognizer?.stopListening()
        audioTrackPlayer?.stop()
        textToSpeech?.stop()
        _conversationState.value = ConversationState.STOPPED
        _audioWaveLevel.value = 0f
        SanaVoiceService.stop(context)

        viewModelScope.launch {
            delay(500)
            if (!_isHandsFreeActive.value) {
                _conversationState.value = ConversationState.IDLE
            }
        }
    }

    private fun startListeningLoop() {
        if (!_isHandsFreeActive.value) return

        _conversationState.value = ConversationState.LISTENING
        _audioWaveLevel.value = 0.1f
        speechRecognizer?.startListening(settings.value.languageMode)
    }

    /**
     * Natural Interruption: User spoke while SANA was speaking
     */
    private fun handleUserInterruption() {
        Log.d(tag, "Natural Interruption triggered -> stopping audio output instantly")
        _conversationState.value = ConversationState.INTERRUPTED
        audioTrackPlayer?.stop()
        textToSpeech?.stop()
        _audioWaveLevel.value = 0f

        viewModelScope.launch {
            delay(80)
            if (_isHandsFreeActive.value) {
                _conversationState.value = ConversationState.LISTENING
                speechRecognizer?.startListening(settings.value.languageMode)
            }
        }
    }

    /**
     * Speech-to-text received final speech string from user
     */
    private fun handleSpeechRecognized(text: String) {
        if (text.isBlank()) {
            if (_isHandsFreeActive.value) startListeningLoop()
            return
        }

        consecutiveErrors = 0
        _conversationState.value = ConversationState.PROCESSING

        val userMsg = ChatMessage(
            role = MessageRole.USER,
            text = text
        )
        _messages.value = _messages.value + userMsg

        processUserInput(text)
    }

    /**
     * Fast reasoning and action pipeline
     */
    fun processUserInput(text: String, imageBase64: String? = null) {
        viewModelScope.launch {
            _conversationState.value = ConversationState.PROCESSING

            // Check if local offline parser can handle immediate direct commands
            val localMatch = LocalCommandParser.parse(text)
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)

            // If API key is missing AND local command matched: execute local phone control directly!
            if (effectiveApiKey.isBlank() && localMatch.matched) {
                executeActionAndReply(
                    command = localMatch.command,
                    param = localMatch.parameter,
                    replyText = "${localMatch.spokenResponse ?: "Action executed."} (Note: AI brain key not configured)",
                    emotion = localMatch.emotion,
                    audioBytes = null
                )
                return@launch
            }

            // Call Gemini Brain (fast concise generation)
            val aiResponse = geminiClient.generateResponse(
                userInput = text,
                history = _messages.value,
                settings = settings.value,
                memories = _memories.value,
                imageBase64 = imageBase64
            )

            _currentEmotion.value = aiResponse.emotion

            // Check if phone control action execution is requested
            if (aiResponse.actionCommand != null) {
                executeActionAndReply(
                    command = aiResponse.actionCommand,
                    param = aiResponse.actionParameter,
                    replyText = aiResponse.replyText,
                    emotion = aiResponse.emotion,
                    audioBytes = aiResponse.audioBytes
                )
            } else {
                // Direct audible reply
                deliverSanaReply(
                    speechText = aiResponse.replyText,
                    emotion = aiResponse.emotion,
                    audioBytes = aiResponse.audioBytes
                )
            }
        }
    }

    /**
     * Executes real Android system commands and produces verified speech reply
     */
    private fun executeActionAndReply(
        command: String?,
        param: String?,
        replyText: String,
        emotion: EmotionType,
        audioBytes: ByteArray?
    ) {
        var actionResult: ActionResult? = null

        when (command?.uppercase()) {
            "OPEN_WHATSAPP" -> {
                actionResult = phoneControl.openWhatsApp()
            }
            "CAMERA" -> {
                actionResult = phoneControl.openCamera()
            }
            "CALL" -> {
                actionResult = phoneControl.callContactOrNumber(param ?: "")
            }
            "TORCH_ON" -> {
                actionResult = phoneControl.toggleTorch(true)
            }
            "TORCH_OFF" -> {
                actionResult = phoneControl.toggleTorch(false)
            }
            "BATTERY" -> {
                actionResult = phoneControl.getBatteryStatus()
            }
            "YOUTUBE" -> {
                actionResult = phoneControl.openYouTube(param)
            }
            "WEB_SEARCH" -> {
                actionResult = phoneControl.searchWeb(param ?: "")
            }
            "SETTINGS" -> {
                actionResult = phoneControl.openSettingsScreen(param ?: "general")
            }
            "REMEMBER" -> {
                param?.let { fact ->
                    viewModelScope.launch {
                        memoryDao.insertMemory(MemoryEntity(content = fact))
                    }
                }
                actionResult = ActionResult(true, "MEMORY", "Memory saved: '$param'", true)
            }
            "FORGET" -> {
                viewModelScope.launch {
                    if (param == "ALL" || param.isNullOrBlank()) {
                        memoryDao.clearAllMemories()
                    } else {
                        memoryDao.deleteMemoriesByKeyword(param)
                    }
                }
                actionResult = ActionResult(true, "MEMORY", "Memory cleared.", true)
            }
            "RECALL_MEMORIES" -> {
                val list = _memories.value
                val memoryListText = if (list.isEmpty()) {
                    "ابھی آپ کی کوئی یادداشت محفوظ نہیں ہے۔"
                } else {
                    "آپ کی یادداشتیں:\n" + list.joinToString("\n") { it.content }
                }
                deliverSanaReply(memoryListText, EmotionType.HAPPY, audioBytes = null)
                return
            }
            "NOTIFICATIONS_SUMMARY" -> {
                val summary = SanaNotificationListenerService.getSummary()
                deliverSanaReply(summary, EmotionType.HAPPY, audioBytes = null)
                return
            }
        }

        val finalSpeechText = if (actionResult != null && !actionResult.success) {
            "$replyText (${actionResult.detail})"
        } else {
            replyText
        }

        deliverSanaReply(
            speechText = finalSpeechText,
            emotion = emotion,
            actionResult = actionResult,
            audioBytes = audioBytes
        )
    }

    /**
     * Primary speech delivery: Real Gemini 24kHz audio through AudioTrack, or TTS fallback
     */
    private fun deliverSanaReply(
        speechText: String,
        emotion: EmotionType,
        actionResult: ActionResult? = null,
        audioBytes: ByteArray? = null
    ) {
        _currentEmotion.value = emotion

        val sanaMsg = ChatMessage(
            role = MessageRole.SANA,
            text = speechText,
            emotion = emotion,
            actionTag = actionResult?.actionType,
            isActionVerified = actionResult?.verified ?: false
        )
        _messages.value = _messages.value + sanaMsg
        _conversationState.value = ConversationState.SPEAKING

        // Priority 1: Real native Gemini PCM audio output through phone speaker
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            Log.d(tag, "Playing real Gemini PCM audio through phone speaker (${audioBytes.size} bytes)")
            audioTrackPlayer?.playPcmAudio(audioBytes)
        } else {
            // Priority 2: Android Text-To-Speech engine fallback
            Log.d(tag, "Playing speech through local Android TextToSpeech engine")
            textToSpeech?.speak(
                text = speechText,
                speed = settings.value.ttsSpeed,
                pitch = settings.value.ttsPitch,
                languageHint = settings.value.languageMode
            )
        }
    }

    /**
     * Called when audio finishes playing through phone speaker.
     * When hands-free is active, automatically listen again!
     */
    private fun handleAudioFinished() {
        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        // Automatic hands-free re-listen loop!
        viewModelScope.launch {
            delay(200) // Natural conversational pause
            if (_isHandsFreeActive.value) {
                startListeningLoop()
            }
        }
    }

    /**
     * Self-Healing System for Speech Recognition glitches/timeouts
     */
    private fun handleSpeechError(errorCode: Int, errorMessage: String) {
        Log.w(tag, "Handling speech error $errorCode: $errorMessage")
        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            lastError = errorMessage
        )

        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        consecutiveErrors++

        // Handle silence or no-match smoothly without crashing continuous mode
        if (errorCode == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
            errorCode == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            viewModelScope.launch {
                delay(250)
                if (_isHandsFreeActive.value) {
                    startListeningLoop()
                }
            }
            return
        }

        // Severe error: trigger Self-Healing with backoff
        _conversationState.value = ConversationState.RECOVERING
        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            recoveryAttempt = consecutiveErrors
        )

        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            val backoffMs = (400L * consecutiveErrors).coerceAtMost(2500L)
            delay(backoffMs)

            if (_isHandsFreeActive.value) {
                speechRecognizer?.restart()
                delay(150)
                startListeningLoop()
            }
        }
    }

    // -------------------------------------------------------------
    // Memory Management UI Affordances
    // -------------------------------------------------------------

    fun addManualMemory(fact: String, category: String = "User") {
        if (fact.isBlank()) return
        viewModelScope.launch {
            memoryDao.insertMemory(MemoryEntity(content = fact.trim(), category = category))
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            memoryDao.deleteMemoryById(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            memoryDao.clearAllMemories()
        }
    }

    // -------------------------------------------------------------
    // Settings & Audition
    // -------------------------------------------------------------

    fun updateSettings(newSettings: SanaSettingsData) {
        prefs.updateSettings(newSettings)
    }

    fun setCustomApiKey(key: String) {
        prefs.setCustomApiKey(key)
        updateDiagnostics()
    }

    fun testVoiceAudition() {
        val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        viewModelScope.launch {
            if (apiKey.isNotBlank()) {
                val sampleText = "السلام علیکم! میں ثناء ہوں۔ میری آواز اس طرح سنائی دے گی۔"
                val audio = geminiClient.generateGeminiSpeechAudio(sampleText, apiKey)
                if (audio != null) {
                    audioTrackPlayer?.playPcmAudio(audio)
                    return@launch
                }
            }
            // Fallback to local TTS
            textToSpeech?.speak(
                text = "جی میں ثناء ہوں۔ میری آواز کی رفتار اور لہجہ اس طرح سنائی دے گا۔",
                speed = settings.value.ttsSpeed,
                pitch = settings.value.ttsPitch,
                languageHint = "ur"
            )
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    fun updateDiagnostics() {
        val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        val battery = phoneControl.getBatteryStatus()

        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            micReady = speechRecognizer != null,
            ttsReady = (audioTrackPlayer != null || textToSpeech?.isReady() == true),
            aiConfigured = apiKey.isNotBlank(),
            batteryPct = battery.detail.filter { it.isDigit() }.toIntOrNull() ?: 100
        )
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        audioTrackPlayer?.destroy()
        textToSpeech?.destroy()
    }
}
