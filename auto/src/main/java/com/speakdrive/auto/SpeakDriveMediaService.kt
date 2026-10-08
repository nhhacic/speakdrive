package com.speakdrive.auto

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.MicPermissionChecker
import com.speakdrive.audio.LiveAudio
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
            onNotificationUpdated = { mainHandler.post(::ensureMicrophoneForegroundType) }
        )
        setMediaNotificationProvider(notificationProvider)

        val player = SpeakDrivePlayer(Looper.getMainLooper(), engine, settings, contentProvider, serviceScope)
        val builder = MediaLibrarySession.Builder(this, player, LibraryCallback())
        launchAppIntent()?.let(builder::setSessionActivity)
        librarySession = builder.build()

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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onUpdateNotificationAsync(session: MediaSession, startInForegroundRequired: Boolean): ListenableFuture<Void?> {
        // When Media3 must start the foreground service (playback resumption from the car), always
        // post the notification, even before the engine has left IDLE.
        if (!engine.state.value.isInLesson && !startInForegroundRequired) {
            dismissNotificationAndStopIfIdle()
            return Futures.immediateFuture(null)
        }
        val future = super.onUpdateNotificationAsync(session, startInForegroundRequired)
        if (startInForegroundRequired) {
            future.addListener(::ensureMicrophoneForegroundType, ContextCompat.getMainExecutor(this))
        }
        return future
    }

    /**
     * Stops the foreground service and removes any stale notification when no lesson is active.
     */
    private fun dismissNotificationAndStopIfIdle() {
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

        // If car is not connected and no lesson is in progress, stop the service.
        if (!engine.isCarConnected.value) {
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

    private fun isAllowedController(controller: MediaSession.ControllerInfo): Boolean {
        val pkg = controller.packageName
        if (controller.isTrusted || pkg == packageName || pkg in TRUSTED_CONTROLLERS) return true
        return runCatching {
            val flags = packageManager.getApplicationInfo(pkg, 0).flags
            flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
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
            if (!isAllowedController(controller)) {
                Log.w(TAG, "Rejected media controller ${controller.packageName}")
                return MediaSession.ConnectionResult.reject()
            }
            return super.onConnect(session, controller)
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

    private companion object {
        const val TAG = "SpeakDriveMediaService"

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
