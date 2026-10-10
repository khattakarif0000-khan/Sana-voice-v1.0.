package com.example.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.phone.AntiTheftSensitivity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ChatMessage
import com.example.model.ConversationState
import com.example.model.MessageRole
import com.example.phone.AntiTheftAlertState
import com.example.ui.components.AntiTheftProtectionSheet
import com.example.ui.components.DailyReportSheet
import com.example.ui.components.DiagnosticSheet
import com.example.ui.components.ElevenLabsModal
import com.example.ui.components.MemoryVaultSheet
import com.example.ui.components.PermissionCenterSheet
import com.example.ui.components.ResetSanaModal
import com.example.ui.components.RoutinesSheet
import com.example.ui.components.SanaMenuModal
import com.example.ui.components.SanaOrb
import com.example.ui.components.SettingsSheet
import com.example.ui.components.TeachingModeSheet
import com.example.ui.components.TradingModal
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
    MENU,
    SETTINGS,
    VOICE_SETTINGS,
    MEMORY,
    PERMISSIONS,
    DIAGNOSTICS,
    VISION,
    TRADING,
    TEACHING,
    ANTI_THEFT,
    DAILY_REPORT,
    ROUTINES,
    RESET
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SanaScreen(
    viewModel: SanaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()

    var activeSheet by remember { mutableStateOf(ActiveSheet.NONE) }
    var typedText by remember { mutableStateOf("") }
    var isTextInputMode by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Auto-scroll when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startConversation(true)
        }
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
            .navigationBarsPadding()
            .imePadding(),
        containerColor = SanaBlack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Unified Clean Top Header Bar (One primary ☰ SANA MENU button)
            UnifiedTopHeaderBar(
                isAiConnected = diagnosticStatus.aiConfigured,
                isAntiTheftArmed = isAntiTheftArmed,
                onOpenMenu = { activeSheet = ActiveSheet.MENU },
                onToggleInputMode = { isTextInputMode = !isTextInputMode },
                isTextInputMode = isTextInputMode
            )

            // Anti-Theft Active Alert Banner
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
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                SanaOrb(
                    state = state,
                    audioLevel = audioWaveLevel,
                    modifier = Modifier.size(120.dp),
                    orbSize = 120.dp
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
                    }
                }
            )

            // Active Teaching Mode Banner
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

            // Verified Social Comment Review Card
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

            // 4. Conversation Transcript Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            // 5. Type Message Field (Always accessible, Section 16 & 17)
            TypeMessageBar(
                value = typedText,
                onValueChange = { typedText = it },
                onSend = {
                    if (typedText.isNotBlank()) {
                        val toSend = typedText
                        typedText = ""
                        keyboardController?.hide()
                        viewModel.processUserInput(toSend)
                    }
                }
            )

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

    // Modal Bottom Sheets for Unified Menu and Features
    if (activeSheet != ActiveSheet.NONE) {
        ModalBottomSheet(
            onDismissRequest = { activeSheet = ActiveSheet.NONE },
            sheetState = sheetState,
            containerColor = SanaSurfaceDark,
            scrimColor = Color.Black.copy(alpha = 0.75f)
        ) {
            when (activeSheet) {
                ActiveSheet.MENU -> SanaMenuModal(
                    onSelectOption = { optionId ->
                        when (optionId) {
                            "VOICE" -> {
                                if (hasRecordPermission()) viewModel.startConversation(true)
                                else audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            }
                            "TYPE_MESSAGE" -> isTextInputMode = true
                            "MEMORY" -> activeSheet = ActiveSheet.MEMORY
                            "TEACHING" -> activeSheet = ActiveSheet.TEACHING
                            "CAMERA_VISION" -> activeSheet = ActiveSheet.VISION
                            "TRADING" -> activeSheet = ActiveSheet.TRADING
                            "ANTI_THEFT" -> activeSheet = ActiveSheet.ANTI_THEFT
                            "ROUTINES" -> activeSheet = ActiveSheet.ROUTINES
                            "COMPANION" -> activeSheet = ActiveSheet.SETTINGS
                            "IMAGE_GEN" -> {
                                viewModel.processUserInput("Please generate a 16:9 professional YouTube thumbnail image design.")
                            }
                            "PHONE_CONTROL" -> activeSheet = ActiveSheet.PERMISSIONS
                            "NOTIFICATIONS" -> {
                                viewModel.generateDailyReport()
                                activeSheet = ActiveSheet.DAILY_REPORT
                            }
                            "LANGUAGES" -> activeSheet = ActiveSheet.SETTINGS
                            "VOICE_SETTINGS" -> activeSheet = ActiveSheet.VOICE_SETTINGS
                            "DIAGNOSTICS" -> activeSheet = ActiveSheet.DIAGNOSTICS
                            "SETTINGS" -> activeSheet = ActiveSheet.SETTINGS
                            "PERMISSIONS" -> activeSheet = ActiveSheet.PERMISSIONS
                            "RESET_SANA" -> activeSheet = ActiveSheet.RESET
                        }
                    },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.VOICE_SETTINGS -> ElevenLabsModal(
                    settings = settings,
                    isAudioPlaying = isAudioPlaying,
                    onSaveSettings = { viewModel.updateSettings(it) },
                    onTestVoice = { viewModel.testVoiceAudition(it) },
                    onStopVoice = { viewModel.stopVoicePlayback() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.TRADING -> TradingModal(
                    onAnalyzeChart = { prompt, base64 ->
                        viewModel.processUserInput(prompt, base64)
                    },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.RESET -> ResetSanaModal(
                    onResetSession = { viewModel.resetCurrentSession() },
                    onResetSettings = { viewModel.resetAppSettings() },
                    onClearMemory = { viewModel.resetPersonalMemory() },
                    onFullCleanReset = { viewModel.fullCleanReset() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.SETTINGS -> SettingsSheet(
                    settings = settings,
                    isAiConnected = diagnosticStatus.aiConfigured,
                    isAudioPlaying = isAudioPlaying,
                    onSaveSettings = { viewModel.updateSettings(it) },
                    onTestVoice = { viewModel.testVoiceAudition(it) },
                    onStopVoice = { viewModel.stopVoicePlayback() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.MEMORY -> MemoryVaultSheet(
                    memories = memories,
                    onAddMemory = { viewModel.saveOrUpdateMemory(it) },
                    onDeleteMemory = { viewModel.deleteMemory(it) },
                    onClearAll = { viewModel.clearAllMemories() },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.PERMISSIONS -> PermissionCenterSheet(
                    onDismiss = { activeSheet = ActiveSheet.NONE },
                    onRequestPermission = { perm ->
                        // request launcher handled
                    }
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
                    currentSensitivity = AntiTheftSensitivity.valueOf(settings.antiTheftSensitivity),
                    onToggleArm = { arm ->
                        if (arm) viewModel.armAntiTheft()
                        else viewModel.disarmAntiTheft()
                    },
                    onSetSensitivity = { viewModel.setAntiTheftSensitivity(it) },
                    onTestWarning = { level ->
                        // test warning
                    },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.DAILY_REPORT -> DailyReportSheet(
                    report = dailyReport,
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                ActiveSheet.ROUTINES -> RoutinesSheet(
                    routines = routines,
                    onCreateRoutine = { title, trigger, action ->
                        viewModel.createRoutine(title, trigger, action)
                    },
                    onToggleRoutine = { id, enabled ->
                        viewModel.toggleRoutine(id, enabled)
                    },
                    onDeleteRoutine = { viewModel.deleteRoutine(it) },
                    onRunNow = { viewModel.runRoutineNow(it) },
                    onDismiss = { activeSheet = ActiveSheet.NONE }
                )

                else -> Unit
            }
        }
    }
}

@Composable
private fun UnifiedTopHeaderBar(
    isAiConnected: Boolean,
    isAntiTheftArmed: Boolean,
    onOpenMenu: () -> Unit,
    onToggleInputMode: () -> Unit,
    isTextInputMode: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App title & Online indicator
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

        // Header Action: One Unified Menu Button + Keyboard toggle
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleInputMode,
                modifier = Modifier.testTag("type_message_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = "Toggle Keyboard Type Message",
                    tint = if (isTextInputMode) SanaNeonCyan else SanaTextSecondary
                )
            }

            // PRIMARY ONE SANA MENU BUTTON (Section 18)
            Button(
                onClick = onOpenMenu,
                modifier = Modifier.testTag("sana_main_menu_button"),
                colors = ButtonDefaults.buttonColors(containerColor = SanaSurfaceElevated),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SanaNeonCyan.copy(alpha = 0.4f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "SANA Menu",
                    tint = SanaNeonCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MENU",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TypeMessageBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = "Type a message… (اردو / English / Voice Commands)",
                    color = SanaTextMuted,
                    fontSize = 13.sp
                )
            },
            modifier = Modifier
                .weight(1f)
                .testTag("type_message_input_field"),
            maxLines = 3,
            singleLine = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SanaNeonCyan,
                unfocusedBorderColor = Color(0x3300E5FF),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = SanaNeonCyan
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onSend,
            enabled = value.isNotBlank(),
            modifier = Modifier
                .size(46.dp)
                .background(
                    if (value.isNotBlank()) SanaNeonCyan else SanaSurfaceElevated,
                    RoundedCornerShape(12.dp)
                )
                .testTag("send_message_button")
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send Message",
                tint = if (value.isNotBlank()) Color.Black else SanaTextMuted,
                modifier = Modifier.size(20.dp)
            )
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
        ConversationState.STOPPED, ConversationState.IDLE -> SanaNeonCyan
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(stateColor, CircleShape)
            )
            Text(
                text = state.displayName,
                color = stateColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = emotion.label,
                color = SanaTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
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
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // STOP Button (instant stop of active speech and recording)
        Button(
            onClick = onStop,
            modifier = Modifier
                .size(52.dp)
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
                .size(68.dp)
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
                modifier = Modifier.size(34.dp)
            )
        }

        // Hands-free continuous toggle indicator
        Box(
            modifier = Modifier
                .size(52.dp)
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
