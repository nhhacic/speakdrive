package com.speakdrive.audio

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of audio streaming, coordinating the microphone recorder,
 * audio player, and audio focus.
 */
@Singleton
class AudioStreamManager @Inject constructor(
    private val microphoneRecorder: MicrophoneRecorder,
    private val audioPlayer: AudioPlayer,
    private val audioFocusHandler: AudioFocusHandler
) {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var focusJob: Job? = null
    
    private var isActive = false

    /**
     * Starts the audio session, requests audio focus, and returns the microphone stream.
     * It handles automatic pause/resume of mic/player based on audio focus.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startSession(): Flow<ByteArray> {
        isActive = true
        if (audioFocusHandler.requestAudioFocus()) {
            observeAudioFocus()
        }
        return microphoneRecorder.startRecording()
    }

    /**
     * Plays a chunk of audio through the AudioPlayer.
     * Note: Does not play if audio focus is completely lost.
     */
    suspend fun playAudioOutput(audioData: ByteArray) {
        val state = audioFocusHandler.audioFocusState.value
        if (state == AudioFocusState.GAIN || state == AudioFocusState.LOSS_TRANSIENT_CAN_DUCK) {
            audioPlayer.playChunk(audioData)
        }
    }

    /**
     * Stops the audio session, releases resources, and abandons audio focus.
     */
    fun stopSession() {
        isActive = false
        focusJob?.cancel()
        microphoneRecorder.stopRecording()
        audioPlayer.stop()
        audioFocusHandler.abandonAudioFocus()
    }

    /**
     * Releases all internal resources.
     */
    fun release() {
        stopSession()
        audioPlayer.release()
    }

    private fun observeAudioFocus() {
        focusJob?.cancel()
        focusJob = scope.launch {
            audioFocusHandler.audioFocusState.collectLatest { state ->
                if (!isActive) return@collectLatest

                when (state) {
                    AudioFocusState.GAIN -> {
                        microphoneRecorder.resumeRecording()
                        // Player resumes automatically when next chunk arrives
                    }
                    AudioFocusState.LOSS_TRANSIENT -> {
                        microphoneRecorder.pauseRecording()
                        audioPlayer.pause()
                    }
                    AudioFocusState.LOSS_TRANSIENT_CAN_DUCK -> {
                        // Ducking can be handled by lowering volume if needed, 
                        // but here we just continue recording and playing.
                    }
                    AudioFocusState.LOSS -> {
                        microphoneRecorder.pauseRecording()
                        audioPlayer.stop()
                    }
                    AudioFocusState.NONE -> {
                        // Do nothing
                    }
                }
            }
        }
    }
}
