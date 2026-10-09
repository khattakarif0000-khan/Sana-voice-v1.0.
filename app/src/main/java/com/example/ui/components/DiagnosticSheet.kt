package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
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
import com.example.model.DiagnosticLevel
import com.example.model.DiagnosticNotice
import com.example.model.DiagnosticStatus
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber

@Composable
fun DiagnosticSheet(
    status: DiagnosticStatus,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onStopConversation: () -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
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
                    text = "Strict Status Accuracy — Operational vs Physical Verification",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanaTextSecondary
                )
            }
            IconButtonContent(onClick = onRetry)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // State Legend Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendIndicator(color = SanaNeonGreen, label = "VERIFIED")
                LegendIndicator(color = SanaWarningAmber, label = "CONFIGURED")
                LegendIndicator(color = SanaNeonRed, label = "FAILED / BLOCKED")
                LegendIndicator(color = Color(0xFF888E9E), label = "NOT TESTED")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Diagnostic Rows
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Gemini AI Brain
                DiagnosticItemRow(
                    name = "Gemini AI Brain Engine",
                    level = status.aiBrainLevel,
                    detail = status.aiBrainDetail
                )

                // 2. Microphone & Speech Recognizer
                DiagnosticItemRow(
                    name = "Microphone & Speech Input",
                    level = status.micLevel,
                    detail = status.micDetail
                )

                // 3. Gemini Native Voice Output
                DiagnosticItemRow(
                    name = "Gemini Native Audio Output",
                    level = status.geminiVoiceLevel,
                    detail = status.geminiVoiceDetail
                )

                // 4. ElevenLabs Premium Voice
                DiagnosticItemRow(
                    name = "ElevenLabs Premium Voice",
                    level = status.elevenLabsLevel,
                    detail = status.elevenLabsStatus
                )

                // 5. Automation & Routines (Configured)
                DiagnosticItemRow(
                    name = "Automation & Routines (Configured)",
                    level = status.routinesLevel,
                    detail = status.routinesDetail
                )

                // 6. Automation & Routines (Physical Verification)
                DiagnosticItemRow(
                    name = "Routines Physical Verification",
                    level = status.routinesPhysicalLevel,
                    detail = status.routinesPhysicalDetail
                )

                // 7. Smart Anti-Theft & Protection
                DiagnosticItemRow(
                    name = "Smart Anti-Theft Motion Protection",
                    level = status.antiTheftLevel,
                    detail = status.antiTheftStatus
                )

                // 8. Network Connectivity
                DiagnosticItemRow(
                    name = "Network Connectivity",
                    level = status.networkLevel,
                    detail = status.networkDetail
                )

                // 9. Battery Status
                DiagnosticItemRow(
                    name = "Battery Health & Level",
                    level = status.batteryLevel,
                    detail = "${status.batteryPct}% capacity"
                )

                if (status.recoveryAttempt > 0) {
                    DiagnosticItemRow(
                        name = "Self-Healing Recovery Engine",
                        level = DiagnosticLevel.VERIFIED_OPERATIONAL,
                        detail = "Auto-recovered (${status.recoveryAttempt} recovery cycles handled smoothly)"
                    )
                }
            }
        }

        // Diagnostic Error / Notice with Category, Message, and Recovery Action
        if (status.activeNotice != null || status.lastError != null) {
            Spacer(modifier = Modifier.height(14.dp))
            val notice = status.activeNotice ?: DiagnosticNotice(
                category = "System Engine",
                message = status.lastError ?: "Unknown warning",
                recoveryAction = "Review Settings or tap 'Re-test' to re-verify component health."
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF261217)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SanaNeonRed.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = SanaNeonRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Diagnostic Notice: ${notice.category}",
                            color = SanaNeonRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = notice.message,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = SanaWarningAmber,
                            modifier = Modifier.size(14.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recovery Action: ${notice.recoveryAction}",
                            color = SanaWarningAmber,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons
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
            Text("Close Diagnostics", color = Color.White)
        }
    }
}

@Composable
private fun DiagnosticItemRow(name: String, level: DiagnosticLevel, detail: String) {
    val (dotColor, badgeBg, badgeTextColor) = when (level) {
        DiagnosticLevel.VERIFIED_OPERATIONAL -> Triple(
            SanaNeonGreen,
            SanaNeonGreen.copy(alpha = 0.15f),
            SanaNeonGreen
        )
        DiagnosticLevel.CONFIGURED_UNVERIFIED -> Triple(
            SanaWarningAmber,
            SanaWarningAmber.copy(alpha = 0.15f),
            SanaWarningAmber
        )
        DiagnosticLevel.UNAVAILABLE_FAILED -> Triple(
            SanaNeonRed,
            SanaNeonRed.copy(alpha = 0.18f),
            SanaNeonRed
        )
        DiagnosticLevel.NOT_TESTED_INACTIVE -> Triple(
            Color(0xFF888E9E),
            Color(0x22888E9E),
            Color(0xFFB0B7C6)
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = detail, color = SanaTextMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }

        Box(
            modifier = Modifier
                .background(badgeBg, RoundedCornerShape(6.dp))
                .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(dotColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = level.label,
                    color = badgeTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LegendIndicator(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
