package com.speakdrive.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.ai.model.LearnerSettings

/** Settings group: AI memory of the learner, daily practice reminder and streak freeze. */
@Composable
fun MemorySettingsGroup(
    onOpenMemory: () -> Unit,
    viewModel: LearnerMemoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshNotificationState()
        onPauseOrDispose { }
    }
    MemorySettingsContent(
        state = state,
        onOpenMemory = onOpenMemory,
        onSetRememberLearner = viewModel::setRememberLearner,
        onSetReminderEnabled = viewModel::setReminderEnabled,
        onSetReminderTime = viewModel::setReminderTime,
        onSetStreakFreeze = viewModel::setStreakFreeze,
        onNotificationStateChanged = viewModel::refreshNotificationState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySettingsContent(
    state: LearnerMemoryUiState,
    onOpenMemory: () -> Unit,
    onSetRememberLearner: (Boolean) -> Unit,
    onSetReminderEnabled: (Boolean) -> Unit,
    onSetReminderTime: (Int) -> Unit,
    onSetStreakFreeze: (Boolean) -> Unit,
    onNotificationStateChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onNotificationStateChanged()
    }
    val askForNotifications = {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsRuntimePermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    SettingsGroupCard(title = stringResource(R.string.settings_group_memory), icon = Icons.Filled.Psychology) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_remember_learner)) },
            supportingContent = { Text(stringResource(R.string.settings_remember_learner_desc)) },
            trailingContent = { Switch(checked = state.rememberLearner, onCheckedChange = onSetRememberLearner) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_open_memory)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMemory)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_reminder)) },
            supportingContent = {
                Text(
                    stringResource(R.string.settings_reminder_desc) + "\n" +
                        if (state.reminderMinute == LearnerSettings.REMINDER_AUTO) {
                            stringResource(R.string.settings_reminder_time_auto, formatMinuteOfDay(state.autoReminderMinute))
                        } else {
                            stringResource(R.string.settings_reminder_time_fixed, formatMinuteOfDay(state.reminderMinute))
                        }
                )
            },
            trailingContent = {
                Switch(
                    checked = state.reminderEnabled,
                    onCheckedChange = { enabled ->
                        onSetReminderEnabled(enabled)
                        if (enabled && !state.canNotify) askForNotifications()
                    }
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        if (state.reminderEnabled) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                OutlinedButton(onClick = { showTimePicker = true }) {
                    Text(stringResource(R.string.settings_reminder_change_time))
                }
                if (state.reminderMinute != LearnerSettings.REMINDER_AUTO) {
                    TextButton(onClick = { onSetReminderTime(LearnerSettings.REMINDER_AUTO) }) {
                        Text(stringResource(R.string.settings_reminder_use_auto))
                    }
                }
            }
            if (!state.canNotify) {
                Text(
                    stringResource(R.string.settings_reminder_permission_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                OutlinedButton(onClick = askForNotifications, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.settings_reminder_permission))
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_streak_freeze)) },
            supportingContent = { Text(stringResource(R.string.settings_streak_freeze_desc)) },
            trailingContent = { Switch(checked = state.streakFreezeEnabled, onCheckedChange = onSetStreakFreeze) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        Text(
            stringResource(R.string.settings_memory_voice_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }

    if (showTimePicker) {
        val initial = if (state.reminderMinute == LearnerSettings.REMINDER_AUTO) state.autoReminderMinute else state.reminderMinute
        val pickerState = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onSetReminderTime(pickerState.hour * 60 + pickerState.minute)
                    showTimePicker = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(android.R.string.cancel)) }
            },
            text = { TimePicker(state = pickerState) }
        )
    }
}
