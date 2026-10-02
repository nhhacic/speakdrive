package com.speakdrive.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * Handles recording audio from the device microphone.
 * Designed to provide raw PCM audio stream for real-time processing (e.g., speech recognition).
 */
@Singleton
class MicrophoneRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    /**
     * Starts recording and returns a Flow of ByteArray containing the raw PCM audio data.
     * Requires RECORD_AUDIO permission.
     *
     * @return Flow of ByteArray representing the audio chunks.
     */
    @RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    fun startRecording(): Flow<ByteArray> = flow {
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw IllegalStateException("Invalid AudioRecord parameters or unsupported format.")
        }

        stopRecording() // Ensure previous instance is stopped

        // Using VOICE_RECOGNITION for built-in noise suppression and optimization
        @SuppressLint("MissingPermission")
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBufferSize * 2
        )

        val record = audioRecord ?: throw IllegalStateException("Failed to initialize AudioRecord.")
        
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            audioRecord = null
            throw IllegalStateException("AudioRecord failed to initialize.")
        }

        record.startRecording()
        isRecording.set(true)
        isPaused.set(false)

        val buffer = ByteArray(minBufferSize)

        try {
            while (isRecording.get() && coroutineContext.isActive) {
                if (!isPaused.get()) {
                    val readResult = record.read(buffer, 0, buffer.size)
                    if (readResult > 0) {
                        // Copy to prevent mutation issues
                        emit(buffer.copyOfRange(0, readResult))
                    } else if (readResult < 0) {
                        // Handle read errors
                        throw RuntimeException("AudioRecord read error: $readResult")
                    }
                }
            }
        } finally {
            stopRecording()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Pauses the recording. Flow will stop emitting but will remain active.
     */
    fun pauseRecording() {
        isPaused.set(true)
    }

    /**
     * Resumes a paused recording.
     */
    fun resumeRecording() {
        isPaused.set(false)
    }

    /**
     * Stops the recording and releases resources.
     */
    fun stopRecording() {
        if (isRecording.compareAndSet(true, false)) {
            try {
                audioRecord?.stop()
            } catch (e: Exception) {
                // Ignore state exceptions during stop
            }
            audioRecord?.release()
            audioRecord = null
        }
    }
}
