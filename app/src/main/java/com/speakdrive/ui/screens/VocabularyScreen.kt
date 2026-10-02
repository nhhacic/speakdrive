package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.local.entity.LearnedWordEntity
import com.speakdrive.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class VocabularyItem(val word: LearnedWordEntity, val reviewLabel: String, val isDue: Boolean)

@HiltViewModel
class VocabularyViewModel @Inject constructor(sessionRepository: SessionRepository) : ViewModel() {
    val words: StateFlow<List<VocabularyItem>?> = sessionRepository.observeAllWords()
        .map { words ->
            val now = System.currentTimeMillis()
            words.map { word ->
                val days = TimeUnit.MILLISECONDS.toDays(word.nextReviewAt - now)
                val isDue = word.nextReviewAt <= now
                VocabularyItem(
                    word = word,
                    reviewLabel = when {
                        isDue -> "Đến hạn ôn"
                        days < 1 -> "Ôn lại trong hôm nay"
                        else -> "Ôn lại sau $days ngày"
                    },
                    isDue = isDue
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onBack: () -> Unit,
    onStartReview: (mediaId: String) -> Unit,
    viewModel: VocabularyViewModel = hiltViewModel()
) {
    val words by viewModel.words.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sổ từ vựng") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                }
            )
        }
    ) { padding ->
        val list = words
        if (list.isNullOrEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (list == null) "" else "Từ mới bạn học được trong mỗi buổi sẽ xuất hiện ở đây và được nhắc ôn lại theo lịch.",
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val dueCount = list.count { it.isDue }
            if (dueCount > 0) {
                item {
                    Button(onClick = { onStartReview(MediaIds.REVIEW) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Ôn $dueCount từ đến hạn bằng giọng nói")
                    }
                }
            }
            items(list, key = { it.word.id }) { item ->
                Card {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        Text(item.word.word, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        if (item.word.meaning.isNotBlank()) Text(item.word.meaning, style = MaterialTheme.typography.bodyMedium)
                        if (item.word.exampleSentence.isNotBlank()) {
                            Text("“${item.word.exampleSentence}”", style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                        }
                        Text(
                            "${item.reviewLabel} • đã ôn ${item.word.reviewCount} lần",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (item.isDue) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
