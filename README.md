# SANA V3 — Production-Ready Android AI Voice Assistant

<p align="center">
  <b>SANA (ثناء)</b> is an intelligent, caring, emotionally aware AI voice companion and Android assistant built with Kotlin and Jetpack Compose.
</p>

---

## 🌟 Key Features

1. **One-Tap Hands-Free Continuous Voice Loop**:
   - Tap the microphone button once to enter continuous conversation mode.
   - Flow: **User Speaks → Speech-to-Text → Gemini AI Reasoning → Text-To-Speech Output → Automatic Re-Listen**.
   - Runs until the user taps **STOP CONVERSATION**.

2. **Natural Interruption**:
   - SANA supports natural conversational flow. If SANA is speaking and the user begins speaking, audio playback halts immediately and the new voice input is captured seamlessly.

3. **Multilingual Brain**:
   - Default language: **Urdu (اردو)**.
   - Fluently supports **English**, **Roman Urdu**, and **Auto language detection**.

4. **Real Android Phone Control (Never Faked)**:
   - **WhatsApp**: Direct verified launch via package intents.
   - **Camera**: Native still camera capture intent.
   - **Phone Calls**: Contact lookup via Android Contacts provider and direct calling (`CALL_PHONE` or dialer fallback).
   - **Flashlight / Torch**: Hardware camera flash toggle via `CameraManager`.
   - **Battery Telemetry**: Real-time battery percentage and charging status.
   - **YouTube & Web Search**: Native app launch and search queries.
   - **System Settings**: Quick access to Wi-Fi, Bluetooth, Audio, and App Permission settings.

5. **User-Controlled Memory Vault**:
   - Safe, on-device local **Room Database** (`sana_vault.db`).
   - SANA remembers what you ask ("Remember this", "What do you remember about me?", "Forget that").
   - Interactive Memory Vault management sheet to search, manually add, or clear memories.

6. **Personality & Companion Mode**:
   - Emotionally aware, empathetic, warm, and supportive tone.
   - Optional **Love / Companion Mode** with configurable **Playful Jealousy** (OFF / Light / Playful) with strictly safe conversational boundaries.

7. **Self-Healing & Diagnostics**:
   - Live health monitoring of Microphone, AI Brain, TTS, Battery, Network, and Audio Focus.
   - Exponential backoff auto-recovery on transient timeouts or speech recognizer glitches.

8. **Foreground Voice Service & Notifications**:
   - `SanaVoiceService` provides persistent foreground service notification while hands-free mode is active.
   - `SanaNotificationListenerService` can read and summarize device notifications upon request.

9. **Futuristic Visuals**:
   - Obsidian dark UI with Neon Pink (`#FF2E93`) and Cyber Cyan (`#00E5FF`) accents.
   - Interactive Canvas **SanaOrb** pulsating dynamically to real speech sound levels.

---

## 🛠️ Build & Run Instructions

### Prerequisites
- Android Studio Ladybug / Meerkat or modern Android CLI SDK
- JDK 17 / 21
- Android Gradle Plugin 8.x / 9.x

### Build Debug APK
```bash
./gradlew assembleDebug
```
The generated APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit & Robolectric Tests
```bash
./gradlew testDebugUnitTest
```

---

## 🔑 Configuration & API Keys
Configure your Gemini API key securely:
1. In `.env`:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
2. Or enter your custom key directly within the in-app **SANA Settings** panel.
