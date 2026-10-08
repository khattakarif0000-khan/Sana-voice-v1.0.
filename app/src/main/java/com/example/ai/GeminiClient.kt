package com.example.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.MemoryEntity
import com.example.data.SanaSettingsData
import com.example.model.ChatMessage
import com.example.model.EmotionType
import com.example.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SanaAiResponse(
    val replyText: String,
    val emotion: EmotionType = EmotionType.NEUTRAL,
    val actionCommand: String? = null,
    val actionParameter: String? = null,
    val audioBytes: ByteArray? = null,
    val isAiSuccess: Boolean = true,
    val isMissingApiKey: Boolean = false,
    val errorMessage: String? = null
)

class GeminiClient(private val context: Context) {
    private val tag = "GeminiClient"

    // Reusable HTTP client with persistent connection pool for low latency
    private val okHttpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun getEffectiveApiKey(customKey: String): String {
        val trimmedCustom = customKey.trim()
        if (trimmedCustom.isNotBlank()) return trimmedCustom

        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Throwable) {
            ""
        }

        return if (buildConfigKey.isNotBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            buildConfigKey
        } else {
            ""
        }
    }

    suspend fun generateResponse(
        userInput: String,
        history: List<ChatMessage>,
        settings: SanaSettingsData,
        memories: List<MemoryEntity>,
        imageBase64: String? = null
    ): SanaAiResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(settings.customApiKey)

        if (apiKey.isBlank()) {
            return@withContext SanaAiResponse(
                replyText = "AI سروس کنفیگر نہیں ہے۔ براہ کرم Settings یا Secrets میں اپنی Gemini API Key درج کریں۔ (AI service is not configured. Please enter your Gemini API key in Settings.)",
                emotion = EmotionType.CONFUSED,
                isAiSuccess = false,
                isMissingApiKey = true,
                errorMessage = "API key missing"
            )
        }

        val systemInstruction = buildSystemPrompt(settings, memories)

        // Use fast gemini-3.1-flash-lite-preview for ultra-fast conversational reasoning
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite-preview:generateContent?key=$apiKey"

        try {
            val requestJson = buildRequestBodyJson(userInput, history, systemInstruction, imageBase64)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            var attempts = 0
            var lastException: Exception? = null

            while (attempts < 2) {
                attempts++
                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string() ?: ""
                        if (!response.isSuccessful) {
                            val errorDetail = parseErrorMessage(responseBody)
                            Log.e(tag, "Gemini API error ($response.code): $errorDetail")
                            return@withContext SanaAiResponse(
                                replyText = "معذرت، AI سروس سے رابطہ نہیں ہو سکا۔ ($errorDetail)",
                                emotion = EmotionType.SAD,
                                isAiSuccess = false,
                                errorMessage = "HTTP ${response.code}: $errorDetail"
                            )
                        }

                        val parsed = parseGeminiResponse(responseBody)

                        // Attempt to fetch native Gemini TTS audio for real voice output
                        val cleanSpeechText = cleanTextForVoice(parsed.replyText)
                        val pcmAudio = if (cleanSpeechText.isNotBlank()) {
                            generateGeminiSpeechAudio(cleanSpeechText, apiKey)
                        } else null

                        return@withContext parsed.copy(audioBytes = pcmAudio)
                    }
                } catch (e: IOException) {
                    lastException = e
                    Log.w(tag, "Gemini network retry attempt $attempts", e)
                    if (attempts >= 2) throw e
                }
            }

            return@withContext SanaAiResponse(
                replyText = "نیٹ ورک کی خرابی کی وجہ سے رابطہ منقطع ہو گیا۔ براہ کرم انٹرنیٹ چیک کریں۔",
                emotion = EmotionType.FRUSTRATED,
                isAiSuccess = false,
                errorMessage = lastException?.message
            )
        } catch (e: Exception) {
            Log.e(tag, "Gemini generation failed", e)
            return@withContext SanaAiResponse(
                replyText = "تکنیکی خرابی: ${e.localizedMessage ?: "نامعلوم خرابی"}",
                emotion = EmotionType.SAD,
                isAiSuccess = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Generates native audio speech from Gemini TTS models with fallback
     */
    suspend fun generateGeminiSpeechAudio(
        text: String,
        apiKey: String,
        voiceName: String = "Kore"
    ): ByteArray? = withContext(Dispatchers.IO) {
        val sanitizedText = cleanTextForVoice(text)
            .replace(Regex("[\\p{So}\\p{Cn}]"), "") // Remove emojis like ❤️ which causes TTS 400
            .trim()

        if (sanitizedText.isBlank()) return@withContext null

        val candidateModels = listOf(
            "gemini-3.8-flash-tts",
            "gemini-3.1-flash-tts-preview",
            "gemini-3.8-flash-lite-tts",
            "gemini-2.5-pro-preview-tts",
            "gemini-2.5-flash-preview-tts"
        )

        val root = JSONObject().apply {
            val contents = JSONArray().apply {
                put(JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", sanitizedText))
                    }
                    put("parts", parts)
                })
            }
            put("contents", contents)

            val genConfig = JSONObject().apply {
                val modalities = JSONArray().apply { put("AUDIO") }
                put("responseModalities", modalities)

                val speechConfig = JSONObject().apply {
                    val voiceConfig = JSONObject().apply {
                        val prebuilt = JSONObject().apply {
                            put("voiceName", voiceName)
                        }
                        put("prebuiltVoiceConfig", prebuilt)
                    }
                    put("voiceConfig", voiceConfig)
                }
                put("speechConfig", speechConfig)
            }
            put("generationConfig", genConfig)
        }

        val requestBody = root.toString().toRequestBody(jsonMediaType)

        for (model in candidateModels) {
            val ttsEndpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            try {
                val request = Request.Builder()
                    .url(ttsEndpoint)
                    .post(requestBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(tag, "Gemini TTS model $model returned ${response.code}, trying next model")
                        return@use
                    }
                    val bodyStr = response.body?.string() ?: ""
                    val obj = JSONObject(bodyStr)
                    val candidates = obj.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val parts = firstCandidate?.optJSONObject("content")?.optJSONArray("parts")
                    for (i in 0 until (parts?.length() ?: 0)) {
                        val part = parts?.optJSONObject(i)
                        val inlineData = part?.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val b64 = inlineData.optString("data")
                            if (b64.isNotBlank()) {
                                Log.d(tag, "Gemini TTS successfully generated audio using $model")
                                return@withContext Base64.decode(b64, Base64.DEFAULT)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Gemini TTS model $model call failed", e)
            }
        }
        return@withContext null
    }

    private fun buildSystemPrompt(
        settings: SanaSettingsData,
        memories: List<MemoryEntity>
    ): String {
        val memoryText = if (memories.isEmpty()) {
            "No prior memories saved yet."
        } else {
            memories.joinToString("\n") { "- ${it.content} (Saved: ${it.category})" }
        }

        val loveModePrompt = if (settings.loveModeEnabled) {
            """
            LOVE/COMPANION MODE: ENABLED.
            Jealousy Level: ${settings.jealousyLevel}.
            - Express affection, warmth, caring, and playful teasing.
            - If user mentions another AI, playfully tease them (e.g. "Oh really? 😄 Then I might get a tiny bit jealous… but I'm still happy you're back.").
            - Strict Safety: Never manipulate, guilt-trip, isolate, or claim ownership.
            """.trimIndent()
        } else {
            """
            LOVE/COMPANION MODE: DISABLED.
            - Be a warm, intelligent, caring, respectful personal assistant.
            """.trimIndent()
        }

        return """
            You are SANA (سنا), an intelligent, caring, emotionally aware AI voice companion and Android assistant.
            
            LOW-LATENCY FAST RESPONSE RULES:
            - Respond in 1 to 2 short, concise sentences.
            - Do not give long essays or unnecessary greetings before answering.
            - Speak directly and naturally.
            
            LANGUAGE RULES:
            - Default conversation language: Urdu (اردو).
            - Also fluently understand and speak English and Roman Urdu.
            - Match the user's language naturally (Urdu for Urdu, English for English, Roman Urdu for Roman Urdu).
            
            $loveModePrompt
            
            USER-CONTROLLED MEMORY VAULT:
            Known memories about this user:
            $memoryText
            
            REAL ANDROID PHONE ACTIONS:
            When the user requests an action, append the tag at the end of your short answer:
            - Open WhatsApp: [ACTION: OPEN_WHATSAPP]
            - Open Camera: [ACTION: CAMERA]
            - Phone Call: [ACTION: CALL: <name or phone number>]
            - Flashlight ON: [ACTION: TORCH_ON]
            - Flashlight OFF: [ACTION: TORCH_OFF]
            - Battery Status: [ACTION: BATTERY]
            - Open YouTube: [ACTION: YOUTUBE: <query>]
            - Web Search: [ACTION: WEB_SEARCH: <query>]
            - Open Settings: [ACTION: SETTINGS: <type>]
            - Save Memory: [ACTION: REMEMBER: <fact>]
            - Forget Memory: [ACTION: FORGET: <keyword>]
            - Show Memories: [ACTION: RECALL_MEMORIES]
            - Summarize Notifications: [ACTION: NOTIFICATIONS_SUMMARY]
            
            EMOTION AWARENESS:
            Tag your emotion at the very start:
            [EMOTION: HAPPY], [EMOTION: SAD], [EMOTION: ANGRY], [EMOTION: FRUSTRATED], [EMOTION: STRESSED], [EMOTION: CONFUSED], [EMOTION: EXCITED], [EMOTION: TIRED], [EMOTION: AFFECTIONATE], [EMOTION: PLAYFUL], [EMOTION: NEUTRAL]
        """.trimIndent()
    }

    private fun buildRequestBodyJson(
        userInput: String,
        history: List<ChatMessage>,
        systemInstruction: String,
        imageBase64: String?
    ): JSONObject {
        val root = JSONObject()

        // System Instruction
        val sysContent = JSONObject().apply {
            val parts = JSONArray().apply {
                put(JSONObject().put("text", systemInstruction))
            }
            put("parts", parts)
        }
        root.put("systemInstruction", sysContent)

        // Contents
        val contentsArray = JSONArray()

        val contextHistory = history.takeLast(6)
        for (msg in contextHistory) {
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            val parts = JSONArray().apply {
                put(JSONObject().put("text", msg.text))
            }
            contentsArray.put(JSONObject().apply {
                put("role", role)
                put("parts", parts)
            })
        }

        val currentParts = JSONArray().apply {
            put(JSONObject().put("text", userInput))
            if (!imageBase64.isNullOrBlank()) {
                val inlineData = JSONObject().apply {
                    put("mimeType", "image/jpeg")
                    put("data", imageBase64)
                }
                put(JSONObject().put("inlineData", inlineData))
            }
        }
        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", currentParts)
        })

        root.put("contents", contentsArray)

        // Generation Config with maxOutputTokens limit for snappy replies
        val genConfig = JSONObject().apply {
            put("temperature", 0.7)
            put("topP", 0.9)
            put("maxOutputTokens", 120)
        }
        root.put("generationConfig", genConfig)

        return root
    }

    private fun parseGeminiResponse(rawJson: String): SanaAiResponse {
        return try {
            val obj = JSONObject(rawJson)
            val candidates = obj.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            var fullText = ""
            for (i in 0 until (parts?.length() ?: 0)) {
                val p = parts?.optJSONObject(i)
                val isThought = p?.optBoolean("thought", false) ?: false
                if (!isThought) {
                    val t = p?.optString("text", "") ?: ""
                    if (t.isNotBlank()) {
                        fullText = if (fullText.isEmpty()) t else "$fullText\n$t"
                    }
                }
            }

            if (fullText.isBlank()) {
                return SanaAiResponse(
                    replyText = "کوئی جواب موصول نہیں ہوا۔",
                    emotion = EmotionType.CONFUSED,
                    isAiSuccess = true
                )
            }

            var cleanText = fullText
                .replace(Regex("(?s)<thought>.*?</thought>"), "")
                .replace(Regex("(?s)<thinking>.*?</thinking>"), "")
                .trim()
            var detectedEmotion = EmotionType.NEUTRAL

            val emotionRegex = Regex("\\[EMOTION:\\s*([A-Z_]+)\\]")
            val emotionMatch = emotionRegex.find(cleanText)
            if (emotionMatch != null) {
                val rawEmotion = emotionMatch.groupValues[1]
                detectedEmotion = try {
                    EmotionType.valueOf(rawEmotion)
                } catch (e: Exception) {
                    EmotionType.NEUTRAL
                }
                cleanText = cleanText.replace(emotionMatch.value, "").trim()
            }

            var actionCommand: String? = null
            var actionParam: String? = null

            val actionRegex = Regex("\\[ACTION:\\s*([^:\\]]+)(?::\\s*([^\\]]+))?\\]")
            val actionMatch = actionRegex.find(cleanText)
            if (actionMatch != null) {
                actionCommand = actionMatch.groupValues[1].trim()
                actionParam = actionMatch.groupValues.getOrNull(2)?.trim()
                cleanText = cleanText.replace(actionMatch.value, "").trim()
            }

            SanaAiResponse(
                replyText = cleanText,
                emotion = detectedEmotion,
                actionCommand = actionCommand,
                actionParameter = actionParam,
                isAiSuccess = true
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Gemini response", e)
            SanaAiResponse(
                replyText = "جواب پڑھنے میں خرابی پیش آئی۔",
                emotion = EmotionType.CONFUSED,
                isAiSuccess = false,
                errorMessage = e.message
            )
        }
    }

    private fun cleanTextForVoice(raw: String): String {
        return raw
            .replace(Regex("\\[EMOTION:[^\\]]+\\]"), "")
            .replace(Regex("\\[ACTION:[^\\]]+\\]"), "")
            .replace(Regex("[*#_`~]"), "")
            .trim()
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JSONObject(errorBody)
            val error = json.optJSONObject("error")
            error?.optString("message", errorBody) ?: errorBody
        } catch (e: Exception) {
            errorBody.take(150)
        }
    }
}
