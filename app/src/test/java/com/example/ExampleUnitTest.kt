package com.example

import com.example.ai.LocalCommandParser
import com.example.model.EmotionType
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testLocalCommandParser_whatsApp() {
        val result = LocalCommandParser.parse("SANA WhatsApp kholo")
        assertTrue(result.matched)
        assertEquals("OPEN_WHATSAPP", result.command)
    }

    @Test
    fun testLocalCommandParser_camera() {
        val result = LocalCommandParser.parse("Camera open karo")
        assertTrue(result.matched)
        assertEquals("CAMERA", result.command)
    }

    @Test
    fun testLocalCommandParser_torch() {
        val onResult = LocalCommandParser.parse("Torch on karo")
        assertTrue(onResult.matched)
        assertEquals("TORCH_ON", onResult.command)

        val offResult = LocalCommandParser.parse("Torch band karo")
        assertTrue(offResult.matched)
        assertEquals("TORCH_OFF", offResult.command)
    }

    @Test
    fun testLocalCommandParser_battery() {
        val result = LocalCommandParser.parse("Battery kitni hai?")
        assertTrue(result.matched)
        assertEquals("BATTERY", result.command)
    }

    @Test
    fun testLocalCommandParser_memory() {
        val result = LocalCommandParser.parse("Remember my name is Ali")
        assertTrue(result.matched)
        assertEquals("REMEMBER", result.command)
        assertTrue(result.parameter?.contains("Ali") == true)
    }
}
