package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SanaSettingsData(
    val languageMode: String = "auto", // "auto", "ur", "en", "roman_ur"
    val loveModeEnabled: Boolean = false,
    val jealousyLevel: String = "OFF", // "OFF", "Light", "Playful"
    val ttsSpeed: Float = 1.05f,
    val ttsPitch: Float = 1.15f,
    val continuousHandsFree: Boolean = true,
    val customApiKey: String = "",
    val confirmSensitiveActions: Boolean = true,
    // Anti-Theft Protection
    val antiTheftEnabled: Boolean = false,
    val antiTheftSensitivity: String = "MEDIUM", // "LOW", "MEDIUM", "HIGH"
    val antiTheftPin: String = "1234",
    // Voice Provider & ElevenLabs Configuration
    val voiceProvider: String = "GEMINI_NATIVE", // "GEMINI_NATIVE", "ELEVEN_LABS"
    val elevenLabsApiKey: String = "",
    val elevenLabsModelId: String = "eleven_multilingual_v2",
    val elevenLabsVoiceId: String = "21m00Tcm4TlvDq8ikWAM",
    val elevenLabsStability: Float = 0.65f,
    val elevenLabsSimilarity: Float = 0.80f,
    val elevenLabsOutputFormat: String = "mp3_44100_128"
)

class SanaPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("sana_v4_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SanaSettingsData> = _settings.asStateFlow()

    private fun loadSettings(): SanaSettingsData {
        return SanaSettingsData(
            languageMode = prefs.getString("language_mode", "auto") ?: "auto",
            loveModeEnabled = prefs.getBoolean("love_mode", false),
            jealousyLevel = prefs.getString("jealousy_level", "OFF") ?: "OFF",
            ttsSpeed = prefs.getFloat("tts_speed", 1.05f),
            ttsPitch = prefs.getFloat("tts_pitch", 1.15f),
            continuousHandsFree = prefs.getBoolean("continuous_hands_free", true),
            customApiKey = prefs.getString("custom_api_key", "") ?: "",
            confirmSensitiveActions = prefs.getBoolean("confirm_sensitive", true),
            antiTheftEnabled = prefs.getBoolean("anti_theft_enabled", false),
            antiTheftSensitivity = prefs.getString("anti_theft_sensitivity", "MEDIUM") ?: "MEDIUM",
            antiTheftPin = prefs.getString("anti_theft_pin", "1234") ?: "1234",
            voiceProvider = prefs.getString("voice_provider", "GEMINI_NATIVE") ?: "GEMINI_NATIVE",
            elevenLabsApiKey = prefs.getString("eleven_labs_api_key", "") ?: "",
            elevenLabsModelId = prefs.getString("eleven_labs_model_id", "eleven_multilingual_v2") ?: "eleven_multilingual_v2",
            elevenLabsVoiceId = prefs.getString("eleven_labs_voice_id", "21m00Tcm4TlvDq8ikWAM") ?: "21m00Tcm4TlvDq8ikWAM",
            elevenLabsStability = prefs.getFloat("eleven_labs_stability", 0.65f),
            elevenLabsSimilarity = prefs.getFloat("eleven_labs_similarity", 0.80f),
            elevenLabsOutputFormat = prefs.getString("eleven_labs_output_format", "mp3_44100_128") ?: "mp3_44100_128"
        )
    }

    fun updateSettings(newSettings: SanaSettingsData) {
        prefs.edit().apply {
            putString("language_mode", newSettings.languageMode)
            putBoolean("love_mode", newSettings.loveModeEnabled)
            putString("jealousy_level", newSettings.jealousyLevel)
            putFloat("tts_speed", newSettings.ttsSpeed)
            putFloat("tts_pitch", newSettings.ttsPitch)
            putBoolean("continuous_hands_free", newSettings.continuousHandsFree)
            putString("custom_api_key", newSettings.customApiKey)
            putBoolean("confirm_sensitive", newSettings.confirmSensitiveActions)
            putBoolean("anti_theft_enabled", newSettings.antiTheftEnabled)
            putString("anti_theft_sensitivity", newSettings.antiTheftSensitivity)
            putString("anti_theft_pin", newSettings.antiTheftPin)
            putString("voice_provider", newSettings.voiceProvider)
            putString("eleven_labs_api_key", newSettings.elevenLabsApiKey)
            putString("eleven_labs_model_id", newSettings.elevenLabsModelId)
            putString("eleven_labs_voice_id", newSettings.elevenLabsVoiceId)
            putFloat("eleven_labs_stability", newSettings.elevenLabsStability)
            putFloat("eleven_labs_similarity", newSettings.elevenLabsSimilarity)
            putString("eleven_labs_output_format", newSettings.elevenLabsOutputFormat)
            apply()
        }
        _settings.value = newSettings
    }

    fun resetToDefaults() {
        val defaults = SanaSettingsData()
        updateSettings(defaults)
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString("custom_api_key", key).apply()
        _settings.value = _settings.value.copy(customApiKey = key)
    }

    fun setLoveMode(enabled: Boolean, jealousyLevel: String = _settings.value.jealousyLevel) {
        prefs.edit()
            .putBoolean("love_mode", enabled)
            .putString("jealousy_level", jealousyLevel)
            .apply()
        _settings.value = _settings.value.copy(loveModeEnabled = enabled, jealousyLevel = jealousyLevel)
    }
}
