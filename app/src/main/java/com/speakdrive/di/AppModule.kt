package com.speakdrive.di

import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.data.repository.UserPreferencesRepository
import com.speakdrive.playback.CarAutoStartPolicy
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/** Connects the storage-agnostic interfaces of :ai to the app's Room and DataStore implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindSessionStore(impl: SessionRepository): SessionStore

    @Binds
    abstract fun bindLearningSettings(impl: UserPreferencesRepository): LearningSettings

    companion object {
        @Provides
        fun provideCarAutoStartPolicy(preferences: UserPreferencesRepository): CarAutoStartPolicy =
            CarAutoStartPolicy { preferences.preferences.first().autoStartOnCarConnect }
    }
}
