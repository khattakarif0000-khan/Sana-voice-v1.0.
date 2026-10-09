package com.example.voice

import android.content.Context
import android.util.Log
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

/**
 * Real ElevenLabs Premium Voice integration client conforming to Section 20.
 * If credentials are not configured or the network call fails, returns null
 * to enable seamless, graceful fallback to Gemini Native voice.
 */
class ElevenLabsClient(private val context: Context) {

    private val tag = "ElevenLabsClient"

    companion object {
        const val DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM" // Rachel / expressive
        const val DEFAULT_MODEL_ID = "eleven_multilingual_v2"
    }

    /**
     * Synthesizes audio using real ElevenLabs REST API.
     * Returns audio bytes on success, or null on error / missing configuration.
     */
    suspend fun generateSpeechAudio(
        text: String,
        apiKey: String,
        voiceId: String = DEFAULT_VOICE_ID,
        modelId: String = DEFAULT_MODEL_ID
    ): ElevenLabsAudioResult? = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            Log.d(tag, "ElevenLabs API Key is not configured. Falling back to Gemini Native Voice.")
            return@withContext null
        }

        val startTime = System.currentTimeMillis()
        val targetVoice = voiceId.ifBlank { DEFAULT_VOICE_ID }
        val targetModel = modelId.ifBlank { DEFAULT_MODEL_ID }
        val endpoint = "https://api.elevenlabs.io/v1/text-to-speech/$targetVoice"

        var connection: HttpURLConnection? = null
        try {
            val url = URL(endpoint)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 12000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("xi-api-key", trimmedKey)
                setRequestProperty("Accept", "audio/mpeg")
            }

            val requestBody = JSONObject().apply {
                put("text", text)
                put("model_id", targetModel)
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.65)
                    put("similarity_boost", 0.80)
                    put("use_speaker_boost", true)
                })
            }

            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime

            if (responseCode in 200..299) {
                val audioBytes = connection.inputStream.use { stream: InputStream ->
                    stream.readBytes()
                }
                Log.i(tag, "ElevenLabs voice audio generated successfully. Bytes: ${audioBytes.size}, Latency: ${latency}ms")
                return@withContext ElevenLabsAudioResult(audioBytes, latency, targetModel)
            } else {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.w(tag, "ElevenLabs API call failed with code $responseCode: $errorStream. Falling back to Gemini voice.")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.w(tag, "ElevenLabs network exception: ${e.message}. Falling back to Gemini voice.")
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }
}
