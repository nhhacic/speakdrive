package com.speakdrive.auto

import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Wraps Media3's notification provider and remembers the last notification it built,
 * so the service can re-post it with the microphone foreground-service type.
 */
@UnstableApi
class CapturingNotificationProvider(
    private val delegate: MediaNotification.Provider,
    private val onNotificationUpdated: () -> Unit
) : MediaNotification.Provider {

    @Volatile
    var latest: MediaNotification? = null
        private set

    fun clearLatest() {
        latest = null
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val wrappedCallback = MediaNotification.Provider.Callback { notification ->
            latest = notification
            onNotificationChangedCallback.onNotificationChanged(notification)
            onNotificationUpdated()
        }
        return delegate.createNotification(mediaSession, mediaButtonPreferences, actionFactory, wrappedCallback)
            .also { latest = it }
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.notificationChannelInfo
}
