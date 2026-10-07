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
import androidx.compose.material.icons.filled.Favorite
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
import com.example.ui.theme.SanaNeonBlue
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonPurple
import com.example.ui.theme.SanaNeonRed
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
    var showApiKey by remember { mutableStateOf(false) }

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
            Box(
                modifier = Modifier
                    .background(
                        if (isAiConnected) SanaNeonGreen.copy(alpha = 0.15f) else SanaNeonRed.copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    )
                    .border(
                        1.dp,
                        if (isAiConnected) SanaNeonGreen else SanaNeonRed,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isAiConnected) "AI CONNECTED" else "AI KEY NEEDED",
                    color = if (isAiConnected) SanaNeonGreen else SanaNeonRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Language Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Conversation Language",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Default is Urdu (اردو), with English & Roman Urdu fluently supported.",
                    color = SanaTextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val langs = listOf(
                        "auto" to "Auto",
                        "ur" to "اردو (Urdu)",
                        "en" to "English",
                        "roman_ur" to "Roman Urdu"
                    )
                    langs.forEach { (key, label) ->
                        val selected = languageMode == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) SanaNeonPink else SanaSurfaceElevated,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { languageMode = key }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color.White else SanaTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Love Mode & Playful Jealousy (Section 11)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Love Mode",
                            tint = SanaNeonPink,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Love / Companion Mode",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Affection, caring, warm emotional companionship",
                                color = SanaTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Switch(
                        checked = loveModeEnabled,
                        onCheckedChange = { loveModeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SanaNeonPink,
                            uncheckedTrackColor = SanaSurfaceElevated
                        )
                    )
                }

                if (loveModeEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Playful Jealousy Level:",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OFF", "Light", "Playful").forEach { level ->
                            val selected = jealousyLevel == level
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selected) SanaNeonPurple else SanaSurfaceElevated,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { jealousyLevel = level }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = level,
                                    color = if (selected) Color.White else SanaTextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Safety Note: Jealousy is strictly playful and fictional. SANA will never manipulate, guilt-trip, isolate, or claim ownership.",
                        color = SanaTextMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Voice Tuning (Speed & Pitch & Audition)
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
                    Text(
                        text = "Voice Tone & Speed",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    OutlinedButton(
                        onClick = onTestVoice,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Voice", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Speech Speed: ${String.format("%.2f", ttsSpeed)}x",
                    color = SanaTextSecondary,
                    fontSize = 13.sp
                )
                Slider(
                    value = ttsSpeed,
                    onValueChange = { ttsSpeed = it },
                    valueRange = 0.8f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = SanaNeonCyan,
                        activeTrackColor = SanaNeonCyan,
                        inactiveTrackColor = SanaSurfaceElevated
                    )
                )

                Text(
                    text = "Voice Pitch: ${String.format("%.2f", ttsPitch)}x",
                    color = SanaTextSecondary,
                    fontSize = 13.sp
                )
                Slider(
                    value = ttsPitch,
                    onValueChange = { ttsPitch = it },
                    valueRange = 0.8f..1.4f,
                    colors = SliderDefaults.colors(
                        thumbColor = SanaNeonPink,
                        activeTrackColor = SanaNeonPink,
                        inactiveTrackColor = SanaSurfaceElevated
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Gemini API Key Configuration
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
                    confirmSensitiveActions = confirmSensitive
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
