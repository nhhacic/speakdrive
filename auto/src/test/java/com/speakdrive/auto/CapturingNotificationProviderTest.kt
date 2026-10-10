package com.speakdrive.auto

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CapturingNotificationProviderTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private lateinit var session: MediaSession
    private lateinit var appIntent: PendingIntent

    @Before
    fun setUp() {
        session = MediaSession.Builder(context, IdlePlayer()).build()
        appIntent = PendingIntent.getActivity(context, 0, Intent(Intent.ACTION_MAIN), PendingIntent.FLAG_IMMUTABLE)
    }

    @After
    fun tearDown() {
        session.release()
    }

    @Test
    fun `tapping the phone notification opens the app`() {
        val delegate = FakeProvider(notification())
        val provider = CapturingNotificationProvider(delegate, appIntent) {}

        val created = provider.createNotification(session, ImmutableList.of(), UnusedActionFactory, {})

        assertThat(created.notification.contentIntent).isSameInstanceAs(appIntent)
        assertThat(provider.latest).isSameInstanceAs(created)
    }

    @Test
    fun `a notification rebuilt later (e g with the artwork) still opens the app`() {
        val delegate = FakeProvider(notification())
        var forwarded: MediaNotification? = null
        var updates = 0
        val provider = CapturingNotificationProvider(delegate, appIntent) { updates++ }
        provider.createNotification(session, ImmutableList.of(), UnusedActionFactory) { forwarded = it }

        val rebuilt = MediaNotification(NOTIFICATION_ID, notification())
        delegate.callback!!.onNotificationChanged(rebuilt)

        assertThat(rebuilt.notification.contentIntent).isSameInstanceAs(appIntent)
        assertThat(forwarded).isSameInstanceAs(rebuilt)
        assertThat(provider.latest).isSameInstanceAs(rebuilt)
        assertThat(updates).isEqualTo(1)
    }

    @Test
    fun `an intent the notification already has is kept`() {
        val own = PendingIntent.getActivity(context, 1, Intent(Intent.ACTION_VIEW), PendingIntent.FLAG_IMMUTABLE)
        val delegate = FakeProvider(notification(contentIntent = own))
        val provider = CapturingNotificationProvider(delegate, appIntent) {}

        val created = provider.createNotification(session, ImmutableList.of(), UnusedActionFactory, {})

        assertThat(created.notification.contentIntent).isSameInstanceAs(own)
    }

    private fun notification(contentIntent: PendingIntent? = null): Notification =
        NotificationCompat.Builder(context, "lesson")
            .setContentIntent(contentIntent)
            .build()

    private class FakeProvider(private val notification: Notification) : MediaNotification.Provider {
        var callback: MediaNotification.Provider.Callback? = null

        override fun createNotification(
            mediaSession: MediaSession,
            mediaButtonPreferences: ImmutableList<CommandButton>,
            actionFactory: MediaNotification.ActionFactory,
            onNotificationChangedCallback: MediaNotification.Provider.Callback
        ): MediaNotification {
            callback = onNotificationChangedCallback
            return MediaNotification(NOTIFICATION_ID, notification)
        }

        override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle) = false

        override fun getNotificationChannelInfo() =
            MediaNotification.Provider.NotificationChannelInfo("lesson", "Lesson")
    }

    private object UnusedActionFactory : MediaNotification.ActionFactory {
        override fun createMediaAction(mediaSession: MediaSession, icon: IconCompat, title: CharSequence, command: Int) =
            error("unused")

        override fun createCustomAction(
            mediaSession: MediaSession,
            icon: IconCompat,
            title: CharSequence,
            customAction: String,
            extras: Bundle
        ) = error("unused")

        override fun createCustomActionFromCustomCommandButton(mediaSession: MediaSession, customCommandButton: CommandButton) =
            error("unused")

        override fun createMediaActionPendingIntent(mediaSession: MediaSession, command: Int) = error("unused")
    }

    private class IdlePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState() = State.Builder().build()
    }

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
