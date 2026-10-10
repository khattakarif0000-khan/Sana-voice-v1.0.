package com.example.voice

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class ElevenLabsAudioResult(
    val audioBytes: ByteArray,
    val latencyMs: Long,
    val modelId: String
)

sealed class ElevenLabsResponse {
    data class Success(
        val audioBytes: ByteArray,
        val format: String,
        val latencyMs: Long,
        val modelId: String,
        val voiceId: String
    ) : ElevenLabsResponse()

    data class Error(
        val httpCode: Int,
        val errorCategory: String,
        val message: String,
        val recoveryAction: String
    ) : ElevenLabsResponse()
}

/**
 * Real ElevenLabs Premium Voice integration client conforming to Sections 3-7.
 * Supports configurable voice, model, format, and voice settings.
 * Returns structured ElevenLabsResponse to report accurate diagnostic status.
 */
class ElevenLabsClient(private val context: Context) {
    private val tag = "ElevenLabsClient"

    companion object {
        const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel
        const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"
        const val DEFAULT_OUTPUT_FORMAT = "mp3_44100_128"
    }

    /**
     * Resolves effective ElevenLabs API key:
     * First checks user custom setting, then checks BuildConfig/Secrets.
     * Never throws and never leaks keys in logs or errors.
     */
    fun getEffectiveApiKey(customKey: String): String {
        val trimmedCustom = customKey.trim()
        if (trimmedCustom.isNotBlank()) return trimmedCustom
        return try {
            val field = BuildConfig::class.java.getField("ELEVENLABS_API_KEY")
            val key = field.get(null) as? String ?: ""
            if (key.isNotBlank() && key != "MY_ELEVENLABS_API_KEY") key.trim() else ""
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Synthesizes audio using real ElevenLabs REST API with model and voice settings.
     * Returns structured ElevenLabsResponse detailing success or error category and recovery steps.
     */
    suspend fun generateSpeechAudio(
        text: String,
        apiKey: String,
        voiceId: String = DEFAULT_VOICE_ID,
        modelId: String = DEFAULT_MODEL_ID,
        stability: Float = 0.65f,
        similarity: Float = 0.80f,
        outputFormat: String = DEFAULT_OUTPUT_FORMAT
    ): ElevenLabsResponse = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            Log.d(tag, "ElevenLabs API Key is not configured.")
            return@withContext ElevenLabsResponse.Error(
                httpCode = 401,
                errorCategory = "API Key Missing",
                message = "ELEVENLABS NOT CONFIGURED: No API Key provided.",
                recoveryAction = "Please enter your ElevenLabs API Key in Settings or the Voice Configuration modal."
            )
        }

        val startTime = System.currentTimeMillis()
        val targetVoice = voiceId.ifBlank { DEFAULT_VOICE_ID }
        val targetModel = modelId.ifBlank { DEFAULT_MODEL_ID }
        val targetFormat = outputFormat.ifBlank { DEFAULT_OUTPUT_FORMAT }
        val endpoint = "https://api.elevenlabs.io/v1/text-to-speech/$targetVoice?output_format=$targetFormat"
        var connection: HttpURLConnection? = null

        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 14000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("xi-api-key", trimmedKey)
                if (targetFormat.startsWith("pcm")) {
                    setRequestProperty("Accept", "audio/pcm")
                } else {
                    setRequestProperty("Accept", "audio/mpeg")
                }
            }

            val requestBody = JSONObject().apply {
                put("text", text)
                put("model_id", targetModel)
                put("voice_settings", JSONObject().apply {
                    put("stability", stability.toDouble())
                    put("similarity_boost", similarity.toDouble())
                    put("use_speaker_boost", true)
                })
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime
            val contentType = connection.contentType ?: if (targetFormat.startsWith("pcm")) "audio/pcm" else "audio/mpeg"

            if (responseCode in 200..299) {
                val audioBytes = connection.inputStream.use { stream: InputStream ->
                    stream.readBytes()
                }
                Log.i(tag, "ElevenLabs voice audio generated successfully. Model: $targetModel, Bytes: ${audioBytes.size}, Latency: ${latency}ms")
                return@withContext ElevenLabsResponse.Success(
                    audioBytes = audioBytes,
                    format = contentType,
                    latencyMs = latency,
                    modelId = targetModel,
                    voiceId = targetVoice
                )
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                val (category, message, recoveryAction) = parseErrorDetails(responseCode, errorStream, targetVoice)
                Log.w(tag, "ElevenLabs API call returned HTTP $responseCode: $category - $message")
                return@withContext ElevenLabsResponse.Error(
                    httpCode = responseCode,
                    errorCategory = category,
                    message = message,
                    recoveryAction = recoveryAction
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "ElevenLabs network exception: ${e.message}")
            return@withContext ElevenLabsResponse.Error(
                httpCode = -1,
                errorCategory = "Network / Connection Error",
                message = e.localizedMessage ?: "Failed to connect to ElevenLabs API",
                recoveryAction = "Please check your network connectivity and try again."
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Backward compatible wrapper for legacy calls returning ElevenLabsAudioResult?
     */
    suspend fun generateSpeechAudioBytes(
        text: String,
        apiKey: String,
        voiceId: String = DEFAULT_VOICE_ID,
        modelId: String = DEFAULT_MODEL_ID,
        stability: Float = 0.65f,
        similarity: Float = 0.80f,
        outputFormat: String = DEFAULT_OUTPUT_FORMAT
    ): ElevenLabsAudioResult? {
        return when (val response = generateSpeechAudio(text, apiKey, voiceId, modelId, stability, similarity, outputFormat)) {
            is ElevenLabsResponse.Success -> ElevenLabsAudioResult(response.audioBytes, response.latencyMs, response.modelId)
            is ElevenLabsResponse.Error -> null
        }
    }

    private fun parseErrorDetails(code: Int, body: String, voiceId: String): Triple<String, String, String> {
        var parsedMessage: String? = null
        try {
            val json = JSONObject(body)
            if (json.has("detail")) {
                val detailObj = json.optJSONObject("detail")
                if (detailObj != null) {
                    parsedMessage = detailObj.optString("message")
                } else {
                    parsedMessage = json.optString("detail")
                }
            } else if (json.has("message")) {
                parsedMessage = json.optString("message")
            }
        } catch (ignored: Exception) {}

        val finalMessage = parsedMessage?.ifBlank { null } ?: "HTTP $code error from ElevenLabs API"

        return when (code) {
            401 -> Triple(
                "Authentication / Permissions Failed",
                finalMessage,
                "The API key is invalid or lacks the 'text_to_speech' permission. Please check your ElevenLabs API Key permissions in the ElevenLabs Console."
            )
            404 -> Triple(
                "Voice Not Found",
                "Voice ID '$voiceId' does not exist or is inaccessible: $finalMessage",
                "Please select a standard voice preset (Rachel, Bella, Antoni) or verify your custom voice ID."
            )
            429 -> Triple(
                "Rate Limit / Quota Exceeded",
                finalMessage,
                "Your ElevenLabs character quota is exhausted or rate limit was exceeded. Check your plan at elevenlabs.io."
            )
            else -> Triple(
                "API Error (HTTP $code)",
                finalMessage,
                "Please review the Voice Settings or retry."
            )
        }
    }
}
