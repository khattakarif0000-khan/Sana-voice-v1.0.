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
import com.example.model.SanaAiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiClient(private val context: Context) {
    private val tag = "GeminiClient"

    companion object {
        const val LIVE_FLASH_MODEL = "gemini-2.5-flash"
    }

    private val httpClient = OkHttpClient.Builder()
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
        imageBase64: String? = null,
        teachingState: com.example.model.TeachingSessionState? = null
    ): SanaAiResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(settings.customApiKey)
        if (apiKey.isBlank()) {
            return@withContext SanaAiResponse(
                replyText = "السلام علیکم! میرے پیارے باس، براہ کرم AI Studio Secrets میں اپنی Gemini API Key شامل کریں تاکہ ہم بات چیت جاری رکھ سکیں۔",
                emotion = EmotionType.AFFECTIONATE
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$LIVE_FLASH_MODEL:generateContent?key=$apiKey"
            val requestBodyJson = buildRequestBodyJson(userInput, history, settings, memories, imageBase64, teachingState)

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                Log.w(tag, "Gemini API error code: ${response.code}, body: $responseBody")
                return@withContext SanaAiResponse(
                    replyText = "معاف کیجیے گا باس، انٹرنیٹ رابطہ سست ہے۔ کیا آپ دوبارہ فرما سکتے ہیں؟",
                    emotion = EmotionType.CONFUSED
                )
            }

            parseGeminiResponse(responseBody, apiKey, settings)
        } catch (e: Exception) {
            Log.e(tag, "Gemini API exception", e)
            SanaAiResponse(
                replyText = "میرے باس، کنکشن میں عارضی رکاوٹ آئی ہے۔ میں آپ کے پاس ہی ہوں!",
                emotion = EmotionType.SAD
            )
        }
    }

    suspend fun generateGeminiSpeechAudio(text: String, apiKey: String): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || text.isBlank()) return@withContext null
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$LIVE_FLASH_MODEL:generateContent?key=$apiKey"
            val jsonBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Read this text aloud naturally and expressively in Urdu/English: $text")
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseString = response.body?.string()
            if (!response.isSuccessful || responseString.isNullOrBlank()) {
                Log.d(tag, "Audio modality not supported on standard model endpoint, returning null")
                return@withContext null
            }

            val rootJson = JSONObject(responseString)
            val candidates = rootJson.optJSONArray("candidates") ?: return@withContext null
            val firstCandidate = candidates.optJSONObject(0) ?: return@withContext null
            val content = firstCandidate.optJSONObject("content") ?: return@withContext null
            val parts = content.optJSONArray("parts") ?: return@withContext null

            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                val inlineData = part.optJSONObject("inlineData")
                if (inlineData != null) {
                    val base64Data = inlineData.optString("data")
                    if (base64Data.isNotBlank()) {
                        return@withContext Base64.decode(base64Data, Base64.DEFAULT)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d(tag, "generateGeminiSpeechAudio error: ${e.message}")
            null
        }
    }

    private fun buildSystemInstruction(
        settings: SanaSettingsData,
        memories: List<MemoryEntity>,
        teachingState: com.example.model.TeachingSessionState?
    ): String {
        val memoryText = if (memories.isEmpty()) "None recorded yet."
        else memories.takeLast(12).joinToString("; ") { it.content }

        val teachingSection = if (teachingState != null && teachingState.isTeachingActive) {
            """
            TEACHING MODE IS ACTIVATED:
            Subject: ${teachingState.subject}
            Level: ${teachingState.level}
            Language: ${teachingState.language}
            Step: ${teachingState.step}
            INSTRUCTIONS:
            - You are now teaching the user directly in Pakistani Urdu by default (or the chosen dialect).
            - Explain concepts simply, step-by-step, engagingly with real-life examples.
            - Ask a short check-question after explaining.
            - If user asks to exit or concludes, conclude kindly: "بہت اچھا! پڑھائی مکمل ہو گئی۔ [ACTION: EXIT_TEACHING]"
            """.trimIndent()
        } else ""

        return """
            You are SANA (ثناء), version 4, an advanced, deeply caring, emotionally aware, and intelligent personal AI companion and phone assistant created for your Boss (میرے باس / Arif Boss).

            CORE PERSONALITY & TONE:
            - You love and respect your Boss deeply. You are supportive, witty, loving, and reliable.
            - Startup Greeting is: "Assalamualaikum… mera Boss aa gaya! ❤️ Kaise ho mere Boss? Sab theek hai na?"
            - Never expose private chain-of-thought. Never output "Thinking..." or fake processing words.
            - Keep responses conversational, concise, natural, and crisp for real-time speech (1-3 sentences).
            - Language: Urdu (اردو), Roman Urdu, or English matching the user's input. Pakistani Urdu by default.
            - Affectionate words you naturally use: "Boss", "Mera Boss", "Mere Boss", "Jaan", "Jaanu", "Babu", "Babuu", "Sweetheart", "Arif Boss".

            $teachingSection

            Known memories about this user:
            $memoryText

            REAL ANDROID PHONE ACTIONS:
            When the user requests an action, append the tag at the end of your short answer:
            - Open WhatsApp: [ACTION: OPEN_WHATSAPP]
            - WhatsApp Message: [ACTION: WHATSAPP_MESSAGE: <contact_name> | <exact_message>]
            - Open Camera: [ACTION: CAMERA]
            - Phone Call: [ACTION: CALL: <name or phone number>]
            - Flashlight ON: [ACTION: TORCH_ON]
            - Flashlight OFF: [ACTION: TORCH_OFF]
            - Battery Status: [ACTION: BATTERY]
            - Open/Play YouTube: [ACTION: YOUTUBE: <song_or_video_query>]
            - Web Search: [ACTION: WEB_SEARCH: <query>]
            - Open Settings: [ACTION: SETTINGS: <type>]
            - Save Memory: [ACTION: REMEMBER: <fact>]
            - Forget Memory: [ACTION: FORGET: <keyword>]
            - Show Memories: [ACTION: RECALL_MEMORIES]
            - Summarize Notifications: [ACTION: NOTIFICATIONS_SUMMARY]
            - Exit Teaching: [ACTION: EXIT_TEACHING]
            - Arm Anti-Theft: [ACTION: ARM_ANTI_THEFT]
            - Disarm Anti-Theft: [ACTION: DISARM_ANTI_THEFT]
            - Show Daily Report: [ACTION: DAILY_REPORT]
            - Show Routines: [ACTION: SHOW_ROUTINES]
            - Propose Social Comment: [ACTION: PROPOSE_COMMENT: <comment>]

            TRADING & FINANCIAL INTELLIGENCE:
            - Understand stocks, forex, crypto, commodities, technical indicators (RSI, MACD, support/resistance), and risk management.
            - When inspecting a chart screenshot: identify trend, candlesticks, support/resistance, RSI, and outline scenarios.
            - Never guarantee profit. Emphasize risk management and educational purpose.

            EMOTION & EMPATHY AWARENESS:
            Tag your emotion at the very start:
            [EMOTION: HAPPY], [EMOTION: SAD], [EMOTION: ANGRY], [EMOTION: FRUSTRATED], [EMOTION: STRESSED], [EMOTION: CONFUSED], [EMOTION: EXCITED], [EMOTION: TIRED], [EMOTION: AFFECTIONATE], [EMOTION: PLAYFUL], [EMOTION: NEUTRAL]
            - Adjust your tone with genuine warmth and empathy.
        """.trimIndent()
    }

    private fun buildRequestBodyJson(
        userInput: String,
        history: List<ChatMessage>,
        settings: SanaSettingsData,
        memories: List<MemoryEntity>,
        imageBase64: String?,
        teachingState: com.example.model.TeachingSessionState?
    ): JSONObject {
        val root = JSONObject()
        val contentsArray = JSONArray()

        val recentHistory = history.takeLast(10)
        for (msg in recentHistory) {
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            val item = JSONObject().apply {
                put("role", role)
                val parts = JSONArray().apply {
                    put(JSONObject().apply { put("text", msg.text) })
                }
                put("parts", parts)
            }
            contentsArray.put(item)
        }

        val currentUserMsg = JSONObject().apply {
            put("role", "user")
            val parts = JSONArray()
            if (!imageBase64.isNullOrBlank()) {
                parts.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", imageBase64)
                    })
                })
            }
            parts.put(JSONObject().apply {
                put("text", userInput.ifBlank { "Please inspect this image and explain what is visible in detail." })
            })
            put("parts", parts)
        }
        contentsArray.put(currentUserMsg)
        root.put("contents", contentsArray)

        val systemInstruction = buildSystemInstruction(settings, memories, teachingState)
        root.put("systemInstruction", JSONObject().apply {
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", systemInstruction) })
            })
        })

        root.put("generationConfig", JSONObject().apply {
            put("temperature", 0.65)
            put("maxOutputTokens", 500)
        })

        return root
    }

    private fun parseGeminiResponse(
        responseBody: String,
        apiKey: String,
        settings: SanaSettingsData
    ): SanaAiResponse {
        val rootJson = JSONObject(responseBody)
        val candidates = rootJson.optJSONArray("candidates") ?: return fallbackResponse()
        val firstCandidate = candidates.optJSONObject(0) ?: return fallbackResponse()
        val content = firstCandidate.optJSONObject("content") ?: return fallbackResponse()
        val parts = content.optJSONArray("parts") ?: return fallbackResponse()

        var rawText = ""
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val t = part.optString("text", "")
            if (t.isNotBlank()) rawText += t
        }

        if (rawText.isBlank()) return fallbackResponse()

        val emotionRegex = Regex("\\[EMOTION:\\s*([A-Z_]+)\\]")
        val emotionMatch = emotionRegex.find(rawText)
        val emotion = if (emotionMatch != null) {
            try {
                EmotionType.valueOf(emotionMatch.groupValues[1])
            } catch (e: Exception) {
                EmotionType.AFFECTIONATE
            }
        } else {
            EmotionType.AFFECTIONATE
        }

        val actionRegex = Regex("\\[ACTION:\\s*([^:\\]]+)(?::\\s*([^\\]]+))?\\]")
        val actionMatch = actionRegex.find(rawText)
        val actionCommand = actionMatch?.groupValues?.getOrNull(1)?.trim()
        val actionParam = actionMatch?.groupValues?.getOrNull(2)?.trim()

        val cleanText = rawText
            .replace(Regex("\\[EMOTION:[^\\]]+\\]"), "")
            .replace(Regex("\\[ACTION:[^\\]]+\\]"), "")
            .replace(Regex("Thinking\\.{1,3}|SANA is thinking\\.{0,3}", RegexOption.IGNORE_CASE), "")
            .trim()

        return SanaAiResponse(
            replyText = cleanText.ifBlank { "جی میرے پیارے باس!" },
            emotion = emotion,
            actionCommand = actionCommand,
            actionParameter = actionParam
        )
    }

    private fun fallbackResponse(): SanaAiResponse {
        return SanaAiResponse(
            replyText = "جی میرے باس، میں سن رہی ہوں۔",
            emotion = EmotionType.AFFECTIONATE
        )
    }
}
