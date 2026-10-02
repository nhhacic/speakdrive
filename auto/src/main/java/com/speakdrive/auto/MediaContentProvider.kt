package com.speakdrive.auto

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

object MediaContentProvider {
    const val ROOT_ID = "speakdrive_root"
    const val TOPICS_ID = "topics"
    const val SETTINGS_ID = "settings"
    const val RESUME_ID = "resume"
    const val RANDOM_ID = "random"

    val topics = listOf(
        "Travel", "Work", "Food", "Shopping", 
        "Health", "Entertainment", "Daily", "Interview"
    )

    fun getRootItem(): MediaItem {
        return buildBrowsableItem(ROOT_ID, "SpeakDrive", "English Speaking Practice")
    }

    fun getChildren(parentId: String): List<MediaItem> {
        return when (parentId) {
            ROOT_ID -> listOf(
                buildPlayableItem(RESUME_ID, "Resume", "Continue last session"),
                buildBrowsableItem(TOPICS_ID, "Topics", "Choose a conversation topic"),
                buildPlayableItem(RANDOM_ID, "Random", "Practice random topic"),
                buildBrowsableItem(SETTINGS_ID, "Settings", "Adjust difficulty")
            )
            TOPICS_ID -> topics.map { topic ->
                buildPlayableItem("topic_${topic.lowercase()}", topic, "Practice speaking about $topic")
            }
            SETTINGS_ID -> listOf(
                buildPlayableItem("level_beginner", "Beginner", "Easy conversations"),
                buildPlayableItem("level_intermediate", "Intermediate", "Normal conversations"),
                buildPlayableItem("level_advanced", "Advanced", "Challenging conversations")
            )
            else -> emptyList()
        }
    }

    private fun buildBrowsableItem(id: String, title: String, subtitle: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .build()
        
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun buildPlayableItem(id: String, title: String, subtitle: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .build()
        
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(metadata)
            .build()
    }
}
