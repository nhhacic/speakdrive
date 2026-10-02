package com.speakdrive.auto

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class SpeakDrivePlayer : SimpleBasePlayer(android.os.Looper.getMainLooper()) {
    
    private var isPlayingState = false
    
    override fun getState(): State {
        return State.Builder()
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(
                        Player.COMMAND_PLAY_PAUSE,
                        Player.COMMAND_STOP,
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS
                    )
                    .build()
            )
            .setPlayWhenReady(isPlayingState, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(if (isPlayingState) Player.STATE_READY else Player.STATE_IDLE)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        isPlayingState = playWhenReady
        // Here we would call: if (playWhenReady) conversationEngine.start() else conversationEngine.pause()
        invalidateState()
        return Futures.immediateVoidFuture()
    }
    
    override fun handleStop(): ListenableFuture<*> {
        isPlayingState = false
        // conversationEngine.stop()
        invalidateState()
        return Futures.immediateVoidFuture()
    }
    
    override fun handleSeekToNext(): ListenableFuture<*> {
        // conversationEngine.nextTopic()
        return Futures.immediateVoidFuture()
    }
}
