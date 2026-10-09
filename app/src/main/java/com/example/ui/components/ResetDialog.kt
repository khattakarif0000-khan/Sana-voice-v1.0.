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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber

enum class ResetOptionType {
    SESSION,
    SETTINGS,
    MEMORY,
    FULL
}

@Composable
fun ResetSanaModal(
    onResetSession: () -> Unit,
    onResetSettings: () -> Unit,
    onClearMemory: () -> Unit,
    onFullCleanReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var pendingConfirmation by remember { mutableStateOf<ResetOptionType?>(null) }

    if (pendingConfirmation != null) {
        val target = pendingConfirmation!!
        val title = when (target) {
            ResetOptionType.SESSION -> "Reset Current Session?"
            ResetOptionType.SETTINGS -> "Reset App Settings to Default?"
            ResetOptionType.MEMORY -> "Clear All Personal Memory?"
            ResetOptionType.FULL -> "Perform Full Clean Reset of SANA?"
        }
        val description = when (target) {
            ResetOptionType.SESSION -> "Clears active conversation, stops playback/listening, and returns SANA to IDLE."
            ResetOptionType.SETTINGS -> "Restores default voice, personality, language, and sensitivity settings."
            ResetOptionType.MEMORY -> "Permanently removes all saved personal memories from local storage. Cannot be undone."
            ResetOptionType.FULL -> "Halts microphone, stops all audio playback, disarms sensors, clears conversation, and restarts SANA fresh to IDLE."
        }
        val confirmColor = if (target == ResetOptionType.MEMORY || target == ResetOptionType.FULL) SanaNeonRed else SanaNeonCyan

        AlertDialog(
            onDismissRequest = { pendingConfirmation = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (target == ResetOptionType.FULL) Icons.Default.Warning else Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = confirmColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(description, color = SanaTextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (target) {
                            ResetOptionType.SESSION -> onResetSession()
                            ResetOptionType.SETTINGS -> onResetSettings()
                            ResetOptionType.MEMORY -> onClearMemory()
                            ResetOptionType.FULL -> onFullCleanReset()
                        }
                        pendingConfirmation = null
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = confirmColor)
                ) {
                    Text(
                        text = if (target == ResetOptionType.FULL) "Confirm Full Reset" else "Confirm",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingConfirmation = null }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = SanaSurfaceCard
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "♻ Reset SANA (دوبارہ ترتیب دیں)",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Clean, safe options to clear state or restore defaults",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanaTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ResetOptionCard(
            title = "1. Reset Current Session",
            subtitle = "Clears active chat, stops microphone/speech, returns to IDLE",
            icon = Icons.Default.Refresh,
            color = SanaNeonCyan,
            onClick = { pendingConfirmation = ResetOptionType.SESSION }
        )

        Spacer(modifier = Modifier.height(10.dp))

        ResetOptionCard(
            title = "2. Reset App Settings",
            subtitle = "Restores default UI preferences, voice, and sensitivity",
            icon = Icons.Default.SettingsBackupRestore,
            color = SanaNeonPink,
            onClick = { pendingConfirmation = ResetOptionType.SETTINGS }
        )

        Spacer(modifier = Modifier.height(10.dp))

        ResetOptionCard(
            title = "3. Clear Personal Memory",
            subtitle = "Permanently removes user-saved facts (requires confirmation)",
            icon = Icons.Default.DeleteSweep,
            color = SanaWarningAmber,
            onClick = { pendingConfirmation = ResetOptionType.MEMORY }
        )

        Spacer(modifier = Modifier.height(10.dp))

        ResetOptionCard(
            title = "4. Full Clean Reset",
            subtitle = "Halts audio/mic/camera, cleans state, restarts SANA fresh",
            icon = Icons.Default.RestartAlt,
            color = SanaNeonRed,
            isDestructive = true,
            onClick = { pendingConfirmation = ResetOptionType.FULL }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SanaSurfaceElevated)
        ) {
            Text("Close", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResetOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isDestructive) Color(0xFF261217) else SanaSurfaceCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            color.copy(alpha = if (isDestructive) 0.6f else 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = SanaTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}
