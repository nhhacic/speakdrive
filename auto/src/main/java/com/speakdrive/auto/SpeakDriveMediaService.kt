package com.speakdrive.auto

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.audio.LiveAudio
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The media service Android Auto connects to. It hosts the [SpeakDrivePlayer] and runs in the
 * foreground while a lesson is active, so the conversation keeps going with the phone locked.
 */
@UnstableApi
@AndroidEntryPoint
class SpeakDriveMediaService : MediaLibraryService() {

    @Inject lateinit var engine: ConversationEngine
    @Inject lateinit var contentProvider: MediaContentProvider
    @Inject lateinit var voiceCommandHandler: VoiceCommandHandler
    @Inject lateinit var settings: LearningSettings
    @Inject lateinit var micPermission: MicPermissionChecker
    @Inject lateinit var liveAudio: LiveAudio

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var notificationProvider: CapturingNotificationProvider
    private var librarySession: MediaLibrarySession? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        liveAudio.setCarConnected(engine.isCarConnected.value)
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "speakdrive:lesson_wakelock")?.apply {
            setReferenceCounted(false)
        }

        notificationProvider = CapturingNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build(),
            contentIntent = launchAppIntent(),
            onNotificationUpdated = { mainHandler.post(::ensureMicrophoneForegroundType) }
        )
        setMediaNotificationProvider(notificationProvider)

        // No session activity: Android Auto sends it when the driver taps the artwork on the media
        // card, which opens the phone app ("Unlock phone to access SpeakDrive") instead of keeping
        // SpeakDrive on the car screen. The phone notification gets the app intent from the provider.
        val player = SpeakDrivePlayer(Looper.getMainLooper(), engine, settings, contentProvider, serviceScope)
        librarySession = MediaLibrarySession.Builder(this, player, LibraryCallback()).build()

        // The engine may start a lesson without a notification update (e.g. from the phone UI).
        serviceScope.launch {
            var wasInLesson = false
            engine.state.collect { state ->
                if (state == ConversationState.ACTIVE) triggerNotificationUpdate()
                if (state.isInLesson) {
                    try {
                        wakeLock?.acquire(4 * 60 * 60 * 1000L)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not acquire WakeLock", e)
                    }
                } else {
                    try {
                        if (wakeLock?.isHeld == true) wakeLock?.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not release WakeLock", e)
                    }
                    // Only when a lesson has just ended. On creation the engine is still IDLE while a
                    // car / headset Play button is starting one; stopping now would crash the service
                    // ("startForegroundService() did not then call startForeground()").
                    if (wasInLesson) dismissNotificationAndStopIfIdle()
                }
                wasInLesson = state.isInLesson
            }
        }

        // Rename the custom buttons when the lesson mode changes ("Đổi truyện" in a story, …).
        serviceScope.launch {
            engine.lesson.map { it?.mode }.distinctUntilChanged().collect { mode ->
                librarySession?.setCustomLayout(customLayout(mode))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onUpdateNotificationAsync(session: MediaSession, startInForegroundRequired: Boolean): ListenableFuture<Void?> {
        // When Media3 must start the foreground service (playback resumption from the car), always
        // post the notification, even before the engine has left IDLE.
        if (!engine.state.value.isInLesson && !startInForegroundRequired) {
            dismissNotificationOnly()
            return Futures.immediateFuture(null)
        }
        val future = super.onUpdateNotificationAsync(session, startInForegroundRequired)
        if (startInForegroundRequired) {
            future.addListener(::ensureMicrophoneForegroundType, ContextCompat.getMainExecutor(this))
        }
        return future
    }

    /**
     * Removes foreground status and stale notification without stopping the service.
     */
    private fun dismissNotificationOnly() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not stop foreground", e)
        }
        val notificationId = notificationProvider.latest?.notificationId
        if (notificationId != null) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(notificationId)
        }
        notificationProvider.clearLatest()
    }

    /**
     * Stops the foreground service and removes any stale notification when no lesson is active.
     */
    private fun dismissNotificationAndStopIfIdle() {
        dismissNotificationOnly()

        // If car is not connected and no lesson is in progress, stop the service.
        if (!engine.isCarConnected.value && !engine.state.value.isInLesson) {
            pauseAllPlayersAndStopSelf()
        }
    }

    /**
     * Media3 starts the foreground service with the "mediaPlayback" type only. Recording the
     * microphone while the phone is locked also needs the "microphone" type (Android 11+),
     * so re-post the same notification with both types while a lesson is running.
     */
    private fun ensureMicrophoneForegroundType() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!engine.state.value.isInLesson || !micPermission.hasMicPermission() || !isPlaybackOngoing) return
        val notification = notificationProvider.latest ?: return
        try {
            ServiceCompat.startForeground(
                this,
                notification.notificationId,
                notification.notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } catch (e: Exception) {
            // Android 14+ refuses this when the app was never visible since boot; the lesson
            // still works while the phone app is open. See docs/TESTING.md.
            Log.w(TAG, "Could not add the microphone foreground-service type", e)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep the lesson running when the app is swiped away during a drive.
        if (!engine.state.value.isInLesson) {
            dismissNotificationAndStopIfIdle()
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
        librarySession?.run {
            player.release()
            release()
        }
        librarySession = null
        liveAudio.setCarConnected(false)
        super.onDestroy()
    }

    internal fun isAllowedController(controller: MediaSession.ControllerInfo): Boolean {
        // 1. Same app process / UID (Phone UI connecting to its own MediaService)
        if (controller.uid == android.os.Process.myUid()) return true

        // 2. System UID (Media notification, lockscreen controls, Bluetooth system routing)
        if (controller.uid == android.os.Process.SYSTEM_UID || controller.uid == 1000 || controller.uid == 0) return true

        // 3. Trusted Media3 controller or matching package names
        val pkg = controller.packageName
        if (controller.isTrusted || pkg.isEmpty() || pkg == packageName || pkg == applicationContext.packageName || pkg == "com.speakdrive.ai" || pkg == "com.speakdrive" || pkg in TRUSTED_CONTROLLERS) return true

        // 4. System packages (Bluetooth, Android Auto, System UI, etc.)
        val isSystem = runCatching {
            val flags = packageManager.getApplicationInfo(pkg, 0).flags
            flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
        if (isSystem) return true

        // 5. Common system/auto/media prefixes to avoid rejecting car headunits or OEM media routers
        val lowerPkg = pkg.lowercase()
        return lowerPkg.contains("android") || lowerPkg.contains("auto") || lowerPkg.contains("systemui") || lowerPkg.contains("bluetooth") || lowerPkg.contains("media")
    }

    /** The "repeat" and "next" buttons, named for what they do in [mode] (see [LessonActionLabels]). */
    private fun customLayout(mode: SessionMode?): ImmutableList<CommandButton> {
        val labels = LessonActionLabels.forMode(mode)
        val repeatButton = CommandButton.Builder()
            .setIconResId(R.drawable.ic_repeat)
            .setDisplayName(getString(labels.repeat))
            .setSessionCommand(REPEAT_COMMAND)
            .setEnabled(true)
            .build()
        val nextButton = CommandButton.Builder()
            .setIconResId(R.drawable.ic_skip_next)
            .setDisplayName(getString(labels.next))
            .setSessionCommand(NEXT_COMMAND)
            .setEnabled(true)
            .build()
        return ImmutableList.of(repeatButton, nextButton)
    }

    private fun launchAppIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        /**
         * Only trusted controllers may start lessons (which open the microphone): this app, the
         * system (notification, Bluetooth / steering-wheel buttons), Android Auto and Assistant.
         */
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            try {
                Log.d(TAG, "onConnect from ${controller.packageName} (uid=${controller.uid}, isTrusted=${controller.isTrusted})")
                if (!isAllowedController(controller)) {
                    Log.w(TAG, "Rejected media controller ${controller.packageName} (uid=${controller.uid})")
                    return MediaSession.ConnectionResult.reject()
                }
                val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(REPEAT_COMMAND)
                    .add(NEXT_COMMAND)
                    .build()

                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailableSessionCommands(sessionCommands)
                    .setCustomLayout(customLayout(engine.lesson.value?.mode))
                    .build()
            } catch (t: Throwable) {
                Log.e(TAG, "Exception during onConnect from ${controller.packageName}", t)
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_ACTION_REPEAT -> {
                    engine.repeat()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CUSTOM_ACTION_NEXT -> {
                    engine.next()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootParams = LibraryParams.Builder().setExtras(contentProvider.rootExtras()).build()
            return Futures.immediateFuture(LibraryResult.ofItem(contentProvider.rootItem(), rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            LibraryResult.ofItemList(contentProvider.children(parentId), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            contentProvider.item(mediaId)?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        /**
         * Resolves what a controller asks to play. Items from the browse tree carry a media id;
         * "Hey Google, play … on SpeakDrive" arrives as a search query instead.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> = serviceScope.future {
            mediaItems.mapNotNull { requested ->
                val query = requested.requestMetadata.searchQuery
                val mediaId = if (query != null || requested.mediaId.isEmpty()) {
                    voiceCommandHandler.resolve(query)
                } else {
                    requested.mediaId
                }
                contentProvider.item(mediaId)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, contentProvider.search(query).size, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(contentProvider.search(query), params))

        /** "Play" pressed in the car with nothing selected: continue the most recent topic. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val item = contentProvider.item(MediaIds.RESUME) ?: contentProvider.rootItem()
            MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0L)
        }
    }

    companion object {
        const val TAG = "SpeakDriveMediaService"
        const val CUSTOM_ACTION_REPEAT = "com.speakdrive.auto.ACTION_REPEAT"
        const val CUSTOM_ACTION_NEXT = "com.speakdrive.auto.ACTION_NEXT"
        private val REPEAT_COMMAND = SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY)
        private val NEXT_COMMAND = SessionCommand(CUSTOM_ACTION_NEXT, Bundle.EMPTY)

        /** Media controllers allowed to connect besides this app and system apps. */
        val TRUSTED_CONTROLLERS = setOf(
            "com.google.android.projection.gearhead", // Android Auto
            "com.google.android.googlequicksearchbox", // Google Assistant
            "com.google.android.carassistant",
            "com.google.android.wearable.app",
            "com.google.android.apps.automotive.templates.host"
        )
    }
}
