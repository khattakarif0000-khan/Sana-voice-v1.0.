package com.example.data

import com.example.model.ChatMessage
import com.example.model.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyConversationReport(
    val dateString: String,
    val totalInteractions: Int,
    val topicsDiscussed: List<String>,
    val timeSections: List<String>,
    val keyPoints: List<String>,
    val actionItemsAndPlans: List<String>,
    val highlights: List<String>
)

object DailyReportGenerator {

    fun generateReport(messages: List<ChatMessage>): DailyConversationReport {
        val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        val userMessages = messages.filter { it.role == MessageRole.USER }
        val sanaMessages = messages.filter { it.role == MessageRole.SANA }

        if (userMessages.isEmpty()) {
            return DailyConversationReport(
                dateString = todayStr,
                totalInteractions = 0,
                topicsDiscussed = listOf("No conversations recorded yet today."),
                timeSections = listOf("Today"),
                keyPoints = listOf("SANA is ready to listen whenever you start speaking."),
                actionItemsAndPlans = listOf("Start hands-free conversation with SANA."),
                highlights = listOf("SANA V4 initialized and standing by for Boss.")
            )
        }

        // Extract topics based on keywords
        val topics = mutableListOf<String>()
        val actions = mutableListOf<String>()
        val keyPoints = mutableListOf<String>()
        val highlights = mutableListOf<String>()

        userMessages.forEach { msg ->
            val text = msg.text.lowercase()
            when {
                text.contains("math") || text.contains("science") || text.contains("teach") || text.contains("پڑھائی") -> {
                    if (!topics.contains("Personal Learning & Education")) topics.add("Personal Learning & Education")
                    keyPoints.add("Interactive study session on educational topics.")
                }
                text.contains("whatsapp") || text.contains("call") || text.contains("camera") || text.contains("torch") -> {
                    if (!topics.contains("Phone Control & System Automation")) topics.add("Phone Control & System Automation")
                    actions.add("Device operation executed: ${msg.text.take(30)}")
                }
                text.contains("trading") || text.contains("stock") || text.contains("crypto") || text.contains("forex") -> {
                    if (!topics.contains("Financial & Trading Intelligence")) topics.add("Financial & Trading Intelligence")
                    keyPoints.add("Market concepts, analysis, and risk management discussed.")
                }
                text.contains("image") || text.contains("thumbnail") || text.contains("picture") -> {
                    if (!topics.contains("Creative Visuals & Thumbnail Planning")) topics.add("Creative Visuals & Thumbnail Planning")
                    actions.add("Visual concept designed.")
                }
                text.contains("remember") || text.contains("yaad") -> {
                    if (!topics.contains("Memory Vault Update")) topics.add("Memory Vault Update")
                    keyPoints.add("Personal preference or fact added to user memory vault.")
                }
                else -> {
                    if (!topics.contains("General Voice Conversation")) topics.add("General Voice Conversation")
                }
            }
        }

        if (topics.isEmpty()) {
            topics.add("General Conversation & Assistant Queries")
        }

        // Time sections
        val firstMsgTime = messages.firstOrNull()?.let { timeFormat.format(Date(it.timestamp)) } ?: "Morning"
        val lastMsgTime = messages.lastOrNull()?.let { timeFormat.format(Date(it.timestamp)) } ?: "Now"
        val timeSections = listOf("Active Session: $firstMsgTime – $lastMsgTime")

        // Highlights
        highlights.add("Total user queries processed: ${userMessages.size}")
        highlights.add("Spoken AI responses generated: ${sanaMessages.size}")
        if (actions.isNotEmpty()) {
            highlights.add("Actions executed: ${actions.size}")
        } else {
            highlights.add("Pure conversational exchange with emotional companion tuning.")
        }

        if (keyPoints.isEmpty()) {
            keyPoints.add("Natural multilingual dialogue maintained smoothly.")
        }

        if (actions.isEmpty()) {
            actions.add("No pending device actions.")
        }

        return DailyConversationReport(
            dateString = todayStr,
            totalInteractions = userMessages.size + sanaMessages.size,
            topicsDiscussed = topics,
            timeSections = timeSections,
            keyPoints = keyPoints.take(5),
            actionItemsAndPlans = actions.take(5),
            highlights = highlights
        )
    }
}
