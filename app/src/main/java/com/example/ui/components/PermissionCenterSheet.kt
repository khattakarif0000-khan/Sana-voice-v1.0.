package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary

data class PermissionItem(
    val permission: String,
    val title: String,
    val rationale: String,
    val isCritical: Boolean = false
)

@Composable
fun PermissionCenterSheet(
    onRequestPermission: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val permissionsList = listOf(
        PermissionItem(
            permission = android.Manifest.permission.RECORD_AUDIO,
            title = "Microphone (Record Audio)",
            rationale = "Essential for hands-free voice conversation and speech recognition.",
            isCritical = true
        ),
        PermissionItem(
            permission = android.Manifest.permission.CALL_PHONE,
            title = "Phone Calling",
            rationale = "Allows direct hands-free calling when you command 'Call Ali'. (Dialer is used if ungranted).",
            isCritical = false
        ),
        PermissionItem(
            permission = android.Manifest.permission.READ_CONTACTS,
            title = "Contacts Access",
            rationale = "Allows looking up contact names to find their phone numbers for voice calls.",
            isCritical = false
        ),
        PermissionItem(
            permission = android.Manifest.permission.CAMERA,
            title = "Camera",
            rationale = "Allows launching camera and visual understanding ('What is this?').",
            isCritical = false
        ),
        PermissionItem(
            permission = android.Manifest.permission.POST_NOTIFICATIONS,
            title = "Notifications",
            rationale = "Required for persistent foreground service during hands-free conversations.",
            isCritical = true
        ),
        PermissionItem(
            permission = android.Manifest.permission.ACCESS_FINE_LOCATION,
            title = "Location",
            rationale = "Provides real-time local weather and prayer time answers.",
            isCritical = false
        )
    )

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
                    text = "Permission Center",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Transparent Android security & device access",
                    style = MaterialTheme.typography.bodySmall,
                    color = SanaTextSecondary
                )
            }
            OutlinedButton(
                onClick = { openAppSettings(context) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan)
            ) {
                Text("App Settings", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(permissionsList) { item ->
                val isGranted = ContextCompat.checkSelfPermission(
                    context,
                    item.permission
                ) == PackageManager.PERMISSION_GRANTED

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                if (item.isCritical) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "REQUIRED",
                                        color = SanaNeonPink,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.rationale,
                                color = SanaTextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (isGranted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Granted",
                                    tint = SanaNeonGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text("Granted", color = SanaNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { onRequestPermission(item.permission) },
                                colors = ButtonDefaults.buttonColors(containerColor = SanaNeonPink),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Allow", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Notification Access listener special item
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification Listener Access",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Allows SANA to read and summarize incoming notifications when requested.",
                                color = SanaTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan)
                        ) {
                            Text("Configure", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SanaNeonCyan),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
