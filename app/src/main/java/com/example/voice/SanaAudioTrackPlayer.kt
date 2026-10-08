package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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
     * Plays PCM audio through the Android device speaker without premature cutoff.
     * Automatically extracts PCM data from WAV RIFF containers if present.
     */
    fun playPcmAudio(audioData: ByteArray) {
        if (audioData.isEmpty()) {
            onPlaybackFinished()
            return
        }

        // Stop any active session cleanly first
        stop()
        isInterrupted.set(false)

        playbackJob = scope.launch {
            try {
                // Extract raw PCM payload (strip WAV header if present)
                val pcmBytes = extractPcmBytes(audioData)
                if (pcmBytes.isEmpty()) {
                    onPlaybackFinished()
                    return@launch
                }

                requestAudioFocus()

                val minBufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE_HZ,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                )
                val bufferSize = maxOf(minBufferSize * 4, 8192)

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
                    // Allow the complete audio to finish playing through the speaker
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
     * Extracts raw PCM payload from byte stream, stripping standard 44-byte WAV header if present
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
     * Instantly halt audio playback on user speech / interruption / STOP
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
