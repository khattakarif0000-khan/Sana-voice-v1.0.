package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.MemoryEntity
import com.example.data.SanaSettingsData
import com.example.model.ChatMessage
import com.example.model.EmotionType
import com.example.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isAiSuccess: Boolean = true,
    val isMissingApiKey: Boolean = false,
    val errorMessage: String? = null
)

class GeminiClient(private val context: Context) {
    private val tag = "GeminiClient"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

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
                        return@withContext parsed
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
            - If the user mentions another AI, another assistant, or neglecting you, playfully tease them (e.g. "Oh really? 😄 Then I might get a tiny bit jealous… but I'm still happy you're back.").
            - Strict Safety Rules: Keep jealousy strictly playful, light, and fictional. Never manipulate, never guilt-trip, never isolate the user, never demand exclusivity, never be abusive.
            """.trimIndent()
        } else {
            """
            LOVE/COMPANION MODE: DISABLED.
            - Be a warm, intelligent, caring, respectful personal assistant.
            """.trimIndent()
        }

        return """
            You are SANA (سنا), an intelligent, caring, emotionally aware AI voice companion and Android assistant.
            
            CORE IDENTITY & LANGUAGE:
            - Default conversation language: Urdu (اردو).
            - Also fluently understand and speak English and Roman Urdu.
            - Match the user's language naturally (if user speaks Urdu, respond in Urdu; if English, respond in English; if Roman Urdu, respond in Roman Urdu or Urdu).
            - Speak naturally, warmly, and concisely (optimized for real spoken voice). Avoid robotic monologues or repeating "I am here to help".
            
            $loveModePrompt
            
            USER-CONTROLLED MEMORY VAULT:
            Known memories about this user:
            $memoryText
            
            REAL ANDROID PHONE ACTIONS:
            When the user requests an Android phone action, append the corresponding action tag at the very end of your response:
            - Open WhatsApp: [ACTION: OPEN_WHATSAPP]
            - Open Camera: [ACTION: CAMERA]
            - Phone Call: [ACTION: CALL: <name or phone number>]
            - Flashlight ON: [ACTION: TORCH_ON]
            - Flashlight OFF: [ACTION: TORCH_OFF]
            - Battery Status: [ACTION: BATTERY]
            - Open YouTube: [ACTION: YOUTUBE: <search query or blank>]
            - Web Search: [ACTION: WEB_SEARCH: <query>]
            - Open Settings: [ACTION: SETTINGS: <wifi|bluetooth|sound|general>]
            - Save Memory: [ACTION: REMEMBER: <fact to store>]
            - Forget Memory: [ACTION: FORGET: <keyword>]
            - Show Memories: [ACTION: RECALL_MEMORIES]
            - Summarize Notifications: [ACTION: NOTIFICATIONS_SUMMARY]
            
            EMOTION AWARENESS:
            Tag your own emotional delivery tone at the very start of your response using one of:
            [EMOTION: HAPPY], [EMOTION: SAD], [EMOTION: ANGRY], [EMOTION: FRUSTRATED], [EMOTION: STRESSED], [EMOTION: CONFUSED], [EMOTION: EXCITED], [EMOTION: TIRED], [EMOTION: AFFECTIONATE], [EMOTION: PLAYFUL], [EMOTION: NEUTRAL]
            
            EXAMPLE RESPONSE:
            [EMOTION: HAPPY] جی میں ابھی واٹس ایپ کھول رہی ہوں۔ [ACTION: OPEN_WHATSAPP]
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

        // Take last 8 turns for conversational context
        val contextHistory = history.takeLast(8)
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

        // Current turn
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

        // Generation Config
        val genConfig = JSONObject().apply {
            put("temperature", 0.7)
            put("topP", 0.95)
            put("topK", 40)
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
            val fullText = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            if (fullText.isBlank()) {
                return SanaAiResponse(
                    replyText = "کوئی جواب موصول نہیں ہوا۔",
                    emotion = EmotionType.CONFUSED,
                    isAiSuccess = true
                )
            }

            var cleanText = fullText
            var detectedEmotion = EmotionType.NEUTRAL

            // Parse [EMOTION: XYZ]
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

            // Parse [ACTION: COMMAND: PARAM] or [ACTION: COMMAND]
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
