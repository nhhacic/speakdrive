package com.speakdrive.auto

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.SessionStore
import javax.inject.Inject

/** Builds the media tree Android Auto browses (see [MediaIds] for its shape). */
class MediaContentProvider @Inject constructor(
    private val topicManager: TopicManager,
    private val sessionStore: SessionStore,
    private val settings: LearningSettings
) {
    fun rootItem(): MediaItem = browsable(MediaIds.ROOT, "SpeakDrive", "Luyện nói tiếng Anh khi lái xe")

    /** Hints for Android Auto: show every level as a plain list (large text, easy to tap while parked). */
    @OptIn(UnstableApi::class)
    fun rootExtras(): Bundle = Bundle().apply {
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
    }

    suspend fun children(parentId: String): List<MediaItem> = when (val target = MediaIds.parse(parentId)) {
        is MediaTarget.Browse -> browseChildren(target.mediaId)
        else -> emptyList()
    }

    private suspend fun browseChildren(parentId: String): List<MediaItem> = when {
        parentId == MediaIds.ROOT -> listOf(
            browsable(MediaIds.HOME, "Bắt đầu", "Tiếp tục, ngẫu nhiên, ôn tập"),
            browsable(MediaIds.TOPICS, "Chủ đề", "8 chủ đề hội thoại"),
            browsable(MediaIds.ROLEPLAY, "Nhập vai", "AI đóng vai trong tình huống thực tế"),
            browsable(MediaIds.LEVELS, "Độ khó", settings.snapshot().level.displayName)
        )
        parentId == MediaIds.HOME -> homeItems()
        parentId == MediaIds.TOPICS -> topicManager.getAllTopics().map { topic ->
            playable(MediaIds.topic(topic.id), "${topic.emoji} ${topic.titleVi}", topic.titleEn)
        }
        parentId == MediaIds.ROLEPLAY -> topicManager.getAllTopics().map { topic ->
            browsable(MediaIds.roleplayTopic(topic.id), "${topic.emoji} ${topic.titleVi}", "${topic.scenarios.size} tình huống")
        }
        parentId.startsWith(MediaIds.roleplayTopic("")) -> {
            val topic = topicManager.getTopicById(parentId.removePrefix(MediaIds.roleplayTopic("")))
            topic?.scenarios.orEmpty().map { playable(MediaIds.scenario(it.id), it.titleVi, it.titleEn) }
        }
        parentId == MediaIds.LEVELS -> {
            val current = settings.snapshot().level
            DifficultyLevel.entries.map { level ->
                val mark = if (level == current) "✓ " else ""
                playable(MediaIds.level(level), "$mark${level.displayName}", "${level.labelVi} (${level.cefr})")
            }
        }
        else -> emptyList()
    }

    private suspend fun homeItems(): List<MediaItem> {
        val lastTopic = topicManager.getTopicById(settings.snapshot().lastTopicId)
        val dueCount = sessionStore.wordsDueForReview(limit = 50).size
        return listOf(
            playable(
                MediaIds.RESUME,
                "Tiếp tục bài học",
                lastTopic?.let { "${it.emoji} ${it.titleVi}" } ?: "AI chọn chủ đề cho bạn"
            ),
            playable(MediaIds.RANDOM, "Chủ đề ngẫu nhiên", "Thử một chủ đề mới"),
            playable(
                MediaIds.REVIEW,
                "Ôn tập từ vựng",
                if (dueCount > 0) "$dueCount từ đến hạn ôn" else "Chưa có từ cần ôn — AI sẽ trò chuyện tự do"
            )
        )
    }

    /** Resolves any media id Android Auto or the phone may send back to us. */
    suspend fun item(mediaId: String): MediaItem? = when (val target = MediaIds.parse(mediaId)) {
        MediaTarget.Resume, MediaTarget.Random, MediaTarget.Review -> homeItems().find { it.mediaId == mediaId }
        is MediaTarget.Topic -> topicManager.getTopicById(target.topicId)?.let { topic ->
            val levelNote = target.level?.let { " • ${it.displayName}" }.orEmpty()
            playable(mediaId, "${topic.emoji} ${topic.titleVi}", topic.titleEn + levelNote)
        }
        is MediaTarget.Scenario -> topicManager.getScenario(target.scenarioId)?.let { (_, scenario) ->
            playable(mediaId, scenario.titleVi, scenario.titleEn)
        }
        is MediaTarget.Level -> playable(mediaId, target.level.displayName, target.level.labelVi)
        is MediaTarget.Browse -> if (mediaId == MediaIds.ROOT) rootItem() else browsable(mediaId, mediaId, "")
        MediaTarget.Unknown -> null
    }

    /** Voice/text search from Android Auto's search screen. */
    fun search(query: String): List<MediaItem> {
        val q = TopicManager.normalize(query)
        if (q.isBlank()) return emptyList()
        return topicManager.getAllTopics().flatMap { topic ->
            val topicHit = topicManager.findTopicByQuery(query)?.id == topic.id
            val scenarioHits = topic.scenarios.filter { TopicManager.normalize(it.titleVi + " " + it.titleEn).contains(q) }
            buildList {
                if (topicHit) add(playable(MediaIds.topic(topic.id), "${topic.emoji} ${topic.titleVi}", topic.titleEn))
                scenarioHits.forEach { add(playable(MediaIds.scenario(it.id), it.titleVi, it.titleEn)) }
            }
        }
    }

    fun randomTopicIdExcept(topicId: String): String =
        topicManager.getAllTopics().filter { it.id != topicId }.random().id

    /** The "now playing" item, whose subtitle tells the driver what is happening. */
    fun lessonItem(lesson: ActiveLesson, state: ConversationState): MediaItem {
        val status = when (state) {
            ConversationState.CONNECTING -> "Đang kết nối…"
            ConversationState.ACTIVE -> "Đang trò chuyện"
            ConversationState.PAUSED -> "Tạm dừng"
            ConversationState.RECONNECTING -> "Đang kết nối lại…"
            ConversationState.WAITING_FOR_NETWORK -> "Chờ có mạng…"
            ConversationState.ENDING -> "Đang tổng kết…"
            else -> ""
        }
        val metadata = MediaMetadata.Builder()
            .setTitle("${lesson.topic.emoji} ${lesson.titleVi}")
            .setArtist(listOf(lesson.level.displayName, status).filter { it.isNotEmpty() }.joinToString(" • "))
            .setAlbumTitle("SpeakDrive")
            .setDisplayTitle(lesson.titleVi)
            .setSubtitle(status)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
        return MediaItem.Builder().setMediaId(MediaIds.LESSON).setMediaMetadata(metadata).build()
    }

    private fun browsable(id: String, title: String, subtitle: String) = item(id, title, subtitle, browsable = true)

    private fun playable(id: String, title: String, subtitle: String) = item(id, title, subtitle, browsable = false)

    private fun item(id: String, title: String, subtitle: String, browsable: Boolean): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setIsBrowsable(browsable)
            .setIsPlayable(!browsable)
            .setMediaType(if (browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }
}
