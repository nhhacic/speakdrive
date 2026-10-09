package com.speakdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.speakdrive.BuildConfig
import com.speakdrive.R
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.ScreenAwakeMode
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.data.repository.UserPreferences
import kotlin.math.roundToInt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Storage
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream
import java.io.OutputStream

/**
 * Các danh mục cài đặt logic của SpeakDrive.
 */
enum class SettingsSection(
    val titleRes: Int,
    val icon: ImageVector
) {
    LEARNING(R.string.settings_group_learning, Icons.Filled.School),
    VOICE(R.string.settings_group_voice, Icons.Filled.RecordVoiceOver),
    DRIVING(R.string.settings_group_car, Icons.Filled.DirectionsCar),
    MEMORY(R.string.settings_group_memory, Icons.Filled.Psychology),
    DATA(R.string.settings_group_data, Icons.Filled.Storage),
    AZURE(R.string.settings_group_azure, Icons.Filled.Cloud),
    ABOUT(R.string.settings_group_info, Icons.Filled.Info);
}

// ----------------------------------------------------------------------------
// Nhóm: Quản lý Dữ liệu (Sao lưu, Khôi phục, Xuất Anki)
// ----------------------------------------------------------------------------
@Composable
fun DataBackupSettingsSection(
    onExportBackup: (OutputStream) -> Unit,
    onImportBackup: (InputStream) -> Unit,
    onExportAnki: (OutputStream) -> Unit
) {
    val context = LocalContext.current

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                onExportBackup(stream)
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                onImportBackup(stream)
            }
        }
    }

    val exportAnkiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { stream ->
                onExportAnki(stream)
            }
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            // Sao lưu
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_title)) },
                supportingContent = { Text(stringResource(R.string.settings_backup_desc)) },
                trailingContent = {
                    Button(onClick = {
                        val fileName = "speakdrive_backup_${System.currentTimeMillis() / 1000}.json"
                        exportBackupLauncher.launch(fileName)
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Khôi phục
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_restore_title)) },
                supportingContent = { Text(stringResource(R.string.settings_restore_desc)) },
                trailingContent = {
                    Button(onClick = {
                        importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                    }) {
                        Text(stringResource(R.string.start))
                    }
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            // Xuất Anki
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_anki_title)) },
                supportingContent = { Text(stringResource(R.string.settings_anki_desc)) },
                trailingContent = {
                    Button(onClick = {
                        val fileName = "speakdrive_anki_${System.currentTimeMillis() / 1000}.txt"
                        exportAnkiLauncher.launch(fileName)
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Nhóm 1: Luyện tập & Trình độ (Learning & Practice)
// ----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LearningSettingsSection(
    prefs: UserPreferences,
    onSetLevel: (DifficultyLevel) -> Unit,
    onSetAdaptiveLevelRecommendation: (Boolean) -> Unit,
    onSetDailyGoal: (Int) -> Unit,
    onSetDrillSentenceLength: (DrillSentenceLength) -> Unit,
    onSetDrillCategory: (DrillCategory) -> Unit,
    onSetStorytellingStyle: (StorytellingStyle) -> Unit,
    onSetStoryDuration: (StoryDuration) -> Unit,
    onSetMultiVoiceStorytelling: (Boolean) -> Unit,
    onSetBetterPhrasing: (Boolean) -> Unit = {}
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    SettingsGroupCard(
        title = stringResource(R.string.settings_group_learning),
        icon = Icons.Filled.School
    ) {
        // Cấp độ người học
        Text(
            stringResource(R.string.settings_learner_level),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DifficultyLevel.entries.forEach { level ->
                FilterChip(
                    selected = level == prefs.learner.level,
                    onClick = { onSetLevel(level) },
                    label = { Text("${level.displayName} (${level.cefr})") }
                )
            }
        }
        Text(
            "${prefs.learner.level.getLabel(isVi)} — ${prefs.learner.level.getDescription(isVi)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Gợi ý cấp độ thích ứng
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_adaptive_level), fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_adaptive_level_desc) + "\n" +
                        stringResource(R.string.settings_adaptive_level_voice_tip)
                )
            },
            trailingContent = {
                Switch(
                    checked = prefs.learner.adaptiveLevelRecommendation,
                    onCheckedChange = onSetAdaptiveLevelRecommendation
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetAdaptiveLevelRecommendation(!prefs.learner.adaptiveLevelRecommendation) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Gợi ý nói tự nhiên hơn (Better Phrasing)
        ListItem(
            headlineContent = { Text(if (isVi) "Gợi ý nói tự nhiên hơn" else "Better phrasing upgrades", fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(
                    if (isVi) "AI nhẹ nhàng gợi ý cách diễn đạt tự nhiên của người bản xứ khi bạn dùng câu đơn giản (ví dụ: 'very tired' ➔ 'exhausted').\nLệnh giọng nói: \"bật/tắt gợi ý nói hay hơn\"."
                    else "AI gently offers natural native-speaker phrasing upgrades when you use simple expressions (e.g. 'very tired' ➔ 'exhausted').\nVoice command: \"turn on/off better phrasing\"."
                )
            },
            trailingContent = {
                Switch(
                    checked = prefs.learner.betterPhrasingEnabled,
                    onCheckedChange = onSetBetterPhrasing
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetBetterPhrasing(!prefs.learner.betterPhrasingEnabled) }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Mục tiêu luyện tập mỗi ngày
        var goal by remember { mutableFloatStateOf(prefs.dailyGoalMinutes.toFloat()) }
        LaunchedEffect(prefs.dailyGoalMinutes) { goal = prefs.dailyGoalMinutes.toFloat() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.settings_daily_goal),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.settings_goal_minutes_format, goal.roundToInt()),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = goal,
            onValueChange = { goal = it },
            onValueChangeFinished = { onSetDailyGoal(goal.roundToInt()) },
            valueRange = 5f..60f,
            steps = 10
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Phản xạ câu Drill
        Text(
            stringResource(R.string.settings_drill_sentence_length),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrillSentenceLength.entries.forEach { length ->
                FilterChip(
                    selected = length == prefs.learner.drillSentenceLength,
                    onClick = { onSetDrillSentenceLength(length) },
                    label = { Text(length.getLabel(isVi)) }
                )
            }
        }
        Text(
            prefs.learner.drillSentenceLength.getDescription(isVi) + "\n" + stringResource(R.string.settings_drill_sentence_length_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Chủ đề Drill
        Text(
            stringResource(R.string.settings_drill_category),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrillCategory.entries.forEach { category ->
                FilterChip(
                    selected = category == prefs.learner.drillCategory,
                    onClick = { onSetDrillCategory(category) },
                    label = { Text(category.getLabel(isVi)) }
                )
            }
        }
        Text(
            prefs.learner.drillCategory.getDescription(isVi) + "\n" + stringResource(R.string.settings_drill_category_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Chế độ kể chuyện
        Text(
            stringResource(R.string.settings_storytelling_mode),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorytellingStyle.entries.forEach { style ->
                FilterChip(
                    selected = style == prefs.learner.storytellingStyle,
                    onClick = { onSetStorytellingStyle(style) },
                    label = { Text(style.getLabel(isVi)) }
                )
            }
        }
        Text(
            prefs.learner.storytellingStyle.getDescription(isVi) + "\n" + stringResource(R.string.settings_story_style_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Độ dài câu chuyện
        Text(
            stringResource(R.string.settings_story_duration),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StoryDuration.entries.forEach { duration ->
                FilterChip(
                    selected = duration == prefs.learner.storyDuration,
                    onClick = { onSetStoryDuration(duration) },
                    label = { Text(duration.getLabel(isVi)) }
                )
            }
        }
        Text(
            prefs.learner.storyDuration.getDescription(isVi) + "\n" + stringResource(R.string.settings_story_duration_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Đa giọng kịch truyền thanh
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_multi_voice), fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_multi_voice_desc) + "\n" +
                        stringResource(R.string.settings_multi_voice_voice_tip)
                )
            },
            trailingContent = {
                Switch(
                    checked = prefs.learner.multiVoiceStorytelling,
                    onCheckedChange = onSetMultiVoiceStorytelling
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSetMultiVoiceStorytelling(!prefs.learner.multiVoiceStorytelling) }
        )
    }
}

// ----------------------------------------------------------------------------
// Nhóm 2: Giọng nói & Tương tác AI (Voice & Speech Interaction)
// ----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSettingsSection(
    prefs: UserPreferences,
    onSetAiVolume: (Int) -> Unit,
    onSetVoice: (AiVoice) -> Unit,
    onSetRandomVoice: (Boolean) -> Unit,
    onSetPronunciationStrictness: (PronunciationStrictness) -> Unit,
    onSetAllowVietnameseHelp: (Boolean) -> Unit,
    onSetAllowBargeIn: (Boolean) -> Unit
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    SettingsGroupCard(
        title = stringResource(R.string.settings_group_voice),
        icon = Icons.Filled.RecordVoiceOver
    ) {
        // Âm lượng giọng AI
        var dragVolume by remember(prefs.learner.aiVolume) { mutableFloatStateOf(prefs.learner.aiVolume.toFloat()) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.ai_volume_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${dragVolume.roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = dragVolume,
            onValueChange = { dragVolume = it },
            onValueChangeFinished = { onSetAiVolume(dragVolume.roundToInt().coerceIn(10, 100)) },
            valueRange = 10f..100f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.settings_volume_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Chọn giọng AI
        Text(
            stringResource(R.string.settings_voice_choice),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Column(Modifier.selectableGroup()) {
            val isRandom = prefs.learner.randomVoice
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_voice_random), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(stringResource(R.string.settings_voice_random_desc)) },
                leadingContent = { RadioButton(selected = isRandom, onClick = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = isRandom, role = Role.RadioButton) { onSetRandomVoice(true) }
            )
            AiVoice.entries.forEach { voice ->
                val selected = !isRandom && voice.id == prefs.learner.voiceId
                ListItem(
                    headlineContent = { Text(voice.id, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(voice.getLabel(isVi)) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onSetVoice(voice) }
                )
            }
        }
        Text(
            stringResource(R.string.settings_voice_switch_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Độ khắt khe khi chấm phát âm
        Text(
            stringResource(R.string.settings_strictness),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PronunciationStrictness.userFacingEntries.forEach { strictness ->
                FilterChip(
                    selected = strictness == prefs.learner.pronunciationStrictness,
                    onClick = { onSetPronunciationStrictness(strictness) },
                    label = { Text(strictness.getLabel(isVi)) }
                )
            }
        }
        val currentStrictness = prefs.learner.pronunciationStrictness
        val strictnessDesc = if (currentStrictness == PronunciationStrictness.AUTO) {
            val resolved = currentStrictness.resolveForLevel(prefs.learner.level)
            stringResource(
                R.string.settings_strictness_auto_desc,
                prefs.learner.level.getLabel(isVi),
                prefs.learner.level.cefr,
                resolved.getDescription(isVi)
            )
        } else {
            currentStrictness.getDescription(isVi)
        }
        Text(
            strictnessDesc,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.settings_strictness_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Trợ giúp tiếng Việt & Ngắt lời AI
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_vietnamese_help), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.settings_vietnamese_help_desc)) },
            trailingContent = {
                Switch(checked = prefs.learner.allowVietnameseHelp, onCheckedChange = onSetAllowVietnameseHelp)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_barge_in), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(stringResource(R.string.settings_barge_in_desc)) },
            trailingContent = {
                Switch(checked = prefs.learner.allowBargeIn, onCheckedChange = onSetAllowBargeIn)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

// ----------------------------------------------------------------------------
// Nhóm 3: Chế độ Lái xe & Màn hình (Driving & Screen)
// ----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrivingSettingsSection(
    prefs: UserPreferences,
    onSetAutoStartOnCarConnect: (Boolean) -> Unit,
    onSetAutoPauseWhenUnfocused: (Boolean) -> Unit,
    onSetScreenAwakeMode: (ScreenAwakeMode) -> Unit,
    onSetShowTranslationSubtitle: (Boolean) -> Unit
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    SettingsGroupCard(
        title = stringResource(R.string.settings_group_car),
        icon = Icons.Filled.DirectionsCar
    ) {
        // Tự bắt đầu khi kết nối ô tô
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.settings_auto_start_car), fontWeight = FontWeight.Bold)
            },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_auto_start_car_desc) + "\n" + stringResource(R.string.settings_auto_start_car_voice_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = prefs.learner.autoStartOnCarConnect,
                    onCheckedChange = onSetAutoStartOnCarConnect
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Tự tạm dừng khi rời app
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.settings_auto_pause_unfocused),
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_auto_pause_unfocused_desc) + "\n" + stringResource(R.string.settings_auto_pause_unfocused_voice_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = prefs.learner.autoPauseWhenUnfocused,
                    onCheckedChange = onSetAutoPauseWhenUnfocused
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Giữ màn hình sáng
        Text(
            stringResource(R.string.settings_screen_awake),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ScreenAwakeMode.entries.forEach { mode ->
                FilterChip(
                    selected = prefs.learner.screenAwakeMode == mode,
                    onClick = { onSetScreenAwakeMode(mode) },
                    label = { Text(mode.getLabel(isVi)) }
                )
            }
        }
        Text(
            prefs.learner.screenAwakeMode.getDescription(isVi) + "\n" + stringResource(R.string.settings_screen_awake_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Phụ đề dịch nghĩa
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.settings_show_translation_subtitles),
                    fontWeight = FontWeight.Bold
                )
            },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_show_translation_subtitles_desc) + "\n" + stringResource(R.string.settings_show_translation_subtitles_voice_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Switch(
                    checked = prefs.learner.showTranslationSubtitle,
                    onCheckedChange = onSetShowTranslationSubtitle
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

// ----------------------------------------------------------------------------
// Nhóm 5: Dịch vụ Chấm phát âm Azure (Azure Pronunciation Service)
// ----------------------------------------------------------------------------
@Composable
fun AzureSettingsSection(
    prefs: UserPreferences,
    azureTest: AzureTestState,
    onSetAzureEnabled: (Boolean) -> Unit,
    onTestAzure: () -> Unit,
    onSaveAndTestAzure: (String, String) -> Unit
) {
    SettingsGroupCard(
        title = stringResource(R.string.settings_group_azure),
        icon = Icons.Filled.Cloud
    ) {
        AzureSection(
            enabled = prefs.learner.azureEnabled,
            savedRegion = prefs.learner.azureRegion,
            savedKey = prefs.learner.azureKey,
            testState = azureTest,
            onEnabledChange = onSetAzureEnabled,
            onTestConnection = onTestAzure,
            onSaveAndTest = onSaveAndTestAzure
        )
    }
}

// ----------------------------------------------------------------------------
// Nhóm 6: Ngôn ngữ & Thông tin ứng dụng (App & Info)
// ----------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutSettingsSection(
    prefs: UserPreferences,
    onSetAppLanguage: (AppLanguage) -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit
) {
    // Ngôn ngữ giao diện
    SettingsGroupCard(
        title = stringResource(R.string.settings_group_language),
        icon = Icons.Filled.Language
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppLanguage.entries.forEach { lang ->
                FilterChip(
                    selected = lang == prefs.appLanguage,
                    onClick = { onSetAppLanguage(lang) },
                    label = {
                        val name = if (lang == AppLanguage.SYSTEM) stringResource(R.string.settings_language_system) else lang.nativeName
                        Text("${lang.flagEmoji} $name")
                    }
                )
            }
        }
        Text(
            stringResource(R.string.settings_language_desc) + "\n" + stringResource(R.string.settings_language_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Thông tin & Pháp lý
    SettingsGroupCard(
        title = stringResource(R.string.settings_group_info),
        icon = Icons.Filled.Info
    ) {
        NavRow(stringResource(R.string.settings_privacy_policy), onOpenPrivacy)
        NavRow(stringResource(R.string.settings_about), onOpenAbout)
    }

    // Version Footer
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(
                R.string.settings_version_footer,
                BuildConfig.VERSION_NAME,
                com.speakdrive.ai.BuildConfig.LIVE_MODEL,
                com.speakdrive.ai.BuildConfig.TEXT_MODEL
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ----------------------------------------------------------------------------
// Reusable Sub-components
// ----------------------------------------------------------------------------
@Composable
fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            content()
        }
    }
}

@Composable
fun AzureSection(
    enabled: Boolean,
    savedRegion: String,
    savedKey: String,
    testState: AzureTestState,
    onEnabledChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    onSaveAndTest: (String, String) -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_azure_enable)) },
        supportingContent = {
            Text(stringResource(R.string.settings_azure_enable_desc))
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onEnabledChange) }
    )

    var region by rememberSaveable(savedRegion) { mutableStateOf(savedRegion) }
    var key by rememberSaveable(savedKey) { mutableStateOf(savedKey) }
    val changed = region.trim() != savedRegion || key.trim() != savedKey

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = region,
            onValueChange = { region = it },
            label = { Text(stringResource(R.string.settings_azure_region)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.settings_azure_key)) },
            supportingText = { Text(stringResource(R.string.settings_azure_key_hint)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { if (changed) onSaveAndTest(region, key) else onTestConnection() },
            enabled = testState != AzureTestState.Testing && region.isNotBlank() && key.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    testState == AzureTestState.Testing -> stringResource(R.string.settings_azure_status_testing)
                    changed -> stringResource(R.string.settings_azure_save_test)
                    else -> stringResource(R.string.settings_azure_status_idle)
                }
            )
        }
        val (message, color) = when (testState) {
            AzureTestState.Idle -> "" to MaterialTheme.colorScheme.onSurfaceVariant
            AzureTestState.Testing -> stringResource(R.string.settings_azure_status_testing) to MaterialTheme.colorScheme.onSurfaceVariant
            AzureTestState.Ok -> stringResource(R.string.settings_azure_status_ok) to MaterialTheme.colorScheme.tertiary
            is AzureTestState.Failed -> stringResource(R.string.settings_azure_status_error, testState.message) to MaterialTheme.colorScheme.error
        }
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
fun NavRow(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
