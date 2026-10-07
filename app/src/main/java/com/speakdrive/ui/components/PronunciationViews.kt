package com.speakdrive.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakdrive.ai.pronunciation.WordResult
import com.speakdrive.ai.pronunciation.WordStatus
import com.speakdrive.ui.theme.AppColors

private val CorrectColor = AppColors.CorrectGreen
private val WrongColor = AppColors.ErrorRed

/**
 * Câu đích với các từ được tô màu trực quan: từ đúng màu xanh mint,
 * từ nghe nhầm màu đỏ san hô kèm từ đã nghe, từ thiếu bị gạch ngang.
 */
fun markedSentence(words: List<WordResult>, missingColor: Color): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { index, word ->
        if (index > 0) append(" ")
        when (word.status) {
            WordStatus.OK -> if (word.isProblem) {
                withStyle(SpanStyle(color = WrongColor, fontWeight = FontWeight.Bold)) { append(word.word) }
                if (!word.heardAs.isNullOrBlank()) {
                    withStyle(SpanStyle(color = WrongColor, fontSize = 13.sp)) { append(" («${word.heardAs}»)") }
                }
                word.azureScore?.let { withStyle(SpanStyle(color = WrongColor, fontSize = 12.sp)) { append(" ($it)") } }
            } else {
                withStyle(SpanStyle(color = CorrectColor, fontWeight = FontWeight.SemiBold)) { append(word.word) }
            }
            WordStatus.WRONG -> {
                withStyle(SpanStyle(color = WrongColor, fontWeight = FontWeight.Bold)) { append(word.word) }
                withStyle(SpanStyle(color = WrongColor, fontSize = 13.sp)) { append(" («${word.heardAs}»)") }
            }
            WordStatus.MISSING -> withStyle(SpanStyle(color = missingColor, textDecoration = TextDecoration.LineThrough)) {
                append(word.word)
            }
        }
    }
}

/** Thẻ huấn luyện phát âm thông minh với phản hồi từng âm/từ */
@Composable
fun DrillCard(
    target: String?,
    translation: String? = null,
    attemptWords: List<WordResult>?,
    attemptPassed: Boolean?,
    attemptNumber: Int?,
    accuracyPercent: Int?,
    problemNote: String?,
    azureSummary: String?,
    azureWeakSounds: String?,
    azureWarning: String?,
    passedCount: Int,
    sentenceCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Icon + Goal progress pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_repeat_after_ai),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (sentenceCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_passed_sentences, passedCount, sentenceCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Target Sentence
            Text(
                text = target ?: androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_ai_first_sentence_hint),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )

            if (!translation.isNullOrBlank()) {
                Text(
                    text = translation,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    lineHeight = 20.sp
                )
            }

            // Attempt evaluation feedback
            if (attemptWords != null && attemptPassed != null) {
                val feedbackBg = if (attemptPassed) {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                }
                val feedbackTextColor = if (attemptPassed) CorrectColor else WrongColor

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(feedbackBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (attemptPassed) {
                                androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_attempt_passed, attemptNumber ?: 1)
                            } else if ((accuracyPercent ?: 0) >= 100) {
                                androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_attempt_failed_clarity, attemptNumber ?: 1)
                            } else {
                                androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_attempt_failed, attemptNumber ?: 1, accuracyPercent ?: 0)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = feedbackTextColor
                        )

                        Text(
                            text = markedSentence(attemptWords, MaterialTheme.colorScheme.outline),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }
                }

                if (azureSummary != null) {
                    Text(
                        azureSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!azureWeakSounds.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            androidx.compose.ui.res.stringResource(com.speakdrive.R.string.convo_weak_sounds, azureWeakSounds),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = WrongColor
                        )
                    }
                }

                if (!azureWarning.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(azureWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                if (!problemNote.isNullOrBlank()) {
                    Text(
                        problemNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

