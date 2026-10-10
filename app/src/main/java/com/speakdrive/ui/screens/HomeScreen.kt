package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import android.widget.Toast
import com.speakdrive.ai.diagnostics.DiagnosticsLogExport
import com.speakdrive.ai.model.Topic
import com.speakdrive.ai.session.VoiceCommandParser
import com.speakdrive.auto.MediaIds
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Width from which the home screen shows the full topic list in a second pane (unfolded Fold, tablets). */
private val TwoPaneMinWidth = 600.dp

@Composable
fun HomeScreen(
    onStartLesson: (mediaId: String) -> Unit,
    onOpenCurrentLesson: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenAllTopics: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (voice, toggleVoice) = rememberHomeVoiceCommand { transcript ->
        if (VoiceCommandParser.parseSaveDiagnosticsLogCommand(transcript)) {
            // "Lưu nhật ký": same as the button on the About screen.
            scope.launch {
                val place = withContext(Dispatchers.IO) { DiagnosticsLogExport.saveToDownloads(context) }
                val message = if (place != null) context.getString(R.string.about_diagnostics_saved, place)
                else context.getString(R.string.about_diagnostics_failed)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            return@rememberHomeVoiceCommand
        }
        val mediaId = viewModel.resolveVoiceCommand(transcript)
        if (mediaId == MediaIds.RESUME && state.currentLesson != null) onOpenCurrentLesson() else onStartLesson(mediaId)
    }
    HomeContent(
        state = state,
        onStartLesson = onStartLesson,
        onOpenCurrentLesson = onOpenCurrentLesson,
        onOpenProgress = onOpenProgress,
        onOpenAllTopics = onOpenAllTopics,
        onDeleteCustomScenario = viewModel::deleteCustomScenario,
        voice = voice,
        onToggleVoice = toggleVoice
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onStartLesson: (mediaId: String) -> Unit,
    onOpenCurrentLesson: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenAllTopics: () -> Unit = {},
    onDeleteCustomScenario: (String) -> Unit = {},
    voice: HomeVoiceUi = HomeVoiceUi.Idle,
    onToggleVoice: () -> Unit = {}
) {
    var sheetTopic by remember { mutableStateOf<Topic?>(null) }
    var showStorySheet by remember { mutableStateOf(false) }
    var showRoleplaySheet by remember { mutableStateOf(false) }
    val isVi = LocalConfiguration.current.locales[0].language == "vi"

    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (currentHour) {
        in 5..11 -> stringResource(R.string.home_greeting_morning_emoji)
        in 12..17 -> stringResource(R.string.home_greeting_afternoon_emoji)
        else -> stringResource(R.string.home_greeting_evening_emoji)
    }
    val suggestedTopics = remember(state.topics, state.lastTopic) {
        recommendTopics(state.topics, state.lastTopic?.id)
    }

    // Everything above the topic list: what to do now, today's progress, review, practice modes.
    val mainBlocks: LazyListScope.() -> Unit = {
        item(key = "header") {
            HomeHeader(greeting, state.stats.streakDays, state.stats.streakFreezes)
        }
        if (state.isCarConnected) {
            item(key = "car_banner") { CarConnectedBanner() }
        }
        item(key = "resume_card") {
            HomeResumeCard(
                state = state,
                isVi = isVi,
                voice = voice,
                onResume = {
                    if (state.currentLesson != null) onOpenCurrentLesson() else onStartLesson(MediaIds.RESUME)
                },
                onRandom = { onStartLesson(MediaIds.RANDOM) },
                onToggleVoice = onToggleVoice
            )
        }
        item(key = "today") { HomeTodayCard(state, onOpenProgress) }
        if (state.stats.dueWordCount > 0 || state.stats.dueMistakeCount > 0) {
            item(key = "review_queue") {
                HomeReviewQueue(
                    dueWordCount = state.stats.dueWordCount,
                    dueMistakeCount = state.stats.dueMistakeCount,
                    onStartReview = { onStartLesson(MediaIds.REVIEW) },
                    onStartMistakes = { onStartLesson(MediaIds.MISTAKES) }
                )
            }
        }
        item(key = "practice_modes") {
            HomePracticeModes(
                state = state,
                onOpenStories = { showStorySheet = true },
                onPronunciation = { onStartLesson(MediaIds.pronunciation(state.lastTopic?.id)) },
                onIelts = { onStartLesson(MediaIds.IELTS) },
                onOpenRoleplay = { showRoleplaySheet = true }
            )
        }
    }

    // The app-level Scaffold in MainActivity already pads for the status bar.
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (maxWidth >= TwoPaneMinWidth) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(start = 20.dp, end = 12.dp, bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = mainBlocks
                    )
                    TopicsContent(
                        topics = state.topics,
                        isVi = isVi,
                        onTopicClick = { sheetTopic = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 28.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    mainBlocks()
                    item(key = "topics_header") {
                        HomeTopicSuggestionsHeader(totalCount = state.topics.size, onOpenAllTopics = onOpenAllTopics)
                    }
                    items(suggestedTopics, key = { "suggested_${it.topic.id}" }) { item ->
                        TopicRow(item = item, isVi = isVi, onClick = { sheetTopic = item.topic })
                    }
                }
            }
        }
    }

    sheetTopic?.let { topic ->
        ModalBottomSheet(
            onDismissRequest = { sheetTopic = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            TopicSheet(
                topic = topic,
                isVi = isVi,
                onPick = { mediaId ->
                    sheetTopic = null
                    onStartLesson(mediaId)
                }
            )
        }
    }

    if (showStorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showStorySheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            StorySheet(
                storyTopics = state.storyTopics,
                recentStories = state.recentStories,
                unfinishedStory = state.unfinishedStory,
                isVi = isVi,
                onPick = { mediaId ->
                    showStorySheet = false
                    onStartLesson(mediaId)
                }
            )
        }
    }

    if (showRoleplaySheet) {
        ModalBottomSheet(
            onDismissRequest = { showRoleplaySheet = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            RoleplaySheet(
                lastTopic = state.lastTopic,
                customScenarios = state.customScenarios,
                isVi = isVi,
                onPick = { mediaId ->
                    showRoleplaySheet = false
                    onStartLesson(mediaId)
                },
                onDeleteCustomScenario = onDeleteCustomScenario,
                onOpenAllTopics = {
                    showRoleplaySheet = false
                    onOpenAllTopics()
                }
            )
        }
    }
}
