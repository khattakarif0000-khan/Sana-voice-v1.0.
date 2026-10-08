package com.example.viewmodel

import android.app.Application
import android.content.Context
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SanaViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "SanaViewModel"
    private val context: Context get() = getApplication<Application>().applicationContext

    private val db = SanaApplication.instance.database
    private val memoryDao = db.memoryDao()
    private val prefs = SanaApplication.instance.preferences
    val settings: StateFlow<SanaSettingsData> = prefs.settings

    val phoneControl = PhoneControlManager(context)
    val geminiClient = GeminiClient(context)

    // State Machine: IDLE, LISTENING, PROCESSING, SPEAKING, INTERRUPTED, RECOVERING, ERROR, STOPPED
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

    // Continuous auto-conversation master switch
    private val _isHandsFreeActive = MutableStateFlow(false)
    val isHandsFreeActive: StateFlow<Boolean> = _isHandsFreeActive.asStateFlow()

    // Live audio wave amplitude (0f - 1f)
    private val _audioWaveLevel = MutableStateFlow(0f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    // Real Gemini Voice Engine (Zero Android TTS)
    private var speechRecognizer: SanaSpeechRecognizer? = null
    private var audioTrackPlayer: SanaAudioTrackPlayer? = null

    // Background jobs
    private var listeningLoopJob: Job? = null
    private var recoveryJob: Job? = null
    private var processingJob: Job? = null

    // First spoken startup greeting state
    private var hasSpokenStartupGreeting = false

    companion object {
        const val STARTUP_GREETING_TEXT = "Assalamualaikum… mera Boss aa gaya! ❤️ Kaise ho mere Boss? Sab theek hai na?"
    }

    init {
        loadMemories()
        initVoiceEngine()
        updateDiagnostics()

        // Welcome greeting
        _messages.value = listOf(
            ChatMessage(
                role = MessageRole.SANA,
                text = STARTUP_GREETING_TEXT,
                emotion = EmotionType.AFFECTIONATE
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

    private fun initVoiceEngine() {
        speechRecognizer = SanaSpeechRecognizer(
            context = context,
            onResult = { text ->
                handleSpeechRecognized(text)
            },
            onError = { code, desc ->
                handleSpeechError(code, desc)
            },
            onNoSpeechDetected = {
                handleNoSpeechDetected()
            },
            onSpeechStart = {
                // If SANA is speaking and user speaks, trigger instant interruption!
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

        // Observe RMS amplitude from live mic
        viewModelScope.launch {
            speechRecognizer?.rmsDb?.collect { rms ->
                if (_conversationState.value == ConversationState.LISTENING) {
                    _audioWaveLevel.value = rms
                }
            }
        }

        // Dedicated 24kHz PCM AudioTrack speaker player
        audioTrackPlayer = SanaAudioTrackPlayer(
            context = context,
            onPlaybackStarted = {
                _conversationState.value = ConversationState.SPEAKING
                _diagnosticStatus.value = _diagnosticStatus.value.copy(geminiVoiceReady = true)
                _audioWaveLevel.value = 0.7f
            },
            onPlaybackFinished = {
                _audioWaveLevel.value = 0f
                handleAudioFinished()
            },
            onPlaybackError = { errorMsg ->
                Log.e(tag, "AudioTrack playback error: $errorMsg")
                _audioWaveLevel.value = 0f
                _diagnosticStatus.value = _diagnosticStatus.value.copy(
                    lastError = "Audio playback error: $errorMsg"
                )
                handleAudioFinished()
            }
        )
    }

    // -------------------------------------------------------------
    // Core Auto-Conversation Lifecycle
    // -------------------------------------------------------------

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
                lastError = "Microphone permission required."
            )
            return
        }

        _isHandsFreeActive.value = true
        SanaVoiceService.start(context)

        if (!hasSpokenStartupGreeting) {
            hasSpokenStartupGreeting = true
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
            speakGeminiVoice(STARTUP_GREETING_TEXT, EmotionType.AFFECTIONATE, effectiveApiKey)
        } else {
            startListeningLoop()
        }
    }

    fun stopConversation() {
        _isHandsFreeActive.value = false
        listeningLoopJob?.cancel()
        recoveryJob?.cancel()
        processingJob?.cancel()

        speechRecognizer?.stopListening()
        audioTrackPlayer?.stop()

        _conversationState.value = ConversationState.STOPPED
        _audioWaveLevel.value = 0f
        SanaVoiceService.stop(context)

        viewModelScope.launch {
            delay(400)
            if (!_isHandsFreeActive.value) {
                _conversationState.value = ConversationState.IDLE
            }
        }
    }

    private fun startListeningLoop() {
        if (!_isHandsFreeActive.value) return

        listeningLoopJob?.cancel()
        listeningLoopJob = viewModelScope.launch {
            // Guard: Never listen while speaking
            if (audioTrackPlayer?.isPlaying?.value == true) {
                return@launch
            }

            _conversationState.value = ConversationState.LISTENING
            _audioWaveLevel.value = 0.1f
            speechRecognizer?.startListening(settings.value.languageMode)
        }
    }

    /**
     * Interruption / Barge-in: user spoke while SANA was speaking
     */
    private fun handleUserInterruption() {
        Log.d(tag, "Barge-in: User interrupted SANA -> stopping audio immediately")
        _conversationState.value = ConversationState.INTERRUPTED
        audioTrackPlayer?.stop()
        _audioWaveLevel.value = 0f

        viewModelScope.launch {
            delay(100)
            if (_isHandsFreeActive.value) {
                startListeningLoop()
            }
        }
    }

    /**
     * Speech finalized after end-of-speech detection
     */
    private fun handleSpeechRecognized(text: String) {
        if (text.isBlank()) {
            handleNoSpeechDetected()
            return
        }

        _conversationState.value = ConversationState.PROCESSING
        _audioWaveLevel.value = 0f

        val userMsg = ChatMessage(
            role = MessageRole.USER,
            text = text
        )
        _messages.value = _messages.value + userMsg

        processUserInput(text)
    }

    /**
     * Calm handling of no speech heard — prevents rapid start/stop tut-tut loop
     */
    private fun handleNoSpeechDetected() {
        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        // Wait a calm, quiet 1.2s before listening again without rapid clicking/beeping
        listeningLoopJob?.cancel()
        listeningLoopJob = viewModelScope.launch {
            delay(1200)
            if (_isHandsFreeActive.value && _conversationState.value != ConversationState.SPEAKING) {
                startListeningLoop()
            }
        }
    }

    /**
     * Main reasoning and action pipeline
     */
    fun processUserInput(text: String, imageBase64: String? = null) {
        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _conversationState.value = ConversationState.PROCESSING

            // Local offline command check
            val localMatch = LocalCommandParser.parse(text)
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)

            // If local command matched (e.g. WhatsApp, Torch, Battery)
            if (localMatch.matched) {
                executeActionAndReply(
                    command = localMatch.command,
                    param = localMatch.parameter,
                    replyText = localMatch.spokenResponse ?: "Action executed.",
                    emotion = localMatch.emotion,
                    effectiveApiKey = effectiveApiKey
                )
                return@launch
            }

            // Call Gemini Brain
            val aiResponse = geminiClient.generateResponse(
                userInput = text,
                history = _messages.value,
                settings = settings.value,
                memories = _memories.value,
                imageBase64 = imageBase64
            )

            _currentEmotion.value = aiResponse.emotion

            // Check if action execution is requested
            if (aiResponse.actionCommand != null) {
                executeActionAndReply(
                    command = aiResponse.actionCommand,
                    param = aiResponse.actionParameter,
                    replyText = aiResponse.replyText,
                    emotion = aiResponse.emotion,
                    effectiveApiKey = effectiveApiKey,
                    preloadedAudio = aiResponse.audioBytes
                )
            } else {
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
        effectiveApiKey: String,
        preloadedAudio: ByteArray? = null
    ) {
        var actionResult: ActionResult? = null

        when (command?.uppercase()) {
            "OPEN_WHATSAPP" -> actionResult = phoneControl.openWhatsApp()
            "CAMERA" -> actionResult = phoneControl.openCamera()
            "CALL" -> actionResult = phoneControl.callContactOrNumber(param ?: "")
            "TORCH_ON" -> actionResult = phoneControl.toggleTorch(true)
            "TORCH_OFF" -> actionResult = phoneControl.toggleTorch(false)
            "BATTERY" -> actionResult = phoneControl.getBatteryStatus()
            "YOUTUBE" -> actionResult = phoneControl.openYouTube(param)
            "WEB_SEARCH" -> actionResult = phoneControl.searchWeb(param ?: "")
            "SETTINGS" -> actionResult = phoneControl.openSettingsScreen(param ?: "general")
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
                speakGeminiVoice(memoryListText, EmotionType.HAPPY, effectiveApiKey)
                return
            }
            "NOTIFICATIONS_SUMMARY" -> {
                val summary = SanaNotificationListenerService.getSummary()
                speakGeminiVoice(summary, EmotionType.HAPPY, effectiveApiKey)
                return
            }
        }

        val finalSpeechText = if (actionResult != null && !actionResult.success) {
            "$replyText (${actionResult.detail})"
        } else {
            replyText
        }

        if (preloadedAudio != null && preloadedAudio.isNotEmpty()) {
            deliverSanaReply(finalSpeechText, emotion, actionResult, preloadedAudio)
        } else {
            speakGeminiVoice(finalSpeechText, emotion, effectiveApiKey, actionResult)
        }
    }

    /**
     * Generates and speaks through Gemini's 24kHz PCM AudioTrack output (Zero Android TTS)
     */
    private fun speakGeminiVoice(
        text: String,
        emotion: EmotionType,
        apiKey: String,
        actionResult: ActionResult? = null
    ) {
        viewModelScope.launch {
            val audioBytes = if (apiKey.isNotBlank()) {
                geminiClient.generateGeminiSpeechAudio(text, apiKey)
            } else null

            deliverSanaReply(
                speechText = text,
                emotion = emotion,
                actionResult = actionResult,
                audioBytes = audioBytes
            )
        }
    }

    /**
     * Deliver SANA response: Spoken ONLY via Gemini AudioTrack. Never via Android TTS.
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

        if (audioBytes != null && audioBytes.isNotEmpty()) {
            _conversationState.value = ConversationState.SPEAKING
            Log.d(tag, "Playing Gemini PCM audio through phone speaker (${audioBytes.size} bytes)")
            audioTrackPlayer?.playPcmAudio(audioBytes)
        } else {
            // If Gemini audio is not available, DO NOT FALL BACK TO TTS.
            // Display message in chat and calmly proceed to next turn or idle.
            Log.w(tag, "Gemini audio unavailable — TTS fallback is strictly disabled")
            _diagnosticStatus.value = _diagnosticStatus.value.copy(
                lastError = if (geminiClient.getEffectiveApiKey(settings.value.customApiKey).isBlank())
                    "Gemini API key needed for voice audio." else null
            )
            handleAudioFinished()
        }
    }

    /**
     * Audio finished playing -> automatically re-listen for next turn in auto mode!
     */
    private fun handleAudioFinished() {
        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        // Automatic continuous re-listen!
        listeningLoopJob?.cancel()
        listeningLoopJob = viewModelScope.launch {
            delay(250) // Natural short pause
            if (_isHandsFreeActive.value) {
                startListeningLoop()
            }
        }
    }

    /**
     * Error handling for speech recognizer
     */
    private fun handleSpeechError(errorCode: Int, errorMessage: String) {
        Log.w(tag, "Speech recognition error $errorCode: $errorMessage")
        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            lastError = errorMessage
        )

        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        // Self-healing recovery with non-blocking gentle delay
        _conversationState.value = ConversationState.RECOVERING
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(1500)
            if (_isHandsFreeActive.value) {
                speechRecognizer?.cancel()
                delay(200)
                startListeningLoop()
            }
        }
    }

    // -------------------------------------------------------------
    // Memory Management
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
    // Settings & Voice Audition
    // -------------------------------------------------------------

    fun updateSettings(newSettings: SanaSettingsData) {
        prefs.updateSettings(newSettings)
    }

    fun setCustomApiKey(key: String) {
        prefs.setCustomApiKey(key)
        updateDiagnostics()
    }

    /**
     * Audition voice: tests Gemini voice through phone speaker (No TTS)
     */
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
            _diagnosticStatus.value = _diagnosticStatus.value.copy(
                lastError = "Gemini API key is required to hear SANA's voice."
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
            geminiVoiceReady = (audioTrackPlayer != null),
            aiConfigured = apiKey.isNotBlank(),
            batteryPct = battery.detail.filter { it.isDigit() }.toIntOrNull() ?: 100
        )
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        audioTrackPlayer?.destroy()
    }
}
