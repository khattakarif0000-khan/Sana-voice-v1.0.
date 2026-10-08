package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DiagnosticStatus
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary

@Composable
fun DiagnosticSheet(
    status: DiagnosticStatus,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onStopConversation: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    text = "System Diagnostics",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time engine health & self-healing monitor",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanaTextSecondary
                )
            }
            IconButtonContent(onClick = onRetry)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Grid / Cards
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DiagnosticRow(
                    name = "Microphone & Speech Recognizer",
                    isHealthy = status.micReady,
                    detail = if (status.micReady) "Operational & Ready" else "Requires Record Permission or Init"
                )
                DiagnosticRow(
                    name = "Gemini AI Brain Connection",
                    isHealthy = status.aiConfigured,
                    detail = if (status.aiConfigured) "Configured & Active" else "API Key Missing / Not Provided"
                )
                DiagnosticRow(
                    name = "Gemini Native Voice Output",
                    isHealthy = status.geminiVoiceReady,
                    detail = if (status.geminiVoiceReady) "24kHz AudioTrack Speaker Ready" else "Audio engine ready"
                )
                DiagnosticRow(
                    name = "Network Connectivity",
                    isHealthy = status.networkAvailable,
                    detail = if (status.networkAvailable) "Connected" else "Offline (Using local command fallback)"
                )
                DiagnosticRow(
                    name = "Device Battery Level",
                    isHealthy = status.batteryPct >= 15,
                    detail = "${status.batteryPct}% capacity"
                )
                if (status.recoveryAttempt > 0) {
                    DiagnosticRow(
                        name = "Self-Healing State",
                        isHealthy = true,
                        detail = "Auto-recovered (${status.recoveryAttempt} attempts handled smoothly)"
                    )
                }
            }
        }

        if (status.lastError != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1017)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = SanaNeonRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Last diagnostic notice: ${status.lastError}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Settings", fontSize = 13.sp)
            }

            Button(
                onClick = onStopConversation,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = SanaNeonRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Stop Loop", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SanaSurfaceElevated),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Close", color = Color.White)
        }
    }
}

@Composable
private fun DiagnosticRow(name: String, isHealthy: Boolean, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = detail, color = SanaTextMuted, fontSize = 11.sp)
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (isHealthy) SanaNeonGreen else SanaNeonRed, CircleShape)
        )
    }
}

@Composable
private fun IconButtonContent(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan)
    ) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Re-test", fontSize = 12.sp)
    }
}
