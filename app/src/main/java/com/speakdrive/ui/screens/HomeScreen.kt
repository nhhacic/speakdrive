package com.speakdrive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.CompletedSession
import com.speakdrive.ai.model.CustomScenario
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.Topic
import com.speakdrive.auto.MediaIds
import com.speakdrive.ui.components.TopicCard
import com.speakdrive.ui.theme.AppColors
import java.util.Calendar

private enum class TopicCategoryTab(val labelRes: Int) {
    ALL(R.string.home_filter_all),
    DAILY(R.string.home_filter_daily),
    WORK(R.string.home_filter_work),
    TRAVEL(R.string.home_filter_travel)
}

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
    var selectedCategory by rememberSaveable { mutableStateOf(TopicCategoryTab.ALL) }
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> stringResource(R.string.home_greeting_morning_emoji)
        in 12..17 -> stringResource(R.string.home_greeting_afternoon_emoji)
        else -> stringResource(R.string.home_greeting_evening_emoji)
    }

    val filteredTopics = remember(state.topics, selectedCategory) {
        when (selectedCategory) {
            TopicCategoryTab.ALL -> state.topics
            TopicCategoryTab.DAILY -> state.topics.filter {
                it.topic.id in setOf("daily", "social_dating", "food", "shopping", "entertainment")
            }
            TopicCategoryTab.WORK -> state.topics.filter {
                it.topic.id in setOf(
                    "work", "interview", "startup_tech", "tech_it",
                    "medical_expert", "civil_engineering", "transport_engineering"
                )
            }
            TopicCategoryTab.TRAVEL -> state.topics.filter {
                it.topic.id in setOf("travel", "driving_emergency", "health")
            }
        }
    }

    Scaffold(
        topBar = {
            HomeTopBar(
                isCarConnected = state.isCarConnected,
                onOpenProgress = onOpenProgress,
                onOpenSettings = onOpenSettings
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Chào hỏi cá nhân hóa & sẵn sàng rảnh tay
            item(key = "greeting_header") {
                HomeGreetingHeader(greeting = greeting)
            }

            // 2. HERO ONE-TAP CARD (Tâm điểm thị giác chính của màn hình)
            item(key = "hero_action_card") {
                HeroActionCard(
                    state = state,
                    isVi = isVi,
                    onResumeLesson = {
                        if (state.currentLesson != null) {
                            onOpenCurrentLesson()
                        } else {
                            onStartLesson(MediaIds.RESUME)
                        }
                    },
                    onRandomLesson = { onStartLesson(MediaIds.RANDOM) }
                )
            }

            // 3. BENTO DASHBOARD: Tiến độ hôm nay & Hàng đợi ôn tập song song
            item(key = "bento_dashboard") {
                BentoDashboardSection(
                    state = state,
                    isVi = isVi,
                    onOpenProgress = onOpenProgress,
                    onStartReview = { onStartLesson(MediaIds.REVIEW) },
                    onStartMistakes = { onStartLesson(MediaIds.MISTAKES) },
                    onStartPronunciation = { onStartLesson(MediaIds.pronunciation(state.lastTopic?.id)) }
                )
            }

            // 4. CHẾ ĐỘ HỌC NHANH (Quick Practice Carousel)
            item(key = "quick_modes_row") {
                QuickModesSection(
                    state = state,
                    onOpenStories = {
                        if (state.unfinishedStory != null) {
                            onStartLesson(MediaIds.STORY_RESUME)
                        } else {
                            showStorySheet = true
                        }
                    },
                    onOpenPronunciation = { onStartLesson(MediaIds.pronunciation(state.lastTopic?.id)) },
                    onOpenRandom = { onStartLesson(MediaIds.RANDOM) }
                )
            }

            // 5. CHỦ ĐỀ LUYỆN NÓI: Header & Bộ lọc Tabs
            item(key = "topics_header") {
                TopicSectionHeader(
                    totalCount = filteredTopics.size,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
            }

            // 6. DANH SÁCH CHỦ ĐỀ (Lưới 2 cột cân đối, hiển thị 2 dòng không cắt chữ)
            filteredTopics.chunked(2).forEachIndexed { index, rowItems ->
                item(key = "topics_row_$index") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { item ->
                            TopicCard(
                                emoji = item.topic.emoji,
                                title = if (isVi) item.topic.titleVi else item.topic.titleEn,
                                subtitle = if (isVi) item.topic.titleEn else item.topic.titleVi,
                                completedLessons = item.completedLessons,
                                onClick = { sheetTopic = item.topic },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 7. KỊCH BẢN TÙY CHỈNH (Nếu người dùng đã tạo)
            if (state.customScenarios.isNotEmpty()) {
                item(key = "custom_scenarios_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.home_custom_scenarios_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${state.customScenarios.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                items(state.customScenarios, key = { "custom_${it.id}" }) { scenario ->
                    CustomScenarioCard(
                        scenario = scenario,
                        isVi = isVi,
                        onPlay = { onStartCustomScenario(scenario) },
                        onDelete = { onDeleteCustomScenario(scenario.id) }
                    )
                }
            }

            // 8. Mẹo Lái Xe Rảnh Tay
            item(key = "driving_tip_footer") {
                DrivingTipFooter()
            }
        }
    }

    // Sheet xem chi tiết và kịch bản của Topic
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

    // Sheet khám phá câu chuyện AI
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

/** TopBar tối giản kèm trạng thái xe và nút truy cập nhanh Cài đặt, Tiến trình */
@Composable
private fun HomeTopBar(
    isCarConnected: Boolean,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_speakdrive_logo),
                contentDescription = "SpeakDrive Logo",
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "SpeakDrive",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Voice-First English",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isCarConnected) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.tertiary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            IconButton(onClick = onOpenProgress) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = stringResource(R.string.nav_progress),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Chào hỏi thân thiện theo buổi */
@Composable
private fun HomeGreetingHeader(greeting: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.home_ready_drive),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** HERO CARD: 1-Tap & Drive Action Card */
@Composable
private fun HeroActionCard(
    state: HomeUiState,
    isVi: Boolean,
    onResumeLesson: () -> Unit,
    onRandomLesson: () -> Unit
) {
    val isLessonActive = state.currentLesson != null
    val lastTopic = state.lastTopic

    val heroGradient = if (isLessonActive) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
            )
        )
    } else {
        AppColors.PrimaryGradient
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(heroGradient)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Header Tags Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLessonActive) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.home_hero_tag_active),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "🌟 ${stringResource(R.string.home_hero_tag_interactive)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Level Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "${state.level.cefr} • ${if (isVi) state.level.labelVi else state.level.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Tiêu đề bài học
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val topicTitle = if (isLessonActive && state.currentLesson != null) {
                        "${state.currentLesson.topic.emoji} ${state.currentLesson.getTitle(isVi)}"
                    } else if (lastTopic != null) {
                        val topicName = if (isVi) lastTopic.titleVi else lastTopic.titleEn
                        stringResource(R.string.home_reflex_resume_prefix, "${lastTopic.emoji} $topicName")
                    } else {
                        stringResource(R.string.home_reflex_card_title)
                    }

                    Text(
                        text = topicTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = if (isLessonActive && state.currentLesson != null) {
                            state.currentLesson.level.getLabel(isVi)
                        } else {
                            lastTopic?.description ?: stringResource(R.string.home_reflex_default_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // NÚT CTA CHÍNH (Cao 54dp, to rõ, dễ bấm 1 chạm)
                Button(
                    onClick = onResumeLesson,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = if (isLessonActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
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
                            if (isLessonActive) R.string.home_hero_active_button
                            else if (lastTopic != null) R.string.home_btn_resume
                            else R.string.home_btn_start
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }

                // Gợi ý khẩu lệnh giọng nói & nút ngẫu nhiên
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.home_voice_prompt_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    if (!isLessonActive) {
                        Surface(
                            onClick = onRandomLesson,
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Shuffle,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.home_hero_random_btn),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** BENTO DASHBOARD: Gom Streak, Mục tiêu ngày và Hàng đợi ôn tập vào 2 cột đối xứng */
@Composable
private fun BentoDashboardSection(
    state: HomeUiState,
    isVi: Boolean,
    onOpenProgress: () -> Unit,
    onStartReview: () -> Unit,
    onStartMistakes: () -> Unit,
    onStartPronunciation: () -> Unit
) {
    val goal = state.dailyGoalMinutes.coerceAtLeast(1)
    val progress = (state.stats.minutesToday.toFloat() / goal).coerceIn(0f, 1f)
    val progressPercent = (progress * 100).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cột trái: Streak & Mục tiêu ngày
        val freezes = if (state.stats.streakFreezes > 0) "  " + stringResource(R.string.home_streak_freezes, state.stats.streakFreezes) else ""
        val streakDesc = "🔥 ${state.stats.streakDays}$freezes ${stringResource(R.string.home_streak_label)}"
        Card(
            onClick = onOpenProgress,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics {
                    contentDescription = streakDesc
                }
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔥", fontSize = 22.sp)
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${state.stats.streakDays} ${stringResource(R.string.home_streak_label)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.home_goal_progress_format, state.stats.minutesToday, goal, progressPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                Text(
                    text = "+${state.stats.wordsToday} ${stringResource(R.string.home_new_words_label)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Cột phải: Hàng đợi ôn tập SRS & Sửa lỗi
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 22.sp)
                    Text(
                        text = stringResource(R.string.home_bento_srs_title),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val dueWordCount = state.stats.dueWordCount
                val dueMistakeCount = state.stats.dueMistakeCount

                if (dueWordCount > 0) {
                    Surface(
                        onClick = onStartReview,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "📚 " + stringResource(R.string.home_vocab_review_card_title, dueWordCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (dueMistakeCount > 0) {
                    Surface(
                        onClick = onStartMistakes,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🔁 " + stringResource(R.string.home_bento_srs_mistakes, dueMistakeCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (dueWordCount == 0 && dueMistakeCount == 0) {
                    Text(
                        text = stringResource(R.string.home_bento_srs_all_good),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        onClick = onStartPronunciation,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🗣️ " + stringResource(R.string.home_bento_srs_shadowing),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

/** CHẾ ĐỘ HỌC NHANH (Carousel nằm ngang mềm mại) */
@Composable
private fun QuickModesSection(
    state: HomeUiState,
    onOpenStories: () -> Unit,
    onOpenPronunciation: () -> Unit,
    onOpenRandom: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.home_quick_modes_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Luyện nghe AI Story
            val hasUnfinished = state.unfinishedStory != null
            QuickModeCard(
                icon = "🎧",
                title = stringResource(R.string.home_quick_mode_listen),
                badge = if (hasUnfinished) "▶ " + stringResource(R.string.home_story_sheet_in_progress) else "AI Podcast",
                badgeColor = if (hasUnfinished) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                badgeTextColor = if (hasUnfinished) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onOpenStories
            )

            // 2. Luyện phát âm Phoneme Drill
            QuickModeCard(
                icon = "🗣️",
                title = stringResource(R.string.home_quick_mode_pronounce),
                badge = "Shadowing",
                badgeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                badgeTextColor = MaterialTheme.colorScheme.primary,
                onClick = onOpenPronunciation
            )

            // 3. Ngẫu nhiên tình huống
            QuickModeCard(
                icon = "🎲",
                title = stringResource(R.string.home_quick_mode_random),
                badge = "Surprise",
                badgeColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                badgeTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenRandom
            )
        }
    }
}

@Composable
private fun QuickModeCard(
    icon: String,
    title: String,
    badge: String,
    badgeColor: Color,
    badgeTextColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(136.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 24.sp)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeTextColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Header phân loại chủ đề kèm Filter Chips */
@Composable
private fun TopicSectionHeader(
    totalCount: Int,
    selectedCategory: TopicCategoryTab,
    onSelectCategory: (TopicCategoryTab) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_topics_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.home_topics_count, totalCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TopicCategoryTab.values().forEach { tab ->
                val isSelected = tab == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(tab) },
                    label = {
                        Text(
                            text = stringResource(tab.labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

/** Driving Tip Footer */
@Composable
private fun DrivingTipFooter() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💡", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.home_driving_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            .verticalScroll(rememberScrollState())
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
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
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
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    ListItem(
                        headlineContent = { Text(scenarioTitle, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("$topicTitle • $statusText") },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
