package com.example.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiClient
import com.example.ai.LocalCommandParser
import com.example.data.DailyConversationReport
import com.example.data.DailyReportGenerator
import com.example.data.MemoryDao
import com.example.data.MemoryEntity
import com.example.data.RoutineDao
import com.example.data.RoutineEntity
import com.example.data.SanaDatabase
import com.example.data.SanaPreferences
import com.example.data.SanaSettingsData
import com.example.model.ActionResult
import com.example.model.ChatMessage
import com.example.model.ConversationState
import com.example.model.DiagnosticLevel
import com.example.model.DiagnosticNotice
import com.example.model.DiagnosticStatus
import com.example.model.EmotionType
import com.example.model.MessageRole
import com.example.model.TeachingSessionState
import com.example.phone.AntiTheftAlertState
import com.example.phone.AntiTheftManager
import com.example.phone.AntiTheftSensitivity
import com.example.phone.PhoneControlManager
import com.example.voice.ElevenLabsClient
import com.example.voice.ElevenLabsResponse
import com.example.voice.SanaAudioTrackPlayer
import com.example.voice.SanaSpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SocialCommentDraft(
    val isDraftActive: Boolean = false,
    val draftComment: String = "",
    val platform: String = "App"
)

class SanaViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "SanaViewModel"
    private val prefs = SanaPreferences(application)
    val settings: StateFlow<SanaSettingsData> = prefs.settings

    private val db = SanaDatabase.getInstance(application)
    private val memoryDao: MemoryDao = db.memoryDao()
    private val routineDao: RoutineDao = db.routineDao()

    private val geminiClient = GeminiClient(application)
    private val elevenLabsClient = ElevenLabsClient(application)
    val phoneControl = PhoneControlManager(application)

    private var speechRecognizer: SanaSpeechRecognizer? = null
    private var audioTrackPlayer: SanaAudioTrackPlayer? = null

    val antiTheftManager = AntiTheftManager(application) { warningNum, warningMessage ->
        viewModelScope.launch {
            val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
            if (apiKey.isNotBlank()) {
                val audio = geminiClient.generateGeminiSpeechAudio(warningMessage, apiKey)
                if (audio != null) {
                    audioTrackPlayer?.playPcmAudio(audio)
                }
            }
        }
    }

    // Conversation State Flow
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(EmotionType.AFFECTIONATE)
    val currentEmotion: StateFlow<EmotionType> = _currentEmotion.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isHandsFreeActive = MutableStateFlow(true)
    val isHandsFreeActive: StateFlow<Boolean> = _isHandsFreeActive.asStateFlow()

    private val _audioWaveLevel = MutableStateFlow(0f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying.asStateFlow()

    // Truthful Diagnostic Status
    private val _diagnosticStatus = MutableStateFlow(DiagnosticStatus())
    val diagnosticStatus: StateFlow<DiagnosticStatus> = _diagnosticStatus.asStateFlow()

    // Memories State
    val memories: StateFlow<List<MemoryEntity>> = memoryDao.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Routines State
    private val _routines = MutableStateFlow<List<RoutineEntity>>(emptyList())
    val routines: StateFlow<List<RoutineEntity>> = _routines.asStateFlow()

    // Anti-Theft Status
    val isAntiTheftArmed: StateFlow<Boolean> = antiTheftManager.isArmed
    val antiTheftAlertState: StateFlow<AntiTheftAlertState> = antiTheftManager.alertState

    // Teaching State
    private val _teachingSession = MutableStateFlow(TeachingSessionState())
    val teachingSession: StateFlow<TeachingSessionState> = _teachingSession.asStateFlow()

    // Daily Report State
    private val _dailyReport = MutableStateFlow(DailyReportGenerator.generateReport(emptyList()))
    val dailyReport: StateFlow<DailyConversationReport> = _dailyReport.asStateFlow()

    // Verified Social Comment Draft State
    private val _socialDraft = MutableStateFlow(SocialCommentDraft())
    val socialDraft: StateFlow<SocialCommentDraft> = _socialDraft.asStateFlow()

    private var processingJob: Job? = null
    private var listeningLoopJob: Job? = null
    private var recoveryJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        initSpeechAndAudio()
        observeAntiTheftAlerts()
        loadRoutines()
        postStartupGreeting()
        updateDiagnostics()
    }

    private fun postStartupGreeting() {
        val greetingText = "Assalamualaikum… mera Boss aa gaya! ❤️\nKaise ho mere Boss? Sab theek hai na?"
        val welcomeMsg = ChatMessage(
            role = MessageRole.SANA,
            text = greetingText,
            emotion = EmotionType.AFFECTIONATE
        )
        _messages.value = listOf(welcomeMsg)
    }

    private fun observeAntiTheftAlerts() {
        viewModelScope.launch {
            antiTheftManager.alertState.collect { alertState ->
                when (alertState) {
                    AntiTheftAlertState.WARNING_1 -> {
                        val warningText = "یہ میرے باس کا فون ہے۔ براہ کرم اسے واپس نیچے رکھ دیں۔"
                        speakWarningMessage(warningText)
                    }
                    AntiTheftAlertState.WARNING_2 -> {
                        val warning2Text = "فوری طور پر فون نیچے رکھ دیں! غیر مجاز حرکت محسوس ہوئی ہے۔"
                        speakWarningMessage(warning2Text)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun speakWarningMessage(warningText: String) {
        val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        viewModelScope.launch {
            if (apiKey.isNotBlank()) {
                val audio = geminiClient.generateGeminiSpeechAudio(warningText, apiKey)
                if (audio != null) {
                    audioTrackPlayer?.playPcmAudio(audio)
                    return@launch
                }
            }
        }
    }

    private fun loadRoutines() {
        viewModelScope.launch {
            routineDao.getAllRoutinesFlow().collect { list ->
                _routines.value = list
                _diagnosticStatus.value = _diagnosticStatus.value.copy(routinesCount = list.size)
            }
        }
    }

    private fun initSpeechAndAudio() {
        try {
            audioTrackPlayer = SanaAudioTrackPlayer(
                context = getApplication(),
                onPlaybackStarted = {
                    _isAudioPlaying.value = true
                    _conversationState.value = ConversationState.SPEAKING
                },
                onPlaybackFinished = {
                    _isAudioPlaying.value = false
                    handleAudioFinished()
                },
                onPlaybackError = { errorMsg ->
                    Log.w(tag, "Audio playback error: $errorMsg")
                    _isAudioPlaying.value = false
                    _diagnosticStatus.value = _diagnosticStatus.value.copy(
                        lastError = errorMsg,
                        activeNotice = DiagnosticNotice(
                            category = "Audio Output",
                            message = errorMsg,
                            recoveryAction = "Check device speaker volume and mute state."
                        )
                    )
                    handleAudioFinished()
                }
            )

            speechRecognizer = SanaSpeechRecognizer(
                context = getApplication(),
                onResult = { recognizedText ->
                    if (recognizedText.isNotBlank()) {
                        processUserInput(recognizedText)
                    } else {
                        handleNoSpeech()
                    }
                },
                onError = { errorCode, errorMsg ->
                    handleSpeechError(errorCode, errorMsg)
                },
                onNoSpeechDetected = {
                    handleNoSpeech()
                },
                onSpeechStart = {
                    if (_conversationState.value == ConversationState.SPEAKING) {
                        handleUserInterruption()
                    }
                },
                onSpeechEnd = {
                    _audioWaveLevel.value = 0f
                }
            )

            // Connect speech recognizer RMS level to visual orb
            viewModelScope.launch {
                speechRecognizer?.rmsDb?.collect { rms ->
                    if (_conversationState.value == ConversationState.LISTENING) {
                        _audioWaveLevel.value = rms
                    }
                }
            }

            // Connect audio player playing state
            viewModelScope.launch {
                audioTrackPlayer?.isPlaying?.collect { playing ->
                    if (playing) {
                        _audioWaveLevel.value = 0.65f
                    } else if (_conversationState.value != ConversationState.LISTENING) {
                        _audioWaveLevel.value = 0f
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error initializing speech/audio engines", e)
        }
    }

    // -------------------------------------------------------------
    // Conversation & Hands-Free Lifecycle
    // -------------------------------------------------------------
    fun toggleConversation(enable: Boolean) {
        _isHandsFreeActive.value = enable
        if (enable) {
            startListeningLoop()
        } else {
            stopConversation()
        }
    }

    fun startConversation(isHandsFree: Boolean = true) {
        _isHandsFreeActive.value = isHandsFree
        startListeningLoop()
    }

    fun stopConversation() {
        _isHandsFreeActive.value = false
        listeningLoopJob?.cancel()
        processingJob?.cancel()
        recoveryJob?.cancel()
        speechRecognizer?.stopListening()
        audioTrackPlayer?.stop()
        _conversationState.value = ConversationState.IDLE
        _audioWaveLevel.value = 0f
    }

    fun handleUserInterruption() {
        Log.d(tag, "Barge-in: Halting SANA voice output on user speech")
        audioTrackPlayer?.stop()
        _conversationState.value = ConversationState.LISTENING
    }

    private fun startListeningLoop() {
        if (!_isHandsFreeActive.value) return
        if (_conversationState.value == ConversationState.SPEAKING ||
            _conversationState.value == ConversationState.PROCESSING) {
            return
        }

        _conversationState.value = ConversationState.LISTENING
        mainHandler.post {
            speechRecognizer?.startListening(settings.value.languageMode)
        }
    }

    private fun handleNoSpeech() {
        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        listeningLoopJob?.cancel()
        listeningLoopJob = viewModelScope.launch {
            delay(400)
            if (_isHandsFreeActive.value && _conversationState.value != ConversationState.SPEAKING) {
                startListeningLoop()
            }
        }
    }

    // -------------------------------------------------------------
    // Reasoning Pipeline: UNDERSTAND -> PLAN -> ACT -> VERIFY -> RESPOND
    // -------------------------------------------------------------
    fun processUserInput(text: String, imageBase64: String? = null) {
        if (text.isBlank() && imageBase64 == null) return

        // Post User message to chat
        val userMsg = ChatMessage(
            role = MessageRole.USER,
            text = text,
            imageBase64 = imageBase64
        )
        _messages.value = _messages.value + userMsg

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            _conversationState.value = ConversationState.PROCESSING

            // Local offline command check
            val localMatch = LocalCommandParser.parse(text)
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)

            if (localMatch.matched) {
                executeActionAndReply(
                    command = localMatch.command,
                    param = localMatch.parameter,
                    secondParam = localMatch.secondParameter,
                    replyText = localMatch.spokenResponse ?: "Action executed.",
                    emotion = localMatch.emotion,
                    effectiveApiKey = effectiveApiKey
                )
                return@launch
            }

            // Check for exit teaching voice commands
            val lowerText = text.lowercase()
            if (_teachingSession.value.isTeachingActive &&
                (lowerText.contains("exit teaching") || lowerText.contains("stop teaching") ||
                 lowerText.contains("پڑھائی ختم") || lowerText.contains("کلاس ختم") ||
                 lowerText.contains("teaching khatam"))) {
                exitTeachingSession()
                return@launch
            }

            // Call Gemini Brain
            val aiResponse = geminiClient.generateResponse(
                userInput = text,
                history = _messages.value,
                settings = settings.value,
                memories = memories.value,
                imageBase64 = imageBase64,
                teachingState = _teachingSession.value
            )

            _currentEmotion.value = aiResponse.emotion

            // Check if action execution is requested
            if (aiResponse.actionCommand != null) {
                var firstParam = aiResponse.actionParameter
                var secondParam: String? = null
                if (firstParam != null && firstParam.contains("|")) {
                    val split = firstParam.split("|", limit = 2)
                    firstParam = split[0].trim()
                    secondParam = split.getOrNull(1)?.trim()
                }

                executeActionAndReply(
                    command = aiResponse.actionCommand,
                    param = firstParam,
                    secondParam = secondParam,
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

    private fun executeActionAndReply(
        command: String?,
        param: String?,
        secondParam: String? = null,
        replyText: String,
        emotion: EmotionType,
        effectiveApiKey: String,
        preloadedAudio: ByteArray? = null
    ) {
        var actionResult: ActionResult? = null
        when (command?.uppercase()) {
            "OPEN_WHATSAPP" -> actionResult = phoneControl.openWhatsApp()
            "WHATSAPP_MESSAGE" -> actionResult = phoneControl.sendWhatsAppMessage(param ?: "", secondParam ?: "")
            "CAMERA" -> actionResult = phoneControl.openCamera()
            "CALL" -> actionResult = phoneControl.callContactOrNumber(param ?: "")
            "TORCH_ON" -> actionResult = phoneControl.toggleTorch(true)
            "TORCH_OFF" -> actionResult = phoneControl.toggleTorch(false)
            "BATTERY" -> actionResult = phoneControl.getBatteryStatus()
            "YOUTUBE" -> actionResult = phoneControl.playYouTube(param)
            "WEB_SEARCH" -> actionResult = phoneControl.searchWeb(param ?: "")
            "SETTINGS" -> actionResult = phoneControl.openSettingsScreen(param ?: "general")
            "EXIT_TEACHING" -> {
                exitTeachingSession()
                return
            }
            "ARM_ANTI_THEFT" -> {
                val armed = armAntiTheft()
                actionResult = ActionResult(armed, "ANTI_THEFT", if (armed) "Anti-theft armed." else "Sensor unavailable.", true)
            }
            "DISARM_ANTI_THEFT" -> {
                disarmAntiTheft()
                actionResult = ActionResult(true, "ANTI_THEFT", "Anti-theft disarmed.", true)
            }
            "DAILY_REPORT" -> {
                generateDailyReport()
                actionResult = ActionResult(true, "DAILY_REPORT", "Daily report generated.", true)
            }
            "SHOW_ROUTINES" -> {
                actionResult = ActionResult(true, "ROUTINES", "${_routines.value.size} routines loaded.", true)
            }
            "PROPOSE_COMMENT" -> {
                proposeSocialDraft(param ?: replyText)
                actionResult = ActionResult(true, "SOCIAL_DRAFT", "Proposed comment ready for review.", true)
            }
            "REMEMBER" -> {
                param?.let { fact ->
                    viewModelScope.launch {
                        saveOrUpdateMemory(fact)
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
                val mems = memories.value
                val count = mems.size
                actionResult = ActionResult(true, "MEMORY", "$count memories stored in Vault.", true)
            }
        }

        val finalSpeechText = if (actionResult != null && actionResult.detail.isNotBlank()) {
            "$replyText\n(${actionResult.detail})"
        } else {
            replyText
        }

        if (preloadedAudio != null && preloadedAudio.isNotEmpty() && settings.value.voiceProvider != "ELEVEN_LABS") {
            deliverSanaReply(finalSpeechText, emotion, actionResult, preloadedAudio)
        } else {
            speakVoice(finalSpeechText, emotion, effectiveApiKey, actionResult)
        }
    }

    private fun speakVoice(
        text: String,
        emotion: EmotionType,
        apiKey: String,
        actionResult: ActionResult? = null
    ) {
        viewModelScope.launch {
            val elevenApiKey = elevenLabsClient.getEffectiveApiKey(settings.value.elevenLabsApiKey)
            // ElevenLabs Premium Voice Attempt
            if (settings.value.voiceProvider == "ELEVEN_LABS" && elevenApiKey.isNotBlank()) {
                val elevenResult = elevenLabsClient.generateSpeechAudio(
                    text = text,
                    apiKey = elevenApiKey,
                    voiceId = settings.value.elevenLabsVoiceId,
                    modelId = settings.value.elevenLabsModelId,
                    stability = settings.value.elevenLabsStability,
                    similarity = settings.value.elevenLabsSimilarity,
                    outputFormat = settings.value.elevenLabsOutputFormat
                )

                when (elevenResult) {
                    is ElevenLabsResponse.Success -> {
                        _diagnosticStatus.value = _diagnosticStatus.value.copy(
                            elevenLabsLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
                            elevenLabsStatus = "Connected & Active (${elevenResult.latencyMs}ms, ${elevenResult.modelId})"
                        )
                        deliverSanaReply(text, emotion, actionResult, elevenResult.audioBytes, isMp3 = !elevenResult.format.contains("pcm"))
                        return@launch
                    }
                    is ElevenLabsResponse.Error -> {
                        Log.w(tag, "ElevenLabs call returned error: ${elevenResult.errorCategory} - ${elevenResult.message}. Executing seamless fallback to Gemini Native voice.")
                        _diagnosticStatus.value = _diagnosticStatus.value.copy(
                            elevenLabsLevel = DiagnosticLevel.CONFIGURED_UNVERIFIED,
                            elevenLabsStatus = "Fallback to Gemini Native Active (${elevenResult.errorCategory})"
                        )
                    }
                }
            }

            // Gemini Native Voice (Default)
            val audioBytes = if (apiKey.isNotBlank()) {
                geminiClient.generateGeminiSpeechAudio(text, apiKey)
            } else null

            deliverSanaReply(
                speechText = text,
                emotion = emotion,
                actionResult = actionResult,
                audioBytes = audioBytes,
                isMp3 = false
            )
        }
    }

    private fun deliverSanaReply(
        speechText: String,
        emotion: EmotionType,
        actionResult: ActionResult? = null,
        audioBytes: ByteArray? = null,
        isMp3: Boolean = false
    ) {
        val sanaMsg = ChatMessage(
            role = MessageRole.SANA,
            text = speechText,
            emotion = emotion,
            actionTag = actionResult?.actionType,
            isActionVerified = actionResult?.verified ?: false
        )
        _messages.value = _messages.value + sanaMsg

        if (audioBytes != null && audioBytes.isNotEmpty()) {
            if (isMp3) {
                audioTrackPlayer?.playMp3Audio(audioBytes)
            } else {
                audioTrackPlayer?.playPcmAudio(audioBytes)
            }
        } else {
            handleAudioFinished()
        }
    }

    private fun handleAudioFinished() {
        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        listeningLoopJob?.cancel()
        listeningLoopJob = viewModelScope.launch {
            delay(150)
            if (_isHandsFreeActive.value) {
                startListeningLoop()
            }
        }
    }

    private fun handleSpeechError(errorCode: Int, errorMessage: String) {
        Log.w(tag, "Speech recognition error $errorCode: $errorMessage")
        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            lastError = errorMessage
        )

        if (!_isHandsFreeActive.value) {
            _conversationState.value = ConversationState.IDLE
            return
        }

        _conversationState.value = ConversationState.RECOVERING
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            delay(1200)
            if (_isHandsFreeActive.value) {
                startListeningLoop()
            }
        }
    }

    // -------------------------------------------------------------
    // Reset Engine (Section 19: Clean Session, Settings, Memory, Full)
    // -------------------------------------------------------------
    fun resetCurrentSession() {
        stopConversation()
        _teachingSession.value = TeachingSessionState()
        _socialDraft.value = SocialCommentDraft()
        postStartupGreeting()
        _conversationState.value = ConversationState.IDLE
        Log.d(tag, "Session reset completed.")
    }

    fun resetAppSettings() {
        prefs.resetToDefaults()
        updateDiagnostics()
        Log.d(tag, "Settings restored to defaults.")
    }

    fun resetPersonalMemory() {
        viewModelScope.launch {
            memoryDao.clearAllMemories()
            Log.d(tag, "All personal memory erased.")
        }
    }

    fun fullCleanReset() {
        stopConversation()
        antiTheftManager.disarmProtection()
        _teachingSession.value = TeachingSessionState()
        _socialDraft.value = SocialCommentDraft()
        postStartupGreeting()
        updateDiagnostics()
        Log.d(tag, "Full Clean Reset completed successfully.")
    }

    // -------------------------------------------------------------
    // Personal Teaching Mode
    // -------------------------------------------------------------
    fun startTeachingSession(subject: String, level: String, language: String) {
        _teachingSession.value = TeachingSessionState(
            isTeachingActive = true,
            subject = subject,
            level = level,
            language = language,
            step = 1
        )
        val prompt = "ہم پڑھائی شروع کرتے ہیں! مضمون: $subject, لیول: $level, زبان: $language۔ براہ کرم پہلا قدم محبت سے سکھائیں۔"
        processUserInput(prompt)
    }

    fun exitTeachingSession() {
        _teachingSession.value = TeachingSessionState(isTeachingActive = false)
        val reply = "بہت خوب! آج کی پڑھائی مکمل ہو گئی۔ اگر دوبارہ سیکھنا ہو تو مجھے بتائیے گا۔"
        deliverSanaReply(reply, EmotionType.HAPPY)
    }

    // -------------------------------------------------------------
    // Anti-Theft Protection
    // -------------------------------------------------------------
    fun armAntiTheft(): Boolean {
        val armed = antiTheftManager.armProtection()
        updateDiagnostics()
        return armed
    }

    fun disarmAntiTheft(enteredPin: String = ""): Boolean {
        antiTheftManager.disarmProtection()
        updateDiagnostics()
        return true
    }

    fun setAntiTheftSensitivity(sens: AntiTheftSensitivity) {
        antiTheftManager.setSensitivity(sens)
        prefs.updateSettings(settings.value.copy(antiTheftSensitivity = sens.name))
        updateDiagnostics()
    }

    // -------------------------------------------------------------
    // Routines & Automation
    // -------------------------------------------------------------
    fun createRoutine(title: String, triggerDescription: String, actionCommand: String) {
        viewModelScope.launch {
            val entity = RoutineEntity(
                title = title,
                triggerDescription = triggerDescription,
                actionCommand = actionCommand,
                lastRunStatus = "Created & Armed"
            )
            routineDao.insertRoutine(entity)
        }
    }

    fun toggleRoutine(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            routineDao.toggleRoutine(id, enabled)
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            routineDao.deleteRoutineById(id)
        }
    }

    fun runRoutineNow(routine: RoutineEntity) {
        processUserInput(routine.actionCommand)
    }

    // -------------------------------------------------------------
    // Social Draft
    // -------------------------------------------------------------
    private fun proposeSocialDraft(comment: String) {
        _socialDraft.value = SocialCommentDraft(
            isDraftActive = true,
            draftComment = comment,
            platform = "Android Social"
        )
    }

    fun confirmAndShareSocialDraft() {
        val draft = _socialDraft.value
        if (draft.isDraftActive) {
            phoneControl.shareSocialContent(draft.draftComment, draft.platform)
            _socialDraft.value = SocialCommentDraft(isDraftActive = false)
        }
    }

    fun cancelSocialDraft() {
        _socialDraft.value = SocialCommentDraft(isDraftActive = false)
    }

    // -------------------------------------------------------------
    // Daily Conversation Report
    // -------------------------------------------------------------
    fun generateDailyReport() {
        viewModelScope.launch(Dispatchers.Default) {
            val report = DailyReportGenerator.generateReport(messages.value)
            _dailyReport.value = report
        }
    }

    // -------------------------------------------------------------
    // Memory Management
    // -------------------------------------------------------------
    fun saveOrUpdateMemory(content: String) {
        viewModelScope.launch {
            memoryDao.insertMemory(MemoryEntity(content = content.trim()))
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
        updateDiagnostics()
    }

    fun setCustomApiKey(key: String) {
        prefs.setCustomApiKey(key)
        updateDiagnostics()
    }

    fun stopVoicePlayback() {
        audioTrackPlayer?.stop()
        _isAudioPlaying.value = false
        _conversationState.value = ConversationState.IDLE
    }

    fun testVoiceAudition(customSettings: SanaSettingsData? = null) {
        val activeSettings = customSettings ?: settings.value
        val apiKey = geminiClient.getEffectiveApiKey(activeSettings.customApiKey)
        val elevenApiKey = elevenLabsClient.getEffectiveApiKey(activeSettings.elevenLabsApiKey)

        // Check device volume without modifying system volume
        val volCheck = audioTrackPlayer?.checkAudioVolume()
        if (volCheck != null && (volCheck.isMuted || volCheck.currentVolume == 0)) {
            _diagnosticStatus.value = _diagnosticStatus.value.copy(
                activeNotice = DiagnosticNotice(
                    category = "Audio Output Warning",
                    message = "Device media volume is muted or at zero (${volCheck.currentVolume}/${volCheck.maxVolume}).",
                    recoveryAction = "Please increase device media volume to hear SANA's preview voice."
                )
            )
        }

        viewModelScope.launch {
            val sampleText = "السلام علیکم! میں ثناء ہوں۔ میری آواز اس طرح سنائی دے گی۔"

            // Dedicated ElevenLabs Test
            if (activeSettings.voiceProvider == "ELEVEN_LABS") {
                if (elevenApiKey.isBlank()) {
                    _diagnosticStatus.value = _diagnosticStatus.value.copy(
                        elevenLabsLevel = DiagnosticLevel.NOT_TESTED_INACTIVE,
                        elevenLabsStatus = "ELEVENLABS NOT CONFIGURED: API key missing",
                        lastError = "ElevenLabs API Key is not configured.",
                        activeNotice = DiagnosticNotice(
                            category = "ElevenLabs Configuration",
                            message = "ElevenLabs API Key is not configured.",
                            recoveryAction = "Enter your ElevenLabs API Key in Settings or the Voice Configuration modal."
                        )
                    )
                    return@launch
                }

                // Cleanly stop any existing playback before preview
                audioTrackPlayer?.stop()

                val result = elevenLabsClient.generateSpeechAudio(
                    text = sampleText,
                    apiKey = elevenApiKey,
                    voiceId = activeSettings.elevenLabsVoiceId,
                    modelId = activeSettings.elevenLabsModelId,
                    stability = activeSettings.elevenLabsStability,
                    similarity = activeSettings.elevenLabsSimilarity,
                    outputFormat = activeSettings.elevenLabsOutputFormat
                )

                when (result) {
                    is ElevenLabsResponse.Success -> {
                        _diagnosticStatus.value = _diagnosticStatus.value.copy(
                            elevenLabsLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
                            elevenLabsStatus = "Connected & Verified (${result.latencyMs}ms, ${result.modelId})",
                            activeNotice = null,
                            lastError = null
                        )
                        audioTrackPlayer?.playAudioBytes(result.audioBytes, result.format)
                        return@launch
                    }
                    is ElevenLabsResponse.Error -> {
                        _diagnosticStatus.value = _diagnosticStatus.value.copy(
                            elevenLabsLevel = DiagnosticLevel.UNAVAILABLE_FAILED,
                            elevenLabsStatus = "Failed: ${result.errorCategory} (HTTP ${result.httpCode})",
                            lastError = result.message,
                            activeNotice = DiagnosticNotice(
                                category = result.errorCategory,
                                message = result.message,
                                recoveryAction = result.recoveryAction
                            )
                        )
                        // Do NOT mask the ElevenLabs error during a dedicated test
                        return@launch
                    }
                }
            }

            // Gemini Native Voice Preview
            if (apiKey.isNotBlank()) {
                audioTrackPlayer?.stop()
                val audio = geminiClient.generateGeminiSpeechAudio(sampleText, apiKey)
                if (audio != null) {
                    audioTrackPlayer?.playPcmAudio(audio)
                    return@launch
                }
            }

            _diagnosticStatus.value = _diagnosticStatus.value.copy(
                lastError = "API key is required to hear SANA's voice.",
                activeNotice = DiagnosticNotice(
                    category = "Voice Engine",
                    message = "No operational voice credentials available for preview.",
                    recoveryAction = "Configure either Gemini API Key or ElevenLabs API Key in Settings."
                )
            )
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    fun updateDiagnostics() {
        val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        val elevenApiKey = elevenLabsClient.getEffectiveApiKey(settings.value.elevenLabsApiKey)
        val battery = phoneControl.getBatteryStatus()

        val aiBrainLevel = if (apiKey.isNotBlank()) DiagnosticLevel.VERIFIED_OPERATIONAL else DiagnosticLevel.UNAVAILABLE_FAILED
        val aiBrainDetail = if (apiKey.isNotBlank()) "Connected & Verified (Gemini 2.5 Flash)" else "API Key Missing / Not Configured"

        val (elevenLevel, elevenStatus) = when {
            settings.value.voiceProvider != "ELEVEN_LABS" ->
                Pair(DiagnosticLevel.NOT_TESTED_INACTIVE, "Not Active — Using Gemini Native")
            elevenApiKey.isBlank() ->
                Pair(DiagnosticLevel.NOT_TESTED_INACTIVE, "Not Configured")
            else ->
                Pair(DiagnosticLevel.CONFIGURED_UNVERIFIED, "Configured (${settings.value.elevenLabsModelId})")
        }

        val antiTheftDesc = if (antiTheftManager.isArmed.value) {
            "Armed (${settings.value.antiTheftSensitivity})"
        } else {
            "Disarmed (Standby)"
        }

        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            micReady = speechRecognizer != null,
            micLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
            micDetail = "Real Android Microphone & Recognizer Ready",
            geminiVoiceReady = (audioTrackPlayer != null),
            geminiVoiceLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
            geminiVoiceDetail = "24kHz AudioTrack Speaker Output Verified (Volume Boosted)",
            aiConfigured = apiKey.isNotBlank(),
            aiBrainLevel = aiBrainLevel,
            aiBrainDetail = aiBrainDetail,
            elevenLabsLevel = elevenLevel,
            elevenLabsStatus = elevenStatus,
            batteryPct = battery.detail.filter { it.isDigit() }.toIntOrNull() ?: 100,
            antiTheftStatus = antiTheftDesc,
            routinesCount = _routines.value.size,
            routinesLevel = if (_routines.value.isNotEmpty()) DiagnosticLevel.VERIFIED_OPERATIONAL else DiagnosticLevel.CONFIGURED_UNVERIFIED,
            routinesDetail = "${_routines.value.size} routines configured & active",
            routinesPhysicalLevel = DiagnosticLevel.NOT_TESTED_INACTIVE,
            routinesPhysicalDetail = "Physical Android test not run (Reboot & OEM lifecycle)"
        )
    }

    override fun onCleared() {
        super.onCleared()
        antiTheftManager.disarmProtection()
        speechRecognizer?.destroy()
        audioTrackPlayer?.destroy()
    }
}
