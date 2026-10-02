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
class PlaybackConnection @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val mutex = Mutex()
    private var controller: MediaController? = null

    @OptIn(UnstableApi::class)
    private suspend fun controller(): MediaController = mutex.withLock {
        controller?.takeIf { it.isConnected } ?: run {
            val token = SessionToken(context, ComponentName(context, SpeakDriveMediaService::class.java))
            MediaController.Builder(context, token).buildAsync().await().also { controller = it }
        }
    }

    /** Starts the lesson behind [mediaId] (see com.speakdrive.auto.MediaIds). */
    suspend fun play(mediaId: String) {
        val controller = controller()
        controller.setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
        controller.prepare()
        controller.play()
    }

    fun release() {
        controller?.release()
        controller = null
    }
}
