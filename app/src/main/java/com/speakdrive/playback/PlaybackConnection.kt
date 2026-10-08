package com.speakdrive.playback

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.speakdrive.auto.SpeakDriveMediaService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts lessons from the phone through the same media service Android Auto uses. Going
 * through the service (instead of calling the engine directly) makes Media3 put it in the
 * foreground, so the lesson survives the screen turning off.
 */
@Singleton
open class PlaybackConnection internal constructor(
    private val context: Context?,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, false)

    constructor() : this(null, true)

    private val mutex = Mutex()
    private var controller: MediaController? = null

    @OptIn(UnstableApi::class)
    private suspend fun controller(): MediaController = mutex.withLock {
        controller?.takeIf { it.isConnected } ?: run {
            // A controller whose service went away is useless; release it instead of leaking it.
            controller?.release()
            controller = null
            val ctx = checkNotNull(context) { "Context required for real PlaybackConnection" }
            val token = SessionToken(ctx, ComponentName(ctx, SpeakDriveMediaService::class.java))
            MediaController.Builder(ctx, token).buildAsync().await().also { controller = it }
        }
    }

    /** Starts the lesson behind [mediaId] (see com.speakdrive.auto.MediaIds). */
    open suspend fun play(mediaId: String) {
        val controller = controller()
        controller.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
        controller.prepare()
        controller.play()
    }

    /** Stops playback on the media controller. */
    open suspend fun stop() {
        mutex.withLock {
            controller?.takeIf { it.isConnected }?.stop()
        }
    }

    fun release() {
        // Never in the middle of controller(): it holds the lock while connecting.
        if (!mutex.tryLock()) return
        try {
            controller?.release()
            controller = null
        } finally {
            mutex.unlock()
        }
    }
}
