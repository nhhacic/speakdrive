package com.speakdrive.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VoiceOverOff
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.ai.model.ActiveLesson
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.SessionMode
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ui.components.ChatBubble
import com.speakdrive.ui.components.DrillCard
import com.speakdrive.ui.components.VoiceMicButton

@Composable
fun ConversationScreen(
    mediaId: String?,
    onBack: () -> Unit,
    onLessonSaved: (sessionId: String) -> Unit,
    onLessonDiscarded: () -> Unit,
    viewModel: ConversationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var permissionDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.start(mediaId)
    }

    LaunchedEffect(mediaId) {
        if (mediaId == null) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.start(mediaId) else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ConversationEvent.Saved -> onLessonSaved(event.sessionId)
                ConversationEvent.Discarded -> onLessonDiscarded()
            }
        }
    }

    val isInLesson = state.state.isInLesson
    val isCarConnected = state.isCarConnected
    // Context-aware screen awake behavior:
    // When connected to Android Auto: let phone screen turn off automatically according to device timeout.
    // When not connected to Android Auto: keep phone screen awake continuously during practice.
    val shouldKeepScreenOn = isInLesson && !isCarConnected

    val view = LocalView.current
    DisposableEffect(shouldKeepScreenOn) {
        view.keepScreenOn = shouldKeepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    ConversationContent(
        state = state,
        permissionDenied = permissionDenied,
        onBack = onBack,
        onEnd = viewModel::end,
        onToggle = viewModel::togglePause,
        onRetry = { viewModel.retry(mediaId) },
        onNextStory = viewModel::nextStory,
        onReplayStory = viewModel::replayStory,
        onToggleBargeIn = {
            val nextState = !state.isBargeInEnabled
            viewModel.toggleBargeIn()
            val toastText = if (nextState) {
                context.getString(R.string.convo_barge_in_on_toast)
            } else {
                context.getString(R.string.convo_barge_in_off_toast)
            }
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        },
        onSetAiVolume = viewModel::setAiVolume,
        onUserInteraction = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationContent(
    state: ConversationUiState,
    permissionDenied: Boolean,
    onBack: () -> Unit,
    onEnd: () -> Unit,
    onToggle: () -> Unit,
    onRetry: () -> Unit,
    onNextStory: () -> Unit = {},
    onReplayStory: () -> Unit = {},
    onToggleBargeIn: () -> Unit = {},
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSetAiVolume: (Int) -> Unit = {},
    onUserInteraction: () -> Unit = {}
) {
    val isVi = LocalConfiguration.current.locales[0].language == "vi"
    val lesson = state.lesson
    var showVolumeDialog by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial)
                    onUserInteraction()
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            lesson?.let { "${it.topic.emoji} ${it.getTitle(isVi)}" } ?: stringResource(R.string.convo_title_default),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (lesson != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        lesson.level.getLabel(isVi),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    state.elapsed,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
                },
                actions = {
                    if (state.state.isInLesson) {
                        IconButton(
                            onClick = { showVolumeDialog = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isVi) "Âm lượng AI" else "AI Volume",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalIconToggleButton(
                            checked = state.isBargeInEnabled,
                            onCheckedChange = { onToggleBargeIn() },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = if (state.isBargeInEnabled) Icons.Filled.RecordVoiceOver else Icons.Filled.VoiceOverOff,
                                contentDescription = stringResource(
                                    if (state.isBargeInEnabled) R.string.convo_barge_in_active
                                    else R.string.convo_barge_in_inactive
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onEnd,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(stringResource(R.string.convo_btn_end), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                permissionDenied -> MessageCard(
                    title = stringResource(R.string.convo_permission_title),
                    message = stringResource(R.string.convo_permission_required),
                    primaryLabel = stringResource(R.string.convo_btn_grant_permission),
                    onPrimary = onRequestPermission,
                    secondaryLabel = stringResource(R.string.convo_btn_open_settings),
                    onSecondary = onOpenAppSettings
                )
                state.error != null && !state.state.isInLesson -> MessageCard(
                    title = if (state.error is EngineError.AiNotConfigured) stringResource(R.string.convo_error_ai_not_configured) else stringResource(R.string.convo_error_cannot_start),
                    message = when (val err = state.error) {
                        is EngineError.ConnectionFailed -> {
                            if (!err.detail.isNullOrBlank()) "${err.getMessage(isVi)}\n\n${stringResource(R.string.convo_tech_detail)}: ${err.detail}"
                            else err.getMessage(isVi)
                        }
                        else -> err.getMessage(isVi)
                    },
                    primaryLabel = stringResource(R.string.convo_btn_retry),
                    onPrimary = onRetry,
                    secondaryLabel = if (state.error == EngineError.MissingMicPermission) stringResource(R.string.convo_btn_grant_permission) else null,
                    onSecondary = onRequestPermission
                )
                lesson == null && !state.state.isInLesson && state.state != ConversationState.ENDING -> MessageCard(
                    title = stringResource(R.string.convo_empty_lesson_title),
                    message = stringResource(R.string.convo_empty_lesson_desc),
                    primaryLabel = stringResource(R.string.convo_btn_home),
                    onPrimary = onBack
                )
            }

            state.drill?.let { drill ->
                val attempt = drill.lastAttempt
                DrillCard(
                    target = drill.target,
                    attemptWords = attempt?.words,
                    attemptPassed = attempt?.passed,
                    attemptNumber = attempt?.attemptNumber,
                    accuracyPercent = attempt?.accuracyPercent,
                    problemNote = attempt?.modelNotes,
                    azureSummary = attempt?.azure?.let {
                        if (isVi) "Azure: ${it.pronunciationScore}/100 • chính xác ${it.accuracyScore} • trôi chảy ${it.fluencyScore} • đầy đủ ${it.completenessScore}"
                        else "Azure: ${it.pronunciationScore}/100 • accuracy ${it.accuracyScore} • fluency ${it.fluencyScore} • completeness ${it.completenessScore}"
                    },
                    azureWeakSounds = attempt?.azure?.describeProblems()?.joinToString(),
                    azureWarning = attempt?.azureError,
                    passedCount = drill.passedSentences,
                    sentenceCount = drill.sentences,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Transcript(state, modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Glanceable Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        rememberStatusText(state.state, state.activeSpeaker, state.lesson, state.isAiThinking),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (state.state.isInLesson) {
                    if (lesson?.mode == SessionMode.STORY_LISTENING) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onReplayStory,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.convo_btn_replay_story), style = MaterialTheme.typography.labelLarge)
                            }
                            Button(
                                onClick = onNextStory,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.convo_btn_next_story), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }

                    VoiceMicButton(state = state.micState, onClick = onToggle)

                    FilterChip(
                        selected = state.isBargeInEnabled,
                        onClick = onToggleBargeIn,
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.isBargeInEnabled) Icons.Filled.RecordVoiceOver else Icons.Filled.VoiceOverOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(
                                    if (state.isBargeInEnabled) R.string.convo_barge_in_active
                                    else R.string.convo_barge_in_inactive
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.isBargeInEnabled,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )

                    val tipText = if (lesson?.mode == SessionMode.STORY_LISTENING) {
                        stringResource(R.string.convo_tip_story)
                    } else {
                        stringResource(R.string.convo_tip_driving)
                    }
                    Text(
                        tipText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showVolumeDialog) {
        AlertDialog(
            onDismissRequest = { showVolumeDialog = false },
            title = {
                Text(
                    text = if (isVi) "Âm lượng giọng nói AI" else "AI Voice Volume",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isVi) "Mức âm lượng:" else "Volume level:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${state.aiVolume}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.aiVolume.toFloat(),
                        onValueChange = { onSetAiVolume(it.roundToInt().coerceIn(10, 100)) },
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = if (isVi) "Mẹo giọng nói: Bạn có thể nói \"nói nhỏ lại\", \"giảm âm lượng\", \"nói to lên\" hoặc \"volume 50%\" bất cứ lúc nào."
                        else "Voice tip: You can say \"speak softer\", \"lower volume\", \"speak louder\" or \"volume 50%\" at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showVolumeDialog = false }) {
                    Text(if (isVi) "Đóng" else "Close")
                }
            }
        )
    }
}

@Composable
private fun rememberStatusText(
    state: ConversationState,
    speaker: Speaker?,
    lesson: ActiveLesson?,
    isThinking: Boolean = false
): String = when (state) {
    ConversationState.IDLE -> stringResource(R.string.convo_status_idle)
    ConversationState.CONNECTING -> if (lesson?.mode == SessionMode.STORY_LISTENING) stringResource(R.string.convo_status_selecting_story) else stringResource(R.string.convo_connecting)
    ConversationState.ACTIVE -> when {
        isThinking -> if (lesson?.mode == SessionMode.STORY_LISTENING) {
            stringResource(R.string.convo_status_ai_story_thinking)
        } else {
            stringResource(R.string.convo_status_ai_thinking)
        }
        speaker == Speaker.AI -> if (lesson?.mode == SessionMode.STORY_LISTENING) {
            stringResource(R.string.convo_status_ai_storytelling)
        } else {
            stringResource(R.string.convo_speaking)
        }
        speaker == Speaker.USER -> stringResource(R.string.convo_listening)
        else -> if (lesson?.mode == SessionMode.STORY_LISTENING) {
            stringResource(R.string.convo_status_story_hint)
        } else {
            stringResource(R.string.convo_status_your_turn)
        }
    }
    ConversationState.PAUSED -> stringResource(R.string.convo_paused)
    ConversationState.RECONNECTING -> stringResource(R.string.convo_status_reconnecting)
    ConversationState.WAITING_FOR_NETWORK -> stringResource(R.string.convo_status_waiting_network)
    ConversationState.ENDING -> stringResource(R.string.convo_status_ending)
    ConversationState.ENDED -> stringResource(R.string.convo_status_ended)
    ConversationState.ERROR -> stringResource(R.string.convo_status_error)
}

@Composable
private fun Transcript(state: ConversationUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val lastText = state.transcript.lastOrNull()?.text
    LaunchedEffect(state.transcript.size, lastText) {
        if (state.transcript.isNotEmpty()) {
            listState.animateScrollToItem(state.transcript.lastIndex)
        }
    }
    if (state.drill == null && state.transcript.isEmpty() && state.state.isInLesson) {
        val isStory = state.lesson?.mode == SessionMode.STORY_LISTENING
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    if (isStory) stringResource(R.string.convo_empty_transcript_story)
                    else stringResource(R.string.convo_empty_transcript_talk),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 24.sp
                )
            }
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.transcript, key = { it.id }) { turn ->
            ChatBubble(text = turn.text, isUser = turn.speaker == Speaker.USER)
        }
    }
}

@Composable
private fun MessageCard(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(primaryLabel, fontWeight = FontWeight.SemiBold)
                }
                if (secondaryLabel != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(secondaryLabel)
                    }
                }
            }
        }
    }
}

