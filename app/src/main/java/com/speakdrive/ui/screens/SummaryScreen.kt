package com.speakdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.LevelAdjustmentDirection
import com.speakdrive.ai.model.LevelRecommendation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.speakdrive.R
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.pronunciation.PronunciationDrill
import com.speakdrive.data.local.entity.PronunciationAttemptEntity
import com.speakdrive.ui.components.ChatBubble
import com.speakdrive.ui.theme.AppColors

@Composable
fun SummaryScreen(
    onPracticeAgain: (mediaId: String) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SummaryContent(
        state = state,
        onPracticeAgain = onPracticeAgain,
        onHome = onHome,
        onBack = onBack,
        onApplyRecommendation = viewModel::applyRecommendation
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryContent(
    state: SummaryUiState,
    onPracticeAgain: (mediaId: String) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onApplyRecommendation: (DifficultyLevel) -> Unit = {}
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"
    var showTranscript by rememberSaveable { mutableStateOf(false) }

    val displayTitle = when {
        state.mode == SessionMode.VOCAB_REVIEW -> stringResource(R.string.summary_title_vocab_review)
        state.mode == SessionMode.REPEAT_AFTER_ME -> stringResource(
            R.string.summary_title_pronunciation,
            (if (isVi) state.topicTitleVi else state.topicTitleEn).orEmpty()
        )
        state.scenarioTitleVi != null -> stringResource(
            R.string.summary_title_roleplay,
            if (isVi) state.scenarioTitleVi else (state.scenarioTitleEn ?: state.scenarioTitleVi).orEmpty()
        )
        state.topicTitleVi != null -> "${state.topicEmoji.orEmpty()} ${if (isVi) state.topicTitleVi else (state.topicTitleEn ?: state.topicTitleVi).orEmpty()}".trim()
        else -> stringResource(R.string.summary_title_default)
    }

    val displayLevel = state.level?.getLabel(isVi).orEmpty()

    val minutes = state.durationMs / 60_000
    val seconds = (state.durationMs / 1000) % 60
    val displayDuration = if (minutes > 0) {
        stringResource(R.string.summary_duration_min_sec, minutes, seconds)
    } else {
        stringResource(R.string.summary_duration_sec, seconds)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.summary_screen_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        if (state.isLoading || detail == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (state.isLoading) CircularProgressIndicator()
                else Text(stringResource(R.string.summary_not_found))
            }
            return@Scaffold
        }

        val session = detail.session

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Title Card
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                stringResource(R.string.summary_hero_completed),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Text(
                            displayTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (displayLevel.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        displayLevel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (displayDuration.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        displayDuration,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Summarizing indicator or evaluation metrics
            if (state.isSummarizing) {
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
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.summary_analyzing),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                item {
                    ScoreSection(
                        fluency = session.fluencyScore,
                        grammar = session.grammarScore,
                        vocabulary = session.vocabularyScore
                    )
                }

                // Adaptive Level Recommendation
                if (!state.isSummarizing && state.isAdaptiveEnabled && state.levelRecommendation != null && state.level != null) {
                    item {
                        LevelRecommendationCard(
                            currentLevel = state.level,
                            recommendation = state.levelRecommendation,
                            isApplied = state.isLevelApplied,
                            appliedLevel = state.appliedLevel,
                            isVi = isVi,
                            onApply = onApplyRecommendation
                        )
                    }
                }

                // AI Encouragement Quote
                session.encouragement?.takeIf { it.isNotBlank() }?.let { text ->
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
                            Row(
                                modifier = Modifier.padding(18.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.AiViolet.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = AppColors.AiViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(R.string.summary_feedback_title),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.AiViolet
                                    )
                                    Text(
                                        "“$text”",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pronunciation breakdown
            if (detail.attempts.isNotEmpty()) {
                val sentences = detail.attempts.groupBy { PronunciationDrill.key(it.target) }.values.toList()
                val passedCount = sentences.count { tries -> tries.any { it.passed } }
                val scoreSuffix = session.pronunciationScore?.let { " • $it%" } ?: ""
                item {
                    SectionHeader(
                        stringResource(R.string.summary_pronunciation_section, passedCount, sentences.size, scoreSuffix)
                    )
                }
                items(sentences, key = { "p${it.first().id}" }) { tries ->
                    DrillSentenceCard(tries)
                }
            }

            // Learned Words
            if (detail.words.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.summary_learned_words_section, detail.words.size)) }
                items(detail.words, key = { "w${it.id}" }) { word ->
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
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                word.word,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (word.meaning.isNotBlank()) {
                                Text(word.meaning, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (word.exampleSentence.isNotBlank()) {
                                Text(
                                    "“${word.exampleSentence}”",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Grammar Corrections
            if (detail.corrections.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.summary_grammar_section)) }
                items(detail.corrections, key = { "c${it.id}" }) { correction ->
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
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                correction.original,
                                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                                color = AppColors.ErrorRed
                            )
                            Text(
                                "➔ ${correction.corrected}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.CorrectGreen
                            )
                            if (correction.explanation.isNotBlank()) {
                                Text(
                                    correction.explanation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Next suggestion
            session.nextSuggestion?.takeIf { it.isNotBlank() && !state.isSummarizing }?.let { suggestion ->
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.summary_next_suggestion_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    suggestion,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Actions: Practice Again & Back to Home
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onPracticeAgain(state.againMediaId) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.summary_btn_continue_topic), fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onHome,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(stringResource(R.string.summary_btn_home))
                    }
                }
            }

            // Transcript Toggle
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = { showTranscript = !showTranscript },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showTranscript) stringResource(R.string.summary_transcript_hide)
                        else stringResource(R.string.summary_transcript_view, detail.messages.size),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showTranscript) {
                items(detail.messages, key = { "m${it.id}" }) { message ->
                    ChatBubble(text = message.text, isUser = message.speaker == "USER")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/** Đánh giá 3 kỹ năng cốt lõi: Trôi chảy, Ngữ pháp, Từ vựng */
@Composable
private fun ScoreSection(fluency: Int?, grammar: Int?, vocabulary: Int?) {
    if (fluency == null && grammar == null && vocabulary == null) return
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
            Text(
                stringResource(R.string.summary_skills_evaluation),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            ScoreMetricBar(stringResource(R.string.summary_fluency_label), fluency)
            ScoreMetricBar(stringResource(R.string.summary_grammar_label), grammar)
            ScoreMetricBar(stringResource(R.string.summary_vocabulary_label), vocabulary)
        }
    }
}

@Composable
private fun ScoreMetricBar(label: String, score: Int?) {
    val progress = ((score ?: 0) / 10f).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                score?.let { "$it/10" } ?: "—",
                style = MaterialTheme.typography.titleSmall,
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

@Composable
private fun DrillSentenceCard(tries: List<PronunciationAttemptEntity>) {
    val passed = tries.any { it.passed }
    val last = tries.last()
    val problems = tries.flatMap { it.problemWords.split("|") }.map { it.trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }

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
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (if (passed) "✓ " else "✗ ") + last.target,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (passed) AppColors.CorrectGreen else AppColors.ErrorRed,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (passed) AppColors.CorrectGreen.copy(alpha = 0.15f)
                            else AppColors.ErrorRed.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (passed) stringResource(R.string.summary_drill_passed_at, tries.first { it.passed }.attemptNumber)
                        else stringResource(R.string.summary_drill_failed),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (passed) AppColors.CorrectGreen else AppColors.ErrorRed
                    )
                }
            }

            Text(
                stringResource(R.string.summary_drill_ai_heard, last.heard),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            tries.mapNotNull { it.azurePronScore }.maxOrNull()?.let { best ->
                Text(
                    stringResource(R.string.summary_drill_azure_score, best),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (problems.isNotEmpty()) {
                Text(
                    stringResource(R.string.summary_drill_problem_words, problems.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.ErrorRed,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun LevelRecommendationCard(
    currentLevel: DifficultyLevel,
    recommendation: LevelRecommendation,
    isApplied: Boolean,
    appliedLevel: DifficultyLevel?,
    isVi: Boolean,
    onApply: (DifficultyLevel) -> Unit
) {
    val direction = recommendation.direction
    val targetLevel = recommendation.targetLevel

    val containerColor = when {
        isApplied -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        direction == LevelAdjustmentDirection.LEVEL_UP -> Color(0xFFE8F5E9)
        direction == LevelAdjustmentDirection.LEVEL_DOWN -> Color(0xFFFFF3E0)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isApplied -> AppColors.CorrectGreen
        direction == LevelAdjustmentDirection.LEVEL_UP -> AppColors.CorrectGreen.copy(alpha = 0.6f)
        direction == LevelAdjustmentDirection.LEVEL_DOWN -> Color(0xFFFF9800).copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.5.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeIcon = when (direction) {
                        LevelAdjustmentDirection.LEVEL_UP -> Icons.AutoMirrored.Filled.TrendingUp
                        LevelAdjustmentDirection.LEVEL_DOWN -> Icons.AutoMirrored.Filled.TrendingFlat
                        LevelAdjustmentDirection.KEEP -> Icons.Filled.CheckCircle
                    }
                    val badgeColor = when (direction) {
                        LevelAdjustmentDirection.LEVEL_UP -> AppColors.CorrectGreen
                        LevelAdjustmentDirection.LEVEL_DOWN -> Color(0xFFE65100)
                        LevelAdjustmentDirection.KEEP -> MaterialTheme.colorScheme.primary
                    }
                    Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                    Text(
                        when (direction) {
                            LevelAdjustmentDirection.LEVEL_UP -> stringResource(R.string.summary_level_up_badge)
                            LevelAdjustmentDirection.LEVEL_DOWN -> stringResource(R.string.summary_level_down_badge)
                            LevelAdjustmentDirection.KEEP -> stringResource(R.string.summary_level_keep_badge)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }

                if (direction != LevelAdjustmentDirection.KEEP) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "${currentLevel.cefr} ➔ ${targetLevel.cefr}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val reasonText = if (isVi) {
                recommendation.reasonVi.takeIf { it.isNotBlank() } ?: recommendation.reasonEn
            } else {
                recommendation.reasonEn.takeIf { it.isNotBlank() } ?: recommendation.reasonVi
            }
            Text(
                reasonText,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp
            )

            if (isApplied) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = AppColors.CorrectGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.summary_level_applied_success, appliedLevel?.getLabel(isVi) ?: targetLevel.getLabel(isVi)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.CorrectGreen
                    )
                }
            } else if (direction != LevelAdjustmentDirection.KEEP) {
                Button(
                    onClick = { onApply(targetLevel) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (direction == LevelAdjustmentDirection.LEVEL_UP) AppColors.CorrectGreen else Color(0xFFE65100),
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (direction == LevelAdjustmentDirection.LEVEL_UP) {
                            stringResource(R.string.summary_btn_accept_level_up, targetLevel.getLabel(isVi))
                        } else {
                            stringResource(R.string.summary_btn_accept_level_down, targetLevel.getLabel(isVi))
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


