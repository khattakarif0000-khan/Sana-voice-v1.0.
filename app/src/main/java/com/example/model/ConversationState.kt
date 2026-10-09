package com.example.model

enum class ConversationState(val displayName: String) {
    IDLE("Ready"),
    LISTENING("Listening..."),
    PROCESSING("Active"),
    SPEAKING("Speaking"),
    INTERRUPTED("Interrupted"),
    RECOVERING("Self-Healing..."),
    STOPPED("Stopped"),
    ERROR("Error")
}

val ConversationState.label: String
    get() = this.displayName

enum class EmotionType(val label: String, val emoji: String) {
    NEUTRAL("Calm", "✨"),
    HAPPY("Happy", "😊"),
    SAD("Gentle & Caring", "🥺"),
    ANGRY("Patient & Calming", "🕊️"),
    FRUSTRATED("Supportive", "🤝"),
    STRESSED("Comforting", "🌸"),
    CONFUSED("Clear & Helpful", "💡"),
    EXCITED("Excited", "🎉"),
    TIRED("Warm & Soothing", "☕"),
    AFFECTIONATE("Affectionate", "💖"),
    PLAYFUL("Playful", "😏")
}

enum class MessageRole {
    USER,
    SANA,
    ASSISTANT,
    SYSTEM
}

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val emotion: EmotionType = EmotionType.NEUTRAL,
    val actionTag: String? = null,
    val isActionVerified: Boolean = false,
    val imageBase64: String? = null
)

data class ActionResult(
    val success: Boolean,
    val actionType: String,
    val detail: String,
    val verified: Boolean,
    val errorReason: String? = null
)

data class SanaAiResponse(
    val replyText: String,
    val emotion: EmotionType = EmotionType.AFFECTIONATE,
    val actionCommand: String? = null,
    val actionParameter: String? = null,
    val audioBytes: ByteArray? = null
)

enum class DiagnosticLevel(val label: String) {
    VERIFIED_OPERATIONAL("VERIFIED"),             // GREEN: actually operational & verified
    CONFIGURED_UNVERIFIED("CONFIGURED"),          // YELLOW: configured/available but not fully verified
    UNAVAILABLE_FAILED("FAILED / BLOCKED"),       // RED: unavailable, missing configuration, failed, or blocked
    NOT_TESTED_INACTIVE("NOT TESTED / INACTIVE") // GRAY: not tested / not configured
}

data class DiagnosticNotice(
    val category: String,
    val message: String,
    val recoveryAction: String
)

data class DiagnosticStatus(
    val micReady: Boolean = false,
    val micLevel: DiagnosticLevel = DiagnosticLevel.CONFIGURED_UNVERIFIED,
    val micDetail: String = "Initializing recognizer...",
    val geminiVoiceReady: Boolean = false,
    val geminiVoiceLevel: DiagnosticLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
    val geminiVoiceDetail: String = "24kHz AudioTrack Speaker Output Verified",
    val aiConfigured: Boolean = false,
    val aiBrainLevel: DiagnosticLevel = DiagnosticLevel.UNAVAILABLE_FAILED,
    val aiBrainDetail: String = "API Key Missing / Not Configured",
    val elevenLabsLevel: DiagnosticLevel = DiagnosticLevel.NOT_TESTED_INACTIVE,
    val elevenLabsStatus: String = "Not Configured / Inactive",
    val antiTheftLevel: DiagnosticLevel = DiagnosticLevel.CONFIGURED_UNVERIFIED,
    val antiTheftStatus: String = "Disarmed (Standby)",
    val routinesLevel: DiagnosticLevel = DiagnosticLevel.CONFIGURED_UNVERIFIED,
    val routinesCount: Int = 0,
    val routinesDetail: String = "Configured routines active",
    val routinesPhysicalLevel: DiagnosticLevel = DiagnosticLevel.NOT_TESTED_INACTIVE,
    val routinesPhysicalDetail: String = "Physical Android test not run (Reboot & OEM lifecycle)",
    val networkAvailable: Boolean = true,
    val networkLevel: DiagnosticLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
    val networkDetail: String = "Connected",
    val batteryPct: Int = -1,
    val batteryLevel: DiagnosticLevel = DiagnosticLevel.VERIFIED_OPERATIONAL,
    val batteryDetail: String = "Normal",
    val lastError: String? = null,
    val activeNotice: DiagnosticNotice? = null,
    val recoveryAttempt: Int = 0
)

data class TeachingSessionState(
    val isTeachingActive: Boolean = false,
    val subject: String = "Mathematics (ریاضی)",
    val level: String = "Beginner (بنیادی)",
    val language: String = "Pakistani Urdu (اردو)",
    val currentTopic: String? = null,
    val step: Int = 1
)

data class SocialDraftState(
    val isDraftActive: Boolean = false,
    val platform: String = "WhatsApp",
    val draftComment: String = "",
    val targetPostSummary: String = "")
