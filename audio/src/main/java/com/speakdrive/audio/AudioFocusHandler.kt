package com.speakdrive.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enumerates the possible states of Audio Focus.
 */
enum class AudioFocusState {
    GAIN, LOSS, LOSS_TRANSIENT, LOSS_TRANSIENT_CAN_DUCK, NONE
}

/**
 * Handles requesting and abandoning Android Audio Focus.
 */
@Singleton
class AudioFocusHandler @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _audioFocusState = MutableStateFlow(AudioFocusState.NONE)
    val audioFocusState: StateFlow<AudioFocusState> = _audioFocusState.asStateFlow()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
        }
    }

    /**
     * Requests audio focus.
     * @return true if focus is granted, false otherwise.
     */
    fun requestAudioFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            _audioFocusState.value = AudioFocusState.GAIN
            true
        } else {
            false
        }
    }

    /**
     * Abandons audio focus.
     */
    fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
        _audioFocusState.value = AudioFocusState.NONE
    }

    override fun onAudioFocusChange(focusChange: Int) {
        _audioFocusState.value = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusState.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusState.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusState.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusState.LOSS_TRANSIENT_CAN_DUCK
            else -> AudioFocusState.NONE
        }
    }
}
