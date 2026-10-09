package com.speakdrive.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.InputStream
import java.io.OutputStream

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onOpenMemory: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val azureTest by viewModel.azureTest.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(backupStatus) {
        backupStatus?.let { status ->
            val message = when {
                status == "BACKUP_SUCCESS" -> context.getString(R.string.settings_backup_success)
                status.startsWith("RESTORE_SUCCESS") -> {
                    val count = status.substringAfter(": ").toIntOrNull() ?: 0
                    context.getString(R.string.settings_restore_success, count, 0)
                }
                status.startsWith("ANKI_SUCCESS") -> {
                    val count = status.substringAfter(": ").toIntOrNull() ?: 0
                    context.getString(R.string.settings_anki_success, count)
                }
                status.startsWith("BACKUP_ERROR") -> context.getString(R.string.settings_backup_error, status.substringAfter(": "))
                status.startsWith("RESTORE_ERROR") -> context.getString(R.string.settings_restore_error, status.substringAfter(": "))
                else -> status
            }
            snackbarHostState.showSnackbar(message)
            viewModel.clearBackupStatus()
        }
    }

    SettingsContent(
        prefs = prefs,
        azureTest = azureTest,
        snackbarHostState = snackbarHostState,
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
        onSetAiVolume = viewModel::setAiVolume,
        onSetAutoPauseWhenUnfocused = viewModel::setAutoPauseWhenUnfocused,
        onSetShowTranslationSubtitle = viewModel::setShowTranslationSubtitle,
        onSetAutoStartOnCarConnect = viewModel::setAutoStartOnCarConnect,
        onSetScreenAwakeMode = viewModel::setScreenAwakeMode,
        onSetBetterPhrasing = viewModel::setBetterPhrasing,
        onExportBackup = viewModel::exportBackup,
        onImportBackup = viewModel::importBackup,
        onExportAnki = viewModel::exportAnki,
        extraGroups = { MemorySettingsGroup(onOpenMemory = onOpenMemory) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    prefs: UserPreferences,
    azureTest: AzureTestState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
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
    onSetAiVolume: (Int) -> Unit = {},
    onSetAutoPauseWhenUnfocused: (Boolean) -> Unit = {},
    onSetShowTranslationSubtitle: (Boolean) -> Unit = {},
    onSetAutoStartOnCarConnect: (Boolean) -> Unit = {},
    onSetScreenAwakeMode: (ScreenAwakeMode) -> Unit = {},
    onSetBetterPhrasing: (Boolean) -> Unit = {},
    onExportBackup: (OutputStream) -> Unit = {},
    onImportBackup: (InputStream) -> Unit = {},
    onExportAnki: (OutputStream) -> Unit = {},
    initialSection: SettingsSection? = null,
    extraGroups: @Composable () -> Unit = {}
) {
    var selectedSection by rememberSaveable { mutableStateOf(initialSection) }
    var viewAllMode by rememberSaveable { mutableStateOf(false) }

    // Xử lý nút Back của Android: nếu đang trong sub-screen hoặc view all thì quay lại Hub Overview
    BackHandler(enabled = selectedSection != null || viewAllMode) {
        selectedSection = null
        viewAllMode = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            viewAllMode -> stringResource(R.string.settings_category_all)
                            selectedSection != null -> stringResource(selectedSection!!.titleRes)
                            else -> stringResource(R.string.settings_title)
                        }
                    )
                },
                navigationIcon = {
                    if (selectedSection != null || viewAllMode) {
                        IconButton(onClick = {
                            selectedSection = null
                            viewAllMode = false
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back_to_categories)
                            )
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                },
                actions = {
                    if (selectedSection == null) {
                        if (viewAllMode) {
                            IconButton(onClick = { viewAllMode = false }) {
                                Icon(
                                    Icons.Filled.GridView,
                                    contentDescription = stringResource(R.string.settings_view_categories)
                                )
                            }
                        } else {
                            IconButton(onClick = { viewAllMode = true }) {
                                Icon(
                                    Icons.Filled.ViewAgenda,
                                    contentDescription = stringResource(R.string.settings_view_all)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = Triple(selectedSection, viewAllMode, initialSection),
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "SettingsSectionTransition"
        ) { (section, isViewAll, _) ->
            when {
                // 1. Chế độ xem toàn bộ (View All Mode)
                isViewAll -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LearningSettingsSection(
                            prefs = prefs,
                            onSetLevel = onSetLevel,
                            onSetAdaptiveLevelRecommendation = onSetAdaptiveLevelRecommendation,
                            onSetDailyGoal = onSetDailyGoal,
                            onSetDrillSentenceLength = onSetDrillSentenceLength,
                            onSetDrillCategory = onSetDrillCategory,
                            onSetStorytellingStyle = onSetStorytellingStyle,
                            onSetStoryDuration = onSetStoryDuration,
                            onSetMultiVoiceStorytelling = onSetMultiVoiceStorytelling,
                            onSetBetterPhrasing = onSetBetterPhrasing
                        )

                        VoiceSettingsSection(
                            prefs = prefs,
                            onSetAiVolume = onSetAiVolume,
                            onSetVoice = onSetVoice,
                            onSetRandomVoice = onSetRandomVoice,
                            onSetPronunciationStrictness = onSetPronunciationStrictness,
                            onSetAllowVietnameseHelp = onSetAllowVietnameseHelp,
                            onSetAllowBargeIn = onSetAllowBargeIn
                        )

                        DrivingSettingsSection(
                            prefs = prefs,
                            onSetAutoStartOnCarConnect = onSetAutoStartOnCarConnect,
                            onSetAutoPauseWhenUnfocused = onSetAutoPauseWhenUnfocused,
                            onSetScreenAwakeMode = onSetScreenAwakeMode,
                            onSetShowTranslationSubtitle = onSetShowTranslationSubtitle
                        )

                        extraGroups()

                        DataBackupSettingsSection(
                            onExportBackup = onExportBackup,
                            onImportBackup = onImportBackup,
                            onExportAnki = onExportAnki
                        )

                        AzureSettingsSection(
                            prefs = prefs,
                            azureTest = azureTest,
                            onSetAzureEnabled = onSetAzureEnabled,
                            onTestAzure = onTestAzure,
                            onSaveAndTestAzure = onSaveAndTestAzure
                        )

                        AboutSettingsSection(
                            prefs = prefs,
                            onSetAppLanguage = onSetAppLanguage,
                            onOpenPrivacy = onOpenPrivacy,
                            onOpenAbout = onOpenAbout
                        )
                    }
                }

                // 2. Màn hình con của Danh mục đã chọn (Sub-screen Detail)
                section != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        // Quick Category Selector Bar: Giúp đổi nhóm cài đặt tức thì
                        QuickCategorySelector(
                            currentSection = section,
                            onSelectSection = { selectedSection = it }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (section) {
                                SettingsSection.LEARNING -> {
                                    LearningSettingsSection(
                                        prefs = prefs,
                                        onSetLevel = onSetLevel,
                                        onSetAdaptiveLevelRecommendation = onSetAdaptiveLevelRecommendation,
                                        onSetDailyGoal = onSetDailyGoal,
                                        onSetDrillSentenceLength = onSetDrillSentenceLength,
                                        onSetDrillCategory = onSetDrillCategory,
                                        onSetStorytellingStyle = onSetStorytellingStyle,
                                        onSetStoryDuration = onSetStoryDuration,
                                        onSetMultiVoiceStorytelling = onSetMultiVoiceStorytelling,
                                        onSetBetterPhrasing = onSetBetterPhrasing
                                    )
                                }
                                SettingsSection.VOICE -> {
                                    VoiceSettingsSection(
                                        prefs = prefs,
                                        onSetAiVolume = onSetAiVolume,
                                        onSetVoice = onSetVoice,
                                        onSetRandomVoice = onSetRandomVoice,
                                        onSetPronunciationStrictness = onSetPronunciationStrictness,
                                        onSetAllowVietnameseHelp = onSetAllowVietnameseHelp,
                                        onSetAllowBargeIn = onSetAllowBargeIn
                                    )
                                }
                                SettingsSection.DRIVING -> {
                                    DrivingSettingsSection(
                                        prefs = prefs,
                                        onSetAutoStartOnCarConnect = onSetAutoStartOnCarConnect,
                                        onSetAutoPauseWhenUnfocused = onSetAutoPauseWhenUnfocused,
                                        onSetScreenAwakeMode = onSetScreenAwakeMode,
                                        onSetShowTranslationSubtitle = onSetShowTranslationSubtitle
                                    )
                                }
                                SettingsSection.MEMORY -> {
                                    extraGroups()
                                }
                                SettingsSection.DATA -> {
                                    DataBackupSettingsSection(
                                        onExportBackup = onExportBackup,
                                        onImportBackup = onImportBackup,
                                        onExportAnki = onExportAnki
                                    )
                                }
                                SettingsSection.AZURE -> {
                                    AzureSettingsSection(
                                        prefs = prefs,
                                        azureTest = azureTest,
                                        onSetAzureEnabled = onSetAzureEnabled,
                                        onTestAzure = onTestAzure,
                                        onSaveAndTestAzure = onSaveAndTestAzure
                                    )
                                }
                                SettingsSection.ABOUT -> {
                                    AboutSettingsSection(
                                        prefs = prefs,
                                        onSetAppLanguage = onSetAppLanguage,
                                        onOpenPrivacy = onOpenPrivacy,
                                        onOpenAbout = onOpenAbout
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Màn hình Cài đặt chính: Bảng điều khiển Hub Danh mục (Settings Dashboard)
                else -> {
                    SettingsDashboard(
                        prefs = prefs,
                        onSelectSection = { selectedSection = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Thanh chuyển đổi nhanh danh mục cài đặt nằm ở đầu màn hình con.
 */
@Composable
private fun QuickCategorySelector(
    currentSection: SettingsSection,
    onSelectSection: (SettingsSection) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsSection.entries.forEach { section ->
                val isSelected = section == currentSection
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectSection(section) },
                    leadingIcon = {
                        Icon(
                            section.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(stringResource(section.titleRes)) }
                )
            }
        }
    }
}
