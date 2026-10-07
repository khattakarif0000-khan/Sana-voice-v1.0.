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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.ChatMessage
import com.example.model.ConversationState
import com.example.model.MessageRole
import com.example.ui.components.DiagnosticSheet
import com.example.ui.components.MemoryVaultSheet
import com.example.ui.components.PermissionCenterSheet
import com.example.ui.components.SanaOrb
import com.example.ui.components.SettingsSheet
import com.example.ui.components.VisionModal
import com.example.ui.theme.SanaBlack
import com.example.ui.theme.SanaBorderGlow
import com.example.ui.theme.SanaCyanGlow
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
import com.example.ui.theme.SanaTextSecondary
import com.example.ui.theme.SanaWarningAmber
import com.example.viewmodel.SanaViewModel

enum class ActiveSheet {
    NONE,
    SETTINGS,
    MEMORY,
    PERMISSIONS,
    DIAGNOSTICS,
    VISION
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
            .background(SanaBlack),
        containerColor = SanaBlack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Top Bar: Logo & Navigation Badges
            TopHeaderBar(
                isAiConnected = diagnosticStatus.aiConfigured,
                onOpenPermissions = { activeSheet = ActiveSheet.PERMISSIONS },
                onOpenMemory = { activeSheet = ActiveSheet.MEMORY },
                onOpenSettings = { activeSheet = ActiveSheet.SETTINGS },
                onOpenDiagnostics = { activeSheet = ActiveSheet.DIAGNOSTICS }
            )

            // 2. Central AI Orb Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                SanaOrb(
                    state = state,
                    audioLevel = audioWaveLevel,
                    orbSize = 200.dp
                )
            }

            // 3. Current State Banner & Emotion Badge
            StateStatusBanner(
                state = state,
                emotion = emotion,
                isHandsFree = isHandsFree,
                onInterrupted = {
                    if (state == ConversationState.SPEAKING) {
                        viewModel.processUserInput("...")
                    }
                }
            )

            // 4. Quick Action Chips (WhatsApp, Camera, Torch, Battery, Vision)
            QuickActionChipsRow(
                onActionSelected = { prompt ->
                    viewModel.processUserInput(prompt)
                },
                onOpenVision = { activeSheet = ActiveSheet.VISION }
            )

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
                ActiveSheet.NONE -> {}
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    isAiConnected: Boolean,
    onOpenPermissions: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "SANA",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = SanaNeonPink,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(SanaCyanGlow, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "V3 PROD",
                    color = SanaNeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = state.displayName.uppercase(),
                color = stateColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
            if (isHandsFree) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "• HANDS-FREE",
                    color = SanaNeonGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }

        if (state == ConversationState.SPEAKING) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Speaking... (speak or tap to interrupt)",
                color = SanaNeonPink.copy(alpha = 0.8f),
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
    onOpenVision: () -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
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
    val bubbleColor = if (isUser) SanaSurfaceElevated else SanaSurfaceCard
    val align = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                shape = RoundedCornerShape(
                    topStart = 14.dp,
                    topEnd = 14.dp,
                    bottomStart = if (isUser) 14.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 14.dp
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isUser) Color(0x22FFFFFF) else Color(0x33FF2E93)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isUser) "You" else "SANA",
                            color = if (isUser) SanaNeonCyan else SanaNeonPink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        if (!isUser) {
                            Text(
                                text = message.emotion.emoji,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Action Execution verified badge
                    if (message.actionTag != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    if (message.isActionVerified) SanaNeonGreen.copy(alpha = 0.15f)
                                    else SanaNeonRed.copy(alpha = 0.15f),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = if (message.isActionVerified) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = "Action status",
                                tint = if (message.isActionVerified) SanaNeonGreen else SanaNeonRed,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (message.isActionVerified) "Verified: ${message.actionTag}" else "Notice: ${message.actionTag}",
                                color = if (message.isActionVerified) SanaNeonGreen else SanaNeonRed,
                                fontSize = 11.sp,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Prominent STOP button when hands-free is active
        AnimatedVisibility(
            visible = isHandsFreeActive,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = SanaNeonRed),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(48.dp)
                    .testTag("stop_conversation_button")
            ) {
                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "STOP CONVERSATION",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large 1-Tap Mic Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isListening = state == ConversationState.LISTENING

            Button(
                onClick = onToggleMic,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isHandsFreeActive) SanaNeonPink else SanaNeonCyan
                ),
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .testTag("mic_toggle_button"),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isHandsFreeActive) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isHandsFreeActive) "Stop Hands-Free" else "Start Hands-Free",
                    tint = if (isHandsFreeActive) Color.White else Color.Black,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isHandsFreeActive) "Tap mic or Stop to end hands-free mode" else "1-Tap to start continuous hands-free conversation",
            color = SanaTextMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}
