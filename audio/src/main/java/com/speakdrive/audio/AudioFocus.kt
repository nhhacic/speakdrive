package com.speakdrive.audio

import kotlinx.coroutines.flow.StateFlow

enum class AudioFocusState {
    /** We own audio focus. */
    GAIN,

    /** Lost for good, e.g. the user started music in another app. */
    LOSS,

    /** Lost for a short while, e.g. a phone call. */
    LOSS_TRANSIENT,

    /** Someone short wants the speaker, e.g. a navigation prompt. Speech should pause, not duck. */
    LOSS_TRANSIENT_CAN_DUCK,

    /** Not requested. */
    NONE
}

interface AudioFocus {
    val state: StateFlow<AudioFocusState>

    /** @return true if focus was granted (or will be granted later, see [AudioFocusState]). */
    fun request(): Boolean
    fun abandon()
}
