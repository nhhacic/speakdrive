package com.speakdrive.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.data.repository.DayPractice
import com.speakdrive.ui.components.StatTile
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiến trình") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("🔥 ${state.stats.streakDays}", "ngày liên tục", Modifier.weight(1f))
                    StatTile("${state.stats.totalMinutes}", "tổng số phút", Modifier.weight(1f))
                    StatTile("${state.stats.completedSessions}", "buổi học", Modifier.weight(1f))
                }
            }
            item {
                Card {
                    Column(Modifier.padding(16.dp)) {
                        Text("7 ngày qua (phút)", style = MaterialTheme.typography.titleMedium)
                        WeekChart(state.stats.lastSevenDays, Modifier.fillMaxWidth().height(140.dp).padding(top = 12.dp))
                    }
                }
            }
            item {
                Card(onClick = onOpenVocabulary) {
                    ListItem(
                        headlineContent = { Text("Sổ từ vựng") },
                        supportingContent = { Text("${state.stats.dueWordCount} từ đến hạn ôn tập") }
                    )
                }
            }
            item { Text("Theo chủ đề", style = MaterialTheme.typography.titleMedium) }
            items(state.topicRows) { (title, count) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = MaterialTheme.typography.bodyMedium)
                    Text("$count buổi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            item { Text("Lịch sử", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (state.history.isEmpty()) {
                item { Text("Chưa có buổi học nào. Hãy bắt đầu từ trang chủ!", style = MaterialTheme.typography.bodyMedium) }
            }
            items(state.history, key = { it.sessionId }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = { Text(if (item.isCompleted) item.subtitle else "${item.subtitle} • chưa hoàn tất") },
                    trailingContent = { item.averageScore?.let { Text("$it/10", color = MaterialTheme.colorScheme.primary) } },
                    modifier = Modifier.clickable { onOpenSession(item.sessionId) }
                )
            }
        }
    }
}

/** Simple bar chart of minutes practised per day. */
@Composable
private fun WeekChart(days: List<DayPractice>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val maxMinutes = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(10)
    val description = days.joinToString { "${it.date.dayOfMonth}/${it.date.monthValue}: ${it.minutes} phút" }
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (days.isEmpty()) return@Canvas
            val slot = size.width / days.size
            val barWidth = slot * 0.55f
            days.forEachIndexed { index, day ->
                val fraction = day.minutes.toFloat() / maxMinutes
                val barHeight = (size.height * fraction).coerceAtLeast(4f)
                drawRoundRect(
                    color = if (day.minutes > 0) barColor else emptyColor,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("vi")),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
