package com.speakdrive.ai.di

import com.speakdrive.ai.GeminiLiveManager
import com.speakdrive.ai.diagnostics.AndroidDiagnosticsLogSaver
import com.speakdrive.ai.diagnostics.DiagnosticsLogSaver
import com.speakdrive.ai.live.LiveConversationClient
import com.speakdrive.ai.network.AndroidConnectivityObserver
import com.speakdrive.ai.network.ConnectivityObserver
import com.speakdrive.ai.pronunciation.AzurePronunciationAssessor
import com.speakdrive.ai.pronunciation.PronunciationAssessor
import com.speakdrive.ai.session.AndroidMicPermissionChecker
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.ai.summary.GeminiSummaryGenerator
import com.speakdrive.ai.summary.SummaryGenerator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

/** Dispatcher the conversation engine runs its state machine on. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EngineDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds
    abstract fun bindLiveClient(impl: GeminiLiveManager): LiveConversationClient

    @Binds
    abstract fun bindSummaryGenerator(impl: GeminiSummaryGenerator): SummaryGenerator

    @Binds
    abstract fun bindConnectivity(impl: AndroidConnectivityObserver): ConnectivityObserver

    @Binds
    abstract fun bindMicPermission(impl: AndroidMicPermissionChecker): MicPermissionChecker

    @Binds
    abstract fun bindPronunciationAssessor(impl: AzurePronunciationAssessor): PronunciationAssessor

    @Binds
    abstract fun bindDiagnosticsLogSaver(impl: AndroidDiagnosticsLogSaver): DiagnosticsLogSaver

    companion object {
        @Provides
        @EngineDispatcher
        fun provideEngineDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate
    }
}
