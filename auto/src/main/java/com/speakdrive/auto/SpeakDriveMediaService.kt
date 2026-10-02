package com.speakdrive.auto

import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.common.util.UnstableApi

@UnstableApi
@AndroidEntryPoint
class SpeakDriveMediaService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: SpeakDrivePlayer
    private val voiceCommandHandler = VoiceCommandHandler()

    override fun onCreate() {
        super.onCreate()
        
        player = SpeakDrivePlayer()
        
        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.release()
        mediaLibrarySession = null
        player.release()
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(
                LibraryResult.ofItem(MediaContentProvider.getRootItem(), params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = MediaContentProvider.getChildren(parentId)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(children, params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = MediaContentProvider.getChildren(MediaContentProvider.TOPICS_ID)
                .find { it.mediaId == mediaId } 
                ?: MediaContentProvider.getChildren(MediaContentProvider.SETTINGS_ID)
                    .find { it.mediaId == mediaId }
                ?: MediaContentProvider.getRootItem()
            
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            voiceCommandHandler.handlePlayFromSearch(query, params?.extras)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
    }
}
