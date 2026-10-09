package com.speakdrive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.speakdrive.R
import com.speakdrive.auto.MediaIds
import com.speakdrive.data.local.entity.LearnerFactEntity
import com.speakdrive.data.local.entity.MistakeEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the AI remembers about the learner, with a way to delete any of it. */
@Composable
fun LearnerMemoryScreen(
    onBack: () -> Unit,
    onStartReview: (mediaId: String) -> Unit,
    viewModel: LearnerMemoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LearnerMemoryContent(
        state = state,
        onBack = onBack,
        onStartReview = { onStartReview(MediaIds.MISTAKES) },
        onAddFact = viewModel::addFact,
        onDeleteFact = viewModel::deleteFact,
        onForgetAllFacts = viewModel::forgetAllFacts,
        onDeleteMistake = viewModel::deleteMistake
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnerMemoryContent(
    state: LearnerMemoryUiState,
    onBack: () -> Unit,
    onStartReview: () -> Unit,
    onAddFact: (String) -> Unit = {},
    onDeleteFact: (Long) -> Unit,
    onForgetAllFacts: () -> Unit,
    onDeleteMistake: (Long) -> Unit,
    now: Long = System.currentTimeMillis()
) {
    var showAddFactDialog by remember { mutableStateOf(false) }
    var newFactText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.memory_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.memory_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!state.rememberLearner) {
                item {
                    Text(
                        stringResource(R.string.memory_off_banner),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.memory_facts_title),
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { showAddFactDialog = true }) {
                                Text("+ " + stringResource(R.string.memory_add_fact))
                            }
                            if (state.facts.isNotEmpty()) {
                                TextButton(onClick = onForgetAllFacts) {
                                    Text(stringResource(R.string.memory_forget_all))
                                }
                            }
                        }
                    }
                )
            }
            if (state.facts.isEmpty()) {
                item { EmptyText(stringResource(R.string.memory_facts_empty)) }
            } else {
                items(state.facts, key = { "fact_${it.id}" }) { fact -> FactRow(fact, onDelete = { onDeleteFact(fact.id) }) }
            }

            item { SectionHeader(title = stringResource(R.string.memory_mistakes_title)) }
            if (state.mistakes.isEmpty()) {
                item { EmptyText(stringResource(R.string.memory_mistakes_empty)) }
            } else {
                item {
                    Button(onClick = onStartReview, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.memory_start_review))
                    }
                }
                items(state.mistakes, key = { "mistake_${it.id}" }) { mistake ->
                    MistakeCard(mistake, now = now, onDelete = { onDeleteMistake(mistake.id) })
                }
            }
        }
    }

    if (showAddFactDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFactDialog = false
                newFactText = ""
            },
            title = { Text(stringResource(R.string.memory_add_fact_title)) },
            text = {
                OutlinedTextField(
                    value = newFactText,
                    onValueChange = { newFactText = it },
                    placeholder = { Text(stringResource(R.string.memory_add_fact_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFactText.isNotBlank()) {
                            onAddFact(newFactText)
                            newFactText = ""
                            showAddFactDialog = false
                        }
                    },
                    enabled = newFactText.isNotBlank()
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddFactDialog = false
                    newFactText = ""
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun FactRow(fact: LearnerFactEntity, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
            Text(fact.fact, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.memory_delete))
            }
        }
    }
}

@Composable
private fun MistakeCard(mistake: MistakeEntity, now: Long, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                Text(
                    mistake.original,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    mistake.corrected,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (mistake.explanation.isNotBlank()) {
                    Text(mistake.explanation, style = MaterialTheme.typography.bodySmall)
                }
                val due = if (mistake.nextReviewAt <= now) {
                    stringResource(R.string.memory_mistake_due_now)
                } else {
                    stringResource(R.string.memory_mistake_due_on, formatDate(mistake.nextReviewAt))
                }
                val times = if (mistake.timesMade > 1) " · " + stringResource(R.string.memory_mistake_made_times, mistake.timesMade) else ""
                Text(due + times, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.memory_delete))
            }
        }
    }
}

private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
