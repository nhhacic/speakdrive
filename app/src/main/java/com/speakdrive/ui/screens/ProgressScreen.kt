package com.speakdrive.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.data.repository.DayPractice
import com.speakdrive.ui.components.StatTile
import com.speakdrive.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onBack: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onOpenVocabulary: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressContent(
        state = state,
        isVi = isVi,
        onBack = onBack,
        onOpenSession = onOpenSession,
        onOpenVocabulary = onOpenVocabulary,
        onApplyRecommendedLevel = viewModel::applyRecommendedLevel
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressContent(
    state: ProgressUiState,
    isVi: Boolean,
    onBack: () -> Unit,
    onOpenSession: (sessionId: String) -> Unit,
    onOpenVocabulary: () -> Unit,
    onApplyRecommendedLevel: (DifficultyLevel) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.progress_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // KPI Stat Tiles
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("🔥 ${state.stats.streakDays}", stringResource(R.string.progress_streak_label), Modifier.weight(1f))
                    StatTile("${state.stats.totalMinutes}", stringResource(R.string.progress_total_time_label), Modifier.weight(1f))
                    StatTile("${state.stats.completedSessions}", stringResource(R.string.progress_sessions_learned_label), Modifier.weight(1f))
                }
            }

            // CEFR Roadmap & Adaptive Recommendation Card
            item {
                CefrRoadmapCard(
                    currentLevel = state.currentLevel,
                    recommendation = state.roadmapRecommendation,
                    isAdaptiveEnabled = state.isAdaptiveEnabled,
                    isVi = isVi,
                    onApplyLevel = onApplyRecommendedLevel
                )
            }

            // 7-day Practice Bar Chart
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(20.dp)
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
                            Text(
                                stringResource(R.string.progress_chart_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val totalWeekMinutes = state.stats.lastSevenDays.sumOf { it.minutes }
                            Text(
                                stringResource(R.string.progress_total_week_format, totalWeekMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        WeekChart(
                            days = state.stats.lastSevenDays,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .padding(top = 8.dp)
                        )
                    }
                }
            }

            // Weekly Fluency Trends Card
            if (state.weeklyFluency.hasData) {
                item {
                    WeeklyFluencyTrendsCard(
                        fluency = state.weeklyFluency,
                        isVi = isVi
                    )
                }
            }

            // Vocabulary Shortcut Card
            item {
                Card(
                    onClick = onOpenVocabulary,
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
                            Text(stringResource(R.string.progress_vocab_card_title), fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text(
                                if (state.stats.dueWordCount > 0)
                                    stringResource(R.string.progress_vocab_card_due, state.stats.dueWordCount)
                                else
                                    stringResource(R.string.progress_vocab_card_all)
                            )
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
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            // Topics Breakdown Section
            item {
                Text(
                    stringResource(R.string.progress_topic_breakdown_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item {
                Card(
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
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        state.topicRows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${row.emoji} ${if (isVi) row.titleVi else row.titleEn}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.progress_session_count_format, row.count),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Session History Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.progress_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.progress_history_count, state.history.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.progress_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            items(state.history, key = { it.sessionId }) { item ->
                val displayTitle = when {
                    item.mode == SessionMode.VOCAB_REVIEW.name -> stringResource(R.string.progress_session_vocab_review)
                    item.mode == SessionMode.REPEAT_AFTER_ME.name ->
                        stringResource(R.string.progress_session_pronunciation, if (isVi) item.topicTitleVi else item.topicTitleEn)
                    item.scenarioTitleVi != null -> "🎭 ${if (isVi) item.scenarioTitleVi else (item.scenarioTitleEn ?: item.scenarioTitleVi)}"
                    else -> "${item.topicEmoji} ${if (isVi) item.topicTitleVi else item.topicTitleEn}"
                }
                val minutes = (item.activeDurationMs / 60_000).coerceAtLeast(if (item.activeDurationMs > 0) 1 else 0)
                val dateStr = DateTimeFormatter.ofPattern("dd/MM HH:mm").format(Instant.ofEpochMilli(item.startedAt).atZone(ZoneId.systemDefault()))
                val minUnit = stringResource(R.string.settings_minutes)
                val levelStr = item.level.getLabel(isVi)
                val subtitle = "$dateStr • $minutes $minUnit • $levelStr"

                Card(
                    onClick = { onOpenSession(item.sessionId) },
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
                    ListItem(
                        headlineContent = {
                            Text(displayTitle, fontWeight = FontWeight.Bold)
                        },
                        supportingContent = {
                            val incompleteLabel = stringResource(R.string.progress_session_incomplete)
                            Text(
                                if (item.isCompleted) subtitle else "$subtitle • $incompleteLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            item.averageScore?.let { score ->
                                val scoreColor = when {
                                    score >= 8 -> AppColors.CorrectGreen
                                    score >= 6 -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(scoreColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "$score/10",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = scoreColor
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

/** Thẻ hiển thị thang 6 bậc CEFR và gợi ý lộ trình nâng/hạ cấp độ */
@Composable
private fun CefrRoadmapCard(
    currentLevel: DifficultyLevel,
    recommendation: LevelRecommendation?,
    isAdaptiveEnabled: Boolean,
    isVi: Boolean,
    onApplyLevel: (DifficultyLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    stringResource(R.string.progress_roadmap_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.progress_current_level_label, currentLevel.getLabel(isVi), currentLevel.cefr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // CEFR 6-step progress bar
            val allLevels = DifficultyLevel.entries
            val currentIndex = allLevels.indexOf(currentLevel)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                allLevels.forEachIndexed { index, lvl ->
                    val isCurrent = index == currentIndex
                    val isPast = index < currentIndex

                    val badgeBg = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        isPast -> AppColors.CorrectGreen.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val badgeTextColor = when {
                        isCurrent -> MaterialTheme.colorScheme.onPrimary
                        isPast -> AppColors.CorrectGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 36.dp else 28.dp)
                                .clip(CircleShape)
                                .background(badgeBg)
                                .then(
                                    if (isCurrent) Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lvl.cefr.split("-").first(),
                                style = if (isCurrent) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = badgeTextColor
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            lvl.displayName.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            maxLines = 1,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            if (isAdaptiveEnabled && recommendation != null) {
                val isUp = recommendation.direction == LevelAdjustmentDirection.LEVEL_UP
                val isDown = recommendation.direction == LevelAdjustmentDirection.LEVEL_DOWN
                val badgeColor = when {
                    isUp -> AppColors.CorrectGreen
                    isDown -> Color(0xFFE65100)
                    else -> MaterialTheme.colorScheme.secondary
                }
                val badgeTitle = when {
                    isUp -> stringResource(R.string.summary_level_up_badge)
                    isDown -> stringResource(R.string.summary_level_down_badge)
                    else -> stringResource(R.string.summary_level_keep_badge)
                }

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isUp) "🚀 $badgeTitle" else if (isDown) "🛡️ $badgeTitle" else "🎯 $badgeTitle",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Text(
                            text = if (isVi) recommendation.reasonVi else recommendation.reasonEn,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isUp || isDown) {
                            Button(
                                onClick = { onApplyLevel(recommendation.targetLevel) },
                                modifier = Modifier.align(Alignment.End),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (isUp) stringResource(R.string.summary_btn_accept_level_up, recommendation.targetLevel.cefr)
                                    else stringResource(R.string.summary_btn_accept_level_down, recommendation.targetLevel.cefr),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Biểu đồ cột 7 ngày tối giản với hiệu ứng gradient và đỉnh bo tròn */
@Composable
private fun WeekChart(days: List<DayPractice>, modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryLight = MaterialTheme.colorScheme.primaryContainer
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val maxMinutes = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(10)
    val today = remember { LocalDate.now() }
    val minutesUnit = stringResource(R.string.settings_minutes)
    val description = days.joinToString { "${it.date.dayOfMonth}/${it.date.monthValue}: ${it.minutes} $minutesUnit" }

    Column(modifier.semantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (days.isEmpty()) return@Canvas
            val slot = size.width / days.size
            val barWidth = slot * 0.48f

            days.forEachIndexed { index, day ->
                val fraction = day.minutes.toFloat() / maxMinutes
                val barHeight = (size.height * fraction).coerceAtLeast(6f)
                val left = index * slot + (slot - barWidth) / 2
                val top = size.height - barHeight

                val isToday = day.date == today
                val barBrush = if (day.minutes > 0) {
                    Brush.verticalGradient(
                        colors = listOf(primaryColor, primaryLight),
                        startY = top,
                        endY = size.height
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(emptyColor, emptyColor)
                    )
                }

                drawRoundRect(
                    brush = barBrush,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // Marker dot for today
                if (isToday) {
                    drawCircle(
                        color = primaryColor,
                        radius = 3.dp.toPx(),
                        center = Offset(left + barWidth / 2, size.height + 6.dp.toPx())
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val isToday = day.date == today
                Text(
                    text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (isToday) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WeeklyFluencyTrendsCard(
    fluency: WeeklyFluencySummary,
    isVi: Boolean
) {
    if (!fluency.hasData) return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.progress_fluency_trends_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // WPM
                fluency.currentWeekWpm?.let { wpm ->
                    val diff = fluency.previousWeekWpm?.let { wpm - it }
                    val diffText = when {
                        diff != null && diff > 0 -> "+$diff"
                        diff != null && diff < 0 -> "$diff"
                        else -> null
                    }
                    FluencyMetricBox(
                        title = stringResource(R.string.progress_fluency_wpm_label),
                        value = "$wpm",
                        unit = "WPM",
                        trend = diffText,
                        trendPositive = (diff ?: 0) >= 0,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Filler Ratio
                fluency.currentWeekFillerRatio?.let { filler ->
                    val percent = (filler * 100).toInt()
                    FluencyMetricBox(
                        title = stringResource(R.string.progress_fluency_filler_label),
                        value = "$percent%",
                        unit = if (isVi) "từ đệm" else "fillers",
                        trend = null,
                        trendPositive = percent <= 5,
                        modifier = Modifier.weight(1f)
                    )
                }

                // MLU (Mean Length of Utterance)
                fluency.currentWeekMlu?.let { mlu ->
                    FluencyMetricBox(
                        title = stringResource(R.string.progress_fluency_mlu_label),
                        value = "%.1f".format(mlu),
                        unit = if (isVi) "từ/câu" else "w/sent",
                        trend = null,
                        trendPositive = mlu >= 6f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FluencyMetricBox(
    title: String,
    value: String,
    unit: String,
    trend: String?,
    trendPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            if (trend != null) {
                Text(
                    trend,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (trendPositive) AppColors.CorrectGreen else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
