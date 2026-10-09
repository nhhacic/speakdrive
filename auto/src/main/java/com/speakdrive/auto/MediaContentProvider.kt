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
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.TranscriptTurn
import com.speakdrive.ai.session.LearningSettings
import com.speakdrive.ai.session.SessionStore
import javax.inject.Inject

/** Builds the media tree Android Auto browses (see [MediaIds] for its shape). */
class MediaContentProvider @Inject constructor(
    private val topicManager: TopicManager,
    private val sessionStore: SessionStore,
    private val settings: LearningSettings,
    private val artworkGenerator: AutoCardArtworkGenerator = AutoCardArtworkGenerator()
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
        // Android Auto shows at most 4 tabs; the level picker lives at the end of "Bắt đầu".
        parentId == MediaIds.ROOT -> listOf(
            browsable(MediaIds.HOME, "Bắt đầu", "Tiếp tục, kể chuyện, ngẫu nhiên, phát âm, ôn tập, độ khó"),
            browsable(MediaIds.STORIES, "🎧 Luyện nghe kể chuyện", "AI kể chuyện người nổi tiếng, khoa học, lịch sử"),
            browsable(MediaIds.TOPICS, "Chủ đề", "${topicManager.getConversationTopics().size} chủ đề hội thoại"),
            browsable(MediaIds.ROLEPLAY, "Nhập vai", "AI đóng vai trong tình huống thực tế")
        )
        parentId == MediaIds.HOME -> homeItems()
        parentId == MediaIds.STORIES -> {
            val unfinished = sessionStore.latestUnfinishedStorySession()
            val recent = sessionStore.recentStorySessions(5)
            buildList {
                if (unfinished != null) {
                    val topic = topicManager.getTopicById(unfinished.topicId)
                    val scenario = unfinished.scenarioId?.let { topicManager.getScenario(it)?.second }
                    val title = scenario?.titleVi ?: (topic?.titleVi ?: "câu chuyện trước")
                    add(
                        playable(
                            MediaIds.STORY_RESUME,
                            "▶ Tiếp tục: $title",
                            "Nghe tiếp câu chuyện đang dở dang (${topic?.titleEn ?: "Story"})"
                        )
                    )
                }
                add(
                    playable(
                        MediaIds.STORY_RECOMMENDED,
                        "✨ Chuyện gợi ý cho bạn",
                        "AI chọn câu chuyện hấp dẫn theo sở thích của bạn"
                    )
                )
                add(
                    playable(
                        MediaIds.STORY_RANDOM,
                        "🎲 Chuyện ngẫu nhiên bất ngờ",
                        "Nghe một mẩu chuyện ngẫu nhiên từ AI"
                    )
                )
                topicManager.getStoryTopics().forEach { topic ->
                    add(
                        browsable(
                            MediaIds.storyTopic(topic.id),
                            "${topic.emoji} ${topic.titleVi}",
                            "${topic.scenarios.size} câu chuyện hay (${topic.titleEn})"
                        )
                    )
                }
                if (recent.isNotEmpty()) {
                    add(
                        browsable(
                            MediaIds.browse("stories_recent"),
                            "🕒 Đã nghe gần đây",
                            "${recent.size} câu chuyện nghe gần đây nhất"
                        )
                    )
                }
            }
        }
        parentId.startsWith(MediaIds.storyTopic("")) -> {
            val topicId = parentId.removePrefix(MediaIds.storyTopic(""))
            val topic = topicManager.getTopicById(topicId)
            if (topic != null) {
                listOf(
                    playable(
                        MediaIds.story(topic.id, "${TopicManager.DYNAMIC_PREFIX}story_recommended_${topic.id}"),
                        "✨ AI kể chuyện mới trong ${topic.titleVi}",
                        "AI sáng tác câu chuyện mới theo chủ đề ${topic.titleEn}"
                    )
                ) + topic.scenarios.map { playable(MediaIds.story(topic.id, it.id), it.titleVi, it.titleEn) }
            } else {
                emptyList()
            }
        }
        parentId == MediaIds.STORIES_RECENT || parentId == MediaIds.browse("stories_recent") -> {
            val recent = sessionStore.recentStorySessions(10)
            recent.map { session ->
                val topic = topicManager.getTopicById(session.topicId)
                val topicTitle = topic?.titleVi ?: session.topicId
                val scenarioTitle = session.scenarioId?.let { sId ->
                    topic?.scenarios?.firstOrNull { it.id == sId }?.titleVi
                } ?: topicTitle
                val statusText = if (session.isCompleted) "Đã hoàn thành" else "Chưa nghe hết"
                val subtitle = "$topicTitle • $statusText"
                val targetScenarioId = session.scenarioId ?: "${TopicManager.DYNAMIC_PREFIX}story_recommended_${session.topicId}"
                playable(
                    MediaIds.story(session.topicId, targetScenarioId),
                    "🎧 $scenarioTitle",
                    subtitle
                )
            }
        }
        parentId == MediaIds.TOPICS -> topicManager.getConversationTopics().map { topic ->
            playable(MediaIds.topic(topic.id), "${topic.emoji} ${topic.titleVi}", topic.titleEn)
        }
        parentId == MediaIds.ROLEPLAY -> {
            val customs = sessionStore.customScenarios()
            val customItem = if (customs.isNotEmpty()) {
                listOf(
                    browsable(
                        MediaIds.CUSTOM_SCENARIOS,
                        "⭐ Tình huống tự tạo của bạn",
                        "${customs.size} tình huống riêng bạn đã lưu"
                    )
                )
            } else emptyList()
            customItem + topicManager.getConversationTopics().map { topic ->
                browsable(MediaIds.roleplayTopic(topic.id), "${topic.emoji} ${topic.titleVi}", "${topic.scenarios.size} tình huống mẫu + AI mở rộng")
            }
        }
        parentId == MediaIds.CUSTOM_SCENARIOS -> {
            sessionStore.customScenarios().map { custom ->
                playable(
                    MediaIds.scenario(custom.id),
                    custom.titleVi,
                    custom.missionObjective?.let { m -> "🎯 $m" } ?: custom.titleEn
                )
            }
        }
        parentId.startsWith(MediaIds.roleplayTopic("")) -> {
            val topic = topicManager.getTopicById(parentId.removePrefix(MediaIds.roleplayTopic("")))
            if (topic != null) {
                listOf(
                    playable(
                        MediaIds.scenario("${TopicManager.DYNAMIC_PREFIX}explore_${topic.id}"),
                        "🎲 Khám phá tình huống AI mới",
                        "AI tạo tình huống chuyên sâu ngẫu nhiên trong ${topic.titleVi}"
                    )
                ) + topic.scenarios.map {
                    playable(MediaIds.scenario(it.id), it.titleVi, it.missionObjective?.let { m -> "🎯 $m" } ?: it.titleEn)
                }
            } else {
                emptyList()
            }
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
        val snapshot = settings.snapshot()
        val lastTopic = topicManager.getTopicById(snapshot.lastTopicId)
        val dueCount = sessionStore.wordsDueForReview(limit = 50).size
        val dueMistakes = sessionStore.mistakesDueForReview(limit = 50).size
        val resumeSubtitle = when (snapshot.lastSessionMode) {
            SessionMode.REPEAT_AFTER_ME ->
                "🗣️ Luyện phát âm (Shadowing)" + (lastTopic?.let { " • ${it.titleVi}" } ?: "")
            SessionMode.ROLEPLAY -> {
                val scenario = snapshot.lastScenarioId?.let { sId ->
                    topicManager.getScenario(sId)?.second
                }
                "🎭 Nhập vai" + (scenario?.let { " • ${it.titleVi}" } ?: lastTopic?.let { " • ${it.titleVi}" } ?: "")
            }
            SessionMode.VOCAB_REVIEW ->
                "📚 Ôn tập từ vựng" + (lastTopic?.let { " • ${it.titleVi}" } ?: "")
            SessionMode.MISTAKE_REVIEW -> "🔁 Ôn lỗi sai"
            SessionMode.STORY_LISTENING ->
                "🎧 Luyện nghe kể chuyện" + (lastTopic?.let { " • ${it.titleVi}" } ?: "")
            SessionMode.IELTS_SPEAKING ->
                "🎯 Luyện thi IELTS" + (lastTopic?.let { " • ${it.titleVi}" } ?: "")
            SessionMode.FREE_TALK ->
                lastTopic?.let { "${it.emoji} ${it.titleVi}" } ?: "AI chọn chủ đề cho bạn"
        }
        return listOf(
            playable(
                MediaIds.RESUME,
                "Tiếp tục bài học",
                resumeSubtitle
            ),
            playable(
                MediaIds.STORY_RECOMMENDED,
                "🎧 Luyện nghe kể chuyện",
                "AI chọn câu chuyện hấp dẫn theo sở thích của bạn"
            ),
            playable(MediaIds.RANDOM, "Chủ đề ngẫu nhiên", "Thử một chủ đề mới"),
            playable(
                MediaIds.PRONUNCIATION,
                "Luyện phát âm",
                "Nhắc lại câu của AI, chấm từng từ" + (lastTopic?.let { " • ${it.titleVi}" } ?: "")
            ),
            playable(
                MediaIds.REVIEW,
                "Ôn tập từ vựng",
                if (dueCount > 0) "$dueCount từ đến hạn ôn" else "Chưa có từ cần ôn — AI sẽ trò chuyện tự do"
            ),
            playable(
                MediaIds.MISTAKES,
                "Ôn lỗi sai",
                if (dueMistakes > 0) "$dueMistakes câu sai đến hạn nói lại" else "Nói lại đúng những câu từng sai"
            ),
            playable(
                MediaIds.IELTS,
                "🎯 Luyện thi IELTS Speaking",
                "Luyện Part 1, 2, 3 và ước lượng Band Score"
            ),
            browsable(MediaIds.LEVELS, "Độ khó: ${snapshot.level.displayName}", "Chọn cấp độ từ A1 đến C2")
        )
    }

    /** Resolves any media id Android Auto or the phone may send back to us. */
    suspend fun item(mediaId: String): MediaItem? = when (val target = MediaIds.parse(mediaId)) {
        MediaTarget.Resume, MediaTarget.Random, MediaTarget.Review, MediaTarget.Mistakes, MediaTarget.Ielts -> homeItems().find { it.mediaId == mediaId }
        is MediaTarget.Vocab -> {
            val title = if (target.word != null) "Học từ: ${target.word}" else "Luyện tập từ vựng"
            val sub = when (target.mode) {
                "pronunciation" -> "Luyện phát âm từ vựng"
                "sentence" -> "Thử thách đặt câu"
                else -> "Học từ vựng & phát âm"
            }
            playable(mediaId, title, sub)
        }
        MediaTarget.StoryRecommended -> playable(
            mediaId,
            "✨ Chuyện gợi ý cho bạn",
            "AI chọn câu chuyện hấp dẫn theo sở thích của bạn"
        )
        MediaTarget.StoryResume -> {
            val unfinished = sessionStore.latestUnfinishedStorySession()
            val topic = unfinished?.let { topicManager.getTopicById(it.topicId) }
            val scenario = unfinished?.scenarioId?.let { topicManager.getScenario(it)?.second }
            val title = scenario?.titleVi ?: (topic?.titleVi ?: "câu chuyện trước")
            playable(
                mediaId,
                "▶ Tiếp tục: $title",
                "Nghe tiếp câu chuyện đang dở dang"
            )
        }
        is MediaTarget.Story -> {
            val topic = topicManager.getTopicById(target.topicId)
            val scenario = target.scenarioId?.let { topicManager.getScenario(it)?.second }
            val title = scenario?.titleVi ?: (topic?.let { "${it.emoji} Kể chuyện: ${it.titleVi}" } ?: "Luyện nghe kể chuyện")
            val subtitle = scenario?.titleEn ?: (topic?.titleEn ?: "AI Story Listening")
            playable(mediaId, title, subtitle)
        }
        is MediaTarget.Pronunciation -> {
            val topic = topicManager.getTopicById(target.topicId) ?: topicManager.getTopicById(settings.snapshot().lastTopicId)
            playable(mediaId, "Luyện phát âm", topic?.let { "${it.emoji} ${it.titleVi}" } ?: "Nhắc lại câu của AI")
        }
        is MediaTarget.Topic -> topicManager.getTopicById(target.topicId)?.let { topic ->
            val levelNote = target.level?.let { " • ${it.displayName}" }.orEmpty()
            playable(mediaId, "${topic.emoji} ${topic.titleVi}", topic.titleEn + levelNote)
        }
        is MediaTarget.Scenario -> topicManager.getScenario(target.scenarioId)?.let { (_, scenario) ->
            playable(mediaId, scenario.titleVi, scenario.missionObjective?.let { "🎯 $it" } ?: scenario.titleEn)
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
        topicManager.getAllTopics().filter { it.id != topicId }.randomOrNull()?.id ?: topicId

    /** The "now playing" item, whose metadata and artwork tell the driver what is happening. */
    fun lessonItem(
        lesson: ActiveLesson,
        state: ConversationState,
        transcript: List<TranscriptTurn> = emptyList(),
        drillTarget: String? = null,
        drillTargetTranslation: String? = null,
        drillStatus: DrillStatus? = null
    ): MediaItem {
        val isStory = lesson.mode == SessionMode.STORY_LISTENING
        val status = when (state) {
            ConversationState.CONNECTING -> if (isStory) "Đang chọn truyện…" else "Đang kết nối…"
            ConversationState.ACTIVE -> if (isStory) "Đang kể chuyện" else "Đang trò chuyện"
            ConversationState.PAUSED -> "Tạm dừng"
            ConversationState.RECONNECTING -> "Đang kết nối lại…"
            ConversationState.WAITING_FOR_NETWORK -> "Chờ có mạng…"
            ConversationState.ENDING -> "Đang tổng kết…"
            else -> ""
        }

        // The title stays the topic while driving: a title that changes with every AI sentence is
        // text the driver would be tempted to read (Android Auto driver-distraction rules).
        val hasTarget = !drillTarget.isNullOrBlank()

        val (title, subtitle, artist) = when {
            hasTarget -> {
                val title = drillTarget
                val subtitle = if (!drillTargetTranslation.isNullOrBlank()) {
                    "🇻🇳 $drillTargetTranslation"
                } else {
                    "🗣️ Nhắc lại theo AI"
                }
                val artist = "${lesson.level.displayName} • SpeakDrive"
                Triple(title, subtitle, artist)
            }
            lesson.mode == SessionMode.REPEAT_AFTER_ME -> {
                val title = "🎯 Luyện phát âm • ${lesson.topic.emoji} ${lesson.topic.titleVi}"
                val subtitle = if (status.isNotEmpty()) "$status • Hãy nghe và nhắc lại" else "Hãy nghe và nhắc lại"
                val artist = "${lesson.level.displayName} • Luyện phát âm"
                Triple(title, subtitle, artist)
            }
            isStory -> {
                val storyTitle = lesson.scenario?.titleVi ?: lesson.titleVi
                val title = "📖 $storyTitle"
                val subtitle = if (status.isNotEmpty()) "$status • Bấm Next để đổi truyện" else "Bấm Next để đổi truyện"
                val artist = "${lesson.topic.emoji} ${lesson.titleVi} • ${lesson.level.displayName}"
                Triple(title, subtitle, artist)
            }
            else -> {
                val title = "${lesson.topic.emoji} ${lesson.titleVi}"
                val subtitle = if (status.isNotEmpty()) "$status • Nói tự nhiên bằng tiếng Anh" else "Nói tự nhiên bằng tiếng Anh"
                val artist = listOf(lesson.level.displayName, status).filter { it.isNotEmpty() }.joinToString(" • ")
                Triple(title, subtitle, artist)
            }
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setSubtitle(subtitle)
            .setArtist(artist)
            .setAlbumTitle("SpeakDrive")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)

        val cardArtwork = artworkGenerator.generateCard(lesson, transcript, drillTarget, drillTargetTranslation, drillStatus)
        if (cardArtwork != null) {
            metadataBuilder.setArtworkData(cardArtwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder().setMediaId(MediaIds.LESSON).setMediaMetadata(metadataBuilder.build()).build()
    }

    /** Standby item shown on Android Auto when no lesson is active, keeping the app on screen. */
    fun standbyItem(justEnded: Boolean = false): MediaItem {
        val title = if (justEnded) "✓ Đã hoàn thành bài học" else "Luyện nói tiếng Anh"
        val subtitle = if (justEnded) "Bấm ▶ để tiếp tục bài mới" else "SpeakDrive • Sẵn sàng luyện tập"
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setDisplayTitle(title)
            .setSubtitle(subtitle)
            .setArtist("SpeakDrive • Bấm ▶ để bắt đầu")
            .setAlbumTitle("SpeakDrive")
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
        return MediaItem.Builder().setMediaId(MediaIds.RESUME).setMediaMetadata(metadata).build()
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
