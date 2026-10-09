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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import com.example.data.RoutineEntity
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextSecondary

@Composable
fun RoutinesSheet(
    routines: List<RoutineEntity>,
    onToggleRoutine: (Long, Boolean) -> Unit,
    onRunNow: (RoutineEntity) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onCreateRoutine: (title: String, trigger: String, action: String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newTrigger by remember { mutableStateOf("Manual / On Demand") }
    var newAction by remember { mutableStateOf("BATTERY_CHECK") }

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
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = SanaNeonPink,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Routines & Automation",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Scheduled Actions & Life Assistant Tasks",
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

        // Create Routine Dialog / Form
        if (showCreateDialog) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SanaNeonPink.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Add New Automation Routine", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Routine Name (e.g. Morning Briefing)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SanaNeonPink,
                            unfocusedBorderColor = Color(0x33FF007F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTrigger,
                        onValueChange = { newTrigger = it },
                        label = { Text("Trigger Schedule (e.g. 08:00 AM Daily)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SanaNeonPink,
                            unfocusedBorderColor = Color(0x33FF007F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showCreateDialog = false }) {
                            Text("Cancel", color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank()) {
                                    onCreateRoutine(newTitle, newTrigger, newAction)
                                    newTitle = ""
                                    showCreateDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SanaNeonPink)
                        ) {
                            Text("Save Routine")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Add Routine Button
        if (!showCreateDialog) {
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = SanaNeonPink),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create New Routine", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Routines List
        if (routines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No routines configured yet.", color = SanaTextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(routines, key = { it.id }) { routine ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = routine.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Trigger: ${routine.triggerDescription}",
                                        color = SanaNeonCyan,
                                        fontSize = 12.sp
                                    )
                                }
                                Switch(
                                    checked = routine.isEnabled,
                                    onCheckedChange = { onToggleRoutine(routine.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SanaNeonGreen,
                                        checkedTrackColor = SanaNeonGreen.copy(alpha = 0.35f),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = SanaSurfaceElevated
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status: ${routine.lastRunStatus}",
                                    color = SanaTextSecondary,
                                    fontSize = 11.sp
                                )

                                Row {
                                    OutlinedButton(
                                        onClick = { onRunNow(routine) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonCyan),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Run Now", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { onDeleteRoutine(routine.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SanaNeonRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
