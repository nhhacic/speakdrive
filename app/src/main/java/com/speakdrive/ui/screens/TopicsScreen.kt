package com.speakdrive.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.ai.TopicManager
import com.speakdrive.ai.model.Topic
import com.speakdrive.ai.model.TopicCategory

/** Filter chips of the topic list; `null` category means all topics. */
internal enum class TopicFilter(val labelRes: Int, val category: TopicCategory?) {
    ALL(R.string.home_filter_all, null),
    DAILY(R.string.home_filter_daily, TopicCategory.DAILY),
    WORK(R.string.home_filter_work, TopicCategory.WORK),
    TRAVEL(R.string.home_filter_travel, TopicCategory.TRAVEL)
}

/** Topics in [filter] whose names or voice keywords contain [query] (accents and case ignored). */
internal fun filterTopics(topics: List<TopicProgressUi>, filter: TopicFilter, query: String): List<TopicProgressUi> {
    val q = TopicManager.normalize(query.trim())
    return topics.filter { item ->
        val topic = item.topic
        (filter.category == null || topic.category == filter.category) &&
            (q.isBlank() || topic.searchText().contains(q))
    }
}

private fun Topic.searchText(): String =
    TopicManager.normalize((listOf(titleVi, titleEn) + keywords).joinToString(" "))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicsScreen(
    onBack: () -> Unit,
    onStartLesson: (mediaId: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isVi = LocalConfiguration.current.locales[0].language == "vi"
    var sheetTopic by remember { mutableStateOf<Topic?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.topics_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        TopicsContent(
            topics = state.topics,
            isVi = isVi,
            onTopicClick = { sheetTopic = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
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
}

/** Search box, category chips and every topic; also the right pane of the home screen on wide screens. */
@Composable
internal fun TopicsContent(
    topics: List<TopicProgressUi>,
    isVi: Boolean,
    onTopicClick: (Topic) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(TopicFilter.ALL) }
    val shown = remember(topics, filter, query) { filterTopics(topics, filter, query) }

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "topics_search") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.topics_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.topics_search_clear))
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TopicFilter.entries.forEach { option ->
                        val selected = option == filter
                        FilterChip(
                            selected = selected,
                            onClick = { filter = option },
                            label = {
                                Text(
                                    text = stringResource(option.labelRes),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item(key = "topics_empty") {
                Text(
                    text = stringResource(R.string.topics_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }
        items(shown, key = { "topic_${it.topic.id}" }) { item ->
            TopicRow(item = item, isVi = isVi, onClick = { onTopicClick(item.topic) })
        }
    }
}
