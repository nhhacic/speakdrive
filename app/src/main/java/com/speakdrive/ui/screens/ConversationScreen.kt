package com.speakdrive.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.ai.model.ConversationState
import com.speakdrive.ai.model.EngineError
import com.speakdrive.ai.model.Speaker
import com.speakdrive.ui.components.ChatBubble
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

    // Practising on the phone: keep the screen awake while a lesson is running.
    val view = LocalView.current
    DisposableEffect(state.state.isInLesson) {
        view.keepScreenOn = state.state.isInLesson
        onDispose { view.keepScreenOn = false }
    }

    ConversationContent(
        state = state,
        permissionDenied = permissionDenied,
        onBack = onBack,
        onEnd = viewModel::end,
        onToggle = viewModel::togglePause,
        onRetry = { viewModel.retry(mediaId) },
        onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        onOpenAppSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            )
        }
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
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    val lesson = state.lesson
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            lesson?.let { "${it.topic.emoji} ${it.titleVi}" } ?: "Hội thoại",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (lesson != null) {
                            Text("${lesson.level.displayName} • ${state.elapsed}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại") }
                },
                actions = {
                    if (state.state.isInLesson) TextButton(onClick = onEnd) { Text("Kết thúc") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                permissionDenied -> MessageCard(
                    title = "Cần quyền micro",
                    message = "SpeakDrive cần micro để nghe bạn nói. Âm thanh chỉ được gửi tới AI trong lúc học và không được lưu lại.",
                    primaryLabel = "Cấp quyền",
                    onPrimary = onRequestPermission,
                    secondaryLabel = "Mở cài đặt",
                    onSecondary = onOpenAppSettings
                )
                state.error != null && !state.state.isInLesson -> MessageCard(
                    title = "Không bắt đầu được bài học",
                    message = state.error.messageVi,
                    primaryLabel = "Thử lại",
                    onPrimary = onRetry,
                    secondaryLabel = if (state.error == EngineError.MissingMicPermission) "Cấp quyền" else null,
                    onSecondary = onRequestPermission
                )
                lesson == null && !state.state.isInLesson && state.state != ConversationState.ENDING -> MessageCard(
                    title = "Chưa có bài học nào",
                    message = "Hãy chọn một chủ đề ở trang chủ để bắt đầu.",
                    primaryLabel = "Về trang chủ",
                    onPrimary = onBack
                )
            }

            Transcript(state, modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    state.statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                if (state.state.isInLesson) {
                    VoiceMicButton(state = state.micState, onClick = onToggle)
                    Text(
                        "Mẹo: nói \"end the lesson\" để kết thúc mà không cần chạm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Transcript(state: ConversationUiState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val lastText = state.transcript.lastOrNull()?.text
    LaunchedEffect(state.transcript.size, lastText) {
        if (state.transcript.isNotEmpty()) listState.animateScrollToItem(state.transcript.lastIndex)
    }
    if (state.transcript.isEmpty() && state.state.isInLesson) {
        Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "AI sẽ chào bạn trước. Cứ trả lời tự nhiên bằng tiếng Anh — không cần chạm vào màn hình.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
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
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPrimary) { Text(primaryLabel) }
                if (secondaryLabel != null) OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}
