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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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

@Composable
fun SettingsSheet(
    settings: SanaSettingsData,
    isAiConnected: Boolean,
    onSaveSettings: (SanaSettingsData) -> Unit,
    onTestVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    var languageMode by remember { mutableStateOf(settings.languageMode) }
    var loveModeEnabled by remember { mutableStateOf(settings.loveModeEnabled) }
    var jealousyLevel by remember { mutableStateOf(settings.jealousyLevel) }
    var ttsSpeed by remember { mutableStateOf(settings.ttsSpeed) }
    var ttsPitch by remember { mutableStateOf(settings.ttsPitch) }
    var customApiKey by remember { mutableStateOf(settings.customApiKey) }
    var confirmSensitive by remember { mutableStateOf(settings.confirmSensitiveActions) }
    var voiceProvider by remember { mutableStateOf(settings.voiceProvider) }
    var elevenLabsApiKey by remember { mutableStateOf(settings.elevenLabsApiKey) }
    var elevenLabsVoiceId by remember { mutableStateOf(settings.elevenLabsVoiceId) }
    var elevenLabsModelId by remember { mutableStateOf(settings.elevenLabsModelId) }
    var antiTheftEnabled by remember { mutableStateOf(settings.antiTheftEnabled) }
    var showApiKey by remember { mutableStateOf(false) }
    var showElevenKey by remember { mutableStateOf(false) }

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
            Column {
                Text(
                    text = "SANA Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Personality, Voice & Brain Configuration",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanaTextSecondary
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Language Mode Selector
        Text(text = "Primary Conversation Language", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "auto" to "Auto (اردو / Eng)",
                "ur" to "Urdu (اردو)",
                "roman_ur" to "Roman Urdu",
                "en" to "English"
            ).forEach { (mode, label) ->
                val isSelected = languageMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) SanaNeonCyan.copy(alpha = 0.25f) else SanaSurfaceElevated,
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isSelected) SanaNeonCyan else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { languageMode = mode }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else SanaTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Love Mode & Personality Tuning
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Love & Affection Mode",
                            color = SanaNeonPink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Warm, deeply affectionate Urdu companion tone",
                            color = SanaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = loveModeEnabled,
                        onCheckedChange = { loveModeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SanaNeonPink,
                            checkedTrackColor = SanaNeonPink.copy(alpha = 0.4f),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = SanaSurfaceElevated
                        )
                    )
                }

                if (loveModeEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Playful Jealousy Intensity:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OFF", "Light", "Playful").forEach { level ->
                            val isSelected = jealousyLevel == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) SanaNeonPink.copy(alpha = 0.25f) else SanaSurfaceElevated,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) SanaNeonPink else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { jealousyLevel = level }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level,
                                    color = if (isSelected) Color.White else SanaTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Voice Provider & ElevenLabs Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Speech Voice Engine",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Ultra low-latency Gemini 24kHz vs ElevenLabs Premium",
                    color = SanaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isGemini = voiceProvider == "GEMINI_NATIVE"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isGemini) SanaNeonCyan.copy(alpha = 0.25f) else SanaSurfaceElevated,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (isGemini) SanaNeonCyan else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { voiceProvider = "GEMINI_NATIVE" }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Gemini Native", color = if (isGemini) Color.White else SanaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("24kHz Low-Latency", color = SanaNeonCyan, fontSize = 10.sp)
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
                            .border(
                                1.dp,
                                if (isEleven) SanaNeonPink else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { voiceProvider = "ELEVEN_LABS" }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ElevenLabs", color = if (isEleven) Color.White else SanaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Optional Premium", color = SanaNeonPink, fontSize = 10.sp)
                        }
                    }
                }

                if (voiceProvider == "ELEVEN_LABS") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ElevenLabs Model & Key:",
                        color = SanaNeonPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = elevenLabsApiKey,
                        onValueChange = { elevenLabsApiKey = it },
                        placeholder = { Text("Paste ElevenLabs API Key", color = SanaTextMuted, fontSize = 12.sp) },
                        visualTransformation = if (showElevenKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showElevenKey = !showElevenKey }) {
                                Icon(
                                    imageVector = if (showElevenKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Fallback Rule: If key is blank or call fails, SANA seamlessly falls back to Gemini Native without breaking speech.",
                        color = SanaTextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Voice Audition Button
        OutlinedButton(
            onClick = onTestVoice,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test Voice Audition", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Gemini API Key Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Gemini API Configuration",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Injected securely from AI Studio Secrets or entered here directly.",
                    color = SanaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customApiKey,
                    onValueChange = { customApiKey = it },
                    placeholder = { Text("Paste custom Gemini API Key (Optional)", color = SanaTextMuted, fontSize = 13.sp) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle key",
                                tint = SanaNeonCyan
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SanaNeonCyan,
                        unfocusedBorderColor = Color(0x3300E5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save & Apply Button
        Button(
            onClick = {
                val updated = settings.copy(
                    languageMode = languageMode,
                    loveModeEnabled = loveModeEnabled,
                    jealousyLevel = jealousyLevel,
                    ttsSpeed = ttsSpeed,
                    ttsPitch = ttsPitch,
                    customApiKey = customApiKey,
                    confirmSensitiveActions = confirmSensitive,
                    voiceProvider = voiceProvider,
                    elevenLabsApiKey = elevenLabsApiKey,
                    elevenLabsModelId = elevenLabsModelId,
                    elevenLabsVoiceId = elevenLabsVoiceId,
                    antiTheftEnabled = antiTheftEnabled
                )
                onSaveSettings(updated)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SanaNeonPink),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Save & Apply Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
