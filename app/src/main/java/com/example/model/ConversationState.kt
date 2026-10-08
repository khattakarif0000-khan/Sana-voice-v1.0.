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

data class DiagnosticStatus(
    val micReady: Boolean = false,
    val geminiVoiceReady: Boolean = false,
    val aiConfigured: Boolean = false,
    val networkAvailable: Boolean = true,
    val audioFocusGranted: Boolean = false,
    val batteryPct: Int = -1,
    val lastError: String? = null,
    val recoveryAttempt: Int = 0
)
