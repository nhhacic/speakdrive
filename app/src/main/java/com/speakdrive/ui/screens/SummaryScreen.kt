package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.ui.components.ChatBubble

@Composable
fun SummaryScreen(
    onPracticeAgain: (mediaId: String) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    viewModel: SummaryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SummaryContent(state, onPracticeAgain, onHome, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryContent(
    state: SummaryUiState,
    onPracticeAgain: (mediaId: String) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit
) {
    var showTranscript by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kết quả buổi học") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                }
            )
        }
    ) { padding ->
        val detail = state.detail
        if (state.isLoading || detail == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.isLoading) CircularProgressIndicator() else Text("Không tìm thấy buổi học này.")
            }
            return@Scaffold
        }
        val session = detail.session
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(state.title, style = MaterialTheme.typography.titleLarge)
                        Text("${state.levelLabel} • ${state.durationLabel}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (state.isSummarizing) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                        Text("AI đang chấm điểm và tổng kết buổi học…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item { ScoreSection(session.fluencyScore, session.grammarScore, session.vocabularyScore) }
                session.encouragement?.takeIf { it.isNotBlank() }?.let { text ->
                    item { Text(text, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic) }
                }
            }

            if (detail.words.isNotEmpty()) {
                item { SectionTitle("Từ mới đã học (${detail.words.size})") }
                items(detail.words, key = { "w${it.id}" }) { word ->
                    Card {
                        Column(Modifier.padding(12.dp).fillMaxWidth()) {
                            Text(word.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            if (word.meaning.isNotBlank()) Text(word.meaning, style = MaterialTheme.typography.bodyMedium)
                            if (word.exampleSentence.isNotBlank()) {
                                Text("“${word.exampleSentence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
            }

            if (detail.corrections.isNotEmpty()) {
                item { SectionTitle("Cần cải thiện") }
                items(detail.corrections, key = { "c${it.id}" }) { correction ->
                    Card {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                correction.original,
                                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                                color = MaterialTheme.colorScheme.error
                            )
                            Text("✓ ${correction.corrected}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.tertiary)
                            if (correction.explanation.isNotBlank()) {
                                Text(correction.explanation, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            session.nextSuggestion?.takeIf { it.isNotBlank() && !state.isSummarizing }?.let { suggestion ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Gợi ý lần sau", style = MaterialTheme.typography.titleMedium)
                            Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onPracticeAgain(state.againMediaId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Luyện tiếp chủ đề này")
                    }
                    OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Về trang chủ") }
                }
            }

            item {
                HorizontalDivider()
                TextButton(onClick = { showTranscript = !showTranscript }) {
                    Text(if (showTranscript) "Ẩn nội dung hội thoại" else "Xem lại nội dung hội thoại (${detail.messages.size})")
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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ScoreSection(fluency: Int?, grammar: Int?, vocabulary: Int?) {
    if (fluency == null && grammar == null && vocabulary == null) return
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Đánh giá", style = MaterialTheme.typography.titleMedium)
            ScoreBar("Trôi chảy", fluency)
            ScoreBar("Ngữ pháp", grammar)
            ScoreBar("Từ vựng", vocabulary)
        }
    }
}

@Composable
private fun ScoreBar(label: String, score: Int?) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(score?.let { "$it/10" } ?: "—", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        LinearProgressIndicator(progress = { (score ?: 0) / 10f }, modifier = Modifier.fillMaxWidth())
    }
}
