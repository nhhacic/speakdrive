package com.speakdrive.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.BuildConfig
import com.speakdrive.R
import com.speakdrive.ai.model.AiVoice
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.ai.model.DifficultyLevel
import com.speakdrive.ai.model.DrillCategory
import com.speakdrive.ai.model.DrillSentenceLength
import com.speakdrive.ai.model.PronunciationStrictness
import com.speakdrive.ai.model.StoryDuration
import com.speakdrive.ai.model.StorytellingStyle
import com.speakdrive.data.repository.UserPreferences
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val azureTest by viewModel.azureTest.collectAsStateWithLifecycle()
    SettingsContent(
        prefs = prefs,
        azureTest = azureTest,
        onBack = onBack,
        onOpenProgress = onOpenProgress,
        onOpenVocabulary = onOpenVocabulary,
        onOpenPrivacy = onOpenPrivacy,
        onOpenAbout = onOpenAbout,
        onSetLevel = viewModel::setLevel,
        onSetVoice = viewModel::setVoice,
        onSetRandomVoice = viewModel::setRandomVoice,
        onSetPronunciationStrictness = viewModel::setPronunciationStrictness,
        onSetStorytellingStyle = viewModel::setStorytellingStyle,
        onSetStoryDuration = viewModel::setStoryDuration,
        onSetMultiVoiceStorytelling = viewModel::setMultiVoiceStorytelling,
        onSetAllowVietnameseHelp = viewModel::setAllowVietnameseHelp,
        onSetAllowBargeIn = viewModel::setAllowBargeIn,
        onSetAzureEnabled = viewModel::setAzureEnabled,
        onTestAzure = viewModel::testAzureConnection,
        onSaveAndTestAzure = viewModel::saveAndTestAzure,
        onSetDailyGoal = viewModel::setDailyGoal,
        onSetAppLanguage = viewModel::setAppLanguage,
        onSetAdaptiveLevelRecommendation = viewModel::setAdaptiveLevelRecommendation,
        onSetDrillSentenceLength = viewModel::setDrillSentenceLength,
        onSetDrillCategory = viewModel::setDrillCategory,
        onSetAiVolume = viewModel::setAiVolume
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(
    prefs: UserPreferences,
    azureTest: AzureTestState,
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onSetAppLanguage: (AppLanguage) -> Unit = {},
    onSetLevel: (DifficultyLevel) -> Unit = {},
    onSetAdaptiveLevelRecommendation: (Boolean) -> Unit = {},
    onSetDrillSentenceLength: (DrillSentenceLength) -> Unit = {},
    onSetDrillCategory: (DrillCategory) -> Unit = {},
    onSetVoice: (AiVoice) -> Unit = {},
    onSetRandomVoice: (Boolean) -> Unit = {},
    onSetPronunciationStrictness: (PronunciationStrictness) -> Unit = {},
    onSetStorytellingStyle: (StorytellingStyle) -> Unit = {},
    onSetStoryDuration: (StoryDuration) -> Unit = {},
    onSetMultiVoiceStorytelling: (Boolean) -> Unit = {},
    onSetAllowVietnameseHelp: (Boolean) -> Unit = {},
    onSetAllowBargeIn: (Boolean) -> Unit = {},
    onSetAzureEnabled: (Boolean) -> Unit = {},
    onTestAzure: () -> Unit = {},
    onSaveAndTestAzure: (String, String) -> Unit = { _, _ -> },
    onSetDailyGoal: (Int) -> Unit = {},
    onSetAiVolume: (Int) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Group 0: Interface Language (Multilingual UI)
            SettingsGroupCard(title = stringResource(R.string.settings_group_language), icon = Icons.Filled.Language) {
                Text(
                    stringResource(R.string.settings_group_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = lang == prefs.appLanguage,
                            onClick = { onSetAppLanguage(lang) },
                            label = { Text("${lang.flagEmoji} ${lang.nativeName}") }
                        )
                    }
                }
                Text(
                    stringResource(R.string.settings_language_desc) + "\n" + stringResource(R.string.settings_language_voice_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val isVi = prefs.appLanguage == AppLanguage.VIETNAMESE || (prefs.appLanguage == AppLanguage.SYSTEM && java.util.Locale.getDefault().language == "vi")

            // Group 1: Learning & Level
            SettingsGroupCard(title = stringResource(R.string.settings_group_learning), icon = Icons.Filled.School) {
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
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetAdaptiveLevelRecommendation(!prefs.learner.adaptiveLevelRecommendation) }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
                    if (isVi) {
                        "Tự động theo cấp độ học viên: ${prefs.learner.level.getLabel(true)} (${prefs.learner.level.cefr}) — ${resolved.getDescription(true)}"
                    } else {
                        "Auto adapted to your level: ${prefs.learner.level.displayName} (${prefs.learner.level.cefr}) — ${resolved.getDescription(false)}"
                    }
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
                    colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSetMultiVoiceStorytelling(!prefs.learner.multiVoiceStorytelling) }
                )
            }

            // Group 2: AI Voice & Speech Interaction
            SettingsGroupCard(title = stringResource(R.string.settings_group_voice), icon = Icons.Filled.RecordVoiceOver) {
                // AI Voice Volume Setting
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isVi) "Âm lượng giọng nói AI" else "AI Voice Volume",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${prefs.learner.aiVolume}%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = prefs.learner.aiVolume.toFloat(),
                    onValueChange = { onSetAiVolume(it.roundToInt().coerceIn(10, 100)) },
                    valueRange = 10f..100f,
                    steps = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (isVi) {
                        "Điều chỉnh độ lớn giọng nói của AI. Rất hữu ích khi dùng tai nghe Bluetooth để âm lượng vừa vặn, không bị quá to.\nTip lệnh giọng nói: Nói \"nói nhỏ lại\", \"giảm âm lượng\", \"nói to lên\" hoặc \"âm lượng 50%\"."
                    } else {
                        "Adjust AI voice volume. Especially helpful for Bluetooth headsets to prevent loud audio.\nVoice tip: Say \"speak softer\", \"lower volume\", \"speak louder\" or \"volume 50%\"."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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
            }

            // Group 3: Azure Speech Service
            SettingsGroupCard(title = stringResource(R.string.settings_group_azure), icon = Icons.Filled.Cloud) {
                AzureSection(
                    enabled = prefs.learner.azureEnabled,
                    testState = azureTest,
                    onEnabledChange = onSetAzureEnabled,
                    onTestConnection = onTestAzure
                )
            }

            // Group 4: Information & About
            SettingsGroupCard(title = stringResource(R.string.settings_group_info), icon = Icons.Filled.Info) {
                NavRow(stringResource(R.string.settings_privacy_policy), onOpenPrivacy)
                NavRow(stringResource(R.string.settings_about), onOpenAbout)
            }

            // App Version Footer
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
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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


/** Optional third judge for pronunciation drills: Azure Pronunciation Assessment. */
@Composable
private fun AzureSection(
    enabled: Boolean,
    testState: AzureTestState,
    onEnabledChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_azure_enable)) },
        supportingContent = {
            Text(stringResource(R.string.settings_azure_enable_desc))
        },
        trailingContent = { Switch(checked = enabled, onCheckedChange = onEnabledChange) }
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        Button(
            onClick = onTestConnection,
            enabled = testState != AzureTestState.Testing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (testState == AzureTestState.Testing) stringResource(R.string.settings_azure_status_testing) else stringResource(R.string.settings_azure_status_idle))
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
private fun NavRow(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
}
