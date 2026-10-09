package com.speakdrive.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.speakdrive.BuildConfig
import com.speakdrive.R
import com.speakdrive.ai.model.AppLanguage
import com.speakdrive.data.repository.UserPreferences

/**
 * Hub Overview của màn hình Cài đặt:
 * Hiển thị tóm tắt cấu hình của người học cùng 6 thẻ danh mục logic.
 */
@Composable
fun SettingsDashboard(
    prefs: UserPreferences,
    onSelectSection: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Thẻ Tóm tắt Cấu hình Người học (Learner Profile Summary Card)
        LearnerProfileSummaryCard(prefs = prefs, isVi = isVi)

        // 2. Danh sách 6 Nhóm Danh mục (Category Cards)
        SettingsSection.entries.forEach { section ->
            SettingsCategoryCard(
                section = section,
                summary = getSectionSummary(section, prefs, isVi),
                onClick = { onSelectSection(section) }
            )
        }

        // 3. Footer phiên bản
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
}

/**
 * Thẻ hiển thị tóm tắt hồ sơ và các chỉ số cấu hình trọng yếu.
 */
@Composable
fun LearnerProfileSummaryCard(
    prefs: UserPreferences,
    isVi: Boolean
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    stringResource(R.string.settings_dashboard_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill(
                    icon = Icons.Filled.School,
                    label = "${prefs.learner.level.displayName} (${prefs.learner.level.cefr})",
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    icon = Icons.Filled.RecordVoiceOver,
                    label = if (prefs.learner.randomVoice) stringResource(R.string.settings_voice_random) else prefs.learner.voiceId,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryPill(
                    icon = Icons.Filled.Timer,
                    label = stringResource(R.string.settings_goal_minutes_format, prefs.dailyGoalMinutes),
                    modifier = Modifier.weight(1f)
                )
                SummaryPill(
                    icon = Icons.Filled.DirectionsCar,
                    label = if (prefs.learner.autoStartOnCarConnect) {
                        if (isVi) "Tự nối xe: Bật" else "Car Auto: On"
                    } else {
                        if (isVi) "Tự nối xe: Tắt" else "Car Auto: Off"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Thẻ Danh mục hiển thị trên Dashboard.
 */
@Composable
fun SettingsCategoryCard(
    section: SettingsSection,
    summary: String,
    onClick: () -> Unit
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
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    section.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    stringResource(section.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.settings_open_memory),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Tính toán dòng tóm tắt trạng thái hiển thị trên Thẻ Danh mục.
 */
@Composable
private fun getSectionSummary(
    section: SettingsSection,
    prefs: UserPreferences,
    isVi: Boolean
): String {
    return when (section) {
        SettingsSection.LEARNING -> {
            val levelStr = "${prefs.learner.level.getLabel(isVi)} (${prefs.learner.level.cefr})"
            val goalStr = stringResource(R.string.settings_goal_minutes_format, prefs.dailyGoalMinutes)
            val drill = prefs.learner.drillSentenceLength.getLabel(isVi)
            "$levelStr • $goalStr • $drill"
        }
        SettingsSection.VOICE -> {
            val voice = if (prefs.learner.randomVoice) {
                stringResource(R.string.settings_voice_random)
            } else {
                "${prefs.learner.voiceId}"
            }
            val strictness = prefs.learner.pronunciationStrictness.getLabel(isVi)
            "$voice • ${prefs.learner.aiVolume}% • $strictness"
        }
        SettingsSection.DRIVING -> {
            val car = if (prefs.learner.autoStartOnCarConnect) {
                if (isVi) "Tự nối xe: Bật" else "Car Auto: On"
            } else {
                if (isVi) "Tự nối xe: Tắt" else "Car Auto: Off"
            }
            val screen = prefs.learner.screenAwakeMode.getLabel(isVi)
            "$car • $screen"
        }
        SettingsSection.MEMORY -> {
            val memory = if (prefs.learner.rememberLearner) {
                if (isVi) "Nhớ học viên: Bật" else "Memory: On"
            } else {
                if (isVi) "Nhớ học viên: Tắt" else "Memory: Off"
            }
            if (isVi) "$memory • Nhắc nhở & Chuỗi học" else "$memory • Reminders & Streaks"
        }
        SettingsSection.DATA -> {
            if (isVi) "Sao lưu JSON, khôi phục & xuất Anki" else "JSON Backup, restore & Anki export"
        }
        SettingsSection.AZURE -> {
            if (prefs.learner.azureEnabled) {
                if (isVi) "Chấm phát âm bằng Azure: Bật" else "Azure Grading: Enabled"
            } else {
                if (isVi) "Chấm phát âm bằng Azure: Tắt" else "Azure Grading: Off"
            }
        }
        SettingsSection.ABOUT -> {
            val langName = if (prefs.appLanguage == AppLanguage.SYSTEM) {
                stringResource(R.string.settings_language_system)
            } else {
                prefs.appLanguage.nativeName
            }
            "${prefs.appLanguage.flagEmoji} $langName • ${stringResource(R.string.settings_about)}"
        }
    }
}
