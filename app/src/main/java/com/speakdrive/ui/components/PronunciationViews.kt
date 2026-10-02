package com.speakdrive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.speakdrive.ai.pronunciation.WordResult
import com.speakdrive.ai.pronunciation.WordStatus

/** Brand-independent signal colours: readable on both light and dark cards. */
private val CorrectColor = Color(0xFF2E7D32)
private val WrongColor = Color(0xFFC62828)

/**
 * The target sentence with each word marked: correct words in green, misheard words in red with
 * what was heard, missing words struck through.
 */
fun markedSentence(words: List<WordResult>, missingColor: Color): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { index, word ->
        if (index > 0) append(" ")
        when (word.status) {
            WordStatus.OK -> if (word.isProblem) {
                // The transcript heard the word, but Azure says it was pronounced badly.
                withStyle(SpanStyle(color = WrongColor, fontWeight = FontWeight.Bold)) { append(word.word) }
                word.azureScore?.let { withStyle(SpanStyle(color = WrongColor)) { append(" ($it)") } }
            } else {
                withStyle(SpanStyle(color = CorrectColor)) { append(word.word) }
            }
            WordStatus.WRONG -> {
                withStyle(SpanStyle(color = WrongColor, fontWeight = FontWeight.Bold)) { append(word.word) }
                withStyle(SpanStyle(color = WrongColor)) { append(" («${word.heardAs}»)") }
            }
            WordStatus.MISSING -> withStyle(SpanStyle(color = missingColor, textDecoration = TextDecoration.LineThrough)) {
                append(word.word)
            }
        }
    }
}

/** Shown during a repeat-after-me lesson: what to say, and how the last attempt went. */
@Composable
fun DrillCard(
    target: String?,
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
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Nhắc lại câu này", style = MaterialTheme.typography.labelSmall)
                if (sentenceCount > 0) {
                    Text("Đạt $passedCount/$sentenceCount câu", style = MaterialTheme.typography.labelSmall)
                }
            }
            Text(
                target ?: "AI sắp đọc câu đầu tiên…",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (attemptWords != null && attemptPassed != null) {
                Text(
                    if (attemptPassed) "✓ Lần $attemptNumber: ĐẠT" else "✗ Lần $attemptNumber: CHƯA ĐẠT • app nghe đúng $accuracyPercent% số từ",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (attemptPassed) CorrectColor else WrongColor
                )
                Text(markedSentence(attemptWords, MaterialTheme.colorScheme.outline), style = MaterialTheme.typography.bodyLarge)
                if (azureSummary != null) {
                    Text(azureSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (!azureWeakSounds.isNullOrBlank()) {
                    Text("Âm cần sửa: $azureWeakSounds", style = MaterialTheme.typography.bodyMedium, color = WrongColor)
                }
                if (!azureWarning.isNullOrBlank()) {
                    Text("⚠ $azureWarning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!problemNote.isNullOrBlank()) {
                    Text(problemNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}
