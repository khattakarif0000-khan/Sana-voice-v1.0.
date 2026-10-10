package com.example.ai

import com.example.model.EmotionType

data class LocalParsedCommand(
    val matched: Boolean,
    val command: String? = null,
    val parameter: String? = null,
    val secondParameter: String? = null,
    val spokenResponse: String? = null,
    val emotion: EmotionType = EmotionType.NEUTRAL
)

object LocalCommandParser {
    fun parse(input: String): LocalParsedCommand {
        val lower = input.trim().lowercase()

        // 1. WhatsApp Message vs WhatsApp Open (Section 11)
        // e.g.: "WhatsApp par Ali ko message bhejo: Main aa raha hoon"
        if (lower.contains("whatsapp") || lower.contains("واٹس ایپ")) {
            val messageMatch = Regex("(?i)(?:par|pe|ko)?\\s*([a-zA-Z0-9_+\\-]+)\\s*(?:ko)?\\s*(?:message|msg|paigham|پیغام)\\s*(?:bhejo|karo|send karo|send|بھیجو)[:\\s]+(.+)").find(input)
            if (messageMatch != null) {
                val contact = messageMatch.groupValues[1].trim()
                val messageText = messageMatch.groupValues[2].trim()
                return LocalParsedCommand(
                    matched = true,
                    command = "WHATSAPP_MESSAGE",
                    parameter = contact,
                    secondParameter = messageText,
                    spokenResponse = "واٹس ایپ پر $contact کے لیے پیغام تیار کر کے کھول دیا ہے۔ (Opening WhatsApp message for $contact.)",
                    emotion = EmotionType.HAPPY
                )
            }

            return LocalParsedCommand(
                matched = true,
                command = "OPEN_WHATSAPP",
                spokenResponse = "واٹس ایپ کھول دیا ہے۔ (Opening WhatsApp.)",
                emotion = EmotionType.HAPPY
            )
        }

        // 2. Camera
        if (lower.contains("camera") || lower.contains("کیمرا") || lower.contains("photo") || lower.contains("تصویر")) {
            return LocalParsedCommand(
                matched = true,
                command = "CAMERA",
                spokenResponse = "کیمرا کھول دیا ہے۔ (Opening camera.)",
                emotion = EmotionType.HAPPY
            )
        }

        // 3. Torch / Flashlight ON
        if ((lower.contains("torch") || lower.contains("flashlight") || lower.contains("ٹارچ") || lower.contains("بتی")) &&
            (lower.contains("on") || lower.contains("kholo") || lower.contains("jalao") || lower.contains("آن") || lower.contains("جلاؤ"))) {
            return LocalParsedCommand(
                matched = true,
                command = "TORCH_ON",
                spokenResponse = "فلیش لائٹ آن کر دی ہے۔",
                emotion = EmotionType.NEUTRAL
            )
        }

        // 4. Torch OFF
        if ((lower.contains("torch") || lower.contains("flashlight") || lower.contains("ٹارچ") || lower.contains("بتی")) &&
            (lower.contains("off") || lower.contains("band") || lower.contains("بند"))) {
            return LocalParsedCommand(
                matched = true,
                command = "TORCH_OFF",
                spokenResponse = "فلیش لائٹ بند کر دی ہے۔",
                emotion = EmotionType.NEUTRAL
            )
        }

        // 5. Battery
        if (lower.contains("battery") || lower.contains("بیٹری") || lower.contains("charging") || lower.contains("چارج")) {
            return LocalParsedCommand(
                matched = true,
                command = "BATTERY",
                spokenResponse = "بیٹری کا اسٹیٹس چیک کیا جا رہا ہے۔",
                emotion = EmotionType.NEUTRAL
            )
        }

        // 6. YouTube Search & Playback (Section 12)
        if (lower.contains("youtube") || lower.contains("یوٹیوب")) {
            val query = lower
                .replace("youtube", "")
                .replace("یوٹیوب", "")
                .replace("kholo", "")
                .replace("open", "")
                .replace("par", "")
                .replace("pe", "")
                .replace("chalao", "")
                .replace("play karo", "")
                .replace("play", "")
                .replace("lagao", "")
                .trim()

            val speech = if (query.isNotBlank()) "یوٹیوب پر تلاش کر کے گانا چلایا جا رہا ہے۔" else "یوٹیوب کھول دیا ہے۔"
            return LocalParsedCommand(
                matched = true,
                command = "YOUTUBE",
                parameter = query.ifBlank { null },
                spokenResponse = speech,
                emotion = EmotionType.HAPPY
            )
        }

        // 7. Phone call: "call ali", "ali ko call karo", "call 03001234567"
        if (lower.startsWith("call ") || lower.contains("ko call karo") || lower.contains("کو کال کرو") || lower.startsWith("کال ")) {
            val target = extractCallTarget(input)
            if (target.isNotBlank()) {
                return LocalParsedCommand(
                    matched = true,
                    command = "CALL",
                    parameter = target,
                    spokenResponse = "$target کو کال ملائی جا رہی ہے۔",
                    emotion = EmotionType.HAPPY
                )
            }
        }

        // 8. Settings
        if (lower.contains("settings") || lower.contains("سیٹنگز") || lower.contains("سیٹنگ")) {
            return LocalParsedCommand(
                matched = true,
                command = "SETTINGS",
                parameter = "general",
                spokenResponse = "سیٹنگز کھول دی ہے۔",
                emotion = EmotionType.NEUTRAL
            )
        }

        // 9. Memory
        if (lower.startsWith("remember ") || lower.contains("yaad rakho") || lower.contains("یاد رکھو")) {
            val fact = input
                .replace(Regex("(?i)remember"), "")
                .replace(Regex("(?i)yaad rakho"), "")
                .replace("یاد رکھو", "")
                .replace(Regex("(?i)\\bthat\\b"), "")
                .replace(Regex("(?i)\\bkeh\\b"), "")
                .trim()
            if (fact.isNotBlank()) {
                return LocalParsedCommand(
                    matched = true,
                    command = "REMEMBER",
                    parameter = fact,
                    spokenResponse = "میں نے یہ محفوظ کر لیا ہے: $fact",
                    emotion = EmotionType.AFFECTIONATE
                )
            }
        }

        if (lower.contains("what do you remember") || lower.contains("meri yaadein") || lower.contains("میری یادیں")) {
            return LocalParsedCommand(
                matched = true,
                command = "RECALL_MEMORIES",
                spokenResponse = "آپ کی محفوظ کردہ یادیں چیک کر رہی ہوں۔",
                emotion = EmotionType.HAPPY
            )
        }

        if (lower.contains("delete my memory") || lower.contains("clear memory") || lower.contains("بھول جاؤ")) {
            return LocalParsedCommand(
                matched = true,
                command = "FORGET",
                parameter = "ALL",
                spokenResponse = "تمام یادیں مٹا دی گئی ہیں۔",
                emotion = EmotionType.SAD
            )
        }

        // 10. Anti-Theft Disarm checked BEFORE Arm to prevent substring false-match
        if (lower.contains("disarm anti-theft") || lower.contains("disarm anti theft") || lower.contains("chori se bachao band") || lower.contains("چوری سے بچاؤ بند")) {
            return LocalParsedCommand(
                matched = true,
                command = "DISARM_ANTI_THEFT",
                spokenResponse = "اینٹی تھیفٹ پروٹیکشن غیر فعال کر دی گئی ہے۔",
                emotion = EmotionType.NEUTRAL
            )
        }

        if (lower.contains("arm anti-theft") || lower.contains("arm anti theft") || lower.contains("chori se bachao on") || lower.contains("چوری سے بچاؤ آن")) {
            return LocalParsedCommand(
                matched = true,
                command = "ARM_ANTI_THEFT",
                spokenResponse = "اینٹی تھیفٹ پروٹیکشن فعال کر دی گئی ہے۔ فون ہلانے پر وارننگ ملے گی۔",
                emotion = EmotionType.HAPPY
            )
        }

        // 11. Daily Report
        if (lower.contains("daily report") || lower.contains("conversation report") || lower.contains("rozana report") || lower.contains("روزانہ رپورٹ")) {
            return LocalParsedCommand(
                matched = true,
                command = "DAILY_REPORT",
                spokenResponse = "آج کی گفتگو کا مکمل خلاصہ حاضر ہے۔",
                emotion = EmotionType.HAPPY
            )
        }

        // 12. Routines
        if (lower.contains("show routines") || lower.contains("meri routines") || lower.contains("روٹینز")) {
            return LocalParsedCommand(
                matched = true,
                command = "SHOW_ROUTINES",
                spokenResponse = "آپ کی تمام آٹومیشن روٹینز کھل رہی ہیں۔",
                emotion = EmotionType.HAPPY
            )
        }

        // 13. Trading Chart Scan
        if (lower.contains("trading chart") || lower.contains("chart scan") || lower.contains("چارٹ اسکین")) {
            return LocalParsedCommand(
                matched = true,
                command = "OPEN_TRADING",
                spokenResponse = "ٹریڈنگ چارٹ اسکین کھل رہا ہے۔ کیمرا چارٹ کے سامنے رکھیں۔",
                emotion = EmotionType.HAPPY
            )
        }

        return LocalParsedCommand(matched = false)
    }

    private fun extractCallTarget(text: String): String {
        return text
            .replace(Regex("(?i)\\bcall\\b"), "")
            .replace(Regex("(?i)ko call karo"), "")
            .replace(Regex("(?i)ko call lagao"), "")
            .replace(Regex("(?i)ko phone karo"), "")
            .replace("کو کال کرو", "")
            .replace("کال کرو", "")
            .replace("کال", "")
            .trim()
    }
}
