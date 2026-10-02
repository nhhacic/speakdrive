package com.speakdrive.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.ai.model.Topic
import com.speakdrive.auto.MediaIds
import com.speakdrive.ui.components.StatTile
import com.speakdrive.ui.components.TopicCard

@Composable
fun HomeScreen(
    onStartLesson: (mediaId: String) -> Unit,
    onOpenCurrentLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state, onStartLesson, onOpenCurrentLesson, onOpenSettings, onOpenProgress)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onStartLesson: (mediaId: String) -> Unit,
    onOpenCurrentLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProgress: () -> Unit
) {
    var sheetTopic by remember { mutableStateOf<Topic?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SpeakDrive") },
                actions = {
                    IconButton(onClick = onOpenProgress) { Icon(Icons.Filled.Insights, contentDescription = "Tiến trình") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Cài đặt") }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isCarConnected) fullWidth { CarBanner() }
            state.currentLesson?.let { lesson ->
                fullWidth {
                    Card(
                        onClick = onOpenCurrentLesson,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        ListItem(
                            headlineContent = { Text("Bài học đang diễn ra") },
                            supportingContent = { Text("${lesson.topic.emoji} ${lesson.titleVi} • ${lesson.level.displayName}") },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                        )
                    }
                }
            }
            fullWidth { StatsRow(state) }
            fullWidth {
                QuickStartCard(
                    lastTopic = state.lastTopic,
                    onResume = { onStartLesson(MediaIds.RESUME) },
                    onRandom = { onStartLesson(MediaIds.RANDOM) }
                )
            }
            if (state.stats.dueWordCount > 0) {
                fullWidth {
                    Card(onClick = { onStartLesson(MediaIds.REVIEW) }) {
                        ListItem(
                            headlineContent = { Text("Ôn tập từ vựng") },
                            supportingContent = { Text("${state.stats.dueWordCount} từ đến hạn ôn — AI sẽ hỏi bạn từng từ") },
                            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) }
                        )
                    }
                }
            }
            fullWidth {
                Text("Chủ đề luyện tập", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            }
            items(state.topics, key = { it.topic.id }) { item ->
                TopicCard(
                    emoji = item.topic.emoji,
                    title = item.topic.titleVi,
                    subtitle = item.topic.titleEn,
                    completedLessons = item.completedLessons,
                    onClick = { sheetTopic = item.topic }
                )
            }
            fullWidth {
                Text(
                    "Mẹo: trên xe, hãy nói \"Hey Google, play travel English on SpeakDrive\" để bắt đầu mà không cần chạm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }

    sheetTopic?.let { topic ->
        ModalBottomSheet(onDismissRequest = { sheetTopic = null }) {
            TopicSheet(
                topic = topic,
                onPick = { mediaId ->
                    sheetTopic = null
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
            Spacer(Modifier.padding(6.dp))
            Text(
                "Đã kết nối Android Auto — hãy điều khiển bài học trên màn hình xe.",
                color = MaterialTheme.colorScheme.onTertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun StatsRow(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("🔥 ${state.stats.streakDays}", "ngày liên tục", Modifier.weight(1f))
            StatTile("${state.stats.minutesToday}", "phút hôm nay", Modifier.weight(1f))
            StatTile("${state.stats.wordsToday}", "từ mới hôm nay", Modifier.weight(1f))
        }
        val goal = state.dailyGoalMinutes.coerceAtLeast(1)
        LinearProgressIndicator(
            progress = { (state.stats.minutesToday.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
        Text(
            "Mục tiêu hôm nay: ${state.stats.minutesToday}/$goal phút",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickStartCard(lastTopic: Topic?, onResume: () -> Unit, onRandom: () -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Luyện tập ngay", style = MaterialTheme.typography.titleMedium)
            Text(
                lastTopic?.let { "Lần trước: ${it.emoji} ${it.titleVi}" } ?: "AI sẽ chọn một chủ đề phù hợp để bạn bắt đầu.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onResume, modifier = Modifier.weight(1f)) {
                    Text(if (lastTopic != null) "Tiếp tục" else "Bắt đầu")
                }
                OutlinedButton(onClick = onRandom, modifier = Modifier.weight(1f)) { Text("Ngẫu nhiên") }
            }
        }
    }
}

@Composable
private fun TopicSheet(topic: Topic, onPick: (mediaId: String) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
        Text("${topic.emoji} ${topic.titleVi}", style = MaterialTheme.typography.titleLarge)
        Text(topic.titleEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onPick(MediaIds.topic(topic.id)) }, modifier = Modifier.fillMaxWidth()) {
            Text("Trò chuyện tự do về chủ đề này")
        }
        Spacer(Modifier.height(16.dp))
        Text("Nhập vai tình huống", style = MaterialTheme.typography.titleMedium)
        topic.scenarios.forEach { scenario ->
            ListItem(
                headlineContent = { Text(scenario.titleVi) },
                supportingContent = { Text("AI đóng vai ${scenario.aiRole}") },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Bắt đầu nhập vai") { onPick(MediaIds.scenario(scenario.id)) }
            )
        }
    }
}
