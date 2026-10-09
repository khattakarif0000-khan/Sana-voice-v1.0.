package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SanaSettingsData
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber

data class VoiceOption(val id: String, val name: String, val desc: String)
data class ModelOption(val id: String, val name: String, val desc: String)

@Composable
fun ElevenLabsModal(
    settings: SanaSettingsData,
    onSaveSettings: (SanaSettingsData) -> Unit,
    onTestVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    var voiceProvider by remember { mutableStateOf(settings.voiceProvider) }
    var selectedModel by remember { mutableStateOf(settings.elevenLabsModelId.ifBlank { "eleven_multilingual_v2" }) }
    var selectedVoice by remember { mutableStateOf(settings.elevenLabsVoiceId.ifBlank { "21m00Tcm4TlvDq8ikWAM" }) }
    var apiKey by remember { mutableStateOf(settings.elevenLabsApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var stability by remember { mutableStateOf(settings.elevenLabsStability) }
    var similarityBoost by remember { mutableStateOf(settings.elevenLabsSimilarity) }
    var outputFormat by remember { mutableStateOf(settings.elevenLabsOutputFormat) }
    var languageConfig by remember { mutableStateOf("Auto / Multilingual (Urdu + Roman Urdu + English)") }

    val availableModels = listOf(
        ModelOption("eleven_multilingual_v2", "Multilingual v2 (Recommended)", "Real support for Urdu, English, Hindi, Arabic"),
        ModelOption("eleven_turbo_v2_5", "Turbo v2.5", "Ultra-low latency multilingual model"),
        ModelOption("eleven_flash_v2_5", "Flash v2.5", "Lowest latency real-time voice streaming")
    )

    val availableVoices = listOf(
        VoiceOption("21m00Tcm4TlvDq8ikWAM", "Rachel", "Warm, natural & expressive female voice"),
        VoiceOption("EXAVITQu4vr4xnSDxMaL", "Bella", "Friendly, calm female tone"),
        VoiceOption("ErXwobaYiN019PkySvjV", "Antoni", "Polite, professional male tone"),
        VoiceOption("VR6AewLTigWG4xSOukaG", "Arnold", "Authoritative, confident tone")
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(SanaNeonPink.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = SanaNeonPink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Voice Provider & Model Selection",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Gemini Native 24kHz vs ElevenLabs Premium",
                        style = MaterialTheme.typography.bodySmall,
                        color = SanaTextSecondary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Voice Provider Selector
        Text(text = "Voice Provider (صوتی نظام)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isGemini = voiceProvider == "GEMINI_NATIVE"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isGemini) SanaNeonCyan.copy(alpha = 0.25f) else SanaSurfaceElevated,
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, if (isGemini) SanaNeonCyan else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { voiceProvider = "GEMINI_NATIVE" }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gemini Native Voice", color = if (isGemini) Color.White else SanaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("24kHz Low Latency (Default)", color = SanaNeonCyan, fontSize = 10.sp)
                }
            }

            val isEleven = voiceProvider == "ELEVEN_LABS"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isEleven) SanaNeonPink.copy(alpha = 0.25f) else SanaSurfaceElevated,
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, if (isEleven) SanaNeonPink else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { voiceProvider = "ELEVEN_LABS" }
                    .padding(vertical = 12.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ElevenLabs Premium", color = if (isEleven) Color.White else SanaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Optional Expressive Voice", color = SanaNeonPink, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (voiceProvider == "ELEVEN_LABS") {
            // ElevenLabs Configuration Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SanaNeonPink.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ElevenLabs Model Selection",
                        color = SanaNeonPink,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Choose only real supported ElevenLabs multilingual models",
                        color = SanaTextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    availableModels.forEach { model ->
                        val isSelected = selectedModel == model.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) SanaNeonPink.copy(alpha = 0.2f) else SanaSurfaceElevated,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, if (isSelected) SanaNeonPink else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedModel = model.id }
                                .padding(10.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(model.name, color = if (isSelected) Color.White else SanaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SanaNeonPink, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Text(model.desc, color = SanaTextMuted, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Selection
                    Text(
                        text = "Voice Character Selection:",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    availableVoices.forEach { voice ->
                        val isSelected = selectedVoice == voice.id
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(
                                    if (isSelected) SanaNeonCyan.copy(alpha = 0.2f) else SanaSurfaceElevated,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(1.dp, if (isSelected) SanaNeonCyan else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedVoice = voice.id }
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(voice.name, color = if (isSelected) Color.White else SanaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(voice.desc, color = SanaTextMuted, fontSize = 10.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SanaNeonCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stability and Similarity Sliders
                    Text(
                        text = "Voice Stability: ${(stability * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = stability,
                        onValueChange = { stability = it },
                        valueRange = 0.2f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = SanaNeonPink, activeTrackColor = SanaNeonPink)
                    )

                    Text(
                        text = "Similarity Boost: ${(similarityBoost * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = similarityBoost,
                        onValueChange = { similarityBoost = it },
                        valueRange = 0.3f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = SanaNeonCyan, activeTrackColor = SanaNeonCyan)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // API Key Field (Optional override / AI Studio Secrets primary)
                    Text(
                        text = "ElevenLabs API Key (Optional local override):",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        placeholder = { Text("AI Studio Secrets or paste key here", color = SanaTextMuted, fontSize = 11.sp) },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key",
                                    tint = SanaNeonPink
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SanaNeonPink,
                            unfocusedBorderColor = Color(0x33FF007F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Fallback Rule Display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B202D)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = SanaNeonCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Zero Silence Guarantee: If key is missing or ElevenLabs call fails, SANA seamlessly speaks via Gemini Native voice without interruption.",
                                color = SanaNeonCyan,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            // Gemini Native Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SanaNeonGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini Native Voice Active", color = SanaNeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ultra-fast direct PCM 24kHz stream through Android AudioTrack. Natural Pakistani Urdu and English phrasing.",
                        color = SanaTextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Test Voice Audition Button
        OutlinedButton(
            onClick = onTestVoice,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test Voice Audition (صدا کی آزمائش)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Save Voice Settings Button
        Button(
            onClick = {
                val updated = settings.copy(
                    voiceProvider = voiceProvider,
                    elevenLabsModelId = selectedModel,
                    elevenLabsVoiceId = selectedVoice,
                    elevenLabsApiKey = apiKey,
                    elevenLabsStability = stability,
                    elevenLabsSimilarity = similarityBoost,
                    elevenLabsOutputFormat = outputFormat
                )
                onSaveSettings(updated)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SanaNeonPink),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Save Voice Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
