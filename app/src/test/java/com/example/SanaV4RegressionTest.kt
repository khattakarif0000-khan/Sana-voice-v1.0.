package com.example

import com.example.ai.LocalCommandParser
import com.example.data.SanaSettingsData
import com.example.model.ConversationState
import com.example.model.EmotionType
import com.example.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SanaV4RegressionTest {

    @Test
    fun `test local command parser handles WhatsApp Message action accurately`() {
        val result = LocalCommandParser.parse("WhatsApp par Ali ko message bhejo: Main ghar aa raha hoon")
        assertTrue("WhatsApp message command should be matched", result.matched)
        assertEquals("WHATSAPP_MESSAGE", result.command)
        assertEquals("Ali", result.parameter)
        assertEquals("Main ghar aa raha hoon", result.secondParameter)
    }

    @Test
    fun `test local command parser handles general WhatsApp open`() {
        val result = LocalCommandParser.parse("WhatsApp kholo")
        assertTrue("WhatsApp open command should be matched", result.matched)
        assertEquals("OPEN_WHATSAPP", result.command)
    }

    @Test
    fun `test local command parser handles YouTube search and playback`() {
        val result = LocalCommandParser.parse("YouTube par Atif Aslam ka gana chalao")
        assertTrue("YouTube command should be matched", result.matched)
        assertEquals("YOUTUBE", result.command)
        assertTrue(result.parameter?.contains("atif aslam") == true)
    }

    @Test
    fun `test local command parser handles Anti-Theft Arm and Disarm`() {
        val armResult = LocalCommandParser.parse("arm anti-theft")
        assertTrue(armResult.matched)
        assertEquals("ARM_ANTI_THEFT", armResult.command)

        val disarmResult = LocalCommandParser.parse("disarm anti-theft")
        assertTrue(disarmResult.matched)
        assertEquals("DISARM_ANTI_THEFT", disarmResult.command)
    }

    @Test
    fun `test local command parser handles trading chart scan`() {
        val result = LocalCommandParser.parse("trading chart scan karo")
        assertTrue(result.matched)
        assertEquals("OPEN_TRADING", result.command)
    }

    @Test
    fun `test local command parser handles memory remember command`() {
        val result = LocalCommandParser.parse("remember that my tea is Karak Chai")
        assertTrue(result.matched)
        assertEquals("REMEMBER", result.command)
        assertEquals("my tea is Karak Chai", result.parameter)
    }

    @Test
    fun `test settings default configuration preserves Gemini Native as default`() {
        val defaults = SanaSettingsData()
        assertEquals("GEMINI_NATIVE", defaults.voiceProvider)
        assertEquals("eleven_multilingual_v2", defaults.elevenLabsModelId)
        assertEquals("21m00Tcm4TlvDq8ikWAM", defaults.elevenLabsVoiceId)
    }
}
