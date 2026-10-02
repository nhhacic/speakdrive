package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.BuildConfig
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.DifficultyLevel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenPrivacy: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Độ khó", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DifficultyLevel.entries.forEach { level ->
                    FilterChip(
                        selected = level == prefs.learner.level,
                        onClick = { viewModel.setLevel(level) },
                        label = { Text("${level.displayName} (${level.cefr})") }
                    )
                }
            }
            Text(prefs.learner.level.labelVi, style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()

            Text("Giọng AI", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                AiVoice.entries.forEach { voice ->
                    val selected = voice.id == prefs.learner.voiceId
                    ListItem(
                        headlineContent = { Text(voice.id) },
                        supportingContent = { Text(voice.labelVi) },
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { viewModel.setVoice(voice) }
                    )
                }
            }
            Text("Giọng mới áp dụng từ bài học tiếp theo.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Cho phép giải thích bằng tiếng Việt") },
                supportingContent = { Text("Khi bạn bí, AI có thể giải thích ngắn bằng tiếng Việt rồi quay lại tiếng Anh.") },
                trailingContent = {
                    Switch(checked = prefs.learner.allowVietnameseHelp, onCheckedChange = viewModel::setAllowVietnameseHelp)
                }
            )

            ListItem(
                headlineContent = { Text("Cho phép ngắt lời AI") },
                supportingContent = {
                    Text("Chỉ bật khi dùng tai nghe. Với loa ngoài hoặc loa xe, micro sẽ nghe thấy giọng AI và AI tự ngắt lời chính nó.")
                },
                trailingContent = {
                    Switch(checked = prefs.learner.allowBargeIn, onCheckedChange = viewModel::setAllowBargeIn)
                }
            )

            HorizontalDivider()

            var goal by remember { mutableFloatStateOf(prefs.dailyGoalMinutes.toFloat()) }
            LaunchedEffect(prefs.dailyGoalMinutes) { goal = prefs.dailyGoalMinutes.toFloat() }
            Text("Mục tiêu mỗi ngày: ${goal.roundToInt()} phút", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = goal,
                onValueChange = { goal = it },
                onValueChangeFinished = { viewModel.setDailyGoal(goal.roundToInt()) },
                valueRange = 5f..60f,
                steps = 10
            )

            HorizontalDivider()

            NavRow("Tiến trình & lịch sử", onOpenProgress)
            NavRow("Sổ từ vựng", onOpenVocabulary)
            NavRow("Chính sách bảo mật", onOpenPrivacy)

            HorizontalDivider()
            Text(
                "SpeakDrive ${BuildConfig.VERSION_NAME}\nAI: ${com.speakdrive.ai.BuildConfig.LIVE_MODEL} • ${com.speakdrive.ai.BuildConfig.TEXT_MODEL}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NavRow(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
