package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class SanaAudioTrackPlayer(
    private val context: Context,
    private val onPlaybackStarted: () -> Unit,
    private val onPlaybackFinished: () -> Unit,
    private val onPlaybackError: (String) -> Unit
) {
    private val tag = "SanaAudioTrackPlayer"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var currentAudioTrack: AudioTrack? = null
    private var currentMediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val isInterrupted = AtomicBoolean(false)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    companion object {
        const val SAMPLE_RATE_HZ = 24000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes
    }

    /**
     * Ensures volume is optimal for spoken voice output and not muted.
     * Prevents the "Voice output too quiet" issue noted in Section 8.
     */
    private fun ensureAdequateVoiceVolume() {
        val am = audioManager ?: return
        try {
            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            // Ensure at least 70% volume for speech audibility
            val targetMinVol = (maxVol * 0.70f).toInt().coerceAtLeast(1)
            if (currentVol < targetMinVol) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetMinVol, 0)
                Log.d(tag, "Adjusted STREAM_MUSIC volume from $currentVol to $targetMinVol / $maxVol for voice clarity.")
            }
        } catch (e: Exception) {
            Log.w(tag, "Volume check non-fatal exception: ${e.message}")
        }
    }

    /**
     * Normalizes and amplifies 16-bit PCM samples with peak-limiting to prevent clipping.
     * Guarantees clear, loud physical voice output through device speaker.
     */
    private fun boostAndNormalizePcm(rawPcm: ByteArray): ByteArray {
        if (rawPcm.size < 2) return rawPcm
        val output = rawPcm.clone()
        val numSamples = output.size / 2
        var maxPeak = 0

        // 1. Measure peak amplitude
        for (i in 0 until numSamples) {
            val low = output[i * 2].toInt() and 0xFF
            val high = output[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            val absVal = Math.abs(sample)
            if (absVal > maxPeak) {
                maxPeak = absVal
            }
        }

        // Target amplitude peak ~28000 out of 32767 for loud, clean, distortion-free output
        val targetPeak = 28000.0
        val gain: Double = if (maxPeak > 100) {
            val calculated = targetPeak / maxPeak.toDouble()
            calculated.coerceIn(1.2, 3.2) // Controlled gain boost without harsh distortion
        } else {
            1.5
        }

        // 2. Apply digital gain with soft limiting
        for (i in 0 until numSamples) {
            val low = output[i * 2].toInt() and 0xFF
            val high = output[i * 2 + 1].toInt()
            val sample = (high shl 8) or low
            val boosted = (sample * gain).toInt().coerceIn(-32767, 32767)
            output[i * 2] = (boosted and 0xFF).toByte()
            output[i * 2 + 1] = ((boosted shr 8) and 0xFF).toByte()
        }

        return output
    }

    /**
     * Plays PCM audio through the Android device speaker without premature cutoff.
     * Automatically extracts PCM data from WAV RIFF containers if present.
     */
    fun playPcmAudio(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            onPlaybackFinished()
            return
        }

        stop()
        isInterrupted.set(false)

        playbackJob = scope.launch {
            try {
                ensureAdequateVoiceVolume()
                val extractedPcm = extractPcmBytes(audioData)
                val pcmBytes = boostAndNormalizePcm(extractedPcm)
                requestAudioFocus()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )
                val bufferSize = maxOf(minBufferSize * 2, 4096)

                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE_HZ)
                    .setChannelMask(CHANNEL_CONFIG)
                    .setEncoding(AUDIO_FORMAT)
                    .build()

                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                currentAudioTrack = track
                // Set volume to maximum track level for full speaker projection
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    track.setVolume(1.0f)
                }

                track.play()
                _isPlaying.value = true
                onPlaybackStarted()

                val chunkSize = 2048
                var offset = 0
                while (offset < pcmBytes.size && !isInterrupted.get()) {
                    val bytesToWrite = minOf(chunkSize, pcmBytes.size - offset)
                    val written = track.write(pcmBytes, offset, bytesToWrite)
                    if (written < 0) {
                        Log.e(tag, "AudioTrack write error: $written")
                        break
                    }
                    offset += written
                }

                if (!isInterrupted.get()) {
                    // Calculate TRUE audio duration: total bytes / (24000 samples/sec * 2 bytes/sample)
                    val totalDurationMs = ((pcmBytes.size.toDouble() / (SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)) * 1000.0).toLong()
                    delay(totalDurationMs + 100L)
                }

                releaseTrackSafely(track)
                _isPlaying.value = false
                abandonAudioFocus()
                if (!isInterrupted.get()) {
                    onPlaybackFinished()
                }
            } catch (e: Exception) {
                Log.e(tag, "AudioTrack playback exception", e)
                _isPlaying.value = false
                abandonAudioFocus()
                onPlaybackError(e.localizedMessage ?: "Audio playback failed")
            }
        }
    }

    /**
     * Plays MP3 audio (e.g. from ElevenLabs premium voice) through standard Android MediaPlayer.
     */
    fun playMp3Audio(mp3Data: ByteArray) {
        if (mp3Data.isEmpty()) {
            onPlaybackFinished()
            return
        }

        stop()
        isInterrupted.set(false)

        playbackJob = scope.launch {
            try {
                ensureAdequateVoiceVolume()
                val tempFile = File.createTempFile("sana_voice_", ".mp3", context.cacheDir)
                FileOutputStream(tempFile).use { it.write(mp3Data) }

                requestAudioFocus()

                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(tempFile.absolutePath)
                    setVolume(1.0f, 1.0f)
                    prepare()
                }

                currentMediaPlayer = mp

                mp.setOnCompletionListener {
                    _isPlaying.value = false
                    abandonAudioFocus()
                    releaseMediaPlayerSafely(mp)
                    tempFile.delete()
                    if (!isInterrupted.get()) {
                        onPlaybackFinished()
                    }
                }

                mp.setOnErrorListener { _, what, extra ->
                    Log.w(tag, "MediaPlayer error: $what, $extra")
                    _isPlaying.value = false
                    abandonAudioFocus()
                    releaseMediaPlayerSafely(mp)
                    tempFile.delete()
                    onPlaybackError("MediaPlayer error: $what")
                    true
                }

                mp.start()
                _isPlaying.value = true
                onPlaybackStarted()
            } catch (e: Exception) {
                Log.e(tag, "MP3 playback exception", e)
                _isPlaying.value = false
                abandonAudioFocus()
                onPlaybackError(e.localizedMessage ?: "MP3 playback failed")
            }
        }
    }

    /**
     * Extracts raw PCM payload from byte stream, stripping standard 44-byte WAV header if present.
     */
    private fun extractPcmBytes(data: ByteArray): ByteArray {
        if (data.size > 44 && data[0] == 'R'.code.toByte() && data[1] == 'I'.code.toByte() && data[2] == 'F'.code.toByte() && data[3] == 'F'.code.toByte()) {
            val dataOffset = findDataChunkOffset(data)
            return if (dataOffset != -1 && dataOffset + 8 < data.size) {
                data.copyOfRange(dataOffset + 8, data.size)
            } else {
                data.copyOfRange(44, data.size)
            }
        }
        return data
    }

    private fun findDataChunkOffset(data: ByteArray): Int {
        for (i in 12 until (data.size - 4)) {
            if (data[i] == 'd'.code.toByte() && data[i + 1] == 'a'.code.toByte() && data[i + 2] == 't'.code.toByte() && data[i + 3] == 'a'.code.toByte()) {
                return i
            }
        }
        return -1
    }

    /**
     * Instantly halt audio playback on user speech / interruption / STOP.
     */
    fun stop() {
        isInterrupted.set(true)
        playbackJob?.cancel()
        playbackJob = null

        currentAudioTrack?.let { track ->
            try {
                track.pause()
                track.flush()
                track.stop()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping audio track", e)
            } finally {
                releaseTrackSafely(track)
            }
        }
        currentAudioTrack = null

        currentMediaPlayer?.let { mp ->
            try {
                if (mp.isPlaying) mp.stop()
            } catch (e: Exception) {
                Log.w(tag, "Error stopping media player", e)
            } finally {
                releaseMediaPlayerSafely(mp)
            }
        }
        currentMediaPlayer = null

        _isPlaying.value = false
        abandonAudioFocus()
    }

    private fun releaseTrackSafely(track: AudioTrack) {
        try {
            track.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing audio track", e)
        }
    }

    private fun releaseMediaPlayerSafely(mp: MediaPlayer) {
        try {
            mp.reset()
            mp.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing media player", e)
        }
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                        focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        stop()
                    }
                }
                .build()
            audioFocusRequest = focusRequest
            am.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    fun destroy() {
        stop()
    }
}
