package com.example.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ChatMessage
import com.example.model.ConversationState
import com.example.model.MessageRole
import com.example.phone.AntiTheftAlertState
import com.example.phone.AntiTheftSensitivity
import com.example.ui.components.AntiTheftProtectionSheet
import com.example.ui.components.DailyReportSheet
import com.example.ui.components.DiagnosticSheet
import com.example.ui.components.MemoryVaultSheet
import com.example.ui.components.PermissionCenterSheet
import com.example.ui.components.RoutinesSheet
import com.example.ui.components.SanaOrb
import com.example.ui.components.SettingsSheet
import com.example.ui.components.TeachingModeSheet
import com.example.ui.components.VisionModal
import com.example.ui.theme.SanaBlack
import com.example.ui.theme.SanaNeonBlue
import com.example.ui.theme.SanaNeonCyan
import com.example.ui.theme.SanaNeonGreen
import com.example.ui.theme.SanaNeonPink
import com.example.ui.theme.SanaNeonPurple
import com.example.ui.theme.SanaNeonRed
import com.example.ui.theme.SanaSurfaceCard
import com.example.ui.theme.SanaSurfaceDark
import com.example.ui.theme.SanaSurfaceElevated
import com.example.ui.theme.SanaTextMuted
import com.example.ui.theme.SanaTextPrimary
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber
import com.example.viewmodel.SanaViewModel

enum class ActiveSheet {
    NONE,
    SETTINGS,
    MEMORY,
    PERMISSIONS,
    DIAGNOSTICS,
    VISION,
    TEACHING,
    ANTI_THEFT,
    DAILY_REPORT,
    ROUTINES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanaScreen(
    viewModel: SanaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.conversationState.collectAsState()
    val emotion by viewModel.currentEmotion.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val diagnosticStatus by viewModel.diagnosticStatus.collectAsState()
    val memories by viewModel.memories.collectAsState()
    val teachingSession by viewModel.teachingSession.collectAsState()
    val routines by viewModel.routines.collectAsState()
    val isAntiTheftArmed by viewModel.isAntiTheftArmed.collectAsState()
    val antiTheftAlertState by viewModel.antiTheftAlertState.collectAsState()
    val dailyReport by viewModel.dailyReport.collectAsState()
    val socialDraft by viewModel.socialDraft.collectAsState()
    val isHandsFree by viewModel.isHandsFreeActive.collectAsState()
    val audioWaveLevel by viewModel.audioWaveLevel.collectAsState()

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val listState = rememberLazyListState()

    // Auto-scroll when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Permission request launcher
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startConversation(true)
        }
    }

    val genericPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.updateDiagnostics()
    }

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        containerColor = SanaBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Top Bar (Header with Quick Tool Icons)
            TopHeaderBar(
                isAiConnected = diagnosticStatus.aiConfigured,
                isAntiTheftArmed = isAntiTheftArmed,
                onOpenPermissions = { activeSheet = ActiveSheet.PERMISSIONS },
                onOpenMemory = { activeSheet = ActiveSheet.MEMORY },
                onOpenSettings = { activeSheet = ActiveSheet.SETTINGS },
                onOpenDiagnostics = { activeSheet = ActiveSheet.DIAGNOSTICS },
                onOpenAntiTheft = { activeSheet = ActiveSheet.ANTI_THEFT }
            )

            // Anti-Theft Active Alert Banner (Section 15)
            if (antiTheftAlertState != AntiTheftAlertState.DISARMED) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(
                            if (antiTheftAlertState == AntiTheftAlertState.ALARM_ACTIVE) SanaNeonRed.copy(alpha = 0.25f)
                            else SanaNeonGreen.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            if (antiTheftAlertState == AntiTheftAlertState.ALARM_ACTIVE) SanaNeonRed
                            else SanaNeonGreen.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (antiTheftAlertState == AntiTheftAlertState.ALARM_ACTIVE) SanaNeonRed else SanaNeonGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (antiTheftAlertState) {
                                    AntiTheftAlertState.ARMED_MONITORING -> "Anti-Theft Active (Monitoring Motion)"
                                    AntiTheftAlertState.WARNING_1 -> "⚠️ Anti-Theft Warning 1 Triggered!"
                                    AntiTheftAlertState.WARNING_2 -> "🚨 Anti-Theft Warning 2: Put Phone Down!"
                                    AntiTheftAlertState.ALARM_ACTIVE -> "🔴 ALARM ACTIVE — Unauthorized Movement!"
                                    else -> ""
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Disarm (غیر فعال کریں)",
                            color = SanaNeonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.disarmAntiTheft() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 2. Central AI Pulsing Orb & Waveform
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SanaOrb(
                    state = state,
                    audioLevel = audioWaveLevel,
                    modifier = Modifier.size(130.dp),
                    orbSize = 130.dp
                )
            }

            // 3. Status Text Banner (State & Emotion label)
            StateStatusBanner(
                state = state,
                emotion = emotion,
                isHandsFree = isHandsFree,
                onInterrupted = {
                    if (state == ConversationState.SPEAKING) {
                        viewModel.handleUserInterruption()
                    } else if (state == ConversationState.LISTENING) {
                        viewModel.processUserInput("...")
                    }
                }
            )

            // 4. Quick Action Chips (V4 upgraded: Teaching, Vision, Anti-Theft, Daily Report, Routines, Tools)
            QuickActionChipsRow(
                onActionSelected = { prompt ->
                    viewModel.processUserInput(prompt)
                },
                onOpenVision = { activeSheet = ActiveSheet.VISION },
                onOpenTeaching = { activeSheet = ActiveSheet.TEACHING },
                onOpenAntiTheft = { activeSheet = ActiveSheet.ANTI_THEFT },
                onOpenDailyReport = {
                    viewModel.generateDailyReport()
                    activeSheet = ActiveSheet.DAILY_REPORT
                },
                onOpenRoutines = { activeSheet = ActiveSheet.ROUTINES }
            )

            // Dedicated Active Teaching Mode Banner
            if (teachingSession.isTeachingActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .background(SanaNeonGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .border(1.dp, SanaNeonGreen.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🎓 ${teachingSession.subject} (Step ${teachingSession.step})",
                            color = SanaNeonGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "خروج (Exit)",
                            color = SanaNeonRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { viewModel.exitTeachingSession() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Verified Social Comment Review Card (Section 14)
            if (socialDraft.isDraftActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = SanaSurfaceCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SanaNeonCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Verified Social Draft (${socialDraft.platform})", color = SanaNeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Explicit Review Required", color = SanaTextMuted, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\"${socialDraft.draftComment}\"", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.cancelSocialDraft() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SanaNeonRed)
                            ) {
                                Text("Discard", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { viewModel.confirmAndShareSocialDraft() },
                                colors = ButtonDefaults.buttonColors(containerColor = SanaNeonGreen)
                            ) {
                                Text("Confirm & Post", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 5. Conversation Transcript Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            // 6. Bottom Controls: Hands-Free Mic Button & Stop Button
            BottomControlBar(
                state = state,
                isHandsFreeActive = isHandsFree,
                onToggleMic = {
                    if (hasRecordPermission()) {
                        viewModel.toggleConversation(true)
                    } else {
                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStop = { viewModel.stopConversation() }
            )
        }
    }

    // Modal Bottom Sheets
    if (activeSheet != ActiveSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = sheetState,
            containerColor = SanaSurfaceDark,
            scrimColor = Color.Black.copy(alpha = 0.75f)
        ) {
            when (activeSheet) {
                ActiveSheet.SETTINGS -> SettingsSheet(
                    settings = settings,
                    isAiConnected = diagnosticStatus.aiConfigured,
                    onSaveSettings = { viewModel.updateSettings(it) },
                    onTestVoice = { viewModel.testVoiceAudition() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.MEMORY -> MemoryVaultSheet(
                    memories = memories,
                    onAddMemory = { viewModel.addManualMemory(it) },
                    onDeleteMemory = { viewModel.deleteMemory(it) },
                    onClearAll = { viewModel.clearAllMemories() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.PERMISSIONS -> PermissionCenterSheet(
                    onRequestPermission = { perm ->
                        genericPermissionLauncher.launch(perm)
                    },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.DIAGNOSTICS -> DiagnosticSheet(
                    status = diagnosticStatus,
                    onRetry = { viewModel.updateDiagnostics() },
                    onOpenSettings = { activeSheet = ActiveSheet.SETTINGS },
                    onStopConversation = { viewModel.stopConversation() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.VISION -> VisionModal(
                    onAnalyzeImage = { prompt, base64 ->
                        viewModel.processUserInput(prompt, base64)
                    },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.TEACHING -> TeachingModeSheet(
                    teachingSession = teachingSession,
                    onStartTeaching = { subject, level, language ->
                        viewModel.startTeachingSession(subject, level, language)
                    },
                    onExitTeaching = { viewModel.exitTeachingSession() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.ANTI_THEFT -> AntiTheftProtectionSheet(
                    isArmed = isAntiTheftArmed,
                    alertState = antiTheftAlertState,
                    currentSensitivity = try { AntiTheftSensitivity.valueOf(settings.antiTheftSensitivity) } catch (e: Exception) { AntiTheftSensitivity.MEDIUM },
                    onToggleArm = { arm ->
                        if (arm) viewModel.armAntiTheft() else viewModel.disarmAntiTheft()
                    },
                    onSetSensitivity = { sens -> viewModel.setAntiTheftSensitivity(sens) },
                    onTestWarning = { num -> viewModel.testAntiTheftWarning(num) },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.DAILY_REPORT -> DailyReportSheet(
                    report = dailyReport,
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.ROUTINES -> RoutinesSheet(
                    routines = routines,
                    onToggleRoutine = { id, en -> viewModel.toggleRoutine(id, en) },
                    onRunNow = { routine -> viewModel.runRoutineNow(routine) },
                    onDeleteRoutine = { id -> viewModel.deleteRoutine(id) },
                    onCreateRoutine = { title, trigger, action -> viewModel.createRoutine(title, trigger, action) },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )
                ActiveSheet.NONE -> {}
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    isAiConnected: Boolean,
    isAntiTheftArmed: Boolean,
    onOpenPermissions: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAntiTheft: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "SANA V4",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .background(
                        if (isAiConnected) SanaNeonGreen.copy(alpha = 0.15f) else SanaNeonRed.copy(alpha = 0.15f),
                        CircleShape
                    )
                    .border(
                        1.dp,
                        if (isAiConnected) SanaNeonGreen else SanaNeonRed,
                        CircleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isAiConnected) "Online" else "Offline",
                    color = if (isAiConnected) SanaNeonGreen else SanaNeonRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenAntiTheft, modifier = Modifier.testTag("anti_theft_button")) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = "Anti-Theft Protection",
                    tint = if (isAntiTheftArmed) SanaNeonGreen else SanaNeonRed
                )
            }
            IconButton(onClick = onOpenMemory, modifier = Modifier.testTag("memory_button")) {
                Icon(Icons.Default.Psychology, contentDescription = "Memory Vault", tint = SanaNeonCyan)
            }
            IconButton(onClick = onOpenPermissions, modifier = Modifier.testTag("permissions_button")) {
                Icon(Icons.Default.Security, contentDescription = "Permission Center", tint = SanaNeonGreen)
            }
            IconButton(onClick = onOpenDiagnostics, modifier = Modifier.testTag("diagnostics_button")) {
                Icon(Icons.Default.Info, contentDescription = "Diagnostics", tint = SanaWarningAmber)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings_button")) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

@Composable
private fun StateStatusBanner(
    state: ConversationState,
    emotion: com.example.model.EmotionType,
    isHandsFree: Boolean,
    onInterrupted: () -> Unit
) {
    val stateColor = when (state) {
        ConversationState.LISTENING -> SanaNeonCyan
        ConversationState.PROCESSING -> SanaNeonRed
        ConversationState.SPEAKING -> SanaNeonPink
        ConversationState.INTERRUPTED -> SanaNeonPurple
        ConversationState.RECOVERING -> SanaWarningAmber
        ConversationState.ERROR -> SanaNeonRed
        ConversationState.STOPPED, ConversationState.IDLE -> SanaTextSecondary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(stateColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (state == ConversationState.PROCESSING) "Active" else state.displayName,
                color = stateColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state == ConversationState.SPEAKING) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tap to interrupt SANA",
                color = SanaNeonPurple,
                fontSize = 11.sp,
                modifier = Modifier.clickable { onInterrupted() }
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${emotion.emoji} ${emotion.label}",
                color = SanaTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun QuickActionChipsRow(
    onActionSelected: (String) -> Unit,
    onOpenVision: () -> Unit,
    onOpenTeaching: () -> Unit,
    onOpenAntiTheft: () -> Unit,
    onOpenDailyReport: () -> Unit,
    onOpenRoutines: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            ActionChip(label = "🎓 Teaching Mode", color = SanaNeonGreen) { onOpenTeaching() }
        }
        item {
            ActionChip(label = "🛡️ Anti-Theft", color = SanaNeonRed) { onOpenAntiTheft() }
        }
        item {
            ActionChip(label = "📊 Daily Report", color = SanaNeonCyan) { onOpenDailyReport() }
        }
        item {
            ActionChip(label = "⏱️ Routines", color = SanaNeonPink) { onOpenRoutines() }
        }
        item {
            ActionChip(label = "📸 Vision / Camera", color = SanaNeonCyan) { onOpenVision() }
        }
        item {
            ActionChip(label = "💬 WhatsApp", color = SanaNeonGreen) { onActionSelected("WhatsApp kholo") }
        }
        item {
            ActionChip(label = "📷 Camera", color = SanaNeonPink) { onActionSelected("Camera kholo") }
        }
        item {
            ActionChip(label = "🔦 Torch", color = SanaWarningAmber) { onActionSelected("Torch on karo") }
        }
        item {
            ActionChip(label = "🔋 Battery", color = SanaNeonBlue) { onActionSelected("Battery status kya hai?") }
        }
        item {
            ActionChip(label = "🧠 Memory", color = SanaNeonPurple) { onActionSelected("What do you remember about me?") }
        }
    }
}

@Composable
private fun ActionChip(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    val horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement
    ) {
        if (!isUser && !isSystem) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        Brush.linearGradient(listOf(SanaNeonPink, SanaNeonCyan)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("S", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> SanaNeonCyan.copy(alpha = 0.18f)
                    isSystem -> SanaSurfaceElevated
                    else -> SanaSurfaceCard
                }
            ),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) SanaNeonCyan.copy(alpha = 0.4f) else Color(0x1AFFFFFF)
            ),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.9f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser && !isSystem) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SANA (سنا)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SanaNeonPink
                        )
                        Text(
                            text = message.emotion.label,
                            fontSize = 10.sp,
                            color = SanaTextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    color = if (isUser) Color.White else SanaTextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                if (message.actionTag != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (message.isActionVerified) SanaNeonGreen.copy(alpha = 0.15f)
                                else SanaWarningAmber.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (message.isActionVerified) Icons.Default.CheckCircle else Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = if (message.isActionVerified) SanaNeonGreen else SanaWarningAmber,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Action: ${message.actionTag} (Verified)",
                                fontSize = 10.sp,
                                color = if (message.isActionVerified) SanaNeonGreen else SanaWarningAmber,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomControlBar(
    state: ConversationState,
    isHandsFreeActive: Boolean,
    onToggleMic: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // STOP Button (instant stop of active speech and recording)
        Button(
            onClick = onStop,
            modifier = Modifier
                .size(54.dp)
                .testTag("stop_conversation_button"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = SanaNeonRed.copy(alpha = 0.25f),
                contentColor = SanaNeonRed
            ),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, SanaNeonRed),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Stop,
                contentDescription = "Stop SANA",
                modifier = Modifier.size(24.dp)
            )
        }

        // Master Hands-Free Mic Button
        val isListening = state == ConversationState.LISTENING
        val isProcessing = state == ConversationState.PROCESSING
        val isSpeaking = state == ConversationState.SPEAKING

        val buttonColor = when {
            isListening -> SanaNeonCyan
            isProcessing -> SanaNeonRed
            isSpeaking -> SanaNeonPink
            else -> SanaNeonPurple
        }

        Button(
            onClick = onToggleMic,
            modifier = Modifier
                .size(72.dp)
                .testTag("master_microphone_button"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonColor
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Toggle Hands-free Listening",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }

        // Hands-free continuous toggle indicator
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    if (isHandsFreeActive) SanaNeonGreen.copy(alpha = 0.2f) else SanaSurfaceElevated,
                    CircleShape
                )
                .border(
                    1.dp,
                    if (isHandsFreeActive) SanaNeonGreen else Color(0x33FFFFFF),
                    CircleShape
                )
                .clickable { onToggleMic() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isHandsFreeActive) "AUTO" else "MANUAL",
                    color = if (isHandsFreeActive) SanaNeonGreen else SanaTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isHandsFreeActive) "ON" else "OFF",
                    color = if (isHandsFreeActive) SanaNeonGreen else SanaTextMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
