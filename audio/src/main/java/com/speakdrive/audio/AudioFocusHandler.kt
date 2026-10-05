package com.speakdrive.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests audio focus for spoken content. Because the AI speaks, we ask the system to tell us
 * about ducking instead of lowering our volume, and the conversation engine pauses instead.
 */
@Singleton
class AudioFocusHandler @Inject constructor(
    @ApplicationContext context: Context
) : AudioFocus, AudioManager.OnAudioFocusChangeListener {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener(this)
        .build()

    private val _state = MutableStateFlow(AudioFocusState.NONE)
    override val state: StateFlow<AudioFocusState> = _state.asStateFlow()

    override fun request(): Boolean {
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) _state.value = AudioFocusState.GAIN
        return granted
    }

    override fun abandon() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        _state.value = AudioFocusState.NONE
    }

    override fun onAudioFocusChange(focusChange: Int) {
        _state.value = when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> AudioFocusState.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> AudioFocusState.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusState.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusState.LOSS_TRANSIENT_CAN_DUCK
            else -> AudioFocusState.NONE
        }
    }
}
