package com.speakdrive.playback

import android.util.Log
import com.speakdrive.ai.ConversationEngine
import com.speakdrive.auto.MediaIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Automatically triggers lesson playback when Android Auto connects,
 * if no lesson is already running.
 */
@Singleton
class CarConnectionAutoStarter @Inject constructor(
    private val carConnection: CarConnectionObserver,
    private val playbackConnection: PlaybackConnection,
    private val engine: ConversationEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wasConnected = false
    private var isStarted = false

    fun start() {
        if (isStarted) return
        isStarted = true
        scope.launch {
            carConnection.isConnectedToCar
                .catch { e -> Log.w(TAG, "Car connection collection error", e) }
                .collect { isConnected ->
                engine.setCarConnected(isConnected)
                if (isConnected && !wasConnected) {
                    Log.d(TAG, "Android Auto connected, automatically starting lesson")
                    try {
                        if (!engine.state.value.isInLesson) {
                            Log.i(TAG, "Auto-starting lesson on Android Auto connection")
                            playbackConnection.play(MediaIds.RESUME)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to auto-start lesson on car connect", e)
                    }
                }
                wasConnected = isConnected
            }
        }
    }

    private companion object {
        const val TAG = "CarConnectionAutoStarter"
    }
}
