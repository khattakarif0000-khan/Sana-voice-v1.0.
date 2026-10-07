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
     * Plays raw PCM (24kHz 16-bit Mono) through the device speaker.
     */
    fun playPcmAudio(pcmData: ByteArray) {
        if (pcmData.isEmpty()) {
            onPlaybackFinished()
            return
        }

        // Cancel any active playback
        stop()
        isInterrupted.set(false)

        playbackJob = scope.launch {
            try {
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

                track.play()
                _isPlaying.value = true
                onPlaybackStarted()

                val chunkSize = 2048
                var offset = 0

                while (offset < pcmData.size && !isInterrupted.get()) {
                    val bytesToWrite = minOf(chunkSize, pcmData.size - offset)
                    val written = track.write(pcmData, offset, bytesToWrite)
                    if (written < 0) {
                        Log.e(tag, "AudioTrack write error: $written")
                        break
                    }
                    offset += written
                }

                if (!isInterrupted.get()) {
                    // Calculate remaining audio duration based on sample rate to let buffer drain
                    val remainingMs = ((pcmData.size.toFloat() / (SAMPLE_RATE_HZ * BYTES_PER_SAMPLE)) * 1000L).toLong()
                    val waitMs = minOf(remainingMs, 500L) // Small safety wait for buffer completion
                    delay(waitMs)
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
     * Instantly halt audio playback on user speech / interruption
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
