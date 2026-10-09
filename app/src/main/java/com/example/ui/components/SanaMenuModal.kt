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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SanaNeonBlue
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonPurple
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber

data class MenuItemData(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color
)

@Composable
fun SanaMenuModal(
    onSelectOption: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val menuItems = listOf(
        MenuItemData("VOICE", "Voice Assistant", "Hands-free speech", Icons.Default.Mic, SanaNeonCyan),
        MenuItemData("TYPE_MESSAGE", "Type Message", "Multilingual text AI", Icons.Default.Keyboard, SanaNeonCyan),
        MenuItemData("MEMORY", "Memory Vault", "Safe local memories", Icons.Default.Psychology, SanaNeonPurple),
        MenuItemData("TEACHING", "Personal Teaching", "Urdu & dialects education", Icons.Default.School, SanaNeonGreen),
        MenuItemData("CAMERA_VISION", "Visual / Camera", "Visual inspection & OCR", Icons.Default.CameraAlt, SanaNeonCyan),
        MenuItemData("TRADING", "Trading Intelligence", "Chart scan & RSI/support", Icons.Default.TrendingUp, SanaWarningAmber),
        MenuItemData("ANTI_THEFT", "Smart Anti-Theft", "Owner motion defense", Icons.Default.Shield, SanaNeonRed),
        MenuItemData("ROUTINES", "Routines & Automation", "Daily recurring tasks", Icons.Default.Schedule, SanaNeonPink),
        MenuItemData("COMPANION", "Companion & Love", "Emotional warmth", Icons.Default.Favorite, SanaNeonPink),
        MenuItemData("IMAGE_GEN", "Image Generation", "16:9 thumbnails & art", Icons.Default.Image, SanaNeonBlue),
        MenuItemData("PHONE_CONTROL", "Phone Control", "WhatsApp, calls & torch", Icons.Default.PhoneAndroid, SanaNeonCyan),
        MenuItemData("NOTIFICATIONS", "Daily Report", "Conversation analytics", Icons.Default.Notifications, SanaNeonGreen),
        MenuItemData("LANGUAGES", "Languages", "Urdu, Roman, English", Icons.Default.Language, SanaNeonBlue),
        MenuItemData("VOICE_SETTINGS", "Voice Settings", "Gemini & ElevenLabs", Icons.Default.RecordVoiceOver, SanaNeonPink),
        MenuItemData("DIAGNOSTICS", "Diagnostics", "Truthful system verification", Icons.Default.Info, SanaWarningAmber),
        MenuItemData("SETTINGS", "Settings", "Personality & keys", Icons.Default.Settings, Color.White),
        MenuItemData("PERMISSIONS", "Permissions & Privacy", "Android device access", Icons.Default.Security, SanaNeonGreen),
        MenuItemData("RESET_SANA", "Reset SANA", "Clean session or restore", Icons.Default.RestartAlt, SanaNeonRed)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "☰ SANA V4 MENU",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unified command hub — All assistant features accessible in one place",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanaTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(menuItems, key = { it.id }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectOption(item.id)
                            onDismiss()
                        },
                    colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, item.tint.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(item.tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.subtitle,
                            color = SanaTextMuted,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
