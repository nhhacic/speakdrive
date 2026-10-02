package com.speakdrive.audio.di

import com.speakdrive.audio.AudioFocus
import com.speakdrive.audio.AudioFocusHandler
import com.speakdrive.audio.LiveAudio
import com.speakdrive.audio.LiveAudioIO
import com.speakdrive.audio.TextToSpeechAnnouncer
import com.speakdrive.audio.VoiceAnnouncer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    abstract fun bindAudioFocus(impl: AudioFocusHandler): AudioFocus

    @Binds
    abstract fun bindVoiceAnnouncer(impl: TextToSpeechAnnouncer): VoiceAnnouncer

    @Binds
    abstract fun bindLiveAudio(impl: LiveAudioIO): LiveAudio
}
