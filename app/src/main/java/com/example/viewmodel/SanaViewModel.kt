package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SanaApplication
import com.example.ai.GeminiClient
import com.example.ai.LocalCommandParser
import com.example.data.DailyConversationReport
import com.example.data.DailyReportGenerator
import com.example.data.MemoryEntity
import com.example.data.RoutineEntity
import com.example.data.SanaDatabase
import com.example.data.SanaPreferences
import com.example.data.SanaSettingsData
import com.example.model.ActionResult
import com.example.model.ChatMessage
import com.example.model.ConversationState
import com.example.model.DiagnosticStatus
import com.example.model.EmotionType
import com.example.model.MessageRole
import com.example.model.SocialDraftState
import com.example.model.TeachingSessionState
import com.example.phone.AntiTheftAlertState
import com.example.phone.AntiTheftManager
import com.example.phone.AntiTheftSensitivity
import com.example.phone.PhoneControlManager
import com.example.service.SanaNotificationListenerService
import com.example.voice.ElevenLabsClient
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

    // Core managers
    private val prefs = SanaPreferences(context)
    val settings: StateFlow<SanaSettingsData> = prefs.settings

    private val geminiClient = GeminiClient(context)
    private val phoneControl = PhoneControlManager(context)
    private val memoryDao = SanaDatabase.getInstance(context).memoryDao()
    private val routineDao = SanaDatabase.getInstance(context).routineDao()
    private val elevenLabsClient = ElevenLabsClient(context)

    // Anti-Theft & Owner Protection Manager (Section 15)
    private val antiTheftManager = AntiTheftManager(
        context = context,
        onTriggerSpokenWarning = { warningNum, message ->
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
            speakVoice(message, EmotionType.ANGRY, effectiveApiKey)
        }
    )
    val antiTheftAlertState: StateFlow<AntiTheftAlertState> = antiTheftManager.alertState
    val isAntiTheftArmed: StateFlow<Boolean> = antiTheftManager.isArmed

    // State flows
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _currentEmotion = MutableStateFlow(EmotionType.AFFECTIONATE)
    val currentEmotion: StateFlow<EmotionType> = _currentEmotion.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _diagnosticStatus = MutableStateFlow(DiagnosticStatus())
    val diagnosticStatus: StateFlow<DiagnosticStatus> = _diagnosticStatus.asStateFlow()

    private val _memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val memories: StateFlow<List<MemoryEntity>> = _memories.asStateFlow()

    // Automation & Routines (Section 18)
    private val _routines = MutableStateFlow<List<RoutineEntity>>(emptyList())
    val routines: StateFlow<List<RoutineEntity>> = _routines.asStateFlow()

    // Personal Teaching Mode (Test 3)
    private val _teachingSession = MutableStateFlow(TeachingSessionState())
    val teachingSession: StateFlow<TeachingSessionState> = _teachingSession.asStateFlow()

    // Daily Conversation Report (Section 17)
    private val _dailyReport = MutableStateFlow(DailyReportGenerator.generateReport(emptyList()))
    val dailyReport: StateFlow<DailyConversationReport> = _dailyReport.asStateFlow()

    // Verified Social Interaction (Section 14)
    private val _socialDraft = MutableStateFlow(SocialDraftState())
    val socialDraft: StateFlow<SocialDraftState> = _socialDraft.asStateFlow()

    // Continuous auto-conversation master switch
    private val _isHandsFreeActive = MutableStateFlow(false)
    val isHandsFreeActive: StateFlow<Boolean> = _isHandsFreeActive.asStateFlow()

    // Live audio wave amplitude (0f - 1f)
    private val _audioWaveLevel = MutableStateFlow(0f)
    val audioWaveLevel: StateFlow<Float> = _audioWaveLevel.asStateFlow()

    // Real Voice Engine (Zero Android TTS)
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
        loadRoutines()
        initVoiceEngine()
        updateDiagnostics()

        // Welcome greeting (Startup greeting requirement)
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

    private fun loadRoutines() {
        viewModelScope.launch {
            routineDao.getAllRoutinesFlow().collect { list ->
                if (list.isEmpty()) {
                    // Seed standard default routines (Section 18)
                    val defaultRoutines = listOf(
                        RoutineEntity(title = "Morning Briefing", triggerDescription = "08:00 AM Daily", actionCommand = "BATTERY_CHECK", isEnabled = true),
                        RoutineEntity(title = "Battery & Health Check", triggerDescription = "Every 4 Hours", actionCommand = "BATTERY_CHECK", isEnabled = true),
                        RoutineEntity(title = "Study & Focus Reminder", triggerDescription = "06:00 PM Daily", actionCommand = "STUDY_REMINDER", isEnabled = false)
                    )
                    defaultRoutines.forEach { routineDao.insertRoutine(it) }
                } else {
                    _routines.value = list
                    _diagnosticStatus.value = _diagnosticStatus.value.copy(routinesCount = list.size)
                }
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

        // Dedicated PCM & MP3 AudioTrack player
        audioTrackPlayer = SanaAudioTrackPlayer(
            context = context,
            onPlaybackStarted = {
                _conversationState.value = ConversationState.SPEAKING
                _diagnosticStatus.value = _diagnosticStatus.value.copy(geminiVoiceReady = true)
            },
            onPlaybackFinished = {
                handleAudioFinished()
            },
            onPlaybackError = { errorMsg ->
                Log.e(tag, "Audio playback error: $errorMsg")
                _diagnosticStatus.value = _diagnosticStatus.value.copy(lastError = errorMsg)
                handleAudioFinished()
            }
        )
    }

    /**
     * Start conversation session.
     */
    fun startConversation(isHandsFree: Boolean = true) {
        _isHandsFreeActive.value = isHandsFree
        com.example.service.SanaVoiceService.start(context)

        // Speak the mandatory startup greeting once if not yet spoken
        if (!hasSpokenStartupGreeting) {
            hasSpokenStartupGreeting = true
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
            speakVoice(STARTUP_GREETING_TEXT, EmotionType.AFFECTIONATE, effectiveApiKey)
            return
        }

        startListeningLoop()
    }

    /**
     * Immediately stops the voice session and cancels active recordings and audio.
     */
    fun stopConversation() {
        Log.i(tag, "Explicit STOP triggered by user.")
        _isHandsFreeActive.value = false
        listeningLoopJob?.cancel()
        listeningLoopJob = null
        recoveryJob?.cancel()
        recoveryJob = null
        processingJob?.cancel()
        processingJob = null

        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        audioTrackPlayer?.stop()

        _audioWaveLevel.value = 0f
        _conversationState.value = ConversationState.STOPPED
        com.example.service.SanaVoiceService.stop(context)
    }

    /**
     * Handles instant user interruption / barge-in while SANA is speaking.
     */
    fun handleUserInterruption() {
        Log.i(tag, "User interruption triggered. Instantly cutting off SANA audio.")
        _conversationState.value = ConversationState.INTERRUPTED
        audioTrackPlayer?.stop()

        viewModelScope.launch {
            delay(120)
            if (_isHandsFreeActive.value) {
                startListeningLoop()
            } else {
                _conversationState.value = ConversationState.IDLE
            }
        }
    }

    fun toggleConversation(enable: Boolean) {
        if (enable) {
            startConversation(true)
        } else {
            stopConversation()
        }
    }

    private fun startListeningLoop() {
        if (!_isHandsFreeActive.value) return

        recoveryJob?.cancel()
        listeningLoopJob?.cancel()

        listeningLoopJob = viewModelScope.launch {
            try {
                audioTrackPlayer?.stop()
                _conversationState.value = ConversationState.LISTENING
                speechRecognizer?.startListening()
            } catch (e: Exception) {
                Log.e(tag, "Failed to start listening loop", e)
                handleSpeechError(-1, e.localizedMessage ?: "Mic start failed")
            }
        }
    }

    private fun handleSpeechRecognized(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            handleNoSpeechDetected()
            return
        }

        Log.i(tag, "User spoken transcript: '$trimmed'")
        _audioWaveLevel.value = 0f

        // Check for STOP commands
        val lower = trimmed.lowercase()
        if (lower == "stop" || lower == "ruko" || lower == "ruko sana" ||
            lower == "chup" || lower == "bas" || lower == "روکو" || lower == "بس") {
            stopConversation()
            return
        }

        processUserInput(trimmed)
    }

    private fun handleNoSpeechDetected() {
        Log.d(tag, "No speech detected in turn")
        _audioWaveLevel.value = 0f

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

    /**
     * Main reasoning pipeline: UNDERSTAND -> PLAN -> ACT -> VERIFY -> RESPOND
     */
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

            // If local command matched (e.g. WhatsApp, Torch, Battery, Anti-Theft, Daily Report, Routines)
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
                memories = _memories.value,
                imageBase64 = imageBase64,
                teachingState = _teachingSession.value
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
                val list = _memories.value
                val memoryListText = if (list.isEmpty()) {
                    "ابھی آپ کی کوئی یادداشت محفوظ نہیں ہے۔"
                } else {
                    "آپ کی یادداشتیں:\n" + list.joinToString("\n") { it.content }
                }
                speakVoice(memoryListText, EmotionType.HAPPY, effectiveApiKey)
                return
            }
            "NOTIFICATIONS_SUMMARY" -> {
                val summary = SanaNotificationListenerService.getSummary()
                speakVoice(summary, EmotionType.HAPPY, effectiveApiKey)
                return
            }
        }

        val finalSpeechText = if (actionResult != null && !actionResult.success) {
            "$replyText (${actionResult.detail})"
        } else {
            replyText
        }

        if (preloadedAudio != null && preloadedAudio.isNotEmpty() && settings.value.voiceProvider != "ELEVEN_LABS") {
            deliverSanaReply(finalSpeechText, emotion, actionResult, preloadedAudio)
        } else {
            speakVoice(finalSpeechText, emotion, effectiveApiKey, actionResult)
        }
    }

    /**
     * Unified Voice Routing Engine:
     * - Checks settings.voiceProvider.
     * - If ELEVEN_LABS is selected and configured, synthesizes via ElevenLabs API and plays MP3.
     * - If ElevenLabs fails or is not configured, or if GEMINI_NATIVE is selected:
     *   Seamlessly synthesizes via Gemini and plays 24kHz PCM AudioTrack speaker output.
     * - ZERO Android TTS, zero fake speech.
     */
    private fun speakVoice(
        text: String,
        emotion: EmotionType,
        apiKey: String,
        actionResult: ActionResult? = null
    ) {
        viewModelScope.launch {
            // Check ElevenLabs option
            if (settings.value.voiceProvider == "ELEVEN_LABS" && settings.value.elevenLabsApiKey.isNotBlank()) {
                val elevenResult = elevenLabsClient.generateSpeechAudio(
                    text = text,
                    apiKey = settings.value.elevenLabsApiKey,
                    voiceId = settings.value.elevenLabsVoiceId
                )
                if (elevenResult != null) {
                    _diagnosticStatus.value = _diagnosticStatus.value.copy(
                        elevenLabsStatus = "Connected & Active (${elevenResult.latencyMs}ms)"
                    )
                    deliverSanaReply(text, emotion, actionResult, elevenResult.audioBytes, isMp3 = true)
                    return@launch
                } else {
                    Log.w(tag, "ElevenLabs call returned null. Executing seamless fallback to Gemini Native voice.")
                    _diagnosticStatus.value = _diagnosticStatus.value.copy(
                        elevenLabsStatus = "Fallback to Gemini Native Active"
                    )
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

    /**
     * Deliver SANA response: Spoken ONLY via real audio players. Never via Android TTS.
     */
    private fun deliverSanaReply(
        speechText: String,
        emotion: EmotionType,
        actionResult: ActionResult? = null,
        audioBytes: ByteArray? = null,
        isMp3: Boolean = false
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
            if (isMp3) {
                Log.d(tag, "Playing MP3 voice audio through phone speaker (${audioBytes.size} bytes)")
                audioTrackPlayer?.playMp3Audio(audioBytes)
            } else {
                Log.d(tag, "Playing Gemini PCM audio through phone speaker (${audioBytes.size} bytes)")
                audioTrackPlayer?.playPcmAudio(audioBytes)
            }
        } else {
            Log.w(tag, "Voice audio unavailable — TTS fallback is strictly disabled")
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

        listeningLoopJob?.cancel()
        listeningLoopJob = viewModelScope.launch {
            delay(150) // Crisp, natural short pause
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
    // Anti-Theft & Owner Protection (Section 15)
    // -------------------------------------------------------------

    fun armAntiTheft(): Boolean {
        val success = antiTheftManager.armProtection()
        prefs.updateSettings(settings.value.copy(antiTheftEnabled = success))
        updateDiagnostics()
        return success
    }

    fun disarmAntiTheft() {
        antiTheftManager.disarmProtection()
        prefs.updateSettings(settings.value.copy(antiTheftEnabled = false))
        updateDiagnostics()
    }

    fun setAntiTheftSensitivity(sens: AntiTheftSensitivity) {
        antiTheftManager.setSensitivity(sens)
        prefs.updateSettings(settings.value.copy(antiTheftSensitivity = sens.name))
    }

    fun testAntiTheftWarning(warningNumber: Int) {
        val msg = if (warningNumber == 1) AntiTheftManager.WARNING_1_MESSAGE else AntiTheftManager.WARNING_2_MESSAGE
        val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        speakVoice(msg, EmotionType.ANGRY, effectiveApiKey)
    }

    // -------------------------------------------------------------
    // Automation & Routines (Section 18)
    // -------------------------------------------------------------

    fun toggleRoutine(id: Long, isEnabled: Boolean) {
        viewModelScope.launch {
            routineDao.toggleRoutine(id, isEnabled)
        }
    }

    fun runRoutineNow(routine: RoutineEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            var status = "Completed"
            when (routine.actionCommand.uppercase()) {
                "BATTERY_CHECK" -> {
                    val battery = phoneControl.getBatteryStatus()
                    val response = "روٹین چیک: بیٹری ${battery.detail} پر ہے۔"
                    val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
                    speakVoice(response, EmotionType.HAPPY, apiKey)
                    status = "Success: ${battery.detail}"
                }
                "STUDY_REMINDER" -> {
                    val msg = "میرے باس! پڑھائی کا وقت ہو گیا ہے۔ کیا آپ ریاضی یا سائنس کا سیشن شروع کرنا چاہیں گے؟"
                    val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
                    speakVoice(msg, EmotionType.AFFECTIONATE, apiKey)
                    status = "Study Prompt Spoken"
                }
                else -> {
                    processUserInput("Execute routine ${routine.title}")
                    status = "Triggered via Brain"
                }
            }
            routineDao.updateLastRun(routine.id, now, status)
        }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch {
            routineDao.deleteRoutineById(id)
        }
    }

    fun createRoutine(title: String, trigger: String, action: String) {
        viewModelScope.launch {
            routineDao.insertRoutine(
                RoutineEntity(
                    title = title.trim(),
                    triggerDescription = trigger.trim(),
                    actionCommand = action.trim(),
                    isEnabled = true
                )
            )
        }
    }

    // -------------------------------------------------------------
    // Daily Conversation Report (Section 17)
    // -------------------------------------------------------------

    fun generateDailyReport(): DailyConversationReport {
        val report = DailyReportGenerator.generateReport(_messages.value)
        _dailyReport.value = report
        return report
    }

    // -------------------------------------------------------------
    // Verified Social Interaction (Section 14)
    // -------------------------------------------------------------

    fun proposeSocialDraft(comment: String, platform: String = "WhatsApp") {
        _socialDraft.value = SocialDraftState(
            isDraftActive = true,
            platform = platform,
            draftComment = comment,
            targetPostSummary = "Visible content reviewed by SANA"
        )
    }

    fun confirmAndShareSocialDraft() {
        val draft = _socialDraft.value
        if (draft.isDraftActive && draft.draftComment.isNotBlank()) {
            val result = phoneControl.shareSocialContent(draft.draftComment, draft.platform)
            _socialDraft.value = SocialDraftState(isDraftActive = false)
            val reply = if (result.success) "کمنٹ شیئر کر دیا گیا ہے۔" else "شیئرنگ میں خرابی پیش آئی۔"
            val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
            speakVoice(reply, EmotionType.HAPPY, apiKey)
        }
    }

    fun cancelSocialDraft() {
        _socialDraft.value = SocialDraftState(isDraftActive = false)
    }

    // -------------------------------------------------------------
    // Personal Teaching Mode (Test 3)
    // -------------------------------------------------------------

    fun startTeachingSession(
        subject: String = "Mathematics (ریاضی)",
        level: String = "Beginner (بنیادی)",
        language: String = "Pakistani Urdu (اردو)"
    ) {
        _teachingSession.value = TeachingSessionState(
            isTeachingActive = true,
            subject = subject,
            level = level,
            language = language,
            step = 1
        )
        val welcomeMsg = "جی بالکل! اب ہم ${subject} کا ذاتی سیشن شروع کر رہے ہیں۔ آپ کیا سمجھنا چاہتے ہیں؟"
        val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        speakVoice(welcomeMsg, EmotionType.HAPPY, effectiveApiKey)
    }

    fun exitTeachingSession() {
        if (_teachingSession.value.isTeachingActive) {
            _teachingSession.value = _teachingSession.value.copy(isTeachingActive = false)
            val exitMsg = "پڑھائی سیشن مکمل ہو گیا۔ میں عام اسسٹنٹ موڈ میں حاضر ہوں۔"
            val effectiveApiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
            speakVoice(exitMsg, EmotionType.HAPPY, effectiveApiKey)
        }
    }

    // -------------------------------------------------------------
    // Advanced Memory Management (Test 2)
    // -------------------------------------------------------------

    private suspend fun saveOrUpdateMemory(fact: String, category: String = "User") {
        val trimmed = fact.trim()
        if (trimmed.isBlank()) return

        val existing = _memories.value.find { mem ->
            val memLower = mem.content.lowercase()
            val factLower = trimmed.lowercase()
            (factLower.contains("favorite color") && memLower.contains("favorite color")) ||
            (factLower.contains("naam") && memLower.contains("naam")) ||
            (factLower.contains("name is") && memLower.contains("name is")) ||
            (factLower.contains("city") && memLower.contains("city")) ||
            (factLower.contains("age") && memLower.contains("age"))
        }

        if (existing != null) {
            memoryDao.updateMemory(existing.id, trimmed, System.currentTimeMillis())
        } else {
            memoryDao.insertMemory(MemoryEntity(content = trimmed, category = category))
        }
    }

    fun addManualMemory(fact: String, category: String = "User") {
        if (fact.isBlank()) return
        viewModelScope.launch {
            saveOrUpdateMemory(fact, category)
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

    fun testVoiceAudition() {
        val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        viewModelScope.launch {
            val sampleText = "السلام علیکم! میں ثناء ہوں۔ میری آواز اس طرح سنائی دے گی۔"
            if (settings.value.voiceProvider == "ELEVEN_LABS" && settings.value.elevenLabsApiKey.isNotBlank()) {
                val result = elevenLabsClient.generateSpeechAudio(
                    sampleText,
                    settings.value.elevenLabsApiKey,
                    settings.value.elevenLabsVoiceId
                )
                if (result != null) {
                    audioTrackPlayer?.playMp3Audio(result.audioBytes)
                    return@launch
                }
            }

            if (apiKey.isNotBlank()) {
                val audio = geminiClient.generateGeminiSpeechAudio(sampleText, apiKey)
                if (audio != null) {
                    audioTrackPlayer?.playPcmAudio(audio)
                    return@launch
                }
            }
            _diagnosticStatus.value = _diagnosticStatus.value.copy(
                lastError = "API key is required to hear SANA's voice."
            )
        }
    }

    fun clearChat() {
        _messages.value = emptyList()
    }

    fun updateDiagnostics() {
        val apiKey = geminiClient.getEffectiveApiKey(settings.value.customApiKey)
        val battery = phoneControl.getBatteryStatus()

        val elevenStatus = if (settings.value.voiceProvider != "ELEVEN_LABS") {
            "Not Active (Using Gemini Native)"
        } else if (settings.value.elevenLabsApiKey.isBlank()) {
            "Not Configured (Fallback Active)"
        } else {
            "Configured & Armed"
        }

        val antiTheftDesc = if (antiTheftManager.isArmed.value) {
            "Armed (${settings.value.antiTheftSensitivity})"
        } else {
            "Disarmed"
        }

        _diagnosticStatus.value = _diagnosticStatus.value.copy(
            micReady = speechRecognizer != null,
            geminiVoiceReady = (audioTrackPlayer != null),
            aiConfigured = apiKey.isNotBlank(),
            batteryPct = battery.detail.filter { it.isDigit() }.toIntOrNull() ?: 100,
            elevenLabsStatus = elevenStatus,
            antiTheftStatus = antiTheftDesc,
            routinesCount = _routines.value.size
        )
    }

    override fun onCleared() {
        super.onCleared()
        antiTheftManager.disarmProtection()
        speechRecognizer?.destroy()
        audioTrackPlayer?.destroy()
    }
}
