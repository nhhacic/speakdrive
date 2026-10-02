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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val azureTest by viewModel.azureTest.collectAsStateWithLifecycle()

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

            AzureSection(
                enabled = prefs.learner.azureEnabled,
                savedRegion = prefs.learner.azureRegion,
                savedKey = prefs.learner.azureKey,
                testState = azureTest,
                onEnabledChange = viewModel::setAzureEnabled,
                onSaveAndTest = viewModel::saveAndTestAzure
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

/** Optional third judge for pronunciation drills: Azure Pronunciation Assessment. */
@Composable
private fun AzureSection(
    enabled: Boolean,
    savedRegion: String,
    savedKey: String,
    testState: AzureTestState,
    onEnabledChange: (Boolean) -> Unit,
    onSaveAndTest: (region: String, key: String) -> Unit
) {
    var region by rememberSaveable(savedRegion) { mutableStateOf(savedRegion) }
    var key by rememberSaveable(savedKey) { mutableStateOf(savedKey) }
    val missingCredentials = savedRegion.isBlank() || savedKey.isBlank()

    ListItem(
        headlineContent = { Text("Chấm phát âm bằng Azure") },
        supportingContent = {
            Text(
                "Khi luyện phát âm, Microsoft Azure chấm thêm từng từ và từng âm (0–100). Câu chỉ đạt khi cả AI, " +
                    "bản ghi chữ và Azure cùng đồng ý. Miễn phí 5 giờ âm thanh/tháng."
            )
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onEnabledChange) }
    )
    if (!enabled) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text("Region (vd: southeastasia)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text("Key (KEY 1 trong Azure portal)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { onSaveAndTest(region, key) },
            enabled = region.isNotBlank() && key.isNotBlank() && testState != AzureTestState.Testing,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Lưu & kiểm tra kết nối") }
        val (message, color) = when (testState) {
            AzureTestState.Idle -> (if (missingCredentials) "Chưa có Region/Key: Azure sẽ được bỏ qua khi chấm." else "") to
                MaterialTheme.colorScheme.error
            AzureTestState.Testing -> "Đang kiểm tra…" to MaterialTheme.colorScheme.onSurfaceVariant
            AzureTestState.Ok -> "✓ Kết nối Azure thành công" to MaterialTheme.colorScheme.tertiary
            is AzureTestState.Failed -> "✗ ${testState.message}" to MaterialTheme.colorScheme.error
        }
        if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodySmall, color = color)
        Text(
            "Key chỉ lưu trên điện thoại này và chỉ được gửi tới Azure. Hướng dẫn lấy key: docs/AZURE_SETUP.md.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
