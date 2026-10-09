package com.speakdrive.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.auto.MediaIds
import com.speakdrive.auto.MediaLessonResolver
import com.speakdrive.auto.MediaTarget
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
 *
 * If the MediaController connection fails for any reason (e.g. system session rejection,
 * timeout, background restrictions), it gracefully falls back to starting the engine directly
 * and kicking off the background service so the lesson never fails to start.
 */
@Singleton
open class PlaybackConnection internal constructor(
    private val context: Context?,
    private val engine: ConversationEngine?,
    private val settings: LearningSettings?,
    @Suppress("UNUSED_PARAMETER") forTesting: Boolean
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        engine: ConversationEngine,
        settings: LearningSettings
    ) : this(context, engine, settings, false)

    constructor() : this(null, null, null, true)

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
        try {
            val controller = controller()
            controller.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
            controller.prepare()
            controller.play()
        } catch (e: Exception) {
            Log.w(TAG, "MediaController.play failed ($e); falling back to direct engine start", e)
            fallbackStart(mediaId)
        }
    }

    private suspend fun fallbackStart(mediaId: String) {
        val eng = engine
        val st = settings

        // Start engine directly with the resolved LessonRequest
        if (eng != null) {
            if (mediaId == MediaIds.STORY_RESUME || MediaIds.parse(mediaId) is MediaTarget.StoryResume) {
                eng.resumeStory()
                return
            }
            val request = if (st != null) MediaLessonResolver.resolve(mediaId, st) else null
            if (request != null) {
                eng.start(request)
            }
        }
    }

    /** Stops playback on the media controller. */
    open suspend fun stop() {
        mutex.withLock {
            try {
                controller?.takeIf { it.isConnected }?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop controller", e)
            }
        }
        engine?.end()
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

    companion object {
        private const val TAG = "PlaybackConnection"
    }
}
