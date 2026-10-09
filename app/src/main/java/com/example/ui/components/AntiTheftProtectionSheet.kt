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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.phone.AntiTheftAlertState
import com.example.phone.AntiTheftSensitivity
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber

@Composable
fun AntiTheftProtectionSheet(
    isArmed: Boolean,
    alertState: AntiTheftAlertState,
    currentSensitivity: AntiTheftSensitivity,
    onToggleArm: (Boolean) -> Unit,
    onSetSensitivity: (AntiTheftSensitivity) -> Unit,
    onTestWarning: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSensitivity by remember { mutableStateOf(currentSensitivity) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isArmed) SanaNeonGreen else SanaNeonRed,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Smart Anti-Theft & Protection",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Owner Protection & Lifting Alert",
                        style = MaterialTheme.typography.bodySmall,
                        color = SanaTextSecondary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Armed Status Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isArmed) SanaNeonGreen.copy(alpha = 0.12f) else SanaSurfaceCard
            ),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isArmed) SanaNeonGreen.copy(alpha = 0.5f) else Color(0x22FFFFFF)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isArmed) "PROTECTION ARMED" else "PROTECTION DISARMED",
                        color = if (isArmed) SanaNeonGreen else SanaTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (alertState) {
                            AntiTheftAlertState.DISARMED -> "Arm SANA before placing phone down on table or charging."
                            AntiTheftAlertState.ARMED_MONITORING -> "Actively monitoring accelerometer. Phone is protected."
                            AntiTheftAlertState.WARNING_1 -> "⚠️ WARNING 1 TRIGGERED: 'This is my Boss’s phone.'"
                            AntiTheftAlertState.WARNING_2 -> "🚨 WARNING 2 TRIGGERED: Put phone down immediately!"
                            AntiTheftAlertState.ALARM_ACTIVE -> "🔴 ALARM ACTIVE: Siren sounding & vibration active."
                        },
                        color = if (alertState == AntiTheftAlertState.ALARM_ACTIVE) SanaNeonRed else SanaTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isArmed,
                    onCheckedChange = { onToggleArm(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SanaNeonGreen,
                        checkedTrackColor = SanaNeonGreen.copy(alpha = 0.35f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = SanaSurfaceElevated
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sensitivity Selection
        Text(
            text = "Motion Sensitivity Threshold",
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AntiTheftSensitivity.values().forEach { sens ->
                val isSelected = selectedSensitivity == sens
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
                        .clickable {
                            selectedSensitivity = sens
                            onSetSensitivity(sens)
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = sens.label.split(" ").first(),
                        color = if (isSelected) Color.White else SanaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Warning Behavior Specification Note
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Protection Protocol (Section 15)",
                    color = SanaNeonCyan,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Sustained lifting/tilting triggers Warning 1:\n   \"This is my Boss’s phone. Please put it back.\"\n2. If movement continues within 5s: Warning 2.\n3. If handling persists: Loud emergency alert tone & vibration activate until Boss disarms.",
                    color = SanaTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onTestWarning(1) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Warning 1", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onTestWarning(2) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaWarningAmber)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Warning 2", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Disarm / Arm Button
        Button(
            onClick = {
                onToggleArm(!isArmed)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isArmed) SanaNeonRed else SanaNeonGreen
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = if (isArmed) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isArmed) "Disarm Protection Now (غیر فعال کریں)" else "Arm SANA Protection (چوری سے تحفظ فعال کریں)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}
