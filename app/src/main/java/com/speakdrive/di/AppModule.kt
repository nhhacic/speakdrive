package com.speakdrive.di

import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.SessionStore
import com.speakdrive.data.repository.SessionRepository
import com.speakdrive.data.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Connects the storage-agnostic interfaces of :ai to the app's Room and DataStore implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindSessionStore(impl: SessionRepository): SessionStore

    @Binds
    abstract fun bindLearningSettings(impl: UserPreferencesRepository): LearningSettings
}
