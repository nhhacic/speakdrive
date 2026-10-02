package com.speakdrive.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles playing audio from a raw PCM ByteArray stream.
 * Configured for 24000Hz (common output from generative AI models).
 */
@Singleton
class AudioPlayer @Inject constructor() {

    companion object {
        private const val SAMPLE_RATE = 24000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    
    private val minBufferSize = AudioTrack.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    /**
     * Initializes the AudioTrack if not already initialized.
     */
    private fun initializeAudioTrack() {
        if (audioTrack != null) return

        if (minBufferSize == AudioTrack.ERROR || minBufferSize == AudioTrack.ERROR_BAD_VALUE) {
            throw IllegalStateException("Invalid AudioTrack parameters or unsupported format.")
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .setEncoding(AUDIO_FORMAT)
            .build()

        audioTrack = AudioTrack(
            audioAttributes,
            audioFormat,
            minBufferSize * 2, // Double buffering
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        
        if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack?.release()
            audioTrack = null
            throw IllegalStateException("AudioTrack failed to initialize.")
        }
    }

    /**
     * Plays a chunk of audio data.
     * Call this repeatedly as chunks arrive from a stream.
     * 
     * @param audioData The raw PCM audio bytes.
     */
    suspend fun playChunk(audioData: ByteArray) = withContext(Dispatchers.IO) {
        if (audioTrack == null) {
            initializeAudioTrack()
        }

        val track = audioTrack ?: return@withContext

        if (!isPlaying.get()) {
            track.play()
            isPlaying.set(true)
        }

        val written = track.write(audioData, 0, audioData.size)
        if (written < 0) {
            throw RuntimeException("AudioTrack write error: $written")
        }
    }

    /**
     * Pauses the audio playback.
     */
    fun pause() {
        if (isPlaying.compareAndSet(true, false)) {
            try {
                audioTrack?.pause()
            } catch (e: Exception) {
                // Ignore exception
            }
        }
    }

    /**
     * Stops the audio playback, flushing any unplayed data.
     */
    fun stop() {
        if (isPlaying.compareAndSet(true, false)) {
            try {
                audioTrack?.stop()
                audioTrack?.flush()
            } catch (e: Exception) {
                // Ignore exception
            }
        }
    }

    /**
     * Releases the AudioTrack resources completely.
     */
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
