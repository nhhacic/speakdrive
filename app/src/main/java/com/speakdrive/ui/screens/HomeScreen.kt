package com.speakdrive.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.style.TextOverflow
import com.speakdrive.ai.model.CustomScenario
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.Topic
import com.speakdrive.auto.MediaIds
import com.speakdrive.ui.components.StatTile
import com.speakdrive.ui.components.TopicCard
import java.util.Calendar

@Composable
fun HomeScreen(
    onStartLesson: (mediaId: String) -> Unit,
    onOpenCurrentLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        state = state,
        onStartLesson = onStartLesson,
        onOpenCurrentLesson = onOpenCurrentLesson,
        onOpenSettings = onOpenSettings,
        onOpenProgress = onOpenProgress,
        onStartCustomScenario = viewModel::startCustomScenario,
        onDeleteCustomScenario = viewModel::deleteCustomScenario
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onStartLesson: (mediaId: String) -> Unit,
    onOpenCurrentLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    onStartCustomScenario: (CustomScenario) -> Unit = {},
    onDeleteCustomScenario: (String) -> Unit = {}
) {
    var sheetTopic by remember { mutableStateOf<Topic?>(null) }
    var showStorySheet by remember { mutableStateOf(false) }
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> stringResource(R.string.home_greeting_morning_emoji)
        in 12..17 -> stringResource(R.string.home_greeting_afternoon_emoji)
        else -> stringResource(R.string.home_greeting_evening_emoji)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_speakdrive_logo),
                            contentDescription = "SpeakDrive Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "SpeakDrive",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenProgress) {
                        Icon(Icons.Filled.Insights, contentDescription = stringResource(R.string.nav_progress), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Android Auto Connection Status
            if (state.isCarConnected) fullWidth { CarBanner() }

            // Active lesson in progress
            state.currentLesson?.let { lesson ->
                fullWidth {
                    ActiveLessonBanner(
                        title = "${lesson.topic.emoji} ${lesson.getTitle(isVi)}",
                        level = lesson.level.getLabel(isVi),
                        onClick = onOpenCurrentLesson
                    )
                }
            }

            // Daily Progress & Streak Header
            fullWidth {
                DailyGoalSection(
                    greeting = greeting,
                    state = state
                )
            }

            // Hero Quick Practice Card
            fullWidth {
                QuickStartHeroCard(
                    lastTopic = state.lastTopic,
                    isVi = isVi,
                    onResume = { onStartLesson(MediaIds.RESUME) },
                    onRandom = { onStartLesson(MediaIds.RANDOM) }
                )
            }

            // Special Learning Modes (Pronunciation & SRS Review)
            fullWidth {
                SpecialModesRow(
                    lastTopic = state.lastTopic,
                    dueWordCount = state.stats.dueWordCount,
                    dueMistakeCount = state.stats.dueMistakeCount,
                    onPronunciation = { onStartLesson(MediaIds.pronunciation(state.lastTopic?.id)) },
                    onReview = { onStartLesson(MediaIds.REVIEW) },
                    onMistakes = { onStartLesson(MediaIds.MISTAKES) }
                )
            }

            // AI Story Listening Mode
            fullWidth {
                StoryListeningHeroCard(
                    unfinishedStory = state.unfinishedStory,
                    level = state.level,
                    isVi = isVi,
                    onResumeStory = { onStartLesson(MediaIds.STORY_RESUME) },
                    onRecommended = { onStartLesson(MediaIds.STORY_RECOMMENDED) },
                    onRandom = { onStartLesson(MediaIds.STORY_RANDOM) },
                    onExploreStories = { showStorySheet = true }
                )
            }

            // Topic section title
            fullWidth {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_topics_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.home_topics_count, state.topics.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Topic Cards Grid
            items(state.topics, key = { it.topic.id }) { item ->
                TopicCard(
                    emoji = item.topic.emoji,
                    title = if (isVi) item.topic.titleVi else item.topic.titleEn,
                    subtitle = if (isVi) item.topic.titleEn else item.topic.titleVi,
                    completedLessons = item.completedLessons,
                    onClick = { sheetTopic = item.topic }
                )
            }

            // Custom Scenarios Section
            if (state.customScenarios.isNotEmpty()) {
                fullWidth {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.home_custom_scenarios_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${state.customScenarios.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(state.customScenarios, key = { it.id }) { scenario ->
                    CustomScenarioCard(
                        scenario = scenario,
                        isVi = isVi,
                        onPlay = { onStartCustomScenario(scenario) },
                        onDelete = { onDeleteCustomScenario(scenario.id) }
                    )
                }
            }

            // Hands-free tip footer
            fullWidth {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.home_driving_tip),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    sheetTopic?.let { topic ->
        ModalBottomSheet(
            onDismissRequest = { sheetTopic = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TopicSheet(
                topic = topic,
                isVi = isVi,
                onPick = { mediaId ->
                    sheetTopic = null
                    onStartLesson(mediaId)
                }
            )
        }
    }

    if (showStorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showStorySheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            StorySheet(
                storyTopics = state.storyTopics,
                recentStories = state.recentStories,
                unfinishedStory = state.unfinishedStory,
                isVi = isVi,
                onPick = { mediaId ->
                    showStorySheet = false
                    onStartLesson(mediaId)
                }
            )
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun CarBanner() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.home_car_connected_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    stringResource(R.string.home_car_connected_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ActiveLessonBanner(
    title: String,
    level: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Radio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.home_active_lesson_banner),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        level,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun DailyGoalSection(
    greeting: String,
    state: HomeUiState
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // 3 KPI metric tiles
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val freezes = if (state.stats.streakFreezes > 0) "  " + stringResource(R.string.home_streak_freezes, state.stats.streakFreezes) else ""
            StatTile("🔥 ${state.stats.streakDays}$freezes", stringResource(R.string.home_streak_label), Modifier.weight(1f))
            StatTile("${state.stats.minutesToday}", stringResource(R.string.home_minutes_today_label), Modifier.weight(1f))
            StatTile("${state.stats.wordsToday}", stringResource(R.string.home_new_words_label), Modifier.weight(1f))
        }

        // Daily Progress Bar
        val goal = state.dailyGoalMinutes.coerceAtLeast(1)
        val progress = (state.stats.minutesToday.toFloat() / goal).coerceIn(0f, 1f)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.home_daily_goal_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.home_goal_progress_format, state.stats.minutesToday, goal, (progress * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
            }
        }
    }
}

@Composable
private fun QuickStartHeroCard(
    lastTopic: Topic?,
    isVi: Boolean,
    onResume: () -> Unit,
    onRandom: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.home_reflex_card_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                lastTopic?.let {
                    val topicTitle = if (isVi) it.titleVi else it.titleEn
                    stringResource(R.string.home_reflex_resume_prefix, "${it.emoji} $topicTitle")
                } ?: stringResource(R.string.home_reflex_default_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (lastTopic != null) R.string.home_btn_resume else R.string.home_btn_start),
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onRandom,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.home_btn_random))
                }
            }
        }
    }
}

@Composable
private fun SpecialModesRow(
    lastTopic: Topic?,
    dueWordCount: Int,
    dueMistakeCount: Int,
    onPronunciation: () -> Unit,
    onReview: () -> Unit,
    onMistakes: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Pronunciation Shadowing Drill Card
        Card(
            onClick = onPronunciation,
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.home_pronunciation_card_title), fontWeight = FontWeight.SemiBold)
                },
                supportingContent = {
                    Text(stringResource(R.string.home_pronunciation_card_desc))
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                trailingContent = {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }

        // Vocabulary Spaced Repetition Due Card
        if (dueWordCount > 0) {
            Card(
                onClick = onReview,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.home_vocab_review_card_title, dueWordCount), fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(stringResource(R.string.home_vocab_review_card_desc))
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }

        // Mistake review: the learner's own earlier mistakes, due for spaced repetition
        if (dueMistakeCount > 0) {
            Card(
                onClick = onMistakes,
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.home_mistake_review_card_title, dueMistakeCount), fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(stringResource(R.string.home_mistake_review_card_desc))
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Replay,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }
}

@Composable
private fun TopicSheet(
    topic: Topic,
    isVi: Boolean,
    onPick: (mediaId: String) -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(topic.emoji, fontSize = 28.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(if (isVi) topic.titleVi else topic.titleEn, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (isVi) topic.titleEn else topic.titleVi, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { onPick(MediaIds.topic(topic.id)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_topic_sheet_free_talk), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onPick(MediaIds.pronunciation(topic.id)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_topic_sheet_pronunciation))
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onPick(MediaIds.story(topic.id, "${TopicManager.DYNAMIC_PREFIX}story_recommended_${topic.id}")) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.home_topic_sheet_story))
        }

        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.home_topic_sheet_roleplay_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        // Dynamic Scenario Discovery
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable {
                    onPick(MediaIds.scenario("${TopicManager.DYNAMIC_PREFIX}explore_${topic.id}"))
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.home_topic_sheet_ai_scenario_title), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.home_topic_sheet_ai_scenario_desc, if (isVi) topic.titleVi else topic.titleEn)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
            )
        }

        // Static Scenarios
        topic.scenarios.forEach { scenario ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onPick(MediaIds.scenario(scenario.id)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(if (isVi) scenario.titleVi else scenario.titleEn, fontWeight = FontWeight.Medium) },
                    supportingContent = {
                        Column {
                            Text(stringResource(R.string.home_topic_sheet_role_ai, scenario.aiRole))
                            scenario.missionObjective?.let { mission ->
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🎯 $mission",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun StoryListeningHeroCard(
    unfinishedStory: CompletedSession? = null,
    level: DifficultyLevel = DifficultyLevel.INTERMEDIATE,
    isVi: Boolean = true,
    onResumeStory: () -> Unit = {},
    onRecommended: () -> Unit,
    onRandom: () -> Unit,
    onExploreStories: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎧", fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.home_story_hero_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        val levelBadge = if (isVi) "${level.labelVi} (${level.cefr})" else "${level.displayName} (${level.cefr})"
                        Text(
                            "${stringResource(R.string.home_story_hero_badge)} • $levelBadge",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
                OutlinedButton(
                    onClick = onExploreStories,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(stringResource(R.string.home_story_btn_all), style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                stringResource(R.string.home_story_hero_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
            )

            if (unfinishedStory != null) {
                Surface(
                    onClick = onResumeStory,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.home_story_continue_title),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                stringResource(R.string.home_story_continue_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onRecommended,
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    )
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_story_btn_recommended), fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onRandom,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.home_story_btn_random))
                }
            }
        }
    }
}

@Composable
private fun StorySheet(
    storyTopics: List<Topic>,
    recentStories: List<CompletedSession>,
    unfinishedStory: CompletedSession? = null,
    isVi: Boolean,
    onPick: (mediaId: String) -> Unit
) {
    var customTopicInput by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .padding(bottom = 36.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text("🎧", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.home_story_sheet_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.home_story_sheet_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (unfinishedStory != null) {
            Card(
                onClick = { onPick(MediaIds.STORY_RESUME) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
                    headlineContent = { Text(stringResource(R.string.home_story_sheet_resume_title), fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(stringResource(R.string.home_story_sheet_resume_desc)) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }

        // Quick Pick
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onPick(MediaIds.STORY_RECOMMENDED) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_story_sheet_ai_pick), style = MaterialTheme.typography.labelMedium)
            }

            OutlinedButton(
                onClick = { onPick(MediaIds.STORY_RANDOM) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.home_story_sheet_random), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Custom Topic Input
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_story_sheet_custom_title), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customTopicInput,
                        onValueChange = { customTopicInput = it },
                        placeholder = { Text(stringResource(R.string.home_story_sheet_custom_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val slug = TopicManager.slugify(customTopicInput.trim())
                            if (slug.isNotBlank()) {
                                onPick(MediaIds.story("story_famous", "${TopicManager.DYNAMIC_PREFIX}story_custom_$slug"))
                            }
                        },
                        enabled = customTopicInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.home_story_sheet_custom_btn))
                    }
                }
            }
        }

        // Recent Stories Section
        if (recentStories.isNotEmpty()) {
            Text(stringResource(R.string.home_story_sheet_recent_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            recentStories.forEach { session ->
                val topic = storyTopics.find { it.id == session.topicId }
                val topicTitle = if (isVi) (topic?.titleVi ?: session.topicId) else (topic?.titleEn ?: session.topicId)
                val scenarioTitle = session.scenarioId?.let { sId ->
                    topic?.scenarios?.firstOrNull { it.id == sId }?.let { if (isVi) it.titleVi else it.titleEn }
                } ?: topicTitle
                val statusText = if (session.isCompleted) stringResource(R.string.home_story_sheet_completed) else stringResource(R.string.home_story_sheet_in_progress)
                val targetScenarioId = session.scenarioId ?: "${TopicManager.DYNAMIC_PREFIX}story_recommended_${session.topicId}"
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(MediaIds.story(session.topicId, targetScenarioId)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    ListItem(
                        headlineContent = { Text(scenarioTitle, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("$topicTitle • $statusText") },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }

        // Story Topics & Scenarios
        Text(stringResource(R.string.home_story_sheet_explore_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        storyTopics.forEach { topic ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(topic.emoji, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(if (isVi) topic.titleVi else topic.titleEn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (isVi) topic.titleEn else topic.titleVi, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Dynamic topic recommendation
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(MediaIds.story(topic.id, "${TopicManager.DYNAMIC_PREFIX}story_recommended_${topic.id}"))
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        val topicTitle = if (isVi) topic.titleVi else topic.titleEn
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.home_story_sheet_generate_new, topicTitle), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall) },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }

                    topic.scenarios.forEach { scenario ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(MediaIds.story(topic.id, scenario.id)) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            ListItem(
                                headlineContent = { Text(if (isVi) scenario.titleVi else scenario.titleEn, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall) },
                                supportingContent = { Text(if (isVi) scenario.titleEn else scenario.titleVi, style = MaterialTheme.typography.labelSmall) },
                                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomScenarioCard(
    scenario: CustomScenario,
    isVi: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🎯", fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isVi) scenario.titleVi else scenario.titleEn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "AI: ${scenario.aiRole} • You: ${scenario.learnerRole}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.memory_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

