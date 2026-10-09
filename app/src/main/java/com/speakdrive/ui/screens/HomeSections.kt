package com.speakdrive.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakdrive.R
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ui.theme.AppColors

// The blocks of the home screen, top to bottom. Each block is one full-width piece so it
// still reads well with the large system font many learners use (150% and up).

/** One line: greeting and streak. Progress and Settings live in the bottom bar, not here. */
@Composable
internal fun HomeHeader(greeting: String, streakDays: Int, streakFreezes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        val freezes = if (streakFreezes > 0) "  " + stringResource(R.string.home_streak_freezes, streakFreezes) else ""
        val streakDesc = "🔥 $streakDays$freezes ${stringResource(R.string.home_streak_label)}"
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clearAndSetSemantics { contentDescription = streakDesc }
        ) {
            Text(
                text = "🔥 $streakDays$freezes",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/** Shown while the phone is connected to Android Auto: the car screen is the place to control practice. */
@Composable
internal fun CarConnectedBanner() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.home_car_connected_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.home_driving_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/** The main card: continue (or start) a lesson, give a voice command, or pick a random topic. */
@Composable
internal fun HomeResumeCard(
    state: HomeUiState,
    isVi: Boolean,
    voice: HomeVoiceUi,
    onResume: () -> Unit,
    onRandom: () -> Unit,
    onToggleVoice: () -> Unit
) {
    val lesson = state.currentLesson
    val isLessonActive = lesson != null
    val isPaused = isLessonActive && state.lessonState == ConversationState.PAUSED
    val lastTopic = state.lastTopic
    val accent = if (isLessonActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
    val background = if (isLessonActive) {
        Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.85f)))
    } else {
        AppColors.PrimaryGradient
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(background)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroPill {
                    when {
                        isPaused -> {
                            Icon(Icons.Filled.Pause, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            HeroPillText(stringResource(R.string.home_hero_tag_paused))
                        }
                        isLessonActive -> {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Red)
                            )
                            Spacer(Modifier.width(6.dp))
                            HeroPillText(stringResource(R.string.home_hero_tag_active))
                        }
                        else -> HeroPillText("🌟 " + stringResource(R.string.home_hero_tag_interactive))
                    }
                }
                HeroPill { HeroPillText((lesson?.level ?: state.level).cefr) }
            }

            Text(
                text = when {
                    lesson != null -> "${lesson.topic.emoji} ${lesson.getTitle(isVi)}"
                    lastTopic != null -> stringResource(
                        R.string.home_reflex_resume_prefix,
                        "${lastTopic.emoji} ${if (isVi) lastTopic.titleVi else lastTopic.titleEn}"
                    )
                    else -> stringResource(R.string.home_reflex_card_title)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (lesson == null && lastTopic == null) {
                Text(
                    text = stringResource(R.string.home_reflex_default_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = accent),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = if (isLessonActive) Icons.Filled.Radio else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        when {
                            isLessonActive -> R.string.home_hero_active_button
                            lastTopic != null -> R.string.home_btn_resume
                            else -> R.string.home_btn_start
                        }
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val listening = voice is HomeVoiceUi.Listening
                HeroSecondaryButton(
                    icon = if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    label = stringResource(if (listening) R.string.home_voice_listening else R.string.home_voice_button),
                    highlighted = listening,
                    onClick = onToggleVoice,
                    modifier = Modifier.weight(1f)
                )
                if (!isLessonActive) {
                    HeroSecondaryButton(
                        icon = Icons.Filled.Shuffle,
                        label = stringResource(R.string.home_btn_random),
                        highlighted = false,
                        onClick = onRandom,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val voiceNote = when (voice) {
                HomeVoiceUi.Idle -> null
                is HomeVoiceUi.Listening ->
                    if (voice.partial.isBlank()) stringResource(R.string.home_voice_examples) else "“${voice.partial}”"
                HomeVoiceUi.NotHeard -> stringResource(R.string.home_voice_not_heard)
                HomeVoiceUi.Unavailable -> stringResource(R.string.home_voice_unavailable)
            }
            if (voiceNote != null) {
                Text(
                    text = voiceNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

@Composable
private fun HeroPill(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.2f)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { content() }
    }
}

@Composable
private fun HeroPillText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HeroSecondaryButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = if (highlighted) 0.35f else 0.2f),
        border = if (highlighted) BorderStroke(1.5.dp, Color.White) else null,
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Minutes and new words today against the daily goal; opens Progress. */
@Composable
internal fun HomeTodayCard(state: HomeUiState, onOpenProgress: () -> Unit) {
    val goal = state.dailyGoalMinutes.coerceAtLeast(1)
    val progress = (state.stats.minutesToday.toFloat() / goal).coerceIn(0f, 1f)
    val allReviewed = state.stats.dueWordCount == 0 && state.stats.dueMistakeCount == 0
    Card(
        onClick = onOpenProgress,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_today_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.home_today_summary,
                            state.stats.minutesToday,
                            goal,
                            state.stats.wordsToday
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
            if (allReviewed) {
                Text(
                    text = stringResource(R.string.home_bento_srs_all_good),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Words and mistakes due for review, number first. Nothing is shown when everything is reviewed. */
@Composable
internal fun HomeReviewQueue(
    dueWordCount: Int,
    dueMistakeCount: Int,
    onStartReview: () -> Unit,
    onStartMistakes: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (dueWordCount > 0) {
            ReviewRow(
                emoji = "📚",
                text = pluralStringResource(R.plurals.home_review_words_due, dueWordCount, dueWordCount),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onStartReview
            )
        }
        if (dueMistakeCount > 0) {
            ReviewRow(
                emoji = "🔁",
                text = pluralStringResource(R.plurals.home_review_mistakes_due, dueMistakeCount, dueMistakeCount),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onClick = onStartMistakes
            )
        }
    }
}

@Composable
private fun ReviewRow(emoji: String, text: String, container: Color, content: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = container,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = content,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
    }
}

/** 2×2 grid of practice modes: stories, pronunciation, IELTS, roleplay. */
@Composable
internal fun HomePracticeModes(
    state: HomeUiState,
    onOpenStories: () -> Unit,
    onPronunciation: () -> Unit,
    onIelts: () -> Unit,
    onOpenRoleplay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.home_modes_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeTile(
                emoji = "🎧",
                label = stringResource(R.string.home_mode_stories),
                note = if (state.unfinishedStory != null) "▶ " + stringResource(R.string.home_story_sheet_in_progress) else null,
                onClick = onOpenStories,
                modifier = Modifier.weight(1f)
            )
            ModeTile(
                emoji = "🗣️",
                label = stringResource(R.string.home_quick_mode_pronounce),
                note = null,
                onClick = onPronunciation,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ModeTile(
                emoji = "🎓",
                label = stringResource(R.string.home_mode_ielts),
                note = null,
                onClick = onIelts,
                modifier = Modifier.weight(1f)
            )
            val customCount = state.customScenarios.size
            ModeTile(
                emoji = "🎭",
                label = stringResource(R.string.home_mode_roleplay),
                note = if (customCount > 0) pluralStringResource(R.plurals.home_mode_roleplay_custom, customCount, customCount) else null,
                onClick = onOpenRoleplay,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModeTile(emoji: String, label: String, note: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxHeight()
            .heightIn(min = 64.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/** A handful of suggested topics and the way into the full list. */
@Composable
internal fun HomeTopicSuggestionsHeader(totalCount: Int, onOpenAllTopics: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_topics_suggested_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onOpenAllTopics) {
            Text(
                text = stringResource(R.string.home_topics_see_all, totalCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

/** One topic as a full-width row: emoji, names in both languages, lessons done. */
@Composable
internal fun TopicRow(item: TopicProgressUi, isVi: Boolean, onClick: () -> Unit) {
    val topic = item.topic
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(topic.emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isVi) topic.titleVi else topic.titleEn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isVi) topic.titleEn else topic.titleVi,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.completedLessons > 0) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = stringResource(R.string.topic_card_sessions, item.completedLessons),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
